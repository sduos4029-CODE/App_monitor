package dev.devicepulse;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

/** Only on a disposable emulator: tests a same-signature 1.0 → 1.1 update without uninstall. */
final class UpgradeState {
  static void run(Instrumentation t, String mode) {
    Bundle result = new Bundle();
    try (Store db = new Store(t.getTargetContext())) {
      Context c = t.getTargetContext();
      android.content.SharedPreferences p = c.getSharedPreferences("settings", 0);
      if (mode.equals("seed")) {
        long id = db.start(6, 5);
        db.sample(id, Probe.take(c));
        db.finish(id, "stopped");
        JSONArray rows = new JSONArray();
        rows.put(
            new JSONObject(
                "{\"uid\":10001,\"package\":\"upgrade.fixture\",\"rxBytes\":1234,\"txBytes\":null}"));
        db.saveApps(id, rows);
        db.reason(db.apps(id).optJSONObject(0).optLong("id"), "Backup");
        p.edit().putLong("upgradeId", id).putString("upgradeMarker", "preserve-history").commit();
        result.putString(
            "stream",
            "Seeded old installation with actual API sample, reason fixture and preference"
                + " marker.");
      } else {
        long id = p.getLong("upgradeId", -1);
        if (!"preserve-history".equals(p.getString("upgradeMarker", ""))
            || db.session(id) == null
            || db.samples(id).length() != 1
            || !"Backup".equals(db.apps(id).optJSONObject(0).optString("reason")))
          throw new AssertionError("Existing data lost across update");
        if (c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode < 2)
          throw new AssertionError("Updated version not installed");
        result.putString(
            "stream",
            "PASS same-signature 1.0.0 → 1.1.0 update retained schema-1 history, API sample, reason"
                + " and preferences. No uninstall performed.");
        db.erase();
        p.edit().remove("upgradeId").remove("upgradeMarker").commit();
      }
      t.finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      result.putString("stream", android.util.Log.getStackTraceString(failure));
      t.finish(Activity.RESULT_CANCELED, result);
    }
  }
}
