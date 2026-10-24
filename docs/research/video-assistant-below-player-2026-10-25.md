# Video assistant action below the player

## Result

The browser-backed video action now uses a dedicated row immediately below the
detected player, aligned right. The row participates in normal page layout so
the page title and other content move down instead of being covered. The video
is not wrapped, moved, copied or reopened.

Placement is conservative: it examines at most 12 ancestors, requires ordinary
block flow and verifies that containers reserve the added space without resizing
the video. Fixed-height, sticky, clipped, reordered or otherwise unsafe layouts
use the browser-toolbar fallback. The row remains reserved while temporarily
hidden (pause, menus and fullscreen) to avoid repeated layout jumps. Removal of
the video, navigation and page teardown remove its row. Repeated removal by a
website is capped at three insertion attempts until a viewport resize.

## Embedded-player fallback

Child frames keep their page DOM unchanged. Miowser shows a contextual **Play in
Miowser** action above the existing browser navigation buttons when the selected
playing video cannot safely show the row. The existing six navigation actions
remain available, and the measured toolbar height also positions snackbars.

Fullscreen requires user activation in the video document. The native toolbar
therefore arms the exact frame/video/source and displays **Tap the video within
10 seconds to open it in Miowser**, with Cancel. A real tap on that video requests
fullscreen synchronously in the original document. It does not synthesize input,
evaluate a fullscreen request without a gesture, or bypass iframe permissions.
Website links, form controls and explicit player buttons keep their normal tap
behavior. Cancel, expiry, navigation, hiding the browser, source replacement,
player removal or an invalid frame binding prevent the pending entry.

Cancellation feedback is scoped to a unique native request ticket, so dismissal
of an earlier snackbar cannot cancel a newer request, including after renderer
replacement. JavaScript revalidates the exact source revision and visible video
at tap time.

The toolbar collects only a distinct projection of video identity and source
revision. Position, buffering progress and other playback-clock changes do not
recompose the browser screen or recreate the toolbar action. No new polling
interval, media download, decoder or ExoPlayer instance is introduced.

The underlying activation requirement is documented by the
[Fullscreen Standard](https://fullscreen.spec.whatwg.org/#dom-element-requestfullscreen).
DRM, media keys, licenses, source selection and the site's fullscreen permission
policy are unchanged. Detection still requires an accessible HTML video and a
supported WebView provider; this is not universal site support.

## Local verification

- 114 simulated browser-script checks, including the existing continuity,
  controls, hold-speed, lifecycle and clipping checks.
- New checks cover title displacement, stable row spacing, fixed-height/sticky
  containers, embedded frames, safe areas, scrolling, provider row removal,
  cancellation/expiry, untrusted clicks, stale source/frame identities and
  revoked fullscreen permission.
- 96 focused JVM tests passed, including four new tests for toolbar selection, no duplicate action when the
  chosen video has a row, stable identity across clock updates, and exclusion
  of paused/hidden/ended/unready videos.
- Focused JVM regression tests, debug/release Kotlin compilation and debug APK
  assembly are run locally. Actual site layout, touch routing and playback on
  the phone remain the user's checks. No device installation or app-data changes.

## Device checklist

1. Play a video on a normal page. Confirm the action is below the player,
   aligned right, with the title and website buttons unobscured. Tap it and
   compare position, speed and quality with the website player.
2. Try YouTube-style layouts, long pages, scrolling, portrait/landscape and page
   zoom. The action should use either a safe row or the contextual toolbar.
3. Try an embedded player. Tap **Play in Miowser**, then tap the video. Verify
   Cancel and waiting longer than 10 seconds leave ordinary playback unchanged.
4. After arming the fallback, navigate, change tabs, pause or change the video
   source. Confirm a later tap cannot open the old video. Try arming twice.
5. Return from fullscreen, pause/resume and open browser menus. Check for
   duplicate buttons, repeated page movement or leftover rows after navigation.
6. Check rich-player seek, gestures, hold-to-speed badge, captions/audio when
   supported, PiP and a subsequent Library/ExoPlayer video.
