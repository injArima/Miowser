# Restore the single-tap video assistant

## Behavior

The user authorized removal of the two-step toolbar route and restoration of the
original consistent floating button. The action is again inside the visible
video frame. One trusted tap requests fullscreen for that exact existing HTML
video and attaches the existing Miowser rich controls.

The same path applies inside supported embedded frames. There is no toolbar
entry action, arming timer, follow-up page-tap listener or below-player layout
row. Website content is not shifted to reserve button space. Fullscreen-denied,
hidden, paused or otherwise ineligible videos retain the previous behavior.

Only the placement/toolbar implementation from `c72b4e0` was reversed. The
verified production sources from `50f0f78` provide the restored baseline, keeping
the earlier viewport/body clipping correction, shared rich-player controls,
native-control suppression, hold-speed indicator and lifecycle handling.
Samsung research reports remain available as historical evidence.

Removed the toolbar composable, target-selection model, related JVM tests,
translation strings, tab/tracker entry methods, bridge messages and presentation
telemetry. Ordinary page clicks cannot activate a previously armed request.
Existing Library/ExoPlayer playback stays separate; the browser route does not
reopen a media URL, create another decoder or change DRM/license handling.

## Verification

- The existing 99 browser-script checks pass after restoration.
- Three regression checks cover direct single-tap entry in an embedded frame,
  ignored legacy toolbar-arm messages and no page-layout row/video wrapping:
  **102 script checks passed**.
- **92 focused JVM tests passed** for browser-video, shared player and retained
  tabs. Debug/release Kotlin compilation and normal debug APK assembly passed.
- Device testing remains with the user. No phone installation or app-data
  changes are part of this work.

## Device checklist

1. Play a video on a normal page and an embedded-video page. Confirm the same
   floating icon opens the rich player with one tap and no toolbar prompt.
2. Compare playback position and selected quality before and after entry.
   Confirm no duplicate audio or video restart.
3. Check scroll/rotation, return from fullscreen, seek, gestures, hold-to-speed
   feedback, supported tracks and PiP, followed by a Library video.
4. Confirm the page title stays in its original place and ordinary taps on the
   website never activate a pending Miowser action.
