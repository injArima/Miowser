package com.miowser.app.browser.media

/** Optional YouTube Music current-player fallback. No queue/recommendation or document-wide image search. */
internal const val SITE_MEDIA_PRESENTATION_SCRIPT = """
var musicPresentationBar = null;
function currentMusicBar() {
    if (location.protocol !== 'https:' || location.hostname !== 'music.youtube.com') return null;
    try {
        if (!musicPresentationBar || !musicPresentationBar.isConnected)
            musicPresentationBar = document.querySelector('ytmusic-player-bar');
        return musicPresentationBar;
    } catch (ignored) { return null; }
}
function siteMediaMetadata() {
    var bar = currentMusicBar();
    function text(selector) {
        try {
            var node = bar && bar.querySelector(selector);
            return node ? String(node.textContent || '').trim().slice(0, 512) : '';
        } catch (ignored) { return ''; }
    }
    return {title: text('.title, #song-title'), artist: text('.byline, .subtitle'), album: ''};
}
function siteMediaButton(action) {
    var selector = action === 'nexttrack' ? '#next-button' : action === 'previoustrack' ? '#previous-button' : null;
    if (!selector) return null;
    try {
        var bar = currentMusicBar(), button = bar && bar.querySelector(selector);
        return button && button.isConnected && !button.disabled && !button.hidden &&
            !button.hasAttribute('disabled') && button.getAttribute('aria-disabled') !== 'true' &&
            typeof button.click === 'function' ? button : null;
    } catch (ignored) { return null; }
}
"""
