# Animation smoothness — step 5

Date: 2026-10-09. Scope: **Polish navigation and list changes without retaining heavy screens** from the [animation plan](G:/Projects/Miowser/docs/ANIMATION_SMOOTHNESS_PLAN_2026-10-08.md). Device checks and measurements belong to the user.

## Changes

### Preserve lightweight Hub state

Library, Downloads and More each have a saveable state bucket. Downloads has independent buckets for Downloading and Downloaded. Switching destinations disposes the outgoing grid/list and its effects, while preserving scroll state. Library keeps its selected folder, search and sort instead of resetting to Unfiled on every navigation tap; More keeps its scroll position. Covered Hub content is also disposed while the player or backup workspace is open, then restored when it becomes visible.

The folder strip preserves its horizontal position across destination restoration. Explicitly selecting Unfiled or changing the default folder still resets the strip appropriately. A deleted saved folder falls back to Unfiled after the folder store is restored. A real folder/search/sort change resets the Library grid to the top; returning to the same view restores its saved position. Old filtered results are cleared when criteria change, so old-folder cards cannot remain actionable while a new worker projection is pending.

Destination content switches immediately. The existing navigation pill supplies continuity without retaining two populated grids through a crossfade. Saveable buckets contain lightweight UI state, not decoded images, running collectors or WebViews. Download/playback commands and the browser's page-retention budget are unchanged.

### Restrained list motion

A confirmed manual Library removal or move out of the current folder can animate surviving cards into place using the shared 220 ms selection curve. This uses the installed Compose lazy-item placement owner, stable keys, and no entry/exit fades. The action happens immediately.

The motion guard requires an exact single-key removal with survivors in their previous order. Search/sort/folder changes, automated routing, additions/Undo, restored data, bulk changes and concurrent insertion/reordering snap. Scrolling, hidden content and reduced motion also suppress placement and invalidate pending hints. After a snapped layout, one cancellable frame re-arms stable nodes for the next manual edit; there is no custom frame loop or action-start delay. Physical placement/interruption still needs device confirmation.

Thumbnail geometry and placeholders remain 16:9. Existing memory-cache hits, visible decode deferral and direct image replacement are preserved; no image crossfade, video capture, surface scaling or shared artwork-to-player transition was added. The latter remains an optional experiment requiring first-frame and memory evidence.

### Benchmark and profile preparation

`hubNavigation` measures six Library → Downloads → More → Library cycles after scrolling a populated artwork Library. `hubDownloadLists` measures six Downloaded/Downloading switches. Both can use the existing active-transfer fixture, which starts outside the measured block. The runner accepts both journeys.

Interaction profile collection now traverses Hub destinations and both download lists, repeats playlist transitions, and repeats remote subtitle edits when the loopback media/subtitle fixture is supplied. The benchmark APKs are compiled locally. Runtime selectors, frame metrics and generated profiles require the user's device run.

**Existing baseline/startup profiles are preserved.** No profile was invented or replaced from source inspection. Profile generation and before/after release-equivalent comparisons remain pending under the user's instruction to leave device checks to them; this is not a claim that refreshed profiles were generated or that frame performance improved measurably.

## Local verification

- **489 JVM tests passed**, including eight added tests; zero failures, errors or skips.
- Debug lint: **zero errors**, 81 warnings and 5 hints.
- Debug APK, R8 unsigned release and both benchmark APKs built successfully.
- Benchmark runner passed PowerShell syntax parsing; scoped whitespace check passed.
- All 337 recorded source hashes match the delivered source; existing baseline/startup profile hashes are unchanged.
- Packaged debug bytecode contains the new placement owner; release bytecode contains the new downloaded-grid marker. The delivered APK includes Step 5 code.

Eight new JVM tests exercise exact manual removals, automatic changes, reordered survivors, concurrent/bulk edits, consumed hints, suppression, fresh destination state and re-arming after a bulk snap. They validate motion eligibility, not on-device placement, saveable-state restoration or accessibility.

No emulator, device, ADB, instrumentation or benchmark/profile run was executed. Evidence is under `G:/Projects/Miowser/.local/optimization/`: the Step 5 before snapshot, scoped diff, source manifest, logs, preserved profile hashes and results JSON. Earlier work and APKs remain intact.

## Installable build

[Download the Step 5 debug APK](G:/Projects/Miowser/Miowser-0.1.0-animation-step5-debug.apk).

Size: 27,284,500 bytes. SHA-256: `d4d3464de746d58ffa0a6db595ba9c1b132d96eeec23422831309f0421de602f`.

The release APK is unsigned build evidence. Benchmark target/test APKs are retained for the user's measurement setup; they were not installed or run.

## Device checklist

Compare the Step 4 and Step 5 builds with the same Library, downloads, subtitle files and settings.

### Navigation and restoration

