package dev.devicepulse;

import android.content.*;
import android.widget.*;
import org.json.*;

/**
 * Additional 1.1 regression cases. Synthetic rows are confined to this disposable test
 * installation.
 */
final class OvernightChecks {
  static long session(Store db) {
    long id = db.start(6, 5), start = System.currentTimeMillis() - 1800000L;
    ContentValues v = new ContentValues();
    v.put("start", start);
    v.put("planned", start + 21600000L);
    db.getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + id});
    return id;
  }

  static void sample(Store db, long id, long offset, long elapsed, String data) {
    ContentValues v = new ContentValues();
    v.put("session", id);
    v.put("ts", db.session(id).optLong("start") + offset);
    v.put("elapsed", elapsed);
    v.put("data", data);
    db.getWritableDatabase().insertOrThrow("samples", null, v);
  }

  static boolean text(android.view.View v, String term) {
    if (v instanceof android.widget.TextView
        && ((android.widget.TextView) v).getText().toString().contains(term)) return true;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) if (text(g.getChildAt(i), term)) return true;
    }
    return false;
  }

  static String shell(PulseTests t, String command) throws Exception {
    try (android.os.ParcelFileDescriptor fd = t.getUiAutomation().executeShellCommand(command);
        java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] data = new byte[4096];
      int n;
      while ((n = in.read(data)) != -1) out.write(data, 0, n);
      return out.toString("UTF-8");
    }
  }

  static void run(PulseTests t) throws Exception {
    MainActivity a = t.a;
    t.test(
        "overnight: one reading has no fabricated zero battery loss",
        () -> {
          a.db.erase();
          long id = session(a.db);
          sample(a.db, id, 0, 0, "{\"batteryPct\":82,\"screenOn\":false,\"plugged\":false}");
          JSONObject sum = Analysis.summary(a.db, id);
          t.check(
              sum.isNull("netChangePctPoints") && sum.isNull("drainPctPerHour"),
              "One point pretends to measure change");
          a.db.erase();
        });
    t.test(
        "overnight: unknown states excluded and coverage partitions sum to session",
        () -> {
          long id = session(a.db);
          sample(a.db, id, 0, 0, "{\"batteryPct\":82,\"screenOn\":null,\"plugged\":null}");
          sample(
              a.db, id, 300000, 300000, "{\"batteryPct\":81,\"screenOn\":false,\"plugged\":false}");
          sample(
              a.db, id, 600000, 600000, "{\"batteryPct\":80,\"screenOn\":false,\"plugged\":false}");
          JSONObject sum = Analysis.summary(a.db, id);
          t.check(
              sum.optLong("screenOffUnpluggedMs") == 300000,
              "Unknown states were counted as unused");
          t.check(sum.optLong("coveredMs") == 600000, "Coverage incorrect");
          t.check(
              sum.optLong("screenOffUnpluggedMs")
                      + sum.optLong("otherObservedMs")
                      + sum.optLong("unobservedMs")
                  == sum.optLong("windowMs"),
              "Partition doesn't sum");
          a.db.erase();
        });
    t.test(
        "overnight: missing battery in unplugged interval remains unavailable",
        () -> {
          long id = session(a.db);
          sample(a.db, id, 0, 0, "{\"batteryPct\":null,\"screenOn\":false,\"plugged\":false}");
          sample(
              a.db,
              id,
              300000,
              300000,
              "{\"batteryPct\":null,\"screenOn\":false,\"plugged\":false}");
          JSONObject sum = Analysis.summary(a.db, id);
          t.check(
              sum.isNull("screenOffDrainPctPerHourEstimated"),
              "Missing battery substituted with zero drain");
          a.db.erase();
        });
    t.test(
        "overnight: clock discontinuity suppresses rates and breaks chart lines",
        () -> {
          long id = session(a.db);
          sample(a.db, id, 0, 0, "{\"batteryPct\":82}");
          sample(a.db, id, 300000, 100, "{\"batteryPct\":80}");
          JSONObject sum = Analysis.summary(a.db, id);
          t.check(
              sum.optBoolean("clockUncertain") && sum.isNull("drainPctPerHour"),
              "Clock change presented as reliable rate");
          t.ui(
              () -> {
                Graph g = new Graph(a, "Battery", "%");
                g.timeline(a.db.samples(id), "batteryPct", 300000);
                t.check(g.breaks.get(1), "Clock discontinuity joined in chart");
              });
          a.db.erase();
        });
    t.test(
        "overnight: native five-tab navigation and empty report have no demo readings",
        () -> {
          t.ui(
              () -> {
                a.selected = -1;
                a.navigate("Dashboard");
                t.check(a.nav.getChildCount() == 5, "Expected five bottom destinations");
                a.nav.getChildAt(1).performClick();
                t.check(
                    a.page.equals("Overnight") && text(a.body, "No overnight report yet"),
                    "Overnight navigation failed");
                t.check(
                    !text(a.body, "82%") && !text(a.body, "420 MB"),
                    "Mockup numbers leaked into real UI");
                a.nav.getChildAt(2).performClick();
                t.check(
                    a.page.equals("Activity") && text(a.body, "Exact per-app battery: unavailable"),
                    "Per-app battery boundary hidden");
              });
        });
    t.test(
        "overnight: purpose is unknown unless user confirms it; rankings are not energy",
        () -> {
          long id = session(a.db);
          a.db.finish(id, "stopped");
          JSONArray rows = new JSONArray();
          rows.put(
              new JSONObject(
                  "{\"uid\":10001,\"package\":\"synthetic.test.only\",\"rxBytes\":1234,\"txBytes\":567,\"batteryMah\":null}"));
          a.db.saveApps(id, rows);
          t.ui(
              () -> {
                a.selected = id;
                a.navigate("Activity");
                t.check(
                    text(a.body, "Purpose: not known")
                        && text(a.body, "Possible explanations · not verified"),
                    "Unconfirmed purpose inferred");
              });
          long rowId = a.db.apps(id).optJSONObject(0).optLong("id");
          a.db.reason(rowId, "Backup");
          t.ui(
              () -> {
                a.render();
                t.check(text(a.body, "User-confirmed context: Backup"), "Confirmed reason absent");
              });
          a.db.erase();
          t.ui(
              () -> {
                a.selected = -1;
                a.navigate("Dashboard");
              });
        });
    t.test(
        "overnight: seven-hour choice survives activity state saving",
        () -> {
          t.ui(
              () -> {
                a.pendingHours = 7;
                android.os.Bundle saved = new android.os.Bundle();
                a.onSaveInstanceState(saved);
                t.check(
                    saved.getInt("pendingHours") == 7,
                    "7h changed to 6h during permission recreation");
              });
        });
    t.test(
        "overnight: public Android Battery usage intent opens the system screen",
        () -> {
          t.ui(() -> new OvernightScreen(a).batterySettings());
          boolean opened = false;
          long deadline = android.os.SystemClock.uptimeMillis() + 15000;
          while (android.os.SystemClock.uptimeMillis() < deadline) {
            String stack = shell(t, "dumpsys activity activities");
            for (String line : stack.split("\n"))
              if (line.contains("mResumedActivity") && line.contains("PowerUsageSummaryActivity"))
                opened = true;
            if (opened) break;
            android.os.SystemClock.sleep(200);
          }
          t.check(opened, "Android Battery usage screen did not open");
          shell(t, "input keyevent KEYCODE_BACK");
          android.os.SystemClock.sleep(400);
          t.ui(() -> a.navigate("Dashboard"));
        });
  }
}
