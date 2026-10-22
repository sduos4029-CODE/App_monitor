package dev.devicepulse;

/** Measurement semantics kept independent from Android for deterministic tests. */
public final class Logic {
  public static final String MEASURED = "measured",
      IMPORTED = "imported",
      UNAVAILABLE = "unavailable";

  public static boolean finiteNonnegative(double n) {
    return Double.isFinite(n) && n >= 0;
  }

  public static boolean gap(long wallDelta, long elapsedDelta, long interval) {
    return wallDelta < 0
        || elapsedDelta < 0
        || Math.abs(wallDelta - elapsedDelta) > 60000
        || elapsedDelta > interval * 2 + 10000;
  }

  public static String gapReason(long wallDelta, long elapsedDelta, long interval) {
    if (elapsedDelta < 0) return "Reboot / elapsed clock reset";
    if (wallDelta < 0 || Math.abs(wallDelta - elapsedDelta) > 60000)
      return "Clock changed or reboot; timing uncertain";
    return "Sampling delayed: Doze, scheduling or process interruption possible; cause not proven";
  }

  public static Double drainRate(Double first, Double last, long milliseconds) {
    return first == null || last == null || milliseconds <= 0 || last > first
        ? null
        : (first - last) * 3600000.0 / milliseconds;
  }

  public static boolean recurring(int completedViolations) {
    return completedViolations >= 2;
  }

  public static String value(Double n, String unit) {
    return n == null ? "Unavailable" : String.format(java.util.Locale.US, "%.2f %s", n, unit);
  }
}
