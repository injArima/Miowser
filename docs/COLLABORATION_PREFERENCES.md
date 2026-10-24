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

## Pending placement decision

The user proposes moving the browser video assistant action from inside the
playing video to immediately below its frame, aligned right. Discuss whether to
reserve a dedicated row to avoid covering page content, and how to offer the
action for embedded frames where rendering outside the frame is constrained.
The placement change is not implemented yet.
