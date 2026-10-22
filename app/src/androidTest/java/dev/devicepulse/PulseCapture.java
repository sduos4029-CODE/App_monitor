package dev.devicepulse;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Disposable-emulator visual capture. Only live OS values; no synthetic fixtures. */
public class PulseCapture {
  final Instrumentation owner;

  PulseCapture(Instrumentation owner) {
    this.owner = owner;
  }

  MainActivity app;

  void ui(Runnable action) {
    owner.runOnMainSync(action);
    owner.waitForIdleSync();
  }

  void shot(String name) throws Exception {
    owner.waitForIdleSync();
    SystemClock.sleep(700);
    final Bitmap[] frame = new Bitmap[1];
    final int[] status = {-1};
    java.util.concurrent.CountDownLatch copied = new java.util.concurrent.CountDownLatch(1);
    owner.runOnMainSync(
        () -> {
          View decor = app.getWindow().getDecorView();
          frame[0] =
              Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
          PixelCopy.request(
              app.getWindow(),
              frame[0],
              result -> {
                status[0] = result;
                copied.countDown();
              },
              new Handler(Looper.getMainLooper()));
        });
    if (!copied.await(30, java.util.concurrent.TimeUnit.SECONDS) || status[0] != PixelCopy.SUCCESS)
      throw new AssertionError("Native window screenshot failed: " + status[0]);
    Bitmap image = frame[0];
    File folder = new File(app.getFilesDir(), "screenshots");
    folder.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }

  public void runCapture() {
    Bundle result = new Bundle();
    try {
      app =
          (MainActivity)
              owner.startActivitySync(
                  new Intent(owner.getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ui(
          () -> {
            app.db.erase();
            app.selected = app.db.start(6, 5);
            app.db.sample(app.selected, Probe.take(app));
          });
      SystemClock.sleep(1500);
      ui(
          () -> {
            app.db.sample(app.selected, Probe.take(app));
            app.db.finish(app.selected, "stopped");
          });
      for (String page :
          new String[] {"Dashboard", "Components", "History", "Activity", "Findings", "Settings"}) {
        ui(() -> app.navigate(page));
        shot(page.toLowerCase(java.util.Locale.ROOT));
      }
      ui(() -> app.navigate("History"));
      shot("history-real-short-session");
      ui(
          () -> {
            for (int i = 0; i < app.body.getChildCount(); i++)
              if (app.body.getChildAt(i) instanceof Graph) {
                ((ScrollView) app.body.getParent()).scrollTo(0, app.body.getChildAt(i).getTop());
                break;
              }
          });
      shot("graphs-real-short-session");
      ui(
          () -> {
            app.db.erase();
            app.selected = -1;
            app.navigate("Dashboard");
          });
      result.putString(
          "stream",
          "8 native app-window PixelCopy screenshots captured from real short-session API"
              + " observations; capture history then deleted. No endurance claim.");
      owner.finish(Activity.RESULT_OK, result);
    } catch (Throwable t) {
      result.putString("stream", android.util.Log.getStackTraceString(t));
      owner.finish(Activity.RESULT_CANCELED, result);
    }
  }
}
