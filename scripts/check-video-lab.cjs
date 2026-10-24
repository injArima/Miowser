// Host-only checks of the fixture's playback contract. No emulator/device/browser needed.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { URLSearchParams } = require('node:url');
const source = fs.readFileSync(path.join(__dirname,
  '../app/src/videoPrototype/assets/video-lab/fixture.js'), 'utf8');

function fixture() {
  const messages = [];
  const timers = new Map();
  const listeners = new Map();
  const elements = new Map();
  let now = 1000, timerId = 0, assignments = 0, fullscreenCalls = 0;
  const video = {
    currentTime: 15, duration: 45, paused: false, ended: false, readyState: 4,
    playbackRate: 1, videoWidth: 320, videoHeight: 180, controls: true,
    seekable: { length: 1, start: () => 5, end: () => 40 },
    buffered: { length: 1, end: () => 40 },
    addEventListener: (type, fn) => listeners.set(`video:${type}`, fn),
    removeEventListener: (type) => listeners.delete(`video:${type}`),
    pause() { this.paused = true; },
    play() { this.paused = false; return Promise.resolve(); },
    requestFullscreen() { fullscreenCalls++; document.fullscreenElement = this; return Promise.resolve(); },
    set src(value) { assignments++; this.resource = value; }
  };
  const open = {
    disabled: true, addEventListener: (type, fn) => listeners.set(`open:${type}`, fn),
    removeEventListener: (type) => listeners.delete(`open:${type}`)
  };
  elements.set('fixture-video', video);
  elements.set('open', open);
  elements.set('status', {});
  const document = {
    fullscreenElement: null, fullscreenEnabled: true,
    getElementById: id => elements.get(id),
    addEventListener: (type, fn) => listeners.set(`document:${type}`, fn),
    removeEventListener: (type) => listeners.delete(`document:${type}`)
  };
  const bridge = { postMessage: json => messages.push(JSON.parse(json)) };
  const window = {
    MiowserVideoLab: bridge, addEventListener: (type, fn) => listeners.set(`window:${type}`, fn)
  };
  vm.runInNewContext(source, {
    window, document, URLSearchParams, location: { search: '?generation=7&nonce=test&kind=direct' },
    crypto: { randomUUID: () => 'same-document' }, performance: { now: () => now },
    setTimeout: fn => { timers.set(++timerId, fn); return timerId; },
    clearTimeout: id => timers.delete(id)
  });
  function dispatch(key) { listeners.get(key)?.(); }
  function flush() {
    now += 250;
    const pending = [...timers.values()];
    timers.clear();
    pending.forEach(fn => fn());
  }
  function command(action, value, extra = {}) {
    bridge.onmessage?.({ data: JSON.stringify({ action, value, generation: 7,
      nonce: 'test', documentId: 'same-document', ...extra }) });
  }
  return { video, document, bridge, messages, timers, dispatch, flush, command,
    assignments: () => assignments, fullscreenCalls: () => fullscreenCalls };
}

let checks = 0;
function check(name, test) { test(); checks++; console.log(`PASS ${name}`); }

check('fullscreen entry retains source, position and media element', () => {
  const f = fixture();
  f.dispatch('video:loadedmetadata');
  f.flush();
  f.dispatch('open:click');
  assert.equal(f.fullscreenCalls(), 1);
  assert.equal(f.document.fullscreenElement, f.video);
  assert.equal(f.video.currentTime, 15);
  assert.equal(f.assignments(), 1);
  assert.equal(f.video.controls, false);
  assert.equal(f.messages.at(-1).type, 'enter');
});
check('return restores inline controls without resetting playback', () => {
  const f = fixture();
  f.dispatch('open:click');
  f.video.currentTime = 22;
  f.document.fullscreenElement = null;
  f.dispatch('document:fullscreenchange');
  assert.equal(f.video.controls, true);
  assert.equal(f.video.currentTime, 22);
  assert.equal(f.assignments(), 1);
  assert.equal(f.messages.at(-1).type, 'exit');
});
check('native controls target the original element', () => {
  const f = fixture();
  f.command('pause'); assert.equal(f.video.paused, true);
  f.command('play'); assert.equal(f.video.paused, false);
  f.command('rate', 1.5); assert.equal(f.video.playbackRate, 1.5);
  f.command('seek', 30); assert.equal(f.video.currentTime, 30);
  assert.equal(f.assignments(), 1);
});
check('seeking respects the actual playable window', () => {
  const f = fixture();
  f.command('seek', 100); assert.equal(f.video.currentTime, 40);
  f.command('seek', -10); assert.equal(f.video.currentTime, 5);
  f.video.seekable.length = 0;
  f.command('seek', 20); assert.equal(f.video.currentTime, 5);
});
check('stale or foreign commands cannot affect playback', () => {
  const f = fixture();
  f.command('pause', undefined, { generation: 6 });
  f.command('pause', undefined, { nonce: 'foreign' });
  f.command('pause', undefined, { documentId: 'other-document' });
  assert.equal(f.video.paused, false);
  f.command('rate', 9); assert.equal(f.video.playbackRate, 1);
  f.command('download', 'https://example.com'); assert.equal(f.assignments(), 1);
});
check('frequent events are coalesced into one scheduled update', () => {
  const f = fixture();
  for (let i = 0; i < 100; i++) f.dispatch('video:timeupdate');
  assert.equal(f.timers.size, 1);
  f.flush();
  assert.equal(f.messages.length, 1);
  assert.equal(f.timers.size, 0);
});
check('navigation tears down commands and scheduled updates', () => {
  const f = fixture();
  f.dispatch('video:timeupdate');
  f.dispatch('window:pagehide');
  assert.equal(f.timers.size, 0);
  assert.equal(f.bridge.onmessage, null);
  f.dispatch('open:click');
  f.dispatch('video:timeupdate');
  assert.equal(f.fullscreenCalls(), 0);
  assert.equal(f.messages.length, 0);
});
check('diagnostics count metadata/source events independently of entering fullscreen', () => {
  const f = fixture();
  f.dispatch('video:loadstart');
  f.dispatch('video:loadedmetadata');
  f.flush();
  f.dispatch('open:click');
  assert.equal(f.messages.at(-1).sourceChanges, 1);
  assert.equal(f.messages.at(-1).metadataLoads, 1);
  assert.equal(f.messages.at(-1).documentId, 'same-document');
});
console.log(`${checks} fixture contract checks passed. Android fullscreen and decoding remain device checks.`);
