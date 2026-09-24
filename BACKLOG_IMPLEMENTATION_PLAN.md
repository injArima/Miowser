# Miowser backlog implementation plan

Last updated: 2026-09-21

## Scope and working method

This plan completes the functional backlog through deferred browser expansions. Release packaging,
store preparation, final branding, and the final release audit remain a later phase.

For every implementation step:

1. Preserve existing persisted data or add a versioned migration.
2. Put parsing, classification, scheduling, and persistence logic behind pure unit-tested code.
3. Compile the debug app and run the complete unit suite.
4. Give the user a focused runtime checklist instead of spending tokens driving the emulator.
5. Record confirmed limitations and update `PROJECT_STATUS.md`.
6. Commit only after an explicit `commit this` request.

## Recommended execution order

The order below deliberately moves bounded media probing before restart recovery. Recovery should be
built after all intended source types have stable transfer plans, otherwise its persisted schema
would have to be redesigned while adding `blob:` downloads or DASH `SegmentBase`.

### Step 1 — Close the snackbar task and establish the baseline

**Status:** Complete. The user confirmed the single-surface snackbar and correct action/dismissal
behavior on-device.

Before starting Step 2, preserve the known-good baseline:

- Keep the latest full unit-suite result as the baseline (184 tests at this checkpoint).
- Treat snackbar behavior as a regression item in later manual checklists, not active development.
- Keep the current uncommitted-work warning visible until a commit is explicitly requested.

### Step 2 — Resolve generic `.bin` video filenames

**Goal:** Finish webpage downloads with the correct name and extension even when the first response
uses a generic URL or `application/octet-stream`.

**Implementation status:** Complete. The user confirmed the corrected download behavior on-device.
The final identity is resolved before output creation, which replaces the originally planned
pending-entry/pre-Q rename step.

Implementation:

1. Introduce one pure `DownloadIdentityResolver` used by WebView handoff and direct downloads.
2. Record every identity input separately: RFC 5987 `filename*`, regular `filename`, final redirected
   URL, original URL, declared MIME type, and suggested browser filename.
3. Add bounded magic-byte recognition for MP4/M4V/MOV, WebM/Matroska, common audio, subtitles, ZIP,
   and APK (ZIP plus Android manifest/signature structure where practical).
4. Resolve identity in strict precedence order: trustworthy content disposition, meaningful final
   URL suffix, specific MIME type, magic bytes, then `.bin` as the honest last resort.
5. Allow a MediaStore pending entry to be renamed before `IS_PENDING` is cleared; use an equivalent
   file rename on pre-Q devices.
6. Ensure redirects and range probes feed their final response metadata into the resolver.
7. Add table-driven tests for generic MIME, misleading URL suffixes, UTF-8 filenames, redirects,
   MP4/WebM signatures, APKs, and unknown data.

Acceptance:

- The previously reproduced webpage video no longer lands as `.bin`.
- Grabber downloads retain their existing correct naming.
- Automatic APK downloads keep their server filename or become `.apk` through reliable detection.
- Unknown bytes remain `.bin`; the app never invents a media type without evidence.

### Step 3 — Make direct-media probing bounded and server-compatible

**Goal:** Verify direct media without risking an accidental full-file transfer when Range is ignored.

**Implementation status:** Complete in code; focused tests, the full unit suite, and the debug build
pass. Runtime confirmation remains on the user checklist below.

Implementation:

1. Try `HEAD` first while forwarding cookies, User-Agent, Referer, and redirect handling.
2. Accept useful size/type/disposition metadata from a successful HEAD response.
3. If HEAD is rejected or inconclusive, issue `GET Range: bytes=0-N` with a small hard cap.
4. Stop reading and disconnect once the cap is reached even when the server returns HTTP 200 and
   ignores Range.
5. Feed sampled bytes into the Step 2 identity resolver.
6. Distinguish exact size, estimated size, and unknown size in `DetectedMedia`.
7. Add tests around the response-decision state machine and bounded-read helper.

