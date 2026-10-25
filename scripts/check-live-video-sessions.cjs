// Tracker and floating-button contract checks. Real WebView behavior remains a device check.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const kotlin = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/miowser/app/browser/video/LiveVideoSessionScript.kt'), 'utf8');
const source = kotlin.match(/"""([\s\S]*?)"""/)[1];
const buttonSource = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/miowser/app/browser/video/VideoAssistantButtonScript.kt'), 'utf8')
  .match(/"""([\s\S]*?)"""/)[1];
const controlSource = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/miowser/app/browser/video/BrowserVideoControlScript.kt'), 'utf8')
  .match(/"""([\s\S]*?)"""/)[1];

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
function style() {
  const values = {}, priorities = {};
  return { setProperty(key, value, priority = '') { values[key] = String(value); priorities[key] = priority; this[key] = String(value); },
    getPropertyValue(key) { return values[key] || ''; }, getPropertyPriority(key) { return priorities[key] || ''; },
    removeProperty(key) { const old = values[key]; delete values[key]; delete priorities[key]; delete this[key]; return old; } };
}
function tracks(items) { return Object.assign(items, new Events(), {
  addEventListener: Events.prototype.addEventListener, removeEventListener: Events.prototype.removeEventListener,
  dispatch: Events.prototype.dispatch }); }
