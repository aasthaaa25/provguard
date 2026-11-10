import { h, mount, icon } from './dom.js';
import { api } from './api.js';
import { store, subscribe } from './store.js';
import { fmtDateTime, fmtUtc, sinkLabel, severityBadge, decisionBadge, badge, val, NOT_RECORDED } from './format.js';
import { pageHead, panel, loading, errorPanel, notice, copyText, eventPicker } from './ui.js';

const OUTCOME = {
  blocked: ['blocked', 'Blocked', 'The agent recorded enforced = true. The call was stopped on the application thread before the monitored method ran.'],
  policy_block_not_enforced: ['notenforced', 'Flagged. The policy would block this, but the call was not stopped.',
    'The blocking policy matched, but the agent was not in Block mode, so the call went ahead. Only Block mode stops calls.'],
  flagged: ['flagged', 'Flagged. The call was not stopped.', 'An alert was recorded for this event. Alerts never stop a call.'],
  allowed: ['allowed', 'Allowed', 'No alert was recorded for this event.'],
  unknown: ['unknown', 'Outcome unknown', 'The record does not contain enough decision fields to say what happened.'],
};

function kv(rows) {
  return h('dl', { class: 'kv kv-tight' }, rows.flatMap(([k, v]) => [h('dt', {}, k), h('dd', {}, v)]));
}

function yesNo(v) {
  return v === null || v === undefined ? val(null) : v ? 'Yes' : 'No';
}

