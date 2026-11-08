# WebView video detection audit — code 43 onward

Implemented in Miowser 0.1.47 (code 48).

## Scope and retained behavior

Reviewed the companion-browser changes from `213c7d9` (code 43), subsequent
automatic-entry attempts, their removal in `4d27939`, and the metadata correction
in `a369e2b`. The retained route is a screen-anchored native floating button that
opens the selected existing HTML video on one physical tap. This also applies
when choosing another episode in the companion pane. Detection does not enter
fullscreen or start playback on its own. The rich player remains separate.

## Fresh Samsung APK inspection

Inspected the supplied `Samsung Browser_30.0.5.24.apk` with REA's Android method
inspection through JADX 0.7.1. This is static evidence, not a new live Samsung
website test. APK SHA-256:

`7431a0efe90bb769506f4a365c7c99828384cc235ce16c1da2bb0d00f997129b`

| Samsung method | Observed behavior | Application to Miowser |
| --- | --- | --- |
| `MediaHandler.onVideoStart` | Tracks the current live media client and updates its listener/assistant when the client changes. | Keep one exact media owner; do not reopen a captured URL or replace its playback engine. |
| `MediaHandler.setVAVisibility` | Updates media information from the current client, URL and title; hides the assistant when playback is stopped or muted. | Keep preview checks, observe readiness/playback/volume changes, and retain the latest episode metadata. Miowser deliberately retains its existing long-form muted-video eligibility. |
| `MediaHandler.onEventFired` | Ignores events from another client; rechecks on volume changes and handles layout/fullscreen events. | Reject stale navigation messages; refresh discovery on late media events. |
| `MediaHandler.onTabShown` | Shows the video assistant again when the tab returns. | Retain existing visibility/reconnect handling. |
| `MAController.showVAView` | Checks visibility through the selected Terrace client, with keyboard/device-state guards. | Prefer a visible eligible video when several are playing. Keep the button's screen position independent of the video frame. |
| `MAManager.start` | Requests fullscreen through the selected media object, then hides the assistant. | Preserve trusted single-tap entry into the existing video. |

Retained method evidence IDs, in table order:

- `ev_970d26e7b8a2850059b3c1da2c3670973130cd411fd4ae4e38d6dcc7459dea64`
- `ev_92b1fee7c5c017534262aff0b02b4436b875fba80347b520d0206bd8c2d1ed2d`
- `ev_41d74d34259033c4a3841fd30b63103885dd133e2c96728b71495569629d1a8f`
- `ev_1de0318076d0c160b58b2faadb1681a904ab10f4772c11274c3f1f8289d743b5`
- `ev_e51aabf3ca1b59a36210bd19d87375c9236ef3e144deb117b328529b32b93e45`
- `ev_18a550815e30d2960afca5fbb9a198a019774d2994f2189d79fb002d35150561`

Raw evidence was exported locally to
`app/build/research/samsung-detection-2026-11-09.json` (ignored build output).
The report contains behavioral observations, not copied Samsung implementation.

## Gaps fixed

| Gap | Change |
| --- | --- |
| Only the first 32 iframe elements were examined for a child player's announcement. | Search all document/open-shadow iframe elements on a cache miss. Cache verified child-window-to-frame matches in a WeakMap, rechecking attachment and window identity before reuse. Unrelated page messages do not trigger this lookup. |
| Idle previews could fill the eight-record table before a muted long video acquired metadata. | Discover on `playing`, `loadeddata` and `durationchange` as well as the existing play, metadata and volume events. Existing eligibility and bounded record limits remain. Listeners are removed on teardown. |
| A delayed reset could disconnect a newer binding on a reused SPA document. | Tag native resets with their navigation generation and ignore older resets in the document. |
| An asynchronous page-commit probe could reconnect/inject after navigation moved on. | Discard the callback unless its captured navigation is still current. |
| A huge offscreen playing video could beat a smaller on-screen video. | Prefer visible area, while retaining recent explicit video interaction priority and offscreen screen-anchor support when no better candidate exists. |
| An untouched main-frame video was treated as recently interacted with during the first five seconds, preventing a larger embedded player from winning. | Treat missing interaction as absent rather than as a timestamp at page startup. |
| The assistant could remain hidden until another event after fullscreen ended. | Schedule a fresh display update after the existing fullscreen-exit grace period restores website controls. |

## Verification

- `node scripts/check-live-video-sessions.cjs`: 142 checks passed. Added regressions
  cover >40 unrelated iframes, verified-cache reuse/invalidation, stale reset and
  reconnect, late metadata/readiness at capacity, visible-video selection and
  recent interaction priority, and initial embedded-player priority. Replaying the
  same harness against the prior committed scripts fails eight checks; the updated
  scripts pass all 142. Existing checks cover trusted input, same-video
  fullscreen, source revisions, paused intent, teardown, DRM non-interference,
  controls, gestures and lifecycle contracts. These are simulated DOM checks.
- Debug APK assembly, release Kotlin compilation, and all 704 unit tests passed.
- `NativeVideoAssistantDisplayTest`: all three emulator tests passed on Android 17.
  These exercise native input ownership, host cleanup, and the production trusted
  fullscreen-click path in a real WebView after scrolling, even when the DOM hit
  target is absent. The controlled fixture does not download real media or prove
  playback on Anikoto/YouTube; those live-site checks remain in the release checklist.

## Practical boundary

Samsung uses private Chromium/Terrace media-client callbacks. Android WebView
does not expose equivalent native handles to Miowser. Our implementation therefore
uses document-start injection, accessible DOM media events and authenticated
per-frame bridge messages. Closed shadow roots, a page-denied Fullscreen API,
an unavailable injection capability, or protected/custom playback with no exposed
HTML video can still prevent detection or fullscreen entry. These changes do not
bypass DRM or establish universal website support.
