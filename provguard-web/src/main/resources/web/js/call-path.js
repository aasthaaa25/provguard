import { h, mount, icon, clear } from './dom.js';
import { api } from './api.js';
import { store, subscribe } from './store.js';
import { fmtDateTime, fmtUtc, sinkLabel, severityBadge, decisionBadge, outcomeBadge, badge, val, NOT_RECORDED } from './format.js';
import { pageHead, panel, loading, errorPanel, notice, eventsTable, noEventsState } from './ui.js';

const CATEGORY = {
  application: ['cyan', 'Application'],
  platform: ['muted', 'JDK'],
  external: ['amber', 'Library'],
};

function simpleName(cls) {
  const i = cls.lastIndexOf('.');
  return i < 0 ? cls : cls.slice(i + 1);
}

function location_(f) {
  if (!f.fileName) return null;
  return f.lineNumber !== null && f.lineNumber !== undefined ? f.fileName + ':' + f.lineNumber : f.fileName;
}

/** Facts only. Every sentence here comes from a field in the record. */
export function observedFacts(d, rulesInfo) {
  const frames = d.path.frames;
  const count = (c) => frames.filter((f) => f.category === c).length;
  const facts = [];
  facts.push('At ' + fmtDateTime(d.ts) + ' (' + fmtUtc(d.ts) + ') the agent intercepted a call to ' + sinkLabel(d.sink.type)
    + (d.thread.name ? ' on thread “' + d.thread.name + '”.' : '.'));
  facts.push(d.mode ? 'The agent was in ' + d.mode.toLowerCase() + ' mode for this event.' : 'The mode this event was recorded under is not in the log.');
  facts.push('The recorded stack has ' + frames.length + ' frame' + (frames.length === 1 ? '' : 's') + ': '
    + count('application') + ' application, ' + count('platform') + ' JDK, ' + count('external') + ' library.');
  if (frames.length >= d.path.agentFrameLimit) {
    facts.push('The agent keeps at most ' + d.path.agentFrameLimit + ' frames, so the true entry point may sit above the first frame shown.');
  }
  if (d.sink.requestedClassName) facts.push('The class requested during deserialization was ' + d.sink.requestedClassName + '.');
  if (d.argument.length !== null) {
    facts.push('The argument was a ' + (d.argument.type || 'value of unknown type') + ' of length ' + d.argument.length
      + '. The log keeps a SHA-256 hash and a short redacted preview, not the argument itself.');
  }
  const matched = d.rules.matchedRules;
  facts.push(matched.length
    ? 'Rule score ' + d.rules.score + '. Matched: ' + matched.map((id) => (rulesInfo[id] ? rulesInfo[id].name : id)).join(', ') + '.'
    : 'Rule score ' + (d.rules.score ?? 'not recorded') + '. No rule matched.');
  facts.push(d.ml.score !== null
    ? 'Anomaly score ' + d.ml.score + ' of 100 from the Isolation Forest. It is a score relative to training data, not a probability.'
    : 'No anomaly score was attached (model status: ' + (d.ml.status || NOT_RECORDED.toLowerCase()) + ').');
  const outcomeText = {
    blocked: 'The agent recorded that it stopped this call.',
    policy_block_not_enforced: 'The blocking policy matched, but the agent was not in Block mode, so the call was not stopped.',
    flagged: 'An alert was recorded. The call was not stopped.',
    allowed: 'No alert was recorded.',
  }[d.outcome] || 'The outcome could not be determined from the record.';
  facts.push(outcomeText);
  return facts;
}

function pickerView(ctx) {
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  const host = h('div');
  function draw() {
    if (!store.events.length) { mount(host, noEventsState(store.health)); return; }
    mount(host, panel('Choose an event', 'Select an event to see the call path the agent recorded for it.',
      eventsTable(store.events.slice(0, 100), { onSelect: (e) => { location.hash = '#/path/' + encodeURIComponent(e.id); } })));
  }
  mount(el, pageHead('Call-path explorer', 'The Java call stack the agent captured when a monitored API was called, shown in execution order.'), host);
  draw();
  const unsub = subscribe(draw);
  return { el, destroy: unsub };
}

