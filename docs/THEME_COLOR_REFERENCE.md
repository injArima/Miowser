# Miowser theme color reference

Reference for the 14 palette themes introduced in 0.1.84. This describes the
current implementation, including Night's updated family assignments in 0.1.85.

## Numbered family convention

Use these numbers when requesting component or theme color changes:

| Number | Family | Night source color |
| --- | --- | --- |
| **1** | Primary | `#ABD2FA` (light blue) |
| **2** | Secondary | `#7692FF` (periwinkle) |
| **3** | Tertiary | `#1B2CC1` (royal blue) |
| **4** | Neutral | `#091540` (navy) |

These are family numbers, not source-list indices. Component foregrounds,
containers, and light/dark tones continue to derive from the assigned family.
Delete/error colors remain separate.

## How many colors do we use?

Each theme has **four input slots: primary, secondary, tertiary, and neutral**.
These are three accent families and one background/text family. Some themes
reuse a source color in two slots, so they have three distinct inputs rather
than four. The supplied images contain 4, 5, or 10 original swatches; keeping
those swatches in the catalog does not mean every swatch becomes an independent
component color.

The inputs generate **48 Material color roles per appearance**. Excluding the
four semantic error/delete roles leaves **44 roles**. Roles are named purposes,
not 44 unique hex colors: for example, `surfaceTint` equals `primary`, and
`background` equals `surface`. The number of distinct rendered colors also
depends on light/dark/AMOLED mode, transparency, disabled states, and overlays.

No single component uses all 44 roles. Components consume a small selection,
often a background and its matching foreground, with additional roles for
selection, borders, or supporting text. Material components also consume roles
through their defaults; counting only explicit `MaterialTheme.colorScheme`
references would miss those uses.

**Delete red is excluded from the 44-role count.** Keep `error`, `onError`,
`errorContainer`, and `onErrorContainer` separate from palette editing. These
roles are shared across all 14 custom themes, with separate light/dark values.
This exclusion is semantic, not a ban on red: Halloween, Amber, Summer Ocean,
Space Man, Ice Cream, and Umbrella intentionally contain red or pink accents.

## Source colors used by each theme

These are the exact RGB inputs before contrast and tonal adjustment. The slot
order in code is `palette(primary, secondary, tertiary, neutral)`; its numbers
are **zero-based indices** into the theme's `swatches` list.

| Theme | Image swatches | Distinct inputs | Primary | Secondary | Tertiary | Neutral | Index mapping |
| --- | ---: | ---: | --- | --- | --- | --- | --- |
| Sea | 4 | 4 | `#1C4D8D` | `#4988C4` | `#BDE8F5` | `#0F2854` | `1, 2, 3, 0` |
| Amber | 10 | 4 | `#F48C06` | `#D00000` | `#FFBA08` | `#03071E` | `7, 4, 9, 0` |
| Black Gold | 5 | 4 | `#FCA311` | `#14213D` | `#E5E5E5` | `#000000` | `2, 1, 3, 0` |
| Summer Ocean | 5 | 4 | `#457B9D` | `#E63946` | `#A8DADC` | `#1D3557` | `3, 0, 2, 4` |
| Halloween | 4 | 3 | `#FF0000` | `#830000` | `#000000` | `#000000` | `3, 1, 0, 0` |
| Retro | 4 | 3 | `#728E6D` | `#E5CD82` | `#15370D` | `#15370D` | `1, 2, 0, 0` |
| Sunset | 4 | 3 | `#FF6F3C` | `#155263` | `#FFC93C` | `#155263` | `1, 0, 3, 0` |
| Space Man | 4 | 3 | `#FF204E` | `#00224D` | `#5D0E41` | `#00224D` | `0, 3, 2, 3` |
| Wood | 4 | 4 | `#B85C38` | `#E0C097` | `#5C3D2E` | `#2D2424` | `2, 3, 1, 0` |
| Cotton Candy | 4 | 3 | `#BEAEE2` | `#CDF0EA` | `#F7DBF0` | `#BEAEE2` | `3, 0, 2, 3` |
| Grass | 4 | 4 | `#5F8D4E` | `#A4BE7B` | `#E5D9B6` | `#285430` | `2, 1, 0, 3` |
| Ice Cream | 4 | 4 | `#00B7CD` | `#DF301C` | `#FF9100` | `#FFF1D1` | `3, 0, 1, 2` |
| Night | 4 | 4 | `#ABD2FA` | `#7692FF` | `#1B2CC1` | `#091540` | `3, 2, 1, 0` |
| Umbrella | 4 | 4 | `#2391FF` | `#F70063` | `#FFF83F` | `#FFCD54` | `0, 3, 1, 2` |

