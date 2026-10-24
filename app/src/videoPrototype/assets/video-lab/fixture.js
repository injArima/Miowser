/* Fixture-only bridge. Never injected into normal browser tabs. */
(() => {
  'use strict';
  const query = new URLSearchParams(location.search);
  const generation = Number(query.get('generation'));
  const nonce = query.get('nonce');
  const documentId = crypto.randomUUID();
  const video = document.getElementById('fixture-video');
  const open = document.getElementById('open');
  const status = document.getElementById('status');
  const bridge = window.MiowserVideoLab;
  const kind = query.get('kind') === 'mse' ? 'mse' : 'direct';
  let sequence = 0, sourceChanges = 0, metadataLoads = 0;
  let lastSent = 0, timer = null, disposed = false, pending = null;
  let originalControls = true, objectUrl = null, mediaSource = null;
  let buffer = null, abort = null;
  const finite = value => Number.isFinite(value) ? value : null;

  function emit(type, message) {
    if (disposed) return;
    if (timer !== null) { clearTimeout(timer); timer = null; }
    lastSent = performance.now();
    const end = video.seekable.length ? video.seekable.length - 1 : -1;
    const buffered = video.buffered.length ? video.buffered.end(video.buffered.length - 1) : 0;
    const state = {
      type, generation, nonce, documentId, sequence: sequence++,
      position: finite(video.currentTime), duration: finite(video.duration), buffered: finite(buffered),
      playing: !video.paused && !video.ended, buffering: !video.paused && video.readyState < 3,
      speed: finite(video.playbackRate), width: video.videoWidth, height: video.videoHeight,
      seekStart: end >= 0 ? finite(video.seekable.start(end)) : null,
      seekEnd: end >= 0 ? finite(video.seekable.end(end)) : null,
      sourceKind: kind === 'mse' ? 'MediaSource / blob' : 'Direct WebM',
      sourceChanges, metadataLoads, message
    };
    if (bridge) bridge.postMessage(JSON.stringify(state));
    status.textContent = message || `${state.position?.toFixed(1)} s · ${state.width}×${state.height}`;
  }

  // At most four ordinary updates per second; no polling loop or whole-document scan.
  function schedule() {
    if (disposed || timer !== null) return;
    timer = setTimeout(() => { timer = null; emit('state'); }, Math.max(0, 250 - (performance.now() - lastSent)));
  }
  function fail(error) { emit('error', String(error?.message || error).slice(0, 200)); }
  const events = ['play', 'pause', 'playing', 'waiting', 'timeupdate', 'seeking', 'seeked',
    'ratechange', 'resize', 'durationchange', 'progress', 'ended'];
  events.forEach(event => video.addEventListener(event, schedule));
  function onLoadStart() { sourceChanges++; schedule(); }
  function onMetadata() { metadataLoads++; open.disabled = !bridge || !document.fullscreenEnabled; schedule(); }
  function onMediaError() { fail(`Video error ${video.error?.code || 'unknown'}`); }
  video.addEventListener('loadstart', onLoadStart);
  video.addEventListener('loadedmetadata', onMetadata);
  video.addEventListener('error', onMediaError);

  function enter() {
    if (disposed || !bridge || video.readyState < 1 || document.fullscreenElement) return;
    originalControls = video.controls;
    video.controls = false;
    emit('enter');
    // Keep requestFullscreen in the actual DOM click: no asynchronous activation transfer.
    try { video.requestFullscreen().catch(error => { video.controls = originalControls; fail(error); }); }
    catch (error) { video.controls = originalControls; fail(error); }
  }
  open.addEventListener('click', enter);
  function onFullscreen() {
    if (!document.fullscreenElement) { video.controls = originalControls; emit('exit'); }
    else emit('state');
  }
  document.addEventListener('fullscreenchange', onFullscreen);

  if (bridge) bridge.onmessage = event => {
    if (disposed || typeof event.data !== 'string' || event.data.length > 1024) return;
    try {
      const command = JSON.parse(event.data);
      if (command.generation !== generation || command.documentId !== documentId || command.nonce !== nonce) return;
      switch (command.action) {
        case 'play': video.play().catch(fail); break;
        case 'pause': video.pause(); break;
        case 'seek': {
          if (!Number.isFinite(command.value) || !video.seekable.length) return;
          const i = video.seekable.length - 1;
          video.currentTime = Math.max(video.seekable.start(i), Math.min(command.value, video.seekable.end(i)));
          break;
        }
        case 'rate':
          if (!Number.isFinite(command.value) || command.value < 0.25 || command.value > 3) return;
          video.playbackRate = command.value;
          break;
        default: return;
      }
      schedule();
    } catch (error) { fail(error); }
  };

  function onSourceOpen() {
    try {
      buffer = mediaSource.addSourceBuffer('video/webm; codecs="vp8,opus"');
      const bytes = pending;
      pending = null;
      buffer.addEventListener('updateend', () => {
        if (!disposed && mediaSource.readyState === 'open' && !buffer.updating) mediaSource.endOfStream();
      }, { once: true });
      buffer.addEventListener('error', () => fail('MediaSource append failed'), { once: true });
      buffer.appendBuffer(bytes);
    } catch (error) { fail(error); }
  }

  async function prepare() {
    if (kind !== 'mse') { video.src = 'sample.webm'; return; }
    if (!window.MediaSource || !MediaSource.isTypeSupported('video/webm; codecs="vp8,opus"')) {
      fail('This WebView does not support the fixture’s VP8/Opus MediaSource format.'); return;
    }
    // Bounded bundled test asset only. This is not a general-purpose blob download/copy path.
    abort = new AbortController();
    const response = await fetch('sample.webm', { signal: abort.signal });
    if (!response.ok) throw new Error('Could not read the offline fixture');
    pending = await response.arrayBuffer();
    if (disposed) { pending = null; return; }
    mediaSource = new MediaSource();
    mediaSource.addEventListener('sourceopen', onSourceOpen, { once: true });
    objectUrl = URL.createObjectURL(mediaSource);
    video.src = objectUrl;
  }

  function dispose() {
    disposed = true;
    if (timer !== null) clearTimeout(timer);
    abort?.abort();
    pending = null;
    if (bridge) bridge.onmessage = null;
    events.forEach(event => video.removeEventListener(event, schedule));
    video.removeEventListener('loadstart', onLoadStart);
    video.removeEventListener('loadedmetadata', onMetadata);
    video.removeEventListener('error', onMediaError);
    open.removeEventListener('click', enter);
    document.removeEventListener('fullscreenchange', onFullscreen);
    mediaSource?.removeEventListener('sourceopen', onSourceOpen);
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
  window.addEventListener('pagehide', dispose, { once: true });
  if (!bridge) status.textContent = 'Update Android System WebView to enable the prototype controls.';
  prepare().catch(error => { if (!disposed) fail(error); });
})();
