/* =====================================================================
   Advertiser dashboard — overview, campaigns, optimizer, budget, payments,
   notifications, profile and support. All data comes from /api/advertiser.
   ===================================================================== */
'use strict';

const SECTIONS = {
  overview: { title: 'Overview', load: loadOverview },
  campaigns: { title: 'Campaigns', load: loadCampaigns },
  optimizer: { title: 'Optimizer', load: loadOptimizerList },
  budget: { title: 'Budget', load: loadBudget },
  payments: { title: 'Payments', load: loadPayments },
  notifications: { title: 'Notifications', load: loadNotifications },
  profile: { title: 'Profile', load: loadProfileStats },
  support: { title: 'Support', load: loadTickets },
};

const AD_TYPE_ICON = { image: '🖼️', video: '🎬', text: '📝', carousel: '🎠' };
const STATUS_COLORS = {
  active: '#22d3a5', scheduled: '#38bdf8', pending: '#f59e0b', paused: '#6d7596', completed: '#6c63ff', rejected: '#f43f5e',
};

const state = {
  section: 'overview',
  options: null,
  editingCampaign: null,
  optimizerCampaignId: null,
  campaignCache: new Map(),
};

document.addEventListener('DOMContentLoaded', () => {
  setupSidebar();
  bindNavigation();
  bindCampaignForm();
  bindPaymentForm();
  bindProfileForms();
  bindSupportForm();

  document.getElementById('overviewPeriod').addEventListener('change', loadOverview);
  document.getElementById('campaignFilter').addEventListener('change', loadCampaigns);
  document.getElementById('optimizerCampaignSelect').addEventListener('change', (event) => {
    state.optimizerCampaignId = Number(event.target.value) || null;
    loadOptimization();
  });
  document.getElementById('markAllReadBtn').addEventListener('click', markAllRead);

  const initial = location.hash.replace('#', '');
  showSection(SECTIONS[initial] ? initial : 'overview');
  refreshUnreadBadge();
  setInterval(refreshUnreadBadge, 30000);
  // Keep the overview live while the simulator delivers traffic.
  setInterval(() => {
    if (state.section === 'overview' && !document.hidden && !document.querySelector('.modal-overlay:not(.hidden)')) loadOverview(true);
  }, 30000);
});

/* ================================================================ navigation */

function bindNavigation() {
  document.addEventListener('click', (event) => {
    const sectionLink = event.target.closest('[data-section]');
    if (sectionLink) {
      event.preventDefault();
      showSection(sectionLink.dataset.section);
      return;
    }
    const action = event.target.closest('[data-action]');
    if (!action) return;
    if (action.tagName === 'A') event.preventDefault();
    const id = Number(action.dataset.id);
    switch (action.dataset.action) {
      case 'logout': logoutUser(); break;
      case 'new-campaign': openCampaignModal(); break;
      case 'view-campaign': openCampaignDetail(id); break;
      case 'edit-campaign': openCampaignModal(id); break;
      case 'delete-campaign': deleteCampaign(id, action); break;
      case 'pause-campaign': changeCampaignState(id, 'pause', action); break;
      case 'resume-campaign': changeCampaignState(id, 'resume', action); break;
      case 'optimize-campaign':
        Modal.close('campaignDetailModal');
        state.optimizerCampaignId = id;
        showSection('optimizer');
        break;
      case 'apply-suggestion': applySuggestion(action.dataset.type, action); break;
      case 'read-notification': markNotificationRead(id, action); break;
      default: break;
    }
  });
}

function showSection(name) {
  if (!SECTIONS[name]) return;
  state.section = name;
  Object.keys(SECTIONS).forEach((key) => {
    document.getElementById(`section-${key}`).classList.toggle('hidden', key !== name);
  });
  document.querySelectorAll('.nav-item[data-section]').forEach((item) => {
    const active = item.dataset.section === name;
    item.classList.toggle('active', active);
    if (active) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  });
  document.getElementById('topbarTitle').textContent = SECTIONS[name].title;
  document.getElementById('topbarBreadcrumb').textContent = `Home › ${SECTIONS[name].title}`;
  if (location.hash !== `#${name}`) history.replaceState(null, '', `#${name}`);
  window.scrollTo({ top: 0 });
  SECTIONS[name].load();
}

/* ================================================================ overview */

async function loadOverview(silent = false) {
  const days = document.getElementById('overviewPeriod').value;
  try {
    const data = await Api.get(`/api/advertiser/overview?days=${days}`);
    const k = data.kpis;
    const label = `vs previous ${data.days} days`;
    document.getElementById('overviewStats').innerHTML = `
      ${kpiCard('👁️', 'rgba(108,99,255,0.15)', Fmt.number(k.impressions), 'Impressions', changeHtml(k.impressionsChange, label))}
      ${kpiCard('🖱️', 'rgba(0,212,255,0.15)', Fmt.number(k.clicks), 'Clicks', changeHtml(k.clicksChange, label))}
      ${kpiCard('🎯', 'rgba(34,211,165,0.15)', Fmt.percent(k.ctr), 'Click-through rate', changeHtml(k.ctrChange, label))}
      ${kpiCard('💸', 'rgba(245,158,11,0.15)', Fmt.moneyShort(k.spend), `Spent · ${Fmt.integer(k.conversions)} conversions`, changeHtml(k.spendChange, label))}`;

    document.getElementById('trendBadge').textContent = data.days > 30 ? 'Weekly' : 'Daily';
    Charts.line(document.getElementById('mainLineChart'), data.trend.labels, [
      { label: 'Impressions', data: data.trend.impressions, color: '#6c63ff' },
      { label: 'Clicks (right axis)', data: data.trend.clicks, color: '#00d4ff', axis: 'right', fill: false },
    ], { emptyMessage: 'No delivery in this period yet' });

    const statuses = Object.keys(STATUS_COLORS);
    const counts = statuses.map((s) => data.statusCounts[s] || 0);
    const total = counts.reduce((a, b) => a + b, 0);
    Charts.doughnut(document.getElementById('statusDonut'), statuses.map(Fmt.capitalize), counts, {
      colors: statuses.map((s) => STATUS_COLORS[s]),
      centerText: String(total),
      centerLabel: total === 1 ? 'campaign' : 'campaigns',
    });
    document.getElementById('walletBadge').textContent = `Available ${Fmt.money(data.wallet.available)}`;

    const body = document.getElementById('recentCampaignBody');
    if (!data.recentCampaigns.length) {
      body.innerHTML = `<tr><td colspan="7">${emptyState('📢', 'No campaigns yet', 'Create your first campaign to start reaching your audience.',
        '<button class="btn btn-primary" data-action="new-campaign">+ New Campaign</button>')}</td></tr>`;
    } else {
      body.innerHTML = data.recentCampaigns.map((c) => `
        <tr>
          <td><a href="#" data-action="view-campaign" data-id="${c.id}" class="font-bold">${esc(c.title)}</a></td>
          <td>${statusBadge(c.status)}</td>
          <td>${Fmt.integer(c.impressions)}</td>
          <td>${Fmt.integer(c.clicks)}</td>
          <td>${Fmt.percent(c.ctr)}</td>
          <td>${Fmt.money(c.spent)}</td>
          <td>${Fmt.money(c.budget)}</td>
        </tr>`).join('');
    }
  } catch (error) {
    if (!silent) handleError(error);
  }
}

