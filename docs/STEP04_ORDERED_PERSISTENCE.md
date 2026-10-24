# Step 4 — Ordered background persistence

Implemented in the existing `G:\Projects\Miowser` checkout. This step removes UI-thread encoding and disk commits from the core app indexes while preserving their existing codecs and preference keys. It does not claim a measured scrolling improvement: Step 3's physical-device findings remain the baseline.

## Ownership and durability

- Library, folders, History, bookmarks, speed dials, inactive tabs, settings, and the download list have a main-thread mutation owner. Public lists expose read access; mutations go through repository methods. Guards catch accidental off-owner edits.
- Each save copies immutable records before submission. Download workers post back to the list owner, which captures a Compose read snapshot; the disk worker never iterates the live UI list or mutable Download objects.
- `OrderedWriter` uses one process-lifetime executor for encoding and `SharedPreferences.commit()`. Accepted writes are FIFO. The caller receives a completion receipt only after the disk operation reports success. A failed latest snapshot remains retryable; retries cannot replace a newer snapshot with stale data.
- Pending edits show “Saving changes…”. Failures remain visible with Retry. Confirmations for core user edits wait for successful persistence; a failed confirmation remains pending through retry. Changes still waiting for a successful write are not guaranteed to survive process death.
- Bulk Library removal, restore/Undo, folder moves, download removal, and resume-all produce one index save per operation. Clearing download records no longer commits the whole list once per removed row.
- A new download's initial record is committed before worker dispatch. Completed downloads show “Saving download record…” until their completed record commits; the foreground notification remains during that boundary. Cleanup waits for the old worker off-main, and worker dispatch is serialized with cleanup so a queued deletion cannot erase a new attempt's recovery files.
- Tab sessions save on meaningful foreground changes as well as pause. Private tabs remain excluded. Repeated restore no longer reloads stale persisted speed-dial state over outstanding edits.
- No database migration or new storage format was introduced. Related indexes still use separate existing preference files: confirmations wait for their writes, but this does not create an atomic transaction across multiple files.

## Store inventory and boundaries

| Data | Owner / callers | Write policy after this step | Read and follow-up boundary |
|---|---|---|---|
| Library and folders | Main owner; Hub and player callbacks | Immutable records → ordered background commit | Initial hydration still synchronous; Step 5 |
| History and bookmarks | Main owner; WebView visit callbacks and UI | Immutable records → ordered background commit | Existing legacy codecs retained; initial restore in Step 5 |
| Download presentation index | Main list owner; UI, service, worker completion messages | Owner snapshot → ordered background commit; explicit completion acknowledgement | Recovery inspection during startup remains Step 5 |
| Download recovery plans, segments, payloads | Transfer worker and recovery-store synchronization | Existing durable worker-side checkpoint protocol retained | Scheduler/transfer lifecycle remains Steps 8–10; remote-network failure matrix still required |
| Download cleanup | Maintenance executor waits for worker; ordered writer owns deletions | Off-main, generation-checked, failure-aware | Does not solve the separate paused-worker scheduler limitation |
| Tab session | Browser's main owner, foreground snapshot observer, pause checkpoint | Snapshot excludes private tabs; ordered background commit | WebView creation/lazy restore remains Step 6 |
| Inactive tabs and speed dials/folders | Main owner | Ordered background commit of copied records | Restore is idempotent; existing codecs retained |
| App settings, Hub settings, menu order | Main UI, immutable settings/order values | Ordered background commits | Small startup reads retained; cosmetic writes currently ordered, not coalesced |
| Failed-video log | Existing lock, process cache | Copied records → ordered background commit | First read remains synchronous; later reads use the current process snapshot |
| Filter metadata and filter bodies | Existing filter executor + storage lock; some metadata UI callers | Existing subsystem unchanged | UI metadata commits/cache access remain synchronous; filter publication and cancellation belong to Step 12 |
| Private-session crash marker and permission clearing | Browser main owner / privacy completion callbacks | Existing synchronous security boundaries retained | Do not replace these with fire-and-forget saves; explicit asynchronous privacy handshake belongs to Step 16 |
| Per-site decisions and player track preferences | Existing small preference wrappers | Existing `apply()` behavior retained | Not part of the migrated list-index path; review with Steps 14/16 |
| Thumbnail/preview files and derived file-inspection caches | Existing thumbnail/cache workers and UI reads | Existing derived-cache policies retained | Nonblocking artwork and cache access belong to Step 11 |

