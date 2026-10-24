# P3: simple network patterns without regex work

Implemented on 24 October 2026 following the 1DM investigation. Device checks
remain with the user.

## What changed

After resource-type, party and page-domain constraints pass, a network rule can
now match a single ASCII literal using direct character comparisons. Supported
forms include substring, start anchor, end anchor, both anchors, domain anchor
with a path, and a trailing separator, including compatible combinations.
Plain-domain rules retain their existing hostname shortcut.

For example, `/ads.js`, `|https://example.test/ads.js|`, and
`||example.test/ads^` can avoid regex cache lookup, compilation and a regex Matcher
allocation on ordinary ASCII comparisons. Matching remains case-insensitive.
Domain anchoring reproduces the existing scheme and subdomain-label rules;
it does not substitute a looser hostname/path test.

Wildcards, internal separators, non-ASCII literals, embedded quote escapes and
unrecognized expressions keep the bounded lazy regex matcher. Comparisons that
encounter Unicode also fall back to that matcher, preserving Unicode case
equivalences and separator behavior. End-anchored URLs ending in a line terminator
use regex because `$` can match before that terminator.

## Memory and cache behavior

Each rule has one packed integer describing the literal's offset/length and
anchor flags. It refers to the existing expression, retaining no new literal
string, matcher object or compiled regex. Normal string matching allocates no
substrings, lowercase URL copies or regex Matchers.

The descriptor is recognized once when a rule is constructed, including when
restored from its saved expression. The binary layout and `compiled-v10.bin`
version remain unchanged. Existing valid caches work without a forced rebuild,
list download, source reparse or token reindex. Cache integrity checks, source
attribution, native domain prefilter, token buckets and memory guards remain in
place. The descriptor is derived data and is excluded from rule equality.

Priority, manual exceptions, resource restrictions, page-domain exclusions and
party checks still run through the same rule predicates. Both normal request
matching and the blocked-page source explanation use these predicates.

## Local verification

The new comparisons use the original `AdblockPatternCompiler.compile` regex as
the reference. Coverage includes positive/negative matches, repeated literal
occurrences, separators, malformed schemes, authority/port boundaries, Unicode
case equivalents, line terminators, generated patterns/URLs, resource and domain
constraints, manual precedence, long URLs and compiled-cache restoration.

The bundled EasyList exercises **3,022 additional rules** eligible for string
matching; plain-domain rules are outside that count. This is coverage, not a
device speed measurement. End-to-end comparisons also use regex-only copies of
the rules to check optimized and restored engines against the previous behavior.

Final local checks passed: **104 adblock tests** with a **192 MiB test heap**,
plus debug and development Kotlin compilation. The suite includes the existing
30,000-rule cache restore and 60,000-rule replacement memory regressions.

## Device checklist

1. Restart with existing filter lists and check that preparation uses the saved
   cache. Browse normal pages and previously blocked sites.
2. Check a simple custom rule and its exception, then refresh/toggle one list.
   Confirm behavior and blocked-page list attribution survive restarting.
3. Recheck the previously blank website with protection on and with a site
   exception. This change does not claim to resolve that unconfirmed issue.
4. Play a video while browsing and while updating lists on a lower-end device.
   Check stuttering, memory pressure and responsiveness.

ZIP source storage is separate work and is not changed by P3.
