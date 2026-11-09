# Miowser 0.1.55 (code 56)

- **Device Videos** is the first tab in the Hub. Browse, search, and sort videos from Android’s media library, linked folders, and completed Miowser downloads. Tap a video to open it in Miowser’s player.
- The device catalog opens from its local database and checks for changes in the background. You can grant access to all videos, select videos on supported Android versions, or link a folder. Unlinking a folder leaves its files on the device.
- **Miowser Hub** is the second tab. Your saved Library, folders, downloads, and existing Library playlists are available there.

## Verify P3

1. Open Device Videos before granting media access. Check completed downloads, then try granting all-video access and selected-video access separately.
2. Link a folder with videos in nested folders. Search for one by file name or folder, switch between grid and list, and play it. If the folder also appears in Android’s video library, check that Android-recognized copies appear only once in All sources.
3. Add, rename, move, and remove a file. Check that changes show up after returning to the app or tapping Refresh. Revoke access or unmount removable storage, then reconnect it and verify the videos reappear.
4. Switch to Miowser Hub and check the saved Library, Downloads, and browser entry points.

MediaStore changes trigger a rescan of the affected volume. Device Videos opens one video at a time and uses a bounded in-memory thumbnail cache. The saved Library keeps its full playlists.
