# P1 performance and reliability pass

Date: 2026-10-09. Scope: items **3–11** of the [performance audit](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md), on top of the existing P0 changes and the user's working tree.

No emulator, device, installation, recording, or runtime benchmark was run. The browser's normal budget is **6 live pages**, as requested, and **3 on low-memory devices**. These are initial limits to assess on the user's device.

## Implementation

| Item | Change and expected benefit |
|---|---|
| 3 — Download capacity | Pause disconnects native requests and yields the transfer at a checkpoint. Queued tasks and preparatory coroutines can be canceled. A new attempt waits for the previous owner and its segment children to finish; rapid Resume cannot start a second owner. This frees download workers for other queued files. |
| 4 — Bounded transfers | HLS media and AES-128 decryption stream through 64 KiB buffers. Metadata reads are bounded; AES keys must be 16 bytes. HLS/DASH use up to four worker tasks per transfer, rather than queuing a task for every segment. Eight native connections are shared across transfers, discovery, and playback/prefetch; background work can occupy at most six, leaving playback capacity. Existing segment digests are reused within an attempt; elapsed-only recovery updates are sampled to one second. Durable segment markers and range recovery remain. Local concatenation, muxing, publication, and retries check pause/cancel. |
| 5 — Browser memory | Least recently used inactive WebViews retire at the 6/3 budget and under memory pressure. The visible page, active media, page-backed transfers, loading pages, popups, and pending browser callbacks are protected. Dormant navigation is process-local and capped at 128 KiB per tab / 1 MiB total. Switching back restores bounded saved navigation or reloads the URL. |
| 6 — Player cache | Cache initialization and expiry maintenance run off the UI thread before transport attachment. Prefetch consults actual cached spans; partial resources resume after a cap and eviction makes resources eligible again. Candidate plans are reused across nearby passes. Seek, track changes, network changes, and lifecycle stop cancel in-flight work and connection waits. A canceled range probe does not suppress later prefetch. Active cache sessions prevent expiry during an immediate reopen after long playback. |
| 7 — Saved subtitles | Valid SRT and VTT files receive durable private Library copies. Old VTT filenames remain readable; backup version 2 accepts both formats. A format replacement removes the old copy. Subtitle style changes remain independent of file loading; millisecond delay is preserved. |
| 8 — Backup consistency | Settings are copied as immutable values on their owner; JSON conversion and file staging run on IO. Coordinated asset staging is checked against subsequent preference values and a write revision; a busy changing dataset fails clearly after three attempts instead of publishing a mixed snapshot. Only staged files enter the archive. Restore writes and verifies a private durable journal before applying data, fences late persistence/asset writes, and replays an interrupted generation before repositories load on the next startup. Storage is checked before staging. Only regenerable artwork may be omitted to meet existing limits, and the omission count is shown. |
| 9 — Discovery work | Tab/document generations own probe handles; navigation cancels stale requests and static crawls. Detection indexes, URLs, bridge messages, queued probes, crawl pages, and results have explicit budgets. Passive bridge reports batch on a 100 ms window. WeakSet listener tracking prevents duplicate element listeners; document tokens reject reports from retired pages. Explicit probes take queue priority. |
| 10 — UI publication | Workers keep exact counters and publish progress at most four times per second, with status changes flushing immediately. Download rows and notices share a presentation model. External subtitle timing updates the native subtitle view only when cues change, without a 50 ms Compose clock in the main player. Timeline and subtitle polling suspend below STARTED. Save-on-pause/exit remains. |
| 11 — Service lifecycle | Cold-process notification Resume restores records asynchronously after establishing the required foreground notice. Platform data-sync timeout requests coordinated checkpoints and waits for owned work to relinquish resources before stopping. Ordinary service teardown also checkpoints owned/queued work. Unchanged notification presentations are skipped; meaningful elapsed-time changes still update. Paused notices remain dismissible. |

## Practical limits

