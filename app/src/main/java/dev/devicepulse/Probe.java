package dev.devicepulse;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.*;
import android.hardware.*;
import android.location.*;
import android.media.*;
import android.net.*;
import android.net.wifi.*;
import android.nfc.*;
import android.os.*;
import android.provider.Settings;
import android.telephony.*;
import android.view.*;
import java.io.*;
import java.util.*;
import org.json.*;

final class Probe {
  static long lastCpu = -1, lastElapsed = -1;

  static String read(String file) {
    try {
      return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file))).trim();
    } catch (Exception e) {
      return null;
    }
  }

  static Double number(Intent b, String k, double factor) {
    return b == null || !b.hasExtra(k) ? null : b.getIntExtra(k, 0) / factor;
  }

  static JSONObject take(Context c) {
    JSONObject o = new JSONObject();
    Store.put(o, "capturedAt", System.currentTimeMillis());
    Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    BatteryManager bm = c.getSystemService(BatteryManager.class);
    Double level = number(b, BatteryManager.EXTRA_LEVEL, 1),
        scale = number(b, BatteryManager.EXTRA_SCALE, 1);
    Store.put(
        o, "batteryPct", level == null || scale == null || scale <= 0 ? null : 100 * level / scale);
    Store.put(o, "tempC", number(b, BatteryManager.EXTRA_TEMPERATURE, 10));
    Store.put(o, "voltageMv", number(b, BatteryManager.EXTRA_VOLTAGE, 1));
    int current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
    Store.put(o, "currentUa", current == Integer.MIN_VALUE ? null : current);
    int health = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
    Store.put(
        o,
        "health",
        new String[] {
              "Unknown",
              "Unknown",
              "Good",
              "Overheat",
              "Dead",
              "Over voltage",
              "Unspecified failure",
              "Cold"
            }
            [Math.min(7, Math.max(0, health))]);
    int plugged = b == null ? -1 : b.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
        state = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
    Store.put(o, "plugged", plugged < 0 ? null : plugged != 0);
    Store.put(
        o,
        "chargingSource",
        plugged == 0
            ? "Unplugged"
            : plugged == 1 ? "AC" : plugged == 2 ? "USB" : plugged == 4 ? "Wireless" : "Unknown");
    Store.put(
        o,
        "chargingState",
        state == 2
            ? "Charging"
            : state == 3
                ? "Discharging"
                : state == 4 ? "Not charging" : state == 5 ? "Full" : "Unknown");
    PowerManager pm = c.getSystemService(PowerManager.class);
    Store.put(o, "screenOn", pm.isInteractive());
    Store.put(o, "powerSave", pm.isPowerSaveMode());
    Store.put(o, "doze", pm.isDeviceIdleMode());
    Store.put(
        o, "thermalStatus", Build.VERSION.SDK_INT >= 29 ? pm.getCurrentThermalStatus() : null);
    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    c.getSystemService(ActivityManager.class).getMemoryInfo(mi);
    Store.put(o, "memoryTotalBytes", mi.totalMem);
    Store.put(o, "memoryAvailableBytes", mi.availMem);
    StatFs fs = new StatFs(c.getFilesDir().getPath());
    Store.put(o, "storageTotalBytes", fs.getTotalBytes());
    Store.put(o, "storageAvailableBytes", fs.getAvailableBytes());
    long elapsed = SystemClock.elapsedRealtime(), cpu = android.os.Process.getElapsedCpuTime();
    Store.put(
        o,
        "ownCpuPctOneCore",
        lastElapsed >= 0 && elapsed > lastElapsed
            ? 100.0 * (cpu - lastCpu) / (elapsed - lastElapsed)
            : null);
    lastElapsed = elapsed;
    lastCpu = cpu;
    Store.put(o, "cpuFrequencyKhz", read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
    Store.put(o, "deviceCpuUsage", null);
    Store.put(o, "cpuTemperatureC", null);
    Store.put(o, "gpuTemperatureC", null);
    Store.put(
        o,
        "brightnessSetting",
        Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1) == -1
            ? null
            : Settings.System.getInt(
                c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1));
    Display display =
        c.getSystemService(android.hardware.display.DisplayManager.class)
            .getDisplay(Display.DEFAULT_DISPLAY);
    Store.put(o, "refreshHz", display == null ? null : display.getRefreshRate());
    Store.put(o, "components", components(c));
    Store.put(o, "source", "measured · Android public APIs; manufacturer-reported sensor values");
    return o;
  }

  static void component(JSONObject o, String name, String state, String value) {
    JSONObject s = new JSONObject();
    Store.put(s, "state", state);
    Store.put(s, "value", value);
    Store.put(s, "source", "Android public API");
    Store.put(o, name, s);
  }

  static boolean permission(Context c, String p) {
    return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
  }

  static JSONObject components(Context c) {
    JSONObject o = new JSONObject();
    ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
    NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
    component(
        o,
        "Connectivity",
        "measured",
        caps == null
            ? "No active network"
            : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                ? "Wi-Fi"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    ? "Mobile"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        ? "VPN"
                        : "Other active network");
    WifiManager wm = c.getSystemService(WifiManager.class);
    if (wm == null) component(o, "Wi-Fi", "unsupported", null);
    else
      try {
        WifiInfo w = wm.getConnectionInfo();
        String ssid = w == null ? null : w.getSSID();
        boolean hidden = ssid == null || ssid.equals("<unknown ssid>");
        boolean connected = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        component(
            o,
            "Wi-Fi",
            "measured",
            (wm.isWifiEnabled() ? "Enabled" : "Disabled")
                + " · "
                + (connected ? "Connected" : "Not active Wi-Fi transport")
                + (w == null
                    ? " · metrics unavailable"
                    : " · RSSI "
                        + (w.getRssi() <= -127 ? "unavailable" : w.getRssi() + " dBm")
                        + " · frequency "
                        + (w.getFrequency() <= 0 ? "unavailable" : w.getFrequency() + " MHz")
                        + " · link "
                        + (w.getLinkSpeed() < 0 ? "unavailable" : w.getLinkSpeed() + " Mbps")));
        component(
            o,
            "Network name",
            hidden ? "permission-required / restricted" : "measured",
            hidden ? "Location permission/service and OS policy govern SSID" : ssid);
      } catch (SecurityException e) {
        component(o, "Wi-Fi", "restricted", null);
      }
    BluetoothManager bt = c.getSystemService(BluetoothManager.class);
    BluetoothAdapter ba = bt == null ? null : bt.getAdapter();
    if (ba == null) component(o, "Bluetooth", "unsupported", null);
    else if (Build.VERSION.SDK_INT >= 31 && !permission(c, Manifest.permission.BLUETOOTH_CONNECT))
      component(
          o,
          "Bluetooth",
          "permission-required",
          "Open Bluetooth details to allow nearby-device access");
    else
      try {
        StringBuilder pairs = new StringBuilder();
        if (ba.isEnabled())
          for (BluetoothDevice d : ba.getBondedDevices())
            pairs.append(d.getName() == null ? "Unnamed paired device" : d.getName()).append("; ");
        component(
            o,
            "Bluetooth",
            "measured",
            (ba.isEnabled() ? "Enabled" : "Disabled")
                + " · paired: "
                + (pairs.length() == 0 ? "none" : pairs)
                + " · connected profiles: headset="
                + profile(ba, BluetoothProfile.HEADSET)
                + ", audio="
                + profile(ba, BluetoothProfile.A2DP)
                + " (other profiles unavailable)");
      } catch (SecurityException e) {
        component(o, "Bluetooth", "restricted", null);
      }
    LocationManager lm = c.getSystemService(LocationManager.class);
    component(
        o,
        "Location service",
        "measured",
        Build.VERSION.SDK_INT >= 28
            ? String.valueOf(lm.isLocationEnabled())
            : "GPS provider " + lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
    NfcAdapter n = NfcAdapter.getDefaultAdapter(c);
    component(
        o,
        "NFC",
        n == null ? "unsupported" : "measured",
        n == null ? null : n.isEnabled() ? "Enabled" : "Disabled");
    AudioManager am = c.getSystemService(AudioManager.class);
    component(
        o,
        "Audio",
        "measured",
        "Media volume "
            + am.getStreamVolume(AudioManager.STREAM_MUSIC)
            + " / "
            + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            + " · ringer mode "
            + am.getRingerMode()
            + " · music active "
            + am.isMusicActive());
    component(
        o,
        "Camera",
        c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            ? "available hardware"
            : "unsupported",
        "No camera opened; current exclusive availability not observed");
    component(
        o,
        "Microphone",
        c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            ? "available hardware"
            : "unsupported",
        "No recording; other apps' microphone use unavailable");
    component(
        o,
        "Mobile",
        c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            ? "measured"
            : "unsupported",
        caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            ? "Active mobile transport"
            : "No active mobile transport; subscriber details restricted");
    return o;
  }

  static String profile(BluetoothAdapter a, int p) {
    try {
      int s = a.getProfileConnectionState(p);
      return s == BluetoothProfile.STATE_CONNECTED
          ? "connected"
          : s == BluetoothProfile.STATE_CONNECTING
              ? "connecting"
              : s == BluetoothProfile.STATE_DISCONNECTING ? "disconnecting" : "disconnected";
    } catch (SecurityException e) {
      return "permission-required/restricted";
    }
  }

  static String cpu() {
    return "Chipset: "
        + (Build.VERSION.SDK_INT >= 31
            ? Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL
            : Build.HARDWARE + " (hardware identifier, not verified chipset)")
        + "\nArchitecture: "
        + Arrays.toString(Build.SUPPORTED_ABIS)
        + "\nLogical processors: "
        + Runtime.getRuntime().availableProcessors()
        + "\n"
        + "Device-wide CPU usage: restricted/unavailable. App CPU is process CPU time divided by"
        + " elapsed time (100% = one core). CPU/GPU temperatures unavailable through a portable"
        + " public API.";
  }
}
