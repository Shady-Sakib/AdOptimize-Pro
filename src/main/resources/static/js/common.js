/* =====================================================================
   AdOptimize Pro — shared browser utilities
   Api client (CSRF + JSON + error handling), HTML escaping, formatting,
   toasts, modal/confirm dialogs, inline form errors and sign-out.
   ===================================================================== */
'use strict';

/* ---------------------------------------------------------------- escaping */

/** Escapes text for safe insertion into innerHTML (all user data goes through this). */
function esc(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/* ---------------------------------------------------------------- formatting */

const Fmt = {
  number(value) {
    const n = Number(value) || 0;
    if (Math.abs(n) >= 1e6) return (n / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
    if (Math.abs(n) >= 1e4) return (n / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
    return n.toLocaleString('en-US');
  },
  integer(value) {
    return (Number(value) || 0).toLocaleString('en-US');
  },
  money(value) {
    const n = Number(value) || 0;
    return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },
  moneyShort(value) {
    const n = Number(value) || 0;
    if (Math.abs(n) >= 1e6) return '$' + (n / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
    if (Math.abs(n) >= 1e4) return '$' + (n / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
    return Fmt.money(n);
  },
  percent(value, digits = 2) {
    return (Number(value) || 0).toFixed(digits) + '%';
  },
  /** Parses "2026-09-16" (local date) or "2026-09-16T10:00:00" (local date-time). */
  parse(value) {
    if (!value) return null;
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [y, m, d] = value.split('-').map(Number);
      return new Date(y, m - 1, d);
    }
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date;
  },
  date(value) {
    const date = Fmt.parse(value);
    return date ? date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—';
  },
  dateTime(value) {
    const date = Fmt.parse(value);
    return date ? date.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' }) : '—';
  },
  timeAgo(value) {
    const date = Fmt.parse(value);
    if (!date) return '';
    const seconds = Math.max(0, Math.round((Date.now() - date.getTime()) / 1000));
    if (seconds < 60) return 'just now';
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return minutes + ' min ago';
    const hours = Math.round(minutes / 60);
    if (hours < 24) return hours + (hours === 1 ? ' hour ago' : ' hours ago');
    const days = Math.round(hours / 24);
    if (days < 30) return days + (days === 1 ? ' day ago' : ' days ago');
    return Fmt.date(value);
  },
  /** Today as yyyy-mm-dd in the browser's local time zone. */
  isoToday(offsetDays = 0) {
    const d = new Date();
    d.setDate(d.getDate() + offsetDays);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  },
  capitalize(text) {
    return text ? text.charAt(0).toUpperCase() + text.slice(1) : '';
  },
};

const STATUS_BADGE = {
  active: 'badge-success',
  scheduled: 'badge-info',
  pending: 'badge-warning',
  paused: 'badge-muted',
  completed: 'badge-primary',
  rejected: 'badge-danger',
  open: 'badge-warning',
  resolved: 'badge-success',
};

function statusBadge(status) {
  return `<span class="badge ${STATUS_BADGE[status] || 'badge-muted'}">${esc(Fmt.capitalize(status))}</span>`;
}

function priorityBadge(priority) {
  const cls = { high: 'badge-danger', medium: 'badge-warning', low: 'badge-muted' }[priority] || 'badge-muted';
  return `<span class="badge ${cls}">${esc(Fmt.capitalize(priority))}</span>`;
}

function changeHtml(change, suffix = 'vs previous period') {
  if (change === null || change === undefined) return `<span class="stat-change">No earlier data to compare</span>`;
  const up = change >= 0;
  return `<div class="stat-change ${up ? 'up' : 'down'}">${up ? '↑' : '↓'} ${Math.abs(change).toFixed(1)}% ${esc(suffix)}</div>`;
}

/* ---------------------------------------------------------------- API client */

class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

const Api = (() => {
  const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.getAttribute('content') || '';
  const base = meta('ctx').replace(/\/$/, '');
  const csrfToken = meta('_csrf');
  const csrfHeader = meta('_csrf_header') || 'X-CSRF-TOKEN';
  let redirecting = false;

  function url(path) {
    return base + path;
  }

  function loginPage() {
    return url(location.pathname.startsWith(base + '/admin') ? '/admin/login' : '/');
  }

  async function request(method, path, body) {
    const options = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    if (method !== 'GET' && csrfToken) {
      options.headers[csrfHeader] = csrfToken;
    }

    let response;
    try {
      response = await fetch(url(path), options);
    } catch (networkError) {
      throw new ApiError(0, 'Could not reach the server. Check your connection and try again.');
    }

    let data = null;
    const type = response.headers.get('Content-Type') || '';
    if (type.includes('application/json')) {
      data = await response.json().catch(() => null);
    }

    if (response.ok) return data;

    const message = data?.message || `Request failed (${response.status}).`;
    const error = new ApiError(response.status, message, data?.fieldErrors);
    const isAuthCall = path.startsWith('/api/auth/');
    if (response.status === 401 && !isAuthCall) {
      // Session ended: warn once and send the user to the sign-in page.
      error.handled = true;
      if (!redirecting) {
        redirecting = true;
        toast(message, 'warning');
        const suffix = /deactivated/i.test(message) ? '?deactivated' : '';
        setTimeout(() => { location.href = loginPage() + suffix; }, 1400);
      }
    }
    throw error;
  }

  /** Downloads a file from an authenticated GET endpoint. */
  async function download(path, fallbackName) {
    let response;
    try {
      response = await fetch(url(path), { credentials: 'same-origin' });
    } catch (e) {
      throw new ApiError(0, 'Could not reach the server. Check your connection and try again.');
    }
    if (!response.ok) {
      const data = await response.json().catch(() => null);
      throw new ApiError(response.status, data?.message || 'Download failed.');
    }
    const disposition = response.headers.get('Content-Disposition') || '';
    const match = disposition.match(/filename="?([^";]+)"?/);
    const blob = await response.blob();
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = match ? match[1] : fallbackName;
    document.body.appendChild(link);
    link.click();
    setTimeout(() => { URL.revokeObjectURL(link.href); link.remove(); }, 1000);
  }

  return {
    url,
    csrfToken,
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body === undefined ? {} : body),
    put: (path, body) => request('PUT', path, body),
    patch: (path, body) => request('PATCH', path, body),
    del: (path) => request('DELETE', path),
    download,
  };
})();

/* ---------------------------------------------------------------- toasts */

function toast(message, type = 'info', duration = 4200) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);
  }
  const icons = { success: '✅', error: '⛔', warning: '⚠️', info: 'ℹ️' };
  const item = document.createElement('div');
  item.className = `toast toast-${type}`;
  item.setAttribute('role', type === 'error' ? 'alert' : 'status');
  item.innerHTML = `<span class="toast-icon">${icons[type] || icons.info}</span><span class="toast-message">${esc(message)}</span>`;
  container.appendChild(item);
  setTimeout(() => {
    item.classList.add('leaving');
    setTimeout(() => item.remove(), 220);
  }, duration);
}

