# Miowser 0.2.00 (code 95)

- Added a portrait welcome card in More and master Settings, with a reversible Customize Miowser setup.
- Choose Browser first or Player first, then Browser only, Player only, or Both. Review the effect before applying; Cancel leaves preferences unchanged.
- Browser only opens directly in the browser and keeps Rich player, WebView player, subtitle tools, and downloads. Device library and its settings are hidden, and device scanning is suspended.
- Player only shows Device, Downloads, and More. Browser navigation, online watch catalog, and browser-specific settings are hidden; device playback and subtitle tools remain available.
- Browser only and Both ask for Automatic, Manual, or Disabled watch history. Existing records are retained when hidden, and private browsing remains excluded.
- Browser-only watch catalogs and all-downloads lists open as standalone pages with Back to browser, without the hub navigation bar.
- The browser menu now opens master Settings. Settings ordering follows the preferred experience, and Customize again stays accessible in every mode.
- Settings search respects the enabled experience; master Settings searches both settings areas. Saved tabs, folders, playback progress, permissions, and individual preferences are preserved when sections are hidden.

Build and JVM regression checks are performed. Device testing is left to you.
