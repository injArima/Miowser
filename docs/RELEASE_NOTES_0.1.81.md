# Miowser 0.1.81 (code 82)

- Fixed playback speed resetting between videos. Explicit speed selections are
  saved as a player preference, shared by the rich and WebView players and
  included in settings backups.
- Rich-player creation and local playlist replacements restore the chosen speed
  instead of forcing 1x. The speed shown in the controls starts from that value.
- WebView playback restores the choice for each owned video/source and retries
  a limited number of times if a website resets it during loading. The selected
  rate also becomes that video's default playback rate.
- Temporary hold-to-play-at-2x does not overwrite the saved choice. Resetting to
  normal 1x through the speed control does save 1x for subsequent videos.

Device checks are left to you:

1. Set a precise speed such as 1.37x in each player. Switch videos through the
   side pane and through local/Network playlists; check the actual playback rate
   and its displayed value.
2. Exit and reopen the player, then restart the app. The chosen speed should
   remain selected. Check switching between the two player types too.
3. Hold for temporary 2x, release, and open another video. It should use the
   explicit saved speed rather than 2x.
4. Set normal 1x, then repeat the switches. Test a website that initializes its
   own speed after loading; retries should remain bounded if it rejects changes.
