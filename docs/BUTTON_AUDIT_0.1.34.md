# Material 3 Expressive button changes in 0.1.34

| Page or surface | Control | Change |
| --- | --- | --- |
| Browser menu | Quick access icons | Tonal icon buttons morph to a smaller corner radius while pressed. The edit drag state keeps its existing behavior. |
| Downloads | Downloading and Downloaded selector | Replaced segmented controls with a connected Expressive toggle button pair. Counts, haptics, and selection stay in place. |
| All Tabs | Browsing and Private selectors | Tonal icon toggle buttons morph between checked, unchecked, and pressed shapes. |
| History, Library, and other search fields | Submit icon | Tonal icon button morphs on press. Clear remains a quiet icon button. |
| Library | Add folder icon | Tonal icon button morphs on press. It still opens the same folder menu. |
| Browser content | Jump to top | Tonal icon button morphs on press. |
| Ad blocker | Individual list refresh icon | Tonal icon button morphs on press. The action still refreshes only that list. |
| Shared confirmation prompts | Action buttons | Outlined and tonal options morph on press, retaining their existing two-tap and caution colors. |
| Browser settings, backup and restore, browser home, media actions, Library health, site controls, and other callers of `MiowserActionButton` | Filled, tonal, outlined, text, and caution actions | Shared buttons morph from a rounded pill to the pressed shape. Their roles, labels, colors, and callbacks remain the same. |

The non-player theme supplies the Material 3 Expressive motion scheme. When Reduce motion is enabled, it uses the standard scheme. The player theme retains its separate palette and motion setup. The project retains the stable `compose-bom:2026.08.00` and selects Material 3 `1.5.0-beta02` directly, so it continues building with Android SDK 37.0.

Button families deliberately not introduced: split buttons need a safe default action plus a related menu; the Library Add menu asks the user to choose an action. FAB menus need a floating action that opens several related actions; New tab and Browse each perform one action. Settings choices that wrap onto multiple lines remain chips.

Sources: [Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3), [button groups](https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonGroupDefaults), [button shapes](https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonDefaults), [icon button shapes](https://developer.android.com/reference/kotlin/androidx/compose/material3/IconButtonDefaults).
