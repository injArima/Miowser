package com.miowser.app.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.miowser.app.adblock.ElementRuleAction
import com.miowser.app.adblock.SiteElementRules
import org.json.JSONArray
import org.json.JSONObject

internal class ElementRuleBridge {
    @JavascriptInterface
    fun rulesFor(hostname: String?): String {
        val rules = SiteElementRules.forHost(hostname)
        return JSONObject().apply {
            put("hide", JSONArray(rules.filter { it.action == ElementRuleAction.HIDE }
                .map { it.selector }))
            put("delete", JSONArray(rules.filter { it.action == ElementRuleAction.DELETE }
                .map { it.selector }))
        }.toString()
    }
}

private const val ELEMENT_RULE_INJECTION_SCRIPT = """
    (function() {
        function validSelectors(values) {
            if (!Array.isArray(values)) return [];
            return values.filter(function(selector) {
                if (typeof selector !== 'string' || !selector || selector.length > 1024) return false;
                try { document.querySelector(selector); return true; } catch (_) { return false; }
            });
        }

        function readRules() {
            try {
                var bridge = window.MiowserElementRules;
                if (!bridge) return { hide: [], delete: [] };
                var parsed = JSON.parse(bridge.rulesFor(document.location.hostname) || '{}');
                return { hide: validSelectors(parsed.hide), delete: validSelectors(parsed.delete) };
            } catch (_) {
                return { hide: [], delete: [] };
            }
        }

        var state = window.__miowserElementRuleState || { rules: { hide: [], delete: [] } };
        window.__miowserElementRuleState = state;

        function applyCurrentRules() {
            var rules = state.rules;
            var style = document.querySelector('style[data-miowser-element-rules="1"]');
            var target = document.head || document.documentElement;
            if (!target) return false;
            if (rules.hide.length) {
                if (!style) {
                    style = document.createElement('style');
                    style.setAttribute('data-miowser-element-rules', '1');
                    target.appendChild(style);
                }
                var css = rules.hide.map(function(selector) {
                    return selector + '{display:none!important;}';
                }).join('\n');
                if (style.textContent !== css) style.textContent = css;
            } else if (style) {
                style.remove();
            }
            if (rules.delete.length) {
                try {
                    document.querySelectorAll(rules.delete.join(','))
                        .forEach(function(node) { node.remove(); });
                } catch (_) {}
            }
            return true;
        }

        state.refresh = function() {
            state.rules = readRules();
            // CSS hiding is maintained by the browser; only DOM deletion needs an observer.
            if (!state.rules.delete.length && state.observer) {
                state.observer.disconnect();
                state.observer = null;
            }
            if (state.rules.delete.length && !state.observer) {
                var scheduled = false;
                state.observer = new MutationObserver(function() {
                    if (scheduled) return;
                    scheduled = true;
                    requestAnimationFrame(function() {
                        scheduled = false;
                        if (state.rules.delete.length) applyCurrentRules();
                    });
                });
                state.observer.observe(document, { childList: true, subtree: true });
            }
            return applyCurrentRules();
        };
        window.__miowserApplyElementRules = state.refresh;
        if (!state.refresh()) {
            var readyObserver = new MutationObserver(function() {
                if (applyCurrentRules()) readyObserver.disconnect();
            });
            readyObserver.observe(document, { childList: true, subtree: true });
        }
    })();
"""

fun WebView.installElementRuleInjectionScript() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, ELEMENT_RULE_INJECTION_SCRIPT, setOf("*"))
    }
}

fun WebView.applyElementRulesNow() {
    evaluateJavascript(ELEMENT_RULE_INJECTION_SCRIPT, null)
}
