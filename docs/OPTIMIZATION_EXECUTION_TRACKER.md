# Miowser optimization execution tracker

Use with the [implementation guide](G:/Projects/Miowser/docs/OPTIMIZATION_IMPLEMENTATION_GUIDE.md) and [source assessment](G:/Projects/Miowser/docs/SHIP_READINESS_OPTIMIZATION_PLAN.md).

This tracker was created on 2026-09-25. Step 1 is complete: source checkpoint verified, build evidence archived, and the baseline app launched on a disposable emulator. The unit task reused the prior 302-test success; fresh lint reproduced 14 errors, 74 warnings, and 3 hints. Runtime performance remains unmeasured.

Step 2 update: all 14 lint errors and both document-start warnings are resolved. Fresh unit execution passed 302 tests; debug build/lint passed with 0 errors, 72 warnings, and 3 hints. Older-device runtime verification remains open; see the [Step 2 results and warning dispositions](G:/Projects/Miowser/docs/STEP02_LINT_VERIFICATION.md).

Step 3 update: release benchmark harness, isolated datasets, shrinking, and generated startup profiles are implemented and verified. Emulator measurements are recorded, including a successful 20-launch profile-enabled run. They do not establish a speed improvement or physical-device acceptance; see the [Step 3 guide and results](G:/Projects/Miowser/docs/STEP03_BENCHMARKS.md).

2026-09-26 device update: after the initial low-battery rejection, testing resumed at 56% on Samsung SM-G990B2. The 20-launch unminified baseline and five-repetition optimized interaction suite passed. Library and Downloads scrolling exceeded the proposed frame-deadline target under the recorded power-saving/thermal conditions. See [physical-device results](G:/Projects/Miowser/docs/STEP03_PHYSICAL_DEVICE_RESULTS.md); remaining device and performance acceptance is still open.

## Current status — 2026-10-09

The later [dated audit](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md) uses different item numbering. P0 items 1–2, P1 items 3–11, and P2 items 12–16 are implemented and locally verified. The [P0](G:/Projects/Miowser/docs/P0_OPTIMIZATION_VERIFICATION_2026-10-08.md), [P1](G:/Projects/Miowser/docs/P1_OPTIMIZATION_VERIFICATION_2026-10-09.md), and [P2 results and 54-check device list](G:/Projects/Miowser/docs/P2_OPTIMIZATION_VERIFICATION_2026-10-09.md) are the current evidence. Device and emulator checks are now owned by the user. Historical runs below predate these changes and do not establish current performance. Profiles are not regenerated; current release measurements and beta/ship gates remain open.

## Work queue

Status values: Not started / Implementing / Automated checks passed / Awaiting device verification / Complete / Needs correction. “Complete” requires the step's stated evidence, not only compilation.

