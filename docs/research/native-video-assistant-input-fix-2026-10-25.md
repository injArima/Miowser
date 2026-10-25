# Native video-assistant input correction

## Problem

The initial native prototype painted the icon above a transparent web button.
Android deliberately passed every gesture through, so a visible native face did
not itself own input. Changes to the web hit target or visibility could send a
touch to page content. Auto-hide also disabled the web target before the native
300 ms fade finished. Its original view test proved touch-through, which was not
sufficient verification of the required button behavior.

## Current behavior

- The native layer captures DOWN within the full 48 dp target and owns the
  complete gesture. Input outside the target follows its existing path.
- A drag, multiple pointers, cancellation, rejected/stale preparation, or a
  touch on a fading face is consumed without clicking or scrolling the page.
  The pull-to-refresh callback also recognizes native gesture ownership.
- A valid tap uses the existing bound document/navigation/token channel to
  prepare a short-lived input guard. The acknowledgement must match both the
  displayed version and this physical press's request ID.
  A very quick tap during initial native layout is buffered until display-ready
  is sent, rather than being discarded during those first two frames.
- Only after acknowledgement does Android forward copies of that gesture's
  original DOWN/UP to the same WebView, preserving their timing/source and
  translating between the views' screen origins. UP can arrive before the
  acknowledgement; at most two MotionEvents are retained and then recycled.
- Chromium generates the trusted click. The document-start capture guard
  consumes that click at the prepared coordinates and invokes the original
  synchronous `click(event)` launch function. No JavaScript `click()`, native
  bridge call to `requestFullscreen()`, toolbar arming, or second user tap is used.
- The guard prevents site click handlers and link navigation even if the web
  button no longer occupies the native button's location. It is scoped to that
  target and press, expires, and is removed on cancellation, scroll or teardown.
  Script-generated events cannot activate it.
- Geometry changes, page navigation, host release and renderer retirement cancel
  outstanding input. Native gesture buffering has a 700 ms safety timeout; the
  page guard has a 1500 ms safety timeout. Long holds do not become delayed taps.

The existing player action, native appearance/placement, embedded-frame fallback,
keyboard/accessibility control and separate ExoPlayer route are retained. There
is no additional decoder or continuous input polling. The input handshake is
performed only for an actual press on the native target.

## Verification on the emulator

Tested on the connected `emulator-5554`, updating Miowser in place. No app data,
cookies, saved tabs, Library entries or device media were cleared. No phone was
used for these checks.

| Check | Result |
| --- | --- |
| Focused JVM browser/player/retained-engine tests | 96 passed |
| Browser script checks | 126 passed |
| Android instrumentation suite | 3 passed on emulator |
| Debug APK and Android test APK assembly | Passed |
| Release Kotlin compilation | Passed |
| Physical YouTube swipe, drag from native face, then single tap | Passed for touch ownership and fullscreen entry |

The instrumentation suite verifies:

1. Retained engine detachment with SwipeRefreshLayout's internal spinner.
2. Initial-frame input buffering until the matching acknowledgement, rejection of stale IDs,
   drag/rejected/cancelled/fading input consumption, and unchanged outside input.
3. Actual Chromium fullscreen entry from a trusted click after scrolling, while
   the web button's host is deliberately hidden and a clickable page element
   occupies the background. The background link is not activated. The offline
   fixture controls video readiness; it tests the production tracker/button/input
   route and Chromium's real fullscreen API, not media decoding.

The physical YouTube check used the existing public watch tab. A downward drag
starting on the visible native face left page scroll position and document
identity unchanged, produced no page clicks and did not open fullscreen. The
following single physical tap entered fullscreen without page clicks or URL
navigation. Test output is saved locally under `.local/native-assistant-input-*`.

## Separate playback observation

On this emulator, the YouTube video paused during fullscreen entry while
restoring its playback position. This also occurred when the original DOM button
was tapped directly through Chromium input, bypassing the new native gesture
route. Thus the input tests establish correct touch ownership and one-tap entry;
they do **not** establish uninterrupted YouTube playback through that transition.
Automatic playback recovery is not changed in this touch-interception patch.