/** Shows an API error as a toast (and inline on a form, when field errors and a form are given). */
function handleError(error, form, fieldMap) {
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {};
  if (form && fieldErrors && Object.keys(fieldErrors).length) {
    const shown = FormErrors.show(form, fieldErrors, fieldMap);
    toast(shown > 1 ? 'Please fix the highlighted fields.' : error.message, 'error');
    return;
  }
  if (!(error instanceof ApiError)) console.error(error);
  if (error.handled) return; // the API client already warned and is redirecting
  toast(error.message || 'Something went wrong.', 'error');
}

/* ---------------------------------------------------------------- inline form errors */

const FormErrors = {
  /** Marks inputs whose data-field matches an error key. Returns the number of fields marked. */
  show(form, fieldErrors, fieldMap = {}) {
    FormErrors.clear(form);
    let shown = 0;
    let first = null;
    Object.entries(fieldErrors).forEach(([field, message]) => {
      const name = fieldMap[field] || field;
      const input = form.querySelector(`[data-field="${name}"]`);
      if (!input) return;
      input.classList.add('input-error');
      input.setAttribute('aria-invalid', 'true');
      const holder = input.closest('.form-group') || input.parentElement;
      const div = document.createElement('div');
      div.className = 'field-error';
      div.textContent = message;
      holder.appendChild(div);
      shown++;
      if (!first) first = input;
    });
    if (first) first.focus({ preventScroll: false });
    return shown || Object.keys(fieldErrors).length;
  },
  clear(form) {
    form.querySelectorAll('.field-error').forEach((el) => el.remove());
    form.querySelectorAll('.input-error').forEach((el) => {
      el.classList.remove('input-error');
      el.removeAttribute('aria-invalid');
    });
  },
  /** Client-side check helper: collects messages and shows them like server errors. */
  validate(form, checks) {
    const errors = {};
    checks.forEach(([field, valid, message]) => {
      if (!errors[field] && !valid) errors[field] = message;
    });
    if (Object.keys(errors).length) {
      const count = FormErrors.show(form, errors);
      toast(count > 1 ? 'Please fix the highlighted fields.' : Object.values(errors)[0], 'error');
      return false;
    }
    FormErrors.clear(form);
    return true;
  },
  /** Removes a field's error as soon as the user edits it. */
  attachAutoClear(form) {
    form.addEventListener('input', (event) => {
      const target = event.target;
      if (!target.classList?.contains('input-error')) return;
      target.classList.remove('input-error');
      target.removeAttribute('aria-invalid');
      target.closest('.form-group')?.querySelectorAll('.field-error').forEach((el) => el.remove());
    });
  },
};

