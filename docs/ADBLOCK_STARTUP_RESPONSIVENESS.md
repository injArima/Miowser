# Ad-block startup and multi-list processing — 2026-09-27

This document supersedes the earlier eight-active-list / automatic-pausing policy. Source changes only: no build, automated checks, device verification, timing measurements or stress tests were run. Existing APKs do not include these edits.

## User-visible behavior

- Enabled subscriptions stay enabled. The engine no longer pauses lists based on list count or the sum of their downloaded sizes/rule counts. Bulk addition enables every accepted new subscription.
- A one-time migration restores rows explicitly marked by the old automatic responsiveness-pause policy. It leaves other disabled rows alone. Old bulk imports did not label automatically disabled rows reliably, so those ambiguous rows must be enabled by the user rather than overriding potentially deliberate choices.
- Browser startup schedules filter preparation without waiting for parsing or downloads. Basic built-in safeguards are immediately available when blocking is on; full installed lists take effect as preparation completes. Settings display loading, active and failure states.
- Subsequent requests use the new generation immediately. Cosmetic filtering refreshes on open pages without an automatic reload. Already-completed requests cannot be blocked retroactively.

## Processing and lookup

1. **Compile each source once per content version when possible.** Validation and engine assembly share `ParsedFilterCache`. Its app-private, content-addressed binary entries contain parsed rules and their regex representations. The key includes content, source priority tier and parser schema. Identical content at different subscription URLs shares an entry. Cache loss/corruption triggers parsing; it does not delete or disable the subscription. Maintainers must bump the parser-cache schema when parsing semantics change.
2. **Reuse the assembled index on restart.** An app-private snapshot stores the assembled network/cosmetic indexes. Enabled-list identities, file size/timestamps, manual rules, app update time and schema invalidate it. The in-process installed generation is reused when unchanged. Regex restoration and deserialization still cost time; this is not an instant-loading guarantee.
3. **Deduplicate during assembly.** Identical network rules within each source-priority tier and identical cosmetic rules enter the installed generation once. Manual and subscription precedence remain separate. The engine's capacity guard counts unique admitted rules, rather than rejecting overlapping subscriptions by adding their raw rule counts.
4. **Use indexed request matching.** Host suffixes look up domain buckets. Mandatory literal tokens narrow generic rules to candidates. Candidates still undergo full pattern/options matching, preserving exception and manual-rule precedence. Unicode/very long URLs use the full-matcher fallback. Matching all arbitrary filter syntax is not an O(1) operation.
5. **Retain native WebView interception.** Requests continue through the existing `shouldInterceptRequest` path; the engine does not scan raw filter files per request. Chromium extension APIs are not being added to this Android WebView app.
6. **Defer cosmetics.** The document-start script registers a scheduler rather than fetching CSS immediately. After DOMContentLoaded, it uses requestIdleCallback with a bounded timeout, or a short timer fallback. Pending refreshes are coalesced and unchanged style text is not reassigned. Page-finish injection remains the fallback. Empty ad spaces may be visible briefly. CSS retrieval still uses the synchronous bridge at that later point; this is not a fully asynchronous CSS engine.
7. **Control background work.** Engine builds run serially on a background-priority thread. Source bodies are read one at a time. Downloads are serialized; unchanged bodies are not rewritten. Refreshes prioritize lists deferred by the previous batch deadline, then unfinished/failed and older lists, so successful early entries do not continually push unfinished entries to the end.

## Guardrails that remain

These protect admission and background processing; they never flip an enabled switch off:

- Up to 50 saved custom subscriptions for new additions, 8 MiB per downloaded list, 120,000 parsed rules per source, 256 KiB for new manual-rule saves/imports. Legacy larger manual files remain readable up to the existing read limit.
- At most 250,000 **unique** rules in an assembled generation, plus a per-priority-bucket limit on rules without usable indexes. An over-capacity update fails explicitly and leaves the currently installed generation active. On a cold start without a usable full generation, basic safeguards remain active and the failure is displayed. Keeping a subscription enabled does not mean a failed download or over-capacity update was applied.
- Malformed/unsupported or excessively complex patterns are skipped as documented by the parser. Oversized sources are rejected whole, not truncated in a way that could discard exception rules.
- Parsed cache entries are limited to 32 MiB each and the disposable parsed cache to 96 MiB with oldest-entry eviction. The assembled snapshot reader has a 48 MiB limit. Losing a cache causes background reconstruction, not a change to user settings.
- Download byte limits, connect/read timeouts, a body-read deadline and a batch deadline remain. Failed/deferred rows display an error and remain eligible for retry. A socket read can exceed the batch deadline by its timeout.
- Newly scheduled catch-up downloads wait 15 minutes. New periodic schedules start after four days; already-scheduled WorkManager jobs are retained.

These limits are not measured performance guarantees for every device. The current implementation still uses an in-memory object index; a much larger ruleset may ultimately need a more compact or disk-backed representation. No claim of unlimited list capacity or measured speedup is made.

## Manual checks for the user

1. Build/install the current source from Android Studio, keeping app data. No new APK was assembled by the assistant.
2. Inspect your existing enabled lists, including more than eight if you already have that many. Confirm no count-based pausing and that intentionally disabled rows stay off. Check the migration note above for old unmarked bulk-import rows.
3. Force-stop and reopen. Confirm browser navigation responds while filters prepare. Let preparation complete, then repeat an unchanged restart and compare both browser-opening and full-readiness times.
4. Refresh one normal list or edit a small manual rule. Confirm the status returns to active and the intended blocking/exception behavior changes. Unchanged sources should reuse their parse cache; this is an implementation property, not a timing assertion you must infer from the UI.
5. Open a normal page during preparation, then after readiness. Confirm new requests receive filtering and cosmetic hiding appears after DOM readiness without interrupting navigation. Compare a reload while accounting for already-completed requests.
6. Add a small ordinary batch containing a duplicate URL. Confirm new rows are enabled, the duplicate is skipped, and progress/errors are readable. If a prior batch had unfinished downloads, use Refresh all and confirm those are attempted before previously successful entries.
7. Browse offline after a successful preparation, then return to Hub and reopen the browser. Cached preparation must not depend on a network update.
8. Check scrolling during ordinary loading, global blocking off/on, a per-site override and a manual exception. Report device model, Android version, enabled-list/rule counts and launch/readiness times if lag persists.

Large imports, maximum-capacity benchmarks and deliberate corruption exercises remain separate stress tests that require approval before the assistant runs them.

Broader media-detector work remains in progress; this change addresses multi-list browser usability.
