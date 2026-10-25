# Miowser project status

Last updated: 2026-09-21 (collaboration preferences updated 2026-10-25)

Current working preferences: [Collaboration preferences](docs/COLLABORATION_PREFERENCES.md).

Browser video assistant: [current implementation and cleanup overview](docs/research/samsung-video-assistant-plan-2026-10-24.md).

## Product direction

- Hub is the app homepage.
- Hub has a 16:9 Library of everything opened in the rich player.
- Hub has Downloads with Downloading and Downloaded sections; failed/canceled items stay under Downloading with Retry.
- The Hub's browser button reveals the persistent browser session.
- The browser overflow menu provides a route back to Hub.
- Detected media remains its own browser page and opens in the rich player.

## Current checkpoint

- Browser navigation, persistent tabs, history, bookmarks, popup control, link long-press actions,
  pull-to-refresh, jump-to-top, a native browser homepage, and persistent speed dials exist.
- Speed dials support responsive layout, folders, edit/move/remove, multi-select batch move/delete,
  persistent sorting, and hold-Home focus behavior for both homepage search and webpage URLs.
- Media detection, rich playback, HLS/DASH/direct downloads, retry, live pause/resume,
  multi-connection direct downloads, per-download notifications, time remaining/time spent, and
  download-link refresh exist.
- Detected-media enrichment merges authenticated response metadata, bounded MP4 metadata, adaptive
  manifest details, and live media-element facts. It distinguishes exact/estimated sizes and
  exposes filename, MIME, redirect target, duration, resolution, bitrate, encryption, and quality
  count when those values can be proven or conservatively estimated.
- The rich-player optimization pass adds explicit startup/rebuffering feedback, actionable
  retry/close errors, decoder fallback, lifecycle-aware pause/resume, safer gesture classification,
  and lower-frequency UI/progress updates. It is compile/unit verified and awaits the user's
  focused runtime checklist.
- Hub is the application landing page. Bottom navigation provides Library, Downloads, and More.
  The Library persists player opens and resume progress, captures 16:9 artwork, supports
  search/sort/removal, and marks unavailable local files. Downloads are divided into Downloading
  and Downloaded. More has a separate persisted Hub-settings store.
- Ad blocker has a dedicated browser page, mandatory default protection while enabled, scheduled
  list refresh, URL subscriptions, manual network/cosmetic rules, and plain-text import.
- The shared Material snackbar now has one rounded surface, two-way swipe dismissal, and separates
  action callbacks from dismissal callbacks. The user confirmed the fix on-device.
- Runtime testing is performed by the user from an assistant-provided checklist; the assistant
  runs compile and unit checks unless explicitly asked otherwise.
- Completed downloads now route by file type: media opens in the rich player, APKs show their app
  icon and open Android's installer, and other files use an installed system handler. Download
  preflight also detects active-source and completed-name conflicts, with refresh/replace/renamed
  copy choices. This enhancement is implemented and awaiting the user's runtime verification.
- Commit and push changes regularly, as subsequently authorized by the user.

## Pending work

The detailed implementation sequence and acceptance criteria are recorded in
`BACKLOG_IMPLEMENTATION_PLAN.md`. Steps 1 through 8 are implemented; Step 8 awaits its focused
runtime checklist before active backlog work moves to Step 9.

The generic non-DRM MediaSource/Blob resolution and watch-through caching design is intentionally
tracked separately in `MSE_STREAMING_BACKLOG.md` so it does not reorder this main backlog.

### 2. Resolve generic `.bin` filenames for some webpage videos

- **Status:** Complete. The user confirmed the previously reproduced download now works.
- A shared `DownloadIdentityResolver` now combines RFC 5987/regular Content-Disposition names,
  final redirected URL, original URL, declared MIME, WebView's suggestion, and a bounded 64 KiB
  payload signature.
