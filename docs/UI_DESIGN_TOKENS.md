# Miowser UI design tokens

The Compose UI uses these shared values. Changes to a token update its consumers together. This catalog records the values used in version 0.1.30; the migration preserves the existing measurements and colors.

| Category | Tokens and values | Where applied |
| --- | --- | --- |
| Spacing (`UiSpacing`) | `xxs` 2, `xs` 4, `s` 8, `compact` 10, `m` 12, `comfortable` 14, `l` 16, `roomy` 18, `xl` 20, `xxl` 24, `xxxl` 32 dp | Repeated padding, item gaps, and spacers in browser chrome, menus and sheets, hub, settings, history, onboarding, and player controls. |
| Corners (`UiCorners`) | `r5` 5, `r8` 8, `r10` 10, `r12` 12, `r14` 14, `r16` 16, `r18` 18, `r20` 20, `r24` 24, `r32` 32 dp | Repeated card, tile, menu, sheet, tab preview, and player control shapes. Material's five shape roles use `r8`, `r12`, `r18`, `r24`, `r32`. |
| Icons (`UiIconSize`) | `small` 18, `menu` 20, `standard` 24, `featured` 28 dp | Common navigation, action, menu, status, history, and player glyphs. |
| Controls (`UiControlSize`) | `compact` 40, `action` 44, `touchTarget` 48, `prominent` 64 dp | Repeated search/action controls, quick access tiles, menu rows, and prominent player controls. |
| Elevation (`UiElevation`) | `low` 2, `raised` 4, `medium` 6, `high` 8, `floating` 12 dp | Repeated tonal and shadow elevation for floating surfaces and sheets. |
| Secure status (`UiSemanticColors`) | `secureOnLight` `#137333`, `secureOnDark` `#81C995` | HTTPS state in the address bar. Error and other status colors continue to use theme color roles. |
| Player colors (`PlayerUiColors`) | `backdrop` black, `content` white, `panel` `#151313`, `sidePanel` `#17191D`, `disabledControl` `#9C9C9C`, `seekBackward` `#FFAAA5`, `seekForward` `#A8E6B0`, `caution` `#FFD875`, `guidanceSurface` `#2D291E`, `subtitlePreview` `#8C8C8C` | Rich and WebView player chrome, seek feedback, guidance, and subtitle preview. Fixed colors keep video overlays legible independently of wallpaper accents. |

## Existing token sources

- `Theme.kt` and `Color.kt`: Material 3 color roles for light, dark, dynamic color, and AMOLED surfaces. UI components should consume `MaterialTheme.colorScheme` rather than duplicate palette values.
- `Type.kt`: `ExpressiveTypography` for the browser and hub, with a separate player type hierarchy.
- `MotionPolicy.kt`: `MotionTokens` for app-owned transitions, easing, and pressed state; `MotionPolicy` honors reduced motion.
- `ExpressiveTokens.kt`: semantic aliases for page gutter, section gap, item gap, icon sizes, and minimum touch target.

Measurements that encode media dimensions, gesture thresholds, selection coordinates, or a single component's bespoke geometry remain local. Their meaning is tied to that feature rather than to the visual scale.
