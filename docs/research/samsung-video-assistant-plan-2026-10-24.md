# Samsung Internet Video Assistant: findings and Miowser plan

Date: 24 October 2026. **Research and implementation plan only.** No Miowser
application code was changed for this investigation; no APK was installed or
executed and no phone/app data was accessed.

## Main finding

### Approved scope and execution update

The subsequent user decision is to build this as an **additional feature**, reuse
existing player UI wherever suitable, and preserve all current playback/media
workflows. Merging or automatically rerouting existing playback is deferred.
The historical six-step research table below is not authorization for those
merges. The approved seven-step sequence is: isolated continuity prototype;
active-video tracking; floating button; core controls; supported additional
controls; transitions/recovery; performance/regression hardening.

Step 1 was an isolated prototype. The user confirmed pause/speed, reported seek
failure, and requested removal of the extra development app/build. Gestures had
not been connected. Its sources/build wiring have been removed; findings remain
in [the historical step 1 report](video-assistant-step-1-2026-10-24.md).

Step 2 now tracks live video sessions inside normal tabs, alongside existing
media discovery, with no playback rerouting or new controls. Local compilation
and contract checks passed; actual WebView/website behavior remains for the
user's device checks. See [the step 2 report](video-assistant-step-2-2026-10-24.md).

Step 3 adds a frame-local contextual button in normal tabs. Its trusted click
opens that exact video's existing browser fullscreen route, using temporary
browser controls until the step 4 rich-control adapter is implemented. Current
Media3 workflows are preserved. See [the step 3 implementation and device
checklist](video-assistant-step-3-2026-10-24.md).

The user confirmed step 3 works perfectly. Step 4 adds an owned browser-backed
controller and reuses the existing transport/timeline/speed/rotation UI, while
keeping native playback independent. Local checks passed; actual controls and
seeking still need the user's device checks. See
[the step 4 report](video-assistant-step-4-2026-10-24.md).

Samsung's inspected **Video Assistant → Fullscreen** path controls a browser-backed
media session. It does not first ask the user to select a detected URL and open
that URL in a newly created standalone player.

The Java code carries the same Terrace media-player client into its fullscreen
UI. This is strong evidence for continuity of the logical playback session and
explains the reported experience. It is **not a runtime measurement** proving
that every website retains an identical decoder, bitrate or quality throughout
fullscreen. Adaptive streaming can still adapt. The native implementation behind
the JNI boundary was not decompiled in this investigation.

Samsung also has a separate standalone-player path. Findings below concern the
traced in-browser fullscreen action, not every popup/cast/PiP route in the app.

## Artifact and evidence

- Supplied file: `C:/Users/Inja/Downloads/Samsung Browser_30.0.5.24.apk`
- Package/version: `com.sec.android.app.sbrowser`, `30.0.5.24` / `1300524502`.
- SHA-256: `7431a0efe90bb769506f4a365c7c99828384cc235ce16c1da2bb0d00f997129b`.
- REA/JADX headless bridge: 0.7.1; package inventory: 25,385 top-level classes.
- APK signature/authenticity was not verified. Package/version above are artifact
  declarations. Decompilation is static evidence, not observed app execution.
- [Exported REA evidence](G:/Projects/Miowser/.local/research/samsung-video-assistant-30.0.5.24/rea-evidence.json)
  retains 55 records, including method bodies, overloads, artifact identity and
  provider limitations. Evidence identifiers below resolve within that bundle.

## What Samsung does

