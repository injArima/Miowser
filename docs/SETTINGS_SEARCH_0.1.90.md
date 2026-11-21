# Settings search — introduced 0.1.90, updated 0.1.91

Browser settings and Media settings have a permanent inline search field beneath the title bar. Media
search also covers Interface, Video, Subtitles, and Advanced. These are the six
existing destinations hosted by the shared settings search controller.

## User flow

- Tap the visible field to type on the same settings page; no separate Search
  screen or autofocus navigation is used. Results replace the page body while
  the query is nonblank and show a settings path and short explanation.
- The existing destination opens, the target is brought into view, and a
  Primary-family outline gently pulses twice. Reduce motion uses a steady
  outline for the same brief interval instead.
- Navigation/action entries focus their existing settings row; search does not
  execute Clear, Reset, permission, or backup actions automatically.
- Clear or Back restores the original settings body and its scroll position.
  The body stays composed while searching, and its accessibility nodes are
  hidden beneath the results. Result selection clears the query and keyboard.
  Queries use saveable UI state, not a new settings/history preference store,
  and clear when leaving settings. Empty results use a full-width centered
  icon, heading, and explanation beneath the persistent field.

## Coverage and matching

`settings/SettingsSearchIndex.kt` contains 48 definitions, with paths,
descriptions, destination metadata, anchors, and aliases such as rotation,
captions, wallpaper, and parallel download threads. Matching ignores case,
diacritics, punctuation, and extra whitespace; all entered words must match.
Exact titles rank ahead of partial and descriptive matches. Matching is local,
with normalized strings cached in memory.

Auto rescan is omitted when discovery is disabled. Resume playback and Clear
Library follow the same availability conditions as the actual settings rows.
Outline color is omitted when subtitle outlining is off. Unsupported settings
such as hardware acceleration are not invented as search results.

Subtitle results target the actual size, bold, color, opacity, outline, offset,
preset, font, or reset control rather than the entire appearance card. A tall
compound setting scrolls its heading/initial controls into view rather than
requesting an oversized rectangle.

## Implementation reference

| Source | Responsibility |
| --- | --- |
| `settings/SettingsSearchIndex.kt` | Pure searchable catalog, normalization/ranking, and conditional availability. |
| `ui/SettingsSearch.kt` | Inline field/results, query/back state, jump sequencing, scroll requests, timeout cleanup, and pulse drawing. |
| `ui/SimplePageScaffold.kt` | Contextual inline search field/body and title anchors. Unrelated pages have no search field. |
| `ui/SettingsPage.kt` | Shared anchors for navigation, switch, and compound option rows, plus Version. |
| `ui/SubtitleAppearanceControls.kt` | Individual subtitle-control anchors. |
| `browser/BrowserScreen.kt` | Routes through existing screens and preserves the Browser-to-Media return path. |
| `ui/theme/ComponentTokens.kt` | Query bound, highlight width/opacity, pulse count, highlight hold time, and jump timeout. |
| `ui/theme/MotionPolicy.kt` | Shared pulse easing/durations, honoring existing motion policy. |

Only a pending matching target installs draw/scroll owners. Pulse values are
read during drawing, not as whole-page composition updates. Requests have
sequence guards, clear after feedback, and expire rather than lingering after
navigation or a missing target.

When adding or renaming a setting, update its catalog definition and ensure its
anchor equals the shared row label or explicitly marked control group. Page
titles may be targets for category results. Search routes must remain actual
settings destinations and must not apply preference changes.

## Verification

JVM coverage checks catalog IDs/anchors, normalization, aliases, ranking,
destination metadata, scope separation, and hidden-setting filtering. The
existing app regression suite and debug build are also run. Device testing
remains with the user: keyboard/back behavior, large fonts, long pages,
repeated jumps, reduced motion, conditional rows, and return navigation.
