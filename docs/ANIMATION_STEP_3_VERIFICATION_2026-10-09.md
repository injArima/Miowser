# Animation smoothness — step 3

Date: 2026-10-09. Scope: **Restrict animated work to the component that draws it** from the [animation plan](G:/Projects/Miowser/docs/ANIMATION_SMOOTHNESS_PLAN_2026-10-08.md). Device validation belongs to the user.

## Changes

| Area | Result |
|---|---|
| Hub selection pill | Animated position and width are read in drawing. The pill is no longer a measured child that changes width each frame. Its cached gradient is drawn within the pill bounds; RTL uses the matching physical position. Target positions use the same individual pixel rounding as segment layout. |
| Hub rail viewport | A full-width, fixed-height outer viewport reports stable geometry to the screen while the compact, centered inner rail expands/contracts. The necessary width measurement is confined to the rail. Existing 48 dp segment height, 4 dp inner padding, 2 dp gaps, shape and shadow remain. |
| Hub segment widths | Animation state is passed through composition and read only by a layout modifier during segment measurement. Labels remain unscaled; constraints still limit their available width and long labels ellipsize. The selected segment can fit narrower windows without an unnecessary 96 dp minimum. |
| Hub labels and icons | Label opacity and icon press scale update in graphics-layer lambdas. Label color uses BasicText's draw-time color producer. The icon's inherited color changes are confined to its small child scope. Text measurement is cached by text measurer, style, density/font scale and layout direction; viewport constraints determine width targets. |
| Browser Home and speed dial | Animated Home press color and speed-dial selection backgrounds are read during drawing. Selection borders, rounded clipping, icon tint, tap/long-press actions and labels retain their existing behavior. |

The Hub keeps one selection transition and Step 2's motion policy. Commands and destination selection start immediately. No paths, brushes, thumbnail loads or media decoding are introduced into frame callbacks; the pill gradient remains remembered. Video/WebView geometry, progress controls, sheets, folder/menu/counter UI and lazy-item keys are preserved.

Existing tab drag offsets already use placement-phase reads. Retained tab gesture position/velocity, close interruption and player-overlay transitions belong to numbered step 4; they are not changed in this patch.

## Local verification

- **468 JVM tests passed**, zero failures/errors/skips.
- Debug lint: **zero errors**, 81 warnings and 5 hints. These warnings remain; this is not a lint-clean app.
- Debug and R8/resource-shrunk **unsigned release APKs** built successfully in the settled verification run (4m 52s).
- The source manifest covers **310 source/test files**; all hashes match the verified files. Sources were not edited while verification was running.

Four new tests run the actual viewport measure policy against child widths changing in both directions, density/height constraints, oversized children and RTL centering. They verify stable reported screen geometry and bounded, centered placement. They do not simulate a complete Compose screen or count real device frames.

Source inspection confirms animated pill bounds are read during drawing, widths during local measurement and label opacity/press scale in graphics layers. No emulator, connected-device, instrumentation, layout inspector or on-device benchmark checks were run. Actual frame deadlines and the absence of unrelated grid/list remeasurement remain a device trace gate.

Evidence under `G:/Projects/Miowser/.local/optimization/`: `animation-step3-before/`, `animation-step3-source.diff`, `animation-step3-source-manifest.json`, `animation-step3-initial-verification.log`, `animation-step3-verification.log`, and `animation-step3-results.json`. Previous optimization work, Step 1/2 and their APKs are retained.

## Installable build

[Debug APK](G:/Projects/Miowser/Miowser-0.1.0-animation-step3-debug.apk), 27,656,227 bytes.

SHA256: `d03df0ff6018ec0643d31789353495a3b03dc3c827084dfebdca86a1b7625e04`.

The linked debug APK is installable. The release output is unsigned; its size/hash and the source-manifest hash are recorded in the results JSON. Source phase isolation and local measure-policy tests do not establish a measured production performance gain.

## Device checklist

Compare to the Step 2 build using the same folders, videos, downloads and font/display settings.

### Hub geometry and behavior

- [ ] Switch Library → Downloads → More repeatedly. Selection and content respond on tap; the pill lands on the selected segment and the rail remains centered.
- [ ] Reverse selection halfway through a transition, including Library → More → Library. No pill jump, outline mismatch or stranded label remains.
- [ ] Watch the screen above the rail during repeated switches. Library cards, the download list, headers and scrollbars should not visibly move with the pill.
- [ ] Check each segment while its width changes. Taps choose the correct destination; its icon stays centered vertically and its label remains readable and unscaled.
- [ ] Long-press Library when selected and unselected, then during a selection transition. Library settings opens once with the existing compact, continuous sheet.
- [ ] Run 1, 9, 10 and more active downloads. The download badge remains readable and does not clip or overlap the label during selection changes.
- [ ] Repeat on a narrow portrait screen, landscape and split screen. The rail stays within the available width and selected labels ellipsize cleanly when necessary.
- [ ] Repeat at 150% and 200% font scale and with altered display size/density. The rail has stable height, labels are not stretched, and icons/touch targets remain usable.
- [ ] On a wide layout, verify the all-label rail. At large font scale, verify the compact selected-label layout; resizing between modes leaves the pill aligned.
- [ ] Enable RTL. Segment order, labels, pill target position and long press follow the mirrored layout; no highlight travels outside the rail.
- [ ] Rotate or resize during selection, then tap another destination. The cached text geometry refreshes and no stale target remains.
- [ ] Check light, dark and dynamic themes. The subtle pill gradient, surface, soft shadow, label colors and clipping match the previous build.

### Motion and accessibility

- [ ] With Follow system, try Android animation scales 0.5×, 1× and 2×. Timings follow the system and destination actions remain immediate.
- [ ] With app Reduce motion enabled and with Android animations off, repeat every destination selection. All transitions settle; no stuck scaled icon or hidden interactive label remains.
- [ ] Press/release Hub icons repeatedly. Press scaling stays restrained and only the icon scales; the hit area remains fixed for the current segment.
- [ ] Use TalkBack and keyboard navigation. Each segment announces its label and selected state once; Library's long-press action remains available. The decorative pill adds no focus target.

### Browser and regression

- [ ] Tap and hold Home. Its press-color response stays circular, and click/long-press behavior remains intact without shifting other bottom-bar controls.
- [ ] Select, deselect, rearrange and open speed dials. The selection fill remains rounded with the existing border and icon tint; labels do not flicker with the color transition.
- [ ] Test the URL media counter, folder chip menus and Library counts. None is clipped by the rail changes.
- [ ] Repeat Hub switches while downloads update and while real artwork is loading. Existing progress, pause/completion state and thumbnail readiness remain correct.
- [ ] Play with subtitles, change settings, seek and open the playlist. Step 1 clocks, stable title, progress/timer alignment and existing player geometry remain intact.
- [ ] Swipe tabs, cancel a swipe, scroll vertically, close and Undo. Existing behavior is preserved; retained swipe/velocity changes are a later step.

### Performance gate

- [ ] If collecting traces, inspect repeated Hub switches: pill painting should not drive Library/download grid measurement every frame. Expect the small rail to measure while segment widths change and the small icon scope to update its inherited color.
- [ ] Compare matched release-equivalent runs at 60 Hz and higher refresh rates, with the same artwork/network/thermal conditions. Record frame-overrun tails, missed deadlines, UI CPU and first response. Successful local tests and debug visual checks do not establish a measured production speed/frame-rate gain.
