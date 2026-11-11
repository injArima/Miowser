# Page gestures and dynamic-media overhead

Implemented in Miowser 0.1.76 (code 77).

## Samsung comparison

Inspected the supplied Samsung Browser 30.0.5.24 APK using REA/JADX 0.7.1.
SHA-256: `7431a0efe90bb769506f4a365c7c99828384cc235ce16c1da2bb0d00f997129b`.
These are static observations. Samsung Internet is not installed on the currently
connected emulator, so this investigation does not establish live YouTube Music
timings or a measured playback advantage over Miowser.

| Evidence | Observation | Application to Miowser |
| --- | --- | --- |
| `TinSwipeRefreshHandler.initWebContents` — `ev_da6a911932adc525503934fd157aff218d13b2513d0b042ea63211efc6811a4c` | Registers itself through `setOverscrollRefreshHandler` on WebContents. | Refresh should follow page gesture ownership rather than a raw parent-view drag. |
| `TinSwipeRefreshHandler.start` — `ev_667009f3769ceb840ec1811c8e831a0e0a389089d84d5e795de55f8c9f2cbea8` | The overscroll callback checks support and action type before attaching/starting refresh UI. | Gate parent interception before refresh can cancel a website's touch stream. |
| `TinSwipeRefreshHandler.supportPullToRefresh` — `ev_4afbf2d8412b2dcbef8c95052f61ad6e415ecb52261a8753f91ac878377c6d35` | Defers to the browser's listener and excludes extension views. | Preserve native overlay input ownership and page-specific scroll restrictions. |
| `TinBrowserMediaPlayerManagerClient.isPrepared` — `ev_d355e931e94b179778ac542723f4b4b13b2d5dc73edeb8c88829e2966c142da6` | Reads preparation state through its existing native media client. | Keep webpage playback in its existing WebView; do not introduce another player/probe gate before a tap can play media. |
| `TinBrowserMediaPlayerManagerClient.onVideoStart` — `ev_b4e94142e29ab4be946e77f7cf18e0ae3a83844249c0fc1a9c2312f3ce4d8179` | Native playback notifies the current media client and playback listeners. | Remove unnecessary observation work around playback; this does not reveal Samsung's buffering/CDN/preload policy. |

Complete local Evidence was exported to the ignored build output
`app/build/research/samsung-page-interactions-2026-11-11.json`.

The upstream Chromium overscroll implementation corroborates the engine-level
distinction: it waits for a scroll acknowledgement and checks gesture direction,
initial boundary state and CSS overscroll restrictions before activation.
Android WebView does not expose Samsung's private Terrace overscroll handler.
Miowser therefore implements a conservative page-aware gate around its existing
SwipeRefreshLayout, rather than copying Samsung code or changing browser engines.

Primary references:

- [Chromium overscroll implementation](https://chromium.googlesource.com/chromium/src/+/HEAD/ui/android/overscroll_refresh.cc)
- [Chrome: CSS overscroll behavior](https://developer.chrome.com/blog/overscroll-behavior)
- [Android: parent touch interception](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup)
- [Android WebView touch delivery](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/java/src/org/chromium/android_webview/AwContents.java)

## Findings and changes

1. **The old refresh wrapper knew only the top-level WebView scroll position.**
   It could intercept a drag while a carousel, nested scrolling list, slider or
   page-owned control was being used. The new gate requires a gesture that began
   at the page top, a clearly downward single-finger drag, and an ordinary page
   hit target. Horizontal/upward starts and multitouch remain page-owned for
   that entire gesture. Child interception requests are respected.
2. **Page interaction is examined without delaying delivery of the physical tap.**
   DOWN reaches Chromium first. One asynchronous hit-test examines the touched
   element and its ancestor chain; it does not scan the document or add a
   continuously running page observer. It handles open shadow roots, semantic
   controls, editable fields, touch-action, overscroll-behavior and nested
   overflow containers. Iframes and inaccessible/unknown targets retain their
   input. Until a positive answer arrives, the app cannot claim the gesture.
   Stale answers after cancellation, a new touch or a changed URL are ignored.
3. **Media activity crossed the synchronous JavaScript bridge after every DOM
   mutation, even when playback had not changed.** The sniffer now reports
   activity transitions. Metadata discovery and native batching remain intact.
   A fixture containing 2,000 unrelated mutations now produces only one
   unchanged activity report; this is not a live playback latency measurement.
4. **SPA navigation invalidated the native grabber token without replacing the
   JavaScript document.** Reused video/audio elements could continue reporting
   against a retired token. Same-document navigation now rebinds the sniffer
   once, republishes current metadata and activity, and preserves media source,
   play/pause state, position and playback rate.
5. **The page-element rule observer ran even without delete rules.** It is now
   installed only while DOM deletion rules exist and disconnected when those
   rules are reverted. CSS-only rules use the existing stylesheet. Coalesced
   deletion of newly added matching elements remains supported.

## Limits

This removes identified input and observer overhead. It does not prove the sole
cause of a particular song's startup delay or guarantee Samsung-equivalent
latency. CDN response time, buffering, account/region restrictions, cookies,
site code and WebView version still matter. No autoplay/DRM/security restrictions
are disabled, no page click is replayed, and no webpage media URL is reopened in
another engine. Closed shadow roots and custom controls that expose neither
semantics nor scroll restrictions remain a WebView compatibility boundary.

## Verification and device checklist

- Pure Kotlin gesture tests cover target-query ordering, protected controls,
  upward/horizontal direction locks, diagonal movement, pinch, cancellation,
  child ownership and gestures that began away from the top.
- `node scripts/check-browser-page-interactions.cjs`: 25 DOM fixture checks.
- `node scripts/check-live-video-sessions.cjs`: 142 existing player/detection
  fixture checks. Neither JavaScript suite is an on-device website test.

Device checks are left to the user:

1. On YouTube Music, swipe carousels, the queue/player and nested lists; drag the
   seek/volume controls. None should refresh the page. Repeat through several
   same-page track changes and confirm media detection stays current.
2. On an ordinary webpage, pull down from blank content at the top to refresh.
   Start farther down and scroll to the top within the same drag; then start a
   fresh drag. Check horizontal/diagonal swipes, pinch, text selection and an
   interrupted pull, including that the loader disappears.
3. Try the native floating video button and the side-pane handoff, including
   iframe players. Their existing one-tap behavior should remain unchanged.
4. Save/revert CSS hide and DOM delete rules. Confirm matching dynamic elements
   still disappear and unrelated page controls remain responsive.
5. Compare tap-to-audio timing across multiple tracks under the same network,
   account, ad-blocking configuration and cache conditions. If delay remains,
   capture a focused timing trace before changing buffering/network policies.
