# Futurice P2 implementation and acceptance

This is F10–F12 from `FUTURICE_BEST_PRACTICES_AUDIT_2026-10-12.md`. Existing player, Library, transfer, and browser behavior remains the regression contract. The repository contains earlier uncommitted work; these checks cover the current combined working tree.

## F10: text and accessibility

- Moved shared Browser and Hub navigation labels, long-press labels, download-section counts, and the Library video count to Android resources. The visible English text and compact navigation layout remain the same. The Library count now uses Android plurals.
- Hub navigation and Downloads segment benchmarks use stable Compose test tags. The tab-overview button has a stable tag as well. The Hub Downloads badge is hidden from separate screen-reader announcements; the navigation segment announces the active count once.
- These are the first small resource batches, not a claim that the full app is translated. On a device, verify TalkBack focus/order and count announcements, RTL navigation, 150% and 200% font scaling, contrast in both themes, and 48dp touch targets. Keep the subtitle sample and player overlays compact.

## F11: static checks

- Added `.editorconfig` and `checkKotlinFormatting` to local CI. The check rejects tabs, trailing whitespace, and missing final newlines in Kotlin source; it does not reformat the existing tree.
- Replaced five boxed Compose numeric states with primitive states, moved `Modifier` ahead of other optional parameters in two composables, removed API 26 guards below the app's minSdk 26, and pointed the manifest round icon at its existing round resource.
- Fresh debug lint: **0 errors, 63 warnings** (59 `UseKtx`, 3 `MissingOnRenderProcessGone`, 1 obsolete `-v26` resource-folder suggestion). The prior P1 report was 0 errors, 69 warnings, 5 hints. Moving the adaptive-icon XMLs to `mipmap-anydpi` made AAPT unable to find either manifest icon, so they remain in `mipmap-anydpi-v26` and that one suggestion is retained.
- The renderer warnings persisted in a fresh lint run, but `MiowserWebViewClient.onRenderProcessGone` and the popup client's override in `Tab.kt` both return `true` and clean up their respective views. Lint reports the popup site twice. These are documented as analysis false positives; no broad suppression hides a future missing handler.
- `UseKtx` is a style category, not a crash or performance finding. Many flagged preference edits intentionally call synchronous `commit()` for durable session, backup, or download state. Do not auto-replace them with asynchronous `apply()`. URI and bitmap suggestions can be considered when touching those files. The version notices from the older audit need an isolated dependency compatibility review, not an all-at-once upgrade.

## F12: fixture coverage and measurement gate

- Added tall benchmark-only browser pages so jump-to-top can be exercised, plus journeys for short tab-swipe reversals, repeated jump-to-top actions, player controls and overlays, and remote subtitle controls. The fixture server already provides a real VTT response and an active slow transfer; run relevant journeys with `-FixtureServer http://127.0.0.1:8765` and `-ActiveTransfers` where applicable.
- Macrobenchmark frame metrics measure journey rendering, **not** input-to-action latency or actual subtitle visibility. Inspect traces and record those separately. A short automated swipe followed by an opposite swipe approximates reversal; a finger interruption during settling still needs manual inspection.
- Use a physical midrange phone and a low-memory phone. For each, hold build, fixture, OS/WebView, battery/power mode, refresh rate, ambient/thermal state, and font scale constant across paired runs. Capture the runner's source checkpoint, APK hash, Perfetto traces, first display/fully drawn, frame deadlines/overruns, and battery/thermal records. Also note CPU, memory/GC, first response after tapping a player control, first video frame, and whether the caption is visually present after changing delay/style.
- Run `tabSwipeReversals`, `jumpToTopReversals`, `playerControlsAndOverlays`, `remoteSubtitleControls`, `hubNavigation`, and `hubDownloadLists` with 20 iterations and matched `-R8` runs. Stress 30 tabs and 1,000 Downloads. Repeat Hub journeys with active transfers. Manually test actual interrupted swipes, playback/playlist/rotation, VTT and SRT selection and millisecond delay, paused/resumed notifications, and focus/RTL/font scaling.
- The profile generator already covers startup, Hub navigation, Downloads list switching, Library scrolling, player/playlist, browser tabs, and remote subtitle settings. After the physical journeys are stable, collect `-Journey profile` with the fixture server and import it using `Update-OptimizationProfile.ps1`; then rebuild release and compare with/without the profile under otherwise matched conditions. No profile regeneration or runtime speedup is claimed here.

## Local verification

On 2026-10-15, `checkKotlinFormatting`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleBenchmark`, `:benchmark:assembleBenchmark`, and the R8 `:app:assembleRelease` passed offline. The unit run contained **514 tests, zero failures/errors**. The benchmark APK compiled but its journeys were not executed on a device in this pass; physical-device acceptance remains open.
