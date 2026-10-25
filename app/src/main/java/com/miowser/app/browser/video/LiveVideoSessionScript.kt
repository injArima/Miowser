package com.miowser.app.browser.video

/** Per-document telemetry and a frame-local button. No URL extraction or native-player handoff. */
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
    var assistant = null;
    var controls = null, fullscreenSerial = 0, lifecycleSerial = -1;
    var trackIds = new WeakMap(), nextTrackId = 0;
    var listeners = [];
    var MAX_VIDEOS = 8, UPDATE_MS = 500;

    function send(message) {
        try {
            var text = JSON.stringify(message);
            if (text.length <= 24576) bridge.postMessage(text);
        } catch (ignored) {}
    }
    function hello() { if (videos.size > 0) send({type: 'hello', documentId: documentId}); }
    function finite(value) { return Number.isFinite(value) ? value : null; }
    function trackId(track) {
        var id = trackIds.get(track);
        if (!id) { id = ++nextTrackId; trackIds.set(track, id); }
        return id;
    }
    function mediaTracks(video, audio) {
        var result = [], tracks;
        try {
            tracks = audio ? video.audioTracks : video.textTracks;
            if (!tracks) return result;
            for (var i = 0; i < Math.min(tracks.length, 128) && result.length < (audio ? 8 : 16); i++) {
                var track = tracks[i];
                if (!audio && track.kind !== 'subtitles' && track.kind !== 'captions') continue;
                result.push({id: trackId(track), label: String(track.label || '').slice(0, 80),
                    language: String(track.language || '').slice(0, 16),
                    selected: audio ? !!track.enabled : track.mode === 'showing'});
            }
        } catch (ignored) {}
        return result;
    }
    function ranges(value) {
        var result = [];
        try {
            for (var i = Math.max(0, value.length - 8); i < value.length; i++) {
                result.push([finite(value.start(i)), finite(value.end(i))]);
            }
        } catch (ignored) {}
        return result;
    }
    function refreshSource(record) {
        var video = record.video;
        var source = video.currentSrc || video.src || '';
        var object = video.srcObject || null;
        if (source !== record.source || object !== record.object) {
            record.sourceRevision++;
            record.source = source;
            record.object = object;
            var owned = assistant ? assistant.owner() : null;
            if (controls && owned && owned.record === record) controls.release();
        }
    }
    function snapshot(record) {
        var video = record.video;
        refreshSource(record);
        var result = {
            elementId: record.id, position: finite(video.currentTime), duration: finite(video.duration),
            playing: !video.paused && !video.ended,
            buffering: !video.paused && (video.readyState < 3 || !!video.seeking), muted: !!video.muted,
            speed: finite(video.playbackRate), width: video.videoWidth || 0, height: video.videoHeight || 0,
            seekable: ranges(video.seekable), buffered: ranges(video.buffered), readyState: video.readyState,
            sourceRevision: record.sourceRevision
        };
        var owner = assistant ? assistant.owner() : null;
        if (owner && owner.record === record) {
            result.captions = mediaTracks(video, false);
            result.audioTracks = mediaTracks(video, true);
            result.scale = window.getComputedStyle(video).objectFit || 'contain';
        }
        return result;
    }
    function packet(type, extra) {
        if (!running || !binding) return;
        var states = [];
        videos.forEach(function (record) {
            if (record.video.isConnected) states.push(snapshot(record));
            else unwatch(record);
        });
        var message = {type: type, documentId: documentId, navigation: binding.navigation,
            token: binding.token, sequence: sequence++, videos: states};
        if (extra) Object.keys(extra).forEach(function (key) { message[key] = extra[key]; });
        send(message);
    }
    function flush() {
        timer = null;
        if (!running || !binding) return;
        packet('state');
        lastSent = performance.now();
        if (videos.size === 0) {
            send({type: 'retire', documentId: documentId, navigation: binding.navigation, token: binding.token});
            binding = null;
            clearInterval(heartbeat);
            heartbeat = null;
        }
    }
    function schedule() {
        if (assistant) assistant.schedule();
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
    }
    function watch(video) {
        if (!video || video.tagName !== 'VIDEO' || !video.isConnected) return;
        var id = ids.get(video);
        if (id && videos.has(id)) { schedule(); return; }
        if (videos.size >= MAX_VIDEOS) return;
        id = id || 'v' + (++nextId);
        ids.set(video, id);
        var record = {id: id, video: video, source: null, object: null, sourceRevision: 0,
            ratio: 1, cleanups: []};
        videos.set(id, record);
        ['loadedmetadata', 'durationchange', 'loadstart', 'emptied', 'resize', 'play', 'pause',
            'playing', 'waiting', 'seeking', 'seeked', 'timeupdate', 'ratechange', 'volumechange', 'ended', 'progress', 'error']
            .forEach(function (type) {
                video.addEventListener(type, schedule);
                record.cleanups.push(function () { video.removeEventListener(type, schedule); });
            });
        [video.textTracks, video.audioTracks].forEach(function (tracks) {
            if (!tracks || !tracks.addEventListener) return;
            ['addtrack', 'removetrack', 'change'].forEach(function (event) {
                tracks.addEventListener(event, schedule);
                record.cleanups.push(function () { tracks.removeEventListener(event, schedule); });
            });
        });
        function interaction() { record.lastInteraction = performance.now(); schedule(); }
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
        if (window.__miowserCreateVideoButton) assistant = window.__miowserCreateVideoButton({
            records: function () { return videos; },
            bound: function () { return running && binding !== null; },
            changed: schedule,
            display: function (data) {
                if (!running || !binding) return;
                var message = {type: 'assistantDisplay', documentId: documentId,
                    navigation: binding.navigation, token: binding.token};
                Object.keys(data).forEach(function (key) { message[key] = data[key]; });
                send(message);
            },
            pressReady: function (data) {
                if (!running || !binding) return;
                send({type: 'assistantPressReady', documentId: documentId, navigation: binding.navigation,
                    token: binding.token, version: data.version, request: data.request, ok: data.ok});
            },
            entering: function () { return ++fullscreenSerial; },
            fullscreen: function (record, attempt, active) {
                if (!active && controls) controls.release();
                packet(active ? 'fullscreenActive' : 'fullscreenEnded', {elementId: record.id, attempt: attempt});
            },
            failed: function () {
                if (running && binding) send({type: 'fullscreenError', documentId: documentId,
                    navigation: binding.navigation, token: binding.token});
            }
        });
        if (window.__miowserCreateVideoControls) controls = window.__miowserCreateVideoControls({
            transportIntent: function () { if (assistant) assistant.cancelEntryPlayback(); },
            owner: function () { return assistant ? assistant.owner() : null; },
            rich: function (id, attempt, attached) { return assistant && assistant.rich(id, attempt, attached); },
            trackId: trackId,
            refreshSource: refreshSource,
            reply: function (type, command, ok) {
                packet(type, {commandId: command.commandId, elementId: command.elementId,
                    attempt: command.attempt, ok: ok});
            }
        });
        if (window.IntersectionObserver) intersection = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                var record = videos.get(ids.get(entry.target));
                if (record) {
                    record.ratio = entry.isIntersecting ? entry.intersectionRatio : 0;
                    record.intersectionBounds = entry.boundingClientRect;
                    record.intersectionRect = entry.intersectionRect;
                }
            });
            schedule();
        }, {threshold: [0, 0.05, 0.1, 0.25, 0.5, 0.75, 1]});
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
        function scrollDisplay() { if (assistant) assistant.scroll(); schedule(); }
        function layoutDisplay() { if (assistant) assistant.layoutChanged(); schedule(); }
        listen(document, 'pointerup', function (event) { if (assistant) assistant.wake(event); }, {passive: true});
        listen(window, 'scroll', scrollDisplay, {passive: true, capture: true});
        listen(window, 'resize', layoutDisplay, {passive: true});
        if (window.visualViewport) {
            listen(window.visualViewport, 'resize', layoutDisplay, {passive: true});
            listen(window.visualViewport, 'scroll', scrollDisplay, {passive: true});
        }
        hello();
    }
    function stop() {
        if (!running) return;
        if (binding) send({type: 'retire', documentId: documentId,
            navigation: binding.navigation, token: binding.token});
        running = false;
        if (controls) controls.destroy();
        controls = null;
        if (assistant) assistant.destroy();
        assistant = null;
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
        listeners.forEach(function (cleanup) { cleanup(); });
        listeners = [];
        binding = null;
    }
    bridge.onmessage = function (event) {
        if (!running || typeof event.data !== 'string' || event.data.length > 1024) return;
        try {
            var message = JSON.parse(event.data);
            if (message.type === 'reset' && message.documentId === documentId) {
                if (controls) controls.release();
                if (assistant) assistant.fallback();
                binding = null;
                if (assistant) assistant.policy(null);
                if (document.visibilityState !== 'hidden') hello();
                return;
            }
            if (message.type === 'bind') {
                if (message.documentId !== documentId || !Number.isSafeInteger(message.navigation) ||
                    typeof message.token !== 'string' || message.token.length > 80) return;
                binding = {navigation: message.navigation, token: message.token};
                if (assistant) assistant.policy(message.button);
                schedule(); return;
            }
            if (!binding || message.documentId !== documentId || message.navigation !== binding.navigation ||
                message.token !== binding.token) return;
            if (message.type === 'buttonPolicy') {
                if (assistant) assistant.policy(message.button);
                return;
            }
            if (message.type === 'control') {
                if (controls) controls.receive(message);
                return;
            }
            if (message.type === 'nativeDisplayReady' || message.type === 'nativeDisplayLost') {
                if (assistant) {
                    if (message.type === 'nativeDisplayLost') assistant.nativeLost();
                    else assistant.nativeReady(message.version, message.ok);
                }
                return;
            }
            if (message.type === 'nativePress' || message.type === 'nativePressCancel') {
                if (assistant) {
                    if (message.type === 'nativePress') assistant.nativePress(message.version, message.request);
                    else assistant.nativePressCancel(message.request);
                }
                return;
            }
            if (['releaseFullscreen', 'suspendFullscreen', 'resumeFullscreen', 'fallbackFullscreen'].indexOf(message.type) >= 0) {
                var owner = assistant ? assistant.owner() : null;
                var matches = owner && owner.record.id === message.elementId && owner.attempt === message.attempt;
                if (!Number.isSafeInteger(message.recoveryId) || message.recoveryId < 0) return;
                if (matches) {
                    if (message.recoveryId < lifecycleSerial) return;
                    lifecycleSerial = message.recoveryId;
                }
                if (message.type === 'resumeFullscreen') {
                    if (matches && controls) controls.release();
                    var resumed = matches && owner.video.isConnected && assistant.rich(message.elementId, message.attempt, true);
                    packet('fullscreenResumed', {elementId: message.elementId, attempt: message.attempt,
                        recoveryId: message.recoveryId, ok: !!resumed});
                } else if (matches) {
                    if (message.type !== 'suspendFullscreen' || message.pip !== true) assistant.cancelEntryPlayback();
                    if (controls) controls.release();
                    if (message.type === 'releaseFullscreen') assistant.release(message.elementId, message.attempt);
                    else assistant.rich(message.elementId, message.attempt, message.type === 'suspendFullscreen');
                }
                return;
            }
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
