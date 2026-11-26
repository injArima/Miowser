package com.miowser.app.browser

/** Shared by document-start scripts; DOM traversal yields to input and never scans inside an observer callback. */
internal const val BOUNDED_MEDIA_DISCOVERY_SCRIPT = """
function createBoundedMediaDiscovery(visit) {
    var jobs = [], timer = null, stopped = false, pumping = false;
    function enqueue(root) {
        if (stopped || !root || [1, 9, 11].indexOf(root.nodeType) < 0) return;
        var existing = jobs.find(function (job) { return job.root === root; });
        if (existing) { existing.again = true; return; }
        if (jobs.length >= 64) { jobs = []; root = document; }
        var walker = document.createTreeWalker(root, 1);
        jobs.push({root: root, walker: walker, node: root.nodeType === 1 ? root : walker.nextNode()});
        if (timer === null && !pumping) timer = setTimeout(pump, 0);
    }
    function pump() {
        timer = null;
        if (stopped) return;
        pumping = true;
        var remaining = 128, until = performance.now() + 4;
        while (jobs.length && remaining-- > 0 && performance.now() < until) {
            var job = jobs[0], node = job.node;
            if (!node || job.root.isConnected === false) {
                jobs.shift();
                if (job.again && job.root.isConnected !== false) enqueue(job.root);
                continue;
            }
            job.node = job.walker.nextNode();
            if (node.isConnected !== false) { try { visit(node); } catch (ignored) {} }
        }
        pumping = false;
        if (jobs.length) timer = setTimeout(pump, 8);
    }
    function mutations(changes, fallback) {
        var limit = Math.min(changes.length, 32), nodes = 0;
        for (var i = 0; i < limit; i++) {
            var change = changes[i];
            if (change.type !== 'childList') continue;
            for (var j = 0; j < change.addedNodes.length; j++) {
                if (++nodes > 32) { enqueue(fallback); return; }
                enqueue(change.addedNodes[j]);
            }
        }
        if (changes.length > limit) enqueue(fallback);
    }
    return {enqueue: enqueue, mutations: mutations, stop: function () {
        stopped = true; jobs = []; if (timer !== null) clearTimeout(timer); timer = null;
    }};
}
"""
