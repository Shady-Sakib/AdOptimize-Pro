/* =====================================================================
   Admin panel — dashboard, analytics, users, ad review, moderation,
   revenue, reports, complaints and settings. Data from /api/admin.
   ===================================================================== */
'use strict';

const ADMIN_SECTIONS = {
  dashboard: { title: 'Admin Dashboard', crumb: 'Dashboard', load: loadDashboard },
  analytics: { title: 'Analytics', crumb: 'Analytics', load: loadAnalytics },
  users: { title: 'Users', crumb: 'Users', load: loadUsers },
  ads: { title: 'Ad Management', crumb: 'Ad Management', load: loadAds },
  moderation: { title: 'Moderation', crumb: 'Moderation', load: loadModeration },
  revenue: { title: 'Revenue', crumb: 'Revenue', load: loadRevenue },
  reports: { title: 'Reports', crumb: 'Reports', load: loadReportSummary },
  complaints: { title: 'Complaints', crumb: 'Complaints', load: loadComplaints },
  settings: { title: 'Settings', crumb: 'Settings', load: loadSettings },
};

const AD_ICON = { image: '🖼️', video: '🎬', text: '📝', carousel: '🎠' };
const AUDIENCE_COLORS = ['#6c63ff', '#00d4ff', '#22d3a5', '#f59e0b', '#f43f5e', '#a78bfa'];

const adminState = {
  section: 'dashboard',
  campaigns: new Map(),
  tickets: new Map(),
  users: new Map(),
  replyTicketId: null,
  openUserId: null,
};

document.addEventListener('DOMContentLoaded', () => {
  setupSidebar();
  bindAdminActions();
  bindSettingsForm();
  bindReplyForm();

  document.getElementById('userSearch').addEventListener('input', debounce(loadUsers, 300));
  document.getElementById('userFilter').addEventListener('change', loadUsers);
  document.getElementById('adsFilter').addEventListener('change', loadAds);
  document.getElementById('complaintsFilter').addEventListener('change', loadComplaints);

  const initial = location.hash.replace('#', '');
  showAdminSection(ADMIN_SECTIONS[initial] ? initial : 'dashboard');
  setInterval(() => {
    if (!document.hidden && adminState.section === 'dashboard' && !document.querySelector('.modal-overlay:not(.hidden)')) loadDashboard(true);
  }, 30000);
});

/* ================================================================ navigation & actions */

function bindAdminActions() {
  document.addEventListener('click', (event) => {
    const link = event.target.closest('[data-section]');
    if (link) {
      event.preventDefault();
      showAdminSection(link.dataset.section);
      return;
    }
    const action = event.target.closest('[data-action]');
    if (!action) return;
    if (action.tagName === 'A') event.preventDefault();
    const id = Number(action.dataset.id);
    switch (action.dataset.action) {
      case 'logout': logoutUser(); break;
      case 'refresh': ADMIN_SECTIONS[adminState.section].load(); break;
      case 'approve': approveCampaign(id, action); break;
      case 'reject': rejectCampaign(id, action); break;
      case 'remove': removeCampaign(id, action); break;
      case 'clear-flag': clearFlag(id, action); break;
      case 'scan': rescan(action); break;
      case 'view-user': openUser(id); break;
      case 'toggle-user': toggleUser(id, action.dataset.active === 'true', action); break;
      case 'reply': openReply(id); break;
      case 'reopen': reopenTicket(id, action); break;
      case 'download-report': downloadReport(action.dataset.type, action); break;
      case 'run-simulation': runSimulation(action); break;
      case 'reset-settings': loadSettings(); toast('Changes discarded.', 'info'); break;
      default: break;
    }
  });
}

function showAdminSection(name) {
  if (!ADMIN_SECTIONS[name]) return;
  adminState.section = name;
  Object.keys(ADMIN_SECTIONS).forEach((key) => {
    document.getElementById(`section-${key}`).classList.toggle('hidden', key !== name);
  });
  document.querySelectorAll('.nav-item[data-section]').forEach((item) => {
    const active = item.dataset.section === name;
    item.classList.toggle('active', active);
    if (active) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  });
  document.getElementById('topbarTitle').textContent = ADMIN_SECTIONS[name].title;
  document.getElementById('topbarBreadcrumb').textContent = `Admin › ${ADMIN_SECTIONS[name].crumb}`;
  if (location.hash !== `#${name}`) history.replaceState(null, '', `#${name}`);
  window.scrollTo({ top: 0 });
  ADMIN_SECTIONS[name].load();
}

function setBadge(id, count) {
  const badge = document.getElementById(id);
  if (!badge) return;
  badge.textContent = count > 99 ? '99+' : String(count);
  badge.classList.toggle('hidden', !count);
}

function statCard(icon, background, value, label, sub) {
  return `<div class="stat-card">
      <div class="stat-icon" style="background:${background}" aria-hidden="true">${icon}</div>
      <div class="stat-value">${esc(value)}</div>
      <div class="stat-label">${esc(label)}</div>
      ${sub ? `<div class="stat-change">${esc(sub)}</div>` : ''}
    </div>`;
}

