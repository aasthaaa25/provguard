import { h, svg } from './dom.js';
import { fmtNum, fmtTime, fmtDateTime } from './format.js';

function niceMax(max) {
  if (max <= 4) return 4;
  const pow = Math.pow(10, Math.floor(Math.log10(max)));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * pow >= max) return m * pow;
  return max;
}

/** Stacked bars per time bucket: allowed, flagged but not blocked, blocked. Counts come straight from /api/stats. */
export function timelineChart(timeline) {
  const buckets = timeline.buckets || [];
  const W = 720, H = 230, L = 40, R = 10, T = 10, B = 28;
  const max = niceMax(Math.max(1, ...buckets.map((b) => b.total)));
  const innerW = W - L - R, innerH = H - T - B;
  const slot = innerW / Math.max(1, buckets.length);
  const barW = Math.max(2, Math.min(28, slot * 0.78));
  const y = (v) => T + innerH - (v / max) * innerH;
  const root = svg('svg', { class: 'chart', viewBox: `0 0 ${W} ${H}`, role: 'img',
    'aria-label': `Events per ${timeline.bucketSeconds} second interval, ${buckets.length} intervals, most in one interval: ${Math.max(...buckets.map((b) => b.total))}` });
  for (let i = 0; i <= 4; i++) {
    const v = (max / 4) * i;
    root.appendChild(svg('line', { class: i === 0 ? 'axis' : 'grid', x1: L, x2: W - R, y1: y(v), y2: y(v) }));
    root.appendChild(svg('text', { x: L - 6, y: y(v) + 4, 'text-anchor': 'end' }, fmtNum(Math.round(v * 10) / 10)));
  }
  buckets.forEach((b, i) => {
    const x = L + i * slot + (slot - barW) / 2;
    const allowed = b.total - b.flagged;
    const flaggedOnly = b.flagged - b.blocked;
    let top = y(0);
    const tip = `${fmtDateTime(b.t)}: ${b.total} events, ${b.flagged} flagged, ${b.blocked} blocked`;
    const g = svg('g', {}, svg('title', {}, tip));
    for (const [count, color] of [[allowed, '#4c8dff'], [flaggedOnly, '#f5b942'], [b.blocked, '#ff6b6b']]) {
      if (count <= 0) continue;
      const hgt = (count / max) * innerH;
      top -= hgt;
      g.appendChild(svg('rect', { x, y: top, width: barW, height: hgt, fill: color, rx: 1 }));
    }
    root.appendChild(g);
  });
  const labelIdx = buckets.length <= 1 ? [0] : [0, Math.floor((buckets.length - 1) / 2), buckets.length - 1];
  const wide = timeline.bucketSeconds >= 86400;
  for (const i of [...new Set(labelIdx)]) {
    const b = buckets[i];
    const anchor = i === 0 ? 'start' : i === buckets.length - 1 && buckets.length > 1 ? 'end' : 'middle';
    root.appendChild(svg('text', { x: L + i * slot + slot / 2, y: H - 8, 'text-anchor': anchor }, wide ? fmtDateTime(b.t) : fmtTime(b.t)));
  }
  return h('div', {}, root, h('div', { class: 'legend' },
    h('span', {}, h('i', { class: 'swatch allowed' }), 'Allowed'),
    h('span', {}, h('i', { class: 'swatch flagged' }), 'Flagged, not stopped'),
    h('span', {}, h('i', { class: 'swatch blocked' }), 'Blocked (enforced)')));
}

/** Histogram of 10 bins (0-9 ... 90-100). Optionally marks a threshold such as the ML score of 80. */
export function histogramChart(bins, { label, threshold } = {}) {
  const W = 520, H = 190, L = 34, R = 8, T = 10, B = 30;
  const max = niceMax(Math.max(1, ...bins.map((b) => b.count)));
  const innerW = W - L - R, innerH = H - T - B;
  const slot = innerW / bins.length;
  const root = svg('svg', { class: 'chart', viewBox: `0 0 ${W} ${H}`, role: 'img', 'aria-label': label });
  const y = (v) => T + innerH - (v / max) * innerH;
  for (let i = 0; i <= 4; i++) {
    const v = (max / 4) * i;
    root.appendChild(svg('line', { class: i === 0 ? 'axis' : 'grid', x1: L, x2: W - R, y1: y(v), y2: y(v) }));
    root.appendChild(svg('text', { x: L - 6, y: y(v) + 4, 'text-anchor': 'end' }, fmtNum(Math.round(v * 10) / 10)));
  }
  bins.forEach((b, i) => {
    const x = L + i * slot + slot * 0.1;
    const hgt = (b.count / max) * innerH;
    const high = threshold !== undefined && b.from >= threshold;
    root.appendChild(svg('g', {}, svg('title', {}, `Score ${b.from} to ${b.to}: ${b.count} events`),
      svg('rect', { x, y: y(0) - hgt, width: slot * 0.8, height: hgt, fill: high ? '#f5b942' : '#2dd4ee', rx: 1 })));
    root.appendChild(svg('text', { x: L + i * slot + slot / 2, y: H - 10, 'text-anchor': 'middle' }, String(b.from)));
  });
  if (threshold !== undefined) {
    const x = L + (threshold / 10) * slot;
    root.appendChild(svg('line', { x1: x, x2: x, y1: T, y2: y(0), stroke: '#f5b942', 'stroke-dasharray': '4 3' }));
  }
  return root;
}

/** Horizontal bar list. Items are [label, count]; widths are relative to the largest item. */
export function barList(items, { total, format = (l) => l } = {}) {
  const max = Math.max(1, ...items.map(([, c]) => c));
  return h('ul', { class: 'bars' }, items.map(([label, count]) => {
    const pct = total ? ' (' + Math.round((count / total) * 100) + '%)' : '';
    return h('li', { class: 'bar-row' },
      h('div', { class: 'bar-top' }, h('span', {}, format(label)), h('span', { class: 'muted' }, fmtNum(count) + pct)),
      h('div', { class: 'bar-track', 'aria-hidden': 'true' }, h('div', { class: 'bar-fill', style: { width: (count / max) * 100 + '%' } })));
  }));
}