Acceptance:

- A Range-ignoring server cannot cause the probe to download the entire file.
- Authenticated probes continue to work.
- Detected-media size/type labels remain honest.

Delivered:

- Direct candidates now use authenticated, redirect-aware HEAD first.
- Missing, rejected, or generic HEAD metadata falls back to a bounded 512 KiB ranged GET. A valid
  HEAD result is also enriched through this bounded path for direct MP4 duration/resolution.
- The fallback disconnects before closing its stream, preventing connection-reuse draining when a
  server ignores Range and returns HTTP 200 with the entire file.
- Sample bytes and response metadata feed the shared Step 2 identity resolver; trustworthy response
  filenames are retained on the detected item and reused when downloading it.
- Error-response lengths and partial-range Content-Length values are never presented as full sizes.
- `DetectedMedia.sizeKind` explicitly separates exact, estimated, and unknown sizes.
- Pure tests cover HEAD decisions, MIME/signature contradictions, bounded reads, and exact-size
  interpretation.
- Network facts and live `<video>` facts now merge field-by-field, so a later partial report cannot
  erase a filename, exact size, duration, or resolution already learned by another detector.
- Direct MP4 files expose duration and display resolution from a complete bounded `moov` box in the
  prefix or suffix, including version-1 headers and quarter-turn presentation matrices.
- HLS uses explicit byte ranges as exact size when possible; otherwise it samples up to three
  representative segments before falling back to advertised average bandwidth. DASH metadata uses
  DOM-parsed representation resolution/bitrates and MPD or complete Period durations.
- MIME type, redirect destination, bitrate, and filename are retained. Stale async probe results
  from a previous page generation are discarded.

### Step 4 — Support WebView-only `blob:` and `data:` downloads

**Goal:** Save downloads whose bytes only exist inside the WebView renderer.

**Implementation status:** Complete in code for real Blob objects and `data:` URLs; focused tests
and the debug build pass. Runtime confirmation remains on the user checklist. MediaSource/MSE
object URLs are explicitly identified as a separate limitation below.

Implementation:

1. Add a per-request tokenized JavaScript bridge; reject messages not associated with the active
   page/request.
2. Decode `data:` URLs natively with strict MIME, filename, and maximum-size validation.
3. For `blob:` URLs, fetch the Blob inside the page and transfer bounded binary chunks through the
   bridge rather than constructing one huge Base64 string.
4. Stream chunks directly into a pending MediaStore output and update download progress.
5. Support cancellation, cleanup of partial files, page/navigation invalidation, and duplicate
   request protection.
6. Reuse the normal download row, notification, filename resolver, and completed-file behavior.
7. Define a conservative transfer cap initially; surface a clear error instead of risking WebView
   or app-process memory exhaustion.

Acceptance:

- Small and medium `data:`/`blob:` downloads complete through the ordinary Downloads interface.
- Cancellation removes partial output.
- Cross-page or stale bridge messages cannot write data.
- Oversized renderer-only objects fail clearly and safely.

Delivered:

- Every Blob transfer uses a native-created UUID token tied to the originating tab's current page
  generation. Navigating invalidates the token, cancels the session, and deletes partial output.
- Real Blob objects stream from WebView in ordered 48 KiB chunks; only one chunk is Base64-encoded
  at a time. Native code validates sequence, declared total, per-chunk size, and a 256 MiB cap.
- `data:` URLs are decoded natively with byte-correct percent decoding, strict MIME validation,
  and a 32 MiB cap. A literal `+` is preserved instead of being corrupted by form decoding.
- Both paths use the existing download identity resolver, conflict dialog, queue, pause/resume,
  progress, per-download notification, MediaStore/pre-Q output, completed-file opening, and
  persistence behavior.
