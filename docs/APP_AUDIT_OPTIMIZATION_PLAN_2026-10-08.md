# Miowser app audit and optimization plan

Date: 2026-10-08. Scope: the current working tree, including the recent player, subtitle, Library, folder-rule, backup, browser, and notification changes.

## Summary

The next optimization pass should focus on download scheduling, bounded transfers, WebView lifetime, player cache correctness, and backup consistency. These have clearer benefits than another visual redesign or a wholesale architecture rewrite.

The initial audit also identified correctness gaps: four lint errors, missing renderer-crash recovery, and SRT subtitles not receiving the same durable Library copy as WebVTT.

The initial source audit changed no application code. P0 items 1 and 2, P1 items 3–11, and P2 items 12–16 have since been implemented; see the [P0 verification report](G:/Projects/Miowser/docs/P0_OPTIMIZATION_VERIFICATION_2026-10-08.md) and [P1 implementation, verification, and device checklist](G:/Projects/Miowser/docs/P1_OPTIMIZATION_VERIFICATION_2026-10-09.md). The findings below retain the pre-implementation audit evidence. No emulator, device, installation, or runtime measurement was performed by the assistant.

**P0 local status:** 398 unit tests passed, debug/release lint had zero errors, and debug plus R8-shrunk release builds passed. Device recovery and API-26 codec checks remain with the user.

**P1 implementation status:** items 3–11 are implemented, including the user's selected six-page normal WebView budget and three-page low-memory budget. The P1 report records the final local checks and practical limits. Device acceptance and performance measurements remain with the user; P2 implementation and local verification are now recorded in the [P2 report](G:/Projects/Miowser/docs/P2_OPTIMIZATION_VERIFICATION_2026-10-09.md); the separate animation plan remains future work. P2 device acceptance and current profile generation remain pending.

### Evidence and limits

- Inventoried 165 production Kotlin files, approximately 29,223 lines, plus manifests, build configuration, tests, and benchmark infrastructure. Reviewed the main flows and resource-owning code across every subsystem below; this is not a claim that every line was exhaustively verified.
- Base Git revision: `f5a975ddc21c5913da622483f0229d5ffe5e0159`. The substantial existing working-tree changes are part of this audit; the revision alone does not identify the audited source.
- **Fresh local check:** `:app:lintDebug --offline --no-daemon` completed analysis and failed with **4 errors, 84 warnings, 5 hints**. [Generated report](G:/Projects/Miowser/app/build/reports/lint-results-debug.html). Generated reports can be overwritten; the relevant findings are recorded below.
- **Existing test evidence:** the local report dated 2026-10-08 02:32 shows **386 tests, zero failures, errors, or skips**. Tests were not rerun for this documentation-only audit. [Test report](G:/Projects/Miowser/app/build/reports/tests/testDebugUnitTest/index.html).
- Historical [physical measurements](G:/Projects/Miowser/docs/STEP03_PHYSICAL_DEVICE_RESULTS.md) recorded Library scroll deadline misses of 7.052% and Downloads of 1.847%. Those runs predate recent changes, used synthetic records, and had power-saving/thermal limitations. They justify investigating smoothness but do not describe current performance.
- Source-confirmed mechanisms are distinguished below from effects that still need a trace or device measurement. No speedup percentage is promised.

## What is already good

Preserve these foundations:

- Startup reads and core record encoding/writes already run off the UI thread, with explicit readiness and save acknowledgements.
- Saved tabs restore without immediately creating every WebView. Switching between live tabs retains their pages.
- Library artwork has a 24 MiB memory cache, sampled decoding, two artwork workers, and delayed loading during scrolling. Sorting/searching Library records runs on a worker.
- Ad blocking builds and publishes complete generations off-main, with bounded decision/CSS caches and an installed fallback.
- Media probes have two workers and a bounded queue. Subtitle fetching has byte limits; subtitle timing lookup already uses binary search.
- Player sample buffering and disk caching are separate. The current progress bar deliberately shows Media3's contiguous playable buffer rather than claiming all disk-cache ranges are playable.
- When everything is paused, the download service stops its notification tick and foreground ownership. Paused notifications are dismissible.
- Download output is in public Downloads/Miowser. Backup/clear-record actions preserve downloaded media. Backup archives have allowlisted paths, size limits, and hashes, and are read back after publication.
- Release shrinking and baseline/startup profiles exist. These should be refreshed and measured, not replaced.

## Coverage

