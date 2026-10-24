# Step 3 physical-device measurements — 2026-09-26

These measurements use the separate `com.miowser.app.benchmark` installation. The ordinary Miowser installation and its data were not modified. Use the [run guide](G:/Projects/Miowser/docs/STEP03_BENCHMARKS.md) to reproduce the scenarios.

## Conditions

- Samsung SM-G990B2, Android 16 / API 36; reported usable RAM 7,534,648 KiB (this is not a low-memory reference device).
- WebView 153.0.8010.36; active display 1080 × 2340 at 60 Hz; font scale 0.8; locale `en-US-u-fw-mon-mu-celsius`.
- USB connected; power saving enabled and left unchanged. Initial battery 56%, battery temperature 36.1°C, Android thermal status 0. During the first startup series thermal status rose to 2; final battery temperature was 39.9°C and skin temperature 43.3°C.
- Later runs began/ended at status 2 until the 30-tab startup run ended at status 3 (severe). The 30-tab switching run stayed at status 3; the 1,000-download run began at 3 and ended at 2. At the end of all testing the battery was 49% and 40.9°C. These later stress results are thermally constrained. No battery or thermal guard was suppressed or device power setting overridden.
- These are measurements under the user's current phone settings. Thermal conditions are not controlled and normal filter initialization/background phone activity are not isolated. Do not attribute differences solely to R8 or profiles, or declare shipping performance gates passed from these runs.
- Synthetic fixture: 500 Library records, 2,000 History records, default 200 failed download records and 10 embedded tabs. Only one Library entry has playable media; the others are metadata placeholders, not representative thumbnail workloads.

## Completed measurements

All durations below are milliseconds. p95 uses nearest rank and all samples in the successful run.

| Configuration | Tabs / downloads | Launches | First display median / p95 | Fully drawn median / p95 | Evidence directory |
|---|---|---:|---:|---:|---|
| Unminified, Compilation.None | 10 / 200 | 20 | 702.76 / 758.39 | 1,230.27 / 1,339.39 | `20260926T015355744` |
| R8, Compilation.None | 10 / 200 | 20 | 561.86 / 621.89 | 1,108.45 / 1,223.50 | `20260926T021238458` |
| R8 + required Baseline Profile | 10 / 200 | 20 | 434.62 / 487.45 | 929.81 / 1,047.27 | `20260926T021611663` |
| R8 + required Baseline Profile | 1 / 200 | 20 | 443.01 / 509.94 | 902.82 / 992.07 | `20260926T021917301` |
| R8 + required Baseline Profile | 30 / 200 | 20 | 450.16 / 484.46 | 1,092.12 / 1,227.15 | `20260926T022225152` |
| R8 + required Baseline Profile (short suite) | 10 / 200 | 5 | 419.39 / 508.92 | 870.03 / 1,056.91 | `20260926T020309261` |

Evidence directories are under `.local/optimization/evidence/step03/` and include raw JSON, per-iteration traces, APK identity, source checkpoint, and environment records. The initial baseline's battery was captured early in the run and after it; subsequent runs automatically capture battery and thermal state before and after instrumentation.

## Interaction suite

`20260926T020309261` passed all five journeys with five repetitions each, R8 enabled, and required Baseline Profile compilation. Library and Downloads each perform three flings in each direction per repetition. Tab switching and the actual local-player first-frame readiness checks passed.

| Journey | Sampled frames | Positive frame overrun | Overrun p95 | CPU frame duration p95 |
|---|---:|---:|---:|---:|
| Library scroll | 3,219 | 7.052% | 1.09 ms | 15.35 ms |
| Downloads scroll | 3,140 | 1.847% | -2.15 ms | 10.95 ms |
| Browser and tabs | 47 | 61.702% | 74.53 ms | 46.68 ms |
| Local-player opening | 28 | 67.857% | 114.35 ms | 129.86 ms |

Positive `frameOverrunMs` means the sampled frame exceeded its deadline; a negative percentile means it finished before that deadline. Percentages pool all sampled frames across the five repetitions. Both scroll journeys exceed the proposed <1% target in this run. Browser/player samples cover short opening transitions, not sustained browsing or playback, and their small frame counts must not be interpreted as whole-session jank rates. No tap-to-first-frame duration was collected by these frame metrics.

The separate 30-tab overview/switching test (`20260926T022540103`) passed five repetitions. Its 44 sampled transition frames had 81.818% positive overrun, 98.18 ms overrun p95, and 87.00 ms CPU frame-duration p95. This flags the opening/switching transition for investigation; it does not establish a whole-browser jank rate or input-latency percentile.

The 1,000-download stress test (`20260926T022715642`) passed five repetitions, using 10 tabs and the required profile. Of 3,136 sampled frames, 55 had positive overrun (**1.754%**); overrun p95 was -2.14 ms and CPU frame-duration p95 was 11.61 ms. It also exceeds the proposed <1% deadline-miss target in these conditions. The 200- and 1,000-record cases occurred at different thermal states, so the small percentage difference is not evidence that the larger list is faster.

All eight completed runs have successful result records. The run index with before/after thermal state and final battery level is archived as `.local/optimization/evidence/step03/physical-device-run-index.csv`. Frame sample totals were cross-checked against the benchmark's reported frame counts.

## Excluded attempts

- `20260926T011053250`: rejected before timing because battery was 20%; Macrobenchmark requires at least 25%. No guard was suppressed.
- `20260926T020003563`: intentionally interrupted while running Library scrolling to reduce interaction repetitions from 20 to five. No result from this incomplete suite is used. Startup remains a separate 20-launch measurement.

## Interpretation and remaining work

This phone supplies the first physical baseline, including 20 cold launches for each of the unminified, R8-only, and profile-enabled 1/10/30-tab configurations. The five core journeys and the larger tab/download cases pass their functional assertions. The baseline work can inform subsequent implementation steps, but the scrolling target has not passed.

The low-memory reference-device matrix, representative artwork, dedicated action-to-response latency, and controlled thermal/power comparisons remain open. Frame timing measures rendering deadlines; it does not measure the entire tap-to-play interval. The broader remote-media, download, accessibility, and release-candidate checks remain in their assigned later steps. No app behavior was changed during this device session; only measurement scripts and documentation changed.