- Generic `.bin`/dynamic endpoint identities can now resolve to MP4/M4V/MOV, WebM/Matroska,
  common audio/subtitle/image types, ZIP, or APK. Unknown bytes deliberately remain `.bin`.
- Direct downloads resolve the final filename and MIME before creating their MediaStore or legacy
  output, so the visible download row and saved file use the same corrected name without a later
  rename.

### 3. Add true download recovery after process death

- **Status:** Implemented in code and compile/unit verified; awaiting the manual process-kill matrix.
- Direct range downloads reopen a private sparse payload and continue only missing bytes using
  `If-Range`; incompatible validators, length, prefix fingerprint, or range behavior trigger a safe
  clean restart instead of combining sources.
- HLS and DASH retain atomically completed, length/digest-marked segments in private storage and
  fetch only missing verified items before muxing. Partial `.tmp` items are never reused.
- Paused work restores paused; interrupted queued/downloading/muxing work with a valid plan restores
  paused and resumable. Corrupt or absent recovery state stays an explained failed record.
- Transfer plans retain elapsed active time and output identity separately from presentation
  history. Old history records remain readable after the request-metadata codec expansion.
- User-Agent and Referer are persisted as non-secret reconstruction metadata. Raw cookies are not
  persisted; the queue reacquires the current session from WebView's CookieManager before resume.
- Cancel and delete clear private recovery state, while ordinary failures retain verified progress
  for Retry.

### 4. Support WebView-only download sources

- **Status:** Implemented for real Blob objects and `data:` URLs; awaiting the user's runtime
  checklist confirmation.
- Blob transfers use page-bound UUID tokens and ordered 48 KiB chunks with a 256 MiB safety cap.
  Navigation, cancellation, malformed/out-of-order data, overrun, and inactivity all remove the
  partial output.
- `data:` URLs decode natively with a 32 MiB cap and byte-correct percent/Base64 handling.
- Both paths reuse download naming, conflict handling, pause/resume, progress, notifications,
  public Downloads output, persistence, and completed-file behavior. Payload URLs themselves are
  deliberately not persisted.
- **Known boundary:** MediaSource/MSE object URLs are not Blob files. They fail clearly and still
  require segment capture/reconstruction or use of the grabber's detected HLS/DASH source.

### 5. Complete DASH addressing coverage

- **Status:** Implemented; awaiting the user's runtime checklist confirmation.
- DASH detection and downloads support VOD `SegmentTemplate`, `SegmentList`, and common single-file
  `SegmentBase` manifests.
- Version 0/1 MP4 `sidx` indexes resolve into strict HTTP ranges for separate video/audio tracks,
  which are downloaded concurrently and passed through the existing mux pipeline.
- Relative BaseURLs, redirects, authenticated requests, pause/resume/cancel, malformed-index cleanup,
  and explicit DRM rejection are covered.
- **Known boundary:** an external `RepresentationIndex` resource is rejected. The common inline-index
  form, where index and media ranges are in the representation MP4, is supported.

### 6. Close the direct-media probe gap

- **Status:** Implemented; awaiting the user's runtime checklist confirmation.
- Direct candidates now use HEAD first with cookies, User-Agent, Referer, redirects, MIME, size, and
  Content-Disposition metadata preserved.
- Rejected or inconclusive HEAD responses use a hard-capped 2 MiB ranged GET. The connection is
  disconnected before stream close, so a server that ignores Range cannot turn verification into a
  full-file transfer.
- Sample bytes feed the shared download identity resolver; false extension/HTML responses are
  rejected, server filenames are retained, and exact/estimated/unknown sizes stay distinct.
- Focused probe tests, the complete unit suite, and the debug build pass.

### 7. Finish rich-player track/source controls

- **Status:** Implemented in code and compile/unit verified; awaiting the runtime stream matrix.
- Fit/Zoom, playback speed, seek, double-tap seek, brightness, volume, rotation, rotation lock,
  control lock, full-screen system-bar behavior, and player download action remain intact.
