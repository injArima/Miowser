# Miowser 0.1.29 (code 30)

- The WebView video assistant now reserves its limited frame tracking for actively playing, audible videos and frames relaying an active player. This helps embedded players appear on pages with many idle or muted video previews, including Anikoto.
- A brief fullscreen transition during rotation no longer immediately disconnects the WebView player and reports that the site ended fullscreen.
- The Anikoto episode adapter and saved-show stack remain outside this release, following the return to the 0.1.23 feature baseline.
