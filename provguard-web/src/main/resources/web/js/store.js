import { api, connectLive } from './api.js';

// Shared client-side state. The agent and the log are never touched from here: pausing or clearing the view
// only changes what this page shows.

const MAX_EVENTS = 1000;
const listeners = new Set();

export const store = {
  health: null,
  healthError: null,
  events: [],          // newest first, what the Live Monitor shows
  byId: new Map(),
  buffer: [],          // events received while the display is paused
  paused: false,
  hiddenBeforeSeq: 0,  // "Clear view" hides everything up to this sequence number, on this page only
  latestSeq: 0,
  epoch: null,
  loaded: false,
  loadError: null,
  conn: { mode: 'sse', state: 'connecting', detail: null, at: null },
  lastUpdate: null,
  live: null,
};

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

let queued = false;
function emit() {
  if (queued) return;
  queued = true;
  queueMicrotask(() => {
    queued = false;
    for (const fn of listeners) {
      try { fn(store); } catch (error) { console.error(error); }
    }
  });
}

function insertSorted(list, item) {
  if (list.length === 0 || item.seq > list[0].seq) { list.unshift(item); return; }
  let i = 0;
  while (i < list.length && list[i].seq > item.seq) i++;
  list.splice(i, 0, item);
}

function trim() {
  while (store.events.length > MAX_EVENTS) {
    const dropped = store.events.pop();
    store.byId.delete(dropped.id);
  }
}

function ingest(item) {
  if (!item || typeof item.id !== 'string' || store.byId.has(item.id)) return;
  if (item.seq > store.latestSeq) store.latestSeq = item.seq;
  store.byId.set(item.id, item);
  if (store.paused) store.buffer.push(item);
  else { insertSorted(store.events, item); trim(); }
  store.lastUpdate = Date.now();
  emit();
}

function resetAll(epoch) {
  store.events = [];
  store.buffer = [];
  store.byId = new Map();
  store.latestSeq = 0;
  store.hiddenBeforeSeq = 0;
  store.epoch = epoch ?? null;
  emit();
}

export function pause() { store.paused = true; emit(); }
export function resume() {
  store.paused = false;
  for (const item of store.buffer) insertSorted(store.events, item);
  store.buffer = [];
  trim();
  emit();
}
export function clearView() { store.hiddenBeforeSeq = store.latestSeq; emit(); }
export function showAll() { store.hiddenBeforeSeq = 0; emit(); }
export function visibleEvents() {
  return store.hiddenBeforeSeq ? store.events.filter((e) => e.seq > store.hiddenBeforeSeq) : store.events;
}

export async function loadInitial() {
  try {
    const page = await api.events({ limit: 500 });
    resetAll(page.epoch);
    for (const item of [...page.items].reverse()) ingest(item);
    store.latestSeq = Math.max(store.latestSeq, page.latestSeq || 0);
    store.loaded = true;
    store.loadError = null;
    store.lastUpdate = Date.now();
  } catch (error) {
    store.loadError = error.message;
  }
  emit();
}

export async function refreshHealth() {
  try {
    store.health = await api.health();
    store.healthError = null;
    if (store.epoch !== null && store.health.epoch !== store.epoch) {
      await loadInitial(); // log rotated or truncated, or backend restarted
    } else if (store.epoch === null) {
      store.epoch = store.health.epoch;
    }
  } catch (error) {
    store.healthError = error.message;
    store.health = null;
  }
  emit();
}

export function start() {
  refreshHealth().then(loadInitial).then(() => {
    store.live = connectLive({
      onEvent: ingest,
      onReset: async (info) => { resetAll(info.epoch); await loadInitial(); },
      onHello: async (hello) => {
        if (store.epoch !== null && hello.epoch !== undefined && hello.epoch !== store.epoch) {
          resetAll(hello.epoch);
          await loadInitial();
        } else if (store.epoch === null) {
          store.epoch = hello.epoch;
        }
      },
      onState: (conn) => { store.conn = conn; emit(); },
      getLatestSeq: () => store.latestSeq,
    });
  });
  setInterval(refreshHealth, 5000);
}

export function reconnect() {
  if (store.live) store.live.reconnect();
  refreshHealth();
}
