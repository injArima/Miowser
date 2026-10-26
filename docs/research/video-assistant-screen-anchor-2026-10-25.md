# Screen-anchored native video button

## Placement

The native video assistant now uses a fixed position at the lower-right of the
browser-content viewport, above Jump to top. It no longer uses the selected
video's rectangle to position the main-page button. Page content is not moved.

Both buttons use shared app layout constants. Jump to top retains its 48 dp size
and 16 dp edge margins. The video button retains a 48 dp face, 60 dp touch target
and 28 dp mark. Their right edges align and the visible buttons have a 12 dp
vertical gap. The video button's invisible touch overhang is included, leaving
6 dp between its touch target and Jump to top's 48 dp slot.

The lower slot stays reserved even when Jump to top is hidden. The containing
Scaffold already excludes the browser bottom bar and system insets, so this
placement is above them without relying on a hardcoded screen height. Placement
uses the current visual viewport/page scale and safe-area insets. If both slots
cannot fit, the video control hides rather than overlapping the reserved slot.

Scrolling no longer hides and repositions the main-page button against the video.
The button stays in the same screen slot while its selected video remains
eligible. Existing video selection, single-tap action, native touch capture and
fullscreen-entry recovery remain in place. Validated state updates renew the
native presentation without resending unchanged geometry or adding another
polling loop.

## Embedded-frame boundary

The native action supports qualified main-document videos. The old embedded-frame
DOM fallback has been removed, so no assistant action is drawn inside a video or
iframe. Embedded-frame playback can still be tracked, but it does not receive a
button until a reliable native, screen-anchored trusted-input route exists.

Visibility now follows Samsung Internet's inspected gate: active playback and
audible media are required. This filters muted autoplay previews and also hides
the action when the chosen video is paused, ended, or muted.

## Verification

Per the user's instruction, no build, tests, emulator checks or installation were
run. Existing script expectations were updated for fixed screen positioning.

User checks after building a fresh APK:

1. Play a YouTube/main-page video. Confirm the button appears at the lower-right,
   above the reserved Jump to top position, rather than beside the video.
2. Scroll while the video stays visible (including a sticky player). The native
   button should remain in its screen slot. When Jump to top appears, both should
   align with a gap and neither touch target should overlap.
3. Check the same slot while Jump to top is hidden, in landscape, after zooming,
   and after opening/closing the keyboard.
4. Confirm single-tap player entry, drag/cancellation handling and fullscreen
   playback continuity. Pausing, ending or muting the selected video hides the
   control; scrolling a still-qualified video offscreen does not move it.
