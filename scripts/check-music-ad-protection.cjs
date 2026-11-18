'use strict';
// Host-only regression suite. Run: node --test scripts/check-music-ad-protection.cjs
// Native Node WHATWG Response/Request/streams are used, NOT JSON-shaped Response mocks.
// Node has no XHR or browser Location: their small fixtures model those provider surfaces.
// Only final response URL metadata/clone URL propagation is supplied by the transport fixture;
// body consumption, errors, headers, status, clone teeing and readers are entirely native.
const assert = require('node:assert/strict');
const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const base = path.join(__dirname, '../app/src/main/java/com/miowser/app');
const kotlin = fs.readFileSync(path.join(base, 'adblock/MusicAdProtection.kt'), 'utf8');
function constant(name) {
  const match = kotlin.match(new RegExp('private const val ' + name + ' = """([\\s\\S]*?)"""'));
  assert(match, name);
  return match[1];
}
const resource = JSON.parse(constant('MUSIC_RESOURCE'));
const source = constant('MUSIC_RUNTIME').replace('/* MUSIC_RESOURCE */', constant('MUSIC_RESOURCE'));
const script = new vm.Script(source, {filename: 'MusicAdProtection.js'});
const music = 'https://music.youtube.com';
const playerURL = music + '/youtubei/v1/player?key=fixture';
const nextURL = music + '/youtubei/v1/next';
const nativeParse = JSON.parse;
const fields = ['playerAds', 'adPlacements', 'adSlots'];
const payload = () => ({videoDetails: {videoId: 'song-1', title: 'Track'},
  playabilityStatus: {status: 'OK'}, streamingData: {formats: [{url: 'https://media.test/track'}]},
  playerAds: [{ad: true}], adPlacements: [{ad: true}], adSlots: [{ad: true}],
  important: 'keep', playlist: {videoId: 'next', playerAds: ['unrelated']}});
function clean(value) {
  for (const field of fields) assert.equal(Object.hasOwn(value, field), false, field);
  assert.equal(value.videoDetails?.videoId, 'song-1');
  assert.equal(value.important, 'keep');
  assert.equal(value.playlist?.playerAds[0], 'unrelated');
}