- Renderer payloads are not copied into SharedPreferences: only `blob:`/`data:` scheme markers are
  persisted. Interrupted renderer transfers restore as failed and require returning to the page.
- Pure tests cover scheme classification, Base64/percent decoding, caps, ordered chunk accounting,
  overrun rejection, persistence enum compatibility, and preservation of renderer handoffs when a
  duplicate is renamed.

Known boundary:

- A `blob:` URL backed by `MediaSource`/MSE is not a Blob file and cannot be read with `fetch()`.
  It now fails with a clear segment-capture message. Supporting that case requires intercepting and
  reconstructing `SourceBuffer` data (or using the already-detected HLS/DASH source), not weakening
  this bounded Blob transfer.

### Step 5 — Add DASH `SegmentBase` and MP4 `sidx` support

**Goal:** Download common single-file, byte-range DASH representations.

**Status:** Implemented; awaiting the user's runtime checklist confirmation.

Delivered:

- MPD parsing now retains `SegmentBase` index and initialization ranges, inherited
  AdaptationSet metadata, representation validators, relative BaseURLs, and separate audio/video
  tracks.
- A bounded ISO-BMFF parser handles version 0 and version 1 `sidx` boxes with strict size, overflow,
  timescale, nested-reference, and truncation checks.
- `sidx` references become exact authenticated HTTP byte-range requests. Servers must return the
  requested `206 Content-Range`; ignored or incorrect ranges fail instead of corrupting output.
- Selected audio/video representations download concurrently to bounded temporary files, concatenate
  in index order, and use the existing MP4 mux pipeline. Pause, resume, cancel, redirects, cleanup,
  and download progress remain connected.
- DRM-bearing manifests fail explicitly before transfer.
- Existing `SegmentTemplate` and `SegmentList` paths now stream segments to temporary files instead
  of retaining every segment body in memory.

Known boundary: a separate external `RepresentationIndex` resource is rejected with a clear reason.
The implemented path covers the common form where the `sidx`, initialization bytes, and media ranges
belong to the representation's single MP4 resource.

Implementation:

1. Extend the MPD model with initialization ranges, index ranges, media ranges, and representation
   validators.
2. Fetch the initialization/index ranges and parse ISO-BMFF boxes with strict length and overflow
   checks.
3. Parse `sidx` timescale, earliest presentation time, first offset, and references into concrete
   byte ranges.
4. Support separate video/audio representations and feed their ranges into the existing concurrent
   transfer and mux pipeline.
5. Reject DRM/ContentProtection combinations the app cannot legally or technically decode.
6. Handle redirects and relative BaseURLs at MPD, Period, AdaptationSet, and Representation levels.
7. Add fixture tests for version 0/1 `sidx`, malformed boxes, nested/unsupported references,
   audio+video, and relative URLs.

Acceptance:

- Existing `SegmentTemplate` and `SegmentList` downloads do not regress.
- A public non-DRM `SegmentBase` reference stream downloads and muxes correctly.
- Unsupported DRM or malformed indexes fail with a useful reason and clean partial data.

### Step 6 — Implement true restart-safe download recovery

**Goal:** Continue missing bytes/segments after process death instead of restarting from zero.

**Status:** Implemented in code and compile/unit verified. Runtime recovery scenarios are ready for
the manual checklist; they have deliberately not been driven on the emulator by the agent.

Implementation:

1. Introduce a versioned persisted transfer plan separate from presentation/history fields.
2. Persist source validators (ETag/Last-Modified/length), output identity, completed byte ranges or
   segment IDs, elapsed time, and transfer kind.
3. Keep partial output pending and reopenable; verify it before resume.
4. Direct files: resume missing ranges with `If-Range`; restart safely if validators changed.
5. HLS/DASH: retain verified segment cache state and download only missing segments before muxing.
6. Reacquire current cookies from `CookieManager` using source/page URLs. Do not persist raw session
   cookies indefinitely. Persist only non-secret request metadata needed to reconstruct the request.
