# Miowser optimization and ship-readiness plan

**2026-10-09 status:** This earlier document retains historical requirements/results. See the [current dated audit](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md) and [P2 implementation, local results, updated fixtures, and device checklist](G:/Projects/Miowser/docs/P2_OPTIMIZATION_VERIFICATION_2026-10-09.md). P0/P1/P2 source work is locally verified; current device acceptance and profile regeneration remain with the user.

Review date: 2026-09-25. Scope: the current working tree, including uncommitted UI changes.

Execution companion: [step-by-step implementation guide](G:/Projects/Miowser/docs/OPTIMIZATION_IMPLEMENTATION_GUIDE.md) and [progress/verification tracker](G:/Projects/Miowser/docs/OPTIMIZATION_EXECUTION_TRACKER.md).

## Recommendation

Freeze major feature expansion and concentrate the next release on one complete journey: **browse → find media → play or download → return to it in the Library**. Miowser already has enough functionality for a useful first release. Its largest opportunities are reducing launch work, controlling WebView and download resource use, and making existing actions predictable.

Keep Kotlin, Compose, WebView, and Media3. A framework rewrite, a new visual redesign, or a broad module split would add release risk without addressing the concrete problems below. Preserve the requested expanding Hub navigation, All/Unfiled/custom Library folders, and the player's separate visual theme.

This is a source-based engineering assessment, not a measured performance report. The review inventoried 118 production Kotlin files and 51 Kotlin test files, traced the main browsing/playback/download flows, examined persistence, permissions, background services, UI components, build configuration, and existing status/checklist documents. No current-device frame, startup, battery, or memory measurements were collected. The existing September 17 CPU trace predates the current UI; it cannot establish current performance. Visual and accessibility acceptance still need device testing.

## What is already worth keeping

- Native Compose surfaces, shared Material colors/shapes, dynamic color, lazy grids/lists, and stable item keys already provide a good foundation.
- Tab previews have disk/count limits; Library artwork has a byte-sized memory cache and loads off the UI thread. Extend these protections rather than replacing them wholesale.
- Ad-block lists build in the background and publish a complete ruleset; domain indexing and fallback protection already exist.
- Media probing has bounded samples and navigation-generation checks. Recursive discovery has depth, page, candidate, and time limits.
- Download recovery distinguishes verified progress from partial work; filename resolution, conflict handling, MediaStore output, and file-type routing already exist.
- Player error/rebuffer feedback, decoder fallback, lifecycle pause, track selection, and reduced polling are implemented. The player already polls at 500 ms while playing and 1,000 ms while idle; a plan should not propose those as missing features.
- Private tabs are excluded from ordinary restore/history and automatic Library writes. The deliberately chosen shared-storage private mode remains a product constraint, not an isolated browser profile.

## Priority and measurement rules

**P0** means resolve before a public release candidate. **P1** means complete during polish, or document a measured, limited deferral. **P2** means post-launch unless evidence elevates it. Effort estimates below assume one Android engineer: S = 1–2 engineering days, M = 3–5, L = 1–2 weeks. They exclude device feedback and store review and are not commitments.

Capture a baseline first, then compare the same devices, data, WebView version, network fixture, and release configuration. Initial targets below are proposed acceptance budgets, not claims about current behavior or Android's official thresholds.

