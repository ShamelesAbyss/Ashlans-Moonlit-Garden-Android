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

garden_visible=false
for attempt in {1..12}; do
    adb shell uiautomator dump --compressed /data/local/tmp/moonlit-window.xml > "$diagnostics/uiautomator-dump.txt" 2>&1 || true
    adb exec-out cat /data/local/tmp/moonlit-window.xml > "$diagnostics/window.xml" 2>/dev/null || true
    if grep -Fq 'The cottage garden' "$diagnostics/window.xml" && grep -Fq 'Tend the nine beds' "$diagnostics/window.xml"; then
        garden_visible=true
        break
    fi
    sleep 10
done

if [[ "$garden_visible" != true ]]; then
    adb logcat -d -b all > "$diagnostics/logcat.txt" || true
    adb exec-out screencap -p > app/build/screenshots/native-garden.png || true
    echo 'Compose garden text was not visible in the emulator accessibility hierarchy.' >&2
    exit 1
fi

adb logcat -d -b all > "$diagnostics/logcat.txt"
sleep 2
adb exec-out screencap -p > app/build/screenshots/native-garden.png
test -s app/build/screenshots/native-garden.png
printf 'APK installed; MainActivity remained foreground; garden and nine-bed controls were visible; emulator screenshot captured.\n' > "$diagnostics/result.txt"
