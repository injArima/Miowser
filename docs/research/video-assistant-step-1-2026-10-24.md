# Video Assistant — step 1 prototype

**Historical prototype — retired at the user's request.** The user confirmed
pause/speed worked and reported seek failure; gestures were not wired in this
prototype. The lab activity, sources, fixtures and separate generated build have
been removed. These notes preserve the findings, not a currently available lab.
Continue with [step 2 in the main app](video-assistant-step-2-2026-10-24.md).
The prototype source remains recoverable from GitHub checkpoint `67ce45c`.

## Scope

This is the **additional** browser-backed player prototype approved after the
Samsung investigation. Existing browser fullscreen, media discovery, the Media
page, Media3 playback, Library playlists and downloads are not rerouted.

The lab is compiled only into **debug** and **development**, with a separate
**Miowser Video Lab** launcher icon. Release/benchmark source sets do not include
the lab code or fixtures. Its activity ignores incoming URLs/extras and loads
only bundled fixtures; all WebView requests outside the two fixture origins and
asset path are rejected. It does not open normal tabs, add Library entries,
download files, persist playback history, or clear browsing data.

## Implemented

- One WebView remains attached while Chromium supplies a fullscreen custom View.
  Entering/exiting does not call `loadUrl`, replace `src`, copy the playing blob,
  create Media3, pause the source WebView, or seek back to zero.
- Reuses the existing `PlayerCenterControls`, `PlayerBufferedTimeline`, timer
  formatting and player theme. The two existing composables changed from
  `private` to `internal`; their existing behavior and callers are unchanged.
- Controls target the fixture's original video: play/pause, ±10 seconds, timeline
  seeking, and a simple 1×/1.5× speed switch. Speed choices and remaining player
  controls are intentionally not redesigned in this proof.
- The fixture's **Open prototype** button synchronously requests fullscreen from
  the DOM click. It is not the planned floating button from step 3.
- A frame-specific reply channel carries a nonce, document ID and navigation
  generation. The receiver rejects stale/replayed state; the script rejects
  commands for another document. Commands are restricted to media controls.
- Updates are event-driven and coalesced; ordinary updates are limited to four
  per second. There is no continuous whole-page scan or polling loop.
- Back returns to the existing inline video, restoring its native controls.
  Source/session counters and entry/exit positions remain visible below the page.
- Rotation is handled without recreating the activity/WebView. App backgrounding
  pauses the lab video; returning does not auto-resume it. The lab has no PiP or
  background-playback mode. Fullscreen bars and keep-screen-on state are restored
  on exit. Closing destroys only the lab WebView.

## Offline fixtures

1. Direct WebM video, 320×180, 45 seconds, moving pattern plus test tone.
2. The same video inside a different local HTTPS origin, with fullscreen allowed.
3. The same clip appended to a real MediaSource/SourceBuffer (`blob:` URL).

The generated asset is 700,344 bytes and contains no third-party content. Its
generation command and SHA-256 are in
`app/src/videoPrototype/assets/video-lab/README.md`.

The small one-time MSE asset append is a controlled fixture setup. It is not a
general-purpose blob capture or native-player handoff implementation.

## How to open

Build/install your usual debug or development variant and launch **Miowser Video
Lab** from Android's app drawer. Debug shares the existing `com.miowser.app`
package, so use your existing signing setup for an in-place update. Development
uses `com.miowser.app.dev` and is separate from the installed normal app.
Do not uninstall or clear data to test this feature.

The lab does not yet appear on ordinary websites. General live-video tracking,
the contextual floating button, and additional rich controls belong to the next
approved steps. No merge with the existing Media3 workflow has been introduced.

## Device checklist — left to the user

Repeat the following for **Direct video**, **Cross-origin iframe**, and
**MediaSource / blob**:

1. Play the inline video and seek to about 12 seconds. Note the session ID,
   dimensions, source-change counter and metadata-load counter.
2. Tap **Open prototype**. Playback should continue near that position, with
   matching dimensions/session/counters, without restarting or double audio.
3. Test pause/resume, both 10-second skip buttons, dragging the timeline, and
   1×/1.5× speed. The visible position should follow the actual video.
4. Press Back. The inline video should continue at its current position with the
   same source and restored native controls. Repeat entry/exit several times.
5. Enter while paused. It should remain paused until Play is tapped.
6. Rotate in fullscreen, then return inline. There should be no source reload or
   position reset. Hide/show the overlay controls and check the return button.
7. Background the lab and return. Playback should be paused, with the session
   retained; tap Play to resume. Close/reopen the lab to verify clean setup.
8. Switch fixtures and close the lab. Confirm there is no lingering test tone.
9. Spot-check normal browser fullscreen, Media-page playback, a Library playlist
   and downloads to confirm their entry paths remain unchanged.

If the MediaSource fixture reports an unsupported codec, record Android System
WebView's provider/version. The fixture does not silently substitute direct-file
playback, since that would invalidate this check.

## Local verification

- Debug and development Kotlin compilation passed.
- Development APK packaging and release Kotlin compilation passed. The merged
  release manifest, compiled release classes and merged release assets exclude
  the lab; the development APK contains the offline fixtures.
- 15 focused JVM tests passed: 5 lease/seek-window tests and the 10 existing
  `PlayerScreenTest` checks.
- `node scripts/check-video-lab.cjs`: 8 fixture contract checks passed. These use
  a simulated DOM to verify command/ownership/cleanup behavior, not decoding or
  Android fullscreen.
- The generated WebM decodes to 540 frames / approximately 45 seconds with
  FFmpeg on the computer.

Manual-test artifact: `app/build/outputs/apk/development/app-development.apk`
(28,700,132 bytes). SHA-256:
`3d539f76fd6697b5ef2b7b7d408dac7d15c41c763e36929bfcae26e92ddb5fd4`.

Commands used:

```powershell
node scripts/check-video-lab.cjs
.\gradlew.bat :app:compileDebugKotlin :app:compileDevelopmentKotlin :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.BrowserVideoLabStateTest' --tests 'com.miowser.app.player.PlayerScreenTest' --console=plain --offline
.\gradlew.bat :app:assembleDevelopment :app:processReleaseMainManifest :app:mergeReleaseAssets :app:compileReleaseKotlin --console=plain --offline
```

No device commands, installs, data removal, runtime playback measurements or
website compatibility checks were performed. Source/event counters are useful
continuity evidence; they cannot prove decoder identity, ABR quality, codec or
bitrate continuity. This fixed-quality fixture does not test adaptive streams,
DRM, provider-specific controls, or arbitrary nested/sandboxed frames.

The fullscreen and frame-message design follows Android's
[WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient),
[frame-aware message callback](https://developer.android.com/reference/androidx/webkit/WebViewCompat.WebMessageListener),
and [asset loader](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader)
APIs. Trusted-click restrictions remain relevant to subsequent website support.
