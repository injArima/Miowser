# Miowser publication handoff

## Confirmed identity and distribution

- Publisher: **1nja**; privacy contact: **injamamul2k@gmail.com**.
- Package ID: **`com.miowser.app`**. Keep this ID fixed for updates.
- First public release: versionCode 1, versionName 0.1.0. Increment versionCode for every subsequent upload.
- Channels: Google Play and direct APK, with the **same installed app signing certificate**.

## Signing and same-app updates

For this first release, enroll in Play App Signing and let Play generate the permanent **app signing key**. Create a separate **upload key** and keep its keystore and passwords offline and backed up. Build the release AAB with `./gradlew :app:bundleRelease`; sign a copy for Play using `scripts/Sign-PlayUploadBundle.ps1` and the two environment variables named in that script. The script never creates a key or puts a password in command-line arguments.

One way to create the upload key with the installed JDK is `keytool -genkeypair -v -keystore miowser-upload.jks -alias miowser-upload -keyalg RSA -keysize 4096 -validity 10000`. Let `keytool` prompt for passwords. Generate and store the key **outside the repository**; make at least one secure offline backup. In PowerShell, set the two password environment variables in the current shell, then run `./scripts/Sign-PlayUploadBundle.ps1 -Keystore <absolute path> -Alias miowser-upload`. Clear those variables from the shell when finished. Do not put passwords in a committed script or CI log. If Play App Signing is instead configured with a publisher-owned app signing key, revisit the direct-APK instructions below; the two signing models cannot be casually interchanged after launch.

After Play accepts an internal-track AAB, download a **Play-signed universal APK** from Play Console's app bundle explorer for direct distribution. Check its signing certificate against the Play app signing certificate, install/update-test it, and distribute that APK. **Do not distribute an APK signed by the upload key**: Play-delivered apps use the different app signing key, so those APKs would not update each other. Keep the private upload key and any signed bundles out of Git.

The release Gradle variant intentionally has no local signing config. `assembleRelease` therefore produces an **unsigned APK for build verification only**. It is not the direct-distribution artifact. Google Play requires an AAB for a new app.

## Privacy policy and store presence

The single source of policy text is `app/src/main/assets/privacy_policy.txt`; the Hub menu opens it in-app. Run `scripts/generate_privacy_html.py` after policy edits to update `docs/privacy-policy.html`. Publish that HTML at a stable, publicly accessible HTTPS URL (for example, a project Pages site), then use that exact URL in the Play listing and Data safety section. A local HTML file is not a public URL. Verify it loads without sign-in and on a phone browser. Reconcile the Play Data safety answers with the final build and hosted policy.

Android cloud backup and device-transfer extraction are disabled in the manifest; the app's explicit Backup & Restore feature is the intended way to copy settings and references. Verify this behavior during the signed-release device pass.

The listing draft, store icon, and feature graphic are in `release-assets/`. Supply real device screenshots; at least two are required. Verify the icon and graphics after upload, since store crops can differ. Review the final wording and declare the APK-opening browser feature when completing Play's `REQUEST_INSTALL_PACKAGES` form.

## Publisher/device release gate

The user handles device and emulator checks. Before either channel goes public, test a **signed release** on real devices. Cover at least a current Android device and an Android 8–9 device for legacy storage behavior. Capture real, redacted screenshots for the listing.

1. **Install and update:** Fresh install, relaunch, background/foreground, rotation, update direct Play-signed APK → Play and Play → newer direct Play-signed APK. Data and downloaded media should persist through updates. A mismatched signing certificate must be caught before distribution.
2. **Browser:** Open/reopen tabs, normal and private navigation, bookmarks, speed dial, site settings, long-press links, back gesture, and tab actions. Test slow and offline pages, plus permission-denied paths for sites.
3. **Media detection and player:** Detect a playable page, add to Library before playback, stream from a download prompt, seek by slider and gestures, play/pause, buffering indicator, title overlay, elapsed/remaining timer, audio/subtitle tracks, SRT/VTT selection and styling, and Library playlist/next item. Check Wi-Fi and mobile buffering on a real network.
4. **Downloads:** Start, pause, dismiss a paused notification, resume, finish, retry a failed download, and open a downloaded item. Check a user-initiated APK open and Android's install permission prompt. Reject permission and verify that browsing still works.
5. **Library:** Enter folders and subfolders with back gesture, count entries, change default folder, manual moves, edit folder rules, play queue, missing-source handling, remove/re-add references. Verify no unexpected reclassification.
6. **Backup and privacy:** Set a custom backup folder and interval, create/restore a backup on a clean install, confirm normal history/tabs/cookies/media files stay excluded, and review the backup ZIP's unencrypted warning. Open Hub → three-dot menu → Privacy policy and Third-party notices, scroll each page, and return with both arrow and gesture.
7. **Storage retention:** Download a test media file, uninstall Miowser, and verify the file remains in public storage. Reinstall and confirm app-private records start clean while the public file remains accessible through a file manager.
8. **Accessibility and appearance:** Check light/dark mode, larger font/display sizes, TalkBack labels and focus order, small screens, and both gesture and three-button navigation.

In Play Console complete app setup, current policy declarations, content rating, target audience, account verification, and testing requirements. New personal developer accounts may require a closed test with 12 opted-in testers for 14 continuous days. Use an internal track before public rollout. Direct APK distribution must point users to the Play-signed universal APK and its checksum.

## Local verification on 13 October 2026

- `:app:testDebugUnitTest`: **508 tests, 0 failures**.
- `:app:lintDebug`: **0 errors, 80 warnings, 5 hints**; `:app:lintRelease`: **0 errors, 79 warnings, 5 hints**. The remaining warnings are mainly Kotlin extension/API modernization and dependency updates; the WebView renderer warnings are false positives because its clients already override `onRenderProcessGone`.
- `:app:assembleRelease` and `:app:bundleRelease`: successful with R8/resource shrinking. Outputs are `app/build/outputs/apk/release/app-release-unsigned.apk` and `app/build/outputs/bundle/release/app-release.aab`. Both are unsigned; they are build evidence, not distributable packages.
- `Sign-PlayUploadBundle.ps1` was exercised against the bundle with a disposable one-day local key; the signed copy verified and the test key/artifact were removed. The publisher's actual upload key has not been created.
- No emulator, ADB, device, or Play Console checks were run; those belong to the publisher's device/release gate above.

## Policy references

- [Privacy policy and Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Play App Signing and key options](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en-en)
- [App bundle and upload signing](https://developer.android.com/studio/publish/upload-bundle)
- [Install-packages permission policy](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en)
- [Store listing graphic requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
