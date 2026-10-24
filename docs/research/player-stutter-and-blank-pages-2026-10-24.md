# Player stuttering and blank pages — 24 October 2026

The user confirmed stuttering occurs in Miowser's player, while the website's own
player works. The reported website becomes visible when ad blocking is disabled.
Device checks remain with the user; the code findings below are not a device
crash diagnosis without a current crash trace.

## Player findings and changes

1. **The remote player retained 30 seconds of past samples in the allocator used
   for forward buffering.** At a sufficiently high bitrate, that alone can fill
   the old 64 MiB sample target. Load control can stop loading while the player
   lacks future samples to advance and release old ones. Retaining samples also
   reduces heap available to filters and UI. The player now releases past samples
   immediately; backward seeking still works through its disk cache or source.
2. **Local files used Media3's automatic track-based memory target.** In the
   shipped Media3 1.4.1 source, the default video and audio targets alone are
   2,200 allocator segments (about 137.5 MiB at 64 KiB each). Both local and remote
   playback now use an explicit heap-aware sample target: normally up to 16 MiB
   on small heaps/low-RAM devices and 32 MiB on larger heaps, reduced when the
   remaining heap is crowded. The 4 MiB lower bound allows loading to progress;
   this target is not a hard cap on a decoder or an unusually large media chunk.
   The disk prefetch window is unchanged.
3. **Direct streams and local files launched a second decoder for midpoint
   artwork while playback was active.** Every playback type now captures the
   already-decoded visible frame instead. A first-frame thumbnail remains the
   fallback; midpoint artwork is captured when playback reaches the midpoint.
   Existing artwork is retained. Captures skip when two transient bitmaps would
   leave less than 8 MiB for other app work.
4. **Each adaptive-prefetch pass prepared and checked every candidate segment.**
   It copied request specs, looked up cookies, and hashed keys for the whole
   window before selecting one missing piece. Candidates are now ordered first,
   and checks stop at the first missing piece. Cache-key hashing also avoids a
   separate formatter for every digest byte. Cache keys retain identical bytes.
   Entering rebuffering cancels an in-flight prefetch so playback takes priority.
5. **The optional native-domain index builder duplicated domain maps.** It now
   sorts an array of existing domain references instead of constructing a second
   tree map, and checks heap headroom while collecting masks and before allocating
   its arrays. Under pressure, the existing exact Kotlin matcher remains active
   if this optional acceleration cannot be built safely.

Media3 load-control behavior was checked against the
[1.4.1 implementation](https://raw.githubusercontent.com/androidx/media/1.4.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/DefaultLoadControl.java).
Audio-focus policy, track selection, subtitle delay, PiP policy and playlist
session ownership were not changed.

## Website investigation and changes

The reported page was inspected in an isolated desktop tab without accepting its
age prompt. Current EasyList, EasyPrivacy and Peter Lowe text sources were saved
under `build/tmp/player-page-investigation/` for a local diagnostic matcher run.

- The main document, both observed application scripts, both stylesheets, the
  first-party analytics URL and the observed challenge-script path were allowed.
- All 13,636 applicable cosmetic selectors were checked against the desktop DOM.
  Only two advertisement links matched; none matched the document root, body,
  main container or visible prompt.
- This does **not** reproduce the phone's cached list versions, custom rules,
  installed engine generation, WebView provider or post-prompt document state.
  It does not establish that the reported website is fixed.
- A confirmed separate blank-page path returned successful empty HTML when a
  top-level document was blocked. Blocked navigations now return an explanation
  and surface an error through the existing native browser error UI, rather than
  silently showing an empty page. Subresource blocking stays active.
- Synthetic blocked responses now use `Cache-Control: no-store`, so they are not
  retained as successful replacements after protection or rules change.
- A failed filter rebuild deliberately retains the previously installed rules.
  Therefore turning additional lists off in settings does not prove that they
  have been removed from the live matcher if the update subsequently failed.
  This remains a possible explanation, not a confirmed cause on this device.

## Local verification and device checks

Local tests passed with a 192 MiB test heap and cover the heap budgets, bitmap headroom, existing playback/cache
semantics, unchanged persistent cache keys, safe blocked-page content, and
adblock matching/cache behavior. A direct Media3 load-control regression also
checks that reaching the byte target permits playback to resume and releasing
samples permits loading again. Debug and development Kotlin compilation passed.

Device checks for the user:

1. Play the same direct stream and local file that stuttered. Watch past 30 seconds
   and test backward/forward seeks, pause/resume, playlist transitions and PiP.
2. Check subtitles and track changes. Check first-frame artwork and a midpoint
   thumbnail after watching or seeking near the middle.
3. Refresh filters while playing on a small-heap device. If a crash remains,
   capture the current crash trace; the earlier Compose OOM trace alone cannot
   identify a decoder, network stall or current allocation source.
4. For the blank website, keep the desired lists and rules intact. After changing
   list switches, wait for preparation to finish and note any "not applied" or
   memory warning before reloading. Record whether the new result is a visible
   blocking explanation, a renderer error, or still a blank page. If still blank,
   the live list versions, custom rules and blocked-host summary are needed to
   reproduce that configuration; no global site exemption was added.

No device data was read, cleared or removed during this work.
