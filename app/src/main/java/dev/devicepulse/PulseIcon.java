package dev.devicepulse;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small native vector icons, no image downloads or font glyph dependencies. */
final class PulseIcon extends View {
  final String kind;
  final int color;
  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

  PulseIcon(Context c, String kind, int color) {
    super(c);
    this.kind = kind;
    this.color = color;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    c.scale(getWidth() / 32f, getHeight() / 32f);
    p.setColor(color);
    p.setStrokeWidth(2.2f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    Path path = new Path();
    switch (kind) {
      case "Home":
        path.moveTo(4, 15);
        path.lineTo(16, 5);
        path.lineTo(28, 15);
        path.moveTo(8, 13);
        path.lineTo(8, 27);
        path.lineTo(24, 27);
        path.lineTo(24, 13);
        path.moveTo(13, 27);
        path.lineTo(13, 19);
        path.lineTo(19, 19);
        path.lineTo(19, 27);
        c.drawPath(path, p);
        break;
      case "Overnight":
        p.setStyle(Paint.Style.FILL);
        path.addCircle(16, 16, 12, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(22, 10, 11, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(path, p);
        break;
      case "Apps":
        for (int x = 5; x < 28; x += 13)
          for (int y = 5; y < 28; y += 13) c.drawRoundRect(x, y, x + 9, y + 9, 2, 2, p);
        break;
      case "History":
        c.drawArc(5, 5, 27, 27, -150, 300, false, p);
        path.moveTo(16, 9);
        path.lineTo(16, 17);
        path.lineTo(21, 20);
        path.moveTo(3, 8);
        path.lineTo(4, 17);
        path.lineTo(11, 14);
        c.drawPath(path, p);
        break;
      default:
        c.drawCircle(16, 16, 8, p);
        c.drawCircle(16, 16, 3, p);
        for (int i = 0; i < 8; i++) {
          double r = i * Math.PI / 4;
          c.drawLine(
              16 + (float) Math.cos(r) * 10,
              16 + (float) Math.sin(r) * 10,
              16 + (float) Math.cos(r) * 13,
              16 + (float) Math.sin(r) * 13,
              p);
        }
    }
    c.restore();
  }
}
