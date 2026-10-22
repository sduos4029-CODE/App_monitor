package dev.devicepulse;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    if (i == null || !Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
    try (Store db = new Store(c)) {
      if (db.active() != null) {
        db.event(
            db.active().optLong("id"),
            "reboot",
            "BOOT_COMPLETED received. Monitoring is not automatically restarted.");
        db.finish(db.active().optLong("id"), "interrupted");
      }
    }
  }
}
