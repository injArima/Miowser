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
class Element extends Events {
  constructor(tag) {
    super(); this.tagName = tag.toUpperCase(); this.children = []; this.attributes = {};
    this.isConnected = false; this.parentElement = null; this.hidden = false;
    this.style = { setProperty(key, value) { this[key] = value; } };
    this.animationCount = 0;
  }
  setAttribute(key, value) { this.attributes[key] = value; }
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
      paddingTop: '0px', paddingBottom: '0px', ...element.computed }) });
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
  function run() { vm.runInContext(buttonSource + controlSource + source, context); }
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
    render();
  }
  function render() { const pending = [...animationFrames.values()]; animationFrames.clear(); pending.forEach(fn => fn()); }
  function mutate(added = []) {
    mutations.filter(m => m.connected).forEach(m => m.callback([{ type: 'childList', addedNodes: added }]));
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
    const id = ++commandId;
    bridge.onmessage({data: JSON.stringify({type: 'control', documentId: hello.documentId,
      navigation: 3, token: 'frame-token', elementId: active?.elementId, attempt: active?.attempt,
      commandId: id, action, value, ...credentials})});
    return id;
  }
  return { initial, window, document, messages, timers, intervals, children, intersections,
    run, bind, flush, mutate, state, button, policy, click, render, elements, animationFrames, control, time: () => time };
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
  assert.equal(f.button().style.width, '48px');
  assert.equal(f.button().attributes['aria-label'], 'Open video fullscreen with Miowser');
  assert.equal(f.button().style['background-color'], '#123456');
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
  assert.equal(v.controls, true); assert.equal(f.button().hidden, true);
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
check('button stays inside the clipped visible video', () => {
  const v = video(); v.getBoundingClientRect = () => ({ left: -100, top: -30, width: 640, height: 360 });
  const f = enabledFixture([v]), b = f.button();
  assert.equal(b.style.top, '8px'); assert.equal(b.style.left, '484px');
});
check('overflow-clipping ancestors constrain placement', () => {
  const v = video(), parent = new Element('div');
  parent.getBoundingClientRect = () => ({ left: 200, top: 20, right: 400, bottom: 140, width: 200, height: 120 });
  parent.computed = { overflowX: 'hidden', overflowY: 'hidden' }; v.parentElement = parent;
  const f = enabledFixture([v]); assert.equal(f.button().style.left, '344px'); assert.equal(f.button().style.top, '28px');
});
check('pinch-zoom viewport preserves the apparent touch target size', () => {
  const f = enabledFixture();
  Object.assign(f.window.visualViewport, { scale: 2, offsetLeft: 200, offsetTop: 40, width: 540, height: 960 });
  f.window.visualViewport.dispatch('resize'); f.render();
  assert.equal(f.button().style.width, '24px'); assert.equal(f.button().style.height, '24px');
  assert.equal(f.button().style.left, '612px'); assert.equal(f.button().style.top, '44px');
});
check('safe-area insets are respected inside the WebView viewport', () => {
  const f = fixture(); const original = f.window.getComputedStyle;
  f.window.visualViewport.width = 620;
  f.window.getComputedStyle = e => ({ ...original(e), ...(e.tagName === 'SPAN' ? {paddingRight: '40px'} : {}) });
  f.bind(); f.policy(true); f.flush(); assert.equal(f.button().style.left, '524px');
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
  assert.equal(f.animationFrames.size, 1); f.render(); assert.equal(f.button().style.top, '108px');
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
check('rich controls only replace browser controls after explicit attachment', () => {
  const f = playingFixture(); assert.equal(f.initial[0].controls, true);
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
  f.flush(); assert.equal(ack(f, id).ok, false);
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
console.log(checks + ' live-video script checks passed; no device tests were run.');
