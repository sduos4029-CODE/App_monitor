package dev.devicepulse;

import java.util.*;
import org.json.*;

final class Analysis {
  static Double n(JSONObject o, String k) {
    return o == null || o.isNull(k) || !o.has(k) ? null : o.optDouble(k);
  }

  static JSONObject summary(Store db, long id) {
    JSONObject s = db.session(id), o = new JSONObject();
    if (s == null) return o;
    JSONArray rows = db.samples(id);
    long start = s.optLong("start"),
        end = s.isNull("end") ? System.currentTimeMillis() : s.optLong("end");
    long window = Math.max(0, end - start), covered = 0, off = 0, gaps = 0;
    Double first = null, last = null, min = null, max = null;
    long firstBatteryTs = 0, lastBatteryTs = 0;
    int batteryCount = 0;
    double offDrain = 0;
    boolean offBatteryKnown = false, clockUncertain = end < start;
    JSONObject previous = null;
    for (int i = 0; i < rows.length(); i++) {
      JSONObject r = rows.optJSONObject(i), d = Store.parse(r.optString("data"));
      Double battery = n(d, "batteryPct"), t = n(d, "tempC");
      if (battery != null) {
        if (first == null) {
          first = battery;
          firstBatteryTs = r.optLong("ts");
        }
        last = battery;
        lastBatteryTs = r.optLong("ts");
        batteryCount++;
      }
      if (t != null) {
        min = min == null ? t : Math.min(min, t);
        max = max == null ? t : Math.max(max, t);
      }
      if (previous != null) {
        long wall = r.optLong("ts") - previous.optLong("ts"),
            elapsed = r.optLong("elapsed") - previous.optLong("elapsed");
        clockUncertain |= wall < 0 || elapsed < 0 || Math.abs(wall - elapsed) > 60000;
        long interval =
            Math.max(0, Math.min(end, r.optLong("ts")) - Math.max(start, previous.optLong("ts")));
        if (!Logic.gap(wall, elapsed, s.optLong("interval"))) {
          covered += interval;
          JSONObject pd = Store.parse(previous.optString("data"));
          if (Boolean.FALSE.equals(pd.opt("screenOn"))
              && Boolean.FALSE.equals(pd.opt("plugged"))
              && Boolean.FALSE.equals(d.opt("screenOn"))
              && Boolean.FALSE.equals(d.opt("plugged"))) {
            off += interval;
            Double pb = n(pd, "batteryPct");
            if (pb != null && battery != null && wall > 0) {
              offBatteryKnown = true;
              if (pb >= battery) offDrain += (pb - battery) * interval / wall;
            }
          }
        } else gaps++;
      }
      previous = r;
    }
    covered = Math.min(window, covered);
    off = Math.min(covered, off);
    boolean batteryPair = batteryCount >= 2 && lastBatteryTs > firstBatteryTs;
    Store.put(o, "sampleCount", rows.length());
    Store.put(o, "batteryReadingCount", batteryCount);
    Store.put(o, "firstBatteryTs", first == null ? null : firstBatteryTs);
    Store.put(o, "lastBatteryTs", last == null ? null : lastBatteryTs);
    Store.put(o, "batterySpanMs", batteryPair ? lastBatteryTs - firstBatteryTs : null);
    Store.put(o, "windowMs", window);
    Store.put(o, "coveredMs", covered);
    Store.put(o, "screenOffUnpluggedMs", off);
    Store.put(o, "otherObservedMs", covered - off);
    Store.put(o, "unobservedMs", window - covered);
    Store.put(o, "clockUncertain", clockUncertain);
    Store.put(o, "gapCount", gaps);
    Store.put(o, "minC", min);
    Store.put(o, "maxC", max);
    Store.put(o, "firstPct", first);
    Store.put(o, "lastPct", last);
    Store.put(o, "netChangePctPoints", batteryPair ? last - first : null);
    Store.put(
        o,
        "drainPctPerHour",
        batteryPair && !clockUncertain
            ? Logic.drainRate(first, last, lastBatteryTs - firstBatteryTs)
            : null);
    Store.put(
        o,
        "screenOffDrainPctPerHourEstimated",
        off > 0 && offBatteryKnown && !clockUncertain ? offDrain * 3600000 / off : null);
    return o;
  }