export function render(ctx) {
  const id = ctx.params[0];
  if (!id) return pickerView(ctx);
  const el = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } });
  let alive = true;

  async function load() {
    mount(el, pageHead('Call-path explorer'), loading('Loading event'));
    try {
      const [detail, policies] = await Promise.all([api.event(id), api.policies().catch(() => null)]);
      if (!alive) return;
      const info = {};
      if (policies) for (const r of policies.rules) info[r.id] = r;
      draw(detail, info);
    } catch (error) {
      if (!alive) return;
      const missing = error.status === 404;
      mount(el, pageHead('Call-path explorer'), errorPanel(
        missing ? 'This event is not in the current index. It may belong to an earlier log, or the log was reset.' : error.message,
        missing ? null : load),
        h('a', { href: '#/path' }, 'Choose another event'));
    }
  }

  function draw(d, info) {
    const frames = d.path.frames;
    let selected = Math.max(0, frames.findIndex((f) => f.role === 'sink'));
    if (!frames.length) selected = -1;
    let hideJdk = frames.length > 25;

    const railHost = h('div');
    const inspector = h('div');

    function visibleFrames() {
      const out = [];
      let hidden = 0;
      frames.forEach((f, i) => {
        const keep = !hideJdk || f.category !== 'platform' || f.role === 'sink' || f.role === 'entry' || f.flags.length || i === selected;
        if (keep) {
          if (hidden) { out.push({ gap: hidden, before: i }); hidden = 0; }
          out.push({ f, i });
        } else hidden++;
      });
      if (hidden) out.push({ gap: hidden, before: frames.length });
      return out;
    }

    function drawRail() {
      if (!frames.length) {
        mount(railHost, h('p', { class: 'muted' }, 'This record has no call frames.'));
        return;
      }
      const items = visibleFrames().map((item) => {
        if (item.gap) {
          return h('li', {}, h('div', { class: 'collapsed-note' }, h('button', { type: 'button', onclick: () => { hideJdk = false; toggleBox.checked = false; drawRail(); } },
            item.gap + ' JDK frame' + (item.gap === 1 ? '' : 's') + ' hidden. Show')));
        }
        const { f, i } = item;
        const [catKind, catText] = CATEGORY[f.category] || ['muted', f.category];
        const classes = ['node', 'cat-' + f.category, 'role-' + f.role, f.flags.length ? 'flagged' : ''];
        const btn = h('button', {
          type: 'button', class: classes, 'aria-current': String(i === selected), dataset: { index: i },
          onclick: () => { selected = i; drawRail(); drawInspector(); },
        },
          h('div', { class: 'n-top' },
            h('span', { class: 'muted' }, String(f.position)),
            h('span', { class: 'n-name' }, simpleName(f.className) + '.' + f.methodName),
            f.role === 'entry' ? badge('cyan', 'Entry', 'Outermost frame the agent recorded') : null,
            f.role === 'sink' ? badge('red', 'Monitored sink', 'The instrumented JDK method') : null,
            badge(catKind, catText),
            f.flags.map((fl) => badge('amber', 'Reflection', fl.reason))),
          h('div', { class: 'n-sub' }, f.className + '.' + f.methodName + (location_(f) ? ' (' + location_(f) + ')' : '')));
        return h('li', {}, btn);
      });
      const list = h('ol', { class: 'rail', 'aria-label': 'Call frames in execution order, entry first' }, items);
      list.addEventListener('keydown', (ev) => {
        if (ev.key !== 'ArrowDown' && ev.key !== 'ArrowUp') return;
        const nodes = [...list.querySelectorAll('button.node')];
        const at = nodes.indexOf(document.activeElement);
        const next = nodes[at + (ev.key === 'ArrowDown' ? 1 : -1)];
        if (next) { ev.preventDefault(); next.focus(); }
      });
      const hasSinkFrame = frames.some((f) => f.role === 'sink');
      mount(railHost, list, hasSinkFrame ? null : h('div', { class: 'sink-terminal' },
        h('strong', {}, 'Monitored sink: ' + sinkLabel(d.sink.type)),
        h('div', { class: 'muted' }, 'The instrumented frame is not the last frame in this record, so it is shown here instead of in the path.')));
      const current = railHost.querySelector('button.node[aria-current="true"]');
      if (current && document.activeElement && document.activeElement.classList && document.activeElement.classList.contains('node')) current.focus({ preventScroll: true });
    }

    function drawInspector() {
      const f = frames[selected];
      mount(inspector, panel('Selected frame', f ? 'Frame ' + f.position + ' of ' + frames.length : null, !f
        ? h('p', { class: 'muted' }, 'Select a frame in the path to inspect it.')
        : h('div', { style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
          h('dl', { class: 'kv kv-tight' },
            h('dt', {}, 'Class'), h('dd', { class: 'mono' }, f.className),
            h('dt', {}, 'Method'), h('dd', { class: 'mono' }, f.methodName),
            h('dt', {}, 'Source'), h('dd', { class: 'mono' }, val(location_(f))),
            h('dt', {}, 'Module'), h('dd', {}, val(f.moduleName)),
            h('dt', {}, 'Class loader'), h('dd', {}, val(f.classLoaderName)),
            h('dt', {}, 'Kind'), h('dd', {}, (CATEGORY[f.category] || [0, f.category])[1] + (f.role !== 'intermediate' ? ', ' + f.role : ''))),
          f.flags.length
            ? notice('warn', h('strong', {}, 'Flagged: '), f.flags.map((x) => x.reason).join('. '))
            : h('p', { class: 'muted' }, 'No flag on this frame. The agent scores the whole path, not individual frames, so most frames carry no flag.'))));
    }

    const header = h('div', { class: 'panel' }, h('div', { class: 'panel-body', style: { display: 'flex', flexWrap: 'wrap', gap: '10px 18px', alignItems: 'center' } },
      outcomeBadge(d.outcome), severityBadge(d.combined.riskLevel), decisionBadge(d.decision.recorded),
      h('span', {}, sinkLabel(d.sink.type)),
      h('span', { class: 'muted', title: fmtUtc(d.ts) }, fmtDateTime(d.ts)),
      h('span', { class: 'muted mono', title: d.id }, 'ID ' + d.id)));

    const toggleBox = h('input', { type: 'checkbox', checked: hideJdk, onchange: (ev) => { hideJdk = ev.target.checked; drawRail(); } });
    const toggle = h('label', { class: 'field', style: { flexDirection: 'row', alignItems: 'center', gap: '8px' } }, toggleBox, 'Collapse JDK internals');

    const assessment = panel('Assessment', 'Rules and the ML model are scored separately.', h('div', { style: { display: 'flex', flexDirection: 'column', gap: '14px' } },
      h('div', { class: 'grid-2 even' },
        h('div', {}, h('div', { class: 'muted' }, 'Rule score'), h('div', { class: 'score-box' }, h('span', { class: 'big' }, d.rules.score ?? 'n/a'), h('span', { class: 'muted' }, '/ 100'))),
        h('div', {}, h('div', { class: 'muted' }, 'Anomaly score'), h('div', { class: 'score-box' },
          h('span', { class: 'big' }, d.ml.score ?? 'n/a'), h('span', { class: 'muted' }, d.ml.score !== null ? '/ 100' : '(' + (d.ml.status || 'not scored') + ')')))),
      d.rules.matchedRules.length
        ? h('div', { class: 'pill-list' }, d.rules.matchedRules.map((r) => h('span', { class: 'badge muted', title: info[r] ? info[r].description : '' }, info[r] ? info[r].name : r)))
        : h('p', { class: 'muted' }, 'No rule matched.'),
      h('a', { href: '#/event/' + encodeURIComponent(d.id) }, 'Open full event details')));

    const observed = panel('What the agent observed', null, h('div', { class: 'observed' },
      h('ul', {}, observedFacts(d, info).map((t) => h('li', {}, t))),
      d.path.signature ? h('p', { class: 'muted' }, 'Path signature: ', h('span', { class: 'mono' }, d.path.signature)
        + (d.path.frequency ? ', seen ' + d.path.frequency + ' time(s) in the agent’s recent window' : '')) : null));

    mount(el,
      pageHead('Call-path explorer', 'The Java call stack captured when the monitored API was called, entry point first.',
        h('a', { class: 'btn', href: '#/path' }, 'Choose another event'),
        h('a', { class: 'btn', href: '#/live/' + encodeURIComponent(d.id) }, 'Back to live monitor')),
      header,
      notice('info', h('strong', {}, 'What this is: '), 'a stack trace taken at the moment of the call. It shows which methods were running, not how data moved between them. ProvGuard does not do taint tracking or data-flow analysis.'),
      h('div', { class: 'explorer' },
        panel('Execution path', frames.length + ' recorded frame' + (frames.length === 1 ? '' : 's'), railHost, toggle),
        h('div', { style: { display: 'flex', flexDirection: 'column', gap: '20px' } }, inspector, assessment, observed)));
    drawRail();
    drawInspector();
  }

  load();
  return { el, destroy() { alive = false; } };
}
