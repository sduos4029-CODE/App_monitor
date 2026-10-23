package dev.devicepulse;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/**
 * Overnight-first UI. Only recorded observations; no automatic attribution of energy or job
 * purpose.
 */
final class OvernightScreen {
  final MainActivity a;
  final int blue = 0xff42acff;

  OvernightScreen(MainActivity a) {
    this.a = a;
  }

  void navigation() {
    a.nav = new LinearLayout(a);
    a.nav.setGravity(Gravity.CENTER);
    a.nav.setPadding(0, a.dp(6), 0, a.dp(4));
    String[] names = {"Home", "Overnight", "Apps", "History", "Settings"},
        pages = {"Dashboard", "Overnight", "Activity", "History", "Settings"};
    for (int i = 0; i < names.length; i++) {
      String page = pages[i], name = names[i];
      boolean selected =
          a.page.equals(page)
              || (page.equals("Settings")
                  && Arrays.asList("Components", "Findings", "Log").contains(a.page));
      int color = selected ? blue : a.muted;
      LinearLayout item = new LinearLayout(a);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);
      item.setMinimumHeight(a.dp(64));
      item.addView(
          new PulseIcon(a, name, color), new LinearLayout.LayoutParams(a.dp(25), a.dp(25)));
      TextView label = a.text(name, 11, color);
      label.setGravity(Gravity.CENTER);
      label.setTextIsSelectable(false);
      label.setPadding(0, a.dp(3), 0, 0);
      item.addView(label);
      item.setFocusable(true);
      item.setClickable(true);
      item.setSelected(selected);
      item.setContentDescription(name + (selected ? ", selected" : ""));
      item.setBackground(a.shape(selected ? (a.dark ? 0xff152b40 : 0xffe0efff) : a.bg));
      item.setOnClickListener(v -> a.navigate(page));
      a.nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
    a.root.addView(a.nav);
  }