| Area | Assessment / next focus |
|---|---|
| Startup and process restore | Async loading is established; remaining synchronous service restore and first-use cache/preferences work need attention. |
| Browser tabs and WebView | Lazy restore is established; live engines have no eviction policy or memory-pressure response, and clients lack renderer recovery. |
| Navigation, permissions, private mode | Retain existing origin checks, safe WebView defaults, and the disclosed shared-storage private model. Audit cleanup and remaining synchronous permission writes. |
| Media discovery and link/download handoffs | Bounded probes exist; cancellation, bridge input volume, duplicate detection, and stale work remain gaps. Preserve Stream and Select text actions. |
| Ad blocking and subscriptions | Generation/caches are established; URL token allocation and redundant rebuild work are measurement candidates. |
| Download engine and recovery | Paused worker starvation, unbounded HLS reads, eager segment queues, repeated hashing/checkpoint work, and frequent progress publication. |
| Download notifications | Recent paused behavior is improved; platform timeout handling and unchanged notification rebuilds remain. |
| Player, gestures, playlist | Reduce first-use blocking and frequent screen-wide updates; preserve the compact controls, overlays, playlist position, and skip controls. |
| Subtitles | Rendering/timing lookup improved; fix durable SRT storage, and isolate subtitle clock/style updates from the rest of the player. |
| Library, folders, automatic rules | Preserve hierarchy and manual-move priority; reduce repeated rule/token work and address silent saved-record eviction. |
| Bookmarks, history, speed dial | Async core stores established; bound favicon decoding and audit repeated whole-collection persistence as datasets grow. |
| Backup and restore | Good archive validation; cross-store snapshot consistency, concurrent writes, interruption recovery, and temporary-space budgets need work. |
| Settings, menus, responsive UI | Move remaining synchronous saves off-main; use actual container dimensions and verify font scaling/accessibility. |
| Build, tests, release performance | Close fresh lint errors, extend fixtures to current features, refresh profiles, and compare controlled release builds. |

## Prioritized work

**P0:** close before treating the build as release-ready. **P1:** next reliability/resource pass. **P2:** targeted improvements after the first pass, guided by measurements. These are work priorities, not claims of observed crashes.

### 1. P0 — Restore a clean lint gate

**Evidence:** [LibraryThumbnailStore.kt:225](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryThumbnailStore.kt:225) calls `getScaledFrameAtTime`, requiring API 27, without an API-26 fallback. Three additional lint errors are Media3 opt-in findings at [PlayerScreen.kt:2393](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt:2393) and [PlayerWindowPrefetcher.kt:26](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerWindowPrefetcher.kt:26), including the file-level finding.

**Change:** guard the scaled-frame API and provide a bounded fallback for API 26. Scope Media3 opt-ins to the declarations using them. Do not silently raise the minimum SDK or blanket-suppress lint.

**Done when:** debug/release lint have zero errors and relevant tests/builds pass. Keep deferred API-26 device verification visible. Most remaining warnings are style suggestions; prioritize the renderer, synchronous-write, and window-size findings over mechanical KTX conversion.

### 2. P0 — Recover from WebView renderer failure

**Evidence:** [MiowserWebViewClient.kt:37](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/MiowserWebViewClient.kt:37) and the popup proxy client at [Tab.kt:211](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt:211) have no `onRenderProcessGone` handling. Lint reports six locations, including duplicate reports; these are not six independent defects.

**Change:** detach and retire affected engines, invalidate renderer-backed transfers and callbacks, retain the tab record, and offer explicit Reload. Handle all affected views when a renderer is shared, including popup proxies. Coordinate this with item 5 so eviction and renderer recovery use the same lifecycle operations.

**Done when:** simulated renderer termination leaves the native app usable, tabs can reload, and a page-generated transfer fails clearly without hanging or publishing a partial file.

### 3. P1 — Make Pause release transfer capacity

**Evidence:** [DownloadQueue.kt:26](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadQueue.kt:26) has two download workers. [Download.kt:176](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/Download.kt:176) waits inside the worker while paused. HLS/DASH segment workers also wait. Consequently, two paused running jobs can occupy both slots while a third waits indefinitely.

**Change:** implement checkpoint-and-yield pause. Finish a safe checkpoint, close active network/file ownership, return the worker slot, and reconstruct work on Resume. Keep paused records/verified artifacts and the existing dismissible-notification behavior. Make queued cancellation/removal release queued tasks promptly.

**Done when:** pause A and B, enqueue C, and C starts without resuming either. Resume continues verified work. Cancel, retry, rapid pause/resume, and process restore cannot run duplicate workers or delete active recovery files.

### 4. P1 — Bound transfer memory and segment scheduling

**Evidence:** [HlsDownloader.kt:196](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/HlsDownloader.kt:196) reads entire segments through [PausableIo.kt:9](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/PausableIo.kt:9), an uncapped byte-array accumulator. Encryption adds another full payload. Manifest/key reads also use this helper. HLS and DASH enqueue every missing segment upfront at [HlsDownloader.kt:160](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/HlsDownloader.kt:160) and [DashDownloader.kt:199](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DashDownloader.kt:199). DASH media-to-file streaming is already present and should be retained.

