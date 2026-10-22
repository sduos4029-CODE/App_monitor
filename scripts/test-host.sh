#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/build/host-tests
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d app/build/host-tests app/src/main/java/dev/devicepulse/Logic.java tests/LogicTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp app/build/host-tests LogicTest