7. Restore paused jobs as paused, interrupted active jobs as resumable, and corrupt/incompatible
   records as restartable with an explanation.
8. Version and migrate the download-store codec so old records remain readable.
9. Update per-download notifications immediately after restore.

Delivered:

- Direct downloads keep a private random-access payload, persist byte-range completion atomically,
  use `If-Range`, and fall back to a clean single stream when the server stops honoring ranges.
- HLS and DASH keep only completely written segment artifacts. Each reusable item has a durable
  length/digest marker; unfinished temporary files are ignored and only missing verified items are
  fetched before muxing.
- Recovery plans are versioned separately from download-history records and retain transfer kind,
  output identity, validators/fingerprints, progress, and active elapsed time.
- User-Agent and Referer are migrated into the history codec; raw cookies remain unpersisted and
  are reacquired from the current WebView cookie jar when work resumes.
- Restored interrupted work with a valid plan becomes paused/resumable, and the foreground service
  immediately recreates its individual notification. Cancel/delete removes private artifacts.

Acceptance:

- Killing Miowser during direct, HLS, and DASH downloads preserves completed work.
- Resume continues from the missing portion when the source is unchanged.
- Changed sources restart safely rather than combining incompatible bytes.
- Cancel/delete still removes temporary state and output as requested.

### Step 7 — Finish player subtitle, audio, and source controls

**Goal:** Make the three currently disabled controls functional without redesigning the player's
visual style.

**Status:** Implemented in code and compile/unit verified. Runtime stream combinations are ready
for the manual checklist; the agent has not driven the emulator.

Implementation:

1. Observe Media3 `Tracks` and expose available text/audio/video groups as stable UI models.
2. Subtitles: Off/Auto/track selection, language/label display, forced-caption indication, and
   optional external subtitle attachment later.
3. Audio: Auto/track selection with language, channel, role, and codec details where available.
4. Sources/quality: Auto plus representation choices grouped by resolution/bitrate; connect detected
   alternate URLs when they truly represent the same title.
5. Apply `TrackSelectionOverride` and clear overrides correctly when returning to Auto.
6. Persist sensible global preferences and per-library-item overrides only where identity is stable.
7. Handle live manifests and tracks appearing after playback starts.

Delivered:

- Media3 track groups are observed continuously and converted into stable, supported subtitle,
  audio, and video-quality choices with language, forced-caption, channel, codec, resolution, and
  bitrate details where the manifest supplies them.
- Subtitles expose Off, Auto, and explicit tracks. Audio and Sources expose Auto and explicit
  tracks; returning to Auto clears the relevant override and restores adaptive behavior.
- Overrides apply through `TrackSelectionParameters` without rebuilding playback. Controls remain
  disabled when the active media has no meaningful choice.
- Subtitle defaults persist globally, while explicit subtitle/audio/video choices persist only for
  the stable Library identity of that media item. Live tracks can populate controls after startup.
- The player does not guess that separate grabber URLs are interchangeable merely because they
  appeared on one page. Media3-proven representations are exposed now; cross-URL switching remains
  intentionally excluded until detection carries a trustworthy same-media identity and per-source
  request headers.

Acceptance:

- Controls enable only when choices exist.
- Selection takes effect without restarting playback where Media3 permits.
- Auto restores adaptive selection.
- Existing gestures, rotation, lock, download action, and resume progress do not regress.

### Step 8 — Player optimization pass

**Goal:** Improve behavior and performance while retaining the player's intentionally custom,
immersive UI. Material 3 Expressive does not apply to the player.

**Status:** Implemented in code and compile/unit verified; awaiting the focused runtime checklist.

Implementation sequence:

1. Startup/buffering/error/retry state clarity.
2. Gesture threshold, accidental-trigger, edge exclusion, and accessibility review.
3. Control auto-hide timing and animation continuity.
4. Orientation/rotation-lock and process/configuration restoration.
5. Decoder failure fallback and actionable playback errors.
6. Thumbnail capture cost, library progress write frequency, and unnecessary recomposition audit.
7. Lower-end-device playback and long-session memory checks.