**Change:** stream HLS bytes/decryption into temporary files with small fixed buffers; enforce separate manifest/key limits and validate media prefixes. Use a bounded producer/worker loop, not one queued task per segment. Share a connection budget across direct ranges, HLS, DASH, discovery, and player prefetch, with playback priority. Bound retry and cancellation latency.

Recovery currently hashes verified files in several passes and saves recovery metadata after each segment. Measure that I/O, avoid redundant digest passes, and batch only checkpoints whose loss is safely recoverable. Preserve corruption detection and atomic publication.

**Done when:** memory does not grow with segment payload size; pending tasks/connections have explicit caps; oversized/malformed responses fail safely; recovery still detects corruption. Establish the actual memory budget with release measurements rather than guessing a heap limit.

### 5. P1 — Bound retained WebView engines

**Evidence:** [RetainedTabEngines.kt:8](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/RetainedTabEngines.kt:8) retains every engine acquired until explicit release. [TabController.kt:10](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabController.kt:10) has no live-engine budget. No app memory-pressure handler was found. Lazy restore controls initial cost, but visiting every tab eventually creates every engine.

**Change:** add memory-pressure handling and an LRU policy for eligible background engines. Protect the visible page, active renderer transfers, and any explicitly supported ongoing activity. Save enough navigation state to make reopening understandable; avoid keeping large serialized WebView state for every dormant tab. Private state stays process-local.

**Done when:** live-engine count remains within an agreed budget after visiting 30 tabs; back/forward and popup return remain correct; renderer memory is measured separately from app memory. Choose the normal budget from reference/low-memory measurements rather than silently imposing an arbitrary number.

### 6. P1 — Correct player cache ownership and prefetch accounting

**Evidence:** [PlayerScreen.kt:411](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt:411) creates transport in composition. [PlayerMediaCache.kt:42](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerMediaCache.kt:42) initializes the cache, reads preferences, and removes expired resources synchronously under the cache lock. These are possible opening stalls; their duration is unmeasured.

[PlayerWindowPrefetcher.kt:217](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerWindowPrefetcher.kt:217) remembers requests as completed independently of actual cache contents. [PlayerMediaCache.kt:150](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerMediaCache.kt:150) can skip a write for low space or cap it at 12 MiB, yet the caller records completion. LRU eviction can subsequently remove those bytes. Progressive prefetch estimates time from average bytes per duration, which is approximate for variable-bitrate media.

**Change:** initialize/prune storage asynchronously before attaching transport; keep player operations on their required owner. Confirm cached spans before counting a request complete, retain resumable offsets for capped writes, and bound/reconcile bookkeeping after eviction and seeks. Reuse window candidate lists rather than rebuilding and sorting them each pass. Stop irrelevant prefetch on seek, track change, lifecycle exit, or network loss, including a request already in progress.

Preserve **Wi-Fi 15 minutes ahead / 5 behind**, **mobile 5 ahead / 5 behind**, and **10-minute idle expiry**. These are targets within available storage/server capability: at 10 Mb/s, 15 minutes alone needs about 1.125 GB, exceeding the current 768 MiB maximum. Prefer playback and the current window; never report bytes as cached merely because a request was attempted. Keep Media3's playable-buffer indicator accurate; any disk-cache visualization must represent real, possibly disjoint ranges. Background cleanup scheduling is best effort, while reopening must enforce expiry immediately.

**Done when:** capped/evicted/skipped ranges can be filled later; distant seeking cannot leave prefetch stuck; Wi-Fi/mobile transitions change the budget; high-bitrate/low-space cases preserve playback; cached bytes expire without touching user downloads.

### 7. P1 — Give SRT the same durable subtitle support

**Evidence:** [ExternalWebvtt.kt:27](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/ExternalWebvtt.kt:27) parses SRT and WebVTT. [LibrarySubtitle.kt:53](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibrarySubtitle.kt:53) only reads/saves text starting with `WEBVTT`. A selected SRT link can be recorded, but its text is not retained, so reopening depends on the remote link.

**Change:** save a validated subtitle format plus the bounded original text, or normalize both formats into a versioned durable representation. Migrate existing WebVTT copies and extend the backup allowlist/schema deliberately. Keep subtitle loading independent of styling changes and preserve millisecond delay semantics.

**Done when:** selected SRT and WebVTT survive restart and backup/restore; once copied, an expired subtitle URL does not prevent playback while the video itself remains available. Font, position, delay, and color changes never clear the attached subtitle.

