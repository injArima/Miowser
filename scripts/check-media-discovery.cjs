'use strict';
// Run the shipped discovery helper with deterministic timers; no Android runtime required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../app/src/main/java/com/miowser/app');
const constant = (file, name) => {
  const source = fs.readFileSync(path.join(base, file), 'utf8');
  const match = source.match(new RegExp('(?:internal |private )?const val ' + name + ' = """([\\s\\S]*?)"""'));
  assert(match, name); return match[1];
};
const helper = constant('browser/BoundedMediaDiscovery.kt', 'BOUNDED_MEDIA_DISCOVERY_SCRIPT');
for (const [file, name] of [
  ['grabber/VideoSnifferScript.kt', 'VIDEO_SNIFFER_TEMPLATE'],
  ['browser/video/LiveVideoSessionScript.kt', 'LIVE_VIDEO_SESSION_TEMPLATE']
]) new vm.Script(constant(file, name).replace('/*BOUNDED_MEDIA_DISCOVERY*/', helper), {filename: name});
function fixture(count, cost = 0) {
  let clock = 0, serial = 0, walkers = 0;
  const timers = new Map(), visited = [];
  const nodes = Array.from({length: count}, (_, id) => ({nodeType: 1, isConnected: true, id}));
  const document = {nodeType: 9, children: nodes, createTreeWalker(root) {
    walkers++; let index = 0;
    return {nextNode: () => (root.children || [])[index++] || null};
  }};
  const ctx = vm.createContext({document, performance: {now: () => (clock += cost)},
    setTimeout(fn) { timers.set(++serial, fn); return serial; }, clearTimeout(id) { timers.delete(id); }});
  vm.runInContext(helper, ctx);
  const discovery = ctx.createBoundedMediaDiscovery(node => visited.push(node.id));
  const step = () => { const [id, fn] = timers.entries().next().value; timers.delete(id); fn(); };
  const drain = () => { let limit = 1000; while (timers.size && limit-- > 0) step(); assert(limit > 0); };
  return {ctx, document, nodes, discovery, visited, timers, step, drain, get walkers() { return walkers; }};
}
let passed = 0;
const test = (name, fn) => { fn(); passed++; console.log('PASS ' + name); };
test('large pages discover the tail without walking synchronously or exceeding 128 visits per task', () => {
  const f = fixture(3000); f.discovery.enqueue(f.document); assert.equal(f.visited.length, 0);
  while (f.timers.size) { const before = f.visited.length; f.step(); assert(f.visited.length - before <= 128); }
  assert.equal(f.visited.length, 3000); assert.equal(f.visited.at(-1), 2999);
});
test('wall-clock budget yields before the element-count budget', () => {
  const f = fixture(300, 0.25); f.discovery.enqueue(f.document); f.step();
  assert(f.visited.length > 0 && f.visited.length < 128); f.drain(); assert.equal(f.visited.length, 300);
});
test('mutation floods enqueue bounded work and still rediscover a player beyond the first batch', () => {
  const f = fixture(300); f.discovery.mutations(Array.from({length: 5000}, () => ({type: 'childList', addedNodes: f.nodes})), f.document);
  assert.equal(f.visited.length, 0); assert(f.walkers <= 33); assert.equal(f.timers.size, 1);
  f.drain(); assert(f.visited.includes(299));
});
test('repeated roots queue a later pass for nodes inserted behind the walker', () => {
  const f = fixture(300); f.discovery.enqueue(f.document); f.step();
  f.nodes.unshift({nodeType: 1, isConnected: true, id: 'late-player'});
  f.discovery.enqueue(f.document); f.drain(); assert(f.visited.includes('late-player'));
});
test('nested discovery keeps one timer and cancellation drops pending work', () => {
  const f = fixture(300);
  const discovery = f.ctx.createBoundedMediaDiscovery(() => discovery.enqueue({nodeType: 11, children: []}));
  discovery.enqueue(f.document); f.step(); assert.equal(f.timers.size, 1);
  discovery.stop(); assert.equal(f.timers.size, 0);
});
test('removed subtrees and visitor errors do not strand later work', () => {
  const f = fixture(3), discovered = [];
  const discovery = f.ctx.createBoundedMediaDiscovery(node => { if (node.id === 0) throw Error('site DOM'); discovered.push(node.id); });
  discovery.enqueue({nodeType: 1, isConnected: false, children: []});
  discovery.enqueue(f.document); f.drain(); assert.deepEqual(discovered, [1, 2]);
});
console.log(`Media discovery: two composed scripts parsed; ${passed} host regressions passed.`);