| Journey / metric | Proposed acceptance budget | Measurement |
|---|---|---|
| Cold launch into a populated Hub | p95 usable Hub within 1.5 s on the reference midrange phone; within 2.5 s on the low-memory phone | At least 20 cold launches; record initial display and fully usable content separately |
| Hub section changes and opening Tabs | Visible response within 100 ms; no wait for disk, network, or thumbnail completion | Input-to-first-response trace and video |
| Library / Downloads / Tabs scrolling | Under 1% deadline-missed app frames in the fixed scenario; investigate every app-owned stall over 100 ms | Release Macrobenchmark frame timing and Perfetto; evaluate 60 and 120 Hz separately |
| Warm tab activation | p95 visible response within 150 ms for an already-live tab | Fixed lightweight pages; report reloads separately |
| Local video startup | p95 first video frame within 750 ms on the reference phone | Fixed local samples; remote startup reported separately under controlled network conditions |
| Download UI | At most 4 progress publications/s per visible transfer; state changes immediate | Trace while scrolling and downloading concurrently |
| Idle / all downloads paused | No ongoing foreground service solely for paused records; no periodic download notification rebuilds | Background five minutes; inspect service, CPU, threads, and network |
| Memory | No monotonic retained growth across 30 open/close/player cycles; record app and WebView renderer memory separately | Repeat after settling; investigate >10% retained growth before accepting |
| Reliability | No crashes, ANRs, corrupted completed files, or lost acknowledged user edits in the defined release matrix | Device scenarios plus focused integration tests |

Include fresh install, 500 Library entries (the current cap), 2,000 History entries (the current cap), 200 download records, and 1/10/30 tabs. Stress download history separately at 1,000 records because it lacks an evident retention cap. Use deterministic local/test-host content for comparisons and public websites only for compatibility checks. Never use arbitrary website load time as an app-only speed metric.

Add a small Macrobenchmark/Baseline Profile setup covering launch, Hub scrolling, browser entry, Tabs, and player opening. Baseline Profiles can improve compiled app paths from installation; they do not remove network latency or WebView initialization work. [Android Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview), [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics).

## Engineering work, in order

### 1. Remove launch-time work from the first frame — P0, M

**Evidence:** `MainActivity.kt:74` onward restores Library, folders, and downloads before `setContent`. `BrowserScreen.kt:542` loads more stores and restores every saved tab. `Tab.kt` immediately loads its preview, constructs a WebView, installs bridges, and calls `loadUrl`. Recovery restoration also reads per-download plans and completed-item metadata.

**Change:** display the Hub shell promptly and restore its minimum data asynchronously through an explicit Loading/Ready/Error state. Do not briefly claim the Library is empty while it is loading. Decode snapshots on an I/O dispatcher and publish UI state on one owner. Restore tab metadata without constructing/loading every saved WebView. Create the selected browser tab when the browser is entered, with optional measured idle prewarming afterward. Load History, bookmarks, speed dials, and filter details when needed; retain fallback protection before any browser request.

Keep interrupted-private-session cleanup ahead of WebView use or authenticated work. Defer expensive recovery inspection until Downloads is opened or the user resumes a transfer. Preserve settings ordering and idempotent restoration.

**Acceptance:** Hub launch with 30 restored tab records issues no page loads solely because those records exist; no false empty-state flash; first browser entry remains within an explicitly measured budget. Compare startup with empty and populated recovery directories.

### 2. Make persistence asynchronous without weakening durability — P0, M–L

**Evidence:** History, bookmarks, settings, speed dials, tab sessions, Hub preferences, and downloads use synchronous `SharedPreferences.commit()`. UI actions and activity pause reach these stores. Download persistence rewrites the full list; bulk clearing calls delete/persist for every row. Library already uses `apply()`, but serializes its full index before that call.

**Change:** introduce repository-owned, ordered writes from immutable snapshots. Keep small preferences lightweight; use DataStore or an equivalent serialized writer. Consider Room for growing download/history indexes if the baseline shows whole-index serialization is material. A database migration is not a prerequisite for moving I/O off the UI thread.

Separate frequently changing progress from durable transfer state. Coalesce redundant progress writes, persist meaningful transitions, batch clear/remove/move actions into one transaction, and surface storage errors. Maintain explicit flush/acknowledgment boundaries for privacy markers, user edits, and recovery metadata. Do not mechanically replace every `commit()` with `apply()` and assume process-death safety is solved.

**Acceptance:** StrictMode finds no app-owned blocking store writes during typing, settings changes, page completion, navigation, or backgrounding. Process-kill tests retain acknowledged edits and valid recoverable transfers. Existing codec migrations remain readable.

