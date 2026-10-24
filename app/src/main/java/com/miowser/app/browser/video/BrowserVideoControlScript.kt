package com.miowser.app.browser.video

/** Only fixed media actions on the currently owned fullscreen element; no generic script/URL API. */
internal const val BROWSER_VIDEO_CONTROL_SCRIPT = """
(function () {
    'use strict';
    window.__miowserCreateVideoControls = function (api) {
        var lastCommand = 0, seekCleanup = null, disposed = false, heldRate = null;
        function releaseRate() {
            var held = heldRate;
            heldRate = null;
            if (!held) return;
            try {
                // Do not undo a provider's subsequent rate/source change.
                if (held.video.currentSrc === held.source && held.video.srcObject === held.object &&
                    Math.abs(held.video.playbackRate - 2) < 0.01) held.video.playbackRate = held.rate;
            } catch (ignored) {}
        }
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
            if (['play', 'pause', 'seek', 'skip', 'rate', 'richUi', 'snapshot', 'mute', 'scale', 'caption', 'audio', 'holdRate', 'releaseRate'].indexOf(command.action) < 0) return;
            lastCommand = command.commandId;
            try {
                switch (command.action) {
                    case 'holdRate':
                        if (!heldRate) heldRate = {video: video, rate: video.playbackRate, source: video.currentSrc, object: video.srcObject};
                        video.playbackRate = 2; ack(Math.abs(video.playbackRate - 2) < 0.01); break;
                    case 'releaseRate': releaseRate(); ack(true); break;
                    case 'play': {
                        var result = video.play();
                        api.reply('controlStarted', command, true);
                        if (result && result.then) result.then(function () { ack(true); }, function () { ack(false); });
                        else ack(!video.paused);
                        break;
                    }
                    case 'pause': video.pause(); ack(video.paused); break;
                    case 'mute':
                        if (value !== 0 && value !== 1) { ack(false); return; }
                        video.muted = value === 1; ack(video.muted === (value === 1)); break;
                    case 'scale': {
                        if (!Number.isInteger(value) || value < 0 || value > 2) { ack(false); return; }
                        var fit = ['contain', 'cover', 'fill'][value];
                        video.style.setProperty('object-fit', fit, 'important');
                        ack(window.getComputedStyle(video).objectFit === fit); break;
                    }
                    case 'caption': case 'audio': {
                        if (!Number.isSafeInteger(value)) { ack(false); return; }
                        var audio = command.action === 'audio', tracks = audio ? video.audioTracks : video.textTracks;
                        if (!tracks) { ack(false); return; }
                        var target = null;
                        for (var t = 0; t < Math.min(tracks.length, 128); t++) {
                            var candidate = tracks[t];
                            if (!audio && candidate.kind !== 'captions' && candidate.kind !== 'subtitles') continue;
                            if (api.trackId(candidate) === value) target = candidate;
                        }
                        if (!target && !(value === -1 && !audio)) { ack(false); return; }
                        if (target) { if (audio) target.enabled = true; else target.mode = 'showing'; }
                        for (var j = 0; j < Math.min(tracks.length, 128); j++) {
                            var other = tracks[j];
                            if (other === target) continue;
                            if (audio) other.enabled = false;
                            else if (other.kind === 'captions' || other.kind === 'subtitles') other.mode = 'disabled';
                        }
                        ack(!target || (audio ? target.enabled : target.mode === 'showing')); break;
                    }
                    case 'rate':
                        if (!Number.isFinite(value) || value < 0.25 || value > 3) { ack(false); return; }
                        heldRate = null; // An explicit speed selection supersedes a temporary hold.
                        video.playbackRate = value;
                        ack(Math.abs(video.playbackRate - value) < 0.01);
                        break;
                    case 'richUi':
                        if (value !== 1) releaseRate();
                        ack(api.rich(command.elementId, command.attempt, value === 1)); break;
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
        return {receive: receive, release: function () { releaseRate(); if (seekCleanup) seekCleanup(); }, destroy: function () {
            disposed = true;
            releaseRate();
            if (seekCleanup) seekCleanup();
            seekCleanup = null;
        }};
    };
})();
"""
