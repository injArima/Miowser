# Step 5 — Asynchronous Hub startup

Implementation in `G:\Projects\Miowser`, 2026-09-26. This is not a physical-device performance acceptance record.

## Behavior

- Activity composition begins without synchronously restoring Library, folders, or downloads. A stable loading screen precedes the populated Hub. An unsuccessful read displays Retry, rather than an empty Library.
- Settings load before repository consumers become interactive. Library, folder, and download metadata reads/decoding run on the I/O dispatcher; immutable results are published on the main owner. Process-level initialization survives activity recreation and retries incomplete loads without duplicating entries.
- Hub sort and download-section preferences are applied before its first composition.
- History, bookmarks, speed dials, inactive tabs, and session records are deferred until browser entry. Browser readiness is separate from Hub readiness. Browser controls are unavailable while that initialization runs, and failures offer Retry.
- The fully-drawn signal now means the local Hub is populated. It no longer waits for browser initialization. Compare populated Hub and first browser entry separately; the old Step 3 fully-drawn boundary included browser restoration.
- Interrupted-private-session cleanup is a shared barrier for browser entry, remote Library playback, and cookie-dependent download dispatch. Independent local Hub reads and local playback do not need cookies. The interrupted marker is cleared only after cleanup and a successful marker write.
- Downloads display lightweight records first. Only interrupted/paused records require a recovery inspection, which runs off the main thread. They show a checking message and cannot be resumed/retried until verification returns. Cancellation/deletion cannot be undone by a late result. Saving unrelated edits during verification retains the original durable download record.
- Pausing at the Hub before first browser entry does not overwrite saved tabs with an empty list.

## Scope boundaries

Existing storage keys/codecs remain unchanged. This does not introduce a database migration. Browser entry still creates all saved WebViews; selected-tab-only engine creation belongs to Step 6. Filter initialization, WebView startup, thumbnail reads, and the existing security cleanup APIs are not all made asynchronous by this step. Notification cold-start restoration remains Step 9.

Recovery inspection conservatively reuses the existing recovery-plan and completed-unit checks; it is not a replacement transfer-validation protocol. No transfer is automatically resumed on startup. Existing live-tab behavior is preserved after browser initialization.

## Reproduce and accept

1. Use Android Studio's bundled JDK and the dedicated disposable emulator. Do not clear the normal app or real browsing data.
2. Run debug unit tests, debug/benchmark lint, debug/release assembly, and benchmark instrumentation classes `StartupTest`, `PersistenceDurabilityTest`, and `AppBenchmarks`.
3. Check empty startup and 500 Library / 2,000 History / 1,000 downloads / 30 tabs. Verify the correct sort, no false empty Library, browser entry, Back to Hub, and preserved tabs after a Hub-only pause/restart.
4. Run the benchmark-only malformed-preference probe. Verify Error, repair the synthetic preference, Retry, and recreate the activity without duplicate records or lost tabs.
5. On the reference physical device, record populated Hub latency and first browser entry separately under the same compilation, WebView, power, and thermal conditions. Repeat at least 20 cold launches. The deferred WebView cost must remain visible in the report.
6. Exercise an interrupted private session followed by local playback, remote playback, and resume. Confirm cookies are cleared before remote actions. Check recovery inspection with valid/missing artifacts and cancel/delete during inspection.

Physical timing and user acceptance remain open until these device checks are recorded. A successful build or emulator smoke test alone does not close Step 5's performance gate.

## Automated evidence

- 314 unit tests passed, with no failures, errors, or skipped tests. Four new tests cover concurrent initialization, failed reads, cancellation, and a successful empty snapshot.
- Debug lint: zero errors, 48 warnings, 4 hints. Benchmark lint: zero errors, 61 warnings, 4 hints. No lint baseline or blanket suppression added. The first combined build encountered an internal lint service error; a subsequent build against stable sources passed.
- Debug and optimized release assembly passed. A final rebuild includes the neutral recovery-check label; that two-line presentation change followed the 11-check device run.
- Eleven tests passed on the dedicated API 37 emulator: five startup checks, the Step 4 durability/restart check, and all five existing app journeys. The normal phone app was not cleared or modified.
- The storage-error probe repaired a deliberately mistyped preference, retried successfully, and recreated the activity. Its 500 Library records and 30 saved tabs survived; History remained deferred. The stress launch used 1,000 downloads.
- The recovery probe restored 100 interrupted records with a main-thread StrictMode policy that terminates on disk reads/writes. It verified conservative checking status, a valid synthetic recovery plan, missing plans, repeated restore, and cancellation/deletion before late verification returned.
- The privacy probe loaded the local Hub while an interrupted-session marker was still present, then awaited two concurrent privacy-barrier callers. It confirmed the synthetic private cookie and the marker were cleared before both calls completed.
- The 30-tab test paused and force-stopped at the Hub before opening the browser, then verified all 30 tabs still opened. Empty startup and browser entry also passed.

Raw evidence is in `.local/optimization/evidence/step05/` (ignored). `device-outputs` retains the complete device results and traces. `device-checks.log` records the 11-test pass; `artifact-build.log` records final artifact verification.

## Timing interpretation

The unminified emulator smoke run used `CompilationMode.None` and one Macrobenchmark iteration: initial display 2,073.43 ms; populated Hub full display 4,285.94 ms. A separate 30-tab UI test observed 3,669 ms from shell launch through populated-content detection and 3,083 ms from Browse tap through native tab-control detection. These latter values include automation/idle-wait overhead.

These are diagnostics, not p95 estimates, target acceptance, or controlled before/after results. The earlier Step 4 emulator smoke full-display sample was 11,938.13 ms during a shared-host build, and its readiness boundary also included browser restoration. Neither a percentage speed claim nor comparison with the Samsung optimized baseline is justified. The current emulator run does not meet the proposed physical-device budget; actual device acceptance remains open. First browser entry still warrants Step 6's selected-engine creation work.

The existing generated Baseline Profile remains packaged; it has not been regenerated for this changed readiness boundary. Refresh and remeasure profiles in the controlled performance/release phase.

## Test APK and source

[Step 5 debug APK](G:/Projects/Miowser/Miowser-0.1.0-step5-debug.apk), 25,372,695 bytes, normal `com.miowser.app` debug signature.

SHA-256: `041301D1C5E27BB1AF3B50234B6EACCD987970D68D38DE50072830FC3BEBA013`.

The final APK installed and opened successfully on the dedicated emulator; its settled Hub was visually checked. Earlier APKs remain unchanged. Normal debug/release manifests exclude all benchmark fixture/probe activities, and both APKs exclude the fixture media asset. The optimized release is unsigned (3,711,725 bytes), so use the debug APK for direct testing.

Source checkpoint before Step 5: `.local/optimization/checkpoints/20260925T215121241Z/`. Final checkpoint pointer: `.local/optimization/evidence/step05/source-checkpoint.json`. Artifact hashes and test counts: `verification.json` in the same evidence directory. No commit, push, or publication was performed.

Status: **Automated checks passed; physical performance/device acceptance pending.** Step 6 remains unimplemented.
