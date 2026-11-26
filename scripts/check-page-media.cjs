'use strict';
// Host-only bridge/compatibility regressions. No WebView, Android app or device is started.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const base = path.join(__dirname, '../app/src/main/java/com/miowser/app/browser/media');
function constant(file, name) {
  const text = fs.readFileSync(path.join(base, file), 'utf8');
  const match = text.match(new RegExp('internal const val ' + name + ' = """([\\s\\S]*?)"""'));
  assert(match, name); return match[1];
}
const compatibility = constant('SiteMediaCompatibility.kt', 'SITE_MEDIA_COMPATIBILITY_SCRIPT');
const source = constant('PageMediaScript.kt', 'PAGE_MEDIA_BRIDGE_SCRIPT')
  .replace('/*SITE_MEDIA_COMPATIBILITY*/', compatibility)
  .replace('/*MEDIA_ARTWORK*/', constant('MediaArtworkScript.kt', 'MEDIA_ARTWORK_SCRIPT'))
  .replace('/*BOUNDED_MEDIA_DISCOVERY*/', constant('../BoundedMediaDiscovery.kt', 'BOUNDED_MEDIA_DISCOVERY_SCRIPT'));
new vm.Script(source, {filename: 'PageMediaScript.js'});
new vm.Script(compatibility, {filename: 'SiteMediaCompatibility.js'});

