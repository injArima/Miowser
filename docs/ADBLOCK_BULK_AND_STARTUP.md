# Bulk filter lists and restart protection

Source changes in `G:\Projects\Miowser`, 2026-09-27. No build, tests, benchmarks, emulator, or device checks were run, per the owner's instructions. Existing APKs do not include these changes.

## Restart issue found in source

The issue was still possible: `initializeAsync` loaded subscription metadata, queued a full engine rebuild, and returned. BrowserScreen then marked the browser ready and created/loaded its selected WebView. Network requests and document-start cosmetic CSS could therefore use only the small fallback list. A later reload would use the now-complete ruleset.

The browser-entry initializer now awaits `AdBlockEngine.ensureReady`. Concurrent callers share a process-owned initial build; leaving the screen does not cancel that build. Parsing and storage work stay on a worker. BrowserScreen cannot create the first selected engine until the full local ruleset is installed, so document-start CSS and initial network requests see that ruleset. Already-loaded rules return immediately. A failed initial build uses the existing browser startup error/Retry path instead of silently loading pages with incomplete protection.

This can add a visible preparation wait on the first browser entry after a cold start, especially with many lists. It does not wait for a network refresh. The bundled EasyList, cached subscriptions, and saved manual rules are used first. This is a source-level fix; actual first-load behavior is awaiting owner verification.

## Bulk import

Ad blocker → Your filter lists → **Add lists in bulk**:

- Paste one URL per line; whitespace-separated URLs are also accepted. Maximum 50 entries per batch, with a 100,000-character input limit.
- HTTP/HTTPS URLs only. Unsupported schemes, invalid hosts, embedded credentials, invalid ports, and oversized URLs are skipped.
- Names are generated from the host and filename. No name entry is required.
- Existing and within-batch duplicates are skipped. Scheme/host casing, default ports and fragments are normalized; path and query casing and encoded values are preserved.
- Valid entries are saved in one metadata update and enabled. Lists download sequentially on a worker, then protection rebuilds once for the batch.
- Progress and a persistent-in-process summary show additions, duplicates, invalid input, and download failures. Failed URLs remain listed with their existing error/retry controls.
- The draft remains available after completion so failed/invalid entries can be corrected. Pasting it again skips entries already saved; use refresh to retry those entries.
- The existing single-list option remains available. A batch continues if the user leaves the settings page. Saved pending subscriptions survive process death and are eligible for catch-up refresh on the next startup.

## Less maintenance

Included protection requires no added lists. Periodic refresh remains every four days. After local protection is ready, a network-constrained catch-up job is queued when enabled lists are missing an initial update, have an error, or are stale. Bulk completion also schedules maintenance. Retries are bounded to five worker attempts per job; periodic maintenance remains available afterward. Background network work does not delay first-page protection.

No new default third-party subscriptions were added automatically, and the global protection preference remains under user control.

## Owner's manual checklist

Build/run from Android Studio, or run `./gradlew.bat :app:assembleDebug` from the project with Android Studio's JBR configured as JAVA_HOME. Install `G:\Projects\Miowser\app\build\outputs\apk\debug\app-debug.apk`.

1. Paste two valid filter-list URLs and tap **Add and apply lists**. Confirm automatic names, progress, enabled rows, and final results. Confirm the original single-list dialog still works.
2. Paste an existing URL, a repeated new URL, and invalid text alongside a valid URL. Confirm duplicate/invalid counts, one entry per accepted URL, and no loss of existing subscriptions. HTTP paths with different casing must not be incorrectly merged.
3. Add a valid URL that returns HTML or an error. Confirm it is reported as a failed list rather than installed as rules. Retry the saved row after correcting connectivity; remove a permanently invalid row and add its corrected URL.
4. Start a small batch and leave settings. Return and confirm progress or completion is still shown. Restart and confirm saved subscriptions remain.
5. With blocking enabled and a familiar ad-heavy page saved as the selected tab, force-stop/reopen the app, enter Browse, and inspect the **first load without refreshing**. Check both blocked requests and hidden ad elements. Also check an ordinary newly entered page after restart.
6. Repeat a normal cold launch without network access. Browser preparation should use bundled/cached rules without waiting for a download. Restore connectivity and confirm catch-up updates can run.
7. Check manual rules and custom subscriptions still apply after restart. Toggle global protection off/on and confirm the choice persists and behavior follows it.
8. Note any excessive first-browser preparation delay, startup Retry screen, page breakage, or incorrect import summary. Include device/Android/WebView version and approximate number of subscriptions.

No stress testing has been performed or authorized. Any assistant-run stress test requires separate approval.
