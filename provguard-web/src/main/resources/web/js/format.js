import { h } from './dom.js';

export const NOT_RECORDED = 'Not recorded';
export const UNAVAILABLE = 'Unavailable';

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
const dateTimeFmt = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
const numFmt = new Intl.NumberFormat();

export function fmtNum(n) {
  return typeof n === 'number' && Number.isFinite(n) ? numFmt.format(n) : UNAVAILABLE;
}
export function fmtTime(ts) {
  return Number.isFinite(ts) ? timeFmt.format(new Date(ts)) : UNAVAILABLE;
}
export function fmtDateTime(ts) {
  return Number.isFinite(ts) ? dateTimeFmt.format(new Date(ts)) : UNAVAILABLE;
}
export function fmtUtc(ts) {
  return Number.isFinite(ts) ? new Date(ts).toISOString() : UNAVAILABLE;
}
export function fmtBytes(n) {
  if (!Number.isFinite(n)) return UNAVAILABLE;
  if (n < 1024) return n + ' B';
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
  if (n < 1073741824) return (n / 1048576).toFixed(1) + ' MB';
  return (n / 1073741824).toFixed(2) + ' GB';
}
export function fmtAge(seconds) {
  if (!Number.isFinite(seconds)) return UNAVAILABLE;
  if (seconds < 5) return 'just now';
  if (seconds < 60) return Math.round(seconds) + ' s ago';
  if (seconds < 3600) return Math.round(seconds / 60) + ' min ago';
  if (seconds < 86400) return (seconds / 3600).toFixed(1) + ' h ago';
  return (seconds / 86400).toFixed(1) + ' d ago';
}
export function fmtUptime(seconds) {
  if (!Number.isFinite(seconds)) return UNAVAILABLE;
  const s = Math.floor(seconds);
  const parts = [];
  if (s >= 3600) parts.push(Math.floor(s / 3600) + ' h');
  if (s >= 60) parts.push(Math.floor((s % 3600) / 60) + ' min');
  parts.push((s % 60) + ' s');
  return parts.join(' ');
}

/** Renders a value, or a muted "Not recorded" marker when the log did not contain it. */
export function val(v, fallback = NOT_RECORDED) {
  if (v === null || v === undefined || v === '') return h('span', { class: 'na' }, fallback);
  return String(v);
}

export function badge(kind, text, title) {
  return h('span', { class: 'badge ' + kind, title: title || null }, text);
}

export function capitalize(text) {
  const lower = String(text).toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export function severityBadge(level) {
  if (!level) return h('span', { class: 'na' }, UNAVAILABLE);
  const kinds = { LOW: 'green', MEDIUM: 'amber', HIGH: 'red', CRITICAL: 'solid-red' };
  return badge(kinds[level] || 'muted', capitalize(level));
}

export function decisionBadge(decision) {
  if (!decision) return h('span', { class: 'na' }, UNAVAILABLE);
  const kinds = { ALLOW: 'green', ALERT: 'amber', BLOCK: 'red' };
  return badge(kinds[decision] || 'muted', capitalize(decision));
}

export const OUTCOME_TEXT = {
  blocked: 'Blocked',
  policy_block_not_enforced: 'Would block, not enforced',
  flagged: 'Flagged',
  allowed: 'Allowed',
  unknown: 'Unknown',
};

export function outcomeBadge(outcome) {
  const text = OUTCOME_TEXT[outcome] || OUTCOME_TEXT.unknown;
  const kinds = { blocked: 'solid-red', policy_block_not_enforced: 'outline-amber', flagged: 'amber', allowed: 'green', unknown: 'muted' };
  const titles = {
    blocked: 'The agent recorded that it stopped this call (Block mode).',
    policy_block_not_enforced: 'The blocking policy matched, but the active mode does not stop calls. The call ran.',
    flagged: 'An alert was recorded. The call was not stopped.',
    allowed: 'No alert was recorded.',
  };
  return badge(kinds[outcome] || 'muted', text, titles[outcome]);
}

export function sinkLabel(type) {
  const labels = {
    RUNTIME_EXEC: 'Runtime.exec',
    PROCESS_BUILDER_START: 'ProcessBuilder.start',
    DESERIALIZATION_RESOLVE_CLASS: 'ObjectInputStream.resolveClass',
    JNDI_LOOKUP: 'JNDI lookup',
    SCRIPT_ENGINE_EVAL: 'ScriptEngine.eval',
  };
  return labels[type] || type || UNAVAILABLE;
}

export function shortId(id) {
  return typeof id === 'string' ? (id.length > 13 ? id.slice(0, 8) + '…' : id) : UNAVAILABLE;
}

export function debounce(fn, ms) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}
