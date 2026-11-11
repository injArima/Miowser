// Bounded DOM fixtures; real site/renderer behavior remains a device check.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = file => fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/miowser/app', file), 'utf8')
  .match(/"""([\s\S]*?)"""/)[1];
const targetSource = source('browser/BrowserPageGestureTarget.kt');
let checks = 0;
function check(name, test) { test(); checks++; console.log('PASS ' + name); }

class Element {
  constructor(tag = 'div', parent = null, computed = {}) {
    this.tagName = tag.toUpperCase(); this.parentElement = parent; this.computed = computed;
    this.scrollWidth = this.clientWidth = 300; this.scrollHeight = this.clientHeight = 100;
    this.attributes = {}; this.isConnected = true; this.listeners = new Map(); this.descendants = [];
  }
  getAttribute(key) { return this.attributes[key] ?? null; }
  setAttribute(key, value) { this.attributes[key] = value; }
  addEventListener(type, callback) {
    this.listeners.set(type, [...(this.listeners.get(type) || []), callback]);
  }
  dispatch(type) { (this.listeners.get(type) || []).forEach(fn => fn()); }
  querySelectorAll() { return this.descendants; }
  matches(selector) {
    if (selector === 'video, audio') return /^(VIDEO|AUDIO)$/.test(this.tagName);
    return selector.split(',').some(s => s === this.tagName.toLowerCase() ||
      s === 'a[href]' && this.tagName === 'A' && this.attributes.href ||
      s === '[onclick]' && this.attributes.onclick ||
      s === '[draggable="true"]' && this.attributes.draggable === 'true' ||
      s.startsWith('[contenteditable]') && this.isContentEditable);
  }
  remove() { this.isConnected = false; }
}

function targetFixture() {
  const html = new Element('html'), body = new Element('body', html), item = new Element('div', body);
  html.scrollHeight = 2000;
  let target = item, point;
  const document = {documentElement: html, body, scrollingElement: html,
    elementFromPoint: (x, y) => { point = [x, y]; return target; }};
  const window = {innerWidth: 400, innerHeight: 800, getComputedStyle: el => ({
    cursor: 'auto', touchAction: 'auto', overscrollBehaviorY: 'auto',
    overflowX: 'visible', overflowY: 'visible', ...el.computed})};
  const fn = vm.runInNewContext(targetSource, {window, document});
  return {html, body, item, window, document, allow: () => fn(0.25, 0.5),
    setTarget: el => { target = el; }, point: () => point};
}

check('ordinary page content permits refresh without scanning the document', () => {
  const f = targetFixture(); assert.equal(f.allow(), true); assert.deepEqual(f.point(), [100, 400]);
});
for (const tag of ['button', 'input', 'textarea', 'video', 'audio', 'canvas', 'iframe', 'select']) {
  check(tag + ' keeps its touch stream', () => {
    const f = targetFixture(); f.setTarget(new Element(tag, f.body)); assert.equal(f.allow(), false);
  });
}
check('children of a semantic control keep gestures', () => {
  const f = targetFixture(), button = new Element('div', f.body); button.attributes.role = 'button';
  f.item.parentElement = button; assert.equal(f.allow(), false);
});
check('links and editable elements keep gestures', () => {
  const f = targetFixture(), a = new Element('a', f.body); a.attributes.href = '/song';
  f.setTarget(a); assert.equal(f.allow(), false);
  f.item.isContentEditable = true; f.setTarget(f.item); assert.equal(f.allow(), false);
});
for (const axis of ['X', 'Y']) {
  check('nested ' + axis + ' scroll areas retain gestures even at their boundary', () => {
    const f = targetFixture(); f.item.computed['overflow' + axis] = 'auto';
    f.item[axis === 'X' ? 'scrollWidth' : 'scrollHeight'] = 1000;
    assert.equal(f.allow(), false);
  });
}
for (const behavior of ['contain', 'none']) {
  check('root overscroll-behavior ' + behavior + ' disables native refresh', () => {
    const f = targetFixture(); f.html.computed.overscrollBehaviorY = behavior;
    assert.equal(f.allow(), false);
  });
}
check('touch-action and custom clickable cursors are respected', () => {
  const f = targetFixture(); f.item.computed.touchAction = 'pan-x pinch-zoom'; assert.equal(f.allow(), false);
  f.item.computed = {cursor: 'pointer'}; assert.equal(f.allow(), false);
});
check('document scroller is not mistaken for a nested scroller', () => {
  const f = targetFixture(); f.html.computed.overflowY = 'auto'; assert.equal(f.allow(), true);
});
check('open shadow content is hit-tested and the host is traversed only once', () => {
  const f = targetFixture(), child = new Element('span');
  child.getRootNode = () => ({host: f.item});
  f.item.shadowRoot = {elementFromPoint: () => child};
  assert.equal(f.allow(), true);
  child.attributes.role = 'slider'; assert.equal(f.allow(), false);
});
check('zoomed visual viewport coordinates are accounted for', () => {
  const f = targetFixture(); f.window.visualViewport = {offsetLeft: 20, offsetTop: 30, width: 200, height: 400};
  assert.equal(f.allow(), true); assert.deepEqual(f.point(), [70, 230]);
});
check('unknown, inaccessible and excessively deep hit targets yield to the page', () => {
  const f = targetFixture(); f.setTarget(null); assert.equal(f.allow(), false);
  let node = f.body; for (let i = 0; i < 60; i++) node = new Element('div', node);
  f.setTarget(node); assert.equal(f.allow(), false);
  f.document.elementFromPoint = () => { throw Error('unavailable'); }; assert.equal(f.allow(), false);
});

