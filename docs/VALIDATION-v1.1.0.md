# DevicePulse 1.1.0 — executed verification

Built from `devicepulse/overnight-ui`, retaining package `dev.devicepulse`, schema 1, the original debug certificate, and the original `Nightwatch.apk` unchanged. VersionCode 2 permits an update over the matching-signed 1.0.0 app. No uninstall or user-data migration is needed for that matching certificate.

## Implemented overnight workflow

Home starts a 6h/7h target session. Overnight shows actual endpoint battery observations, whole-device change, coverage, unknown time and a timeline. Apps shows permitted network/foreground observations, energy unavailability and user-confirmed context. History retains comparisons, exports/imports; Settings contains component details, findings and log. Native vector icons, compact cards, blue controls and five bottom tabs implement the reviewed design without embedding the generated mockup or its example values.

A delayed final sample now keeps the actual end timestamp and records `target-overrun`; the observed window may exceed its target. Two timed battery readings are required to claim a change. Null screen/charging states never count as unused time. Clock discontinuities break chart connections and suppress rates. CSV/JSON/PDF contain the same coverage summary and raw-source evidence.

## Checks that actually ran

- **Build, test APK and Android lint passed**, using JDK 21.0.8+9, Gradle 8.10.2 / AGP 8.7.3, SDK/build-tools 35.0.0. No lint errors; remaining warnings concern localization/string formatting. No new runtime libraries or sensitive permissions.
- **16 host assertions passed.** Unavailability, finite metrics, delays/reboots/clock shifts, drain/charging and recurrence.
- **23 instrumentation tests passed** on the shipping debug APK, on an Android 11/API 30 AOSP software emulator at 480×800 / 200 dpi. The original 15 checks cover real foreground service sampling/stopping, accelerated target completion, permissions, recurrence, imports, CSV/JSON/PDF content-URI roundtrips, deletion, native pages and graphs.
- Eight added checks cover single-reading unavailability, explicit-null states, coverage partition arithmetic, missing subset battery values, clock breaks/rate suppression, five-tab navigation/no mockup numbers, user-confirmed rather than inferred purpose, retained 7h state and opening the actual Android Battery usage screen. Total: 23 executed tests.
- **Same-signature update retention passed.** The disposable emulator was seeded while running the original 1.0.0 APK. A normal `adb install -r` of 1.1.0 preserved its API sample, session, confirmed-reason fixture and settings marker; no uninstall was performed. Fixture data was removed after validation.
- **Ten native app-window PNG captures** were taken using PixelCopy, including a verified scroll to the overnight graph. These show real short-session emulator API observations. Native report, app activity, home and graph captures were visually inspected. System overlays/chrome are excluded; images are not mockups or edited simulation results.
- JSON/CSV and the PDF were independently parsed on the host. Coverage partition arithmetic, timestamps/units, unavailability, sources, summary and limitations were checked. CSV regressions verify both integer and decimal negative numbers remain numeric while formula text is escaped.
- APK signature verification passed. The private debug key remains outside the repository and source ZIP. See `delivery/verification-v1.1.0` for command output, certificate digest and actual export examples.

## Practical limits

No complete six-hour/seven-hour endurance run, physical battery-overhead measurement, thermal calibration or real-phone reboot/Doze/vendor-killer test has been executed. Target-expiry and interruptions use short controlled tests/fixtures; they do not prove uninterrupted overnight collection. Hardware/radio readings are emulator observations, not physical validation.

Runtime coverage is API 30. Other supported versions (26–35), Android 13+ notification prompts and Android 14+ special-use foreground service behavior need physical-device testing. Activity state saving for a 7h choice was checked; a real rotation during an Android 13 notification prompt was not executed.

Positive per-app usage-access grant and accurate OS bucket coverage require phone testing. Network activity does not prove energy use, malicious behavior, a backup or any private job purpose. Android Battery usage can use a different time range; DevicePulse opens that screen but does not read its private figures. Exact per-app battery/CPU/wake-lock metrics remain unavailable unless separately imported from an authorized collector; imports are not verified measurements or real alert inputs.

Report generation and real Android content-URI read/write were exercised. Full document-picker/share-sheet and collector-import selection/confirmation UI flows still need end-to-end phone testing. Physical Bluetooth pairs, Wi-Fi SSID permissions, sensors and CPU frequency coverage remain device-dependent. Keep Play Protect enabled; neither installation approval nor Play Store readiness is claimed.

Synthetic fixtures live only in the disposable test installation and are deleted. Actual screenshots show a short stopped session with two API observations, not a completed night. Existing user records and preferences are preserved; only the disposable test commands erase their test data.
