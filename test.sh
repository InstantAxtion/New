#!/usr/bin/env bash
# Runs the game's tests on a desktop JVM (no Android device needed).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build/test
mkdir -p build/test
javac -nowarn -encoding UTF-8 -d build/test $(find tests/stubs tests/src -name '*.java') \
    $(find src -name '*.java' ! -name MainActivity.java ! -name Sound.java)
java -Djava.awt.headless=true -Xmx2g -cp build/test com.instantaxtion.zombiesandbox.GameTests
