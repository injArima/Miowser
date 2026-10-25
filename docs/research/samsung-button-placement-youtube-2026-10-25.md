# Samsung Internet button placement and display on YouTube

## Scope and result

This investigation covers **where the assistant is drawn and how its visibility
is managed**. It does not change or redesign Miowser's existing player action.
Miowser application code, APKs and app data were not modified.

The findings combine the supplied Samsung Internet 30.0.5.24 APK with live
observation on the connected SM-G990B2, whose installed browser reports the same
version. The live page was the public mobile YouTube watch page for Blender's
Big Buck Bunny (`https://m.youtube.com/watch?v=aqz-KE-bpKQ`).

**Live result:** Samsung draws native Android assistant buttons below the right
edge of the playing video. They temporarily overlay the title/details area;
they do not add a row, move the title down or require space inside the website's
player container. Scrolling changes the browser viewport offset and the overlay
is repositioned accordingly. On this YouTube page the video stayed pinned near
the top as the recommendations scrolled underneath.

## 1. Where it shows the button

The saved position defaults to 3: **below-right**. The other supported anchors
are above-left (0), below-left (1) and above-right (2).

The position calculation uses the entire assistant strip, which can contain
multiple buttons. In the captured state it contained fullscreen and pop-up
buttons. The right edge is aligned to the visible video, then constrained by
the allowed browser area. The below-video anchor is offset by **8 dp**.

For the below-right mode, the inspected code is equivalent to:

```text
x = min(max(video.right - assistant.width, video.left), allowed.right)
y = min(video.bottom + gap, allowed.bottom)
gap = 8 dp
```

`allowed` accounts for the assistant's dimensions, browser toolbar/bottom-bar
space, optional tab/bookmark/translation bars and navigation margins. Clamping
can pull the overlay toward the screen interior when there is insufficient
space; it does not create extra website layout space. The insufficient-space
branch is established by code, not exercised in the captured YouTube case.

### Measured YouTube coordinates

The display is 1080 x 2340 physical pixels, with effective density 450 dpi.
Coordinates below are unscaled screen pixels or explicitly marked local pixels.

| Measurement | Browser bars visible | Browser bars collapsed |
| --- | --- | --- |
| Engine-reported video rectangle, local to content | `(0,135)-(1080,743)` | `(0,135)-(1080,743)` |
| Browser content rectangle | `(0,234)-(1080,2205)` | `(0,99)-(1080,2341)` |
| Video rectangle translated to screen coordinates | `(0,369)-(1080,977)` | `(0,234)-(1080,842)` |

For the collapsed-bars case, the actual Android hierarchy reported:

| Native view | Screen bounds |
| --- | --- |
| Assistant `LinearLayout` | `(716,865)-(1080,1024)` |
| Fullscreen `ImageButton` | `(762,888)-(875,1001)` |
| Pop-up `ImageButton` | `(921,888)-(1034,1001)` |

The assistant starts at y=865, which is **23 physical pixels below the video's
bottom at y=842**. This agrees with the 8 dp gap rounded at 450 dpi. The circular
button faces are 113 x 113 physical pixels, agreeing with the 40 dp resource.
Margins mean the visible icon face starts farther below the video than the
assistant strip's 8 dp anchor gap.

## 2. How it shows the button

1. Samsung tracks the current browser media client and requests its visible
   rectangle with `checkVideoVisible`.
2. Its engine reports visibility and a rectangle through
   `onCheckVideoVisibleResult`. The Java callback adds the browser content-view
   offset and clamps the result to the visible content area.
3. `MAButtonView.show` attaches a full-screen-sized, non-clickable Android root
   through `Activity.addContentView`. Its positioned child is a `LinearLayout`
   with clickable `ImageButton` children. `updateButtonView` calls
   `bringToFront()`. This is an app overlay above the browser renderer, not a
   DOM element inside YouTube.
