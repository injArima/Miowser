# Animation smoothness — step 1

Date: 2026-10-09. Scope: **Remove frame-time competition first** from the [animation plan](G:/Projects/Miowser/docs/ANIMATION_SMOOTHNESS_PLAN_2026-10-08.md). Implemented and locally verified. Device testing belongs to the user.

## Changes

| Area | Result |
|---|---|
| Subtitle renderer | Retains its independent 50 ms playing clock and applies only changed cue sets. Paused rendering sleeps until a player event; seeking wakes it immediately. Style/delay changes restart only the rendering effect, immediately applying the style and current cues when active. Subtitle fetching/selection stays independent. The unused copy of embedded cues in the player owner is removed; Media3 continues rendering embedded subtitles. |
| Player timeline | Position, duration, buffer and scrub position live in a retained timeline owner read only by the progress bar and timer. The player, video title and action-row composition do not read periodic timeline samples. Visible playback/buffer loading samples every 500 ms; player events refresh immediately. Hidden/locked controls, covered footer panels and stopped lifecycle scopes release the clock listener. The partly visible timeline remains active beside the playlist. |
| Seeking and controls | Double taps, horizontal gestures and 10-second buttons read actual player position/duration at interaction. Scrubbing prevents auto-hide, owns its local preview, and clears on cancellation/backgrounding. Playback speed changes arrive from player events. The auto-hide timer runs only in the active lifecycle. The action row reserves the widest observed timer width per video, avoiding repeated width shrink/growth as timer digits change. |
| Download presentation | The existing P1 cadence remains: at most four ordinary progress publications per second, with exact transfer accounting and forced state-transition publication. Progress/status and elapsed/remaining text now have their own small composition scopes. Elapsed clocks run only for visible active rows, in the STARTED lifecycle, and stop when the retained Hub is covered by the player. Hidden rows stop reading ordinary byte progress and stop indeterminate progress animation. Thread fills read byte progress during drawing, rather than changing child widths in composition/layout. Transfer workers and notifications keep their own owners. |
| Library collector | Background sort/filter collection suspends while the retained Library is covered or its lifecycle is stopped. Reopening refreshes from the current records; ordinary navigation retains the existing lightweight state. |
| Artwork | P2's dedicated visible-decode workers, cancellation, cache budgets and scrolling decode deferral remain in place. The adaptive-stream midpoint monitor reads the player directly and no longer needs a periodically published root UI timeline. It also sleeps while playback is paused and idle. |

This reduces source-confirmed UI work alongside animation. It does **not** establish a measured device CPU or frame-rate improvement. New motion timings, easing, panel animations and gesture springs belong to later steps.

## Local verification

- Final settled build: **455 JVM tests passed**, zero failures/errors/skips; debug APK and the R8/resource-shrunk unsigned release APK built successfully. Debug lint: **zero errors**, 82 warnings and 5 hints. Warnings remain; this is not a lint-clean app.
- Six added regression tests cover paused/event wakeups, playing-clock cadence and pause, ownership cancellation/restart, player-listener cleanup without playback mutation, millisecond subtitle boundaries/restyling, and forced exact download counters at pause/error/completion.
- No emulator, connected-device, instrumentation or on-device benchmark checks were run.

Evidence is stored under `G:/Projects/Miowser/.local/optimization/`: `animation-step1-source.diff`, `animation-step1-source-manifest.json`, `animation-step1-verification.log`, and `animation-step1-final-verification.log`, and `animation-step1-settled-verification.log`, and `animation-step1-results.json`. The scoped before snapshot preserves the previous P2 sources. Existing P0/P1/P2 work and the P2 APK are retained.

## Installable build

[Debug APK](G:/Projects/Miowser/Miowser-0.1.0-animation-step1-debug.apk), 27,454,117 bytes.

SHA256: `0494d0fd53ba7df654297044f294b45b960114d2068fa4c7d617ce55e6e88d15`.

The final manifest covers 306 source/test files; every hash still matches the files used for the settled build. The release output is unsigned; the linked debug APK is the installable artifact.

## Device checklist

Use the same videos and transfers as the previous P2 build. Check both portrait and landscape where applicable.

### Subtitles

