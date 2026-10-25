# Samsung Internet: single-tap entry and external placement recheck

The later [display-only investigation on live YouTube](samsung-button-placement-youtube-2026-10-25.md)
adds measured screen coordinates, native view bounds, animation resources and
show/hide timing. It leaves Miowser's existing activation action unchanged.

Follow-up: the user approved removing the two-step route and
[restoring the consistent floating button](video-assistant-single-tap-restored-2026-10-25.md).

## Conclusion

The two-step toolbar entry introduced in Miowser is a regression in interaction,
not a requirement of the previously working frame-local button. The user rejects
that interaction and requires one activation action consistently across sites.

Samsung separates overlay placement from player activation. Its Android view can
sit outside an embedded video because it belongs to the activity. Its fullscreen
action uses the selected live player through Samsung's Terrace/JNI interface;
it does not arm a JavaScript listener and ask the user to tap the page afterward.

An important new finding: the inspected preference defaults to position **3**.
In the position calculation, 3 means **right, below the video**, clamped to the
available browser area. Samsung does not implement that position by inserting a
new row into the website or requiring the frame to reserve page-layout space.

## Target and scope

- Supplied APK: `C:/Users/Inja/Downloads/Samsung Browser_30.0.5.24.apk`.
- Declared package/version: `com.sec.android.app.sbrowser`, 30.0.5.24.
- SHA-256 rechecked by REA:
  `7431a0efe90bb769506f4a365c7c99828384cc235ce16c1da2bb0d00f997129b`.
- Static JADX analysis only; no app installation, execution or phone access.
- Signature authenticity was not verified. Native fullscreen policy below the
  final JNI declaration was not decompiled or measured.
- New retained evidence:
  [REA bundle](../../.local/research/samsung-single-tap-2026-10-25.json).
  Previously exported methods with the same APK hash are also reused from
  [the original evidence bundle](../../.local/research/samsung-video-assistant-30.0.5.24/rea-evidence.json).

## Traced behavior

| Finding | Exact observed boundary | Evidence |
| --- | --- | --- |
| App-owned overlay | `MAButtonView.show` attaches through `Activity.addContentView` and positions its child with `setX`/`setY`. | `ev_2d1c38664ad4d6e8ab32e94d1b9ea0bdce96998c802a227c1b5c71326c78cf4f` |
| Engine-owned visibility | `MAController.showVAView` asks the selected Terrace client to `checkVideoVisible`. | `ev_8318dbf553839025fee611ee9779582e30298c40afacb76e5785937a8ab15652` |
| Below-right default | `getPrefVideoAssistantPosition` returns preference default 3; `retrievePosition` uses right alignment for 3 and `videoVisibleRect.bottom + distance` for 1/3. | New `ev_f69bef9b8a7e20a65ddb8e974064a5f9dac7fb696ded26ab83b9deb901ce4953`; reused `ev_5dd5e9a0cd5fa08a77ec052dc96c23458aabaa00be6aa59ae6aa8b18ebe475eb` |
| Browser-aware placement limits | `getAllowedPosition` accounts for browser toolbars, tab/bookmark/translation bars and navigation margins. It bounds overlay coordinates, not website flow. | `ev_028705108408852f76fee4bb41dc952349342639276a77962c29ff7846103243` |
| Fullscreen action identity | `Fullscreen.id()` returns 0. Child-item click sends message 2 with that action ID; `MessageHandler.handleMessage` calls `start(msg.arg1)`. | New `ev_7dd8e4ecfe5dc836b4ec78007af7cf9e94d846519d78d23ab00f4b8e6003345d`, `ev_032c725b451e1af6c2301b780729a3fbebf90256991ec862b33aa5c96f358265`; reused `ev_2409af163361144e04411e04352df73c54241c505f1c89659a1fd0511383a9f7` |
| Direct activation | `MAManager.start(0)` calls `MediaInfo.requestFullscreen`, which forwards to its existing Terrace client. This traced branch has no second page-tap state. | Reused `ev_07434185de2ad3006d8ee75b5f3f77a8a85fd02331181bb7c4c9e140e522a369`, `ev_00bd3a86d36216c9daa3de19c9525db374a43b5c90856d5add7c1a3de3109237` |
| Native player handle | `TinBrowserMediaPlayerManagerClient.requestFullscreen` checks its nonzero native handle and forwards it to JNI. The generated JNI adapter calls `J.N.MGoS$Wxu(long)`. | `ev_ece8e71b6e04440730acef583f2a9178b59ff709a410e2d6851ed944d8a6a678`, `ev_3192e49d39f8f029b91108e2a2a0192d82fb68bfe1b2c99a386acc23c82598e0` |
| Native boundary, not a public WebView helper | `J.N.MGoS$Wxu` is marked `native_method` with no Java body. The APK also packages `lib/arm64-v8a/libterrace.so` (173,541,960 uncompressed bytes); inventory alone does not locate the native function inside it. | `ev_278fe7fef23be32ad2f705c17854035b8fe6ba90afe208195a08f901a6ffbbec`; read-only ZIP inventory |

