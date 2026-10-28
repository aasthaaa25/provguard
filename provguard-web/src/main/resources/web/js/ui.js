import { h, icon } from './dom.js';
import { fmtTime, fmtUtc, sinkLabel, severityBadge, decisionBadge, outcomeBadge, val } from './format.js';

export function pageHead(title, subtitle, ...actions) {
  return h('div', { class: 'page-head' },
    h('div', {}, h('h1', { id: 'page-title', tabindex: '-1' }, title), subtitle ? h('p', {}, subtitle) : null),
    actions.length ? h('div', { class: 'actions' }, actions) : null);
}

export function panel(title, subtitle, body, extraHeader) {
  return h('section', { class: 'panel' },
    title ? h('header', {}, h('h2', {}, title), subtitle ? h('p', {}, subtitle) : null, extraHeader || null) : null,
    h('div', { class: 'panel-body' }, body));
}

export function loading(text = 'Loading') {
  return h('div', { class: 'loading', role: 'status' }, h('span', { class: 'spinner' }), text);
}

export function notice(kind, ...children) {
  return h('div', { class: 'notice ' + kind, role: kind === 'bad' ? 'alert' : null }, h('div', {}, children));
}

export function errorPanel(message, onRetry) {
  return h('div', { class: 'panel' }, h('div', { class: 'empty' },
    h('h2', {}, 'Could not load this page'),
    h('p', {}, message),
    onRetry ? h('button', { class: 'btn', type: 'button', onclick: onRetry }, icon('refresh'), 'Try again') : null));
}

export const START_COMMANDS = [
  '# 1. Build once (from the repository root)',
  'mvn -DskipTests package',
  '',
  '# 2. Run a safe demo with the agent attached; it writes events.jsonl',
  '.\\scripts\\run-demo.ps1 -Mode ALERT -Demo suspicious',
  '',
  '# 3. Start this dashboard on that log',
  'java -jar provguard-web\\target\\provguard-web-1.0.0.jar --events events.jsonl',
].join('\n');

export function noEventsState(health) {
  const missing = health && health.log && !health.log.exists;
  return h('div', { class: 'panel' }, h('div', { class: 'empty' },
    h('h2', {}, missing ? 'The event log does not exist yet' : 'No events recorded yet'),
    h('p', {}, missing
      ? 'The dashboard is watching for the log file and will pick it up when the agent creates it. The path must match the agent’s events= argument.'
      : 'The log exists but holds no events. The agent writes a record each time a monitored API is called. This page updates by itself when one arrives.'),
    h('pre', { class: 'cmd' }, START_COMMANDS)));
}

function scoreNode(n) {
  return Number.isFinite(n) ? String(n) : h('span', { class: 'na' }, 'n/a');
}

/**
 * Event table shared by Overview, Live Monitor and Audit logs. Text goes in as text nodes only.
 * `onSelect(event)` is called on click or on Enter/Space.
 */
export function eventsTable(events, { onSelect, selectedId, compact = false } = {}) {
  const head = h('tr', {},
    h('th', { scope: 'col' }, 'Time'),
    h('th', { scope: 'col' }, 'Sink'),
    h('th', { scope: 'col' }, 'Severity'),
    h('th', { scope: 'col', class: 'num', title: 'Rule-based score, 0 to 100' }, 'Rules'),
    h('th', { scope: 'col', class: 'num hide-sm', title: 'Isolation Forest anomaly score, 0 to 100' }, 'ML'),
    h('th', { scope: 'col' }, 'Decision'),
    h('th', { scope: 'col', class: compact ? 'hide-md' : '' }, 'Outcome'),
    h('th', { scope: 'col', class: 'hide-md' }, 'Entry point'));
  const rows = events.map((e) => {
    const tr = h('tr', {
      class: ['row-link', e.id === selectedId ? 'selected' : ''],
      tabindex: '0',
      'data-id': e.id,
      onclick: () => onSelect && onSelect(e),
      onkeydown: (ev) => {
        if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); onSelect && onSelect(e); }
      },
    },
      h('td', { class: 'nowrap', title: fmtUtc(e.ts) }, fmtTime(e.ts)),
      h('td', { class: 'nowrap' }, sinkLabel(e.sinkType)),
      h('td', { class: 'nowrap' }, severityBadge(e.riskLevel), Number.isFinite(e.finalScore) ? h('span', { class: 'muted' }, ' ' + e.finalScore) : null),
      h('td', { class: 'num' }, scoreNode(e.ruleScore)),
      h('td', { class: 'num hide-sm' }, scoreNode(e.mlScore)),
      h('td', {}, decisionBadge(e.decision)),
      h('td', { class: compact ? 'hide-md' : '' }, outcomeBadge(e.outcome)),
      h('td', { class: 'hide-md' }, e.entry ? h('span', { class: 'mono ellipsis', title: e.entry }, e.entry) : h('span', { class: 'na' }, 'Not recorded')));
    return tr;
  });
  return h('div', { class: 'table-wrap' }, h('table', {}, h('thead', {}, head), h('tbody', {}, rows)));
}

export function link(href, text, props) {
  return h('a', { href, ...(props || {}) }, text);
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export { val };

/** Event chooser used by pages that need an id in the URL. Imported lazily to avoid a store cycle at load. */
export function eventPicker(title, subtitle, hashPrefix, store, subscribe) {
  const host = h('div');
  const draw = () => {
    host.replaceChildren(store.events.length
      ? panel('Choose an event', 'Newest first.', eventsTable(store.events.slice(0, 100), { onSelect: (e) => { location.hash = hashPrefix + encodeURIComponent(e.id); } }))
      : noEventsState(store.health));
  };
  draw();
  const unsub = subscribe(draw);
  return { el: h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } }, pageHead(title, subtitle), host), destroy: unsub };
}
