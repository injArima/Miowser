package com.miowser.app.browser.video

/** Only fixed media actions on the currently owned fullscreen element; no generic script/URL API. */
internal const val BROWSER_VIDEO_CONTROL_SCRIPT = """
(function () {
    'use strict';
    window.__miowserCreateVideoControls = function (api) {
        var lastCommand = 0, seekCleanup = null, disposed = false;
        function receive(command) {
            if (disposed || !Number.isSafeInteger(command.commandId) || command.commandId <= lastCommand) return;
            var owner = api.owner();
            if (!owner || owner.record.id !== command.elementId || owner.attempt !== command.attempt ||
                !owner.record.video.isConnected || document.fullscreenElement !== owner.record.video) return;
            var video = owner.record.video, finished = false;
            function ack(ok) {
                if (finished || disposed) return;
                finished = true;
                api.reply('controlAck', command, ok);
            }
            var value = command.value;
            if (['play', 'pause', 'seek', 'skip', 'rate', 'richUi', 'snapshot'].indexOf(command.action) < 0) return;
            lastCommand = command.commandId;
            try {
                switch (command.action) {
                    case 'play': {
                        var result = video.play();
                        api.reply('controlStarted', command, true);
                        if (result && result.then) result.then(function () { ack(true); }, function () { ack(false); });
                        else ack(!video.paused);
                        break;
                    }
                    case 'pause': video.pause(); ack(video.paused); break;
                    case 'rate':
                        if (!Number.isFinite(value) || value < 0.25 || value > 3) { ack(false); return; }
                        video.playbackRate = value;
                        ack(Math.abs(video.playbackRate - value) < 0.01);
                        break;
                    case 'richUi': ack(api.rich(command.elementId, command.attempt, value === 1)); break;
                    case 'snapshot': ack(true); break;
                    case 'seek': case 'skip': {
                        if (!Number.isFinite(value) || (command.action === 'skip' && Math.abs(value) > 60)) { ack(false); return; }
                        var target = command.action === 'skip' ? video.currentTime + value : value;
                        var ranges = video.seekable;
                        if (ranges.length > 0) {
                            var closest = null, distance = Infinity;
                            for (var i = 0; i < Math.min(ranges.length, 64); i++) {
                                var start = ranges.start(i), end = ranges.end(i);
                                if (target >= start && target <= end) { closest = target; break; }
                                [start, end].forEach(function (point) {
                                    if (Math.abs(point - target) < distance) { closest = point; distance = Math.abs(point - target); }
                                });
                            }
                            target = closest;
                        } else if (Number.isFinite(video.duration) && video.duration > 0 && video.readyState >= 1) {
                            // Some WebViews report no seekable ranges for otherwise seekable VOD.
                            target = Math.max(0, Math.min(target, video.duration));
                        } else { ack(false); return; }
                        if (target === null || target < 0 || target > 31536000) { ack(false); return; }
                        if (seekCleanup) seekCleanup();
                        var timeout = null;
                        function cleanup() {
                            video.removeEventListener('seeked', settled);
                            if (timeout !== null) clearTimeout(timeout);
                            if (seekCleanup === cancel) seekCleanup = null;
                        }
                        function settled() { cleanup(); ack(!video.seeking && Math.abs(video.currentTime - target) <= 2); }
                        function cancel() { cleanup(); ack(false); }
                        seekCleanup = cancel;
                        video.addEventListener('seeked', settled);
                        timeout = setTimeout(settled, 7000);
                        video.currentTime = target;
                        api.reply('controlStarted', command, true);
                        if (!video.seeking && Math.abs(video.currentTime - target) <= 0.5) settled();
                        break;
                    }
                }
            } catch (ignored) {
                if (command.action === 'seek' || command.action === 'skip') {
                    if (seekCleanup) seekCleanup();
                }
                ack(false);
            }
        }
        return {receive: receive, release: function () { if (seekCleanup) seekCleanup(); }, destroy: function () {
            disposed = true;
            if (seekCleanup) seekCleanup();
            seekCleanup = null;
        }};
    };
})();
"""