- [ ] Scroll Library well below the top, switch to Downloads → More → Library, and verify the same position returns.
- [ ] Select a primary folder and a nested folder; switch destinations and return. Folder, breadcrumbs, count and position remain correct.
- [ ] Search within a folder and switch destinations. Query, search visibility, results and scroll position survive.
- [ ] Change sort, search and folder deliberately. The new view starts at the top; no stale old-folder card remains actionable during loading.
- [ ] Tap Library while it is already selected. It does not unexpectedly reset folder or scroll position.
- [ ] Scroll the folder strip horizontally, leave and return. Its position survives; selecting Unfiled and changing the default folder still behave sensibly.
- [ ] Delete the selected folder or replace the folder set through restore. Selection safely falls back to Unfiled when missing.
- [ ] Scroll Downloading, switch to Downloaded and scroll independently. Repeated sublist and destination switches restore both positions.
- [ ] Scroll More to its lower settings, leave and return. Position and preference values survive.
- [ ] Open a player from Library or Downloads, then return. Hub content, selected folder/query and position restore without duplicating jobs.
- [ ] Open/close Backup & restore from More. More restores and any restored Library/download/folder changes appear correctly.
- [ ] Rotate, resize/split-screen and exercise Android activity recreation. Saved selection/query/list positions remain usable and no missing-folder crash occurs.

### List changes

- [ ] Remove one visible Library record while the grid is still. Surviving cards move coherently into the gap without entrance/exit fades.
- [ ] Move one visible video to another folder using the nested menu. Routing happens immediately; surviving cards fill the gap.
- [ ] Undo a removal. The restored record appears promptly without a cascade of entrances; its subtitle and playback details remain intact.
- [ ] Remove or move several records rapidly before the projection catches up. No cascade, duplicate action or stale placement hint remains.
- [ ] Apply/edit a title or website rule that reorganizes several videos. Membership/counts update immediately and cards snap to their new arrangement.
- [ ] Import/restore a populated Library or clear it. No full-grid animation runs; empty states and folder counts are correct.
- [ ] Change title/recency/progress ordering while data updates. Reordering is immediate and no wrong card receives an animation/action.
- [ ] Start a fling during placement and remove a record while scrolling. Scrolling remains responsive, without unintended placement or clipping.
- [ ] Perform a single manual removal after a bulk edit, once settled. Stable nodes are ready again; no old bulk movement starts later.

### Artwork, playback and controls

- [ ] Fling through real thumbnails and revisit cached cards. Image bounds do not change, cached artwork appears promptly and no repeated image fade runs.
- [ ] Switch destinations repeatedly after images load. Memory remains bounded; inactive grids do not keep extraction/decoding work running.
- [ ] Check uncached/missing images and unavailable files. Placeholder size stays stable and Locate/Remove/Open actions remain correct.
- [ ] Check downloaded video, APK and other-file cards. File type, artwork, quality labels and their actions remain correct after restoration.
- [ ] Play a video with saved VTT/SRT and edit subtitle styling/delay. Playback/captions remain correct and the static grey sample remains compact.
- [ ] Recheck rapid playlist/subtitle dismissal/reopen, interrupted tab swipes and controls visibility from Step 4. No title flash, click-through or stale close is introduced.

### Reduced motion and accessibility

- [ ] Test app Reduce motion and Android animations off independently. Manual removals and navigation complete immediately with no stuck frame/callback.
- [ ] Compare Android animation scales 0.5×, 1× and 2×. Placement follows system duration once.
- [ ] Use keyboard/TalkBack to switch destinations. Inactive content is absent from focus/navigation; restored content has sensible focus.
- [ ] Test light/dark/dynamic themes, RTL and 150/200% font sizes. Cards, folder/menu/counter geometry and touch targets remain usable.

### Performance and profile gate

- [ ] Run the populated `hubNavigation` and `hubDownloadLists` journeys on the same physical device before/after, with and without active transfers. Record frame deadlines/overruns, first response, CPU, memory, refresh rate and thermal state.
- [ ] Compare sustained 60 Hz and higher-refresh navigation/flinging. Confirm no inactive collectors/extraction work or motion loop continues after rest/disposal.
- [ ] Generate interaction profiles on the unminified benchmark target after validating the journeys. Supply the existing loopback fixture for remote playback/subtitle coverage.
- [ ] Import only successful profile evidence with the profile update script, then rebuild release and verify profile packaging. Compare refreshed-profile effects separately from the Step 5 code change.
- [ ] Re-run release-equivalent scrolling, startup, browser/tab and player journeys. Accept only if latency, playback, throughput and memory pass the regression contract alongside smoother motion.

## User-run measurement commands

Use the existing dedicated benchmark package/fixture, not the production app's data. Replace the serial with the user's selected device. These commands were prepared, not executed:

```powershell
& ./scripts/Run-OptimizationBenchmarks.ps1 -Serial '<device-serial>' -Journey hubNavigation -R8
& ./scripts/Run-OptimizationBenchmarks.ps1 -Serial '<device-serial>' -Journey hubDownloadLists -R8
# After setting up the existing loopback server/device forwarding:
& ./scripts/Run-OptimizationBenchmarks.ps1 -Serial '<device-serial>' -Journey hubNavigation -R8 -FixtureServer 'http://127.0.0.1:8765' -ActiveTransfers
& ./scripts/Run-OptimizationBenchmarks.ps1 -Serial '<device-serial>' -Journey profile -FixtureServer 'http://127.0.0.1:8765'
& ./scripts/Update-OptimizationProfile.ps1 -EvidenceDirectory '<successful-profile-evidence-directory>'
```

Use matched compilation/cache/thermal conditions and preserve the before-profile APK/evidence. Local JVM/lint/build success does not establish real-device frame-rate gains.
