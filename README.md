# Miowser

Miowser is an Android browser focused on finding, playing, downloading, and organizing web media. It includes a Library with folders, a subtitle-capable player, bookmarks, speed dial, site controls, filter lists, and optional local backups.

## Build

- Android Studio with Android SDK 37 and JDK 17 or newer
- Minimum Android API 26; target API 37

Open the project in Android Studio, or run `./gradlew :app:assembleDebug`. Release builds use R8 and resource shrinking. The release AAB and APK are intentionally unsigned until the publisher provides an upload key and configures Play App Signing.

## Privacy and publication

The [privacy policy](docs/privacy-policy.html) is available in the app from the Hub menu. The [publication handoff](docs/PUBLICATION_2026-10-13.md) records the remaining signing, store, and device-check steps. Downloaded media uses public device storage; backup files are user-selected and unencrypted.

This repository does not contain a release signing key. No source-code license has been selected yet; publication of the repository does not grant additional reuse rights.
