# Miowser design references

## Hub bottom navigation — user video, 2026-09-24

The reference video shows a compact shared rounded rail. Inactive categories show icons;
the selected category expands into a darker pill containing its icon and label. The
selection moves and resizes smoothly. The user's later clarification in the separate
"Library Folder Switcher" discussion established that this reference is for the
Hub's **Library / Downloads / More bottom navigation**, not the Library folder row.

For Miowser, make the three Hub destinations one compact connected rounded control.
The selected destination expands to icon plus label while the other two become
icon-only; animate the background and widths quickly without delaying navigation.
Preserve the existing navigation actions, Library long-press settings, Downloads
badge, system navigation-bar clearance, minimum touch targets, and full TalkBack
labels. Use Material You colors. On wide screens, all labels may remain visible;
with large font sizes, favor readability over exact motion.

The Library folder row remains a separate, scalable control. Preserve **All** first,
**Unfiled** as the default home view, custom folders after it, dividers, and the
fixed create-folder action on the same row. Many folders or long names must remain
scrollable and accessible. Do not apply the expanding-pill pattern to this row.

## Sameera Wijerathna's android-common repository

Source: https://github.com/sameerasw/android-common

Useful patterns observed in its guides:

- Segmented picker: connected Material 3 buttons, consistent shapes, small haptic tick.
- Floating toolbar: selected label expands; compact width and large font scale hide
  other labels; navigation insets are accounted for.
- Rounded card container and list items: reusable grouped settings rows, clear
  supporting text, disabled-state explanation, restrained icon accents.
- Bottom sheets: consistent container color, horizontal padding, navigation-bar
  clearance, scrollable content, preserved input state.
- Theme: dynamic color and shared shape/typography tokens, with contrast checks.
- Optional motion: pager synchronization, blur, and shader effects. Evaluate visual
  performance and accessibility before using these in Miowser; avoid WebView and the
  deliberately separate player surface.

Proposed adoption order: Hub bottom navigation; Downloads sub-tabs; shared
sheets/settings rows; subtle press/haptic/transition polish; optional
visual effects only after performance review. Keep the rich player visually separate.
