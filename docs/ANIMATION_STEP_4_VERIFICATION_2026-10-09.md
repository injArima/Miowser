# Animation smoothness — step 4

Date: 2026-10-09. Scope: **Make interruption and dismissal deliberate** from the [animation plan](G:/Projects/Miowser/docs/ANIMATION_SMOOTHNESS_PLAN_2026-10-08.md). Device validation belongs to the user.

## Changes

### Tab swipe ownership

Each tab retains a single motion owner and Animatable. A new pointer takes ownership, cancels settlement and starts from the displayed offset. Horizontal release velocity feeds the existing near-critical spring policy; velocity is bounded relative to card width and the visible offset remains within the card's bounds. A return that hits a boundary continues to rest rather than stranding the card.

Vertical scrolling keeps precedence once it wins. Missing, consumed, cancelled or additional pointers return the card instead of committing a close. Resizing invalidates the old gesture/settlement and returns the card under the new bounds. Close-button and swipe completion share a close-once guard. Generation and coroutine-cancellation checks prevent an interrupted or queued old completion from closing the tab. Placement-phase offset reads and lazy identity keys remain intact.

The existing long, mostly horizontal close threshold remains; velocity changes settlement rather than making a short vertical/diagonal fling delete a tab. Functional finger movement stays immediate even in reduced-motion mode. App reduction or Android animation scale zero settles directly without awaiting animation frames.

### Player overlay ownership

A request-scoped owner tracks target visibility separately from rendered presence, for the right-side playlist and full-screen subtitle workspace. Outgoing content stays mounted through exit; reopening retargets the same transition. Revision checks reject old exit completion, and switching targets retains one modal until replacement. A new playback request disposes the old overlay owner.

The playlist fades with its scrim (240/180 ms) and its panel slides from the right. Subtitle settings fade with a fixed dark background (200/140 ms), with at most 12 dp content travel. These share the existing motion policy and standard Compose duration scaling. Reduced motion removes slide and makes presentation settlement immediate. Video/WebView surfaces are not scaled, blurred or captured.

While a modal is present, including during exit, underlying video gestures and controls cannot receive pointer/focus/accessibility actions. Closing content stops accepting its own actions. Back remains consumed by the closing modal, so repeated Back does not accidentally leave the player. Fading-out player controls also lose focus/accessibility and their individual actions become disabled; the video gesture layer is not covered by a new full-screen hidden-controls interceptor.

The close button receives initial keyboard focus and the overlay declares its accessibility pane title. After final exit, focus restoration checks the current request owner/revision and trigger availability before returning to the playlist/subtitles button. TalkBack's actual focus behavior requires device validation. Framework sheets/menus retain their own motion/drag owner; opening a custom modal clears competing control menus/sheets and framework-open callbacks reject a competing modal state.

Blank-panel taps are shielded without exposing a fake button to keyboard/accessibility navigation or consuming scroll movement. The outside dismissal action has an accessibility label.

Subtitle selection/style edits and playlist previous/next/select commands run immediately. Animation completion governs presentation cleanup. The playlist icon stays between Lock and Back; its compact right-side geometry, the full-screen subtitle layout, grey two-line sample, subtitles and progress controls remain intact. The visible timeline beside the playlist continues sampling; a full-screen subtitle workspace suspends only the covered timeline UI clock.

## Local verification

- **481 JVM tests passed**, including 13 added tests; zero failures, errors or skips.
- Debug lint: **zero errors**, 81 warnings and 5 hints. The two new modifier-parameter warnings were corrected; the remaining warning/hint totals match Step 3.
- Debug APK and R8 unsigned release build succeeded. Final combined verification: BUILD SUCCESSFUL in 5m 3s.
- Source evidence covers 316 main/test files; recorded hashes match the delivered sources. Scoped diff whitespace check passed.

Thirteen new JVM tests cover retained offsets, actual velocity-driven bounded spring settlement, frame-free reduced/system-zero completion, close-once behavior, interrupted and queued stale jobs, boundary recovery, outgoing modal presence, rapid reopen/close revisions, replacement panels, repeated Back and independent request ownership. These tests do not replace pointer, focus, accessibility or real-device frame validation.

No emulator, connected-device, instrumentation or on-device benchmark checks were run. Evidence under `G:/Projects/Miowser/.local/optimization/`: `animation-step4-before/`, `animation-step4-source.diff`, `animation-step4-source-manifest.json`, verification logs and `animation-step4-results.json`. Previous optimization work and Step 1–3 APKs are retained.

## Installable build

[Download the Step 4 debug APK](G:/Projects/Miowser/Miowser-0.1.0-animation-step4-debug.apk).

Size: 27,284,500 bytes. SHA-256: `962e15e03e3ce1879c767ac08b58f0754195ba9158de4498b56f9d5846d1f3ac`.

The R8 release output is unsigned and is retained as local build evidence, not an installable release delivery.

## Device checklist

Compare to the Step 3 build using the same tabs, Library playlist, videos and subtitle files. Test portrait and landscape where relevant.

### Tab gestures

