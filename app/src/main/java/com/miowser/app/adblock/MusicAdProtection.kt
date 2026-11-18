package com.miowser.app.adblock

import java.security.MessageDigest

/**
 * Trusted, bundled Music resources, independent of downloaded filter lists. This small schema is
 * not a uBO/Brave scriptlet interpreter. Only the named player fields and envelopes below exist.
 * Bump the resource version when changing behavior; the startup fingerprint includes all code.
 * Original implementation informed by the authorized 2026-11-18 runtime-script-4.js findings.
 */
internal object MusicAdProtection {
    val documentStartScript: String by lazy {
        MUSIC_RUNTIME.replace("/* MUSIC_RESOURCE */", MUSIC_RESOURCE)
    }

    fun resourceDigest(): String = MessageDigest.getInstance("SHA-256")
        .digest(documentStartScript.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

// Curated schema: exact origin, exact endpoint paths, own-data-property paths (no expressions),
// property overrides, and hard budgets. No remote resources, executable filter text or eval.
private const val MUSIC_RESOURCE = """
{
  "schema": "miowser.music-player-data/1",
  "version": "1.0.0",
  "origin": "https://music.youtube.com",
  "endpoints": {"/youtubei/v1/player": "player", "/youtubei/v1/next": "next"},
  "adFields": ["playerAds", "adPlacements", "adSlots"],
  "overrides": {"ytInitialPlayerResponse": ["playerAds", "adPlacements", "adSlots"],
                "playerResponse": ["adPlacements"]},
  "envelopes": [["playerResponse"], ["ytInitialPlayerResponse"],
                ["response", "playerResponse"], ["data", "playerResponse"],
                ["responses", "*", "playerResponse"]],
  "maxTextBytes": 1048576,
  "maxPathVisits": 256,
  "maxArrayEntries": 64
}
"""

private const val MUSIC_RUNTIME = """
(function() {
    'use strict';
    try {
        var resource = /* MUSIC_RESOURCE */;
        var stateKey = '__miowserMusicAdProtectionV1';
        var nativeApply = Reflect.apply;
        var own = Object.getOwnPropertyDescriptor;
        var define = Object.defineProperty;
        var NativeJSON = JSON;
        var nativeParse = NativeJSON.parse;
        var nativeStringify = NativeJSON.stringify;
        var nativeThen = Promise.prototype.then;
        var NativeURL = URL;
        var parsing = 0; // Includes our internal parses AND native reviver re-entry.
        var active = false;
        var cleanups = [];
        var xhrCache = new WeakMap();

        function exactPage() {
            try {
                var url = new NativeURL(document.location.href);
                return url.origin === resource.origin && !url.username && !url.password;
            } catch (_) { return false; }
        }
        var previous = own(window, stateKey);
        if (previous) {
            if (previous.value && previous.value.version === resource.version &&
                typeof previous.value.refresh === 'function') previous.value.refresh();
            return;
        }
        // An existing controller must also be able to stop after navigation. Never create a
        // controller or consult the Android bridge on a different origin.
        if (!exactPage()) return;
        if (resource.schema !== 'miowser.music-player-data/1' || typeof Proxy !== 'function') return;

        function enabled() { return active && exactPage(); }
        function data(object, key) {
            if (!object || typeof object !== 'object') return undefined;
            var desc = own(object, key);
            return desc && 'value' in desc ? desc.value : undefined;
        }
        function playerShape(value) {
            return typeof data(data(value, 'videoDetails'), 'videoId') === 'string' ||
                typeof data(data(value, 'playabilityStatus'), 'status') === 'string';
        }
        function prune(value, endpoint) {
            // Deliberately follow only curated envelope paths, never the renderer/playlist tree.
            // Even arrays and cyclic/non-JSON objects have a fixed visit budget.
            var changed = false;
            var visits = 0;
            function removeFields(object) {
                if (!object || typeof object !== 'object' || Array.isArray(object)) return;
                for (var i = 0; i < resource.adFields.length; i++) {
                    var key = resource.adFields[i];
                    var desc = own(object, key);
                    if (desc && 'value' in desc && desc.configurable) {
                        changed = delete object[key] || changed;
                    }
                }
            }
            function path(object, parts, index) {
                if (++visits > resource.maxPathVisits || !object || typeof object !== 'object') return;
                if (index === parts.length) { removeFields(object); return; }
                if (parts[index] === '*') {
                    if (!Array.isArray(object)) return;
                    var length = Math.min(object.length, resource.maxArrayEntries);
                    for (var j = 0; j < length && visits < resource.maxPathVisits; j++) {
                        path(data(object, String(j)), parts, index + 1);
                    }
                } else {
                    path(data(object, parts[index]), parts, index + 1);
                }
            }
            try {
                if (endpoint === 'player' || playerShape(value)) removeFields(value);
                for (var i = 0; i < resource.envelopes.length; i++) path(value, resource.envelopes[i], 0);
            } catch (_) { /* Frozen objects, proxies or schema drift must leave a usable result. */ }
            return changed;
        }
        function withinTextBudget(text) {
            if (typeof text !== 'string' || text.length > resource.maxTextBytes) return false;
            var bytes = 0;
            for (var i = 0; i < text.length; i++) {
                var code = text.charCodeAt(i);
                if (code < 128) bytes++;
                else if (code < 2048) bytes += 2;
                else if (code >= 0xd800 && code <= 0xdbff && i + 1 < text.length &&
                         text.charCodeAt(i + 1) >= 0xdc00 && text.charCodeAt(i + 1) <= 0xdfff) {
                    bytes += 4;
                    i++;
                } else bytes += 3;
                if (bytes > resource.maxTextBytes) return false;
            }
            return true;
        }
        function cleanText(text, endpoint) {
            if (!enabled() || parsing || !withinTextBudget(text)) return text;
            parsing++;
            try {
                // Capture once before installing hooks: never call the hooked JSON.parse here.
                var object = nativeApply(nativeParse, NativeJSON, [text]);
                return prune(object, endpoint) ? nativeApply(nativeStringify, NativeJSON, [object]) : text;
            } catch (_) { return text; }
            finally { parsing--; }
        }
        function endpointFor(url) {
            try {
                if (typeof url !== 'string' || url.length > 8192) return '';
                var parsed = new NativeURL(url);
                if (parsed.origin !== resource.origin || parsed.username || parsed.password) return '';
                return data(resource.endpoints, parsed.pathname) || '';
            } catch (_) { return ''; }
        }
        function restoreSlot(object, key, descriptor, replacement) {
            return function() {
                try {
                    var current = own(object, key);
                    if (!current) return;
                    if (('value' in replacement && current.value === replacement.value) ||
                        (replacement.get && current.get === replacement.get && current.set === replacement.set)) {
                        if (descriptor) define(object, key, descriptor);
                        else delete object[key];
                    }
                } catch (_) { }
            };
        }
        function hookMethod(object, key, apply) {
            if (!object) return;
            var desc = own(object, key);
            if (!desc || typeof desc.value !== 'function' || !desc.configurable) return;
            var replacement = Object.assign({}, desc, {value: new Proxy(desc.value, {apply: apply})});
            define(object, key, replacement);
            cleanups.push(restoreSlot(object, key, desc, replacement));
        }
        function hookGetter(object, key, read) {
            var desc = object && own(object, key);
            if (!desc || typeof desc.get !== 'function' || !desc.configurable) return;
            var replacement = Object.assign({}, desc, {get: new Proxy(desc.get, {
                apply: function(target, receiver, args) {
                    // The native getter runs first, including brand/state/responseType errors.
                    var value = nativeApply(target, receiver, args);
                    if (!enabled()) return value;
                    try { return read(receiver, value); } catch (_) { return value; }
                }
            })});
            define(object, key, replacement);
            cleanups.push(restoreSlot(object, key, desc, replacement));
        }
        function overrideData(object, key, hidden, onWrite) {
            var desc = own(object, key);
            // Accessors and non-writable/non-configurable slots belong to the site/provider.
            if (desc && (!('value' in desc) || !desc.writable || !desc.configurable)) return null;
            if (!desc && !Object.isExtensible(object)) return null;
            var value = desc && desc.value;
            var written = false;
            var replacement = {
                configurable: true, enumerable: desc ? desc.enumerable : true,
                get: function() { return hidden && enabled() ? undefined : value; },
                set: function(next) {
                    value = next;
                    written = true;
                    if (onWrite) { try { onWrite(next); } catch (_) { } }
                }
            };
            define(object, key, replacement);
            if (onWrite) onWrite(value);
            return function() {
                var original = desc ? Object.assign({}, desc, {value: value}) :
                    written ? {value: value, writable: true, enumerable: true, configurable: true} : null;
                restoreSlot(object, key, original, replacement)();
            };
        }
        function hookGlobal(key, fields) {
            var children = [];
            function releaseChildren() {
                for (var i = children.length - 1; i >= 0; i--) children[i]();
                children = [];
            }
            var release = overrideData(window, key, false, function(value) {
                releaseChildren(); // Retain at most the current object per global, across songs.
                if (!value || typeof value !== 'object' || Array.isArray(value)) return;
                for (var i = 0; i < fields.length; i++) {
                    try {
                        var child = overrideData(value, fields[i], true, null);
                        if (child) children.push(child);
                    } catch (_) { }
                }
            });
            if (release) cleanups.push(function() { releaseChildren(); release(); });
        }
        function install() {
            hookMethod(JSON, 'parse', function(target, receiver, args) {
                if (!enabled() || parsing || !withinTextBudget(args[0])) return nativeApply(target, receiver, args);
                parsing++;
                try {
                    var value = nativeApply(target, receiver, args); // Exceptions/reviver/this/args preserved.
                    prune(value, '');
                    return value;
                } finally { parsing--; }
            });
            // Keep fetch completely native. Read on demand, on the original Response, exactly once.
            // This preserves URL/status/headers/redirect flags, clones, streams and bodyUsed semantics.
            var responseProto = typeof Response === 'function' && Response.prototype;
            var responseURL = responseProto && own(responseProto, 'url');
            if (responseURL && typeof responseURL.get === 'function') {
                ['json', 'text'].forEach(function(kind) {
                    hookMethod(responseProto, kind, function(target, receiver, args) {
                        var promise = nativeApply(target, receiver, args);
                        if (!enabled()) return promise;
                        var endpoint = '';
                        try { endpoint = endpointFor(nativeApply(responseURL.get, receiver, [])); } catch (_) { }
                        if (!endpoint) return promise;
                        try {
                            return nativeApply(nativeThen, promise, [function(value) {
                                if (!enabled()) return value;
                                if (kind === 'text') return cleanText(value, endpoint);
                                prune(value, endpoint);
                                return value;
                            }]);
                        } catch (_) { return promise; } // A site's non-Promise replacement stays usable.
                    });
                });
            }
            var xhrProto = typeof XMLHttpRequest === 'function' && XMLHttpRequest.prototype;
            var xhrURL = xhrProto && own(xhrProto, 'responseURL');
            var xhrType = xhrProto && own(xhrProto, 'responseType');
            if (xhrURL && xhrURL.get && xhrType && xhrType.get) {
                function readXHR(xhr, value) {
                    var endpoint = endpointFor(nativeApply(xhrURL.get, xhr, []));
                    if (!endpoint) return value;
                    var type = nativeApply(xhrType.get, xhr, []);
                    if (type === 'json') { prune(value, endpoint); return value; }
                    if (type !== '' && type !== 'text') return value;
                    var cached = xhrCache.get(xhr);
                    if (cached && cached.value === value && cached.endpoint === endpoint) return cached.result;
                    var result = cleanText(value, endpoint);
                    // Bound retained text per live XHR as well as transformation work.
                    if (typeof value === 'string' && value.length <= resource.maxTextBytes) {
                        xhrCache.set(xhr, {value: value, endpoint: endpoint, result: result});
                    }
                    return result;
                }
                hookGetter(xhrProto, 'response', readXHR);
                hookGetter(xhrProto, 'responseText', readXHR);
            }
            Object.keys(resource.overrides).forEach(function(key) {
                hookGlobal(key, resource.overrides[key]);
            });
        }
        function stop() {
            active = false; // Already handed-out/pruned data cannot be reconstructed.
            for (var i = cleanups.length - 1; i >= 0; i--) { try { cleanups[i](); } catch (_) { } }
            cleanups = [];
            xhrCache = new WeakMap();
        }
        function refresh() {
            var allowed = false;
            try {
                allowed = exactPage() && window.MiowserAdBlock &&
                    window.MiowserAdBlock.musicAdProtectionEnabled(document.location.hostname, document.location.href) === true;
            } catch (_) { }
            if (!allowed) { stop(); return; }
            if (active) return;
            active = true;
            try { install(); } catch (_) { stop(); } // Provider mismatch fails open, including partial installs.
        }
        define(window, stateKey, {value: {version: resource.version, refresh: refresh}, configurable: true});
        window.addEventListener('pagehide', stop);
        window.addEventListener('pageshow', refresh); // Restore policy after back-forward cache activation.
        refresh();
    } catch (_) { /* Unsupported provider or page shape: keep the working page. */ }
})();
"""
