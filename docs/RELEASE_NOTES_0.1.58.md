# Miowser 0.1.58 (code 59)

- **Device Videos** can browse videos together or by folder. Folder cards show the video count and artwork from up to four videos. Miowser remembers the grouping, layout, and sort choices across app launches.
- Opening a video from Device Videos gives the player a playlist for the current folder or filtered result. The playlist loads as you scroll, and Previous, Next, and autoplay cross page boundaries.
- Local artwork checks memory and the private JPEG cache first. When a preview is missing, Miowser asks Android for a thumbnail and falls back to extracting a scaled frame. List and folder previews use smaller images than grid previews. JPEG encoding runs on a separate bounded worker.
- Existing generated previews move to private app files on first use. Miowser keeps up to 64 MiB or 2,048 previews and evicts the least recently used first. Generated previews stay separate from backup-managed Library artwork and subtitles.
- Media change notifications are coalesced during scans. The catalog update adds a folder index and preserves existing rows and linked sources.

## Device checklist

1. Open **Device Videos → Folders**. Browse folders with the same name on different sources, check the counts, and return to the folder list. Switch to List, restart Miowser, and check that the layout choice persists.
2. In a folder with more than 150 videos, open one around a page boundary. Scroll the player playlist past the loaded rows, select a video farther down, and try Next, Previous, and autoplay. Repeat with a filtered search.
3. Open a grid, switch to List, and reopen Miowser. Check that previews return. Replace or rename a file and refresh. Try a large or 4K video, a portrait video, and one Android cannot thumbnail.
4. Scroll quickly, switch away from the Hub, and bring Miowser back. Check that the visible cards fill in with the correct previews and that playback stays responsive.
5. Revoke a linked folder grant or remove a storage volume, then reconnect it. Confirm saved Library entries, watch progress, and Downloads are still accessible through Miowser Hub.
