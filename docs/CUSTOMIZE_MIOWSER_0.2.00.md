# Customize Miowser — 0.2.00

## Entry and flow

The More page and master Settings share a neutral, rounded portrait card. Its
4:5 aspect ratio and 400 dp maximum width live in `ExperienceTokens`; text inside
the card can scroll independently for large fonts, and Customize stays at the
bottom right. The containing settings list also scrolls.

Customize follows Introduction → Priority → Features → Watch history → Review.
Player only skips Watch history. Both and Browser only explicitly offer
Automatic, Manual, and Disabled. Back revisits choices; Cancel/Explore first
return without applying. Draft choices survive activity recreation.

Apply publishes one settings snapshot, updates the destination and scanning
policy, and shows the updated card in master Settings. Back then reaches the
selected home. Customize again is available regardless of hidden sections.

## Capabilities

| Choice | Startup | Hub/navigation | Watch catalog |
| --- | --- | --- | --- |
| Browser only | Browser | Browser menu; standalone catalog/downloads pages with Back; no hub rail | User chooses Automatic, Manual, or Disabled |
| Player only | Device | Device, Downloads, More; no browser FAB | Hidden; previous online history preference retained |
| Both | Browser or Device according to priority | Device, optional Network, Downloads, More, browser FAB | User chooses Automatic, Manual, or Disabled |

Browser only retains both browser-enhancement players and all subtitle tools.
Player only retains local/downloaded playback and subtitle tools. Local resume
is independent of online watch history. Disabled history hides Network and its
browser-menu entry without deleting records. Manual saving continues through
the existing detected-media actions; automatic recording and private browsing
retain their existing rules.

Both priorities affect the order of Browser/Media rows in master Settings and
More. Priority never disables a feature and is remembered while using a single
experience. Downloads, appearance, backups, diagnostics, and applicable player
preferences remain reachable. Device-only browser requests lead to Settings
instead of silently enabling the browser; download-link recovery explains how
to re-enable it.

## Persistence and ownership

`settings/AppExperience.kt` defines the pure mode/priority/step model, summaries,
and application transform. `Settings` exposes effective capabilities rather
than overwriting individual preferences. `SettingsStore` saves enum names and
the customization flag in the existing backed-up settings store. Unknown or
missing keys retain the existing Both / Player first experience on upgrade.

Device scanning uses `deviceLibraryAvailable && deviceDiscoveryEnabled`.
Switching to Browser only cancels an active scan; switching back restores the
saved discovery choice. It does not revoke permissions, unlink folders, or
clear the index. Player only pauses browser-owned media and does not request
browser preparation. Saved tabs remain available when Browser is re-enabled.

`ExperienceSettings.kt` owns the shared card, settings rows, and draft wizard.
`BrowserScreen` routes existing settings, players, catalog, and download views.
`BrowserBackNavigation` keeps browser-only Back from exposing the hidden hub.
`OverflowMenuSheet` reuses the Hub shortcut as Watch catalog in Browser only
and hides it when history is disabled, preserving stored menu order. Standalone
catalog/download headers expose options; the download settings sheet is shared
with the hub rail so folder selection and reconnection stay available.

Search omits device-library controls in Browser only and browser/history/source
controls in Player only. Master Settings combines both search scopes; individual
settings pages keep their existing scopes. Results still jump and pulse.

## Verification and device checklist

JVM tests cover all 18 mode/priority/history combinations, default upgrades,
preference retention, scan gating, wizard branches, search availability, and
browser-only Back behavior. Existing regression tests and a debug build are run.

Device testing remains with the user: try each combination, Cancel and Back at
each step, rotate mid-setup, check large fonts, restart the app, re-enable hidden
features, open browser shortcuts in Player only, play local/downloaded videos,
use both browser players/subtitles, save manually/automatically, and verify
standalone catalog/download navigation with every history mode.
