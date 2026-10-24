# Video Assistant step 4 — browser-backed core player controls

Follow-up: the user verified this step works perfectly. Supported additional
controls are implemented in [step 5](video-assistant-step-5-2026-10-24.md).

## Result

Videos opened with the floating Miowser action now receive a core player overlay
that controls the existing browser video. It reuses the app's transport buttons,
buffered timeline, timer formatting, speed-menu components, rotation button and
player theme.

Implemented controls: play/pause, ±10-second skip, timeline seek, speed selection,
manual rotation/rotation lock, Back, and show/hide controls with timed hiding
while playing. The current video dimensions are displayed; they are not a claim
of a specific adaptive representation/codec/bitrate.

**This is still an add-on.** Ordinary website fullscreen does not acquire the new
overlay. Existing Media-page/native-player entry points, downloads, Library
playlists, subtitle settings, native gestures, audio-focus choices and PiP
behavior are not rerouted or merged. Step 5 covers additional controls and
gestures for the browser-backed route; unsupported options are not shown as
functional buttons here.

## Ownership and attachment

- The floating action retains its frame/element identity and gives each
  fullscreen entry an attempt number. Its fullscreen event publishes a fresh
  bounded snapshot to the existing frame-specific channel.
- Native controls activate only when both that owner and the app's accepted
  fullscreen custom View exist. Event order between the two callbacks is handled.
- A stable per-tab controller exposes state and fixed media actions. Commands
  bind to tab/navigation/frame/document/element plus fullscreen attempt, with a
  monotonically increasing command ID. JavaScript also requires that the same
  element is still connected and actually fullscreen.
- Browser controls remain enabled until the rich overlay attaches. A confirmed
  attachment hides browser controls; exit restores the website's original value.
  Failed/timed-out attachment releases the overlay and requests browser controls
  again. No source restart or new decoder is used for this fallback.
- The retained WebView continues running. The current source tab remains
  protected by the existing fullscreen retention path. The native-player path
  that pauses page media is not used.

## Seeking and command confirmation

- Commands and their replies bypass the ordinary telemetry batching delay.
  State replies are fresh full-frame snapshots, preserving sibling videos.
- Skip deltas apply to the page's fresh `currentTime`, rather than a sampled
  native position. Timeline drags send a seek only on release.
- Actual seekable ranges clamp targets, including holes and live DVR bounds.
  A finite-duration VOD with metadata but no reported ranges still attempts a
  seek. This avoids the prototype's blanket empty-range rejection.
- Seeks report their started position immediately, then confirm using `seeked`
  and the actual position. Ignored/rejected requests produce a recoverable
  message. They do not fabricate success, seek back, reload or pause the source.
- Seek confirmation has a seven-second page deadline and an eight-second native
  deadline. A slower network/decoder may continue the browser's seek after the
  confirmation deadline; no forced rollback is introduced.
- Play promises and applied speed are checked. Websites/providers can reject
  requests; the overlay reports failure instead of altering the media source.
- New commands supersede older pending commands in the same family. Late
  responses cannot finish a newer operation or take ownership of a later video.
- Pending state/handlers expire or clear on navigation, frame/renderer retirement
  and fullscreen exit. Pagehide removes seek listeners and timers.

The command protocol has no arbitrary script, URL-fetch, download, playlist or
native-resource operation. Page metadata remains untrusted and bounded.

## UI and compatibility

- Controls sit above the retained video View. The blank tap target and actual
  controls are siblings, avoiding the ancestor input-consumption bug previously
  fixed in the player.
- Only the overlay collects playback ticks; BrowserScreen and the WebView host
  do not collect per-position state. Existing telemetry limits remain in place.
- Status/navigation/cutout insets and the existing motion preference are used.
  The timeline and controls have their existing interaction/accessibility owners.
- Rotation is explicit, handles the current orientation without recreating the
  retained session, and restores the previous orientation request on exit if
  changed by this overlay.
- Four existing player helpers changed visibility to `internal`. The shared
  center controls gained an optional seek-enabled flag, defaulting to the
  original native-player behavior. No native playback backend/actions changed.

## Local verification

- Normal debug APK packaging and release Kotlin compilation passed.
- 47 focused JVM tests passed: 13 new controller tests, 15 session-registry tests,
  10 existing player tests and 9 retained-engine tests.
- 55 simulated-DOM checks passed, including all step 3 checks plus owned command
  routing, control attachment/release, finite-duration empty-range seeking, fresh
  skips, live-window holes, asynchronous/ignored seeks, command supersession,
  rejected requests and pagehide cleanup.
- APK/manifest inspection confirmed the overlay/control script is present, no
  Video Lab is packaged, and no system-overlay permission was added.
- Source whitespace checks passed. No device test, installation, uninstall or
  app-data modification was performed for this step.

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.*' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' --console=plain --offline
.\gradlew.bat :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

The simulated DOM and JVM tests do not establish actual WebView control latency,
seek support, touchscreen behavior or provider compatibility. Device checks
remain with the user, especially because the first prototype's seek problem was
observed on a device.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, normal `com.miowser.app`
package; 27,405,604 bytes. SHA-256:
`50811917d0c4858318cc56a2679bd356c7a1232e4f1f3f66d1633e2daec153f1`.

## User checklist

1. Update the normal app in place, keeping its data. Start the same videos that
   worked in step 3 and tap the floating action. Confirm only one control set
   remains once the overlay attaches, with playback at the same position.
2. Pause/resume several times, including rapid alternating taps and a buffering
   video. Check responsiveness and absence of duplicate playback/audio.
3. Test both skip buttons and drag the timeline forward/backward, both while
   playing and paused. Repeat on the site where the prototype could not seek.
   Report any failure message and whether the actual position changes.
4. Test 0.5×, 1×, 1.5× and 2× playback and confirm the real audio/video speed.
5. Hide/show controls; check timed hiding while playing, and the speed menu and
   slider remain touchable. Use the shared rotation button, hold to lock/unlock,
   then press Back. Check the previous app orientation and website controls.
6. Repeat entry/exit, cross-origin iframe and MSE/blob playback, a live/DVR
   source, and a page with two videos. Confirm the selected element is controlled.
7. Enter fullscreen through the website's own button: its original controls
   should remain. Change site quality before entry and check normal continuity.
8. Check private tabs, background/return, navigation and tab closing. Spot-check
   the existing native Media player, Library playlists, subtitles, gestures, PiP
   and downloads, including on the lower-end device.

Do not expect the additional browser-route gestures/subtitle/PiP/playlist menus
in this step. Those are separate from the existing native player's features and
remain for the next approved step.

Implementation checkpoint: `f32da90` on `codex/browser-video-sessions` in
`injArima/Miowser`. Step 3 remains recoverable at `e8af865`.
