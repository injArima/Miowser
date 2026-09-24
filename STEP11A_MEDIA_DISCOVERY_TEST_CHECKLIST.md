# Step 11A — media discovery checks

1. Open a page with a video or manifest linked in raw HTML. Open **Detected media** and check that the link appears once, with its usual verified media details.
2. Open a page with an embedded player or a link to another video page. In **Detected media**, choose depth 1 and 8 pages, then tap **Scan site**. Check that progress updates and media from followed pages appears without duplicate entries.
3. Tap **Stop** during a longer scan. Check that progress stops and the page remains responsive.
4. Try a page containing links to a different site, a logout page, or many segment files. Check that the scan does not visit those pages or fill the media list with segments.
5. Change depth/page cap and run the scan again. Confirm results respect the chosen limits and no download begins until you explicitly tap Download.
6. Navigate to another page while a scan is active. Confirm old-page results do not appear on the new page.
7. Move **Minimum length** above a short detected clip. Confirm it disappears and the hidden count updates; move the slider back to Off and confirm it returns.
8. Confirm the chosen minimum survives an app restart. Test HLS/DASH, MP4, and WebM/MKV samples and confirm their duration is displayed when the source exposes usable metadata.

The HTML scan reads raw markup and does not execute scripts. JavaScript-only or blob/MSE sources remain outside this step. Site scans follow only embeds and media-like routes on the same exact origin, so a site using unrelated URL shapes may need a future site-specific extractor.
