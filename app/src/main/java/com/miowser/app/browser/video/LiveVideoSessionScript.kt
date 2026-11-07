package com.miowser.app.browser.video

/** Per-document telemetry and frame-aware browser-session playback. */
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
    var frameRoutes = new Map(), shadowObservers = new Map(), shadowSweep = null,
        lastHeartbeatAt = 0, lastShadowScanAt = 0, lastHelloAt = -1000;
    var MAX_VIDEOS = 8, UPDATE_MS = 500;

    function send(message) {
        try {
            var text = JSON.stringify(message);
            if (text.length <= 24576) bridge.postMessage(text);
        } catch (ignored) {}
    }
    function hello() {
        if (binding || performance.now() - lastHelloAt < 500) return;
        var active = false;
        videos.forEach(function (record) {
            var video = record.video;
            if (video.isConnected && !video.paused && !video.ended && !video.muted && video.volume > 0)
                active = true;
        });
        // Idle/muted previews must not occupy the bounded frame table ahead of the
        // actual player. A parent with a live child route still needs a channel.
        if (!active && frameRoutes.size === 0) return;
        lastHelloAt = performance.now();
        send({type: 'hello', documentId: documentId});
    }
    function childFrame(source) {
        var roots = [document];
        shadowObservers.forEach(function (_, root) { roots.push(root); });
        var checked = 0;
        for (var r = 0; r < roots.length && checked < 32; r++) {
            var frames = roots[r].querySelectorAll('iframe,frame');
            for (var i = 0; i < frames.length && checked < 32; i++, checked++) {
                if (frames[i].contentWindow === source) return frames[i];
            }
        }
        return null;
    }
    function routeFrame(document, message) {
        var route = frameRoutes.get(document);
        if (route) route.source.postMessage(message, '*');
        else if (window !== window.top && message.miowserVideoPrepare)
            window.parent.postMessage({miowserVideoPrepared: true, documentId: document,
                elementId: message.elementId, version: message.version, request: message.request,
                ok: false}, '*');
    }
    function onFrameMessage(event) {
        var data = event.data;
        if (!running || !data || typeof data !== 'object' ||
            typeof data.documentId !== 'string' || data.documentId.length < 1 || data.documentId.length > 80) return;
        if (event.source === window.parent && window !== window.top) {
            if (data.miowserVideoPrepare === true || data.miowserVideoCancel === true) {
                if (!Number.isSafeInteger(data.request) || data.request < 1 ||
                    !Number.isSafeInteger(data.version) || data.version < 1) return;
                if (data.documentId === documentId) {
                    if (data.miowserVideoCancel === true) assistant && assistant.embeddedCancel(data.request);
                    else {
                        var prepared = assistant && assistant.embeddedPress(data.request, data.elementId);
                        window.parent.postMessage({miowserVideoPrepared: true, documentId: documentId,
                            elementId: data.elementId, version: data.version, request: data.request, ok: !!prepared,
                            x: prepared && prepared.x, y: prepared && prepared.y,
                            width: prepared && prepared.width, height: prepared && prepared.height}, '*');
                    }
                } else routeFrame(data.documentId, data);
            }
            return;
        }
        var frame = childFrame(event.source);
        if (!frame) return;
        if (data.miowserVideoPrepared === true) {
            var pending = frameRoutes.get(data.documentId);
            if (!pending || pending.source !== event.source) return;
            if (data.elementId !== pending.elementId) return;
            if (data.ok === true) {
                var preparedRect = frame.getBoundingClientRect();
                if (![data.x, data.y, data.width, data.height].every(Number.isFinite) ||
                    data.width <= 0 || data.height <= 0 || data.x < 0 || data.y < 0 ||
                    data.x > data.width || data.y > data.height) data.ok = false;
                else {
                    data.x = preparedRect.left + data.x * preparedRect.width / data.width;
                    data.y = preparedRect.top + data.y * preparedRect.height / data.height;
                    data.width = window.innerWidth; data.height = window.innerHeight;
                    var preparedView = window.visualViewport;
                    var vx = preparedView ? preparedView.offsetLeft : 0;
                    var vy = preparedView ? preparedView.offsetTop : 0;
                    var vw = preparedView ? preparedView.width : window.innerWidth;
                    var vh = preparedView ? preparedView.height : window.innerHeight;
                    if (data.x < vx || data.x > vx + vw || data.y < vy || data.y > vy + vh)
                        data.ok = false;
                }
            }
            if (window === window.top) {
                if (assistant) assistant.externalPressReady(data.documentId, data.elementId, data.version, data.request,
                    data.ok === true, data.x, data.y);
            } else window.parent.postMessage(data, '*');
            return;
        }
        if (data.miowserVideoFrame !== true) return;
        var previous = frameRoutes.get(data.documentId);
        if (data.active !== true) {
            if (previous && previous.source === event.source) {
                clearTimeout(previous.timeout); frameRoutes.delete(data.documentId);
                if (window === window.top) {
                    if (assistant) assistant.externalCandidate({documentId: data.documentId, active: false});
                } else window.parent.postMessage(data, '*');
                schedule();
            }
            return;
        }
        if (frameRoutes.size >= 8 && !previous) return;
        if (typeof data.elementId !== 'string' || data.elementId.length < 1 || data.elementId.length > 80) return;
        if (![data.x, data.y, data.width, data.height, data.area].every(Number.isFinite) ||
            data.width <= 0 || data.height <= 0 || data.width > 32768 || data.height > 32768 ||
            data.x < 0 || data.y < 0 || data.x > data.width || data.y > data.height ||
            data.area < 0 || data.area > 1073741824) return;
        var rect = frame.getBoundingClientRect();
        var x = rect.left + data.x * rect.width / data.width;
        var y = rect.top + data.y * rect.height / data.height;
        var view = window.visualViewport;
        var left = view ? view.offsetLeft : 0, top = view ? view.offsetTop : 0;
        var right = left + (view ? view.width : window.innerWidth);
        var bottom = top + (view ? view.height : window.innerHeight);
        if (rect.width < 120 || rect.height < 80 || x < rect.left || x > rect.right ||
            y < rect.top || y > rect.bottom || x < left || x > right || y < top || y > bottom) return;
        if (previous) clearTimeout(previous.timeout);
        var route = {source: event.source, elementId: data.elementId, timeout: null};
        route.timeout = setTimeout(function () {
            if (frameRoutes.get(data.documentId) !== route) return;
            frameRoutes.delete(data.documentId);
            if (window === window.top) {
                if (assistant) assistant.externalCandidate({documentId: data.documentId, active: false});
            } else window.parent.postMessage({miowserVideoFrame: true, documentId: data.documentId,
                active: false}, '*');
            schedule();
        }, 4000);
        frameRoutes.set(data.documentId, route);
        var mapped = {miowserVideoFrame: true, documentId: data.documentId, active: true,
            x: x, y: y, width: window.innerWidth, height: window.innerHeight,
            area: data.area * rect.width * rect.height / (data.width * data.height), elementId: data.elementId};
        if (window === window.top) {
            if (!binding) hello();
            if (assistant) {
                assistant.externalCandidate({documentId: data.documentId, elementId: data.elementId, active: true,
                    embedded: true, target: {x: x, y: y}, area: mapped.area, seenAt: performance.now()});
                assistant.lease();
            }
            schedule();
        } else window.parent.postMessage(mapped, '*');
    }
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
        if (videos.size === 0 && frameRoutes.size === 0) {
            send({type: 'retire', documentId: documentId, navigation: binding.navigation, token: binding.token});
            binding = null;
        }
    }
    function schedule() {
        if (assistant) assistant.schedule();
        if (!running) return;
        if (!binding) { hello(); return; }
        if (timer !== null) return;
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
        if (videos.size >= MAX_VIDEOS) {
            if (video.paused || video.ended || video.muted || !(video.volume > 0)) return;
            var idle = null;
            videos.forEach(function (record) {
                if (!idle && (record.video.paused || record.video.ended || record.video.muted ||
                    record.ratio === 0)) idle = record;
            });
            if (!idle) return;
            unwatch(idle);
        }
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
        if (!binding) hello();
        schedule();
    }
    function startHeartbeat() {
        if (heartbeat !== null) return;
        heartbeat = setInterval(function () {
            var now = performance.now();
            // A player can attach an open shadow root to an existing host without
            // causing a light-DOM mutation. Rediscover it at a bounded cadence.
            if (document.visibilityState !== 'hidden' && now - lastShadowScanAt >= 5000) {
                lastShadowScanAt = now;
                scanOpenRoots(document);
            }
            if (document.visibilityState !== 'hidden' && videos.size > 0 &&
                (now - lastHeartbeatAt >= 10000 || assistant && assistant.needsFrameHeartbeat())) {
                lastHeartbeatAt = now;
                if (!binding) hello();
                schedule();
            }
        }, 2500);
    }
    function scan(root) {
        if (root.tagName === 'VIDEO') watch(root);
        if (root.querySelectorAll) {
            var found = root.querySelectorAll('video');
            for (var i = 0; i < found.length; i++) watch(found[i]);
        }
        scanOpenRoots(root);
    }
    function scanOpenRoots(root) {
        // Document observers do not cross shadow boundaries. Watch open roots only;
        // closed roots remain outside the page API and are not probed.
        var walker = root === document && shadowSweep ||
            document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
        var node = root === document && shadowSweep ? walker.currentNode :
            root.nodeType === 1 ? root : walker.nextNode(), visited = 0;
        while (node && visited++ < 4096) {
            if (node.shadowRoot && !shadowObservers.has(node.shadowRoot)) {
                var shadow = node.shadowRoot;
                var watcher = new MutationObserver(processMutations);
                watcher.observe(shadow, {childList: true, subtree: true,
                    attributes: true, attributeFilter: ['src']});
                shadow.addEventListener('play', onPlay, true);
                shadowObservers.set(shadow, {observer: watcher, host: node});
                scan(shadow);
            }
            node = walker.nextNode();
        }
        if (root === document) shadowSweep = node ? walker : null;
    }
    function onPlay(event) { watch(event.target); }
    function processMutations(mutations) {
        videos.forEach(function (record) { if (!record.video.isConnected) unwatch(record); });
        shadowObservers.forEach(function (entry, root) {
            if (entry.host.isConnected) return;
            entry.observer.disconnect(); root.removeEventListener('play', onPlay, true);
            shadowObservers.delete(root);
        });
        mutations.forEach(function (mutation) {
            if (mutation.type === 'attributes') {
                var target = mutation.target;
                watch(target.tagName === 'SOURCE' ? target.parentElement : target);
            }
            for (var i = 0; i < mutation.addedNodes.length; i++) {
                var added = mutation.addedNodes[i];
                if (added.nodeType === 1) scan(added);
            }
        });
        schedule();
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
        startHeartbeat();
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
                var reply = {type: 'assistantPressReady', documentId: documentId, navigation: binding.navigation,
                    token: binding.token, version: data.version, request: data.request, ok: data.ok};
                if (data.ok && Number.isFinite(data.pressX) && Number.isFinite(data.pressY)) {
                    reply.pressX = data.pressX; reply.pressY = data.pressY;
                }
                send(reply);
            },
            frameCandidate: function (candidate) {
                if (window === window.top) return;
                var message = {miowserVideoFrame: true, documentId: documentId, active: !!candidate};
                if (candidate) Object.keys(candidate).forEach(function (key) { message[key] = candidate[key]; });
                window.parent.postMessage(message, '*');
            },
            prepareExternal: function (document, elementId, version, request) {
                if (!frameRoutes.has(document)) {
                    if (assistant) assistant.externalPressReady(document, elementId, version, request, false);
                } else routeFrame(document, {miowserVideoPrepare: true, documentId: document,
                    elementId: elementId, version: version, request: request});
            },
            cancelExternal: function (document, request) {
                routeFrame(document, {miowserVideoCancel: true, documentId: document,
                    version: 1, request: request});
            },
            lease: function (version) {
                if (binding) send({type: 'assistantLease', documentId: documentId,
                    navigation: binding.navigation, token: binding.token, version: version});
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
        observer = new MutationObserver(processMutations);
        observer.observe(document.documentElement || document, {childList: true, subtree: true,
            attributes: true, attributeFilter: ['src']});
        scan(document);
        listen(document, 'DOMContentLoaded', function () { scan(document); connect(); });
        // A late-starting player must not be permanently crowded out by eight idle previews.
        listen(document, 'play', onPlay, true);
        listen(document, 'visibilitychange', visibility);
        listen(window, 'message', onFrameMessage);
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
        shadowObservers.forEach(function (entry, root) {
            entry.observer.disconnect(); root.removeEventListener('play', onPlay, true);
        });
        shadowObservers.clear();
        shadowSweep = null;
        if (intersection) intersection.disconnect();
        if (resize) resize.disconnect();
        observer = intersection = resize = null;
        videos.forEach(function (record) { record.cleanups.forEach(function (cleanup) { cleanup(); }); });
        videos.clear();
        frameRoutes.forEach(function (route) { clearTimeout(route.timeout); });
        frameRoutes.clear();
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
            if (message.type === 'companionEnter') {
                if (assistant && typeof message.elementId === 'string') assistant.companionEnter(message.elementId);
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
                    if (resumed && message.paused === true) owner.video.pause();
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
