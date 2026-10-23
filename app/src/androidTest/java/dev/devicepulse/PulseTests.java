package dev.devicepulse;

import android.app.*;
import android.content.*;
import android.database.sqlite.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Runs only on a disposable emulator: deletes DevicePulse test history. */
public class PulseTests extends Instrumentation {
  MainActivity a;
  int passed;
  StringBuilder log = new StringBuilder();
  Bundle args;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    args = b;
    start();
  }

  void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  interface Job {
    void run() throws Exception;
  }

  void test(String name, Job job) throws Exception {
    job.run();
    passed++;
    log.append("PASS ").append(name).append('\n');
    Bundle b = new Bundle();
    b.putString("stream", "PASS " + name + "\n");
    sendStatus(0, b);
  }

  void ui(Job j) {
    final Throwable[] e = {null};
    runOnMainSync(
        () -> {
          try {
            j.run();
          } catch (Throwable t) {
            e[0] = t;
          }
        });
    if (e[0] != null) throw new AssertionError(e[0]);
  }

  JSONObject json(String s) throws Exception {
    return new JSONObject(s);
  }

  void shot(String name) throws Exception {
    waitForIdleSync();
    SystemClock.sleep(500);
    android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
    check(bitmap != null, "No screenshot");
    File dir = new File(getTargetContext().getFilesDir(), "screenshots");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  void waitRunning(boolean state) {
    long end = SystemClock.uptimeMillis() + 20000;
    while (MonitorService.running != state && SystemClock.uptimeMillis() < end)
      SystemClock.sleep(100);
    check(MonitorService.running == state, "Service running state " + state);
  }

  public void onStart() {
    Bundle out = new Bundle();
    try {
      if (args != null && args.getString("upgrade") != null) {
        UpgradeState.run(this, args.getString("upgrade"));
        return;
      }
      if (args != null && "true".equals(args.getString("capturesOnly"))) {
        new PulseCapture(this).runCapture();
        return;
      }
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      ui(
          () -> {
            a.db.erase();
            a.prefs.edit().clear().commit();
            a.navigate("Dashboard");
          });
      test(
          "first launch has no demo records or automatic sensitive permission",
          () -> {
            check(a.db.sessions().length() == 0, "Nonempty first history");
            check(!AppActivity.allowed(a), "Unexpected usage grant");
            check(
                !Probe.permission(a, "android.permission.ACCESS_FINE_LOCATION"),
                "Location granted automatically");
          });
      test(
          "null measurements never become zero; classification and recurring semantics",
          () -> {
            check(Analysis.n(json("{\"x\":null}"), "x") == null, "Null lost");
            check(Logic.value(null, "mAh").equals("Unavailable"), "Null displayed as zero");
            check(!Logic.recurring(1) && Logic.recurring(2), "Recurring logic");
            check(Logic.drainRate(10.0, 20.0, 1000) == null, "Charging interpreted as drain");
          });
      test(
          "Doze gaps, reboot elapsed reset and clock jump are detected without guessing cause",
          () -> {
            check(!Logic.gap(300000, 300000, 300000), "Normal sample gap");
            check(Logic.gap(900000, 900000, 300000), "Delay absent");
            check(Logic.gap(300000, -300, 300000), "Reboot absent");
            check(Logic.gap(-100, 300000, 300000), "Clock jump absent");
          });
      test(
          "6h and 7h targets persist correctly; recovery marks interruption",
          () -> {
            for (int hours : new int[] {6, 7}) {
              long id = a.db.start(hours, 5);
              JSONObject s = a.db.session(id);
              check(
                  s.optLong("planned") - s.optLong("start") == hours * 3600000L,
                  "Duration mismatch");
              a.db.recover();
              check(a.db.session(id).optString("state").equals("interrupted"), "Recovery failed");
            }
            a.db.erase();
          });
      test(
          "foreground monitoring records actual battery/components, stop persists",
          () -> {
            ui(
                () -> {
                  a.db.start(6, 5);
                  a.startForegroundService(new Intent(a, MonitorService.class));
                });
            waitRunning(true);
            SystemClock.sleep(1200);
            JSONObject active = a.db.active();
            check(active != null, "No active session");
            long id = active.optLong("id");
            JSONArray samples = a.db.samples(id);
            check(samples.length() > 0, "No service sample");
            JSONObject measured = Store.parse(samples.optJSONObject(0).optString("data"));
            check(
                measured.has("batteryPct")
                    && measured.has("components")
                    && measured.has("screenOn"),
                "Required sample fields absent");
            ui(() -> a.startService(new Intent(a, MonitorService.class).setAction("stop")));
            waitRunning(false);
            check(a.db.session(id).optString("state").equals("stopped"), "Stop not persisted");
          });
      test(
          "expired target completes service and marks late stop instead of restart",
          () -> {
            ui(
                () -> {
                  long id = a.db.start(7, 5);
                  android.content.ContentValues v = new android.content.ContentValues();
                  v.put("planned", System.currentTimeMillis() - 1000);
                  a.db.getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + id});
                  a.startForegroundService(new Intent(a, MonitorService.class));
                });
            SystemClock.sleep(2000);
            check(a.db.active() == null, "Expired session remains running");
            org.json.JSONObject ended = a.db.sessions().optJSONObject(0);
            check(
                ended.optLong("end") > ended.optLong("planned"),
                "Late stop falsely timestamped at target");
            check(a.db.samples(ended.optLong("id")).length() > 0, "Actual final reading omitted");
            check(
                a.db.sessions().optJSONObject(0).optString("state").equals("completed"),
                "Not completed");
          });
      test(
          "denied usage/network access is explicit and never invents battery/CPU values",
          () -> {
            JSONObject s = a.db.sessions().optJSONObject(0);
            JSONArray list =
                AppActivity.collect(
                    a, s.optLong("start"), System.currentTimeMillis(), a.db, s.optLong("id"));
            for (int i = 0; i < list.length(); i++) {
              JSONObject row = list.optJSONObject(i);
              check(
                  row.isNull("batteryMah") && row.isNull("cpuMs") && row.isNull("wakeLockMs"),
                  "Invented privileged metrics");
            }
            check(
                a.db.events(s.optLong("id")).toString().contains("Usage access not granted"),
                "Denial not recorded");
          });
      // Synthetic fixtures are limited to this disposable test installation and deleted after each
      // test.
      test(
          "history coverage separates off/unplugged from session and excludes gaps",
          () -> {
            try (Store test = new Store(a)) {
              test.erase();
              long id = test.start(6, 5), start = System.currentTimeMillis() - 1800000L;
              android.content.ContentValues bounds = new android.content.ContentValues();
              bounds.put("start", start);
              bounds.put("planned", start + 21600000L);
              test.getWritableDatabase().update("sessions", bounds, "id=?", new String[] {"" + id});
              for (int i = 0; i < 4; i++) {
                JSONObject d =
                    json(
                        "{\"batteryPct\":"
                            + (90 - i)
                            + ",\"tempC\":"
                            + (30 + i)
                            + ",\"screenOn\":false,\"plugged\":false}");
                android.content.ContentValues v = new android.content.ContentValues();
                v.put("session", id);
                v.put("ts", start + (i == 3 ? 1500000 : i * 300000));
                v.put("elapsed", i == 3 ? 1500000 : i * 300000);
                v.put("data", d.toString());
                test.getWritableDatabase().insertOrThrow("samples", null, v);
              }
              JSONObject sum = Analysis.summary(test, id);
              check(sum.optLong("coveredMs") == 600000, "Gap counted as coverage");
              check(sum.optLong("screenOffUnpluggedMs") == 600000, "Subset incorrect");
              check(sum.optInt("gapCount") == 1, "Gap count wrong");
              check(sum.optDouble("maxC") == 33, "Max wrong");
              test.erase();
            }
          });
      test(
          "recurring findings use completed sessions only and ignore imported claims",
          () -> {
            a.db.erase();
            for (int k = 0; k < 3; k++) {
              long id = a.db.start(6, 5), start = System.currentTimeMillis() - 3600000L;
              android.content.ContentValues bounds = new android.content.ContentValues();
              bounds.put("start", start);
              bounds.put("planned", start + 21600000L);
              a.db.getWritableDatabase().update("sessions", bounds, "id=?", new String[] {"" + id});
              for (int i = 0; i < 2; i++) {
                android.content.ContentValues sample = new android.content.ContentValues();
                sample.put("session", id);
                sample.put("ts", start + i * 3600000L);
                sample.put("elapsed", i * 3600000L);
                sample.put("data", "{\"batteryPct\":" + (90 - i * 10) + ",\"tempC\":45}");
                a.db.getWritableDatabase().insertOrThrow("samples", null, sample);
              }
              a.db.finish(id, k == 2 ? "stopped" : "completed");
            }
            String finding = Analysis.findings(a.db, 40, 5, 100);
            check(
                finding.contains("RECURRING")
                    && finding.contains("2 completed session(s)")
                    && !finding.contains("3 completed session(s)"),
                "Recurrence includes noncompleted session");
            a.db.erase();
          });
      final JSONObject[] report = {null};
      test(
          "privileged import validates interval, classification, duplicate UID and nonnegative"
              + " values",
          () -> {
            JSONObject s = json("{\"start\":100,\"end\":1000}");
            String valid =
                "{\"schemaVersion\":1,\"classification\":\"privileged-import\",\"collector\":\"Authorized"
                    + " test"
                    + " fixture\",\"device\":\"Synthetic\",\"startEpochMs\":100,\"endEpochMs\":900,\"metrics\":[{\"uid\":10001,\"cpuMs\":10,\"batteryMah\":null}]}";
            check(
                Reports.validateImport(valid, s).getJSONArray("metrics").length() == 1,
                "Valid rejected");
            for (String invalid :
                new String[] {
                  valid.replace("privileged-import", "simulated"),
                  valid.replace("\"cpuMs\":10", "\"cpuMs\":-1"),
                  valid.replace("\"endEpochMs\":900", "\"endEpochMs\":1100"),
                  valid.replace("\"cpuMs\":10", "\"cpuMs\":\"10\"")
                }) {
              boolean rejected = false;
              try {
                Reports.validateImport(invalid, s);
              } catch (Exception expected) {
                rejected = true;
              }
              check(rejected, "Bad import accepted");
            }
            JSONObject duplicate = new JSONObject(valid);
            JSONArray rows = duplicate.getJSONArray("metrics");
            rows.put(rows.getJSONObject(0));
            boolean rejected = false;
            try {
              Reports.validateImport(duplicate.toString(), s);
            } catch (Exception expected) {
              rejected = true;
            }
            check(rejected, "Duplicate UID accepted");
          });
      test(
          "CSV, JSON and multipage PDF export real records, units and nulls",
          () -> {
            long id = a.db.start(6, 5);
            a.db.sample(id, Probe.take(a));
            a.db.finish(id, "stopped");
            report[0] = a.db.report(id);
            String csv = Reports.csv(report[0]);
            check(
                csv.contains("unobservedMs") && csv.contains("derived estimate"),
                "Coverage omitted from CSV");
            check(Reports.line(-2.5).contains("\"-2.5\""), "Decimal temperature altered in CSV");
            check(
                Reports.line(-150000).contains("\"-150000\""),
                "Negative numeric current altered in CSV");
            check(
                Reports.line("=UNTRUSTED()").contains("\"\'=UNTRUSTED()\""),
                "CSV formula escaped incorrectly");
            check(
                csv.contains("timestamp_epoch_ms")
                    && csv.contains("UNAVAILABLE")
                    && csv.contains("°C"),
                "CSV semantics missing");
            check(
                new JSONObject(report[0].toString()).optJSONArray("samples").length() > 0,
                "JSON not roundtrip");
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            Reports.pdf(pdf, report[0]);
            byte[] bytes = pdf.toByteArray();
            check(bytes.length > 100 && new String(bytes, 0, 4).equals("%PDF"), "PDF invalid");
            File dir = new File(a.getFilesDir(), "test-exports");
            dir.mkdirs();
            try (FileOutputStream f = new FileOutputStream(new File(dir, "report.pdf"))) {
              f.write(bytes);
            }
            try (FileOutputStream f = new FileOutputStream(new File(dir, "report.csv"))) {
              f.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            try (FileOutputStream f = new FileOutputStream(new File(dir, "report.json"))) {
              f.write(report[0].toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
          });
      test(
          "Android content URI exports reopen as valid JSON/CSV/PDF",
          () -> {
            if (Build.VERSION.SDK_INT < 29) throw new AssertionError("Requires API29 test device");
            for (String format : new String[] {"json", "csv", "pdf"}) {
              android.content.ContentValues v = new android.content.ContentValues();
              v.put(
                  android.provider.MediaStore.Downloads.DISPLAY_NAME, "DevicePulse-test." + format);
              v.put(
                  android.provider.MediaStore.Downloads.MIME_TYPE,
                  format.equals("json")
                      ? "application/json"
                      : format.equals("pdf") ? "application/pdf" : "text/csv");
              android.net.Uri uri =
                  a.getContentResolver()
                      .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
              check(uri != null, "Cannot create own export");
              try {
                Reports.write(a, uri, format, report[0]);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                  byte[] block = new byte[8192];
                  int n;
                  while ((n = in.read(block)) != -1) output.write(block, 0, n);
                }
                byte[] bytes = output.toByteArray();
                check(bytes.length > 50, "Empty document");
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (format.equals("json"))
                  check(new JSONObject(content).optInt("schemaVersion") == 1, "JSON invalid");
                if (format.equals("csv"))
                  check(content.contains("timestamp_epoch_ms"), "CSV invalid");
                if (format.equals("pdf")) check(content.startsWith("%PDF"), "PDF invalid");
              } finally {
                a.getContentResolver().delete(uri, null, null);
              }
            }
          });
      test(
          "user confirmed reasons persist and deletion clears all local tables",
          () -> {
            JSONObject s = a.db.sessions().optJSONObject(0);
            JSONArray entries = new JSONArray();
            entries.put(
                json(
                    "{\"uid\":10001,\"package\":\"test"
                        + " fixture\",\"rxBytes\":123,\"txBytes\":null}"));
            a.db.saveApps(s.optLong("id"), entries);
            long row = a.db.apps(s.optLong("id")).optJSONObject(0).optLong("id");
            a.db.reason(row, "Backup");
            a.db.saveApps(s.optLong("id"), entries);
            check(
                a.db.apps(s.optLong("id")).optJSONObject(0).optString("reason").equals("Backup"),
                "Reason lost");
            a.db.erase();
            a.db.event(s.optLong("id"), "late-worker", "Must not recreate deleted history");
            check(
                a.db.sessions().length() == 0 && a.db.events(-1).length() == 0, "Deletion failed");
          });
      test(
          "native pages render small-phone dark/light layouts without simulated history",
          () -> {
            for (String page :
                new String[] {
                  "Dashboard", "Activity", "Components", "History", "Findings", "Log", "Settings"
                }) {
              ui(() -> a.navigate(page));
              waitForIdleSync();
              check(a.body.getChildCount() > 0, "Empty " + page);
            }
            ui(
                () -> {
                  a.prefs.edit().putBoolean("dark", false).commit();
                  a.navigate("Components");
                });
            waitForIdleSync();
            ui(
                () -> {
                  a.prefs.edit().putBoolean("dark", true).commit();
                  a.navigate("Dashboard");
                });
          });
      test(
          "actual short-session samples render graphs with correct available values",
          () -> {
            ui(
                () -> {
                  long id = a.db.start(6, 5);
                  a.db.sample(id, Probe.take(a));
                  a.selected = id;
                });
            SystemClock.sleep(1100);
            ui(
                () -> {
                  a.db.sample(a.selected, Probe.take(a));
                  a.db.finish(a.selected, "stopped");
                  a.navigate("History");
                });
            waitForIdleSync();
            shot("history-real-short-session");
            ui(
                () -> {
                  for (int i = 0; i < a.body.getChildCount(); i++)
                    if (a.body.getChildAt(i) instanceof Graph) {
                      ((ScrollView) a.body.getParent()).scrollTo(0, a.body.getChildAt(i).getTop());
                      break;
                    }
                });
            shot("graphs-real-short-session");
          });
      OvernightChecks.run(this);
      ComponentChecks.run(this);
      for (String page :
          new String[] {
            "Dashboard", "Overnight", "Components", "History", "Activity", "Findings", "Settings"
          }) {
        ui(() -> a.navigate(page));
        shot(page.toLowerCase(Locale.ROOT));
      }
      ui(
          () -> {
            a.db.erase();
            a.navigate("Dashboard");
          });
      out.putInt("passed", passed);
      out.putString(
          "stream",
          log
              + "\n"
              + passed
              + " tests passed. Synthetic history fixtures deleted; screenshots show actual"
              + " empty/OS state. Full 6/7-hour endurance, OEM termination, radios and physical"
              + " sensors not validated.\n");
      finish(Activity.RESULT_OK, out);
    } catch (Throwable t) {
      out.putString("stream", log + "\nFAIL " + android.util.Log.getStackTraceString(t));
      finish(Activity.RESULT_CANCELED, out);
    }
  }
}