- Subtitle, audio, and source controls now observe live Media3 track groups and enable only when
  choices exist. Their sheets show available language, forced-caption, channel, codec, resolution,
  and bitrate information.
- Subtitles support Off/Auto/specific selection. Audio and video support Auto/specific selection;
  Auto clears overrides and restores Media3 adaptation without rebuilding the MediaItem.
- A global subtitle default and per-Library-item explicit overrides persist across player sessions.
- Separate grabber URLs are not automatically grouped: same-page detection is insufficient proof
  that an ad, preview, alternate encode, and the selected title are interchangeable. External
  source switching waits for a trustworthy same-media identity and source-specific headers.

### 8. Player optimization pass

- **Status:** Implemented in code and compile/unit verified; awaiting the runtime checklist.
- Startup/rebuffering states, error/retry UX, decoder fallback, lifecycle pause/resume, gesture
  direction and system-edge protection, screen-on state, position polling, and Library write
  frequency have been hardened without redesigning the custom player.
- Lower-end-device and long-session behavior remain runtime checks rather than code assumptions.

### 9. Verify pre-Android-10 storage on real API 26-28 hardware

- **Status:** Code-reviewed and compile/unit verified. API 26/28 runtime testing is deferred at the user's request; revisit only if an issue appears.
- The permission request, legacy public-Downloads path, and media scan are implemented. Legacy
  output now atomically reserves a free filename so unrelated files cannot be overwritten, and
  the visible record reflects an automatically suffixed filename. Deletion requests a rescan.
- This remains unverified on an actual pre-Q device/emulator image; the user chose to defer that matrix.
- Test direct, HLS, DASH, multi-connection, pause/cancel/delete, visibility in Downloads, and denied
  storage permission behavior.

### 10. Complete browser privacy, permissions, and file-handling hardening

- **Status:** Implemented in code and debug build/unit verified; manual browser matrix pending.
- Settings now contains clear browsing data (history, cookies/site storage, cache, form suggestions,
  site decisions, download records, Library records) with explicit checkboxes and confirmation.
  Download and Library records are unchecked by default. Clearing download records leaves files.
- Camera, microphone, and coarse location requests show exact-origin prompts with Allow once,
  Always allow, and Block. Remembered decisions are listed on a separate page and can be forgotten.
  Unsupported WebView permission resources are denied with an explanation. Android runtime permission
  requests still gate actual hardware access.
- Private tabs are visually marked, excluded from tab restore, browser history, and automatic player
  Library writes. A crash marker purges shared site data on the next launch. The user selected the
  lighter shared-storage model: cookies/site storage/cache/form data are cleared at private-session
  entry and exit, which also logs out ordinary tabs. Downloads made in private tabs persist normally.
- Web form uploads use an Activity Result file picker; selected content URIs are bounded and checked
  against app-owned authorities. External app links require confirmation and unsafe schemes are
  blocked. SSL errors fail closed; main-frame network errors have retry UI.
- JavaScript, cookies, and popups have persisted browser controls. Public output filenames and MIME
  types are validated at the shared storage entry point; direct-file identity already uses response
  headers and signatures to avoid false extensions.
- After History is cleared, revisiting an already-loaded normal tab records its completed page again
  without requiring a reload; private, home, failed, and in-flight pages remain excluded.
- Platform limits still need manual review: WebView does not provide equivalent site-level prompts
  for every notification/clipboard access, protected-media identifiers are denied, and this chosen
  private mode is not an isolated browser profile. The browser UI's full accessibility/font/inset
  matrix remains a user runtime check, listed in STEP10_BROWSER_TEST_CHECKLIST.md.

### 11. Deferred grabber and tab-manager expansions