  LinearLayout card(String title, String detail) {
    LinearLayout c = a.box(title, detail);
    GradientDrawable bg =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {a.card, a.dark ? 0xff111d2a : 0xfff6faff});
    bg.setCornerRadius(a.dp(18));
    bg.setStroke(a.dp(1), a.dark ? 0xff293c50 : 0xffd4e0ef);
    c.setBackground(bg);
    if (detail.isEmpty() && c.getChildCount() > 1) c.removeViewAt(1);
    return c;
  }

  void small(LinearLayout c, String text) {
    c.addView(a.text(text, 13, a.muted));
  }

  TextView strong(String text, int size) {
    TextView v = a.text(text, size, a.ink);
    v.setTypeface(null, Typeface.BOLD);
    return v;
  }

  String pct(Double v) {
    return v == null ? "Unavailable" : String.format(Locale.getDefault(), "%.0f%%", v);
  }

  static String bytes(Double b) {
    return b == null
        ? "Unavailable"
        : b >= 1e9
            ? String.format(Locale.US, "%.2f GB", b / 1e9)
            : b >= 1e6
                ? String.format(Locale.US, "%.1f MB", b / 1e6)
                : b >= 1000
                    ? String.format(Locale.US, "%.1f kB", b / 1000)
                    : String.format(Locale.US, "%.0f B", b);
  }

  void title(String title, String subtitle) {
    a.title(title);
    a.note(subtitle);
  }

  void startActions(LinearLayout c) {
    LinearLayout row = new LinearLayout(a);
    row.addView(a.button("Start 6h", () -> a.start(6)), new LinearLayout.LayoutParams(0, -2, 1));
    View spacer = new View(a);
    row.addView(spacer, new LinearLayout.LayoutParams(a.dp(8), 1));
    row.addView(a.button("Start 7h", () -> a.start(7)), new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(row);
  }

  void home() {
    title("What happened while you slept?", "Overnight battery monitoring · local and private");
    JSONObject active = a.db.active();
    LinearLayout monitor =
        card(
            active == null ? "Ready for tonight" : "Monitoring your night",
            active == null
                ? "Start before bed, unplug your phone and turn the screen off."
                : "Started "
                    + MainActivity.time(active.optLong("start"))
                    + "\nTarget "
                    + MainActivity.time(active.optLong("planned")));
    if (active == null) startActions(monitor);
    else {
      monitor.addView(
          a.button(
              "View current report",
              () -> {
                a.selected = active.optLong("id");
                a.navigate("Overnight");
              }));
      monitor.addView(a.button("Stop monitoring", this::stop));
    }
    JSONArray latest = a.db.sessions();
    JSONObject s = latest.length() == 0 ? null : latest.optJSONObject(0);
    if (s != null) {
      JSONObject sum = Analysis.summary(a.db, s.optLong("id"));
      LinearLayout last =
          card(
              "Latest report · " + s.optString("state"),
              MainActivity.time(s.optLong("start")) + "\n" + batteryChange(sum));
      last.addView(
          a.button(
              "Review overnight report",
              () -> {
                a.selected = s.optLong("id");
                a.navigate("Overnight");
              }));
    }
    JSONObject live = Probe.take(a);
    LinearLayout current =
        card(
            "Current device readings",
            pct(Analysis.n(live, "batteryPct"))
                + " battery · "
                + Logic.value(Analysis.n(live, "tempC"), "°C")
                + "\n"
                + live.optString("chargingSource")
                + " · screen "
                + (live.optBoolean("screenOn") ? "on" : "off"));
    small(
        current,
        "These readings describe "
            + android.os.Build.MANUFACTURER
            + " "
            + android.os.Build.MODEL
            + ". Emulator values are not physical-device validation.");
    current.addView(a.button("Live device dashboard", () -> a.navigate("Components")));
    LinearLayout help =
        card(
            "The morning report",
            "Battery change, screen-off/unplugged coverage, sampling gaps and permitted app"
                + " activity.");
    small(
        help,
        "Exact per-app battery and internal job purposes are not exposed to an ordinary app. Data"
            + " transfer is not proof of battery drain.");
    help.addView(a.button("Monitoring limits", this::limits));
  }

  String batteryChange(JSONObject sum) {
    Double first = Analysis.n(sum, "firstPct"),
        last = Analysis.n(sum, "lastPct"),
        change = Analysis.n(sum, "netChangePctPoints");
    if (change == null) return "Battery change unavailable · two timed readings are required";
    return pct(first)
        + " → "
        + pct(last)
        + "\n"
        + String.format(
            Locale.getDefault(),
            "%.1f percentage points %s",
            Math.abs(change),
            change < 0 ? "lost" : change > 0 ? "gained" : "changed")
        + " · whole device";
  }

  String shortTime(long timestamp) {
    return new java.text.SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        .format(new Date(timestamp));
  }

  String readingTime(long timestamp) {
    return new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        .format(new Date(timestamp));
  }

  static String span(long ms) {
    if (ms >= 3600000) return String.format(Locale.US, "%dh %02dm", ms / 3600000, ms / 60000 % 60);
    if (ms >= 60000) return (ms / 60000) + "m " + (ms / 1000 % 60) + "s";
    return String.format(Locale.US, "%.1fs", ms / 1000.0);
  }

  void metric(LinearLayout parent, String name, String value) {
    LinearLayout row = new LinearLayout(a);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView key = a.text(name, 13, a.muted);
    key.setPadding(0, a.dp(3), 0, a.dp(3));
    row.addView(key, new LinearLayout.LayoutParams(0, -2, 1));
    TextView v = strong(value, 14);
    v.setGravity(Gravity.END);
    row.addView(v, new LinearLayout.LayoutParams(-2, -2));
    parent.addView(row);
  }

  void info(LinearLayout parent, String label, Runnable click) {
    TextView link = a.text(label + "  ›", 13, blue);
    link.setTextIsSelectable(false);
    link.setMinHeight(a.dp(36));
    link.setFocusable(true);
    link.setOnClickListener(v -> click.run());
    parent.addView(link);
  }

  void report() {
    title("Your night, explained", "Whole-device change · actual recorded observations");
    JSONObject s = a.chosen();
    if (s == null) {
      LinearLayout c =
          card(
              "No overnight report yet",
              "Start a 6h or 7h target session before bed. No demo values are substituted.");
      startActions(c);
      c.addView(a.button("How monitoring works", this::limits));
      return;
    }
    long id = s.optLong("id");
    JSONObject sum = Analysis.summary(a.db, id);
    long target = s.optLong("planned") - s.optLong("start");
    LinearLayout overview =
        card(
            (target / 3600000) + "h target · " + s.optString("state"),
            shortTime(s.optLong("start"))
                + " → "
                + (s.isNull("end") ? "now" : shortTime(s.optLong("end"))));
    Double change = Analysis.n(sum, "netChangePctPoints");
    overview.addView(
        strong(
            change == null
                ? (sum.isNull("firstPct")
                    ? "Battery unavailable"
                    : pct(Analysis.n(sum, "firstPct")) + " · change unavailable")
                : pct(Analysis.n(sum, "firstPct")) + " → " + pct(Analysis.n(sum, "lastPct")),
            27));
    small(
        overview,
        change == null
            ? "Battery change unavailable until two timed readings exist"
            : change == 0
                ? "No net change in the recorded battery levels"
                : String.format(
                    Locale.getDefault(),
                    "%.1f percentage points %s",
                    Math.abs(change),
                    change < 0 ? "lost" : "gained"));
    small(overview, "Whole device · " + sum.optInt("batteryReadingCount") + " battery readings");
    info(
        overview,
        "Reading times & measurement details",
        () ->
            a.explain(
                "Battery observations",
                "First: "
                    + (sum.isNull("firstBatteryTs")
                        ? "Unavailable"
                        : MainActivity.time(sum.optLong("firstBatteryTs")))
                    + "\nLast: "
                    + (sum.isNull("lastBatteryTs")
                        ? "Unavailable"
                        : MainActivity.time(sum.optLong("lastBatteryTs")))
                    + "\nTarget: "
                    + MainActivity.time(s.optLong("planned"))
                    + "\nRecorded end: "
                    + (s.isNull("end") ? "Still active" : MainActivity.time(s.optLong("end")))
                    + "\n"
                    + "Battery values come from Android. Coarse percentages and charging affect"
                    + " interpretation. The target may be exceeded when Android delays collection;"
                    + " no on-time reading or stop is assumed. This is not per-app energy"
                    + " attribution."));
    LinearLayout coverage = card("Coverage of this session", "");
    if (sum.optBoolean("clockUncertain"))
      small(
          coverage,
          "Clock discontinuity: coverage durations are unreliable. Inspect the timestamped log.");
    else {
      metric(coverage, "Screen off + unplugged", span(sum.optLong("screenOffUnpluggedMs")));
      metric(coverage, "Unobserved / delayed", span(sum.optLong("unobservedMs")));
      metric(coverage, "Other observed intervals", span(sum.optLong("otherObservedMs")));
    }
    small(
        coverage,
        sum.optInt("sampleCount")
            + " samples · "
            + sum.optInt("gapCount")
            + " internal gaps · durations estimated");
    info(
        coverage,
        "Timing details",
        () ->
            a.explain(
                "Coverage limits",
                "Recorded session window: "
                    + MainActivity.duration(sum.optLong("windowMs"))
                    + ". Coverage uses adjacent timely samples. Both endpoints must explicitly"
                    + " report screen-off and unplugged to count that subset. Unknown state never"
                    + " counts as unused time. Missing intervals are unknown, and screen off does"
                    + " not prove nobody touched the phone. Clock/reset warnings invalidate"
                    + " duration estimates."));
    Graph graph = new Graph(a, "Battery level overnight", "%");
    graph.timeline(a.db.samples(id), "batteryPct", s.optLong("interval"));
    graph.windowStart = s.optLong("start");
    graph.windowEnd = s.isNull("end") ? System.currentTimeMillis() : s.optLong("end");
    a.body.addView(graph, new LinearLayout.LayoutParams(-1, a.dp(218)));
    a.note("Dots are observations. Lines break across detected gaps.");
    LinearLayout review = card("What deserves review?", reviewText(id));
    review.addView(a.button("Review app activity", () -> a.navigate("Activity")));
    small(review, "Data transfer is not proof of battery drain.");
    LinearLayout reasons =
        card(
            "Possible explanations · not verified",
            "Sync, backups, updates, downloads and alarms are possibilities, not detected tasks.");
    reasons.addView(a.button("Add your confirmed context", () -> a.navigate("Activity")));
    LinearLayout actions =
        card("Report & evidence", "Export timestamps, sources and coverage limits.");
    for (String f : new String[] {"csv", "json", "pdf"})
      actions.addView(a.button("Export " + f.toUpperCase(Locale.ROOT), () -> a.export(f)));
    actions.addView(a.button("Open Android battery usage", this::batterySettings));
    actions.addView(a.button("Sampling & component log", () -> a.navigate("Log")));
    actions.addView(a.button("Monitoring limits", this::limits));
    if (a.db.active() != null) actions.addView(a.button("Stop monitoring", this::stop));
  }

  String reviewText(long id) {
    JSONArray rows = a.db.apps(id);
    JSONObject best = null;
    double max = -1;
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      double n = a.order(row, false);
      if (n > max) {
        max = n;
        best = row;
      }
    }
    if (best != null && max == 0)
      return "The returned buckets contain no positive transfer totals. Missing buckets and unknown"
          + " per-app energy prevent a conclusion about battery use.";
    return best == null
        ? "No readable per-UID transfer observations yet. Open app activity to check access and"
            + " refresh. Missing data is not zero activity."
        : displayName(best)
            + " · "
            + bytes(max)
            + " transferred in returned session-wide OS buckets. This does not prove that it caused"
            + " the battery change.";
  }

  String displayName(JSONObject row) {
    String pkg = row.optString("pkg");
    if (!pkg.contains(",") && !pkg.startsWith("System/"))
      try {
        ApplicationInfo info = a.getPackageManager().getApplicationInfo(pkg, 0);
        return a.getPackageManager().getApplicationLabel(info).toString();
      } catch (PackageManager.NameNotFoundException ignored) {
      }
    return pkg.isEmpty() ? "UID " + row.optInt("uid") : pkg;
  }

  void apps() {
    title("Overnight app activity", "Session transfers and foreground time · not energy");
    LinearLayout unavailable =
        card(
            "Exact per-app battery: unavailable",
            "Network data does not measure energy. Android’s own battery report may use a different"
                + " reporting period.");
    unavailable.addView(a.button("Open Android battery usage", this::batterySettings));
    JSONObject s = a.chosen();
    if (s == null) {
      card(
          "No session selected",
          "Record a session first. Your phone’s private app tasks are not inferred.");
      a.action("Start a night session", () -> a.navigate("Dashboard"));
      return;
    }
    a.note(
        "Session #"
            + s.optLong("id")
            + " · "
            + shortTime(s.optLong("start"))
            + " → "
            + (s.isNull("end") ? "now" : shortTime(s.optLong("end"))));
    LinearLayout access =
        card(
            AppActivity.allowed(a) ? "Usage access granted" : "Optional usage access",
            AppActivity.allowed(a)
                ? "Showing permitted OS buckets; records may be delayed or incomplete."
                : "Allow other-app records where Android permits. Battery monitoring also works"
                    + " without this access.");
    LinearLayout accessActions = new LinearLayout(a);
    accessActions.addView(
        a.button("Access settings", a::usageSettings), new LinearLayout.LayoutParams(0, -2, 1));
    accessActions.addView(new View(a), new LinearLayout.LayoutParams(a.dp(8), 1));
    accessActions.addView(
        a.button(a.busy ? "Reading…" : "Refresh", () -> refreshApps(s)),
        new LinearLayout.LayoutParams(0, -2, 1));
    access.addView(accessActions);
    info(
        access,
        "What these activity figures mean",
        () ->
            a.explain(
                "Activity coverage",
                "Network and foreground-time buckets are returned by Android for the session query."
                    + " They are not limited to screen-off periods and can be delayed or extend"
                    + " outside the query. Foreground time is not energy; no private job purpose,"
                    + " per-app CPU or wake-lock reading is available to this ordinary app."
                    + " Privileged imports stay labelled separately in History."));
    boolean byTime = a.prefs.getBoolean("sortTime", false);
    a.action(
        byTime ? "Foreground time ↓ · switch to data" : "Data transferred ↓ · switch to time",
        () -> {
          a.prefs.edit().putBoolean("sortTime", !byTime).apply();
          a.render();
        });
    JSONArray raw = a.db.apps(s.optLong("id"));
    ArrayList<JSONObject> list = new ArrayList<>();
    for (int i = 0; i < raw.length(); i++) list.add(raw.optJSONObject(i));
    list.sort((x, y) -> Double.compare(a.order(y, byTime), a.order(x, byTime)));
    if (list.isEmpty())
      card(
          "No permitted app observations",
          "A missing record does not mean zero activity. Check access and refresh; Android may"
              + " still restrict records.");
    double max = list.isEmpty() ? 0 : Math.max(0, a.order(list.get(0), byTime));
    int rank = 0;
    for (JSONObject row : list) {
      JSONObject data = Store.parse(row.optString("data"));
      double value = a.order(row, byTime);
      String amount =
          value < 0 ? "Unavailable" : byTime ? MainActivity.duration((long) value) : bytes(value);
      LinearLayout c =
          card((++rank) + ". " + displayName(row), "UID " + row.optInt("uid") + " · " + amount);
      if (value >= 0 && max > 0) {
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress((int) (1000 * value / max));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(blue));
        bar.setContentDescription(
            "Relative " + (byTime ? "foreground time" : "data transfer") + ": " + amount);
        c.addView(bar, new LinearLayout.LayoutParams(-1, a.dp(10)));
      }
      small(
          c,
          "Received "
              + bytes(Analysis.n(data, "rxBytes"))
              + " · sent "
              + bytes(Analysis.n(data, "txBytes"))
              + "\nForeground "
              + (data.isNull("foregroundMs")
                  ? "Unavailable"
                  : MainActivity.duration(data.optLong("foregroundMs"))));
      String reason = row.isNull("reason") ? "" : row.optString("reason");
      small(
          c,
          reason.isEmpty() || reason.equals("Unknown / clear")
              ? "Purpose: not known. No private tasks are detected."
              : "User-confirmed context: " + reason + " (not independently verified)");
      c.addView(a.button("Confirm a reason", () -> a.reason(row)));
      c.addView(
          a.button(
              "Sources & limitations",
              () ->
                  a.explain(
                      "Observation sources",
                      row.optString("pkg")
                          + "\n"
                          + data.optString("networkSource", "Network readings unavailable")
                          + "\n"
                          + data.optString("usageSource", "Foreground duration unavailable")
                          + "\n"
                          + "No per-app energy, CPU or wake-lock reading is available locally."
                          + " Imported metrics stay separate in History.")));
    }
    LinearLayout purpose =
        card(
            "Possible explanations · not verified",
            "Sync · backup · update/download · alarms. These are common possibilities, not tasks"
                + " DevicePulse has detected. Check the app’s settings or logs before confirming a"
                + " reason.");
    small(
        purpose,
        "High data transfer alone cannot establish battery drain, necessity or suspicious"
            + " behavior.");
  }

  void refreshApps(JSONObject s) {
    if (a.busy) return;
    a.busy = true;
    long id = s.optLong("id"),
        start = s.optLong("start"),
        end = s.isNull("end") ? System.currentTimeMillis() : s.optLong("end");
    new Thread(
            () -> {
              try (Store db = new Store(a)) {
                db.saveApps(id, AppActivity.collect(a, start, end, db, id));
              } finally {
                a.runOnUiThread(
                    () -> {
                      a.busy = false;
                      if (!a.isFinishing() && !a.isDestroyed() && a.page.equals("Activity"))
                        a.render();
                    });
              }
            })
        .start();
    a.render();
  }

  void stop() {
    new AlertDialog.Builder(a)
        .setTitle("Stop monitoring?")
        .setMessage(
            "Recorded observations remain available; this will not be labelled a completed target"
                + " session.")
        .setPositiveButton(
            "Stop",
            (d, w) -> {
              a.startService(new Intent(a, MonitorService.class).setAction("stop"));
              a.h.postDelayed(a::render, 500);
            })
        .setNegativeButton("Keep monitoring", null)
        .show();
  }

  void batterySettings() {
    try {
      a.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
    } catch (android.content.ActivityNotFoundException | SecurityException missing) {
      a.explain(
          "Android battery usage",
          "This device does not expose a battery-usage screen to this app. In Android Settings,"
              + " look for Battery → Battery usage; names vary by manufacturer. DevicePulse cannot"
              + " read that private report.");
      a.safe(new Intent(Settings.ACTION_SETTINGS));
    }
  }

  void limits() {
    a.explain(
        "Overnight monitoring limits",
        "6h/7h are target windows, not guaranteed coverage. Android may defer samples or terminate"
            + " the process during sleep. No wake lock or exact alarm is used. Missing intervals"
            + " stay unknown. Screen off is not proof that nobody touched the phone. Battery loss"
            + " is whole-device; returned app network buckets and foreground time do not measure"
            + " energy or reveal private job purposes. Reboot/process interruptions are recorded"
            + " where observable. Physical overnight accuracy and overhead still need device"
            + " testing.");
  }
}
