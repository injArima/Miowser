# Suppress duplicate WebView controls during Video Assistant playback

## Cause

The existing bridge set `video.controls = false` after Miowser's overlay attached.
That does not fully describe Chromium's behavior: fullscreen video itself is a
reason to display its built-in controls, even without the controls attribute.
The previous entry path also explicitly enabled them before the overlay attached.
Touches could reach the underlying fullscreen WebView when the app did not handle
a particular region/gesture.

Primary evidence: Chromium's
[HTMLMediaElement::ShouldShowControls](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/renderer/core/html/media/html_media_element.cc)
and its [media-control stylesheet](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/renderer/modules/media_controls/resources/mediaControls.css).
The supplied screenshot is consistent with that path; no device investigation
was performed in this change.

## Fix

- Apply control suppression synchronously before requesting fullscreen, so the
  initial frame does not intentionally expose the browser controls.
- Mark only the owned video with an entry-specific token. A stylesheet hides
  that video's control root/panel/overlay/buttons and disables pointer events.
  Text-track cues are not targeted. There is no page-wide video-hiding rule.
- Prefer a constructable stylesheet in the video's document/shadow root; use a
  local style element on providers without that API. Rules are separate so an
  unsupported pseudo-element selector cannot invalidate the other rules.
- Watch only the selected video's controls attribute. Provider attempts to enable
  it are corrected at most once per animation frame; there is no new polling
  timer or document-wide attribute observer.
- The fullscreen Android host intercepts underlying browser touch input and
  excludes its accessibility/focus descendants while an owned Miowser session is
  active, suspended or reconnecting. Miowser's Compose controls remain siblings
  above the host and use their own existing gesture handling.
- Background/PiP transitions retain suppression until the exact session returns
  or exits. Attaching controls repeatedly reuses one guard instead of leaking
  sheets/observers.
- Exit/failure/page teardown removes the sheet and observer, cancels queued
  correction frames and restores the prior marker, pointer-events value/priority,
  controls state and existing scale restoration. Unavailable-control fallback
  releases the guard and Android touch block so the existing website recovery
  controls are usable.
- Ordinary website fullscreen remains unaffected. The video element, source,
  decoder, position, playback speed and caption selection are not replaced.
  This route does not create ExoPlayer.

## Verification

Commands:

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.*' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.player.PlayerOverlayStateTest' --tests 'com.miowser.app.player.PlayerTrackControlsTest' --tests 'com.miowser.app.player.PlayerSubtitleStyleTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Final local results:

- Debug APK packaging and release Kotlin compilation passed.
- 92 focused JVM tests passed, including the owned-surface input policy. No
  failures, errors or skips.
- 93 simulated browser checks passed, including selected-element scoping,
  immediate suppression, provider re-enabling, bounded correction frames,
  cleanup/restoration, constructable sheets, shadow-root placement, ordinary
  site fullscreen and background/PiP roundtrips.
- Source/staged whitespace checks passed. APK inspection confirmed the native
  surface host and guard script are packaged, with no Video Lab.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 27,471,140 bytes. SHA-256:
`d85705f64eadef8de9dd36cf5bf73eee8e419d827feaca86e88e2f39d1ae18ca`.

Implementation checkpoint: `6477453` on `codex/browser-video-sessions` in
`injArima/Miowser`; shared-UI checkpoint remains `49fca21`.

The DOM checks validate guard scope/restoration/lifetime and protocol behavior;
they do not render Chromium's UA shadow controls. JVM policy checks do not
replace an actual Android touch/accessibility check. Device testing remains
with the user. No device access, install/uninstall or data clearing is included.

## User checks

1. Repeat the reported paused-video case. Only Miowser's play button, timeline,
   timer and top controls should be visible. Check the extra center play button,
   lower WebView seek bar/time/volume/menu and upper browser button are absent.
2. Tap the former browser-control positions, black borders and system-edge
   regions. Use Miowser's seek/play/speed/lock controls. Hidden browser controls
   should not receive actions. Check TalkBack exposes Miowser's controls.
3. Hide/show controls, pause/resume, rotate, open/close subtitles, enter/expand
   PiP and background/return. Check duplicate controls do not reappear and the
   app's gestures and native captions still work.
4. Return to the page: its original controls and pointer interactions should be
   restored with uninterrupted playback. Repeat on a second site and an iframe.
5. Test ordinary website fullscreen and the separate native player; both retain
   their original controls. If control recovery fails, check the existing
   website-control fallback and return action.
