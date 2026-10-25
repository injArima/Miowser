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
eligible. Existing video selection, visibility eligibility, single-tap action,
native touch capture, auto-hide and fullscreen-entry recovery remain in place.
User activity can renew the native presentation at most once per second; idle
playback-clock updates still do not resend unchanged geometry. The native safety
lease is 4.5 seconds so a renewed three-second activity timer can finish its fade
before that lease expires.

## Existing embedded-frame boundary

The native prototype supports main-frame pages. Its pre-existing embedded-frame
DOM fallback is retained to preserve that player's working trusted single-tap
action. That fallback still lives inside its iframe and retains its previous
placement. It is not a native screen overlay: moving it outside the iframe needs
additional cross-frame input/coordinate routing, which this placement patch does
not add.

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
4. Confirm single-tap player entry, drag/cancellation handling, auto-hide and
   fullscreen playback continuity. An ineligible/offscreen video still hides
   the control; screen anchoring does not change video-selection eligibility.
