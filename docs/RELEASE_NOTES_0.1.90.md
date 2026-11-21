# Miowser 0.1.90 (code 91)

- Added search to Browser settings and Media/Player settings, including the
  Interface, Video, Subtitles, and Advanced pages.
- Results show their settings path and support related terms such as rotation,
  captions, wallpaper, and download threads. Hidden conditional settings are
  omitted.
- Selecting a result opens its page, brings the setting into view, and briefly
  pulses an outline around it. Reduce motion uses a steady highlight instead.
- Added individual subtitle-control targets, separate Browser/Media query
  state, and keyboard-aware results using the app's segmented list styling.

On-device testing is left to you. See `SETTINGS_SEARCH_0.1.90.md` for coverage
and implementation details.
