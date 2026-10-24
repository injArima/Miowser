# Miowser animation smoothness plan

Date: 2026-10-08. Updated: 2026-10-09. Steps 1–5 are implemented (489 JVM tests, debug lint, debug and R8 release builds after step 5). Benchmark/profile journeys are prepared; device validation, profile collection and measured performance acceptance remain user-owned.

## Goal

Make motion feel continuous, responsive, and consistent while reducing work on the UI thread. Preserve Miowser's compact layout, continuous bottom sheets, full-screen subtitle settings, right-side playlist, readable controls, soft shadows, and existing gesture behavior.

Smoothness and speed need separate evidence: an attractive 300 ms transition can still drop frames or delay an action. Use the [performance acceptance contract](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md) for every change.

## What Nuvio actually does

Inspected the public `cmp-rewrite` branch at revision `78e6373a86e0fdb6336086ab8f8ad7478a9d8737`, including Android-specific code. Its current Android implementation uses Compose, so several techniques fit Miowser directly. Source references below are pinned to that revision.

Twenty selected source files were read without building or running Nuvio. Of the initial eighteen animation/UI files, sixteen have the same Git blob as the October 2 `0.5.6-beta` tree; `PlayerControls.kt` and `PlayerScreenContent.kt` differ. This establishes continuity of the core techniques, not which release appeared in the user's recording. Source inspection does not prove Nuvio's frame rate on a particular device.