- Expected improvements follow from eliminating work and bounding resources; **no measured speedup or frame-rate improvement is claimed**. Use the comparison checklist below to validate on the same device and workload.
- The WebView budget can temporarily be exceeded by protected pages. Loading/media/callback state changes trigger another retirement check. A retired page may reload, and navigation larger than the saved-state budget falls back to the current URL. Private navigation stays process-local and is not added to backups.
- Chromium's internal sockets are outside the native connection budget. Playback receives reserved capacity and priority over waiting background work; this is not a global scheduler for every network request in the app.
- Wi-Fi retains the requested **15 minutes ahead / 5 minutes behind** target; mobile uses **5 / 5 minutes**. Adaptive stream prefetch uses selected-track segments. Progressive-file time-to-byte mapping is approximate. Live streams, servers without valid byte ranges, changing credentials, available space, connection speed, and the existing cache size cap can prevent reaching the full target.
- Existing cache limits remain: at most **768 MiB**, reduced according to free space at initialization; prefetch stops below **96 MiB free**. Resource pieces are capped at **12 MiB** and checked against actual cache spans. The progress bar continues to show Media3's playable buffer, rather than presenting arbitrary disk bytes as playable time.
- Cache expiry remains ten minutes after closing a video, with reopening renewing its lifetime. In-process maintenance and WorkManager cooperate; Android may delay background cleanup, and opening the cache also reconciles expiry.
- Verified range/segment downloads can resume saved work. Non-range streams and renderer-generated blob/data transfers may restart their incomplete body; they do not publish a partial finished file. Renderer transfers still require their originating live page.
- Archive limits remain **1,500 entries, 32 MiB per entry, 256 MiB total**. Settings, filters, and subtitles are required; excessive required data fails rather than silently dropping it. Artwork omissions are explicit. Version 1 archives remain readable.
- An interrupted restore replays the verified journal before app data is exposed. Recovery is exceptional synchronous startup IO and can delay startup for a large archive. If replay fails, startup fails instead of exposing partial restored data; resolve the storage/access condition and reopen. Restored data stays fenced until the process closes. Public downloaded media is outside the restore transaction.
- Backup exclusions remain history, tabs, private browsing, cookies/login data, transfer recovery pieces, and video payloads. Custom backup permissions and schedules remain device-specific.

## Local verification

