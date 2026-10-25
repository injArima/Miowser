# Native video-assistant display prototype

**Historical prototype:** the decorative touch-through design below was replaced
after emulator feedback. See the [native input correction](native-video-assistant-input-fix-2026-10-25.md)
for the current gesture ownership, verification and remaining playback observation.

## Scope

The user selected **prototype native Android display first**, retaining uniform
single-tap entry. This implements the display behavior measured in the
[Samsung YouTube investigation](samsung-button-placement-youtube-2026-10-25.md).

The native display prototype applies to **main-frame video pages**, including
the kind of mobile YouTube watch page investigated. Embedded frames keep a
reachable version of the existing in-frame overlay and the same one-tap action.
General native coordinate mapping across nested/cross-origin frames is outside
this prototype. A native surface is not offered where viewport geometry cannot
be matched reliably; the working web visual remains available instead.

## Display and input

- A decorative Android `FrameLayout`/`ImageView` layer sits above the retained
  WebView inside its existing pull-to-refresh host. It draws a 40 dp circular
  face with Miowser's mark, app theme colors and 3 dp elevation. The real web
  button retains its 48 dp target, accessibility label and original click action.
- The native layer is non-clickable, non-focusable and excluded from the
  accessibility tree. Actual touches pass through at their original coordinates
  to the real web button below it. No native click-to-JavaScript launch,
  synthesized click, coordinate-forwarding gesture or second page tap is used.
- Before the web face becomes transparent, the native layer acknowledges its
  version after layout/draw scheduling. Stale versions, documents, navigation
  tokens and untrusted frame bindings cannot acknowledge a different target.
  Rejected or unavailable native rendering leaves the web face visible.
- Keyboard/accessibility focus restores the web face and focus outline and
  keeps that control visible until focus leaves it.
- The button anchors below-right of the visible main-frame video, with an 8 dp
  gap and safe-viewport clamping. No website row is inserted and no video or
  page content is moved. As with Samsung, it temporarily overlays page content.
- Show scheduling waits 200 ms; fades last 300 ms; auto-hide begins after
  3 seconds. Expiry disables the tap target immediately, before the visual fade
  completes. Playback-clock updates do not reset the timer.
- Scroll movement hides stale geometry and reveals the control after 200 ms of
  settling. Layout changes schedule bounded checks through 1000 ms. Ordinary
  page taps can reveal an expired button; only tapping the button enters the
  player. Touching the button before expiry keeps its click available.

The original `click(event)` launch function is unchanged from `4f478b9`.
Its source SHA-256 is
`6827a1342585133c2cca41537df3e0bfa77ccbd0f3cb31721de4b87716b08213`.
Existing rich-player controls, native-control suppression, hold feedback,
source/session continuity, DRM handling and the independent ExoPlayer route
remain on their existing paths.

## Lifecycle and cost

Geometry is normalized to the visual viewport and checked against the actual
WebView host dimensions. Invalid coordinates, non-square hit boxes, excessive
sizes or mismatched viewport proportions decline native presentation. This is
especially relevant during rotation, keyboard appearance and viewport changes.

Display messages are separate from the 500 ms playback snapshots and deduplicated
by presentation geometry/state. The native layer does not collect playback
position or recompose the browser screen. It uses hardware view elevation and a
vector drawable rather than a software bitmap layer or another media decoder.
Unchanged playback samples do not resend native geometry.

Navigation, renderer retirement, host release, policy changes and native expiry
clear the native face. A short native safety timeout also clears stale display
if a page stops updating. Host release detaches the WebView from the extra layout
before reuse, preserving the existing retained-engine behavior. Child frames
cannot request native display through their message channels.

## Local verification

- 115 browser-script checks pass. The harness now uses actual timeout deadlines
  so 200 ms/300 ms/3 s display behavior and existing seek/entry timeouts can be
  tested independently.
- 96 focused JVM tests pass, including new geometry alignment, invalid-coordinate,
  keyboard/viewport mismatch and rotation cases.
- Debug/release Kotlin compilation, debug APK assembly and Android-test Kotlin
  compilation are checked locally.
- A real-MotionEvent instrumentation test verifies the decorative Android layer
  forwards the original touch coordinates to an underlying view. It is compiled
  but **not run**; phone testing remains with the user.
- A second instrumentation regression test checks release with SwipeRefreshLayout's
  internal spinner present, so the retained engine is detached from its actual
  content container and can be reattached. This test is also compiled, not run.
- No app installation, device test, app-data clearing or phone configuration
  change is performed for this implementation.

## Device checklist

1. Play a video on the mobile YouTube watch page. Confirm the circular mark
   appears below-right, fades in and disappears after approximately 3 seconds.
2. Tap that mark **once** while visible. Confirm the rich player opens directly
   at the current position and quality, without any toolbar prompt or extra tap.
3. Scroll and let the page settle, including hiding/showing browser toolbars.
   Confirm the visible mark and its actual tap target stay aligned.
4. Pause/play, switch videos on the same page, navigate, switch tabs and return
   from fullscreen. Check for a stale, doubled or invisible clickable control.
5. Rotate, zoom the page and open/close the keyboard. Check alignment and that
   the working web visual remains available if native geometry is declined.
6. Test an embedded/cross-origin player. It should retain the reachable in-frame
   action and still open the rich player with one tap.
7. Check web-page links/scrolling, pull-to-refresh, reduced motion, keyboard/
   accessibility focus, gestures, 2x hold feedback and a subsequent Library video.