class Events {
  constructor() { this.listeners = new Map(); }
  addEventListener(name, callback) {
    if (!this.listeners.has(name)) this.listeners.set(name, new Set());
    this.listeners.get(name).add(callback);
  }
  removeEventListener(name, callback) { this.listeners.get(name)?.delete(callback); }
  dispatchEvent(event) {
    for (const fn of [...this.listeners.get(event.type) || []]) fn(event);
    return true;
  }
  emit(type, extra = {}) { this.dispatchEvent({type, target: this, ...extra}); }
}
function media(tag = 'AUDIO', options = {}) {
  return Object.assign(new Events(), {
    tagName: tag, nodeType: 1, children: [], isConnected: true,
    src: 'https://media.test/song.mp3', currentSrc: 'https://media.test/song.mp3', srcObject: null,
    currentTime: 10, duration: 180, playbackRate: 1, paused: false, ended: false,
    readyState: 4, seeking: false, muted: false, volume: 1, calls: [],
    seekable: {length: 1, start: () => 0, end: () => 180},
    play() { this.calls.push('play'); this.paused = false; this.fire?.('play'); return Promise.resolve(); },
    pause() { this.calls.push('pause'); this.paused = true; this.fire?.('pause'); },
    ...options
  });
}
function fixture(initial = [media()], url = 'https://www.youtube.com/watch?v=episode') {
  let time = 1000, nextTimer = 1;
  const timers = new Map(), messages = [], observers = [], scans = [], registrations = new Map();
  const apiCalls = [], elements = [...initial];
  const playCalls = [];
  class HTMLMediaElement extends Events {
    play(...args) {
      playCalls.push({receiver: this, args});
      if (this.playError) throw this.playError;
      this.calls.push('play'); this.paused = false; this.fire?.('play');
      return this.playReturn || Promise.resolve();
    }
  }
  const originalPlay = HTMLMediaElement.prototype.play;
  class Element extends Events {
    constructor() { super(); this.nodeType = 1; this.tagName = 'DIV'; this.children = []; this.isConnected = true; }
    attachShadow(options) {
      this.shadowArgs = options;
      const root = new Events(); root.nodeType = 11; root.children = []; root.mode = options.mode;
      if (options.mode === 'open') this.shadowRoot = root;
      return root;
    }
  }
  const originalAttach = Element.prototype.attachShadow;
  class Metadata {
    constructor(value = {}) { this.values = {title: '', artist: '', album: '', artwork: [], ...value}; }
  }
  for (const key of ['title', 'artist', 'album', 'artwork']) Object.defineProperty(Metadata.prototype, key, {
    configurable: true, enumerable: true, get() { return this.values[key]; }, set(value) { this.values[key] = value; }
  });
  class Session {
    constructor() { this.values = {metadata: null, playbackState: 'none'}; }
    setActionHandler(name, fn) {
      apiCalls.push({method: 'setActionHandler', thisValue: this, args: [name, fn]});
      if (name === 'invalid') throw new TypeError('unsupported action');
      registrations.set(name, fn);
      return 'original-return';
    }
    setPositionState(value) {
      apiCalls.push({method: 'setPositionState', thisValue: this, args: [value]});
      if (value?.duration < 0) throw new TypeError('invalid duration');
    }
  }
  for (const key of ['metadata', 'playbackState']) Object.defineProperty(Session.prototype, key, {
    configurable: true, enumerable: true, get() { return this.values[key]; }, set(value) { this.values[key] = value; }
  });
  const session = new Session(), originalAction = session.setActionHandler, originalPosition = session.setPositionState;
  const bridge = {postMessage: text => {
    assert(text.length <= 16384); messages.push(JSON.parse(text));
  }};
  const document = Object.assign(new Events(), {nodeType: 9, children: elements, title: 'Document fallback'});
  for (const [key, value] of [['hidden', true], ['visibilityState', 'hidden'], ['webkitHidden', true], ['webkitVisibilityState', 'hidden']]) {
    Object.defineProperty(document, key, {configurable: true, enumerable: true, get: () => value});
  }
  const visibilityDescriptor = Object.getOwnPropertyDescriptor(document, 'visibilityState');
  document.createTreeWalker = root => {
    scans.push(root);
    const nodes = [];
    function walk(node) { for (const child of node.children || []) { nodes.push(child); walk(child); } }
    walk(root); let index = 0;
    return {nextNode: () => nodes[index++] || null};
  };
  class MutationObserver {
    constructor(fn) { this.fn = fn; this.connected = true; observers.push(this); }
    observe(root, options) { this.root = root; this.options = options; }
    disconnect() { this.connected = false; }
  }
  const window = Object.assign(new Events(), {MiowserPageMedia: bridge, frames: [], Element, HTMLMediaElement});
  window.top = window; window.parent = window;
  const context = vm.createContext({window, document, navigator: {mediaSession: session}, location: new URL(url), URL,
    MutationObserver, performance: {now: () => time}, Event: class {constructor(type) { this.type = type; }},
    setTimeout(fn, delay = 0) { const id = nextTimer++; timers.set(id, {fn, at: time + delay}); return id; },
    clearTimeout(id) { timers.delete(id); }
  });
  function fire(element, type, extra = {}, root = document) {
    root.emit(type, {target: element, ...extra});
  }
  function detachedMedia(tag = 'AUDIO', options = {}) {
    const element = media(tag, {isConnected: false, paused: true, ...options});
    Object.setPrototypeOf(element, HTMLMediaElement.prototype);
    delete element.play;
    element.fire = type => {
      element.emit(type);
      if (element.isConnected) fire(element, type);
    };
    return element;
  }
  initial.forEach(element => { element.fire = type => fire(element, type); });
  vm.runInContext(source, context);
  function advance(ms) {
    const until = time + ms;
    for (let i = 0; i < 10000; i++) {
      const entry = [...timers].filter(([, t]) => t.at <= until).sort((a, b) => a[1].at - b[1].at)[0];
      if (!entry) { time = until; return; }
      timers.delete(entry[0]); time = entry[1].at; entry[1].fn();
    }
    throw new Error('Unbounded timers');
  }
  function reply(message) { bridge.onmessage({data: JSON.stringify(message)}); }
  let bound;
  function bind(navigation = 3, token = 'frame-token') {
    advance(250);
    const hello = messages.findLast(m => m.type === 'hello'); assert(hello, 'missing candidate hello');
    bound = {documentId: hello.documentId, navigation, token, ownerPrefix: 'tab:instance:' + navigation + ':frame:document:'};
    reply({type: 'bind', ...bound, playbackAllowed: true}); advance(250);
  }
  function send(type, extra = {}) { reply({type, ...bound, ...extra}); }
  function state() { return messages.findLast(m => m.type === 'state')?.media; }
  let commandId = 0;
  function command(action, extra = {}) {
    const current = state(); assert(current);
    send('command', {commandId: ++commandId,
      ownerKey: bound.ownerPrefix + current.elementId + ':' + current.sourceRevision,
      elementId: current.elementId, sourceRevision: current.sourceRevision, action, ...extra});
    advance(250);
  }
  function mutate(root = document, addedNodes = []) {
    observers.find(o => o.root === root && o.connected)?.fn([{type: 'childList', target: root, addedNodes}]);
  }
  return {window, document, session, Metadata, Element, originalAttach, originalAction, originalPosition,
    HTMLMediaElement, originalPlay, playCalls, detachedMedia,
    visibilityDescriptor, apiCalls, registrations, elements, timers, messages, observers, scans,
    fire, advance, reply, bind, send, state, command, mutate, context, get bound() { return bound; }};
}
let passed = 0;
function test(name, fn) { fn(); passed++; console.log('PASS ' + name); }

