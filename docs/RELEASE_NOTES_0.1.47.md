# Miowser 0.1.47 (code 48)

- Improved WebView video detection for late-loading players and pages with many embedded frames.
- Improved selection when several videos are playing, giving the visible video priority while preserving recent user interaction.
- Prevented stale navigation messages from disconnecting the current detector.
- Improved floating-button recovery after leaving fullscreen.
- Preserved single-tap entry, companion-pane episode switching, current episode information, and the existing playback engine.

Validation: 142 script regression checks and 704 unit tests passed. Debug APK
assembly and release Kotlin compilation passed. All three focused emulator tests
passed, covering native input ownership, host cleanup, and a real Chromium trusted
fullscreen tap after scrolling. These fixtures do not establish live-site coverage.

Suggested device checks:

- Play on Anikoto and YouTube without reloading to obtain the button.
- In the companion pane, choose another episode and tap its floating button once; check sound, title, quality and playback time.
- Switch episode/server quickly, navigate back/forward, switch tabs, and return from fullscreen.
- Check pages with several previews and a main video; the button should open the intended video.
- Check pause, seeking, subtitles, speed, rotation, background audio and PiP remain unchanged.
