# P2 performance optimization — implementation and verification

**Follow-up, 2026-10-12:** automatic player audio-focus handling was disabled at the user's request. The original focus behavior and checklist item 41 below describe the October 9 build; the current policy and checks are in [Player audio-focus change](G:/Projects/Miowser/docs/PLAYER_AUDIO_FOCUS_CHANGE_2026-10-12.md).



Date: 2026-10-09. Covers items **12–16** of the [dated audit](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md). P0/P1 behavior is preserved. The assistant runs local source, unit, lint, and build checks only. Device acceptance and regenerated profiles remain with the user.



## Delivered changes



| Item | Change |

|---|---|

| 12 — Persistence | Complete immutable store snapshots coalesce only while queued. Commands, file operations, retries, and barriers retain ordering. Superseded receipts wait for their replacement to confirm durability. Player style changes preview immediately and save after a 250 ms edit pause, with lifecycle/exit flushes. Brightness gestures save at completion. Track/display, permission, filter configuration, and private-session marker changes use ordered durable background commands. Filter Undo follows committed removal; parsing/reload completion stays separate from saving. Private entry waits for its saved crash marker. Backup preference commits and scheduling run off-main. Initial player/permission preference reads warm on IO. Queue execution/coalescing/peak depth and Library encoded-payload counters are available for diagnosis. |

| 13 — Artwork | Visible thumbnail decode has two separate workers, apart from two extraction workers and one maintenance worker. Obsolete queued card decoding/extraction is cancelled. Bitmap bounds determine sampling before allocating pixels; invalid/excessive source dimensions are rejected. Existing 640×360, JPEG-86 Library artwork remains. Thumbnail memory stays byte-weighted at 24 MiB; favicon/APK image caches use 2 MiB budgets. Metadata/type caches include key weight and cap entry count. Concurrent per-media metadata reads share results; failed extraction receives a five-minute retry cooldown. Streams retain the already-decoded player-frame fallback. |

| 14 — Library | Rule phrases, folder relationships, and domain lists compile when rules/defaults change; each video's required titles/hosts parse once per route. Bulk reorganization computes on a background dispatcher, checks Library/folder revisions, and applies one snapshot. Changed inputs cause recomputation; repeated conflicts report a retry message. Website priority, whole words/phrases, punctuation/case handling, AND specificity, manual moves, and default/parent scope are preserved. Explicit saves and manual placements survive the separate 500-item recent-play eviction. Legacy records migrate as saved rather than guessing intent. New protected saves have an explicit 5,000-item supported limit and report capacity instead of deleting another save. Deletion/replacement schedules private-asset cleanup after a durable save and a 60-second undo window, with revision guards. |

| 15 — Filters | Exact packed five-character ASCII tokens replace URL-window substring objects. Primitive indexes still verify the entire network rule. Exceptions/manual tiers, anchored domains, Unicode and long-URL fallback remain. Concurrent builds share a future only when both fingerprint and filter-input revision agree. Filter mutations invalidate an in-progress build even if selections change and then change back; publication validates inputs atomically with installation. Unrelated Library saves do not invalidate filter work. Disposable compiled cache advances to v4. Audited P1 algorithms are retained only in the test source for equivalent-input comparisons. |

| 16 — UI/platform | Player session owns construction, cancellation, and release after listeners/progress are handled. Media audio attributes, audio focus, and headphone-disconnect pause are explicit. Brightness/volume have visible labelled controls in the speed menu. Sheets, subtitle workspace, and playlist sizing use their actual available container. The unused separate Downloads screen is removed after reference checks. Page-console text is not forwarded; diagnostics remove HTTP(S) user-info, queries, and fragments, and debug/page-console logging is gated out of release. Benchmark fixtures now include real generated artwork, valid local media, nested rules, and optional controlled remote/subtitle/transfer journeys. Profile collection includes common interactions. |



## Retention and operational limits



- **Saved Library records:** automatic playback eviction never removes explicit saves or manual placements. At 5,000 protected records, new saves/moves that add protection fail with a message. Existing legacy records and Undo restorations are preserved even if over that limit; further protected additions wait until below it.

