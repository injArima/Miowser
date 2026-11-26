# Miowser 0.2.08 (code 103)

- Added Background playback to the website settings sheet, with subtext explaining tab switching, minimized playback, screen locking and the single-session handoff rule.
- Permit the current playing session on enabled sites to continue across browser tabs and with the screen locked. Starting media in another tab pauses the previous session; unrelated hidden tabs cannot start themselves.
- Enable recognised music/radio players by default, including YouTube/YouTube Music, Spotify, SoundCloud, Apple Music, Amazon Music, Deezer, TIDAL, Pandora, Bandcamp, JioSaavn, Gaana, Audiomack, Qobuz, TuneIn and iHeart. Any other HTTP(S) site can be enabled individually. Explicit saved On/Off choices take precedence.
- Save exact-host choices in the existing site-preferences store, including backup/restore. The global Background playback option remains the master switch, and the site sheet explains when it is off.
- Apply scoped background visibility compatibility to the actual media-owning frame on permitted sites, including embedded players. No page reload is required to change the setting.
- Added a short, renewable playback-only CPU lease for screen-off media. It releases on pause, stop, owner replacement or service teardown; no screen wake or runtime permission prompt is added.

See [per-site background playback notes](SITE_BACKGROUND_PLAYBACK_0.2.08.md). Device testing remains with the user.
