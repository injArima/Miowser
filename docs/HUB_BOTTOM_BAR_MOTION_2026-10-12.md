# Hub bottom bar motion

Date: 2026-10-12. Scope: the Library / Downloads / More bottom bar. The earlier app audit remains deferred.

## Change

The old selection transition animated all three segment widths. Each frame remeasured the segments and their text under changing constraints, moved neighboring segments, and resized the shadowed rail. The existing fixed outer viewport protected the page from that size change, but the bar itself still performed layout work during destination mounting.

The bar now reserves a fixed slot for each button. Compact mode reserves enough space for the widest label, capped to fit the available width, and shows the selected label. Wide mode retains all labels. This can make the compact rail wider than before, but its width and touch targets stay steady as selection changes.

The highlight travels in the draw phase; icons move and labels fade through graphics layers. Highlight interpolation uses fractional pixels rather than rounding every frame, while resting positions match measured button boundaries. Press feedback, content-color transitions, the download badge, Library long press, RTL and reduced-motion support remain. Screen navigation executes on tap; there is no delay waiting for the animation.

This removes a known source of frame-time layout work. No device trace was collected, so the user's actual frame-rate improvement remains to be checked.

## Verification

**503 JVM tests passed**, with zero failures/errors/skips. Lint reported zero errors, 80 warnings and five hints. Debug and R8 unsigned release builds succeeded in 3m 31s. Scoped whitespace checks passed. A source comparison against the previous Library-cache build confirmed that only `HubHomePage.kt` and `HubRailLayout.kt` changed; that cache and the requested disabled automatic audio-focus handling are preserved.

Command: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`. Evidence, source diff, source manifest and APK hashes: `G:/Projects/Miowser/.local/optimization/hub-bar-2026-10-12-*`.

No emulator, device, ADB or instrumentation checks were run. The existing tests include fixed-viewport constraints/RTL and motion-policy coverage; they do not render this animation or prove device smoothness. No new tests were added for this reversible visual change.

## APK

[Installable debug APK](G:/Projects/Miowser/Miowser-0.1.0-hub-bar-debug.apk), 27,287,823 bytes.

SHA-256: `20bc8d1df4a2bc3130ae8f3af61f1014abcc75cbd8a73b85cd0883de864f249d`.

The release APK is unsigned and retained as build evidence; use the debug APK for the checks below.

## Device checklist

- [ ] Switch Library → Downloads → More → Library slowly, then rapidly in both directions. The rail stays the same size; the highlight follows the final selection without jumping.
- [ ] Repeat before all cold-page metadata/previews finish, then after they settle. The selected destination changes promptly and the bar remains responsive.
- [ ] Tap another destination while the highlight is moving. It retargets from its current position; no stale animation selects an older page.
- [ ] Tap the current destination repeatedly. No unnecessary navigation or highlight restart occurs.
- [ ] Check the selected label and inactive icons in portrait, landscape and split-screen. Labels do not overlap icons or clip outside their slots.
- [ ] Check light, dark and dynamic themes, RTL and increased font sizes. Selection, ellipsis, contrast and touch targets remain usable.
- [ ] Long-press Library and change its sort; open saved video details. The Library settings flow still works.
- [ ] Run/pause/complete downloads while switching destinations. The active-count badge remains visible and does not shift button positions.
- [ ] Enable app Reduce motion and Android animations off separately. Selection settles immediately and navigation still works. Also try positive Android animation scales.
- [ ] Return from Browser/player and background/resume. Selection, Library filters/scroll state and the previously added persistent inspection cache remain correct.
- [ ] Compare repeated switches on the same device/build conditions. Report any remaining lag with the starting page, destination, cache state and animation scale.
