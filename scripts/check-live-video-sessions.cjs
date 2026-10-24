// Read-only tracker contract checks. Real WebView/website behavior remains a device check.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const kotlin = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/miowser/app/browser/video/LiveVideoSessionScript.kt'), 'utf8');
const source = kotlin.match(/"""([\s\S]*?)"""/)[1];

class Events {
  constructor() { this.events = new Map(); }
  addEventListener(type, callback) {
    const list = this.events.get(type) || [];
    list.push(callback); this.events.set(type, list);
  }
  removeEventListener(type, callback) {
    this.events.set(type, (this.events.get(type) || []).filter(fn => fn !== callback));
  }
  dispatch(type, event = {}) { [...(this.events.get(type) || [])].forEach(fn => fn(event)); }
}
function video(source = 'blob:https://site.test/video') {
  return Object.assign(new Events(), {
    tagName: 'VIDEO', nodeType: 1, isConnected: true, currentSrc: source, src: source, srcObject: null,
    currentTime: 12, duration: 45, paused: false, ended: false, muted: false,
    playbackRate: 1, readyState: 4, videoWidth: 1280, videoHeight: 720,
    seekable: { length: 1, start: () => 0, end: () => 45 },
    buffered: { length: 1, start: () => 0, end: () => 20 },
    getBoundingClientRect: () => ({ left: 0, top: 10, width: 640, height: 360 }),
    querySelectorAll: () => []
  });
}
function fixture(initial = [video()]) {
  const messages = [], timers = new Map(), intervals = new Map(), children = [];
  const mutations = [], intersections = [];
  let time = 1000, next = 1;
  const document = Object.assign(new Events(), { visibilityState: 'visible', documentElement: {},
    querySelectorAll: () => initial.filter(v => v.isConnected) });
  const window = Object.assign(new Events(), { innerWidth: 1080, innerHeight: 1920,
    frames: children, parent: {}, IntersectionObserver: true, ResizeObserver: true });
  const bridge = { postMessage: text => messages.push(JSON.parse(text)) };
  window.MiowserLiveVideo = bridge;
  class MutationObserver {
    constructor(callback) { this.callback = callback; this.connected = true; mutations.push(this); }
    observe() {}
    disconnect() { this.connected = false; }
  }
  class IntersectionObserver {
    constructor(callback) { this.callback = callback; intersections.push(this); }
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  class ResizeObserver { observe() {} unobserve() {} disconnect() {} }
  const context = vm.createContext({ window, document, MutationObserver, IntersectionObserver, ResizeObserver,
    performance: { now: () => time }, setTimeout: fn => { timers.set(next, fn); return next++; },
    clearTimeout: id => timers.delete(id), setInterval: fn => { intervals.set(next, fn); return next++; },
    clearInterval: id => intervals.delete(id) });
  function run() { vm.runInContext(source, context); }
  run();
  function bind(extra = {}) {
    const hello = messages.find(m => m.type === 'hello');
    assert(hello, 'no hello from a frame with video');
    bridge.onmessage({ data: JSON.stringify({ type: 'bind', documentId: hello.documentId,
      navigation: 3, token: 'frame-token', ...extra }) });
  }
  function flush() {
    time += 500;
    const pending = [...timers.values()]; timers.clear(); pending.forEach(fn => fn());
  }
  function mutate(added = []) {
    mutations.filter(m => m.connected).forEach(m => m.callback([{ type: 'childList', addedNodes: added }]));
  }
  function state() { return messages.filter(m => m.type === 'state').at(-1); }
  return { initial, window, document, messages, timers, intervals, children, intersections,
    run, bind, flush, mutate, state, time: () => time };
}
let checks = 0;
function check(name, test) { test(); checks++; console.log('PASS ' + name); }

check('reports current media properties without changing playback', () => {
  const f = fixture(); f.bind(); f.flush();
  const state = f.state().videos[0], original = f.initial[0];
  assert.equal(state.position, 12); assert.equal(state.speed, 1);
  assert.equal(state.sourceKind, 'blob'); assert.equal(state.playing, true);
  assert.equal(original.currentTime, 12); assert.equal(original.currentSrc, original.src);
  assert.equal(original.paused, false);
});
check('quality changes under one URL report smaller dimensions', () => {
  const f = fixture(); f.bind(); f.flush();
  f.initial[0].videoWidth = 640; f.initial[0].videoHeight = 360;
  f.initial[0].dispatch('resize'); f.flush();
  assert.equal(f.state().videos[0].width, 640);
  assert.equal(f.state().videos[0].height, 360);
  assert.equal(f.state().videos[0].sourceRevision, 1);
});
check('same-source elements receive different identities', () => {
  const f = fixture([video(), video()]); f.bind(); f.flush();
  assert.equal(new Set(f.state().videos.map(v => v.elementId)).size, 2);
});
check('source replacement retains element identity and increments revision', () => {
  const f = fixture(); f.bind(); f.flush();
  const before = f.state().videos[0];
  f.initial[0].currentSrc = 'https://cdn.test/other.mp4';
  f.initial[0].dispatch('loadstart'); f.flush();
  assert.equal(f.state().videos[0].elementId, before.elementId);
  assert.equal(f.state().videos[0].sourceKind, 'network');
  assert.equal(f.state().videos[0].sourceRevision, 2);
});
check('empty seekable ranges are preserved despite known duration', () => {
  const f = fixture(); f.initial[0].seekable.length = 0; f.bind(); f.flush();
  assert.equal(f.state().videos[0].duration, 45);
  assert.equal(f.state().videos[0].seekable.length, 0);
  assert.equal(f.state().videos[0].buffered[0][1], 20);
});
check('timeupdate bursts produce one bounded snapshot', () => {
  const f = fixture(); f.bind(); f.flush();
  for (let i = 0; i < 200; i++) f.initial[0].dispatch('timeupdate');
  assert.equal(f.timers.size, 1); f.flush();
  assert.equal(f.messages.filter(m => m.type === 'state').length, 2);
});
check('video removal clears frame ownership and heartbeat', () => {
  const f = fixture(); f.bind(); f.flush();
  f.initial[0].isConnected = false; f.mutate(); f.flush();
  assert.equal(f.state().videos.length, 0);
  assert.equal(f.messages.at(-1).type, 'retire');
  assert.equal(f.intervals.size, 0);
});
check('new dynamic elements get watched and capped', () => {
  const f = fixture([]);
  assert.equal(f.messages.length, 0); assert.equal(f.intervals.size, 0);
  const added = Array.from({ length: 100 }, () => video());
  f.mutate(added); f.bind(); f.flush();
  assert.equal(f.state().videos.length, 8);
  assert.equal(f.intervals.size, 1);
});
check('a playing dynamic video can replace an idle preview at capacity', () => {
  const previews = Array.from({ length: 8 }, () => { const v = video(); v.paused = true; return v; });
  const f = fixture(previews); f.bind(); f.flush();
  const chosen = video('https://site.test/chosen.mp4');
  f.document.dispatch('play', { target: chosen }); f.flush();
  assert.equal(f.state().videos.length, 8);
  assert(f.state().videos.some(v => v.source === chosen.currentSrc));
});
check('hidden/offscreen video is not marked visible', () => {
  const f = fixture(); f.bind(); f.flush();
  f.document.visibilityState = 'hidden'; f.document.dispatch('visibilitychange'); f.flush();
  assert.equal(f.state().videos[0].visible, false);
  f.document.visibilityState = 'visible';
  f.initial[0].getBoundingClientRect = () => ({ left: 0, top: 3000, width: 640, height: 360 });
  f.initial[0].dispatch('resize'); f.flush();
  assert.equal(f.state().videos[0].visible, false);
});
check('data URLs never enter the native telemetry payload', () => {
  const f = fixture([video('data:video/mp4;base64,' + 'A'.repeat(100000))]); f.bind(); f.flush();
  assert.equal(f.state().videos[0].sourceKind, 'data');
  assert.equal(f.state().videos[0].source, null);
});
check('long signed URLs cannot overflow the batch limit', () => {
  const f = fixture(Array.from({ length: 8 }, () => video('https://cdn.test/' + 'x'.repeat(7000))));
  f.bind(); f.flush();
  assert(JSON.stringify(f.state()).length <= 24576);
  assert(f.state().videos.every(v => v.source === null));
});
check('pagehide cancels sampling and pageshow can reconnect', () => {
  const f = fixture(); f.bind(); f.flush();
  f.initial[0].dispatch('timeupdate'); f.window.dispatch('pagehide');
  assert.equal(f.timers.size, 0); assert.equal(f.intervals.size, 0);
  const count = f.messages.length; f.initial[0].dispatch('timeupdate'); f.flush();
  assert.equal(f.messages.length, count);
  f.window.dispatch('pageshow'); f.bind({ navigation: 4 }); f.flush();
  assert.equal(f.state().navigation, 4); assert.equal(f.state().videos.length, 1);
});
check('initialization is idempotent and reconnects child frames', () => {
  const f = fixture(); const observers = f.intersections.length; f.run();
  assert.equal(f.intersections.length, observers);
  let invites = 0; f.children.push({ postMessage: () => invites++ });
  f.window.__miowserLiveVideos.connect(); assert.equal(invites, 1);
});
check('wrong-document bindings are ignored', () => {
  const f = fixture(); f.bind({ documentId: 'stale' }); f.flush();
  assert.equal(f.state(), undefined);
});
check('user interaction is reported once rather than repeated by clock ticks', () => {
  const f = fixture(); f.bind(); f.flush();
  f.initial[0].dispatch('pointerdown'); f.flush();
  assert.equal(f.state().videos[0].interacted, true);
  f.initial[0].dispatch('timeupdate'); f.flush();
  assert.equal(f.state().videos[0].interacted, false);
});
console.log(checks + ' live-video script checks passed; no device tests were run.');