| Step | Deliverable | Status | Owner / evidence |
|---|---|---|---|
| 1 | Identifiable checkpoint and test environment | Complete | Codex; [baseline record and launch evidence](G:/Projects/Miowser/docs/OPTIMIZATION_BASELINE.md) |
| 2 | Lint blockers fixed | Awaiting device verification | Codex fixes/checks passed; project owner or API 29–34 device: cache eviction and download-output checklist; [results](G:/Projects/Miowser/docs/STEP02_LINT_VERIFICATION.md) |
| 3 | Reproducible release benchmarks | Awaiting device verification | Physical baseline and 1/10/30-tab + 1,000-download matrix recorded on Samsung; all journey checks passed, scrolling target missed; low-memory device and controlled conditions still open; [physical results](G:/Projects/Miowser/docs/STEP03_PHYSICAL_DEVICE_RESULTS.md) |
| 4 | Ordered background persistence | Automated checks passed | Core store migration, 310 tests, zero lint errors, debug/release builds, restart probe and five emulator journeys passed; physical acceptance and subsystem boundaries documented; [details](G:/Projects/Miowser/docs/STEP04_ORDERED_PERSISTENCE.md) |
| 5 | Asynchronous Hub startup | Automated checks passed | Core reads off-main, explicit loading/retry, separate browser readiness, privacy/recovery barriers; 314 unit tests and 11 emulator checks passed; physical performance acceptance pending; [details](G:/Projects/Miowser/docs/STEP05_ASYNC_STARTUP.md) |
| 6 | Lazy tab restoration | Automated checks passed | Engine-free saved records, explicit activation, retained warm pages, async previews, queued-session restore; 319 unit tests and 16 distinct emulator checks passed; physical acceptance pending; [details](G:/Projects/Miowser/docs/STEP06_LAZY_TABS.md) |
| 7 | Renderer recovery | Awaiting device verification | P0 renderer recovery; see current P0/P1/P2 reports |
| 8 | Pause/checkpoint scheduler | Awaiting device verification | P1 pause/checkpoint scheduler; see current P0/P1/P2 reports |
| 9 | Correct service lifetime and notifications | Awaiting device verification | P1 service lifetime and notification sampling; see current P0/P1/P2 reports |
| 10 | Bounded transfer memory/concurrency | Awaiting device verification | P1 bounded transfers; see current P0/P1/P2 reports |
| 11 | Nonblocking artwork | Awaiting device verification | P2 artwork workers, cancellation, and decode bounds; see current P0/P1/P2 reports |
| 12 | Cancellable discovery and efficient filters | Awaiting device verification | P1 discovery cancellation; P2 filter indexing and reload revisions; see current P0/P1/P2 reports |
| 13 | Isolated, sampled UI state | Awaiting device verification | P1 sampled progress/subtitle ownership; P2 session lifecycle and actual constraints; see current P0/P1/P2 reports |
| 14 | Native audio/permissions/navigation/accessibility | Awaiting device verification | P2 media focus/noisy handling and accessible gesture alternatives; live WebView/TalkBack still pending; see current P0/P1/P2 reports |
| 15 | Confirmed duplication removed | Awaiting device verification | P2 unused Downloads screen removed; see current P0/P1/P2 reports |
| 16 | Privacy/backup/hostile-input verification | Awaiting device verification | P0 durable backup/restore; P2 redacted diagnostics; see current P0/P1/P2 reports |
| 17 | Measured and tested release candidate | Awaiting device verification | Debug/release/R8 benchmark builds pass; current device measurements, regenerated profiles, and release acceptance still pending; see current P0/P1/P2 reports |
| 18 | Beta findings closed and ship decision | Not started | Codex: implementation/checks; project owner: device/product acceptance |

## Original Step 1 test environment

| Field | Value |
|---|---|
| Source revision + working-tree patch identity | f5a975ddc21c5913da622483f0229d5ffe5e0159 + checkpoint 20260924T221958188Z; hashes in baseline record |
| Candidate version / artifact hash | 0.1.0 (1), debug correctness baseline; SHA-256 A9559D7D7FBFE4ED1B5AB43CD3709343AE46A16D2E3605EC238CF35E574857E6 |
| Reference device / RAM / Android API | Disposable API 37.1 / 16KB emulator created, effective 4096 MiB per launch log; physical benchmark phone unselected |
| Low-memory device / Android API | Unselected |
| WebView version / refresh rate | com.google.android.webview 149.0.7827.5 / guest display 60 Hz |
| Build variant / R8 / compilation mode / profile state | Debug / unminified / not benchmark-controlled / no app-owned profile |
| Dataset and network fixture identity | empty-v1 fresh disposable install verified at Hub; notification/debug local-network grants; populated fixtures belong to Step 3 |
| Distribution channel | Undecided |
| Promised Android support range | minSdk currently 26; release verification pending |

## Measurements

Targets are proposals from the source assessment. Record baseline before changes and compare like-for-like optimized builds. “Unmeasured” must not be converted to Pass.