Delivered:

- Startup and rebuffering now have distinct labels. Buffering remains visible even when the main
  controls were already hidden.
- Playback errors are translated into concise network, expired-source, missing-file, permission,
  malformed-stream, decoder, DRM, or generic guidance. Recoverable failures expose Try again;
  terminal failures expose a clear close path.
- Media3 decoder fallback is enabled, allowing another available decoder to be tried when the
  preferred decoder cannot initialize.
- Lifecycle resume now respects an intentional user pause instead of always restarting playback
  after the app returns to the foreground.
- Ended media restarts from zero, and the screen stays awake only while playback is expected to
  continue.
- Gesture classification waits for a clearly dominant axis and does not claim vertical gestures
  from Android's system-edge area. Existing double-tap, seek, brightness, and volume behavior is
  preserved.
- The gesture surface now uses responsive, non-overlapping zones: center double-tap toggles
  play/pause, side double-taps seek ten seconds, and vertical brightness/volume remain side-only.
  Visible controls limit horizontal swipe seeking to the unobstructed video band; hidden controls
  restore the usable video surface. Status/navigation insets and the portrait lower-control region
  remain unclaimed so Android gestures and player controls keep priority.
- Position polling is reduced from four updates per second to two while playing and one while
  idle. Rebuffer transitions no longer force redundant Library writes; lifecycle/exit saves remain
  durable, and ordinary progress writes use a five-second movement threshold.
- Configuration rotation continues without Activity recreation through the existing manifest
  contract, preserving the live player and its Compose state.
- Pure tests cover gesture classification and playback-error presentation. The complete debug
  unit suite and APK build pass without compiler warnings.

Acceptance:

- No browser audio continues behind the player.
- Buffering and errors are always distinguishable from pause.
- Gestures remain predictable in portrait and landscape.
- Player state survives supported configuration changes without visual jumps or duplicate playback.

### Step 9 — Verify and fix pre-Android-10 storage

**Goal:** Convert the existing API 26-28 fallback from code-reviewed to runtime-verified.

**Status:** Code review and a legacy filename-collision fix are complete; debug build and unit
suite pass. API 26/28 runtime confirmation is pending the user's manual checklist. Step 9 must
not be marked complete until those results are known.

Code-review finding:

- Legacy public Downloads previously opened the requested path with `file.outputStream()` (or
  `RandomAccessFile(..., "rw")`). An unrelated existing file with that name could be silently
  truncated. Both legacy open paths now reserve a free name with atomic `createNewFile()`, using
  `name (2).ext`, `name (3).ext`, and so on. All downloader entry points update the visible
  download record to the actual reserved filename. Cancel/delete also request a media scan after
  deleting a legacy file so a previously indexed entry can be removed from the system index.
- The Q+ MediaStore path is unchanged.
- Filename suffix generation has focused pure tests.

Test matrix:

1. API 26 and API 28 emulator/device.
2. Permission accepted, denied, and denied then granted.
3. Direct single/multi-connection, HLS, DASH, pause/resume, cancel, retry, and delete.
4. File visibility in system Downloads and third-party file managers after media scan.
5. Duplicate filenames, low storage, revoked permission, process death, and app restart.

Implementation response:

- Fix only defects exposed by this matrix.
- Keep Q+ MediaStore behavior unchanged.
- Add pure tests for any new path/filename/state decision logic; device-only storage behavior remains
  a manual checklist.

### Step 10 — Browser privacy, permissions, and file-handling hardening

**Goal:** Turn the working browser into a controlled production browser surface.

