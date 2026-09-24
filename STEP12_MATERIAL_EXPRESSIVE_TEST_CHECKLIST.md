# Step 12: Material 3 Expressive visual checks

The app builds and its unit tests pass. Please check these on your device; these visual and touch
checks are intentionally left to you.

- [ ] On the Hub, check Library, Downloading, Downloaded, and More. Navigation remains compact,
  clearly marks the selected destination, and every button still works. Long-press Library for its
  settings sheet.
- [ ] Open a browser page and its native homepage. Check the address field, bookmark/star, bottom
  bar, speed-dial selection, browser menu and its tile reordering. No page viewport is covered.
- [ ] Open Tabs, History, Bookmarks, Detected media, Downloads, Ad blocker, and Settings. Check
  their app bars, cards, controls, dialogs, and empty states, plus back navigation.
- [ ] Trigger a snackbar with an action and dismiss one by swiping. The action still runs only when
  pressed, and the snackbar stays above the browser bottom bar.
- [ ] Repeat a few screens in light and dark mode, with Material You wallpaper colors. If possible,
  use an Android version below 12 to inspect the fallback palettes.
- [ ] Increase system font size and display size, then rotate the device. Hub navigation labels,
  settings switches, tab cards, and menu tiles remain readable and tappable without clipping.
- [ ] Open the rich player. Its controls, layout, colors, rotation, and gestures should look and
  behave as they did before this visual pass.

Implementation deliberately stays on stable Compose Material 3 1.4.0. The official 1.5 Expressive
components remain alpha; the shared app-owned wrappers make a later upgrade local and reviewable.