### 8. P1 — Make backup snapshots consistent and restore interruption-safe

**Evidence:** [BackupSchedule.kt:87](G:/Projects/Miowser/app/src/main/java/com/miowser/app/backup/BackupSchedule.kt:87) waits for existing ordered writes, then [BackupEngine.kt:37](G:/Projects/Miowser/app/src/main/java/com/miowser/app/backup/BackupEngine.kt:37) reads each preference store separately and copies files. Later writes and independent artwork/filter writers are not frozen by this barrier. `@Synchronized` coordinates BackupEngine calls, not all repositories. Restore rolls back caught exceptions but updates files and preferences progressively at [BackupEngine.kt:76](G:/Projects/Miowser/app/src/main/java/com/miowser/app/backup/BackupEngine.kt:76); process termination bypasses that exception rollback.

**Change:** capture one immutable logical snapshot on the repository owner after the durability boundary, stage referenced assets under coordinated file ownership, then archive on a worker. For restore, stop accepting conflicting mutations, stage and validate everything, and use a durable restore journal/generation with recovery before repository startup. Do not rely solely on a later process-close dialog. Ensure paused transfers and independent maintenance writers cannot overwrite restored records.

Preflight space for the archive, verification extraction, restore staging, and rollback. Current archive bounds are 1,500 entries / 32 MiB each / 256 MiB total. Keep bounds; make large backups handle regenerable artwork explicitly rather than silently omitting settings or selected subtitles. Preserve hashes/read-back validation even if metadata-only verification later avoids an extra full extraction.

**Done when:** a backup made during title/folder/subtitle changes represents one consistent state; interrupted restore recovers to an entirely old or entirely new state; revoked directory access and low space are recoverable; history/tabs/cookies/private data remain excluded and downloaded media remains untouched.

### 9. P1 — Cancel stale discovery and bound bridge input

**Evidence:** [Tab.kt:401](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt:401) discards the returned probe handle. Navigation invalidates results but cannot cancel those probes. [StaticMediaDiscovery.kt:18](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/StaticMediaDiscovery.kt:18) only sets a stop flag; it does not disconnect an in-flight request. [GrabberBridge.kt:18](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/GrabberBridge.kt:18) validates only nonblank input before dispatch. Per-page detection lists/seen URLs have no explicit size cap, and matching existing detections scans the list.

**Change:** retain handles by tab/document generation, cancel and disconnect stale work, and discard canceled scans before starting them. Deduplicate by URL with a bounded index; impose URL/message/count budgets before posting to main. Batch passive bridge updates while allowing explicit user checks priority. Use a WeakSet to avoid repeatedly attaching element listeners when DOM nodes are reinserted.

**Done when:** rapidly navigating or closing a tab releases its probes promptly; a busy page cannot grow the detector/posted work indefinitely; explicit media checks still proceed under passive discovery load. Preserve legitimate metadata updates and authenticated request context.

### 10. P1 — Sample download progress and isolate player clocks

**Evidence:** [DirectFileDownloader.kt:219](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DirectFileDownloader.kt:219) updates observable per-range and aggregate progress for every buffer, repeatedly summing range state. [PlayerScreen.kt:672](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt:672) updates a subtitle clock every 50 ms; [PlayerScreen.kt:810](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt:810) reads it in the main player composition. Broad recomposition cost needs measurement, but these update paths are confirmed.

**Change:** retain exact transfer counts in worker counters; publish immutable progress at a bounded rate, initially no more than four times per second, with state changes immediate. Use a shared status/presentation model for rows and notifications. Move subtitle timing into a dedicated overlay and publish cues only when they change; separate timeline, gesture, track, and title state. Suspend UI-only clocks when the screen is not active. Keep position/delay precision and save-on-pause/exit behavior.

**Done when:** progress is monotonic and accurate, status transitions remain immediate, subtitle precision is preserved, and counters/traces show unrelated player controls do not update at the subtitle-clock rate.

### 11. P1 — Complete service lifecycle handling

**Evidence:** [DownloadForegroundService.kt:75](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadForegroundService.kt:75) removes tick callbacks but has no platform timeout override. Notification refresh rebuilds/posts running notices every second. Resume after process death calls synchronous `DownloadQueue.restore` from `onStartCommand`.

**Change:** handle platform-imposed data-sync timeout with coordinated checkpoint/worker cancellation before service shutdown. Restore records asynchronously while meeting foreground-start requirements. Publish notices only when presentation changes, retaining meaningful timing updates. Keep notification-dismissal state transitions and all-paused service shutdown intact.

**Done when:** timeout cannot leave unowned network workers running; cold-process notification Resume does not block the main thread on record decoding; notification denial/dismissal/pause/resume remain correct. Use controlled timeout testing rather than waiting through a long transfer manually.

