package dev.devicepulse;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

public class MonitorService extends Service {
  static volatile boolean running;
  static final String CHANNEL = "monitor";
  Store db;
  long id;
  Handler handler = new Handler();
  boolean ending;
  final Runnable sample = () -> tick();
  final BroadcastReceiver changes =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (id > 0 && !ending) {
            db.event(id, "component-event", i.getAction());
            tick();
          }
        }
      };

  public void onCreate() {
    super.onCreate();
    db = new Store(this);
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                CHANNEL, "Monitoring session", NotificationManager.IMPORTANCE_LOW));
    IntentFilter f = new IntentFilter();
    for (String a :
        new String[] {
          Intent.ACTION_SCREEN_ON,
          Intent.ACTION_SCREEN_OFF,
          Intent.ACTION_POWER_CONNECTED,
          Intent.ACTION_POWER_DISCONNECTED
        }) f.addAction(a);
    registerReceiver(changes, f);
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "stop".equals(intent.getAction())) {
      if (db.active() != null) id = db.active().optLong("id");
      finish("stopped");
      return START_NOT_STICKY;
    }
    if (running) return START_NOT_STICKY;
    JSONObject active = db.active();
    if (active == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    id = active.optLong("id");
    Intent open = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, MonitorService.class).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE);
    Notification n =
        new Notification.Builder(this, CHANNEL)
            .setSmallIcon(dev.devicepulse.R.drawable.ic_pulse)
            .setContentTitle("DevicePulse is monitoring")
            .setContentText(
                "Passive samples; Android may delay screen-off collection. Tap to view.")
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
            .build();
    startForeground(1, n);
    running = true;
    db.event(
        id,
        "start",
        "User-started passive monitor. No wake lock, exact alarm, network upload or continuous"
            + " sensor listeners.");
    tick();
    return START_NOT_STICKY;
  }

  void tick() {
    handler.removeCallbacks(sample);
    JSONObject s = db.session(id);
    if (s == null || !"running".equals(s.optString("state"))) return;
    if (System.currentTimeMillis() >= s.optLong("planned")) {
      finish("completed");
      return;
    }
    JSONObject data = Probe.take(this);
    db.sample(id, data);
    double threshold = getSharedPreferences("settings", 0).getFloat("temperature", 40);
    if (!data.isNull("tempC") && data.optDouble("tempC") > threshold) {
      db.event(
          id,
          "temperature-alert",
          "Measured battery temperature "
              + data.optDouble("tempC")
              + " °C above "
              + threshold
              + " °C");
      alert(data.optDouble("tempC"));
    }
    handler.postDelayed(
        sample,
        Math.min(
            s.optLong("interval"),
            Math.max(1000, s.optLong("planned") - System.currentTimeMillis())));
  }

  void alert(double t) {
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "alerts", "Temperature alerts", NotificationManager.IMPORTANCE_DEFAULT));
    nm.notify(
        2,
        new Notification.Builder(this, "alerts")
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("Battery temperature threshold")
            .setContentText(t + " °C · review device conditions")
            .setOnlyAlertOnce(true)
            .build());
  }

  void finish(String state) {
    ending = true;
    handler.removeCallbacks(sample);
    if (id > 0) {
      JSONObject session = db.session(id);
      if (System.currentTimeMillis() <= session.optLong("planned")) db.sample(id, Probe.take(this));
      else
        db.event(
            id,
            "late-stop",
            "Collector resumed after target; no sample attributed to target window. Unknown"
                + " trailing coverage.");
      db.finish(id, state);
      long sid = id;
      new Thread(
              () -> {
                try (Store store = new Store(this)) {
                  JSONObject s = store.session(sid);
                  store.saveApps(
                      sid,
                      AppActivity.collect(this, s.optLong("start"), s.optLong("end"), store, sid));
                }
              })
          .start();
    }
    running = false;
    stopForeground(true);
    stopSelf();
  }

  public void onDestroy() {
    handler.removeCallbacks(sample);
    unregisterReceiver(changes);
    if (!ending && id > 0) {
      db.event(
          id,
          "interruption",
          "Service destroyed; session ended. Android may also kill without callback.");
      db.finish(id, "interrupted");
    }
    running = false;
    db.close();
    super.onDestroy();
  }

  public IBinder onBind(Intent i) {
    return null;
  }
}
