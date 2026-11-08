const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = name => fs.readFileSync(path.join(__dirname,
  '../../main/java/com/miowser/app/browser/video', name + '.kt'), 'utf8').split('"""')[1];
function style() {
  const values = new Map();
  return {getPropertyValue: k => values.get(k)?.[0] || '',
    getPropertyPriority: k => values.get(k)?.[1] || '',
    setProperty: (k, v, p = '') => values.set(k, [v, p]), removeProperty: k => values.delete(k)};
}
function element(parent = null) {
  const attributes = new Map();
  const node = {style: style(), parentElement: parent, children: [], isConnected: true,
    getRootNode: () => document, addEventListener() {}, removeEventListener() {},
    setAttribute: (k, v) => attributes.set(k, v), getAttribute: k => attributes.get(k) ?? null,
    removeAttribute: k => attributes.delete(k), appendChild() {}, remove() {}};
  parent?.children.push(node);
  return node;
}
const document = {addEventListener() {}, removeEventListener() {}, fullscreenElement: null,
  visibilityState: 'visible', createElement: () => element(), head: {appendChild() {}}};
const window = {addEventListener() {}, removeEventListener() {}, requestAnimationFrame: () => 1,
  cancelAnimationFrame() {}, getComputedStyle: () => ({display: 'block', visibility: 'visible', opacity: '1'})};
const context = {window, document, performance: {now: () => 1000},
  setTimeout: () => 1, clearTimeout() {}, MutationObserver: class {observe() {} disconnect() {}}};
vm.createContext(context);
vm.runInContext(source('BrowserVideoStageScript'), context);

// Present an existing video and restore only our edits; preserve concurrent site edits.
const body = element(), container = element(body), sibling = element(body), video = element(container);
video.style.setProperty('width', '640px');
container.style.setProperty('transform', 'translateX(10px)');
const restore = window.__miowserStageVideo(video);
assert.equal(video.style.getPropertyValue('position'), 'fixed');
assert.equal(sibling.style.getPropertyValue('visibility'), 'hidden');
container.style.setProperty('overflow', 'auto');
restore();
assert.equal(video.style.getPropertyValue('width'), '640px');
assert.equal(video.style.getPropertyValue('position'), '');
assert.equal(container.style.getPropertyValue('transform'), 'translateX(10px)');
assert.equal(container.style.getPropertyValue('overflow'), 'auto');
assert.equal(sibling.style.getPropertyValue('visibility'), '');

// A companion handoff must work even when the webpage Fullscreen API is unavailable.
Object.assign(video, {currentSrc: 'https://media.example/episode.mp4', srcObject: null, duration: 1200,
  videoWidth: 1920, videoHeight: 1080, paused: false, ended: false, muted: true, volume: 0,
  controls: true, readyState: 4, playbackRate: 1,
  requestFullscreen() { throw new Error('Fullscreen API must not be used for automatic handoff'); },
  pause() { this.paused = true; }});
const record = {id: 'episode', video, sourceRevision: 1};
let assistant, presented = 0, stageRequests = 0;
vm.runInContext(source('VideoAssistantButtonScript'), context);
assistant = window.__miowserCreateVideoButton({records: () => new Map([['episode', record]]),
  bound: () => true, changed() {}, entering: () => 1,
  stage: active => { if (active) { stageRequests++; assistant.stageReady(); } },
  fullscreen: (r, attempt, active, staged) => {
    assert.equal(r, record); assert.equal(active, true); assert.equal(staged, true); presented++;
  }});
assistant.policy({enabled: true, autoEnter: true});
assert.equal(assistant.maybeAutoEnter(record), true);
assert.equal(presented, 1);
assert.equal(stageRequests, 1);
assert.equal(video.muted, false);
assert.equal(video.volume, 1);
assert.equal(assistant.maybeAutoEnter(record), false);
assert.equal(assistant.owner().record, record);

// Controls must operate on this exact staged owner despite document.fullscreenElement being null.
let acknowledged = false;
vm.runInContext(source('BrowserVideoControlScript'), context);
const controls = window.__miowserCreateVideoControls({owner: () => assistant.owner(),
  refreshSource() {}, reply: (type, command, ok) => { acknowledged = type === 'controlAck' && ok; }});
controls.receive({commandId: 1, elementId: record.id, attempt: 1, sourceRevision: 1, action: 'pause'});
assert.equal(video.paused, true);
assert.equal(acknowledged, true);
assistant.release(record.id, 1);
assert.equal(assistant.owner(), null);
assert.equal(video.style.getPropertyValue('width'), '640px');
assert.equal(video.controls, true);
console.log('Automatic handoff, staged controls and page restoration passed');
