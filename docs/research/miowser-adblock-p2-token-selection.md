# P2: frequency-aware URL-rule tokens

Implemented on 24 October 2026 following the 1DM investigation. Device testing
remains with the user; P0 baseline measurements were not performed.

## Implementation

- A rebuild counts required ASCII literal windows from existing URL-rule objects
  using a fixed 65,536-entry integer histogram (256 KiB). It then chooses a
  relatively rare five-character window for each rule, or a three-character
  window when the rule has no five-character literal. It does not reparse sources
  or construct a second complete rule list.
- The histogram is approximate. Collisions may affect bucket efficiency, but the
  lookup key remains exact and every candidate still runs its full predicate.
  Tokens never cross wildcards, separators or the start of filter options.
- Domain buckets and their native prefilter remain unchanged. The optimization
  targets URL rules that otherwise share global token buckets. Manual/subscribed
  priority, exceptions, resource types, party and domain constraints are retained.
- Rehashing moves existing bucket references instead of copying their rule lists.
  Duplicate-key inserts no longer trigger unnecessary table growth. Replacement
  tables are installed only when that tier is complete; insufficient headroom
  leaves compatible buckets in use. The histogram is not retained after building.
- `compiled-v10.bin` saves the selected buckets and blocked-page source metadata directly. The first updated launch
  rebuilds this cache once; later valid cache loads bypass frequency analysis.
  Earlier cache versions are invalidated without deleting source lists/settings.

Allow/block decisions and source priority are preserved. If several rules in one
priority tier match, a different equally authoritative rule can be reported as
the first match because bucket selection changed.

## Verification

Local tests compare decisions before/after optimization, including the bundled
EasyList, wildcards, short literals, options, manual exceptions, Unicode/long URL
fallback and snapshot restoration. The fixed shared-prefix regression reduced
potential full-predicate candidates from 2,000 to 0 for an unrelated URL. This is
a deterministic work-count check, not a claim about device latency or overall
browser speed. Local checks use a 192 MiB test-heap override.

Final local verification: all 95 adblock tests passed, and debug/development Kotlin
compilation passed. Memory regressions cover restoring 30,000 optimized URL rules
alongside their previous generation and the existing 60,000 domain-pattern rule
replacement. Attribution tests cover duplicates, hosts conversion, manual
precedence, more than 64 sources, source persistence and malformed cache data.

## Device checklist

1. Allow preparation to finish once, then restart twice without changing lists.
2. Check normal pages, ad blocking, custom rules and site exceptions.
3. Refresh/toggle a list and confirm its behavior survives a restart.
4. Check streaming playback while browsing or updating lists on a lower-end device.
5. Recheck the previously blank website and any blocked-page explanation.

The player fixes from the previous task remain in place. String-matching fast
paths and ZIP source storage are outside P2.

## Blocking-list drop-down

The blocked-page screen now has an expandable **Blocking lists** section with list
names, source URLs and the matching rule. It identifies all matching blockers in
the priority tier responsible for the block, including a rule shared by multiple
lists. Custom rules and Miowser's built-in fallback have their own source labels.
It does not list irrelevant rules or subscription blocks superseded by a manual
exception. Normal subresource requests do not collect this explanation.

Each network rule stores one integer group reference. Names/URLs and source groups
are shared, and the existing rebuild deduplication map merges attribution without
retaining duplicate rules. Group references and metadata survive a compiled-cache
restart. Source grouping supports more than 64 lists; there is no bitmask source
limit. Build-only union maps are released before publication.

The native display resolves current list names, so renaming a list does not
require reindexing. If a displayed list has been disabled/removed but remains in
the installed generation, the section labels it **list update pending**. This
helps distinguish saved settings from a failed replacement-ruleset update.

Additional device checks:

1. Visit a blocked site, expand/collapse **Blocking lists**, and check the name,
   URL and matching rule. Test portrait and landscape scrolling.
2. Put the same blocking rule in two lists; both should appear. An unrelated list
   should not appear. Check a Custom rules block as well.
3. Rename a list and reload the blocked page; its current name should be shown.
4. Restart and revisit the page; source attribution should survive the cache load.
5. Turn protection off for that site and reload; the blocked-page details should
   clear along with the blocked page.
