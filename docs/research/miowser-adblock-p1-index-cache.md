# P1: restore completed adblock indexes

Implemented on 24 October 2026, following the index-cache finding in
[the 1DM investigation](1dm-18.2-adblock-storage.md).
P0 measurements and device checks were skipped at the user's request.

## Changes

- The compiled cache now includes domain buckets, generic-rule order, and the
  physical slots and ordered buckets of both URL-token tables. Each rule is
  written once in its existing bucket; no extra rule-ID map is built.
- Loading fills those structures directly. It bypasses `addRule`, literal-token
  extraction, token selection, table growth, and reinsertion into token tables.
  Rule objects and the domain hash map are still reconstructed; this is not a
  zero-allocation or fully memory-mapped network matcher.
- The four priority tiers retain their order: manual exceptions, manual blocks,
  subscription exceptions, and subscription blocks. Matching predicates, resource
  and domain options, native domain prefilter, and cosmetic rules are unchanged.
- Schema version 2 uses `compiled-v8.bin`. The first load after updating builds
  this new cache from saved sources once. Unchanged subsequent loads reuse it.
  Input fingerprints continue to invalidate it when rules or enabled lists change.
- A streaming CRC32 checksum, count/tier/bucket validation, and token-table
  validation reject damaged payloads. The existing loader then rebuilds from
  saved source lists; it never publishes a partially restored matcher.
- Array allocation checks account for the incoming allocation before reserving
  memory. Existing heap-headroom protection and atomic cache replacement remain
  active. Sources and user settings are preserved. ZIP source storage is deferred.

## Local verification

The adblock unit suite and development Kotlin compilation passed. The suite covers
cache round trips, collision buckets, table growth after restore, priority and
option equivalence, Unicode/long-URL fallback order, corruption/truncation,
old-version invalidation, and the existing 60,000-rule replacement regression.
The tests passed with the existing local 192 MB test-heap override. A focused
cache-test rerun also passed after strengthening the corruption test.

No device measurements were taken, so no startup-time or memory-reduction figure
is claimed. This change removes index-building work from a valid cache load;
source rebuilds still run when inputs change or the cache is unusable.

## Device checklist for the user

1. Open the browser once after updating and let adblock preparation complete.
   Restart the app twice without changing lists; confirm protection returns and
   the saved list names, switches, and rules remain intact.
2. Check a familiar ad-heavy page, a tracker-blocking example, and cosmetic hiding.
   Check a site where media playback previously worked.
3. Check a site exception and manual block/allow rules before and after restart.
4. Refresh one list, then restart. Toggle a list and edit a custom rule, allowing
   preparation to finish each time; confirm the updated behavior survives restart.
5. With several lists enabled, navigate while preparation runs and check for
   responsiveness or crashes. Report list names and the observed sequence if one
   occurs. Do not clear app data for these checks.