- 11A code complete, runtime check pending: successful page loads now scan bounded raw HTML for
  media URLs. Detected Media also has a user-started site scan with selectable depth (0–2) and
  page cap (4/8/16), live progress and Stop. Page traversal stays on the exact origin, follows
  embeds and likely media-detail links, checks redirects, and is capped at 45 seconds, 512 KiB
  per page, and 64 media candidates. Candidates join the existing deduplication, ad-block, and
  MediaProbe verification path; scanning never starts a download. This cannot extract URLs that
  exist only after JavaScript runs or inside opaque MSE/blob streams. See
  STEP11A_MEDIA_DISCOVERY_TEST_CHECKLIST.md for the user's runtime checks.
- Detected Media now has a persisted 0–30 minute minimum-length slider in 30-second increments.
  A non-zero threshold shows only playable media whose duration is known and meets the minimum;
  subtitles remain visible. Duration comes from HLS/DASH manifests, live media elements, MP4
  metadata, and newly added WebM/MKV metadata parsing. Direct-file prefix/suffix samples are capped
  at 2 MiB, so a malformed file or metadata outside both bounded windows can still show “Length
  unavailable” rather than an invented value.
- 11B code complete, runtime check pending: Tabs is now an adaptive thumbnail grid with active and
  private-state treatments plus new/close/select actions. A tab captures a lightweight preview
  while its visible page loads or scrolls, and again before Tabs opens; the underlying WebView
  remains composed and alive. The capture reads the displayed viewport through PixelCopy, and
  the grid starts at the active card before its first layout.
  Regular previews use a separate cache identity persisted beside the tab URL/opener relationship,
  while image bytes stay in the app cache. The cache is capped at 30 previews and 12 MiB on disk and
  30 decoded previews in memory; closing a tab removes its preview. Private previews stay only in
  memory. Legacy two-field tab sessions still decode. See STEP11B_TAB_THUMBNAIL_TEST_CHECKLIST.md.
- 11C code complete, runtime check pending: normal tabs unused for 14 days and older duplicate-URL
  tabs become lightweight inactive records, releasing their WebViews and cached previews. The active
  tab, private tabs, and native-home tabs are protected. Tabs shows an Inactive entry with count;
  tapping a record reloads it as a fresh active tab, while its trailing delete removes only that
  record. Records expire after 30 days by default, controlled by “Delete inactive tabs after 30
  days” in Browser Settings. Session migration keeps prior two/three-field records valid. See
  STEP11C_INACTIVE_TABS_TEST_CHECKLIST.md.

### 12. Material 3 Expressive UI outside the player

- Stable-compatible Expressive pass implemented: full typography and rounded shape scales,
  cohesive light/dark fallback surface palettes, shared section cards and empty states, selected-
  state motion, and themed Hub, browser chrome, homepage, overflow, snackbars, tabs, settings, and
  full-page app bars. Dynamic wallpaper color remains active on Android 12+.
- The immersive player now has its own legacy theme boundary; its typography, shapes, and fallback
  colors are not changed by the non-player conversion.
- Code compiles and existing unit tests pass. Visual, font-scale, orientation, dynamic-color, and
  navigation checks remain for the user; see STEP12_MATERIAL_EXPRESSIVE_TEST_CHECKLIST.md.
- Official Material 3 1.5 Expressive APIs remain alpha as of Sep 2026. The stable-compatible layer
  does not need them for this pass; revisit the wrappers when 1.5 stabilizes or a concrete component
  need justifies an alpha prototype.

### 13. Release audit and product polish

- Run a fresh end-to-end functional audit after the current feature wave, including process death,
  orientation, low storage, offline transitions, multiple concurrent downloads, accessibility,
  and supported Android versions.
- Replace the placeholder launcher/play glyph with final Miowser branding.
- Review performance, memory/WebView lifecycle, error wording, empty states, Material You contrast,
  touch targets, and screen-reader labels.
- Clean development screenshots/traces from the repository root.
- Create a commit checkpoint when explicitly requested; substantial completed work after commit
  `4a4c095` is currently still uncommitted.