| Question | Observed code | Conclusion and evidence |
| --- | --- | --- |
| How does it learn that a video started? | `TinBrowserMediaPlayerManagerClient.onVideoStart` is called from native code and forwards a specific player client through `TerraceMediaClient`; `MediaHandler.onVideoStart` registers listeners and selects that client. | It knows a live playback session, rather than choosing from a page-wide URL list. `ev_a6bfb3e9b006a0a449e4cb99721025c3bc1721d96df2dc5982d2934d142d53d8`, `ev_88cc094d7e4da5115c2e62ed96a1c5225a93fea605a83954c7385facc49a5c9e`. |
| When is the assistant shown? | `MediaHandler.setVAVisibility` checks settings/state and whether the selected client is playing and unmuted. `shouldShowVA` excludes hidden tabs/fullscreen and other conflicting states. `MAManager.show` posts a 200 ms delayed action. | Visibility follows playback and browser state. Muted videos are suppressed in this inspected branch. These are Samsung policies, not requirements we must copy. `ev_3495b331ef6955ad822fd3634aa5736dd13f91baa27d2daa927dc2922bd08e86`, `ev_49c535819cadafd9a849f719df783bea351a5a908b75712c48cb47fa06d1f946`, `ev_c47dd279e11b31d7dafbd3215deaa567b7f1ad1da7fa941ff8dca09cd5734cdb`. |
| How is the button placed? | `MAController.showVAView` asks the browser engine whether the video is visible. Its callback intersects the video rectangle with the content viewport. `MAButtonView.show` attaches an Android view through the activity; `retrievePosition` anchors it around the video within allowed bounds. | This path uses an app-owned overlay and engine-supplied geometry, not evidence of a universal DOM-injected button. The inspected path does not require a system-wide overlay merely because the APK declares that permission. `ev_13a48c552c2cce8f584e72b621a8a16a3e062e7391c65278598bd0d175334346`, `ev_f527b2d2abfa81c51449afced1248740be7e3c63833adbcc377642c86dd659f5`, `ev_5c5ded1f2d161f4642c1ff1ae6189069ab9c29e60ffce324c34ca3555009a188`, `ev_5dd5e9a0cd5fa08a77ec052dc96c23458aabaa00be6aa59ae6aa8b18ebe475eb`. |
| What happens on Fullscreen? | Item click sends its action ID. `MAManager.start(0)` calls `MediaInfo.requestFullscreen`, forwarding through Terrace to `TinBrowserMediaPlayerManagerClient.requestFullscreen`, which invokes JNI on the existing native client handle. | The action targets the selected browser player. There is no URL-picker or fresh standalone-player construction in this traced action. `ev_2409af163361144e04411e04352df73c54241c505f1c89659a1fd0511383a9f7`, `ev_07434185de2ad3006d8ee75b5f3f77a8a85fd02331181bb7c4c9e140e522a369`, `ev_00bd3a86d36216c9daa3de19c9525db374a43b5c90856d5add7c1a3de3109237`, `ev_e0c8de75de6ed739c7c6ab342cdc4ba3e8c30b759672fb61207357ad91b9a9bd`, `ev_f94fae1de9b713c74de50d3ebe474816fb91fb42bb02dea9ff4482be9106c7fe`. |
| How does its rich fullscreen UI connect? | Native `onEnterFullscreen` notifies the media client. `MediaHandler.createMPManager` supplies the same client in `MediaInfo` and sets standalone=false. `MPManagerFactory` selects `MPTerraceVideoView`; that view's controls come from `MediaInfo.getPlayerControl`. `MPManager.enterFullscreenVideo` constructs the fullscreen controller. | The controls wrap browser-backed playback. The factory separately selects `MPStandaloneVideoView` when standalone=true. `ev_8725f69855f2b996394f04fe8c51a69a0eecb40eeccdfefea6abdb35af7f85e6`, `ev_f5aac7ff458ebc8a91a2dcea10fc0dc535cc17d01f9f1bfc1e59499eb7408e2d`, `ev_390f9a0fc2630e1bcb8cce2aab5aec4300875606c705b846722872109b8ef08f`, `ev_491e53d22759142c76986d225ee7453d1d427a154b00c4606e22ca54f8ffeca4`, `ev_3b240c133a628086c6cc07ac7bab9587bc3d3a916c2dfaba14e61989d4210966`. |
| Does it handle every stream identically? | `MAController.shouldDimmed` dims the popup item for media classified as MSE or app-pinned mode. | Its own feature set varies by route; fullscreen and standalone popup should not be treated as equivalent. `ev_789405ea21944efa28e46ee8cb650dab288b544dc8ca057b8195888196d77fcc`. |

The `MediaInfo` client constructor captures URL, current position, duration,
cookies and dimensions while retaining the client reference:
`ev_d009bef6397b40bc5faecfe8b6b11310939e93241927fd4845d362839c4ce1e1`.
Those fields do not establish that Samsung reconstructs every fullscreen stream
from its URL, or that dimensions uniquely identify an adaptive representation.

## Why Miowser currently needs more steps

