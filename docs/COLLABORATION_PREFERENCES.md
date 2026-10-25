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

Uniform single-tap entry is required. The user rejected the toolbar's extra page
tap and explicitly authorized restoring the original in-frame floating button.
That restoration supersedes the earlier below-player row and toolbar fallback;
see [the restoration report](research/video-assistant-single-tap-restored-2026-10-25.md).

Samsung's external-placement findings remain in
[the reinspection report](research/samsung-single-tap-recheck-2026-10-25.md).
Discuss and prove a revised external-placement route before replacing the
working browser-player entry path again. Placement must not add activation steps.

The user then explicitly selected a **native Android display prototype** with
additional work to preserve single-tap input. The current prototype uses a
decorative native face over the existing real web tap target on main-frame pages;
embedded frames retain their working in-frame overlay. Scope and device checks
are in [the prototype report](research/native-video-assistant-display-prototype-2026-10-25.md).

The user subsequently requested emulator testing and correction of native button
tap/scroll interception. The native layer now owns its target's gestures and
forwards a physical tap only after the bound document prepares its input guard.
This remains a single tap, retains the existing launch action, and prevents page
click-through. See [the input fix](research/native-video-assistant-input-fix-2026-10-25.md).

The latest placement request supersedes video-relative positioning for the native
floating button: anchor it to the browser screen, directly above the space
reserved for Jump to top. Keep that space reserved even when Jump to top is hidden.
The existing embedded-frame DOM fallback remains a separate implementation
boundary; it cannot render outside its iframe using the current input route.
