package com.miowser.app.browser.video

/** Read-only, per-document telemetry. No source assignment or playback commands. */
internal const val LIVE_VIDEO_SESSION_SCRIPT = """
(function () {
    'use strict';
    if (window.__miowserLiveVideos) return;
    var bridge = window.MiowserLiveVideo;
    if (!bridge) return;
    var documentId = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    var videos = new Map(), ids = new WeakMap(), nextId = 0, sequence = 0;
    var binding = null, timer = null, heartbeat = null, observer = null;
    var intersection = null, resize = null, running = false, lastSent = -1000;
    var removed = new Set(), listeners = [];
    var MAX_VIDEOS = 8, UPDATE_MS = 500;

    function send(message) {
        try {
            var text = JSON.stringify(message);
            if (text.length <= 24576) bridge.postMessage(text);
        } catch (ignored) {}
    }
    function hello() { if (videos.size > 0) send({type: 'hello', documentId: documentId}); }
    function finite(value) { return Number.isFinite(value) ? value : null; }
    function ranges(value) {
        var result = [];
        try {
            for (var i = Math.max(0, value.length - 8); i < value.length; i++) {
                result.push([finite(value.start(i)), finite(value.end(i))]);
            }
        } catch (ignored) {}
        return result;
    }
    function snapshot(record) {
        var video = record.video, rect = video.getBoundingClientRect();
        var source = video.currentSrc || video.src || '';
        var object = video.srcObject || null;
        if (source !== record.source || object !== record.object) {
            record.sourceRevision++;
            record.source = source;
            record.object = object;
        }
        var kind = object ? 'stream' : source.indexOf('blob:') === 0 ? 'blob' :
            source.indexOf('data:') === 0 ? 'data' : /^https?:/i.test(source) ? 'network' : 'unknown';
        var visible = video.isConnected && document.visibilityState !== 'hidden' &&
            rect.width > 0 && rect.height > 0 && record.ratio > 0 &&
            rect.top < window.innerHeight && rect.left < window.innerWidth &&
            rect.top + rect.height > 0 && rect.left + rect.width > 0;
        var result = {
            elementId: record.id, source: kind === 'data' || source.length > 8192 ? null : source,
            sourceKind: kind, position: finite(video.currentTime), duration: finite(video.duration),
            playing: !video.paused && !video.ended, ended: !!video.ended,
            buffering: !video.paused && video.readyState < 3, muted: !!video.muted,
            speed: finite(video.playbackRate), width: video.videoWidth || 0, height: video.videoHeight || 0,
            visible: visible, intersection: visible ? record.ratio : 0,
            bounds: {left: finite(rect.left), top: finite(rect.top), width: finite(rect.width),
                height: finite(rect.height), viewportWidth: window.innerWidth, viewportHeight: window.innerHeight},
            seekable: ranges(video.seekable), buffered: ranges(video.buffered), readyState: video.readyState,
            sourceRevision: record.sourceRevision, interacted: record.interacted
        };
        record.interacted = false;
        return result;
    }
    function flush() {
        timer = null;
        if (!running || !binding) return;
        var states = [];
        videos.forEach(function (record) {
            if (record.video.isConnected) states.push(snapshot(record));
            else unwatch(record);
        });
        // Keep string-sized payloads bounded even when a page uses very long signed URLs.
        var message = {type: 'state', documentId: documentId, navigation: binding.navigation,
            token: binding.token, sequence: sequence++, videos: states, removed: Array.from(removed)};
        if (JSON.stringify(message).length > 24576) states.forEach(function (state) { state.source = null; });
        removed.clear();
        lastSent = performance.now();
        send(message);
        if (videos.size === 0) {
            send({type: 'retire', documentId: documentId, navigation: binding.navigation, token: binding.token});
            binding = null;
            clearInterval(heartbeat);
            heartbeat = null;
        }
    }
    function schedule() {
        if (!running || !binding || timer !== null) return;
        timer = setTimeout(flush, Math.max(0, UPDATE_MS - (performance.now() - lastSent)));
    }
    function listen(target, type, callback, options) {
        target.addEventListener(type, callback, options);
        listeners.push(function () { target.removeEventListener(type, callback, options); });
    }
    function unwatch(record) {
        record.cleanups.forEach(function (cleanup) { cleanup(); });
        if (intersection) intersection.unobserve(record.video);
        if (resize) resize.unobserve(record.video);
        videos.delete(record.id);
        if (removed.size < MAX_VIDEOS) removed.add(record.id);
    }
    function watch(video) {
        if (!video || video.tagName !== 'VIDEO' || !video.isConnected) return;
        var id = ids.get(video);
        if (id && videos.has(id)) { schedule(); return; }
        if (videos.size >= MAX_VIDEOS) return;
        id = id || 'v' + (++nextId);
        ids.set(video, id);
        var record = {id: id, video: video, source: null, object: null, sourceRevision: 0,
            ratio: 1, interacted: false, cleanups: []};
        videos.set(id, record);
        ['loadedmetadata', 'durationchange', 'loadstart', 'emptied', 'resize', 'play', 'pause',
            'playing', 'waiting', 'seeking', 'seeked', 'timeupdate', 'ratechange', 'ended', 'progress', 'error']
            .forEach(function (type) {
                video.addEventListener(type, schedule);
                record.cleanups.push(function () { video.removeEventListener(type, schedule); });
            });
        function interaction() { record.interacted = true; schedule(); }
        video.addEventListener('pointerdown', interaction, {passive: true});
        record.cleanups.push(function () { video.removeEventListener('pointerdown', interaction); });
        if (intersection) intersection.observe(video);
        if (resize) resize.observe(video);
        if (heartbeat === null) heartbeat = setInterval(function () {
            if (document.visibilityState !== 'hidden' && videos.size > 0) {
                if (!binding) hello();
                schedule();
            }
        }, 10000);
        if (!binding) hello();
        schedule();
    }
    function scan(root) {
        if (root.tagName === 'VIDEO') watch(root);
        if (root.querySelectorAll) {
            var found = root.querySelectorAll('video');
            for (var i = 0; i < found.length && videos.size < MAX_VIDEOS; i++) watch(found[i]);
        }
    }
    function reconnectChildren() {
        for (var i = 0; i < window.frames.length; i++) {
            try { window.frames[i].postMessage({miowserVideoConnect: true}, '*'); } catch (ignored) {}
        }
    }
    function connect() {
        if (!running) start();
        hello();
        reconnectChildren();
    }
    function visibility() { if (document.visibilityState !== 'hidden') connect(); schedule(); }
    function start() {
        if (running) return;
        running = true;
        if (window.IntersectionObserver) intersection = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                var record = videos.get(ids.get(entry.target));
                if (record) record.ratio = entry.isIntersecting ? entry.intersectionRatio : 0;
            });
            schedule();
        }, {threshold: [0, 0.25, 0.75, 1]});
        if (window.ResizeObserver) resize = new ResizeObserver(schedule);
        observer = new MutationObserver(function (mutations) {
            // Remove detached elements before adding replacements, so a full table can make room.
            videos.forEach(function (record) { if (!record.video.isConnected) unwatch(record); });
            mutations.forEach(function (mutation) {
                if (mutation.type === 'attributes') {
                    var target = mutation.target;
                    watch(target.tagName === 'SOURCE' ? target.parentElement : target);
                }
                for (var i = 0; i < mutation.addedNodes.length && videos.size < MAX_VIDEOS; i++) {
                    var node = mutation.addedNodes[i];
                    if (node.nodeType === 1) scan(node);
                }
            });
            schedule();
        });
        observer.observe(document.documentElement || document, {childList: true, subtree: true,
            attributes: true, attributeFilter: ['src']});
        scan(document);
        listen(document, 'DOMContentLoaded', function () { scan(document); connect(); });
        // A late-starting player must not be permanently crowded out by eight idle previews.
        listen(document, 'play', function (event) {
            var video = event.target;
            if (!video || video.tagName !== 'VIDEO' || videos.has(ids.get(video))) return;
            if (videos.size >= MAX_VIDEOS) {
                var candidate = null;
                videos.forEach(function (record) {
                    if (!candidate && (record.video.paused || record.video.ended || record.ratio === 0)) candidate = record;
                });
                if (candidate) unwatch(candidate);
            }
            watch(video);
        }, true);
        listen(document, 'visibilitychange', visibility);
        listen(window, 'scroll', schedule, {passive: true, capture: true});
        listen(window, 'resize', schedule, {passive: true});
        hello();
    }
    function stop() {
        if (!running) return;
        if (binding) send({type: 'retire', documentId: documentId,
            navigation: binding.navigation, token: binding.token});
        running = false;
        if (timer !== null) clearTimeout(timer);
        timer = null;
        clearInterval(heartbeat);
        heartbeat = null;
        if (observer) observer.disconnect();
        if (intersection) intersection.disconnect();
        if (resize) resize.disconnect();
        observer = intersection = resize = null;
        videos.forEach(function (record) { record.cleanups.forEach(function (cleanup) { cleanup(); }); });
        videos.clear();
        removed.clear();
        listeners.forEach(function (cleanup) { cleanup(); });
        listeners = [];
        binding = null;
    }
    bridge.onmessage = function (event) {
        if (!running || typeof event.data !== 'string' || event.data.length > 1024) return;
        try {
            var message = JSON.parse(event.data);
            if (message.type === 'reset' && message.documentId === documentId) {
                binding = null;
                if (document.visibilityState !== 'hidden') hello();
                return;
            }
            if (message.type !== 'bind' || message.documentId !== documentId ||
                !Number.isSafeInteger(message.navigation) || typeof message.token !== 'string' ||
                message.token.length > 80) return;
            binding = {navigation: message.navigation, token: message.token};
            schedule();
        } catch (ignored) {}
    };
    window.addEventListener('message', function (event) {
        if (event.source === window.parent && event.data && event.data.miowserVideoConnect === true) connect();
    });
    window.addEventListener('pagehide', stop);
    window.addEventListener('pageshow', function () { if (!running) connect(); });
    window.__miowserLiveVideos = {connect: connect};
    start();
})();
"""