class Element extends Events {
  constructor(tag) {
    super(); this.tagName = tag.toUpperCase(); this.children = []; this.attributes = {};
    this.isConnected = false; this.parentElement = null; this.hidden = false;
    this.style = style();
    this.animationCount = 0;
  }
  setAttribute(key, value) { this.attributes[key] = value; }
  getAttribute(key) { return Object.hasOwn(this.attributes, key) ? this.attributes[key] : null; }
  removeAttribute(key) { delete this.attributes[key]; }
  appendChild(child) {
    this.children.push(child); child.parentElement = this;
    child.setConnected?.(this.isConnected); return child;
  }
  setConnected(connected) {
    this.isConnected = connected;
    this.children.forEach(child => child.setConnected?.(connected));
    this.shadow?.setConnected(connected);
  }
  attachShadow() { this.shadow = new Element('shadow'); return this.shadow; }
  remove() { this.parentElement.children = this.parentElement.children.filter(child => child !== this); this.setConnected(false); }
  animate() { this.animationCount++; return { cancel() {} }; }
  querySelectorAll() { return []; }
}
function video(source = 'blob:https://site.test/video') {
  return Object.assign(new Events(), {
    tagName: 'VIDEO', nodeType: 1, isConnected: true, currentSrc: source, src: source, srcObject: null,
    currentTime: 12, duration: 45, paused: false, ended: false, muted: false,
    playbackRate: 1, readyState: 4, videoWidth: 1280, videoHeight: 720, controls: false,
    seeking: false,
    style: style(), textTracks: tracks([]), audioTracks: tracks([]),
    attributes: {}, setAttribute: Element.prototype.setAttribute, getAttribute: Element.prototype.getAttribute,
    removeAttribute: Element.prototype.removeAttribute,
    play() { this.paused = false; this.dispatch('play'); return {then(resolve) {resolve();}}; },
    pause() { this.paused = true; this.dispatch('pause'); },
    seekable: { length: 1, start: () => 0, end: () => 45 },
    buffered: { length: 1, start: () => 0, end: () => 20 },
    getBoundingClientRect: () => ({ left: 0, top: 10, width: 640, height: 360 }),
    querySelectorAll: () => []
  });
}
function fixture(initial = [video()]) {
  const messages = [], timers = new Map(), intervals = new Map(), children = [], animationFrames = new Map();
  const elements = [], root = new Element('html'); root.setConnected(true);
  const mutations = [], intersections = [];
  let time = 1000, next = 1;
  const document = Object.assign(new Events(), { visibilityState: 'visible', documentElement: root,
    fullscreenEnabled: true, fullscreenElement: null,
    exitFullscreen() { this.fullscreenElement = null; this.dispatch('fullscreenchange'); return {catch() {}}; },
    querySelectorAll: () => initial.filter(v => v.isConnected),
    createElement: tag => { const e = new Element(tag); elements.push(e); return e; },
    createElementNS: (_, tag) => { const e = new Element(tag); elements.push(e); return e; } });
  initial.forEach(v => {
    v.fullscreenRequests = 0;
    v.requestFullscreen = function () {
      this.fullscreenRequests++; document.fullscreenElement = this; document.dispatch('fullscreenchange');
      return { catch() {} };
    };
  });
  const visualViewport = Object.assign(new Events(), {offsetLeft: 0, offsetTop: 0, width: 1080, height: 1920, scale: 1});
  const window = Object.assign(new Events(), { innerWidth: 1080, innerHeight: 1920,
    frames: children, parent: {}, IntersectionObserver: true, ResizeObserver: true, visualViewport,
    requestAnimationFrame: fn => { animationFrames.set(next, fn); return next++; },
    cancelAnimationFrame: id => animationFrames.delete(id),
    matchMedia: () => ({ matches: false }),
    getComputedStyle: element => ({ display: 'block', visibility: 'visible', opacity: '1',
      overflowX: 'visible', overflowY: 'visible', paddingLeft: '0px', paddingRight: '0px',
      paddingTop: '0px', paddingBottom: '0px', objectFit: element.style?.getPropertyValue('object-fit') || 'contain', ...element.computed }) });
  const bridge = { postMessage: text => messages.push(JSON.parse(text)) };
  window.MiowserLiveVideo = bridge;
  class MutationObserver {
    constructor(callback) { this.callback = callback; this.connected = true; mutations.push(this); }
    observe(target, options) { this.target = target; this.options = options; }
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
    performance: { now: () => time }, setTimeout: (fn, delay = 0) => { timers.set(next, {fn, at: time + delay}); return next++; },
    clearTimeout: id => timers.delete(id), setInterval: fn => { intervals.set(next, fn); return next++; },
    clearInterval: id => intervals.delete(id) });
  function run() { vm.runInContext(buttonSource + controlSource + source, context); }
  run();
  function bind(extra = {}) {
    const hello = messages.find(m => m.type === 'hello');
    assert(hello, 'no hello from a frame with video');
    bridge.onmessage({ data: JSON.stringify({ type: 'bind', documentId: hello.documentId,
      navigation: 3, token: 'frame-token', ...extra }) });
  }
  function advance(ms) {
    const end = time + ms;
    for (let count = 0; count < 1000; count++) {
      const nextTimer = [...timers.entries()].filter(([, t]) => t.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
      if (!nextTimer) break;
      time = Math.max(time, nextTimer[1].at); timers.delete(nextTimer[0]); nextTimer[1].fn(); render();
      if (count === 999) throw new Error('unbounded timeout loop');
    }
    time = end; render();
  }
  function flush() { advance(500); }
  function render() { const pending = [...animationFrames.values()]; animationFrames.clear(); pending.forEach(fn => fn()); }
  function mutate(added = []) {
    mutations.filter(m => m.connected).forEach(m => m.callback([{ type: 'childList', addedNodes: added }]));
  }
  function attribute(target, name) {
    mutations.filter(m => m.connected && (m.target === target || m.options?.subtree) && m.options?.attributes &&
      (!m.options.attributeFilter || m.options.attributeFilter.includes(name)))
      .forEach(m => m.callback([{type: 'attributes', target, attributeName: name, addedNodes: []}]));
  }
  function state() { return messages.filter(m => m.type === 'state').at(-1); }
  function button() { return elements.filter(e => e.tagName === 'BUTTON').at(-1); }
  function policy(enabled, extra = {}, credentials = {}) {
    const hello = messages.find(m => m.type === 'hello');
    bridge.onmessage({ data: JSON.stringify({ type: 'buttonPolicy', documentId: hello.documentId,
      navigation: 3, token: 'frame-token', ...credentials,
      button: { enabled, background: '#123456', foreground: '#FFFFFF', ...extra } }) });
  }
  function click(trusted = true) {
    button().dispatch('click', { isTrusted: trusted, preventDefault() {}, stopPropagation() {} });
  }
  let commandId = 0;
  function control(action, value, credentials = {}) {
    const hello = messages.find(m => m.type === 'hello');
    const active = messages.filter(m => m.type === 'fullscreenActive').at(-1);
    const latest = [...messages].reverse().find(m => m.videos?.some(v => v.elementId === active?.elementId));
    const revision = latest?.videos.find(v => v.elementId === active?.elementId)?.sourceRevision;
    const id = ++commandId;
    bridge.onmessage({data: JSON.stringify({type: 'control', documentId: hello.documentId,
      navigation: 3, token: 'frame-token', elementId: active?.elementId, attempt: active?.attempt,
      commandId: id, sourceRevision: revision, action, value, ...credentials})});
    return id;
  }
  function lifecycle(type, extra = {}, credentials = {}) {
    const hello = messages.find(m => m.type === 'hello'), active = messages.filter(m => m.type === 'fullscreenActive').at(-1);
    bridge.onmessage({data: JSON.stringify({type, documentId: hello.documentId, navigation: 3, token: 'frame-token',
      elementId: active?.elementId, attempt: active?.attempt, recoveryId: 0, ...extra, ...credentials})});
  }
  return { initial, window, document, messages, timers, intervals, children, intersections, mutations, attribute,
    run, bind, flush, advance, mutate, state, button, policy, click, render, elements, animationFrames, control, lifecycle, time: () => time };
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

function enabledFixture(initial) {
  const f = fixture(initial); f.bind(); f.policy(true); f.flush(); return f;
}
check('floating action is visible only after app policy enables it', () => {
  const f = fixture(); f.bind(); f.flush(); assert.equal(f.button(), undefined);
  f.policy(true); f.flush();
  assert.equal(f.button().hidden, false); assert.equal(f.button().disabled, false);
  assert.equal(f.button().style.width, '60px');
  assert.equal(f.button().attributes['aria-label'], 'Open video fullscreen with Miowser');
  assert.equal(f.button().children[0].style['background-color'], '#123456');
  assert.equal(f.button().parentElement.parentElement, null); // Closed shadow root, not a page layout wrapper.
});
check('disabled policy leaves no touchable action over the page', () => {
  const f = enabledFixture(); f.policy(false); f.flush();
  assert.equal(f.button().hidden, true); assert.equal(f.button().disabled, true);
  assert.equal(f.button().style.visibility, 'hidden');
  assert.equal(f.button().style['pointer-events'], 'none');
  f.click(); assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('trusted click opens the exact existing video without changing source/time/speed', () => {
  const f = enabledFixture(); const v = f.initial[0]; v.playbackRate = 1.5;
  f.click();
  assert.equal(f.document.fullscreenElement, v); assert.equal(v.fullscreenRequests, 1);
  assert.equal(v.currentTime, 12); assert.equal(v.playbackRate, 1.5);
  assert.equal(v.currentSrc, 'blob:https://site.test/video'); assert.equal(v.paused, false);
  assert.equal(v.controls, false); assert.equal(f.button().hidden, true);
});
check('return restores website controls and keeps the live position', () => {
  const f = enabledFixture(); const v = f.initial[0]; f.click();
  v.currentTime = 22; f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange'); f.flush();
  assert.equal(v.controls, false); assert.equal(v.currentTime, 22); assert.equal(v.paused, false);
  assert.equal(f.button().hidden, false);
});
check('already-enabled website controls remain enabled after return', () => {
  const v = video(); v.controls = true; const f = enabledFixture([v]); f.click();
  f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange');
  assert.equal(v.controls, true);
});
check('programmatic clicks cannot trigger the action', () => {
  const f = enabledFixture(); f.click(false); assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('a video paused before the click is revalidated', () => {
  const f = enabledFixture(); f.initial[0].paused = true; f.click();
  assert.equal(f.initial[0].fullscreenRequests, 0);
  f.initial[0].dispatch('pause'); f.flush(); assert.equal(f.button().hidden, true);
});
check('scrolling a video offscreen before the next frame cannot open a stale selection', () => {
  const f = enabledFixture();
  f.initial[0].getBoundingClientRect = () => ({left: 0, top: -500, width: 640, height: 360});
  f.click(); assert.equal(f.initial[0].fullscreenRequests, 0); assert.equal(f.button().hidden, true);
});
check('the most recently touched eligible video wins within a frame', () => {
  const first = video(), second = video('https://cdn.test/second.mp4');
  const f = enabledFixture([first, second]); second.dispatch('pointerdown'); f.flush(); f.click();
  assert.equal(f.document.fullscreenElement, second); assert.equal(first.fullscreenRequests, 0);
});
check('fullscreen rejection restores controls and reports a bounded failure', () => {
  const f = enabledFixture(); const v = f.initial[0];
  v.requestFullscreen = () => ({ catch(fn) { fn(new Error('denied')); } });
  f.click(); f.flush();
  assert.equal(v.controls, false); assert.equal(v.currentTime, 12); assert.equal(v.paused, false);
  assert.equal(f.messages.filter(m => m.type === 'fullscreenError').length, 1);
  assert.equal(f.button().hidden, false);
});
check('synchronous fullscreen failure does not strand the button', () => {
  const f = enabledFixture(); f.initial[0].requestFullscreen = () => { throw new Error('unsupported'); };
  f.click(); f.flush(); assert.equal(f.button().hidden, false); assert.equal(f.initial[0].controls, false);
});
check('button anchors below the clipped visible video without adding page space', () => {
  const v = video(); v.getBoundingClientRect = () => ({ left: -100, top: -30, width: 640, height: 360 });
  const f = enabledFixture([v]), b = f.button();
  assert.equal(b.style.top, '338px'); assert.equal(b.style.left, '480px');
});
check('overflow-clipping ancestors constrain placement', () => {
  const v = video(), parent = new Element('div');
  parent.getBoundingClientRect = () => ({ left: 200, top: 20, right: 400, bottom: 140, width: 200, height: 120 });
  parent.computed = { overflowX: 'hidden', overflowY: 'hidden' }; v.parentElement = parent;
  const f = enabledFixture([v]); assert.equal(f.button().style.left, '340px'); assert.equal(f.button().style.top, '148px');
});
check('pinch-zoom viewport preserves the apparent touch target size', () => {
  const f = enabledFixture();
  Object.assign(f.window.visualViewport, { scale: 2, offsetLeft: 200, offsetTop: 40, width: 540, height: 960 });
  f.window.visualViewport.dispatch('resize'); f.advance(250);
  assert.equal(f.button().style.width, '30px'); assert.equal(f.button().style.height, '30px');
  assert.equal(f.button().style.left, '610px'); assert.equal(f.button().style.top, '374px');
});
check('safe-area insets are respected inside the WebView viewport', () => {
  const f = fixture(); const original = f.window.getComputedStyle;
  f.window.visualViewport.width = 620;
  f.window.getComputedStyle = e => ({ ...original(e), ...(e.tagName === 'SPAN' ? {paddingRight: '40px'} : {}) });
  f.bind(); f.policy(true); f.flush(); assert.equal(f.button().style.left, '520px');
});
check('small previews, hidden videos and denied fullscreen do not get a button', () => {
  const tiny = video(); tiny.getBoundingClientRect = () => ({left: 0, top: 0, width: 100, height: 60});
  const a = enabledFixture([tiny]); assert.equal(a.button(), undefined);
  const hidden = video(); hidden.computed = {opacity: '0'};
  const b = enabledFixture([hidden]); assert.equal(b.button(), undefined);
  const c = fixture(); c.document.fullscreenEnabled = false; c.bind(); c.policy(true); c.flush();
  assert.equal(c.button(), undefined);
});
check('an element removed from the page cannot retain an active button', () => {
  const f = enabledFixture(); f.initial[0].isConnected = false; f.mutate(); f.flush();
  assert.equal(f.button().hidden, true);
});
check('pagehide removes the host and cancels animation-frame work', () => {
  const f = enabledFixture(); f.window.dispatch('scroll'); f.window.dispatch('pagehide');
  assert.equal(f.button().isConnected, false); assert.equal(f.animationFrames.size, 0);
});
check('scroll bursts coalesce geometry work without waiting for telemetry', () => {
  const f = enabledFixture(); f.initial[0].getBoundingClientRect = () => ({left: 0, top: 100, width: 640, height: 360});
  for (let i = 0; i < 100; i++) f.window.dispatch('scroll');
  assert.equal(f.animationFrames.size, 1); f.advance(250); assert.equal(f.button().style.top, '468px');
});
check('stale policy tokens cannot re-enable a hidden button', () => {
  const f = enabledFixture(); f.policy(false); f.flush();
  f.policy(true, {}, {token: 'old-frame-token'}); f.flush(); assert.equal(f.button().hidden, true);
});
check('reduced motion suppresses the entrance animation', () => {
  const f = fixture(); f.bind(); f.policy(true, {reducedMotion: true}); f.flush();
  assert.equal(f.button().animationCount, 0);
});
check('main-frame navigation reset hides the old document action immediately', () => {
  const f = enabledFixture(), doc = f.messages.find(m => m.type === 'hello').documentId;
  // Simulate the app's navigation invalidation before the old document is detached.
  const bridge = f.window.MiowserLiveVideo;
  bridge.onmessage({data: JSON.stringify({type: 'reset', documentId: doc})});
  assert.equal(f.button().hidden, true);
});

function playingFixture() { const f = enabledFixture(); f.click(); return f; }
function ack(f, id) { return f.messages.filter(m => m.type === 'controlAck' && m.commandId === id).at(-1); }
check('browser controls are hidden from entry and an explicit fallback restores them', () => {
  const f = playingFixture(); assert.equal(f.initial[0].controls, false);
  const id = f.control('richUi', 1);
  assert.equal(ack(f, id).ok, true); assert.equal(f.initial[0].controls, false);
  f.control('richUi', 0); assert.equal(f.initial[0].controls, true);
});
check('browser controls restore on exit after using the rich UI', () => {
  const f = playingFixture(); f.control('richUi', 1);
  f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange');
  assert.equal(f.initial[0].controls, false); // Original website had custom inline controls.
  assert.equal(f.messages.filter(m => m.type === 'fullscreenEnded').length, 1);
});
check('transport commands control only the owned video and report immediately', () => {
  const f = playingFixture(), v = f.initial[0];
  const pause = f.control('pause'); assert.equal(v.paused, true); assert.equal(ack(f, pause).ok, true);
  const play = f.control('play'); assert.equal(v.paused, false); assert.equal(ack(f, play).ok, true);
  assert.equal(v.currentTime, 12); assert.equal(v.currentSrc, 'blob:https://site.test/video');
});
check('seek works with a finite VOD duration even when seekable is empty', () => {
  const f = playingFixture(); f.initial[0].seekable.length = 0;
  const id = f.control('seek', 25);
  assert.equal(f.initial[0].currentTime, 25); assert.equal(ack(f, id).ok, true);
});
check('skip uses fresh DOM time instead of the older native sample', () => {
  const f = playingFixture(); f.initial[0].currentTime = 18;
  const id = f.control('skip', 10);
  assert.equal(f.initial[0].currentTime, 28); assert.equal(ack(f, id).ok, true);
});
check('seek targets are clamped around holes in the actual live window', () => {
  const f = playingFixture();
  f.initial[0].seekable = {length: 2, start: i => i === 0 ? 5 : 20, end: i => i === 0 ? 10 : 30};
  f.control('seek', 14); assert.equal(f.initial[0].currentTime, 10);
  f.control('seek', 18); assert.equal(f.initial[0].currentTime, 20);
  f.control('seek', 100); assert.equal(f.initial[0].currentTime, 30);
});
check('unknown live media does not invent a seekable window', () => {
  const f = playingFixture(); f.initial[0].seekable.length = 0; f.initial[0].duration = Infinity;
  const id = f.control('seek', 20);
  assert.equal(ack(f, id).ok, false); assert.equal(f.initial[0].currentTime, 12);
});
check('asynchronous seek waits for seeked and retains the requested pause state', () => {
  const f = playingFixture(); f.initial[0].seeking = true; f.initial[0].paused = true;
  const id = f.control('seek', 20);
  assert.equal(ack(f, id), undefined);
  assert(f.messages.some(m => m.type === 'controlStarted' && m.commandId === id));
  f.initial[0].seeking = false; f.initial[0].dispatch('seeked');
  assert.equal(ack(f, id).ok, true); assert.equal(f.initial[0].paused, true);
});
check('ignored seek setters produce an error rather than a false success', () => {
  const f = playingFixture();
  Object.defineProperty(f.initial[0], 'currentTime', {get: () => 12, set() {}});
  const id = f.control('seek', 25); assert.equal(ack(f, id), undefined);
  f.advance(7000); assert.equal(ack(f, id).ok, false);
});
check('a newer seek cancels the old confirmation listener', () => {
  const f = playingFixture(); f.initial[0].seeking = true;
  const first = f.control('seek', 20), second = f.control('seek', 30);
  assert.equal(ack(f, first).ok, false); assert.equal(ack(f, second), undefined);
  f.initial[0].seeking = false; f.initial[0].dispatch('seeked');
  assert.equal(ack(f, second).ok, true); assert.equal(f.initial[0].currentTime, 30);
});
check('rate changes acknowledge the real applied speed', () => {
  const f = playingFixture(); const id = f.control('rate', 1.5);
  assert.equal(ack(f, id).ok, true); assert.equal(f.initial[0].playbackRate, 1.5);
  const bad = f.control('rate', 8); assert.equal(ack(f, bad).ok, false);
  assert.equal(f.initial[0].playbackRate, 1.5);
});
check('wrong-frame, element, attempt and navigation commands are ignored', () => {
  const f = playingFixture();
  f.control('pause', undefined, {token: 'other-frame'});
  f.control('pause', undefined, {elementId: 'other-video'});
  f.control('pause', undefined, {attempt: 999});
  f.control('pause', undefined, {navigation: 1});
  assert.equal(f.initial[0].paused, false);
});
check('commands cannot affect inline media after fullscreen exit', () => {
  const f = playingFixture(); f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange');
  f.control('pause'); assert.equal(f.initial[0].paused, false);
});
check('ordinary site-created fullscreen never becomes an add-on control target', () => {
  const f = enabledFixture(); f.document.fullscreenElement = f.initial[0]; f.document.dispatch('fullscreenchange');
  assert.equal(f.messages.filter(m => m.type === 'fullscreenActive').length, 0);
  f.control('pause'); assert.equal(f.initial[0].paused, false);
});
check('command replies preserve same-URL video identities and do not drop sibling videos', () => {
  const f = enabledFixture([video(), video()]); f.click();
  const id = f.control('pause'); assert.equal(ack(f, id).videos.length, 2);
  assert.equal(new Set(ack(f, id).videos.map(v => v.elementId)).size, 2);
});
check('pagehide releases seek listeners and sends no late acknowledgement', () => {
  const f = playingFixture(); f.initial[0].seeking = true;
  const id = f.control('seek', 25); f.window.dispatch('pagehide');
  f.initial[0].seeking = false; f.initial[0].dispatch('seeked'); f.flush();
  assert.equal(ack(f, id), undefined); assert.equal(f.timers.size, 0);
});
check('replayed commands cannot reapply a previous pause', () => {
  const f = playingFixture(); const first = f.control('pause'); f.control('play');
  f.control('pause', undefined, {commandId: first}); assert.equal(f.initial[0].paused, false);
});
check('mute controls the current element without seeking, source replacement or pausing', () => {
  const f = playingFixture(), v = f.initial[0], source = v.currentSrc;
  assert.equal(ack(f, f.control('mute', 1)).ok, true); assert.equal(v.muted, true);
  assert.equal(ack(f, f.control('mute', 0)).ok, true); assert.equal(v.muted, false);
  assert.equal(ack(f, f.control('mute', 4)).ok, false);
  assert.equal(v.currentTime, 12); assert.equal(v.currentSrc, source); assert.equal(v.paused, false);
});
check('scaling applies to the owning video and exit restores the exact inline style priority', () => {
  const v = video(); v.style.setProperty('object-fit', 'scale-down', 'important');
  const f = enabledFixture([v]); f.click();
  assert.equal(ack(f, f.control('scale', 1)).ok, true); assert.equal(v.style.getPropertyValue('object-fit'), 'cover');
  assert.equal(ack(f, f.control('scale', 2)).videos[0].scale, 'fill');
  assert.equal(ack(f, f.control('scale', 0)).videos[0].scale, 'contain');
  assert.equal(ack(f, f.control('scale', 99)).ok, false);
  f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange');
  assert.equal(v.style.getPropertyValue('object-fit'), 'scale-down'); assert.equal(v.style.getPropertyPriority('object-fit'), 'important');
});
check('scaling with no previous inline property removes it on pagehide', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('scale', 1); f.window.dispatch('pagehide');
  assert.equal(v.style.getPropertyValue('object-fit'), ''); assert.equal(v.style.getPropertyPriority('object-fit'), '');
});
function captionFixture() {
  const v = video();
  v.textTracks = tracks([{kind: 'subtitles', label: 'English', language: 'en', mode: 'showing'},
    {kind: 'captions', label: 'French', language: 'fr', mode: 'disabled'},
    {kind: 'metadata', label: 'provider metadata', mode: 'hidden'}]);
  v.audioTracks = tracks([{label: 'Original', language: 'ja', enabled: true}, {label: 'Dub', language: 'en', enabled: false}]);
  const f = enabledFixture([v]); f.click(); return f;
}
check('caption selection and Off leave provider metadata tracks alone', () => {
  const f = captionFixture(), v = f.initial[0];
  const meta = f.messages.filter(m => m.type === 'fullscreenActive').at(-1).videos[0];
  assert.equal(meta.captions.length, 2);
  assert.equal(ack(f, f.control('caption', meta.captions[1].id)).ok, true);
  assert.equal(v.textTracks[0].mode, 'disabled'); assert.equal(v.textTracks[1].mode, 'showing');
  assert.equal(v.textTracks[2].mode, 'hidden');
  assert.equal(ack(f, f.control('caption', -1)).ok, true);
  assert.equal(v.textTracks[1].mode, 'disabled'); assert.equal(v.textTracks[2].mode, 'hidden');
});
check('track identity survives reorder and stale selections fail safely', () => {
  const f = captionFixture(), v = f.initial[0];
  const id = f.messages.filter(m => m.type === 'fullscreenActive').at(-1).videos[0].captions[1].id;
  v.textTracks.reverse(); assert.equal(ack(f, f.control('caption', id)).ok, true);
  assert.equal(v.textTracks[1].mode, 'showing');
  v.textTracks.splice(1, 1); assert.equal(ack(f, f.control('caption', id)).ok, false);
  assert.equal(v.textTracks.find(t => t.kind === 'subtitles').mode, 'disabled');
});
check('audio selection only changes exposed tracks and rejects missing IDs', () => {
  const f = captionFixture(), v = f.initial[0];
  const meta = f.messages.filter(m => m.type === 'fullscreenActive').at(-1).videos[0];
  assert.equal(ack(f, f.control('audio', meta.audioTracks[1].id)).ok, true);
  assert.equal(v.audioTracks[1].enabled, true); assert.equal(v.audioTracks[0].enabled, false);
  assert.equal(ack(f, f.control('audio', -1)).ok, false); assert.equal(v.audioTracks[1].enabled, true);
  assert.equal(v.currentTime, 12); assert.equal(v.paused, false);
});
check('track capabilities are owner-only, bounded and keep packets under the bridge limit', () => {
  const vs = Array.from({length: 8}, () => {
    const v = video('https://cdn.test/' + 'x'.repeat(8000));
    v.textTracks = tracks(Array.from({length: 150}, () => ({kind: 'subtitles', label: 'x'.repeat(1000), language: 'l'.repeat(50), mode: 'disabled'})));
    v.audioTracks = tracks(Array.from({length: 150}, () => ({label: 'x'.repeat(1000), language: 'a'.repeat(50), enabled: false})));
    return v;
  });
  const f = enabledFixture(vs); assert(f.state().videos.every(v => v.captions === undefined)); f.click();
  const packet = f.messages.filter(m => m.type === 'fullscreenActive').at(-1), owned = packet.videos.find(v => v.elementId === packet.elementId);
  assert.equal(owned.captions.length, 16); assert.equal(owned.audioTracks.length, 8);
  assert.equal(owned.captions[0].label.length, 80); assert.equal(owned.captions[0].language.length, 16);
  assert.equal(packet.videos.filter(v => v.captions !== undefined).length, 1); assert(JSON.stringify(packet).length <= 24576);
});
check('track events schedule bounded telemetry and listeners are removed on pagehide', () => {
  const f = captionFixture(), v = f.initial[0]; f.flush(); const before = f.messages.length;
  for (let n = 0; n < 50; n++) v.textTracks.dispatch('change');
  f.flush(); assert.equal(f.messages.length - before, 1);
  f.window.dispatch('pagehide');
  assert.equal(v.textTracks.events.get('change').length, 0); assert.equal(v.audioTracks.events.get('addtrack').length, 0);
});
check('additional controls reject stale fullscreen attempts', () => {
  const f = captionFixture(), v = f.initial[0];
  ['mute', 'scale', 'caption', 'audio'].forEach(action => f.control(action, 1, {attempt: 999}));
  assert.equal(v.muted, false); assert.equal(v.style.getPropertyValue('object-fit'), '');
  assert.equal(v.textTracks[0].mode, 'showing'); assert.equal(v.audioTracks[0].enabled, true);
});
check('temporary speed restores the fresh DOM speed on release and only once', () => {
  const f = playingFixture(), v = f.initial[0]; v.playbackRate = 1.25;
  assert.equal(ack(f, f.control('holdRate')).ok, true); assert.equal(v.playbackRate, 2);
  f.control('holdRate'); f.control('releaseRate'); assert.equal(v.playbackRate, 1.25);
  v.playbackRate = 1.5; f.control('releaseRate'); assert.equal(v.playbackRate, 1.5);
});
check('fullscreen exit and page teardown restore held speed even after native ownership is gone', () => {
  for (const exit of ['fullscreen', 'pagehide']) {
    const f = playingFixture(), v = f.initial[0]; v.playbackRate = 0.75; f.control('holdRate');
    if (exit === 'fullscreen') { f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange'); }
    else f.window.dispatch('pagehide');
    assert.equal(v.playbackRate, 0.75);
  }
});
check('temporary-speed cleanup respects explicit speed choices and provider source/rate changes', () => {
  const f = playingFixture(), v = f.initial[0];
  f.control('holdRate'); f.control('rate', 1.5); f.control('releaseRate'); assert.equal(v.playbackRate, 1.5);
  f.control('holdRate'); v.playbackRate = 1.25; f.control('releaseRate'); assert.equal(v.playbackRate, 1.25);
  f.control('holdRate'); v.currentSrc = 'blob:https://site.test/replacement'; f.control('releaseRate');
  assert.equal(v.playbackRate, 2); // Do not write an old source's speed onto its replacement.
});
check('rich UI fallback and navigation invalidation release temporary speed', () => {
  for (const exit of ['fallback', 'reset']) {
    const f = playingFixture(), v = f.initial[0]; v.playbackRate = 1.5; f.control('holdRate');
    if (exit === 'fallback') f.control('richUi', 0);
    else f.window.MiowserLiveVideo.onmessage({data: JSON.stringify({type: 'reset', documentId: f.messages.find(m => m.type === 'hello').documentId})});
    assert.equal(v.playbackRate, 1.5);
  }
});
check('background and resume reconcile the same video without replaying or seeking it', () => {
  const f = playingFixture(), v = f.initial[0], source = v.currentSrc;
  f.control('richUi', 1); v.playbackRate = 1.25; f.control('holdRate');
  f.lifecycle('suspendFullscreen', {pip: false});
  assert.equal(v.playbackRate, 1.25); assert.equal(v.controls, false); assert.equal(v.paused, false);
  v.currentTime = 29;
  f.lifecycle('resumeFullscreen', {recoveryId: 71});
  const response = f.messages.filter(m => m.type === 'fullscreenResumed').at(-1);
  assert.equal(response.ok, true); assert.equal(response.recoveryId, 71); assert.equal(response.videos[0].position, 29);
  assert.equal(v.controls, false); assert.equal(v.currentSrc, source); assert.equal(v.playbackRate, 1.25);
});
check('explicit PiP suspension hides browser controls without changing pause intent', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('richUi', 1);
  f.lifecycle('suspendFullscreen', {pip: true}); assert.equal(v.controls, false); assert.equal(v.paused, false);
  f.document.visibilityState = 'hidden'; v.currentTime = 38; f.flush();
  f.document.visibilityState = 'visible'; f.lifecycle('resumeFullscreen', {recoveryId: 72});
  assert.equal(f.messages.filter(m => m.type === 'fullscreenResumed').at(-1).ok, true);
  assert.equal(v.currentTime, 38); assert.equal(v.paused, false);
});
check('resume preserves a manually paused website video', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('pause'); f.lifecycle('suspendFullscreen');
  f.lifecycle('resumeFullscreen', {recoveryId: 73}); assert.equal(v.paused, true); assert.equal(v.currentTime, 12);
});
check('lifecycle messages reject wrong frames, navigation, elements and entry attempts', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('richUi', 1);
  for (const identity of [{token: 'foreign'}, {documentId: 'foreign'}, {navigation: 99}, {elementId: 'foreign'}, {attempt: 99}]) {
    f.lifecycle('releaseFullscreen', {}, identity); f.lifecycle('fallbackFullscreen', {}, identity);
    f.lifecycle('resumeFullscreen', {recoveryId: 1}, identity);
  }
  assert.equal(f.document.fullscreenElement, v); assert.equal(v.controls, false);
  assert(f.messages.filter(m => m.type === 'fullscreenResumed').every(m => m.ok === false));
});
check('resume failure never switches to a different playing element', () => {
  const first = video(), second = video(); const f = enabledFixture([first, second]); f.click();
  const selected = f.document.fullscreenElement, other = selected === first ? second : first;
  f.document.fullscreenElement = other; f.document.dispatch('fullscreenchange');
  f.lifecycle('resumeFullscreen', {recoveryId: 74});
  assert.equal(f.messages.filter(m => m.type === 'fullscreenResumed').at(-1).ok, false);
  assert.equal(other.currentTime, 12); assert.equal(other.controls, false);
});
check('native release restores the website styles and hold speed and exits only once', () => {
  const f = playingFixture(), v = f.initial[0]; let exits = 0;
  f.document.exitFullscreen = function () { exits++; this.fullscreenElement = null; this.dispatch('fullscreenchange'); return {catch() {}}; };
  v.playbackRate = 0.75; f.control('richUi', 1); f.control('holdRate'); f.control('scale', 1);
  f.lifecycle('releaseFullscreen'); f.lifecycle('releaseFullscreen');
  assert.equal(exits, 1); assert.equal(v.controls, false); assert.equal(v.style.getPropertyValue('object-fit'), '');
  assert.equal(v.playbackRate, 0.75); assert.equal(v.currentTime, 12); assert.equal(v.paused, false);
});
check('failed DOM exit exposes browser controls and does not interrupt playback', () => {
  const f = playingFixture(), v = f.initial[0];
  f.document.exitFullscreen = () => ({catch(reject) { reject(); }});
  f.control('richUi', 1); f.lifecycle('releaseFullscreen');
  assert.equal(v.controls, true); assert.equal(v.paused, false); assert.equal(v.currentTime, 12);
});
check('native fallback and channel reset make website controls available', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('richUi', 1); f.lifecycle('fallbackFullscreen');
  assert.equal(v.controls, true); assert.equal(f.document.fullscreenElement, v);
  f.control('richUi', 1);
  f.window.MiowserLiveVideo.onmessage({data: JSON.stringify({type: 'reset', documentId: f.messages.find(m => m.type === 'hello').documentId})});
  assert.equal(v.controls, true); assert.equal(v.paused, false);
});
check('a hanging fullscreen request times out and can be tried again', () => {
  const f = enabledFixture(), v = f.initial[0]; v.requestFullscreen = () => ({catch() {}});
  f.click(); assert.equal(v.controls, false); f.advance(8000);
  assert.equal(v.controls, false); assert.equal(f.messages.filter(m => m.type === 'fullscreenError').length, 1);
  assert.equal(f.button().hidden, false);
  v.requestFullscreen = () => { f.document.fullscreenElement = v; f.document.dispatch('fullscreenchange'); return {catch() {}}; };
  f.click(); assert.equal(f.messages.filter(m => m.type === 'fullscreenActive').length, 1);
});
check('late rejection from an expired entry cannot tear down a newer fullscreen entry', () => {
  const f = enabledFixture(), v = f.initial[0]; let lateReject;
  v.requestFullscreen = () => ({catch(reject) { lateReject = reject; }});
  f.click(); f.advance(8000);
  v.requestFullscreen = () => { f.document.fullscreenElement = v; f.document.dispatch('fullscreenchange'); return {catch() {}}; };
  f.click(); f.control('richUi', 1); lateReject();
  assert.equal(v.controls, false); assert.equal(f.document.fullscreenElement, v);
  assert.equal(f.messages.filter(m => m.type === 'fullscreenError').length, 1);
});
check('an earlier entry release cannot exit the newer entry of the same video', () => {
  const f = playingFixture(), v = f.initial[0];
  const old = f.messages.find(m => m.type === 'fullscreenActive'); f.lifecycle('releaseFullscreen');
  f.render(); f.click(); f.control('richUi', 1);
  f.lifecycle('releaseFullscreen', {}, {attempt: old.attempt});
  assert.equal(f.document.fullscreenElement, v); assert.equal(v.controls, false);
});
check('commands for an older media source cannot seek or pause its replacement', () => {
  const f = playingFixture(), v = f.initial[0];
  v.currentSrc = 'https://cdn.test/new.mp4'; v.currentTime = 3;
  const stale = f.control('seek', 25); assert.equal(ack(f, stale).ok, false); assert.equal(v.currentTime, 3);
  const fresh = f.control('seek', 6); assert.equal(ack(f, fresh).ok, true); assert.equal(v.currentTime, 6);
  assert.equal(ack(f, f.control('pause', undefined, {sourceRevision: 1})).ok, false); assert.equal(v.paused, false);
});
check('source replacement cancels pending seek listeners before their late completion', () => {
  const f = playingFixture(), v = f.initial[0]; v.seeking = true; const seek = f.control('seek', 25);
  v.currentSrc = 'blob:https://site.test/new'; v.currentTime = 2; v.dispatch('loadstart'); f.flush();
  assert.equal(ack(f, seek).ok, false); const count = f.messages.filter(m => m.type === 'controlAck' && m.commandId === seek).length;
  v.seeking = false; v.dispatch('seeked'); f.flush();
  assert.equal(f.messages.filter(m => m.type === 'controlAck' && m.commandId === seek).length, count); assert.equal(v.currentTime, 2);
});
check('late lifecycle requests cannot undo a newer suspension or resume', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('richUi', 1);
  f.lifecycle('suspendFullscreen', {recoveryId: 9, pip: false}); assert.equal(v.controls, false);
  f.lifecycle('resumeFullscreen', {recoveryId: 8}); assert.equal(v.controls, false);
  assert.equal(f.messages.filter(m => m.type === 'fullscreenResumed').length, 0);
  f.lifecycle('resumeFullscreen', {recoveryId: 10}); assert.equal(v.controls, false);
  f.lifecycle('fallbackFullscreen', {recoveryId: 9}); assert.equal(v.controls, false);
  f.lifecycle('releaseFullscreen', {recoveryId: 8}); assert.equal(f.document.fullscreenElement, v);
});
check('an invalid lifecycle request cannot advance the current entry generation', () => {
  const f = playingFixture(), v = f.initial[0]; f.control('richUi', 1);
  f.lifecycle('suspendFullscreen', {recoveryId: 999}, {attempt: 99});
  f.lifecycle('suspendFullscreen', {recoveryId: 1}); assert.equal(v.controls, false);
  f.lifecycle('resumeFullscreen', {recoveryId: -1}); assert.equal(v.controls, false);
  f.lifecycle('resumeFullscreen', {recoveryId: 2}); assert.equal(v.controls, false);
});
check('explicit PiP dismissal pauses the same owned video even if its source just changed', () => {
  const f = playingFixture(), v = f.initial[0]; f.lifecycle('suspendFullscreen', {pip: true});
  v.currentSrc = 'blob:https://site.test/next'; v.currentTime = 3;
  assert.equal(ack(f, f.control('pause', undefined, {sourceRevision: -1})).ok, true);
  assert.equal(v.paused, true); assert.equal(v.currentTime, 3);
  f.control('play');
  f.control('pause', undefined, {sourceRevision: -1, attempt: 99}); assert.equal(v.paused, false);
});
function controlSheets(f) { return f.elements.filter(e => e.tagName === 'STYLE' && e.isConnected); }
check('UA controls are hidden before fullscreen entry with selectors scoped to the owned video', () => {
  const other = video(), selected = video(); const f = enabledFixture([other, selected]);
  selected.dispatch('pointerdown'); f.flush(); f.click();
  const token = selected.getAttribute('data-miowser-rich-controls'), css = controlSheets(f).map(s => s.textContent).join('');
  assert(token); assert(css.includes('video[data-miowser-rich-controls="' + token + '"]::-webkit-media-controls{display:none!important;'));
  assert(css.includes('pointer-events:none!important')); assert(!css.includes('::cue'));
  assert.equal(selected.style.getPropertyValue('pointer-events'), 'none'); assert.equal(other.getAttribute('data-miowser-rich-controls'), null);
  assert.equal(other.style.getPropertyValue('pointer-events'), ''); assert.equal(selected.controls, false);
  assert.equal(selected.currentTime, 12); assert.equal(selected.paused, false);
});
check('provider controls changes coalesce to one frame and cannot re-enable the UI', () => {
  const f = playingFixture(), v = f.initial[0]; f.render();
  v.controls = true;
  for (let n = 0; n < 200; n++) f.attribute(v, 'controls');
  assert.equal(f.animationFrames.size, 1); f.render(); assert.equal(v.controls, false);
  assert.equal(controlSheets(f).length, 1);
  assert.equal(f.mutations.filter(m => m.connected && m.target === v).length, 1);
});
check('repeated attachment reuses the guard and fallback releases all hidden-control state', () => {
  const f = playingFixture(), v = f.initial[0]; const token = v.getAttribute('data-miowser-rich-controls');
  for (let n = 0; n < 20; n++) f.control('richUi', 1);
  assert.equal(v.getAttribute('data-miowser-rich-controls'), token); assert.equal(controlSheets(f).length, 1);
  f.control('richUi', 0);
  assert.equal(v.controls, true); assert.equal(controlSheets(f).length, 0); assert.equal(v.getAttribute('data-miowser-rich-controls'), null);
  assert.equal(v.style.getPropertyValue('pointer-events'), ''); assert.equal(f.mutations.filter(m => m.connected && m.target === v).length, 0);
  f.control('richUi', 1); assert.equal(controlSheets(f).length, 1); assert.equal(v.controls, false);
});
check('return restores prior pointer styles, priority and even an existing empty marker', () => {
  const v = video(); v.controls = true; v.style.setProperty('pointer-events', 'auto', 'important');
  v.setAttribute('data-miowser-rich-controls', ''); const f = enabledFixture([v]); f.click();
  f.lifecycle('releaseFullscreen');
  assert.equal(v.controls, true); assert.equal(v.style.getPropertyValue('pointer-events'), 'auto');
  assert.equal(v.style.getPropertyPriority('pointer-events'), 'important'); assert.equal(v.getAttribute('data-miowser-rich-controls'), '');
  assert.equal(controlSheets(f).length, 0);
});
check('entry failure and pagehide remove guards and cannot keep changing website controls', () => {
  for (const exit of ['failure', 'pagehide', 'fullscreen']) {
    const f = enabledFixture(), v = f.initial[0];
    if (exit === 'failure') v.requestFullscreen = () => { throw new Error('denied'); };
    f.click();
    if (exit === 'pagehide') f.window.dispatch('pagehide');
    if (exit === 'fullscreen') { v.controls = true; f.attribute(v, 'controls'); f.lifecycle('releaseFullscreen'); }
    assert.equal(controlSheets(f).length, 0); assert.equal(v.getAttribute('data-miowser-rich-controls'), null);
    assert.equal(f.mutations.filter(m => m.connected && m.target === v).length, 0);
    v.controls = true; f.render(); assert.equal(v.controls, true);
  }
});
check('suppression also works in an element shadow root without styling other videos', () => {
  const shadow = new Element('shadow'); shadow.setConnected(true);
  const v = video(); v.getRootNode = () => shadow; const f = enabledFixture([v]); f.click();
  assert.equal(shadow.children.filter(e => e.tagName === 'STYLE').length, 1);
  f.lifecycle('releaseFullscreen'); assert.equal(shadow.children.length, 0);
});
check('constructable stylesheet path keeps and restores the providers existing sheets', () => {
  const f = enabledFixture(), v = f.initial[0], provider = {};
  f.document.adoptedStyleSheets = [provider];
  f.window.CSSStyleSheet = class { replaceSync(css) { this.css = css; } };
  f.click(); assert.equal(f.document.adoptedStyleSheets.length, 2);
  assert(f.document.adoptedStyleSheets[1].css.includes('::-webkit-media-controls'));
  assert.equal(controlSheets(f).length, 0);
  f.lifecycle('releaseFullscreen'); assert.equal(f.document.adoptedStyleSheets.length, 1);
  assert.equal(f.document.adoptedStyleSheets[0], provider);
});
check('ordinary site fullscreen never installs the native-control guard', () => {
  const f = enabledFixture(), v = f.initial[0]; v.controls = true;
  f.document.fullscreenElement = v; f.document.dispatch('fullscreenchange');
  assert.equal(controlSheets(f).length, 0); assert.equal(v.controls, true);
  assert.equal(v.style.getPropertyValue('pointer-events'), '');
});
check('background and PiP roundtrips retain a single guard until the exact owner exits', () => {
  const f = playingFixture(), v = f.initial[0]; const token = v.getAttribute('data-miowser-rich-controls');
  f.lifecycle('suspendFullscreen', {recoveryId: 3}); f.lifecycle('resumeFullscreen', {recoveryId: 4});
  f.lifecycle('suspendFullscreen', {recoveryId: 5, pip: true}); f.lifecycle('resumeFullscreen', {recoveryId: 6});
  assert.equal(v.getAttribute('data-miowser-rich-controls'), token); assert.equal(controlSheets(f).length, 1); assert.equal(v.controls, false);
  f.lifecycle('releaseFullscreen', {recoveryId: 7}); assert.equal(controlSheets(f).length, 0);
});
function youtubeBodyFixture(rootOverflow = 'visible', containment = 'none') {
  const v = video(), body = new Element('body'), player = new Element('div');
  v.getBoundingClientRect = () => ({left: 0, top: 60, width: 490, height: 276});
  player.getBoundingClientRect = () => ({left: 0, top: 56, right: 490, bottom: 340, width: 490, height: 284});
  player.computed = {overflowX: 'hidden', overflowY: 'hidden'};
  body.getBoundingClientRect = () => ({left: 0, top: 0, right: 490, bottom: 0, width: 490, height: 0});
  body.computed = {overflowX: 'auto', overflowY: 'scroll', contain: containment};
  v.parentElement = player; player.parentElement = body;
  const f = fixture([v]); body.parentElement = f.document.documentElement;
  f.document.body = body; f.document.scrollingElement = f.document.documentElement;
  f.document.documentElement.computed = {overflowX: rootOverflow, overflowY: rootOverflow, contain: 'none'};
  return {f, body, player, v};
}
check('YouTube-shaped viewport overflow does not clip a visible video to a zero-height body', () => {
  const {f, v} = youtubeBodyFixture(); f.bind(); f.policy(true); f.flush();
  assert.equal(f.button().hidden, false); assert.equal(f.button().style.top, '344px');
  assert.equal(f.button().style.left, '430px'); f.click(); assert.equal(f.document.fullscreenElement, v);
});
check('viewport overflow exception still respects the actual nested player clipping', () => {
  const {f, player} = youtubeBodyFixture();
  player.getBoundingClientRect = () => ({left: 0, top: 56, right: 490, bottom: 95, width: 490, height: 39});
  f.bind(); f.policy(true); f.flush(); assert.notEqual(f.button()?.hidden, false);
});
check('body overflow remains a local clip when the root or containment prevents propagation', () => {
  for (const options of [['auto', 'none'], ['visible', 'paint']]) {
    const {f} = youtubeBodyFixture(...options); f.bind(); f.policy(true); f.flush();
    assert.notEqual(f.button()?.hidden, false);
  }
});
check('a body that is itself the document scroller uses viewport bounds', () => {
  const {f, body} = youtubeBodyFixture('auto'); f.document.scrollingElement = body;
  f.bind(); f.policy(true); f.flush(); assert.equal(f.button().hidden, false);
});
check('the viewport clipping fix does not bypass an iframe fullscreen denial', () => {
  const {f, v} = youtubeBodyFixture(); f.document.fullscreenEnabled = false;
  f.bind(); f.policy(true); f.flush(); assert.equal(f.button(), undefined); assert.equal(v.fullscreenRequests, 0);
});
check('layout discovery never reads or changes a videos DRM session', () => {
  const {f, v} = youtubeBodyFixture(); let keyReads = 0;
  Object.defineProperty(v, 'mediaKeys', {get() { keyReads++; throw new Error('DRM is outside this feature'); }});
  v.setMediaKeys = () => { throw new Error('DRM session must not be changed'); };
  f.bind(); f.policy(true); f.flush(); f.click(); f.control('richUi', 1); f.control('holdRate'); f.control('releaseRate');
  assert.equal(keyReads, 0); assert.equal(v.currentTime, 12); assert.equal(v.paused, false);
});
check('an embedded frame opens its existing video from one floating-button tap', () => {
  const f = fixture();
  // A frame-local document cannot access a cross-origin top document.
  Object.defineProperty(f.window, 'top', {get() {throw new Error('top document must not be inspected');}});
  f.bind(); f.policy(true); f.flush();
  assert.equal(f.button().hidden, false);
  f.click();
  assert.equal(f.initial[0].fullscreenRequests, 1);
  assert.equal(f.document.fullscreenElement, f.initial[0]);
  assert.equal(f.initial[0].currentTime, 12); assert.equal(f.initial[0].paused, false);
  assert.equal(f.messages.filter(m => m.type === 'fullscreenActive').length, 1);
});
check('legacy toolbar arm messages cannot turn an ordinary page tap into fullscreen', () => {
  const f = enabledFixture(), doc = f.messages.find(m => m.type === 'hello').documentId;
  f.window.MiowserLiveVideo.onmessage({data: JSON.stringify({type: 'armToolbarEntry', documentId: doc,
    navigation: 3, token: 'frame-token', elementId: f.state().videos[0].elementId,
    sourceRevision: f.state().videos[0].sourceRevision})});
  f.document.dispatch('click', {isTrusted: true, target: f.initial[0], clientX: 100, clientY: 100,
    preventDefault() {throw new Error('ordinary page input must not be consumed');}});
  assert.equal(f.initial[0].fullscreenRequests, 0);
  f.click(); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('floating entry does not insert a layout row or wrap the video', () => {
  const f = enabledFixture(), v = f.initial[0], parent = v.parentElement;
  const hosts = f.elements.filter(e => e.getAttribute('data-miowser-video-assistant') !== null);
  assert.equal(hosts.length, 1); assert.equal(hosts[0].style.position, 'fixed');
  assert.equal(hosts[0].style.width, '0'); assert.equal(hosts[0].style.height, '0');
  assert.equal(f.button().style.position, 'fixed');
  assert.equal(v.parentElement, parent);
});
function nativeFixture() {
  const f = fixture(); f.bind(); f.policy(true, {nativeDisplay: true}); f.flush();
  f.display = () => f.messages.filter(m => m.type === 'assistantDisplay' && m.visible).at(-1);
  f.nativeAck = (version, ok, credentials = {}) => f.window.MiowserLiveVideo.onmessage({data: JSON.stringify({
    type: 'nativeDisplayReady', documentId: f.messages.find(m => m.type === 'hello').documentId,
    navigation: 3, token: 'frame-token', version, ok, ...credentials})});
  return f;
}
function nativePressFixture() {
  const f = nativeFixture(); f.nativeAck(f.display().version, true);
  f.press = (request = 1, values = {}) => f.window.MiowserLiveVideo.onmessage({data: JSON.stringify({
    type: 'nativePress', documentId: f.messages.find(m => m.type === 'hello').documentId,
    navigation: 3, token: 'frame-token', version: f.display().version, request, ...values})});
  f.cancelPress = request => f.window.MiowserLiveVideo.onmessage({data: JSON.stringify({
    type: 'nativePressCancel', documentId: f.messages.find(m => m.type === 'hello').documentId,
    navigation: 3, token: 'frame-token', version: f.display().version, request})});
  f.input = (type = 'click', values = {}) => {
    const event = {type, isTrusted: true, clientX: parseFloat(f.button().style.left) + 30,
      clientY: parseFloat(f.button().style.top) + 30, prevented: false, stopped: false,
      preventDefault() { this.prevented = true; }, stopPropagation() { this.stopped = true; },
      stopImmediatePropagation() { this.stopped = true; }, ...values};
    f.window.dispatch(type, event); return event;
  };
  return f;
}
check('native press prepares the same selected video without opening it from a bridge message', () => {
  const f = nativePressFixture(); f.press();
  assert.equal(f.messages.at(-1).type, 'assistantPressReady'); assert.equal(f.messages.at(-1).ok, true);
  assert.equal(f.initial[0].fullscreenRequests, 0);
  const event = f.input(); assert.equal(event.stopped, true); assert.equal(event.prevented, true);
  assert.equal(f.initial[0].fullscreenRequests, 1); assert.equal(f.initial[0].currentTime, 12);
});
check('native gesture blocks site pointer handlers and consumes its click only once', () => {
  const f = nativePressFixture(); f.press();
  assert.equal(f.input('pointerdown').stopped, true); assert.equal(f.input('pointerup').stopped, true);
  assert.equal(f.input('touchstart', {changedTouches: [{clientX: 616, clientY: 402}]}).stopped, true);
  f.input(); assert.equal(f.input().stopped, false); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('foreign native press messages cannot install an input guard', () => {
  const f = nativePressFixture(); f.press(1, {token: 'foreign'});
  assert.equal(f.messages.some(m => m.type === 'assistantPressReady'), false);
  assert.equal(f.input().stopped, false); assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('stale geometry and invalid native press IDs are declined', () => {
  const f = nativePressFixture(); f.press(1, {version: f.display().version - 1});
  assert.equal(f.messages.at(-1).ok, false); f.press(-1); assert.equal(f.messages.at(-1).ok, false);
  assert.equal(f.input().stopped, false);
});
check('cancelled native drag does not arm a later page click', () => {
  const f = nativePressFixture(); f.press(); f.cancelPress(1);
  assert.equal(f.input().stopped, false); assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('an old cancellation cannot cancel a newer physical press', () => {
  const f = nativePressFixture(); f.press(2); f.cancelPress(1); f.input();
  assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('native preparation never makes script-generated clicks trusted', () => {
  const f = nativePressFixture(); f.press(); assert.equal(f.input('click', {isTrusted: false}).stopped, false);
  assert.equal(f.initial[0].fullscreenRequests, 0); f.input(); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('a prepared native press expires without stealing later page clicks', () => {
  const f = nativePressFixture(); f.press(); f.advance(1501);
  assert.equal(f.input().stopped, false); assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('a separate touch outside the native target is left to the page', () => {
  const f = nativePressFixture(); f.press();
  assert.equal(f.input('pointerdown', {clientX: 20, clientY: 20}).stopped, false);
  assert.equal(f.input().stopped, false);
});
check('scroll invalidates native input preparation and stale clicks cannot launch', () => {
  const f = nativePressFixture(); f.press(); f.window.dispatch('scroll');
  assert.equal(f.input().stopped, false); assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('a disappearing selected video cannot turn native input into a page link click', () => {
  const f = nativePressFixture(); f.press(); f.initial[0].paused = true;
  const event = f.input(); assert.equal(event.stopped, true); assert.equal(event.prevented, true);
  assert.equal(f.initial[0].fullscreenRequests, 0);
});
check('display waits 200ms before showing the Samsung-style anchor', () => {
  const f = fixture(); f.bind(); f.policy(true); f.advance(199);
  assert.equal(f.button(), undefined); f.advance(1);
  assert.equal(f.button().hidden, false); assert.equal(f.button().style.left, '580px');
  assert.equal(f.button().style.top, '378px');
});
check('native display sends normalized visual geometry with a 60px real tap target', () => {
  const f = nativeFixture(), d = f.display();
  assert.equal(d.left * 1080, 580); assert.equal(d.top * 1920, 378);
  assert.equal(d.width * 1080, 60); assert.equal(d.height * 1920, 60);
  assert.equal(f.button().children[0].style.width, '48px');
});
check('native readiness hides only the web visual and keeps the same one-tap fullscreen action', () => {
  const f = nativeFixture(); assert.equal(f.button().style.opacity, '1');
  f.nativeAck(f.display().version, true);
  assert.equal(f.button().style.opacity, '0'); assert.equal(f.button().style['pointer-events'], 'auto');
  f.click(); assert.equal(f.initial[0].fullscreenRequests, 1); assert.equal(f.initial[0].currentTime, 12);
});
check('failed native rendering leaves the working web face and action available', () => {
  const f = nativeFixture(); f.nativeAck(f.display().version, false);
  assert.equal(f.button().style.opacity, '1'); f.click(); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('old or foreign display acknowledgements cannot hide the current web target', () => {
  const f = nativeFixture(), old = f.display().version;
  f.nativeAck(old, true, {token: 'old-token'}); assert.equal(f.button().style.opacity, '1');
  f.window.visualViewport.height = 1800; f.window.visualViewport.dispatch('resize'); f.advance(250);
  assert(f.display().version > old); f.nativeAck(old, true);
  assert.equal(f.button().style.opacity, '1');
});
check('heartbeat and playback-clock changes do not resend unchanged native geometry', () => {
  const f = nativeFixture(); const before = f.messages.filter(m => m.type === 'assistantDisplay').length;
  for (let i = 0; i < 3; i++) { f.initial[0].currentTime++; f.initial[0].dispatch('timeupdate'); f.flush(); }
  assert.equal(f.messages.filter(m => m.type === 'assistantDisplay').length, before);
});
check('auto-hide disables the tap immediately and cannot be undone by clock updates', () => {
  const f = nativeFixture(); f.nativeAck(f.display().version, true); f.advance(2700);
  assert.equal(f.button().disabled, true); assert.equal(f.button().style['pointer-events'], 'none');
  assert.equal(f.messages.filter(m => m.type === 'assistantDisplay').at(-1).visible, false);
  f.advance(300); assert.equal(f.button().hidden, true);
  f.initial[0].dispatch('timeupdate'); f.flush(); assert.equal(f.button().hidden, true);
});
check('an ordinary page tap reveals a hidden button but never opens the player itself', () => {
  const f = enabledFixture(); f.advance(3100);
  f.document.dispatch('pointerup', {isTrusted: false, target: f.initial[0]}); f.advance(250);
  assert.equal(f.button().hidden, true);
  f.document.dispatch('pointerup', {isTrusted: true, target: f.initial[0]}); f.advance(250);
  assert.equal(f.button().hidden, false); assert.equal(f.initial[0].fullscreenRequests, 0);
  f.click(); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('a touch beginning before auto-hide keeps the button alive for its click', () => {
  const f = enabledFixture(); f.advance(2600); f.button().dispatch('pointerdown', {stopPropagation() {}});
  f.advance(1000); assert.equal(f.button().disabled, false); f.click(); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('scrolling hides stale native geometry until scrolling settles', () => {
  const f = nativeFixture(); f.nativeAck(f.display().version, true);
  f.window.dispatch('scroll'); assert.equal(f.button().hidden, true);
  assert.equal(f.messages.filter(m => m.type === 'assistantDisplay').at(-1).visible, false);
  f.advance(199); assert.equal(f.button().hidden, true);
  f.advance(1); assert.equal(f.button().hidden, false);
});
check('main-frame button clamps inside the viewport when the below-video area is too small', () => {
  const f = fixture(); f.window.visualViewport.height = 400;
  f.bind(); f.policy(true); f.flush(); assert.equal(f.button().style.top, '340px');
});
check('embedded frames retain their reachable existing target without a toolbar route', () => {
  const f = fixture(); f.bind(); f.policy(true, {frameConstrained: true}); f.flush();
  assert.equal(f.button().style.left, '572px'); assert.equal(f.button().style.top, '18px');
  assert.equal(f.messages.some(m => m.type === 'assistantDisplay'), false);
  f.click(); assert.equal(f.initial[0].fullscreenRequests, 1);
});
check('focused web controls remain visible and are not removed by auto-hide', () => {
  const f = nativeFixture(); f.nativeAck(f.display().version, true);
  f.button().dispatch('focus'); f.advance(6000);
  assert.equal(f.button().hidden, false); assert.equal(f.button().style.opacity, '1');
  assert.equal(f.button().style.outline, '2px solid currentColor');
  f.button().dispatch('blur'); f.advance(3300); assert.equal(f.button().hidden, true);
});
function entryReloadFixture(native = false) {
  const f = native ? nativePressFixture() : enabledFixture(), v = f.initial[0];
  const enter = v.requestFullscreen, play = v.play;
  v.playCalls = 0;
  v.play = function () { this.playCalls++; return play.call(this); };
  v.requestFullscreen = function () {
    this.paused = true; this.readyState = 0; this.dispatch('emptied');
    return enter.call(this);
  };
  if (native) { f.press(); f.input(); } else f.click();
  f.render();
  f.mediaReady = () => { v.readyState = 4; v.dispatch('loadeddata'); f.render(); };
  return f;
}
check('entry recovery waits for restored media and resumes the original position on both tap routes', () => {
  for (const native of [false, true]) {
    const f = entryReloadFixture(native), v = f.initial[0], source = v.currentSrc;
    assert.equal(v.playCalls, 0); assert.equal(v.paused, true);
    f.mediaReady(); assert.equal(v.playCalls, 1); assert.equal(v.paused, false);
    assert.equal(v.currentTime, 12); assert.equal(v.currentSrc, source);
    v.pause(); f.render(); assert.equal(v.playCalls, 1);
  }
});
check('explicit pause while fullscreen media reloads cancels entry autoplay', () => {
  const f = entryReloadFixture(), v = f.initial[0]; f.control('pause');
  f.mediaReady(); assert.equal(v.paused, true); assert.equal(v.playCalls, 0);
});
check('even a source-stale pause expresses user intent and cancels entry autoplay', () => {
  const f = entryReloadFixture(), v = f.initial[0]; f.control('pause', undefined, {sourceRevision: 999});
  f.mediaReady(); assert.equal(v.playCalls, 0);
});
check('source replacement cancels fullscreen entry recovery', () => {
  const f = entryReloadFixture(), v = f.initial[0]; v.currentSrc = 'blob:https://site.test/replacement';
  f.mediaReady(); assert.equal(v.playCalls, 0);
});
check('fullscreen exit and hidden document cancel delayed entry playback', () => {
  for (const hide of [false, true]) {
    const f = entryReloadFixture(), v = f.initial[0];
    if (hide) { f.document.visibilityState = 'hidden'; f.document.dispatch('visibilitychange'); }
    else { f.document.fullscreenElement = null; f.document.dispatch('fullscreenchange'); }
    f.mediaReady(); assert.equal(v.playCalls, 0);
  }
});
check('entry recovery expires rather than starting a video long after the tap', () => {
  const f = entryReloadFixture(), v = f.initial[0]; f.advance(8001);
  f.mediaReady(); assert.equal(v.playCalls, 0);
});
check('permission-denied play is not retried and manual controls remain available', () => {
  const f = entryReloadFixture(), v = f.initial[0];
  v.play = function () { this.playCalls++; return {then(resolve, reject) { reject({name: 'NotAllowedError'}); }}; };
  f.mediaReady(); v.dispatch('canplay'); f.render(); assert.equal(v.playCalls, 1);
  assert.equal(f.document.fullscreenElement, v);
});
check('only a load-aborted play may retry on a later media readiness event', () => {
  const f = entryReloadFixture(), v = f.initial[0], play = v.play;
  v.play = function () {
    if (this.playCalls === 0) { this.playCalls++; return {then(resolve, reject) { reject({name: 'AbortError'}); }}; }
    return play.call(this);
  };
  f.mediaReady(); assert.equal(v.playCalls, 1);
  v.dispatch('canplay'); f.render(); assert.equal(v.playCalls, 2); assert.equal(v.paused, false);
});
check('healthy entry finishes observing without any extra play call', () => {
  const f = enabledFixture(), v = f.initial[0], play = v.play; v.playCalls = 0;
  v.play = function () { this.playCalls++; return play.call(this); };
  f.click(); f.render(); f.advance(351); v.pause(); f.render(); assert.equal(v.playCalls, 0);
});
console.log(checks + ' live-video script checks passed; no device tests were run.');