**Implementation checkpoint:** Code and unit/build checks are complete for clear-data controls,
remembered camera/microphone/location decisions, lighter shared-storage private tabs, upload picking,
external links, navigation errors, browser preferences, and output-name validation. Manual validation
is listed in `STEP10_BROWSER_TEST_CHECKLIST.md`. Full site-permission parity for clipboard,
notifications, and protected-media identifiers is limited by WebView's callbacks; do not infer it from
the implemented camera/microphone/location prompts. API 26/28 storage testing was deferred by the user.

This step begins with a short requirements decision because private-mode and site-permission policy
affect persistence architecture.

Implementation sequence:

1. Browsing-data page: history, cookies/site data, cache, form data, permissions, downloads records,
   with explicit scopes and confirmations.
2. Per-site permission prompts and persisted decisions for camera, microphone, geolocation,
   notifications where supported, clipboard, and protected media behavior.
3. Private browsing profile: no history/library writes, isolated/cleared cookies and WebStorage,
   clear visual identity, and explicit download behavior.
4. File chooser/upload support through Activity Result APIs with MIME and URI permission handling.
5. External intents and non-HTTP schemes: supported-app resolution, user confirmation where needed,
   and safe fallback instead of silent failure.
6. TLS/certificate and network-error pages that fail closed and explain recovery options.
7. Filename/path/content validation shared by every download entry point.
8. Browsing preference controls such as JavaScript/cookies/popups only where WebView can enforce them
   reliably per tab or profile.
9. Accessibility labels, keyboard focus, large-font behavior, and edge-to-edge/inset review for every
   browser-owned page.

Acceptance:

- No permission is granted or remembered invisibly.
- Clearing selected data removes exactly the selected categories.
- Private sessions leave no browser history/site storage after closure.
- Uploads, external links, errors, and unsafe schemes have predictable user-visible outcomes.

### Step 11 — Deferred browser expansions

#### 11A. Static and bounded recursive media discovery

**Status:** Complete. Build/unit verified and the user confirmed the scanner and duration filter work.

1. Add a static HTML parser for direct media/source/manifest URLs missed before script execution.
2. Merge static, DOM, and network candidates through one deduplication and verification pipeline.
3. Add optional recursive discovery with explicit user initiation, same-site default, configurable
   depth/page caps, cancellation, and progress.
4. Never auto-download discovered media; detection remains separate from user-authorized download.
5. Rate-limit requests and preserve authenticated browser request identity where permitted.

Acceptance: bounded crawling cannot run indefinitely, leave the allowed origin silently, or flood
the detection list with duplicate segments.

#### 11B. Thumbnail tab overview

**Status:** Implemented in code; build/unit verified; user runtime check pending.

1. Capture a lightweight thumbnail when a tab becomes inactive without destroying its WebView.
2. Cache thumbnails outside the session codec; persist only tab identity/order/opener relationships.
3. Replace the current tab list presentation with an adaptive thumbnail grid while preserving close,
   new-tab, active-tab, opener-return, and persistence behavior.
4. Add multi-select close only if it remains understandable and does not complicate opener fallback.
5. Bound thumbnail memory/disk use and clear stale captures.

Acceptance: switching remains instant, WebViews stay alive, thumbnails do not cause memory pressure,
and back/close behavior remains exactly as currently verified.

#### 11C. Inactive tabs

**Status:** Implemented in code; build and unit verified; user runtime check pending.

1. Move normal tabs that have not been used for 14 days into an Inactive section as URL/title
   records, releasing their WebViews and thumbnails.
2. Detect duplicate normal tabs by normalized final URL and move the older duplicate records into
   the same section. Private tabs are never archived; closing the app discards them.
3. Opening an inactive record creates and reloads a live tab, removes the inactive record, and
   returns it to the normal Browsing section.
4. Delete inactive records after 30 days by default. Add a Browser Settings switch allowing the
   user to disable this automatic deletion.
5. Show an Inactive entry from Tabs with a count and explanatory subtitle. Store only the title,
   URL, last-active time, and archived time; never retain a WebView or preview.
