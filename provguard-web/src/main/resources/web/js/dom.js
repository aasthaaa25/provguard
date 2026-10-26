// Safe DOM construction. Event data is only ever inserted as text nodes or checked attributes.
// There is no innerHTML, outerHTML, insertAdjacentHTML, or document.write anywhere in this dashboard.

const SVG_NS = 'http://www.w3.org/2000/svg';
const ALLOWED_ATTRS = new Set([
  'id', 'type', 'value', 'placeholder', 'title', 'disabled', 'checked', 'tabindex', 'role', 'name',
  'colspan', 'scope', 'open', 'download', 'min', 'max', 'step', 'rows', 'autocomplete', 'spellcheck',
  'selected', 'for', 'target', 'rel', 'lang', 'maxlength', 'inputmode', 'enterkeyhint',
]);

function safeHref(value) {
  // Internal routes and same-origin API downloads only. Never javascript:, data:, or external URLs.
  return typeof value === 'string' && (value.startsWith('#/') || value.startsWith('/api/'));
}

function applyProps(el, props) {
  if (!props) return;
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') {
      el.setAttribute('class', Array.isArray(value) ? value.filter(Boolean).join(' ') : String(value));
    } else if (key === 'style' && typeof value === 'object') {
      Object.assign(el.style, value); // CSSOM assignment, allowed under the page CSP
    } else if (key === 'dataset' && typeof value === 'object') {
      for (const [k, v] of Object.entries(value)) el.dataset[k] = String(v);
    } else if (key.startsWith('on') && typeof value === 'function') {
      el.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key === 'href') {
      if (safeHref(value)) el.setAttribute('href', value);
    } else if (key.startsWith('aria-') || ALLOWED_ATTRS.has(key)) {
      el.setAttribute(key, value === true ? '' : String(value));
    }
    // Anything else is ignored on purpose.
  }
}

function append(el, children) {
  for (const child of children) {
    if (child === null || child === undefined || child === false || child === true) continue;
    if (Array.isArray(child)) append(el, child);
    else if (child instanceof Node) el.appendChild(child);
    else el.appendChild(document.createTextNode(String(child)));
  }
}

export function h(tag, props, ...children) {
  const el = document.createElement(tag);
  applyProps(el, props);
  append(el, children);
  return el;
}

export function svg(tag, attrs, ...children) {
  const el = document.createElementNS(SVG_NS, tag);
  if (attrs) {
    for (const [key, value] of Object.entries(attrs)) {
      if (value === undefined || value === null || value === false) continue;
      if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
      else if (key === 'class') el.setAttribute('class', String(value));
      else if (/^[a-zA-Z-]+$/.test(key) && !key.startsWith('on')) el.setAttribute(key, String(value));
    }
  }
  append(el, children);
  return el;
}

export function icon(name) {
  const el = document.createElementNS(SVG_NS, 'svg');
  el.setAttribute('class', 'icon');
  el.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(SVG_NS, 'use');
  use.setAttribute('href', '#i-' + name);
  el.appendChild(use);
  return el;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

export function mount(node, ...children) {
  clear(node);
  append(node, children);
  return node;
}
