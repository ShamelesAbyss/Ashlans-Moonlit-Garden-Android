# Build the native Android app

## Requirements

- JDK 17 or newer (Gradle uses the JDK 17 bytecode target)
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Gradle Wrapper included in this repository

Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the installed SDK. Then run:

```sh
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:assembleRelease
```

The installable development APK is at `app/build/outputs/apk/debug/app-debug.apk`. It is signed with Android Gradle Plugin's debug certificate and is debuggable. `assembleRelease` validates the optimized non-debuggable variant but, with no production key supplied, its output is unsigned and not installable or publishable as a production app.

The app's stable ID is `com.shamelesabyss.ashlansmoonlitgarden.native`; it is deliberately distinct from the prior edition so both can coexist. It targets API 35 and supports API 26+. No network permission is declared. The user-selected JSON backup pickers use Android's Storage Access Framework; game saves, settings, drafts, and the pre-import recovery copy stay in private app storage.

## Reproducible CI

GitHub Actions runs the JVM domain/migration suite, compiles the Compose instrumentation-test source, assembles the debug variant, verifies package/version, the debug signature, APK alignment, and SHA-256, then uploads build evidence. A tag beginning with `v` reruns the verification job and publishes a clearly labeled debug-signed development release; it is not a production-signed store release. The release includes the APK, matching checksum, signing metadata, and the checked emulator screenshot. A consistent production signing identity is not present; no keystore or credential is checked in.

The workflow provisions Android SDK packages through [`android-actions/setup-android`](https://github.com/android-actions/setup-android) using its documented space-separated package list; it does not request the retired `tools` package. The emulator uses an Android 15 / API 35 AOSP image because the offline game does not depend on Google Play Services.

## What builds do not prove

A successful Gradle build does not establish device-specific gameplay quality, audible playback, foldable behavior on every device, or a production signing identity. The passing main-branch CI run [45306221815](https://github.com/ShamelesAbyss/Ashlans-Moonlit-Garden-Android/actions/runs/45306221815) passed the JVM tests, debug APK checks, ADB install, foreground launch, and raw screenshot capture; the garden image was visually reviewed before publication. The smoke polls for up to 120 seconds for `MainActivity` to reach the resumed state, then waits 60 seconds before capturing the screen and logcat. This confirms launch and a visible screen, not garden interactions or full accessibility coverage.

The Compose `MainActivitySmokeTest` is compiled by CI but is not currently executed: previous software-emulated TCG instrumentation attempts failed before any test completed (0 tests run). The screenshot is a genuine API 35 emulator capture, but it is not hardware or foldable validation. Check the GitHub Actions run linked from a release for the exact test result and artifacts.
