#!/usr/bin/env bash
# End-to-end test on a fresh emulator:
#   1. install + grant overlay/usage permissions
#   2. UI smoke: open every screen, fail on crash
#   3. renderer probe: draw the overlay view into a bitmap and assert pixels
#   4. visibility: the overlay window must exist while a targeted app is in the
#      foreground, disappear over a non-targeted app, and disappear when paused
#
# Note: adb screencap excludes non-trusted overlay windows by design on
# Android 12+, so the overlay itself can never appear in a screenshot; the
# renderer bitmap + WindowManager state are used as evidence instead.
set -euo pipefail

PKG="com.zcc09.crosshaircompanion"
APK="apks/debug/app-debug.apk"
OUT="e2e-out"
WIN_PAT="Window\{[0-9a-f]+ u0 com\.zcc09\.crosshaircompanion\}:"
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

echo "== renderer probe: draw the overlay view and verify pixels =="
adb logcat -c
adb shell am start -n "$PKG/.RenderProbeActivity"
sleep 3
PROBE=$(adb logcat -d 2>/dev/null | grep "CROSSHAIR-PROBE" | tail -2 || true)
echo "$PROBE"
echo "$PROBE" | grep -q "PASS" || { echo "FAIL: renderer probe did not pass"; exit 1; }
adb exec-out run-as "$PKG" cat files/crosshair_probe.png > "$OUT/crosshair_render.png" || true
ls -la "$OUT/crosshair_render.png" || true

echo "== visibility: overlay must exist over the targeted app (Settings) =="
adb logcat -c
adb shell am start -n com.android.settings/.Settings
sleep 5
adb shell dumpsys window windows > "$OUT/windows_settings.txt" 2>/dev/null || true
VIS=$(grep -cE "$WIN_PAT" "$OUT/windows_settings.txt" || true)
DRAWS=$(adb logcat -d 2>/dev/null | grep -c "CrosshairView draw" || true)
echo "overlay windows while Settings fg: $VIS | overlay view draw logs: $DRAWS"
grep -E "$WIN_PAT" -A 10 "$OUT/windows_settings.txt" | head -24 || true
[ "$VIS" -ge 1 ] || { echo "FAIL: overlay window missing over targeted app"; exit 1; }
[ "$DRAWS" -ge 1 ] || { echo "FAIL: overlay view never drew"; exit 1; }
adb exec-out screencap -p > "$OUT/01_settings_foreground.png" || true

echo "== visibility: overlay must disappear over a non-targeted app =="
adb shell input keyevent KEYCODE_HOME
sleep 5
adb shell dumpsys window windows > "$OUT/windows_home.txt" 2>/dev/null || true
HID=$(grep -cE "$WIN_PAT" "$OUT/windows_home.txt" || true)
echo "overlay windows while launcher fg: $HID"
[ "$HID" -eq 0 ] || { echo "FAIL: overlay still present over non-targeted app"; exit 1; }
adb exec-out screencap -p > "$OUT/02_launcher_foreground.png" || true

echo "== pause toggle (notification action) must hide the overlay =="
adb shell am start -n com.android.settings/.Settings
sleep 4
adb shell am start-foreground-service -n "$PKG/.OverlayService" -a com.zcc09.crosshaircompanion.action.TOGGLE || true
sleep 4
adb shell dumpsys window windows > "$OUT/windows_paused.txt" 2>/dev/null || true
PAUSED=$(grep -cE "$WIN_PAT" "$OUT/windows_paused.txt" || true)
echo "overlay windows after pause toggle: $PAUSED"
[ "$PAUSED" -eq 0 ] || { echo "FAIL: pause toggle did not hide overlay"; exit 1; }

echo "== evidence =="
adb shell dumpsys activity services "$PKG" 2>/dev/null | head -40 > "$OUT/service_dump.txt" || true
adb logcat -d -v time > "$OUT/logcat_full.txt" 2>/dev/null || true
FATALS=$(grep -cE "FATAL EXCEPTION" "$OUT/logcat_full.txt" || true)
echo "fatal exceptions in full logcat: $FATALS"
[ "$FATALS" -eq 0 ] || { echo "FAIL: crash detected in logcat"; exit 1; }

echo "E2E: ALL CHECKS PASSED"