const Rules = {
  email: (v) => /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(v),
  personName: (v) => /^\p{L}[\p{L} .'-]*$/u.test(v),
  password: (v) => v.length >= 6 && v.length <= 72 && /\p{L}/u.test(v) && /\d/.test(v),
  luhn(value) {
    const digits = value.replace(/[\s-]/g, '');
    if (!/^\d{13,19}$/.test(digits)) return false;
    let sum = 0;
    let doubleIt = false;
    for (let i = digits.length - 1; i >= 0; i--) {
      let d = Number(digits[i]);
      if (doubleIt) { d *= 2; if (d > 9) d -= 9; }
      sum += d;
      doubleIt = !doubleIt;
    }
    return sum % 10 === 0;
  },
  expiry(value) {
    const m = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(value.trim());
    if (!m) return false;
    const now = new Date();
    const expiryIndex = (2000 + Number(m[2])) * 12 + Number(m[1]);
    const nowIndex = now.getFullYear() * 12 + now.getMonth() + 1;
    return expiryIndex >= nowIndex && expiryIndex <= nowIndex + 240;
  },
};

/* ---------------------------------------------------------------- buttons */

/** Disables a button and shows a spinner; returns a function that restores it. */
function setLoading(button, label = 'Please wait…') {
  if (!button) return () => {};
  const original = button.innerHTML;
  button.disabled = true;
  button.setAttribute('aria-busy', 'true');
  button.innerHTML = `<span class="btn-spinner"></span> ${esc(label)}`;
  return () => {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.innerHTML = original;
  };
}

/* ---------------------------------------------------------------- modals */

const Modal = {
  open(id) {
    const overlay = document.getElementById(id);
    if (!overlay) return;
    overlay.classList.remove('hidden');
    overlay._previousFocus = document.activeElement;
    const focusable = overlay.querySelector('input:not([type=hidden]), select, textarea, button:not(.modal-close)');
    setTimeout(() => focusable?.focus(), 30);
  },
  close(id) {
    const overlay = document.getElementById(id);
    if (!overlay || overlay.classList.contains('hidden')) return;
    overlay.classList.add('hidden');
    const form = overlay.querySelector('form');
    if (form) FormErrors.clear(form);
    overlay._previousFocus?.focus?.();
    if (typeof overlay._onClose === 'function') {
      const callback = overlay._onClose;
      overlay._onClose = null;
      callback();
    }
  },
  /** Wires close buttons, backdrop clicks and the Escape key for every .modal-overlay. */
  init() {
    document.addEventListener('click', (event) => {
      const closer = event.target.closest('[data-close-modal]');
      if (closer) Modal.close(closer.getAttribute('data-close-modal'));
      if (event.target.classList?.contains('modal-overlay')) Modal.close(event.target.id);
    });
    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      const open = [...document.querySelectorAll('.modal-overlay:not(.hidden)')].pop();
      if (open) Modal.close(open.id);
    });
  },
};