/* ================================================================ dashboard */

async function loadDashboard(silent = false) {
  try {
    const d = await Api.get('/api/admin/dashboard');
    setBadge('pendingAdsBadge', d.pendingApprovals);
    setBadge('openTicketsBadge', d.openTickets);
    document.getElementById('lastUpdated').textContent = `Updated ${new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}`;
    document.getElementById('adminStatsGrid').innerHTML = `
      ${statCard('👥', 'rgba(108,99,255,0.15)', Fmt.integer(d.totalUsers), 'Advertisers', `${d.activeUsers} active`)}
      ${statCard('📢', 'rgba(0,212,255,0.15)', Fmt.integer(d.activeCampaigns), 'Active campaigns', `${d.totalCampaigns} in total`)}
      ${statCard('⏳', 'rgba(245,158,11,0.15)', Fmt.integer(d.pendingApprovals), 'Pending approvals', `${d.openTickets} open support tickets`)}
      ${statCard('💰', 'rgba(34,211,165,0.15)', Fmt.moneyShort(d.totalRevenue), 'Total payments received', 'All completed payments')}`;

    Charts.bar(document.getElementById('adminImprChart'), d.impressionsTrend.labels.map((l) => l.split(' ')[0]),
      [{ label: 'Impressions', data: d.impressionsTrend.impressions, color: '#6c63ff' }],
      { emptyMessage: 'No impressions yet' });
    Charts.line(document.getElementById('adminRevChart'), d.revenueTrend.labels.map((l) => l.split(' ')[0]),
      [{ label: 'Payments', data: d.revenueTrend.revenue, color: '#22d3a5' }],
      { formatY: (v) => '$' + Charts_short(v), formatValue: Fmt.money, emptyMessage: 'No payments yet' });

    d.pendingCampaigns.forEach((c) => adminState.campaigns.set(c.id, c));
    document.getElementById('pendingApprovalsList').innerHTML = d.pendingCampaigns.length
      ? d.pendingCampaigns.map((c) => `
        <div class="approval-item">
          <div class="approval-item-header">
            <div class="flex gap-md items-center" style="min-width:0;flex:1">
              <div class="approval-thumb" aria-hidden="true">${AD_ICON[c.adType] || '📢'}</div>
              <div class="approval-meta">
                <div class="approval-title">${esc(c.title)} ${c.flagged ? '<span class="flag-badge" title="Flagged by content filter">⚠️</span>' : ''}</div>
                <div class="approval-info">${esc(c.ownerName)} · ${Fmt.money(c.budget)} · submitted ${Fmt.timeAgo(c.createdAt)}</div>
              </div>
            </div>
            <div class="action-group">
              <button class="btn btn-success btn-sm" data-action="approve" data-id="${c.id}">✓ Approve</button>
              <button class="btn btn-danger btn-sm" data-action="reject" data-id="${c.id}">✕ Reject</button>
            </div>
          </div>
        </div>`).join('')
      : emptyState('✅', 'All caught up', 'No campaigns are waiting for review.');

    d.recentOpenTickets.forEach((t) => adminState.tickets.set(t.id, t));
    document.getElementById('openTicketsList').innerHTML = d.recentOpenTickets.length
      ? d.recentOpenTickets.map((t) => `
        <div class="ticket-card">
          <div class="ticket-header"><span class="ticket-id">${esc(t.reference)}</span>${priorityBadge(t.priority)}</div>
          <div class="ticket-subject">${esc(t.subject)}</div>
          <div class="text-sm text-muted" style="margin-bottom:8px">${esc(t.userName)} · ${Fmt.timeAgo(t.createdAt)}</div>
          <button class="btn btn-ghost btn-sm" data-action="reply" data-id="${t.id}">↩ Reply</button>
        </div>`).join('')
      : emptyState('🎉', 'No open tickets', 'Every support request has been answered.');
  } catch (error) {
    if (!silent) handleError(error);
  }
}

