package com.miowser.app.browser.video

/** Frame-local UI: a trusted DOM click can enter fullscreen without moving the media source. */
internal const val VIDEO_ASSISTANT_BUTTON_SCRIPT = """
(function () {
    'use strict';
    window.__miowserCreateVideoButton = function (api) {
        var policy = {enabled: false}, host = null, button = null, icon = null, insetProbe = null;
        var selected = null, frame = null, animation = null, disposed = false, pending = false;
        var restore = null, entryTimeout = null;
        var rowAnchor = null, rowRecord = null, failedRecord = null, rowAttempts = 0;
        var offered = null, offeredMode = 'none', armed = null, armTimeout = null, attemptRecord = null;
        function availability(record, mode) {
            if (offered === record && offeredMode === mode) return;
            if (offered) offered.assistantAction = 'none';
            offered = record; offeredMode = mode;
            if (record) record.assistantAction = mode;
            api.interacted();
        }
        function cancelArm() {
            armed = null;
            if (armTimeout !== null) clearTimeout(armTimeout);
            armTimeout = null;
        }
        function removeRow() {
            if (host && host.parentElement) host.remove();
            rowAnchor = rowRecord = null;
        }
        function cancelEntryTimeout() {
            if (entryTimeout !== null) clearTimeout(entryTimeout);
            entryTimeout = null;
        }

        function setStyles(element, styles) {
            Object.keys(styles).forEach(function (key) { element.style.setProperty(key, styles[key]); });
        }
        function hide() {
            selected = null;
            availability(null, 'none');
            cancelArm();
            if (rowRecord && !rowRecord.video.isConnected) removeRow();
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
        function enter(event, record) {
            // Page scripts cannot programmatically activate this action.
            if (!event.isTrusted || disposed || pending || !policy.enabled || !api.bound() ||
                !document.fullscreenEnabled || document.visibilityState === 'hidden' ||
                document.fullscreenElement || !record || !eligible(record)) return;
            var view = viewport(), currentBox = clip(record, view);
            if ((currentBox.right - currentBox.left) * view.scale < 120 ||
                (currentBox.bottom - currentBox.top) * view.scale < 80) { hide(); return; }
            event.preventDefault(); event.stopPropagation();
            var video = record.video;
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
            hide();
            try {
                // Hide from the first fullscreen frame, before the native overlay attaches.
                hideBrowserControls(entry);
                // Must stay in this synchronous trusted click, before any promise/bridge roundtrip.
                var result = video.requestFullscreen();
                if (result && typeof result.catch === 'function') result.catch(function () { failed(entry); });
            } catch (ignored) { failed(entry); }
            return true;
        }
        function click(event) { enter(event, selected); }
        function toolbarTap(event) {
            if (!armed || !event.isTrusted) return;
            var intent = armed, record = intent.record;
            api.refreshSource(record);
            if (!eligible(record) || record.sourceRevision !== intent.revision) { cancelArm(); return; }
            var box = clip(record, viewport());
            if (event.clientX < box.left || event.clientX > box.right ||
                event.clientY < box.top || event.clientY > box.bottom) return;
            // Do not consume links, form controls or the website's explicit player buttons.
            var target = event.target;
            if (target && target.closest && target.closest('a,button,input,select,textarea,[role="button"]')) return;
            cancelArm();
            if (enter(event, record) && event.stopImmediatePropagation) event.stopImmediatePropagation();
        }
        function makeButton() {
            if (host) return;
            host = document.createElement('div');
            host.setAttribute('data-miowser-video-assistant', '');
            // A normal-flow row reserves space; never move/wrap the video or cover page text.
            setStyles(host, {'all': 'initial', 'display': 'block', 'position': 'relative',
                'box-sizing': 'border-box', 'clear': 'both', 'pointer-events': 'none'});
            var shadow = host.attachShadow({mode: 'closed'});
            button = document.createElement('button');
            button.type = 'button'; button.hidden = true;
            button.setAttribute('aria-label', 'Open video fullscreen with Miowser');
            button.title = 'Open video fullscreen with Miowser';
            setStyles(button, {'all': 'initial', 'box-sizing': 'border-box', 'position': 'absolute',
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
        }
        function placeRow(record, view) {
            if (attemptRecord !== record) { attemptRecord = record; failedRecord = null; rowAttempts = 0; }
            // A child frame cannot reserve space in its embedding page. Keep its DOM untouched.
            if (window.top !== window) return false;
            var video = record.video, rect = video.getBoundingClientRect();
            var size = 48 / view.scale, gap = 8 / view.scale, height = size + gap * 2;
            if (rowRecord !== record || !rowAnchor || !rowAnchor.isConnected ||
                !host || host.parentElement !== rowAnchor.parentElement) {
                removeRow();
                if (failedRecord === record) return false;
                // Stop retrying if a provider repeatedly removes the row during re-rendering.
                if (++rowAttempts > 3) { failedRecord = record; return false; }
                var anchor = video, parent = null;
                for (var i = 0; anchor && i < 12; i++, anchor = anchor.parentElement) {
                    var candidate = anchor.parentElement;
                    if (!candidate || !/^(BODY|DIV|MAIN|SECTION|ARTICLE)$/.test(candidate.tagName)) continue;
                    var a = anchor.getBoundingClientRect(), style = window.getComputedStyle(anchor);
                    var flow = window.getComputedStyle(candidate);
                    if (Math.abs(a.top - rect.top) > 24 || Math.abs(a.top + a.height - rect.top - rect.height) > 24 ||
                        a.width < rect.width - 1 || a.width > rect.width + 48 ||
                        /absolute|fixed|sticky/.test(style.position) || style.cssFloat && style.cssFloat !== 'none' ||
                        flow.display !== 'block' || /fixed|sticky/.test(flow.position) ||
                        /hidden|clip/.test(flow.overflowY)) continue;
                    parent = candidate; break;
                }
                if (!parent) { failedRecord = record; return false; }
                // The row must push following content down, not overflow a fixed-height player.
                // A scroll container owns its own flow; normal ancestors must grow with the row.
                var reservations = [], container = parent;
                for (var n = 0; container && container !== document.body &&
                    container !== document.documentElement && n < 12; n++, container = container.parentElement) {
                    var containerStyle = window.getComputedStyle(container);
                    if (/fixed|sticky/.test(containerStyle.position)) { failedRecord = record; return false; }
                    if (/auto|scroll/.test(containerStyle.overflowY)) break;
                    reservations.push({element: container, height: container.getBoundingClientRect().height});
                }
                makeButton();
                parent.insertBefore(host, anchor.nextSibling);
                rowAnchor = anchor; rowRecord = record;
                setStyles(host, {'height': height + 'px'});
                if (reservations.some(function (item) {
                    return item.element.getBoundingClientRect().height < item.height + height - 1;
                })) { removeRow(); failedRecord = record; return false; }
            }
            var parentRect = host.parentElement.getBoundingClientRect();
            var parentStyle = window.getComputedStyle(host.parentElement);
            var offset = Math.max(0, rect.left - parentRect.left - (parseFloat(parentStyle.paddingLeft) || 0) -
                (parseFloat(parentStyle.borderLeftWidth) || 0));
            setStyles(host, {'width': rect.width + 'px', 'max-width': '100%', 'height': height + 'px',
                'margin-left': offset + 'px'});
            setStyles(button, {'right': gap + 'px', 'top': gap + 'px',
                'width': size + 'px', 'height': size + 'px', 'border-radius': (16 / view.scale) + 'px',
                'background-color': policy.background, 'color': policy.foreground});
            setStyles(icon, {'width': (28 / view.scale) + 'px', 'height': (28 / view.scale) + 'px'});
            var row = host.getBoundingClientRect(), current = video.getBoundingClientRect();
            var following = host.nextElementSibling, overlapsFollowing = false;
            for (var s = 0; following && s < 4; s++, following = following.nextElementSibling) {
                var followingRect = following.getBoundingClientRect();
                if (followingRect.width <= 0 || followingRect.height <= 0) continue;
                overlapsFollowing = followingRect.top < row.top + row.height - 1 &&
                    followingRect.top + followingRect.height > row.top &&
                    followingRect.left < row.left + row.width && followingRect.left + followingRect.width > row.left;
                break;
            }
            // Reject layouts that overlap the player, reorder the row or resize the video.
            if (row.top < current.top + current.height - 1 || row.top > current.top + current.height + 32 ||
                Math.abs(current.width - rect.width) > 1 || Math.abs(current.height - rect.height) > 1 ||
                row.width < size + gap * 2 || row.height < height - 1 || overlapsFollowing) {
                removeRow(); failedRecord = record; return false;
            }
            var insets = window.getComputedStyle(insetProbe);
            var x = row.left + row.width - gap - size, y = row.top + gap;
            var visible = x >= view.left + (parseFloat(insets.paddingLeft) || 0) &&
                y >= view.top + (parseFloat(insets.paddingTop) || 0) &&
                x + size <= view.left + view.width - (parseFloat(insets.paddingRight) || 0) &&
                y + size <= view.top + view.height - (parseFloat(insets.paddingBottom) || 0);
            // Check row clipping too, including ancestors above the insertion container.
            if (visible) {
                var rowClip = clip({video: host}, view);
                visible = x >= rowClip.left && y >= rowClip.top && x + size <= rowClip.right && y + size <= rowClip.bottom;
            }
            return visible;
        }
        function update() {
            frame = null;
            if (disposed || !policy.enabled || !api.bound() || document.visibilityState === 'hidden' ||
                !document.fullscreenEnabled || document.fullscreenElement || pending) { hide(); return; }
            var view = viewport(), best = null, bestScore = -1, bestInteraction = -1;
            api.records().forEach(function (record) {
                if (!eligible(record)) return;
                var box = clip(record, view), width = box.right - box.left, height = box.bottom - box.top;
                if (width * view.scale < 120 || height * view.scale < 80) return;
                var interaction = record.lastInteraction || 0, score = width * height;
                if (interaction > bestInteraction || (interaction === bestInteraction && score > bestScore)) {
                    best = record; bestScore = score; bestInteraction = interaction;
                }
            });
            if (!best) { hide(); if (rowRecord && !rowRecord.video.isConnected) removeRow(); return; }
            if (rowRecord && rowRecord !== best) { removeRow(); failedRecord = null; rowAttempts = 0; }
            if (!placeRow(best, view)) {
                if (button) { button.hidden = true; button.disabled = true;
                    setStyles(button, {'visibility': 'hidden', 'pointer-events': 'none'}); }
                selected = null;
                availability(best, 'toolbar');
                if (armed && armed.record !== best) cancelArm();
                return;
            }
            var appearing = button.hidden;
            setStyles(button, {'visibility': 'visible', 'pointer-events': 'auto'});
            selected = best;
            availability(best, 'row');
            cancelArm();
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
        document.addEventListener('click', toolbarTap, true);
        return {
            arm: function (elementId, revision) {
                cancelArm();
                var record = offered;
                if (!policy.enabled || !api.bound() || pending || !document.fullscreenEnabled ||
                    document.fullscreenElement || document.visibilityState === 'hidden' || offeredMode !== 'toolbar' ||
                    !record || record.id !== elementId || !eligible(record)) return;
                api.refreshSource(record);
                if (record.sourceRevision !== revision) return;
                armed = {record: record, revision: revision};
                armTimeout = setTimeout(cancelArm, 10000);
            },
            cancelArm: cancelArm,
            layoutChanged: function () { failedRecord = null; rowAttempts = 0; schedule(); },
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
                if (attached) hideBrowserControls(restore);
                else { clearControlGuard(restore); restore.video.controls = true; }
                return true;
            },
            fallback: function () {
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
                cancelEntryTimeout();
                if (frame !== null) window.cancelAnimationFrame(frame);
                frame = null;
                hide(); restoreControls();
                document.removeEventListener('fullscreenchange', fullscreenChanged);
                document.removeEventListener('click', toolbarTap, true);
                removeRow();
                host = button = icon = insetProbe = null;
            }
        };
    };
})();
"""