function ensureDialog() {
  let overlay = document.getElementById('dialog-modal');
  if (overlay) return overlay;
  overlay = document.createElement('div');
  overlay.id = 'dialog-modal';
  overlay.className = 'modal-overlay hidden';
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-modal', 'true');
  overlay.innerHTML = `
    <div class="modal" style="max-width:440px">
      <div class="modal-header">
        <h2 class="modal-title" id="dialog-title"></h2>
        <button class="modal-close" type="button" data-close-modal="dialog-modal" aria-label="Close">✕</button>
      </div>
      <p class="modal-text" id="dialog-text"></p>
      <form id="dialog-form" novalidate>
        <div class="form-group hidden" id="dialog-input-group">
          <label class="form-label" for="dialog-input" id="dialog-input-label"></label>
          <textarea class="form-control" id="dialog-input" data-field="value" rows="3"></textarea>
        </div>
        <div class="flex gap-sm" style="justify-content:flex-end">
          <button type="button" class="btn btn-ghost" data-close-modal="dialog-modal">Cancel</button>
          <button type="submit" class="btn btn-primary" id="dialog-confirm"></button>
        </div>
      </form>
    </div>`;
  document.body.appendChild(overlay);
  return overlay;
}

/**
 * Styled replacement for window.confirm / window.prompt.
 * Resolves to true (confirm) or the entered text (prompt) — or null when cancelled.
 */
function dialog({ title, message, confirmText = 'Confirm', danger = false, input = null }) {
  const overlay = ensureDialog();
  overlay.querySelector('#dialog-title').textContent = title;
  overlay.querySelector('#dialog-text').textContent = message || '';
  const confirmButton = overlay.querySelector('#dialog-confirm');
  confirmButton.textContent = confirmText;
  confirmButton.className = `btn ${danger ? 'btn-danger' : 'btn-primary'}`;
  const group = overlay.querySelector('#dialog-input-group');
  const field = overlay.querySelector('#dialog-input');
  group.classList.toggle('hidden', !input);
  if (input) {
    overlay.querySelector('#dialog-input-label').textContent = input.label;
    field.value = input.value || '';
    field.placeholder = input.placeholder || '';
    field.maxLength = input.maxLength || 2000;
  }
  const form = overlay.querySelector('#dialog-form');

  return new Promise((resolve) => {
    let result = null;
    form.onsubmit = (event) => {
      event.preventDefault();
      if (input) {
        const value = field.value.trim();
        if (input.validate) {
          const problem = input.validate(value);
          if (problem) {
            FormErrors.show(form, { value: problem });
            return;
          }
        }
        result = value;
      } else {
        result = true;
      }
      Modal.close('dialog-modal');
    };
    overlay._onClose = () => resolve(result);
    Modal.open('dialog-modal');
    setTimeout(() => (input ? field : confirmButton).focus(), 40);
  });
}

const confirmDialog = (options) => dialog(options).then((value) => value === true);

/* ---------------------------------------------------------------- misc */

function debounce(fn, wait = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}

/** Signs out with a CSRF-protected POST (Spring Security's /logout). */
function logoutUser() {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = Api.url('/logout');
  const token = document.createElement('input');
  token.type = 'hidden';
  token.name = '_csrf';
  token.value = Api.csrfToken;
  form.appendChild(token);
  document.body.appendChild(form);
  form.submit();
}

function setupPasswordToggles(root = document) {
  root.querySelectorAll('.toggle-password').forEach((button) => {
    button.addEventListener('click', () => {
      const input = button.parentElement.querySelector('input');
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      button.textContent = show ? '🙈' : '👁';
      button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    });
  });
}

/** Mobile sidebar open/close with a click-away backdrop. */
function setupSidebar() {
  const sidebar = document.getElementById('sidebar');
  const toggle = document.getElementById('sidebar-toggle');
  if (!sidebar || !toggle) return;
  const backdrop = document.createElement('div');
  backdrop.className = 'sidebar-backdrop';
  document.body.appendChild(backdrop);
  const setOpen = (open) => {
    sidebar.classList.toggle('open', open);
    backdrop.classList.toggle('visible', open);
    toggle.setAttribute('aria-expanded', String(open));
  };
  toggle.addEventListener('click', () => setOpen(!sidebar.classList.contains('open')));
  backdrop.addEventListener('click', () => setOpen(false));
  sidebar.addEventListener('click', (event) => {
    if (event.target.closest('.nav-item') && window.innerWidth <= 1024) setOpen(false);
  });
}

function emptyState(icon, title, text, actionHtml = '') {
  return `<div class="empty-state"><div class="empty-state-icon">${icon}</div><h3>${esc(title)}</h3><p>${esc(text)}</p>${actionHtml}</div>`;
}

document.addEventListener('DOMContentLoaded', () => Modal.init());