### 12. P2 — Remove remaining persistence bottlenecks carefully

**Evidence:** [OrderedWriter.kt:26](G:/Projects/Miowser/app/src/main/java/com/miowser/app/persistence/OrderedWriter.kt:26) queues every snapshot. Session/Library/history saves encode complete collections; [BrowserScreen.kt:807](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt:807) observes session snapshots. Backup directory/interval callbacks use synchronous `commit` through [BackupSchedule.kt:39](G:/Projects/Miowser/app/src/main/java/com/miowser/app/backup/BackupSchedule.kt:39); permission clearing also commits synchronously. Player styles use independent `apply` saves outside the ordered writer.

**Change:** move remaining disk operations off-main; coordinate settings/track/style saves with snapshot boundaries. Coalesce superseded full snapshots by key only where every receipt/barrier is still acknowledged by an equally new durable snapshot. Exclude commands/deletions/file side effects from generic coalescing. Persist slider settings at gesture completion or debounce while keeping the visual preview immediate. Measure bytes/write and queue depth before considering Room for larger collections.

**Done when:** StrictMode finds no avoidable main-thread store I/O; rapid edits do not create a long save backlog; failed writes remain retryable and newer saves cannot be overwritten. Do not replace durability checks with blanket `apply()` calls.

### 13. P2 — Bound and prioritize artwork work

**Evidence:** [LibraryThumbnailStore.kt:39](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryThumbnailStore.kt:39) shares two workers between decoding, extraction, storage, and pruning. Background execution prevents direct main-thread extraction, but long extractor work can still delay visible artwork. [SpeedDialSiteMetadata.kt:202](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/SpeedDialSiteMetadata.kt:202) decodes favicon bytes before resizing; a compressed-byte cap does not bound decoded pixel memory. Metadata maps in [DownloadedFileInspector.kt:12](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadedFileInspector.kt:12) are unbounded.

**Change:** bound bitmap dimensions before decode, use byte-weighted metadata/image caches, separate visible decode priority from extraction/maintenance, and cancel or discard obsolete queued work. Prefer already-decoded player frames for streams. Coalesce per-media metadata extraction and avoid retrying a failed thumbnail on every card revisit.

**Done when:** a grid fling cancels unnecessary pending work; extraction cannot starve visible thumbnails; oversized icons cannot cause a large decode allocation; cached artwork survives ordinary navigation without duplicate jobs.

### 14. P2 — Precompute rule matching; protect saved records

**Evidence:** [LibraryTitleRule.kt:21](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryTitleRule.kt:21) tokenizes titles and phrases per match and allocates window lists. [LibraryFolderRouting.kt:53](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryFolderRouting.kt:53) repeatedly filters the folder collection during routing. Reorganization iterates on the main owner. [MediaLibrary.kt:7](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/MediaLibrary.kt:7) caps records at 500, trimming the oldest even for explicit Add to Library.

**Change:** compile phrases and folder/domain indexes when rules change; tokenize video facts once per routing pass. Compute bulk destinations off-main from an immutable snapshot, then apply them once after checking revisions. Preserve website-over-title priority, AND specificity, manual moves, default-folder source scope, and parent-only child routing.

Treat the 500-entry cap as a product/data-retention decision: recommend explicit saves persist until removal, with any recent-play history bounded separately. Do not silently expand an in-memory list indefinitely; measure a larger dataset before choosing indexed database storage. Clean orphaned captions/artwork after deletion safely rather than only on the next startup.

**Done when:** rule edits remain responsive at the supported scale, stale computations cannot overwrite manual moves, and adding video 501 cannot silently discard an explicitly saved item under the revised retention policy.

### 15. P2 — Reduce ad-block matching allocations without changing decisions

**Evidence:** [FilterEngine.kt:72](G:/Projects/Miowser/app/src/main/java/com/miowser/app/adblock/FilterEngine.kt:72) allocates a substring for every five-character window of uncached ASCII URLs. Decision caching already avoids this for repeated identical requests; unique query URLs still take the full path.

**Change:** benchmark representative misses/hits and long URLs first. Evaluate rolling token hashes or another allocation-light index with collision verification. Deduplicate concurrent reload requests by fingerprint, preserving the installed generation and exceptions/manual-rule precedence.

**Done when:** classification decisions match the existing regression corpus and allocation/CPU measurements improve on the same rules and URLs. Do not weaken filtering or remove supported rules merely to improve a benchmark.

### 16. P2 — Focus UI/refactoring and refresh release evidence

