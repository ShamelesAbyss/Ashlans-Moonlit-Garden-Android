# Privacy

Ashlan’s Moonlit Garden is designed for offline, private play. The Android manifest declares no `INTERNET` permission. The app does not include advertising, analytics, telemetry, accounts, cloud synchronization, or a remote service dependency.

Garden progress, settings, unfinished journal drafts, and journal pages are stored in the app’s private DataStore on the device. Imported saves are validated locally. A pre-import recovery save is also kept locally. Nothing in the app uploads personal writing. No system cloud-backup permission is enabled.

When you choose **Export JSON backup**, Android’s document picker writes a file to the location you select. The exported file can include garden progress, settings, and every journal page. Store, share, or delete that file carefully; anyone who receives it may be able to read the text inside. Import replaces game progress after confirmation and merges journal entries so current pages are not silently discarded.

This document describes the application source and intended behavior; verify the actual APK permissions and behavior for each published build. Report a discrepancy through the repository’s issue tracker without including personal journal content.
