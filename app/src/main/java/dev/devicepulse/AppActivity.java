package dev.devicepulse;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import java.util.*;
import org.json.*;

final class AppActivity {
  static boolean allowed(Context c) {
    return c.getSystemService(AppOpsManager.class)
            .checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName())
        == AppOpsManager.MODE_ALLOWED;
  }

  static JSONArray collect(Context c, long start, long end, Store db, long session) {
    Map<Integer, JSONObject> map = new HashMap<>();
    if (allowed(c)) {
      try {
        Map<String, UsageStats> uses =
            c.getSystemService(UsageStatsManager.class).queryAndAggregateUsageStats(start, end);
        for (Map.Entry<String, UsageStats> e : uses.entrySet()) {
          int uid;
          try {
            uid = c.getPackageManager().getApplicationInfo(e.getKey(), 0).uid;
          } catch (PackageManager.NameNotFoundException ex) {
            continue;
          }
          JSONObject o = entry(map, uid, e.getKey());
          Store.put(
              o,
              "foregroundMs",
              o.optLong("foregroundMs", 0) + e.getValue().getTotalTimeInForeground());
          Store.put(
              o,
              "usageSource",
              "measured · OS aggregate buckets may extend outside session; foreground time is not"
                  + " energy");
        }
      } catch (RuntimeException ex) {
        db.event(session, "usage-restricted", ex.getClass().getSimpleName());
      }
    } else
      db.event(
          session,
          "permission",
          "Usage access not granted; other-app network and foreground activity unavailable");
    NetworkStatsManager nm = c.getSystemService(NetworkStatsManager.class);
    for (int type : new int[] {ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE}) {
      try (NetworkStats ns = nm.querySummary(type, null, start, end)) {
        NetworkStats.Bucket b = new NetworkStats.Bucket();
        while (ns.hasNextBucket()) {
          ns.getNextBucket(b);
          JSONObject o = entry(map, b.getUid(), label(c, b.getUid()));
          Store.put(o, "rxBytes", o.optLong("rxBytes", 0) + b.getRxBytes());
          Store.put(o, "txBytes", o.optLong("txBytes", 0) + b.getTxBytes());
          Store.put(
              o,
              "networkSource",
              "measured · Android NetworkStats buckets, Wi-Fi/mobile; may be delayed/incomplete");
        }
      } catch (Exception e) {
        db.event(
            session,
            "network-restricted",
            (type == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Mobile")
                + " totals unavailable: "
                + e.getClass().getSimpleName());
      }
    }
    List<JSONObject> sorted = new ArrayList<>(map.values());
    sorted.sort(
        (a, b) ->
            Long.compare(
                b.optLong("rxBytes", 0) + b.optLong("txBytes", 0),
                a.optLong("rxBytes", 0) + a.optLong("txBytes", 0)));
    JSONArray a = new JSONArray();
    for (JSONObject o : sorted) {
      Store.put(o, "queryStartEpochMs", start);
      Store.put(o, "queryEndEpochMs", end);
      a.put(o);
    }
    return a;
  }

  static JSONObject entry(Map<Integer, JSONObject> map, int uid, String pkg) {
    JSONObject o = map.get(uid);
    if (o == null) {
      o = new JSONObject();
      Store.put(o, "uid", uid);
      Store.put(o, "package", pkg);
      for (String k :
          new String[] {"foregroundMs", "rxBytes", "txBytes", "batteryMah", "cpuMs", "wakeLockMs"})
        Store.put(o, k, null);
      Store.put(o, "privilegedMetrics", "unavailable; import from authorized collector only");
      map.put(uid, o);
    }
    return o;
  }

  static String label(Context c, int uid) {
    String[] p = c.getPackageManager().getPackagesForUid(uid);
    return p == null ? "System/unknown UID " + uid : String.join(", ", p);
  }
}
