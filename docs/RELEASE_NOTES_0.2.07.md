# Miowser 0.2.07 (code 102)

- Fixed the missing website Media Session API in Android WebView by supplying a document-start playback adapter when the provider has no native API. Websites can now provide track title, artist, album, cover, position and supported transport handlers to Miowser's existing Android media session.
- Previous/next controls now receive the website's registered actions, rather than being absent because WebView never exposed the registration API. Keep actual site capabilities; do not show nonfunctional track buttons on unrelated pages.
- Added a scoped YouTube Music current-player fallback for track text, direct player-bar cover images, and enabled previous/next buttons. Website Media Session data/handlers take precedence. Late metadata and cover updates replace the notification without reloading the page.
- Publish display metadata and artwork for Android's system media surface and register the current session's notification tap destination. Position registered before media readiness is attached when the real player starts.
- Preserve private-notification redaction, stale-owner/command protection, hidden-tab playback restrictions, bounded artwork loading and queued media discovery.

See [implementation and verification](BROWSER_MEDIA_PRESENTATION_0.2.07.md). Device testing remains with the user; Android/One UI owns the cover cropping, background colors and progress animation.
