# Step 11C — Inactive tabs test checklist

## Duplicate tabs

1. Open the same ordinary webpage in two or more normal tabs.
2. Keep one copy selected, then open the Tabs page.
3. Confirm the selected copy remains in Browsing and the older copies move into an **Inactive tabs** card.
4. Confirm private tabs and Miowser home tabs never move into Inactive.

## Inactive list and restoration

1. Open the Inactive tabs card and confirm each record shows its title, URL, reason, and archive date.
2. Tap a record and confirm it reloads as a normal live tab and disappears from Inactive.
3. Return to Inactive, delete one record with its trash button, and confirm only that record is removed.
4. From the Inactive list, use the Android back gesture and confirm it returns to the main Tabs page first.

## Persistence and memory behavior

1. Leave one record in Inactive, close Miowser, and relaunch it.
2. Confirm the record remains available.
3. Confirm restored normal tabs retain their titles/previews and private tabs are still cleared on app close.
4. Scroll through Tabs and confirm the existing 30-preview cache behavior still works.

## Cleanup setting

1. Open Browser Settings and confirm **Delete inactive tabs after 30 days** is enabled by default.
2. Disable it, leave and reopen Settings, and confirm the choice persists.
3. Re-enable it and confirm ordinary tab use and restoration still work.

The 14-day and 30-day boundaries are covered by unit tests because they are not practical to reproduce manually. Cleanup is evaluated at startup, when Tabs opens, and when its setting is re-enabled.
