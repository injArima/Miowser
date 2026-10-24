# Video Assistant step 5 — additional browser-backed controls

Follow-up: the user verified this step works perfectly. Transitions and recovery
are implemented in [step 6](video-assistant-step-6-2026-10-24.md).

The user verified step 4 works. Step 5 extends the same live browser video with
supported additional controls, reusing the native player's gesture surface,
feedback, brightness/volume sheet, icons and menus. It does not create a second
decoder, extract/reopen a media URL or change existing Media/Library entry points.

## Controls and capability boundaries

| Control | Browser-backed implementation |
| --- | --- |
| Tap and double tap | Tap shows/hides controls; double tap uses the existing player zone rules for backward/forward skip and play/pause. |
| Horizontal drag | Uses the shared seek recognizer and feedback; sends one seek on release. Uses the current VOD or live DVR window; unknown windows do not acquire invented seek support. |
| Vertical drag | Existing left brightness/right media-volume gestures, respecting system-edge/toolbar guards. Fixed-volume devices omit volume gestures and disable the volume slider. |
| Hold for 2× | Temporary rate on the same HTML video. Release/cancellation restores the fresh pre-hold DOM rate. Fullscreen exit, UI fallback, navigation invalidation and page teardown also release it. An explicit speed choice supersedes a hold; later provider source/rate changes are respected. |
| Brightness & volume | Reuses the existing sheet. Browser brightness is session-only and restores the Activity's original brightness override on exit. Browser adjustments do not save the native player's brightness preference. Volume uses Android's media stream; adjusting it unmutes the selected element. |
| Mute | Toggles the selected element's `muted` property without pausing/seeking/reopening its source. |
| Fit / Zoom / Fill | Sets only the owning video's `object-fit`. Exit restores its exact previous inline value and priority, or removes the added property. |
| Screen lock | Disables controls/gestures; a visible unlock button remains. Back unlocks first. Shared controls become inactive immediately during their hiding animation. |
| Subtitles | Appears only for exposed HTML subtitle/caption tracks; selection and Off leave provider metadata tracks alone. |
| Audio tracks | Appears only when more than one HTML audio track is exposed. Many WebViews/providers do not expose this API. |
| Picture in picture | Explicit PiP button uses the existing Activity PiP implementation and owns one tab/navigation/frame/document/element/fullscreen attempt. No auto-entry is added. |

The shared portrait/landscape gesture zones intentionally follow the existing
player. Controls have visible labelled alternatives to gestures. Extra actions
wrap onto another row on narrow screens instead of overflowing.

Custom subtitle files, subtitle styling/delay/alignment, Library playlists,
provider-specific audio/quality menus, casting and downloads retain their
existing native-player/browser routes. The browser add-on does not display
nonfunctional versions of those options. Adaptive streaming continues under the
website's existing player; Fit/Zoom/Fill changes rendering, not stream quality.

## Ownership, memory and compatibility

- Additional commands use the existing fixed-action bridge and exact fullscreen
  ownership checks. Native track selection only accepts IDs exposed by the
  current owner. JavaScript checks that a selected track still exists.
- Track IDs belong to track objects, not their current list position. Reordering
  cannot select a different language accidentally. Removed track requests fail
  without changing another selection.
- Only the owned fullscreen video's telemetry includes track metadata. It is
  bounded to 16 caption tracks and 8 audio tracks, with 80-character labels and
  16-character language tags, inspecting at most 128 entries per track list.
  No cues/subtitle-file bodies are copied to native memory. Existing per-frame,
  per-video, update-rate and 24,576-character packet limits remain in force.
- Track listeners share the existing batched telemetry scheduler and are removed
  with their video/document. There is no new timer or page-wide scan per track.
- Temporary speed has browser-side cleanup because native ownership can already
  be gone when Compose disposes the overlay. The native release is also guarded
  against replacement elements/fullscreen attempts.
- Native Media3 playback keeps its original gesture call site and defaults. The
  shared surface now accepts position/duration callbacks instead of directly
  requiring a Media3 Player. Its native caller supplies the same player values.
  The adjustment sheet's new callbacks have defaults preserving native behavior.