- [ ] Slowly drag a normal tab in each direction and release below the existing close threshold. It returns to rest without a position jump.
- [ ] Drag past the threshold, pause, then release. It closes once and Undo restores the correct tab and count.
- [ ] Repeat with fast release in both directions. Settlement follows release velocity without overshooting the visible bounds.
- [ ] Throw a card outward just below the close threshold. It returns fully even if it touches the boundary on the way back.
- [ ] Grab during return or dismissal, hold still, then move slightly in either direction. The new drag begins at the displayed position, and the previous close does not fire.
- [ ] Repeat several rapid grab/release/reverse sequences. No stale animation changes the new drag or removes another tab.
- [ ] Scroll the grid vertically, including diagonals and during a fling. The grid keeps scroll ownership and no tab closes.
- [ ] Add a second finger during a horizontal drag or cancel the pointer gesture. The tab returns rather than closing.
- [ ] Rotate, resize/split-screen or leave Tabs during a swipe/settlement. No stale close, stranded offset or wrong-tab action remains after returning.
- [ ] Tap a displaced card, press its close button during settlement, then Undo. Close fires once and ordinary selection remains correct.
- [ ] Repeat with private tabs and switch normal/private/inactive sections. Identity, selection and counts remain correct.
- [ ] Enable app Reduce motion and Android animations off separately. Finger drag remains immediate; return/close completes with no stuck callback.
- [ ] Try Android animation scales 0.5×, 1× and 2×. Settlement follows the system once; closing still fires once.

### Playlist and subtitle transitions

- [ ] Open the playlist. Its scrim fades with the right-side panel; the video, title, progress and timer stay in place.
- [ ] Close by X, outside tap and Back. The outgoing panel stays present through exit and no dimming layer remains afterwards.
- [ ] Press Back repeatedly during exit. The player stays open until a subsequent Back after dismissal; no click passes through the closing modal.
- [ ] Reopen immediately after dismissal and repeatedly reverse open/close where possible. The final panel is correct and no old exit removes it.
- [ ] Select a playlist entry and use Previous/Next. The video command starts on tap; the old request's overlay/focus completion cannot affect the new video.
- [ ] Check the first/last playlist entry and a playlist whose trigger becomes unavailable. Disabled controls remain grey and focus restoration does not crash.
- [ ] The playlist icon remains between Lock and Back. Portrait, landscape and split-screen retain the compact side-panel width.
- [ ] With the playlist open, verify the visible progress/timer updates. Underlying gestures, scrubbing and player buttons cannot execute while the modal is present.
- [ ] Open subtitle settings. The full-screen dark background fades without moving the video; content travel is short and the sample stays compact on grey.
- [ ] Switch Tracks/Inspect/Settings in portrait and use all three landscape columns. No accidental outside dismissal or lost selection occurs.
- [ ] Select VTT and SRT, then change font, size, color, opacity, outline, shadow, background, vertical position and positive/negative millisecond delay while paused and playing. Video captions persist and changes apply immediately.
- [ ] Close/reopen subtitle settings while editing or seeking. The selected file/style persists and old subtitle text does not leak into another video.
- [ ] Open audio/video/adjustment sheets and speed/scale menus around custom overlay use. One modal owns interaction; framework sheet gestures are not wrapped in competing animation.
- [ ] Background/return, rotate and resize during enter/exit, including while buffering. No stuck scrim, stale focus or interrupted playback command remains.
- [ ] Repeat all open/close actions with app Reduce motion and Android animations off. Every exit cleans up and all actions still complete.

### Controls, focus and accessibility

- [ ] Show/hide player controls repeatedly while playing, paused and buffering. Title remains steady; both control scrims stay coordinated.
- [ ] During a controls exit, try its play, seek, rotate, lock, playlist, subtitle, timer and progress actions. Hidden controls cannot execute stale actions or remain focusable.
- [ ] Tap/seek using video gestures with controls hidden and during rapid visibility reversals. Responses remain prompt and no new full-screen hidden-controls blocker appears.
- [ ] Use a keyboard to open each overlay. Focus starts at Close, stays within visible modal content and returns to the triggering button after final dismissal.
- [ ] With TalkBack, check pane announcement, navigation within the overlay, hidden/closing content and focus after dismissal. Underlying video/controls must not be announced or activated during modal ownership.
- [ ] While typing a subtitle delay, dismiss with Back/X and reopen. Focus/keyboard state is sensible, and the millisecond value persists.
- [ ] Check light/dark/dynamic themes, RTL and 150/200% font size. Clipping, shades, disabled actions and touch targets remain usable.

### Regression and performance gate

- [ ] Test remote MP4/HLS/DASH and local media with subtitles and concurrent downloads. Cue timing, playback state, first frame, progress, thumbnails and notifications remain correct.
- [ ] Compare matched release-equivalent runs at 60 Hz and higher refresh rates. Record frame deadlines/jank, first response, UI CPU, memory and thermal state while interrupting gestures and overlays. Confirm no custom settlement work continues after rest or disposal. Local tests/debug visual checks do not establish a measured production performance gain.
