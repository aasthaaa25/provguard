import { h, icon, mount, clear } from './dom.js';
import { store, subscribe, start } from './store.js';
import { fmtTime } from './format.js';
import { errorPanel, loading } from './ui.js';

export const FRONTEND_VERSION = '1.0.0';

const ROUTES = [
  { id: 'overview', label: 'Overview', icon: 'grid', load: () => import('./overview.js') },
  { id: 'live', label: 'Live monitor', icon: 'pulse', load: () => import('./live-monitor.js') },
  { id: 'path', label: 'Call-path explorer', icon: 'route', load: () => import('./call-path.js') },
  { id: 'event', label: 'Event details', icon: 'doc', load: () => import('./event-details.js') },
  { id: 'analytics', label: 'ML analytics', icon: 'bars', load: () => import('./analytics.js') },
  { id: 'policies', label: 'Policies', icon: 'shield', load: () => import('./policies.js') },
  { id: 'audit', label: 'Audit logs', icon: 'list', load: () => import('./audit-logs.js') },
  { id: 'settings', label: 'Settings and health', icon: 'sliders', load: () => import('./settings.js') },
];

const viewEl = document.getElementById('view');
const navEl = document.getElementById('nav');
const chipsEl = document.getElementById('status-chips');
const titleEl = document.getElementById('topbar-title');
const bannerEl = document.getElementById('demo-banner');
const sidebar = document.getElementById('sidebar');
const scrim = document.getElementById('scrim');
const menuBtn = document.getElementById('menu-btn');

let current = null;   // { destroy }
let renderToken = 0;

export function navigate(hash) {
  if (location.hash === hash) route();
  else location.hash = hash;
}

function parseHash() {
  const parts = location.hash.replace(/^#\/?/, '').split('/').filter(Boolean).map(decodeURIComponent);
  const id = parts[0] || 'overview';
  return { route: ROUTES.find((r) => r.id === id) || ROUTES[0], params: parts.slice(1) };
}

async function route() {
  const { route: r, params } = parseHash();
  const token = ++renderToken;
  if (current && current.destroy) { try { current.destroy(); } catch (e) { console.error(e); } }
  current = null;
  titleEl.textContent = r.label;
  document.title = (store.health?.backend?.demoMode ? '[DEMO] ' : '') + r.label + ' · ProvGuard';
  for (const a of navEl.querySelectorAll('a')) {
    if (a.dataset.route === r.id) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  }
  closeMenu();
  mount(viewEl, loading('Loading ' + r.label.toLowerCase()));
  try {
    const mod = await r.load();
    if (token !== renderToken) return;
    const page = mod.render({ params, navigate });
    if (token !== renderToken) { if (page.destroy) page.destroy(); return; }
    current = page;
    mount(viewEl, page.el);
    const heading = viewEl.querySelector('#page-title');
    (heading || viewEl).focus({ preventScroll: true });
    window.scrollTo(0, 0);
  } catch (error) {
    console.error(error);
    mount(viewEl, errorPanel('This page failed to render: ' + (error && error.message ? error.message : 'unknown error'), route));
  }
}

function buildNav() {
  clear(navEl);
  for (const r of ROUTES) {
    navEl.appendChild(h('a', { href: '#/' + r.id, 'data-route': r.id }, icon(r.icon), r.label));
  }
  mount(document.getElementById('sidebar-foot'),
    h('div', {}, 'Dashboard ' + FRONTEND_VERSION),
    h('div', {}, 'Read-only. It cannot change the agent.'));
}

function chip(dotClass, label, value, title) {
  return h('li', { class: 'chip', title: title || null }, h('span', { class: 'dot ' + dotClass, 'aria-hidden': 'true' }), label + ' ', h('strong', {}, value));
}

const AGENT_TEXT = {
  recent_activity: ['ok', 'recent log activity'],
  idle: ['warn', 'log quiet'],
  no_events: ['warn', 'no events yet'],
  no_log: ['warn', 'no log file'],
};

function renderStatus() {
  const hd = store.health;
  const items = [];
  if (!hd) {
    items.push(chip('bad', 'Backend', 'unreachable', store.healthError || ''));
  } else {
    items.push(chip('ok', 'Backend', 'up', 'This dashboard service is responding. It says nothing about the agent.'));
    const log = hd.log;
    const logDot = log.state === 'ok' ? 'ok' : 'warn';
    items.push(chip(logDot, 'Log', log.state === 'ok' ? 'readable' : log.state, 'Event log file state'));
    if (hd.backend.demoMode) {
      items.push(chip('warn', 'Agent', 'none, demo data', hd.agent.basis));
    } else {
      const [adot, atext] = AGENT_TEXT[hd.agent.status] || ['warn', hd.agent.status];
      items.push(chip(adot, 'Agent', atext + ' (inferred)', hd.agent.basis));
    }
    items.push(chip('info', 'Mode', hd.agent.mode ? hd.agent.mode.toLowerCase() + ' (last recorded)' : 'not recorded', hd.agent.modeSource));
  }
  const c = store.conn;
  const live = c.state === 'live';
  const label = c.mode === 'sse' ? 'Live stream' : 'Polling';
  items.push(chip(live ? 'ok' : c.state === 'connecting' ? 'info' : 'warn', label,
    live ? 'connected' : c.state === 'reconnecting' ? 'reconnecting' : c.state === 'connecting' ? 'connecting' : 'error', c.detail || ''));
  items.push(chip('', 'Updated', store.lastUpdate ? fmtTime(store.lastUpdate) : 'never'));
  mount(chipsEl, items);
  const demo = !!(hd && hd.backend.demoMode);
  bannerEl.hidden = !demo;
  if (demo) bannerEl.textContent = 'DEMO DATA — NOT LIVE TELEMETRY';
  const bare = document.title.replace(/^\[DEMO\] /, '');
  document.title = demo ? '[DEMO] ' + bare : bare;
}

function openMenu() {
  sidebar.classList.add('open'); scrim.hidden = false; menuBtn.setAttribute('aria-expanded', 'true');
}
function closeMenu() {
  sidebar.classList.remove('open'); scrim.hidden = true; menuBtn.setAttribute('aria-expanded', 'false');
}
menuBtn.addEventListener('click', () => (sidebar.classList.contains('open') ? closeMenu() : openMenu()));
scrim.addEventListener('click', closeMenu);
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeMenu(); });

buildNav();
subscribe(renderStatus);
renderStatus();
window.addEventListener('hashchange', route);
start();
route();