test('audio and video fallback; finite unknown duration and bounded URLs', () => {
  for (const tag of ['AUDIO', 'VIDEO']) {
    const item = media(tag), f = fixture([item]); f.bind();
    assert.equal(f.state().playing, true);
    assert.equal(f.state().hasMetadataTitle, false);
    assert(f.state().actions.includes('seekforward'));
    f.command('seekforward'); assert.equal(item.currentTime, 20);
    f.command('seekbackward'); assert.equal(item.currentTime, 10);
    f.command('seekto', {positionMs: 999999}); assert.equal(item.currentTime, 180);
    item.duration = Infinity; item.currentTime = NaN; f.fire(item, 'durationchange'); f.advance(250);
    assert.equal(f.state().durationMs, null); assert.equal(f.state().positionMs, 0);
    assert(!f.state().actions.includes('seekforward'));
    f.session.metadata = new f.Metadata({title: 't'.repeat(1000), artwork: [{src: 'data:image/png;base64,private'}, {src: 'javascript:x'}]});
    f.advance(250); assert.equal(f.state().title.length, 512); assert.equal(f.state().artworkUrl, null);
  }
});
test('muted autoplay previews and never-played paused elements never become owners', () => {
  const preview = media('VIDEO', {muted: true}), idle = media('AUDIO', {paused: true});
  const f = fixture([preview, idle]); f.advance(30000);
  assert.equal(f.messages.length, 0); assert.equal(f.timers.size, 0);
  const song = media(); song.fire = type => f.fire(song, type); f.elements.push(song); f.fire(song, 'playing');
  f.bind(); const id = f.state().elementId;
  song.paused = true; f.fire(song, 'pause'); f.advance(250);
  assert.equal(f.state().elementId, id); assert.equal(f.state().playing, false);
  f.fire(preview, 'timeupdate'); f.advance(250); assert.equal(f.state().elementId, id);
});
test('chooses the latest real playing element and retains its paused session', () => {
  const first = media(), second = media('VIDEO', {paused: true}); const f = fixture([first, second]); f.bind();
  const firstId = f.state().elementId;
  second.paused = false; f.fire(second, 'playing'); f.advance(250); const secondId = f.state().elementId;
  assert.notEqual(firstId, secondId);
  first.paused = true; second.paused = true; f.fire(second, 'pause'); f.advance(250);
  assert.equal(f.state().elementId, secondId);
});
test('media session hooks preserve original callbacks, arguments, return values and errors', () => {
  const item = media(), f = fixture([item]); f.bind();
  let received;
  function callback(details) { received = details; }
  assert.equal(f.session.setActionHandler('nexttrack', callback), 'original-return');
  assert.equal(f.registrations.get('nexttrack'), callback);
  assert.equal(f.apiCalls.at(-1).thisValue, f.session);
  assert.throws(() => f.session.setActionHandler('invalid', callback), TypeError);
  f.advance(250); f.command('nexttrack'); assert.equal(received.action, 'nexttrack');
  f.session.setActionHandler('nexttrack', null); f.advance(250);
  assert(!f.state().actions.includes('nexttrack'));
  f.session.setActionHandler('pause', () => { throw new Error('site error'); });
  f.command('pause'); assert.equal(f.messages.findLast(m => m.type === 'ack').ok, false);
  assert.equal(item.calls.length, 0);
});
test('same-document metadata replacement and in-place changes preserve explicit metadata', () => {
  const f = fixture(); f.bind(); const owner = f.state().elementId;
  f.session.metadata = new f.Metadata({title: 'Song one', artist: 'Artist', album: 'Album', artwork: [{src: '/cover.jpg'}]});
  f.advance(250); assert.equal(f.state().title, 'Song one'); assert.equal(f.state().hasMetadataTitle, true);
  assert.equal(f.state().artworkUrl, 'https://www.youtube.com/cover.jpg');
  f.session.metadata.title = 'Song two'; f.advance(250); assert.equal(f.state().title, 'Song two');
  assert.equal(f.state().elementId, owner);
  f.session.metadata = new f.Metadata({title: 'Song three'}); f.advance(250); assert.equal(f.state().title, 'Song three');
});
test('position anchor rebases at pause/resume and buffering without counting suspended time', () => {
  const item = media(), f = fixture([item]); f.bind();
  f.session.setPositionState({duration: 180, position: 40, playbackRate: 1});
  f.advance(2000); item.paused = true; f.fire(item, 'pause'); f.advance(250);
  const paused = f.state().positionMs; assert.equal(paused, 42000);
  f.advance(20000); f.fire(item, 'timeupdate'); f.advance(250); assert.equal(f.state().positionMs, paused);
  item.paused = false; f.fire(item, 'play'); f.advance(2000); item.readyState = 2; f.fire(item, 'waiting'); f.advance(250);
  assert.equal(f.state().positionMs, 44000);
  f.advance(12000); f.fire(item, 'timeupdate'); f.advance(250); assert.equal(f.state().positionMs, 44000);
  item.readyState = 4; f.fire(item, 'playing'); f.advance(1000); f.fire(item, 'timeupdate'); f.advance(250);
  assert(f.state().positionMs >= 45000 && f.state().positionMs <= 45250);
  assert.throws(() => f.session.setPositionState({duration: -1}), TypeError);
});
test('old owner commands fail immediately when assigned src changes before currentSrc', () => {
  const item = media(), f = fixture([item]); f.bind(); const previous = {...f.state()};
  item.src = 'https://media.test/new.mp3'; // The renderer may still expose the previous currentSrc here.
  f.command('pause'); assert.equal(item.calls.length, 0);
  f.fire(item, 'loadedmetadata'); f.advance(250);
  assert(f.state().sourceRevision > previous.sourceRevision);
  f.command('pause'); assert.deepEqual(item.calls, ['pause']);
  item.srcObject = {}; f.command('play'); assert.deepEqual(item.calls, ['pause']);
});
test('removed player immediately reports null and stale commands do not reach its replacement', () => {
  const item = media(), f = fixture([item]); f.bind(); const previous = f.state();
  item.isConnected = false; f.mutate(); f.advance(250); assert.equal(f.state(), null);
  f.send('command', {commandId: 1, ownerKey: f.bound.ownerPrefix + previous.elementId + ':' + previous.sourceRevision,
    elementId: previous.elementId, sourceRevision: previous.sourceRevision, action: 'pause'});
  assert.equal(item.calls.length, 0);
  const replacement = media(); f.elements.push(replacement); f.fire(replacement, 'playing'); f.advance(250);
  assert.notEqual(f.state().elementId, previous.elementId);
  item.isConnected = true; f.fire(item, 'playing'); f.advance(250);
  assert.equal(f.state().sourceRevision, previous.sourceRevision); // Reinserted element retains its identity.
});
test('token, navigation, document, source and command replay validation', () => {
  const item = media(), f = fixture([item]); f.bind();
  for (const extra of [{token: 'wrong'}, {navigation: 2}, {documentId: 'wrong'}, {sourceRevision: 999}, {ownerKey: 'wrong'}]) {
    f.command('pause', extra); assert.equal(item.calls.length, 0);
  }
  f.command('pause', {commandId: 10}); assert.equal(item.calls.length, 1);
  f.command('pause', {commandId: 10}); assert.equal(item.calls.length, 1);
  f.send('reset'); item.paused = false;
  f.command('pause'); assert.equal(item.calls.length, 1);
  f.window.__miowserPageMedia.connect(); f.bind(4, 'new-token');
  f.command('pause', {navigation: 3, token: 'frame-token'}); assert.equal(item.calls.length, 1);
});
test('visibility patch applies only to exact HTTPS hosts and authenticated current owner', () => {
  const allowed = ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'open.spotify.com'];
  const denied = ['http://youtube.com', 'https://youtube.com.evil.test', 'https://evil.youtube.com',
    'https://spotify.com', 'https://www.open.spotify.com', 'https://example.test'];
  for (const url of [...allowed.map(host => 'https://' + host), ...denied]) {
    const f = fixture([media()], url); f.bind();
    let calls = 0; f.document.addEventListener('visibilitychange', () => { calls++; });
    const ownerKey = f.bound.ownerPrefix + f.state().elementId + ':' + f.state().sourceRevision;
    f.send('background', {allowed: true, ownerKey: 'wrong'}); assert.equal(f.document.hidden, true);
    f.send('background', {allowed: true, ownerKey, token: 'wrong'}); assert.equal(f.document.hidden, true);
    f.send('background', {allowed: true, ownerKey});
    assert.equal(f.document.hidden, !allowed.some(host => url === 'https://' + host));
    f.send('background', {allowed: false}); assert.equal(f.document.hidden, true);
    assert.equal(Object.getOwnPropertyDescriptor(f.document, 'visibilityState').get, f.visibilityDescriptor.get);
    f.document.emit('visibilitychange'); assert(calls > 0); // Site listeners survive enable/disable.
  }
});
test('compatibility never emits a redundant visible event that could resume a paused site player', () => {
  const f = fixture(); f.bind(); let hidden = false, changes = 0;
  for (const key of ['hidden', 'webkitHidden', 'visibilityState', 'webkitVisibilityState']) {
    Object.defineProperty(f.document, key, {configurable: true, get: () =>
      key === 'hidden' || key === 'webkitHidden' ? hidden : hidden ? 'hidden' : 'visible'});
  }
  f.document.addEventListener('visibilitychange', () => { changes++; });
  const ownerKey = f.bound.ownerPrefix + f.state().elementId + ':' + f.state().sourceRevision;
  f.send('background', {allowed: true, ownerKey});
  f.send('background', {allowed: false});
  assert.equal(changes, 0);
  hidden = true;
  f.send('background', {allowed: true, ownerKey});
  assert.equal(changes, 1);
  f.send('background', {allowed: false});
  assert.equal(changes, 2);
});
test('source replacement, reset and retirement restore compatibility and original API hooks', () => {
  const item = media(), f = fixture([item]); f.bind();
  const ownerKey = f.bound.ownerPrefix + f.state().elementId + ':' + f.state().sourceRevision;
  f.send('background', {allowed: true, ownerKey}); assert.equal(f.document.hidden, false);
  item.src = 'https://media.test/next.mp3'; f.fire(item, 'loadstart'); f.advance(250);
  assert.equal(f.document.hidden, true);
  f.send('destroy'); assert.equal(f.session.setActionHandler, f.originalAction);
  assert.equal(f.session.setPositionState, f.originalPosition);
  assert.equal(f.Element.prototype.attachShadow, f.originalAttach);
  assert(f.observers.every(o => !o.connected)); assert.equal(f.timers.size, 0);
});
test('BFCache preserves captured next/previous callbacks while restoring and reapplying hooks cleanly', () => {
  const f = fixture(); f.bind();
  const calls = [];
  function next(details) { calls.push(details.action); }
  function previous(details) { calls.push(details.action); }
  f.session.setActionHandler('nexttrack', next);
  f.session.setActionHandler('previoustrack', previous);
  f.advance(250);
  for (let cycle = 0; cycle < 2; cycle++) {
    const actionHook = f.session.setActionHandler;
    assert.notEqual(actionHook, f.originalAction);
    f.window.emit('pagehide', {persisted: true});
    assert.equal(f.session.setActionHandler, f.originalAction);
    assert.equal(f.session.setPositionState, f.originalPosition);
    assert.equal(f.Element.prototype.attachShadow, f.originalAttach);
    assert.equal(Object.hasOwn(f.session, 'metadata'), false);
    assert.equal(f.registrations.get('nexttrack'), next);
    assert.equal(f.registrations.get('previoustrack'), previous);
    assert(f.observers.every(observer => !observer.connected));
    assert.equal(f.timers.size, 0);
    f.window.emit('pageshow', {persisted: true}); f.bind();
    assert.notEqual(f.session.setActionHandler, f.originalAction);
    assert.notEqual(f.session.setActionHandler, actionHook);
    assert(f.state().actions.includes('nexttrack'));
    assert(f.state().actions.includes('previoustrack'));
    f.command('nexttrack'); f.command('previoustrack');
  }
  assert.deepEqual(calls, ['nexttrack', 'previoustrack', 'nexttrack', 'previoustrack']);
  assert.equal(f.apiCalls.filter(call => call.method === 'setActionHandler').length, 2);
  f.send('destroy');
  assert.equal(f.session.setActionHandler, f.originalAction);
  assert.equal(f.session.setPositionState, f.originalPosition);
  assert.equal(f.Element.prototype.attachShadow, f.originalAttach);
  assert.equal(f.timers.size, 0);
});
test('never-connected audio owns metadata and supports exact play delegation, pause and seek', () => {
  const f = fixture([]), audio = f.detachedMedia(), promise = Promise.resolve('site promise'), argument = {};
  audio.playReturn = promise;
  f.session.metadata = new f.Metadata({title: 'Detached song', artist: 'Detached artist'});
  assert.equal(audio.play(argument, 42), promise);
  assert.equal(f.playCalls.length, 1);
  assert.equal(f.playCalls[0].receiver, audio);
  assert.deepEqual(f.playCalls[0].args, [argument, 42]);
  assert.equal(audio.isConnected, false);
  f.bind(); assert.equal(f.state().title, 'Detached song'); assert.equal(f.state().playing, true);
  f.command('seekforward'); assert.equal(audio.currentTime, 20);
  assert.equal(f.messages.findLast(message => message.type === 'ack').ok, true);
  f.command('pause'); assert.equal(audio.paused, true); assert.equal(f.state().playing, false);
  assert.equal(f.messages.findLast(message => message.type === 'ack').ok, true);
  f.command('play'); assert.equal(audio.paused, false); assert.equal(f.state().playing, true);
  const old = {...f.state()}; audio.src = 'https://media.test/detached-next.mp3';
  f.command('pause'); assert.equal(audio.paused, false);
  audio.fire('loadedmetadata'); f.advance(250); assert(f.state().sourceRevision > old.sourceRevision);
  f.command('pause'); assert.equal(audio.paused, true);
  f.send('destroy');
  assert.equal(f.HTMLMediaElement.prototype.play, f.originalPlay);
  assert([...audio.listeners.values()].every(listeners => listeners.size === 0));
  assert.equal(f.timers.size, 0);
});
test('connected then removed video retires and cannot be rediscovered as detached playback', () => {
  const f = fixture([]), video = f.detachedMedia('VIDEO');
  video.isConnected = true; f.elements.push(video); video.play(); f.bind();
  const previous = {...f.state()}; assert.equal(previous.playing, true);
  video.isConnected = false; f.mutate(); f.advance(250); assert.equal(f.state(), null);
  video.play(); f.advance(250); assert.equal(f.state(), null);
  assert([...video.listeners.values()].every(listeners => listeners.size === 0));
  f.send('command', {commandId: 99, ownerKey: f.bound.ownerPrefix + previous.elementId + ':' + previous.sourceRevision,
    elementId: previous.elementId, sourceRevision: previous.sourceRevision, action: 'pause'});
  assert(!video.calls.includes('pause'));
});
test('detached direct listeners skip connected events and are removed on eviction and BFCache cleanup', () => {
  const f = fixture([]), audio = f.detachedMedia(); audio.play(); f.bind();
  const revision = f.state().sourceRevision;
  audio.isConnected = true; f.elements.push(audio); audio.fire('loadstart'); f.advance(250);
  assert.equal(f.state().sourceRevision, revision + 1);
  assert([...audio.listeners.values()].every(listeners => listeners.size === 0));
  audio.isConnected = false; f.mutate(); f.advance(250); assert.equal(f.state(), null);
  const players = [];
  for (let i = 0; i < 40; i++) {
    const item = f.detachedMedia(); players.push(item); item.play(); item.pause(); f.advance(250);
  }
  const listenerCount = item => [...item.listeners.values()].reduce((sum, listeners) => sum + listeners.size, 0);
  assert.equal(listenerCount(players[0]), 0);
  assert(players.filter(item => listenerCount(item) > 0).length <= 16);
  const retained = players.at(-1); assert(listenerCount(retained) > 0);
  f.window.emit('pagehide', {persisted: true});
  assert(players.every(item => listenerCount(item) === 0));
  assert.equal(f.HTMLMediaElement.prototype.play, f.originalPlay);
  f.window.emit('pageshow', {persisted: true}); f.bind();
  assert(listenerCount(retained) > 0);
  f.command('play'); assert.equal(retained.paused, false);
  f.send('destroy'); assert(players.every(item => listenerCount(item) === 0));
  assert.equal(f.HTMLMediaElement.prototype.play, f.originalPlay);
});
test('prototype play preserves synchronous native errors without enrolling the failed element', () => {
  const f = fixture([]), audio = f.detachedMedia(), error = new TypeError('native play failed');
  audio.playError = error;
  assert.throws(() => audio.play('argument'), thrown => thrown === error);
  assert.equal(f.playCalls.length, 1);
  assert.deepEqual(f.playCalls[0].args, ['argument']);
  f.advance(250); assert.equal(f.messages.length, 0);
  assert.equal(audio.listeners.size, 0);
});
test('late open shadow roots are observed once; closed roots and root overflow stay untouched', () => {
  const f = fixture([]), host = new f.Element(), options = {mode: 'open'};
  const root = host.attachShadow(options); assert.equal(host.shadowArgs, options);
  assert.equal(f.observers.filter(o => o.root === root).length, 1);
  const item = media('VIDEO'); root.children.push(item); f.mutate(root, [item]); f.bind();
  assert.equal(f.state().playing, true);
  const closed = new f.Element().attachShadow({mode: 'closed'});
  assert(!f.observers.some(o => o.root === closed));
  for (let i = 0; i < 80; i++) new f.Element().attachShadow({mode: 'open'});
  assert.equal(f.observers.length, 32);
});
test('timeupdate throttles messages without DOM scans; heartbeat stops for paused and empty pages', () => {
  const item = media(), f = fixture([item]); f.bind(); const scans = f.scans.length, before = f.messages.length;
  for (let i = 0; i < 500; i++) f.fire(item, 'timeupdate');
  f.advance(250); assert(f.messages.length - before <= 2); assert.equal(f.scans.length, scans);
  const count = f.messages.length; f.advance(11000); assert(f.messages.length >= count + 2);
  item.paused = true; f.fire(item, 'pause'); f.advance(250); assert.equal(f.timers.size, 0);
  const idle = fixture([]); idle.advance(60000); assert.equal(idle.timers.size, 0); assert.equal(idle.messages.length, 0);
});
test('parent reconnect reaches child contexts without accepting child telemetry as native commands', () => {
  const f = fixture([]); let reconnect;
  f.window.frames.push({postMessage: message => { reconnect = message; }});
  f.window.__miowserPageMedia.connect(); assert.equal(reconnect.miowserPageMediaConnect, true);
  f.window.emit('message', {source: {}, data: {miowserPageMediaConnect: true, type: 'command', action: 'pause'}});
  assert.equal(f.messages.length, 0);
});
test('hidden tab pauses all media and rejects later play events', () => {
  const first = media(), second = media('VIDEO');
  const f = fixture([first, second]); f.bind();
  f.send('playback', {allowed: false}); f.advance(250);
  assert(first.paused && second.paused);
  second.play(); f.advance(250); assert(second.paused);
  const playCount = second.calls.filter(call => call === 'play').length;
  f.command('play'); assert.equal(second.calls.filter(call => call === 'play').length, playCount);
});

