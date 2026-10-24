# Step 6 — Saved tabs without eager WebViews

Implemented in `G:\Projects\Miowser`. Automated checks passed; physical-device acceptance remains pending. Step 7 renderer-loss recovery is not part of this change.

## What changed

`Tab` is now an engine-free record at construction: identity, URL, title, opener ID, private flag, thumbnail key, and activity time remain available without a WebView. `TabController` owns the record list and a separate registry of live WebViews. Merely reading `liveWebView`, saving a session, applying preferences, closing an unopened record, or pausing the activity cannot create an engine.

The Hub restores saved records off-main and publishes them without loading pages. Browser-only initialization still awaits Step 5's privacy/settings prerequisites. Activating a remote tab creates its engine on the main thread. Explicit navigation and popup transport are the other creation paths. Settings, clients, permissions, JavaScript bridges, and document-start scripts are installed before the first URL load.

Only acquired engines have Compose AndroidView hosts. Switching tabs keeps existing engines and their DOM state. Host release detaches views; controller close/disposal owns destruction. There is no new background-tab eviction policy. Opening a link in a background tab creates its record and loads it when selected.

Fresh native Home tabs need no hidden WebView. Returning to Home in an already-live tab preserves the existing navigation behavior. Lifecycle, filter refresh, media pause, and browsing-data cleanup now operate on live engines only. Security cleanup may still create its existing temporary WebView when Android's cache-clearing API requires one; that is not a retained tab engine.

Preview decoding runs on the I/O dispatcher when an overview card needs it. Closed/deleted tabs and newer captures invalidate stale results. Preview deletion uses the cache worker. The existing bounded preview memory cache remains in place.

The session codec and preference keys are unchanged. Private records remain excluded. Opener IDs are resolved against the entire restored record set before any engine exists, including an opener appearing later in the saved order. Invalid/self opener references are ignored.

Session saves publish an immutable in-process snapshot before joining the ordered disk writer. Activity recreation sees that latest submission, including an empty session, instead of stale disk contents. This is not a durability guarantee before commit; the existing pending/failure feedback and retry still apply.

## Verification guide

1. Use the disposable benchmark application and emulator; do not clear the normal phone app.
2. Run debug unit tests, debug/benchmark lint, and debug/release builds. Existing two-, three-, and five-field session codec tests must still pass.
3. Run `com.miowser.benchmark.LazyTabTest`. Its benchmark-only probe renders the real BrowserScreen with an inspectable controller. Restore 30 records at the Hub and verify zero tab engines; enter Browse and verify one; open another record and verify two.
4. Set a JavaScript/DOM sentinel in the first page, switch away and back, and verify the original engine object and sentinel remain. Close an unopened card without increasing the engine count.
5. Start on a restored child with a dormant parent. Close the child from Tabs, verify its engine is released, then return to the parent and verify only its engine is created.
6. Open native Home with zero engines, navigate, and verify exactly one engine. Start a private tab and verify private history/session exclusion.
   The queued-session regression blocks the ordered writer, submits a changed session and then an empty one, and checks restoration sees both before disk commit.
7. Rerun `StartupTest`, `PersistenceDurabilityTest`, and the five `AppBenchmarks` journeys. Record first browser entry separately from populated Hub readiness.
8. On the reference physical device, repeat the optimized 1/10/30-tab matrix with the same compilation, WebView, battery, and thermal conditions. Check warm page scroll/forms, popup navigation, permissions, Back/Forward, and 30 close/open cycles. Do not interpret a single emulator timing as a p95 or shipping performance acceptance.

Evidence is retained under `.local/optimization/evidence/step06/` (ignored). No dependency upgrade or database migration is included.

## Build results — 2026-09-26

- 319 unit tests passed, with no failures, errors, or skips.
- Debug lint: 0 errors, 48 warnings, 5 hints. Benchmark lint: 0 errors, 62 warnings, 5 hints.
- Debug and release builds passed. Normal merged manifests contain no benchmark probe activities; normal APKs contain no fixture video. Release baseline `.prof` and `.profm` assets are present. Profiles were not regenerated for this step.
- Test APK: `G:\Projects\Miowser\Miowser-0.1.0-step6-debug.apk`, 25,383,568 bytes. SHA-256: `4703C57062080ECDA852B573094BD7EC672AB9F9238905D600DCAD36DA0B9F25`.
- The initial lazy-tab test run could not read the probe label behind the status bar. The benchmark-only overlay was corrected to respect status-bar insets; original failures are retained in the evidence.
- All 16 distinct emulator checks passed: five corrected `LazyTabTest` cases, five `StartupTest` cases, one persistence durability test, and five existing benchmark journeys. The initial run passed the 11 existing checks; the corrected run passed all five lazy-tab checks. No production change was needed for the test-overlay failure.
- Verified 30 restored records with zero retained engines at Hub, one on Browse, two after selecting another remote record, and the original WebView identity plus JavaScript/DOM sentinel after switching back. Closing a dormant record did not create an engine; closing the active child released it and allowed its dormant opener to activate. Native Home, private history/session exclusion, and changed/empty queued-session restoration passed.
- Standard 30-tab startup probe: Hub shell/UI wait 2,816 ms; first browser UI wait 2,201 ms. The separate controller probe recorded 2,115 ms. These are single emulator samples with different boundaries and probe overhead, not p95 measurements or physical-device speedup evidence.
- The normal debug APK installed and launched successfully on the dedicated API 37.1 emulator; the Library screen was visually checked. No physical device was connected. Existing Step 4 and Step 5 APKs were preserved.

Physical-device performance, low-memory coverage, real-site scroll/form/popup behavior, and the controlled 1/10/30-tab comparison remain pending. Same-process switching preserves warm engines; process death still restores URLs and metadata, not DOM/form/scroll state or WebView back stacks.
