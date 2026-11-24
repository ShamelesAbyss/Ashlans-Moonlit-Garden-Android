#!/usr/bin/env bash
set -euo pipefail

app_id='com.shamelesabyss.ashlansmoonlitgarden.native'
main_activity='com.shamelesabyss.moonlitgarden.MainActivity'
diagnostics='app/build/reports/emulator-smoke'

mkdir -p app/build/screenshots "$diagnostics"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop "$app_id"

if ! adb shell am start -n "$app_id/$main_activity" > "$diagnostics/activity-start.txt" 2>&1; then
    adb logcat -d -b all > "$diagnostics/logcat.txt" || true
    cat "$diagnostics/activity-start.txt" >&2
    exit 1
fi

started=false
for attempt in {1..60}; do
    if adb shell pidof "$app_id" 2>/dev/null | tr -d '\r' | grep -Eq '[0-9]'; then
        started=true
        break
    fi
    sleep 2
done

adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' > "$diagnostics/resumed-activity.txt" || true
if [[ "$started" != true ]] || ! grep -F "$app_id" "$diagnostics/resumed-activity.txt" > /dev/null; then
    adb logcat -d -b all > "$diagnostics/logcat.txt" || true
    echo 'Native activity did not remain running in the foreground.' >&2
    cat "$diagnostics/activity-start.txt" >&2
    cat "$diagnostics/resumed-activity.txt" >&2
    exit 1
fi

sleep 15
adb exec-out screencap -p > app/build/screenshots/native-garden.png
test -s app/build/screenshots/native-garden.png
printf 'APK installed; MainActivity remained foreground; emulator screenshot captured.\n' > "$diagnostics/result.txt"
