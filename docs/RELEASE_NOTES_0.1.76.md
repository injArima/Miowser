# Miowser 0.1.76 (code 77)

- Website controls, nested lists, carousels and media sliders take priority over
  browser pull-to-refresh. Refresh requires a clear downward gesture from the
  page top on ordinary content and respects the website's scroll restrictions.
- Reduced redundant media-activity reporting and disabled unused page-element
  deletion observers on pages without delete rules.
- Media detection rebinds correctly when a website changes tracks or episodes
  without loading a new document.

Device checks are left to you: try YouTube Music's swipe/seek controls and track
changes, ordinary top-of-page refresh, interrupted pulls, pinch and selection,
and the existing floating player button/side-pane flow. Compare actual song
startup under the same network and account conditions; a measured Samsung
playback-speed comparison was not performed.

See the [investigation and full checklist](research/samsung-page-interactions-2026-11-11.md).
