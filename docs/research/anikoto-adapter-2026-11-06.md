# Anikoto adapter

Inspection of `https://anikototv.to/watch/one-piece-odmau/ep-1` on 2026-11-06 showed:

- `#w-episodes a[data-num]` contains the episode number, title on its parent `li`, watch URL, and separate `data-sub` / `data-dub` availability flags. The page rendered 1,180 episodes across range filters.
- `#w-servers .type[data-type="sub|dub"] li[data-sv-id]` contains the current episode's server choices. The same server ID can appear under both audio types, so the language is part of the choice identity.
- `#ani-seasons` is the site's container for other seasons, but was empty for the inspected show. The adapter accepts watch links from that container when populated.
- Server IDs and the site's opaque episode/server request tokens are not media URLs. A server choice is activated through the site's own click handler; Miowser's existing media detection then observes any playable stream.

The adapter reads the rendered DOM only for exact Anikoto watch URLs. It does not call or reproduce the site's private AJAX endpoints or decode its tokens. A page navigation clears the snapshot, and callbacks from an older navigation cannot overwrite the current tab. The snapshot is bounded to 2,500 episodes, 60 server choices, and 40 season links.

The Detected media page shows an Episodes action on Anikoto watch pages. The sheet filters episodes by Sub/Dub, lists the current episode's servers separately, and opens episode or season watch pages in the same tab. A future queue can use the episode identity while keeping server and language choices distinct.