The primary/secondary/tertiary colors are adjusted only when their original
values cannot meet the configured contrast target against the relevant
surfaces. Containers and foregrounds use derived tones. Neutral colors are
mixed with gray before deriving surfaces and text; backgrounds therefore do
not reproduce a bright source swatch at full intensity.

## The 44 non-error roles

| Family | Role count | Names |
| --- | ---: | --- |
| Primary | 8 | `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` |
| Secondary | 8 | `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `secondaryFixed`, `secondaryFixedDim`, `onSecondaryFixed`, `onSecondaryFixedVariant` |
| Tertiary | 8 | `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer`, `tertiaryFixed`, `tertiaryFixedDim`, `onTertiaryFixed`, `onTertiaryFixedVariant` |
| Surface/background | 10 | `background`, `surface`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceVariant` |
| Neutral text/icons | 3 | `onBackground`, `onSurface`, `onSurfaceVariant` |
| Inverse surfaces | 2 | `inverseSurface`, `inverseOnSurface` |
| Boundaries | 2 | `outline`, `outlineVariant` |
| Inverse accent | 1 | `inversePrimary` |
| Surface tint | 1 | `surfaceTint` |
| Scrim | 1 | `scrim` (black) |
| **Total, excluding error/delete** | **44** | |

## Which component uses which colors?

Counts below describe the named roles for the indicated base state. They do
not count transparency as a separate color, nor include every pressed,
disabled, or focus state. A different component variant can use different roles.

| Component / state | Main roles | Count |
| --- | --- | ---: |
| Filled primary action | `primary`, `onPrimary` | 2 |
| Filled tonal action | `secondaryContainer`, `onSecondaryContainer` | 2 |
| Text action on its host surface | `primary`; the host supplies the background | 1 plus host |
| Standard outlined action (current Material 3 library) | `onSurfaceVariant`, `outlineVariant`; the host supplies the background | 2 plus host |
| Shared settings section/card with body and supporting text | `surfaceContainerLow`, `onSurface`, `onSurfaceVariant` | 3 |
| Shared bottom sheet with title and supporting text | `surface`, `onSurface`, `onSurfaceVariant` | 3 |
| Selected theme card | `surfaceContainer`, `onSurface`, `primary` border | 3, plus decorative source swatches |
| Unselected theme card | `surfaceContainerLow`, `onSurface`, `outlineVariant` border | 3, plus decorative source swatches |
| Shared snackbar with action and dismiss control | `surfaceContainerHigh`, `onSurface`, `primary`, `onSurfaceVariant` | 4 |
| Standard checkbox or switch | Selected/unselected track, thumb, outline, and content roles come from Material defaults | Varies by state |
| Browser bottom-bar surface and home pressed highlight | `surfaceContainerLow`, `primaryContainer`; icons/buttons add their own roles | 2 plus controls |
| Multi-connection download chunks, excluding semantic red | `primary`, `tertiary`, `secondary` | 3; currently also cycles through `error` |
| Delete action | `error`, or `errorContainer` with `onErrorContainer`, depending on style | Excluded |

Source examples:
[action buttons](../app/src/main/java/com/miowser/app/ui/MiowserActionButton.kt),
[settings sections and sheets](../app/src/main/java/com/miowser/app/ui/ExpressiveSurfaces.kt),
[theme picker](../app/src/main/java/com/miowser/app/ui/ThemePalettePicker.kt),
[snackbars](../app/src/main/java/com/miowser/app/ui/MiowserSnackbarHost.kt),
[download progress](../app/src/main/java/com/miowser/app/browser/HubPage.kt).