| Current code | Gap relevant to this feature |
| --- | --- |
| [VideoSnifferScript.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/VideoSnifferScript.kt:27) | Reports source, duration and dimensions. Reports are deduplicated by source+duration; quality changes can leave those unchanged. Playback activity is an aggregate boolean, not the identity of the video the user wants to control. No current position/rate snapshot or per-element geometry is reported. |
| [GrabberBridge.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/GrabberBridge.kt:7) | Document-generation guards exist, but reports are keyed by source and activity is shared. Multiple frames/elements need independent session identity and command routing. |
| [MediaType.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/grabber/MediaType.kt:68) | Detection merging retains the better/higher resolution and maximum reported bitrate. These are useful discovery facts, not reliable current playback-quality state. Do not repurpose them as the handoff target. |
| [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt:1314) | Builds a new remote request from a selected candidate, then pauses page media. It does not transfer live position/rate or the currently selected adaptive representation. `Remote.startPositionMs` exists but this builder leaves its default at zero. |
| [BlobMediaOptions.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BlobMediaOptions.kt:9) | Correctly labels nearby verified network detections as choices, not proof that one backs a particular blob. Keep that distinction; nearest/latest URL is not a sound automatic choice. |
| [Tab.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/Tab.kt:1010) and [BrowserScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/browser/BrowserScreen.kt:2957) | Already receive and display WebView fullscreen custom views. This is a useful starting point, currently without Miowser's rich controls around the live browser session. |
| [PlayerScreen.kt](G:/Projects/Miowser/app/src/main/java/com/miowser/app/player/PlayerScreen.kt:555) | The rich player currently operates on ExoPlayer. Reusing its visual controls with browser playback needs an explicit backend adapter; a floating button alone does not provide this. |

## Recommended design

**One visible action, two capability-driven playback routes.** The button refers
to the exact playing element, never a guessed item from the Media page.

1. **Browser-session route:** keep the existing page/video alive and render its
   fullscreen custom view with supported Miowser controls. This is the closest
   architectural match to Samsung and the preferred route when preserving an
   opaque MSE/blob session is important. No URL extraction or second playback
   pipeline is needed for this route.
2. **Existing native-player route:** use Media3 when the active resource and its
   playback requirements are known. Carry fresh cookies, request context,
   position, rate and an exact quality selection when available. Direct files
   are the straightforward first case. Adaptive playback needs more evidence.

The browser route is a **prototype gate**, not a promise of Samsung API parity.
Android WebView does not provide Samsung's Terrace client API. It does provide a
fullscreen custom-view callback; whether a particular provider/site permits all
the desired rich controls must be demonstrated before the larger UI refactor.
[Android fullscreen API](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowCustomView(android.view.View,%20android.webkit.WebChromeClient.CustomViewCallback)),
[Chromium fullscreen architecture](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/full-screen.md).

### Quality contract

- Same-session browser fullscreen retains the existing logical session; normal
  adaptive changes remain possible. Do not label this a permanent quality lock.
