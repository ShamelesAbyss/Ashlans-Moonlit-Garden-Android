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

GitHub Actions runs the JVM domain/migration suite, assembles debug and release variants, verifies package/version, the debug signature, APK alignment, and SHA-256, then uploads build evidence. Version tags publish an accurately labeled debug-signed development APK. A consistent production signing identity is not present; no keystore or credential is checked in.

The workflow provisions Android SDK packages through [`android-actions/setup-android`](https://github.com/android-actions/setup-android) using its documented space-separated package list; it does not request the retired `tools` package.

## What builds do not prove

A successful Gradle build does not establish device-specific gameplay quality, audible playback, foldable behavior on every device, or a production signing identity. No Android hardware/emulator test is claimed unless its result is attached to that build. Check the GitHub Actions run linked from the release for the exact test result.
