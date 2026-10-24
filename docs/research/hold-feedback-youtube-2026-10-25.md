# Hold-speed feedback and YouTube action visibility

## Changes

- Restore a shared rounded `2×` indicator in the original top-center position,
  with status-bar padding. Both the native player and browser-backed player use
  it. It remains visible during a hold even if the rest of the controls hide.
- Browser hold state is separate from the selected permanent speed. The badge
  requires an active hold and a reported 2× playback rate; selecting permanent
  2× speed does not leave the hold badge visible. Release, cancellation,
  source/recovery changes and PiP clear the hold presentation.
- Correct the floating action's ancestor clipping calculation for viewport
  overflow propagated from HTML/body. The viewport is already clipped before
  ancestors are considered; treating a zero-height body as another local clip
  incorrectly discarded the visible video.
- Continue respecting genuine nested player clips, root/body containment,
  offscreen geometry and fullscreen permission denials. There is no YouTube URL
  extraction, platform-specific player API, site exception or permission bypass.

## YouTube investigation

A temporary desktop in-app browser tab was used to inspect the public
[Blender Foundation Big Buck Bunny video](https://www.youtube.com/watch?v=aqz-KE-bpKQ).
The page had a playing HTML video backed by a blob source, with real video
dimensions 1280×720. Its visible video rectangle was approximately 490×276 CSS
pixels at y=60. The enclosing player had an ordinary overflow-hidden rectangle
around that video. Higher up, the body box had zero height and overflow-x:auto /
overflow-y:scroll, while HTML's overflow was visible in both axes.

The old loop clipped against the body's bottom at zero, producing an empty
rectangle. The fix follows [CSS Overflow viewport propagation](https://www.w3.org/TR/css-overflow-3/#overflow-propagation):
viewport scrolling is handled by viewport bounds, while real inner clipping
containers still constrain placement. Containment prevents the special body
case. A fixture using the observed layout reproduces the old failure and checks
the corrected action placement and entry.

This was desktop, logged-out, read-only layout inspection, not an Android
WebView/device reproduction. The inspection tab was closed afterward. No
YouTube account action or Miowser/phone data access was performed. The browser's
read-only DOM interface does not establish actual Android fullscreen API support;
that remains a device check.

## DRM and permission boundaries

Playback remains on the original HTML video and its current browser session.
The fix does not read/set `mediaKeys`, request a key system, alter EME/CDM state,
change licenses, decrypt content, fetch a replacement URL, clone the video or
change capture/output protection. A script test makes DRM access throw and
verifies normal discovery/hold/release never accesses it.

Fullscreen still requires the existing trusted click and the document's allowed
fullscreen capability. Embedded players that deny fullscreen remain denied.
The action is offered over an eligible inline video before entering Miowser's
player; this change does not adopt the site's own already-fullscreen player.
Provider restrictions on playback/rates/output can still apply. No licensed or
protected YouTube playback was tested. References:
[Fullscreen permissions](https://fullscreen.spec.whatwg.org/#permissions-policy-integration),
[YouTube embedded player parameters](https://developers.google.com/youtube/player_parameters).

## Validation

```powershell
node scripts/check-live-video-sessions.cjs
.\gradlew.bat :app:testDebugUnitTest --tests 'com.miowser.app.browser.video.*' --tests 'com.miowser.app.player.PlayerScreenTest' --tests 'com.miowser.app.player.PlayerOverlayStateTest' --tests 'com.miowser.app.player.PlayerTrackControlsTest' --tests 'com.miowser.app.player.PlayerSubtitleStyleTest' --tests 'com.miowser.app.browser.RetainedTabEnginesTest' :app:compileReleaseKotlin :app:assembleDebug --console=plain --offline
```

Final local verification passed:

- Debug APK packaging and release Kotlin compilation.
- All 92 focused JVM tests, with no failures, errors or skips.
- All 99 simulated browser-script checks, including six new clipping/permission/
  DRM-access regression cases and the existing hold/release/cleanup cases.
- Source/staged whitespace checks and APK inspection for the shared hold label
  and viewport fix. No Video Lab is packaged.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, 27,471,140 bytes. SHA-256:
`e3530ec2c2265bc2ac62241bc78eddff058951001a3fbc5626b79891a33d763b`.

Implementation checkpoint: `620cf73` on `codex/browser-video-sessions` in
`injArima/Miowser`. The prior native-control fix remains at `6477453`.

Visual/touch/DRM-provider checks remain with the user; no install, uninstall or
app-data clearing is included.

## User checks

1. Hold a playing video in both playback routes, with controls shown and hidden.
   Check `2×` appears, speed changes and releasing restores the prior speed and
   hides the badge. Repeat Back, source change, background and PiP during a hold.
2. Set permanent 2× speed without holding: it should not show the hold badge.
3. Open a normal public YouTube video in the browser and start inline playback.
   Check the floating action appears, opens the same video and retains position.
   Repeat portrait/landscape, a second video and a visible iframe player.
4. Recheck a player inside a clipped container and a fullscreen-disallowed iframe.
   The action should not overflow the clip or bypass the iframe's restriction.
5. Check a site previously working, the existing native player and protected
   content you are authorized to view. Normal provider restrictions should remain
   intact; no source or license handoff is expected.
