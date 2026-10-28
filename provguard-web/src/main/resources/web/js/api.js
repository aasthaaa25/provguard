// All data comes from the Java bridge. The browser never reads the log file itself.

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function toQuery(params) {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params || {})) {
    if (v !== undefined && v !== null && v !== '' && !(Array.isArray(v) && v.length === 0)) {
      q.set(k, Array.isArray(v) ? v.join(',') : String(v));
    }
  }
  const s = q.toString();
  return s ? '?' + s : '';
}

export async function getJson(path, params, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs || 10000);
  try {
    const response = await fetch(path + toQuery(params), {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal: controller.signal,
    });
    let body = null;
    try { body = await response.json(); } catch { /* non-JSON error body */ }
    if (!response.ok) {
      throw new ApiError((body && body.message) || 'Request failed (' + response.status + ')', response.status, body && body.error);
    }
    return body;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === 'AbortError') throw new ApiError('The dashboard backend did not answer in time.', 0, 'timeout');
    throw new ApiError('Cannot reach the dashboard backend. Is it still running?', 0, 'unreachable');
  } finally {
    clearTimeout(timer);
  }
}

export const api = {
  health: () => getJson('/api/health'),
  events: (params) => getJson('/api/events', params),
  event: (id) => getJson('/api/events/' + encodeURIComponent(id)),
  stats: (params) => getJson('/api/stats', params),
  policies: () => getJson('/api/policies'),
  model: () => getJson('/api/model'),
  exportUrl: (params) => '/api/export' + toQuery(params),
};

/**
 * Live updates. Server-Sent Events are preferred. If the browser gives up on the stream, or EventSource is
 * missing, the client falls back to polling /api/events?after=<seq> and retries SSE periodically.
 * Both paths feed the same handlers, and the store de-duplicates by event id.
 */
export function connectLive({ onEvent, onReset, onHello, onState, getLatestSeq }) {
  let source = null;
  let pollTimer = null;
  let retryTimer = null;
  let stopped = false;
  let failures = 0;

  const setState = (mode, state, detail) => onState({ mode, state, detail: detail || null, at: Date.now() });

  function stopPolling() {
    clearInterval(pollTimer);
    pollTimer = null;
  }

  async function pollOnce() {
    try {
      const page = await api.events({ after: getLatestSeq(), limit: 500 });
      onHello({ epoch: page.epoch, latestSeq: page.latestSeq });
      for (const item of [...page.items].reverse()) onEvent(item);
      setState('polling', 'live');
    } catch (error) {
      setState('polling', 'error', error.message);
    }
  }

  function startPolling() {
    if (pollTimer || stopped) return;
    setState('polling', 'connecting');
    pollOnce();
    pollTimer = setInterval(pollOnce, 3000);
    retryTimer = setTimeout(() => { stopPolling(); openStream(); }, 20000);
  }

  function openStream() {
    if (stopped) return;
    if (typeof EventSource === 'undefined') { startPolling(); return; }
    setState('sse', 'connecting');
    const after = getLatestSeq();
    source = new EventSource('/api/stream' + (after ? '?after=' + after : ''));
    source.addEventListener('hello', (e) => {
      failures = 0;
      try { onHello(JSON.parse(e.data)); } catch { /* ignore */ }
      setState('sse', 'live');
    });
    source.addEventListener('security-event', (e) => {
      try { onEvent(JSON.parse(e.data)); setState('sse', 'live'); } catch { /* malformed frame */ }
    });
    source.addEventListener('reset', (e) => {
      try { onReset(JSON.parse(e.data)); } catch { onReset({}); }
    });
    source.addEventListener('overflow', () => { source.close(); setTimeout(openStream, 500); });
    source.onerror = () => {
      failures += 1;
      if (source.readyState === EventSource.CLOSED || failures >= 4) {
        source.close();
        startPolling();
      } else {
        setState('sse', 'reconnecting');
      }
    };
  }

  openStream();
  return {
    reconnect() {
      if (source) source.close();
      stopPolling();
      clearTimeout(retryTimer);
      failures = 0;
      openStream();
    },
    stop() {
      stopped = true;
      if (source) source.close();
      stopPolling();
      clearTimeout(retryTimer);
    },
  };
}
