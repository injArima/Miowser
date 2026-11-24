# Website permissions and ARY Plus — 0.2.03

## Confirmed app cause

The screenshot's generic message came from `BrowserScreen.handleWebPermission`.
The old runtime-permission mapper accepted only VIDEO_CAPTURE and AUDIO_CAPTURE;
any other resource was denied before the user had a choice. The message did not
name the resource. This included supported WebView resources such as protected
media identifiers and MIDI SysEx.

[Android's PermissionRequest reference](https://developer.android.com/reference/android/webkit/PermissionRequest)
documents protected-media identifiers for EME license requests, MIDI device
control, and the need to grant explicitly selected resources rather than blindly
granting whatever a request contains.

## Lightweight site observation

Checked the supplied [ARY Plus episode page](https://aryplus.tv/video/v2/3/6a7f216446c9625e3f392dbe/6a57868b5bf57c474cc00a50)
on 24 November 2026 in one temporary desktop Chrome session with an initial
navigation and a four-second observation wait. No account login, purchases,
license extraction, Android device session, or repeated performance traces were
used. The owned browser process was terminated after capture.

- 152 network requests across 45 origins during the 8.556-second scenario.
- 41 script requests, including Shaka Player, Radiant, advertising, analytics,
  chat, and Stripe resources. These counts describe requests, not CPU usage.
- Console output showed an encrypted-media robustness warning, Shaka
  configuration output, and an ARY Player autoplay message. Protected-media
  capability probing is therefore a strong candidate for the Android prompt,
  but the exact native Android permission resource array was not captured.
- Advertising script output warned about parser-blocking `document.write`.
  There was also an image CORS failure and a 401 on an optional loyalty endpoint.
  These are observations, not proof of playback failure or a Miowser regression.

This capture is plain desktop Chrome without Miowser's ad blocker. It does not
measure phone frame rates, establish video-start latency, or prove every listed
request happens inside Miowser. Site resource fan-out is a plausible contributor
to loading delay, but no broad site-specific timing or playback changes were
made from this short observation.

## Implemented behavior

`WebPermissionPolicy` builds an explicit review from frozen resource IDs:

| Capability | Android app permission | Confirmation | Remembered choice |
| --- | --- | --- | --- |
| Camera | CAMERA | Website prompt, plus Android permission if missing | Exact origin/resource, except private tabs |
| Microphone | RECORD_AUDIO | Website prompt, plus Android permission if missing | Exact origin/resource, except private tabs |
| Protected content/DRM | None | Two-step | Exact origin/resource, except private tabs |
| MIDI SysEx | None | Two-step, with device-control explanation | Exact origin/resource, except private tabs |
| Unfamiliar resource | WebView still determines support | Identifier shown; two-step Allow once | Never automatically persisted |

Existing Block decisions remain effective and can be forgotten in Site
permissions. Sensitive first-tap state is keyed to the individual request,
preventing a previous request's confirmation from authorizing a new one.
Insecure or malformed requests get a specific explanation. Android-denied
camera/microphone access and site DRM license restrictions are not bypassed.

`WebPermissionLease` and `WebPermissionResolution` bind a result to one request,
intersect grants with its requested resources, and remove the request from the
tab's tracking map on completion. Canceled requests do not call the platform
again. A pending Android permission result cannot be assigned to a newer site's
request. This fixes a real retention issue: previously answered requests stayed
in the tab's set until renderer/tab teardown and could be denied again then.

## Verification

JVM coverage checks resource names, DRM/MIDI/unknown review paths, runtime
permission mapping, secure origins, exact grant subsets, frozen request sets,
cancellation, one-time completion, and cleanup after platform failure. Debug
build and regression checks are run. Device testing remains with the user:
ARY Plus playback, both Allow choices, Block/Forget, private tabs, reload or tab
close during either prompt stage, and camera/microphone Android prompts.