function kpiCard(icon, background, value, label, change) {
  return `<div class="stat-card">
      <div class="stat-icon" style="background:${background}" aria-hidden="true">${icon}</div>
      <div class="stat-value">${esc(value)}</div>
      <div class="stat-label">${esc(label)}</div>
      ${change}
    </div>`;
}

/* ================================================================ campaigns */

async function loadCampaigns() {
  const grid = document.getElementById('campaignsGrid');
  const filter = document.getElementById('campaignFilter').value;
  try {
    const campaigns = await Api.get(`/api/advertiser/campaigns?status=${encodeURIComponent(filter)}`);
    campaigns.forEach((c) => state.campaignCache.set(c.id, c));
    if (!campaigns.length) {
      grid.innerHTML = `<div style="grid-column:1/-1">${filter === 'all'
        ? emptyState('📢', 'No campaigns yet', 'Create a campaign, set a budget and schedule, and submit it for review.',
          '<button class="btn btn-primary" data-action="new-campaign">+ Create Campaign</button>')
        : emptyState('🔍', `No ${filter} campaigns`, 'Try a different status filter.')}</div>`;
      return;
    }
    grid.innerHTML = campaigns.map(campaignCard).join('');
  } catch (error) {
    handleError(error);
  }
}

function campaignCard(c) {
  const notices = [];
  if (c.status === 'rejected' && c.rejectionReason) {
    notices.push(`<div class="campaign-notice danger">❌ Rejected: ${esc(c.rejectionReason)} Edit the campaign to resubmit it.</div>`);
  }
  if (c.flagged) {
    notices.push(`<div class="campaign-notice warning">⚠️ Flagged for manual review: ${esc(c.flagReason)}</div>`);
  }
  if (c.status === 'scheduled') {
    notices.push(`<div class="campaign-notice warning" style="color:#7dd3fc;background:rgba(56,189,248,0.08);border-color:rgba(56,189,248,0.2)">🗓️ Approved — goes live on ${Fmt.date(c.startDate)}</div>`);
  }
  const optimizable = ['active', 'scheduled', 'paused'].includes(c.status);
  return `
    <article class="campaign-card">
      <div class="campaign-card-top">
        <div class="campaign-thumb" aria-hidden="true">${AD_TYPE_ICON[c.adType] || '📢'}</div>
        <div class="campaign-info">
          <div class="campaign-title">${esc(c.title)}</div>
          <div class="campaign-meta">${esc(c.audienceLabel)} · ${esc(c.adTypeLabel)} · ${Fmt.date(c.startDate)} – ${Fmt.date(c.endDate)}</div>
        </div>
        ${statusBadge(c.status)}
      </div>
      ${notices.join('')}
      <div class="campaign-metrics">
        <div class="campaign-metric"><div class="campaign-metric-val">${Fmt.number(c.impressions)}</div><div class="campaign-metric-lbl">Impressions</div></div>
        <div class="campaign-metric"><div class="campaign-metric-val">${Fmt.number(c.clicks)}</div><div class="campaign-metric-lbl">Clicks</div></div>
        <div class="campaign-metric"><div class="campaign-metric-val">${Fmt.percent(c.ctr)}</div><div class="campaign-metric-lbl">CTR</div></div>
      </div>
      <div class="campaign-progress-label"><span>Budget used</span><span>${Fmt.money(c.spent)} / ${Fmt.money(c.budget)}</span></div>
      <div class="progress-bar-wrap" role="progressbar" aria-valuenow="${c.budgetUsedPercent}" aria-valuemin="0" aria-valuemax="100">
        <div class="progress-bar" style="width:${c.budgetUsedPercent}%;${c.budgetUsedPercent >= 80 ? 'background:var(--grad-warning)' : ''}"></div>
      </div>
      <div class="campaign-actions">
        <button class="btn btn-ghost btn-sm" data-action="view-campaign" data-id="${c.id}">📈 Details</button>
        ${c.editable ? `<button class="btn btn-ghost btn-sm" data-action="edit-campaign" data-id="${c.id}">✏️ Edit</button>` : ''}
        ${c.pausable ? `<button class="btn btn-warning btn-sm" data-action="pause-campaign" data-id="${c.id}">⏸ Pause</button>` : ''}
        ${c.resumable ? `<button class="btn btn-success btn-sm" data-action="resume-campaign" data-id="${c.id}">▶ Resume</button>` : ''}
        ${optimizable ? `<button class="btn btn-ghost btn-sm" data-action="optimize-campaign" data-id="${c.id}">💡 Optimize</button>` : ''}
        <button class="btn btn-danger btn-sm" data-action="delete-campaign" data-id="${c.id}">🗑 Delete</button>
      </div>
    </article>`;
}

async function changeCampaignState(id, verb, button) {
  const restore = setLoading(button, verb === 'pause' ? 'Pausing…' : 'Resuming…');
  try {
    const campaign = await Api.post(`/api/advertiser/campaigns/${id}/${verb}`);
    toast(verb === 'pause' ? `"${campaign.title}" paused.` : `"${campaign.title}" is ${campaign.status} again.`, 'success');
    Modal.close('campaignDetailModal');
    refreshCurrentSection();
  } catch (error) {
    restore();
    handleError(error);
  }
}

