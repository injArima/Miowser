# Miowser 0.1.56 (code 57)

- Device Videos and Downloads share a preview cache. Reopening the Hub can load a saved thumbnail without seeking into the video again. Files with a new revision get a new preview.
- Visible cards take priority over look-ahead. Queued work is dropped when you scroll away or switch views. Thumbnail generation extracts one video frame at a time.
- The preview cache is limited to 64 MiB on disk and up to 24 MiB in memory, with a smaller memory allowance on lower-heap devices. Unreadable media backs off before retrying. Saved Library artwork, including posters, stays on its existing backup path.

## Verify P4

1. Open Device Videos and Downloads for the same completed video. Wait for its preview, restart Miowser, and confirm the preview returns.
2. Fling a long grid or list, switch folders or tabs while it is moving, and stop. Confirm that visible cards fill in and match their videos.
3. Replace a video with a different file and refresh Device Videos. Confirm the preview updates. Open a large or 4K file while the library is loading thumbnails.
4. Verify existing saved Library artwork, local midpoint thumbnails, and stream posters still appear.

The shared cache uses one 640 × 360 preview size. Older Downloads previews can be regenerated on first use.
