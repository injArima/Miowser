package com.miowser.app.browser.media

/** Pure selection inside the page-media closure; no image requests or document-wide scans. */
internal const val MEDIA_ARTWORK_SCRIPT = """
var musicArtworkBar = null;
function selectMediaArtwork(meta, media) {
    var best = null, bestScore = -Infinity;
    var images = meta && meta.artwork;
    if (images) for (var i = 0; i < Math.min(images.length, 8); i++) {
        var image = images[i];
        if (!image) continue;
        var url = httpUrl(image.src);
        if (!url) continue;
        var score = 30, sizeScore = -Infinity, sizes = String(image.sizes || '').slice(0, 256).split(/\s+/);
        for (var j = 0; j < sizes.length; j++) {
            var size = /^(\d{1,5})x(\d{1,5})$/i.exec(sizes[j]);
            if (!size) continue;
            var width = Number(size[1]), height = Number(size[2]), edge = Math.min(width, height);
            if (edge <= 0 || width > 16384 || height > 16384) continue;
            var candidate = (edge < 114 ? 10 : 100) - Math.abs(Math.log(edge / 512) / Math.LN2) * 10
                - Math.abs(Math.log(width / height)) * 15;
            sizeScore = Math.max(sizeScore, candidate);
        }
        if (sizeScore !== -Infinity) score = sizeScore;
        if (score > bestScore) { best = url; bestScore = score; }
    }
    // A narrowly scoped current-player fallback, never a recommendation/queue image.
    if (!best && location.hostname === 'music.youtube.com') {
        try {
            if (!musicArtworkBar || !musicArtworkBar.isConnected) musicArtworkBar = document.querySelector('ytmusic-player-bar');
            var cover = musicArtworkBar && musicArtworkBar.querySelector('#thumbnail img, ytmusic-thumbnail-renderer img');
            if (cover) best = httpUrl(cover.getAttribute('src')) || httpUrl(cover.currentSrc);
        } catch (ignored) {}
    }
    // The selected media's own poster is relevant; favicons and recommendation images are not.
    return best || httpUrl(media && media.poster);
}
"""
