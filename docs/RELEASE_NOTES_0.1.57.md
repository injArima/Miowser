# Miowser 0.1.57 (code 58)

- Open a video in Miowser from Android’s **Open with** menu or use **Open video file** in Device Videos. A single-file grant is enough to play it, and playback can start before catalog discovery. A file opened this way is played for the session.
- Turn **Device video discovery** off under **Miowser Hub → More** to pause catalog scans. Individual file opening, Downloads, and the saved Library stay available.
- Device Videos identifies offline storage, missing folder access, and scan failures. Refresh prioritizes the selected source. **Relink folder** lets you reconnect it or choose a replacement without changing saved Library entries.
- MediaStore-only browsing skips alias checks when the source already gives each video a unique identity. Mixed sources use the canonical URI index to deduplicate matching files.

## Verify P5

1. From Android Files, open a local video with Miowser while the app is closed. Repeat while the browser is already open.
2. Disable discovery in Hub settings. Verify **Open video file** still works and discovery stays off after restart. Re-enable it to resume scanning.
3. Link a folder, select it in the source menu, and refresh. Relink it to the same folder and then to a replacement. Confirm the saved Library and device files remain available.
4. Disconnect removable storage or revoke a folder grant, then check the source status, reconnect it, and retry.
