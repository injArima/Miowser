# Miowser: step-by-step optimization guide

**2026-10-09 status:** This earlier document retains historical requirements/results. See the [current dated audit](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md) and [P2 implementation, local results, updated fixtures, and device checklist](G:/Projects/Miowser/docs/P2_OPTIMIZATION_VERIFICATION_2026-10-09.md). P0/P1/P2 source work is locally verified; current device acceptance and profile regeneration remain with the user.

Created 2026-09-25. This is the execution companion to the [ship-readiness plan](G:/Projects/Miowser/docs/SHIP_READINESS_OPTIMIZATION_PLAN.md). The plan explains priorities; this guide explains how to implement and verify them. These steps are instructions for future implementation, not claims that the changes are already complete.

## How to work through this guide

Complete steps in order. Each step should produce a small reviewable change, a verification result, and a short record in the [execution tracker](G:/Projects/Miowser/docs/OPTIMIZATION_EXECUTION_TRACKER.md). Split larger steps into the numbered changes below. Do not mix dependency upgrades, storage migrations, and UI redesign in one change.

For each step: inspect current code → implement → run the relevant checks → give the user the device checklist → record Pass, Fail, or Awaiting device verification. A successful build is not a successful performance test. A failed device check reopens the step; it does not disappear into the next milestone. Independent work can continue while user verification is pending, but dependent release gates remain open.

Preserve the current Hub navigation, Library folder behavior, player theme, and persisted user data. Do not introduce background-tab eviction or delete established features as a shortcut. Commit only when explicitly requested, consistent with the project's existing workflow.

The latest audit established **302 passing unit tests**, with **14 lint errors and 74 warnings**. Treat these as the September 25 baseline, not a permanent count. Refresh it after code changes.

## Step 1 — Set up a repeatable development checkpoint

**Outcome:** later changes can be compared with the exact starting state.

1. Open `G:\Projects\Miowser` in Android Studio. Confirm Gradle uses a supported installed JDK; the audit successfully used Android Studio's bundled runtime.
2. Record the current revision and uncommitted file list. Preserve the existing UI changes and the source plan. Do not reset, clean, or discard them. A Git revision alone does not identify this working-tree baseline; keep the diff/patch and relevant untracked source documents as well.
3. Create a dedicated test device/profile with disposable browsing/download data. Real user data should not be used for destructive recovery tests.
4. Record device model, RAM, Android API, WebView version, refresh rate, build variant, and dataset size in the tracker.
5. Record the release scope: the existing browse/find/play/download/Library journey. List the intended distribution channel and supported Android versions as unresolved release decisions if not yet known. Leave minimum SDK unchanged.
6. Establish a private location for raw traces/screenshots and a small repository summary for results. Never publish authenticated test URLs or cookie-bearing traces.

**Verification:** the current app launches on the chosen device; you can reproduce the same test data. Use the command appendix for the first compilation/unit/lint run.

**Complete when:** the starting state and environment are identifiable, and the tracker has an owner/status for every step.

## Step 2 — Fix the known lint blockers

**Start in:** [TabThumbnailCache.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabThumbnailCache.kt), [MediaStoreOutput.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/MediaStoreOutput.kt), [LibraryThumbnailStore.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryThumbnailStore.kt), [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt), [PlayerTrackControls.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerTrackControls.kt), and [AndroidManifest.xml](G:/Projects/Miowser/app/src/main/AndroidManifest.xml).

1. Replace `files.removeLast()` in thumbnail trimming with `files.removeAt(files.lastIndex)`. Preserve the existing nonempty-loop condition.
2. Trace every call to MediaStore-only helpers. Retain API 29 guards at entry points; annotate the API-specific helper chain so lint can establish its requirement. Do not silence an actually unguarded call.
3. Resolve the 11 Media3 opt-in errors at the smallest appropriate declarations, using the annotation form accepted by the installed AndroidX tooling. Document where the app depends on unstable APIs.
4. Declare camera hardware optional when the app can run without it. Check whether other permission-implied hardware also needs an optional feature declaration.
5. Run lint again. Review the two document-start warnings: runtime guards already exist, so investigate recognition before adding narrowly justified suppressions.
6. Keep remaining warnings categorized by correctness, performance, compatibility, and style. Do not create a blanket lint baseline to hide release blockers.

