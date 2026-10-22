import dev.devicepulse.Logic;

public class LogicTest {
  static int checks;

  static void check(boolean v) {
    checks++;
    if (!v) throw new AssertionError("Check " + checks);
  }

  public static void main(String[] args) {
    check(Logic.value(null, "mAh").equals("Unavailable"));
    check(Logic.finiteNonnegative(0));
    check(!Logic.finiteNonnegative(-1));
    check(!Logic.finiteNonnegative(Double.NaN));
    check(!Logic.finiteNonnegative(Double.POSITIVE_INFINITY));
    check(!Logic.gap(300000, 300000, 300000));
    check(Logic.gap(900000, 900000, 300000));
    check(Logic.gap(100, -1, 300000));
    check(Logic.gap(-100, 100, 300000));
    check(Logic.gap(600000, 300000, 300000));
    check(Logic.drainRate(null, 50.0, 3600000) == null);
    check(Logic.drainRate(50.0, 60.0, 3600000) == null);
    check(Logic.drainRate(90.0, 80.0, 3600000) == 10);
    check(Logic.drainRate(90.0, 80.0, 0) == null);
    check(!Logic.recurring(1));
    check(Logic.recurring(2));
    System.out.println(
        "PASS "
            + checks
            + " host assertions: unavailable data, finite metrics, gaps/clocks, drain and"
            + " recurrence.");
  }
}