function fixture(t, options = {}) {
  const urls = new WeakMap();
  const urlDesc = Object.getOwnPropertyDescriptor(Response.prototype, 'url');
  const cloneDesc = Object.getOwnPropertyDescriptor(Response.prototype, 'clone');
  // Keep the original native URL getter's brand check. No Miowser code depends on fixture internals.
  Object.defineProperty(Response.prototype, 'url', {...urlDesc, get() {
    const original = Reflect.apply(urlDesc.get, this, []);
    return urls.get(this) ?? original;
  }});
  Object.defineProperty(Response.prototype, 'clone', {...cloneDesc, value: function(...args) {
    const clone = Reflect.apply(cloneDesc.value, this, args);
    if (urls.has(this)) urls.set(clone, urls.get(this));
    return clone;
  }});
  const xhrState = new WeakMap();
  const xhrCalls = [];
  class XMLHttpRequest {
    constructor() { xhrState.set(this, {url: '', text: '', type: '', status: 0, ready: 0}); }
    open(...args) {
      xhrCalls.push({method: 'open', receiver: this, args});
      const s = xhrState.get(this);
      Object.assign(s, {url: String(args[1]), text: '', json: null, status: 0, ready: 1});
    }
    send(...args) { xhrCalls.push({method: 'send', receiver: this, args}); }
    abort() { Object.assign(xhrState.get(this), {text: '', json: null, status: 0, ready: 0}); }
    get responseURL() { return state(this).url; }
    get responseType() { return state(this).type; }
    set responseType(value) { state(this).type = value; }
    get responseText() {
      const s = state(this);
      if (s.type !== '' && s.type !== 'text') throw new DOMException('Wrong response type', 'InvalidStateError');
      return s.text;
    }
    get response() {
      const s = state(this);
      return s.type === 'json' ? s.json : s.type === '' || s.type === 'text' ? s.text : s.binary;
    }
    get status() { return state(this).status; }
    get readyState() { return state(this).ready; }
    getAllResponseHeaders() { state(this); return 'content-type: application/json\r\nx-fixture: yes\r\n'; }
  }
  function state(xhr) {
    if (!xhrState.has(xhr)) throw new TypeError('Illegal invocation');
    return xhrState.get(xhr);
  }
  const listeners = new Map();
  const bridgeCalls = [];
  let policy = options.enabled ?? true;
  const document = {};
  let location = new URL(options.url || music + '/');
  // Browser Location is unforgeable. Only this host fixture can simulate a new document URL.
  Object.defineProperty(document, 'location', {get: () => location});
  const fetchCalls = [];
  function transport(...args) {
    fetchCalls.push({receiver: this, args});
    if (options.fetch) return Reflect.apply(options.fetch, this, args);
    return Promise.resolve(response(JSON.stringify(payload()), {}, playerURL));
  }
  const mediaPlay = function() { return 'native-play'; };
  class HTMLMediaElement {}
  HTMLMediaElement.prototype.play = mediaPlay;
  const session = {setActionHandler() {}, setPositionState() {}};
  const context = vm.createContext({document, URL, Response, Request, Headers, XMLHttpRequest,
    fetch: options.nativeFetch ? globalThis.fetch : transport,
    HTMLMediaElement, navigator: {mediaSession: session},
    MiowserAdBlock: {musicAdProtectionEnabled(host, url) { bridgeCalls.push([host, url]); return policy; }},
    addEventListener(name, fn) {
      if (!listeners.has(name)) listeners.set(name, new Set());
      listeners.get(name).add(fn);
    },
    MutationObserver: class { constructor() { throw new Error('No observers permitted'); } },
    setTimeout() { throw new Error('No timers permitted'); },
    setInterval() { throw new Error('No polling permitted'); }
  });
  vm.runInContext('window = globalThis', context);
  const originals = {
    parse: vm.runInContext('JSON.parse', context), fetch: context.fetch,
    json: Response.prototype.json, text: Response.prototype.text,
    response: Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response'),
    responseText: Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText'),
    open: XMLHttpRequest.prototype.open, send: XMLHttpRequest.prototype.send,
    clone: Response.prototype.clone
  };
  function response(body, init = {}, url = playerURL) {
    const value = new Response(body, init);
    urls.set(value, url);
    return value;
  }
  function run(code) { return vm.runInContext(code, context, {timeout: 1500}); }
  function refresh(value = policy) { policy = value; script.runInContext(context, {timeout: 1500}); }
  function emit(name) { for (const fn of listeners.get(name) || []) fn(); }
  const f = {context, originals, bridgeCalls, fetchCalls, xhrCalls, response, run, refresh, emit,
    parse(text, reviver) { return Reflect.apply(run('JSON.parse'), context, [text, reviver]); },
    navigate(url) { location = new URL(url); },
    xhr(type = '', text = JSON.stringify(payload()), url = playerURL) {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', url, true);
      xhr.responseType = type;
      Object.assign(state(xhr), {text, json: type === 'json' ? nativeParse(text) : null,
        status: 200, ready: 4, binary: new ArrayBuffer(8)});
      return xhr;
    }
  };
  t.after(() => {
    try { refresh(false); } finally {
      Object.defineProperty(Response.prototype, 'url', urlDesc);
      Object.defineProperty(Response.prototype, 'clone', cloneDesc);
      Response.prototype.json = originals.json;
      Response.prototype.text = originals.text;
    }
  });
  if (options.setup) options.setup(f);
  refresh();
  assert.equal(context.fetch, originals.fetch, 'fetch stays native/unchanged');
  assert.equal(HTMLMediaElement.prototype.play, mediaPlay);
  assert.equal(context.navigator.mediaSession, session);
  return f;
}

