# Miowser 0.2.04 (code 99)

- Replaced the padded adaptive-launcher layer used in both player notifications with a dedicated, full-size monochrome notification glyph.
- Enlarged the launcher/app-info mark using a shared proportional inset, including the themed monochrome layer. Existing logo paths and colors are retained.
- Improved browser notification artwork selection using site Media Session images, preferring suitable resolutions over the first small thumbnail. Added selected-video poster and scoped YouTube Music current-player cover fallbacks.
- Artwork updates when image sources/posters change, retries a failed load once, and uses bounded 512-pixel decoding and a 4 MiB memory cache.
- Published artwork in both Android album-art/media-art metadata and the notification large icon. Private-tab artwork remains excluded, and canceled or old-owner loads cannot replace the current cover.

Compared the supplied Brave APK's artwork pipeline. See `NOTIFICATION_ARTWORK_AND_ICONS_0.2.04.md` for evidence, resource references, and device checks. Device testing is left to you.
