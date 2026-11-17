package com.miowser.app.browser.media

/** Document-start code, executed independently in each HTTP(S) frame. */
internal val PAGE_MEDIA_SCRIPT = PAGE_MEDIA_BRIDGE_SCRIPT.replace(
    "/*SITE_MEDIA_COMPATIBILITY*/", SITE_MEDIA_COMPATIBILITY_SCRIPT)

internal const val PAGE_MEDIA_BRIDGE_SCRIPT = """
(function () {
    'use strict';
    if (location.protocol !== 'https:' && location.protocol !== 'http:') return;
    if (window.__miowserPageMedia) { window.__miowserPageMedia.connect(); return; }
    var bridge = window.MiowserPageMedia;
    if (!bridge) return;
    /*SITE_MEDIA_COMPATIBILITY*/
    var compatibility = createSiteMediaCompatibility(), compatibilityOwner = null;
    var documentId = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    var binding = null, generation = -1, sequence = 0, activation = 0, lastCommand = 0;
    var records = new Map(), ids = new WeakMap(), nextId = 0, selected = null;
    var running = false, destroyed = false, timer = null, retry = null, retries = 0, lastHello = -1000, lastSent = -1000;
    var heartbeat = null;
    var observers = [], cleanups = [], metadataCleanups = [], session = null, metadata = null;
    var handlers = Object.create(null), positionState = null;
    var actionNames = ['play', 'pause', 'stop', 'seekto', 'seekbackward', 'seekforward', 'previoustrack', 'nexttrack'];
    var mediaEvents = ['play', 'playing', 'pause', 'ended', 'loadedmetadata', 'loadeddata', 'loadstart', 'emptied',
        'durationchange', 'timeupdate', 'ratechange', 'volumechange', 'seeking', 'seeked', 'waiting', 'stalled', 'error'];
    var MAX_MEDIA = 16, UPDATE_MS = 250, MAX_TIME = 31536000;

    function send(message) {
        try {
            var text = JSON.stringify(message);
            if (text.length <= 16384) bridge.postMessage(text);
        } catch (ignored) {}
    }
    function packet(type, extra) {
        if (!binding) return;
        var message = {type: type, documentId: documentId, navigation: binding.navigation, token: binding.token};
        if (extra) Object.keys(extra).forEach(function (key) { message[key] = extra[key]; });
        send(message);
    }
    function string(value, limit) { return typeof value === 'string' ? value.slice(0, limit) : ''; }
    function seconds(value, fallback) { return Number.isFinite(value) && value >= 0 ? Math.min(MAX_TIME, value) : fallback; }
    function httpUrl(value) {
        if (typeof value !== 'string' || !value || value.length > 4096) return null;
        try {
            var url = new URL(value, location.href);
            return (url.protocol === 'http:' || url.protocol === 'https:') && !url.username && !url.password &&
                url.href.length <= 4096 ? url.href : null;
        } catch (ignored) { return null; }
    }
    function listen(target, name, callback, options) {
        target.addEventListener(name, callback, options);
        cleanups.push(function () { target.removeEventListener(name, callback, options); });
    }
    function playing(media) { return !media.paused && !media.ended; }
    function audible(record) { return !record.media.muted && record.media.volume > 0 || record.engaged; }
    function clearDirect(record) {
        if (!record.directListener) return;
        mediaEvents.forEach(function (name) { record.media.removeEventListener(name, record.directListener); });
        record.directListener = null;
    }
    function live(record) {
        if (record.media.isConnected) {
            record.wasConnected = true;
            clearDirect(record);
            return true;
        }
        return !record.wasConnected;
    }
    function unwatch(record) {
        clearDirect(record);
        records.delete(record.media);
        if (positionState && positionState.record === record) positionState = null;
    }
    function watchDirect(record) {
        if (record.directListener || record.wasConnected || record.media.isConnected) return;
        record.directListener = function (event) {
            // Document/open-root capture owns connected media events, including loadstart.
            if (record.media.isConnected) { record.wasConnected = true; clearDirect(record); return; }
            if (running && !record.wasConnected && records.get(record.media) === record) onMedia(event, true);
        };
        mediaEvents.forEach(function (name) { record.media.addEventListener(name, record.directListener); });
    }
    function refreshSource(record, force) {
        var media = record.media, source = media.currentSrc || '', requested = media.src || '', object = media.srcObject || null;
        var children = media.children || [], sources = '';
        for (var i = 0; i < Math.min(children.length, 16); i++)
            if (children[i].tagName === 'SOURCE') sources += String(children[i].src || '') + '\n' + String(children[i].type || '') + '\n';
        if (force || source !== record.source || requested !== record.requested || sources !== record.sources || object !== record.object) {
            record.source = source; record.requested = requested; record.sources = sources; record.object = object; record.revision++;
            record.everPlayed = false; record.wasActive = false;
            // Position state belongs to a source, not to an element reused for the next track.
            if (selected === record) positionState = null;
        }
    }
    function choose() {
        var best = null;
        records.forEach(function (record, media) {
            if (!live(record)) { unwatch(record); return; }
            refreshSource(record, false);
            var active = playing(media) && media.readyState >= 2 && audible(record);
            if (active && !record.wasActive) { record.activity = ++activation; record.everPlayed = true; }
            record.wasActive = active;
            if (!record.everPlayed) return;
            if (!best || (playing(media) && !playing(best.media)) ||
                playing(media) === playing(best.media) && record.activity > best.activity) best = record;
        });
        selected = best;
        if (compatibilityOwner && (!best || ownerKey(best) !== compatibilityOwner)) {
            compatibilityOwner = null; compatibility.setAllowed(false);
        }
        return best;
    }
    function ownerKey(record) { return binding ? binding.prefix + record.id + ':' + record.revision : ''; }
    function watch(media, allowDetached) {
        if (!media || (media.tagName !== 'AUDIO' && media.tagName !== 'VIDEO')) return null;
        var record = records.get(media) || ids.get(media);
        if (!media.isConnected && (!allowDetached || record && record.wasConnected)) return null;
        if (records.has(media)) { live(record); return record; }
        if (records.size >= MAX_MEDIA) {
            // Idle DOM previews never prevent a later, real player from being discovered.
            var victim = null;
            records.forEach(function (entry) {
                if (!victim && (!live(entry) || !entry.everPlayed || !playing(entry.media))) victim = entry;
            });
            if (!victim || !playing(media) || media.muted || !(media.volume > 0)) return null;
            unwatch(victim);
        }
        if (!record) {
            record = {media: media, id: 'm' + (++nextId), revision: 0, source: null, requested: null, sources: null, object: null,
                everPlayed: false, engaged: false, wasActive: false, activity: 0,
                wasConnected: !!media.isConnected, directListener: null};
            ids.set(media, record);
        }
        records.set(media, record);
        if (media.isConnected) { record.wasConnected = true; clearDirect(record); }
        else watchDirect(record);
        refreshSource(record, false);
        return record;
    }
    function onMedia(event, allowDetached) {
        var record = watch(event.target, allowDetached === true);
        if (!record) return;
        if (event.type === 'loadstart' || event.type === 'emptied') refreshSource(record, true);
        rebasePosition(record);
        if (positionState && positionState.record === record) {
            if (event.type === 'seeked') positionState.position = seconds(record.media.currentTime, 0);
            if (event.type === 'ratechange') positionState.rate = Math.min(16, record.media.playbackRate > 0 ? record.media.playbackRate : 1);
        }
        choose(); schedule();
    }
    function onInteraction(event) {
        if (!event.isTrusted) return;
        var record = watch(event.target);
        if (record) { record.engaged = true; choose(); schedule(); }
    }
    function discover(root) {
        if (!root) return;
        if (root.nodeType !== 1 && root.nodeType !== 9 && root.nodeType !== 11) return;
        var walker = document.createTreeWalker(root, 1), node = root.nodeType === 1 ? root : walker.nextNode(), visited = 0;
        while (node && visited++ < 512) {
            watch(node);
            // Open roots present on insertion are supported without polling the document.
            if (node.shadowRoot) observeRoot(node.shadowRoot);
            node = walker.nextNode();
        }
    }
    function observeRoot(root) {
        if (observers.some(function (entry) { return entry.root === root; }) || observers.length >= 32) return;
        var observer = new MutationObserver(function (changes) {
            var budget = 256;
            changes.forEach(function (change) {
                if (change.type === 'childList') {
                    for (var i = 0; i < change.addedNodes.length && budget-- > 0; i++) discover(change.addedNodes[i]);
                } else if (change.target.tagName === 'SOURCE') watch(change.target.parentElement);
            });
            choose(); schedule();
        });
        observers.push({root: root, observer: observer});
        observer.observe(root, {childList: true, subtree: true, attributes: true, attributeFilter: ['src']});
        mediaEvents.forEach(function (name) { listen(root, name, onMedia, true); });
        listen(root, 'pointerdown', onInteraction, true);
        listen(root, 'keydown', onInteraction, true);
        discover(root);
    }

    // Delegate to the original Web API first. The site's callbacks retain identity, this, arguments and exceptions.
    function hookMethod(target, key, after) {
        var original = target[key], own = Object.getOwnPropertyDescriptor(target, key);
        if (typeof original !== 'function') return;
        var wrapper = function () {
            var result = original.apply(this, arguments);
            if (this === target) { try { after(arguments); } catch (ignored) {} }
            return result;
        };
        try {
            Object.defineProperty(target, key, {configurable: true, writable: true, value: wrapper});
            cleanups.push(function () {
                if (target[key] !== wrapper) return;
                if (own) Object.defineProperty(target, key, own); else delete target[key];
            });
        } catch (ignored) {}
    }
    function hookAccessor(target, key, changed, restores) {
        var own = Object.getOwnPropertyDescriptor(target, key), owner = target, descriptor = own;
        while (!descriptor && (owner = Object.getPrototypeOf(owner))) descriptor = Object.getOwnPropertyDescriptor(owner, key);
        if (!descriptor || typeof descriptor.get !== 'function' || typeof descriptor.set !== 'function' || own && !own.configurable) return;
        var getter = function () { return descriptor.get.call(this); };
        var setter = function (value) { descriptor.set.call(this, value); if (this === target) changed(); };
        try {
            Object.defineProperty(target, key, {configurable: true, enumerable: descriptor.enumerable, get: getter, set: setter});
            restores.push(function () {
                var current = Object.getOwnPropertyDescriptor(target, key);
                if (!current || current.get !== getter || current.set !== setter) return;
                if (own) Object.defineProperty(target, key, own); else delete target[key];
            });
        } catch (ignored) {}
    }
    function watchMetadata() {
        var next = session && session.metadata;
        if (next === metadata) return;
        metadataCleanups.forEach(function (cleanup) { cleanup(); }); metadataCleanups = [];
        metadata = next;
        if (metadata) ['title', 'artist', 'album', 'artwork'].forEach(function (key) {
            hookAccessor(metadata, key, schedule, metadataCleanups);
        });
    }
    function hookSession() {
        try { session = navigator.mediaSession || null; } catch (ignored) { session = null; }
        if (!session) return;
        hookMethod(session, 'setActionHandler', function (args) {
            if (actionNames.indexOf(args[0]) < 0) return;
            if (typeof args[1] === 'function') handlers[args[0]] = args[1]; else delete handlers[args[0]];
            schedule();
        });
        hookMethod(session, 'setPositionState', function (args) {
            var value = args[0];
            positionState = value && Number.isFinite(value.duration) ? {
                duration: seconds(value.duration, null), position: seconds(value.position, 0),
                rate: Number.isFinite(value.playbackRate) && value.playbackRate > 0 ? Math.min(value.playbackRate, 16) : 1,
                at: performance.now(), record: choose(), revision: selected && selected.revision,
                advancing: !!(selected && playing(selected.media) && selected.media.readyState >= 3 && !selected.media.seeking)
            } : null;
            schedule();
        });
        hookAccessor(session, 'metadata', function () { watchMetadata(); schedule(); }, cleanups);
        hookAccessor(session, 'playbackState', schedule, cleanups);
        watchMetadata();
    }
    function hookShadowRoots() {
        var prototype = window.Element && window.Element.prototype;
        if (!prototype || typeof prototype.attachShadow !== 'function') return;
        var original = prototype.attachShadow, descriptor = Object.getOwnPropertyDescriptor(prototype, 'attachShadow');
        var wrapper = function () {
            var root = original.apply(this, arguments);
            if (running && arguments[0] && arguments[0].mode === 'open') {
                try { observeRoot(root); } catch (ignored) {}
            }
            return root;
        };
        try {
            Object.defineProperty(prototype, 'attachShadow', Object.assign({}, descriptor, {value: wrapper}));
            cleanups.push(function () {
                if (prototype.attachShadow === wrapper) Object.defineProperty(prototype, 'attachShadow', descriptor);
            });
        } catch (ignored) {}
    }
    function hookMediaPlay() {
        var prototype = window.HTMLMediaElement && window.HTMLMediaElement.prototype;
        if (!prototype) return;
        var descriptor = Object.getOwnPropertyDescriptor(prototype, 'play');
        if (!descriptor || typeof descriptor.value !== 'function') return;
        var original = descriptor.value;
        var wrapper = function () {
            // Preserve the native receiver validation, exceptions and exact Promise identity.
            var result = original.apply(this, arguments);
            if (running) {
                try { watch(this, true); choose(); schedule(); } catch (ignored) {}
            }
            return result;
        };
        try {
            Object.defineProperty(prototype, 'play', Object.assign({}, descriptor, {value: wrapper}));
            cleanups.push(function () {
                if (prototype.play === wrapper) Object.defineProperty(prototype, 'play', descriptor);
            });
        } catch (ignored) {}
    }
    function seekable(media) { return Number.isFinite(media.duration) && media.duration > 0; }
    function rebasePosition(record) {
        var anchor = positionState;
        if (!anchor || anchor.record !== record || anchor.revision !== record.revision) return;
        var now = performance.now();
        if (anchor.advancing) anchor.position = Math.min(anchor.duration,
            anchor.position + Math.max(0, now - anchor.at) * anchor.rate / 1000);
        anchor.at = now;
        anchor.advancing = playing(record.media) && record.media.readyState >= 3 && !record.media.seeking;
    }
    function availableActions(record) {
        var result = ['play', 'pause', 'stop'];
        if (seekable(record.media)) result.push('seekto', 'seekbackward', 'seekforward');
        actionNames.forEach(function (name) { if (handlers[name] && result.indexOf(name) < 0) result.push(name); });
        return result;
    }
    function state(record) {
        var media = record.media, meta = session && session.metadata, artwork = null;
        if (meta && meta.artwork) {
            for (var i = 0; i < Math.min(meta.artwork.length, 8); i++) {
                artwork = httpUrl(meta.artwork[i].src);
                if (artwork) break;
            }
        }
        var duration = seconds(media.duration, null), position = seconds(media.currentTime, 0);
        var speed = Number.isFinite(media.playbackRate) && media.playbackRate > 0 ? Math.min(media.playbackRate, 16) : 1;
        var anchor = positionState;
        if (anchor && anchor.record === record && anchor.revision === record.revision) {
            rebasePosition(record);
            duration = anchor.duration; speed = anchor.rate;
            position = anchor.position;
        }
        return {elementId: record.id, sourceRevision: record.revision, everPlayed: true,
            hasMetadataTitle: !!string(meta && meta.title, 512),
            title: string(meta && meta.title, 512) || string(document.title, 512),
            artist: string(meta && meta.artist, 512), album: string(meta && meta.album, 512), artworkUrl: artwork,
            pageUrl: httpUrl(location.href), playing: playing(media),
            buffering: playing(media) && (media.readyState < 3 || !!media.seeking),
            positionMs: Math.round(position * 1000), durationMs: duration === null ? null : Math.round(duration * 1000),
            speed: speed, actions: availableActions(record)};
    }
    function hello() {
        if (!running || binding || !choose() || performance.now() - lastHello < 500) return;
        lastHello = performance.now();
        send({type: 'hello', documentId: documentId});
        if (retry === null && retries++ < 6) retry = setTimeout(function () { retry = null; hello(); }, 600);
    }
    function flush() {
        timer = null;
        if (!running) return;
        var record = choose();
        updateHeartbeat(record);
        if (!binding) { hello(); return; }
        watchMetadata();
        packet('state', {sequence: sequence++, activation: activation, media: record ? state(record) : null});
        lastSent = performance.now();
    }
    function updateHeartbeat(record) {
        var active = record && playing(record.media);
        if (!active && heartbeat !== null) { clearTimeout(heartbeat); heartbeat = null; }
        if (active && heartbeat === null) heartbeat = setTimeout(function () {
            heartbeat = null; if (running) flush();
        }, 5000);
    }
    function schedule() {
        if (!running || timer !== null) return;
        timer = setTimeout(flush, Math.max(0, UPDATE_MS - (performance.now() - lastSent)));
    }
    function seek(media, target) {
        if (!seekable(media) || !Number.isFinite(target)) return false;
        target = Math.max(0, Math.min(media.duration, target));
        // Respect disjoint live/DVR ranges when the engine exposes them.
        var ranges = media.seekable;
        if (ranges && ranges.length > 0) {
            var best = null, distance = Infinity;
            for (var i = 0; i < Math.min(ranges.length, 32); i++) {
                var start = ranges.start(i), end = ranges.end(i);
                if (!Number.isFinite(start) || !Number.isFinite(end) || start > end) continue;
                var candidate = Math.max(start, Math.min(end, target));
                if (Math.abs(candidate - target) < distance) { best = candidate; distance = Math.abs(candidate - target); }
            }
            if (best === null) return false;
            target = best;
        }
        media.currentTime = target; positionState = null; return true;
    }
    function command(message) {
        var record = choose();
        if (!record || message.ownerKey !== ownerKey(record) || message.elementId !== record.id ||
            message.sourceRevision !== record.revision || !Number.isSafeInteger(message.commandId) ||
            message.commandId <= lastCommand || availableActions(record).indexOf(message.action) < 0) return;
        lastCommand = message.commandId;
        var media = record.media, action = message.action;
        if (action === 'seekto' && (!Number.isFinite(message.positionMs) || message.positionMs < 0 || message.positionMs > MAX_TIME * 1000)) return;
        function done(ok) {
            // Async play completions from an earlier source/binding cannot acknowledge the current owner.
            if (!binding || message.navigation !== binding.navigation || message.token !== binding.token ||
                records.get(media) !== record || !live(record)) return;
            refreshSource(record, false);
            if (ownerKey(record) !== message.ownerKey) return;
            packet('ack', {commandId: message.commandId, ownerKey: message.ownerKey, ok: ok}); schedule();
        }
        try {
            var details = {action: action}, result;
            if (action === 'seekto') { details.seekTime = message.positionMs / 1000; details.fastSeek = false; }
            if (action === 'seekbackward' || action === 'seekforward') details.seekOffset = 10;
            if (handlers[action]) result = handlers[action].call(session, details);
            else if (action === 'play') result = media.play();
            else if (action === 'pause') media.pause();
            else if (action === 'stop') { media.pause(); if (seekable(media)) seek(media, 0); }
            else if (action === 'seekto') { done(seek(media, details.seekTime)); return; }
            else if (action === 'seekbackward' || action === 'seekforward') {
                done(seek(media, media.currentTime + (action === 'seekforward' ? 10 : -10))); return;
            } else { done(false); return; }
            if (result && typeof result.then === 'function') result.then(function () { done(true); }, function () { done(false); });
            else done(true);
        } catch (ignored) { done(false); }
    }
    function start() {
        if (running || destroyed) return;
        running = true; retries = 0; lastHello = -1000;
        hookSession(); hookShadowRoots(); hookMediaPlay(); observeRoot(document);
        records.forEach(function (record) { watchDirect(record); });
        choose(); schedule();
        listen(document, 'visibilitychange', schedule);
        listen(window, 'popstate', schedule);
        listen(window, 'hashchange', schedule);
        // These events accompany metadata updates on the allowlisted SPA players.
        listen(document, 'yt-navigate-finish', schedule);
        listen(document, 'yt-player-updated', schedule);
        hello();
    }
    function stop(permanent) {
        if (binding) packet('retire');
        running = false; binding = null; selected = null; compatibilityOwner = null;
        compatibility.setAllowed(false);
        if (timer !== null) clearTimeout(timer);
        if (retry !== null) clearTimeout(retry);
        if (heartbeat !== null) clearTimeout(heartbeat);
        timer = retry = heartbeat = null;
        observers.forEach(function (entry) { entry.observer.disconnect(); }); observers = [];
        metadataCleanups.forEach(function (cleanup) { try { cleanup(); } catch (ignored) {} }); metadataCleanups = [];
        cleanups.reverse().forEach(function (cleanup) { try { cleanup(); } catch (ignored) {} }); cleanups = [];
        records.forEach(clearDirect);
        metadata = session = positionState = null;
        // BFCache preserves the site's registrations, which the Media Session API cannot read back.
        if (permanent) { destroyed = true; records.clear(); handlers = Object.create(null); }
    }
    function connect() {
        if (destroyed) return;
        start(); retries = 0; lastHello = -1000;
        if (retry !== null) clearTimeout(retry);
        retry = null;
        if (binding) schedule(); else hello();
        for (var i = 0; i < Math.min(window.frames.length, 64); i++) {
            try { window.frames[i].postMessage({miowserPageMediaConnect: true}, '*'); } catch (ignored) {}
        }
    }
    bridge.onmessage = function (event) {
        if (destroyed || !running || typeof event.data !== 'string' || event.data.length > 2048) return;
        try {
            var message = JSON.parse(event.data);
            if (message.documentId !== documentId || !Number.isSafeInteger(message.navigation) || message.navigation < generation) return;
            if (message.type === 'bind') {
                if (typeof message.token !== 'string' || message.token.length < 1 || message.token.length > 80 ||
                    typeof message.ownerPrefix !== 'string' || message.ownerPrefix.length > 256) return;
                if (binding && message.navigation === binding.navigation && message.token !== binding.token) return;
                generation = message.navigation;
                binding = {navigation: message.navigation, token: message.token, prefix: message.ownerPrefix};
                if (retry !== null) clearTimeout(retry);
                retry = null; retries = 0; schedule(); return;
            }
            if (!binding || message.navigation !== binding.navigation || message.token !== binding.token) return;
            if (message.type === 'reset') {
                compatibilityOwner = null; compatibility.setAllowed(false);
                binding = null; lastCommand = 0; return;
            }
            if (message.type === 'destroy') { stop(true); return; }
            if (message.type === 'background') {
                var current = choose();
                var allowed = message.allowed === true && current && message.ownerKey === ownerKey(current);
                compatibilityOwner = allowed ? message.ownerKey : null;
                compatibility.setAllowed(!!allowed); return;
            }
            if (message.type === 'command') command(message);
        } catch (ignored) {}
    };
    function onFrameMessage(event) {
        if (window !== window.top && event.source === window.parent && event.data && event.data.miowserPageMediaConnect === true) connect();
    }
    function onHide(event) { stop(!event.persisted); }
    function onShow() { if (!running && !destroyed) connect(); }
    window.addEventListener('message', onFrameMessage);
    window.addEventListener('pagehide', onHide);
    window.addEventListener('pageshow', onShow);
    window.__miowserPageMedia = {connect: connect};
    start();
})();
"""
