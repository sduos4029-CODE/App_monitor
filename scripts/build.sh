#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to Android SDK with platform/build-tools 35 installed}"
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
./scripts/test-host.sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
