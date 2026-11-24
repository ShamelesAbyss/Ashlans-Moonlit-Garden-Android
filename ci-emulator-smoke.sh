#!/usr/bin/env bash
set -euo pipefail

app_id='com.shamelesabyss.ashlansmoonlitgarden.native'
main_activity='com.shamelesabyss.moonlitgarden.MainActivity'
diagnostics='app/build/reports/emulator-smoke'

mkdir -p app/build/screenshots "$diagnostics"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop "$app_id"
: > "$diagnostics/resumed-activity.txt"

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

foreground=false
if [[ "$started" == true ]]; then
    for attempt in {1..24}; do
        adb shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' > "$diagnostics/resumed-activity.txt" || true
        if grep -F "$app_id" "$diagnostics/resumed-activity.txt" > /dev/null; then
            foreground=true
            break
        fi
        sleep 5
    done
fi

if [[ "$started" != true ]] || [[ "$foreground" != true ]]; then
    adb logcat -d -b all > "$diagnostics/logcat.txt" || true
    echo 'Native activity did not reach the foreground within the emulator startup wait.' >&2
    cat "$diagnostics/activity-start.txt" >&2
    cat "$diagnostics/resumed-activity.txt" >&2
    exit 1
fi

sleep 60
adb logcat -d -b all > "$diagnostics/logcat.txt"
sleep 2
adb exec-out screencap -p > app/build/screenshots/native-garden.png
test -s app/build/screenshots/native-garden.png
printf 'APK installed; MainActivity reached foreground; emulator settled for 60 seconds; screenshot and logcat captured.\n' > "$diagnostics/result.txt"