- `videoWidth`/`videoHeight` give natural dimensions, not a unique codec/bitrate,
  HLS variant or DASH representation. `currentSrc` can identify an opaque media
  provider rather than a native-fetchable video file.
  [HTML media definitions](https://html.spec.whatwg.org/dev/media.html).
- Native HLS handoff needs the correct media playlist or a matched video variant
  **and** its audio/subtitle groups. DASH needs the matching video/audio
  representations and period. A segment URL alone is not a complete playback
  source. Height alone cannot prove an exact representation match.
- If only a master manifest or an uncertain nearby URL is known, retain browser
  playback where supported; otherwise explain the limitation and keep the
  existing manual Media workflow available. Do not silently choose the highest
  quality or claim the original quality was preserved.
- This does not extract keys or transfer a page's DRM session to Media3. Existing
  authorized browser playback can remain browser-backed where supported.

## Execution plan

These step numbers belong to this feature, separately from the completed adblock
P1–P4 work.

| Step | Work | Completion criteria |
| --- | --- | --- |
| 1 — Prove the browser route | Small isolated WebView fixture: ordinary video, iframe video and MSE stream; trusted user-triggered fullscreen; custom view with play/pause, seek and rate controls through a frame-specific channel. | Establish which provider/site combinations support the route, whether native browser controls conflict, and which capabilities are missing. No assumption that returning from a native callback preserves DOM user activation. |
| 2 — Track live video sessions | Add separate live state keyed by tab + navigation generation + frame context + element ID. Record current source, actual dimensions, position/rate, visibility and playback state; keep discovery/download metadata unchanged. Listen for playing/pause/ended/resize/source changes and element removal. | Two videos sharing a URL remain distinct; paused/hidden/removed/stale elements cannot take over the button; changing site quality updates current state without overwriting historical discovery metadata. |
| 3 — Add the contextual button | Show one small Miowser action attached to the eligible playing frame. Reposition on scroll/resize/zoom; hide on navigation, conflicting app pages and detached elements. Prototype a frame-local DOM button for browser fullscreen so the trusted click can synchronously request fullscreen. Use native overlay presentation only where the action does not depend on transferring DOM activation. | One tap selects the intended element. The button stays near the clipped video, respects insets and accessibility, and does not intercept page controls/scrolling outside its own touch area. No new system-overlay permission. |
| 4 — Add browser-backed rich controls | Introduce a small playback-control interface and capability flags while preserving the existing Media3 backend. Adapt supported controls to the exact live element, with acknowledgement/error handling. Retain the source tab/renderer while it owns playback; do not run the current rich-player path that pauses its WebView. | Play/pause, seek, speed, rotation, Back and supported subtitles work without starting a second decoder. Controls unavailable to the browser backend are not presented as working. Custom subtitles/playlist/PiP receive explicit support decisions and regression checks. |
| 5 — Add reliable native handoff | Start with direct HTTP media. Extend only when active HLS/DASH representation ownership is established. Snapshot live state on tap; pass initial position/rate and a temporary session quality preference into Media3 without overwriting saved preferences. Pause the chosen page element for handoff and restore it on failure. | No media chooser for an unambiguous supported video, no restart at zero, no duplicate audio, correct request context, no silent quality guess. Stale navigation cancels handoff. Native/browser ownership is explicit on Back and failure. |
| 6 — Harden and release | Bound/coalesce events, remove listeners with their document, clean up tabs/renderers/PiP/rotation/focus, and add a browser setting/per-site disable path. Keep private-session state ephemeral. Validate against the existing player, library and download flows. | No continuous whole-page scanning, per-frame Compose recomposition, extra media probes just for positioning, or automatic downloads. Confirm lower-end memory behavior and preserve the recent player touch fixes. |

### Bridge and fullscreen details

Prefer feature-checked document-start scripts and a frame-addressable message
channel. AndroidX supports injecting into matching frames and reports the actual
source origin/main-frame status with a reply proxy. Treat page messages as
untrusted; validate ownership, navigation generation, sizes, action names and
arguments. A channel identifies a context, not a trusted publisher.
[AndroidX WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat),
[message callback metadata](https://developer.android.com/reference/androidx/webkit/WebViewCompat.WebMessageListener).

Keep the document-start installer short. Use observers/events, bounded per-frame
state and one scheduled geometry update for the visible candidate. Refresh
position/rate only at a bounded cadence while controls need it or on a handoff
snapshot, rather than ticking all of BrowserScreen.

For browser fullscreen, request it synchronously from the button's actual click
in the owning document. A delayed native-to-JavaScript command is not guaranteed
to carry transient user activation. Sandboxed frames, permissions policy,
JavaScript disabled, closed shadow roots, canvas players and provider limitations
need explicit fallback behavior, not a promise of universal support.
[Fullscreen requirements](https://fullscreen.spec.whatwg.org/#dom-element-requestfullscreen).

## Validation checklist for implementation

- Direct MP4/WebM, HLS master/media playlists, DASH with separate audio, live and
  MSE/blob playback. Compare position and reported dimensions before/after.
- Change site quality in both directions, including two variants with the same
  resolution. Confirm the action never uses the discovery maximum as live state.
- Same/cross-origin nested frames, dynamically replaced videos, multiple players,
  a paused main video plus an autoplay preview, and fullscreen-disallowed frames.
- Cookie/signed/referer-sensitive sources, session expiry, failed handoff and
  navigation/closing a tab during handoff. Never replay stale callbacks.
- Back/rotation/PiP/background/return; captions, speed and a subsequent native
  Library playlist. No duplicate audio or leaked retained page.
- Low-memory devices during browsing/adblock refresh. Avoid speculative second
  decoders and whole-body blob copies; same-session playback is the preferred
  continuity route where supported.
- Existing Media page and download behavior remain available; the new button
  starts playback rather than downloading a file. Private tabs do not gain
  persistent playback records through this feature.

## Coverage and unresolved questions

| Item | Status |
| --- | --- |
| Samsung button visibility, geometry and action routing | Answered for the inspected Java branches. |
| Browser-backed versus standalone fullscreen | Answered at the Java factory/control layer; same client is retained. |
| Native renderer/decoder operations behind JNI | Not inspected. |
| Exact quality continuity on the supplied phone/sites | Unmeasured. The user's observation is consistent with the architecture. |
| WebView rich-controls/fullscreen parity | Requires step 1; the existing app's custom-view support is a foundation, not proof of full parity. |
| Universal active HLS/DASH identity from a blob URL | Not established and should not be assumed. |

**Recommendation:** begin with step 1, then live-session tracking and the button.
Use browser-backed playback for continuity; add automatic Media3 handoff only
where the source and quality mapping are reliable. Avoid replacing WebView or
embedding Samsung's browser engine to implement this feature.