- [ ] Select a detected VTT and an SRT file; each renders in the video and remains selected after closing settings.
- [ ] Watch across cue starts, cue ends and gaps. Old text clears correctly; long and two-line cues remain readable.
- [ ] With controls hidden or locked, subtitles continue changing at the correct times.
- [ ] While paused on a cue, change font, size, color, opacity, outline, shadow, background and vertical position. The video subtitle updates immediately and stays visible after closing settings.
- [ ] Enter positive and negative millisecond delays, including small values such as 1 and 50 ms. Reset the delay and verify timing returns to normal.
- [ ] Seek while paused into a cue, into a gap, backwards, and near the end. Subtitle text changes immediately without resuming playback.
- [ ] While playing, make repeated visual/delay edits, then continue watching. Subtitles must not disappear or reload the file for an appearance edit.
- [ ] Switch external files, Auto, Off and an embedded subtitle track. Off clears text; returning to a selected track works.
- [ ] Background/return, rotate, and open the next Library playlist video. No old subtitle text or duplicated overlay remains.

### Player controls and seeking

- [ ] Play/pause repeatedly with controls visible and hidden. The title stays steady, including when a stream buffers.
- [ ] Hide controls for at least 30 seconds; reveal them. Position, duration and buffered progress refresh promptly.
- [ ] Lock/unlock controls and open/close subtitle, audio and adjustment panels. The restored timeline is current.
- [ ] Open the right-side playlist during playback. Its entries work and the visible part of the timeline continues advancing.
- [ ] Double-tap seek with controls hidden after a long playback interval. The jump starts from the current position and shows the correct delta and target time.
- [ ] Use horizontal seek gestures and the 10-second buttons while playing and paused. Check beginning/end clamping and unknown-duration streams.
- [ ] Hold a timeline scrub longer than four seconds. Controls stay visible, preview follows the finger, and release performs one seek.
- [ ] Cancel a scrub by backgrounding or changing the visible panel, then return. No stuck preview or permanently disabled auto-hide remains.
- [ ] Toggle total/remaining time. Its right edge stays aligned; the timer and action buttons do not overlap on small screens, larger text, or hour-long videos.
- [ ] Buffer a stream while paused. Buffered shading continues growing while data arrives and remains distinct from played progress; the seek thumb stays centered on its track.
- [ ] Change playback speed, use hold-to-speed, release it, and resume from pause. The displayed speed follows the actual player.
- [ ] Play local MP4 and remote MP4/HLS/DASH; check first response/frame, audio, intentional background pause/resume and headphone disconnection.

### Downloads and hidden UI

- [ ] Run a direct download and an HLS/DASH download. Percent/segment counters, thread bars and elapsed/remaining labels keep updating.
- [ ] Pause during active transfer, including near completion. The final counter and paused state appear immediately; the paused notification is dismissible.
- [ ] Resume, induce a recoverable connection failure, retry, cancel and complete a transfer. No counter rollback or stale status persists.
- [ ] Scroll an active row offscreen for at least ten seconds, then back. It immediately shows current progress and elapsed active time.
- [ ] Switch away from Downloads, close the browser download sheet, open the player over the Hub, then return. Downloads continue and the visible UI catches up.
- [ ] Background the app during a transfer. Its notification and transfer continue; reopening shows the current state.
- [ ] Scroll a Library/downloading grid while a transfer is active. Unrelated cards, headers and menus should remain steady.
- [ ] Open the Library after playback has updated its records while it was covered. Folder filtering, counts and sorting reflect the current records.

### Artwork and smoothness

- [ ] Fling Library and Downloaded grids with cached and uncached artwork, then stop. Visible images fill in promptly without blanking cached cards or flashing repeatedly.
- [ ] Watch an adaptive stream through its midpoint. Existing fallback artwork stays available and midpoint artwork can replace it.
- [ ] Repeat rapid controls/panel open-close, navigation and scrolling with subtitles and a concurrent download. Compare responsiveness and title stability to P2.
- [ ] If collecting performance evidence, compare identical journeys on the same phone/build mode/network and separate cold/warm artwork runs. Check frame deadlines/jank and UI CPU with controls visible, hidden, and with downloads visible/covered. Record battery/thermal conditions. This is the outstanding performance gate; debug/manual visual checks alone do not quantify a production frame-rate gain.