| Observed implementation | Lesson for Miowser |
|---|---|
| Shared motion tokens: fast 150 ms, normal 220 ms, sheet enter/exit 300/250 ms, and named Bezier curves. | Give app-owned motion a small consistent vocabulary. Duration consistency improves coherence; it does not remove frame stalls. [Tokens.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Tokens.kt#L102). |
| Android's floating navigation bar retains a motion object, advances it with `withFrameNanos`, and reads transforms in layer/draw callbacks. Its loop runs while motion is active. | Use frame-paced, local animation state; stop when settled. Stock Compose animation primitives should be the starting point for Miowser. [FloatingNavigationBar.android.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/FloatingNavigationBar.android.kt#L81). |
| The navigation motion object retains spring position/velocity, bounds targets, clamps elapsed time, and checks whether springs are at rest. | Interruption should retarget current motion instead of visibly restarting. Do not port its custom physics engine just to get a travelling pill. [JellyMotion.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/jelly/JellyMotion.kt#L128). |
| Poster navigation derives multiple effects from one elapsed-time animation, retains incoming transition state, and explicitly completes the request after settlement. | Coordinate an overlay's content, scrim, and dismissal with one transition and clear lifecycle ownership. [PosterNavigationMotion.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/navigation/PosterNavigationMotion.kt#L19). |
| Poster lifting records the drawn source into a graphics layer and releases ownership when it is no longer retained/lifted. | Reuse already available artwork if a future card transition warrants it; do not synchronously reload or capture video for decoration. [PosterLift.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterLift.kt#L58). |
| The root-tab host retains visited panes and saveable state, measures/places the selected pane, and gives inactive panes a restricted lifecycle, focus, pointer, and accessibility state. | Preserve lightweight screen state and suspend hidden UI work. Retaining every live WebView would defeat Miowser's browser memory budget. [RootTabHost.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/RootTabHost.kt#L28). |
| Player metadata alpha is animated locally and applied in a graphics layer; gesture feedback uses visibility fades and an 80 ms level tween. | Separate visual feedback from playback state and keep feedback responsive. [PlayerControls.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerControls.kt#L344), [PlayerGestureOverlay.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerGestureOverlay.kt#L64). |

### Apply the technique selectively

Nuvio also uses glass/haze, blur, and a cinematic poster transition: 550 ms with content reveal after 400 ms. Those effects are not performance optimizations. Miowser should begin with translation, alpha, and modest press scaling, and start the requested action immediately. Do not impose a content-delay timer, full-screen blur, or animated video/WebView scaling. [PosterOpenMotion.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterOpenMotion.kt#L8), [PosterNavigationTransition.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/navigation/PosterNavigationTransition.kt#L73).

On Android, Nuvio's common sheet wrapper delegates motion to Material3 `ModalBottomSheet`. Its inset, fully rounded card design differs from the continuous sheets requested for Miowser. Keep Miowser's sheet geometry and Material3's existing drag/settle owner. [BottomSheet.kt](https://github.com/NuvioMedia/NuvioMobile/blob/78e6373a86e0fdb6336086ab8f8ad7478a9d8737/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/BottomSheet.kt#L42).

These are independently applicable implementation principles, not a proposal to copy Nuvio's UI or migrate to its navigation stack.

## Miowser's current motion and bottlenecks

| Area / inspected source | Current behavior | Proposed improvement |
|---|---|---|
| [ExpressiveTokens.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/ui/theme/ExpressiveTokens.kt:8) | Step 2 adds named motion timings/curves alongside theme tokens and a shared Reduce motion preference. | Tune on device; preserve framework-owned motion and Android scaling. |
| [HubHomePage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/HubHomePage.kt) | Step 3 confines rail motion to drawing/local measurement. Step 5 preserves destination scroll/filter/selection state in saveable buckets and disposes inactive or covered content. Content switches immediately. | Validate restoration and phase/layout costs on device; no populated-grid crossfade is added without evidence. |
| [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt:669) | P1 isolated the external subtitle renderer. Step 1 makes paused rendering event-driven and moves the visible timeline clock into progress/timer leaves; step 2 gives controls and both scrims one explicit 220/160 ms visibility fade. | Validate visibility independently of pause/buffer/title state; retain clock/lifecycle isolation. |
| [PlayerOverlayHost.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerOverlayHost.kt), [PlayerPlaylistPanel.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerPlaylistPanel.kt) | Step 4 retains outgoing playlist/subtitle presence through a coordinated scrim/content exit with request/revision guards, interaction ownership and focus restoration. | Validate rapid Back/reopen and accessibility on device. Keep the compact right panel and icon between Lock and Back. |
| [TabsPage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabsPage.kt), [TabSwipeMotion.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabSwipeMotion.kt) | Step 4 retains a motion owner per tab, resumes from the visible offset and settles with bounded release velocity. Cancelled/stale gestures cannot close; placement-phase offset reads remain. | Validate horizontal/vertical arbitration, resize, rapid interruptions and close-once behavior on device. |
| [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt:1580) | Step 2 coordinates 200/140 ms enter/exit fade/slide/scale with travel limited to 12 dp. | Keep its compact form; validate the existing scroll threshold and rapid reversals. Check rapid reversals and near-top oscillation before changing the look again. |
| [HubListPlacement.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/HubListPlacement.kt) | Step 5 permits placement after an exact confirmed manual Library removal/move-out, with stable keys and no entry/exit fades. Bulk/rule/filter/reordered changes snap; scrolling and reduced motion suppress placement. | Validate interrupted placement, artwork stability and bulk-change costs on device. |
| [ExpressiveSurfaces.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/ui/ExpressiveSurfaces.kt:50) | Shared Material3 sheets own their motion and compact shape. | Standardize dismissal sequencing; avoid a second animation competing with sheet drag/settle. |

The largest source-confirmed smoothness risk is work happening alongside animation, not missing easing curves. The general audit's UI publication, persistence, artwork, and transfer changes must accompany this plan.

## Implementation plan

### 1. Remove frame-time competition first

Implemented 2026-10-09. See the [implementation evidence and 33-item device checklist](G:/Projects/Miowser/docs/ANIMATION_STEP_1_VERIFICATION_2026-10-09.md). The device performance gate below remains open.

- Put external subtitle lookup and updates in a dedicated renderer scope. Keep its fine clock and millisecond delay; publish/apply cues when the active cue set, seek, or style changes. Paused style edits must still apply immediately. Do not reattach/refetch subtitle files for a visual adjustment.
- Sample download UI progress initially to at most four publications/s, with immediate completion/error/pause transitions. Keep transfer accounting independent of display sampling.
- Stop UI-only clocks/collectors when their component is not visible. A hidden controls timer can stop while subtitle playback continues; a hidden app UI must not accidentally stop authorized playback/downloads.
- Preserve visible-artwork prioritization and scrolling decode deferral. Start with these source-backed costs; use traces to choose subsequent work.

**Gate:** playing subtitles and active downloads no longer repeatedly invalidate unrelated player/control/layout scopes. Cue timing, title stability, and first response must remain correct. Lower UI CPU and better frame deadlines must be confirmed on the user's device.

### 2. Establish a small motion policy

Implemented 2026-10-09. See the [implementation evidence and 29-item device checklist](G:/Projects/Miowser/docs/ANIMATION_STEP_2_VERIFICATION_2026-10-09.md). Shared tokens and reduced-motion behavior are applied to existing app-owned motion; playlist/subtitle transitions and retained swipe velocity remain later-step work. Device validation remains open.

Create named motion specifications alongside the existing theme tokens. Starting values below are proposals to tune, not measured optimums.

| Interaction | Starting specification | Constraints |
|---|---|---|
| Button press/release | 80–100 ms press; 120–150 ms release; scale 1 ↔ 0.97 | Keep touch bounds fixed; disabled controls do not animate as enabled. |
| Compact feedback/jump-to-top | 180–220 ms enter; 120–160 ms exit; short translation ≤12 dp | One transition; no bounce or visibility churn near threshold. |
| Player controls + top/bottom scrims | 200–220 ms enter; 140–160 ms exit | Fade together; playback state changes do not restart visibility motion. |
| Right-side playlist | 220–260 ms enter; 160–200 ms exit | Translate from right, coordinated scrim; no scaling of video or shifting progress controls. |
| Full-screen subtitle settings | 180–220 ms enter; 140–160 ms exit | Light fade/short translation; fixed compact sample preview; video remains stable. |
| Hub destination switch | 180–220 ms selection; modest content fade if needed | Show selected content promptly; no fade-through blank interval or fetching delay. |
| Swipe release/snap-back | Spring using current position and release velocity; near-critical damping | Tune by distance/device; no fixed completion duration promised for a spring. |
| Material3 sheets/menus/snackbars | Retain framework-owned motion | Unify lifecycle and appearance; do not wrap in another competing transform. |

Use a standard emphasized deceleration curve for entry/selection, and a restrained accelerating exit. Restrict bouncy motion to a gesture release where it communicates physical continuity. Do not bounce text, subtitles, timers, or the player seek thumb.

Honor the platform animation-duration setting with standard Compose machinery. Custom gesture/transition completion must also work when animations are disabled. Provide an app reduced-motion preference if additional control is needed: remove scale, slide, overshoot, and decorative item motion, using immediate state changes or a brief permitted fade. Respect the strictest setting. Functional drag/seek feedback remains immediate and understandable.

### 3. Restrict animated work to the component that draws it

Implemented 2026-10-09. See the [implementation evidence and 24-item device checklist](G:/Projects/Miowser/docs/ANIMATION_STEP_3_VERIFICATION_2026-10-09.md). Hub pill bounds are read during drawing, segment widths during local measurement, and opacity/press scale in graphics layers; a fixed outer viewport contains animated rail measurement. Browser press/selection background colors also update during drawing. Device traces remain required for the gate below.

Read frequently changing translation/scale/alpha in `graphicsLayer` lambdas and positional state in the lambda `offset` modifier when appropriate. That lets eligible changes skip broader composition/measurement. Keep geometry/text measurements keyed to actual content, font scale, density, and constraints. Stable lazy keys preserve item identity. These follow the [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices).

For Hub's variable-width rail, preserve readable unscaled labels. Paint/position the pill from animated bounds; keep any necessary label/segment width animation local. Do not fake text resizing by stretching the whole segment. Avoid allocating paths/brushes or decoding images per frame. A layer is not free: keep its area small and inspect GPU/overdraw costs too.

**Gate:** moving the pill must not cause the Library grid or download list to remeasure each frame. Motion cannot shift hit targets unexpectedly or clip the folder/menu/counter UI.

### 4. Make interruption and dismissal deliberate

Implemented 2026-10-09. See the [implementation evidence and 37-item device checklist](G:/Projects/Miowser/docs/ANIMATION_STEP_4_VERIFICATION_2026-10-09.md). Tab settlement retains position/velocity ownership; playlist and subtitle overlays retain exit presence and coordinate scrim/content motion, cancellation, interaction and focus. Playback commands start immediately. Device validation remains open.

Use one retained gesture owner per tab/card. Track release velocity, cancel settlement when a new drag takes control, and begin from the current visible position. Vertical scrolling keeps precedence once it wins; a cancelled pointer must not close a tab. Commit close exactly once after the completion policy is satisfied; cancellation never fires a stale callback.

For player overlays, maintain target visibility separately from rendered presence. Keep outgoing content until exit completes; repeated open/close/Back retargets the current transition. Use latest callbacks and request identity so completing an old exit cannot close a newly opened panel. Hidden controls must cease accepting clicks/focus; a closing modal still owns interaction until it is dismissed.

Player preparation, folder changes, subtitle selection, and next-video commands start immediately. Animation completion controls presentation cleanup, not the start of useful work. Restore focus to the triggering control after modal exit; avoid simultaneous competing overlays.

**Gate:** rapid interruptions have no positional snap, title flash, stale callback, stuck dimming layer, accidental click-through, or duplicate action.

### 5. Polish navigation and list changes without retaining heavy screens

Implemented 2026-10-09. See the [implementation evidence and 36-item device checklist](G:/Projects/Miowser/docs/ANIMATION_STEP_5_VERIFICATION_2026-10-09.md). Saveable destination/sublist buckets preserve lightweight state while inactive content is disposed. Exact manual Library removals/move-outs get restrained placement; bulk changes snap. Populated Hub journeys and interaction profile paths are prepared and compiled. Actual profile refresh and performance measurement remain pending for the user's device runs; existing profiles are preserved.

Preserve each Hub destination's lightweight scroll/filter/selection state. Decide whether a brief content transition adds clarity after frame costs are fixed; keep transitions small and do not hold two populated grids through a long crossfade. Pause collectors in inactive content.

Animate placement for a single added/removed/moved item only when helpful. Search, sorting, automatic rule reorganization, restored data, and bulk imports should update without a cascade of per-card entrance animations. Keep thumbnail bounds/placeholders stable. Avoid repeated image fades during a fling or when a cached image returns.

Leave shared artwork-to-player transitions as a later optional experiment. If attempted, reuse the existing thumbnail, release it reliably on cancellation, and begin player preparation immediately. Never capture a decoder/WebView surface merely to run a transition. Ship only if first-frame latency and memory remain acceptable.

## Delivery order

| Patch | Scope | Required evidence |
|---|---|---|
| A | Subtitle/UI update isolation; progress sampling; hidden-clock ownership | Existing functional suite plus focused lifecycle/timing checks; user playback + transfer trace. |
| B | Motion tokens and reduced-motion behavior; shared control/scrim transition | Interruption/zero-duration completion checks; no title flash or added first-response delay. |
| C | Hub rail phase reads and retained swipe state/velocity | Pill/layout scope trace; horizontal/vertical arbitration and rapid swipe interruption checks. |
| D | Coordinated playlist/subtitle overlay transitions and focus | Right-panel/full-screen geometry, rapid Back/reopen, TalkBack/focus, stable playback. |
| E | Limited list/navigation polish and profile refresh | Real-artwork scrolling and repeated destination switching under controlled release builds. |

Implement in small reviewable patches. Do not combine this with an app-wide navigation rewrite, custom physics port, or visual overhaul. Do not upgrade dependencies solely to imitate Nuvio; verify the installed API support when implementing.

## Measurement and acceptance — user runs device checks

Extend the existing [benchmark journeys](G:/Projects/Miowser/benchmark/src/main/java/com/miowser/benchmark/AppBenchmarks.kt) with repeated Hub switches, interrupted tab swipes, jump-to-top reversals, player-controls toggles, playlist/subtitle open/close, and playback with real subtitles plus active download progress. Existing placeholder Library and failed-download fixtures are insufficient for these workloads. Refresh interaction profiles after the hot paths settle; compare profile effects separately.

Record frame-overrun p50/p95/p99, missed-deadline proportion, long stalls, first-response/first-frame time, main-thread CPU, memory/GC, and refresh rate. Prefer actual platform frame deadlines: nominal display periods are about 16.7 ms at 60 Hz, 11.1 ms at 90 Hz, and 8.3 ms at 120 Hz, but the application does not receive that entire period as a CPU budget. Inspect the frame timeline rather than using one universal 16 ms threshold.

Targets for a fixed representative journey:

- Aim for <1% missed deadlines and fewer/worse-tail stalls than the matched current baseline. This is an acceptance target, not a result or guarantee for every website/device.
- No repeated unrelated screen recomposition/measurement driven solely by a small control animation or subtitle clock.
- No extra tap-to-first-frame or command-start delay. Related latency, throughput, memory, and visual readiness must pass the audit's regression contract.
- No active custom animation loop after settlement or hidden UI timers without a functional reason. Required spinner/progress animation stops when its state/component no longer needs it.
- Accessibility, gesture continuity, and zero-duration completion pass alongside frame metrics. Fewer frames achieved by removing necessary feedback is not success.

Do not assess debug builds or screen recordings alone as proof of performance; recordings are useful for visual defects. Compare release-equivalent builds on the same device under matched thermal/compilation/cache conditions and report variability.

## Device checklist for the user

Use the relevant subset after each patch, then the complete list for the final build.

- [ ] Switch Library → Downloads → More rapidly and reverse midway. Selection pill, labels, and content remain coherent; destination scroll state survives.
- [ ] Repeat with a populated real-thumbnail Library, large Downloads list, and running progress updates. No blank-image tradeoff or bulk entrance cascade.
- [ ] Drag a tab slowly, fling it, cancel halfway, and grab it during snap-back. Test both directions and vertical grid scrolling. Closing fires once; Undo restores correctly.
- [ ] Scroll around the jump-to-top threshold, reverse a fling, and tap while its visibility changes. No flicker, surprise jump, or stranded button.
- [ ] Toggle player controls during playing, paused, seeking, and buffering. Title does not flash; scrims and controls stay coordinated; spinner remains centered.
- [ ] Open/close/reopen the right playlist rapidly using the icon, outside tap, and Back; select next/previous while it moves. No stale panel, double selection, or click-through.
- [ ] Open full-screen subtitle settings, change every style/delay/position control while playing and paused, then close/reopen. Captions persist; the compact two-line sample stays stable on grey.
- [ ] Seek repeatedly and use brightness/volume gestures. Values respond immediately; level feedback, seek destination, progress/thumb/timer alignment remain correct.
- [ ] Interrupt a transition with orientation change, app background/foreground, split-screen resize, or navigation. No dimming layer, leaked capture, or lost selected file.
- [ ] Set system animations off and test reduced-motion mode. Every action still completes; no delayed callback or stuck exit.
- [ ] Check TalkBack/focus, keyboard, 150/200% font size, light/dark/dynamic themes, and RTL. Invisible panels are not focusable; compact controls retain usable touch targets.
- [ ] Compare 60 Hz and higher refresh rates where available, including sustained playback with downloads. Record thermal state and frame results; report the weakest journey as well as the average.

## Recommendation

Start with update isolation and gesture interruption, then coordinate controls/overlays and improve the Hub rail. Nuvio's strongest transferable ideas are local frame-driven state, continuity during interruption, coordinated clocks, reusable visual content, and clear lifecycle ownership. They provide a concrete engineering direction; the user's matched before/after device measurements determine whether Miowser actually becomes smoother and faster.
