# P4: compressed filter-list sources

Implemented on 24 October 2026 following the 1DM storage investigation. Device
testing remains with the user.

## Storage and refresh

- New and refreshed subscriptions are saved privately as `adblock/list-<id>.zip`,
  with one `filters.txt` entry per archive. Names, URLs, counts, switches and
  update/error metadata stay in the existing subscription store. Custom rules
  remain an editable text file.
- The existing sequential downloader still streams each response into a plain
  temporary file and validates it without retaining a full list string or a
  second parsed ruleset. The validated file is then streamed into a temporary
  ZIP using 32 KiB buffers and fast DEFLATE compression.
- The ZIP is finished, flushed, synced and checked by streaming its contents
  before it replaces the saved source. Failed writes, cancellation or verification
  failures leave the old source intact. The old text representation is retired
  only after successful replacement.
- Existing text files load directly. There is no eager startup conversion or
  download of all lists. Each source migrates on its next successful refresh,
  including a refresh returning unchanged text. Once compressed, byte-identical
  refreshes retain the existing ZIP and timestamp, avoiding needless rebuilds.
- Source comparison uses bounded buffers and compares expanded bytes, not ZIP
  sizes. Changed sources receive a monotonic timestamp so fingerprint-based
  invalidation still works for same-sized replacements.

## Reading and matching

Rebuilds stream the ZIP entry directly through the existing UTF-8 parser. No
extracted source copy is required. Readers validate the single expected entry,
expanded byte limit, exact declared size and CRC. A truncated or damaged source
aborts that replacement generation, retaining the installed protection even if
some rules were delivered before the checksum failure.

The existing 32 MiB expanded-source limit remains. The compressed container gets
a 64 KiB allowance for framing overhead on incompressible inputs. A headroom
check runs before installing a source; this is not a new rule-count limit.

Requests still use the compiled Kotlin/native indexes, decision cache, Bloom
prefilter and P3 string matcher. They never open a source ZIP. The compiled
snapshot layout is unchanged. Migrating source representation changes its
fingerprint and causes one rebuild during that refresh; subsequent unchanged
refreshes/restarts can reuse the compiled cache.

**Tradeoff:** compression reduces saved-source disk use and keeps IO bounded.
Rebuilds pay decompression/CRC work, and successful downloads pay a compression
and verification pass. ZIP storage does not by itself reduce the installed
matcher heap or prove faster browsing. Fast compression and background storage
owners keep this work outside normal request matching.

## Backup and restore

Backups now include both legacy `.txt` and new `.zip` subscription files. The
existing path restrictions and integrity manifest remain. Nested filter archives
are verified before restore can commit a generation. Only filter ZIPs receive
the framing-size allowance; other file limits remain unchanged. Old text-source
backups remain readable.

## Verification

Local coverage includes legacy reads, migration, streamed byte comparison,
failed writes, cancellation after ZIP creation, oversized generated input,
truncation, wrong/multiple entries, CRC damage, dishonest size metadata,
fingerprint timestamps, parser/matcher equivalence, compiled-cache restoration,
removal of both source formats, and backup/restore of both formats.

The bundled EasyList is also compressed and scanned from its ZIP to compare
network/cosmetic/unsupported-rule counts against the original text. These local
checks do not install an app, access a phone or alter existing app data.

Final checks passed: **116 adblock tests and 14 backup tests (130 total)** under
a **192 MiB test heap**, plus debug and development Kotlin compilation. This
includes the existing 30,000-rule restore and 60,000-rule replacement regressions.
The bundled source measured **2,062,728 bytes of text → 820,954 bytes of ZIP**
(about **60% less disk space**), with identical parser counts. This is a source
storage measurement, not a device speed or installed-matcher memory result.

## Device checklist

1. Open with existing lists before refreshing. Check browsing, site exceptions,
   list names/counts and blocked-page source attribution.
2. Refresh one existing list, then restart. Check that its rules still work.
   Refresh it again unchanged and check for unnecessary rule rebuilding.
3. Add several lists together. Check sequential progress, independent failures
   and responsiveness while downloading/applying.
4. Interrupt a refresh or disconnect the network, then retry. Previously active
   lists should continue working.
5. Create and restore a backup in a safe test setup; also check an older backup
   containing text lists. Compare names, switches, custom rules and blocking.
6. Play a video while refreshing lists on a lower-end device. Check stuttering,
   memory pressure and responsiveness; compare source storage use if desired.

The app-data preservation requirement remains: no device checks or data clearing
were performed by the agent.
