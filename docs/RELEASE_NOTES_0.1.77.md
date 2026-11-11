# Miowser 0.1.77 (code 78)

- The app name uses bundled Racing Sans One across app text, including inline
  mentions. Other words retain their existing Material typography. The brand
  font is centralized in `BrandTypography` and needs no network font download.
- Device, Network and Downloads now show **Miowser** in their top bars. Network
  no longer shows a video count under the heading.
- Playing device or downloaded files no longer creates Network Library records.
  Removed the local-playback recording flag and the Network-specific local
  midpoint thumbnail and metadata work. Device scanning, artwork, playlists and
  resume behavior remain available. Local resume positions use separate bounded
  playback bookmarks, included in backups, without creating Network cards.
  Existing saved entries are retained.

Device checks are left to you:

1. Check the three tab headings, browser home, onboarding, dialogs and snackbars
   in light, dark and AMOLED themes. Only the app name should use Racing Sans One.
2. Play a device video and a downloaded video. Their positions should still resume
   correctly, and neither should create a new Network entry.
3. Play an online video through the rich player. Automatic, manual-only, disabled
   and private Library behavior should continue to work as configured.