## Explicit PiP lifecycle

Android pauses an Activity when it enters PiP even though its video remains
visible. Only the exact accepted browser fullscreen owner bypasses the existing
WebView pause in that case; every other tab follows the original lifecycle.
The ownership flag is set before requesting PiP and cleared on rejection,
fullscreen exit, return or stop. Overlay controls/gestures/sheets hide in PiP
without detaching the rich session. On stop/dismissal, the matching video is
explicitly paused and its WebView returns to the background pause path.

No new Activity, system overlay permission, foreground service or background
playback mode was added. Native-player PiP still uses its existing entry point.
Actual WebView/PiP behavior needs the user's device checks: websites, DRM and
WebView implementations can impose restrictions that simulated DOM tests cannot
establish. References: [Android PiP lifecycle](https://developer.android.com/develop/ui/views/picture-in-picture),
[WebView onPause](https://developer.android.com/reference/android/webkit/WebView#onPause()),
[HTML text track API](https://html.spec.whatwg.org/multipage/media.html#text-track-api).

## Verification

Commands used for local validation:

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.*' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Final local verification passed:

- Debug APK packaging and release Kotlin compilation.
- 54 focused JVM tests: 20 controller/ownership/hold tests, 15 session-registry
  tests, 10 existing native-player tests and 9 retained-engine tests. No failures,
  errors or skipped tests.
- 68 simulated-DOM checks, including all earlier checks plus track identity,
  stale track selection, metadata isolation, mute, scale restoration, bounded
  owner-only packets, listener teardown and temporary-speed cleanup paths.
- Source/staged whitespace checks.
- APK inspection: the new overlay and hold commands are packaged; Video Lab is
  absent. The merged manifest retains `com.miowser.app` with no new Activity or
  system-overlay permission from this change.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 27,565,279 bytes. SHA-256:
`0e50fde3e864fd294288b9aa08b7f8d83723922c14ed50042c29fc522f52cf23`.

Implementation checkpoint: `05b4470` on `codex/browser-video-sessions`,
`injArima/Miowser`. Step 4 remains recoverable at `f32da90` (implementation) /
`63eb939` (report).

Device testing remains with the user. No install, uninstall, phone access or
app-data clearing is part of this step.

## User checklist

1. Update the normal app in place. Start a working site video, use the floating
   action and check playback continues at its current position/quality. Recheck
   play/pause, skips, timeline, speed and rotation from step 4.
2. Tap/double tap and drag horizontally in portrait and landscape, while playing
   and paused. Check the final seek and feedback. Repeat on a live/DVR video;
   an unknown seek window should not offer seek actions.
3. Swipe vertically in the left/right gesture zones. Check brightness and volume,
   their sheet sliders, mute/unmute and system volume buttons. Exit fullscreen
   and confirm brightness restores. Check native saved brightness separately.
4. Select 0.75× or 1.5×, hold on the video for 2×, then release. Repeat with Back
   while holding and navigation/exit interruptions; the temporary rate should
   not remain stuck. A later explicit/provider speed change should be respected.
5. Lock controls, try gestures/buttons, then unlock using the icon or Back.
   Confirm hiding controls does not leave tappable buttons during the fade.
6. Select Fit, Zoom and Fill, rotate and exit fullscreen. Check the website's
   original layout, controls and inline video sizing return.
7. Where HTML captions are exposed, switch languages and select Off. Where audio
   tracks are exposed, switch audio. Repeat with dynamically added/removed tracks.
   Provider-specific custom caption/audio UI may not expose either menu here.
8. Tap PiP while playing, then minimize/expand the app. Check the same video/audio
   keeps playing in PiP with no overlay controls or duplicate playback. Dismiss
   PiP and reopen the app: playback should be paused. Try denied PiP permission,
   rotation lock, an iframe video and a low-memory device.
9. Check ordinary website fullscreen, other/background/private tabs, tab closing,
   and the existing Media player, Library playlists, native subtitle settings,
   native gestures/PiP and downloads for regressions.

Steps 6 (transitions/recovery) and 7 (performance/regression hardening) remain
separate future work; no automatic native-player migration is introduced here.
