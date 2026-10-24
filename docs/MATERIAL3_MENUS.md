# Material 3 menu pattern in Miowser

Reference: [Material 3 menus](https://m3.material.io/components/menus/overview) and the [Android Compose menu guide](https://developer.android.com/develop/ui/compose/components/menu).

## Applied menus

- Browser homepage: speed dial actions, folder actions, and speed dial sort.
- Link long-press sheet: open, copy/share, and save actions, separated by purpose.
- Library: folder actions, folder creation, Library sort, and video card actions.
- Downloads: download actions.
- Player: aspect ratio and playback speed selectors.

The anchored menus use `MiowserDropdownMenu` for the rounded surface. They and the link-action sheet use `MiowserMenuItem` for bounded item feedback and `MiowserMenuDivider` between action groups. Leading icons identify actions where useful; the selected sort/selector item has a distinct container. The divider separates destructive actions such as Remove/Delete from ordinary actions. A menu with a single group does not need a divider after every item.

The shared menu item uses a compact 40 dp visual row with 20 dp leading icons, 18 dp trailing indicators, and 10 dp horizontal content padding. The speed dial's Move to folder action opens a drill-down submenu in the same popup. Bulk Move uses the same folder-choice items, so both flows keep the same labels and selected-folder treatment.

## Reuse rules

- Keep the menu anchored to the element that opens it. Dismiss it after choosing an action.
- Use short, verb-led labels and a leading icon when it clarifies the action. Put state or a shortcut at the trailing edge.
- Use a divider at a semantic boundary, especially before destructive actions; avoid decorative dividers between every choice.
- Keep touch feedback within the rounded item shape. Disabled items stay visible when they explain an unavailable option.
- Use the app color scheme for surfaces and selected states. Preserve the player's dark playback surface outside its popup menu.
- Navigation sheets and the browser's rearrangeable tile sheet remain sheets: their scrolling, drag order, and navigation hierarchy serve different purposes from a temporary anchored action menu.