export function render({ params }) {
  const id = params[0];
  if (!id) return eventPicker('Event details', 'Choose an event to see every field the agent recorded for it.', '#/event/', store, subscribe);
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  let alive = true;

  async function load() {
    mount(el, pageHead('Event details'), loading('Loading event'));
    try {
      const [d, policies] = await Promise.all([api.event(id), api.policies().catch(() => null)]);
      if (alive) draw(d, policies);
    } catch (error) {
      if (!alive) return;
      const missing = error.status === 404;
      mount(el, pageHead('Event details'), errorPanel(
        missing ? 'This event is not in the current index. It may belong to an earlier log, or the log was reset.' : error.message, missing ? null : load),
        h('a', { href: '#/event' }, 'Choose another event'));
    }
  }

  function draw(d, policies) {
    const info = {};
    if (policies) for (const r of policies.rules) info[r.id] = r;
    let [kind, title, text] = OUTCOME[d.outcome] || OUTCOME.unknown;
    if (d.outcome === 'policy_block_not_enforced') {
      text = 'The blocking policy matched, but this event was recorded in ' + (d.mode ? d.mode.toLowerCase() + ' mode' : 'a mode not stated in the log')
        + ', and the record says the call was not stopped. Only Block mode stops calls.';
    }
    const copyBtn = h('button', { class: 'btn small', type: 'button', onclick: async (ev) => {
      const ok = await copyText(d.id);
      ev.currentTarget.replaceChildren(icon('copy'), ok ? 'Copied' : 'Copy failed');
    } }, icon('copy'), 'Copy ID');

    const appFrames = d.path.frames.filter((f) => f.category === 'application');
    const condensed = appFrames.length
      ? appFrames.map((f) => f.className.split('.').pop() + '.' + f.methodName).join(' → ') : null;

    const rulesBody = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
      h('div', { class: 'score-box' }, h('span', { class: 'big' }, d.rules.score ?? 'n/a'), h('span', { class: 'muted' }, '/ 100 from deterministic rules')),
      d.rules.matchedRules.length
        ? h('ul', { style: { margin: 0, paddingLeft: '18px' } }, d.rules.matchedRules.map((r) => h('li', {},
          h('strong', {}, info[r] ? info[r].name : r), info[r] ? h('span', { class: 'muted' }, ' (+' + info[r].scoreDelta + ') ' + info[r].description) : null,
          h('div', { class: 'mono faint' }, r))))
        : h('p', { class: 'muted' }, 'No rule matched.'),
      d.rules.explanation ? h('p', { class: 'muted' }, 'Agent explanation: ' + d.rules.explanation) : null);

    const mlBody = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '10px' } },
      h('div', { class: 'score-box' }, h('span', { class: 'big' }, d.ml.score ?? 'n/a'),
        h('span', { class: 'muted' }, d.ml.score !== null ? '/ 100 anomaly score' : '')),
      kv([['Model status', val(d.ml.status)], ['Meaning', 'A score relative to the benign training data. It is not a probability of attack.']]),
      d.ml.score === null ? notice('warn', 'No anomaly score was attached to this event. The rules decided on their own.') : null,
      d.ml.score !== null && d.ml.score >= 80 && (d.rules.score ?? 0) < 60
        ? notice('info', 'A high anomaly score on its own raises an alert. It never blocks without a high rule score.') : null);

    const decisionBody = kv([
      ['Recorded decision', decisionBadge(d.decision.recorded)],
      ['Policy decision', h('span', {}, decisionBadge(d.decision.policy), h('span', { class: 'muted' }, ' what the blocking policy concluded before the mode was applied'))],
      ['Mode', val(d.mode)],
      ['Enforced', d.decision.enforced === null ? val(null) : d.decision.enforced ? badge('solid-red', 'Yes, the call was stopped') : 'No, the call was not stopped'],
      ['Combined score', d.combined.score === null ? val(null) : d.combined.score + ' (rules, adjusted by the ML score)'],
      ['Severity', severityBadge(d.combined.riskLevel)],
    ]);

    const raw = JSON.stringify(d, null, 2);
    mount(el,
      pageHead('Event details', null,
        copyBtn,
        h('a', { class: 'btn primary', href: '#/path/' + encodeURIComponent(d.id) }, 'Open call path'),
        h('a', { class: 'btn', href: '#/event' }, 'Choose another event')),
      h('div', { class: 'outcome ' + kind, role: 'status' }, h('div', {}, h('h2', {}, title), h('p', {}, text))),
      h('div', { class: 'grid-2 even' },
        panel('Event', null, kv([
          ['Event ID', h('span', { class: 'mono' }, d.id)],
          ['ID source', d.derivedId ? 'Derived by the dashboard (the record had no usable eventId)' : 'Written by the agent'],
          ['Time (local)', fmtDateTime(d.ts)],
          ['Time (UTC)', h('span', { class: 'mono' }, d.timestamp || fmtUtc(d.ts))],
          ['Thread', d.thread.name ? d.thread.name + (d.thread.id !== null ? ' (id ' + d.thread.id + ')' : '') : val(null)],
          ['Application or process', h('span', { class: 'na' }, NOT_RECORDED + ': the agent does not log a process id, application name, or user')],
          ['Queue overflow', yesNo(d.queueDropped)],
          ['Log schema version', val(d.schemaVersion)],
        ])),
        panel('Sink and argument', null, kv([
          ['Monitored API', sinkLabel(d.sink.type)],
          ['Method', val(d.sink.method)],
          ['Requested class', d.sink.type === 'DESERIALIZATION_RESOLVE_CLASS' ? val(d.sink.requestedClassName) : h('span', { class: 'na' }, 'Not applicable')],
          ['Argument type', val(d.argument.type)],
          ['Argument length', val(d.argument.length)],
          ['SHA-256', h('span', { class: 'mono' }, val(d.argument.sha256))],
          ['Preview', h('span', {}, h('span', { class: 'mono' }, val(d.argument.redactedPreview, 'Empty')), h('div', { class: 'faint' }, 'Redacted and cut to 20 characters by the agent. The full argument is never stored.'))],
          ['Risky keywords', val(d.argument.suspiciousKeywordCount)],
          ['Special characters', val(d.argument.specialCharacterCount)],
          ['URL-like / command-like', yesNo(d.argument.urlLike) + ' / ' + yesNo(d.argument.commandLike)],
        ]))),
      h('div', { class: 'grid-2 even' },
        panel('Rule-based assessment', 'Deterministic rules in the rules module', rulesBody),
        panel('ML anomaly score', 'Isolation Forest, scored separately from the rules', mlBody)),
      h('div', { class: 'grid-2 even' },
        panel('Decision and enforcement', null, decisionBody),
        panel('Call path summary', null, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
          kv([
            ['Recorded frames', d.path.frameCount + (d.path.stackDepth !== null ? ' (stack depth ' + d.path.stackDepth + ')' : '')],
            ['Application / JDK / library', [d.path.applicationFrameCount, d.path.jdkFrameCount, d.path.externalLibraryFrameCount].map((n) => n ?? '?').join(' / ')],
            ['Reflection in path', yesNo(d.path.reflectionDetected)],
            ['Path signature', h('span', { class: 'mono' }, val(d.path.signature))],
            ['Times seen recently', val(d.path.frequency)],
          ]),
          condensed ? h('p', { class: 'muted' }, 'Application frames, entry first: ', h('span', { class: 'mono' }, condensed)) : null,
          h('a', { href: '#/path/' + encodeURIComponent(d.id) }, 'Open the full call path')))),
      panel('Record as served by the API', null, h('details', {}, h('summary', {}, 'Show JSON'),
        h('p', { class: 'muted' }, 'Only known fields are passed through. The original log line is unchanged.'),
        h('pre', { class: 'raw' }, raw))));
  }

  load();
  return { el, destroy() { alive = false; } };
}
