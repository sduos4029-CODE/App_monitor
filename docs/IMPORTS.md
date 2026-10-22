# Authorized privileged metrics import (schema 1)

History → select session → Import privileged collector metrics → Android document picker → explicit confirmation. Imports are claims from a collector the user already legitimately operates. DevicePulse never elevates privileges, runs a collector, extracts secrets or grants itself privileged Android permissions.

See [example JSON](../examples/privileged-import.json). **It is an example, not observed data.** Change start/end Unix epoch milliseconds to an interval entirely inside the selected recorded session. For an ended session, the interval must end by its end timestamp; for active sessions, it must end by the earlier of the current time and planned target. Use the same device and correct UIDs. The app requires the user to confirm that match.

Required root keys: `schemaVersion: 1`, `classification: "privileged-import"`, nonempty `collector` and `device` descriptions, `startEpochMs`, `endEpochMs`, and `metrics` (1–5000 rows). Maximum document size: 1 MiB. No secrets or identifiers are required.

Each row has a unique nonnegative integer `uid`, optional `package`, and at least one numeric nonnegative finite `batteryMah`, `cpuMs`, or `wakeLockMs`. Omit unavailable values or use JSON null. Numeric strings, negatives, unsupported row fields and duplicate UIDs are rejected. Units: mAh, CPU milliseconds, wake-lock milliseconds. Wake-lock time may overlap CPU time; never sum them into energy. DevicePulse validates structure/ranges, not provenance or truth. Imported values are displayed separately in History and exported as imported; they never overwrite measured data, enter network/foreground rankings or trigger recurring findings/real temperature alerts.
