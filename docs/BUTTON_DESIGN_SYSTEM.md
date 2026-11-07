# Miowser button roles

Miowser uses Material 3 Compose buttons. The app's own action family is `MiowserActionButton`, with dimensions in `ActionButtonTokens` and colors from `MaterialTheme.colorScheme`. Keep these choices consistent as buttons are added.

| Role | Material 3 component | Use in Miowser |
| --- | --- | --- |
| Primary | Filled `Button` | Commit a user choice: Save, Add, Done, Back up now. |
| Significant secondary | `FilledTonalButton` | Refresh all lists or another prominent action alongside a primary action. |
| Alternative | `OutlinedButton` | Choose another route, import, select a folder, or return to a source page. |
| Quiet | `TextButton` | Dismiss a non-destructive notice, reset a value, open help, or navigate from secondary text. |
| Caution | `FilledTonalButton` with `errorContainer`/`onErrorContainer` | Destructive actions and Cancel where the app calls for a soft red tint. Confirmation choices use outlined buttons until a two-step action is revealed. |
| Icon action | `IconButton` | Browser and hub toolbars, search, menus, and other compact actions. |
| Selected icon state | `FilledTonalIconToggleButton` | Browsing/private tab selection. |
| Compact outlined control | `OutlinedIconButton` | Zoom and text-size step controls. |
| Frequent shortcut | `FilledTonalIconButton` | Browser quick access; reorder gestures retain their separate edit state. |
| Main floating creation | `FloatingActionButton` | New tab and primary hub action. |
| Option selection | `SegmentedButton` for a compact related choice; `FilterChip` for wrapping or longer sets | Hub mode, settings presets, and filters. |

The rich and WebView players use Material 3 surfaces for their video-overlay controls because those controls must remain legible over moving video and preserve long-press gestures. The player overlay palette stays separate from the browser and hub theme. Navigation controls with long-press actions also retain their gesture handling.

Sources: [Material 3 buttons](https://m3.material.io/components/buttons), [Android Compose button guidance](https://developer.android.com/develop/ui/compose/components/button), [icon buttons](https://developer.android.com/develop/ui/compose/components/icon-button), [segmented buttons](https://developer.android.com/develop/ui/compose/components/segmented-button).
