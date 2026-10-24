# Ad blocker: correctness and efficient rule updates

Implemented in `G:\Projects\Miowser`. Unbuilt and untested at the user's request. No build, lint, test suite, device check, benchmark, or stress test was run. Media-detection changes and plan Steps 3–6 are not included here. Earlier unverified scroll-smoothness edits remain in the working tree.

## Implemented behavior

1. **Explicit rule support.** Unknown network modifiers reject the whole rule. They no longer silently remove a restriction and broaden a block or exception. Regex literals (including ones with options), procedural cosmetics and scriptlets outside the supported subset are skipped. Domain restrictions are canonicalized. Contradictory first/third-party constraints and invalid/empty domain constraints are rejected. Manual-rule saves show applied and skipped counts.
2. **Defined precedence.** Network and cosmetic rules use manual exception, manual block/hide, subscribed exception, subscribed block/hide priority, in that order. An explicit manual block therefore overrides a subscribed exception. Exceptions beat blocks within the same source tier. Cosmetic exceptions apply globally or to their domain and descendant subdomains, and retain excluded-domain constraints.
3. **Public-suffix-aware site matching.** Bundled Mozilla PSL data replaces the last-two-label approximation, includes ICANN and PRIVATE rules, and handles wildcard and exception entries. Hostnames are canonicalized to ASCII/lowercase. IP literals are compared without DNS lookup. The list is warmed on a worker before normal browser entry. Registrable-domain results have a bounded 2,048-entry cache.
4. **No per-request candidate sorting.** Four prebuilt domain/generic indexes implement priority and early matching. Each installed generation owns a decision cache (2,048 entries with an additional estimated string-weight budget) and cosmetic CSS cache (128 entries with a weight budget). Request keys include the complete URL, both hosts and inferred resource type. Oversized URLs are matched without retaining them in the cache. Response streams are never reused. Caches are in memory only and replaced with the ruleset.
5. **Atomic publication and failure handling.** A rebuild is prepared separately; a generation check and publication use the same lock. Older builds cannot replace newer generations. A failed rebuild leaves the current complete ruleset and its caches installed. On a first-ever startup without a complete build, curated fallback rules remain available. `isFullyLoaded` describes the installed complete ruleset, not merely whether a new rebuild was requested.
6. **Safer persistence and refresh.** Custom rules and downloaded lists use unique temporary files, synced writes and atomic replacement where supported. Filter-source reads take one coherent storage snapshot. Downloaded HTML/error documents and lists without supported rules are rejected before replacing cached bodies. Concurrent refresh operations are serialized off-main. Observable refresh results carry a revision so older callbacks cannot undo later edits. Refresh errors clear the busy state and are surfaced; background refreshes request retry when needed. Rebuilds that were not applied no longer produce an unconditional success message.
7. **Less request logging.** Removed duplicate per-block URL logging from the request path. Aggregate rebuild counts remain.

## Compatibility boundaries

- Supported network modifiers: existing resource types and exclusions, `xhr`/`other`, first/third-party aliases, and domain inclusions/exclusions.
- `$important`, `$match-case`, `$popup`, `$generichide`, `$elemhide`, redirects, parameter rewriting, arbitrary regex rules and other unimplemented modifiers are **skipped**, not newly implemented. Document-wide exception rules are also skipped because their page-whitelisting behavior is not implemented yet. Supported-rule counts may decrease compared with the old parser; previously ignored restrictions were unsafe to apply broadly.
- Native CSS hiding remains supported, including CSS `:has()` where the WebView supports it. No procedural DOM engine or downloaded JavaScript execution was added.
- WebView resource-type inference remains approximate. These changes do not claim complete compatibility with desktop extension filter engines.
- PSL data is bundled rather than refreshed over the network. Its version is `2026-09-24_13-26-36_UTC`; notices are packaged alongside it. Source: [Public Suffix List](https://publicsuffix.org/list/), algorithm: [official format specification](https://github.com/publicsuffix/list/wiki/Format).

## Build and manual checks for the owner

From Android Studio, build/run the updated working tree. Or in PowerShell:

```powershell
Set-Location G:\Projects\Miowser
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK output: `G:\Projects\Miowser\app\build\outputs\apk\debug\app-debug.apk`. Existing root-level Step 6 APKs do not contain these changes.

1. **Basic browsing:** visit your usual ad-heavy and ordinary sites. Confirm blocking still works and login, navigation, images and video playback remain usable. Compare scrolling with protection enabled and disabled under similar conditions.
2. **Manual precedence:** on a disposable page with a known resource, add a manual block for its host, then an exception for that same host. Reload after each save. The block should apply first; the manual exception should win when both exist. Remove the temporary rules afterward. A manual block should also override a subscribed exception for that resource.
3. **Unsupported options:** save a temporary rule such as `||example.com^$popup` or `||example.com^$unknown-option`. The save message should report it skipped; it must not act as an unrestricted domain block. Remove the rule afterward.
4. **Cosmetic exceptions:** use a controlled page containing a known selector. A generic hide plus `example.com#@#selector` should keep that element visible on `example.com` and its subdomains. Repeat with global and excluded-domain exceptions. Choose domains/selectors from your fixture rather than applying broad rules to daily-use sites.
5. **Cache invalidation:** change/remove a temporary rule and revisit the same resource and page. Network decisions and cosmetic CSS should follow the new ruleset after application/reload, with no restart required. Already downloaded resources may require a page reload to observe a new network block.
6. **Failed refresh:** with a working installed list, go offline and refresh. It should report failure, stop showing busy, and preserve existing blocking. Restore connectivity and retry. An invalid HTML subscription URL should report an error rather than install the page as rules.
7. **Overlapping update:** start a refresh, then change one subscription setting while it is pending. After completion, the newer setting should remain visible and effective. This is a single functional scenario, not a repeated stress test.
8. **Restart and packaging:** restart the app, confirm settings/manual rules persist, and try an offline launch. Confirm the PSL resource is included in both debug and release packaging before distribution.

For a controlled domain-classification fixture, expected relationships are:

| Hosts | Expected |
|---|---|
| `a.example.co.uk`, `b.example.co.uk` | Same site |
| `example.co.uk`, `unrelated.co.uk` | Third party |
| `alice.github.io`, `bob.github.io` | Third party (PRIVATE section) |
| `a.www.ck`, `www.ck` | Same site (PSL exception) |
| `a.foo.ck`, `b.foo.ck` | Third party (PSL wildcard) |
| `192.168.1.1`, `192.168.1.2` | Different hosts |

Automated correctness, controlled rebuild-failure/race coverage, and performance remain unverified. Report build errors or the specific page/rule that behaves incorrectly. Any stress test needs separate approval before the assistant runs it.
