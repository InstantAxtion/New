#!/usr/bin/env bash
# Builds a signed APK without Gradle, using the Android command-line tools.
# On Ubuntu/Debian: sudo apt-get install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
KEYSTORE="${KEYSTORE:-keystore/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
OUT="${OUT:-release/ZombieCitySandbox.apk}"

rm -rf build/gen build/classes build/apk
mkdir -p build/gen build/classes build/apk "$(dirname "$OUT")"

echo "==> Packaging resources"
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F build/apk/unsigned.apk

echo "==> Compiling Java"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
    -d build/classes $(find src build/gen -name '*.java')

echo "==> Dexing"
dalvik-exchange --dex --min-sdk-version=21 --output=build/apk/classes.dex build/classes

echo "==> Building APK"
(cd build/apk && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 build/apk/unsigned.apk build/apk/aligned.apk
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" \
    --v4-signing-enabled false --out "$OUT" build/apk/aligned.apk
apksigner verify "$OUT"
echo "==> Built $OUT ($(du -h "$OUT" | cut -f1))"