Theme choices appear as a horizontal gallery of equal-height rounded cards.
Every source swatch gets its own pill. Families appear in order 1, 2, 3, 4,
with repeated source colors shown once; any additional image swatches follow.
Larger palettes wrap their pills within the card, so Amber retains all ten
colors. Wallpaper is identified with an icon because its colors are generated
by Android rather than stored as fixed source swatches.

### Exceptions to keep in mind

- The semantic error family is not exclusive to delete buttons: it also shows
  failures and warnings, and the multi-connection download progress bar
  currently uses `error` as its fourth chunk color. That is existing behavior;
  this reference does not alter it.
- Player overlays have fixed `PlayerUiColors` for white text, dark panels,
  directional seek feedback, and other cues. Player Material controls can use
  the selected palette, but those fixed overlay colors are outside the
  four-input palette system.
- HTTPS status uses fixed green `UiSemanticColors`. Media artwork, website
  content, source-color previews, and user-selected subtitle colors are also
  outside the 44-role count.
- AMOLED overrides the neutral surfaces with black and gray values in
  `Theme.kt`; it keeps the selected dark palette's accents.
- Wallpaper mode uses Android's dynamic scheme on supported versions. The
  existing Miowser fallback has its own definitions in `Color.kt`/`Theme.kt`.
  The 14-theme source table above does not describe those two choices.

## Where to make changes

| Desired change | Edit here | Effect |
| --- | --- | --- |
| Change a theme's source hex colors or displayed name | [`AppColorTheme.kt`](../app/src/main/java/com/miowser/app/ui/theme/AppColorTheme.kt) | Updates that theme's source colors and picker swatches. Preserve enum IDs so saved selections survive. |
| Choose which swatch controls each family | `paletteDefinitions` in [`PaletteColorSchemes.kt`](../app/src/main/java/com/miowser/app/ui/theme/PaletteColorSchemes.kt) | Changes primary, secondary, tertiary, or neutral mapping for that theme only. |
| Change surface tint strength, container tones, or contrast handling | `PaletteDefinition.scheme`, `tone`, `accent`, `inkOn` in `PaletteColorSchemes.kt` | Changes the derived roles across the 14 themes. Check both appearances. |
| Change AMOLED black/gray surfaces or existing fallback behavior | [`Theme.kt`](../app/src/main/java/com/miowser/app/ui/theme/Theme.kt) and [`Color.kt`](../app/src/main/java/com/miowser/app/ui/theme/Color.kt) | Changes AMOLED or the existing Miowser/fallback schemes. |
| Change which role a shared component consumes | The shared component file, such as `MiowserActionButton.kt` or `MiowserSnackbarHost.kt` | Updates that component's instances together. Use a Material role rather than a local hex value. |
| Change player or HTTPS semantic cues | [`ExpressiveTokens.kt`](../app/src/main/java/com/miowser/app/ui/theme/ExpressiveTokens.kt) | Separate fixed player/status colors; not the named theme palettes. |
| Intentionally change delete/error colors | `ErrorLight`/`ErrorDark` and their foreground/container companions in `PaletteColorSchemes.kt` | Affects every custom theme's semantic error/delete family. **Keep this outside ordinary palette edits.** |

Example: for a bluer Sea action color, change Sea's primary swatch in
`AppColorTheme.kt`. To make a different existing Sea swatch the primary, change
the first index in `SEA.palette(1, 2, 3, 0)`. Do not edit individual Save buttons,
progress indicators, or icon tints to achieve a theme-wide change.

After an intentional palette edit, update this reference and the source-color
expectations in `AppColorThemeTest`/`PaletteColorSchemesTest`, then run
`:app:testDebugUnitTest` and `:app:assembleDebug`. Preserve foreground contrast
of at least 4.5:1 and interactive outline contrast of at least 3:1. Device visual
testing should cover light, dark, and AMOLED, including destructive actions.
