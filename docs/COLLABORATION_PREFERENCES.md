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

## Agreed video action placement

The user approved a dedicated row immediately below the detected player, aligned
right, with a browser-toolbar fallback for frames/layouts that cannot safely
reserve that space. Implementation and the user's device checklist are recorded
in [the placement report](research/video-assistant-below-player-2026-10-25.md).