function Charts_short(value) {
  const n = Math.abs(value);
  if (n >= 1e6) return (value / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
  if (n >= 1e3) return (value / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
  return String(Math.round(value));
}

/* ================================================================ analytics */

async function loadAnalytics() {
  try {
    const a = await Api.get('/api/admin/analytics');
    const conversionRate = a.clicks ? (a.conversions * 100) / a.clicks : 0;
    document.getElementById('analyticsStatsGrid').innerHTML = `
      ${statCard('👁️', 'rgba(108,99,255,0.15)', Fmt.number(a.impressions), 'Total impressions', 'Across all campaigns')}
      ${statCard('🖱️', 'rgba(0,212,255,0.15)', Fmt.number(a.clicks), 'Total clicks', '')}
      ${statCard('🎯', 'rgba(34,211,165,0.15)', Fmt.percent(a.ctr), 'Average CTR', '')}
      ${statCard('🛒', 'rgba(245,158,11,0.15)', Fmt.number(a.conversions), 'Conversions', `${Fmt.percent(conversionRate)} of clicks`)}`;
    Charts.line(document.getElementById('analyticsLineChart'), a.monthlyTrend.labels.map((l) => l.split(' ')[0]), [
      { label: 'Impressions', data: a.monthlyTrend.impressions, color: '#6c63ff' },
      { label: 'Clicks (right axis)', data: a.monthlyTrend.clicks, color: '#00d4ff', axis: 'right', fill: false },
    ], { emptyMessage: 'No delivery yet' });
    Charts.doughnut(document.getElementById('audienceDonut'), a.audiences.map((x) => x.label), a.audiences.map((x) => x.impressions), {
      colors: AUDIENCE_COLORS,
      centerText: Fmt.number(a.impressions),
      centerLabel: 'impressions',
      showPercent: true,
    });
    Charts.bar(document.getElementById('revenueBarChart'), a.revenueVsSpend.labels.map((l) => l.split(' ')[0]), [
      { label: 'Payments', data: a.revenueVsSpend.revenue, color: '#22d3a5' },
      { label: 'Ad spend', data: a.revenueVsSpend.adSpend, color: '#6c63ff' },
    ], { formatY: (v) => '$' + Charts_short(v), formatValue: Fmt.money, emptyMessage: 'No money movement yet' });
  } catch (error) {
    handleError(error);
  }
}

/* ================================================================ users */

async function loadUsers() {
  const body = document.getElementById('usersTableBody');
  const q = document.getElementById('userSearch').value.trim();
  const status = document.getElementById('userFilter').value;
  try {
    const users = await Api.get(`/api/admin/users?status=${encodeURIComponent(status)}${q ? `&q=${encodeURIComponent(q)}` : ''}`);
    users.forEach((u) => adminState.users.set(u.id, u));
    body.innerHTML = users.length
      ? users.map((u) => `
        <tr>
          <td>
            <div class="flex items-center gap-sm">
              <div class="user-avatar" style="width:32px;height:32px;font-size:13px;background:var(--grad-primary)" aria-hidden="true">${esc(u.initial)}</div>
              <div style="min-width:0"><a href="#" data-action="view-user" data-id="${u.id}" class="font-bold">${esc(u.name)}</a><div class="text-sm text-muted">${esc(u.email)}</div></div>
            </div>
          </td>
          <td>${u.company ? esc(u.company) : '<span class="text-muted">—</span>'}</td>
          <td>${Fmt.date(u.createdAt)}</td>
          <td>${Fmt.integer(u.campaignCount)}</td>
          <td>${Fmt.money(u.totalSpent)}</td>
          <td><span class="badge ${u.active ? 'badge-success' : 'badge-danger'}">${u.active ? 'Active' : 'Deactivated'}</span></td>
          <td>
            <div class="action-group">
              <button class="btn btn-ghost btn-sm" data-action="view-user" data-id="${u.id}">View</button>
              <button class="btn ${u.active ? 'btn-danger' : 'btn-success'} btn-sm" data-action="toggle-user" data-id="${u.id}" data-active="${!u.active}">${u.active ? 'Deactivate' : 'Activate'}</button>
            </div>
          </td>
        </tr>`).join('')
      : `<tr><td colspan="7">${emptyState('🔍', 'No users found', q ? 'Try a different search term.' : 'No advertisers match this filter.')}</td></tr>`;
  } catch (error) {
    handleError(error);
  }
}

async function toggleUser(id, activate, button) {
  const user = adminState.users.get(id);
  const name = user ? user.name : 'this advertiser';
  if (!activate) {
    const ok = await confirmDialog({
      title: 'Deactivate account?',
      message: `${name} will be signed out immediately and won't be able to sign in until the account is reactivated. Their campaigns are not deleted.`,
      confirmText: 'Deactivate',
      danger: true,
    });
    if (!ok) return;
  }
  const restore = setLoading(button, activate ? 'Activating…' : 'Deactivating…');
  try {
    const updated = await Api.patch(`/api/admin/users/${id}/status`, { active: activate });
    toast(`${updated.name} ${updated.active ? 'activated' : 'deactivated'}.`, 'success');
    if (adminState.openUserId === id) openUser(id);
    loadUsers();
  } catch (error) {
    restore();
    handleError(error);
  }
}

async function openUser(id) {
  try {
    const d = await Api.get(`/api/admin/users/${id}`);
    adminState.openUserId = id;
    const u = d.user;
    adminState.users.set(u.id, u);
    d.campaigns.forEach((c) => adminState.campaigns.set(c.id, c));
    document.getElementById('userModalTitle').textContent = u.name;
    document.getElementById('userModalBody').innerHTML = `
      <div class="flex justify-between items-center gap-md" style="flex-wrap:wrap;margin-bottom:var(--space-lg)">
        <div>
          <div class="text-secondary">${esc(u.email)}${u.company ? ' · ' + esc(u.company) : ''}</div>
          <div class="text-sm text-muted">Joined ${Fmt.date(u.createdAt)} · Last sign-in ${u.lastLoginAt ? Fmt.timeAgo(u.lastLoginAt) : 'never'}</div>
        </div>
        <div class="flex gap-sm items-center">
          <span class="badge ${u.active ? 'badge-success' : 'badge-danger'}">${u.active ? 'Active' : 'Deactivated'}</span>
          <button class="btn ${u.active ? 'btn-danger' : 'btn-success'} btn-sm" data-action="toggle-user" data-id="${u.id}" data-active="${!u.active}">${u.active ? 'Deactivate' : 'Activate'}</button>
        </div>
      </div>
      <div class="grid-3" style="gap:12px;margin-bottom:var(--space-lg)">
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.money(d.wallet.balance)}</div><div class="mini-stat-label">Balance</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.money(d.wallet.totalSpent)}</div><div class="mini-stat-label">Total spent</div></div>
        <div class="mini-stat"><div class="mini-stat-value">${Fmt.money(d.wallet.available)}</div><div class="mini-stat-label">Available</div></div>
      </div>
      <h3 class="card-title" style="font-size:14px;margin-bottom:8px">Campaigns (${d.campaigns.length})</h3>
      ${d.campaigns.length ? `<div class="table-wrapper" style="margin-bottom:var(--space-lg)"><table class="data-table">
        <thead><tr><th>Title</th><th>Status</th><th>Budget</th><th>Spent</th><th>CTR</th></tr></thead>
        <tbody>${d.campaigns.map((c) => `<tr><td>${esc(c.title)}</td><td>${statusBadge(c.status)}</td><td>${Fmt.money(c.budget)}</td><td>${Fmt.money(c.spent)}</td><td>${Fmt.percent(c.ctr)}</td></tr>`).join('')}</tbody>
      </table></div>` : '<p class="text-muted" style="margin-bottom:var(--space-lg)">No campaigns.</p>'}
      <div class="grid-2" style="gap:var(--space-lg)">
        <div>
          <h3 class="card-title" style="font-size:14px;margin-bottom:8px">Recent payments</h3>
          ${d.payments.length ? d.payments.map((p) => `<div class="quick-stat-row" style="margin-bottom:6px"><span class="text-sm">${Fmt.date(p.createdAt)} · ${esc(p.method)}</span><span class="font-bold" style="color:var(--success)">${Fmt.money(p.amount)}</span></div>`).join('') : '<p class="text-muted text-sm">No payments.</p>'}
        </div>
        <div>
          <h3 class="card-title" style="font-size:14px;margin-bottom:8px">Support tickets</h3>
          ${d.tickets.length ? d.tickets.map((t) => `<div class="quick-stat-row" style="margin-bottom:6px"><span class="text-sm" style="min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(t.subject)}</span>${statusBadge(t.status)}</div>`).join('') : '<p class="text-muted text-sm">No tickets.</p>'}
        </div>
      </div>`;
    const overlay = document.getElementById('userModal');
    if (overlay.classList.contains('hidden')) {
      overlay._onClose = () => { adminState.openUserId = null; };
      Modal.open('userModal');
    }
  } catch (error) {
    handleError(error);
  }
}

/* ================================================================ ads */

async function loadAds() {
  const container = document.getElementById('adsContainer');
  const status = document.getElementById('adsFilter').value;
  try {
    const campaigns = await Api.get(`/api/admin/campaigns?status=${encodeURIComponent(status)}`);
    campaigns.forEach((c) => adminState.campaigns.set(c.id, c));
    if (status === 'pending' || status === 'all') setBadge('pendingAdsBadge', campaigns.filter((c) => c.status === 'pending').length);
    container.innerHTML = campaigns.length
      ? campaigns.map(adItem).join('')
      : emptyState('📭', status === 'pending' ? 'No ads waiting for review' : 'No ads found', 'Try another filter.');
  } catch (error) {
    handleError(error);
  }
}

function adItem(c) {
  const canApprove = c.status === 'pending';
  const canReject = ['pending', 'scheduled', 'active', 'paused'].includes(c.status);
  return `
    <div class="approval-item">
      <div class="approval-item-header">
        <div class="flex gap-md" style="min-width:0;flex:1">
          <div class="approval-thumb" aria-hidden="true">${AD_ICON[c.adType] || '📢'}</div>
          <div class="approval-meta" style="flex:1">
            <div class="flex items-center gap-sm" style="flex-wrap:wrap">
              <span class="approval-title">${esc(c.title)}</span>${statusBadge(c.status)}
              ${c.flagged ? `<span class="badge badge-warning" title="${esc(c.flagReason)}">⚠️ Flagged</span>` : ''}
            </div>
            <div class="approval-info">${esc(c.ownerName)} (${esc(c.ownerEmail)}) · ${esc(c.audienceLabel)} · ${esc(c.adTypeLabel)}</div>
            <div class="approval-info">${Fmt.money(c.budget)} budget · ${Fmt.money(c.dailyBudget)}/day · ${Fmt.date(c.startDate)} – ${Fmt.date(c.endDate)} · submitted ${Fmt.timeAgo(c.createdAt)}</div>
            <p class="text-secondary" style="margin-top:8px;white-space:pre-line;overflow-wrap:anywhere">${esc(c.description)}</p>
            ${c.keywords.length ? `<div style="margin-top:6px">${c.keywords.map((k) => `<span class="badge badge-primary" style="margin:2px 4px 2px 0">${esc(k)}</span>`).join('')}</div>` : ''}
            ${c.flagged ? `<div class="campaign-notice warning" style="margin:10px 0 0">⚠️ ${esc(c.flagReason)}</div>` : ''}
            ${c.rejectionReason ? `<div class="campaign-notice danger" style="margin:10px 0 0">Rejection reason: ${esc(c.rejectionReason)}</div>` : ''}
            ${c.impressions ? `<div class="approval-info" style="margin-top:6px">${Fmt.integer(c.impressions)} impressions · ${Fmt.integer(c.clicks)} clicks · ${Fmt.percent(c.ctr)} CTR · ${Fmt.money(c.spent)} spent</div>` : ''}
          </div>
        </div>
        <div class="action-group">
          ${canApprove ? `<button class="btn btn-success btn-sm" data-action="approve" data-id="${c.id}">✓ Approve</button>` : ''}
          ${canReject ? `<button class="btn btn-danger btn-sm" data-action="reject" data-id="${c.id}">✕ Reject</button>` : ''}
          <button class="btn btn-ghost btn-sm" data-action="remove" data-id="${c.id}">🗑 Remove</button>
        </div>
      </div>
    </div>`;
}

async function approveCampaign(id, button) {
  const campaign = adminState.campaigns.get(id);
  if (campaign?.flagged) {
    const ok = await confirmDialog({
      title: 'Approve flagged campaign?',
      message: `The content filter flagged "${campaign.title}" (${campaign.flagReason}). Approve it anyway?`,
      confirmText: 'Approve anyway',
      danger: true,
    });
    if (!ok) return;
  }
  const restore = setLoading(button, 'Approving…');
  try {
    const c = await Api.post(`/api/admin/campaigns/${id}/approve`);
    toast(`"${c.title}" approved${c.status === 'scheduled' ? ` — starts ${Fmt.date(c.startDate)}` : ' and live'}.`, 'success');
    ADMIN_SECTIONS[adminState.section].load();
  } catch (error) {
    restore();
    handleError(error);
  }
}

async function rejectCampaign(id, button) {
  const campaign = adminState.campaigns.get(id);
  const reason = await dialog({
    title: 'Reject campaign',
    message: campaign ? `"${campaign.title}" by ${campaign.ownerName}. The advertiser sees this reason and can edit and resubmit.` : '',
    confirmText: 'Reject campaign',
    danger: true,
    input: {
      label: 'Reason (optional)',
      placeholder: 'e.g. Add clear pricing details to the description',
      maxLength: 300,
      validate: (value) => (value.length > 300 ? 'Reason must be at most 300 characters' : null),
    },
  });
  if (reason === null) return;
  const restore = setLoading(button, 'Rejecting…');
  try {
    const c = await Api.post(`/api/admin/campaigns/${id}/reject`, { reason });
    toast(`"${c.title}" rejected.`, 'success');
    ADMIN_SECTIONS[adminState.section].load();
  } catch (error) {
    restore();
    handleError(error);
  }
}

async function removeCampaign(id, button) {
  const campaign = adminState.campaigns.get(id);
  const ok = await confirmDialog({
    title: 'Remove campaign?',
    message: `"${campaign ? campaign.title : 'This campaign'}" will stop delivering and disappear from the platform. The advertiser is notified.`,
    confirmText: 'Remove',
    danger: true,
  });
  if (!ok) return;
  const restore = setLoading(button, 'Removing…');
  try {
    const result = await Api.del(`/api/admin/campaigns/${id}`);
    toast(result.message, 'success');
    ADMIN_SECTIONS[adminState.section].load();
  } catch (error) {
    restore();
    handleError(error);
  }
}

/* ================================================================ moderation */

async function loadModeration() {
  const container = document.getElementById('moderationContent');
  try {
    const m = await Api.get('/api/admin/moderation');
    m.flaggedCampaigns.forEach((c) => adminState.campaigns.set(c.id, c));
    setBadge('flaggedBadge', m.flaggedOpen);
    container.innerHTML = `
      <div class="stats-grid">
        ${statCard('🚩', 'rgba(244,63,94,0.15)', Fmt.integer(m.flaggedOpen), 'Flagged, awaiting review', '')}
        ${statCard('✅', 'rgba(34,211,165,0.15)', Fmt.integer(m.clean), 'Clean campaigns', '')}
        ${statCard('👀', 'rgba(56,189,248,0.15)', Fmt.integer(m.reviewedToday), 'Reviewed today', 'Approvals and rejections')}
        ${statCard('🤖', 'rgba(245,158,11,0.15)', Fmt.integer(m.autoFlaggedTotal), 'Currently flagged by filter', 'Including cleared flags')}
      </div>
      <div class="grid-2" style="align-items:start">
        <div class="card">
          <div class="card-header"><h3 class="card-title">🚩 Flagged campaigns</h3></div>
          ${m.flaggedCampaigns.length ? m.flaggedCampaigns.map((c) => `
            <div class="approval-item">
              <div class="approval-item-header">
                <div class="approval-meta" style="flex:1">
                  <div class="flex items-center gap-sm" style="flex-wrap:wrap"><span class="approval-title">${esc(c.title)}</span>${statusBadge(c.status)}</div>
                  <div class="approval-info">${esc(c.ownerName)} · <span class="flag-badge">${esc(c.flagReason)}</span></div>
                  <p class="text-secondary text-sm" style="margin-top:6px;overflow-wrap:anywhere">${esc(c.description)}</p>
                </div>
                <div class="action-group">
                  <button class="btn btn-success btn-sm" data-action="clear-flag" data-id="${c.id}">Clear flag</button>
                  ${c.status === 'pending' ? `<button class="btn btn-danger btn-sm" data-action="reject" data-id="${c.id}">Reject</button>` : ''}
                  <button class="btn btn-ghost btn-sm" data-action="remove" data-id="${c.id}">Remove</button>
                </div>
              </div>
            </div>`).join('') : emptyState('🛡️', 'No flagged content', 'The content filter has not flagged any campaign.')}
        </div>
        <div class="card">
          <div class="card-header"><h3 class="card-title">📜 Content policy</h3>
            <span class="badge ${m.contentFilterEnabled ? 'badge-success' : 'badge-danger'}">Filter ${m.contentFilterEnabled ? 'on' : 'off'}</span></div>
          <p class="text-secondary text-sm" style="margin-bottom:12px">Campaign titles, descriptions and keywords containing these phrases are flagged for manual review. Edit the list in Settings.</p>
          <div>${m.bannedWords.length ? m.bannedWords.map((w) => `<span class="word-chip">${esc(w)}</span>`).join('') : '<span class="text-muted">No blocked words configured.</span>'}</div>
          <hr class="divider">
          <div style="display:flex;flex-direction:column;gap:8px">
            <div class="policy-rule">✅ Clear pricing and offer terms</div>
            <div class="policy-rule">✅ No misleading health or income claims</div>
            <div class="policy-rule">✅ Audience-appropriate content</div>
            <div class="policy-rule">✅ Accurate schedule and landing page</div>
          </div>
        </div>
      </div>`;
  } catch (error) {
    handleError(error);
  }
}

async function clearFlag(id, button) {
  const restore = setLoading(button, 'Clearing…');
  try {
    const result = await Api.post(`/api/admin/campaigns/${id}/clear-flag`);
    toast(result.message, 'success');
    loadModeration();
  } catch (error) {
    restore();
    handleError(error);
  }
}

async function rescan(button) {
  const restore = setLoading(button, 'Scanning…');
  try {
    const result = await Api.post('/api/admin/moderation/scan');
    toast(result.message, 'success');
    loadModeration();
  } catch (error) {
    handleError(error);
  } finally {
    restore();
  }
}

/* ================================================================ revenue */

async function loadRevenue() {
  const container = document.getElementById('revenueContent');
  try {
    const r = await Api.get('/api/admin/revenue');
    container.innerHTML = `
      <div class="grid-3" style="margin-bottom:var(--space-xl)">
        <div class="revenue-highlight"><div class="text-sm text-muted">Total payments received</div><div class="revenue-amount">${Fmt.money(r.totalRevenue)}</div><div class="revenue-period">All time</div></div>
        <div class="revenue-highlight"><div class="text-sm text-muted">Platform earnings</div><div class="revenue-amount">${Fmt.money(r.platformEarnings)}</div><div class="revenue-period">${Number(r.platformFee).toFixed(2)}% platform fee</div></div>
        <div class="revenue-highlight"><div class="text-sm text-muted">Transactions</div><div class="revenue-amount">${Fmt.integer(r.transactionCount)}</div><div class="revenue-period">Completed payments</div></div>
      </div>
      <div class="card" style="margin-bottom:var(--space-xl)">
        <div class="card-header"><h3 class="card-title">Monthly payments vs ad spend</h3></div>
        <div class="chart-container" style="height:220px"><canvas id="revenueMonthlyChart" role="img" aria-label="Monthly payments and ad spend"></canvas></div>
      </div>
      <div class="card" style="padding:0">
        <div class="card-header" style="padding:var(--space-lg) var(--space-lg) 0"><h3 class="card-title">Transactions</h3></div>
        ${r.payments.length ? `<div class="table-wrapper"><table class="data-table">
          <thead><tr><th>Reference</th><th>Advertiser</th><th>Amount</th><th>Method</th><th>Date</th><th>Status</th></tr></thead>
          <tbody>${r.payments.map((p) => `<tr>
            <td class="ticket-id">${esc(p.reference)}</td>
            <td><div class="font-bold">${esc(p.userName)}</div><div class="text-sm text-muted">${esc(p.userEmail)}</div></td>
            <td class="font-bold" style="color:var(--success)">${Fmt.money(p.amount)}</td>
            <td>${esc(p.method)}</td>
            <td>${Fmt.dateTime(p.createdAt)}</td>
            <td><span class="badge badge-success">${esc(Fmt.capitalize(p.status))}</span></td>
          </tr>`).join('')}</tbody></table></div>`
        : emptyState('💳', 'No payments yet', 'Payments from advertisers will appear here.')}
      </div>`;
    Charts.bar(document.getElementById('revenueMonthlyChart'), r.monthly.labels.map((l) => l.split(' ')[0]), [
      { label: 'Payments', data: r.monthly.revenue, color: '#22d3a5' },
      { label: 'Ad spend', data: r.monthly.adSpend, color: '#6c63ff' },
    ], { formatY: (v) => '$' + Charts_short(v), formatValue: Fmt.money });
  } catch (error) {
    handleError(error);
  }
}

/* ================================================================ reports */

async function loadReportSummary() {
  try {
    const s = await Api.get('/api/admin/reports/summary');
    const row = (label, value) => `<div class="quick-stat-row"><span class="text-secondary">${esc(label)}</span><span class="font-bold">${esc(value)}</span></div>`;
    document.getElementById('reportSummary').innerHTML =
      row('Advertisers', Fmt.integer(s.totalUsers)) +
      row('Total campaigns', Fmt.integer(s.totalCampaigns)) +
      row('Active campaigns', Fmt.integer(s.activeCampaigns)) +
      row('Pending reviews', Fmt.integer(s.pendingReviews)) +
      row('Payments', Fmt.integer(s.totalPayments)) +
      row('Open tickets', Fmt.integer(s.openTickets)) +
      row('Optimizations applied', Fmt.integer(s.optimizationsApplied));
  } catch (error) {
    handleError(error);
  }
}

async function downloadReport(type, button) {
  const restore = setLoading(button, 'Generating…');
  try {
    await Api.download(`/api/admin/reports/${encodeURIComponent(type)}`, `${type}_report.csv`);
    toast('Report downloaded.', 'success');
  } catch (error) {
    handleError(error);
  } finally {
    restore();
  }
}

/* ================================================================ complaints */

async function loadComplaints() {
  const container = document.getElementById('complaintsContainer');
  const status = document.getElementById('complaintsFilter').value;
  try {
    const tickets = await Api.get(`/api/admin/tickets?status=${encodeURIComponent(status)}`);
    tickets.forEach((t) => adminState.tickets.set(t.id, t));
    if (status === 'open') setBadge('openTicketsBadge', tickets.length);
    container.innerHTML = tickets.length
      ? tickets.map((t) => `
        <div class="ticket-card">
          <div class="ticket-header">
            <span class="ticket-id">${esc(t.reference)}</span>
            <div class="flex gap-sm">${priorityBadge(t.priority)}${statusBadge(t.status)}</div>
          </div>
          <div class="ticket-subject">${esc(t.subject)}</div>
          <div class="text-sm text-muted" style="margin-bottom:6px">${esc(t.userName)} (${esc(t.userEmail)}) · ${Fmt.dateTime(t.createdAt)}</div>
          <div class="ticket-preview">${esc(t.message)}</div>
          ${t.adminReply ? `<div class="ticket-reply"><strong>Reply${t.resolvedAt ? ' · ' + Fmt.dateTime(t.resolvedAt) : ''}:</strong><br>${esc(t.adminReply)}</div>` : ''}
          <div class="action-group">
            ${t.status === 'open'
              ? `<button class="btn btn-success btn-sm" data-action="reply" data-id="${t.id}">↩ Reply &amp; resolve</button>`
              : `<button class="btn btn-ghost btn-sm" data-action="reopen" data-id="${t.id}">↺ Reopen</button>`}
          </div>
        </div>`).join('')
      : emptyState('🎫', status === 'open' ? 'No open tickets' : 'No tickets', 'Support requests from advertisers appear here.');
  } catch (error) {
    handleError(error);
  }
}

function openReply(id) {
  const ticket = adminState.tickets.get(id);
  if (!ticket) return;
  adminState.replyTicketId = id;
  document.getElementById('replyModalTitle').textContent = `Reply to ${ticket.reference}`;
  document.getElementById('replyTicketInfo').innerHTML = `
    <div class="ticket-card" style="margin:0">
      <div class="ticket-subject">${esc(ticket.subject)}</div>
      <div class="text-sm text-muted" style="margin-bottom:6px">${esc(ticket.userName)} · ${priorityBadge(ticket.priority)}</div>
      <div class="ticket-preview" style="margin:0">${esc(ticket.message)}</div>
    </div>`;
  document.getElementById('replyText').value = '';
  Modal.open('replyModal');
}

function bindReplyForm() {
  const form = document.getElementById('replyForm');
  FormErrors.attachAutoClear(form);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const reply = document.getElementById('replyText').value.trim();
    if (!FormErrors.validate(form, [['reply', reply.length >= 5 && reply.length <= 2000, 'Response must be 5–2000 characters']])) return;
    const restore = setLoading(document.getElementById('replySubmitBtn'), 'Sending…');
    try {
      const ticket = await Api.post(`/api/admin/tickets/${adminState.replyTicketId}/resolve`, { reply });
      restore();
      Modal.close('replyModal');
      toast(`${ticket.reference} resolved. The advertiser has been notified.`, 'success');
      ADMIN_SECTIONS[adminState.section].load();
    } catch (error) {
      restore();
      handleError(error, form);
    }
  });
}

async function reopenTicket(id, button) {
  const restore = setLoading(button, 'Reopening…');
  try {
    const ticket = await Api.post(`/api/admin/tickets/${id}/reopen`);
    toast(`${ticket.reference} reopened.`, 'success');
    loadComplaints();
  } catch (error) {
    restore();
    handleError(error);
  }
}

/* ================================================================ settings */

const SETTING_INPUTS = {
  cpcRate: 'set-cpc', cpmRate: 'set-cpm', platformFee: 'set-fee', minBudget: 'set-min-budget',
  maxDailyBudget: 'set-max-daily', peakHoursStart: 'set-peak-start', peakHoursEnd: 'set-peak-end', bannedWords: 'set-banned',
};
const SETTING_TOGGLES = {
  autoApprove: 'set-auto-approve', contentFilter: 'set-content-filter', budgetAlerts: 'set-budget-alerts', simulationEnabled: 'set-simulation',
};

async function loadSettings() {
  try {
    fillSettings(await Api.get('/api/admin/settings'));
  } catch (error) {
    handleError(error);
  }
}

function fillSettings(s) {
  Object.entries(SETTING_INPUTS).forEach(([key, id]) => {
    document.getElementById(id).value = key === 'bannedWords' ? (s.bannedWords || '').split(',').join(', ') : s[key];
  });
  Object.entries(SETTING_TOGGLES).forEach(([key, id]) => { document.getElementById(id).checked = !!s[key]; });
  document.getElementById('settingsUpdated').textContent = s.updatedAt ? `Last saved ${Fmt.dateTime(s.updatedAt)}` : '';
  FormErrors.clear(document.getElementById('settingsForm'));
}

function bindSettingsForm() {
  const form = document.getElementById('settingsForm');
  FormErrors.attachAutoClear(form);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const num = (id) => {
      const raw = document.getElementById(id).value.trim();
      return raw === '' ? null : Number(raw);
    };
    const data = {
      cpcRate: num('set-cpc'),
      cpmRate: num('set-cpm'),
      platformFee: num('set-fee'),
      minBudget: num('set-min-budget'),
      maxDailyBudget: num('set-max-daily'),
      peakHoursStart: num('set-peak-start'),
      peakHoursEnd: num('set-peak-end'),
      bannedWords: document.getElementById('set-banned').value.trim(),
    };
    Object.entries(SETTING_TOGGLES).forEach(([key, id]) => { data[key] = document.getElementById(id).checked; });
    const between = (v, lo, hi) => v !== null && isFinite(v) && v >= lo && v <= hi;
    const checks = [
      ['cpcRate', between(data.cpcRate, 0.01, 100), 'Cost per click must be between $0.01 and $100'],
      ['cpmRate', between(data.cpmRate, 0.01, 1000), 'CPM must be between $0.01 and $1,000'],
      ['platformFee', between(data.platformFee, 1, 30), 'Platform fee must be between 1% and 30%'],
      ['minBudget', between(data.minBudget, 10, 100000), 'Minimum budget must be between $10 and $100,000'],
      ['maxDailyBudget', between(data.maxDailyBudget, 5, 1000000), 'Maximum daily budget must be between $5 and $1,000,000'],
      ['peakHoursStart', between(data.peakHoursStart, 0, 23) && Number.isInteger(data.peakHoursStart), 'Peak hours start must be a whole hour from 0 to 23'],
      ['peakHoursEnd', between(data.peakHoursEnd, 0, 23) && Number.isInteger(data.peakHoursEnd), 'Peak hours end must be a whole hour from 0 to 23'],
      ['peakHoursEnd', data.peakHoursStart === null || data.peakHoursEnd === null || data.peakHoursEnd > data.peakHoursStart, 'Peak hours must end after they start'],
      ['bannedWords', data.bannedWords.length <= 1000, 'Blocked words must be at most 1000 characters'],
    ];
    if (!FormErrors.validate(form, checks)) return;
    const restore = setLoading(document.getElementById('settingsSubmitBtn'), 'Saving…');
    try {
      fillSettings(await Api.put('/api/admin/settings', data));
      toast('Settings saved.', 'success');
    } catch (error) {
      handleError(error, form);
    } finally {
      restore();
    }
  });
}

async function runSimulation(button) {
  const restore = setLoading(button, 'Running…');
  try {
    const result = await Api.post('/api/admin/simulation/run');
    toast(result.message, result.campaignsDelivered || result.statusChanges ? 'success' : 'info');
  } catch (error) {
    handleError(error);
  } finally {
    restore();
  }
}
