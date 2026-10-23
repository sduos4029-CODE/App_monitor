# DevicePulse 1.2.0 validation

Native component-dashboard update; package `dev.devicepulse`, versionCode 3, min API 26 / target 35. Debug signed using the existing key retained outside the repository. No schema change, destructive migration or permission expansion. Original APK and earlier deliveries are preserved.

## Executed checks

- Offline debug/test APK builds and Android lint: successful. Lint reports 0 errors / 20 warnings (localization, existing graph/icon allocations and programmatic-view constructor).
- 16 host assertions: unavailable/nonfinite metrics, sampling gaps, clock changes, drain and recurrence.
- 27 Android instrumentation cases on the Android 11/API 30 AOSP x86_64 emulator, 480×800. Includes the existing 23 monitoring/history/permissions/classification/import/export/UI checks plus four dashboard cases: actual foreground refresh/timestamp changes without replacing views; null/permission/unsupported states; scroll preservation/no history side effects; pause suppression/navigation cleanup.
- Same-signature 1.1→1.2 installation using `adb install -r` without uninstall: retained actual API sample, session, confirmed-reason fixture and preferences; fixture then deleted. Test runs use a disposable emulator, never the user's device.
- Native PixelCopy app-window captures of the installed 1.2 build, including the dashboard, scrolled connections and other components. No generated device values or UI mockup pixels.
- Signed APK verification, downloaded APK/source byte comparison and source ZIP integrity check. Private signing material and machine paths are excluded from the source ZIP.

Raw logs are under `delivery/verification-v1.2.0`. Older 1.1 report-parser evidence remains separately labelled in `delivery/verification-v1.1.0`.

## Scope and limits

The live timestamp denotes when DevicePulse checked Android APIs. Underlying battery/radio/thermal drivers can update less frequently, redact values, or return static emulator values. Auto refresh is every 2 seconds only while Components is foreground; it creates no history session and does not change overnight sampling. No camera, microphone, Bluetooth scan, Wi-Fi scan or continuous sensor registration is activated by the dashboard. Optional sensor readings remain explicitly requested and limited to 15 seconds.

Memory is total device RAM minus available RAM (includes OS/cache); storage is this app's data volume. Core-0 frequency can be restricted or unavailable. Own-process CPU uses process CPU-time deltas (100% = one core); device-wide CPU and portable CPU/GPU temperatures are unavailable. Connections reveal only public API state; paired does not mean connected, hardware present does not mean recording/in use. Missing values are never replaced with zero. Per-app battery/job-purpose limits are unchanged.

No physical phone/TV, full 6/7-hour endurance, overnight battery-overhead benchmark, OEM killing/Doze/reboot endurance, or real radio/thermal accuracy was tested. Runtime testing is API 30; API 33 notification/nearby-device prompts and API 34+ foreground-service handling require supported physical-device validation. Full document-picker/share/import UI end-to-end interactions and positive usage-access rankings on a real account/device remain outside the executed checks. Keep Play Protect enabled; installation approval is not guaranteed.
