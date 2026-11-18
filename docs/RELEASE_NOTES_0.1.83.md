# Miowser 0.1.83 (code 84)

- Browser entry now restores saved ad protection before the first external page
  loads. Saved site exceptions are restored too, including when opening a source
  pane directly from the rich player. Later navigation keeps its immediate path.
- Compatible APK updates can reuse compiled filters. Engine, bundled-list,
  public-suffix and Music-resource changes still invalidate incompatible caches.
- Added a bundled, domain-scoped YouTube Music protection resource. It targets
  player ad fields in initial data and supported JSON/fetch/XHR responses, with
  support for subsequent songs and SPA navigation. It respects the global
  ad-block switch and the site's protection setting.
- Added service-worker request filtering where WebView exposes a usable source
  origin/referrer. Missing or opaque contexts pass through rather than being
  attributed to an unrelated tab.
- Stop, history navigation, closing a source pane, and retiring a renderer cancel
  pending protected loads. Missing saved lists are distinguished from active
  saved/bundled protection; filter downloads remain outside the readiness gate.
- Removed the old cold-start prompt to refresh the webpage after filters load.

The Music resource implements curated player-data rules, not arbitrary remote
scriptlets or full uBlock/Brave filter syntax. Raw response body streams and
unsupported providers/readers remain untouched. Older WebViews without
document-start support use the later fallback. Server-inserted ads and all future
site changes are not covered by a universal blocking guarantee.

Device testing is left to you: check cold and warm starts, offline starts, first
and subsequent Music songs, protection Off/On, Stop while preparing protection,
and source-pane playback in both players.
