package com.miowser.app.browser.media

import java.net.URI
import java.util.Locale

/** Keep this allowlist deliberately narrow; it is not a general page lifecycle policy. */
internal object SiteMediaCompatibility {
    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com",
        "music.youtube.com", "open.spotify.com")

    fun supports(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase(Locale.ROOT) in hosts && uri.rawUserInfo == null
    }.getOrDefault(false)
}

/** Inserted inside the media bridge closure, with no public page API or listener suppression. */
internal const val SITE_MEDIA_COMPATIBILITY_SCRIPT = """
function createSiteMediaCompatibility() {
    var hosts = ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'open.spotify.com'];
    var supported = location.protocol === 'https:' && hosts.indexOf(location.hostname.toLowerCase()) >= 0;
    var enabled = false, restores = [];
    function notify() {
        try { document.dispatchEvent(new Event('visibilitychange')); } catch (ignored) {}
    }
    function setAllowed(allowed) {
        allowed = supported && allowed === true;
        if (enabled === allowed) return;
        var wasHidden = document.hidden, wasVisible = document.visibilityState;
        enabled = allowed;
        if (allowed) {
            ['hidden', 'visibilityState', 'webkitHidden', 'webkitVisibilityState'].forEach(function (key) {
                var original = Object.getOwnPropertyDescriptor(document, key);
                if (original && !original.configurable) return;
                var getter = function () { return key === 'hidden' || key === 'webkitHidden' ? false : 'visible'; };
                try {
                    Object.defineProperty(document, key, {configurable: true, enumerable: original ? original.enumerable : true, get: getter});
                    restores.push(function () {
                        var current = Object.getOwnPropertyDescriptor(document, key);
                        if (!current || current.get !== getter) return;
                        if (original) Object.defineProperty(document, key, original);
                        else delete document[key];
                    });
                } catch (ignored) {}
            });
        } else {
            restores.forEach(function (restore) { try { restore(); } catch (ignored) {} });
            restores = [];
        }
        if (document.hidden !== wasHidden || document.visibilityState !== wasVisible) notify();
    }
    return {setAllowed: setAllowed};
}
"""
