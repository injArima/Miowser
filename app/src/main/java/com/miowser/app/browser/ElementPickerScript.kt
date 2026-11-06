package com.miowser.app.browser

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.miowser.app.adblock.ElementRuleAction
import org.json.JSONObject

data class ElementPickerSelection(
    val selectors: List<String>,
    val labels: List<String>
)

internal data class ElementPickerPalette(
    val surface: Color,
    val foreground: Color,
    val outline: Color,
    val accent: Color,
    val accentSurface: Color,
    val onAccentSurface: Color,
    val error: Color,
    val errorSurface: Color,
    val onErrorSurface: Color
) {
    companion object {
        fun from(colors: ColorScheme) = ElementPickerPalette(
            colors.surfaceContainerHigh, colors.onSurface, colors.outlineVariant,
            colors.primary, colors.primaryContainer, colors.onPrimaryContainer,
            colors.error, colors.errorContainer, colors.onErrorContainer
        )
    }
}

private fun Color.pickerCss(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun Color.pickerHighlight(): String {
    val value = toArgb()
    return "rgba(${value shr 16 and 0xFF},${value shr 8 and 0xFF},${value and 0xFF},.16)"
}

internal class ElementPickerBridge {
    private data class Session(
        val token: String,
        val onSelection: (ElementPickerSelection) -> Unit,
        val onAction: (Int, ElementRuleAction) -> Unit,
        var selection: ElementPickerSelection? = null
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null

    fun begin(token: String, onSelection: (ElementPickerSelection) -> Unit,
              onAction: (Int, ElementRuleAction) -> Unit) {
        session = Session(token, onSelection, onAction)
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
        current.selection = parsed
        mainHandler.post { if (session === current) current.onSelection(parsed) }
    }

    @JavascriptInterface
    fun action(token: String?, index: Int, action: String?) {
        val current = session ?: return
        val selected = current.selection ?: return
        if (token != current.token || index !in selected.selectors.indices) return
        val parsedAction = runCatching { ElementRuleAction.valueOf(action ?: "") }.getOrNull() ?: return
        session = null
        mainHandler.post { current.onAction(index, parsedAction) }
    }
}

internal fun elementPickerStartScript(token: String, palette: ElementPickerPalette): String {
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
                'border:3px solid ${palette.accent.pickerCss()};background:${palette.accent.pickerHighlight()};border-radius:8px;' +
                'box-sizing:border-box;display:none;';
            (document.documentElement || document.body).appendChild(overlay);
            var toolbar = document.createElement('div');
            toolbar.setAttribute('data-miowser-picker', '1');
            toolbar.style.cssText = 'position:fixed;z-index:2147483647;display:none;' +
                'width:min(224px,calc(100vw - 16px));height:56px;pointer-events:auto;';
            var shadow = toolbar.attachShadow({mode:'closed'});
            shadow.innerHTML = '<style>' +
                ':host{all:initial}div{display:flex;gap:4px;align-items:center;padding:4px;' +
                'border-radius:18px;background:${palette.surface.pickerCss()};color:${palette.foreground.pickerCss()};' +
                'border:1px solid ${palette.outline.pickerCss()};box-shadow:0 6px 20px #0004;' +
                'box-sizing:border-box;width:100%;height:56px}' +
                'button{all:unset;display:flex;align-items:center;justify-content:center;' +
                'flex:1;min-width:0;height:46px;border-radius:14px;color:inherit;cursor:pointer;' +
                'font:600 22px sans-serif;touch-action:manipulation}' +
                'svg{width:24px;height:24px;fill:currentColor}' +
                'button:disabled{opacity:.35;cursor:default}button:focus-visible{outline:2px solid ${palette.accent.pickerCss()}}' +
                'button[data-action="HIDE"]{background:${palette.accentSurface.pickerCss()};color:${palette.onAccentSurface.pickerCss()}}' +
                'button[data-action="DELETE"]{background:${palette.errorSurface.pickerCss()};color:${palette.onErrorSurface.pickerCss()}}' +
                '</style><div role="toolbar" aria-label="Page element actions">' +
                '<button type="button" data-action="smaller" aria-label="Select smaller element" title="Smaller"><svg viewBox="0 0 24 24"><path d="M5 11h14v2H5z"/></svg></button>' +
                '<button type="button" data-action="larger" aria-label="Select larger container" title="Larger"><svg viewBox="0 0 24 24"><path d="M11 5h2v14h-2zM5 11h14v2H5z"/></svg></button>' +
                '<button type="button" data-action="HIDE" aria-label="Hide element with CSS" title="Hide with CSS"><svg viewBox="0 0 24 24"><path d="M2.3 3.7 3.7 2.3l18 18-1.4 1.4-3-3A11.8 11.8 0 0 1 12 20C6.6 20 2.4 16.7 1 12c.7-2.2 2-4.1 3.8-5.5L2.3 3.7ZM12 4c5.4 0 9.6 3.3 11 8-.5 1.6-1.4 3.1-2.6 4.3l-3-3A5.5 5.5 0 0 0 10.7 6L8.8 4.3c1-.2 2.1-.3 3.2-.3ZM7.3 9 5.9 7.7A9.7 9.7 0 0 0 3.2 12c1.5 3.7 4.6 6 8.8 6 1.4 0 2.7-.3 3.8-.8l-2-2A5.5 5.5 0 0 1 7.3 9Z"/></svg></button>' +
                '<button type="button" data-action="DELETE" aria-label="Delete element from page" title="Delete from page"><svg viewBox="0 0 24 24"><path d="m18.3 4.3 1.4 1.4L13.4 12l6.3 6.3-1.4 1.4L12 13.4l-6.3 6.3-1.4-1.4 6.3-6.3-6.3-6.3 1.4-1.4 6.3 6.3z"/></svg></button>' +
                '</div>';
            (document.documentElement || document.body).appendChild(toolbar);
            var candidates = [];
            var chosenIndex = 0;
            var armedElement = null;

            shadow.querySelectorAll('button').forEach(function(button) {
                button.addEventListener('click', function(event) {
                    event.preventDefault();
                    event.stopPropagation();
                    var operation = button.getAttribute('data-action');
                    if (operation === 'smaller' || operation === 'larger') {
                        choose(chosenIndex + (operation === 'smaller' ? -1 : 1));
                    } else if (candidates[chosenIndex]) {
                        toolbar.style.display = 'none';
                        try { window.MiowserElementPicker.action(token, chosenIndex, operation); }
                        catch (_) { cleanup(); }
                    }
                });
            });

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
                var top = rect.top >= 64 ? rect.top - 64 :
                    (rect.bottom + 64 <= innerHeight ? rect.bottom + 8 :
                        Math.max(8, Math.min(innerHeight - 64, rect.top + 8)));
                var toolbarWidth = Math.min(224, Math.max(0, innerWidth - 16));
                toolbar.style.left = Math.max(8, Math.min(innerWidth - toolbarWidth - 8,
                    rect.left + (rect.width - toolbarWidth) / 2)) + 'px';
                toolbar.style.top = Math.max(8, top) + 'px';
                toolbar.style.display = 'block';
            }

            function choose(index) {
                chosenIndex = Math.max(0, Math.min(candidates.length - 1, index));
                armedElement = candidates[chosenIndex] && candidates[chosenIndex].element;
                if (armedElement && armedElement.isConnected) show(armedElement);
                shadow.querySelector('[data-action="smaller"]').disabled = chosenIndex === 0;
                shadow.querySelector('[data-action="larger"]').disabled = chosenIndex >= candidates.length - 1;
            }

            function stopCapture() {
                document.removeEventListener('click', onPick, true);
                window.removeEventListener('scroll', refreshArmed, true);
                window.removeEventListener('resize', refreshArmed, true);
            }
            function refreshArmed() {
                if (armedElement && armedElement.isConnected) show(armedElement);
                else if (armedElement) {
                    overlay.style.display = 'none';
                    toolbar.style.display = 'none';
                }
            }
            function onPick(event) {
                if (event.composedPath && event.composedPath().indexOf(toolbar) !== -1) return;
                var element = candidateAt(event);
                if (!(element instanceof Element) || element === document.body ||
                    element === document.documentElement) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                candidates = [];
                var node = element;
                while (node && node instanceof Element && node !== document.body &&
                    node !== document.documentElement && candidates.length < 6) {
                    var selector = selectorFor(node);
                    if (selector) candidates.push({ selector: selector, label: labelFor(node), element: node });
                    node = node.parentElement;
                }
                if (!candidates.length) return;
                choose(0);
                try {
                    window.MiowserElementPicker.selected(token, JSON.stringify({
                        selectors: candidates.map(function(item) { return item.selector; }),
                        labels: candidates.map(function(item) { return item.label; })
                    }));
                } catch (_) { cleanup(); }
            }

            function cleanup() {
                stopCapture();
                overlay.remove();
                toolbar.remove();
                if (window.__miowserElementPicker === api) delete window.__miowserElementPicker;
            }
            var api = {
                cancel: cleanup,
                commit: function(action) {
                    var item = candidates[chosenIndex];
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
