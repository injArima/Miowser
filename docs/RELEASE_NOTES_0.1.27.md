# Miowser 0.1.27 (code 28)

This update keeps the 0.1.23 feature set. Code 28 lets a signed APK update an installed code 25–27 APK without an Android version-code downgrade. The APK must use the same signing key and package name.

- Crash reports are saved locally by default, including for existing installs after a one-time settings migration. The switch in Settings remains available to turn this off.
- Out-of-memory crashes use a small fallback report. WebView renderer crashes are recorded without page addresses or titles. On Android 11 and later, Miowser also records qualifying Android process-exit reasons after the next launch when no Java report was saved.
- Saved Library records from later app versions that this baseline cannot display remain intact during ordinary Library edits. Clear Library still removes them; Undo restores them.