- **Recent playback:** at most 500 new automatic, non-manual records, separate from protected records. Playing a saved item never downgrades its saved status.

- **Cleanup:** only app-private artwork/caption copies are eligible; downloaded public media is untouched. Undo works during the grace period. Failed persistence and a changed Library prevent obsolete cleanup from deleting assets referenced by a newer snapshot.

- **Artwork:** a cancelled platform extraction already running may finish before releasing its retriever, but obsolete queued work and UI delivery are suppressed. Bitmap/cache budgets bound retained work; they do not promise every device's peak heap. Five-minute negative caching is a retry cooldown, not permanent removal of artwork. Direct player-frame capture can still supply a stream thumbnail.

- **Persistence counters:** encoded bytes measure serialized payload, not physical filesystem writes or flash write amplification. The deterministic 10,000-edit queue test checks coalescing/durability, not real-time disk throughput.

- **Filter diagnostics:** equivalent block/allow results and manual/exception precedence are tested. A URL matching several rules in the same tier may report a different matching rule because token traversal order changed; its blocking decision remains equivalent.

- **Profiles:** the checked-in baseline/startup profiles were not regenerated or represented as current P2 runtime evidence. The updated collector must run on your chosen device. The animation plan remains separate.



## Local verification



**449 tests passed; zero failures, errors, or skips.** Debug lint: 0 errors, 70 warnings, 5 hints. Release lint: 0 errors, 71 warnings, 5 hints.

Debug, R8-shrunk unsigned release, R8 benchmark application, and the separate benchmark harness all built successfully with Gradle offline. PowerShell benchmark-runner parsing passed. Eleven host-only HTTP checks passed (whole-file/HEAD, byte ranges, suffix ranges, invalid-range rejection, captions, page markup, deterministic transfer bytes, and missing route). No device or emulator was accessed. New filter revision tests cover change-and-change-back, mutation during reading, failed writes, and overlapping writers. 

