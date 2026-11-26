# Per-site background playback — 0.2.08

## Control and behavior

Open the address-bar site/lock sheet → Site settings → Background playback. It follows the existing Material 3 card and switch styling. Subtext reads:

> Keep this site's audio playing across tabs, while Miowser is minimized, and with the screen locked. Playing media in another tab pauses it.

If the global switch is off, the sheet additionally explains that Background playback must be enabled in Media settings. Site choices remain editable/saved while the master switch is off. Changing the preference takes effect without reloading and does not start paused media.

Both the master switch and the site's effective permission are required for ordinary page background playback. The current media owner can then survive tab changes and Activity pause/screen lock. A new foreground tab playing real audible media transfers ownership and pauses every bound frame in the previous tab. An allowed but unrelated hidden tab is not eligible to start playback or take system controls. Turning permission off on an already hidden owner pauses/releases it immediately; turning it off on the visible page allows normal foreground playback until the page is left. Closing the owner, renderer loss, or switching to Rich player still retires/pauses the appropriate page session. Explicit player headphone/PiP modes retain their separate existing behavior.

Private system publication still redacts website metadata/artwork/URLs. Merely visiting a preset or backgrounding a site does not create a saved host record. Explicit preference edits use the same persistent site-settings behavior as the sheet's other controls.

## Presets and storage reference

`SiteBackgroundPlaybackPolicy.kt` is the single preset/override reference. It enables recognised player hosts for YouTube/YouTube Music, Spotify, SoundCloud, Apple Music, Amazon Music's regional music hosts, Deezer, TIDAL, Pandora, JioSaavn, Gaana, Audiomack, Qobuz, TuneIn and iHeart. Bandcamp's artist subdomains are enabled as a preset family. Examples of public web-player entry points: [YouTube Music](https://music.youtube.com/), [Spotify](https://open.spotify.com/), [SoundCloud](https://soundcloud.com/) and [Apple Music](https://music.apple.com/). Preset inclusion is an app configuration choice, not a guarantee of regional availability or playback support.

Unknown hosts default off. Similar-looking domains do not inherit a preset. User overrides are exact canonical host choices, shared across that host's HTTP(S) schemes and ports, consistent with existing site settings. They do not silently apply to siblings/subdomains. A user can therefore turn one Bandcamp artist off without changing another. Non-web and credential-bearing URLs are excluded. An explicit Off remains saved even if later versions change presets.

`SiteProtection.kt` loads/saves the `background_playback` string set in `site_protection`, using immediate in-memory updates and the existing ordered asynchronous writer. Rows encode `host|true` or `host|false`; old installations with no key inherit presets, and malformed rows are ignored. The existing backup preference inventory already includes the whole `site_protection` store, so no archive format/migration change is needed.

## Runtime reference

- BrowserScreen computes permitted tab IDs from current URLs and site preferences; saved changes trigger coordinator and native media-gate updates.
- PageMediaCoordinator combines site permission, the master switch, active/companion tab IDs and its single current owner. Permission is not a blanket exemption for all hidden engines.
- SiteMediaCompatibility no longer hardcodes five websites. A valid HTTP(S) media frame can receive an authenticated native grant based on its top-level site's preference. Only the actual media owner receives that frame's visibility compatibility; navigation, owner/source changes, revocation and retirement restore normal visibility. Site listeners are not suppressed and autoplay is not forced.
- BrowserPlaybackService hosts the Android media session and a PlaybackWakeLease only while media is playing. The lease acquires a 60-second partial CPU wake lock and renews after 45 seconds. It is released in cancellation/finally paths on pause, stop, owner replacement and destruction; outgoing jobs release before a replacement acquires. Failure to use optional platform power support does not crash playback or retry on every sample. The normal WAKE_LOCK permission is declared; no new dangerous/runtime permission is requested. No screen-on or Wi-Fi lock is added.

[Android background media guidance](https://developer.android.com/media/platform/mediaplayer/background) explains keeping playback in a service and maintaining CPU availability during screen-off playback. The implementation follows the [wake-lock timeout/release guidance](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set). Android/provider limitations, battery restrictions, connectivity loss and websites' own playback/capture/DRM rules can still stop playback. The toggle allows eligible playing page media to continue; it does not unlock unsupported or restricted streams.

## Verification

Checks cover music defaults, lookalike domains, explicit override precedence, exact-host matching, saved-state round trips, malformed data, master/site permission composition, sole-owner retention, arbitrary-site authenticated frame grants, wake-lease renewal/cancellation/failure, and existing media controls/metadata/hidden-tab protections.

Verification passed:

- `:app:assembleDebug :app:testDebugUnitTest`: 872 JVM tests, zero failures/errors/skips.
- `scripts/check-page-media.cjs`: 35 scenarios, including authenticated visibility grants on custom HTTP(S) hosts and revocation.
- `scripts/check-live-video-sessions.cjs`: 142 scenarios.
- `scripts/check-browser-page-interactions.cjs`: 25 scenarios.
- `scripts/check-media-discovery.cjs`: six scenarios and two composed-script syntax checks.
- `scripts/verify-media-artwork.cjs`: nine artwork-selection scenarios.

No emulator, physical-device, instrumentation or screen-off measurement was run. Device review: enable an ordinary site's toggle, start its media, switch to a quiet tab, minimize and lock the screen; confirm controls and audio persist. Start another tab's media and confirm a single owner. Turn the old site's permission off while it plays behind another tab and confirm it stops. Check a preset's explicit Off survives restart, the global master disables retention, private notifications stay redacted, and pause/stop/close do not keep a playback CPU lease alive.