| Metric | Baseline | Candidate | Target / decision |
|---|---|---|---|
| Cold populated Hub p95, Samsung reference | 1,339.39 ms; unminified | 1,047.27 ms; R8 + required profile | ≤1.5 s proposed; 20 launches each, power saving/thermal limitations; controlled acceptance pending |
| Cold populated Hub p95, low-memory | Unmeasured | Unmeasured | ≤2.5 s usable |
| Hub/Tabs first visible response | Unmeasured | Unmeasured | ≤100 ms |
| Warm live-tab activation p95 | Unmeasured | Unmeasured | ≤150 ms |
| Local video first frame p95 | Unmeasured | Unmeasured | ≤750 ms |
| Missed frame deadlines, fixed scroll journey | Unmeasured | Library 7.052%; Downloads 1.847% (200 records) | <1% not met in this physical run; investigate app stalls >100 ms |
| Retained memory after 30 cycles, app + renderer separately | Unmeasured | Unmeasured | No monotonic growth; investigate >10% |
| All-paused service/tick after five minutes | Unmeasured | Unmeasured | None solely for paused records |
| Visible download progress publications/s | Unmeasured | Unmeasured | ≤4 per transfer; state changes immediate |
| Two paused downloads + new C | Source risk identified | Unverified | C starts without resuming A/B |

## Per-step result record

### Step 1 result — 2026-09-25

- Owner: Codex (setup and launch smoke check); project owner retains broader device/product acceptance.
- Source: base revision and 206-file verified working-tree snapshot identified above; application files unchanged.
- Added: checkpoint/emulator helpers, ignored local evidence directory, baseline environment guide. Updated this tracker with ownership and status.
- Automated checks: debug APK assembly succeeded; cached 302-test result preserved; fresh lint still fails with the known 14 errors. Both PowerShell helpers parsed successfully and were exercised.
- Device: disposable Medium Phone emulator, Android 17 / runtime API 37, image 37.1 / 16KB, 4 GiB allocation, 60 Hz, WebView 149.0.7827.5.
- Expected/actual: install and open empty Hub. Passed via APK install, resumed MainActivity, and visually inspected screenshot.
- Evidence: `.local/optimization/evidence/step01/`; source checkpoint `.local/optimization/checkpoints/20260924T221958188Z/` (both ignored).
- Limitation: debug emulator correctness check only; no speed claim. UI hierarchy dump failed, so launch evidence uses screenshot/activity state. Physical benchmark devices and distribution channel remain unresolved.
- Next: Step 2 lint fixes. No optimization code, commit, or publication was performed in Step 1.

### Step 2 result — 2026-09-25

- Owner: Codex for source fixes/automated checks; project owner for remaining older-device checks.
- Changed: thumbnail eviction API, three MediaStore API declarations, four scoped Media3 opt-ins, optional hardware declarations, and two WebView feature-guard shapes.
- Checks: fresh 302-test pass; debug build and lint successful; 0 lint errors, 72 warnings, 3 hints; compiled bytecode and merged manifest verified.
- Smoke check: updated APK installed and MainActivity resumed on disposable emulator-5580; settled empty Library screenshot visually verified and archived. This does not establish release performance.
- Remaining warnings: categorized and assigned in STEP02_LINT_VERIFICATION.md; no blanket suppression or lint baseline introduced.
- Evidence: `.local/optimization/evidence/step02/`; APK hash recorded in the Step 2 report.
- Limitation: only API 37-class emulators are available. Below-API-35 eviction and ordinary download-output runtime checks are still pending; minimum SDK and API 26–28 deferral unchanged.
- The user's running emulator has an active download foreground service and was not upgraded or restarted. No commit made.

### Step 3 result — 2026-09-25

