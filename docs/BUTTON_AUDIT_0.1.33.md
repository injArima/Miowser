# Button audit through 0.1.33

Material 3's button hierarchy is applied according to each action's role. A button already using an appropriate Material 3 component was left in place. Player overlay controls keep their separate video palette and gestures.

| Page or surface | Buttons changed in 0.1.32 | Buttons changed in 0.1.33 |
| --- | --- | --- |
| Browser home | Save speed dial and folder use filled buttons; dialog Cancel uses the shared caution style. | Cancel uses the lower-emphasis outlined caution style. |
| Browser settings | Save custom search and Save home page use filled buttons. | — |
| Browser bottom sheet, Quick access | Shortcut tiles use filled tonal icon buttons; edit mode keeps its drag gesture. | — |
| Browser content | — | Jump to top uses a tonal icon button in place of a small FAB. |
| Search fields used in History, Library, and other pages | — | Submit uses a tonal icon button; Clear stays a quiet icon button. |
| All Tabs | Browsing and private tab selectors use filled tonal icon toggle buttons. | — |
| Zoom and Text size sheets | Step controls use outlined icon buttons; Done uses a filled button. | — |
| Ad blocker | Refresh all uses a tonal button; add and rename dialogs use filled confirmation buttons. | Per-list Refresh uses a tonal icon button; dialog Cancel uses outlined caution. |
| Library folders | Save, Create, and Rename confirmations use filled buttons; import location alternatives use tonal and outlined buttons. | The folder Add icon uses a tonal icon button; dialog Cancel uses outlined caution. |
| Library health | Remove uses a caution button. | Repeated Remove buttons use outlined caution. |
| Site privacy dashboard | Revert uses a caution button. | Revert uses outlined caution. |
| Blocked page elements | Revert uses a caution button. | Repeated Revert actions use outlined caution. |
| Page element picker | Cancel uses a caution button. | Cancel uses outlined caution. |
| Clear browsing data | Clear selected data uses a caution tonal button. | — |
| Backup & restore | The final Close Miowser action uses a filled button. | — |
| Media action dialog | Save uses filled, Play on website uses outlined, and Cancel uses caution. | Cancel uses outlined caution. |

## Other Material 3 button categories

| Guide subpage | Decision in Miowser |
| --- | --- |
| Button groups | The Downloads selector remains a Material 3 segmented control for now. Material 3 Expressive recommends its connected button group, which is unavailable in the project's Material 3 1.4.0 dependency. |
| Extended FABs | No label-dependent primary floating action needs one. The New tab and Browse icons have clear labels for accessibility. |
| FAB menu | Existing New tab and Browse FABs each perform one action. The Library folder menu is in a header, so it stays anchored to its header button. |
| FABs | New tab and Browse remain primary floating actions. Jump to top was changed to a secondary tonal icon button. |
| Icon buttons | Toolbar actions remain quiet; Quick access, folder Add, search Submit, per-list Refresh, and tab selection have tonal emphasis. |
| Segmented buttons | The Downloads two-way selector remains the supported Material 3 implementation in this dependency version. |
| Split button | No current button has a safe default primary action plus a menu of related alternatives. The Library plus menu asks users to choose where to create or import, so one option should not run silently. |

Sources: [All buttons](https://m3.material.io/components/all-buttons), [Button groups](https://m3.material.io/components/button-groups/guidelines), [Buttons](https://m3.material.io/components/buttons/guidelines), [Icon buttons](https://m3.material.io/components/icon-buttons/guidelines), [FAB menu](https://m3.material.io/components/fab-menu/guidelines), [FABs](https://m3.material.io/components/fab/guidelines), [Extended FABs](https://m3.material.io/components/extended-fab/guidelines), [Segmented buttons](https://m3.material.io/components/segmented-buttons/overview), [Split buttons](https://m3.material.io/components/split-button/guidelines).