The final command was:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleBenchmark :benchmark:assembleBenchmark "-Pbenchmark.r8=true" --offline --no-daemon --console=plain --max-workers=1
```

[Final build log](G:/Projects/Miowser/.local/optimization/p2-settled-verification.log), [machine-readable evidence](G:/Projects/Miowser/.local/optimization/p2-final-results.json), [unit report](G:/Projects/Miowser/app/build/reports/tests/testDebugUnitTest/index.html).



## Desktop performance comparisons



| Workload | Samples per implementation | CPU p50 before → after | Elapsed p50 / p95 before → after | Allocated bytes p50 before → after |
|---|---:|---|---|---|
| Filter: 20,480 ASCII URL classifications | 10 | 1,312.50 ms → 1,000.00 ms (23.8% lower) | 1,320.59 ms / 1,363.04 ms → 1,004.05 ms / 1,029.83 ms | 810,118,144 → 341,143,552 (57.9% lower) |
| Routing: 500 records × 20 passes | 6 | 531.25 ms → 39.06 ms (92.6% lower) | 534.26 ms / 644.59 ms → 38.07 ms / 42.31 ms | 1,106,682,080 → 32,122,080 (97.1% lower) |
| Routing: 1000 records × 10 passes | 6 | 500.00 ms → 31.25 ms (93.8% lower) | 513.94 ms / 526.91 ms → 36.52 ms / 37.10 ms | 1,106,680,960 → 32,120,992 (97.1% lower) |
| Routing: 5000 records × 2 passes | 6 | 500.00 ms → 46.88 ms (90.6% lower) | 503.52 ms / 515.38 ms → 38.12 ms / 53.72 ms | 1,106,680,144 → 32,120,144 (97.1% lower) |

Filter CPU ranges: 1,265.62–1,359.38 ms before and 984.38–1,031.25 ms after. Every batch gives the same 7,168 blocked decisions. Routing uses six interleaved batches per size, each processing 10,000 records. CPU resolution is 15.625 ms; elapsed timings better describe short optimized batches. Allocation counts include transient objects, not retained heap. Measurements ran on Windows with Android Studio OpenJDK 25.0.3. The prior full pass had a smaller filter CPU improvement (14.9%); absolute timings vary between host runs, so these figures cannot predict a phone speedup. The writer test submits 10,000 adjacent queued snapshots: 9,999 coalesce, one disk action executes, queue depth stays one, and all receipts await durability. Other cases verify barriers and cross-store ordering.

These are isolated desktop JVM workloads, not Android startup, scroll, network, or frame-rate measurements. Both implementations receive the same immutable rules and inputs; initial warmups precede interleaved samples. The filter corpus includes the bundled EasyList plus manual exceptions/literal rules, hits/misses, unique query URLs, resource-type variation, and separate Unicode/long-URL decision checks. Routing compares the audited uncompiled router against a reused compiled index over 500/1,000/5,000 video facts. CPU counters on Windows have coarse resolution; elapsed timings and allocation counters are reported separately. No phone percentage speedup is claimed.



## Installable APK and source identity



[Installable P2 debug APK](G:/Projects/Miowser/Miowser-0.1.0-p2-debug.apk): **27,832,363 bytes**. SHA-256:

`53eb6901a9ea637da6191109a1ca97066c0c13479229a2ba86809ec4691eead2`

[R8 benchmark application](G:/Projects/Miowser/app/build/outputs/apk/benchmark/app-benchmark.apk) is locally signed and uses the isolated benchmark package. Production release built unsigned; distribution signing is not performed here. Generated outputs can be overwritten by later builds; exact hashes are archived in the result JSON. The source/build/script manifest has SHA-256 `73afa326422afde853ba05a6d3884c3ba8f4d8d468a24d01401bbac725b86c34`. [Manifest](G:/Projects/Miowser/.local/optimization/p2-source-manifest.json), [normalized P2 diff](G:/Projects/Miowser/.local/optimization/p2-source.diff). The diff separates line-ending-only differences, and the manifest preserves exact byte identities. Benchmark app fixtures were not captured in the initial P2 source snapshot; they are identified in the final manifest. Existing unrelated working-tree changes were preserved; no commit was made.



## User device checklist



Use the same phone, WebView, refresh rate, font scale, dataset, build/compilation mode, and thermal conditions for before/after comparisons. Record p50/p95, frame overruns, heap/GC, image-ready latency, and save-queue drain time. Investigate repeatable regressions; a cleaner implementation or successful build is not device performance acceptance.



### Saved data and persistence



- [ ] 1. Install over P1; existing videos, captions, settings, folders, speed dials, bookmarks, and filters remain readable.

- [ ] 2. Add video 501 explicitly; the oldest explicitly saved video remains in Library after restart.

- [ ] 3. Play many new unsaved videos; only automatic recent items age out. Explicit saves/manual placements survive.

- [ ] 4. Add an already auto-recorded video using Add to Library; it becomes protected, retaining progress and recency.

- [ ] 5. Exercise the 5,000 protected-record limit on an isolated fixture: Add/manual Move reports the limit; existing records remain intact.

- [ ] 6. Remove/Undo a video and delete/Undo several videos; recency, folder, progress, subtitle, and thumbnail survive.

- [ ] 7. Rapidly change Hub/browser settings, sort order, speed dials, and folders; restart and verify the last accepted values.

- [ ] 8. Rapid subtitle edits preview immediately and preserve the final values after closing the panel, leaving the player, and backgrounding.

- [ ] 9. Brightness changes by gesture and by the visible slider save at completion; aspect ratio and track choices survive reopening.

- [ ] 10. Use an IO failure/low-space fixture: failed saves remain visible/retryable; retry cannot overwrite a newer value.

- [ ] 11. Create a backup immediately after edits; restored settings, captions, and Library agree. History/tabs/cookies/private data remain excluded.

- [ ] 12. Change backup path/interval quickly; the final committed choice and scheduled interval agree after restart.

- [ ] 13. Revoke backup-folder access; errors remain understandable and controls recover from their busy state.

- [ ] 14. Change site permissions, use them immediately, reset them, and restart; menus/WebView honor the latest choice.

- [ ] 15. After removing/replacing a caption/video, wait beyond the undo window; unreferenced private files are reclaimed. Edit/re-add during the window and verify referenced files remain.

- [ ] 16. Confirm completed public downloads survive Library deletion, clear app data, and uninstall.



### Artwork and larger libraries



- [ ] 17. Compare real-thumbnail grid flings cold/warm; record frame overruns, image-ready p50/p95, and GC/heap.

- [ ] 18. Fling away while extraction is queued; obsolete jobs stop queuing, revisited visible cards receive artwork promptly.

- [ ] 19. Scroll while local midpoint extraction or cleanup runs; existing visible thumbnails are not held behind those jobs.

- [ ] 20. Revisit cards repeatedly; successful metadata/artwork is reused and invalid media does not trigger extraction every visit.

- [ ] 21. Verify artwork quality/aspect ratio for portrait, landscape, unusual sizes, and high-resolution videos.

- [ ] 22. Test oversized/malformed favicons; the app remains responsive and keeps available cached icons.

- [ ] 23. Open/close a populated grid repeatedly; image/metadata cache memory levels off rather than growing with every URI.

- [ ] 24. Compare 500/1,000/5,000 entries, including search, folder selection, counts, sorting, and restart.

- [ ] 25. Check revoked/restored local document grants and relinking; replacement URIs receive fresh metadata/artwork.



### Rule routing and filters



- [ ] 26. MEDIA, PAGE, COMBINED; AND and OR; case variants; `art` versus `cartoon`; `Steel Ball Run` versus `Steel-Ball Run`.

- [ ] 27. A combined rule can satisfy different phrases across media/page titles; one phrase must not be fabricated across the title boundary.

- [ ] 28. Website rules beat title rules; website AND checks page/media hosts, including subdomains.

- [ ] 29. More AND phrases win among title matches; equal specificity preserves folder order.

- [ ] 30. Roots take from default; child/grandchild rules inspect only their parent; unrelated folders/manual moves stay untouched.

- [ ] 31. Rule add/edit prompts before reorganizing existing videos; declining leaves placement unchanged.

- [ ] 32. Start a large reorganization, then manually move/remove/add/change a title; obsolete results cannot roll it back.

- [ ] 33. Edit the rule/default while computation runs; apply reflects current inputs or asks for retry.

- [ ] 34. Ordinary browsing and media detection work while rules are reorganizing a large Library.

- [ ] 35. Compare blocking/allowing on known sites, manual exceptions, resource-type rules, first/third party, Unicode, and long signed URLs.

- [ ] 36. Trigger several identical reloads, then change selections and change them back while parsing; the latest valid generation wins and installed protection remains active. Rapidly toggle/remove/Undo lists and save/Undo manual rules: committed metadata survives restart, Undo follows saved removal, and older callbacks do not roll back newer edits.

- [ ] 37. Try corrupt/oversized filter updates and compiled-cache corruption; previous protection survives errors and text rebuild works.



### Player, layouts, release bridges



- [ ] 38. Local/direct/HLS/DASH: pause/play/seek, buffering spinner, playlist order/actions, cache targets, and subtitle timing remain as in P1.

- [ ] 39. External SRT/VTT remains visible through style, opacity, position, font, delay, pause, and background/foreground changes.

- [ ] 40. Use Brightness & volume from the speed menu with touch, keyboard, and TalkBack; labels/values make sense.

- [ ] 41. Another app takes audio focus; playback pauses/ducks/resumes according to platform policy, without playing over a phone call.

- [ ] 42. Unplug wired headphones and disconnect Bluetooth; audio does not unexpectedly continue through the speaker.

- [ ] 43. Open/close/rotate the player repeatedly; no abandoned extraction/prefetch or released-player callbacks accumulate.

- [ ] 44. Lock controls, reveal Unlock, unlock, and exit; TalkBack can identify the unlock action.

- [ ] 45. Portrait, landscape, split-screen, small window, and 100/150/200% fonts: playlist, subtitle workspace, track/download/site sheets fit their container and remain scrollable.

- [ ] 46. Light/dark/dynamic colors, navigation gestures/buttons, display cutouts: top/bottom overlays, touch targets, timers, and continuous sheets remain coherent.

- [ ] 47. Use the R8 benchmark APK to exercise ad-block, grabber, and renderer-download bridges; source-level checks cannot prove live WebView dispatch.

- [ ] 48. A page logs a message containing a fake secret URL; release emits no page-console/debug text. Diagnostics strip user-info, query, and fragment.



### P0/P1 smoke and measured acceptance



- [ ] 49. Six normal/three low-memory live browser pages remain the configured budget; eviction, back/forward, and renderer recovery work. Start/close/restart private sessions, including process death around entry/exit: private entry waits for its saved crash marker, cleanup remains conservative, and private history/tabs never become normal records.

- [ ] 50. Pause two downloads, enqueue a third, resume; completed bytes and scheduler slots behave correctly. Paused notices remain dismissible.

- [ ] 51. Detected media counter, Add to Library, link Select text, tab long-press, and download-snackbar Stream still work.

- [ ] 52. Run the updated isolated benchmark/profile collectors using the guide below; record current APK hash and compare the same compilation mode.

- [ ] 53. Compare baseline/candidate with interleaved repeated runs; separate profile gains, server variance, and thermal drift from code changes.

- [ ] 54. Accept performance claims only when the intended metric improves beyond noise without related latency/throughput/memory regressions.



## Benchmark/profile guide



The benchmark application uses the separate `com.miowser.app.benchmark` package. Seeding replaces only its test data. Cards now have actual JPEG artwork, valid media, mixed completed/unfinished downloads, and nested rules. Earlier `synthetic-v1` runs are not directly comparable to `artwork-media-v2`. For a P1/P2 performance comparison, build both production snapshots with this same fixture/collector and identical R8/compilation settings. The P1 debug APK is a functional reference, not a release performance baseline.

Run these yourself from the workspace with only your chosen device connected. Substitute its serial:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\Run-OptimizationBenchmarks.ps1 -Serial YOUR_SERIAL -Journey all -Library 500 -Tabs 10 -Downloads 200 -Iterations 20 -R8
```