- Owner: Codex for implementation and automated verification; project owner for physical-device selection and acceptance.
- Added: separate benchmark app/test module, deterministic populated fixtures, five core journeys, evidence/summary/profile scripts, startup readiness signaling, release shrinking, and generated startup/baseline profiles.
- Checks: fresh 302 unit tests passed; debug and benchmark lint report zero errors. Production release assembled and packaged profiles verified; benchmark Activity/assets absent from production.
- Device: disposable API 37 emulator, 4 GiB allocation, 60 Hz, WebView 149.0.7827.5. All five journeys exercised; shrunk JavaScript bridge success visually checked separately from automated native tab switching.
- Measurements: uninterrupted 20-launch runs for unminified, R8-only, and R8 with required profile compilation. Emulator startup did not improve; detailed medians/p95 and limitations are in the Step 3 report. No physical speed target is marked passed.
- Artifact: unsigned production release 3,695,341 bytes; full SHA-256 in the Step 3 report. Benchmark APK size decreased 78.49% before profile addition.
- Evidence: `.local/optimization/evidence/step03/`; final profile-enabled run `20260925T175911731`; final source checkpoint pointer `completed-step03-checkpoint.json` in that evidence directory.
- Remaining: controlled physical midrange/low-memory runs, 1/10/30-tab and 1,000-download cases, representative artwork, trace-based interaction latency, and later accessibility/remote-media regression gates. Step 2 older-device checks remain pending.
- Status: Awaiting device verification. Step 4 has not started; no commit or publication performed.

### Step 3 physical-device follow-up — 2026-09-26

- Device: Samsung SM-G990B2, Android 16 / API 36, approximately 7.2 GiB reported RAM, WebView 153.0.8010.36, 60 Hz, power saving enabled.
- Completed: eight successful runs. Twenty cold launches per unminified/R8-only/profile configuration, including profile-enabled 1/10/30 tabs; five repetitions of each core journey; separate 30-tab switching and 1,000-download scrolling.
- Startup: 10-tab populated p95 was 1,339.39 ms unminified, 1,223.50 ms R8-only, and 1,047.27 ms R8 + profile. These are observed results, not isolated causal effects of R8/profiles.
- Smoothness: Library deadline misses 7.052%; Downloads 1.847% with 200 records and 1.754% with 1,000. The proposed <1% target is not met. Tab/player opening transitions also warrant investigation.
- Conditions: thermal status rose from 0 to 2, reaching 3 in later stress cases. Controlled-temperature repetition and a low-memory phone remain outstanding, along with representative artwork and dedicated input-latency measurements.
- Changed: evidence runner now records battery/thermal before and after, locale/font scale/power saving; summary reports frame-deadline miss counts and percentages. Scripts parsed, summaries exercised, raw frame totals checked. App source unchanged in this follow-up.
- Evidence: [physical-device report](G:/Projects/Miowser/docs/STEP03_PHYSICAL_DEVICE_RESULTS.md); run index `.local/optimization/evidence/step03/physical-device-run-index.csv`. Interrupted and low-battery attempts retained and excluded.
- Baseline established for subsequent optimization work; overall Step 3 status remains Awaiting device verification. Step 4 not started.

### Step 4 result — 2026-09-26

- Implemented a process-lifetime ordered writer, immutable save snapshots, main-owned core lists, visible pending/failure/Retry feedback, and commit-backed confirmations. Existing codecs and keys retained.
- Migrated Library/folders, History, bookmarks, download metadata, tab sessions, inactive tabs, speed dials, app/Hub/menu preferences, and failed-video log writes. Batched core bulk actions; tab changes save while foregrounded. Download completion acknowledgement and cleanup/dispatch ordering verified by an isolated synthetic transfer.
- Checks: 310 unit tests passed; debug lint 0 errors/49 warnings/3 hints; benchmark lint 0 errors/56 warnings/3 hints; debug/release builds passed. Six device checks passed on the disposable API 37 emulator, including StrictMode-protected edits and force-stop/restart readback.
- Artifact: [Step 4 debug APK](G:/Projects/Miowser/Miowser-0.1.0-step4-debug.apk), 25,042,702 bytes. Hash and full evidence in the [Step 4 report](G:/Projects/Miowser/docs/STEP04_ORDERED_PERSISTENCE.md).
- Limits: no new physical-device speed claim. Startup reads remain Step 5; artwork Step 11; filter metadata/publication Step 12; privacy marker/permission boundaries Step 16. Small per-site/player preference wrappers retain their existing behavior. The report inventories these unchanged synchronous paths explicitly.
- Status: Automated checks passed. Physical transfer/interruption and user acceptance remain open. Step 5 has not started. Existing user edits and earlier APK preserved; no commit made.