async function deleteCampaign(id, button) {
  const campaign = state.campaignCache.get(id);
  const name = campaign ? `"${campaign.title}"` : 'this campaign';
  const live = campaign && ['active', 'scheduled', 'paused'].includes(campaign.status);
  const ok = await confirmDialog({
    title: 'Delete campaign?',
    message: `${name} will be removed from your dashboard.${live ? ' It will stop delivering immediately and its unspent budget returns to your available funds.' : ''} Money already spent is not refunded.`,
    confirmText: 'Delete campaign',
    danger: true,
  });
  if (!ok) return;
  const restore = setLoading(button, 'Deleting…');
  try {
    const result = await Api.del(`/api/advertiser/campaigns/${id}`);
    toast(result.message, 'success');
    Modal.close('campaignDetailModal');
    state.campaignCache.delete(id);
    if (state.optimizerCampaignId === id) state.optimizerCampaignId = null;
    refreshCurrentSection();
  } catch (error) {
    restore();
    handleError(error);
  }
}

function refreshCurrentSection() {
  SECTIONS[state.section].load();
}

/* ---------------------------------------------------------------- campaign modal */

async function loadOptions() {
  state.options = await Api.get('/api/advertiser/campaigns/options');
  const audience = document.getElementById('camp-audience');
  audience.innerHTML = '<option value="">Select audience</option>' +
    state.options.audiences.map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('');
  document.getElementById('camp-type').innerHTML =
    state.options.adTypes.map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('');
  return state.options;
}

async function openCampaignModal(id) {
  const form = document.getElementById('campaignForm');
  FormErrors.clear(form);
  form.reset();
  state.editingCampaign = null;
  let campaign = null;
  try {
    const [options, detail] = await Promise.all([
      loadOptions(),
      id ? Api.get(`/api/advertiser/campaigns/${id}`) : Promise.resolve(null),
    ]);
    campaign = detail ? detail.campaign : null;
    const today = Fmt.isoToday();
    const start = document.getElementById('camp-start');
    const end = document.getElementById('camp-end');
    const notice = document.getElementById('campaignEditNotice');

    if (campaign) {
      state.editingCampaign = campaign;
      document.getElementById('campaignModalTitle').textContent = 'Edit Campaign';
      setField('camp-title', campaign.title);
      setField('camp-desc', campaign.description);
      setField('camp-audience', campaign.audience);
      setField('camp-type', campaign.adType);
      setField('camp-budget', campaign.budget);
      setField('camp-daily', campaign.dailyBudget);
      setField('camp-start', campaign.startDate);
      setField('camp-end', campaign.endDate);
      setField('camp-keywords', campaign.keywords.join(', '));
      start.min = campaign.startDate < today ? campaign.startDate : today;
      const reserved = ['pending', 'scheduled', 'active', 'paused'].includes(campaign.status) ? Number(campaign.remaining) : 0;
      document.getElementById('budgetHint').textContent =
        `Minimum ${Fmt.money(options.minBudget)} · ${Fmt.money(Number(options.availableFunds) + reserved)} available for this campaign` +
        (Number(campaign.spent) > 0 ? ` · ${Fmt.money(campaign.spent)} already spent` : '');
      const messages = {
        rejected: 'Saving resubmits this campaign for review.',
        pending: 'This campaign is waiting for review. Saving keeps it in the review queue.',
        active: 'Budget and end-date changes apply immediately. Changing the title, description, audience, format, keywords or start date sends the campaign back for review.',
        scheduled: 'Budget and end-date changes apply immediately. Content or start-date changes send the campaign back for review.',
        paused: 'Budget and end-date changes apply immediately. Content or start-date changes send the campaign back for review.',
      };
      notice.textContent = messages[campaign.status] || '';
      notice.classList.toggle('hidden', !messages[campaign.status]);
      document.getElementById('campaignSubmitBtn').textContent = campaign.status === 'rejected' ? '🚀 Resubmit for Review' : '💾 Save Changes';
    } else {
      document.getElementById('campaignModalTitle').textContent = 'Create Campaign';
      start.min = today;
      start.value = today;
      end.value = Fmt.isoToday(30);
      document.getElementById('budgetHint').textContent =
        `Minimum ${Fmt.money(options.minBudget)} · ${Fmt.money(options.availableFunds)} available`;
      notice.classList.add('hidden');
      document.getElementById('campaignSubmitBtn').textContent = '🚀 Submit for Review';
    }
    end.min = start.value || today;
    Modal.open('campaignModal');
  } catch (error) {
    handleError(error);
  }
}

function setField(id, value) {
  document.getElementById(id).value = value === null || value === undefined ? '' : value;
}