### 3. Fix paused-download scheduling and service lifetime — P0, L

**Evidence:** `DownloadQueue.kt:19` has two workers. `Download.awaitIfPaused()` blocks a worker; HLS/DASH child workers can also wait while paused. Two paused jobs can therefore occupy the dispatch capacity while a new job waits. `DownloadForegroundService.kt:192` treats PAUSED as active and rebuilds notifications every second. `DownloadQueue.restore()` starts the service for restored paused items. No service timeout handler is implemented.

**Change:** distinguish queued, running, paused, and recoverable records in an explicit scheduler. Pausing should checkpoint and release scarce execution slots and, where supported, connections. Use the existing recovery model to resume. Page-bound Blob transfers need an explicit special policy because they cannot be reconstructed like network transfers. Avoid queuing handoff waiters on the same exhausted worker pool.

Stop the foreground service once there is no executing transfer/mux. Keep resumable records and, if useful, ordinary paused notifications. Update notifications only when visible content changes. Handle service-start failures and timeout by safely checkpointing/stopping. Restore queue state before servicing notification actions after process death; notification taps should open the relevant Downloads item rather than the default Library.

Android imposes a background time budget on relevant `dataSync` services and invokes `onTimeout`; a paused service must not silently consume that budget. [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

**Acceptance:** pause A and B, enqueue C, and verify C starts; test resume/cancel from both app and notification after process death. All-paused work uses no ongoing service or recurring tick. Force timeout and confirm a recoverable result without crash or damaged output.

### 4. Bound download memory and total concurrency — P0, M–L

**Evidence:** `PausableIo.kt:9` accumulates input into an unlimited byte array. HLS uses it for segments, manifests, and keys, and decryption creates further data in memory. DASH also reads manifest text through this helper. Separate pools exist for queue dispatch, direct ranges, HLS segments, DASH segments, probes, discovery, and thumbnails. Fixed thread counts do not bound queued work or combined network activity.

**Change:** stream segment bytes into private temporary files and stream AES decryption where practical. Enforce strict limits for manifest/key/index bodies and reject oversized or malformed responses early. Limit outstanding segment tasks with a sliding work window. Establish global and per-host connection limits across transfers, with playback and the visible page prioritized over probes/artwork. Keep the 1/2/4/8 setting as an advanced preference beneath the global cap; more connections are not automatically faster.

Retain verified segment completion and source-validator checks. Reduce redundant full digest passes only after mapping their correctness boundaries; never trade recovery integrity for a better benchmark. Check available storage through download and mux stages, where private intermediates and final output can coexist.

**Acceptance:** oversized responses fail safely; high-bitrate HLS does not allocate memory proportional to segment size; two downloads plus playback remain responsive. Test no-Range servers, corrupt segments, low storage, cancellation during transfer/finalization, and long audio/video sync after muxing.

### 5. Harden WebView lifetime and renderer recovery — P0 for recovery, P1 for eviction, M–L

**Evidence:** `BrowserScreen.kt:1040` keeps every tab's AndroidView composed. Each tab owns a live WebView, even when invisible. Fourteen-day inactive archiving does not bound a heavy same-day browsing session. `MiowserWebViewClient.kt` has network/TLS handling but no `onRenderProcessGone` override.

**Change:** centralize ownership in a tab/session controller, separating saved tab records from live renderers. Preserve instant switches between live tabs. Handle renderer termination by detaching/destroying affected views, invalidating page-bound jobs, and offering reload while preserving metadata. Consider renderer sharing when recovering multiple affected tabs.

Add a measured memory-pressure policy and byte-based preview limits. Background-tab eviction changes the existing requirement to keep tabs alive: treat it as a deliberate policy choice, not a silent optimization. Protect the active tab, active transfers, unsaved form interactions, and private content. Lazy restoration alone can ship first. If later allowing eviction, explain reload behavior and test restoration; a URL/title snapshot cannot reproduce arbitrary page form state.

**Acceptance:** forced renderer termination leaves the app usable; closing tabs releases views and callbacks; memory stabilizes in the 30-cycle test. Background tabs do not continue unnecessary probes or app-managed captures. [WebView memory](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory), [Renderer termination](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination).

### 6. Stop thumbnail and discovery work from competing with interaction — P1, M

**Evidence:** opening Tabs waits on `captureThumbnail(onComplete = openOverview)` in `BrowserScreen.kt:1005`. `Tab.kt` also captures during scrolling at roughly 1.2-second intervals plus a delayed capture. Preview files decode synchronously in the tab constructor. Remote direct-media midpoint artwork invokes a separate `MediaMetadataRetriever`; the extractor uses full-size `getFrameAtTime` before resizing. Speed-dial favicons use an unbounded map and unrestricted bitmap decode.

**Change:** open Tabs immediately with cached artwork and replace its image asynchronously. Capture after scrolling settles or at meaningful visibility boundaries; coalesce to the latest request and allow only one in-flight capture per tab. Decode previews off the main thread. Use dimension/byte-limited favicon loading, a bounded cache, negative caching, and request deduplication. Prefer scaled extraction for local video and the already-decoded playback frame for remote artwork; delay optional midpoint work until playback is stable.

**Acceptance:** Tabs never waits for PixelCopy; rapid scrolling does not build an encoding backlog; first playback is unaffected by thumbnail extraction; long sessions and repeated failed favicon requests stay bounded.

### 7. Give media discovery a cancellable work budget — P1, M

**Evidence:** `MediaProbe.probe()` submits to a four-thread executor with no cancellation handle. Generation checks in `Tab.kt:295` suppress stale results but do not stop their network work. A successful page load also launches a separate static HTML fetch. The DOM observer can attach repeated listeners when media nodes are reinserted and retains a growing reported-key set.

**Change:** cancellable jobs scoped to tab and navigation; bounded pending candidates and per-origin limits; cancel queued/active probes after navigation or closure. Split quick source verification from optional duration/size enrichment. Prioritize the visible tab and user-selected media. Limit or postpone automatic HTML refetching; reuse already-available data when possible without intercepting/replacing ordinary page responses. Guard watched elements with a WeakSet, batch DOM work, and cap reporting state. Retain exact-origin limits on user-triggered deeper scans.

**Acceptance:** navigating away stops stale probe traffic; DOM-heavy pages cannot create unbounded work; ordinary playback starts without waiting for size estimates. Compare page CPU and network requests with discovery enabled and disabled.

### 8. Reduce unnecessary Compose work — P1, M

**Evidence:** `BrowserScreen.kt` is 1,973 lines and coordinates navigation, permissions, tab state, dialogs, transfers, and playback. `PlayerScreen.kt` is 1,667 lines; `HubHomePage.kt` is 1,082. Direct transfer loops publish observable byte/chunk progress on every buffer. Library filtering/sorting occurs in the Library composable.

**Change:** extract state ownership and small rendering boundaries around browser chrome, tab hosting, Hub lists, download rows, permission coordination, and player controls. Publish sampled immutable download UI snapshots while retaining accurate internal counters. Observe progress at the row/progress component rather than the whole screen. Cache derived sorted/filtered views against real data revisions, preserve lazy keys/content types, and save list/query/folder position across navigation. Use lifecycle-aware collection when introducing flows.

Large files are a maintenance concern, not proof of jank. Use recomposition/compiler reports and traces to decide which boundaries matter. Do not add `@Stable` to mutable types simply to suppress recomposition, and do not introduce dependency-injection or multi-module infrastructure solely for file organization.

**Acceptance:** scrolling Library while downloading produces no unrelated root-screen recompositions attributable to byte updates; typing/search and gestures meet the frame budget; state survives foreground/background and navigation transitions.

### 9. Remove per-request ad-block allocations — P1, S–M

**Evidence:** `FilterEngine.kt:35` builds a candidate list and sorts it for every intercepted request. Engine and WebView client both log blocked URLs. Cosmetic CSS is reconstructed by hostname.

**Change:** prepartition manual/subscription rules when compiling; iterate indexed buckets without per-request sorting while preserving exception precedence. Profile the generic-rule bucket before adding a more complex index. Cache cosmetic CSS per hostname and ruleset generation with a size limit. Gate verbose per-request and page-console logging outside ordinary release use and redact source URLs from diagnostics.

**Acceptance:** rule decisions match the existing corpus, including exceptions and domain scope; representative request classification has lower allocations/time; refreshed rules invalidate caches correctly. Do not remove ad blocking to improve a synthetic speed score.

### 10. Complete native interaction and playback behavior — P0 for audio/permissions, P1 for polish, M

**Evidence:** player lifecycle and track controls exist, but player construction has no explicit audio-focus or becoming-noisy handling. `MainActivity` requests notification/legacy storage permissions at launch. Back uses a shared resolver but no app-owned predictive-back progress implementation. Hub More currently contains only a default Downloads-section preference, while browser settings are reached separately. Overflow forces at least five columns and fixed tile heights.

**Change:** configure Media3 audio focus and headphone/Bluetooth disconnection behavior; verify calls, other media apps, and foreground return. Keep background playback out of scope unless explicitly chosen. [Android audio focus](https://developer.android.com/media/optimize/audio-focus), [Media3 player setup](https://developer.android.com/media/implement/playback-app).

Request notification and pre-Q storage access at the first relevant download action, with a usable denial path. Gate the local-network development permission in code as well as the debug manifest. Preserve origin-specific camera/mic/location prompts.

Keep the current Hub rail, but make More a useful settings/help/privacy destination, linking to grouped browser, download, and Library preferences. The underlying stores can remain separate. Give long-press-only actions an explicit menu alternative. Present failed/canceled transfers as actionable states within the current Downloads structure; avoid adding another top-level destination.

Implement predictive-back behavior where appropriate through the existing back policy; verify gesture cancellation as well as completion. Ensure dismissing a sheet, closing a player, navigating webpage history, returning to Hub, and exiting follow one predictable order. Adapt the overflow layout to available width/font scale rather than always forcing five columns. Preserve the existing SwipeRefreshLayout/WebView integration unless an alternative proves nested scrolling works.

Support 48dp touch targets, TalkBack roles/state/actions, 200% text, RTL, landscape/split-screen, keyboard focus, gesture/three-button insets, and reduced/disabled animation. Keep standard short state transitions; avoid blur/shader work on scrolling cards. Decorative null image descriptions are valid when surrounding controls already provide accessible meaning.

**Acceptance:** no launch permission barrage; no audio leaking to speakers after headphone disconnect; essential actions work without discovering hidden gestures; the font/inset/Back matrix passes.

### 11. Close release and trust gaps — P0, M plus device testing

**Evidence:** release minification is disabled, app-specific ProGuard rules are empty, version is 0.1.0/code 1, and no app-owned benchmark or instrumented-test sources were found. The manifest enables backup without explicit data-extraction rules, declares APK installation permission, and only exposes a launcher intent filter. Existing documents contain both pending-runtime notes and conflicting older step statuses.

**Change:** first close the 14 lint errors triaged below; do not suppress the entire report to make the build green. Enable and test optimized release code/resource shrinking with the current build tooling; verify JavaScript bridges and reflection-sensitive paths after shrinking. Generate app Baseline/Startup Profiles against the final journeys. Audit dependencies—especially the pinned Media3 1.4.1—against official compatibility/release notes, then upgrade in a separate validated change if justified. Do not combine a broad dependency upgrade with the UI/persistence refactor. [Android app optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

Define backup exclusions for browsing/site data, private-session markers, signed URLs, permissions, recovery payloads, and derived artwork as appropriate; test device restore. Review bridge input size/rate limits and frame/navigation identity. Bridges are exposed to arbitrary web content: the existing tokenized Blob transfer is useful, but does not replace adversarial bridge tests. Do not claim an exploit was demonstrated by this review. [Android native-bridge guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).

Make the chosen private-mode limitations clear at entry and in help: it clears shared site data and can log normal tabs out; downloads persist. Check explicit failed-video logs, artwork, settings, and crash recovery against that promise. Treat isolated profiles as later work unless product requirements change.

Add a minimal incoming HTTP/HTTPS link or shared-URL entry path if Miowser is shipped as a browser/downloader, including `onNewIntent` handling and malformed-input tests. A full default-browser claim is a separate product decision. Make notification deep links work regardless. Keep APK opening through Android's installer, but confirm its permission/distribution requirements for the chosen release channel rather than assuming store approval.

Prepare final icon/branding, release signing outside the repository, versioning, release notes, privacy/help text, dependency/filter-list notices, and a reproducible release checklist. Verify current distribution requirements at packaging time. Move development captures/traces into an intentional ignored location without deleting useful evidence. Reconcile status documents into one current release matrix.

**Acceptance:** optimized release installs/upgrades successfully; core features work under shrinking; no sensitive URLs/cookies in normal logs; backup/private cleanup and process-death tests pass; actual distribution artifacts are tested, not only debug builds.

## Feature decisions for version 1

These are recommendations for scope and presentation, not instructions to delete existing user-requested work. “Advanced” and “defer expansion” do not mean redundant.

| Feature | Decision | Reason / release condition |
|---|---|---|
| Hub Library with resume, search, sort, artwork | Essential | Makes discovered/downloaded media useful after the first session |
| Library folders, All and Unfiled | Keep | Explicit current design; preserve organization and existing records |
| Persistent browsing, URL/search, back/forward, tabs | Essential | Core discovery journey; improve restoration cost |
| Basic bookmarks and History | Essential | Bookmarks are intentional saves; History is automatic recall, so neither duplicates the other |
| Ad blocking, safe navigation, origin permissions, clear data | Essential | Browser reliability and trust |
| Detected Media with play/download | Essential | Main product differentiator; useful before every metadata field is known |
| Direct, HLS, and supported DASH downloads | Essential | Keep verified support and clear boundaries; stabilize memory/recovery |
| Pause/resume/cancel/retry, process-death recovery | Essential | Data preservation and predictable long downloads |
| Filename resolution, duplicate handling, file opening | Essential | Users must find and use the correct output |
| Local and remote playback, seek, fit, captions/audio/quality | Essential | Complete consumption workflow; retain working track controls |
| Audio focus, error recovery, accessible controls | Essential hardening | Platform behavior is part of feeling native |
| Download notifications and opening the relevant item | Essential | Background work must remain understandable and controllable |
| Incoming links/shared URLs | Small release integration gap | Lets other Android apps hand a URL to Miowser |
| Private browsing | Keep with explicit limitation | Already chosen shared-storage model; do not market profile isolation |
| Thumbnail tab overview | Keep | Useful recognition; capture must not delay navigation |
| Inactive-tab archive | Keep secondary | Helps long-term organization; does not solve same-day memory pressure |
| Speed dials and their folders | Keep; freeze expansion | Quick destinations, distinct from the full bookmark collection |
| Library automatic domain folders | Keep optional | Useful organization; no further automation required for launch |
| Deeper site scan, depth/page controls | Advanced | Helpful when direct discovery fails; keep user-initiated and bounded |
| Minimum-duration filter | Advanced | Can hide valid unknown-duration media; keep default off and explain exclusions |
| Filter subscriptions/manual rules/import | Advanced | Valuable troubleshooting/customization, unnecessary in the primary journey |
| Multi-connection count and per-chunk progress | Advanced | Keep capability; show aggregate progress normally and details on demand |
| Failed-video log | Secondary troubleshooting | Keep recovery context, but prefer immediate retry/source-page actions; offer clear/delete and review privacy |
| Brightness/volume/double-tap/rotation-lock gestures | Keep optional | Useful shortcuts; essential controls need visible equivalents |
| APK icons/install routing | Secondary | Useful generic download handling; no package-manager expansion |
| Reorderable browser overflow menu | Freeze; hide edit emphasis if needed | Existing customization has low launch value; removal gives little speed benefit unless measured |
| Generic MSE/SourceBuffer reconstruction | Defer | Large compatibility and resource surface; existing documented unsupported state is preferable to rushed support |
| Watch-through cache and promoting cached playback to download | Defer | Requires cache ownership, eviction, auth, and recovery correctness |
| Automatic cross-source grouping/switching | Defer | Same-page URLs are not reliable proof of the same media |
| Blur/shaders/animated card decoration | Defer | No core utility; evaluate only after frame and battery targets pass |
| Full isolated private profiles, PiP/background playback, sync/accounts | Outside this release | Separate products of work; not prerequisites for the current foreground journey |

### Actual redundancy or avoidable duplication

1. **Legacy `DownloadsPage.kt`:** source search found its declaration but no call site. Browser Downloads already routes into the Hub. Remove the unused screen after confirming references/build output; retain the shared active download rows in `HubPage.kt`. This is code cleanup, not removal of Downloads.
2. **Fragmented settings entry points:** Hub More uses a whole destination for one preference while browser settings live elsewhere. Consolidate discoverability through More, preserving the explicit three-item Hub rail and existing preference storage.
3. **Repeated download state/progress presentation:** status grouping and labels appear across the legacy page, Hub, and notifications. Create one presentation model so “paused,” “failed,” and “active” cannot disagree. Context-specific layouts should remain.
4. **Repeated full-index writes during bulk actions:** clear/remove once and persist once. This is redundant work with a direct user-visible cost.
5. **Repeated captures/probes/logging:** coalesce equivalent work and drop stale work. These are more valuable performance removals than deleting useful product features.
6. **Overlapping status/backlog documents:** retain historical checklists as evidence, but link them from one authoritative release matrix; correct stale “pending” and “complete” claims.

Do not merge Library into Downloads: Library records playback and remote sources, while Downloads represents transfer/file ownership. Do not remove browser Home because Hub exists: they serve quick web navigation and media management respectively. Avoid deleting established folders/customization purely to make the feature list shorter.

## Execution milestones

| Milestone | Work | Exit gate |
|---|---|---|
| A. Baseline and scope | Benchmark journeys, reproducible datasets, current tests/lint, agreed supported versions and release channel | Baseline report and one authoritative open-issues list |
| B. Reliability and launch | Work 1–5: startup, ordered persistence, scheduler/service, bounded I/O, renderer recovery | Process-death/timeout/low-storage cases pass; launch and paused-idle targets pass |
| C. Smoothness and platform polish | Work 6–10: artwork, discovery, Compose, filtering, audio/permissions/navigation/accessibility | Frame/interaction targets and accessibility matrix pass |
| D. Release candidate | Work 11, optimized profiles/build, packaging, full journey/device verification | No open P0s; signed artifact and upgrade tested; accepted P1 limitations documented |

Budget roughly **5–8 engineering weeks plus device/beta feedback** for the full scope, with overlapping efforts and shared refactors; re-estimate after Milestone A. Lazy tab restoration and removing synchronous writes are early wins. A database migration or background-tab eviction can stay separate if measurements show the smaller changes meet targets. Avoid starting MSE reconstruction during stabilization.

## Release verification matrix

| Area | Required cases |
|---|---|
| Core journey | New install → browser → detected direct/HLS/DASH → play → download → background → completed file → offline playback → Library resume |
| Tabs/navigation | 1/10/30 tabs; popup/opener return; Back/forward; Tabs during loading; native Home; renderer death; process restore; private entry/exit |
| Transfers | Two paused plus a third queued; mixed protocols; range ignored/changed validators; corrupt recovery; auth expiry; network loss/change; notification denial; service timeout; cancel/delete/replace races |
| Storage/output | Low space before and during transfer/mux; missing/revoked local URI; clear records without deleting files; output naming; long media sync/seek; upgrade migration |
| Player/platform | Headphones/Bluetooth disconnect, another app taking focus, call interruption, rotation/lock, captions/audio selection, error retry, rapid close/reopen |
| UI/accessibility | Light/dark/dynamic colors; 100/150/200% text; TalkBack; RTL; landscape/split-screen; small width; keyboard; both navigation modes; reduced motion |
| Device coverage | Low-memory phone and midrange phone, including 60/120 Hz where available; scoped-storage and notification-permission boundaries; foreground-service timeout behavior; target SDK/WebView compatibility |
| Privacy/security | Private crash cleanup; normal-tab logout disclosure; explicit logs/artwork; clear data; backup restore; bridge flooding/malformed messages/stale tokens; unsafe external intents; signed-URL redaction |

API 26–28 runtime testing was explicitly deferred in the existing project status. Preserve that decision for this review; before publicly claiming support for the entire `minSdk=26` range, either obtain release verification for those versions or explicitly revisit the supported range. Do not silently raise the minimum SDK.

Current unit tests chiefly verify pure parsers, codecs, and policies. Keep them, and add focused Android integration tests for ownership/lifecycle/scheduling/persistence boundaries. Use device benchmarks for speed; unit success does not establish smoothness. The existing user-led runtime-checklist workflow remains appropriate for hands-on confirmation.

## Verification from this review

- **Fresh debug compilation and unit tests: passed.** `testDebugUnitTest --rerun-tasks --offline` executed all 24 tasks successfully: **302 tests, 0 failures, 0 errors, 0 skipped**.
- **Android lint: failed on actual findings.** After fetching missing lint dependencies, `lintDebug` completed analysis and reported **14 errors, 74 warnings, 3 hints**. This is a release-readiness gap despite successful compilation/tests.
- **Runtime performance and device acceptance: not measured in this review.** No optimized release build, instrumentation run, or signed artifact was validated. The proposed budgets must be tested before claiming a speed improvement.
- Only this plan was added; application source and the pre-existing uncommitted changes were left intact. No commit was made.

### Lint triage to complete in Milestone A/B

| Finding | Count | Assessment and action |
|---|---:|---|
| `TabThumbnailCache.kt:89` calls `files.removeLast()` | 1 error | Lint resolves this to the API 35 Java List method. Replace with a backward-compatible operation such as `removeAt(lastIndex)` and verify cache trimming below API 35. Treat as a compatibility defect candidate, not a harmless style warning. |
| `MediaStoreOutput.kt:142` references API 29 Downloads URI | 1 error | Public entry points already branch at API 29. Verify all helper call paths and annotate the API-specific helper chain appropriately. Source review does not establish an unguarded pre-Q crash here. |
| Media3 unstable APIs in LibraryThumbnailStore, PlayerScreen, PlayerTrackControls | 11 errors | Add deliberately scoped opt-in declarations or use stable alternatives, and document dependency coupling. These are not 11 demonstrated runtime crashes. |
| Camera permission lacks optional hardware feature declaration | 1 error | Declare camera hardware optional if the app remains usable without it; verify device/store filtering. |
| Synchronous preferences | 12 warnings | Supports work item 2; preserve durability instead of a blanket `apply()` conversion. |
| Missing renderer termination handling | 2 warnings | Same underlying client reported at two locations; addressed by work item 5. |
| Document-start feature checks | 2 warnings | Both functions already contain a runtime capability guard; investigate lint recognition and use an accepted guard shape or a narrowly justified suppression. |
| Remaining warnings/hints | 58 warnings, 3 hints | Triage dependency updates, API/style suggestions, modifier conventions, unused resources, and Compose allocation/offset hints. Do not equate all warnings with blockers or churn the codebase just to clear style suggestions. |

Reports are generated under `app/build/reports/lint-results-debug.html` and `app/build/test-results/testDebugUnitTest/`. Preserve the summary above in release tracking because generated build reports can be overwritten.
