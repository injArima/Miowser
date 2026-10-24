# Player touch actions: subtitle workspace and playlist

## Confirmed cause and fix

`PlayerContent` put the subtitle workspace and playlist inside the same Box as
the video and playback controls. Opening either custom modal enabled that Box's
`playerInputGate`, which consumes pointer events during the Initial pass and
hides descendants from focus/accessibility. This blocked the modal's own rows,
buttons, text field and scrolling, along with the video beneath it.

`PlayerInteractionLayers` now provides separate player and overlay content slots.
Only the video/normal controls slot receives the blocking modifier; modal content
is its sibling. The active modal accepts input. During its exit, its own existing
gate still disables outgoing actions and the covered video remains blocked until
the modal is removed. Focus restoration and stale-exit revision guards remain.

## Related correction

PiP now hides modal UI and clears retained custom overlays immediately rather
than relying on an exit animation from a removed host. Entering PiP also clears
speed/scale popup flags, so those menus do not reopen unexpectedly on return.
This does not change the explicit PiP playback/pause policy, orientation ownership
across playlist items, audio focus, media storage or subtitle-delay calculations.

## Other paths reviewed

| Area | Source review |
| --- | --- |
| Subtitles | Track/file selection, Auto/Off, appearance controls, editable delay, reset, cue alignment and scrolling use direct callbacks. The shared ancestor gate was preventing pointer delivery. |
| Playlist | Row selection, previous/next, current-item state, close, backdrop and Back retain their existing callbacks and request ownership. |
| Standard controls | Play/pause, skip, timeline, timer, rotation/lock, speed and scale actions remain connected. Hidden controls remain disabled. |
| Audio/video tracks | Selection still applies the Media3 override and closes its framework sheet. Those sheets retain their own modal window/input handling. |
| Brightness/volume | Framework sheet sliders and video gestures retain their callbacks. |
| Recovery | Retry, source-page and Close callbacks remain connected; covered controls stay gated while a custom modal is present. |
| PiP/lifecycle | Modal/menu cleanup corrected as above. Existing explicit-PiP continuation and lifecycle pause/resume rules remain. |

No further confirmed defects were found in those reviewed paths. This is a source
audit and local verification, not a claim that every action works on every device.

## Local verification

- **58 player JVM tests passed**, with no failures/errors, using the existing
  192 MiB test-heap override. New tests cover PiP clearing, stale exit rejection
  and repeated cleanup. Existing tests cover overlay transitions, track choices,
  subtitle alignment/style, seek gesture boundaries and error presentation.
- Debug and development Kotlin compilation passed.
- Three Android pointer regression tests compiled. They dispatch actual
  MotionEvents rather than semantic clicks: modal buttons versus covered player
  buttons, slider dragging/text-field focus, and scrolling. Their empty activity
  host is non-exported and debug-only, absent from release/development builds.
- Instrumentation/device tests were **not run**. No phone was accessed and no app
  data was cleared. The unrelated existing BrowserScreen deprecation warning
  remains.

## Device checklist

Check portrait and landscape, with playback running and paused:

1. Subtitles: switch Tracks/Inspect/Settings; select a track/file; use Auto/Off;
   change delay with +/- and the keyboard; reset delay; change font, size, colors,
   opacity, outline and offset. Scroll all panels.
2. Adjust Subtitle: select a detected file, start adjustment, tap a cue, check
   alignment/resumed playback, then try Cancel adjustment.
3. Playlist: scroll, choose another video, Previous/Next, then close by X,
   backdrop and Back. Reopen/close rapidly. Rotation should stay unchanged.
4. With either modal open, taps must not play/pause, seek or change controls
   underneath it. After dismissal, those controls must work again.
5. Check play/pause, skip, seek slider, timer, speed/scale menus, audio/video
   choices, brightness/volume, rotation and lock/unlock.
6. Enter PiP and return, including while a menu is open. Playback should keep its
   existing PiP behavior and no stale menu, modal or input blocker should remain.
7. If practical, check a playback error's Retry/source/Close actions and
   keyboard/TalkBack focus when opening and closing the two custom modals.