test('trusted versioned resource and integration signatures', () => {
  assert.equal(resource.schema, 'miowser.music-player-data/1');
  assert.equal(resource.version, '1.0.0');
  assert.deepEqual(fields, resource.adFields);
  assert(!fields.includes('important'));
  assert.match(kotlin, /internal object MusicAdProtection/);
  assert.match(kotlin, /fun resourceDigest\(\): String/);
  assert.equal(crypto.createHash('sha256').update(source).digest('hex').length, 64);
  const integration = fs.readFileSync(path.join(base, 'browser/DocumentStartScript.kt'), 'utf8');
  assert.match(integration, /addDocumentStartJavaScript\(this, MusicAdProtection.documentStartScript, setOf\("https:\/\/music.youtube.com"\)\)/);
  assert.match(integration, /fun WebView.applyCosmeticFiltersNow\(\) \{\s*evaluateJavascript\(MusicAdProtection.documentStartScript, null\)/);
  assert(!/\beval\s*\(|new Function\s*\(|MutationObserver|setInterval|setTimeout|HTMLMediaElement|mediaSession/.test(source));
});

for (const url of ['http://music.youtube.com/', 'https://youtube.com/', 'https://www.youtube.com/',
  'https://a.music.youtube.com/', 'https://music.youtube.com.evil.test/', 'https://music.youtube.com:444/',
  'https://music.youtube.com./', 'https://user@music.youtube.com/', 'about:blank']) {
  test('no hooks or bridge calls outside exact origin: ' + url, t => {
    const f = fixture(t, {url});
    assert.equal(f.run('JSON.parse'), f.originals.parse);
    assert.equal(Response.prototype.json, f.originals.json);
    assert.equal(Response.prototype.text, f.originals.text);
    assert.equal(f.bridgeCalls.length, 0);
    assert.equal(f.parse(JSON.stringify(payload())).playerAds.length, 1);
    assert.equal(f.run('Object.hasOwn(window, "ytInitialPlayerResponse")'), false);
  });
}

test('disabled startup is inert; enabling/disabling on an already open page is reversible', async t => {
  const f = fixture(t, {enabled: false});
  assert.equal(f.run('JSON.parse'), f.originals.parse);
  assert.equal(Response.prototype.json, f.originals.json);
  assert.equal(f.parse(JSON.stringify(payload())).adSlots.length, 1);
  f.refresh(true);
  clean(f.parse(JSON.stringify(payload())));
  const jsonWrapper = Response.prototype.json;
  f.refresh(true);
  assert.equal(Response.prototype.json, jsonWrapper, 'idempotent SPA/settings refresh');
  f.refresh(false);
  assert.equal(f.run('JSON.parse'), f.originals.parse);
  assert.equal(Response.prototype.json, f.originals.json);
  assert.equal(Response.prototype.text, f.originals.text);
  assert.deepEqual(Object.getOwnPropertyDescriptor(f.context.XMLHttpRequest.prototype, 'response'), f.originals.response);
  assert.equal((await f.response(JSON.stringify(payload())).json()).playerAds.length, 1);
});

test('explicit default TLS port and canonical hostname are accepted', t => {
  const f = fixture(t, {url: 'https://MUSIC.YOUTUBE.COM:443/watch?v=next'});
  clean(f.parse(JSON.stringify(payload())));
});

test('property overrides survive assignment, source changes and SPA rerenders; off restores latest writes', t => {
  const f = fixture(t);
  for (let i = 0; i < 300; i++) {
    f.context.incoming = payload();
    f.run('ytInitialPlayerResponse = incoming; playerResponse = {adPlacements: [1], playerAds: [2]}');
    assert.equal(f.run('ytInitialPlayerResponse.playerAds'), undefined);
    assert.equal(f.run('ytInitialPlayerResponse.adPlacements'), undefined);
    assert.equal(f.run('ytInitialPlayerResponse.adSlots'), undefined);
    assert.equal(f.run('playerResponse.adPlacements'), undefined);
    assert.equal(f.run('playerResponse.playerAds[0]'), 2, 'only the observed property override');
    f.run('ytInitialPlayerResponse.playerAds = ["late"]; playerResponse.adPlacements = ["late"]');
    assert.equal(f.run('ytInitialPlayerResponse.playerAds'), undefined);
    f.navigate(music + '/watch?v=song-' + i);
  }
  f.refresh(false);
  assert.equal(f.run('ytInitialPlayerResponse.playerAds[0]'), 'late');
  assert.equal(f.run('playerResponse.adPlacements[0]'), 'late');
  assert.equal(f.run('Object.getOwnPropertyDescriptor(window, "ytInitialPlayerResponse").writable'), true);
});

test('existing properties keep descriptors, latest assignments, and accessor/nonconfigurable slots', t => {
  const f = fixture(t, {setup(f) {
    f.context.existing = payload();
    f.context.accessorValue = payload();
    f.run('Object.defineProperty(window, "ytInitialPlayerResponse", {value: existing, writable: true, configurable: true, enumerable: false});' +
      'Object.defineProperty(window, "playerResponse", {get() { return accessorValue; }, configurable: true})');
  }});
  assert.equal(f.run('ytInitialPlayerResponse.adSlots'), undefined);
  assert.equal(f.run('playerResponse.adPlacements.length'), 1);
  f.refresh(false);
  assert.equal(f.run('Object.getOwnPropertyDescriptor(window, "ytInitialPlayerResponse").enumerable'), false);
  assert.equal(f.run('ytInitialPlayerResponse.adSlots.length'), 1);
  f.refresh(true);
  f.run('ytInitialPlayerResponse = Object.freeze({adSlots: ["frozen"]})');
  assert.equal(f.run('ytInitialPlayerResponse.adSlots[0]'), 'frozen');
});

test('JSON prune only removes named fields in player shapes or curated player envelopes', t => {
  const f = fixture(t);
  clean(f.parse(JSON.stringify(payload())));
  for (const key of ['playerResponse', 'ytInitialPlayerResponse']) {
    const value = f.parse(JSON.stringify({[key]: payload(), playlist: {adSlots: ['keep']}}));
    clean(value[key]);
    assert.equal(value.playlist.adSlots[0], 'keep');
  }
  for (const key of ['data', 'response']) clean(f.parse(JSON.stringify({[key]: {playerResponse: payload()}}))[key].playerResponse);
  clean(f.parse(JSON.stringify({responses: [{playerResponse: payload()}]})).responses[0].playerResponse);
  const legitimate = {videoId: 'v', playlistId: 'p', adSlots: ['custom'], playerAds: [1], important: true,
    contents: {videoRenderer: {adPlacements: ['keep']}}};
  assert.deepEqual(JSON.parse(JSON.stringify(f.parse(JSON.stringify(legitimate)))), legitimate);
});

test('parse native coercion, receiver, reviver, thrown errors, and recursion guard are preserved', t => {
  const f = fixture(t);
  let coercions = 0;
  assert.equal(f.parse({toString() { coercions++; return '12'; }}), 12);
  assert.equal(coercions, 1);
  assert.throws(() => f.parse('{broken'), e => e.name === 'SyntaxError');
  const marker = new Error('reviver');
  assert.throws(() => f.parse('{"x":1}', () => { throw marker; }), e => e === marker);
  let calls = 0, nested;
  const parsed = f.parse(JSON.stringify(payload()), function(key, value) {
    calls++;
    if (key === 'title') {
      assert.equal(this.videoId, 'song-1');
      nested = f.parse(JSON.stringify(payload())); // Native re-entry, never recursive hooks.
    }
    return value;
  });
  clean(parsed);
  assert.equal(nested.playerAds.length, 1);
  assert(calls > 10 && calls < 100);
  clean(f.parse(JSON.stringify(payload())), 'guard resets even after errors');
});

test('bounded text bytes, fixed envelope visits, cycles, getters, and schema mismatch fail open', t => {
  const f = fixture(t);
  const large = {...payload(), padding: 'a'.repeat(resource.maxTextBytes)};
  assert.equal(f.parse(JSON.stringify(large)).playerAds.length, 1);
  const unicode = {...payload(), padding: '界'.repeat(Math.ceil(resource.maxTextBytes / 3))};
  assert(JSON.stringify(unicode).length < resource.maxTextBytes);
  assert.equal(f.parse(JSON.stringify(unicode)).playerAds.length, 1);
  const responses = Array.from({length: 200}, () => ({playerResponse: payload()}));
  const result = f.parse(JSON.stringify({responses}));
  clean(result.responses[0].playerResponse);
  assert.equal(result.responses[64].playerResponse.adSlots.length, 1);
  f.context.cyclic = {playerResponse: payload()};
  f.context.cyclic.data = f.context.cyclic;
  f.context.cyclic.response = f.context.cyclic;
  let getterCalls = 0;
  Object.defineProperty(f.context.cyclic, 'responses', {get() { getterCalls++; throw new Error('getter'); }});
  const same = f.parse('{}', (key, value) => key === '' ? f.context.cyclic : value);
  assert.equal(same, f.context.cyclic);
  assert.equal(getterCalls, 0);
  clean(same.playerResponse);
  let deep = {playerResponse: payload()};
  for (let i = 0; i < 10000; i++) deep = {unrelated: deep};
  f.context.deep = deep;
  assert.equal(f.parse('{}', key => key === '' ? deep : null), deep);
  const frozen = Object.freeze(payload());
  assert.equal(f.parse('{}', key => key === '' ? frozen : null), frozen);
  assert.equal(frozen.playerAds.length, 1);
});

test('native Response JSON/text consumption keeps identity, metadata, bodyUsed and clone teeing', async t => {
  const f = fixture(t);
  const response = f.response(JSON.stringify(payload()), {status: 203, statusText: 'Fixture', headers: {'x-fixture': 'yes'}});
  const clone = response.clone();
  const metadata = [response.url, response.type, response.status, response.statusText, response.redirected, response.headers];
  assert.equal(response.bodyUsed, false);
  clean(await response.json());
  assert.equal(response.bodyUsed, true);
  assert.equal(clone.bodyUsed, false);
  clean(nativeParse(await clone.text()));
  assert.deepEqual([response.url, response.type, response.status, response.statusText, response.redirected, response.headers], metadata);
  assert.equal(response.url, playerURL);
  await assert.rejects(response.json(), TypeError);
  await assert.rejects(response.text(), TypeError);
  assert.throws(() => response.clone(), TypeError);
});

test('endpoint, final origin and root-shape gating; malformed/unchanged text stays byte identical', async t => {
  const f = fixture(t);
  const text = JSON.stringify(payload());
  for (const url of [music + '/youtubei/v1/browse', music + '/youtubei/v1/player/extra',
    'https://www.youtube.com/youtubei/v1/player', 'http://music.youtube.com/youtubei/v1/player',
    'https://music.youtube.com:444/youtubei/v1/player', 'https://music.youtube.com.evil/youtubei/v1/player']) {
    assert.equal(await f.response(text, {}, url).text(), text);
    assert.equal((await f.response(text, {}, url).json()).adSlots.length, 1);
  }
  const adOnly = '{"adSlots":[1],"playlistId":"keep"}';
  assert.equal(nativeParse(await f.response(adOnly).text()).playlistId, 'keep');
  assert.equal(Object.hasOwn(await f.response(adOnly).json(), 'adSlots'), false);
  assert.equal(await f.response(adOnly, {}, nextURL).text(), adOnly, 'next root needs a player shape');
  clean((await f.response(JSON.stringify({playerResponse: payload()}), {}, nextURL).json()).playerResponse);
  for (const value of ['  { "playlistId": "keep" } \n', '{not json', '', 'null', '[]']) {
    assert.equal(await f.response(value).text(), value);
  }
  await assert.rejects(f.response('not json').json(), SyntaxError);
});

test('native stream errors, locked/disturbed bodies, body readers and invalid receivers remain native', async t => {
  const f = fixture(t);
  const r = f.response(JSON.stringify(payload()));
  const reader = r.body.getReader();
  await assert.rejects(r.json(), TypeError);
  const chunk = await reader.read();
  assert.equal(nativeParse(new TextDecoder().decode(chunk.value)).playerAds.length, 1, 'raw stream untouched');
  reader.releaseLock();
  await assert.rejects(r.text(), TypeError);
  const marker = new Error('stream failure');
  const broken = f.response(new ReadableStream({start(controller) { controller.error(marker); }}));
  await assert.rejects(broken.text(), e => e === marker);
  await assert.rejects(Reflect.apply(Response.prototype.json, {}, []), TypeError);
  const binary = await f.response(JSON.stringify(payload())).arrayBuffer();
  assert.equal(nativeParse(new TextDecoder().decode(binary)).adSlots.length, 1);
});

test('fetch passes original receiver/Request/args/response through and never refetches', async t => {
  const marker = new Error('network rejected');
  let mode = 'resolve', delivered;
  const f = fixture(t, {fetch() {
    if (mode === 'throw') throw marker;
    if (mode === 'reject') return Promise.reject(marker);
    return Promise.resolve(delivered);
  }});
  delivered = f.response(JSON.stringify(payload()), {status: 206, headers: {'x-fixture': 'yes'}});
  const request = new Request(playerURL, {method: 'POST', body: '{}'});
  const init = {credentials: 'include'};
  const receiver = {custom: true};
  const result = await Reflect.apply(f.context.fetch, receiver, [request, init, 'extra']);
  assert.equal(result, delivered);
  assert.equal(f.fetchCalls[0].receiver, receiver);
  assert.deepEqual(f.fetchCalls[0].args, [request, init, 'extra']);
  clean(await result.json());
  assert.equal(request.bodyUsed, false, 'transport fixture does not consume the request');
  assert.equal(f.fetchCalls.length, 1);
  mode = 'reject';
  await assert.rejects(f.context.fetch(request), e => e === marker);
  mode = 'throw';
  assert.throws(() => f.context.fetch(request), e => e === marker);
  assert.equal(f.fetchCalls.length, 3);
});

test('real native fetch abort and rejection behavior is untouched', async t => {
  const f = fixture(t, {nativeFetch: true});
  assert.equal(f.context.fetch, globalThis.fetch);
  const controller = new AbortController();
  const reason = new DOMException('fixture aborted', 'AbortError');
  controller.abort(reason);
  await assert.rejects(f.context.fetch(new Request(playerURL, {signal: controller.signal})), e => e === reason);
  await assert.rejects(f.context.fetch('http://['), TypeError);
  const r = await f.context.fetch('data:application/json,' + encodeURIComponent(JSON.stringify(payload())));
  assert.equal((await r.json()).playerAds.length, 1, 'actual final response URL determines scope');
});

test('midflight disable and navigation skip pending cleansing; pagehide/pageshow restore policy', async t => {
  const f = fixture(t);
  let deliver;
  const r = f.response(new ReadableStream({start(controller) { deliver = controller; }}));
  const pending = r.json();
  f.refresh(false);
  deliver.enqueue(new TextEncoder().encode(JSON.stringify(payload())));
  deliver.close();
  assert.equal((await pending).playerAds.length, 1);
  f.refresh(true);
  f.emit('pagehide');
  assert.equal(Response.prototype.json, f.originals.json);
  f.emit('pageshow');
  assert.notEqual(Response.prototype.json, f.originals.json);
  f.navigate('https://www.youtube.com/');
  assert.equal(f.parse(JSON.stringify(payload())).playerAds.length, 1, 'actual document origin checked per call');
  f.refresh();
  assert.equal(Response.prototype.json, f.originals.json);
});

test('XHR json/text/default getters preserve metadata, identity, errors, binary and abort semantics', t => {
  const f = fixture(t);
  for (const type of ['', 'text', 'json']) {
    const xhr = f.xhr(type);
    xhr.send('original-body');
    const metadata = [xhr.responseURL, xhr.status, xhr.readyState, xhr.getAllResponseHeaders()];
    const first = xhr.response;
    if (type === 'json') {
      clean(first);
      assert.equal(xhr.response, first);
      assert.throws(() => xhr.responseText, e => e.name === 'InvalidStateError');
    } else {
      clean(nativeParse(first));
      assert.equal(xhr.responseText, first);
      assert.equal(xhr.response, first);
    }
    assert.deepEqual([xhr.responseURL, xhr.status, xhr.readyState, xhr.getAllResponseHeaders()], metadata);
    xhr.abort();
    assert.equal(xhr.status, 0);
    assert.equal(xhr.response, type === 'json' ? null : '');
  }
  assert.equal(f.context.XMLHttpRequest.prototype.open, f.originals.open);
  assert.equal(f.context.XMLHttpRequest.prototype.send, f.originals.send);
  assert.equal(f.xhrCalls.filter(c => c.method === 'send').length, 3);
  const binary = f.xhr('arraybuffer');
  assert.equal(binary.response.byteLength, 8);
  assert.throws(() => binary.responseText, e => e.name === 'InvalidStateError');
  const getter = Object.getOwnPropertyDescriptor(f.context.XMLHttpRequest.prototype, 'response').get;
  assert.throws(() => Reflect.apply(getter, {}, []), TypeError);
  assert.equal(f.xhr('', '{malformed').responseText, '{malformed');
  const nonMusic = f.xhr('json', JSON.stringify(payload()), 'https://www.youtube.com/youtubei/v1/player');
  assert.equal(nonMusic.response.playerAds.length, 1);
});

test('XHR reuse/source changes and disabled text are fresh without reopening/refetching', t => {
  const f = fixture(t);
  const xhr = f.xhr();
  clean(nativeParse(xhr.responseText));
  f.refresh(false);
  assert.equal(nativeParse(xhr.responseText).playerAds.length, 1);
  f.refresh(true);
  clean(nativeParse(xhr.responseText));
  xhr.open('POST', nextURL, true);
  assert.equal(xhr.responseText, '');
});

test('disable never overwrites later website API/getter/global replacements', t => {
  const f = fixture(t);
  const siteJSON = function() { return Promise.resolve({site: true}); };
  const siteText = function() { return Promise.resolve('site'); };
  Response.prototype.json = siteJSON;
  Response.prototype.text = siteText;
  const siteGetter = function() { return 'site'; };
  Object.defineProperty(f.context.XMLHttpRequest.prototype, 'responseText', {configurable: true, get: siteGetter});
  const parseReplacement = f.run('JSON.parse = function siteParse() { return {site: true}; }; JSON.parse');
  f.run('Object.defineProperty(window, "ytInitialPlayerResponse", {value: {site: true}, writable: true, configurable: true})');
  f.refresh(false);
  assert.equal(Response.prototype.json, siteJSON);
  assert.equal(Response.prototype.text, siteText);
  assert.equal(f.run('JSON.parse'), parseReplacement);
  assert.equal(Object.getOwnPropertyDescriptor(f.context.XMLHttpRequest.prototype, 'responseText').get, siteGetter);
  assert.equal(f.run('ytInitialPlayerResponse.site'), true);
  f.refresh(true);
  f.refresh(false);
  assert.equal(Response.prototype.json, siteJSON, 'reenable wraps and restores the then-current API');
});

test('missing bridge, missing provider readers, and nonconfigurable globals keep a working page', async t => {
  const f = fixture(t, {setup(f) {
    f.run('delete MiowserAdBlock; Response = undefined; XMLHttpRequest = undefined;' +
      'Object.defineProperty(window, "ytInitialPlayerResponse", {value: {adSlots: [1]}, configurable: false})');
  }});
  assert.equal(f.run('JSON.parse'), f.originals.parse);
  f.context.MiowserAdBlock = {musicAdProtectionEnabled: () => true};
  f.refresh();
  clean(f.parse(JSON.stringify(payload())));
  assert.equal(f.run('ytInitialPlayerResponse.adSlots[0]'), 1);
  assert.equal(Response.prototype.json, f.originals.json);
});

test('a provider/site reader returning a non-Promise keeps its return, receiver, args and exceptions', t => {
  let call;
  const marker = new Error('site-reader');
  const sentinel = {site: true};
  const f = fixture(t, {setup() {
    Response.prototype.json = function(...args) {
      call = {receiver: this, args};
      if (args[0] === 'throw') throw marker;
      return sentinel;
    };
  }});
  const r = f.response('{}');
  assert.equal(r.json('a', 2), sentinel);
  assert.equal(call.receiver, r);
  assert.deepEqual(call.args, ['a', 2]);
  assert.throws(() => r.json('throw'), e => e === marker);
});