Previously traced fullscreen callbacks and `MediaHandler.createMPManager` retain
the same Terrace client and set standalone=false. The player factory selects
`MPTerraceVideoView`; its standalone path is a separate branch. This supports
logical session continuity, but does not measure identical bitrate or decoder
identity through every transition. Adaptive quality may still change normally.

## Miowser regression

`VideoAssistantButtonScript.placeRow` now returns false immediately for every
child frame (`window.top !== window`), even when the earlier frame-local action
worked. Layout rejection also leads to the toolbar route. `armToolbarEntry` then
posts an arm request; `toolbarTap` waits for a subsequent trusted page click.
This is why a previously direct action became two-step.

The original button already supplied a real click in the document containing
the video. Moving activation to an Android toolbar removed that reliable click
context. The workaround handled that constraint by adding user work rather than
preserving the accepted interaction. It should not be presented as a general
Android requirement or as Samsung parity.

For comparison, Android's public
[onShowCustomView callback](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowCustomView(android.view.View,%20android.webkit.WebChromeClient.CustomViewCallback))
delivers fullscreen content after the page enters fullscreen. It does not expose
Samsung's selected native media-client handle. Chromium's ordinary
[Android JavaScript evaluation](https://raw.githubusercontent.com/chromium/chromium/main/content/browser/web_contents/web_contents_android.cc)
calls the main frame's `ExecuteJavaScript`; the
[renderer host source](https://raw.githubusercontent.com/chromium/chromium/main/content/browser/renderer_host/render_frame_host_impl.cc)
separately defines a user-gesture execution method for tests. Calling a native
toolbar button is therefore not evidence that an arbitrary child document has
received the required web activation.

## Recommended correction and follow-up

1. Remove the toolbar arming/second-tap interaction. Restore the original
   consistent, one-tap floating action as the reliable production baseline.
   Preserve the shared rich-player UI and browser-session controls.
2. Treat external placement as a separate capability to prove. A native overlay
   can solve clipping and positioning, but it must be paired with a genuinely
   single-action browser-backed presentation path before replacing that baseline.
3. Evaluate an app-owned fullscreen presentation of the retained WebView/video
   as a prototype, including bounded frame-chain expansion and complete style/
   layout restoration. This is an unproven WebView adaptation, not Samsung's
   private engine API. Do not promise cross-origin/nested-frame compatibility
   before verification, and do not substitute a synthetic click or URL reopen.
4. Retain the same HTML video/session, keep ExoPlayer separate, preserve native
   subtitles/gestures/PiP behavior, and leave DRM, license and fullscreen-policy
   decisions intact. Do not replace the browser engine for this small feature.

The product requirement is uniform: one visible activation action, one tap to
enter the rich player. Placement must not change the number of steps. No
Miowser runtime code or APK was changed during this reinspection; the route
correction above is proposed for discussion under the user's working preference.