function bindCampaignForm() {
  const form = document.getElementById('campaignForm');
  FormErrors.attachAutoClear(form);
  document.getElementById('camp-start').addEventListener('change', (event) => {
    const end = document.getElementById('camp-end');
    end.min = event.target.value;
    if (end.value && end.value <= event.target.value) end.value = '';
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const text = (id) => document.getElementById(id).value.trim();
    const number = (id) => (text(id) === '' ? null : Number(text(id)));
    const data = {
      title: text('camp-title'),
      description: text('camp-desc'),
      audience: text('camp-audience'),
      adType: text('camp-type'),
      budget: number('camp-budget'),
      dailyBudget: number('camp-daily'),
      startDate: text('camp-start'),
      endDate: text('camp-end'),
      keywords: text('camp-keywords'),
    };
    const editing = state.editingCampaign;
    const minBudget = Number(state.options?.minBudget || 0);
    const keywords = data.keywords.split(',').map((k) => k.trim()).filter(Boolean);
    const startChanged = !editing || editing.startDate !== data.startDate;
    const checks = [
      ['title', data.title.length >= 3 && data.title.length <= 100, 'Title must be 3–100 characters'],
      ['description', data.description.length >= 10 && data.description.length <= 1000, 'Description must be 10–1000 characters'],
      ['audience', data.audience !== '', 'Select a target audience'],
      ['adType', data.adType !== '', 'Select an ad format'],
      ['budget', data.budget !== null && isFinite(data.budget), 'Enter a total budget'],
      ['budget', data.budget === null || data.budget >= minBudget, `Total budget must be at least ${Fmt.money(minBudget)}`],
      ['budget', data.budget === null || /^\d+(\.\d{1,2})?$/.test(text('camp-budget')), 'Total budget can have at most 2 decimal places'],
      ['dailyBudget', data.dailyBudget === null || (isFinite(data.dailyBudget) && data.dailyBudget >= 5), 'Daily budget must be at least $5'],
      ['dailyBudget', data.dailyBudget === null || data.budget === null || data.dailyBudget <= data.budget, "Daily budget can't be more than the total budget"],
      ['startDate', data.startDate !== '', 'Choose a start date'],
      ['startDate', !startChanged || data.startDate === '' || data.startDate >= Fmt.isoToday(), "Start date can't be in the past"],
      ['endDate', data.endDate !== '', 'Choose an end date'],
      ['endDate', !data.startDate || !data.endDate || data.endDate > data.startDate, 'End date must be after the start date'],
      ['keywords', keywords.length <= 20, 'Use at most 20 keywords'],
      ['keywords', keywords.every((k) => /^[\p{L}\p{N}][\p{L}\p{N} &'-]{1,29}$/u.test(k)), 'Each keyword must be 2–30 letters, numbers or spaces'],
    ];
    if (!FormErrors.validate(form, checks)) return;

    const button = document.getElementById('campaignSubmitBtn');
    const restore = setLoading(button, editing ? 'Saving…' : 'Submitting…');
    try {
      const saved = editing
        ? await Api.put(`/api/advertiser/campaigns/${editing.id}`, data)
        : await Api.post('/api/advertiser/campaigns', data);
      restore();
      Modal.close('campaignModal');
      const statusText = {
        pending: saved.flagged ? 'submitted — it was flagged for manual review' : 'submitted for admin review',
        active: 'approved and live',
        scheduled: `approved and scheduled for ${Fmt.date(saved.startDate)}`,
      }[saved.status];
      toast(editing ? `"${saved.title}" saved${statusText ? ' and ' + statusText : ''}.` : `"${saved.title}" ${statusText || 'created'}.`, 'success');
      refreshUnreadBadge();
      if (state.section === 'campaigns' || state.section === 'overview' || state.section === 'budget') refreshCurrentSection();
      else showSection('campaigns');
    } catch (error) {
      restore();
      handleError(error, form);
    }
  });
}

/* ---------------------------------------------------------------- campaign detail */

async function openCampaignDetail(id) {
  const body = document.getElementById('campaignDetailBody');
  try {
    const detail = await Api.get(`/api/advertiser/campaigns/${id}`);
    const c = detail.campaign;
    state.campaignCache.set(c.id, c);
    document.getElementById('campaignDetailTitle').textContent = c.title;
    const optimizable = ['active', 'scheduled', 'paused'].includes(c.status);
    body.innerHTML = `
      <div class="flex items-center gap-sm" style="flex-wrap:wrap;margin-bottom:var(--space-md)">
        ${statusBadge(c.status)}
        <span class="badge badge-muted">${esc(c.audienceLabel)}</span>
        <span class="badge badge-muted">${AD_TYPE_ICON[c.adType] || ''} ${esc(c.adTypeLabel)}</span>
        ${c.peakHoursOnly ? '<span class="badge badge-info">🕒 Peak hours</span>' : ''}
      </div>
      ${c.status === 'rejected' && c.rejectionReason ? `<div class="campaign-notice danger">❌ ${esc(c.rejectionReason)}</div>` : ''}
      ${c.flagged ? `<div class="campaign-notice warning">⚠️ ${esc(c.flagReason)}</div>` : ''}
      <p class="text-secondary" style="margin-bottom:var(--space-lg);white-space:pre-line">${esc(c.description)}</p>
      <div class="grid-3" style="gap:12px;margin-bottom:var(--space-lg)">
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.integer(c.impressions)}</div><div class="mini-stat-label">Impressions</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.integer(c.clicks)}</div><div class="mini-stat-label">Clicks · ${Fmt.percent(c.ctr)} CTR</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.integer(c.conversions)}</div><div class="mini-stat-label">Conversions · ${Fmt.percent(c.conversionRate)}</div></div>
      </div>
      <div class="card" style="padding:var(--space-md);margin-bottom:var(--space-lg)">
        <div class="card-header" style="margin-bottom:8px"><h3 class="card-title" style="font-size:14px">Last 30 days</h3></div>
        <div class="chart-container" style="height:200px"><canvas id="detailChart" role="img" aria-label="Daily impressions and clicks"></canvas></div>
      </div>
      <div class="detail-grid">
        <div class="detail-item"><span class="text-muted text-sm">Budget</span><p>${Fmt.money(c.spent)} spent of ${Fmt.money(c.budget)} (${c.budgetUsedPercent}%)</p></div>
        <div class="detail-item"><span class="text-muted text-sm">Daily budget</span><p>${Fmt.money(c.dailyBudget)}</p></div>
        <div class="detail-item"><span class="text-muted text-sm">Schedule</span><p>${Fmt.date(c.startDate)} – ${Fmt.date(c.endDate)}</p></div>
        <div class="detail-item"><span class="text-muted text-sm">Created</span><p>${Fmt.dateTime(c.createdAt)}</p></div>
        <div class="detail-item" style="grid-column:1/-1"><span class="text-muted text-sm">Keywords</span>
          <p>${c.keywords.length ? c.keywords.map((k) => `<span class="badge badge-primary" style="margin:2px 4px 2px 0">${esc(k)}</span>`).join('') : '<span class="text-muted">None</span>'}</p></div>
      </div>
      ${detail.optimizations.length ? `<hr class="divider"><h3 class="card-title" style="font-size:14px;margin-bottom:8px">Applied optimizations</h3>
        ${detail.optimizations.map((o) => `<div class="history-item"><span>✅</span><div style="flex:1">${esc(o.description)}</div><span class="text-muted text-sm">${Fmt.timeAgo(o.appliedAt)}</span></div>`).join('')}` : ''}
      <div class="campaign-actions" style="justify-content:flex-end">
        ${c.editable ? `<button class="btn btn-ghost btn-sm" data-action="edit-campaign" data-id="${c.id}">✏️ Edit</button>` : ''}
        ${c.pausable ? `<button class="btn btn-warning btn-sm" data-action="pause-campaign" data-id="${c.id}">⏸ Pause</button>` : ''}
        ${c.resumable ? `<button class="btn btn-success btn-sm" data-action="resume-campaign" data-id="${c.id}">▶ Resume</button>` : ''}
        ${optimizable ? `<button class="btn btn-primary btn-sm" data-action="optimize-campaign" data-id="${c.id}">💡 Optimize</button>` : ''}
      </div>`;
    Modal.open('campaignDetailModal');
    // Draw after the modal is visible so the canvas has a size.
    requestAnimationFrame(() => {
      Charts.line(document.getElementById('detailChart'), detail.dailyTrend.labels, [
        { label: 'Impressions', data: detail.dailyTrend.impressions, color: '#6c63ff' },
        { label: 'Clicks (right axis)', data: detail.dailyTrend.clicks, color: '#00d4ff', axis: 'right', fill: false },
      ], { emptyMessage: c.status === 'pending' || c.status === 'scheduled' ? 'Statistics appear once the campaign is live' : 'No delivery yet' });
    });
  } catch (error) {
    handleError(error);
  }
}

// The edit button inside the detail modal should replace the detail view.
document.addEventListener('click', (event) => {
  if (event.target.closest('#campaignDetailModal [data-action="edit-campaign"]')) Modal.close('campaignDetailModal');
}, true);

/* ================================================================ optimizer */

async function loadOptimizerList() {
  const select = document.getElementById('optimizerCampaignSelect');
  const scoreCard = document.getElementById('performanceScore');
  const container = document.getElementById('suggestionsContainer');
  try {
    const campaigns = await Api.get('/api/advertiser/campaigns/optimizable');
    if (!campaigns.length) {
      select.innerHTML = '<option value="">No approved campaigns</option>';
      select.disabled = true;
      scoreCard.innerHTML = emptyState('💡', 'Nothing to optimize yet',
        'Suggestions become available once a campaign is approved (scheduled, active or paused).',
        '<button class="btn btn-primary" data-section="campaigns">View campaigns</button>');
      container.innerHTML = '';
      return;
    }
    select.disabled = false;
    if (!campaigns.some((c) => c.id === state.optimizerCampaignId)) {
      state.optimizerCampaignId = campaigns[0].id;
    }
    select.innerHTML = campaigns.map((c) =>
      `<option value="${c.id}" ${c.id === state.optimizerCampaignId ? 'selected' : ''}>${esc(c.title)} (${esc(c.status)})</option>`).join('');
    await loadOptimization();
  } catch (error) {
    handleError(error);
  }
}

async function loadOptimization() {
  if (!state.optimizerCampaignId) return;
  document.getElementById('performanceScore').innerHTML = '<div class="loading-state">Analysing campaign…</div>';
  try {
    renderOptimization(await Api.get(`/api/advertiser/campaigns/${state.optimizerCampaignId}/optimization`));
  } catch (error) {
    document.getElementById('performanceScore').innerHTML = '';
    handleError(error);
  }
}

function renderOptimization(data) {
  const c = data.campaign;
  const scoreColor = data.score >= 80 ? 'var(--success)' : data.score >= 65 ? 'var(--info)' : data.score >= 45 ? 'var(--warning)' : 'var(--danger)';
  const compare = (value, benchmark) => {
    if (!data.enoughData) return '<span class="text-muted">Not enough data</span>';
    const ratio = benchmark ? value / benchmark : 0;
    const color = ratio >= 1 ? 'var(--success)' : ratio >= 0.8 ? 'var(--warning)' : 'var(--danger)';
    return `<span style="color:${color};font-weight:600">${Fmt.percent(value)}</span> <span class="text-muted">vs ${Fmt.percent(benchmark)} benchmark</span>`;
  };
  document.getElementById('performanceScore').innerHTML = `
    <div class="flex gap-lg items-center" style="flex-wrap:wrap">
      <div class="text-center" style="min-width:140px">
        <div class="score-number" style="background:none;color:${scoreColor};-webkit-text-fill-color:${scoreColor}">${data.score}</div>
        <div class="text-muted text-sm">Performance score</div>
        <div class="badge badge-muted" style="margin-top:6px">${esc(data.scoreLabel)}</div>
      </div>
      <div style="flex:1;min-width:260px">
        <h3 class="card-title" style="margin-bottom:4px">${esc(c.title)}</h3>
        <p class="text-muted text-sm" style="margin-bottom:12px">${esc(c.audienceLabel)} · ${esc(c.adTypeLabel)} · ${statusBadge(c.status)}</p>
        <div class="detail-grid">
          <div class="detail-item"><span class="text-muted text-sm">Click-through rate</span><p>${compare(c.ctr, data.benchmarkCtr)}</p></div>
          <div class="detail-item"><span class="text-muted text-sm">Conversion rate</span><p>${c.clicks >= 30 ? compare(c.conversionRate, data.benchmarkConversionRate) : '<span class="text-muted">Needs 30+ clicks</span>'}</p></div>
          <div class="detail-item"><span class="text-muted text-sm">Budget coverage to end date</span><p>${data.pacingPercent.toFixed(0)}% <span class="text-muted">(100% = evenly paced)</span></p></div>
          <div class="detail-item"><span class="text-muted text-sm">Delivery so far</span><p>${Fmt.integer(c.impressions)} impressions · ${Fmt.money(c.spent)} spent</p></div>
        </div>
        ${data.enoughData ? '' : '<p class="text-sm text-muted">Scores become more precise after 500 impressions.</p>'}
      </div>
    </div>`;

  const container = document.getElementById('suggestionsContainer');
  const suggestions = data.suggestions.length
    ? `<div class="grid-2" style="margin-bottom:var(--space-xl)">${data.suggestions.map(suggestionCard).join('')}</div>`
    : `<div class="card" style="margin-bottom:var(--space-xl)">${emptyState('🏆', 'This campaign is well optimized', 'No changes recommended right now. Check back as more data comes in.')}</div>`;
  const history = data.history.length
    ? data.history.map((h) => `<div class="history-item"><span>✅</span><div style="flex:1">${esc(h.description)}</div><span class="text-muted text-sm">${Fmt.dateTime(h.appliedAt)}</span></div>`).join('')
    : '<p class="text-muted text-sm">No optimizations applied to this campaign yet.</p>';
  container.innerHTML = `${suggestions}<div class="card"><div class="card-header"><h3 class="card-title">Optimization history</h3></div>${history}</div>`;
}

function suggestionCard(s) {
  return `
    <div class="suggestion-card">
      <div class="flex justify-between items-center gap-sm" style="margin-bottom:10px">
        <div class="flex items-center gap-sm"><span style="font-size:22px" aria-hidden="true">${esc(s.icon)}</span><h3 class="card-title" style="font-size:15px">${esc(s.title)}</h3></div>
        <span class="suggestion-score ${esc(s.priority)}">${esc(s.priorityLabel)}</span>
      </div>
      <p class="text-secondary" style="margin-bottom:12px">${esc(s.description)}</p>
      <ul class="suggestion-list">${s.items.map((item) => `<li><span aria-hidden="true">→</span><span>${esc(item)}</span></li>`).join('')}</ul>
      ${s.applicable
        ? `<button class="btn btn-primary btn-sm" data-action="apply-suggestion" data-type="${esc(s.type)}">✨ ${esc(s.actionLabel)}</button>`
        : '<span class="text-sm text-muted">Advice only — make these changes on your landing page.</span>'}
    </div>`;
}

async function applySuggestion(type, button) {
  const restore = setLoading(button, 'Applying…');
  try {
    const data = await Api.post(`/api/advertiser/campaigns/${state.optimizerCampaignId}/optimization`, { type });
    const latest = data.history[0];
    toast(latest ? latest.description : 'Optimization applied.', 'success');
    renderOptimization(data);
  } catch (error) {
    restore();
    handleError(error);
    if (error.status === 400) loadOptimization();
  }
}

/* ================================================================ budget */

async function loadBudget() {
  const container = document.getElementById('budgetContent');
  try {
    const data = await Api.get('/api/advertiser/budget');
    data.campaigns.forEach((c) => state.campaignCache.set(c.id, c));
    const w = data.wallet;
    const rows = data.campaigns.filter((c) => c.status !== 'rejected');
    container.innerHTML = `
      <div class="stats-grid">
        ${kpiCard('🏦', 'rgba(108,99,255,0.15)', Fmt.money(w.balance), 'Account balance', `<div class="stat-change">${Fmt.money(w.totalDeposits)} deposited in total</div>`)}
        ${kpiCard('🔒', 'rgba(245,158,11,0.15)', Fmt.money(w.committed), 'Reserved by campaigns', '<div class="stat-change">Unspent budget of live and pending campaigns</div>')}
        ${kpiCard('✅', 'rgba(34,211,165,0.15)', Fmt.money(w.available), 'Available for new campaigns', '<div class="stat-change">Balance minus reserved budget</div>')}
        ${kpiCard('💸', 'rgba(244,63,94,0.15)', Fmt.money(w.totalSpent), 'Total spent', `<div class="stat-change">${data.usedPercent}% of campaign budgets used</div>`)}
      </div>
      <div class="grid-2" style="margin-bottom:var(--space-xl)">
        <div class="card">
          <div class="card-header"><h3 class="card-title">Overall budget usage</h3><span class="badge badge-primary">${data.usedPercent}%</span></div>
          <div class="campaign-progress-label"><span>${Fmt.money(data.totalSpent)} spent</span><span>${Fmt.money(data.totalBudgeted)} budgeted</span></div>
          <div class="progress-bar-wrap" style="height:12px;margin-bottom:var(--space-lg)"><div class="progress-bar" style="width:${data.usedPercent}%"></div></div>
          <p class="text-secondary">${Fmt.money(data.remaining)} of your campaign budgets is still to be spent. Rejected campaigns are not counted.</p>
          ${Number(w.available) < 50 ? '<p class="text-sm" style="margin-top:12px;color:#fbbf24">⚠️ Your available funds are low. Add funds to create or extend campaigns.</p>' : ''}
        </div>
        <div class="card">
          <div class="card-header"><h3 class="card-title">Monthly spend</h3></div>
          <div class="chart-container" style="height:200px"><canvas id="spendChart" role="img" aria-label="Monthly spend"></canvas></div>
        </div>
      </div>
      <div class="card">
        <div class="card-header"><h3 class="card-title">Budget by campaign</h3></div>
        ${rows.length ? `<div class="table-wrapper"><table class="data-table">
          <thead><tr><th>Campaign</th><th>Status</th><th>Daily</th><th>Budget</th><th>Spent</th><th>Remaining</th><th style="min-width:140px">Usage</th></tr></thead>
          <tbody>${rows.map((c) => `<tr>
            <td><a href="#" data-action="view-campaign" data-id="${c.id}" class="font-bold">${esc(c.title)}</a></td>
            <td>${statusBadge(c.status)}</td>
            <td>${Fmt.money(c.dailyBudget)}</td>
            <td>${Fmt.money(c.budget)}</td>
            <td>${Fmt.money(c.spent)}</td>
            <td>${Fmt.money(c.remaining)}</td>
            <td><div class="progress-bar-wrap"><div class="progress-bar" style="width:${c.budgetUsedPercent}%;${c.budgetUsedPercent >= 80 ? 'background:var(--grad-warning)' : ''}"></div></div>
                <span class="text-sm text-muted">${c.budgetUsedPercent}%</span></td>
          </tr>`).join('')}</tbody></table></div>`
        : emptyState('💰', 'No campaign budgets yet', 'Create a campaign to allocate budget.')}
      </div>`;
    Charts.bar(document.getElementById('spendChart'), data.monthlySpend.labels.map((l) => l.split(' ')[0]),
      [{ label: 'Spend', data: data.monthlySpend.spend, color: '#6c63ff' }],
      { formatY: (v) => '$' + Math.round(v).toLocaleString('en-US'), formatValue: Fmt.money, emptyMessage: 'No spend recorded yet' });
  } catch (error) {
    handleError(error);
  }
}

/* ================================================================ payments */

async function loadPayments() {
  try {
    const [wallet, payments] = await Promise.all([Api.get('/api/advertiser/wallet'), Api.get('/api/advertiser/payments')]);
    document.getElementById('walletStrip').innerHTML = `
      <div class="mini-stat"><div class="mini-stat-value">${Fmt.money(wallet.balance)}</div><div class="mini-stat-label">Balance</div></div>
      <div class="mini-stat"><div class="mini-stat-value">${Fmt.money(wallet.committed)}</div><div class="mini-stat-label">Reserved by campaigns</div></div>
      <div class="mini-stat"><div class="mini-stat-value" style="color:var(--success)">${Fmt.money(wallet.available)}</div><div class="mini-stat-label">Available</div></div>`;
    document.getElementById('transactionList').innerHTML = payments.length
      ? payments.map((p) => `
        <div class="transaction-row">
          <div class="transaction-icon" aria-hidden="true">💳</div>
          <div style="flex:1;min-width:0">
            <div class="font-bold">${esc(p.description)}</div>
            <div class="text-sm text-muted">${esc(p.method)} · ${Fmt.dateTime(p.createdAt)} · ${esc(p.reference)}</div>
          </div>
          <div style="text-align:right">
            <div class="font-bold" style="color:var(--success)">+${Fmt.money(p.amount)}</div>
            <span class="badge ${p.status === 'completed' ? 'badge-success' : 'badge-muted'}">${esc(Fmt.capitalize(p.status))}</span>
          </div>
        </div>`).join('')
      : emptyState('🧾', 'No transactions yet', 'Payments you make will appear here.');
  } catch (error) {
    handleError(error);
  }
}

function bindPaymentForm() {
  const form = document.getElementById('paymentForm');
  FormErrors.attachAutoClear(form);
  const holder = document.getElementById('cardHolder');
  const number = document.getElementById('cardNumber');
  const expiry = document.getElementById('cardExpiry');
  const cvv = document.getElementById('cardCVV');
  const amount = document.getElementById('payAmount');

  const updatePreview = () => {
    const digits = number.value.replace(/\D/g, '');
    const masked = (digits + '•'.repeat(Math.max(0, 16 - digits.length))).slice(0, Math.max(16, digits.length));
    document.getElementById('cardNumPreview').textContent = masked.replace(/(.{4})/g, '$1 ').trim();
    document.getElementById('cardHolderPreview').textContent = holder.value.trim() || 'YOUR NAME';
    document.getElementById('cardExpiryPreview').textContent = expiry.value || 'MM/YY';
  };

  holder.addEventListener('input', updatePreview);
  number.addEventListener('input', () => {
    const digits = number.value.replace(/\D/g, '').slice(0, 19);
    number.value = digits.replace(/(.{4})/g, '$1 ').trim();
    updatePreview();
  });
  expiry.addEventListener('input', (event) => {
    let digits = expiry.value.replace(/\D/g, '').slice(0, 4);
    if (digits.length === 1 && Number(digits) > 1) digits = '0' + digits;
    const deleting = event.inputType && event.inputType.startsWith('delete');
    expiry.value = digits.length >= 3 || (digits.length === 2 && !deleting) ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
    updatePreview();
  });
  cvv.addEventListener('input', () => { cvv.value = cvv.value.replace(/\D/g, '').slice(0, 4); });
  document.querySelectorAll('[data-amount]').forEach((button) => {
    button.addEventListener('click', () => {
      amount.value = button.dataset.amount;
      amount.dispatchEvent(new Event('input', { bubbles: true }));
    });
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const value = Number(amount.value);
    const data = {
      cardHolder: holder.value.trim(),
      cardNumber: number.value.replace(/\s/g, ''),
      expiry: expiry.value.trim(),
      cvv: cvv.value.trim(),
      amount: amount.value.trim() === '' ? null : value,
    };
    const checks = [
      ['cardHolder', data.cardHolder.length >= 2, 'Enter the cardholder name'],
      ['cardHolder', Rules.personName(data.cardHolder), 'Cardholder name can only contain letters and spaces'],
      ['cardNumber', Rules.luhn(data.cardNumber), 'Enter a valid card number'],
      ['expiry', Rules.expiry(data.expiry), 'Enter a valid expiry date (MM/YY) that has not passed'],
      ['cvv', /^\d{3,4}$/.test(data.cvv), 'CVV must be 3 or 4 digits'],
      ['amount', data.amount !== null && isFinite(value), 'Enter an amount'],
      ['amount', data.amount === null || (value >= 10 && value <= 50000), 'Amount must be between $10 and $50,000'],
      ['amount', data.amount === null || /^\d+(\.\d{1,2})?$/.test(amount.value.trim()), 'Amount can have at most 2 decimal places'],
    ];
    if (!FormErrors.validate(form, checks)) return;

    const ok = await confirmDialog({
      title: 'Confirm payment',
      message: `Add ${Fmt.money(value)} to your account using the card ending in ${data.cardNumber.slice(-4)}?`,
      confirmText: `Pay ${Fmt.money(value)}`,
    });
    if (!ok) return;

    const restore = setLoading(document.getElementById('paySubmitBtn'), 'Processing…');
    try {
      const payment = await Api.post('/api/advertiser/payments', data);
      restore();
      toast(`${Fmt.money(payment.amount)} added using ${payment.method}.`, 'success');
      form.reset();
      updatePreview();
      refreshUnreadBadge();
      loadPayments();
    } catch (error) {
      restore();
      handleError(error, form);
    }
  });
}

/* ================================================================ notifications */

async function loadNotifications() {
  const list = document.getElementById('notificationList');
  try {
    const data = await Api.get('/api/advertiser/notifications');
    updateUnreadBadge(data.unreadCount);
    const icons = {
      success: ['✅', 'rgba(34,211,165,0.15)'], warning: ['⚠️', 'rgba(245,158,11,0.15)'], error: ['❌', 'rgba(244,63,94,0.15)'],
      pending: ['⏳', 'rgba(245,158,11,0.12)'], info: ['ℹ️', 'rgba(56,189,248,0.15)'],
    };
    list.innerHTML = data.items.length
      ? data.items.map((n) => {
        const [icon, bg] = icons[n.type] || icons.info;
        return `<div class="notification-item ${n.read ? '' : 'unread'}" data-action="read-notification" data-id="${n.id}" role="button" tabindex="0">
            <div class="notif-icon-wrap" style="background:${bg}" aria-hidden="true">${icon}</div>
            <div class="notif-body">
              <div class="notif-title">${esc(n.title)}</div>
              <div class="notif-desc">${esc(n.message)}</div>
              <div class="notif-time">${Fmt.timeAgo(n.createdAt)}</div>
            </div>
            ${n.read ? '' : '<span class="unread-dot" aria-label="Unread"></span>'}
          </div>`;
      }).join('')
      : emptyState('🔔', "You're all caught up", 'Approvals, budget alerts and support replies will show up here.');
    document.getElementById('markAllReadBtn').disabled = data.unreadCount === 0;
  } catch (error) {
    handleError(error);
  }
}

async function markNotificationRead(id, element) {
  if (!element.classList.contains('unread')) return;
  try {
    await Api.post(`/api/advertiser/notifications/${id}/read`);
    element.classList.remove('unread');
    element.querySelector('.unread-dot')?.remove();
    refreshUnreadBadge();
  } catch (error) {
    handleError(error);
  }
}

document.addEventListener('keydown', (event) => {
  if ((event.key === 'Enter' || event.key === ' ') && event.target.matches?.('.notification-item')) {
    event.preventDefault();
    event.target.click();
  }
});

async function markAllRead() {
  try {
    await Api.post('/api/advertiser/notifications/read-all');
    toast('All notifications marked as read.', 'success');
    loadNotifications();
  } catch (error) {
    handleError(error);
  }
}

async function refreshUnreadBadge() {
  try {
    const data = await Api.get('/api/advertiser/notifications/unread-count');
    updateUnreadBadge(data.unreadCount);
  } catch (error) {
    /* badge refresh is best-effort; auth errors are handled by the API client */
  }
}

function updateUnreadBadge(count) {
  const badge = document.getElementById('notifNavBadge');
  badge.textContent = count > 99 ? '99+' : String(count);
  badge.classList.toggle('hidden', count === 0);
  document.getElementById('notifDot').classList.toggle('hidden', count === 0);
}

/* ================================================================ profile */

async function loadProfileStats() {
  try {
    const [profile, wallet, campaigns] = await Promise.all([
      Api.get('/api/advertiser/profile'), Api.get('/api/advertiser/wallet'), Api.get('/api/advertiser/campaigns'),
    ]);
    const active = campaigns.filter((c) => c.status === 'active').length;
    document.getElementById('profileStats').innerHTML = `
      <div class="grid-2" style="gap:12px">
        <div class="mini-stat"><div class="mini-stat-value">${campaigns.length}</div><div class="mini-stat-label">Campaigns</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${active}</div><div class="mini-stat-label">Active now</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.moneyShort(wallet.totalSpent)}</div><div class="mini-stat-label">Total spent</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.moneyShort(wallet.available)}</div><div class="mini-stat-label">Available funds</div></div>
      </div>
      <p class="text-sm text-muted" style="margin-top:12px">Member since ${Fmt.date(profile.createdAt)}</p>`;
  } catch (error) {
    handleError(error);
  }
}

function bindProfileForms() {
  const profileForm = document.getElementById('profileForm');
  FormErrors.attachAutoClear(profileForm);
  profileForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = {
      name: document.getElementById('prof-name').value.trim(),
      email: document.getElementById('prof-email').value.trim(),
      company: document.getElementById('prof-company').value.trim(),
    };
    const checks = [
      ['name', data.name.length >= 2 && data.name.length <= 60, 'Name must be 2–60 characters'],
      ['name', Rules.personName(data.name), 'Name can only contain letters, spaces, apostrophes, periods and hyphens'],
      ['email', Rules.email(data.email), 'Enter a valid email address'],
    ];
    if (!FormErrors.validate(profileForm, checks)) return;
    const restore = setLoading(document.getElementById('profileSubmitBtn'), 'Saving…');
    try {
      const profile = await Api.put('/api/advertiser/profile', data);
      document.getElementById('sidebarName').textContent = profile.name;
      document.getElementById('sidebarEmail').textContent = profile.email;
      document.getElementById('sidebarAvatar').textContent = profile.initial;
      document.getElementById('prof-name').value = profile.name;
      document.getElementById('prof-email').value = profile.email;
      document.getElementById('prof-company').value = profile.company || '';
      toast('Profile updated.', 'success');
    } catch (error) {
      handleError(error, profileForm);
    } finally {
      restore();
    }
  });

  const passwordForm = document.getElementById('passwordForm');
  FormErrors.attachAutoClear(passwordForm);
  passwordForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = {
      currentPassword: document.getElementById('cur-pass').value,
      newPassword: document.getElementById('new-pass').value,
      confirmPassword: document.getElementById('conf-pass').value,
    };
    const checks = [
      ['currentPassword', data.currentPassword !== '', 'Enter your current password'],
      ['newPassword', Rules.password(data.newPassword), 'New password must be 6–72 characters with a letter and a number'],
      ['confirmPassword', data.confirmPassword === data.newPassword, 'New passwords do not match'],
    ];
    if (!FormErrors.validate(passwordForm, checks)) return;
    const restore = setLoading(document.getElementById('passwordSubmitBtn'), 'Updating…');
    try {
      await Api.put('/api/advertiser/profile/password', data);
      passwordForm.reset();
      toast('Password updated.', 'success');
    } catch (error) {
      handleError(error, passwordForm);
    } finally {
      restore();
    }
  });
}

