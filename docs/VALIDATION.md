# Executed verification — DevicePulse 1.0.0

Prepared on 22 October 2026 from repository base `143b8ca94d7ef74e81e71a9fa05c7d465b1dfb1f` (`apk-download`, the repository's original branch). The repository had only `Nightwatch.apk`, package `dev.nightwatch`, with no usable source. That file is unchanged. New development is on `devicepulse/native-monitor`; DevicePulse is a separate `dev.devicepulse` package.

## Build and signing

- Temurin JDK 21.0.8+9, verified against its official published SHA-256 before use.
- Gradle 8.10.2 (wrapper distribution SHA-256 pinned), Android Gradle plugin 8.7.3, SDK/build-tools 35.0.0, min API 26, target/compile API 35.
- `assembleDebug assembleDebugAndroidTest lintDebug`: passed. No lint errors. Remaining warnings concern English string localization; this build is English-only.
- `apksigner verify --verbose --print-certs`: APK signature valid, Android **debug** certificate. Signing key stays outside the repository/source ZIP. Certificate digest is saved in the verification folder.
- No `INTERNET`, accessibility, broad-storage, `BATTERY_STATS`, `DUMP` or root permission in the APK.

## Executed tests

**16 host assertions passed** for unavailable readings, finite/nonnegative values, sampling delays/reboot/clock discontinuities, charging/drain semantics and recurrence.

**15 native instrumentation tests passed** on an actual running Android 11/API 30 AOSP x86_64 emulator, using the shipping APK. The 480×800 display is configured at 200 dpi. This host has no `/dev/kvm`; all emulation is in software.

The tests exercised:

1. No automatic sensitive grants or demonstration history on first launch.
2. Null/unavailable classification, finite values, charging invalidation and recurring thresholds.
3. Delayed samples, elapsed-clock reset and wall-clock discontinuity detection (synthetic timing fixtures).
4. Persisted six/seven-hour targets and absent-collector interruption recovery (short tests, not endurance runs).
5. An actual foreground-service sample of battery/components and explicit Stop persistence.
6. Completion when a target is already elapsed (accelerated target fixture).
7. Denied usage/network access and no invented per-app energy/CPU/wake-lock values.
8. Full-session versus screen-off/unplugged coverage and exclusion of a known gap (synthetic boundary fixtures).
9. Recurrence from completed sessions only; stopped sessions/imported claims excluded.
10. Privileged-import validation including wrong classification, negative/string metrics, invalid time window and duplicate UID.
11. Real API sample CSV/JSON/PDF generation; numeric negative current remains numeric while text formulas are escaped.
12. Writes through Android `ContentResolver` to owned content URIs and reads back valid CSV/JSON/PDF; temporary exported test documents removed.
13. User-confirmed reasons survive a refresh; deletion clears tables and rejects late worker log entries.
14. Native pages render in dark/light mode and small-phone layouts.
15. Battery/temperature graphs render from a real short-session pair of API samples.

Synthetic fixtures exist only during tests on the disposable test installation and are deleted. They are not bundled, retained in the APK, or presented as observed measurements. Screen captures use actual short-session API readings (not a completed overnight session). The test script checks Instrumentation RESULT_OK and the reported passed count; a zero-test or failed run does not count as passing.

Actual exported JSON and CSV were also parsed independently on the host, checking sample presence, units, limitations and UNAVAILABLE fields. `pdfinfo` and `pdftotext` independently parsed the generated two-page PDF and confirmed the report, samples, temperature fields and limitations text.

## Visual evidence and emulator limits

Eight native screens are provided in `delivery/screenshots`. The initial full-screen capture pass exposed a **System UI** ANR dialog in this slow software emulator. It was not a DevicePulse crash. The separate visual capture path uses Android `PixelCopy` on the running app's own native window, excluding other apps' windows/system overlays; these are native window screenshots, not mockups or edited images. See the separate visual-capture result log. Graphs show real short-session emulator API values; emulator battery/temperature reports do not verify physical electrical or thermal accuracy.

## Unverified and physical-device checks still needed

- No actual six-hour or seven-hour endurance run or measurement of monitoring's own battery overhead has been completed. Low-frequency passive collection/no wake lock is a design choice, not proof of negligible overhead.
- Doze/vendor task killers, process termination without callbacks, force-stop and real reboot delivery require physical-device trials. Pure logic/recovery paths were tested; live cause attribution is intentionally not assumed. Android can defer samples and target completion.
- API 26–29 and API 31–35 have compile/lint checks, not runtime device coverage. Android 13+ notification and Android 14+ foreground-service behavior need device verification.
- No physical Bluetooth pairing, Wi-Fi SSID grant/redaction, cellular subscriber behavior, thermal calibration, CPU sysfs coverage or protected sensor capability was verified. Permission denial and unsupported states are supported.
- Positive usage-access grant on a personal device, OS bucket accuracy, the full document-picker/share-sheet interaction and user-confirmed import picker flow were not automated end to end. Validator, report generators and actual Android content-URI read/write paths were exercised. There is no release-signing, Play Store review or Play Protect acceptance claim.
- Reports from a real authorized privileged collector still need user-provided provenance/device matching. The example is explicitly illustrative. Imports never enter real alert/recurrence calculations.

7/30-day comparisons select sessions by their start timestamp in the past 7/30 days; they are not calendar-day or full-device 24-hour totals. Interrupted/stopped observations remain visible, while recurring findings require completed sessions. Missing intervals remain unknown.
