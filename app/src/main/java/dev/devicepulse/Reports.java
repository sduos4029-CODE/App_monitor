package dev.devicepulse;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class Reports {
  static String csv(JSONObject report) {
    StringBuilder b =
        new StringBuilder(
            "record,session,timestamp_epoch_ms,field,value,unit,classification,source\r\n");
    JSONObject session = report.optJSONObject("session");
    long id = session.optLong("id");
    b.append(
        line(
            "coverage",
            id,
            report.optLong("exportedAt"),
            "limitations",
            report.optString("limitations"),
            "",
            "unavailable intervals",
            "DevicePulse"));
    JSONArray samples = report.optJSONArray("samples");
    for (int i = 0; i < samples.length(); i++) {
      JSONObject r = samples.optJSONObject(i), d = Store.parse(r.optString("data"));
      java.util.Iterator<String> keys = d.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        b.append(
            line(
                "sample",
                id,
                r.optLong("ts"),
                k,
                d.isNull(k) ? "UNAVAILABLE" : d.optString(k),
                unit(k),
                d.isNull(k) ? "unavailable" : "measured",
                d.optString("source")));
      }
    }
    for (String table : new String[] {"apps", "events", "imports"}) {
      JSONArray entries = report.optJSONArray(table);
      for (int i = 0; i < entries.length(); i++) {
        JSONObject e = entries.optJSONObject(i);
        b.append(
            line(
                table,
                id,
                e.optLong("ts", session.optLong("end", session.optLong("start"))),
                table,
                e.toString(),
                "see JSON keys and README",
                table.equals("imports") ? "imported" : "measured / user confirmed reason",
                "Android / DevicePulse"));
      }
    }
    return b.toString();
  }

  static String unit(String k) {
    if (k.endsWith("Ms")) return "ms";
    if (k.endsWith("Bytes")) return "bytes";
    if (k.equals("tempC")) return "°C";
    if (k.equals("batteryPct")) return "%";
    if (k.equals("voltageMv")) return "mV";
    if (k.equals("currentUa")) return "µA";
    return "see field";
  }

  static String line(Object... values) {
    StringBuilder b = new StringBuilder();
    for (Object v : values) {
      if (b.length() > 0) b.append(',');
      String s = String.valueOf(v);
      if ((s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@"))
          && !s.matches("[+-]?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) s = "'" + s;
      b.append('"').append(s.replace("\"", "\"\"")).append('"');
    }
    return b + "\r\n";
  }

  static void write(Context c, android.net.Uri uri, String format, JSONObject report)
      throws Exception {
    try (OutputStream stream = c.getContentResolver().openOutputStream(uri, "wt")) {
      if (stream == null) throw new IOException("No output stream");
      if (format.equals("pdf")) pdf(stream, report);
      else
        stream.write(
            (format.equals("csv") ? csv(report) : report.toString(2))
                .getBytes(StandardCharsets.UTF_8));
    }
  }

  static void pdf(OutputStream stream, JSONObject report) throws Exception {
    PdfDocument pdf = new PdfDocument();
    try {
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      paint.setColor(Color.BLACK);
      paint.setTextSize(10);
      String text =
          "DevicePulse local report\nTimestamps: Unix epoch ms; export timezone: "
              + report.optString("timezone")
              + "\nUnits: "
              + report.optString("units")
              + "\n"
              + report.toString(2);
      java.util.ArrayList<String> lines = new java.util.ArrayList<>();
      for (String raw : text.split("\n")) {
        while (raw.length() > 88) {
          lines.add(raw.substring(0, 88));
          raw = raw.substring(88);
        }
        lines.add(raw);
      }
      int index = 0, pageNo = 1;
      while (index < lines.size()) {
        PdfDocument.Page page =
            pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNo++).create());
        Canvas canvas = page.getCanvas();
        canvas.drawText("DevicePulse • local measurements • page " + (pageNo - 1), 30, 25, paint);
        for (int y = 48; y < 805 && index < lines.size(); y += 13)
          canvas.drawText(lines.get(index++), 30, y, paint);
        pdf.finishPage(page);
      }
      pdf.writeTo(stream);
    } finally {
      pdf.close();
    }
  }

  static long integer(JSONObject o, String key) throws Exception {
    Object n = o.get(key);
    if (!(n instanceof Number)
        || !Double.isFinite(((Number) n).doubleValue())
        || ((Number) n).doubleValue() != ((Number) n).longValue())
      throw new IllegalArgumentException(key + " must be a finite integer");
    return ((Number) n).longValue();
  }

  static JSONObject validateImport(String raw, JSONObject session) throws Exception {
    if (raw.length() > 1024 * 1024) throw new IllegalArgumentException("Import exceeds 1 MiB");
    JSONObject root = new JSONObject(raw);
    if (root.optInt("schemaVersion") != 1
        || !"privileged-import".equals(root.optString("classification")))
      throw new IllegalArgumentException(
          "Expected schemaVersion 1 and classification privileged-import");
    if (root.optString("collector").trim().isEmpty() || root.optString("device").trim().isEmpty())
      throw new IllegalArgumentException("Collector and device descriptions are required");
    long start = integer(root, "startEpochMs"), end = integer(root, "endEpochMs");
    long sessionEnd =
        session.isNull("end")
            ? Math.min(System.currentTimeMillis(), session.optLong("planned"))
            : session.optLong("end");
    if (start < session.optLong("start") || end > sessionEnd || end <= start)
      throw new IllegalArgumentException("Import interval must be inside selected session");
    JSONArray entries = root.getJSONArray("metrics");
    if (entries.length() == 0 || entries.length() > 5000)
      throw new IllegalArgumentException("Expected 1–5000 metric rows");
    java.util.HashSet<Integer> uids = new java.util.HashSet<>();
    for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.getJSONObject(i);
      long rawUid = integer(e, "uid");
      if (rawUid > Integer.MAX_VALUE)
        throw new IllegalArgumentException("UID exceeds integer range");
      int uid = (int) rawUid;
      if (uid < 0 || !uids.add(uid))
        throw new IllegalArgumentException("UID must be nonnegative and unique");
      boolean any = false;
      java.util.Iterator<String> keys = e.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        if (!java.util.Arrays.asList("uid", "package", "batteryMah", "cpuMs", "wakeLockMs")
            .contains(k)) throw new IllegalArgumentException("Unknown metric " + k);
      }
      for (String k : new String[] {"batteryMah", "cpuMs", "wakeLockMs"})
        if (e.has(k) && !e.isNull(k)) {
          Object v = e.get(k);
          if (!(v instanceof Number) || !Logic.finiteNonnegative(((Number) v).doubleValue()))
            throw new IllegalArgumentException("Invalid nonnegative metric " + k);
          any = true;
        }
      if (!any) throw new IllegalArgumentException("No supported metric in row");
    }
    return root;
  }
}