/* ================================================================ support */

async function loadTickets() {
  const list = document.getElementById('myTicketsList');
  try {
    const tickets = await Api.get('/api/advertiser/tickets');
    list.innerHTML = tickets.length
      ? tickets.map((t) => `
        <div class="ticket-card">
          <div class="ticket-header">
            <span class="ticket-id">${esc(t.reference)}</span>
            <div class="flex gap-sm">${priorityBadge(t.priority)}${statusBadge(t.status)}</div>
          </div>
          <div class="ticket-subject">${esc(t.subject)}</div>
          <div class="ticket-preview">${esc(t.message)}</div>
          ${t.adminReply ? `<div class="ticket-reply"><strong>Support reply:</strong><br>${esc(t.adminReply)}</div>` : ''}
          <div class="text-sm text-muted">Opened ${Fmt.timeAgo(t.createdAt)}${t.resolvedAt ? ` · resolved ${Fmt.timeAgo(t.resolvedAt)}` : ''}</div>
        </div>`).join('')
      : emptyState('🎫', 'No tickets yet', 'Need help? Submit a ticket and our team will reply here.');
  } catch (error) {
    handleError(error);
  }
}

function bindSupportForm() {
  const form = document.getElementById('supportForm');
  const message = document.getElementById('ticketMessage');
  FormErrors.attachAutoClear(form);
  message.addEventListener('input', () => { document.getElementById('ticketCount').textContent = message.value.length; });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = {
      subject: document.getElementById('ticketSubject').value.trim(),
      priority: document.getElementById('ticketPriority').value,
      message: message.value.trim(),
    };
    const checks = [
      ['subject', data.subject.length >= 5 && data.subject.length <= 120, 'Subject must be 5–120 characters'],
      ['message', data.message.length >= 10 && data.message.length <= 2000, 'Message must be 10–2000 characters'],
    ];
    if (!FormErrors.validate(form, checks)) return;
    const restore = setLoading(document.getElementById('ticketSubmitBtn'), 'Submitting…');
    try {
      const ticket = await Api.post('/api/advertiser/tickets', data);
      form.reset();
      document.getElementById('ticketCount').textContent = '0';
      toast(`Ticket ${ticket.reference} submitted. We'll reply here.`, 'success');
      loadTickets();
    } catch (error) {
      handleError(error, form);
    } finally {
      restore();
    }
  });
}