### Step 5 result — 2026-09-26

- Settings and core Hub indexes now load off-main before interactive content; explicit Loading/Ready/Error with Retry prevents false empty-state rendering and default-setting writes. Initialization is idempotent across activity recreation.
- Browser-only repositories/session restoration are deferred until Browse. Private cleanup gates browser entry, remote playback, and cookie-dependent transfer dispatch. Hub-only pause no longer risks overwriting saved tabs.
- Download display records publish before off-main recovery inspection. Pending items cannot resume/retry, and late results respect cancel/delete. A neutral checking label avoids showing a premature failure.
- Checks: 314 unit tests passed; debug lint 0 errors/48 warnings/4 hints, benchmark lint 0 errors/61 warnings/4 hints. Debug and optimized release builds passed. Eleven emulator checks covered startup failure/retry/recreation, 30-tab preservation, 100 interrupted recovery records with StrictMode, privacy cleanup, Step 4 durability, and all five app journeys.
- Final debug APK installed and opened on the dedicated emulator; settled Hub visually verified. [Step 5 APK](G:/Projects/Miowser/Miowser-0.1.0-step5-debug.apk); hashes and limits in the [report](G:/Projects/Miowser/docs/STEP05_ASYNC_STARTUP.md).
- Diagnostic timing: one unminified emulator full-display sample 4,285.94 ms. Separate 30-tab browser-entry UI check 3,083 ms including automation overhead. No p95 or causal speed claim; Hub readiness now excludes browser restoration, so old full-display values have a different boundary.
- Status: Automated checks passed. Physical performance, representative remote transfers, and broader device acceptance remain open. Step 6 still needs selected-tab WebView creation. Existing user edits and prior APKs retained; no commit or publication.

Copy this block beneath the table for each implementation step:

```text
Step:
Date / owner:
Source revision and working-tree change identity:
Files changed:
Behavior changed:
Automated checks and result:
Device / API / WebView / build:
Scenario and fixture:
Expected result:
Actual result:
Before / after measurements:
Redacted evidence location:
Regression or unresolved risk:
Correction / rollback action if needed:
Final status:
```

## Release gate

- [ ] Zero unresolved P0 issues.
- [ ] Unit tests and lint pass; remaining warnings reviewed.
- [ ] Optimized release and profile packaging verified.
- [ ] Core browse → find → play/download → offline Library journey passes.
- [ ] Pause/resume, timeout, process death, and replacement races pass.
- [ ] Low storage and output integrity checks pass.
- [ ] Renderer recovery and tab restore pass.
- [ ] Agreed performance targets pass on recorded devices.
- [ ] Audio interruption, permissions, Back, accessibility, and insets pass.
- [ ] Private cleanup, backups, bridge validation, and log redaction pass.
- [ ] Upgrade from the previous supported build preserves data.
- [ ] Supported Android range and any deferred coverage explicitly recorded.
- [ ] Signing, versioning, branding, notices, help/privacy, and channel requirements ready.
- [ ] Candidate artifact hash and beta results recorded.
- [ ] Remaining limitations have an owner and release disposition.
- [ ] Release owner records Ship or No ship with date and reason.

## Open product decisions

| Decision | Default working assumption | Final decision / evidence |
|---|---|---|
| Feature scope | Preserve the existing core journey; defer expansion | |
| Background-tab eviction | No silent eviction of existing live tabs; start with lazy restore | |
| Database migration | Only if measurement/transaction needs justify it | |
| Private profiles | Retain disclosed shared-storage model | |
| API 26–28 runtime coverage | Previously deferred; revisit release claim explicitly | |
| Public distribution | Prepare candidate; publication is a separate action | |