function snifferFixture() {
  const activity = [], reports = [], observers = [], media = []; let token = '1';
  const bridge = {documentToken: () => token,
    reportMediaActivityForDocument: (token, playing) => activity.push([token, playing]),
    reportVideoElementForDocument: (...args) => reports.push(args)};
  const document = {readyState: 'complete', documentElement: {}, querySelectorAll: () => media};
  const window = {MiowserGrabber: bridge};
  class MutationObserver { constructor(fn) { this.fn = fn; observers.push(this); } observe() {} }
  vm.runInNewContext(source('grabber/VideoSnifferScript.kt'), {
    window, document, location: {href: 'https://music.test/watch'}, Element, MutationObserver});
  function mutate(nodes = []) { observers.forEach(o => o.fn([{addedNodes: nodes}])); }
  function add(tag) {
    const el = Object.assign(new Element(tag), {src: 'https://music.test/track.mp3', duration: 180,
      paused: true, ended: false}); media.push(el); mutate([el]); return el;
  }
  return {activity, reports, mutate, add, window, token: next => { token = next; }};
}
check('thousands of unrelated SPA mutations cause only one unchanged activity bridge call', () => {
  const f = snifferFixture(); f.add('audio');
  for (let i = 0; i < 2000; i++) f.mutate([new Element('div')]);
  assert.equal(f.activity.length, 1); assert.equal(f.reports.length, 1);
});
check('audio play, duplicate play, pause and removed-media activity remain correct', () => {
  const f = snifferFixture(), el = f.add('audio');
  el.paused = false; el.dispatch('play'); el.dispatch('play');
  assert.deepEqual(f.activity, [['1', false], ['1', true]]);
  el.paused = true; el.dispatch('pause'); assert.deepEqual(f.activity.at(-1), ['1', false]);
  el.paused = false; el.dispatch('play'); el.isConnected = false; f.mutate();
  assert.deepEqual(f.activity.at(-1), ['1', false]);
});
check('same-document navigation rebinds media reports to the new native token', () => {
  const f = snifferFixture(), el = f.add('video'); el.paused = false; el.dispatch('play');
  f.token('2'); f.window.__miowserRefreshMediaSniffer();
  assert.equal(f.reports.at(-1)[0], '2'); assert.deepEqual(f.activity.at(-1), ['2', true]);
  const count = f.reports.length; f.window.__miowserRefreshMediaSniffer(); assert.equal(f.reports.length, count);
  el.duration = 200; el.dispatch('durationchange'); assert.equal(f.reports.at(-1)[2], 200);
});

function rulesFixture() {
  let rules = {hide: [], delete: []}, style = null;
  const observers = [], frames = [], victim = new Element('aside');
  const document = {location: {hostname: 'music.test'}, documentElement: {},
    head: {appendChild: el => { style = el; }},
    querySelector: selector => selector.startsWith('style[') ? style : null,
    querySelectorAll: () => victim.isConnected ? [victim] : [],
    createElement: tag => Object.assign(new Element(tag), {remove() { style = null; }})};
  const window = {MiowserElementRules: {rulesFor: () => JSON.stringify(rules)}};
  class MutationObserver {
    constructor(fn) { this.fn = fn; this.connected = false; observers.push(this); }
    observe() { this.connected = true; } disconnect() { this.connected = false; }
  }
  vm.runInNewContext(source('browser/ElementRuleInjection.kt'), {
    window, document, MutationObserver, requestAnimationFrame: fn => frames.push(fn)});
  return {window, victim, frames, observers, setRules: next => { rules = next; window.__miowserApplyElementRules(); },
    style: () => style, mutate: () => observers.filter(o => o.connected).forEach(o => o.fn([]))};
}
check('empty and CSS-only element rules install no persistent mutation observer', () => {
  const f = rulesFixture(); assert.equal(f.observers.length, 0);
  f.setRules({hide: ['.advert'], delete: []}); assert.match(f.style().textContent, /display:none/);
  assert.equal(f.observers.length, 0);
  f.mutate(); assert.equal(f.frames.length, 0);
});
check('delete observer is enabled when needed and disconnected after reverting rules', () => {
  const f = rulesFixture(); f.setRules({hide: [], delete: ['.advert']});
  assert.equal(f.victim.isConnected, false); assert.equal(f.observers.filter(o => o.connected).length, 1);
  f.victim.isConnected = true; f.mutate(); f.mutate(); assert.equal(f.frames.length, 1);
  f.frames.shift()(); assert.equal(f.victim.isConnected, false);
  f.setRules({hide: [], delete: []}); assert.equal(f.observers.filter(o => o.connected).length, 0);
  f.mutate(); assert.equal(f.frames.length, 0);
});
console.log(checks + ' browser-page interaction checks passed; no device tests were run.');
