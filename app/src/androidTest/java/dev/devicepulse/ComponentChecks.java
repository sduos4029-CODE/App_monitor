package dev.devicepulse;

import android.os.SystemClock;
import android.widget.ScrollView;
import org.json.JSONObject;

/** Regression checks against actual Android API observations; no generated device metrics. */
final class ComponentChecks {
  static void run(PulseTests t) throws Exception {
    MainActivity a = t.a;
    t.test(
        "component dashboard automatically refreshes actual readings without replacing scroll"
            + " views",
        () -> {
          t.ui(() -> a.navigate("Components"));
          ComponentDashboard dashboard = a.componentDashboard;
          android.view.View body = a.body;
          int before = dashboard.updates;
          long captured = dashboard.latest.optLong("capturedAt");
          SystemClock.sleep(2800);
          t.ui(
              () -> {
                t.check(dashboard.updates > before, "No foreground periodic update");
                t.check(dashboard.latest.optLong("capturedAt") > captured, "Stale timestamp");
                t.check(
                    a.body == body && a.componentDashboard == dashboard,
                    "Rebuilt scroll container");
                t.check(
                    dashboard.tiles.size() == 4 && dashboard.statuses.size() == 10,
                    "Missing cards");
                t.check(
                    dashboard.latest.optDouble("batteryPct", -1) >= 0,
                    "No actual battery API reading");
                t.check(a.sensorListener == null, "Dashboard activated sensors");
              });
        });
    t.test(
        "component unavailable and permission states never become zero or enabled",
        () -> {
          t.check("Unavailable".equals(ComponentDashboard.value(null, "%")), "Null became number");
          t.check("Unavailable".equals(ComponentDashboard.shortState(null)), "Missing state");
          JSONObject permission = t.json("{\"state\":\"permission-required\",\"value\":null}");
          JSONObject unsupported = t.json("{\"state\":\"unsupported\",\"value\":null}");
          t.check(
              "permission-required".equals(ComponentDashboard.shortState(permission)),
              "Lost permission state");
          t.check(
              "unsupported".equals(ComponentDashboard.shortState(unsupported)),
              "Lost unsupported state");
          t.ui(
              () ->
                  t.check(
                      OvernightChecks.text(a.body, "Device-wide CPU: restricted / unavailable"),
                      "False CPU attribution"));
        });
    t.test(
        "component refresh preserves scroll position and does not write session history",
        () -> {
          t.ui(
              () -> {
                ScrollView scroll = (ScrollView) a.body.getParent();
                scroll.scrollTo(0, 420);
                int position = scroll.getScrollY();
                int history = a.db.sessions().length();
                a.componentDashboard.update();
                t.check(scroll.getScrollY() == position, "Refresh moved scroll");
                t.check(a.db.sessions().length() == history, "Dashboard created session");
              });
        });
    t.test(
        "paused lifecycle suppresses live refresh and navigation discards component view",
        () -> {
          t.ui(
              () -> {
                ComponentDashboard dashboard = a.componentDashboard;
                int count = dashboard.updates;
                a.onPause();
                a.refresh.run();
                t.check(dashboard.updates == count && !a.resumed, "Refreshed while paused");
                a.onResume();
                a.navigate("Dashboard");
                t.check(a.componentDashboard == null, "Retained invisible live dashboard");
              });
        });
  }
}