**Device check:** on a supported device below API 35, exercise enough thumbnail captures to trigger cache trimming, then close/reopen Tabs. Test the normal download-output path.

**Complete when:** lint has zero unresolved errors and remaining warnings have a disposition. Pure annotation changes do not need invented unit tests; the compatibility-sensitive cache behavior needs runtime verification.

## Step 3 — Establish release performance measurements

**Start in:** [app/build.gradle.kts](G:/Projects/Miowser/app/build.gradle.kts), [settings.gradle.kts](G:/Projects/Miowser/settings.gradle.kts), and [libs.versions.toml](G:/Projects/Miowser/gradle/libs.versions.toml).

1. Capture the current non-debuggable release behavior before enabling optimizations. Keep this first measurement separate from debug profiling.
2. In a separate change, enable release code/resource shrinking using the current AGP configuration. Validate bridge invocation and media paths after shrinking; add only necessary keep rules. Use a local test-signing arrangement for device testing, not production signing secrets in source. [R8 setup guide](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
3. Use the implemented `:benchmark` module and [Step 3 run guide](G:/Projects/Miowser/docs/STEP03_BENCHMARKS.md). It contains Macrobenchmark journeys and a Baseline Profile generator using the official direct-module approach. Its PowerShell runner captures source/device/artifact evidence. For a new project, Android Studio's **File → New → New Module → Baseline Profile Generator** is the template alternative. [Official setup guide](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile).
4. Add repeatable journeys for populated Hub startup, Library scroll, Downloads scroll, browser entry, Tabs opening/switching, and local player opening. Use stable UI semantics/test IDs and wait for a meaningful ready state, not a fixed sleep.
5. Record initial display separately from usable populated content. Add a fully-drawn signal tied to the latter. Never signal “ready” merely because an empty shell appeared.
6. Use 500 Library entries, 2,000 History entries, 200 download records, and 1/10/30 tab records. Add a 1,000-download stress case. Seed disposable data through test-only fixtures without shipping a public import/debug endpoint.
7. Use a fixed midrange device for speed and a low-memory device for stress. Run at least 20 startup repetitions and repeat scrolling sequences. Keep WebView, refresh rate, network, compilation mode, and thermal conditions consistent.
8. Save raw results and a comparison summary. The original plan contains the proposed numeric targets. For every future comparison, use the same optimized baseline configuration; report R8's effect separately from code changes.

**How to investigate a slow run:** reproduce one journey in a system trace; locate the delayed input/frame; identify app main-thread, renderer, disk, image, or network work; change the owning path; rerun that journey. Use debug recomposition tooling for diagnosis, not as the speed score.

**Complete when:** you have actual baseline numbers and can reproduce them. Emulator diagnostics validate the harness; physical-device measurements are required to accept the shipping performance targets.

## Step 4 — Give state and persistence clear owners

Implementation, ownership inventory, verification commands, and scope boundaries: [Step 4 ordered persistence](G:/Projects/Miowser/docs/STEP04_ORDERED_PERSISTENCE.md).

**Start in:** [DownloadQueue.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadQueue.kt), [MediaLibrary.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/MediaLibrary.kt), [HistoryLog.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/history/HistoryLog.kt), and their stores.

1. Inventory each store's read/write callers and thread. Separate small preferences, list indexes, and recovery artifacts.
2. Establish one serialized owner for each mutable repository. Capture immutable snapshots there; do not read a changing Compose list on an I/O thread while the UI mutates it.
3. Move encoding and blocking disk access to an ordered background writer. Publish UI state from the owner. Preserve operation ordering so a delayed old snapshot cannot overwrite a newer edit.
4. Classify durability: a cosmetic sort change can be coalesced; acknowledged user edits and recovery checkpoints need an explicit successful write boundary. Optimistic UI must show an error/revert if persistence fails.
5. Batch multi-delete, clear, and folder moves. Update the repository once and persist one snapshot/transaction rather than calling single-item deletion in a loop.
6. Keep existing codecs initially. Adopt Room for list indexes only if measured scale/transaction needs justify migration; use DataStore or an ordered writer for preferences. Do not migrate every store simultaneously.
7. If migrating, import the old format idempotently, validate the result, mark migration complete only after a successful durable transaction, and retain a recovery path. Interrupted migration must not create duplicates or destroy the original data.

**Tests:** delayed writes completing out of order, write failure, repeated restore, bulk clear, old codec records, process restart after an acknowledged edit. In debug only, use StrictMode to identify app-owned main-thread disk operations.

**Complete when:** edits remain durable and UI actions do not wait for store writes. Do not rely on activity `onStop`/`onPause` as the sole save point.

## Step 5 — Make Hub startup asynchronous

Implementation and verification: [Step 5 asynchronous startup](G:/Projects/Miowser/docs/STEP05_ASYNC_STARTUP.md).

**Start in:** [MainActivity.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/MainActivity.kt) and [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt). Depends on Step 4.

1. Introduce an explicit startup state: Loading, Ready(snapshot), and Error(retry). Separate Hub readiness from browser readiness.
2. Compose the shell promptly. Remove synchronous Library/folder/download restoration from before `setContent`.
3. Restore the minimum Hub snapshot through the repository. Render a stable loading treatment until records are known; render an empty state only after a successful empty result.
4. Keep interrupted-private-session cleanup as a browser/authentication barrier. Independent local Hub reads can proceed, but no browser loads or cookie-dependent resume may bypass cleanup.
5. Move settings initialization into a defined sequence. Repositories must not observe temporary defaults and overwrite saved preferences before settings load finishes.
6. Restore lightweight download display records first. Inspect recovery directories only when needed, without claiming an item is resumable before verification.
7. Defer History, bookmarks, and browser-only details. Keep startup idempotent on activity recreation.

**Device check:** launch with empty data, maximum Library data, and many recovery items. Watch for false “empty Library,” changing sort order, lost folders, and delayed taps. Test retry after a simulated read failure.

**Complete when:** populated Hub launch meets the agreed budget without shifting an unacceptable delay to first browser entry. Record both timings.

## Step 6 — Separate saved tabs from live WebViews

Implementation and verification: [Step 6 lazy tabs](G:/Projects/Miowser/docs/STEP06_LAZY_TABS.md).

**Start in:** [Tab.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt), [TabSessionStore.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabSessionStore.kt), [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt), and [TabsPage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabsPage.kt).

1. Introduce a lightweight record containing identity, title, URL, opener relationship, private flag, preview key, and activity time. Keep private records out of disk restore.
2. Give a tab controller a map of live tab engines keyed by record ID. Use the records for counts, order, and overview cards.
3. Restore records without calling the current `Tab` constructor, because that constructor creates and loads a WebView.
4. Create the selected engine on browser entry or record activation. WebView creation/access must stay on the main thread; only preview decoding and store work move to I/O.
5. Apply settings, bridges, fallback filters, and permission callbacks before the first URL load. Reconnect opener/Back/close behavior through IDs, including when the opener has no live engine yet.
6. Preserve live engines on ordinary switching. Distinguish detaching a view from intentionally destroying its engine; the current `AndroidView.onRelease` destruction must match controller ownership.
7. Decode previews asynchronously. Keep the native Home path lightweight without depending on a hidden loaded page.

**Tests:** restore 30 records while Hub is visible and verify no page network loads; open one and verify only its engine is created; switch back to a live tab without reload; close a child and return to its opener; restore old session formats.

**Complete when:** lazy restore works and existing warm-tab behavior is preserved. Do not add routine background-tab eviction in this step.

## Step 7 — Recover cleanly from renderer loss

**Start in:** [MiowserWebViewClient.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/MiowserWebViewClient.kt) and the tab controller from Step 6.

1. Implement `onRenderProcessGone` and route it to the engine owner.
2. Mark affected engines unavailable; invalidate renderer transfers, permission/file callbacks, probes, and captures that belong to those engines.
3. Detach and destroy unusable views and remove references. Keep tab records and cached previews.
4. Show a reload action. Recreate a new engine only when appropriate; do not reuse a dead WebView or create an automatic reload/crash loop.
5. Account for multiple WebViews sharing a renderer and callbacks arriving for each. Cleanup must be idempotent. [Renderer recovery guide](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination).

**Device check:** in a disposable debug session, use an Android-supported renderer-crash test method. Verify that the app, Hub, other usable tabs, and native downloads remain operable. Repeat close/reload races.

**Complete when:** renderer loss becomes a recoverable page error and closed engines do not remain retained. Memory-pressure eviction remains a separate policy decision if later measurements require it.

## Step 8 — Fix download pause and scheduling

**Start in:** [Download.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/Download.kt), [DownloadQueue.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadQueue.kt), and [DownloadRecoveryStore.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadRecoveryStore.kt).

1. Write a pure scheduler/state-transition policy before connecting it to network workers. Give each attempt an ID/generation so an old completion cannot overwrite a replacement.
2. Model **pause requested → checkpointing → paused**, rather than immediately claiming durable pause while a worker is still writing.
3. For resumable network transfers, checkpoint valid progress, close resources, finish the attempt with a Paused outcome, and release its queue permit in `finally`. Resuming creates a new attempt from verified state.
4. Prevent paused HLS/DASH child tasks from holding all segment permits. Submit a bounded window and release permits when attempts leave execution.
5. For non-range direct downloads, explain when resume must restart. For page-bound Blob transfers, preserve a bounded cooperative mechanism or explicitly communicate the page-lifetime limit. Do not promise disk-based recovery for them.
6. Remove same-pool busy-wait handoff jobs. Chain replacement after old ownership is released. Ensure deletion/replace cannot remove files still owned by an active writer.
7. Keep completed/failed/canceled status changes centralized and preserve existing elapsed-time semantics.

**State contract to test:**

| Event | Required result |
|---|---|
| Pause active transfer | Verified checkpoint; execution slot released |
| Resume paused transfer | One new owner; verify source before continuing |
| Cancel | No new writes after ownership cleanup; remove only that transfer's artifacts |
| Replace | Old attempt cannot publish or mutate the replacement |
| Worker fails | Actionable error; retain verified resumable work |
| Process restarts | Interrupted items restored conservatively; never silently marked complete |

**Device check:** start A/B, pause both, enqueue C, verify C starts. Resume A, cancel B, and retry a failure. Repeat with direct, HLS, and DASH, then with process interruption.

**Complete when:** paused work cannot starve queued work, and the recovery matrix still passes.

## Step 9 — Correct foreground service and notification behavior

**Start in:** [DownloadForegroundService.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DownloadForegroundService.kt). Depends on Step 8.

1. Drive service lifetime from executing work, not the presence of paused records. Establish foreground status promptly when a genuine transfer starts; do not delay it behind lengthy restoration.
2. When no work is executing, stop the tick and service. If retaining paused notifications, make them ordinary actionable notifications and keep their lifecycle separate from foreground cleanup.
3. Restore lightweight queue state before resolving notification actions after process death. Start heavy verification asynchronously when the requested action requires it.
4. Handle service-start rejection and `onTimeout`: request a bounded checkpoint/stop, release work, and return a recoverable state. Do not block the main thread waiting indefinitely for network I/O. Read the current timeout contract and test hooks before implementing. [Timeout guide](https://developer.android.com/develop/background-work/services/fgs/timeout).
5. Update notifications only when progress/text/state changes; throttle progress but publish pause/failure/completion immediately.
6. Include a download ID/destination in notification intents. Handle both cold launch and `onNewIntent` so a tap opens the correct Downloads item.

**Device check:** all paused for five minutes, resume from notification, dismiss/reopen, process death, denied notification permission, forced service timeout. Removing an item while its notification is tapped must not crash.

**Complete when:** all-paused work has no service/tick, actions survive restart, and timeout produces no ANR/crash or corrupted final output.

## Step 10 — Bound transfer memory and network work

**Start in:** [PausableIo.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/PausableIo.kt), [HlsDownloader.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/HlsDownloader.kt), [DashDownloader.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DashDownloader.kt), and [DirectFileDownloader.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/DirectFileDownloader.kt).

1. Replace unbounded `readBytesPausable` use for media with bounded-buffer streaming to temporary files. Keep cancellation and pause checks at buffer boundaries.
2. Give manifests, keys, and indexes explicit byte limits. Count actual bytes even if Content-Length is missing or false. AES-128 keys must have the expected length.
3. Stream decryption to a temporary output where possible; publish only after successful finalization/validation. Keep all partial outputs out of completed recovery markers.
4. Introduce shared/per-host limits for app-owned downloads, probes, and artwork. Integrate player requests where feasible; WebView's internal networking cannot simply be controlled by this semaphore, so limit competing app work rather than claiming a universal connection cap.
5. Acquire permits without holding a worker/permit that a child operation needs. Release on failure/cancel/pause. Use a bounded segment window instead of enqueuing an entire long playlist.
6. Preserve digest/source validation. Avoid repeated digest scans only when the same verified artifact remains immutable and ownership is proven.
7. Budget space for source payload, decryption/mux intermediates, and final copy. If size is unknown, detect write failures and clean up predictably.

**Tests:** giant or chunked manifest, wrong key size, truncated encrypted segment, canceled copy, server ignoring ranges, thousands of segments, mismatched validators, low storage during mux. Verify final-file hash where a fixed fixture permits it and long-playback audio/video sync.

**Complete when:** memory does not grow with individual segment size or playlist task count, and playback/UI remain responsive during two transfers.

## Step 11 — Make artwork opportunistic

**Start in:** [TabThumbnailCache.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/TabThumbnailCache.kt), [Tab.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt), [LibraryThumbnailStore.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryThumbnailStore.kt), and [BrowserHomePage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserHomePage.kt).

1. Remove Tabs navigation's dependency on the capture callback. Schedule a best-effort capture while the page is still visible, open the overview immediately, and retain the cached preview if capture loses the visibility race. A freshness improvement may never gate the tap.
2. Capture after scroll settling or a meaningful visibility boundary. Permit one capture per tab, coalesce pending requests, and ignore stale generations.
3. Perform disk decode/encode away from the UI thread. Check the tab still exists before publishing results; handle bitmap ownership so recycling never races drawing/encoding.
4. Add byte-sized memory limits, bounded dimensions, negative caching, and in-flight deduplication to favicon loading.
5. Prefer scaled local-video frame extraction. Show a frame already decoded by remote playback first; postpone optional remote midpoint extraction and avoid unbounded queued retrievers.
6. Prune artwork using the repository's retained identities. Keep private previews memory-only and remove them at private-session exit.

**Device check:** rapidly scroll, open Tabs, close the source tab, reopen the grid, and repeat. Start remote playback on a slow connection. Compare first-frame timing with artwork enabled/disabled.

**Complete when:** navigation never waits for artwork, capture queues stay bounded, and playback startup does not regress.

## Step 12 — Cancel stale discovery and reduce filter overhead

**Start in:** [MediaProbe.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/MediaProbe.kt), [StaticMediaDiscovery.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/StaticMediaDiscovery.kt), [VideoSnifferScript.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/VideoSnifferScript.kt), and [FilterEngine.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/adblock/FilterEngine.kt).

1. Return a cancellation handle from probes. Track jobs by tab/navigation generation; on invalidation, remove queued work and disconnect active requests. Keep generation checks as a second defense.
2. Put caps on pending candidates and reporting state. Reserve priority for the visible page and media the user selected.
3. Publish quick verified identity before optional size/duration enrichment. Keep “unknown” values honest.
4. Run deeper scanning only on user request. Measure the cost of automatic HTML refetching; defer/reduce it without replacing normal WebView responses or breaking authenticated page behavior.
5. Use a WeakSet for already-watched media elements; batch DOM mutations and avoid duplicate listeners on reinsertion.
6. Separately, partition filter buckets by rule priority during compilation. Iterate without per-request sorting while preserving exception behavior. Cache cosmetic CSS by hostname and ruleset generation.
7. Gate duplicate blocked-request/page-console logs in release; redact signed URLs from error diagnostics.

**Tests:** navigate away mid-probe, close tab mid-result, repeated DOM reinsertion, repeated manifests, rule exceptions/manual priority, filter refresh while requests run. Compare request counts, allocation rate, and CPU on a fixed busy page.

**Complete when:** old pages stop consuming probe capacity and filter decisions remain unchanged. Treat discovery and filter edits as separate reviewable changes within this step.

## Step 13 — Isolate frequently changing UI state

**Start in:** [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt), [HubHomePage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/HubHomePage.kt), [HubPage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/HubPage.kt), and [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt).

1. Use traces/recomposition inspection to find broad readers of download progress, page loading, and player time.
2. Keep precise counters inside transfer owners; publish visible row snapshots at most every 250 ms. Publish state transitions immediately. Stop unnecessary UI-only sampling when not visible.
3. Move progress observation into the row/indicator; avoid making the whole browser/Hub read every byte change.
4. Extract browser chrome, tab host, permission coordination, download presentation, and player controls along ownership boundaries. Preserve existing callbacks until each extraction is verified.
5. Cache Library search/sort against immutable input revisions, query, sort, and folder. A `remember` keyed only by a mutable list object can become stale; test updates explicitly.
6. Preserve lazy keys, use suitable content types, and restore scroll/query/folder state. Test removal of the currently selected folder/item.

**Complete when:** scrolling with two downloads meets the frame budget and mutations remain visible. Do not add stability annotations to mutable models to hide invalidation problems.

## Step 14 — Finish platform behavior and navigation polish

**Start in:** [MainActivity.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/MainActivity.kt), [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt), [BrowserBackNavigation.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserBackNavigation.kt), and [OverflowMenuSheet.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/ui/OverflowMenuSheet.kt).

1. Configure Media3 media audio attributes with automatic audio-focus handling, plus becoming-noisy handling. Keep existing lifecycle behavior; test that returning to the app does not incorrectly override a focus-loss/user pause decision.
2. Request notification permission at the first relevant download, and legacy storage only where required. Resume the pending action after permission; provide a usable denial/retry path. Keep development permission requests debug-only.
3. Preserve the shared Back resolver. For predictive navigation, do not mutate the tab/navigation stack until the gesture commits; cancellation restores the starting state.
4. Connect More to grouped settings/help/privacy while retaining the current Library/Downloads/More rail. Provide menu alternatives for long-press-only actions.
5. Adapt menu columns and height for small widths and large fonts. Keep at least 48dp targets without clipping labels/actions.
6. Add validated incoming web/shared-URL routing, handling both cold intents and `onNewIntent`. Never interpret a shared URL as permission to auto-download.
7. Verify TalkBack labels/order/state, RTL, keyboard navigation, 200% fonts, landscape/split-screen, dynamic colors, both system navigation modes, and disabled animation.

**Device check:** call/audio interruption, disconnect headphones/Bluetooth, leave/return to player, cancel a Back gesture, dismiss stacked UI, deny permissions, then retry. Perform the core journey using TalkBack.

**Complete when:** platform interruptions and navigation feel predictable and all essential actions remain accessible. Retain the separate player theme.

## Step 15 — Simplify presentation and remove confirmed duplication

1. Recheck usages of [DownloadsPage.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/DownloadsPage.kt). If still unused, remove it and any uniquely unused resources. Do not remove the live Hub transfer components.
2. Share download state labels/grouping between Hub and notifications, while keeping layout differences appropriate to each surface.
3. Keep aggregate progress prominent; place connection/chunk details, deeper scans, filter rules, and failed-video logs in advanced/details surfaces.
4. Retain Library, Downloads, browser Home, bookmarks, and History as distinct concepts. Preserve folder/customization records even if an edit entry becomes less prominent.
5. Link old checklists from the tracker and reconcile conflicting status claims with actual evidence.
6. Leave MSE reconstruction, watch-through caching, source grouping, decorative blur/shaders, sync, and background playback deferred. They are not needed to pass the current release journey.

**Complete when:** every primary destination has a clear purpose, retained features remain reachable, and no data was discarded by cleanup.

## Step 16 — Verify privacy, backups, and hostile inputs

**Start in:** [AndroidManifest.xml](G:/Projects/Miowser/app/src/main/AndroidManifest.xml), [BrowsingData.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowsingData.kt), [PrivateSessionStore.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/PrivateSessionStore.kt), and [RendererResourceDownloader.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/RendererResourceDownloader.kt).

1. List all stored categories and where they live, including shared preferences, WebView site data, recovery payloads, previews, Library artwork, failed-video logs, and signed source URLs.
2. Define backup inclusion/exclusion intentionally, using the rules supported on each Android version. Verify the merged manifest and backup/restore behavior on a disposable profile.
3. Check private entry, normal close, crash, and next-launch cleanup. Ensure the UI states the existing shared-storage limitation and that private downloads persist.
4. Send malformed/oversized/flooded bridge inputs, stale tokens, reordered chunks, and events from navigated-away pages. Require bounded resource use and no unauthorized output creation.
5. Check URL/scheme validation and redirects at browser, probe, player, and download boundaries. Scope credentials to appropriate destinations rather than forwarding secrets indiscriminately across redirects.
6. Remove sensitive release logs and document privacy/help behavior. Verify dependency and bundled filter-list notices.

**Complete when:** the app's privacy claims match observed storage behavior, restore does not resurrect prohibited data, and hostile inputs fail without crashes or unbounded work.

## Step 17 — Generate final profiles and test the release candidate

1. Review dependency updates separately against official release notes; upgrade only with a compatibility reason and focused retesting. The pinned Media3 version deserves review, but a wholesale upgrade is not the same as optimization.
2. Regenerate Baseline Profiles after the interaction paths stabilize. Include startup-only paths in the Startup Profile and broader journeys in the Baseline Profile. Use the template's generated tasks and verify the profile reaches the release artifact.
3. Run unit tests, lint, Android integration tests, and the fixed benchmark suite. Reuse the same compilation mode/device/data when comparing measurements. Report regressions as well as improvements.
4. Run the complete matrix from the source plan on release-like builds, then test the actual signed candidate's installation, upgrade, bridges, playback, and notifications. Preserve signing consistency for an upgrade test.
5. Confirm supported Android versions. API 26–28 runtime checks were previously deferred; do not silently change that decision or claim they passed.
6. Finalize branding, version code/name, release notes, help/privacy information, and channel-specific requirements. Keep production keys out of the repository.

**Complete when:** no unresolved P0 remains, all promised-device behavior has evidence, remaining limitations are recorded, and the actual artifact is identified by version and hash.

## Step 18 — Run beta verification and make the ship decision

1. Prepare a concise tester script covering the core journey plus pause/restart, audio interruption, and private-mode expectations. Use an approved internal distribution channel when the owner is ready to distribute.
2. Collect device/build/scenario, expected result, actual result, and reproduction steps. Ask for redacted diagnostics; do not collect browsing history by default.
3. Fix reproducible crashes, data loss, failed core journeys, and material performance regressions. Re-run affected checks and the core smoke test; avoid unrelated feature work.
4. Complete the tracker's release gate. Record pass/fail evidence and any accepted limitation with an owner.
5. Prepare the release artifact and listing for review. Publication is a separate release action; this guide does not publish anything.
6. After a release, review crash/ANR/performance feedback through the chosen distribution tooling and maintain a recoverable previous source/build checkpoint. No automatic monitoring has been scheduled by this guide.

**Complete when:** the release owner can make a ship/no-ship decision from concrete evidence, not a list of compiled features.

## Command and verification guide

Run these from a PowerShell terminal in `G:\Projects\Miowser`. The JDK path below exists on the audited machine; use Android Studio's configured compatible JDK if its location changes.

```powershell
Set-Location -LiteralPath 'G:\Projects\Miowser'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Gradle may need network access the first time dependencies resolve. Use `--offline` only after they are cached. The prior offline lint attempt failed because lint dependencies were missing, not because analysis passed.

For a targeted unit-test iteration, use the real fully qualified test class you changed:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.TabSessionCodecTest'
```

After adding Android integration tests and connecting a disposable test device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Use a selected device/serial when multiple devices are attached. These are correctness tests, not performance numbers. Ordinary development runs can use incremental results; use `--rerun-tasks` when a genuinely fresh execution is needed for an audit.

After adding the Baseline Profile module, inspect the generated tasks rather than assuming a device/variant name:

```powershell
.\gradlew.bat tasks --all
.\gradlew.bat :app:generateBaselineProfile
```

Run the benchmark configuration supplied by the module on the reference physical device. Profile generation itself is not a before/after benchmark. Final packaging checks, once signing/variants are configured:

```powershell
.\gradlew.bat :app:lintRelease :app:assembleRelease :app:bundleRelease
```

Assembling an unsigned release is not proof it installs; generating a bundle is not uploading or publishing it.

Reports normally appear in [unit test results](G:/Projects/Miowser/app/build/test-results/testDebugUnitTest), [debug lint report](G:/Projects/Miowser/app/build/reports/lint-results-debug.html), and the benchmark module's generated result directory. Record actual paths in the tracker because reports can be overwritten.

### How to test process interruption correctly

Use a disposable profile. Start work, reach a known checkpoint, interrupt the process, relaunch, and verify both UI state and actual output. Test each interruption class separately: ordinary background process death, explicit force-stop followed by user relaunch, activity recreation, and WebView renderer termination. Force-stop suppresses background behavior and is not an accurate substitute for all operating-system process death. “Don't keep activities” tests activity lifetime, not download process recovery.

For failures, preserve the fixture, checkpoint, build identity, and redacted error before retrying. For performance failures, preserve the trace and measurement conditions. Revert only the isolated change under investigation; never reset the whole working tree to undo a failed experiment.
