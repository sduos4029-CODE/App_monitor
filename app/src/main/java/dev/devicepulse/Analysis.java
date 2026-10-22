package dev.devicepulse;

import java.util.*;
import org.json.*;

final class Analysis {
  static Double n(JSONObject o, String k) {
    return o == null || o.isNull(k) || !o.has(k) ? null : o.optDouble(k);
  }

  static JSONObject summary(Store db, long id) {
    JSONObject s = db.session(id), o = new JSONObject();
    JSONArray samples = db.samples(id);
    long covered = 0, off = 0, gaps = 0;
    Double first = null, last = null, min = null, max = null;
    double offDrain = 0;
    JSONObject prev = null;
    for (int i = 0; i < samples.length(); i++) {
      JSONObject r = samples.optJSONObject(i), d = Store.parse(r.optString("data"));
      Double b = n(d, "batteryPct"), t = n(d, "tempC");
      if (b != null) {
        if (first == null) first = b;
        last = b;
      }
      if (t != null) {
        min = min == null ? t : Math.min(min, t);
        max = max == null ? t : Math.max(max, t);
      }
      if (prev != null) {
        long delta = r.optLong("ts") - prev.optLong("ts");
        long mono = r.optLong("elapsed") - prev.optLong("elapsed");
        JSONObject pd = Store.parse(prev.optString("data"));
        if (delta >= 0 && !Logic.gap(delta, mono, s.optLong("interval"))) {
          covered += delta;
          if (pd.has("screenOn")
              && !pd.optBoolean("screenOn")
              && pd.has("plugged")
              && !pd.optBoolean("plugged")
              && d.has("screenOn")
              && !d.optBoolean("screenOn")
              && d.has("plugged")
              && !d.optBoolean("plugged")) {
            off += delta;
            Double pb = n(pd, "batteryPct");
            if (pb != null && b != null && pb >= b) offDrain += pb - b;
          }
        } else gaps++;
      }
      prev = r;
    }
    Store.put(o, "sampleCount", samples.length());
    Store.put(o, "coveredMs", covered);
    Store.put(o, "screenOffUnpluggedMs", off);
    Store.put(o, "gapCount", gaps);
    Store.put(o, "minC", min);
    Store.put(o, "maxC", max);
    Store.put(o, "firstPct", first);
    Store.put(o, "lastPct", last);
    Store.put(o, "netChangePctPoints", first == null || last == null ? null : last - first);
    // Sample-to-sample rate is a derived estimate, not measured per-app energy.
    long span =
        samples.length() < 2
            ? 0
            : samples.optJSONObject(samples.length() - 1).optLong("ts")
                - samples.optJSONObject(0).optLong("ts");
    Store.put(o, "drainPctPerHour", Logic.drainRate(first, last, span));
    Store.put(o, "screenOffDrainPctPerHourEstimated", off > 0 ? offDrain * 3600000 / off : null);
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
