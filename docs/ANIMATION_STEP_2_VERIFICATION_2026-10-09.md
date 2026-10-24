# Animation smoothness — step 2

Date: 2026-10-09. Scope: **Establish a small motion policy** from the [animation plan](G:/Projects/Miowser/docs/ANIMATION_SMOOTHNESS_PLAN_2026-10-08.md). Device testing belongs to the user.

## Changes

The Hub and browser settings now expose the same persistent **Reduce motion** preference. Browser settings uses a switch under Accessibility; Hub → More offers Follow system / Reduce. The preference defaults to following Android and is included by the existing settings backup/restore path.

[MotionPolicy.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/ui/theme/MotionPolicy.kt) provides shared durations, emphasized acceleration/deceleration curves, a 12 dp travel limit and a restrained 0.97 press scale. Both the app theme and the separate player-dialog theme install the policy.

| Existing interaction | Normal motion | Reduced motion |
|---|---|---|
| Hub navigation press/release | 90 / 140 ms; icon scale 1 ↔ 0.97, fixed touch bounds | Scale stays 1 |
| Browser Home press color | 90 / 140 ms | Immediate color change |
| Hub navigation selection and speed-dial selection | 220 ms, one existing Hub selection transition | Immediate selected state |
| Jump-to-top | Coordinated 200 ms enter / 140 ms exit, 12 dp travel, subtle scale and fade | No travel/scale, immediate visibility settlement |
| Player controls and top/bottom scrims | One owner, 220 ms fade in / 160 ms fade out | Immediate visibility settlement |
| Default-folder strip repositioning | 220 ms scroll when default changes | Immediate repositioning |
| Existing tab dismissal/return | Named 120 / 180 ms timings using the existing gesture owner | Immediate target placement and close completion |

Playback commands, destination selection and other requested actions start immediately. Player pause, buffering and title updates do not retarget controls visibility. The video surface and WebView receive no decorative scale or blur. Existing compact dimensions, sheets, subtitle preview, playlist position and progress layout are preserved.

### System settings and framework ownership

The policy reads Compose's installed Android animation-duration scale. Standard Compose animations apply positive system multipliers once; the app does not multiply durations itself. Android scale zero **or** the app preference selects reduced motion for app-owned transitions. Tab settlement also checks the current coroutine scale, including if a captured policy predates the zero setting, and checks cancellation before issuing its completion callback.

The app preference governs **app-owned** motion. Material3 sheets, menus, snackbars, ripples and other framework-owned components keep their own gesture/animation owner and follow Android's system scale. Material3 1.4.0 exposes its motion scheme internally; this implementation uses no internal API, reflection, dependency upgrade or second wrapper transition. Turning off Android animations remains the way to suppress framework-owned motion as well.

Tokens for playlist/subtitle entrances and a critically damped swipe spring are available for the later implementation steps. This patch does not add those panel transitions, retained swipe velocity or the Hub drawing/layout refactor. Changing the motion preference alone does not reset the folder strip's manually chosen scroll position.

## Local verification

- **464 JVM tests passed**, zero failures/errors/skips.
- Debug lint: **zero errors**, 82 warnings and 5 hints. The warnings remain; this is not a lint-clean app.
- Debug APK and R8/resource-shrunk **unsigned release APK** built successfully in the settled local verification run (4m 7s).
- The manifest covers **308 source/test files**; every hash matches the files used for verification. No source edits occurred during the build.

Nine new JVM tests exercise strictest-setting precedence, disabled/resting press scales, monotonic non-overshooting curves, frame-free reduced/system-zero settlement, actual normal-animation completion, positive Android-scale handling and interrupted/cancelled completion safety. No emulator, connected-device, instrumentation or on-device benchmark checks were run.

Evidence is stored under `G:/Projects/Miowser/.local/optimization/`: `animation-step2-before/`, `animation-step2-source.diff`, `animation-step2-source-manifest.json`, `animation-step2-verification.log`, and `animation-step2-results.json`. Previous optimization work, Step 1 and their APKs are retained.

## Installable build

[Debug APK](G:/Projects/Miowser/Miowser-0.1.0-animation-step2-debug.apk), 26,922,786 bytes.

SHA256: `4cd3f126f69264c29fbe6bb12058621b9ae76aa7dbcd920efc67cb5ec1afd362`.

The debug APK is installable. The R8 release output is unsigned and is retained under `app/build/outputs/apk/release/`; its size/hash are recorded in the results JSON. Device frame-rate, CPU, visual interruption and accessibility checks remain pending; successful local builds do not quantify a production performance gain.

