import { h, mount, icon, clear } from './dom.js';
import { api } from './api.js';
import { fmtNum, sinkLabel } from './format.js';
import { pageHead, panel, eventsTable, noEventsState, notice, loading } from './ui.js';
import { emptyFilters, filterBar, toParams, hasFilters } from './filters.js';
import { store } from './store.js';

const PAGE = 100;

function csvCell(value) {
  let text = value === null || value === undefined ? '' : String(value);
  if (text && '=+-@\t\r'.includes(text[0])) text = "'" + text; // stop spreadsheets reading it as a formula
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}

function download(name, type, content) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function render() {
  const filters = emptyFilters();
  let items = [];
  let total = 0;
  let nextBefore = null;
  let busy = false;
  let error = null;
  let alive = true;
  let generation = 0;

  const tableHost = h('div');
  const footer = h('div', { class: 'status-line', role: 'status' });
  const exportHost = h('div', { class: 'actions' });
  const bar = filterBar(filters, reload, { dates: true, searchHint: 'Search sink, class, method, rule, thread, or ID' });

  async function fetchPage(before) {
    const my = generation;
    busy = true;
    error = null;
    draw();
    try {
      const page = await api.events({ ...toParams(filters), limit: PAGE, before: before || undefined });
      if (!alive || my !== generation) return;
      items = before ? items.concat(page.items) : page.items;
      total = page.totalMatching;
      nextBefore = page.truncated ? page.nextBefore : null;
    } catch (e) {
      if (alive && my === generation) error = e.message;
    }
    if (alive && my === generation) { busy = false; draw(); }
  }

  function reload() { generation++; items = []; nextBefore = null; fetchPage(null); }

  function drawExport() {
    const params = { ...toParams(filters), limit: 10000 };
    const mk = (format, label) => h('a', { class: 'btn small', href: api.exportUrl({ ...params, format }), download: '' }, icon('download'), label);
    mount(exportHost,
      h('button', { class: 'btn small', type: 'button', disabled: items.length === 0, onclick: () => {
        const head = ['id', 'timestamp', 'sinkType', 'mode', 'ruleScore', 'mlScore', 'finalScore', 'riskLevel', 'decision', 'policyDecision', 'enforced', 'outcome', 'matchedRules', 'entry'];
        const lines = [head.join(',')].concat([...items].reverse().map((e) => head.map((k) => csvCell(k === 'matchedRules' ? (e.matchedRules || []).join(';') : k === 'timestamp' ? e.timestamp : e[k])).join(',')));
        download('provguard-loaded-events.csv', 'text/csv', lines.join('\r\n') + '\r\n');
      } }, icon('download'), 'Export loaded (CSV)'),
      h('button', { class: 'btn small', type: 'button', disabled: items.length === 0, onclick: () => {
        download('provguard-loaded-events.json', 'application/json', JSON.stringify([...items].reverse(), null, 2));
      } }, icon('download'), 'Export loaded (JSON)'),
      mk('csv', 'Export all matching (CSV)'),
      mk('jsonl', 'Export all matching (JSONL)'));
  }

  function draw() {
    clear(tableHost);
    if (busy && !items.length) tableHost.appendChild(loading('Reading the log'));
    else if (error) tableHost.appendChild(notice('bad', 'Could not read events: ' + error));
    else if (!items.length) {
      tableHost.appendChild(total === 0 && !hasFilters(filters) ? noEventsState(store.health)
        : h('div', { class: 'empty' }, h('h2', {}, 'No events match'), h('p', {}, 'Nothing in the log matches the current filters.')));
    } else {
      tableHost.appendChild(eventsTable(items, { onSelect: (e) => { location.hash = '#/event/' + encodeURIComponent(e.id); } }));
    }
    mount(footer,
      h('span', {}, fmtNum(total) + ' matching event' + (total === 1 ? '' : 's') + ', ' + fmtNum(items.length) + ' loaded'),
      nextBefore ? h('button', { class: 'btn small', type: 'button', disabled: busy, onclick: () => fetchPage(nextBefore) }, busy ? 'Loading' : 'Load ' + PAGE + ' older events') : null,
      h('span', { class: 'muted' }, 'Newest first'));
    drawExport();
  }

  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } },
    pageHead('Audit logs', 'Search the recorded events. This page reads from the log through the backend and never changes it.',
      h('button', { class: 'btn', type: 'button', onclick: reload }, icon('refresh'), 'Refresh')),
    h('section', { class: 'panel' }, bar.controls, bar.chips, tableHost, footer),
    panel('Export', 'Exports are copies. The source log is not modified.', h('div', { style: { display: 'flex', flexDirection: 'column', gap: '10px' } }, exportHost,
      h('p', { class: 'muted' }, '“Loaded” exports what is on this page. “All matching” is streamed by the backend, newest 10,000 matches at most, oldest first. Spreadsheet formula characters are neutralised in CSV.'))));

  reload();
  return { el, destroy() { alive = false; } };
}
