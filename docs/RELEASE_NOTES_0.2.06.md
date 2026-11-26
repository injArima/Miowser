# Miowser 0.2.06 (code 101)

- Removed automatic background-tab eviction at the live-tab limit and on Android memory-pressure callbacks. Switching tabs retains the existing page, navigation stack and form state instead of destroying its WebView and forcing a reload.
- Stop hidden-tab media when another tab is selected, including bound cross-origin players and detached audio elements. Native WebView audio muting supplies an immediate fallback on supported providers, including Web Audio. Returning to a tab grants playback permission without automatically restarting it.
- Preserve intentional background playback for the current page's music session and supported fullscreen headphone/PiP modes. A previous tab cannot reclaim the music notification after the user switches tabs.
- Spread media discovery across small asynchronous DOM traversal batches instead of scanning large inserted subtrees synchronously in page mutation callbacks. Media play/readiness listeners still discover an interacted player directly.
- Observe genuine renderer stalls without automatically killing/reloading the tab; mute its audio while unresponsive. Genuine renderer loss still has explicit recovery because Android cannot reuse a dead WebView.

See [implementation and validation notes](BROWSER_TAB_LIFECYCLE_0.2.06.md). Device testing remains with the user.
