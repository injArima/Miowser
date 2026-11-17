# Miowser 0.1.82 (code 83)

- Added Android media controls for webpage playback, including track information,
  artwork, play/pause and seeking. Previous/next controls appear when the website
  provides those actions through its Media Session API.
- Extended **Settings → Video → Background playback** to webpage audio, including
  playback while Miowser is minimized or the screen is off. Added narrowly scoped
  background compatibility handling for YouTube, YouTube Music and Spotify.
- Notification taps return to the playing tab. Playback ownership follows the
  current tab, document and media source, and hands over to Miowser's players.
- Private tabs publish generic media information without artwork. Closed pages,
  stale frames and inactive paused sessions release their media ownership.

Websites still control their streams, authentication and DRM. These changes do
not bypass protected playback or add unsupported codecs.

Device testing is left to you. Check webpage music controls, track changes,
screen-off playback, the background-playback toggle, and transitions into both
players and the source side pane.
