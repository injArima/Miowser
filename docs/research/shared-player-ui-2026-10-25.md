# Shared original player UI with separate playback engines

Follow-up: duplicate WebView controls and their underlying input are addressed
in [the native-control suppression fix](browser-native-controls-2026-10-25.md).

The browser-backed player now uses the original player's actual control
components and panel layouts. The existing native player retains Media3;
browser playback continues on the selected HTML video in its retained WebView.
No ExoPlayer, Media3 playback session, second renderer, media URL reopening or
source restart was added to browser playback.

## UI changes

| Area | Shared implementation / browser behavior |
| --- | --- |
| Top bar | Original right-aligned PiP, rotation, speed indicator, lock and Back layout. Download and Library playlist controls are omitted for this browser session. |
| Main controls | Original play/pause, buffering indicator and skip buttons with the same spacing. Skip buttons are hidden when there is no known seek window. |
| Bottom controls | Original bottom title, source/resolution line, buffered timeline, horizontally scrolling action row and right-hand timer. No separate browser-only action row remains. |
| Timing | Original total/remaining timer interaction; the timer previews the drag position. Unknown-duration streams display Live without a misleading remaining-time action. |
| Speed / adjustments | Original speed menu, choices and brightness/volume sheet. Browser mute/unmute is retained in the speed menu. |
| Scaling | Original fit-screen icon: tap cycles Fit/Zoom/Fill, hold opens the original menu. Browser commands still apply CSS scaling to the current HTML video. |
| Subtitles | Original subtitle workspace layout, choice rows, modal animation, input isolation and close/focus handling. Browser shows available caption tracks and Off; unsupported Auto, Inspect, Settings, custom-file and delay tools are omitted. |
| Audio | Original track-sheet container and choice rows, shown only when multiple HTML audio tracks are exposed. No fabricated Auto/provider-specific choices. |
| Gestures / lock | Existing shared recognizer and feedback. Lock/unlock placement, bounded gradient scrims and the four-second hiding interval follow the original UI. |
| Insets | Original status/navigation/cutout padding is owned by the shared components, removing the separate browser layout's outer inset padding. |

Unsupported options disappear from the browser UI. Existing native-player
controls, subtitle inspection/adjustment/appearance, track selection, downloads
and Library playlists retain their own behavior and handlers. Ordinary website
fullscreen does not acquire the Video Assistant UI.

## Separation and rendering work

- `PlayerChrome.kt` contains the original shared top/center/bottom controls,
  scale labels, scrims, buttons, timeline and timer. It imports no Media3 API.
- `PlayerTrackPanels.kt` contains shared subtitle workspace/choice presentation
  and the original audio/source sheet container, with optional content sections.
  It imports no Media3 API. Native subtitle content still supplies all three
  original sections; the browser supplies only what its session supports.
- The native screen remains responsible for ExoPlayer creation, playback
  lifecycle, native track/quality actions and native timeline sampling. Its
  existing scale preference names and Media3 resize-mode mapping are retained.
- The browser control tree observes a distinct presentation containing metadata,
  capabilities, errors and playback/recovery changes. Position, buffer and frame
  geometry updates are excluded from that presentation.
- Only browser timeline/timer leaves collect clock samples. Their scrub preview
  is shared without lifting clock values into the full control tree. Drag state
  records the owner, fullscreen attempt and source revision; release cannot seek
  a replacement source/entry. A test checks 1,000 clock/buffer/range updates leave
  control presentation equal while actual playback/capability changes propagate.
- No new media polling clock or page telemetry timer was added. Existing step 6
  ownership, command confirmation, source-revision fencing, background/PiP
  recovery and native-control fallback remain in place.

These changes avoid unnecessary UI invalidation and duplicated layouts. Actual
frame-time, memory and playback improvements are not measured by local JVM/DOM
checks and remain device checks, particularly on lower-end hardware.

## Local validation

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.*' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.player.PlayerOverlayStateTest' --tests 'com.miowser.app.player.PlayerTrackControlsTest' --tests 'com.miowser.app.player.PlayerSubtitleStyleTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Final local verification passed:

- Normal debug APK packaging and release Kotlin compilation.
- 91 JVM tests: 25 controller, 6 presentation/scrub, 7 recovery, 17 session,
  9 retained-engine, 10 existing player, 8 overlay-state, 5 native track and
  4 subtitle-style tests. No failures, errors or skips.
- All 84 simulated browser-script checks from the previous implementation.
- APK inspection confirmed the shared UI, presentation and scrub classes are
  packaged, with no Video Lab. Package remains `com.miowser.app`.
- Source inspection confirmed shared UI files import no Media3 API and the
  browser overlay does not construct or call the native playback screen/session.
- Source and staged whitespace checks passed.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 28,581,363 bytes. SHA-256:
`91fdb86e5b9d582b89a5cdf059657a8c0e57252ac59036ab820c3997a69ada7d`.

Implementation checkpoint: `49fca21` on `codex/browser-video-sessions` in
`injArima/Miowser`. The previous step 6 implementation remains recoverable at
`19254f9`, with its report checkpoint at `45e8143`.

No device test, installation, uninstall or data clearing is part of this change.

## User checks

1. Open a website video through the floating action. Compare portrait/landscape
   placement with the native player: title above the timeline, original button
   order, timer at the right, top actions and gradients. Confirm unsupported
   actions are absent and narrow layouts scroll the bottom controls.
2. Check play/pause, skips, horizontal seek, timer preview while dragging,
   remaining/total time, speed choices and 2× hold/release. Repeat on a live/DVR
   source and a source with no known seek window.
3. Tap/hold the aspect button; test mute in the speed menu, brightness/volume,
   rotation while locked/unlocked and control lock. Confirm brightness and the
   website's original sizing/controls restore on exit.
4. Open subtitles and audio where available. Select tracks and Off, scroll,
   close with Back/the close button, reopen rapidly and check touches do not
   operate the video underneath. Unsupported subtitle sections should not show.
5. Recheck PiP, background/return for over a minute, source changes during a
   drag, page navigation and recovery from step 6. Confirm one playback session
   continues with no duplicate audio or automatic URL handoff.
6. Check the native player separately: Library playlists, downloads, subtitle
   Tracks/Inspect/Settings in both orientations, audio/quality selection,
   gestures, scaling and PiP. These exercise the shared UI's native adapters.

This is the approved shared-UI follow-up. It does not mark the separate step 7
performance/regression-hardening plan complete.
