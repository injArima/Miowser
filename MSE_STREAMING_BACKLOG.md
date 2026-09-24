# MSE / Blob streaming backlog

Last updated: 2026-09-21

This is intentionally separate from `BACKLOG_IMPLEMENTATION_PLAN.md`. It covers non-DRM media
whose webpage exposes only a `blob:` MediaSource URL while the real HLS, DASH, or direct source is
hidden behind the page's network and Media Source Extensions pipeline.

## Scope

- Build a generic browser capability for authorized, non-DRM streams.
- Do not add host-specific extractors to Miowser's core.
- Do not attempt to capture keys or bypass EME/DRM.
- Validate with public HLS, DASH, and MSE reference streams.

## Work order

### 1. Observe the source before it becomes a Blob

- Instrument `fetch`, XMLHttpRequest, `PerformanceObserver`, `URL.createObjectURL`,
  `MediaSource.addSourceBuffer`, and SourceBuffer MIME/codecs from the document-start script.
- Include page/frame identity and a navigation generation with every report.
- Feed service-worker-visible requests into the same candidate queue where WebView permits it.

### 2. Verify extensionless media candidates

- Probe bounded response samples for HLS, DASH, ISO-BMFF/fMP4, MPEG-TS, and WebM signatures.
- Reject HTML/JSON errors masquerading as media.
- Preserve cookies, User-Agent, Referer, Origin, redirects, and required safe headers.

### 3. Resolve Blob sessions to real sources

- Track each `blob:` URL as a page-bound media session.
- Correlate candidates using frame/origin, timing, SourceBuffer codecs, manifest contents,
  duration, and dimensions.
- Expose explicit states: resolved source, unresolved segment flow, ordinary Blob, and DRM.
- Replace or supplement an unusable Blob result with the verified source in Detected Media.

### 4. Hand resolved sources to the rich player

- Pass the source type and authenticated request identity to Media3.
- Pause webpage media while the rich player is active.
- Re-resolve signed sources after an expiry response instead of retrying a stale token forever.

### 5. Add temporary watch-through caching

- Route Media3 through an app-owned bounded `SimpleCache` / `CacheDataSource`.
- Delete temporary session data on close or expiry.
- Promote cached spans only when the user explicitly starts a permanent download.

### 6. Reuse cached work in downloads

- Seed HLS/DASH recovery state from verified cached segments.
- Fetch only missing parts, then use the existing verification and mux pipeline.
- Preserve pause/resume, notifications, elapsed time, and expiring-link refresh behavior.

### 7. Add SourceBuffer capture only as a fallback

- Stream `appendBuffer` data to native temporary storage without retaining whole media in JS.
- Track separate audio/video buffers, init segments, append order, timestamp offsets,
  discontinuities, aborts, and removals.
- Delete fallback captures on close unless explicitly promoted to a download.

### 8. Failure and DRM UX

- Detect EME/encrypted events and explain that DRM media remains webpage-only.
- Distinguish resolving, unresolved segment flow, expired source, and unsupported capture states.

## Validation matrix

- Public HLS master and leaf playlists, including signed query parameters.
- Extensionless HLS and DASH endpoints.
- Public hls.js/MSE playback.
- Separate audio/video adaptive streams.
- AES-128 HLS.
- Expired source re-resolution.
- iframe and service-worker delivery.
- Ordinary Blob and `data:` regressions.
- Temporary-cache cleanup and promotion to a permanent download.
- DRM sample producing a clear refusal rather than a broken download.