test('switching back grants permission without resuming paused media', () => {
  const item = media(), f = fixture([item]); f.bind();
  f.send('playback', {allowed: false}); f.advance(250); assert(item.paused);
  f.send('playback', {allowed: true}); f.advance(1000); assert(item.paused);
  f.command('play'); assert.equal(item.paused, false);
});

test('frame-wide pause reaches non-owner media without seeking or changing volume', () => {
  const first = media(), second = media('VIDEO', {currentTime: 77, volume: 0.4});
  const f = fixture([first, second]); f.bind(); f.send('pauseAll'); f.advance(250);
  assert(first.paused && second.paused);
  assert.equal(second.currentTime, 77); assert.equal(second.volume, 0.4);
});

test('denied binding stops a new frame; stale policy cannot override its binding', () => {
  const item = media(), f = fixture([item]); f.bind();
  f.reply({type: 'bind', ...f.bound, playbackAllowed: false}); f.advance(250); assert(item.paused);
  f.reply({type: 'playback', ...f.bound, token: 'stale', allowed: true});
  item.play(); assert(item.paused);
});

test('hidden detached media cannot restart through the native play wrapper', () => {
  const f = fixture([]), audio = f.detachedMedia(); audio.play(); f.bind();
  f.send('playback', {allowed: false}); f.advance(250); assert(audio.paused);
  const originalPromise = Promise.resolve(); audio.playReturn = originalPromise;
  assert.equal(audio.play(), originalPromise); assert(audio.paused);
  f.send('playback', {allowed: true}); f.advance(250); assert(audio.paused);
});

test('an expired frame binding can reconnect on the first explicit play', () => {
  const item = media(), f = fixture([item]); f.bind();
  f.send('playback', {allowed: false}); f.advance(250);
  f.send('reset'); // Native pruning drops an idle non-owner frame.
  item.play(); assert.equal(item.paused, false, 'unbound explicit play must establish a new handshake');
  f.bind(4, 'reconnected'); assert.equal(f.state().playing, true);
});

console.log(`Page media: syntax + ${passed} host regressions passed.`);
