package com.miowser.app.browser.video

/** Frame-local UI: a trusted DOM click can enter fullscreen without moving the media source. */
internal const val VIDEO_ASSISTANT_BUTTON_SCRIPT = """
(function () {
    'use strict';
    window.__miowserCreateVideoButton = function (api) {
        var policy = {enabled: false}, host = null, button = null, icon = null, insetProbe = null;
        var selected = null, frame = null, animation = null, disposed = false, pending = false;
        var restore = null, entryTimeout = null;
        function cancelEntryTimeout() {
            if (entryTimeout !== null) clearTimeout(entryTimeout);
            entryTimeout = null;
        }

        function setStyles(element, styles) {
            Object.keys(styles).forEach(function (key) { element.style.setProperty(key, styles[key]); });
        }
        function hide() {
            selected = null;
            if (animation) { animation.cancel(); animation = null; }
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
                var style = window.getComputedStyle(parent), box = parent.getBoundingClientRect();
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
        function restoreControls() {
            if (!restore) return;
            cancelEntryTimeout();
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
                objectFit: video.style ? video.style.getPropertyValue('object-fit') : undefined,
                fitPriority: video.style ? video.style.getPropertyPriority('object-fit') : ''};
            var entry = restore;
            entryTimeout = setTimeout(function () {
                if (restore === entry && document.fullscreenElement !== entry.video) failed(entry);
            }, 8000);
            // Keep browser controls until the native rich overlay confirms it is attached.
            video.controls = true;
            hide();
            try {
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
            button.appendChild(icon);
            button.addEventListener('click', click);
            button.addEventListener('pointerdown', function (event) { event.stopPropagation(); });
            button.addEventListener('focus', function () { button.style.setProperty('outline', '2px solid currentColor'); });
            button.addEventListener('blur', function () { button.style.setProperty('outline', 'none'); });
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
            makeButton();
            if (!host.isConnected) document.documentElement.appendChild(host);
            var size = 48 / view.scale, gap = 8 / view.scale;
            var insets = window.getComputedStyle(insetProbe);
            var left = Math.max(bestBox.left, view.left + (parseFloat(insets.paddingLeft) || 0));
            var top = Math.max(bestBox.top, view.top + (parseFloat(insets.paddingTop) || 0));
            var right = Math.min(bestBox.right, view.left + view.width - (parseFloat(insets.paddingRight) || 0));
            var bottom = Math.min(bestBox.bottom, view.top + view.height - (parseFloat(insets.paddingBottom) || 0));
            if (right - left < size + gap * 2 || bottom - top < size + gap * 2) { hide(); return; }
            var appearing = button.hidden;
            setStyles(button, {'left': (right - gap - size) + 'px', 'top': (top + gap) + 'px',
                'width': size + 'px', 'height': size + 'px', 'border-radius': (16 / view.scale) + 'px',
                'background-color': policy.background, 'color': policy.foreground,
                'visibility': 'visible', 'pointer-events': 'auto'});
            setStyles(icon, {'width': (28 / view.scale) + 'px', 'height': (28 / view.scale) + 'px'});
            selected = best;
            button.hidden = false; button.disabled = false;
            var reduced = policy.reducedMotion || (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
            if (appearing && !reduced && button.animate) animation = button.animate(
                [{opacity: 0}, {opacity: 1}], {duration: 140, easing: 'ease-out'});
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
            } else if (restore) {
                api.fullscreen(restore.record, restore.attempt, false);
                pending = false; restoreControls();
            }
            schedule();
        }
        document.addEventListener('fullscreenchange', fullscreenChanged);
        return {
            policy: function (next) {
                policy = {enabled: next && next.enabled === true,
                    background: next && /^#[0-9a-f]{6}$/i.test(next.background) ? next.background : '#303038',
                    foreground: next && /^#[0-9a-f]{6}$/i.test(next.foreground) ? next.foreground : '#FFFFFF',
                    reducedMotion: next && next.reducedMotion === true};
                schedule();
            },
            schedule: schedule,
            owner: function () {
                return restore && document.fullscreenElement === restore.video ? restore : null;
            },
            rich: function (elementId, attempt, attached) {
                if (!restore || restore.record.id !== elementId || restore.attempt !== attempt ||
                    document.fullscreenElement !== restore.video || !restore.video.isConnected) return false;
                restore.video.controls = !attached;
                return true;
            },
            fallback: function () {
                if (restore && document.fullscreenElement === restore.video) restore.video.controls = true;
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
                cancelEntryTimeout();
                if (frame !== null) window.cancelAnimationFrame(frame);
                frame = null;
                hide(); restoreControls();
                document.removeEventListener('fullscreenchange', fullscreenChanged);
                if (host) host.remove();
                host = button = icon = insetProbe = null;
            }
        };
    };
})();
"""
