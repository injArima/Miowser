# Segmented-list migration — 0.1.89

Reviewed the [Material 3 Expressive list specifications](https://m3.material.io/components/lists/specs),
including segmented styling, interaction shapes, flexible slots, and touch
targets. The app uses grouped neutral rows with 16 dp outer corners, 4 dp inner
corners, and 2 dp gaps. Regular rows have a 64 dp minimum height and grow with
wrapped text. Compact theme-mode choices retain their 48 dp minimum target.

## Shared implementation

- `SegmentedListTokens` in `ui/theme/ComponentTokens.kt` owns the geometry.
- `ui/SegmentedLists.kt` provides the grouped container, item modifier, lazy-row
  shapes, and native Material `ListItemShapes` for interaction states.
- Non-lazy groups clip their actual visible children at the outer corners.
  Conditional rows therefore cannot leave stale first/last positions. Lazy
  lists calculate positions from the current data list, excluding headings.
- Standard navigation rows use native Expressive `ListItem` leading/content/
  trailing slots. Shared item shapes keep pressed/focused states rounded.
- Grouped rows replace inset divider lines with narrow gaps. Single-item
  groups are rounded on all corners. Custom compound editors remain a single
  segment rather than splitting their labels and controls into separate rows.
- Neutral colors remain responsible for ordinary backgrounds/text/boundaries;
  selected browsing-data and appearance choices use Secondary. Complementary
  leading icons use Tertiary; destructive content retains semantic error red.

## Where to check

| Page / area | Changed elements |
| --- | --- |
| Browser Settings | All shared settings sections: navigation, switches, compound choice blocks, and the About version row; navigation links have relevant icons. |
| Media settings | Library, Video, Network, Permissions, History, and Extra settings groups. Extra settings rows include supporting descriptions. |
| Interface settings | Appearance, Color theme, and Reduce motion segments. |
| Video settings | Display choice segments. |
| Advanced media settings | Optional features and diagnostics groups. |
| Subtitle appearance settings | A single rounded settings segment below the existing subtitle preview. |
| Hub More section | Media settings and Backup & restore rows. |
| Device / Network Library option sheets | Segmented storage-access and Library-tools navigation actions; View/Sort controls use a rounded compound segment. |
| Browser menu | The expanded Page Actions list, including desktop-mode control; Quick Access and the horizontal action tiles retain their layouts. |
| Theme picker | System/Light/Dark/AMOLED choices form one segmented group. Circular palette previews retain their gallery layout. |
| Bookmarks | One lazy segmented list with stable URL keys, two-line titles, and independent remove controls. |
| Site permissions | Segmented decisions with independent Forget actions. |
| Clear browsing data | Separate website-data and saved-record checkbox groups; selected rows use the selected shape and fill. |
| Media library folders | Storage and folder rows use current first/middle/last shapes. Folder browsing and inclusion controls remain separate actions. Linked-folder records form a segmented management group. |
| Blocked page elements | Each site's rules form a segmented group; host heading and Revert all remain outside the rule segments. |
| Crash reports | Segmented report rows below the explanatory text. Open, copy, share, and clear behavior is preserved. |

History and the player subtitle inspector already use segmented lists and keep
their existing geometry. Video grids, media artwork cards, charts, and compound
filter-list editors are not settings/navigation lists and retain their layouts.
No player-overlay redesign or settings/data migration is included.

## Verification

Debug build and the existing JVM regression suite are used for compile/data
regression checks. Device visual and touch testing remains with the user.
Check first/last/single rows, conditional Auto rescan and Clear Library rows,
large fonts, long labels, RTL, nested checkbox/remove/Forget actions, disabled
Library actions, and the two-stage/scrollable bottom sheets.