## Device checklist

Use the Step 1 build as the visual/regression baseline. Test portrait and landscape where relevant.

### Preference and accessibility

- [ ] Fresh settings default to Follow system. Browser → Settings → Accessibility and Hub → More show the same current preference.
- [ ] Change the preference in either location, then visit the other. Restart the app; the chosen value remains.
- [ ] Back up settings with Reduce enabled, change it, restore the backup and reopen. Reduce is restored. An older backup without the key defaults to Follow system.
- [ ] With app reduction off, try Android Animator duration scale 0.5×, 1× and 2×. App transitions follow the scale; commands still start on tap.
- [ ] With Android animations off and app reduction off, every navigation, show/hide and tab dismissal completes. No invisible blocking panel or stranded tab remains.
- [ ] Enable app reduction with Android animations at 1× and 2×. App-owned motion remains immediate; app slide/press scale is absent. Framework sheets/menus retain their Android-controlled motion.
- [ ] Change Android scale while Miowser is open, then repeat these actions. No restart should be needed for the installed Android scale to take effect.
- [ ] Check TalkBack, keyboard navigation, RTL, light/dark/dynamic colors and 150/200% text size. Labels remain legible and touch targets do not shrink.

### Hub and browser

- [ ] Press and release each Hub destination. The icon response is restrained, the touch area stays fixed, and content appears promptly.
- [ ] Switch Library → Downloads → More rapidly, reversing selection midway. Pill, labels and icon colors reach the final selection without a blank content interval.
- [ ] Repeat with Reduce enabled. Selection is immediate, and no pressed icon remains scaled.
- [ ] Press browser Home and select speed-dial entries. Color response is coherent; Home click and long press keep their existing behavior.
- [ ] Reveal/hide jump-to-top during scrolling and reverse a fling. Its fade, short travel and scale remain coordinated without bounce. Check near-threshold flicker; threshold behavior was not redesigned in this step.
- [ ] Tap jump-to-top while entering/exiting and repeat with Reduce enabled. The page responds, and no hidden control continues intercepting taps.
- [ ] Change the default Library folder after scrolling the folder strip. The default moves first and the strip repositions. Changing only the motion preference must not reset a manually scrolled strip.

### Player

- [ ] Show/hide controls while playing, paused and buffering. Top/bottom scrims and controls fade together; no title flash or orphaned dark overlay remains.
- [ ] Pause/resume and trigger buffering with controls already visible. These playback changes must not restart the visibility fade. Buffering spinner stays centered.
- [ ] Toggle controls rapidly, lock/unlock, rotate, and background/return during a fade. The final visibility state is correct and controls are usable.
- [ ] Repeat with app reduction on and Android animations off. Every show/hide completes immediately without a stuck dimming layer.
- [ ] Check disabled actions remain visibly disabled, cannot execute, and do not acquire enabled press scaling. White controls and soft shadows remain readable.
- [ ] Seek, scrub and toggle elapsed/remaining time. Thumb, track, timer and action-row alignment remain unchanged; commands do not wait for fades.
- [ ] Play with VTT/SRT subtitles, then edit font, position, opacity and millisecond delay while paused and playing. Captions persist; the compact sample remains on grey.
- [ ] Open subtitle settings and the right playlist. Their geometry and selection behavior remain intact; their new animated entrance/exit belongs to a later step.

### Tab gestures and regression

- [ ] Swipe a tab in either direction past the close threshold. It closes once; Undo restores it. Repeat with app reduction and Android scale zero.
- [ ] Drag below the threshold and release; the tab returns fully. Start another gesture during settlement and leave the Tabs page during settlement. No cancelled close callback removes the wrong tab.
- [ ] Scroll the tab grid vertically and tap cards/close buttons. Existing gesture arbitration and click behavior remain intact. Retained-position/velocity improvements belong to step 3.
- [ ] Navigate and scroll while downloads and subtitles are active. Transfer counters, pause/completion states, thumbnails, title stability and hidden-UI clock behavior from Step 1 remain correct.
- [ ] Open/drag/dismiss Material3 sheets and menus. There is one motion owner, continuous sheet geometry and no duplicate slide/scale effect.
- [ ] Compare matching release-equivalent runs at 60 Hz and higher refresh rates if available. Record frame deadlines/jank, first response, device/thermal state and variability with identical artwork/network conditions. Local tests and debug visual checks do not prove a production frame-rate improvement.
