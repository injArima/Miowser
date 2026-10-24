# Library and Downloaded inspection cache

Date: 2026-10-12. The Futurice audit plan is deferred; this change addresses repeated work and visual churn on Library/Downloaded entry.

## Findings

Remote Library streams were already displayed without network availability preflights. Local Library cards reopened file/content URIs for availability each time they composed. Downloaded cards separately reopened descriptors, resolved types/dimensions and started preview/icon jobs. Some inspection caches survived only within the process. Unknown results initially showed Checking labels/placeholder icons and then changed after the jobs completed.

Library also recreated its worker projection with an initial Loading state on destination return. These source mechanisms can cause unnecessary work and visible churn; no device trace was taken to attribute every missed frame to them.

## Changes

- Local availability, file type, metadata including known zero/no-video results, and thumbnail inspection/attempt results now have a bounded persistent cache with a process mirror.
- Facts are loaded off-main before startup displays the Hub. Warm cards use cached initial values, avoiding repeated Checking states while identical results return.
- Uncached availability work waits 250 ms and defers during scrolling. Type, dimension and artwork work waits for availability and scrolling to settle. Only composed lazy cards perform inspection; there is no whole-Library link-validation sweep.
- APK icons now have a small PNG cache, so a process restart can decode an icon instead of parsing/copying the APK again. Memory hits remain immediate.
- One lightweight Library projection is retained across Hub destinations. Repository revision, folder, sort and query must match before reuse; data changes still publish fresh results. It retains immutable records, not grids, bitmaps or collectors.
- Play/Open performs a fresh local availability check, using separate locks from APK icon parsing so artwork cannot hold up validation. A deleted file or revoked grant cannot be trusted solely because a cached success exists. Remote playback remains an actual playback attempt rather than a misleading link preflight.
- Relinking and completed download writes invalidate facts for the affected source. Relinking a Library entry also resets derived quality/thumbnail references for the new URI. Failed checks and failed extraction attempts are cached briefly instead of repeatedly retried on every return.
- A cache namespace is checked off-main on Hub resume. Clearing the cache retires its old facts and in-flight producer tickets, clears APK memory and refreshes cards. Late index writes cannot recreate cleared facts from an old generation.
- Writes coalesce for 250 ms on a dedicated worker and replace a bounded binary index. Corrupt/oversized indices safely rebuild. Cache write failures do not fail startup or delete user data.

## Storage and freshness

The new data lives under **`context.cacheDir/media_inspections`**. Android's app-info **Clear cache** removes the inspection index and cached APK icons. Use **Settings → Apps → Miowser → Storage & cache → Clear cache**; wording varies by device. **Clear storage/data** is a different action that deletes app records.

Successful facts live up to **24 hours**; unavailable/failed results retry after **five minutes**. Results can also be evicted or invalidated sooner. Clearing cache deliberately causes one fresh inspection pass for visible items; it is not a reason to recheck the entire collection.

The index holds at most **2,048 facts**, with an estimated 2 MiB memory budget and a 4 MiB disk-read cap. APK PNGs are limited to **64 files / 4 MiB**, plus the existing small memory cache. SHA-256 source keys share a bounded digest cache to avoid repeated UI-thread formatting/allocation.

These are advisory cached facts. External changes to the bytes behind an unchanged URI may leave nonessential displayed metadata stale until expiry, invalidation or cache clearing; actual local availability is checked on opening. Cache persistence is best-effort and regenerable. Existing Library records, subtitles, folders and user-downloaded media are not stored in this cache or removed by clearing it. Existing managed Library/download-preview images remain in their separate asset store; the new cache stores their inspection results/references.

## Local verification

Final verification completed with **503 JVM tests passing**, zero failures/errors/skips, and **zero lint errors** (80 warnings and five hints remain). Debug packaging and the R8 unsigned release build succeeded. Scoped whitespace checks passed; packaged DEX files contain the new cache/projection implementation. The source manifest covers 323 files and was rechecked after packaging. The requested automatic audio-focus handling remains disabled.

Command: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`. Final build completed in 2m 46s. Results, hashes and the task-scoped before/after source diff are saved under `.local/optimization/library-cache-2026-10-12-*`.

New JVM coverage exercises restart persistence, positive/negative expiry, known-zero metadata, namespace/cache clearing, stale producer invalidation, independent facts, malformed/truncated/oversized indices, entry/byte budgets, stable cache keys and Library projection identity/revision rules. These tests do not measure device frame performance or exercise Android content providers.

No emulator, device, ADB, instrumentation or on-device benchmark was run. Evidence: `G:/Projects/Miowser/.local/optimization/library-cache-2026-10-12-*`.

## APK

[Installable debug APK](G:/Projects/Miowser/Miowser-0.1.0-library-cache-debug.apk), 27,287,823 bytes.

SHA-256: `1d0a9fc417690ef0dd0d3caec8014fb476565677f824a0a904df6ed2245fd550`.

The release build is unsigned and retained as build evidence; use the debug APK above for the manual checks.

## Device checklist

- [ ] Open a populated local Library once, wait for visible results, then switch Downloads → More → Library repeatedly. Cached cards should not repeat Checking states or briefly replace the grid with Loading.
- [ ] Repeat with a selected primary/subfolder, active search and each sort. Folder, count, query and ordinary destination scroll restoration remain correct.
- [ ] Switch Downloading/Downloaded repeatedly with videos, audio, APKs, documents and opaque `.bin` names. Types, quality, icons and previews return consistently.
- [ ] Restart the app after the first scan has settled. Cached facts and APK icons should be reused across the new process.
- [ ] Fling quickly through uncached cards. Scrolling remains responsive; pending inspection/extraction waits for settling or is cancelled when cards leave.
- [ ] Add, remove, undo, move and reorganize videos using rules. The projection follows current data rather than showing an old cached list.
- [ ] Complete a new download, including replacement of a previously used output. Its new file is inspected rather than inheriting stale facts.
- [ ] Delete a previously verified local file externally, then tap Play/Open. Missing-file/Locate handling appears, without opening an unusable cached success.
- [ ] Revoke and restore a document grant. Tapping revalidates access; relinking updates the source and rebuilds derived metadata/preview.
- [ ] Clear cache through Android app settings and return. Visible facts regenerate once; Library records, folders, subtitles and downloaded media remain intact.
- [ ] Repeat cache clearing after scrolling and during a recently started inspection. No stuck state, corrupt-index error or old result repopulation occurs.
- [ ] Test a video without dimensions, an audio-only file, an unsupported file and a failed thumbnail/icon extraction. Returning within five minutes does not continuously re-extract failed results.
- [ ] Recheck remote MP4/HLS/DASH and signed/cookie-dependent Library streams. Page entry does not preflight their links; playback still uses the real link/session.
- [ ] Recheck VTT/SRT persistence, millisecond delay/style edits, playlists and playback during calls with the requested automatic audio-focus handling disabled.
- [ ] Recheck record-only deletion, delete-file-and-record, backup/restore, paused notifications and ordinary downloaded-file opening.
- [ ] Compare matched warm/cold release-equivalent page switches and scrolling. Record frame deadlines, first response, CPU/memory and thermal state; report any remaining jitter with the exact view/action. Source changes and JVM/debug success do not prove a measured frame-rate gain.
