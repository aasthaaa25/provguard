import { h, mount, icon } from './dom.js';
import { api } from './api.js';
import { store, subscribe, reconnect, refreshHealth } from './store.js';
import { fmtDateTime, fmtBytes, fmtUptime, fmtAge, fmtNum, badge, val, capitalize } from './format.js';
import { pageHead, panel, notice } from './ui.js';
import { FRONTEND_VERSION } from './app.js';

function kv(rows) {
  return h('dl', { class: 'kv kv-tight' }, rows.flatMap(([k, v]) => [h('dt', {}, k), h('dd', {}, v)]));
}

const AGENT_LABEL = {
  recent_activity: badge('green', 'Recent log activity'),
  idle: badge('amber', 'Log is quiet'),
  no_events: badge('amber', 'No events yet'),
  no_log: badge('amber', 'No log file'),
};

export function render() {
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  let model = null;
  let alive = true;

  function draw() {
    if (!alive) return;
    const hd = store.health;
    const parts = [pageHead('Settings and health', 'Backend health and agent health are different things. This page keeps them apart.',
      h('button', { class: 'btn', type: 'button', onclick: refreshHealth }, icon('refresh'), 'Refresh now'),
      h('button', { class: 'btn', type: 'button', onclick: reconnect }, icon('refresh'), 'Reconnect live stream'))];

    parts.push(notice('info', h('strong', {}, 'Why two kinds of health? '),
      'The backend is the small Java service behind this page. If it is up, the page can load. The agent runs inside your application’s JVM and has no heartbeat the backend can ask. The only evidence of it is what it wrote to the event log, so agent status below is an inference from that log and can be wrong.'));

    if (!hd) {
      parts.push(notice('bad', 'The backend is not reachable: ' + (store.healthError || 'no response') + '. Start it again, then use Refresh now.'));
      mount(el, parts);
      return;
    }
    const { backend, agent, log } = hd;
    const c = store.conn;
    const pk = model && model.packaged;
    parts.push(
      h('div', { class: 'grid-2 even' },
        panel('Backend', 'The dashboard service', kv([
          ['Status', badge('green', 'Up')],
          ['Version', backend.version],
          ['Uptime', fmtUptime(backend.uptimeSeconds)],
          ['Listening on', h('span', { class: 'mono' }, backend.bind)],
          ['Access', backend.localOnly ? 'Local only (loopback)' : badge('amber', 'Remote access enabled')],
          ['Data source', backend.demoMode ? badge('amber', 'DEMO DATA, not live telemetry') : 'Real event log'],
          ['Log poll interval', backend.pollIntervalMs + ' ms'],
        ])),
        panel('Agent', 'Inferred from the event log only', h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } }, kv([
          ['Status', AGENT_LABEL[agent.status] || agent.status],
          ['Confirmed running', badge('muted', 'Cannot be determined')],
          ['Last event', agent.lastEventAt ? fmtDateTime(agent.lastEventTs) + ' (' + fmtAge(agent.lastEventAgeSeconds) + ')' : val(null, 'No events')],
          ['Mode, last recorded', agent.mode ? capitalize(agent.mode) : val(null)],
          ['Monitoring or enforcing', agent.mode === 'BLOCK' ? 'Last event was recorded in Block mode, which can stop calls' : agent.mode ? 'Last event was recorded in a mode that never stops calls' : val(null)],
        ]), h('p', { class: 'muted' }, agent.basis), h('p', { class: 'muted' }, agent.modeSource)))),
      h('div', { class: 'grid-2 even' },
        panel('Event log source', 'Read-only', kv([
          ['File', h('span', { class: 'mono' }, log.fileName)],
          ['State', log.state === 'ok' ? badge('green', 'Readable') : badge('amber', capitalize(log.state))],
          ['Size', fmtBytes(log.sizeBytes)],
          ['Last written', log.lastModifiedMs ? fmtDateTime(log.lastModifiedMs) : val(null)],
          ['Lines read', fmtNum(log.linesRead)],
          ['Events accepted', fmtNum(log.acceptedEvents)],
          ['Events in memory index', fmtNum(log.indexedEvents) + ' (limit ' + fmtNum(log.maxIndexedEvents) + ', ' + fmtNum(log.evictedEvents) + ' older dropped)'],
          ['Malformed lines skipped', fmtNum(log.malformedLines)],
          ['Duplicate events skipped', fmtNum(log.duplicateEvents)],
          ['Log resets seen', fmtNum(log.resets)],
          ['Last read error', log.lastError ? badge('red', log.lastError) : 'None'],
          ['Path', h('span', { class: 'na' }, 'Only the file name is sent to the browser')],
        ])),
        panel('ML model', 'Packaged with this build', kv([
          ['Status', !model ? val(null, 'Loading') : pk.status === 'loaded' ? badge('green', 'Loaded') : badge('amber', 'Unavailable')],
          ['Type', pk ? val(pk.type) : val(null)],
          ['Trees', pk ? val(pk.trees) : val(null)],
          ['Used by the agent', h('span', { class: 'na' }, 'Not confirmed here. Each event records its own model status.')],
        ]))),
      log.recentMalformed.length ? panel('Skipped lines', 'Line numbers and reasons only. Contents are not shown.',
        h('div', { class: 'table-wrap' }, h('table', {}, h('thead', {}, h('tr', {}, h('th', {}, 'Line'), h('th', {}, 'Reason'))),
          h('tbody', {}, log.recentMalformed.map((m) => h('tr', {}, h('td', { class: 'num' }, m.line), h('td', {}, m.reason))))))) : null,
      panel('Frontend', null, kv([
        ['Version', FRONTEND_VERSION],
        ['Live updates', c.mode === 'sse' ? 'Server-Sent Events' : 'Polling every 3 seconds (stream unavailable)'],
        ['Connection', c.state === 'live' ? badge('green', 'Connected') : badge('amber', capitalize(c.state)) ],
        ['Events cached in this tab', fmtNum(store.events.length) + ' (limit 1,000)'],
        ['API origin', h('span', { class: 'mono' }, location.origin)],
        ['Display state', store.paused ? 'Paused (monitoring continues)' : 'Updating'],
      ])));
    mount(el, parts);
  }

  api.model().then((m) => { model = m; draw(); }).catch(() => { model = { packaged: { status: 'unavailable', detail: '' } }; draw(); });
  const unsubscribe = subscribe(draw);
  draw();
  return { el, destroy() { alive = false; unsubscribe(); } };
}
