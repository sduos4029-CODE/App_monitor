package dev.devicepulse;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Foreground-only dashboard. Updates existing views so scrolling and focus remain stable. */
final class ComponentDashboard {
  final MainActivity a;
  final int blue = 0xff65baff, mint = 0xff61dbc1, purple = 0xffc2abff, orange = 0xffffc178;
  final Map<String, Tile> tiles = new LinkedHashMap<>();
  final Map<String, TextView> statuses = new LinkedHashMap<>();
  TextView stamp, thermal, cpuDetail, displayDetail;
  JSONObject latest;
  int updates;
  final String cpuInfo = Probe.cpu();

  ComponentDashboard(MainActivity a) {
    this.a = a;
  }

  void build() {
    a.title("Device dashboard");
    a.note(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
    stamp = a.text("Reading device…", 12, a.muted);
    a.body.addView(stamp);
    LinearLayout top = row();
    tile(top, "Battery", "battery", mint);
    tile(top, "Temperature", "temperature", orange);
    LinearLayout capacity = row();
    tile(capacity, "Memory", "memory", blue);
    tile(capacity, "Storage", "storage", purple);
    thermal = details("Thermal status", "");
    LinearLayout computing = panel("Processor & display", blue);
    cpuDetail = a.text("", 14, a.ink);
    computing.addView(cpuDetail);
    displayDetail = a.text("", 14, a.muted);
    computing.addView(displayDetail);
    computing.addView(
        a.button(
            "Processor details",
            () ->
                a.explain(
                    "Processor",
                    cpuInfo
                        + "\n"
                        + "Accessible frequency is core 0 only, not an average. Own-process CPU is"
                        + " a time delta, not device-wide utilization.")));
    LinearLayout connectivity = panel("Connections", mint);
    for (String name :
        new String[] {"Connectivity", "Wi-Fi", "Network name", "Bluetooth", "Mobile"})
      status(connectivity, name);
    LinearLayout other = panel("Other components", purple);
    for (String name : new String[] {"Location service", "NFC", "Audio", "Camera", "Microphone"})
      status(other, name);
    int count = a.sensors.getSensorList(android.hardware.Sensor.TYPE_ALL).size();
    a.box(
        "Sensors · " + count + " exposed",
        "Inventory available below. Live sensor readings are opt-in for 15 seconds; sensors stay"
            + " idle otherwise.");
    a.note(
        "Checked every 2 seconds while this screen is visible. Values change only when Android"
            + " reports a change. Overnight recording keeps its separate, slower interval. Tap a"
            + " connection row for source and access details.");
    update();
  }

  LinearLayout panel(String title, int accent) {
    LinearLayout c = a.box(title, "");
    c.removeViewAt(1);
    GradientDrawable bg = a.shape(a.card);
    bg.setStroke(a.dp(1), a.dark ? 0xff2b3b50 : 0xffcedbe9);
    c.setBackground(bg);
    return c;
  }

  TextView details(String title, String value) {
    LinearLayout c = panel(title, mint);
    TextView text = a.text(value, 14, a.muted);
    c.addView(text);
    return text;
  }

  LinearLayout row() {
    LinearLayout row = new LinearLayout(a);
    a.body.addView(row, new LinearLayout.LayoutParams(-1, -2));
    return row;
  }

  void tile(LinearLayout row, String title, String key, int color) {
    Tile tile = new Tile(title, color);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
    lp.setMargins(a.dp(3), a.dp(5), a.dp(3), a.dp(5));
    row.addView(tile, lp);
    tiles.put(key, tile);
  }

  void status(LinearLayout panel, String name) {
    TextView line = a.text("", 14, a.ink);
    line.setPadding(a.dp(2), a.dp(12), a.dp(2), a.dp(12));
    line.setTextIsSelectable(false);
    line.setFocusable(true);
    line.setClickable(true);
    line.setContentDescription(name + " · open details");
    line.setOnClickListener(
        v -> {
          JSONObject components = latest == null ? null : latest.optJSONObject("components");
          JSONObject d = components == null ? null : components.optJSONObject(name);
          a.explain(
              name,
              d == null
                  ? "Unavailable"
                  : d.optString("state")
                      + "\n"
                      + (d.isNull("value") ? "Unavailable" : d.optString("value"))
                      + "\nSource: "
                      + d.optString("source")
                      + "\nChecked: "
                      + MainActivity.time(latest.optLong("capturedAt")));
        });
    panel.addView(line);
    View divider = new View(a);
    divider.setBackgroundColor(a.dark ? 0xff2a394c : 0xffdbe5ef);
    panel.addView(divider, new LinearLayout.LayoutParams(-1, a.dp(1)));
    statuses.put(name, line);
  }

  static String shortState(JSONObject d) {
    if (d == null) return "Unavailable";
    if (!"measured".equals(d.optString("state"))) return d.optString("state", "Unavailable");
    String value = d.isNull("value") ? "Unavailable" : d.optString("value");
    int split = value.indexOf(" · ");
    return split < 0 ? value : value.substring(0, split);
  }

  void update() {
    latest = Probe.take(a);
    updates++;
    stamp.setText("● LIVE CHECK  ·  " + MainActivity.time(latest.optLong("capturedAt")));
    Tile battery = tiles.get("battery");
    Double level = Analysis.n(latest, "batteryPct");
    battery.set(
        value(level, "%"),
        latest.optString("chargingState") + " · " + latest.optString("chargingSource"),
        level);
    battery.setOnClickListener(
        v ->
            a.explain(
                "Battery · Android API",
                "Health: "
                    + latest.optString("health")
                    + "\nVoltage: "
                    + value(Analysis.n(latest, "voltageMv"), " mV")
                    + "\nCurrent: "
                    + value(Analysis.n(latest, "currentUa"), " µA")
                    + "\n"
                    + "Current sign and accuracy are device-dependent; unavailable properties"
                    + " remain unavailable."));
    tiles
        .get("temperature")
        .set(
            value(Analysis.n(latest, "tempC"), "°C"),
            "Battery sensor\nCPU / GPU unavailable",
            null);
    capacity("memory", "memoryTotalBytes", "memoryAvailableBytes", "RAM in use");
    capacity("storage", "storageTotalBytes", "storageAvailableBytes", "App data volume");
    thermal.setText(
        (latest.isNull("thermalStatus")
                ? "Unsupported on this Android version"
                : MainActivity.thermal(latest.optInt("thermalStatus")))
            + " · "
            + (latest.optBoolean("powerSave") ? "Battery saver on" : "Battery saver off"));
    cpuDetail.setText(
        Runtime.getRuntime().availableProcessors()
            + " logical processors  ·  "
            + (latest.isNull("cpuFrequencyKhz")
                ? "Frequency unavailable"
                : latest.optString("cpuFrequencyKhz") + " kHz · core 0")
            + "\nDevicePulse CPU: "
            + value(Analysis.n(latest, "ownCpuPctOneCore"), "% of one core")
            + "\nDevice-wide CPU: restricted / unavailable");
    displayDetail.setText(
        "Display "
            + (latest.optBoolean("screenOn") ? "interactive" : "not interactive")
            + "  ·  "
            + value(Analysis.n(latest, "refreshHz"), " Hz")
            + "\nBrightness setting: "
            + (latest.isNull("brightnessSetting")
                ? "Unavailable"
                : latest.optInt("brightnessSetting") + " / 255 · not nits"));
    JSONObject components = latest.optJSONObject("components");
    for (Map.Entry<String, TextView> entry : statuses.entrySet()) {
      JSONObject d = components == null ? null : components.optJSONObject(entry.getKey());
      String summary = shortState(d);
      if (entry.getKey().equals("Location service"))
        summary =
            "true".equals(summary) ? "Enabled" : "false".equals(summary) ? "Disabled" : summary;
      if (entry.getKey().equals("Wi-Fi") && d != null && !d.isNull("value")) {
        String[] parts = d.optString("value").split(" · ");
        summary = parts.length > 1 ? parts[0] + " · " + parts[1] : summary;
      }
      entry.getValue().setText(entry.getKey() + "   ›\n" + summary);
    }
  }

  void capacity(String tile, String totalKey, String freeKey, String caption) {
    Double total = Analysis.n(latest, totalKey), free = Analysis.n(latest, freeKey);
    if (total == null || free == null || total <= 0 || free < 0 || free > total) {
      tiles.get(tile).set("Unavailable", caption, null);
      return;
    }
    double used = total - free;
    tiles
        .get(tile)
        .set(
            String.format(Locale.getDefault(), "%.0f%%", used * 100 / total),
            caption + "\n" + OvernightScreen.bytes(used) + " / " + OvernightScreen.bytes(total),
            used * 100 / total);
  }

  static String value(Double value, String unit) {
    return value == null
        ? "Unavailable"
        : String.format(Locale.getDefault(), "%.1f%s", value, unit);
  }

  final class Tile extends LinearLayout {
    final TextView number, detail;
    final Meter meter;

    Tile(String title, int color) {
      super(a);
      setOrientation(VERTICAL);
      setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(12));
      GradientDrawable bg = a.shape(a.card);
      bg.setStroke(a.dp(1), a.dark ? 0xff2b3b50 : 0xffcedbe9);
      setBackground(bg);
      TextView label = a.text(title.toUpperCase(Locale.getDefault()), 11, a.dark ? color : a.muted);
      addView(label);
      number = a.text("—", 29, a.ink);
      number.setTypeface(null, Typeface.BOLD);
      number.setTextIsSelectable(false);
      addView(number);
      meter = new Meter(color);
      addView(meter, new LinearLayout.LayoutParams(-1, a.dp(6)));
      detail = a.text("", 12, a.muted);
      detail.setMinLines(2);
      addView(detail);
      setMinimumHeight(a.dp(154));
    }

    void set(String value, String caption, Double percent) {
      if (!value.contentEquals(number.getText())) number.setText(value);
      if (!caption.contentEquals(detail.getText())) detail.setText(caption);
      meter.percent = percent;
      meter.invalidate();
    }
  }

  final class Meter extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final int color;
    Double percent;

    Meter(int color) {
      super(a);
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    protected void onDraw(Canvas canvas) {
      float r = getHeight() / 2f;
      p.setColor(a.dark ? 0xff304053 : 0xffd9e4ef);
      canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, p);
      if (percent != null) {
        p.setColor(color);
        canvas.drawRoundRect(
            0,
            0,
            (float) (getWidth() * Math.max(0, Math.min(100, percent)) / 100),
            getHeight(),
            r,
            r,
            p);
      }
    }
  }
}
