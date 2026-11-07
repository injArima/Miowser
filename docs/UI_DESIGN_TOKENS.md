# Miowser UI design tokens

The Compose UI uses these shared values. Changes to a token update its consumers together. The component values below apply to the browser, hub, settings, sheets, and other non-player UI. The migration preserves existing measurements and colors.

| Category | Tokens and values | Where applied |
| --- | --- | --- |
| Spacing (`UiSpacing`) | `xxs` 2, `xs` 4, `s` 8, `compact` 10, `m` 12, `comfortable` 14, `l` 16, `roomy` 18, `xl` 20, `xxl` 24, `xxxl` 32 dp | Repeated padding, item gaps, and spacers in browser chrome, menus and sheets, hub, settings, history, onboarding, and player controls. |
| Corners (`UiCorners`) | `r5` 5, `r8` 8, `r10` 10, `r12` 12, `r14` 14, `r16` 16, `r18` 18, `r20` 20, `r24` 24, `r32` 32 dp | Repeated card, tile, menu, sheet, tab preview, and player control shapes. Material's five shape roles use `r8`, `r12`, `r18`, `r24`, `r32`. |
| Icons (`UiIconSize`) | `small` 18, `menu` 20, `standard` 24, `featured` 28 dp | Common navigation, action, menu, status, history, and player glyphs. |
| Controls (`UiControlSize`) | `compact` 40, `action` 44, `touchTarget` 48, `prominent` 64 dp | Repeated search/action controls, quick access tiles, menu rows, and prominent player controls. |
| Elevation (`UiElevation`) | `low` 2, `raised` 4, `medium` 6, `high` 8, `floating` 12 dp | Repeated tonal and shadow elevation for floating surfaces and sheets. |
| Secure status (`UiSemanticColors`) | `secureOnLight` `#137333`, `secureOnDark` `#81C995` | HTTPS state in the address bar. Error and other status colors continue to use theme color roles. |
| Player colors (`PlayerUiColors`) | `backdrop` black, `content` white, `panel` `#151313`, `sidePanel` `#17191D`, `disabledControl` `#9C9C9C`, `seekBackward` `#FFAAA5`, `seekForward` `#A8E6B0`, `caution` `#FFD875`, `guidanceSurface` `#2D291E`, `subtitlePreview` `#8C8C8C` | Rich and WebView player chrome, seek feedback, guidance, and subtitle preview. Fixed colors keep video overlays legible independently of wallpaper accents. |

## Non-player component tokens

These named contracts are in `ComponentTokens.kt`. When changing a component's dimensions or emphasis, edit its token and keep its UI call sites pointed at that token.

| Component | Tokenized values | UI |
| --- | --- | --- |
| Address bar | Field height 48 dp; leading/trailing accent 48 × 44 dp; submit button 40 dp | Address field and its icon gradients |
| Browser home | Hero top space 76 dp, bottom space 22 dp; speed dial tile 46 dp; responsive grid minimum cell 52 dp, horizontal/vertical inset 12/16 dp, column gap 6 dp | Homepage search area and speed dials |
| Find in page | Horizontal/vertical inset 6/4 dp; field height 48 dp; clear button maximum width 40 dp; match counter minimum width 32 dp | Browser find bar |
| Bottom navigation | Responsive width threshold 336 dp; side padding 12/4 dp; vertical padding 8 dp; button target 48 dp | Browser bottom bar |
| Browser menu | Quick access tile 48 dp, gap 10 dp, inner tile 40 dp, inner icon 24 dp, tile corners 16/14 dp; section corner 24 dp and inset 8 dp; action icon container 44 dp and icon 22 dp | Collapsed and expanded browser menu |
| Sheet handle | Top corner 28 dp; handle 32 × 4 dp; top/bottom clearance 7/18 dp | Shared bottom sheet rim and drag handle |
| Confirmation dialog | Horizontal/vertical inset 22/26 dp; action minimum height 52 dp and gap 10 dp; border and fill emphasis alpha for revealed, caution, and normal actions | Shared confirmation prompts |
| Snackbar | Horizontal inset 12 dp; action inset 8 dp; content gap 12 dp; mark 28 dp | Shared snackbar host |
| History row | Minimum height 80 dp; site icon 44 dp with 14 dp corners; segmented outer/inner corners 24/6 dp, row gap 2 dp; date header 48 dp | Browser history list |
| Tab history menu | Width 260–320 dp; maximum height 376 dp | Long press navigation popup |
| Hub library | Grid minimum cell 168 dp, inset 12 dp, bottom clearance 80 dp; folder rail height 56 dp | Library and download grids, folder rail |
| Hub video card | List artwork 136 × 84 dp; center artwork 76 dp; badge inset 6 × 3 dp; top corners 24 dp; artwork overlay alpha 0.18 | Library grid and list cards |
| Action buttons | Shape radius 24 dp; minimum target height 48 dp | Shared Material 3 primary, tonal, outlined, text, and caution actions |

Component tokens should reference the primitive spacing, size, and shape scales where the values match. Material theme roles remain the source for colors, type, and default component shapes. Player UI is outside the component-token scope of this pass.

## Existing token sources

- `Theme.kt` and `Color.kt`: Material 3 color roles for light, dark, dynamic color, and AMOLED surfaces. UI components should consume `MaterialTheme.colorScheme` rather than duplicate palette values.
- `Type.kt`: `ExpressiveTypography` for the browser and hub, with a separate player type hierarchy.
- `MotionPolicy.kt`: `MotionTokens` for app-owned transitions, easing, and pressed state; `MotionPolicy` honors reduced motion.
- `ExpressiveTokens.kt`: semantic aliases for page gutter, section gap, item gap, icon sizes, and minimum touch target.

Measurements that encode media dimensions, gesture thresholds, selection coordinates, or a single component's bespoke geometry remain local. Their meaning is tied to that feature rather than to the visual scale.