**Evidence:** [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt) has 2,752 lines; [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt) has 2,806. Size increases change risk but is not itself evidence of slowness. Lint flags seven configuration-width/height uses in sheets/player panels. [DownloadsPage.kt:25](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/DownloadsPage.kt:25) has a declaration but no found call site. [AppBenchmarks.kt:126](G:/Projects/Miowser/benchmark/src/main/java/com/miowser/benchmark/AppBenchmarks.kt:126) generates a startup-only profile; current fixtures do not represent real artwork, remote playback, active transfers, nested rules, or subtitle editing.

**Change:** extract resource ownership and high-frequency state first: playback session, subtitle overlay, download presentation, and browser overlays/navigation. Use actual container constraints for responsive panels, preserve touch targets/semantics, and remove confirmed unused screen code after reference/build verification. Redact sensitive URL queries and gate page-console logging in release; current logging forwards page text/URLs.

Configure the player's audio attributes, audio-focus response, and headphone-disconnect behavior explicitly; no focus/noisy handling was found in the inspected source. Add visible/accessible alternatives to gestures and keep locked-player exit understandable. These are platform correctness checks, not claimed performance wins.

Extend deterministic fixtures to current journeys and refresh baseline profiles for common interactions. Reconcile older status documents with this dated plan. Avoid a simultaneous navigation/database/design rewrite, dependency-upgrade sweep, or replacing all custom UI before measurements identify a need.

**Done when:** extracted components have explicit lifecycle boundaries, layout/accessibility regressions pass, release bridges still work under shrinking, and current benchmark/profile evidence matches the delivered APK.

## Implementation order and gates

| Stage | Work | Gate before moving on |
|---|---|---|
| A — Correctness and saved data | Items 1, 2, 7, 8; add focused lifecycle/storage integration checks. | Clean lint; durable subtitles; interruption-safe restore; renderer failure recovery. |
| B — Transfer and cache ownership | Items 3, 4, 5, 6, 9, 11. Design the scheduler and cancellation contracts before parallel changes. | No paused-slot starvation; explicit resource caps; predictable cancellation; real cache accounting. |
| C — Smoothness and I/O efficiency | Items 10, 12, 13, 14, 15; measure each change independently. | Lower UI publication/allocation/write load without loss of state or timing accuracy. |
| D — Responsive UI and release proof | Item 16, representative fixtures, regenerated profiles, release regression checks. | Current release measurements and user acceptance checklist; no unresolved P0. |

Keep patches reviewable by subsystem. Use the existing unit suite for parsers, routing, codecs, scheduler/cache policies, and writer ordering; add integration checks for actual file/provider/lifecycle behavior. Compilation alone cannot validate those boundaries.

## Performance assurance — what must actually become faster

An optimization is accepted as a speed improvement only after it reduces the cost of a defined workload. Cleaner code, longer animation durations, smaller APKs, and fewer recompositions by themselves are not proof that the app feels or runs faster. Items 1, 2, 7, and 8 primarily protect compatibility, recovery, and saved data; report them as reliability fixes, not speedups.

### Expected benefits and regression guards

These hypotheses follow from the audited source. Their size and user-visible effect are still unmeasured.

| Items | Mechanism / expected performance benefit | Measure before and after | Guard against a false improvement |
|---|---|---|---|
| 3 — Paused jobs | Return occupied worker slots so waiting downloads start. | Enqueue-to-start latency with two paused jobs; concurrent worker count. | Resume must retain verified progress and never create duplicate workers. |
| 4 — Transfers | Replace payload-sized arrays and eager segment tasks with bounded streaming and queues. | Peak heap, allocations/GC, queue depth, and bytes/s using identical media. | Lower memory cannot come from silently reducing concurrency or throughput; integrity and cancellation must pass. |
| 5 — Browser engines | Bound expensive live engines and handle memory pressure. | App plus renderer memory after visiting 30 tabs; warm and evicted-tab switch p95. | Eviction makes some switches reload: choose the budget from measured memory/latency tradeoffs, preserve tab state, and protect the active page. |
| 6 — Player setup/cache | Move avoidable first-use disk setup off-main and stop duplicate/stale prefetch. | Main-thread setup time, tap-to-first-frame, rebuffer time, requested bytes, actual cached spans. | The requested 15/5-minute and 5/5-minute windows use more storage/network than a small buffer. They are playback resilience targets, not guaranteed startup speedups. Playback must take priority over prefetch. |
| 9 — Discovery | Cancel irrelevant work and cap bridge input. | Current-page detection latency, stale jobs, request count, allocation peaks during navigation. | Keep genuine detections, private-mode boundaries, and Stream/Add to Library behavior. |
| 10 — UI updates | Sample progress and restrict subtitle-clock updates to the subtitle renderer. | Frame overruns, main-thread CPU, and update scope during playback plus active downloads. | Keep the fine subtitle clock, seek/delay accuracy, and immediate terminal/error states; do not slow the entire UI timer to hide work. |
| 11 — Notifications | Avoid rebuilding unchanged notifications; make cold Resume asynchronous. | Notification builds/s, service CPU while active/paused, Resume-to-start p95. | Preserve sticky running and dismissible paused notices and timely progress/actions. |
| 12 — Persistence | Coalesce superseded snapshots and remove remaining UI-thread commits. | Saves/s, encoded/written bytes, main-thread stalls, pending-save drain time. | Acknowledged state must survive restart; ordered writes, backup snapshots, and flush barriers remain correct. |
| 13 — Artwork | Prioritize visible requests, bound decode, reuse metadata. | Scroll frame overruns, image-ready latency, decode CPU/peak heap with real artwork. | Do not improve frame numbers by leaving cards blank longer or using visibly degraded thumbnails. |
| 14/15 — Rules/filtering | Compile repeated phrase/domain match inputs and reduce URL-token allocation. | CPU/allocation per rule pass and request match on representative datasets. | Matching results, website priority, AND/OR semantics, manual moves, and filter decisions must remain identical. |
| 16 — UI ownership/profiles | Reduce work owned by broad screen scopes; compile measured common paths. | The affected journey's frame/startup metrics under the same compilation mode. | Extraction alone is not a performance result; separate code changes from profile gains. |

