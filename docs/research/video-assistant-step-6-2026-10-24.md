# Video Assistant step 6 — transitions and recovery

Follow-up: the browser route now uses the original player's shared control and
panel layouts, with separate playback engines. See
[the shared UI report](shared-player-ui-2026-10-25.md).

The user verified step 5 works. Step 6 makes interruptions and return paths
explicit for the browser-backed add-on. It continues to use the same HTML video,
retained WebView and fullscreen custom View. Recovery never extracts a URL,
starts a second player, reloads a page, seeks to a saved position or automatically
calls play. Existing Media3 and Library playback routes remain independent.

## Changes

| Situation | Behavior |
| --- | --- |
| Back / return to website | Sends an authenticated release for the exact fullscreen entry before native ownership detaches. Restores website controls, original inline sizing and temporary hold speed, then asks that document to exit fullscreen. The custom View callback closes once, including reentrant calls. |
| Background / foreground | Suspends controls and cancels pending command deadlines. Return requests a fresh snapshot of the same document/frame/element/attempt before re-enabling controls. A paused video remains paused; a playing video is not restarted or repositioned. |
| Long background or PiP | Protects only the owned fullscreen session/channel from ordinary 45-second lease expiry while interrupted or reconnecting. Unrelated sessions still expire and all existing table limits remain. Explicit removal/navigation still invalidates the owner. |
| Explicit PiP | Retains step 5's exact renderer ownership. Temporary holds release, browser controls stay hidden inside PiP, and expansion reconciles the live position. Dismissal can pause the current owned element even if its source changed just before the latest telemetry. This exception cannot target another entry. |
| Source replacement on the same element | Cancels old seek listeners/deadlines, clears pending seek/transport intent and closes transient menus/scrubbing. Normal media commands carry a source revision, so a delayed old-source seek/pause cannot affect its replacement. The live element remains the playback owner. |
| Lost controls or changed fullscreen element | Offers website controls where the document is reachable and a labelled return-to-website action. Does not guess another video or silently open the native player. A return-only identity permits final cleanup after rich control ownership has ended. |
| Hanging fullscreen entry | An eight-second entry deadline restores controls and permits another attempt. A late rejection is checked against its original entry, so it cannot tear down a newer entry. |
| Duplicate custom View requests | Rejects a duplicate while an accepted View remains open. Each callback lease closes once, including reentrancy; callback failures cannot prevent native UI cleanup. |
| Navigation / tab closing / renderer loss | Invalidates outstanding requests and uses existing tab/fullscreen cleanup. Renderer failures retain the app's existing page-error/reload workflow; this step does not automatically reload or promise to preserve a renderer that Android terminated. |
| Rotation | Continues using the accepted custom View and overlay identity. Successful reconciliation preserves the same UI owner; returning or giving up restores the overlay's original orientation and brightness overrides. |

## Protocol and UI details

- Native resume requests and deadlines are bound to an exact owner and increasing
  request ID. Another pause, exit or navigation invalidates earlier replies and
  deadlines. Duplicate foreground notifications do not cancel a pending resume.
- JavaScript rejects older lifecycle request generations as well as wrong
  tokens, documents, navigations, elements and entry attempts. A stale request
  cannot enable/hide controls or exit a newer session.
- Reconnection has one four-second deadline. Until confirmed, player gestures,
  menus and sliders are disabled and a return action remains available. A failed
  reconnect falls back instead of leaving permanently active stale controls.
- An owned video removed from the frame, or a different element becoming
  fullscreen, is not automatically replaced by another playing video.
- Ordinary website fullscreen, with no Video Assistant owner, does not acquire
  this recovery overlay. Native-player controls, audio-focus behavior, playlists,
  subtitle workflows and download flows are not rerouted.
- Each recovery/entry has one bounded timeout; there is no new polling loop,
  source download or persisted session history. Existing telemetry batching,
  track metadata limits and packet limits remain in force.

## Local verification

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.*' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Final local verification passed:

- Normal debug APK packaging and release Kotlin compilation.
- 68 focused JVM tests: 25 controller tests, 17 registry tests, 7 recovery/callback
  tests, 10 existing player tests and 9 retained-engine tests. No failures, errors
  or skipped tests.
- 84 simulated-DOM checks, including previous steps and the new lifecycle,
  source-replacement, stale-request, release and fullscreen-entry cases.
- Source and staged whitespace checks.
- APK inspection confirmed the recovery classes and lifecycle messages are
  packaged. Package remains `com.miowser.app`; no Video Lab, new Activity or
  system-overlay permission was introduced.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 27,592,688 bytes. SHA-256:
`cf7a413fa39b2266f23d3776350c7859ef464c8de2694f21572964f0cc426ac5`.

Implementation checkpoint: `19254f9` on `codex/browser-video-sessions` in
`injArima/Miowser`. Step 5 remains recoverable at `05b4470` (implementation) /
`c5d1b0e` (report).

Device checks are left to the user; no device access, installation, uninstall or
app-data clearing is included.

These checks establish command/state contracts and compilation. They cannot
prove actual touchscreen latency, provider/DRM compatibility, orientation timing,
Android process-death behavior or continuity across every WebView implementation.
A terminated renderer's in-memory video session cannot be recovered by this
protocol. Website scripts may independently pause/change playback on visibility
or fullscreen events; recovery does not override the site's playback policy.

## User checklist

1. **Return and repeat:** open a playing video with the floating action, change
   speed/sizing, hold for 2× and press Back during the hold. Re-enter several
   times quickly. Check position, website controls/sizing and prior speed restore,
   with no duplicate audio or stuck fullscreen. Repeat with screen lock/rotation.
2. **Background:** test playing and manually paused videos. Minimize for at least
   one minute, return, and check controls reconnect to the current position
   without restarting, seeking back or automatically resuming a paused video.
   Repeat rapid Home/return and screen-off/unlock interruptions.
3. **PiP:** enter explicitly, remain in PiP for more than one minute, expand,
   minimize again and dismiss. Check continuous playback while visible in PiP,
   hidden overlay controls, working controls after expansion and paused playback
   after dismissal. Include a site whose video source changes between episodes.
4. **Source changes:** use site quality changes, source replacement or a next
   episode on the same page. Seek just before the change; an old seek must not
   jump the replacement video. Check fresh seeking, menus, tracks and hold speed.
5. **Loss/recovery:** navigate or close the owning tab, use a page that replaces
   its player/frame, interrupt fullscreen entry and test a temporarily stalled
   page. A lost session should offer usable website controls/return action,
   without adopting a different video or reloading automatically. If Android
   kills a renderer, use the existing page recovery UI.
6. **Regression:** test ordinary site fullscreen, cross-origin iframe/MSE videos,
   private/background tabs, native Media/Library player, native subtitles,
   playlists, gestures, PiP and downloads, including on the lower-end device.

Step 7 (performance/regression hardening) remains separate. No automatic merge
with the native playback route is introduced.
