package dev.devicepulse;

import android.content.*;
import android.graphics.*;
import android.view.*;
import java.util.*;
import org.json.*;

final class Graph extends View {
  final Paint p = new Paint(3);
  final ArrayList<Double> values = new ArrayList<>();
  final ArrayList<Long> times = new ArrayList<>();
  final String title, unit;
  long maxGap = Long.MAX_VALUE;

  Graph(Context c) {
    this(c, "Readings", "value");
  }

  float u(float value) {
    return value * getResources().getDisplayMetrics().density;
  }

  float sp(float value) {
    return value * getResources().getDisplayMetrics().scaledDensity;
  }

  Graph(Context c, String title, String unit) {
    super(c);
    this.title = title;
    this.unit = unit;
    setMinimumHeight(190);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setFocusable(true);
  }

  void timeline(JSONArray rows, String key, long interval) {
    maxGap = interval * 2 + 10000;
    for (int i = 0; i < rows.length(); i++) {
      JSONObject r = rows.optJSONObject(i), d = Store.parse(r.optString("data"));
      times.add(r.optLong("ts"));
      values.add(Analysis.n(d, key));
    }
    describe();
  }

  void bars(List<Double> data) {
    values.addAll(data);
    for (int i = 0; i < data.size(); i++) times.add((long) i);
    describe();
  }

  void describe() {
    StringBuilder s = new StringBuilder(title + ", " + unit + ": ");
    for (int i = 0; i < values.size(); i++)
      s.append(
              values.get(i) == null
                  ? "unavailable"
                  : String.format(Locale.US, "%.1f", values.get(i)))
          .append("; ");
    setContentDescription(s);
    invalidate();
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(), h = getHeight();
    p.setTextSize(sp(14));
    p.setColor(0xffb8c9dc);
    c.drawText(title + " (" + unit + ")", u(8), u(22), p);
    Double min = null, max = null;
    for (Double v : values)
      if (v != null) {
        min = min == null ? v : Math.min(min, v);
        max = max == null ? v : Math.max(max, v);
      }
    if (min == null) {
      c.drawText("No measured readings", u(8), u(68), p);
      return;
    }
    double range = Math.max(1, max - min);
    float left = u(54), right = w - u(15), top = u(45), bottom = h - u(38);
    p.setTextSize(sp(11));
    c.drawText(String.format(Locale.US, "%.1f", max), u(3), top, p);
    c.drawText(String.format(Locale.US, "%.1f", min), u(3), bottom, p);
    p.setColor(0xff52657d);
    c.drawLine(left, top, left, bottom, p);
    c.drawLine(left, bottom, right, bottom, p);
    float px = 0, py = 0;
    boolean previous = false;
    long begin = times.get(0), span = Math.max(1, times.get(times.size() - 1) - begin);
    for (int i = 0; i < values.size(); i++) {
      Double v = values.get(i);
      if (v == null) {
        previous = false;
        continue;
      }
      float x = left + (right - left) * (times.get(i) - begin) / span,
          y = bottom - (float) ((v - min) / range) * (bottom - top);
      p.setColor(0xff6ce4c2);
      p.setStrokeWidth(u(2));
      if (previous && times.get(i) - times.get(i - 1) <= maxGap) c.drawLine(px, py, x, y, p);
      c.drawCircle(x, y, u(3), p);
      previous = true;
      px = x;
      py = y;
    }
    p.setColor(0xffb8c9dc);
    p.setTextSize(sp(10));
    String start = times.size() > 0 && begin > 1000000 ? MainActivity.time(begin) : "Rank 1";
    String end =
        begin > 1000000 ? MainActivity.time(times.get(times.size() - 1)) : "Rank " + times.size();
    c.drawText(start, left, h - u(10), p);
    c.drawText(end, Math.max(left, right - p.measureText(end)), h - u(10), p);
  }
}
