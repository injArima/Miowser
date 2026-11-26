package com.miowser.app.browser.media

/** WebView disables the Media Session API. Supply its playback subset before sites feature-detect it. */
internal const val MEDIA_SESSION_FALLBACK_SCRIPT = """
function installMediaSessionFallback(changed) {
    try { if (navigator.mediaSession) return null; } catch (ignored) { return null; }
    try {
        if (typeof window.MediaMetadata !== 'function') {
            var values = new WeakMap();
            function artwork(input) {
                if (input === undefined) input = [];
                return Object.freeze(Array.from(input, function (image) {
                    if (!image || image.src === undefined) throw new TypeError('MediaImage.src is required');
                    return Object.freeze({src: new URL(String(image.src), location.href).href,
                        sizes: image.sizes === undefined ? '' : String(image.sizes),
                        type: image.type === undefined ? '' : String(image.type)});
                }));
            }
            function MediaMetadata(init) {
                if (!(this instanceof MediaMetadata)) throw new TypeError('Use new MediaMetadata');
                init = init || {};
                values.set(this, {title: init.title === undefined ? '' : String(init.title),
                    artist: init.artist === undefined ? '' : String(init.artist),
                    album: init.album === undefined ? '' : String(init.album), artwork: artwork(init.artwork)});
            }
            ['title', 'artist', 'album', 'artwork'].forEach(function (key) {
                Object.defineProperty(MediaMetadata.prototype, key, {configurable: true, enumerable: true,
                    get: function () {
                        var value = values.get(this); if (!value) throw new TypeError('Invalid MediaMetadata receiver');
                        return value[key];
                    },
                    set: function (next) {
                        var value = values.get(this); if (!value) throw new TypeError('Invalid MediaMetadata receiver');
                        value[key] = key === 'artwork' ? artwork(next) : String(next); changed();
                    }});
            });
            Object.defineProperty(window, 'MediaMetadata', {configurable: true, writable: true, value: MediaMetadata});
        }
        var callbacks = Object.create(null), position = null, metadata = null, playbackState = 'none';
        var supported = ['play', 'pause', 'stop', 'seekto', 'seekbackward', 'seekforward', 'previoustrack', 'nexttrack'];
        var shim = {};
        Object.defineProperties(shim, {
            metadata: {configurable: true, enumerable: true, get: function () { return metadata; },
                set: function (value) {
                    if (value === undefined) value = null;
                    if (value !== null && typeof value !== 'object') throw new TypeError('Invalid media metadata');
                    metadata = value; changed();
                }},
            playbackState: {configurable: true, enumerable: true, get: function () { return playbackState; },
                set: function (value) {
                    value = String(value);
                    if (['none', 'paused', 'playing'].indexOf(value) < 0) throw new TypeError('Invalid playbackState');
                    playbackState = value; changed();
                }}
        });
        shim.setActionHandler = function (action, callback) {
            if (this !== shim) throw new TypeError('Invalid MediaSession receiver');
            if (supported.indexOf(action) < 0) {
                var error = new Error('Unsupported media action'); error.name = 'NotSupportedError'; throw error;
            }
            if (callback !== null && typeof callback !== 'function') throw new TypeError('Invalid action handler');
            if (callback === null) delete callbacks[action]; else callbacks[action] = callback;
            changed();
        };
        shim.setPositionState = function (state) {
            if (this !== shim) throw new TypeError('Invalid MediaSession receiver');
            if (state == null || Object.keys(state).length === 0) { position = null; changed(); return; }
            var duration = Number(state.duration), current = state.position === undefined ? 0 : Number(state.position),
                rate = state.playbackRate === undefined ? 1 : Number(state.playbackRate);
            if (Number.isNaN(duration) || duration < 0 || !Number.isFinite(current) || current < 0 || current > duration ||
                !Number.isFinite(rate) || rate === 0) throw new TypeError('Invalid position state');
            position = {duration: duration, position: current, playbackRate: rate}; changed();
        };
        Object.defineProperty(navigator, 'mediaSession', {configurable: true, enumerable: true, get: function () { return shim; }});
        return {session: shim, handlers: callbacks, position: function () { return position; }};
    } catch (ignored) { return null; } // A locked/partial provider must not break the website.
}
"""
