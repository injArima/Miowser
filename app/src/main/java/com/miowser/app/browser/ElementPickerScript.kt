package com.miowser.app.browser

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

data class ElementPickerSelection(
    val selectors: List<String>,
    val labels: List<String>
)

internal class ElementPickerBridge {
    private data class Session(
        val token: String,
        val callback: (ElementPickerSelection) -> Unit
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null

    fun begin(token: String, callback: (ElementPickerSelection) -> Unit) {
        session = Session(token, callback)
    }

    fun cancel() {
        session = null
    }

    @JavascriptInterface
    fun selected(token: String?, payload: String?) {
        val current = session ?: return
        if (token != current.token || payload == null || payload.length > 16_384) return
        val parsed = runCatching {
            val json = JSONObject(payload)
            val selectorArray = json.getJSONArray("selectors")
            val labelArray = json.getJSONArray("labels")
            require(selectorArray.length() in 1..6 && labelArray.length() == selectorArray.length())
            val selectors = (0 until selectorArray.length()).map(selectorArray::getString)
            require(selectors.all { it.length in 1..1_024 && it.none { char ->
                char == '\u0000' || char == '\n' || char == '\r' || char == '{' || char == '}'
            } })
            val labels = (0 until labelArray.length()).map { index ->
                labelArray.getString(index).take(80)
            }
            ElementPickerSelection(selectors, labels)
        }.getOrNull() ?: return
        session = null
        mainHandler.post { current.callback(parsed) }
    }
}

internal fun elementPickerStartScript(token: String): String {
    val quotedToken = javascriptStringLiteral(token)
    return """
        (function() {
            if (window.__miowserElementPicker && window.__miowserElementPicker.cancel) {
                window.__miowserElementPicker.cancel();
            }
            var token = $quotedToken;
            var overlay = document.createElement('div');
            overlay.setAttribute('data-miowser-picker', '1');
            overlay.style.cssText = 'position:fixed;z-index:2147483647;pointer-events:none;' +
                'border:3px solid #f5d77a;background:rgba(245,215,122,.18);border-radius:6px;' +
                'box-sizing:border-box;display:none;transition:all 60ms linear;';
            (document.documentElement || document.body).appendChild(overlay);
            var previewStyle = null;
            var candidates = [];
            var chosenIndex = 0;
            var armedElement = null;

            function escaped(value) {
                if (window.CSS && CSS.escape) return CSS.escape(value);
                return value.replace(/[^a-zA-Z0-9_-]/g, function(char) {
                    return '\\' + char.codePointAt(0).toString(16) + ' ';
                });
            }

            function selectorFor(element) {
                if (!(element instanceof Element)) return '';
                if (element.id) {
                    var byId = '#' + escaped(element.id);
                    try { if (document.querySelectorAll(byId).length === 1) return byId; } catch (_) {}
                }
                var parts = [];
                var node = element;
                while (node && node.nodeType === 1 && node !== document.documentElement) {
                    var part = node.localName.toLowerCase();
                    var classes = Array.from(node.classList || []).filter(function(name) {
                        return name.length > 0 && name.length <= 40 && !/\d{4,}/.test(name);
                    }).slice(0, 2);
                    if (classes.length) part += '.' + classes.map(escaped).join('.');
                    var siblings = node.parentElement ?
                        Array.from(node.parentElement.children).filter(function(item) {
                            return item.localName === node.localName;
                        }) : [];
                    if (siblings.length > 1) part += ':nth-of-type(' + (siblings.indexOf(node) + 1) + ')';
                    parts.unshift(part);
                    var selector = parts.join(' > ');
                    try { if (document.querySelectorAll(selector).length === 1) return selector; } catch (_) {}
                    node = node.parentElement;
                    if (parts.length >= 6) break;
                }
                return parts.join(' > ');
            }

            function labelFor(element) {
                var label = element.localName ? element.localName.toLowerCase() : 'element';
                if (element.id) label += '#' + element.id;
                else if (element.classList && element.classList.length) {
                    label += '.' + Array.from(element.classList).slice(0, 2).join('.');
                }
                return label.slice(0, 80);
            }

            function candidateAt(event) {
                var path = event.composedPath ? event.composedPath() : [];
                var element = path.find(function(item) {
                    return item instanceof Element && !item.hasAttribute('data-miowser-picker');
                });
                return element || document.elementFromPoint(event.clientX, event.clientY);
            }

            function show(element) {
                if (!(element instanceof Element) || element === document.body ||
                    element === document.documentElement) {
                    overlay.style.display = 'none';
                    return;
                }
                var rect = element.getBoundingClientRect();
                overlay.style.display = 'block';
                overlay.style.left = Math.max(0, rect.left) + 'px';
                overlay.style.top = Math.max(0, rect.top) + 'px';
                overlay.style.width = Math.max(0, Math.min(innerWidth, rect.right) - Math.max(0, rect.left)) + 'px';
                overlay.style.height = Math.max(0, Math.min(innerHeight, rect.bottom) - Math.max(0, rect.top)) + 'px';
            }

            function stopCapture() {
                document.removeEventListener('click', onPick, true);
                window.removeEventListener('scroll', refreshArmed, true);
                window.removeEventListener('resize', refreshArmed, true);
            }
            function refreshArmed() {
                if (armedElement && armedElement.isConnected) show(armedElement);
                else if (armedElement) overlay.style.display = 'none';
            }
            function onPick(event) {
                var element = candidateAt(event);
                if (!(element instanceof Element) || element === document.body ||
                    element === document.documentElement) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                if (!armedElement || !armedElement.contains(element)) {
                    armedElement = element;
                    show(armedElement);
                    return;
                }
                stopCapture();
                candidates = [];
                var node = armedElement;
                while (node && node instanceof Element && node !== document.body &&
                    node !== document.documentElement && candidates.length < 6) {
                    var selector = selectorFor(node);
                    if (selector) candidates.push({ selector: selector, label: labelFor(node) });
                    node = node.parentElement;
                }
                overlay.style.display = 'none';
                try {
                    window.MiowserElementPicker.selected(token, JSON.stringify({
                        selectors: candidates.map(function(item) { return item.selector; }),
                        labels: candidates.map(function(item) { return item.label; })
                    }));
                } catch (_) { cleanup(); }
            }

            function clearPreview() {
                if (previewStyle) previewStyle.remove();
                previewStyle = null;
            }
            function preview(index) {
                clearPreview();
                chosenIndex = Math.max(0, Math.min(candidates.length - 1, Number(index) || 0));
                var item = candidates[chosenIndex];
                if (!item) return;
                try { document.querySelector(item.selector); } catch (_) { return; }
                previewStyle = document.createElement('style');
                previewStyle.setAttribute('data-miowser-picker', '1');
                previewStyle.textContent = item.selector + '{display:none!important;}';
                (document.head || document.documentElement).appendChild(previewStyle);
            }
            function cleanup() {
                stopCapture();
                clearPreview();
                overlay.remove();
                if (window.__miowserElementPicker === api) delete window.__miowserElementPicker;
            }
            var api = {
                preview: preview,
                cancel: cleanup,
                commit: function(action) {
                    var item = candidates[chosenIndex];
                    clearPreview();
                    if (action === 'DELETE' && item) {
                        try {
                            document.querySelectorAll(item.selector).forEach(function(node) { node.remove(); });
                        } catch (_) {}
                    }
                    cleanup();
                }
            };
            window.__miowserElementPicker = api;
            document.addEventListener('click', onPick, true);
            window.addEventListener('scroll', refreshArmed, true);
            window.addEventListener('resize', refreshArmed, true);
        })();
    """.trimIndent()
}

internal fun elementPickerPreviewScript(index: Int): String =
    "window.__miowserElementPicker && window.__miowserElementPicker.preview(${index.coerceIn(0, 5)});"

internal fun elementPickerCancelScript(): String =
    "window.__miowserElementPicker && window.__miowserElementPicker.cancel();"

internal fun elementPickerCommitScript(action: String): String =
    "window.__miowserElementPicker && window.__miowserElementPicker.commit(${javascriptStringLiteral(action)});"

internal fun javascriptStringLiteral(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028', '\u2029' -> append("\\u%04x".format(character.code))
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code))
                else append(character)
        }
    }
    append('"')
}