### Before/after acceptance contract

1. Capture a current baseline from the actual working-tree snapshot, not just its base commit. Record build inputs and fixture identity. Keep a reproducible baseline APK for comparison.
2. Change one subsystem per comparison. Use the same release-equivalent R8, profile/compilation mode, device, refresh rate, dataset, and thermal conditions. Compare profile regeneration separately, then confirm the final combined build.
3. Start with at least 10 timed iterations for latency journeys and repeated fixed-duration scroll/animation journeys. Interleave baseline/candidate runs to reduce thermal/order bias; increase runs when the difference is within run-to-run variation. Report p50/p95 and frame overruns, not the single best run.
4. Use a deterministic server/local fixture to measure code costs. Separately verify real-network behavior; server variance must not be credited to an app optimization. Test cold/warm cache, real thumbnails, active downloads, attached subtitles, and populated folders. Existing placeholder/failed-download fixtures do not cover these loads.
5. **Speed gate:** the intended latency/CPU/frame metric must improve beyond observed baseline noise. **Resource gate:** memory/allocation optimizations must lower the intended resource cost without degrading latency, throughput, or frame deadlines. If no benefit can be distinguished, describe it as unproven or retain it only for a separate necessary correctness/resource bound.
6. Flag any >5% adverse movement in related p95 latency, throughput, or memory for investigation. This is a provisional triage threshold, not permission for smaller regressions; a repeatable regression beyond noise needs resolution or an explicit documented tradeoff.
7. Verify first response as well as completion: a polished transition must not delay click handling, player preparation, download start, or content fetching until its animation ends.
8. Publish a compact result per patch: baseline → candidate, workload, device/build, variability, countermetrics, and regression checks. Do not claim a percentage speedup before those results exist.

The highest-confidence immediate performance work is paused-worker release, bounded transfer memory, stale-work cancellation, and isolating frequent UI updates. Browser eviction and prefetch policies need explicit tradeoff measurements. Minor allocations and broader refactoring remain trace-guided.

The separate [animation smoothness plan](G:/Projects/Miowser/docs/ANIMATION_SMOOTHNESS_PLAN_2026-10-08.md) applies this same contract. Device measurements remain with the user; this audit does not run an emulator or device.

## Performance measurement plan — device work stays with the user

Record the APK/source identity, build variant, R8/profile state, device/RAM/API, WebView version, refresh rate, battery/thermal state, font scale, and fixture identity. Compare the same workloads under similar conditions. Measure app and renderer memory separately.

| Metric | Workload | Proposed acceptance |
|---|---|---|
| Populated startup | Empty and 500-record Hub; 1/10/30 restored tabs | Retain earlier provisional p95 goals: 1.5 s reference / 2.5 s low-memory; compare fresh baseline. |
| Scrolling | Real thumbnails; 200/1,000 download records; active progress | Target <1% missed deadlines in a fixed journey; inspect long stalls and 60/120 Hz separately. |
| Player opening | Local, direct remote, HLS, DASH; warm/cold cache | Record tap-to-first-frame p50/p95; separate server time from main-thread setup. |
| Memory | Visit 30 tabs; 30 player open/close cycles; concurrent transfers | Bounded engines/tasks/caches; no unexplained monotonic retained-memory growth. |
| Scheduling/cancellation | Pause two jobs, add a third; seek/close while prefetching | Third job starts; irrelevant connections stop promptly; no duplicate workers. |
| UI/persistence | Active ranges, subtitles, rapid edits, rule changes | Initially ≤4 progress publications/s; subtitle changes only when cues/style change; save queue returns to idle. |
| Background cost | All paused; hidden player; backup/filter maintenance | No service tick solely for paused downloads; no UI clocks solely for invisible screens. |
| Cache/backup | High bitrate, low space, handover, expiry, large archive | Verified actual spans; bounded storage; coherent recoverable backups. |

