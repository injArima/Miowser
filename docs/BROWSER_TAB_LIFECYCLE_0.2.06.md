# Browser tab lifetime and hidden playback — 0.2.06

## Findings and changes

The previous TabController kept at most six live engines (three on low-RAM devices), with lower limits on memory callbacks. Eligible background tabs were destroyed and only a limited navigation bundle was retained. Reactivation recreated the renderer and loaded/restored the page. This was app-driven eviction, separate from a real WebView renderer crash. The limit, eviction selection, memory callbacks and navigation-bundle workaround have been removed. Unopened restored tabs still load lazily; opened tabs retain their engine until the user closes them, the owning controller is disposed, or the renderer actually dies. Explicit tab archive/close operations and the separately requested temporary side-pane-tab cleanup retain their existing behavior.

The page media coordinator previously let any registered tab become the system-media owner. Background playback could therefore retain an old tab across a tab switch. Selection now restricts ownership to the selected tab and an explicitly open companion pane. Previous owners are paused/released, including late reports received while the pause is in flight. Playback permission is independent of background-music permission: ordinary hidden tabs are denied, while the current hosted background music session and explicit fullscreen headphone/PiP paths retain their existing support.

Tab applies WebViewCompat.setAudioMuted on the UI thread, gated by WebViewFeature.MUTE_AUDIO. The media bridge carries authenticated playback policy with each frame binding and subsequent changes, pauses every bound frame rather than just the chosen notification owner, and rejects play attempts while denied. A top-document pause remains as fallback. The first unbound play is allowed to establish the media handshake; native muting already applies to hidden WebViews, and a denied binding pauses the new frame. Providers without native audio-muting support depend on JavaScript delivery and cannot guarantee immediate silence from an unresponsive renderer or unsupported frame. Granting permission never invokes play: paused media stays paused until the user or website explicitly plays it.

The renderer callback marks an unresponsive engine and mutes/pauses its media. A responsive callback restores its desired permission, without playing or reloading it. It does not terminate the process: renderers can be shared by several WebViews. Actual onRenderProcessGone handling still releases exactly the affected engine, invalidates its page-bound callbacks and presents manual recovery. Android/provider crashes and process death remain possible, especially with many live pages; removing app eviction is not a guarantee of unlimited memory or crash-free sites.

The page-media bridge, live-video assistant and resource sniffer previously performed synchronous traversal of inserted DOM subtrees (including unbounded querySelectorAll scans in the sniffer/live-video path). They now share a queued TreeWalker helper: up to 128 nodes or 4 ms per task, yielding between batches, with bounded mutation intake and a bounded queue. Overflow schedules a document pass; repeated roots request a follow-up pass so insertions behind the current cursor can still be found. Nested shadow-root discovery uses the same timer, visitor failures do not strand the queue, and page lifecycle cleanup cancels pending scans. Existing readiness/play events remain the immediate discovery path for interacted players. These are static renderer-thread risks and host-tested corrections, not a measured Android main-thread ANR diagnosis or a universal performance guarantee.

## Validation

Host checks cover tab identity/state across 30 tabs and repeated switches, explicit/stale renderer release, selected/companion/background playback policy, authenticated frame gating, detached audio, no resume on tab return, initial playback, idle-frame reconnect on first play, large DOM tail discovery, bounded tasks, mutation floods, queue cancellation and existing player controls/navigation interactions.

- `:app:assembleDebug :app:testDebugUnitTest`: successful; 861 JVM tests, zero failures/errors/skips.
- `scripts/check-page-media.cjs`: 26 scenarios, including actual composed document-start code and frame-policy regressions.
- `scripts/check-live-video-sessions.cjs`: 142 scenarios using the composed traversal helper, player controls and assistant scripts.
- `scripts/check-browser-page-interactions.cjs`: 25 scenarios, including resource-sniffer mutations and navigation rebinding.
- `scripts/check-media-discovery.cjs`: six scenarios plus parsing both composed live-video/sniffer scripts.
- `scripts/verify-media-artwork.cjs`: nine artwork-selection scenarios.

No emulator, physical-device, instrumentation or performance measurement was run.

For device review: play inline and embedded video, switch to another ordinary tab and confirm silence; switch back and confirm the old video stays paused. Repeat with YouTube Music, private tabs and more than six opened pages. Check Back navigation and form state remain present without refresh. Separately minimize current music with background playback enabled/disabled, and check fullscreen headphone/PiP and side-pane handoff. Sites that reproduce a genuine renderer failure need runtime evidence to distinguish page/provider defects from remaining app work.

## Android API references

- [WebView lifecycle](https://developer.android.com/reference/android/webkit/WebView): onPause does not pause JavaScript; pauseTimers affects every WebView and is deliberately not used.
- [WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat): feature-gated native audio muting and renderer client registration.
- [WebViewRenderProcessClient](https://developer.android.com/reference/androidx/webkit/WebViewRenderProcessClient): unresponsive/responsive callbacks do not require automatic renderer termination.
- [Manage WebView objects](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview): renderer loss requires cleanup/recreation of affected views; it cannot be repaired by reusing the dead engine.
