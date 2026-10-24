# P0 implementation and verification

Date: 2026-10-08. Scope: items 1 and 2 of the [performance optimization plan](G:/Projects/Miowser/docs/APP_AUDIT_OPTIMIZATION_PLAN_2026-10-08.md). Other optimization stages and animation changes are outside this patch.

## Changes

### Android 8.0 thumbnail compatibility

[LibraryThumbnailStore.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/library/LibraryThumbnailStore.kt) uses scaled frame extraction on API 27 and later. API 26 uses a nearby keyframe only when metadata gives valid dimensions within the 4 MP / 4,096-pixel-edge limit. It scales into the existing 640 × 360 artwork canvas and recycles the full frame in `finally`. Unknown dimensions and larger videos keep the existing placeholder/player-capture fallback instead of attempting an unrestricted full-resolution allocation. The minimum SDK stays 26.

This is a bounded compatibility fallback, not a decoder-memory guarantee for malformed media or a claim that API 26 has the same extraction quality/performance as newer Android. Codec/device behavior remains a device check. The API distinction and keyframe choice follow the [Android retriever reference](https://developer.android.com/reference/android/media/MediaMetadataRetriever#getScaledFrameAtTime(long,%20int,%20int,%20int)).

### Media3 opt-ins

The timed `DataSpec` helper now lives inside the already opted-in [PlayerWindowPrefetcher](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerWindowPrefetcher.kt), including its generated data-class members. [PlayerTrackSelectionSheet](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt) explicitly opts into the Media3 types in its signature. No project-wide lint suppression or minimum-SDK increase was added.

### WebView renderer recovery

- Both the normal [WebView client](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/MiowserWebViewClient.kt) and the [popup proxy](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt) handle `onRenderProcessGone` and acknowledge handling it.
- The controller detaches/destroys the supplied engine without first calling normal page methods on the failed renderer. Engine retirement checks instance identity, so a late callback cannot release a newer replacement. Android calls the hook for each affected WebView when a process is shared; this code does not infer that every tab is affected. See the [WebView callback contract](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail)).
- The tab retains its address, title, identity, opener relationship, and saved preview. Loading/navigation flags and page detections are cleared; discovery and pending preview work are invalidated. The failed page shows a native error with **Reload**. Switching back does not silently acquire another engine.
- Reload and explicit navigation can acquire a fresh WebView. The Compose host is keyed by engine identity as well as tab identity, and its scroll callback rejects a retired engine.
- Retired navigation, title/progress, grabber, link, permission, and popup-route callbacks cannot republish failed-page state. Pending file, certificate, geolocation, new-window, fullscreen, and blob UI handoffs are cleared for their owner.

### Page-generated transfer failure

[RendererResourceDownloader](G:/Projects/Miowser/app/src/main/java/com/miowser/app/downloader/RendererResourceDownloader.kt) captures page authorization when a transfer is attached to the queue. Its worker cannot silently use the generation of a later document or a replacement bridge. Renderer retirement permanently revokes the old bridge; navigation also invalidates queued old-page transfers.

Invalidation clears transfer closures holding the old tab/WebView, wakes paused workers, records a failure with an explanation, abandons partial blob output, and persists changed records. The download queue does not automatically retry a revoked renderer source. A failed transfer remains **Failed**, rather than becoming **Canceled** solely because its page disappeared. Completed output and independent HTTP/HLS/DASH downloads remain intact. A transfer that already finished writing valid output can complete before retirement; it is not deleted afterwards.

## Local verification