  static String findings(Store db, float temp, float drain, float megabytes) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    JSONArray sessions = db.sessions();
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < sessions.length(); i++) {
      JSONObject s = sessions.optJSONObject(i);
      if (!"completed".equals(s.optString("state"))) continue;
      long id = s.optLong("id");
      JSONObject sum = summary(db, id);
      Double max = n(sum, "maxC"), rate = n(sum, "drainPctPerHour");
      if (max != null && max > temp) add(counts, "Battery temperature above " + temp + " °C");
      if (rate != null && rate > drain)
        add(counts, "Estimated whole-session drain above " + drain + " percentage points/h");
      JSONArray apps = db.apps(id);
      for (int j = 0; j < apps.length(); j++) {
        JSONObject app = apps.optJSONObject(j), data = Store.parse(app.optString("data"));
        if (!data.isNull("rxBytes")
            && !data.isNull("txBytes")
            && data.optLong("rxBytes") + data.optLong("txBytes") > megabytes * 1000000.0)
          add(
              counts,
              "UID "
                  + app.optInt("uid")
                  + " data transfer above "
                  + megabytes
                  + " MB (partial OS buckets)");
      }
    }
    for (Map.Entry<String, Integer> e : counts.entrySet())
      result
          .append(Logic.recurring(e.getValue()) ? "RECURRING — " : "Single session — ")
          .append(e.getKey())
          .append(": ")
          .append(e.getValue())
          .append(" completed session(s)\n\n");
    return result.length() == 0
        ? "No completed-session threshold violations found. Missing measurements cannot establish"
            + " normal behavior."
        : result
            + "Review only; these findings do not establish causation or malicious behavior."
            + " Current thresholds apply retrospectively. Interrupted/stopped sessions and imports"
            + " are excluded from recurring calculations.";
  }

  static void add(Map<String, Integer> m, String k) {
    m.put(k, m.getOrDefault(k, 0) + 1);
  }

  static String compare(Store db, int days) {
    long since = System.currentTimeMillis() - days * 86400000L;
    JSONArray sessions = db.sessions();
    int count = 0, complete = 0;
    long covered = 0, off = 0;
    double net = 0;
    int netCount = 0;
    Double min = null, max = null;
    for (int i = 0; i < sessions.length(); i++) {
      JSONObject s = sessions.optJSONObject(i);
      if (s.optLong("start") < since) continue;
      count++;
      if ("completed".equals(s.optString("state"))) complete++;
      JSONObject o = summary(db, s.optLong("id"));
      covered += o.optLong("coveredMs");
      off += o.optLong("screenOffUnpluggedMs");
      Double t = n(o, "maxC"), lo = n(o, "minC"), change = n(o, "netChangePctPoints");
      if (t != null) max = max == null ? t : Math.max(t, max);
      if (lo != null) min = min == null ? lo : Math.min(lo, min);
      if (change != null) {
        net += change;
        netCount++;
      }
    }
    return "Past "
        + days
        + " days: "
        + count
        + " sessions, "
        + complete
        + " completed\nObserved sample intervals: "
        + MainActivity.duration(covered)
        + "; screen-off/unplugged: "
        + MainActivity.duration(off)
        + "\nTemperature min/max: "
        + Logic.value(min, "°C")
        + " / "
        + Logic.value(max, "°C")
        + "\nMean net battery change: "
        + (netCount == 0
            ? "Unavailable"
            : String.format(
                Locale.US, "%.1f percentage points (%d sessions)", net / netCount, netCount))
        + "\n"
        + "Includes interrupted-session observations, excludes missing intervals. No simulated"
        + " data.";
  }
}
