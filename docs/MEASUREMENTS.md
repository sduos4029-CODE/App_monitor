# Measurements and limitations — 1.1.0

## Monitoring

A user action starts a `specialUse` foreground service (Android 14+) with a visible low-priority ongoing notification. On Android 13+, notification permission is requested at start; refusing it does not block the service, though Android may show it only in the active-app UI. Start/stop controls remain in DevicePulse. No automatic startup after reboot.

The Handler sampler deliberately does not wake the device. During deep sleep/Doze, handlers and broadcasts may be delayed. Completion occurs when the collector next runs (or the user returns after expiry). In 1.1.0, the final reading and end use their actual collection/stop timestamps. A `target-overrun` event records lateness. The observed session can therefore exceed its 6h/7h target; a sample is never backdated to the target. The timeline and app-activity query use the observed session window. Earlier 1.0 records retain their original capped ends; their sample timestamps still show actual available endpoints. A force-stop can suppress reboot delivery until the user reopens the app. On reopen, a persisted running session with no live collector becomes interrupted, without guessing the exact cause. Reboot is identified when the boot broadcast is received or an elapsed-clock reset is observed. Wall-clock changes also produce an explicit timing-uncertainty gap.

Each sample includes Unix epoch milliseconds and elapsed milliseconds since boot. A delay greater than twice the requested interval plus 10 seconds, a backward clock, or a >60-second wall/elapsed disagreement breaks coverage. Reported covered intervals assume continuity between adjacent timely observations; this is an estimate, not proof that every intermediate reading was observed. Screen-off/unplugged intervals require both endpoints in that state and exclude detected gaps. Initial and trailing uncovered periods remain unknown. Summary durations partition the recorded session into screen-off/unplugged, other observed intervals, and unobserved time. Explicit JSON null states never count as screen-off/unplugged. A clock discontinuity suppresses rates and marks coverage unreliable; the UI directs users to the timestamp log. Samples are processed in insertion order so a backward clock cannot silently reorder collection history. Battery percentage is coarse and manufacturers may report stale or unavailable values.

Drain is a **derived whole-device estimate** in percentage points/hour between two available, timed battery readings (one reading yields unavailable change/rate), not mAh, app attribution or causal evidence. Charging increases invalidate positive-drain interpretation. The screen-off subset sums nonnegative boundary drops, and cannot identify countervailing charge between samples. Android's `isInteractive()` reports interactive state, not panel/backlight electrical power.

## Data classes

- **Measured:** Android API observations or manufacturer-supplied sensor reports; hardware accuracy is not independently calibrated.
- **Estimated/derived:** sample coverage, interpolated state duration and battery drain. Explicitly named and separated from per-app energy.
- **Imported:** user-confirmed privileged-collector claims; format validation is not authenticity or device-match verification. Excluded from real threshold calculations/rankings.
- **Unavailable/restricted/permission-required/unsupported:** never represented as numeric zero.
- **Simulated:** no app demonstration mode or data generation. Tests use synthetic fixtures only on the disposable test installation, then delete them.

## App/UID activity

`UsageStatsManager.queryAndAggregateUsageStats` reports OS buckets. Bucket coverage can exceed query boundaries, foreground time can include a shared UID's multiple packages, and package visibility may limit labels. It is not battery use. `NetworkStatsManager.querySummary` requests Wi-Fi/mobile per-UID byte buckets. Some releases/devices restrict mobile subscriber access, omit removed UIDs or delay updates. Only returned values are measured; a returned zero is distinct from no returned observation. Rankings use known metrics only. Android does not expose ordinary per-app battery, CPU or wake-lock accounting to this app. No `BATTERY_STATS`, `DUMP`, root or hidden API is requested.

Current data-transfer/drain/temperature thresholds produce retrospective findings on completed sessions, recurring at two or more completed violations. Interrupted/stopped observations remain visible but are excluded from recurrence counts. Temperature alerts also appear during active sampling; lack of samples means alerts can be delayed or missed. Thresholds apply retrospectively to retained records, not solely to the current day. No finding calls an app malicious.

## Components

CPU/GPU temperature and whole-device CPU usage are unavailable through portable public APIs. Readable core-0 sysfs frequency is optional and labelled, without privilege escalation. Process CPU time is this app's CPU delta divided by elapsed time, with 100% representing one logical core, including work by DevicePulse since the prior probe.

Sensor power is a manufacturer rating in mA, not measured drain. Live reads subscribe to only the selected sensor for at most 15 seconds and stop on pause/dismiss. Protected sensors may refuse registration; no body-sensor permission is requested.

Bluetooth state/paired devices and headset/audio-profile status require `BLUETOOTH_CONNECT` on modern Android. Other profiles' connection state may be unavailable. Wi-Fi name/RSSI/frequency/link-speed availability depends on device, OS policy and location grants. Network link speed is not Internet throughput. Location shows service enabled state, not position. NFC, camera and microphone hardware can be absent; no recording/camera capture is performed. Other apps' mic/camera usage is not observed.

RAM and app-data filesystem totals are actual device/OS values, not claims of reclaimable memory. Brightness is a 0–255 setting, not nits. Emulator readings describe the emulator and cannot establish physical conditions.

## Public Android API references

- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/reference/android/os/BatteryManager
- https://developer.android.com/reference/android/os/PowerManager
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/app/usage/NetworkStatsManager
- https://developer.android.com/reference/android/hardware/Sensor
- https://developer.android.com/guide/topics/connectivity/bluetooth/permissions
- https://developer.android.com/training/data-storage/shared/documents-files

These are official reference links. The managed proxy blocked developer.android.com during this session; compile-time API checks, SDK annotations, lint and emulator tests are the directly verified evidence, not a claim of live documentation retrieval.

The Android Battery usage shortcut opens the documented public `Intent.ACTION_POWER_USAGE_SUMMARY` screen where supported, with a general Settings fallback. That OS report can use a different reporting period; DevicePulse does not scrape or read its figures.
