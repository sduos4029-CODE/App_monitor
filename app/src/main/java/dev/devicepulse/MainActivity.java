package dev.devicepulse;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
  Store db;
  android.content.SharedPreferences prefs;
  LinearLayout root, body, nav;
  String page = "Dashboard", format;
  long selected = -1;
  JSONObject exportReport;
  int ink, muted, bg, card;
  boolean dark;
  SensorManager sensors;
  SensorEventListener sensorListener;
  Handler h = new Handler();
  int pendingHours = 6;
  boolean busy;
  final Runnable refresh =
      new Runnable() {
        public void run() {
          if (page.equals("Dashboard") || page.equals("Overnight") || page.equals("Components"))
            render();
          h.postDelayed(this, 30000);
        }
      };

  public void onCreate(Bundle b) {
    super.onCreate(b);
    db = new Store(this);
    prefs = getSharedPreferences("settings", 0);
    if (!MonitorService.running) db.recover();
    if (b != null) {
      page = b.getString("page", "Dashboard");
      selected = b.getLong("selected", -1);
      format = b.getString("format");
      pendingHours = b.getInt("pendingHours", 6);
      String saved = b.getString("exportReport");
      if (saved != null) exportReport = Store.parse(saved);
    }
    sensors = getSystemService(SensorManager.class);
    render();
  }

  protected void onSaveInstanceState(Bundle b) {
    b.putString("page", page);
    b.putLong("selected", selected);
    b.putString("format", format);
    b.putInt("pendingHours", pendingHours);
    if (exportReport != null) b.putString("exportReport", exportReport.toString());
    super.onSaveInstanceState(b);
  }

  protected void onResume() {
    super.onResume();
    h.postDelayed(refresh, 30000);
    if (db != null) {
      org.json.JSONObject active = db.active();
      if (active != null
          && MonitorService.running
          && System.currentTimeMillis() >= active.optLong("planned"))
        startService(new Intent(this, MonitorService.class).setAction("check"));
      render();
    }
  }

  protected void onPause() {
    h.removeCallbacks(refresh);
    stopSensors();
    super.onPause();
  }

  protected void onDestroy() {
    db.close();
    super.onDestroy();
  }

  int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  static String time(long t) {
    return t <= 0
        ? "Unavailable"
        : new SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(new Date(t));
  }

  static String duration(long ms) {
    return String.format(
        Locale.US, "%dh %02dm %02ds", ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60);
  }

  TextView text(String s, int size, int color) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setPadding(dp(4), dp(6), dp(4), dp(6));
    v.setTextIsSelectable(true);
    return v;
  }

  void note(String s) {
    body.addView(text(s, 14, muted));
  }

  void title(String s) {
    body.addView(text(s, 23, ink));
  }

  GradientDrawable shape(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(16));
    return d;
  }

  LinearLayout box(String title, String detail) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(12), dp(7), dp(12), dp(9));
    c.setBackground(shape(card));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(5), 0, dp(5));
    body.addView(c, lp);
    c.addView(text(title, 18, ink));
    c.addView(text(detail, 14, muted));
    return c;
  }

  Button button(String s, Runnable action) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33ffffff),
            shape(dark ? 0xff0875d1 : 0xff156bd0),
            null));
    b.setPadding(dp(12), dp(6), dp(12), dp(6));
    LinearLayout.LayoutParams defaultButton = new LinearLayout.LayoutParams(-1, -2);
    defaultButton.setMargins(0, dp(4), 0, dp(4));
    b.setLayoutParams(defaultButton);
    b.setMinHeight(dp(48));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  void action(String s, Runnable r) {
    body.addView(button(s, r));
  }

  void navigate(String p) {
    stopSensors();
    page = p;
    render();
  }

  void render() {
    if (isFinishing() || isDestroyed()) return;
    dark = prefs.getBoolean("dark", true);
    ink = dark ? 0xfff0f5ff : 0xff152338;
    muted = dark ? 0xffb6c5d9 : 0xff42546c;
    bg = dark ? 0xff0b121c : 0xfff1f5f9;
    card = dark ? 0xff172332 : Color.WHITE;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(bg);
    root.setPadding(dp(12), 0, dp(12), 0);
    root.setFitsSystemWindows(true);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(12), i.top, dp(12), i.bottom);
          }
          return insets;
        });
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView brand = text("DevicePulse", 23, ink);
    brand.setTypeface(null, android.graphics.Typeface.BOLD);
    header.addView(brand, new LinearLayout.LayoutParams(0, dp(58), 1));
    FrameLayout settingsButton = new FrameLayout(this);
    PulseIcon settingsIcon = new PulseIcon(this, "Settings", 0xff42acff);
    FrameLayout.LayoutParams iconSize =
        new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER);
    settingsButton.addView(settingsIcon, iconSize);
    settingsButton.setBackground(shape(card));
    settingsButton.setFocusable(true);
    settingsButton.setClickable(true);
    settingsButton.setContentDescription("Settings");
    settingsButton.setOnClickListener(v -> navigate("Settings"));
    header.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(0, dp(8), 0, dp(24));
    scroll.addView(body);
    switch (page) {
      case "Dashboard":
        dashboard();
        break;
      case "Overnight":
        new OvernightScreen(this).report();
        break;
      case "Activity":
        activity();
        break;
      case "Components":
        components();
        break;
      case "History":
        history();
        break;
      case "Findings":
        findings();
        break;
      case "Log":
        log();
        break;
      default:
        settings();
    }
    new OvernightScreen(this).navigation();
  }

  JSONObject chosen() {
    if (selected > 0) {
      JSONObject s = db.session(selected);
      if (s != null) return s;
    }
    JSONArray list = db.sessions();
    return list.length() == 0 ? null : list.optJSONObject(0);
  }

  void dashboard() {
    new OvernightScreen(this).home();
  }

  void start(int hours) {
    if (MonitorService.running || db.active() != null) return;
    pendingHours = hours;
    new AlertDialog.Builder(this)
        .setTitle(hours + "-hour monitoring session")
        .setMessage(
            "Leave the phone unplugged with the screen off. Uses a foreground service and local"
                + " storage. Samples every "
                + prefs.getInt("interval", 5)
                + " minutes."
                + " Android may defer samples while asleep. Notification permission is optional; it"
                + " does not grant usage access. No battery or account data leaves this device.")
        .setPositiveButton(
            "Start",
            (d, w) -> {
              if (Build.VERSION.SDK_INT >= 33
                  && !Probe.permission(this, Manifest.permission.POST_NOTIFICATIONS))
                requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 11);
              else begin();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void begin() {
    if (db.active() != null) return;
    selected = db.start(pendingHours == 7 ? 7 : 6, prefs.getInt("interval", 5));
    page = "Overnight";
    try {
      startForegroundService(new Intent(this, MonitorService.class));
    } catch (RuntimeException e) {
      db.recover();
      explain("Start blocked", e.toString());
    }
    h.postDelayed(() -> render(), 500);
  }

  void summary(JSONObject s) {
    long id = s.optLong("id");
    JSONObject sum = Analysis.summary(db, id);
    box(
        "Session #" + id + " · " + s.optString("state"),
        "Start "
            + time(s.optLong("start"))
            + "\nEnd "
            + (s.isNull("end") ? "still active" : time(s.optLong("end")))
            + "\nTarget "
            + time(s.optLong("planned"))
            + "\n"
            + sum.optInt("sampleCount")
            + " samples · "
            + sum.optInt("gapCount")
            + " internal gaps\nObserved intervals "
            + duration(sum.optLong("coveredMs"))
            + "\nScreen-off + unplugged "
            + duration(sum.optLong("screenOffUnpluggedMs"))
            + "\nBattery net change "
            + Logic.value(Analysis.n(sum, "netChangePctPoints"), "percentage points")
            + "\nDerived full-span drain "
            + Logic.value(Analysis.n(sum, "drainPctPerHour"), "percentage points/h")
            + "\nEstimated screen-off/unplugged drain "
            + Logic.value(
                Analysis.n(sum, "screenOffDrainPctPerHourEstimated"), "percentage points/h")
            + "\nBattery temperature min/max "
            + Logic.value(Analysis.n(sum, "minC"), "°C")
            + " / "
            + Logic.value(Analysis.n(sum, "maxC"), "°C"));
    note(
        "Coverage uses adjacent sample intervals without known gaps; both boundaries must be"
            + " screen-off/unplugged for that subset. It estimates state between samples and"
            + " excludes missing time. Charging can invalidate drain interpretation. Negative net"
            + " change means loss; derived drain is whole-device, never attributed to an app.");
  }

  void history() {
    title("Recorded sessions");
    box("7-day comparison", Analysis.compare(db, 7));
    box("30-day comparison", Analysis.compare(db, 30));
    JSONArray list = db.sessions();
    if (list.length() == 0) {
      note("History is empty.");
      return;
    }
    for (int i = 0; i < list.length(); i++) {
      JSONObject r = list.optJSONObject(i);
      action(
          "#" + r.optLong("id") + " " + time(r.optLong("start")) + " · " + r.optString("state"),
          () -> {
            selected = r.optLong("id");
            render();
          });
    }
    JSONObject s = chosen();
    summary(s);
    JSONArray records = db.samples(s.optLong("id"));
    Graph battery = new Graph(this, "Battery timeline", "%");
    battery.timeline(records, "batteryPct", s.optLong("interval"));
    body.addView(battery, new LinearLayout.LayoutParams(-1, dp(210)));
    Graph temp = new Graph(this, "Battery temperature", "°C");
    temp.timeline(records, "tempC", s.optLong("interval"));
    body.addView(temp, new LinearLayout.LayoutParams(-1, dp(210)));
    note(
        "Dots are samples; lines stop at missing/delayed intervals. No interpolation across"
            + " detected gaps. Export for exact timestamps and component-status history.");
    for (String f : new String[] {"csv", "json", "pdf"})
      action("Export " + f.toUpperCase(Locale.ROOT), () -> export(f));
    action("Import privileged collector metrics…", this::importPicker);
    action(
        "Component-status history",
        () -> {
          page = "Log";
          render();
        });
    JSONArray imports =
        db.query("SELECT * FROM imports WHERE session=?", new String[] {"" + s.optLong("id")});
    for (int i = 0; i < imports.length(); i++)
      box("Imported · not verified by Android", imports.optJSONObject(i).optString("data"));
  }

  void activity() {
    new OvernightScreen(this).apps();
  }

  double order(JSONObject r, boolean byTime) {
    JSONObject d = Store.parse(r.optString("data"));
    if (byTime) return d.isNull("foregroundMs") ? -1 : d.optDouble("foregroundMs");
    return d.isNull("rxBytes") || d.isNull("txBytes")
        ? -1
        : d.optDouble("rxBytes") + d.optDouble("txBytes");
  }

  void reason(JSONObject r) {
    String[] choices = {"Sync", "Backup", "Update", "Alarm", "User activity", "Clear confirmation"};
    new AlertDialog.Builder(this)
        .setTitle("Your confirmed context — not a diagnosis")
        .setItems(
            choices,
            (d, w) -> {
              db.reason(r.optLong("id"), w == choices.length - 1 ? null : choices[w]);
              render();
            })
        .show();
  }

  void components() {
    title("Device components");
    JSONObject o = Probe.take(this);
    box(
        "Battery · measured",
        Logic.value(Analysis.n(o, "batteryPct"), "%")
            + " · "
            + o.optString("chargingState")
            + " · "
            + o.optString("chargingSource")
            + "\nVoltage "
            + Logic.value(Analysis.n(o, "voltageMv"), "mV")
            + "\nCurrent "
            + Logic.value(Analysis.n(o, "currentUa"), "µA")
            + " (sign/accuracy device-dependent)\nHealth "
            + o.optString("health"));
    box(
        "Temperature & thermal state",
        Logic.value(Analysis.n(o, "tempC"), "°C battery")
            + "\nThermal status "
            + (o.isNull("thermalStatus")
                ? "Unsupported before Android 10"
                : thermal(o.optInt("thermalStatus")))
            + "\n"
            + "CPU/GPU temperature: restricted/unavailable\n"
            + "Session min/max and threshold alerts are in History / Settings.");
    box(
        "CPU",
        Probe.cpu()
            + "\nAccessible core-0 frequency: "
            + (o.isNull("cpuFrequencyKhz")
                ? "restricted/unavailable"
                : o.optString("cpuFrequencyKhz") + " kHz")
            + "\nThis app CPU: "
            + Logic.value(Analysis.n(o, "ownCpuPctOneCore"), "% of one core since prior probe"));
    box("Memory", capacity(o, "memoryTotalBytes", "memoryAvailableBytes"));
    box("Storage · app data volume", capacity(o, "storageTotalBytes", "storageAvailableBytes"));
    box(
        "Display",
        "Interactive (not a guarantee of panel power): "
            + o.optBoolean("screenOn")
            + "\nBrightness setting "
            + (o.isNull("brightnessSetting")
                ? "Unavailable"
                : o.optInt("brightnessSetting") + " / 255 (not nits)")
            + "\nRefresh "
            + Logic.value(Analysis.n(o, "refreshHz"), "Hz"));
    JSONObject components = o.optJSONObject("components");
    Iterator<String> names = components.keys();
    while (names.hasNext()) {
      String name = names.next();
      JSONObject detail = components.optJSONObject(name);
      box(
          name + " · " + detail.optString("state"),
          detail.isNull("value") ? "Unavailable" : detail.optString("value"));
    }
    action(
        "Bluetooth details permission",
        () -> {
          if (Build.VERSION.SDK_INT >= 31
              && !Probe.permission(this, Manifest.permission.BLUETOOTH_CONNECT))
            new AlertDialog.Builder(this)
                .setTitle("Nearby-device access")
                .setMessage(
                    "Used only to read Bluetooth enabled state, paired devices and headset/audio"
                        + " profile state. No scans or connections.")
                .setPositiveButton(
                    "Continue",
                    (d, w) ->
                        requestPermissions(
                            new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 12))
                .setNegativeButton("Cancel", null)
                .show();
          else explain("Bluetooth", "Details above use permitted Bluetooth APIs.");
        });
    action(
        "Wi-Fi network-name permission",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Optional precise location permission")
                .setMessage(
                    "Android protects Wi-Fi network names behind location permission and service"
                        + " state on many versions. DevicePulse reads no coordinates and does not"
                        + " request background location. The OS may still redact SSID.")
                .setPositiveButton(
                    "Continue",
                    (d, w) ->
                        requestPermissions(
                            new String[] {
                              Manifest.permission.ACCESS_FINE_LOCATION,
                              Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            13))
                .setNegativeButton("Cancel", null)
                .show());
    action("Sensor inventory & optional live reading", this::sensorInventory);
    action("Refresh components", () -> render());
    note(
        "Hardware availability does not mean a component is currently in use. No continuous camera,"
            + " microphone, radio or sensor probing occurs overnight.");
  }

  static String thermal(int s) {
    String[] t = {"None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown"};
    return s >= 0 && s < t.length ? t[s] + " (" + s + ")" : "Unknown (" + s + ")";
  }

  String capacity(JSONObject o, String total, String available) {
    long t = o.optLong(total), a = o.optLong(available);
    return String.format(
        Locale.US,
        "Total %.2f GB\nAvailable %.2f GB\nUsed %.2f GB",
        t / 1e9,
        a / 1e9,
        (t - a) / 1e9);
  }

  void sensorInventory() {
    stopSensors();
    SensorManager sm = sensors;
    List<Sensor> list = sm.getSensorList(Sensor.TYPE_ALL);
    String[] labels = new String[list.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = list.get(i).getName() + " (type " + list.get(i).getType() + ")";
    if (labels.length == 0) {
      explain("Sensors", "Unsupported: no sensors exposed.");
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Sensors — select for details")
        .setItems(labels, (d, w) -> sensor(list.get(w)))
        .setNegativeButton("Close", null)
        .show();
  }

  void sensor(Sensor s) {
    TextView t =
        text(
            "Type "
                + s.getType()
                + " · "
                + s.getStringType()
                + "\nName "
                + s.getName()
                + "\nVendor "
                + s.getVendor()
                + "\nRange "
                + s.getMaximumRange()
                + " (sensor-specific SI units)\nResolution "
                + s.getResolution()
                + "\nManufacturer power rating "
                + s.getPower()
                + " mA, not measured consumption\n"
                + "Live readings are opt-in, 15 seconds only. Protected/body sensors may require"
                + " permissions; no sensitive sensor permission is requested.",
            15,
            ink);
    t.setPadding(dp(18), dp(14), dp(18), dp(14));
    ScrollView sensorScroll = new ScrollView(this);
    sensorScroll.addView(t);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Sensor details")
            .setView(sensorScroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Read for 15 seconds", null)
            .create();
    dialog.setOnDismissListener(d -> stopSensors());
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(
                    v -> {
                      stopSensors();
                      String base = t.getText().toString().split("\nLive:")[0];
                      sensorListener =
                          new SensorEventListener() {
                            public void onAccuracyChanged(Sensor x, int a) {}

                            public void onSensorChanged(SensorEvent e) {
                              t.setText(
                                  base
                                      + "\nLive: "
                                      + Arrays.toString(e.values)
                                      + "\nTimestamp "
                                      + e.timestamp
                                      + " ns since boot · accuracy "
                                      + e.accuracy);
                            }
                          };
                      try {
                        boolean ok =
                            sensors.registerListener(
                                sensorListener, s, SensorManager.SENSOR_DELAY_NORMAL);
                        if (!ok) t.setText(base + "\nLive: unsupported/restricted");
                      } catch (SecurityException ex) {
                        t.setText(base + "\nLive: permission-required/restricted");
                      }
                      h.postDelayed(
                          () -> {
                            stopSensors();
                            t.append("\nLive reading stopped.");
                          },
                          15000);
                    }));
    dialog.show();
  }

  void stopSensors() {
    if (sensorListener != null) {
      sensors.unregisterListener(sensorListener);
      sensorListener = null;
    }
  }

  void findings() {
    title("Review findings");
    note(
        "Thresholds are review prompts, never a malware diagnosis. Battery drain is derived for the"
            + " whole device, not per-app attribution.");
    box(
        "Completed-session findings",
        Analysis.findings(
            db,
            prefs.getFloat("temperature", 40),
            prefs.getFloat("drain", 5),
            prefs.getFloat("transfer", 100)));
    action("Change thresholds", () -> navigate("Settings"));
  }

  void log() {
    title("Searchable activity & component history");
    EditText q = new EditText(this);
    q.setSingleLine(true);
    q.setTextColor(ink);
    q.setHintTextColor(muted);
    q.setHint("Search events, timestamps, components");
    body.addView(q);
    LinearLayout results = new LinearLayout(this);
    results.setOrientation(LinearLayout.VERTICAL);
    body.addView(results);
    JSONObject s = chosen();
    JSONArray events = db.events(s == null ? -1 : s.optLong("id")),
        samples = s == null ? new JSONArray() : db.samples(s.optLong("id"));
    Runnable draw =
        () -> {
          results.removeAllViews();
          String query = q.getText().toString().toLowerCase(Locale.ROOT);
          int count = 0;
          for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            String line =
                time(e.optLong("ts")) + " · " + e.optString("kind") + "\n" + e.optString("detail");
            if (line.toLowerCase(Locale.ROOT).contains(query)) {
              results.addView(text(line, 14, ink));
              count++;
            }
          }
          for (int i = samples.length() - 1; i >= 0; i--) {
            JSONObject e = samples.optJSONObject(i);
            String line = time(e.optLong("ts")) + " · sample\n" + e.optString("data");
            if (line.toLowerCase(Locale.ROOT).contains(query)) {
              results.addView(text(line, 13, muted));
              count++;
            }
          }
          if (count == 0) results.addView(text("No matching recorded data.", 14, muted));
        };
    q.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            draw.run();
          }

          public void afterTextChanged(Editable e) {}
        });
    draw.run();
  }

  void settings() {
    title("Settings & device tools");
    action("Device components", () -> navigate("Components"));
    action("Review findings", () -> navigate("Findings"));
    action("Searchable activity log", () -> navigate("Log"));
    action("Android battery usage", () -> new OvernightScreen(this).batterySettings());
    action(
        dark ? "Use light mode" : "Use dark mode",
        () -> {
          prefs.edit().putBoolean("dark", !dark).apply();
          render();
        });
    action(
        "Sampling interval: " + prefs.getInt("interval", 5) + " minutes",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("New sessions only")
                .setItems(
                    new String[] {
                      "1 minute · more overhead",
                      "5 minutes · default",
                      "15 minutes · lower overhead"
                    },
                    (d, w) -> {
                      prefs.edit().putInt("interval", new int[] {1, 5, 15}[w]).apply();
                      render();
                    })
                .show());
    threshold("temperature", "Temperature threshold (°C)", 40, 10, 90);
    threshold("drain", "Whole-device drain (percentage points/hour)", 5, 0.1f, 100);
    threshold("transfer", "Per-UID data transfer (decimal MB/session)", 100, 0.01f, 1000000);
    action("Usage access settings", this::usageSettings);
    action(
        "Application permissions & notifications",
        () ->
            safe(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    action(
        "Battery optimization settings",
        () -> safe(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
    action(
        "Restricted settings & security",
        () ->
            explain(
                "Android restricted settings",
                "On some sideloaded installations Android blocks sensitive special access. If you"
                    + " trust this build, open Android Settings → Apps → DevicePulse → ⋮ → Allow"
                    + " restricted settings where that option exists, then return to Usage access."
                    + " OEM labels differ. DevicePulse cannot grant this itself. This app does not"
                    + " use accessibility services, root or notification-listener access. Keep Play"
                    + " Protect enabled. No installation approval is promised."));
    action(
        "Delete all local monitoring data…",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Delete local history?")
                .setMessage(
                    "Stop any active session first. Exported documents outside this app are not"
                        + " deleted.")
                .setPositiveButton(
                    "Delete",
                    (d, w) -> {
                      if (MonitorService.running) {
                        explain("Session active", "Stop monitoring before deletion.");
                        return;
                      }
                      db.erase();
                      selected = -1;
                      render();
                    })
                .setNegativeButton("Cancel", null)
                .show());
    note(
        "No INTERNET permission, tracking SDKs, ads, cloud sync or automatic sensitive permission"
            + " requests. Data stays in the app-private SQLite database. Android may retain"
            + " user-exported documents. DevicePulse is a diagnostic tool, not a security"
            + " verdict.");
  }

  void threshold(String key, String label, float def, float min, float max) {
    action(
        label + ": " + prefs.getFloat(key, def),
        () -> {
          EditText input = new EditText(this);
          input.setInputType(8194);
          input.setText("" + prefs.getFloat(key, def));
          new AlertDialog.Builder(this)
              .setTitle(label)
              .setView(input)
              .setPositiveButton(
                  "Save",
                  (d, w) -> {
                    try {
                      float v = Float.parseFloat(input.getText().toString());
                      if (!Float.isFinite(v) || v < min || v > max)
                        throw new NumberFormatException();
                      prefs.edit().putFloat(key, v).apply();
                      render();
                    } catch (Exception ex) {
                      explain("Invalid threshold", "Enter a number between " + min + " and " + max);
                    }
                  })
              .setNegativeButton("Cancel", null)
              .show();
        });
  }

  void usageSettings() {
    new AlertDialog.Builder(this)
        .setTitle("Optional usage access")
        .setMessage(
            "Allows Android usage statistics and permitted per-UID network buckets. It does not"
                + " expose per-app energy, CPU or wake locks. You can continue without it.")
        .setPositiveButton(
            "Open settings",
            (d, w) ->
                safe(
                    new Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .setNegativeButton("Cancel", null)
        .show();
  }

  void explain(String title, String message) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show();
  }

  void safe(Intent i) {
    try {
      startActivity(i);
    } catch (ActivityNotFoundException | SecurityException e) {
      explain("Unavailable", "This device does not expose the requested settings/action.");
    }
  }

  void export(String f) {
    JSONObject s = chosen();
    if (s == null) {
      explain("No session", "Record a session before exporting.");
      return;
    }
    format = f;
    exportReport = db.report(s.optLong("id"));
    Store.put(exportReport, "summary", Analysis.summary(db, s.optLong("id")));
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(
                f.equals("pdf")
                    ? "application/pdf"
                    : f.equals("json") ? "application/json" : "text/csv")
            .putExtra(Intent.EXTRA_TITLE, "DevicePulse-" + s.optLong("id") + "." + f);
    startActivityForResult(i, 21);
  }

  void importPicker() {
    JSONObject s = chosen();
    if (s == null) return;
    new AlertDialog.Builder(this)
        .setTitle("Import privileged metrics")
        .setMessage(
            "Only import a JSON report from your own authorized collector. DevicePulse validates"
                + " format and interval, not collector truth or device identity. Imported values"
                + " stay labelled and separate from real alerts. Format and example are in source"
                + " docs. No root required by DevicePulse.")
        .setPositiveButton(
            "Choose JSON",
            (d, w) ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json"),
                    22))
        .setNegativeButton("Cancel", null)
        .show();
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    try {
      if (request == 21) {
        if (exportReport == null || format == null)
          throw new IOException("Export context lost; retry export");
        Reports.write(this, uri, format, exportReport);
        String mime =
            format.equals("pdf")
                ? "application/pdf"
                : format.equals("json") ? "application/json" : "text/csv";
        new AlertDialog.Builder(this)
            .setTitle("Report saved")
            .setMessage("Includes units, timestamps, sources and coverage limits.")
            .setPositiveButton(
                "Share",
                (d, w) -> {
                  Intent i =
                      new Intent(Intent.ACTION_SEND)
                          .setType(mime)
                          .putExtra(Intent.EXTRA_STREAM, uri)
                          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  i.setClipData(ClipData.newRawUri("DevicePulse report", uri));
                  safe(Intent.createChooser(i, "Share local report"));
                })
            .setNegativeButton("Done", null)
            .show();
      }
      if (request == 22) {
        JSONObject s = chosen();
        if (s == null) throw new IllegalArgumentException("No selected session");
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
          if (in == null) throw new IOException("No import stream");
          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          byte[] block = new byte[8192];
          int count;
          while ((count = in.read(block)) != -1) {
            buffer.write(block, 0, count);
            if (buffer.size() > 1024 * 1024) break;
          }
          bytes = buffer.toByteArray();
        }
        if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("Import exceeds 1 MiB");
        JSONObject imported =
            Reports.validateImport(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), s);
        new AlertDialog.Builder(this)
            .setTitle("Confirm imported data")
            .setMessage(
                "Collector: "
                    + imported.optString("collector")
                    + "\nDevice claimed: "
                    + imported.optString("device")
                    + "\n"
                    + imported.optJSONArray("metrics").length()
                    + " UID rows. You confirm this report belongs to the selected device/session."
                    + " Values are imported, not measured by DevicePulse.")
            .setPositiveButton(
                "Import",
                (d, w) -> {
                  android.content.ContentValues v = new android.content.ContentValues();
                  v.put("session", s.optLong("id"));
                  v.put("ts", System.currentTimeMillis());
                  v.put("data", imported.toString());
                  db.getWritableDatabase().insertOrThrow("imports", null, v);
                  db.event(
                      s.optLong("id"),
                      "import",
                      "User confirmed privileged collector import; truth/device match not"
                          + " independently verified");
                  render();
                })
            .setNegativeButton("Cancel", null)
            .show();
      }
    } catch (Exception e) {
      explain("Operation failed", e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  public void onRequestPermissionsResult(int r, String[] p, int[] results) {
    super.onRequestPermissionsResult(r, p, results);
    if (r == 11) begin();
    else render();
  }
}
