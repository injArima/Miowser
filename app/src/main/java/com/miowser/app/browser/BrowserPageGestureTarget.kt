package com.miowser.app.browser

import android.webkit.WebView

/** A single hit-test per DOWN, after dispatching that real touch to Chromium. No page scanning. */
internal const val BROWSER_PAGE_GESTURE_TARGET_SCRIPT = """
(function(fx, fy) {
    try {
        var viewport = window.visualViewport;
        var x = (viewport ? viewport.offsetLeft : 0) + fx * (viewport ? viewport.width : window.innerWidth);
        var y = (viewport ? viewport.offsetTop : 0) + fy * (viewport ? viewport.height : window.innerHeight);
        var scroller = document.scrollingElement || document.documentElement;
        var roots = [document.documentElement, document.body];
        for (var r = 0; r < roots.length; r++) {
            if (!roots[r]) continue;
            var rootStyle = window.getComputedStyle(roots[r]);
            if (/^(contain|none)$/.test(rootStyle.overscrollBehaviorY) ||
                /^(hidden|clip)$/.test(rootStyle.overflowY) ||
                /^(none|pan-x)$/.test(rootStyle.touchAction)) return false;
        }
        var node = document.elementFromPoint(x, y);
        if (!node) return false;
        for (var depth = 0; depth < 16 && node.shadowRoot && node.shadowRoot.elementFromPoint; depth++) {
            var inner = node.shadowRoot.elementFromPoint(x, y);
            if (!inner || inner === node) break;
            node = inner;
        }
        var controls = 'a[href],button,input,textarea,select,option,summary,video,audio,canvas,iframe,frame,object,embed,' +
            '[contenteditable]:not([contenteditable="false"]),[draggable="true"],[onclick]';
        var roles = /(^|\s)(button|link|slider|switch|checkbox|radio|tab|menuitem|listbox|option|scrollbar|textbox|combobox)(\s|$)/;
        var visited = 0;
        while (node && visited++ < 48) {
            if (node.matches(controls) || node.isContentEditable || roles.test(node.getAttribute('role') || '')) return false;
            var style = window.getComputedStyle(node);
            if (style.cursor === 'pointer' || /(^|\s)(none|pan-x)(\s|$)/.test(style.touchAction) ||
                /^(contain|none)$/.test(style.overscrollBehaviorY)) return false;
            if (node !== scroller && node !== document.documentElement && node !== document.body &&
                ((/^(auto|scroll|overlay)$/.test(style.overflowY) && node.scrollHeight > node.clientHeight + 1) ||
                 (/^(auto|scroll|overlay)$/.test(style.overflowX) && node.scrollWidth > node.clientWidth + 1))) return false;
            var parent = node.parentElement;
            if (!parent) { var root = node.getRootNode && node.getRootNode(); parent = root && root.host; }
            node = parent;
        }
        return !node;
    } catch (_) { return false; }
})
"""

internal fun WebView.checkPageAllowsRefresh(x: Float, y: Float, result: (Boolean) -> Unit) {
    if (!settings.javaScriptEnabled) {
        result(hitTestResult?.type == WebView.HitTestResult.UNKNOWN_TYPE)
        return
    }
    val location = url
    evaluateJavascript("$BROWSER_PAGE_GESTURE_TARGET_SCRIPT(${x.coerceIn(0f, 1f)},${y.coerceIn(0f, 1f)})") {
        result(url == location && it == "true")
    }
}
