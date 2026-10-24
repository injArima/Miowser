# Video Assistant step 2 — live sessions in the main app

## Result and scope

The live-session foundation now runs in normal browser tabs. It is a separate,
read-only path alongside existing media discovery. It does not change the native
player, ordinary browser fullscreen, downloads, detected-media rows, Library
playlists, audio focus, or PiP behavior.

This step introduces no new floating button or rich-player screen. Those remain
steps 3–5 of the approved add-on plan. It creates no second player/decoder and
does not change a video's source, position, pause state or rate.

## User feedback from step 1

- The user verified prototype pause/resume and speed controls.
- Seeking did not work. Its exact device cause was not captured; do not mark it
  fixed based on the earlier simulated-DOM tests.
- Gestures did not work because the prototype had no connected gesture layer.
  The existing main native player gesture layer was not changed.

The new state captures actual `seekable` and `buffered` ranges, readiness and
duration separately. A known finite duration without a seekable range is marked
**finite duration only**, not proof that seeking is either supported or
impossible. The future browser controller must confirm the outcome of seek
commands instead of simply inheriting the prototype's empty-range behavior.

## Implementation

- Identity includes tab, navigation generation, native frame ID, document ID and
  video element ID. Same-source videos in separate frames remain distinct.
- Native frame origin/main-frame status come from Android's message callback.
  Each document receives an individual binding token; stale navigation tokens,
  replayed sequences and oversized batches are rejected.
- Current source kind, position, speed, playing/buffering/ended/muted states,
  natural video dimensions, source revision and time ranges are captured.
- Quality downshifts replace live dimensions even if the blob URL and duration
  are unchanged. Existing discovery's highest-resolution merge is preserved and
  never used as current playback quality.
- Visibility includes document visibility, viewport bounds and intersection
  observations. Bounds remain **frame-local CSS pixels**; they are not presented
  as top-level coordinates for a nested iframe.
- Events, targeted mutation handling, and resize/intersection observers update
  state. There is no repeated whole-page scan. Ordinary batches are coalesced to
  at most two per second per reporting frame.
- Frames without videos do not claim native session slots or start heartbeats.
  Frames with videos use a 10-second lease heartbeat over the watched set.
- Limits: 8 reporting frames per tab, 8 watched videos per frame, 32 native
  sessions total, 8 buffered/seekable ranges, and 24,576 characters per message.
  An untracked video that starts playing can replace an idle watched preview.
- Removing a video detaches its listeners; frame/page retirement and navigation
  invalidate ownership. Back-forward page restoration can reconnect. A 45-second
  lease checked every 15 seconds removes abandoned frame state.
- The source tab holds a stable `StateFlow`; BrowserScreen does not collect its
  clock updates. No per-position Compose snapshot is added to the browser shell.
- WebView document-start/message features are checked. An unsupported provider
  disables this optional tracker; it does not replace the existing grabber or
  block browsing. Opaque/non-HTTP(S) frame origins are not accepted.
- Private and normal sessions are both process-local. Session state is not
  serialized into history, backups, Library entries or tab restore files. Source
  URLs are not logged; data URL bodies are omitted from messages.

This telemetry is page-controlled data, not privileged proof of a resource or
authorization. This step exposes no native playback/download commands. A later
handoff must revalidate the chosen live element and capabilities.

## Prototype cleanup

- Removed the extra lab activity/launcher entries, source-set wiring, fixtures,
  prototype-only tests and the generated development APK/build intermediates.
- Retained the forensic research and historical implementation notes in Git.
- Removed `com.miowser.app.dev` from the **connected emulator**, using Android's
  keep-data uninstall. Normal `com.miowser.app` remains installed. No physical
  phone was connected, so no removal was attempted on a phone.
- No normal app data was cleared, and no updated APK was installed automatically.
- The existing development build type and unrelated test tooling predate the lab
  and remain available for the project's normal build workflow.

## Validation

- Normal debug APK assembled; release Kotlin compilation passed.
- 34 JVM tests passed: 15 live-session tests, 10 existing player tests and 9
  existing retained-engine tests.
- 16 Node simulated-DOM checks passed, including unchanged playback, same-URL
  quality changes, source replacement, same-source multiple videos, dynamic
  elements, bounded bursts, empty seek ranges, retirement, back-forward
  restoration and message-size limits.
- Inspected the debug APK/merged manifest: no Video Lab classes, activity or
  fixtures remain; the new live tracker is present.
- No emulator/phone playback tests were run. WebView frame delivery, real-site
  compatibility and lower-end performance still need user testing.

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.LiveVideoSessionsTest' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Manual-test artifact: `app/build/outputs/apk/debug/app-debug.apk` (normal
`com.miowser.app` package; update it in place with the existing signing key).

## User regression checklist

1. Browse your usual direct-video and iframe/MSE sites. Confirm the website still
   starts, pauses, seeks, changes speed/quality, and enters its normal fullscreen.
2. Navigate away/back, replace a video within a page, switch tabs, and close/reopen
   tabs. Check for new hangs, duplicate audio or unexpected playback changes.
3. Test a page containing previews plus its main video, and a private tab. Confirm
   normal browsing remains responsive.
4. Open media through the existing Media page, then a Library playlist. Check
   native-player seeking, gestures, subtitles, playlist taps and PiP as before.
5. Run an ordinary download and an ad-block list refresh while browsing on the
   lower-end device. Report new stutter or unusual memory growth.

There is no new control to test in step 2. Exact field/identity behavior has local
contract coverage; its actual WebView integration awaits device verification.
No claim is made that natural dimensions identify a particular adaptive variant,
codec or bitrate. DRM, closed shadow roots and opaque/sandboxed frames have not
been established as supported.

## GitHub recovery checkpoints

Branch: `codex/browser-video-sessions` in `injArima/Miowser`.

- `67ce45c`: snapshot of the previously uncommitted app and step 1 prototype.
- `6f0f180`: remove the prototype and its separate launcher/build wiring.
- `63049c5`: step 2 implementation and local regression checks.

The GitHub `main` branch was not overwritten. Further work should continue with
small logical commits on this branch so individual changes can be reverted.

The frame channel follows AndroidX's
[message-listener API](https://developer.android.com/reference/androidx/webkit/WebViewCompat.WebMessageListener).
AndroidX's [reply proxy implementation](https://raw.githubusercontent.com/androidx/androidx/androidx-main/webkit/webkit/src/main/java/androidx/webkit/internal/JavaScriptReplyProxyImpl.java)
retains a peer object for the browser-side proxy; the tracker uses that identity
alongside its navigation/document binding.