Repeat Library with 1,000/5,000 records, tabs with 1/10/30, and Downloads with 200/1,000. Keep Compilation `None` for a code-only comparison; compare `BaselineProfile` separately after regeneration. The script archives source inputs, device conditions, APK identity, and results. Inspect distributions rather than the best launch.

For controlled remote subtitles and an actual throttled native transfer, run the server in a second desktop terminal (requires Python 3):

```powershell
python .\scripts\p2_fixture_server.py
adb -s YOUR_SERIAL reverse tcp:8765 tcp:8765
.\scripts\Run-OptimizationBenchmarks.ps1 -Serial YOUR_SERIAL -Journey downloadsScroll -Downloads 1000 -FixtureServer http://127.0.0.1:8765 -ActiveTransfers -Iterations 20 -R8
.\scripts\Run-OptimizationBenchmarks.ps1 -Serial YOUR_SERIAL -Journey profile -FixtureServer http://127.0.0.1:8765
```

The transfer serves a 32 MiB range-aware payload at about 160 KiB/s; pause/resume and progress use real DownloadQueue work. `/fixture.mp4`, `/slow/fixture.mp4`, `/fixture.vtt`, and `/page` support manual playback/discovery checks. The tiny included video suits first-frame/subtitle journeys; use a sufficiently long video for the 15-minute prefetch-window checks. The active transfer and remote profile journeys are compiled but have not run on a device. Confirm setup before trusting timings.

After successful unminified profile collection, inspect its evidence directory and run `Update-OptimizationProfile.ps1 -EvidenceDirectory ...`. Rebuild release/benchmark targets and compare profile packaging and startup separately. Common interaction collection covers Library scrolling, local player, playlist, and tabs; optional remote collection exercises captions and millisecond edits. The assistant did not execute these device commands.
