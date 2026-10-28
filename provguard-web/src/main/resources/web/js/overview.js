import { h, mount, icon } from './dom.js';
import { api } from './api.js';
import { store, subscribe } from './store.js';
import { debounce, fmtNum, fmtAge, fmtBytes, sinkLabel, capitalize } from './format.js';
import { pageHead, panel, loading, errorPanel, noEventsState, eventsTable, notice } from './ui.js';
import { timelineChart, barList } from './charts.js';

const MODE_NOTE = {
  MONITOR: 'Monitor mode records events and never stops a call.',
  ALERT: 'Alert mode records and raises alerts. It never stops a call.',
  BLOCK: 'Block mode can stop a call when a high-confidence rule matches.',
};

function healthRow(dot, title, detail) {
  return h('li', {}, h('span', { class: 'dot ' + dot, 'aria-hidden': 'true' }), h('div', {},
    h('div', { class: 't' }, title), h('div', { class: 'd' }, detail)));
}

function healthPanel(health, stats, model) {
  if (!health) {
    return panel('System health', null, notice('bad', 'The dashboard backend is not answering. Start it with the command in the README, then reload.'));
  }
  const log = health.log;
  const agent = health.agent;
  const agentText = {
    recent_activity: ['ok', 'Recent log activity', 'Events were logged in the last ' + Math.round(agent.activeWindowSeconds / 60) + ' minutes. Last event ' + fmtAge(agent.lastEventAgeSeconds) + '.'],
    idle: ['warn', 'Log is quiet', 'No event for ' + fmtAge(agent.lastEventAgeSeconds).replace(' ago', '') + '. The application may be idle, or the agent may not be running.'],
    no_events: ['warn', 'No events yet', 'The log exists but is empty.'],
    no_log: ['warn', 'No log file', 'The configured log was not found.'],
  }[agent.status] || ['warn', agent.status, ''];
  const mlScored = stats ? stats.ml.scored : 0;
  const mlTotal = stats ? stats.total : 0;
  const items = [
    healthRow('ok', 'Backend: up', 'This service is serving the dashboard. It does not prove an agent is running.'),
    healthRow(log.state === 'ok' ? 'ok' : 'warn', 'Event log: ' + (log.state === 'ok' ? 'readable' : log.state),
      log.fileName + ', ' + fmtBytes(log.sizeBytes) + (log.malformedLines ? ', ' + log.malformedLines + ' malformed line(s) skipped' : '')),
    health.backend.demoMode
      ? healthRow('warn', 'Agent: none', 'Demo mode. These events are synthetic and no agent is attached.')
      : healthRow(agentText[0], 'Agent (inferred): ' + agentText[1].toLowerCase(), agentText[2]),
    healthRow('info', 'Last recorded mode: ' + (agent.mode ? capitalize(agent.mode) : 'not recorded'),
      agent.mode ? MODE_NOTE[agent.mode] || '' : 'No event has been logged, so the mode is not known.'),
    healthRow(model && model.packaged.status === 'loaded' ? 'ok' : 'warn',
      'ML model: ' + (model ? (model.packaged.status === 'loaded' ? 'packaged and loadable' : 'unavailable') : 'unknown'),
      mlTotal ? 'The agent attached an anomaly score to ' + fmtNum(mlScored) + ' of ' + fmtNum(mlTotal) + ' events.' : 'No events to compare against yet.'),
  ];
  return panel('System health', null, h('div', {}, h('ul', { class: 'health-list' }, items),
    h('p', { style: { marginTop: '14px' } }, h('a', { href: '#/settings' }, 'Open full health details'))));
}

export function render() {
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  let model = null;
  let stats = null;
  let error = null;
  let alive = true;

  async function load() {
    try {
      const [s, m] = await Promise.all([api.stats(), model ? Promise.resolve(model) : api.model().catch(() => null)]);
      stats = s; model = m; error = null;
    } catch (e) {
      error = e.message;
    }
    if (alive) draw();
  }

  function draw() {
    const health = store.health;
    const parts = [pageHead('Overview', 'Counts below come from the event log. Nothing is estimated or preset.',
      h('button', { class: 'btn', type: 'button', onclick: load }, icon('refresh'), 'Refresh'))];
    if (error && !stats) {
      parts.push(errorPanel(error, load));
      mount(el, parts);
      return;
    }
    if (!stats) { mount(el, parts, loading('Loading statistics')); return; }
    if (stats.total === 0) {
      parts.push(noEventsState(health), healthPanel(health, stats, model));
      mount(el, parts);
      return;
    }
    parts.push(h('div', { class: 'strip' },
      h('div', {}, h('div', { class: 'num' }, fmtNum(stats.total)), h('div', { class: 'label' }, 'Events recorded'),
        h('div', { class: 'hint' }, 'Monitored API calls in the log')),
      h('div', {}, h('div', { class: ['num', stats.flagged ? 'is-amber' : ''] }, fmtNum(stats.flagged)), h('div', { class: 'label' }, 'Flagged'),
        h('div', { class: 'hint' }, 'Decision was alert or block. Not the same as blocked.')),
      h('div', {}, h('div', { class: ['num', stats.blocked ? 'is-red' : ''] }, fmtNum(stats.blocked)), h('div', { class: 'label' }, 'Blocked'),
        h('div', { class: 'hint' }, 'The agent recorded that it stopped the call')),
      h('div', {}, h('div', { class: 'num' }, fmtNum(stats.policyBlockNotEnforced)), h('div', { class: 'label' }, 'Would block, not enforced'),
        h('div', { class: 'hint' }, 'Policy matched in Monitor or Alert mode; the call ran'))));
    if (stats.source.evictedFromIndex > 0) parts.push(notice('info', stats.source.scope));

    const activity = stats.timeline.buckets.length
      ? timelineChart(stats.timeline)
      : h('p', { class: 'muted' }, 'No timestamps to chart.');
    const sinkItems = Object.entries(stats.bySink);
    const recent = store.events.slice(0, 10);
    parts.push(
      h('div', { class: 'grid-2' },
        panel('Event activity', 'Events per ' + (stats.timeline.bucketSeconds >= 60 ? Math.round(stats.timeline.bucketSeconds / 60) + ' min' : stats.timeline.bucketSeconds + ' s') + ' interval', activity),
        healthPanel(health, stats, model)),
      h('div', { class: 'grid-2 even' },
        panel('Events by monitored API', null, barList(sinkItems, { total: stats.total, format: sinkLabel })),
        panel('Recent events', 'Newest first. Select one to open its call path.', recent.length
          ? eventsTable(recent, { compact: true, onSelect: (e) => { location.hash = '#/path/' + encodeURIComponent(e.id); } })
          : h('p', { class: 'muted' }, 'Waiting for events.'),
          h('a', { href: '#/live' }, 'Open live monitor'))));
    mount(el, parts);
  }

  const refresh = debounce(load, 1500);
  let lastSeq = store.latestSeq;
  const unsubscribe = subscribe((s) => {
    if (s.latestSeq !== lastSeq) { lastSeq = s.latestSeq; refresh(); } else if (alive) draw();
  });
  const timer = setInterval(load, 10000);
  draw();
  load();
  return { el, destroy() { alive = false; unsubscribe(); clearInterval(timer); } };
}