6. Add bounded persistence, normalized-URL duplicate tests, migration tests, and clear-data/private-
   mode integration.

Acceptance: active-tab history and opener fallback remain predictable, no private tab is archived,
inactive records consume negligible memory, and auto-deletion follows the setting exactly.

### Step 12 — Material 3 Expressive UI for every surface except the player

**Research finding and feasibility**

- Miowser currently resolves Compose Material 3 **1.4.0 stable** through BOM `2026.08.00`.
- Dynamic Material You color already works on Android 12+.
- The official `MaterialExpressiveTheme` and newest Expressive component set are in Material 3
  **1.5.0-alpha28** as of this plan. The official roadmap still lists stabilization work.
- Therefore the visual conversion is highly feasible, but adopting every official Expressive API
  immediately has medium dependency risk: alpha API movement and recent bottom-sheet/navigation
  changes touch several Miowser-critical surfaces.

**Recommended migration strategy**

Build an Expressive design layer on stable 1.4 first, using public Material 3 components and
Miowser-owned tokens/wrappers. This delivers the intended look and behavior without coupling every
screen to alpha APIs. At the end of the conversion, run a dependency gate:

- adopt Material 3 1.5 alpha only if its required APIs materially improve the result and regression
  testing is acceptable; or
- keep the stable-compatible layer and switch the wrappers to official Expressive APIs when 1.5
  stabilizes.

Implementation sequence:

1. **Design tokens:** complete the typography scale, expanded rounded shape scale, semantic spacing,
   container hierarchy, elevation, icon sizing, and motion durations/springs. Preserve dynamic
   color and define polished non-dynamic light/dark fallbacks.
2. **Theme boundary:** keep the custom player under its existing immersive theme; wrap Hub and
   browser-owned surfaces in the Expressive app theme.
3. **Reusable components:** expressive page scaffold/app bars, grouped list/card containers,
   buttons/icon buttons, selection bars, chips/segmented controls, dialogs, bottom sheets,
   snackbars, empty states, and progress/status treatments.
4. **Hub:** expressive top bar, 16:9 library cards, Downloads status grouping, adaptive bottom
   navigation, browser FAB, More/settings sections, and consistent search/sort/selection motion.
5. **Browser chrome:** compact expressive address field, bottom controls, overflow sheet, pull-to-
   refresh, jump-to-top, badges, and popup/link menus without reducing webpage viewport.
6. **Browser homepage:** expressive search surface, responsive speed-dial/folder states,
   multi-selection, drag feedback, dialogs, and menus.
7. **Full pages:** Tabs, Detected Media, History, Bookmarks, Downloads, Ad blocker, and Settings use
   consistent app bars, grouped expressive list items, shape families, and state transitions.
8. **Motion:** container/selection/press transitions and shared timing. Respect system animation
   scale and avoid decorative motion during rapid browser/download updates.
9. **Adaptive/accessibility pass:** compact/medium/expanded widths, portrait/landscape, font scaling,
   48dp interaction targets, contrast, TalkBack order/labels, and system insets.
10. **Official API gate:** prototype `MaterialExpressiveTheme`, expressive buttons/list items,
    shape-morphing chips, and motion scheme on a separate branch/worktree; adopt only after the full
    unit build and the user checklist pass.

Acceptance:

- Every non-player screen follows one coherent Expressive token/component system.
- Dynamic and fallback color schemes both have readable contrast.
- Compact browser controls remain space-efficient.
- No UI-only conversion changes browsing, download, history, library, or persistence behavior.
- The player remains visually and thematically isolated as requested.

## Material research references

- Android Material 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Compose Material 3 release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- `MaterialExpressiveTheme` API: https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme
- Compose roadmap: https://developer.android.com/jetpack/androidx/compose-roadmap
- Material 3 insets: https://developer.android.com/develop/ui/compose/system/material-insets
