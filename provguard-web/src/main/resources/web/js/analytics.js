import { h, mount, icon } from './dom.js';
import { api } from './api.js';
import { store, subscribe } from './store.js';
import { debounce, fmtNum, fmtDateTime, badge, val, OUTCOME_TEXT, capitalize } from './format.js';
import { pageHead, panel, loading, errorPanel, notice, noEventsState } from './ui.js';
import { histogramChart } from './charts.js';

function kv(rows) {
  return h('dl', { class: 'kv kv-tight' }, rows.flatMap(([k, v]) => [h('dt', {}, k), h('dd', {}, v)]));
}

export function render() {
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  let alive = true;
  let seq = store.latestSeq;

  async function load() {
    try {
      const [stats, model] = await Promise.all([api.stats(), api.model()]);
      if (alive) draw(stats, model);
    } catch (error) {
      if (alive) mount(el, pageHead('ML analytics'), errorPanel(error.message, load));
    }
  }

  function draw(stats, model) {
    const pk = model.packaged;
    const loaded = pk.status === 'loaded';
    const ev = model.evaluation;
    const parts = [pageHead('ML analytics', 'What the packaged anomaly model is, what the agent logged with it, and what the offline evaluation report says.',
      h('button', { class: 'btn', type: 'button', onclick: load }, icon('refresh'), 'Refresh'))];

    parts.push(h('div', { class: 'grid-2 even' },
      panel('Model status', null, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
        kv([
          ['Packaged model', loaded ? badge('green', 'Loaded') : badge('amber', 'Unavailable')],
          ['Type', loaded ? pk.type : val(null, 'Unavailable')],
          ['Trees', val(pk.trees)],
          ['Training sample per tree', val(pk.sampleSize)],
          ['Score scaling', loaded ? 'Raw ' + pk.scaleLowRaw + ' maps to 0, raw ' + pk.scaleHighRaw + ' maps to 100' : val(null, 'Unavailable')],
          ['Scoring endpoint (mlUrl)', h('span', { class: 'na' }, 'Not recorded: the agent does not log whether it used the packaged model or a remote endpoint')],
        ]),
        h('p', { class: 'muted' }, pk.detail))),
      panel('What the score means', null, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '10px' } },
        h('p', {}, model.scoreMeaning),
        h('p', { class: 'muted' }, model.liveScoringNote)))));

    if (stats.total === 0) {
      parts.push(noEventsState(store.health));
    } else {
      const ml = stats.ml;
      parts.push(
        notice('info', h('strong', {}, 'Live counts, no ground truth. '), 'The log has no labels saying which events were truly malicious, so precision, recall, or accuracy cannot be computed from live data. The figures below only describe what the agent recorded.'),
        h('div', { class: 'grid-2' },
          panel('Anomaly score distribution', 'Live: scores the agent logged, ' + fmtNum(ml.scored) + ' of ' + fmtNum(stats.total) + ' events',
            ml.scored ? h('div', {}, histogramChart(ml.histogram, { label: 'Distribution of anomaly scores in ten bins of ten', threshold: ml.highThreshold }),
              h('p', { class: 'muted' }, 'Dashed line: score ' + ml.highThreshold + ', the level the blocking policy treats as high. Bars at or above it are amber.'))
              : h('p', { class: 'muted' }, 'No event carries an anomaly score.')),
          panel('Live detection counts', null, kv([
            ['Events with an ML score', fmtNum(ml.scored)],
            ['Events without one', fmtNum(ml.unavailable)],
            ['Mean score', ml.mean === null ? val(null, 'Unavailable') : String(ml.mean)],
            ['Score ' + ml.highThreshold + ' or higher', fmtNum(ml.highCount)],
            ['Decision: allow', fmtNum(stats.byDecision.ALLOW)],
            ['Decision: alert', fmtNum(stats.byDecision.ALERT)],
            ['Decision: block', fmtNum(stats.byDecision.BLOCK)],
            ['Actually blocked', fmtNum(stats.blocked)],
          ]))),
        h('div', { class: 'grid-2 even' },
          panel('Rule score distribution', 'Shown beside the ML scores because the rules, not the model, decide blocking',
            stats.rules.scored ? histogramChart(stats.rules.histogram, { label: 'Distribution of rule scores in ten bins of ten', threshold: 60 })
              : h('p', { class: 'muted' }, 'No rule scores recorded.')),
          panel('Outcomes in the log', null, kv(Object.entries(stats.byOutcome).filter(([, n]) => n > 0)
            .map(([k, n]) => [OUTCOME_TEXT[k] || capitalize(k), fmtNum(n)])))));
    }

    const metrics = ev.metrics || [];
    parts.push(panel('Offline evaluation', ev.scope, ev.available
      ? h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
        notice('warn', h('strong', {}, 'Not live performance. '), 'These figures come from ', h('span', { class: 'mono' }, ev.source), ', updated ' + fmtDateTime(ev.lastModifiedMs) + '. They describe a held-out test sample, not the events above.'),
        ev.description ? h('p', { class: 'muted' }, ev.description) : null,
        h('div', { class: 'table-wrap' }, h('table', {}, h('thead', {}, h('tr', {}, h('th', {}, 'Item'), h('th', {}, 'Reported value'))),
          h('tbody', {}, metrics.map((m) => h('tr', {}, h('td', {}, m.label), h('td', {}, m.value)))))),
        ev.notReported.length ? h('p', { class: 'muted' }, 'Not reported in the file, so not shown: ' + ev.notReported.join(', ') + '.') : null)
      : notice('warn', ev.detail)));

    mount(el, parts);
  }

  mount(el, pageHead('ML analytics'), loading('Loading model information'));
  const refresh = debounce(load, 2000);
  const unsubscribe = subscribe((s) => { if (s.latestSeq !== seq) { seq = s.latestSeq; refresh(); } });
  load();
  return { el, destroy() { alive = false; unsubscribe(); } };
}
