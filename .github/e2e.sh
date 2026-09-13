#!/usr/bin/env bash
# End-to-end test: install the app on a fresh emulator, smoke-test every UI
# screen, configure one targeted app (Settings) and one non-targeted state
# (launcher), start the overlay service, and verify the crosshair is
# composited at screen center only while the targeted app is in the foreground.
set -euo pipefail

PKG="com.zcc09.crosshaircompanion"
APK="apks/debug/app-debug.apk"
OUT="e2e-out"
mkdir -p "$OUT"

echo "== install =="
adb install -r "$APK"

echo "== grant special permissions =="
adb shell appops set --uid "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell appops set --uid "$PKG" GET_USAGE_STATS allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true

echo "== first launch (creates data dir + default profile) =="
adb shell am start -n "$PKG/.MainActivity"
sleep 5
adb shell am force-stop "$PKG"

echo "== write test config via run-as =="
python3 .github/make_test_prefs.py > "$OUT/test_prefs.xml"
adb push "$OUT/test_prefs.xml" /data/local/tmp/gn_xhair.xml
adb shell run-as "$PKG" mkdir -p shared_prefs
adb shell run-as "$PKG" cp /data/local/tmp/gn_xhair.xml shared_prefs/crosshair_companion.xml
adb shell run-as "$PKG" cat shared_prefs/crosshair_companion.xml > "$OUT/verify_prefs.xml"
head -c 400 "$OUT/verify_prefs.xml"; echo

echo "== keep screen awake, start app + overlay service =="
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell settings put system screen_off_timeout 1800000
adb shell am start -n "$PKG/.MainActivity"
sleep 4
adb shell am start-foreground-service -n "$PKG/.OverlayService" || true
sleep 3

echo "== UI smoke: open every screen, fail on crash =="
adb logcat -c
adb shell am start -n "$PKG/.GamesActivity"
sleep 3
adb shell am start -n "$PKG/.StylesActivity"
sleep 3
adb shell am start -n "$PKG/.EditorActivity" --es profileId p-test
sleep 4
TOP=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 -E "topResumedActivity|mResumedActivity" || true)
echo "top activity: $TOP"
echo "$TOP" | grep -q "EditorActivity" || { echo "FAIL: EditorActivity not resumed"; exit 1; }
CRASH=$(adb logcat -d 2>/dev/null | grep -E "FATAL EXCEPTION|E AndroidRuntime" || true)
if [ -n "$CRASH" ]; then
  echo "$CRASH" | head -40
  echo "FAIL: app crashed while opening screens"
  exit 1
fi
echo "UI smoke OK - all screens opened without crashes"

echo "== screenshot 1: over Settings (targeted game) =="
adb shell am start -n com.android.settings/.Settings
sleep 5
adb exec-out screencap -p > "$OUT/01_settings_visible.png"

echo "== screenshot 2: over launcher (not targeted) =="
adb shell input keyevent KEYCODE_HOME
sleep 5
adb exec-out screencap -p > "$OUT/02_home_hidden.png"

echo "== evidence: window + service state =="
adb shell dumpsys window windows 2>/dev/null | grep -i "crosshair" | head -20 > "$OUT/window_dump.txt" || true
adb shell dumpsys activity services "$PKG" 2>/dev/null | head -40 > "$OUT/service_dump.txt" || true
adb logcat -d 2>/dev/null | grep -i "crosshaircompanion" | tail -60 > "$OUT/logcat.txt" || true
echo "-- window dump --"; cat "$OUT/window_dump.txt"

echo "== verify crosshair pixels =="
python3 .github/verify_screenshots.py
