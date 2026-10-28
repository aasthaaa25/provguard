import { h, icon, clear } from './dom.js';
import { debounce, sinkLabel, capitalize, OUTCOME_TEXT } from './format.js';

export const SINKS = ['RUNTIME_EXEC', 'PROCESS_BUILDER_START', 'DESERIALIZATION_RESOLVE_CLASS'];
export const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const DECISIONS = ['ALLOW', 'ALERT', 'BLOCK'];
export const OUTCOMES = ['allowed', 'flagged', 'policy_block_not_enforced', 'blocked'];

export function emptyFilters() {
  return { q: '', sink: '', severity: [], decision: '', outcome: '', from: '', to: '' };
}

export function hasFilters(f) {
  return !!(f.q || f.sink || f.severity.length || f.decision || f.outcome || f.from || f.to);
}

/** Query parameters understood by /api/events, /api/stats and /api/export. */
export function toParams(f) {
  return {
    q: f.q || undefined,
    sink: f.sink || undefined,
    severity: f.severity.length ? f.severity : undefined,
    decision: f.decision || undefined,
    outcome: f.outcome || undefined,
    from: f.from ? new Date(f.from).toISOString() : undefined,
    to: f.to ? new Date(f.to).toISOString() : undefined,
  };
}

/** Same matching rules as the backend, for the Live Monitor's in-memory list. */
export function matches(e, f) {
  if (f.sink && e.sinkType !== f.sink) return false;
  if (f.severity.length && !f.severity.includes(e.riskLevel)) return false;
  if (f.decision && e.decision !== f.decision) return false;
  if (f.outcome && e.outcome !== f.outcome) return false;
  if (f.from && e.ts < new Date(f.from).getTime()) return false;
  if (f.to && e.ts > new Date(f.to).getTime()) return false;
  if (f.q) {
    const q = f.q.toLowerCase();
    const hay = [e.sinkType, e.sinkMethod, e.entry, e.pathSignature, e.threadName, e.id, ...(e.matchedRules || [])];
    if (!hay.some((s) => s && String(s).toLowerCase().includes(q))) return false;
  }
  return true;
}

/**
 * Builds the filter controls and the visible chips. `state` is mutated in place, then `onChange()` runs.
 * Returns { controls, chips, refresh }.
 */
export function filterBar(state, onChange, { dates = false, searchHint = 'Search sink, class, method, rule, thread, or ID' } = {}) {
  const changed = () => { renderChips(); onChange(); };

  const search = h('input', {
    class: 'input', type: 'search', id: 'f-q', placeholder: searchHint, autocomplete: 'off', spellcheck: 'false',
    'aria-label': 'Search events', value: state.q,
    oninput: debounce((ev) => { state.q = ev.target.value.trim(); changed(); }, 200),
  });

  const sinkSelect = h('select', { class: 'input', id: 'f-sink', onchange: (ev) => { state.sink = ev.target.value; changed(); } },
    h('option', { value: '' }, 'All sinks'),
    SINKS.map((s) => h('option', { value: s }, sinkLabel(s))));
  sinkSelect.value = state.sink;

  const outcomeSelect = h('select', { class: 'input', id: 'f-outcome', onchange: (ev) => { state.outcome = ev.target.value; changed(); } },
    h('option', { value: '' }, 'Any outcome'),
    OUTCOMES.map((o) => h('option', { value: o }, OUTCOME_TEXT[o])));
  outcomeSelect.value = state.outcome;

  const sevButtons = SEVERITIES.map((s) => h('button', {
    type: 'button', 'aria-pressed': String(state.severity.includes(s)),
    onclick: (ev) => {
      const i = state.severity.indexOf(s);
      if (i >= 0) state.severity.splice(i, 1); else state.severity.push(s);
      ev.currentTarget.setAttribute('aria-pressed', String(i < 0));
      changed();
    },
  }, capitalize(s)));
  const severity = h('div', { role: 'group', 'aria-label': 'Severity' }, h('div', { class: 'seg' }, sevButtons));

  const decButtons = [['', 'Any'], ...DECISIONS.map((d) => [d, capitalize(d)])].map(([value, label]) => h('button', {
    type: 'button', 'aria-pressed': String(state.decision === value), dataset: { v: value },
    onclick: () => {
      state.decision = value;
      decButtons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.v === value)));
      changed();
    },
  }, label));
  const decision = h('div', { role: 'group', 'aria-label': 'Decision' }, h('div', { class: 'seg' }, decButtons));

  const field = (label, control) => h('label', { class: 'field' }, label, control);
  const groupField = (label, control) => h('div', { class: 'field' }, h('span', {}, label), control);

  const dateInputs = dates ? [
    field('From', h('input', { class: 'input', type: 'datetime-local', value: state.from, onchange: (ev) => { state.from = ev.target.value; changed(); } })),
    field('To', h('input', { class: 'input', type: 'datetime-local', value: state.to, onchange: (ev) => { state.to = ev.target.value; changed(); } })),
  ] : [];

  const controls = h('div', { class: 'toolbar' },
    h('div', { class: 'search-field' }, icon('search'), search),
    field('Sink', sinkSelect),
    groupField('Severity', severity),
    groupField('Decision', decision),
    field('Outcome', outcomeSelect),
    dateInputs);

  const chips = h('div', { class: 'filter-chips', 'aria-label': 'Active filters', hidden: true });

  function chip(label, remove) {
    return h('span', { class: 'filter-chip' }, label,
      h('button', { type: 'button', 'aria-label': 'Remove filter ' + label, onclick: () => { remove(); syncControls(); changed(); } }, icon('x')));
  }

  function syncControls() {
    search.value = state.q;
    sinkSelect.value = state.sink;
    outcomeSelect.value = state.outcome;
    sevButtons.forEach((b, i) => b.setAttribute('aria-pressed', String(state.severity.includes(SEVERITIES[i]))));
    decButtons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.v === state.decision)));
  }

  function renderChips() {
    clear(chips);
    const items = [];
    if (state.q) items.push(chip('Search: ' + state.q, () => { state.q = ''; }));
    if (state.sink) items.push(chip('Sink: ' + sinkLabel(state.sink), () => { state.sink = ''; }));
    state.severity.forEach((s) => items.push(chip('Severity: ' + capitalize(s), () => { state.severity.splice(state.severity.indexOf(s), 1); })));
    if (state.decision) items.push(chip('Decision: ' + capitalize(state.decision), () => { state.decision = ''; }));
    if (state.outcome) items.push(chip('Outcome: ' + OUTCOME_TEXT[state.outcome], () => { state.outcome = ''; }));
    if (state.from) items.push(chip('From ' + state.from.replace('T', ' '), () => { state.from = ''; }));
    if (state.to) items.push(chip('To ' + state.to.replace('T', ' '), () => { state.to = ''; }));
    chips.hidden = items.length === 0;
    if (items.length) {
      chips.appendChild(h('span', { class: 'muted' }, 'Filters:'));
      items.forEach((i) => chips.appendChild(i));
      chips.appendChild(h('button', { class: 'btn small ghost', type: 'button', onclick: () => {
        Object.assign(state, emptyFilters());
        syncControls();
        changed();
      } }, 'Clear filters'));
    }
  }
  renderChips();
  return { controls, chips };
}
