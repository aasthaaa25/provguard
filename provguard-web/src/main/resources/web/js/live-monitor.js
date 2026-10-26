import { h, mount, icon, clear } from './dom.js';
import { store, subscribe, pause, resume, clearView, showAll, visibleEvents, reconnect } from './store.js';
import { fmtTime, fmtUtc, fmtNum, sinkLabel, severityBadge, decisionBadge, outcomeBadge, val } from './format.js';
import { pageHead, panel, eventsTable, noEventsState, notice } from './ui.js';
import { emptyFilters, filterBar, matches, hasFilters } from './filters.js';

const RENDER_LIMIT = 300;

export function render({ params }) {
  const filters = emptyFilters();
  let selectedId = params[0] || null;
  let alive = true;

  const pauseBtn = h('button', { class: 'btn', type: 'button', 'aria-pressed': 'false', onclick: () => (store.paused ? resume() : pause()) });
  const clearBtn = h('button', { class: 'btn', type: 'button', onclick: clearView,
    title: 'Hides the events currently shown. The log file and the agent are not touched.' }, 'Clear view');
  const showAllBtn = h('button', { class: 'btn ghost', type: 'button', onclick: showAll }, 'Show cleared events');
  const reconnectBtn = h('button', { class: 'btn ghost', type: 'button', onclick: reconnect }, icon('refresh'), 'Reconnect');
  const statusLine = h('div', { class: 'status-line', role: 'status' });
  const tableHost = h('div', { class: 'table-scroll' });
  const side = h('aside', { class: 'detail-side', 'aria-label': 'Selected event' });
  const monitor = h('div', { class: 'monitor' });
  const bar = filterBar(filters, drawTable, { searchHint: 'Search sink, class, method, rule, thread, or ID' });

  const card = h('section', { class: 'panel' }, bar.controls, bar.chips, statusLine, tableHost);

  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } },
    pageHead('Live monitor', 'Events appear as the agent logs them. Pausing or clearing only changes this display. Monitoring and the audit log continue.',
      pauseBtn, clearBtn, showAllBtn, reconnectBtn),
    notice('info', 'This view holds the most recent 1,000 events. Use Audit logs to search the whole log.'),
    monitor);
  monitor.append(card, side);

  function selectEvent(e) {
    selectedId = e.id;
    history.replaceState(null, '', '#/live/' + encodeURIComponent(e.id));
    drawTable();
    drawSide();
    const closeBtn = side.querySelector('button');
    if (closeBtn) closeBtn.focus({ preventScroll: true });
  }

  function drawStatus(shown, loaded) {
    const c = store.conn;
    const state = c.state === 'live' ? 'Connected' : c.state === 'reconnecting' ? 'Reconnecting' : c.state === 'connecting' ? 'Connecting' : 'Connection problem';
    const transport = c.mode === 'sse' ? 'live stream' : 'polling every 3 s';
    mount(statusLine,
      h('span', {}, h('span', { class: 'dot ' + (c.state === 'live' ? 'ok' : 'warn'), 'aria-hidden': 'true' }), ' ' + state + ' (' + transport + ')'),
      h('span', {}, 'Last update ' + (store.lastUpdate ? fmtTime(store.lastUpdate) : 'never')),
      h('span', {}, 'Showing ' + fmtNum(shown) + ' of ' + fmtNum(loaded) + ' loaded'),
      store.paused ? h('span', {}, h('span', { class: 'badge amber' }, 'Display paused'), ' ',
        store.buffer.length ? h('span', { class: 'new-pill' }, store.buffer.length + ' new') : null,
        ' The agent is still monitoring.') : null,
      store.hiddenBeforeSeq ? h('span', { class: 'muted' }, 'Earlier events hidden from this view') : null,
      c.detail ? h('span', { class: 'muted' }, c.detail) : null);
  }

  let lastShown = 0;
  let lastLoaded = 0;
  function drawTable() {
    if (!alive) return;
    const focused = document.activeElement && document.activeElement.closest ? document.activeElement.closest('tr.row-link') : null;
    const focusedId = focused && tableHost.contains(focused) ? focused.dataset.id : null;
    pauseBtn.replaceChildren(icon(store.paused ? 'play' : 'pause'), store.paused ? 'Resume display' : 'Pause display');
    pauseBtn.setAttribute('aria-pressed', String(store.paused));
    showAllBtn.hidden = !store.hiddenBeforeSeq;
    const base = visibleEvents();
    const list = hasFilters(filters) ? base.filter((e) => matches(e, filters)) : base;
    lastShown = list.length;
    lastLoaded = store.events.length;
    drawStatus(lastShown, lastLoaded);
    clear(tableHost);
    if (store.loadError && !store.events.length) {
      tableHost.appendChild(notice('bad', 'Events could not be loaded: ' + store.loadError));
    } else if (!store.events.length) {
      tableHost.appendChild(noEventsState(store.health));
    } else if (!list.length) {
      tableHost.appendChild(h('div', { class: 'empty' }, h('h2', {}, 'No events match'),
        h('p', {}, hasFilters(filters) ? 'No loaded event matches the current filters. Remove a filter to see more.' : 'Everything loaded was cleared from this view. Use “Show cleared events” to bring it back.')));
    } else {
      const table = eventsTable(list.slice(0, RENDER_LIMIT), { onSelect: selectEvent, selectedId });
      table.querySelector('tbody').addEventListener('keydown', (ev) => {
        const row = ev.target.closest && ev.target.closest('tr');
        if (!row) return;
        const next = ev.key === 'ArrowDown' ? row.nextElementSibling : ev.key === 'ArrowUp' ? row.previousElementSibling : null;
        if (next) { ev.preventDefault(); next.focus(); }
      });
      tableHost.appendChild(table);
      if (list.length > RENDER_LIMIT) tableHost.appendChild(h('p', { class: 'muted', style: { padding: '10px 16px' } }, 'Showing the newest ' + RENDER_LIMIT + ' matches.'));
      if (focusedId) {
        const again = [...tableHost.querySelectorAll('tr.row-link')].find((r) => r.dataset.id === focusedId);
        if (again) again.focus({ preventScroll: true });
      }
    }
  }

  function drawSide() {
    const e = selectedId && store.byId.get(selectedId);
    monitor.classList.toggle('has-detail', !!e);
    side.hidden = !e;
    if (!e) { clear(side); return; }
    mount(side, panel('Selected event', null, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
      h('div', { class: 'actions' }, outcomeBadge(e.outcome),
        h('button', { class: 'btn small ghost', type: 'button', 'aria-label': 'Close selected event', onclick: () => {
          selectedId = null; history.replaceState(null, '', '#/live'); drawTable(); drawSide();
        } }, icon('x'), 'Close')),
      h('dl', { class: 'kv kv-tight' },
        h('dt', {}, 'Time'), h('dd', {}, fmtUtc(e.ts)),
        h('dt', {}, 'Sink'), h('dd', {}, sinkLabel(e.sinkType)),
        h('dt', {}, 'Mode'), h('dd', {}, val(e.mode)),
        h('dt', {}, 'Severity'), h('dd', {}, severityBadge(e.riskLevel)),
        h('dt', {}, 'Rule score'), h('dd', {}, val(e.ruleScore)),
        h('dt', {}, 'ML score'), h('dd', {}, Number.isFinite(e.mlScore) ? String(e.mlScore) : h('span', { class: 'na' }, 'Not scored (' + (e.modelStatus || 'unknown') + ')')),
        h('dt', {}, 'Decision'), h('dd', {}, decisionBadge(e.decision)),
        h('dt', {}, 'Policy decision'), h('dd', {}, decisionBadge(e.policyDecision)),
        h('dt', {}, 'Enforced'), h('dd', {}, e.enforced === null ? val(null) : e.enforced ? 'Yes, the call was stopped' : 'No')),
      e.matchedRules && e.matchedRules.length
        ? h('div', {}, h('div', { class: 'muted', style: { marginBottom: '6px' } }, 'Matched rules'),
          h('div', { class: 'pill-list' }, e.matchedRules.map((r) => h('span', { class: 'badge muted mono' }, r))))
        : h('p', { class: 'muted' }, 'No rule matched.'),
      h('div', { class: 'actions' },
        h('a', { class: 'btn primary', href: '#/path/' + encodeURIComponent(e.id) }, 'Open call path'),
        h('a', { class: 'btn', href: '#/event/' + encodeURIComponent(e.id) }, 'Full details')))));
  }

  let signature = '';
  const unsubscribe = subscribe(() => {
    const next = [store.latestSeq, store.events.length, store.paused, store.buffer.length, store.hiddenBeforeSeq, store.loaded, store.loadError].join('|');
    if (next !== signature) { signature = next; drawTable(); drawSide(); } else { drawStatus(lastShown, lastLoaded); }
  });
  drawTable();
  drawSide();
  return { el, destroy() { alive = false; unsubscribe(); } };
}
