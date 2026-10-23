package dev.devicepulse;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

final class Store extends SQLiteOpenHelper {
  Store(Context c) {
    super(c, "devicepulse.db", null, 1);
    setWriteAheadLoggingEnabled(true);
  }

  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE sessions(id INTEGER PRIMARY KEY,start INTEGER,end INTEGER,planned"
            + " INTEGER,interval INTEGER,state TEXT,bootElapsed INTEGER)");
    d.execSQL(
        "CREATE TABLE samples(id INTEGER PRIMARY KEY,session INTEGER,ts INTEGER,elapsed"
            + " INTEGER,data TEXT)");
    d.execSQL("CREATE INDEX sample_session ON samples(session,ts)");
    d.execSQL(
        "CREATE TABLE events(id INTEGER PRIMARY KEY,session INTEGER,ts INTEGER,kind TEXT,detail"
            + " TEXT)");
    d.execSQL(
        "CREATE TABLE apps(id INTEGER PRIMARY KEY,session INTEGER,uid INTEGER,pkg TEXT,data"
            + " TEXT,reason TEXT)");
    d.execSQL("CREATE TABLE imports(id INTEGER PRIMARY KEY,session INTEGER,ts INTEGER,data TEXT)");
  }

  public void onUpgrade(SQLiteDatabase d, int a, int b) {
    throw new IllegalStateException("Unknown schema migration");
  }

  long start(int hours, int minutes) {
    long t = System.currentTimeMillis();
    ContentValues v = new ContentValues();
    v.put("start", t);
    v.put("planned", t + hours * 3600000L);
    v.put("interval", minutes * 60000L);
    v.put("state", "running");
    v.put("bootElapsed", android.os.SystemClock.elapsedRealtime());
    return getWritableDatabase().insertOrThrow("sessions", null, v);
  }

  void event(long s, String kind, String detail) {
    // Atomic existence check: a late collector must not recreate erased history.
    getWritableDatabase()
        .execSQL(
            "INSERT INTO events(session,ts,kind,detail) SELECT ?,?,?,? WHERE EXISTS(SELECT 1 FROM"
                + " sessions WHERE id=?)",
            new Object[] {s, System.currentTimeMillis(), kind, detail, s});
  }

  JSONObject active() {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM sessions WHERE state='running' ORDER BY id DESC LIMIT 1", null)) {
      return c.moveToFirst() ? row(c) : null;
    }
  }

  JSONObject session(long id) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT * FROM sessions WHERE id=?", new String[] {"" + id})) {
      return c.moveToFirst() ? row(c) : null;
    }
  }

  void finish(long id, String state) {
    JSONObject s = session(id);
    if (s == null) return;
    ContentValues v = new ContentValues();
    v.put("end", System.currentTimeMillis());
    v.put("state", state);
    getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + id});
    event(
        id,
        state,
        "Session "
            + state
            + ". Sample timestamps determine coverage; missing intervals are unknown.");
  }

  void sample(long s, JSONObject data) {
    long now = System.currentTimeMillis(), elapsed = android.os.SystemClock.elapsedRealtime();
    JSONObject session = session(s);
    JSONArray old = samples(s);
    if (old.length() > 0) {
      JSONObject last = old.optJSONObject(old.length() - 1);
      long wall = now - last.optLong("ts"), mono = elapsed - last.optLong("elapsed");
      if (Logic.gap(wall, mono, session.optLong("interval")))
        event(
            s,
            "gap",
            Logic.gapReason(wall, mono, session.optLong("interval")) + "; gap " + wall + " ms");
    }
    ContentValues v = new ContentValues();
    v.put("session", s);
    v.put("ts", now);
    v.put("elapsed", elapsed);
    v.put("data", data.toString());
    getWritableDatabase().insertOrThrow("samples", null, v);
  }

  void recover() {
    JSONObject a = active();
    if (a == null) return;
    long id = a.optLong("id");
    JSONArray records = samples(id);
    boolean reboot =
        records.length() > 0
            && android.os.SystemClock.elapsedRealtime()
                < records.optJSONObject(records.length() - 1).optLong("elapsed");
    event(
        id,
        "interruption",
        reboot
            ? "Reboot detected from elapsed clock reset"
            : "Collector absent; process termination, force-stop or reboot possible. Exact"
                + " cause/time unavailable.");
    finish(id, "interrupted");
  }

  JSONArray samples(long s) {
    return query("SELECT * FROM samples WHERE session=? ORDER BY id", new String[] {"" + s});
  }

  JSONArray sessions() {
    return query("SELECT * FROM sessions ORDER BY start DESC", null);
  }

  JSONArray apps(long s) {
    return query("SELECT * FROM apps WHERE session=? ORDER BY id", new String[] {"" + s});
  }

  JSONArray events(long s) {
    return query(
        "SELECT * FROM events " + (s > 0 ? "WHERE session=? " : "") + "ORDER BY ts DESC",
        s > 0 ? new String[] {"" + s} : null);
  }

  JSONArray query(String sql, String[] args) {
    JSONArray a = new JSONArray();
    try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
      while (c.moveToNext()) a.put(row(c));
    }
    return a;
  }

  static JSONObject row(Cursor c) {
    JSONObject o = new JSONObject();
    for (int i = 0; i < c.getColumnCount(); i++)
      try {
        if (c.isNull(i)) o.put(c.getColumnName(i), JSONObject.NULL);
        else if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) o.put(c.getColumnName(i), c.getLong(i));
        else o.put(c.getColumnName(i), c.getString(i));
      } catch (JSONException e) {
        throw new IllegalStateException(e);
      }
    return o;
  }

  static JSONObject parse(String s) {
    try {
      return new JSONObject(s);
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  static void put(JSONObject o, String k, Object v) {
    try {
      o.put(k, v == null ? JSONObject.NULL : v);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  void saveApps(long s, JSONArray entries) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      if (session(s) == null) return;
      HashMap<Integer, String> reasons = new HashMap<>();
      JSONArray old = apps(s);
      for (int i = 0; i < old.length(); i++)
        reasons.put(old.optJSONObject(i).optInt("uid"), old.optJSONObject(i).optString("reason"));
      d.delete("apps", "session=?", new String[] {"" + s});
      for (int i = 0; i < entries.length(); i++) {
        JSONObject o = entries.optJSONObject(i);
        ContentValues v = new ContentValues();
        v.put("session", s);
        v.put("uid", o.optInt("uid"));
        v.put("pkg", o.optString("package"));
        v.put("data", o.toString());
        v.put("reason", reasons.get(o.optInt("uid")));
        d.insertOrThrow("apps", null, v);
      }
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  void reason(long id, String reason) {
    ContentValues v = new ContentValues();
    v.put("reason", reason);
    getWritableDatabase().update("apps", v, "id=?", new String[] {"" + id});
  }

  void erase() {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      for (String table : new String[] {"samples", "sessions", "events", "apps", "imports"})
        d.delete(table, null, null);
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  JSONObject report(long id) {
    JSONObject o = new JSONObject();
    put(o, "schemaVersion", 1);
    put(o, "app", "DevicePulse 1.2.0");
    put(o, "exportedAt", System.currentTimeMillis());
    put(o, "timezone", TimeZone.getDefault().getID());
    put(o, "session", session(id));
    put(o, "summary", Analysis.summary(this, id));
    put(o, "samples", samples(id));
    put(o, "events", events(id));
    put(o, "apps", apps(id));
    put(o, "imports", query("SELECT * FROM imports WHERE session=?", new String[] {"" + id}));
    put(
        o,
        "limitations",
        "Passive samples only; gaps unknown. Screen-off/unplugged coverage uses observed boundaries"
            + " only. OS network buckets may be delayed. Foreground time is not battery use. No"
            + " per-app battery/CPU/wake-lock without labelled import. No simulation data.");
    put(
        o,
        "units",
        "ts/start/end: Unix epoch ms; elapsed: ms since boot; batteryPct: percent; tempC: Celsius;"
            + " voltageMv: mV; currentUa: microampere (device-dependent sign); rxBytes/txBytes:"
            + " bytes; foregroundMs: ms; batteryMah: mAh; cpuMs/wakeLockMs: ms");
    return o;
  }
}