Final verification passed over the settled source. The last run included:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --offline --no-daemon --console=plain --max-workers=1
```

| Check | Result |
|---|---|
| Full debug unit suite | **428 tests**, zero failures, errors, or skips; 30 more than P0. |
| Debug lint | **Zero errors**, 81 warnings, 5 hints. |
| Release lint | **Zero errors**, 82 warnings, 5 hints. |
| Debug APK | Passed; locally signed installable artifact. |
| Release APK | Passed with R8/resource shrinking; unsigned, not the device-check artifact. |
| Browser discovery JavaScript | Node syntax and mock-DOM smoke checks passed: listener deduplication, activity tracking at the cap, and URL bounds. |

Warning counts match P0. Its three documented WebKit constructor `MissingOnRenderProcessGone` false positives remain unsuppressed; no lint checks were disabled. Unit and build checks validate the local mechanisms and packaging, not device codecs, browser callbacks, platform service timing, or measured performance.

[Debug lint](G:/Projects/Miowser/app/build/reports/lint-results-debug.html), [release lint](G:/Projects/Miowser/app/build/reports/lint-results-release.html), [unit tests](G:/Projects/Miowser/app/build/reports/tests/testDebugUnitTest/index.html). Generated reports can be replaced by later builds.

Device artifact: [Miowser-0.1.0-p1-debug.apk](G:/Projects/Miowser/Miowser-0.1.0-p1-debug.apk), **27,521,783 bytes**.

SHA-256: `9547588FBE284D085DB393C3D82B7EEC65BE2B9DDFEBD324A02D1A6031623175`.

Build log: [.local/optimization/p1-complete-verification.log](G:/Projects/Miowser/.local/optimization/p1-complete-verification.log).

The expanded unit suite covers checkpoint release and rapid Resume, bounded worker submission and child completion, metadata limits, exact progress publication, reserved connection capacity and canceled waits, WebView LRU selection, disjoint/evicted cache spans, streamed AES decryption, SRT detection and backup round trips, version 1 compatibility, artwork budgets, restore replay after partial application, and late-writer fencing.

## Device checklist

Run the functional checks with the P1 debug APK. For performance comparisons use equivalent release builds with the same signing/configuration, data, WebView version, device settings, thermal state, network, and workload. A debug-vs-release comparison is not useful performance evidence. Back up useful app data before deliberate process interruption tests.

### A. Download scheduling, recovery, and memory

- [ ] Start downloads A and B, pause both, then enqueue C. C starts while A and B remain paused.
- [ ] Pause a third queued download before it starts. No request starts for it until Resume.
- [ ] Rapidly alternate Pause/Resume on a large ranged MP4, HLS, and DASH download. One record and one owner remain; progress and the completed file are correct.
- [ ] Cancel a queued download, a running ranged download, and a paused download. None resumes later or leaves a newly published partial video.
- [ ] Retry a failed connection while automatic retries are pending. There is no duplicate transfer or duplicate Library/Downloads row.
- [ ] Pause and reopen the app; resume direct ranges, HLS, and DASH from verified recovery data. Elapsed active time excludes the pause.
- [ ] Use a server that ignores ranges. Resume safely restarts the incomplete body without appending duplicate bytes.
- [ ] Download long HLS and DASH playlists with thousands of segments. Memory and queued-task counts do not grow with one task per segment.
- [ ] Download AES-128 HLS with explicit IVs, sequence-number IVs, and key changes. The completed video plays through boundaries with working audio.
- [ ] Test a malformed/oversized manifest and a wrong-length encryption key. Failure is clear, no partial file is published, and a valid next download still starts.
- [ ] Interrupt during concatenation/muxing/publication. Resume rebuilds safely; cancellation removes the incomplete output.
- [ ] Corrupt a saved recovery segment or change the remote file between attempts. Validation rejects unsafe reuse; a resulting completed file is not silently corrupt.
- [ ] Run two downloads while playing a stream and detecting media in several tabs. Playback can obtain native connections; cancellation does not leak capacity.
- [ ] Test a page-generated blob/data download: pause/resume with the originating tab live; then navigate/reload that page. Renderer loss fails clearly and never hangs the queue.

### B. Browser pages and memory pressure

- [ ] Open 20–30 idle normal tabs, visit them in sequence, and return to the most recent six. Recent eligible pages stay live; older pages reopen correctly.
- [ ] Reopen a retired tab after several navigations and exercise Back/Forward. Small navigation histories restore; oversized histories safely reload the current URL.
- [ ] Leave media playing in a background page while opening other tabs. That page is protected until playback stops.
- [ ] Start a page-backed download and open additional tabs. The originating page remains available while the transfer requires it.
- [ ] Keep a file picker, geolocation prompt, browser permission, authentication/certificate prompt, or popup open while changing tabs. The pending callback still belongs to the right live page.
- [ ] Background/foreground the app and apply controlled memory pressure. Idle pages retire, the visible page survives, and foreground operation returns to the normal budget.
- [ ] On a low-memory device, confirm the initial three-page budget. Protected pages may temporarily exceed it.
- [ ] Open private tabs, retire/reopen one within the process, then end the private session and restart. Private navigation does not enter normal session restore or backups.
- [ ] Repeat the existing P0 renderer-termination recovery check. The browser can reload after renderer death and stale page callbacks do not affect the replacement page.

### C. Discovery and browser handoffs

- [ ] Navigate rapidly among pages with embedded videos. Only the current document's results appear; old probes/crawls stop adding detections.
- [ ] Use a page that repeatedly inserts/removes the same video element. Detection updates do not multiply listeners or duplicate entries.
- [ ] Use a busy page with many media URLs; explicit Inspect/check still works while passive work is queued.
- [ ] Confirm authenticated detection preserves User-Agent, Referer, Origin, and appropriate cookies through valid redirects.
- [ ] Regression-check media counter layout, Select text from linked elements, download snackbar Stream, and Add to Library without playing.

### D. Playback, buffer, and subtitles

- [ ] Open a remote video with a cold cache. The loading screen remains responsive; Close/Back during preparation leaves no lingering session.
- [ ] On Wi-Fi and mobile, watch long direct, HLS, and DASH videos; inspect network/cache behavior against the respective target windows.
- [ ] Seek forward/back repeatedly and change quality/audio. Stale prefetch cancels; current segments can be requested promptly.
- [ ] Switch Wi-Fi → mobile → offline → Wi-Fi during playback. Buffer targets adjust without a stale request blocking later work.
- [ ] Use an individual segment/resource over 12 MiB. Later passes continue uncached spans; the resource is not marked complete after one capped piece.
- [ ] Cause cache eviction by playing enough large files, then reopen/seek into an evicted section. Missing data is eligible for fetching again.
- [ ] Reopen the same video within ten minutes and after expiry. Reuse/renewal and eventual idle cleanup work; public downloaded files are unaffected.
- [ ] Test low free space and a server without reliable range support. Playback still works while aggressive prefetch stops safely.
- [ ] Confirm the played/buffered progress layers align, never imply uncached time is playable, and the timer's elapsed/remaining toggle remains correct.
- [ ] Play Library videos with saved VTT and SRT tracks; reopen after the remote subtitle URL expires. A previously saved valid subtitle copy still plays.
- [ ] Change size, font, outline, background, opacity, position, and millisecond delay while subtitles play. They remain visible across changes and seek/track transitions.
- [ ] Pause, background, and resume playback. Subtitle/timeline clocks stop while inactive and recover without unrelated title/control flashing.
- [ ] Regression-check playlist order, next-video controls, gesture seek target/delta, buffering spinner, soft shadows, disabled actions, and top/bottom control gradients.

### E. Backup consistency and restore

- [ ] Back up while Library/settings edits and artwork generation occur. The result is coherent or reports that data kept changing; no mixed archive is reported as successful.
- [ ] Restore a new archive containing settings, Library/folder rules/default folder, bookmarks, speed dials, site settings, filters, saved video details, and VTT/SRT copies.
- [ ] Restore a valid existing version 1 archive. Its allowlisted contents and preference values remain compatible.
- [ ] Verify history, open normal/private tabs, cookies, login data, and video payloads are absent from the archive.
- [ ] Use enough regenerable artwork to reach archive budgets. The omission count is visible; required subtitles/settings/filters remain, and omitted artwork can regenerate.
- [ ] Test oversized required data, low private storage, a corrupt/truncated archive, and revoked backup-folder access. Failure is clear and no false successful backup is shown.
- [ ] Pause downloads immediately before Restore. If owners are still stopping, Restore asks to retry; once quiescent it proceeds safely.
- [ ] Make a controlled process interruption before journal readiness, during private file replacement, and during preference application. Restart exposes the old generation before readiness, or replays the complete new generation after readiness.
- [ ] Ensure late queued record/thumbnail/filter writes cannot overwrite restored data. Close and reopen after Restore; the restored values remain.
- [ ] Verify custom backup destination, preset interval, automatic retention, manual archive retention, and explicit artwork-omission reporting.
- [ ] Confirm Restore and app uninstall do not delete public user-downloaded media; after reinstall, relink a public file if Android requires access again.

### F. Notifications and service lifetime

- [ ] A running download has its active notice. Pause makes the notice dismissible; dismissing it leaves the paused download in the app.
- [ ] Pause all downloads. Foreground service ends and paused notices remain ordinary/dismissible; resuming starts foreground work again.
- [ ] Dismiss paused notices, restart the process, and resume from the app. Dismissal state does not prevent a new running notice.
- [ ] Resume a paused notice after cold process start. A preparing notice appears promptly, records load asynchronously, and only the requested download resumes after validation.
- [ ] Deny notification permission, then retry/pause/resume in the app. Queue ownership and completed output remain correct.
- [ ] Use a controlled data-sync timeout on a supported Android version. Running, queued, and retrying/preparing work checkpoints; no orphan native worker continues after service shutdown. Resume later works.
- [ ] Check row/notification percentages and timing agree. Status transitions appear immediately; a stable paused notice does not continually repost.

### G. Performance comparison

- [ ] Compare release cold startup, cold remote-player entry, and revisiting many tabs against the P0 build, with equal data and conditions.
- [ ] Compare memory during long encrypted HLS/DASH downloads and 30-tab browsing; confirm lower retained allocations/live WebViews without losing protected work.
- [ ] Compare Library/Downloads scrolling and player control interactions during concurrent transfers. Record frame deadlines or a trace rather than judging only by appearance.
- [ ] Check progress publication stays at four per second or less between immediate state changes, and external subtitle cue changes do not recompose the entire player every 50 ms.
- [ ] Record throughput, startup timing, frame misses, peak memory, free space, network type, device model, Android/WebView versions, power-saving state, and thermal state with any feedback.

P2 optimization and the separate animation plan remain future work.
