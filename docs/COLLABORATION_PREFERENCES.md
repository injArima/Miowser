# Collaboration preferences

Recorded from the user's instruction on 2026-10-25.

- When a suggestion has a materially better alternative for the app as a whole,
  share that alternative and explain the relevant experience, performance,
  compatibility and maintenance tradeoffs. Discuss and agree on the route before
  implementing a different approach. Do not invent objections or add approval
  steps to routine work already authorized by the user.
- Keep existing functionality intact unless a change is explicitly agreed.
- Leave device/runtime checks to the user unless they explicitly request them.
  Never remove app data as part of investigation or testing without authorization.
- Commit and push changes to GitHub regularly so earlier versions can be restored,
  as authorized by the user during the browser video assistant work.

## Video action placement and interaction

- Keep browser playback on its existing WebView session, separate from ExoPlayer.
  This is an additional route; preserve existing discovery and player workflows.
- Uniform single-tap entry is required. The rejected below-player row and toolbar
  arming route must not return. Discuss a different approach before replacing the
  working launch path. [Samsung evidence](research/samsung-single-tap-recheck-2026-10-25.md).
- The native Android layer owns the target's gestures and forwards the physical
  tap only after the bound document prepares its input guard. Preserve the
  document/source ownership and click-through protections.
  [Input implementation](research/native-video-assistant-input-fix-2026-10-25.md).
- Anchor the native floating button to the browser screen, directly above the
  reserved Jump-to-top slot, even when Jump to top is hidden. Keep the 48 dp face,
  60 dp touch target and 28 dp mark. Do not show a second, frame-local DOM button.
- Qualify the action from an actively playing, audible main-document video. Muted
  autoplay previews, paused/ended videos, hidden pages and embedded-frame-only
  videos do not get a button. Keep the screen anchor while the qualified video
  scrolls offscreen; do not auto-hide it merely because of inactivity.
- Use the [current implementation and cleanup overview](research/samsung-video-assistant-plan-2026-10-24.md)
  for present behavior; older reports describe historical checkpoints.
