import { h, mount, icon } from './dom.js';
import { api } from './api.js';
import { fmtNum, sinkLabel, badge, val, capitalize } from './format.js';
import { pageHead, panel, loading, errorPanel, notice } from './ui.js';

const DECISION_KIND = { ALLOW: 'green', ALERT: 'amber', BLOCK: 'red' };

export function render() {
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  let alive = true;

  async function load() {
    mount(el, pageHead('Policies'), loading('Loading policies'));
    try {
      const p = await api.policies();
      if (alive) draw(p);
    } catch (error) {
      if (alive) mount(el, pageHead('Policies'), errorPanel(error.message, load));
    }
  }

  function draw(p) {
    let query = '';
    const rulesBody = h('tbody');
    const count = h('span', { class: 'muted' });

    function drawRules() {
      const q = query.toLowerCase();
      const rows = p.rules.filter((r) => !q || r.id.toLowerCase().includes(q) || r.name.toLowerCase().includes(q) || r.description.toLowerCase().includes(q));
      count.textContent = rows.length + ' of ' + p.rules.length + ' rules';
      mount(rulesBody, rows.length ? rows.map((r) => h('tr', {},
        h('td', {}, h('div', {}, h('strong', {}, r.name)), h('div', { class: 'mono faint' }, r.id)),
        h('td', {}, r.description),
        h('td', {}, r.sinks.map((s) => h('div', {}, sinkLabel(s)))),
        h('td', { class: 'num' }, r.scoreDelta === 0 ? '0 (info)' : '+' + r.scoreDelta),
        h('td', { class: 'num' }, fmtNum(r.observedMatches)),
        h('td', {}, r.definedInRulesModule ? badge('green', 'In rules module') : badge('amber', 'Not found in rules module', 'This catalog entry is out of date with provguard-rules.'))))
        : h('tr', {}, h('td', { colspan: '6', class: 'muted' }, 'No rule matches that search.')));
    }

    const t = p.thresholds;
    const matrixCell = (d) => badge(DECISION_KIND[d] || 'muted', capitalize(d));

    mount(el,
      pageHead('Policies', 'The rules and blocking policy that ship in the rules module.', h('button', { class: 'btn', type: 'button', onclick: load }, icon('refresh'), 'Refresh')),
      notice('info', h('strong', {}, 'Read-only. '), p.readOnlyNote),
      h('div', { class: 'grid-2 even' },
        panel('Blocking policy', 'How a rule score and an ML score become a decision', h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
          h('div', { class: 'table-wrap' }, h('table', {},
            h('thead', {}, h('tr', {}, h('th', {}, 'Rule score'), h('th', {}, 'ML score 80 or more'), h('th', {}, 'ML score below 80'), h('th', {}, 'No ML score'))),
            h('tbody', {}, p.decisionMatrix.map((r) => h('tr', {},
              h('td', {}, r.ruleScoreFrom + ' to ' + r.ruleScoreTo), h('td', {}, matrixCell(r.mlHigh)), h('td', {}, matrixCell(r.mlLow)), h('td', {}, matrixCell(r.mlUnavailable))))))),
          h('p', { class: 'muted' }, 'This table is produced by calling the real BlockingPolicy class. A “Block” result stops a call only when the agent runs in Block mode.'),
          h('p', { class: 'muted' }, 'Thresholds in code: critical ' + t.criticalRuleScore + ', high ' + t.highRuleScore + ', medium ' + t.mediumRuleScore + ', high ML score ' + t.highMlScore + '.'))),
        panel('Agent modes', 'Set with the mode= agent argument at JVM start', h('div', { style: { display: 'flex', flexDirection: 'column', gap: '14px' } },
          p.modes.map((m) => h('div', {},
            h('div', { class: 'actions' }, h('strong', {}, capitalize(m.mode)), m.stopsTheCall ? badge('red', 'Can stop calls') : badge('muted', 'Never stops calls')),
            h('p', { class: 'muted' }, m.description)))))),
      panel('Risk levels', 'Mapped from the combined score', h('div', { class: 'pill-list' },
        p.riskLevels.map((r) => badge(r.level === 'LOW' ? 'green' : r.level === 'MEDIUM' ? 'amber' : r.level === 'HIGH' ? 'red' : 'solid-red', capitalize(r.level) + ' from ' + r.minScore)))),
      panel('Rules', 'Each matched rule adds to the rule score. None blocks by itself.', h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
        h('div', { class: 'actions' },
          h('label', { class: 'field' }, 'Filter rules', h('input', { class: 'input', type: 'search', placeholder: 'Name, id, or description', oninput: (ev) => { query = ev.target.value.trim(); drawRules(); } })), count),
        h('div', { class: 'table-wrap' }, h('table', {},
          h('thead', {}, h('tr', {}, h('th', {}, 'Rule'), h('th', {}, 'What it checks'), h('th', {}, 'Applies to'), h('th', { class: 'num' }, 'Score added'), h('th', { class: 'num', title: 'Events in the loaded log where this rule matched' }, 'Matched in log'), h('th', {}, 'Source check'))),
          rulesBody)),
        h('p', { class: 'muted' }, 'Enabled state: all rules are always active; the rules module has no on/off switch. Score values are documented from the source and verified by tests in the web module.'))),
      h('div', { class: 'grid-2 even' },
        panel('Monitored APIs', null, h('div', { class: 'table-wrap' }, h('table', {},
          h('thead', {}, h('tr', {}, h('th', {}, 'API'), h('th', {}, 'Status'))),
          h('tbody', {}, p.sinks.map((s) => h('tr', {}, h('td', {}, sinkLabel(s.type)),
            h('td', {}, s.monitored ? badge('green', 'Monitored') : badge('muted', 'Defined, not monitored in this version'))))))),),
        panel('Agent arguments', null, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '10px' } },
          h('div', { class: 'table-wrap' }, h('table', {},
            h('thead', {}, h('tr', {}, h('th', {}, 'Argument'), h('th', {}, 'Default'), h('th', {}, 'Purpose'))),
            h('tbody', {}, p.agentArguments.map((a) => h('tr', {}, h('td', { class: 'mono' }, a.name), h('td', { class: 'mono' }, a.default), h('td', {}, a.description)))))),
          h('p', { class: 'muted' }, p.agentArgumentsNote)))));
    drawRules();
  }

  load();
  return { el, destroy() { alive = false; } };
}