The remaining synchronous paths above are intentional scope boundaries, not a claim that all app disk I/O has been eliminated. Debug builds enable StrictMode disk-read/write logging to expose those paths during subsequent work. Startup hydration remains unchanged until Step 5.

## Verification

- Eight new JVM tests exercise delayed writes, commit receipts, failure visibility, retries, stale-snapshot exclusion, a barrier followed by a later submission, exception isolation, and reopening a committed clear. The existing codec tests continue to cover old records.
- A benchmark-only activity exercises removal of 400 of 500 Library records, Undo of 20, a bulk folder move, repeated restore while saves are outstanding, two History edit/clear sequences, bookmark add/remove, removal of 50 of 200 download records, and rapid setting edits.
- Main-thread disk writes are forbidden with StrictMode during that edit sequence. A synthetic local transfer then exercises worker completion, owner snapshot, durable commit, and the completion flag. The test force-stops the isolated app after the saved acknowledgement and verifies 120 Library records, the folder mapping, latest History title/count, one bookmark, 151 downloads including the completed local record, and the final setting after restart.
- The probe and its exported activity exist only in the separate `com.miowser.app.benchmark` variant. The normal debug/release application contains no probe endpoint and the user's ordinary data is not seeded or cleared.
- Final verification passed: **310 unit tests, zero failures/errors/skips**; debug lint **0 errors, 49 warnings, 3 hints**; benchmark lint **0 errors, 56 warnings, 3 hints**; debug and optimized release assembly successful.
- All **six emulator checks passed** on the final code: the process-restart durability probe plus populated startup, Library scrolling, Downloads scrolling, browser/tab switching, and local-player first-frame readiness. The foreground dispatch/cleanup handoff change was included in this final run.
- Merged debug/release manifests contain neither benchmark activity. Both normal APKs exclude the fixture MP4. The release APK contains its compiled baseline profile; regenerated performance profiles and physical measurements should be revisited for the final release candidate.

### Test APK

[Miowser-0.1.0-step4-debug.apk](G:/Projects/Miowser/Miowser-0.1.0-step4-debug.apk) — 25,042,702 bytes, debug signed, normal `com.miowser.app` package. The earlier `Miowser-0.1.0-debug.apk` is retained; use the explicitly named Step 4 file for these changes.

SHA-256: `0DD2701EE60FF721AE4736E2146EA635287F584E10FBDD363DF674BCA38C6E40`.

The successful combined verification log, unit XML, device output, lint reports, artifact hashes, and source checkpoint pointer are in `.local/optimization/evidence/step04/`. No commit or publication was performed.

Evidence: `.local/optimization/evidence/step04/`. The one-iteration app journeys are functional smoke checks, not a replacement for controlled physical-device performance measurement. Physical download interruption, sustained transfer, and user acceptance remain open.

## Reproduce

1. Build/test with Android Studio's bundled Java: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
2. Connect only the intended disposable test device. Run `:benchmark:connectedBenchmarkAndroidTest` with runner class `com.miowser.benchmark.PersistenceDurabilityTest` and `-Pbenchmark.r8=false`. This test clears only the benchmark package.
3. For app smoke coverage, run `com.miowser.benchmark.AppBenchmarks` with one iteration. Emulator runs require the explicit `androidx.benchmark.suppressErrors=EMULATOR` diagnostic flag; the durability test itself does not suppress any benchmark guard.
4. Install the rebuilt debug APK to check ordinary user flows. Add/remove bookmarks, edit folders/settings, remove multiple records, and restart after saving has finished. Confirm that a download does not announce completion before its record is saved.
5. Continue with Step 5's asynchronous startup work. Revisit the Step 3 physical measurements after the owning optimizations are implemented; do not infer a speed gain from passing these correctness checks.
