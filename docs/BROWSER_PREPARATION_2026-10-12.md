# Browser preparation after Library

Date: 2026-10-12. The earlier audit plan remains deferred. This change targets first browser entry without moving costly startup ahead of the Library.

## Findings and implementation

Previously, browser entry restored inactive tabs, history, bookmarks and speed dial, then waited for saved ad-block rules. These stores already retained their successful loads within the process; filter parsing also already had a disposable persistent cache. No device trace was collected to attribute the reported 30–60 seconds to a particular stage.

The new process-wide preparation pipeline reuses those caches earlier. It starts only after the Library projection is ready, the grid or empty state has drawn, and the visible Library has stayed idle for 1.5 seconds. Each uncached stage also waits for the main message queue to become idle. Native provider preparation, saved data and filter initialization run sequentially rather than launching competing loads.

WebView provider preparation uses AndroidX WebKit 1.17's asynchronous API, on one background-priority worker whose thread expires after ten idle seconds. Passive preparation explicitly excludes UI-thread startup tasks. There is no hidden WebView, restored-page navigation, renderer pool, service, new Android process or speculative URL fetch. A separate process would not warm the WebView startup state used by this process. [Android's startup guidance](https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup) explains the background-only option and subsequent completion of UI startup work.

Low-RAM devices, memory classes below 192 MiB and current low-memory conditions skip optional preparation. Scrolling, leaving Library, covering it with the player/settings or leaving the resumed lifecycle cancels the passive coroutine. Already-issued native/worker work may finish; cancellation prevents subsequent stages from being issued. Returning to a settled Library can resume incomplete stages. A passive failure does not cause an automatic retry loop.

Explicit browser entry shares the same stage receipts. Completed stages are reused; failed or cancelled stages can be retried without reloading earlier successful stages. A tap before warm-up finishes waits for the remaining work, without the optional idle delay. Existing Retry/error handling stays in place. Protection is checked again against current settings, including enabling it after a disabled warm-up.

Full WebView startup is awaited before private-session cleanup or other cookie-dependent transfers use WebView APIs. The existing privacy barrier remains; no page loads before it and saved protection are ready. This also prevents a first CookieManager/private-cleanup call from bypassing async startup. AndroidX handles provider-version differences; older providers may offer less background preparation. [API reference](https://developer.android.com/reference/androidx/webkit/WebViewCompat).

Native readiness and prepared records are process-memory caches; they are rebuilt after process death. They are not persisted as a potentially stale “browser ready” flag. Existing disposable filter caches remain removable through Android Clear cache. Clearing cache does not delete saved browser records or downloaded media.

Debug logs tagged `Miowser/BrowserStartup` report provider/background completion and per-stage durations without URLs or saved record contents. These allow the user's remaining delay to be narrowed to a preparation stage; they do not measure page network load or first visible frame.

## Verification

**508 JVM tests passed**, with zero failures/errors/skips. The five new preparation tests were also rerun successfully after removing unused test expressions. Lint reported zero errors, 80 warnings and five hints. Debug packaging and the R8 unsigned release build succeeded in 3m 36s; the final targeted test/lint check succeeded in 30s. Scoped whitespace checks passed.

The source comparison against the preceding bottom-bar build found only the three intended existing-file changes, plus three new preparation classes and their test. Packaged DEX files contain the new startup code. The earlier Library inspection cache, bottom-bar changes and disabled automatic audio-focus handling remain. Evidence, full/targeted test reports, source diff, 327-file source manifest and APK hashes are saved under `G:/Projects/Miowser/.local/optimization/browser-preparation-2026-10-12-*`.

New JVM tests exercise concurrent warm/explicit entry, stage order, cancellation with completed-stage reuse, failed-stage retry, idle eligibility and successful cache hits. They do not execute the Android provider, lifecycle or memory APIs. No emulator, device, ADB, instrumentation or on-device benchmark was run.

## APK

[Installable debug APK](G:/Projects/Miowser/Miowser-0.1.0-browser-preparation-debug.apk), 27,287,823 bytes.

SHA-256: `79058b37e39f39cd101418718285a66712993f3cb6ea47227892497bf69506c9`.

The release APK is unsigned and retained as build evidence; use the debug APK for the manual checks.

## Device checklist

- [ ] Cold-start into a populated Library and an empty Library. Library appears before optional browser work begins; its controls and scroll remain responsive.
- [ ] Leave Library idle, then open Browser. Compare with tapping Browser immediately on launch. Warm entry should spend less time in “Opening browser”; early entry may still await remaining work.
- [ ] Repeat Browser → Library → Browser and recreate the activity. Prepared data is reused without duplicated bookmarks, speed dials/history or tabs.
- [ ] Scroll Library before and during preparation, change folders/search/sort, switch Downloads/More, and open playback/settings. No competing hidden page loads or continuing sequence of new preparation stages occurs while Library is busy/covered.
- [ ] Background and return during the 1.5-second idle period and during preparation. Library is usable and incomplete stages can resume; no stuck browser spinner or stale callback alters the current page.
- [ ] Tap Browser during provider preparation and during filter initialization. It joins current work and finishes once; Back can return to Library while initialization completes.
- [ ] Test restore-tabs enabled/disabled, an empty session, many saved tabs, built-in Home and a custom home URL. Only the selected page is loaded on actual browser use.
- [ ] Check saved bookmarks, history, inactive-tab policy, speed dial, site permissions, custom filters and manual rules. First-page protection still works.
- [ ] Disable blocking before launch, let preparation finish, then enable it before browser use. Saved protection is loaded before browsing.
- [ ] Interrupt a private session, reopen the app and enter Browser. Private-session cleanup still completes before a page loads; normal/private entry and exit retain their existing behavior.
- [ ] Open remote Library playback and start/resume cookie-dependent downloads before Browser. The shared privacy/provider barrier remains usable and does not deadlock.
- [ ] Clear Android app cache, force-stop and reopen. Cached filter data can rebuild, saved records/media remain, and optional preparation happens after Library again.
- [ ] Check low-memory devices and memory-pressure/background-return scenarios. Optional warm-up is skipped/deferred; explicit browser entry still works.
- [ ] Check an older/current Android System WebView provider and offline startup. Saved records and cached protection prepare locally; actual website requests still depend on the network.
- [ ] If a preparation/provider error occurs, Library remains usable and browser Retry recovers without duplicate records.
- [ ] Recheck bottom-bar motion, library inspection-cache behavior, subtitles/playlists and the requested disabled automatic audio-focus handling.
- [ ] Time first cold entry, entry after an idle Library and repeated warm entries on the same device. If still slow, share the visible stage and the `Miowser/BrowserStartup` durations; record network/page loading separately.
