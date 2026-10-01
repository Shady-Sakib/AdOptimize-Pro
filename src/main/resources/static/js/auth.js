/* =====================================================================
   Sign in / create account — advertiser page (/) and admin portal (/admin/login)
   ===================================================================== */
'use strict';

document.addEventListener('DOMContentLoaded', () => {
  setupPasswordToggles();
  setupTabs();

  wireForm({
    formId: 'loginForm',
    buttonId: 'loginSubmitBtn',
    endpoint: '/api/auth/login',
    collect: () => ({
      email: value('login-email'),
      password: raw('login-password'),
    }),
    checks: (d) => [
      ['email', d.email !== '', 'Enter your email address'],
      ['email', Rules.email(d.email), 'Enter a valid email address'],
      ['password', d.password !== '', 'Enter your password'],
    ],
    onForbidden: (error) => {
      // An administrator used the advertiser form: send them to the right portal.
      if (/admin portal/i.test(error.message)) {
        setTimeout(() => { location.href = Api.url('/admin/login'); }, 1500);
      }
    },
  });

  wireForm({
    formId: 'registerForm',
    buttonId: 'registerSubmitBtn',
    endpoint: '/api/auth/register',
    collect: () => ({
      name: value('reg-name'),
      company: value('reg-company'),
      email: value('reg-email'),
      password: raw('reg-password'),
      confirmPassword: raw('reg-confirm'),
    }),
    checks: registrationChecks,
  });

  wireForm({
    formId: 'adminLoginForm',
    buttonId: 'adminLoginSubmitBtn',
    endpoint: '/api/auth/admin/login',
    collect: () => ({
      email: value('admin-login-email'),
      password: raw('admin-login-password'),
      groupCode: value('admin-login-groupcode'),
    }),
    checks: (d) => [
      ['email', d.email !== '', 'Enter your email address'],
      ['email', Rules.email(d.email), 'Enter a valid email address'],
      ['password', d.password !== '', 'Enter your password'],
      ['groupCode', d.groupCode !== '', 'Enter the group code'],
    ],
    onForbidden: (error) => {
      if (/advertiser sign-in/i.test(error.message)) {
        setTimeout(() => { location.href = Api.url('/'); }, 1800);
      }
    },
  });

  wireForm({
    formId: 'adminRegisterForm',
    buttonId: 'adminRegisterSubmitBtn',
    endpoint: '/api/auth/admin/register',
    collect: () => ({
      name: value('admin-reg-name'),
      company: value('admin-reg-company'),
      email: value('admin-reg-email'),
      password: raw('admin-reg-password'),
      confirmPassword: raw('admin-reg-confirm'),
      groupCode: value('admin-reg-groupcode'),
    }),
    checks: (d) => [...registrationChecks(d), ['groupCode', d.groupCode !== '', 'Enter the group code']],
  });

  document.getElementById('fillDemo')?.addEventListener('click', (event) => {
    event.preventDefault();
    switchTab('login');
    setValue('login-email', 'demo@adpro.com');
    setValue('login-password', 'demo123');
    document.getElementById('loginSubmitBtn').focus();
  });

  document.getElementById('fillAdminDemo')?.addEventListener('click', (event) => {
    event.preventDefault();
    switchTab('login');
    setValue('admin-login-email', 'admin@adpro.com');
    setValue('admin-login-password', 'admin123');
    setValue('admin-login-groupcode', event.currentTarget.dataset.groupCode || 'group 5');
    document.getElementById('adminLoginSubmitBtn').focus();
  });
});

function registrationChecks(d) {
  return [
    ['name', d.name.length >= 2 && d.name.length <= 60, 'Name must be 2–60 characters'],
    ['name', Rules.personName(d.name), 'Name can only contain letters, spaces, apostrophes, periods and hyphens'],
    ['email', d.email !== '', 'Enter your email address'],
    ['email', Rules.email(d.email), 'Enter a valid email address'],
    ['password', d.password.length >= 6 && d.password.length <= 72, 'Password must be 6–72 characters'],
    ['password', Rules.password(d.password), 'Password must contain at least one letter and one number'],
    ['confirmPassword', d.confirmPassword !== '', 'Confirm your password'],
    ['confirmPassword', d.confirmPassword === d.password, 'Passwords do not match'],
  ];
}

function wireForm({ formId, buttonId, endpoint, collect, checks, onForbidden }) {
  const form = document.getElementById(formId);
  if (!form) return;
  FormErrors.attachAutoClear(form);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = collect();
    if (!FormErrors.validate(form, checks(data))) return;

    const restore = setLoading(document.getElementById(buttonId));
    try {
      const result = await Api.post(endpoint, data);
      toast(result.message, 'success');
      // Keep the button disabled while the browser navigates away.
      setTimeout(() => { location.href = Api.url(result.redirectUrl); }, 600);
    } catch (error) {
      restore();
      if (error.status === 403 && /session has expired/i.test(error.message)) {
        toast(error.message, 'warning');
        setTimeout(() => location.reload(), 1500);
        return;
      }
      handleError(error, form);
      if (error.status === 403 && onForbidden) onForbidden(error);
    }
  });
}

function setupTabs() {
  document.querySelectorAll('[data-tab]').forEach((button) => {
    button.addEventListener('click', () => switchTab(button.dataset.tab));
  });
}

function switchTab(tab) {
  const buttons = document.querySelectorAll('[data-tab]');
  buttons.forEach((button) => {
    const active = button.dataset.tab === tab;
    button.classList.toggle('active', active);
    button.setAttribute('aria-selected', String(active));
    const panel = document.getElementById(button.getAttribute('aria-controls'));
    panel?.classList.toggle('hidden', !active);
  });
}

function value(id) {
  return (document.getElementById(id)?.value || '').trim();
}

function raw(id) {
  return document.getElementById(id)?.value || '';
}

function setValue(id, text) {
  const input = document.getElementById(id);
  if (input) {
    input.value = text;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
}
