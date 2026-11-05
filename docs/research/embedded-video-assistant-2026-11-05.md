# Embedded video assistant

## Evidence and failure

The supplied Samsung Internet 30.0.5.24 APK receives a browser-engine
`onVideoStart` callback carrying a specific `TerraceMediaPlayerManagerClient`.
`MediaHandler` retains that client and checks its playing and muted state before
showing Video Assistant. `MAController` asks the same client whether its video is
visible. This is static APK evidence, not a live Samsung test in this change:
`ev_a6bfb3e9b006a0a449e4cb99721025c3bc1721d96df2dc5982d2934d142d53d8`,
`ev_88cc094d7e4da5115c2e62ed96a1c5225a93fea605a83954c7385facc49a5c9e`,
`ev_3495b331ef6955ad822fd3634aa5736dd13f91baa27d2daa927dc2922bd08e86`,
`ev_13a48c552c2cce8f584e72b621a8a16a3e062e7391c65278598bd0d175334346`.
The retained bundle is linked from the earlier
[Samsung research](samsung-video-assistant-plan-2026-10-24.md).

In a rendered Anikoto Black Clover episode, the main document contained no
`<video>`. Pressing Play inserted a cross-origin `megaplay.buzz` iframe with
`allowfullscreen` and `allow="autoplay; fullscreen; screen-wake-lock"`; its
embedded player began playback. This was observed in a desktop browser, not in
Miowser's Android WebView. Miowser already injected its live-video script into
frames, but its policy disabled the action outside the main document.

## Implementation

Active, audible frame-local video can nominate the same native screen-anchored
action. Frame messages are relayed through their actual parent iframe, translating
the target point at each nesting level. The native action still owns the gesture.
On press, the exact frame and video revalidate playback and return a fresh target;
the actual down/up events are then sent to that frame's video, where a short-lived
guard handles Chromium's trusted click and requests fullscreen synchronously.
No second in-frame button or ExoPlayer instance is created. Existing main-frame
playback remains the first choice when both candidates exist.

Routes are bounded to eight active frames, have four-second leases, and are
discarded on frame removal/navigation. Messages require a bound document,
navigation generation and token before they reach the native display. The tap
must still target the same element and current display version. Invalid geometry
or stale acknowledgements cancel the action. Paused, muted, ended or invisible
videos do not qualify.

## Boundary and user checks

Android WebView does not expose Samsung's Terrace media client. This route works
for accessible HTML video in frames that permit fullscreen; closed shadow roots,
DRM/protected sessions or providers that deny fullscreen may remain unavailable.
No DRM handling was changed.

On-device checks are left to the user. Play a normal Anikoto episode and confirm
the native button appears above Jump to top, remains a single tap, opens the
same playing stream, and keeps Miowser's WebView player controls usable. Switch
episodes/servers, pause/mute/end, navigate away, and check that stale buttons
disappear. Also check a main-page video, a muted preview, nested frames, scroll,
zoom and rotation for regressions.
