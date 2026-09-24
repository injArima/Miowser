# Step 10: browser manual test checklist

Use ordinary test pages and accounts. No emulator/device run was performed by the agent for this step.

## Privacy and data clearing

- Open Settings → Clear browsing data. Confirm History, cookies/site storage, cache, form suggestions,
  and site decisions start checked; Download records and Library records start unchecked.
- Clear History while several regular tabs are already open. Return to an old tab from Tabs without
  reloading it; its page should reappear in History. Reopening the same tab again should not add a
  second entry. Private tabs and failed/in-flight pages should not be recorded.
- Visit a page, save one download and play one video. Clear only History. Verify History disappears,
  while the download record, its file, the Library item, and site login remain.
- Clear only cookies/site storage. Verify a login is removed after reloading the site while History,
  downloads, and Library remain. Clear only cache and check that site storage remains.
- Explicitly select Download records. Verify records disappear but completed public files remain.
  Explicitly select Library records and verify Library is empty.
- Cancel a clear-data confirmation; verify nothing changes. Try the Android back gesture from Clear
  browsing data and Site permissions; each should return to Settings.

## Private tabs

- From Tabs, choose New private tab and read the shared-storage/download warning. Cancel it once.
- Start a private tab, visit a few pages, play a detected video, and download a file. Check that
  History and Library do not gain those pages/videos; the download is still saved normally.
- Open a link in a private child tab and a background tab. Confirm both remain marked Private.
- Close every private tab. Check that its cookies/site data are gone and ordinary tabs' site logins
  were also cleared as warned. Force-stop during a private session, reopen, and confirm private tabs
  are not restored and shared site data is purged before regular tabs load.

## Permissions, uploads, and links

- On an HTTPS camera/microphone test page, verify the exact site origin appears before Android's
  runtime request. Test Block, Allow once, and Always allow; then remove the remembered choice in
  Settings → Site permissions and verify the prompt returns.
- On an HTTPS location test page, repeat the same three choices using approximate location.
  Denying the Android runtime permission must prevent site access even after tapping Allow.
- Use an HTML file input for one file and multiple files; test selecting and canceling. Confirm a
  malicious/unsupported picker result does not expose app-private `file:` or app-owned content URIs.
- Tap `mailto:`, `tel:`, and an app-specific link with and without a handler. Confirm Miowser asks
  before opening another app and explains when no handler exists. `javascript:`, `file:`, `content:`,
  and `data:` top-level links must not be launched.

## Errors, preferences, and interface

- Open an invalid-certificate HTTPS site. Confirm it is blocked, with no Continue-through option.
  Open an unreachable HTTPS site and use Try again after reconnecting.
- Toggle JavaScript, cookies, and popups in Settings. Check the chosen behavior on existing tabs
  and newly opened tabs, then restore preferred settings.
- Increase system font size, enable TalkBack, rotate the device, and test Settings, Clear browsing
  data, Site permissions, Tabs, private indicators, permission dialogs, and the file picker.
- Download a file with a server-supplied odd filename. Confirm it cannot create a path outside
  Miowser's Downloads folder or inject a malformed MIME type; ordinary names still save correctly.

## Known platform limits to watch

- This private mode deliberately shares the WebView profile with regular tabs. It clears shared
  site data at entry/exit; it does not provide simultaneous cookie isolation.
- WebView has no equivalent host callback for every website notification or clipboard read. The
  app does not silently grant unknown protected-media resources.
- The basic WebView picker handles content-provider file selection; advanced capture, folder, and
  save-file flows depend on picker support and are not promised by this implementation.
- Clearing WebView form suggestions does not erase entries held separately by Android's Autofill
  service; those are managed in Android settings.