These are acceptance proposals, not measured results. Collect a current baseline before tuning; do not infer a speed improvement from debug APK size or historical benchmark numbers.

## User regression checklist

Use this after the corresponding patches, rather than repeatedly running the entire list for a small change.

### Browser and UI

- [ ] Open 1/10/30 tabs, visit each, switch, close/undo, restart, and verify back/forward and popup return.
- [ ] Trigger renderer recovery; confirm Reload and page-generated transfer behavior.
- [ ] Long-press All tabs opens a new tab; link long-press Select text still allows partial selection.
- [ ] Media counter stays readable for one, two, and three digits; detected-media Add to Library still works without playing.
- [ ] Rapid navigation/closing during discovery stops old work without losing current-page detections.
- [ ] Check light/dark/dynamic colors, portrait/landscape/split-screen, 100/150/200% fonts, TalkBack, keyboard, and both navigation modes. Verify compact continuous sheets and jump-to-top animation.
- [ ] Private entry/exit/restart preserves the existing disclosure and excludes private records from Library/history/backups.

### Downloads

- [ ] Pause A/B, start C; C runs. Resume A/B continues correctly. Repeat with direct/HLS/DASH mixtures.
- [ ] Pause notifications can be dismissed and stay dismissed; resumed running work has correct sticky notices.
- [ ] Notification Resume works after process death; notification permission denial does not break transfers.
- [ ] Network loss/change, expired links, ignored ranges, retries, cancel/delete/replace, corrupt recovery, and low space have understandable outcomes and no duplicate outputs.
- [ ] Stream from a page-download snackbar stops the pending download, opens playback, and explains how to save from the player. Also test a download that finishes quickly before Stream is tapped.
- [ ] Removing records/clearing data and uninstalling leave completed public media intact; reinstall/restore can relink inaccessible URIs.

### Player and subtitles

- [ ] Direct/HLS/DASH/local playback: pause/play, buffering spinner, soft control overlays/shadows, disabled options, progress/thumb/timer alignment, remaining-time toggle, and seek destination feedback.
- [ ] Library playlist reflects its original visible order; the icon remains between Lock and Back; the side panel and next/previous/10-second actions remain spaced and functional.
- [ ] Wi-Fi 15-ahead/5-behind and mobile 5-ahead/5-behind targets; handover, distant seek, quality/audio change, low space, high bitrate, close/reopen before and after 10 minutes.
- [ ] SRT/WebVTT selection, Library reopen, expired subtitle URL after a saved copy, and backup/restore.
- [ ] Millisecond delay, fonts/presets, opacity, outline, color, bold, and position work during playing/paused/buffering. The compact sample-text preview stays grey in both themes; changes never make subtitles disappear.
- [ ] Lock/orientation, gestures, app background/foreground, headphone disconnect, and another app taking audio focus. Audio-focus/noisy handling is not explicitly configured in the inspected player builder and needs a deliberate policy/check.

### Library, settings, backup

- [ ] Default folder, subfolders, inline nested Move menus, counts, and folder rules survive restart.
- [ ] Website-over-title priority, AND/OR whole-phrase matching, punctuation/case variants, default-only root source, parent-only child scope, title rechecks, and manual-move priority remain correct.
- [ ] Rule changes ask before reorganizing existing items; a subfolder cannot become default; saved-record capacity behavior is explicit.
- [ ] Site-specific minimum duration overrides the global setting; absent override uses global.
- [ ] Custom backup directory and interval survive restart; revoked access reports failure without deleting a good backup.
- [ ] Backup during metadata/folder/subtitle changes; restore with paused work; terminate during restore; large archive, low space, corrupt archive, older schema, and relinking media.
- [ ] Restored settings, bookmarks, speed dial, Library, selected captions, site rules, and filters agree. History/open tabs/cookies/private data remain excluded.

## Recommended starting point

Start Stage A with the four lint errors, durable SRT storage, renderer recovery, and the backup snapshot/restore contract. Then fix paused-worker starvation and uncapped transfer reads before spending time on minor Compose allocations or visual changes. Reassess the P2 order using the first current release traces.
