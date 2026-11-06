# Miowser 0.1.26

- Anikoto watch pages show a floating episode button above the browser video button. Its sheet groups episodes, Sub/Dub versions, and current episode servers.
- The rich player uses its playlist for Anikoto episodes and adds server and language choices. Switching resolves the stream in the existing site WebView while the player stays open. It tries the selected server first, then the other servers for that language; failures offer Retry, another server, and the source page.
- The next episode starts automatically when playback ends.
- Save show creates one expandable Library entry. Episodes retain their own watch progress, and the show follows Library folders and sorting. Playback URLs are resolved when needed rather than saved with the show.
- Version code 27.
