package com.miowser.app.browser.video

/** Frame-local UI: a trusted DOM click can enter fullscreen without moving the media source. */
internal const val VIDEO_ASSISTANT_BUTTON_SCRIPT = """
(function () {
    'use strict';
    window.__miowserCreateVideoButton = function (api) {
        var policy = {enabled: false}, host = null, button = null, plate = null, icon = null, insetProbe = null;
        var selected = null, frame = null, animation = null, disposed = false, pending = false;
        var restore = null, entryTimeout = null;
        var autoTimer = null, fadeTimer = null, wakeTimer = null, layoutTimers = [];
        var candidate = null, candidateRevision = -1, suppressed = false, scrolling = false, readyAt = 0;
        var displayVersion = 0, displayKey = '', nativeReady = false, focused = false;
        var pressGuard = null, pressTimer = null;
        function cancelPress(rearm) {
            if (pressTimer !== null) clearTimeout(pressTimer); pressTimer = null;
            pressGuard = null;
            if (rearm && selected && button && !button.hidden) armAuto();
        }
        function capturePress(event) {
            var guard = pressGuard;
            if (!guard || !event.isTrusted) return;
            var point = event.changedTouches && event.changedTouches[0] || event;
            if (!(point.clientX >= guard.left - 1 && point.clientX <= guard.right + 1 &&
                point.clientY >= guard.top - 1 && point.clientY <= guard.bottom + 1)) {
                cancelPress(true); return;
            }
            // Installed at document start, before site handlers. Only a native-owned,
            // bound physical gesture at this target can use this short-lived guard.
            // Block site handlers/default link navigation even if its DOM hit changed.
            event.stopImmediatePropagation();
            if (event.type === 'click') {
                event.preventDefault(); cancelPress(false); click(event);
            }
        }
        var guardedEvents = ['pointerdown', 'pointerup', 'touchstart', 'touchend', 'mousedown', 'mouseup', 'click'];
        guardedEvents.forEach(function (type) { window.addEventListener(type, capturePress, {capture: true, passive: false}); });
        function reduced() {
            return policy.reducedMotion || (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
        }
        function cancelAuto() { if (autoTimer !== null) clearTimeout(autoTimer); autoTimer = null; }
        function cancelFade() {
            if (fadeTimer !== null) clearTimeout(fadeTimer); fadeTimer = null;
            if (animation) animation.cancel(); animation = null;
        }
        function nativeDisplay(visible, fade, x, y, size, view) {
            if (!policy.nativeDisplay || !api.display) return;
            var data = {visible: visible, fade: !!fade};
            if (visible) {
                data.left = (x - view.left) / view.width; data.top = (y - view.top) / view.height;
                data.width = size / view.width; data.height = size / view.height;
                data.viewportWidth = view.width; data.viewportHeight = view.height;
            }
            var key = JSON.stringify(data) + policy.background + policy.foreground + policy.reducedMotion;
            if (key === displayKey) return;
            displayKey = key; data.version = ++displayVersion; nativeReady = false;
            if (visible && button) { cancelFade(); button.style.setProperty('opacity', '1'); }
            api.display(data);
        }
        function expire() {
            autoTimer = null;
            if (!button || button.hidden || !selected || focused) return;
            var paintedNatively = nativeReady;
            suppressed = true; selected = null;
            button.disabled = true; button.style.setProperty('pointer-events', 'none');
            cancelFade();
            nativeDisplay(false, !reduced());
            if (reduced()) { button.hidden = true; button.style.setProperty('visibility', 'hidden'); return; }
            if (!paintedNatively && button.animate) animation = button.animate(
                [{opacity: 1}, {opacity: 0}], {duration: 300, easing: 'ease-in', fill: 'forwards'});
            fadeTimer = setTimeout(function () {
                fadeTimer = null;
                if (suppressed && button) { button.hidden = true; button.style.setProperty('visibility', 'hidden'); }
            }, 300);
        }
        function armAuto() { cancelAuto(); autoTimer = setTimeout(expire, 3000); }
        function wake(event) {
            if (event && (!event.isTrusted || event.target === host)) return;
            if (!policy.enabled || disposed) return;
            if (wakeTimer !== null) clearTimeout(wakeTimer);
            if (suppressed || !selected) {
                cancelFade();
                if (button) { button.hidden = true; button.disabled = true; }
            }
            cancelAuto(); suppressed = false; readyAt = performance.now() + 200;
            wakeTimer = setTimeout(function () {
                wakeTimer = null; readyAt = 0; schedule();
                if (selected && button && !button.hidden) armAuto();
            }, 200);
            schedule();
        }
        function scroll() {
            layoutTimers.forEach(clearTimeout); layoutTimers = [];
            if (wakeTimer !== null) clearTimeout(wakeTimer);
            hide(); scrolling = true;
            wakeTimer = setTimeout(function () {
                wakeTimer = null; scrolling = false; readyAt = 0; schedule();
            }, 200);
        }
        function layoutChanged() {
            scroll();
            [400, 600, 800, 1000].forEach(function (delay) { layoutTimers.push(setTimeout(schedule, delay)); });
        }
        function cancelEntryTimeout() {
            if (entryTimeout !== null) clearTimeout(entryTimeout);
            entryTimeout = null;
        }
        function cancelEntryPlayback() {
            var entry = restore, recovery = entry && entry.playbackRecovery;
            if (!recovery) return;
            entry.playbackRecovery = null;
            recovery.events.forEach(function (type) { entry.video.removeEventListener(type, recovery.schedule); });
            document.removeEventListener('visibilitychange', recovery.visibility);
            if (recovery.frame !== null) window.cancelAnimationFrame(recovery.frame);
            if (recovery.timeout !== null) clearTimeout(recovery.timeout);
            if (recovery.settle !== null) clearTimeout(recovery.settle);
        }
        function prepareEntryPlayback(entry) {
            if (!entry.wasPlaying || (!entry.source && !entry.object)) return;
            var recovery = {frame: null, timeout: null, settle: null, inFlight: false, attempts: 0,
                events: ['pause', 'emptied', 'loadstart', 'loadedmetadata', 'loadeddata', 'canplay', 'seeked', 'playing', 'ended', 'error']};
            entry.playbackRecovery = recovery;
            function active() { return !disposed && restore === entry && entry.playbackRecovery === recovery; }
            function attempt() {
                recovery.frame = null;
                if (!active()) return;
                var video = entry.video, source = video.currentSrc || video.src || '';
                if (!api.bound() || !video.isConnected || document.visibilityState === 'hidden' || video.ended || video.error ||
                    (video.srcObject || null) !== entry.object || (source && source !== entry.source)) {
                    cancelEntryPlayback(); return;
                }
                // Fullscreen surface handoff may temporarily empty and then restore the
                // same media. Wait for its data/seek to settle; never reload or replace it.
                if (video.paused || video.readyState < 2 || video.seeking) {
                    if (recovery.settle !== null) clearTimeout(recovery.settle);
                    recovery.settle = null;
                }
                if (document.fullscreenElement !== video || video.readyState < 2 || video.seeking || recovery.inFlight) return;
                if (!video.paused) {
                    // Healthy playback needs no play() call. Stop observing as soon as
                    // the entry has stayed playable for a brief surface-settling window.
                    if (recovery.settle === null) recovery.settle = setTimeout(function () {
                        recovery.settle = null;
                        if (active() && !video.paused && video.readyState >= 2 && !video.seeking) cancelEntryPlayback();
                    }, 350);
                    return;
                }
                recovery.inFlight = true; recovery.attempts++;
                try {
                    var result = video.play();
                    if (result && result.then) result.then(function () {
                        if (active()) { cancelEntryPlayback(); api.interacted(); }
                    }, function (error) {
                        if (!active()) return;
                        recovery.inFlight = false;
                        // Only a reload-aborted attempt may be retried on a later media
                        // event. Permission/DRM failures keep the existing manual controls.
                        if (!error || error.name !== 'AbortError' || recovery.attempts >= 3) cancelEntryPlayback();
                    });
                    else { cancelEntryPlayback(); api.interacted(); }
                } catch (ignored) { cancelEntryPlayback(); }
            }
            recovery.schedule = function () {
                if (active() && recovery.frame === null) recovery.frame = window.requestAnimationFrame(attempt);
            };
            recovery.visibility = function () { if (document.visibilityState === 'hidden') cancelEntryPlayback(); };
            recovery.events.forEach(function (type) { entry.video.addEventListener(type, recovery.schedule); });
            document.addEventListener('visibilitychange', recovery.visibility);
            // One entry window, not an ongoing autoplay policy. Explicit transport/lifecycle
            // actions cancel this immediately, even while the media is still loading.
            recovery.timeout = setTimeout(function () { if (active()) cancelEntryPlayback(); }, 8000);
        }

        function setStyles(element, styles) {
            Object.keys(styles).forEach(function (key) { element.style.setProperty(key, styles[key]); });
        }
        function hide() {
            cancelPress(false);
            selected = null;
            focused = false;
            candidate = null; candidateRevision = -1; suppressed = false;
            cancelAuto(); cancelFade(); nativeDisplay(false, false);
            if (button) {
                button.hidden = true; button.disabled = true;
                setStyles(button, {'visibility': 'hidden', 'pointer-events': 'none'});
            }
        }
        function viewport() {
            var visual = window.visualViewport;
            return {left: visual ? visual.offsetLeft : 0, top: visual ? visual.offsetTop : 0,
                width: visual ? visual.width : window.innerWidth,
                height: visual ? visual.height : window.innerHeight,
                scale: visual && visual.scale > 0 ? visual.scale : 1};
        }
        function intersect(a, b) {
            return {left: Math.max(a.left, b.left), top: Math.max(a.top, b.top),
                right: Math.min(a.right, b.right), bottom: Math.min(a.bottom, b.bottom)};
        }
        function clip(record, view) {
            var video = record.video, rect = video.getBoundingClientRect();
            var result = intersect({left: rect.left, top: rect.top, right: rect.left + rect.width,
                bottom: rect.top + rect.height}, {left: view.left, top: view.top,
                right: view.left + view.width, bottom: view.top + view.height});
            // IntersectionObserver can account for clipping outside a cross-origin frame.
            var seen = record.intersectionBounds, visible = record.intersectionRect;
            if (visible && seen && Math.abs(seen.left - rect.left) < 1 && Math.abs(seen.top - rect.top) < 1 &&
                Math.abs(seen.width - rect.width) < 1 && Math.abs(seen.height - rect.height) < 1) {
                result = intersect(result, visible);
            }
            // Bound work to this video's ancestors; never scan the page for layout candidates.
            var parent = video.parentElement;
            for (var i = 0; parent && parent !== document.documentElement && i < 16; i++, parent = parent.parentElement) {
                // Root/body overflow can be propagated to the viewport (CSS Overflow),
                // even when BODY's own box has zero height, as on YouTube. The viewport
                // intersection above already handles that scrollport; it is not a local clip.
                if (parent === document.scrollingElement) continue;
                var style = window.getComputedStyle(parent);
                if (parent.tagName === 'BODY' && parent.parentElement === document.documentElement) {
                    var rootStyle = window.getComputedStyle(document.documentElement);
                    if (rootStyle.overflowX === 'visible' && rootStyle.overflowY === 'visible' &&
                        (!rootStyle.contain || rootStyle.contain === 'none') &&
                        (!style.contain || style.contain === 'none') && style.display !== 'none') continue;
                }
                var box = parent.getBoundingClientRect();
                if (/hidden|clip|auto|scroll/.test(style.overflowX)) {
                    result.left = Math.max(result.left, box.left); result.right = Math.min(result.right, box.right);
                }
                if (/hidden|clip|auto|scroll/.test(style.overflowY)) {
                    result.top = Math.max(result.top, box.top); result.bottom = Math.min(result.bottom, box.bottom);
                }
            }
            return result;
        }
        function eligible(record) {
            var video = record.video;
            if (!video.isConnected || video.paused || video.ended || video.readyState < 2 ||
                !(video.videoWidth > 0) || !(video.videoHeight > 0) || record.ratio <= 0 ||
                typeof video.requestFullscreen !== 'function') return false;
            var style = window.getComputedStyle(video);
            return style.display !== 'none' && style.visibility !== 'hidden' && style.visibility !== 'collapse' &&
                Number(style.opacity) !== 0;
        }
        function clearControlGuard(entry) {
            var guard = entry && entry.controlGuard;
            if (!guard) return;
            entry.controlGuard = null;
            if (guard.observer) guard.observer.disconnect();
            if (guard.frame !== null) window.cancelAnimationFrame(guard.frame);
            if (guard.adopted) {
                guard.root.adoptedStyleSheets = Array.from(guard.root.adoptedStyleSheets).filter(function (sheet) { return sheet !== guard.sheet; });
            } else if (guard.sheet && guard.sheet.parentNode) guard.sheet.parentNode.removeChild(guard.sheet);
            else if (guard.sheet && guard.sheet.parentElement) guard.sheet.remove();
            if (guard.attribute === null) entry.video.removeAttribute('data-miowser-rich-controls');
            else entry.video.setAttribute('data-miowser-rich-controls', guard.attribute);
            if (guard.pointer) entry.video.style.setProperty('pointer-events', guard.pointer, guard.priority);
            else entry.video.style.removeProperty('pointer-events');
        }
        function hideBrowserControls(entry) {
            if (entry.controlGuard) { entry.video.controls = false; return; }
            var video = entry.video, token = 'm' + entry.attempt + Math.random().toString(36).slice(2);
            var root = video.getRootNode ? video.getRootNode() : document;
            var guard = {sheet: null, root: root, adopted: false, observer: null, frame: null,
                attribute: video.getAttribute('data-miowser-rich-controls'),
                pointer: video.style.getPropertyValue('pointer-events'),
                priority: video.style.getPropertyPriority('pointer-events')};
            entry.controlGuard = guard;
            video.setAttribute('data-miowser-rich-controls', token);
            // Fullscreen video can force Chromium controls even with controls=false.
            // Target only this entry, including its UA shadow controls; never hide text-track cues.
            var selector = 'video[data-miowser-rich-controls="' + token + '"]';
            var css = ['-webkit-media-controls', '-webkit-media-controls-enclosure',
                '-webkit-media-controls-panel', '-webkit-media-controls-overlay-enclosure',
                '-webkit-media-controls-overlay-play-button', '-webkit-media-controls-play-button',
                '-webkit-media-controls-timeline', '-webkit-media-controls-current-time-display',
                '-webkit-media-controls-time-remaining-display', '-webkit-media-controls-mute-button',
                '-webkit-media-controls-volume-slider', '-webkit-media-controls-fullscreen-button',
                '-internal-media-controls-overlay-cast-button'].map(function (pseudo) {
                    // Separate rules: an unsupported pseudo must not invalidate the other selectors.
                    return selector + '::' + pseudo + '{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important;}';
                }).join('\n');
            // CSSOM sheets also cover pages that disallow inline <style> through CSP.
            if (window.CSSStyleSheet && 'adoptedStyleSheets' in root) {
                try {
                    var compiled = new window.CSSStyleSheet();
                    compiled.replaceSync(css);
                    root.adoptedStyleSheets = Array.from(root.adoptedStyleSheets).concat([compiled]);
                    guard.sheet = compiled; guard.adopted = true;
                } catch (ignored) {}
            }
            if (!guard.adopted) {
                guard.sheet = document.createElement('style');
                guard.sheet.textContent = css;
                (root.head || root.documentElement || root).appendChild(guard.sheet);
            }
            video.style.setProperty('pointer-events', 'none', 'important');
            video.controls = false;
            guard.observer = new MutationObserver(function () {
                if (restore !== entry || !entry.controlGuard || !video.controls || guard.frame !== null) return;
                // One local write per animation frame, even if a provider re-enables controls.
                guard.frame = window.requestAnimationFrame(function () {
                    guard.frame = null;
                    if (restore === entry && entry.controlGuard && video.controls) video.controls = false;
                });
            });
            guard.observer.observe(video, {attributes: true, attributeFilter: ['controls']});
        }
        function restoreControls() {
            if (!restore) return;
            cancelEntryPlayback();
            cancelEntryTimeout();
            clearControlGuard(restore);
            restore.video.controls = restore.controls;
            if (restore.video.style && restore.objectFit !== undefined) {
                if (restore.objectFit) restore.video.style.setProperty('object-fit', restore.objectFit, restore.fitPriority);
                else restore.video.style.removeProperty('object-fit');
            }
            restore = null;
        }
        function failed(entry) {
            if (disposed || restore !== entry) return;
            pending = false;
            restoreControls();
            api.failed();
            schedule();
        }
        function click(event) {
            // Page scripts cannot programmatically activate this action.
            if (!event.isTrusted || disposed || pending || !policy.enabled || !api.bound() ||
                document.fullscreenElement || !selected || !eligible(selected)) return;
            var view = viewport(), currentBox = clip(selected, view);
            if ((currentBox.right - currentBox.left) * view.scale < 120 ||
                (currentBox.bottom - currentBox.top) * view.scale < 80) { hide(); return; }
            event.preventDefault(); event.stopPropagation();
            var record = selected, video = record.video;
            record.interacted = true; record.lastInteraction = performance.now();
            api.interacted();
            pending = true;
            restore = {video: video, record: record, attempt: api.entering(record), controls: video.controls,
                wasPlaying: !video.paused && !video.ended, source: video.currentSrc || video.src || '', object: video.srcObject || null,
                objectFit: video.style ? video.style.getPropertyValue('object-fit') : undefined,
                fitPriority: video.style ? video.style.getPropertyPriority('object-fit') : ''};
            var entry = restore;
            prepareEntryPlayback(entry);
            entryTimeout = setTimeout(function () {
                if (restore === entry && document.fullscreenElement !== entry.video) failed(entry);
            }, 8000);
            hide();
            try {
                // Hide from the first fullscreen frame, before the native overlay attaches.
                hideBrowserControls(entry);
                // Must stay in this synchronous trusted click, before any promise/bridge roundtrip.
                var result = video.requestFullscreen();
                if (result && typeof result.catch === 'function') result.catch(function () { failed(entry); });
            } catch (ignored) { failed(entry); }
        }
        function makeButton() {
            if (host) return;
            host = document.createElement('div');
            host.setAttribute('data-miowser-video-assistant', '');
            setStyles(host, {'all': 'initial', 'position': 'fixed', 'left': '0', 'top': '0',
                'width': '0', 'height': '0', 'pointer-events': 'none', 'z-index': '2147483646'});
            var shadow = host.attachShadow({mode: 'closed'});
            button = document.createElement('button');
            button.type = 'button'; button.hidden = true;
            button.setAttribute('aria-label', 'Open video fullscreen with Miowser');
            button.title = 'Open video fullscreen with Miowser';
            setStyles(button, {'all': 'initial', 'box-sizing': 'border-box', 'position': 'fixed',
                'display': 'flex', 'align-items': 'center', 'justify-content': 'center',
                'pointer-events': 'none', 'visibility': 'hidden', 'cursor': 'pointer', 'border': '1px solid rgba(255,255,255,.2)',
                'box-shadow': '0 2px 8px rgba(0,0,0,.3)', 'outline-offset': '3px',
                'touch-action': 'manipulation', '-webkit-tap-highlight-color': 'transparent'});
            // Use the app's existing monochrome mark; no external icon/image request.
            icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            icon.setAttribute('viewBox', '0 0 280.21 280.21');
            icon.setAttribute('aria-hidden', 'true'); icon.setAttribute('focusable', 'false');
            ['M140.89,235.75c-52.74,0 -95.65,-42.91 -95.65,-95.65s42.91,-95.65 95.65,-95.65c3.31,0 6,2.69 6,6s-2.69,6 -6,6c-46.12,0 -83.65,37.52 -83.65,83.65s37.52,83.65 83.65,83.65 83.65,-37.52 83.65,-83.65c0,-3.31 2.69,-6 6,-6s6,2.69 6,6c0,52.74 -42.91,95.65 -95.65,95.65Z',
                'M184.46,131.72l-59.84,-34.55c-6.46,-3.73 -14.52,0.93 -14.52,8.39v69.1c0,7.45 8.07,12.11 14.52,8.39l59.84,-34.55c6.46,-3.73 6.46,-13.04 0,-16.77Z',
                'M180.15,56.26a7.53,7.53 0,1 1,-15.06 0a7.53,7.53 0,1 1,15.06 0Z',
                'M211.99,76.89a7.53,7.53 0,1 1,-15.06 0a7.53,7.53 0,1 1,15.06 0Z',
                'M232.43,108.8a7.53,7.53 0,1 1,-15.06 0a7.53,7.53 0,1 1,15.06 0Z'].forEach(function (data) {
                var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                path.setAttribute('d', data); path.setAttribute('fill', 'currentColor'); icon.appendChild(path);
            });
            setStyles(icon, {'pointer-events': 'none', 'display': 'block'});
            plate = document.createElement('span');
            setStyles(plate, {'all': 'initial', 'display': 'flex', 'align-items': 'center',
                'justify-content': 'center', 'pointer-events': 'none', 'border-radius': '50%',
                'box-shadow': '0 2px 6px rgba(0,0,0,.3)'});
            plate.appendChild(icon); button.appendChild(plate);
            button.addEventListener('click', click);
            button.addEventListener('pointerdown', function (event) { cancelAuto(); event.stopPropagation(); });
            button.addEventListener('pointerup', armAuto);
            button.addEventListener('pointercancel', armAuto);
            button.addEventListener('focus', function () {
                focused = true; cancelAuto(); cancelFade(); nativeDisplay(false, false);
                button.style.setProperty('opacity', '1'); button.style.setProperty('outline', '2px solid currentColor');
            });
            button.addEventListener('blur', function () {
                focused = false; button.style.setProperty('outline', 'none');
                if (selected && policy.enabled) { armAuto(); displayKey = ''; schedule(); }
            });
            shadow.appendChild(button);
            insetProbe = document.createElement('span');
            setStyles(insetProbe, {'all': 'initial', 'position': 'fixed', 'visibility': 'hidden',
                'pointer-events': 'none', 'padding-top': 'env(safe-area-inset-top, 0px)',
                'padding-right': 'env(safe-area-inset-right, 0px)', 'padding-bottom': 'env(safe-area-inset-bottom, 0px)',
                'padding-left': 'env(safe-area-inset-left, 0px)'});
            shadow.appendChild(insetProbe);
            document.documentElement.appendChild(host);
        }
        function update() {
            frame = null;
            if (disposed || !policy.enabled || !api.bound() || document.visibilityState === 'hidden' ||
                !document.fullscreenEnabled || document.fullscreenElement || pending) { hide(); return; }
            if (scrolling) return;
            var view = viewport(), best = null, bestBox = null, bestScore = -1, bestInteraction = -1;
            api.records().forEach(function (record) {
                if (!eligible(record)) return;
                var box = clip(record, view), width = box.right - box.left, height = box.bottom - box.top;
                if (width * view.scale < 120 || height * view.scale < 80) return;
                var interaction = record.lastInteraction || 0, score = width * height;
                if (interaction > bestInteraction || (interaction === bestInteraction && score > bestScore)) {
                    best = record; bestBox = box; bestScore = score; bestInteraction = interaction;
                }
            });
            if (!best) { hide(); return; }
            if (candidate !== best || candidateRevision !== best.sourceRevision) {
                candidate = best; candidateRevision = best.sourceRevision; suppressed = false;
            }
            if (suppressed || performance.now() < readyAt) return;
            makeButton();
            if (!host.isConnected) document.documentElement.appendChild(host);
            var size = 60 / view.scale, gap = 8 / view.scale;
            var insets = window.getComputedStyle(insetProbe);
            var left = view.left + (parseFloat(insets.paddingLeft) || 0);
            var top = view.top + (parseFloat(insets.paddingTop) || 0);
            var right = view.left + view.width - (parseFloat(insets.paddingRight) || 0);
            var bottom = view.top + view.height - (parseFloat(insets.paddingBottom) || 0);
            // Root-attached overlay: do not reserve page space or inherit the player's clip.
            // The local viewport also keeps a child frame's existing trusted target reachable.
            if (right - left < size || bottom - top < size) { hide(); return; }
            var x = Math.max(left, Math.min(Math.max(bestBox.right - size, bestBox.left), right - size));
            var y = Math.max(top, Math.min(bestBox.bottom + gap, bottom - size));
            if (policy.frameConstrained) {
                if (bestBox.right - bestBox.left < size + gap * 2 || bestBox.bottom - bestBox.top < size + gap * 2) {
                    hide(); return;
                }
                x = Math.max(left, Math.min(bestBox.right - gap - size, right - size));
                y = Math.max(top, bestBox.top + gap);
            }
            var appearing = button.hidden;
            setStyles(button, {'left': x + 'px', 'top': y + 'px',
                'width': size + 'px', 'height': size + 'px', 'border-radius': '50%',
                'background-color': 'transparent', 'border': 'none', 'box-shadow': 'none', 'color': policy.foreground,
                'visibility': 'visible', 'pointer-events': 'auto'});
            setStyles(plate, {'width': (48 / view.scale) + 'px', 'height': (48 / view.scale) + 'px',
                'background-color': policy.background});
            setStyles(icon, {'width': (28 / view.scale) + 'px', 'height': (28 / view.scale) + 'px'});
            selected = best;
            button.hidden = false; button.disabled = false;
            if (appearing) {
                cancelFade(); button.style.setProperty('opacity', '1'); armAuto();
                if (!reduced() && button.animate) animation = button.animate(
                    [{opacity: 0}, {opacity: 1}], {duration: 300, easing: 'ease-out'});
            }
            if (focused) nativeDisplay(false, false);
            else nativeDisplay(true, false, x, y, size, view);
        }
        function schedule() {
            if (disposed) return;
            if (!policy.enabled || !api.bound()) { hide(); return; }
            if (frame === null) frame = window.requestAnimationFrame(function () {
                try { update(); } catch (ignored) { hide(); }
            });
        }
        function fullscreenChanged() {
            if (restore && document.fullscreenElement === restore.video) {
                cancelEntryTimeout();
                api.fullscreen(restore.record, restore.attempt, true);
                if (restore.playbackRecovery) restore.playbackRecovery.schedule();
            } else if (restore) {
                api.fullscreen(restore.record, restore.attempt, false);
                pending = false; restoreControls();
            }
            schedule();
        }
        document.addEventListener('fullscreenchange', fullscreenChanged);
        return {
            cancelEntryPlayback: cancelEntryPlayback,
            wake: wake, scroll: scroll, layoutChanged: layoutChanged,
            nativeReady: function (version, ok) {
                if (version !== displayVersion || !policy.nativeDisplay || !selected || !button || button.hidden) return;
                nativeReady = ok === true;
                cancelFade(); button.style.setProperty('opacity', nativeReady ? '0' : '1');
            },
            nativeLost: function () {
                cancelPress(true);
                nativeReady = false; displayKey = '';
                if (button && selected && !button.hidden) button.style.setProperty('opacity', '1');
                schedule();
            },
            nativePress: function (version, request) {
                var ok = version === displayVersion && Number.isSafeInteger(request) && request > 0 &&
                    policy.nativeDisplay && nativeReady && selected && eligible(selected) &&
                    button && !button.hidden && !button.disabled && !pending && !document.fullscreenElement;
                cancelPress(false);
                if (ok) {
                    cancelAuto();
                    pressGuard = {request: request, left: parseFloat(button.style.left), top: parseFloat(button.style.top)};
                    pressGuard.right = pressGuard.left + parseFloat(button.style.width);
                    pressGuard.bottom = pressGuard.top + parseFloat(button.style.height);
                    pressTimer = setTimeout(function () { cancelPress(true); }, 1500);
                }
                if (api.pressReady) api.pressReady({version: version, request: request, ok: !!ok});
            },
            nativePressCancel: function (request) {
                if (pressGuard && pressGuard.request === request) cancelPress(true);
            },
            policy: function (next) {
                var enabledBefore = policy.enabled, nativeBefore = policy.nativeDisplay;
                policy = {enabled: next && next.enabled === true,
                    background: next && /^#[0-9a-f]{6}$/i.test(next.background) ? next.background : '#303038',
                    foreground: next && /^#[0-9a-f]{6}$/i.test(next.foreground) ? next.foreground : '#FFFFFF',
                    reducedMotion: next && next.reducedMotion === true,
                    nativeDisplay: next && next.nativeDisplay === true,
                    frameConstrained: next && next.frameConstrained === true};
                if (nativeBefore !== policy.nativeDisplay) {
                    nativeReady = false; displayKey = '';
                    if (button) button.style.setProperty('opacity', '1');
                }
                if (policy.enabled && !enabledBefore) wake();
                if (!policy.enabled) {
                    if (wakeTimer !== null) clearTimeout(wakeTimer); wakeTimer = null;
                    layoutTimers.forEach(clearTimeout); layoutTimers = []; scrolling = false;
                }
                schedule();
            },
            schedule: schedule,
            owner: function () {
                return restore && document.fullscreenElement === restore.video ? restore : null;
            },
            rich: function (elementId, attempt, attached) {
                if (!restore || restore.record.id !== elementId || restore.attempt !== attempt ||
                    document.fullscreenElement !== restore.video || !restore.video.isConnected) return false;
                if (!attached) cancelEntryPlayback();
                if (attached) hideBrowserControls(restore);
                else { clearControlGuard(restore); restore.video.controls = true; }
                return true;
            },
            fallback: function () {
                cancelEntryPlayback();
                if (restore && document.fullscreenElement === restore.video) {
                    clearControlGuard(restore); restore.video.controls = true;
                }
            },
            release: function (elementId, attempt) {
                if (!restore || restore.record.id !== elementId || restore.attempt !== attempt) return;
                var video = restore.video;
                pending = false; restoreControls();
                if (document.fullscreenElement === video && typeof document.exitFullscreen === 'function') {
                    try {
                        var result = document.exitFullscreen();
                        if (result && result.catch) result.catch(function () { video.controls = true; });
                    } catch (ignored) { video.controls = true; }
                } else if (document.fullscreenElement === video) video.controls = true;
                schedule();
            },
            destroy: function () {
                disposed = true;
                if (wakeTimer !== null) clearTimeout(wakeTimer); wakeTimer = null;
                layoutTimers.forEach(clearTimeout); layoutTimers = [];
                cancelEntryTimeout();
                if (frame !== null) window.cancelAnimationFrame(frame);
                frame = null;
                hide(); restoreControls();
                document.removeEventListener('fullscreenchange', fullscreenChanged);
                guardedEvents.forEach(function (type) { window.removeEventListener(type, capturePress, true); });
                if (host) host.remove();
                host = button = plate = icon = insetProbe = null;
            }
        };
    };
})();
"""
