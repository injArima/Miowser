package com.miowser.app.browser.video

/** Reversible presentation of an existing video/frame; never moves or reloads its media element. */
internal const val BROWSER_VIDEO_STAGE_SCRIPT = """
(function () {
    window.__miowserStageVideo = function (target) {
        var edits = [];
        function set(node, name, value) {
            if (!node || !node.style) return;
            edits.push([node, name, node.style.getPropertyValue(name), node.style.getPropertyPriority(name), value]);
            node.style.setProperty(name, value, 'important');
        }
        var branch = target;
        for (var depth = 0; branch && depth < 64; depth++) {
            var parent = branch.parentElement || (branch.getRootNode && branch.getRootNode().host);
            if (!parent) break;
            Array.from(parent.children).forEach(function (sibling) {
                if (sibling !== branch) set(sibling, 'visibility', 'hidden');
            });
            ['transform', 'filter', 'perspective', 'contain'].forEach(function (name) { set(parent, name, 'none'); });
            set(parent, 'overflow', 'visible'); set(parent, 'clip-path', 'none');
            branch = parent;
        }
        var styles = {position: 'fixed', top: '0px', left: '0px', width: '100vw', height: '100vh',
            'max-width': 'none', 'max-height': 'none', 'min-width': '0px', 'min-height': '0px',
            margin: '0px', border: '0px', padding: '0px', visibility: 'visible',
            'z-index': '2147483647', 'object-fit': 'contain', background: 'black', transform: 'none'};
        Object.keys(styles).forEach(function (name) { set(target, name, styles[name]); });
        return function () {
            edits.reverse().forEach(function (edit) {
                var style = edit[0].style;
                if (style.getPropertyValue(edit[1]) !== edit[4]) return;
                if (edit[2]) style.setProperty(edit[1], edit[2], edit[3]);
                else style.removeProperty(edit[1]);
            });
            edits = [];
        };
    };
})();
"""