4. The supplied animation resources fade alpha 0 to 1 and 1 to 0 over **300 ms**.
   The show request has a **200 ms** scheduling delay in the inspected manager.
5. The visible assistant auto-hides after approximately **3 seconds**. On the
   phone, a show at `14:11:44.445` was followed by hide at `14:11:47.464` (3.019 s),
   and a show at `14:12:49.031` by hide at `14:12:52.034` (3.003 s).
6. Scroll completion requests another show/geometry check. A root-layout change
   hides the old placement and schedules five bounded rechecks at 200, 400,
   600, 800 and 1000 ms. The recording also shows reappearance after page touch.

The compiled resources define a **40 dp face**, **8 dp item margins**, **3 dp
elevation**, and a corner radius large enough to make the face circular.
The outer strip adds horizontal padding. These dimensions describe Samsung's
visual treatment, not a requirement to reduce Miowser's existing touch targets.

Visibility is gated by the enabled setting, current playback/client state,
tab/activity visibility, fullscreen/conflicting UI and selected device modes.
The inspected playing-state path suppresses muted or paused playback. Those are
Samsung policy choices; adopting the positioning algorithm does not require
copying every policy or changing Miowser's current behavior.

## Why this works on the observed YouTube layout

The display layer does not query a YouTube CSS selector, attach the button to a
YouTube DOM parent or rely on that parent's height/overflow. It receives the
video's geometry from the browser engine and draws outside the webpage renderer.
This explains the observed placement despite YouTube's pinned-player layout.

No YouTube-specific branch was found in the Java visibility and positioning
methods examined here. That is a bounded finding: native geometry calculation,
remote configuration and other site-specific code were not exhaustively audited.
Desktop YouTube, Shorts, paid/DRM content and every embedded-player variation
were not exercised. The live result establishes this mobile watch-page case.

## Evidence and boundaries

- [Visible buttons on YouTube](../../.local/research/samsung-youtube-live-2026-10-25/visible-buttons.png).
- [Measured native hierarchy](../../.local/research/samsung-youtube-live-2026-10-25/after-small-scroll-ui.xml).
- [Filtered geometry/show-hide logs](../../.local/research/samsung-youtube-live-2026-10-25/placement-logcat.txt),
  retaining 151 relevant Samsung lines without clearing the log buffer.
- [REA evidence bundle](../../.local/research/samsung-button-placement-2026-10-25.json).
- [Decoded animation/layout/shape XML](../../.local/research/samsung-button-placement-xml-2026-10-25.txt).

The captured native root has resource ID `media_assistant_button_main_layout`;
the positioned strip has ID `media_assistant_button`; button children have ID
`menu_img` and accessibility labels `View full screen` / `View in pop-up player`.
These are native Android view nodes, corroborating the decompiled attachment
code and screenshot.

Relevant new REA records include `ev_7ee35872a82b791030a474b26fae3eb29c7cfb32b8f34fa6f1062b362b0326e0`
(visibility request), `ev_ba9703bb61a0376556d69dd8594d6f31e772b0ee550a258004d07fbfe6a47b6b`
(engine callback), `ev_3561615c218e0b69eaa033aacce84c1ae992830f78acd41e8c7e73d6e2126d91`
(layout-change rechecks), and
`ev_f72d3b0e6ea1ea135ac159eef7dda7b7fe9eae45f90cb94863cf059c30af500d`
(live YouTube geometry and timing). Earlier placement/show methods are retained
in the linked [previous reinspection](samsung-single-tap-recheck-2026-10-25.md).

Frida attachment was unavailable because its remote server connection closed.
No app patch, reinstall, rooting or runtime hook was performed. Samsung's own
process-scoped logs, screenshots and UI hierarchy supplied the live evidence.
The public clip was paused at completion, and only capture files created by this
investigation were removed from shared device storage. Miowser was not tested
or altered, and no browser/app data was cleared. Phone captures remain local.