The final offline verification command passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --offline --no-daemon --console=plain --max-workers=1
```

| Check | Result |
|---|---|
| Full debug unit suite | 398 tests; zero failures or errors. |
| Debug lint | Zero errors; 81 warnings. |
| Release lint | Zero errors; 82 warnings. |
| Debug build | Passed; locally signed installable APK. |
| Release build | Passed with R8 and resource shrinking; unsigned APK, not the device-check artifact. |

[Debug lint report](G:/Projects/Miowser/app/build/reports/lint-results-debug.html), [release lint report](G:/Projects/Miowser/app/build/reports/lint-results-release.html), [unit test report](G:/Projects/Miowser/app/build/reports/tests/testDebugUnitTest/index.html). These generated reports may be replaced by subsequent builds.

Three `MissingOnRenderProcessGone` warnings remain in each variant on the WebViewClient superclass constructor calls, although both clients now override the callback. The installed AndroidX WebKit 1.17.0 lint detector's `visitConstructor` reports these calls unconditionally, while its class-level implementation check recognizes the new overrides. Inspected that detector and confirmed the normal client's compiled `boolean onRenderProcessGone(WebView, RenderProcessGoneDetail)` method. These are recorded, unsuppressed constructor false positives; the other warnings remain outside this P0 scope. The gate is zero errors, not zero warnings.

The first combined run hit an internal lint/UAST analysis exception. A serial final run over settled sources passed without disabling checks or changing dependencies. No emulator, installation, renderer termination, or physical-device measurement was performed by the assistant.

New regression coverage exercises:

- A delayed renderer callback after Reload, and duplicate/shared-process callbacks preserving an unrelated engine.
- Old queued page tokens, permanent bridge retirement, replacement bridge independence, and repeated cleanup.
- Paused-worker wakeup preserving failure status; queued transfers releasing their closure; completed download state/progress remaining intact.
- API-26 fallback limits with HD/portrait, unknown/invalid, oversized, and overflow-sized metadata.

These tests validate native ownership/policy behavior. They do not simulate Chromium, a codec, MediaStore publication, or the actual Compose recovery screen.

## Device checklist — user verification

Run these against [Miowser-0.1.0-p0-debug.apk](G:/Projects/Miowser/Miowser-0.1.0-p0-debug.apk), copied from the successful final debug build (27,998,692 bytes).

SHA-256: `E15AA8214C6028ACE1D5062D667A073BD85AA869F8977880525B5660C4E7D211`.

### Normal behavior

- [ ] Open several pages, switch tabs, use Back/Forward, reload from the URL bar, and pull to refresh. Warm tabs stay loaded; ordinary loading/error recovery still works.
- [ ] Open/close/undo tabs, follow a page-created new window, and test the site's “open links here” behavior. Successful routes still navigate once; a failed route does not trigger a delayed navigation.
- [ ] Check media detection, subtitle selection, link text selection, file upload, site permission prompts, and browser fullscreen video. Native player playback and independent downloads still work.

### Controlled renderer loss

Android documents `WebView.loadUrl("chrome://crash")` as a renderer-crash test. Invoke it on a test WebView through a main-thread Android Studio debugger/evaluation or your own test harness. For example, break in `MiowserWebViewClient.onPageFinished`, evaluate `view.loadUrl("chrome://crash")`, then resume. **Typing this into the URL bar is not equivalent:** normal URL routing intentionally restricts internal schemes. Use a disposable test page; this may terminate other views sharing its process.

- [ ] Crash the active test page. The app remains open, loading stops, the URL/title remain, and **Page unavailable → Reload** appears. Reload opens the original address successfully.
- [ ] Repeat with multiple tabs. Each affected tab gets recovery; a genuinely unaffected tab retains its engine. Switching to an affected background tab does not automatically reload it.
- [ ] Repeat a failure, then Reload quickly and switch tabs. Old title/progress/media results must not reappear, and a delayed callback must not destroy the fresh page.
- [ ] Crash with a new-window confirmation, “open links here” popup route, file picker, location/camera/microphone prompt, and fullscreen page video pending. No stale prompt, fullscreen overlay, late grant/upload, or delayed popup navigation should survive.
- [ ] Crash while a page-generated blob download is actively transferring. It fails clearly, its partial output is removed, and another queued job can proceed once that worker unwinds. It must not spend time reconnecting to a dead page.
- [ ] Pause a blob download, then crash its page. It becomes **Failed**, its worker wakes, and its notification/Downloads entry stays coherent. Retry explains that the page must be reopened; redetecting/downloading from the reloaded page works.
- [ ] Keep a page-generated transfer queued behind other work, then navigate its page or crash its renderer before that worker starts. It fails without invoking the old renderer or downloading content from a replacement document.
- [ ] Complete a page-generated download before crashing, and run an independent direct/HLS/DASH download during the crash. Completed media remains available and independent transfers continue.
- [ ] Restart after recovery/failure. Tab records and completed files remain; failed renderer downloads do not regain a stale renderer callback.

### API 26 thumbnails

- [ ] On Android 8.0, open downloaded HD and portrait videos in the Library/Downloads grid. Artwork is correctly proportioned; no unavailable-API error occurs.
- [ ] On Android 8.0, use 4K and missing/malformed-dimension samples. Placeholder/player-captured artwork remains usable without an unrestricted thumbnail decode attempt.
- [ ] On API 27+, check that ordinary midpoint extraction and cached artwork still work.

## Performance interpretation

P0 closes compatibility and recovery gaps. It prevents stale work and retained renderer references, but it does not establish an app-wide speedup. Current startup, scrolling, playback, and memory measurements remain with the user; P1/P2 contain the principal throughput/frame-time work.
