# Video-assistant size and fullscreen playback continuity

## Changes

- Native and web-rendered button faces increase from 40 to 48 dp. The mark
  increases from 24 to 28 dp, and the touch target increases from 48 to 60 dp.
  The same measurements apply to embedded-frame buttons and adapt to page zoom.
  Existing viewport clamping, native gesture ownership and single-tap launch
  remain in place.
- The shared launch path now records whether the existing video was playing,
  together with its source and media-object identity, before fullscreen entry.
  This covers both the native button and the original web button.
- If the same element pauses while its fullscreen surface is being established,
  recovery waits for media data and any pending seek, then calls that element's
  normal `play()` method. It does not reload the page, assign a media source,
  seek, change quality or create ExoPlayer.
- Recovery is event-driven and scoped to the current entry. Healthy playback
  ends observation after a 350 ms settling window without an extra `play()`.
  A successful recovery also ends observation. An entry can wait at most eight
  seconds for its media; only an `AbortError` from an interrupted load can retry,
  on a subsequent media event, with at most three attempts.
- Explicit play/pause commands cancel entry recovery, including a pause whose
  source revision has become stale. Exiting fullscreen, ordinary host suspension,
  hidden documents, fallback, source/object replacement, ended/error media and
  teardown cancel recovery too. Explicit PiP suspension keeps its existing
  playback policy. Permission/DRM-related play rejection is not retried.

The pause mechanism is a correction based on the earlier emulator observation:
YouTube temporarily emptied and restored its existing video during entry, then
remained paused. This implementation has not been verified on YouTube yet.

## Verification status

The user explicitly requested to perform the checks themselves. No build, test
runner, emulator interaction or device installation was performed for this change.
The script contracts and geometry expectations were updated, with additional
entry-recovery cases prepared for the user to run. Earlier checkpoint test
results do not validate this change. Build a fresh APK before testing; the
existing local APK predates these edits.

## User checks

1. Check the larger circle and touch area on YouTube and an embedded player,
   including after scrolling, rotation and page zoom. A single tap should open
   the player; dragging from the button should not click or scroll the page.
2. Enter fullscreen while YouTube is playing. It should continue at the same
   position and quality after any brief loading transition, without another tap.
3. Pause immediately during entry/loading. It should remain paused when ready.
   Check explicit play, seek, playback speed, back and PiP as well.
4. Exit fullscreen, switch videos, or background the app during entry. An old
   recovery must not start playback later or affect a replacement video.
5. Check a video that already transitions smoothly and a source that refuses
   autoplay: normal playback/manual controls should remain available.
