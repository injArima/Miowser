package com.miowser.app.browser.media

/** Page-frame transport eligibility. The native coordinator separately grants the top site's preference. */
internal object SiteMediaCompatibility {
    fun supports(url: String): Boolean = SiteBackgroundPlaybackPolicy.host(url) != null
}

/** Inserted inside the media bridge closure, with no public page API or listener suppression. */
internal const val SITE_MEDIA_COMPATIBILITY_SCRIPT = """
function createSiteMediaCompatibility() {
    var supported = location.protocol === 'https:' || location.protocol === 'http:';
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
