# Video Assistant step 3 — contextual fullscreen button

## Result

Eligible playing videos in normal browser tabs now get a small Miowser button.
Tapping it requests fullscreen on that exact HTML video element. The existing
WebView custom-view path displays the same browser session; no URL extraction,
Media3 instance, download, or new development app is involved.

**This step opens browser fullscreen.** Rich Miowser controls and gestures remain
steps 4–5. Browser controls are temporarily enabled for the requested fullscreen
video and restored to the website's original value on exit/failure. Existing
Media-page actions, native playback, playlists and downloads are unchanged.

## Presentation and selection

- One action per reporting frame/document chooses the most recently touched
  eligible video, otherwise the largest visible playing video in that frame.
- Uses the existing monochrome Miowser mark and the app's current Material
  foreground/background colors. It has an accessible button label and focus
  outline, a 48-pixel apparent touch target and a short opacity entrance respecting
  app/system Reduce motion.
- The button sits near the top-right of the visible video. Placement clips to the
  frame viewport, safe-area insets, overflow-clipping ancestors and applicable
  intersection bounds. VisualViewport changes reposition it and compensate its
  size for pinch zoom.
- Paused/ended/detached, hidden, tiny previews (under 120×80 apparent pixels), or
  fullscreen-disallowed videos do not get an actionable button. Existing
  fullscreen also hides the action.
- The host has no layout footprint and no pointer interception. Only the visible
  button receives touches; hiding also disables its pointer handling explicitly.

## Playback and lifecycle

- A trusted DOM click calls `video.requestFullscreen()` synchronously in the
  owning frame. There is no asynchronous native/JavaScript activation transfer.
- The chosen element and its visible geometry are revalidated on tap. Source,
  position, playback speed, pause state, and selected stream are not reassigned.
  Normal site adaptive-quality changes remain possible; this does not lock ABR.
- Existing fullscreen handling pins the owner tab as before. Back closes that
  view and restores inline controls without a page reload or deliberate seek.
- Synchronous/asynchronous fullscreen failure restores the controls, leaves
  playback alone, and reports a fixed, rate-limited toast. Page-provided error
  strings/URLs are not shown or logged.
- The app enables buttons only on the active foreground browser tab and hides
  them behind Hub, other app pages, browser menus/sheets, native playback, and
  conflicting dialogs. Background tabs stay disabled.
- Frame-specific policy messages use the existing navigation/document binding.
  Navigation invalidation, pagehide, removal, and renderer retirement clean up or
  hide the old action. Back-forward restoration can reconnect it.
- Geometry work is requested by events and coalesced with requestAnimationFrame;
  no continuous frame loop is added. Policy objects are reused across dormant
  tabs, and unchanged policies do not send messages. Existing telemetry cadence
  remains bounded.

## Boundaries

The normal WebView document-start/message capabilities must be available. A
frame's fullscreen permissions policy still applies; a cross-origin iframe
without fullscreen permission cannot be promised this route. Closed shadow-root
players, canvas-only video and site/provider-specific overrides are not established
as supported. The existing manual media workflows remain available.

Native UI gating and script checks are presentation/lifecycle safeguards. Page
JavaScript remains untrusted; no new native media/download command interface or
system overlay permission has been exposed.

The direct-click route follows the
[Fullscreen standard](https://fullscreen.spec.whatwg.org/#dom-element-requestfullscreen)
and Android's existing
[WebChromeClient custom-view handling](https://developer.android.com/reference/android/webkit/WebChromeClient).

## Local validation

- Normal debug APK assembled; release Kotlin compilation passed.
- 34 focused Kotlin tests passed: 15 session registry tests, 10 existing player
  tests, and 9 retained-engine tests.
- 38 simulated-DOM JavaScript checks passed: the original tracker checks plus
  button policy, selection, trusted activation, clipped placement, zoom/insets,
  control restoration, stale clicks, failure, motion preferences and cleanup.
- Inspected the APK and merged manifest: button implementation present; Video Lab
  absent; no `SYSTEM_ALERT_WINDOW` permission added.
- Source whitespace checks passed.
- No app was installed and no device playback tests or app-data changes ran.
  Simulated DOM tests do not verify actual WebView fullscreen or website behavior.

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.LiveVideoSessionsTest' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, normal `com.miowser.app`
package, 28,442,446 bytes. SHA-256:
`6390e99a4e66579a4f8fb786e54abc1593b18637ce7973ef231a52a5e607e148`.

## Device checklist — left to the user

1. Update the normal app in place using the existing signing key; do not uninstall
   or clear data. Play a video on your usual direct-video and iframe/MSE sites.
2. Confirm the Miowser action appears near the top-right of the playing video.
   Pause/end or scroll the video offscreen: the action should hide.
3. Scroll, pinch zoom and rotate. Check placement, touch target, and that ordinary
   page controls/scrolling work outside the button.
4. Tap the action after seeking ahead/changing site quality and speed. Compare
   position/playback on entry, then press Back. There should be no restart at
   zero, duplicate audio or deliberate quality switch. Check inline controls are
   restored and entry/exit can be repeated.
5. Use a page with two videos; touch/play the intended one and confirm its action
   opens that element. Check a permitted cross-origin iframe separately.
6. Open the browser menu, Hub, Settings, Tabs and the existing native player.
   Check the new action does not remain on those surfaces or background tabs.
7. Test a private tab, a dynamically replaced video and navigation away/back.
   Unsupported/forbidden fullscreen should leave the original website usable.
8. Spot-check the existing Media page, Library playlists, native-player gestures,
   subtitles, PiP and downloads, including on a lower-end device.

Implementation checkpoint: `e8af865` on GitHub branch
`codex/browser-video-sessions`. The earlier `63049c5` step 2 implementation remains
available as a recovery point.
