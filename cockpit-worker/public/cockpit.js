/*
 * The cockpit page (B-41). Loads /api/summary and draws each section.
 * A failed source shows its error in its own cards; the other sections still work.
 */
(function () {
  'use strict';

  const { format, shortDate } = window.Charts;
  const STORE_KEY = 'cockpit.view';

  const TABS = {
    projects: 'Projects', points: 'Points', colors: 'Colors', crop_rotate: 'Crop and rotate',
    scoring: 'Scoring', stats: 'Statistics', export: 'Export',
  };
  const ACTIONS = {
    project_created: 'Project created', project_opened: 'Project opened', point_added: 'Point added',
    point_deleted: 'Point deleted', point_favorited: 'Point favorited', comment_added: 'Comment added',
    score_recorded: 'Score recorded', color_changed: 'Colors changed', crop_rotate_changed: 'Crop or rotation changed',
    help_opened: 'Help opened',
  };
  const FAILS = {
    export_fail_source_missing: 'Source video missing', export_fail_output_write: 'Cannot write the output file',
    export_fail_process_start: 'FFmpeg did not start', export_fail_ffmpeg_exit: 'FFmpeg stopped with an error',
    export_fail_other: 'Other',
  };
  const OPTIONS = {
    export_opt_scoreboard: 'Scoreboard', export_opt_comments: 'Comments', export_opt_stats_card: 'Statistics card',
    export_opt_favorites_only: 'Favorites only', export_opt_idle_trim: 'Idle trim',
  };
  const RESOLUTIONS = {
    export_res_720: '720p', export_res_1080: '1080p', export_res_1440: '1440p', export_res_2160: '2160p (4K)', export_res_other: 'Other',
  };
  const TOPICS = { problem: ['Problem', 2], idea: ['Idea', 1], question: ['Question', 3], other: ['Other', 4] };
  const ENCODERS = { software: 'CPU', nvenc: 'NVIDIA', amf: 'AMD', qsv: 'Intel' };

  let view = loadView();
  let latest = null;
  let topicFilter = '';
  let reportLimit = 10;

  // ---------- Small helpers ----------

  function $(id) { return document.getElementById(id); }

  /** The origin has no user name or password, also when the address bar has them. fetch refuses such URLs. */
  function api(path) { return new URL(path, window.location.origin).href; }

  function h(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function loadView() {
    try {
      const saved = JSON.parse(localStorage.getItem(STORE_KEY) || '{}');
      return { days: [7, 30, 90].includes(saved.days) ? saved.days : 30, dev: saved.dev === true };
    } catch {
      return { days: 30, dev: false };
    }
  }

  function saveView() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(view)); } catch { /* The view is a convenience only. */ }
  }

  function percent(part, whole, digits = 0) {
    if (!whole) return '–';
    return `${(100 * part / whole).toFixed(digits)}%`;
  }

  function dateTime(time) {
    return new Date(time).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
  }

  function plural(count, one, many) {
    return `${format(count)} ${count === 1 ? one : many}`;
  }

  function ago(time) {
    const minutes = Math.round((Date.now() - time) / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.round(minutes / 60);
    if (hours < 48) return `${hours} h ago`;
    return `${Math.round(hours / 24)} days ago`;
  }

  function weekTitle(date) { return `Week of ${shortDate(date)}`; }

  function sourceError(container, error) {
    const box = h('div', 'source-error');
    box.append(h('span', 'icon', '!'), h('span', '', error));
    container.replaceChildren(box);
  }

  function empty(container, text) {
    container.replaceChildren(h('div', 'empty', text || 'No data in this period.'));
  }

  /** Each target gets the error of the source, so that a failed source is visible where its numbers would be. */
  function failAll(ids, error) {
    for (const id of ids) sourceError($(id), error);
  }

  function ranked(container, rows, options = {}) {
    const visible = rows.filter(row => row.value > 0 || options.keepZero);
    if (!visible.length) return empty(container, options.emptyText);
    const max = options.max || Math.max(...visible.map(row => row.value));
    const list = h('div', 'ranked');
    for (const row of visible) {
      const item = h('div', 'ranked-row');
      const name = h('span', 'name', row.name);
      name.title = row.name;
      const bar = h('div', 'bar');
      const fill = h('span', row.cls || options.cls || '');
      fill.style.width = `${max ? Math.max(1, 100 * row.value / max) : 0}%`;
      bar.appendChild(fill);
      item.append(name, h('span', 'val', row.label ?? format(row.value)), bar);
      list.appendChild(item);
    }
    container.replaceChildren(list);
  }

  function table(container, headers, rows) {
    if (!rows.length) return empty(container);
    const wrap = h('div', 'table-wrap');
    const tableNode = h('table');
    const head = h('tr');
    for (const header of headers) head.appendChild(h('th', '', header));
    tableNode.appendChild(h('thead')).appendChild(head);
    const body = h('tbody');
    for (const row of rows) {
      const line = h('tr');
      for (const cell of row) {
        const td = h('td', cell && cell.bad ? 'bad' : '', cell && typeof cell === 'object' ? cell.text : cell);
        line.appendChild(td);
      }
      body.appendChild(line);
    }
    tableNode.appendChild(body);
    wrap.appendChild(tableNode);
    container.replaceChildren(wrap);
  }

  // ---------- Loading ----------

  async function load() {
    const button = $('refresh');
    button.disabled = true;
    $('updated').textContent = 'Loading…';
    try {
      const response = await fetch(api(`/api/summary?days=${view.days}${view.dev ? '&dev=1' : ''}`), { cache: 'no-store' });
      if (!response.ok) throw new Error(`The server returned ${response.status}.`);
      latest = await response.json();
      render(latest);
    } catch (error) {
      $('updated').textContent = `Could not load the numbers: ${error.message}`;
    } finally {
      button.disabled = false;
    }
  }

  function syncControls() {
    for (const button of document.querySelectorAll('[data-days]')) {
      button.setAttribute('aria-pressed', String(Number(button.dataset.days) === view.days));
    }
    for (const button of document.querySelectorAll('[data-topic]')) {
      button.setAttribute('aria-pressed', String(button.dataset.topic === topicFilter));
    }
    $('dev').checked = view.dev;
  }

  // ---------- Rendering ----------

  function render(data) {
    window.Charts.hideTooltip();
    const days = data.period.days;
    $('updated').textContent = `Last ${days} days (UTC), compared with the ${days} days before. Updated ${dateTime(data.generatedAt)}.`
      + (data.includeDev ? ' Development builds included.' : '');
    renderAlerts(data);
    renderKpis(data);
    renderFunnel(data);
    renderDownloads(data);
    renderSite(data);
    renderUsage(data);
    renderFeatures(data);
    renderExports(data);
    renderErrors(data);
    renderFeedback(data);
    renderSources(data);
  }

  function renderAlerts(data) {
    const alerts = [];
    const add = (level, icon, title, text) => alerts.push({ level, icon, title, text });
    const notConfigured = section => !section.ok && section.error.startsWith('Not configured');

    for (const [name, section] of [['Downloads', data.downloads], ['App usage', data.app], ['Feedback', data.feedback], ['Site', data.site], ['Workers', data.workers]]) {
      if (section.ok) continue;
      if (notConfigured(section)) {
        const same = alerts.find(alert => alert.text === section.error);
        if (same) same.title = `${same.title.slice(0, -1)} and ${name}:`;
        else add('info', 'i', `${name}:`, section.error);
      }
      else add('critical', '!', `${name} failed to load:`, section.error);
    }

    if (data.downloads.ok) {
      const snapshot = data.downloads.data.lastSnapshot;
      if (!snapshot) add('warning', '!', 'No download snapshot yet.', 'The hourly cron writes the first one. Or use "Take a download snapshot now" at the bottom.');
      else if (!snapshot.ok) add('critical', '!', 'The last download snapshot failed:', `${snapshot.message} (${ago(snapshot.ranAt)}).`);
      else if (Date.now() - snapshot.ranAt > 3 * 3600000) add('warning', '!', 'The download snapshot is old:', `the last one ran ${ago(snapshot.ranAt)}. Check the cron of the cockpit Worker.`);
    }

    if (data.app.ok) {
      const totals = data.app.data.totals;
      if (totals.sessions >= 10) {
        const crashFree = 1 - totals.unclean_exits / totals.sessions;
        if (crashFree < 0.95) add('critical', '!', `Crash-free sessions are ${(crashFree * 100).toFixed(1)}%.`, 'See "Errors" and the versions table.');
        else if (crashFree < 0.98) add('warning', '!', `Crash-free sessions are ${(crashFree * 100).toFixed(1)}%.`, 'See "Errors".');
      }
      if (totals.uncaught_errors > 0) add('warning', '!', plural(totals.uncaught_errors, 'uncaught error', 'uncaught errors'), 'in this period. See "Errors".');
      if (totals.exports_started >= 5 && totals.exports_failed / totals.exports_started > 0.1) {
        add('warning', '!', `${percent(totals.exports_failed, totals.exports_started)} of the exports failed.`, 'See "Exports" for the reasons and the encoders.');
      }
    }

    if (data.feedback.ok) {
      const feedback = data.feedback.data;
      if (feedback.undelivered > 0) add('critical', '!', `${plural(feedback.undelivered, 'feedback report', 'feedback reports')} did not reach Telegram.`, 'Read them in "Latest reports".');
      if (feedback.byTopic.problem > 0) add('info', 'i', plural(feedback.byTopic.problem, 'problem report', 'problem reports'), 'in this period.');
    }

    if (data.workers.ok) {
      const errors = data.workers.data.scripts.reduce((sum, script) => sum + script.errors, 0);
      if (errors > 0) add('warning', '!', plural(errors, 'Worker exception', 'Worker exceptions'), 'in this period. See "Errors" → Workers.');
    }

    if (!alerts.some(alert => alert.level !== 'info')) add('good', '✓', 'Nothing needs attention.', '');

    const order = { critical: 0, warning: 1, info: 2, good: 3 };
    alerts.sort((a, b) => order[a.level] - order[b.level]);
    $('alerts').replaceChildren(...alerts.map(alert => {
      const box = h('div', `alert ${alert.level}`);
      const icon = h('span', 'icon', alert.icon);
      icon.setAttribute('aria-label', alert.level);
      const text = h('div');
      text.append(h('b', '', alert.title), document.createTextNode(alert.text ? ` ${alert.text}` : ''));
      box.append(icon, text);
      return box;
    }));
  }

  function kpiTile(label, value, previous, spark, options = {}) {
    const tile = h('div', 'kpi');
    tile.appendChild(h('div', 'label', label));
    if (value === null || value === undefined) {
      tile.appendChild(h('div', 'value na', options.naText || 'No data'));
      tile.appendChild(h('div', 'delta', options.naHint || ''));
      return tile;
    }
    tile.appendChild(h('div', 'value', options.formatValue ? options.formatValue(value) : format(value)));
    const delta = h('div', 'delta');
    if (previous !== null && previous !== undefined) {
      const diff = value - previous;
      if (options.points) {
        delta.textContent = `${diff >= 0 ? '+' : ''}${(diff * 100).toFixed(1)} pts vs before`;
      } else if (previous === 0) {
        delta.textContent = value === 0 ? 'No change' : `+${format(value)} (none before)`;
      } else {
        delta.textContent = `${diff >= 0 ? '+' : ''}${Math.round(100 * diff / previous)}% vs before (${format(previous)})`;
      }
      const tiny = options.points ? Math.abs(diff) < 0.0005 : diff === 0;
      if (options.points && tiny) delta.textContent = 'No change vs before';
      if (!tiny) delta.classList.add(diff > 0 ? 'up' : 'down');
    } else {
      delta.textContent = options.hint || '';
    }
    tile.appendChild(delta);
    if (spark) {
      const box = h('div', 'spark');
      tile.appendChild(box);
      requestAnimationFrame(() => window.Charts.spark(box, spark, options.color || 1));
    }
    return tile;
  }

  function renderKpis(data) {
    const tiles = [];
    const site = data.site.ok ? data.site.data : null;
    tiles.push(kpiTile('Site visits', site ? site.totals.visits : null, site && site.prevTotals ? site.prevTotals.visits : null,
      site ? site.daily.map(day => day.visits) : null, { naText: 'Not available', naHint: 'See the note above.' }));

    const downloads = data.downloads.ok ? data.downloads.data : null;
    tiles.push(kpiTile('Setup downloads', downloads && downloads.trackingSince ? downloads.total.setup : null,
      downloads ? downloads.total.prevSetup : null, downloads ? downloads.daily.map(day => day.setup) : null,
      { naText: 'No history yet', naHint: 'Starts with the first snapshot.', color: 2 }));

    const app = data.app.ok ? data.app.data : null;
    tiles.push(kpiTile('New installs', app ? app.totals.first_sessions : null, app ? app.prevTotals.first_sessions : null,
      app ? app.daily.map(day => day.first_sessions) : null, { color: 3 }));
    tiles.push(kpiTile('Sessions', app ? app.totals.sessions : null, app ? app.prevTotals.sessions : null,
      app ? app.daily.map(day => day.sessions) : null));

    const crashFree = totals => totals.sessions ? 1 - totals.unclean_exits / totals.sessions : null;
    tiles.push(kpiTile('Crash-free sessions', app ? crashFree(app.totals) : null, app ? crashFree(app.prevTotals) : null, null,
      { points: true, formatValue: value => `${(value * 100).toFixed(1)}%`, naText: 'No sessions', hint: '' }));

    const feedback = data.feedback.ok ? data.feedback.data : null;
    tiles.push(kpiTile('Feedback reports', feedback ? feedback.total : null, feedback ? feedback.prevTotal : null,
      feedback ? feedback.daily.map(day => day.problem + day.idea + day.question + day.other) : null, { color: 4 }));
    $('kpis').replaceChildren(...tiles);
  }

  function renderFunnel(data) {
    const site = data.site.ok ? data.site.data : null;
    const downloadPage = site ? site.pages.find(page => page.name === '/download/' || page.name === '/download') : null;
    const steps = [
      { name: 'Site visits', note: 'Web Analytics', value: site ? site.totals.visits : null },
      { name: 'Download page views', note: 'Web Analytics, /download/', value: site ? (downloadPage ? downloadPage.pageViews : 0) : null },
      { name: 'Setup downloads', note: 'GitHub, with updates', value: data.downloads.ok && data.downloads.data.trackingSince ? data.downloads.data.total.setup : null },
      { name: 'First app starts', note: 'Analytics, session 1', value: data.app.ok ? data.app.data.totals.first_sessions : null },
      { name: 'Returning sessions', note: 'Analytics, session 2+', value: data.app.ok ? data.app.data.totals.sessions - data.app.data.totals.first_sessions : null },
    ];
    const max = Math.max(1, ...steps.map(step => step.value || 0));
    const first = steps.find(step => step.value);
    $('funnel').replaceChildren(...steps.map((step, index) => {
      const row = h('div', 'funnel-step');
      const name = h('div', 'name', step.name);
      name.appendChild(h('small', '', step.note));
      const track = h('div', 'track');
      if (step.value) {
        const fill = h('div', 'fill');
        fill.style.width = `${Math.max(0.5, 100 * step.value / max)}%`;
        track.appendChild(fill);
      }
      const num = h('div', 'num', step.value === null ? 'n/a' : format(step.value));
      // A step from another source can be larger than the first step. A percent over 100 means nothing there.
      if (step.value !== null && index > 0 && first && first !== step && step.value <= first.value) num.appendChild(h('small', '', `${percent(step.value, first.value, 1)} of ${first.name.toLowerCase()}`));
      row.append(name, track, num);
      return row;
    }));
  }

  function renderDownloads(data) {
    if (!data.downloads.ok) return failAll(['downloads-daily', 'downloads-weekly', 'releases'], data.downloads.error);
    const downloads = data.downloads.data;
    $('downloads-note').textContent = downloads.trackingSince
      ? `History since ${shortDate(downloads.trackingSince)}. The first snapshot day is the base line.`
      : 'The history starts with the first snapshot.';
    const series = [{ key: 'setup', label: 'Setup', color: 2 }];
    window.Charts.columns($('downloads-daily'), { data: downloads.daily, series, nullLabel: 'No snapshot yet', gapLabel: 'No snapshot on this day: the next day has its downloads', label: 'Setup downloads per day' });
    window.Charts.columns($('downloads-weekly'), { data: downloads.weekly, series, nullLabel: 'No snapshot yet', formatTitle: weekTitle, label: 'Setup downloads per week' });
    table($('releases'), ['Release', 'Published', 'Setup downloads', 'Other files'], downloads.releases.map(release => [
      release.tag, release.publishedAt ? release.publishedAt.slice(0, 10) : 'not published', format(release.setup), format(release.other),
    ]));
    if (!downloads.releases.length) empty($('releases'), 'No app release on GitHub yet.');
  }

  function renderSite(data) {
    $('site-grid').hidden = !data.site.ok;
    if (!data.site.ok) {
      $('site-note').textContent = 'Cloudflare Web Analytics.';
      return sourceError($('site-daily'), data.site.error);
    }
    const site = data.site.data;
    $('site-note').textContent = site.clampedToDays
      ? `Cloudflare Web Analytics, sampled. Cloudflare refused the long range: these numbers cover only the last ${site.clampedToDays} days.`
      : 'Cloudflare Web Analytics, sampled.';
    window.Charts.lines($('site-daily'), {
      data: site.daily, area: false, label: 'Visits and page views per day',
      series: [{ key: 'visits', label: 'Visits', color: 1 }, { key: 'pageViews', label: 'Page views', color: 2 }],
    });
    const toRows = list => list.map(item => ({ name: item.name, value: item.visits, label: `${format(item.visits)} visits` }));
    ranked($('site-pages'), site.pages.map(item => ({ name: item.name, value: item.pageViews, label: `${format(item.pageViews)} views` })));
    ranked($('site-referrers'), toRows(site.referrers));
    ranked($('site-countries'), toRows(site.countries));
    ranked($('site-devices'), toRows(site.devices));
  }

  function renderUsage(data) {
    const ids = ['usage-daily', 'usage-weekly', 'usage-length', 'usage-number', 'usage-os', 'versions'];
    if (!data.app.ok) return failAll(ids, data.app.error);
    const app = data.app.data;
    const withReturning = rows => rows.map(row => ({ ...row, returning: row.sessions - row.first_sessions }));
    const series = [{ key: 'first_sessions', label: 'First sessions', color: 3 }, { key: 'returning', label: 'Returning sessions', color: 1 }];
    window.Charts.columns($('usage-daily'), { data: withReturning(app.daily), series, label: 'Sessions per day' });
    window.Charts.columns($('usage-weekly'), { data: withReturning(app.weekly), series, formatTitle: weekTitle, label: 'Sessions per week' });

    const shape = app.sessionShape;
    const total = shape.sessions || 0;
    $('active-note').textContent = total ? `Average ${format(shape.avg_active_min)} min. Total ${format(app.totals.active_s / 3600)} active hours.` : '';
    const share = value => ({ value: value || 0, label: `${format(value || 0)} · ${percent(value || 0, total)}` });
    ranked($('usage-length'), [
      { name: 'Under 1 min', ...share(shape.active_under_1_min) },
      { name: '1 to 10 min', ...share(shape.active_1_to_10_min) },
      { name: '10 to 60 min', ...share(shape.active_10_to_60_min) },
      { name: 'Over 60 min', ...share(shape.active_over_60_min) },
    ], { keepZero: true, max: total });
    ranked($('usage-number'), [
      { name: '1st session', ...share(shape.session_n_1), cls: 'c3' },
      { name: '2nd to 5th', ...share(shape.session_n_2_5) },
      { name: '6th to 20th', ...share(shape.session_n_6_20) },
      { name: '21st or later', ...share(shape.session_n_21p) },
    ], { keepZero: true, max: total });

    const os = h('div');
    const osList = h('div');
    const levels = h('div');
    os.append(osList, h('h3', '', 'Statistics level'), levels);
    os.children[1].className = 'hint';
    os.children[1].style.margin = '14px 0 8px';
    ranked(osList, app.osFamilies.map(item => ({ name: item.name, value: item.sessions, label: `${format(item.sessions)} · ${percent(item.sessions, total)}` })), { max: total });
    ranked(levels, [
      { name: 'Extended', value: shape.extended_sessions || 0, label: `${format(shape.extended_sessions || 0)} · ${percent(shape.extended_sessions || 0, total)}` },
      { name: 'Essential only', value: total - (shape.extended_sessions || 0), label: `${format(total - (shape.extended_sessions || 0))} · ${percent(total - (shape.extended_sessions || 0), total)}`, cls: 'c2' },
    ], { keepZero: true, max: total });
    $('usage-os').replaceChildren(os);

    table($('versions'), ['Version', 'Sessions', 'First sessions', 'Crashes', 'Crash-free', 'Uncaught errors', 'Last seen'], app.versions.map(row => {
      const crashFree = row.sessions ? 1 - row.unclean_exits / row.sessions : 1;
      return [
        row.version, format(row.sessions), format(row.first_sessions), format(row.unclean_exits),
        { text: `${(crashFree * 100).toFixed(1)}%`, bad: crashFree < 0.98 && row.sessions >= 10 },
        { text: format(row.uncaught_errors), bad: row.uncaught_errors > 0 },
        ago(row.last_seen),
      ];
    }));
  }

  function counterMap(app) {
    return new Map(app.counters.map(row => [row.key, row]));
  }

  function renderFeatures(data) {
    if (!data.app.ok) return failAll(['tabs', 'actions'], data.app.error);
    const app = data.app.data;
    const extended = app.sessionShape.extended_sessions || 0;
    const counters = counterMap(app);
    if (!extended) {
      empty($('tabs'), 'No extended sessions in this period.');
      return empty($('actions'), 'No extended sessions in this period.');
    }
    const minutes = seconds => seconds >= 3600 ? `${format(seconds / 3600)} h` : `${format(seconds / 60)} min`;
    ranked($('tabs'), Object.entries(TABS).map(([key, name]) => {
      const row = counters.get(`tab_${key}`);
      const time = counters.get(`tab_s_${key}`);
      const sessions = row ? row.sessions : 0;
      return { name, value: sessions, label: `${percent(sessions, extended)} · ${minutes(time ? time.total : 0)}` };
    }).sort((a, b) => b.value - a.value), { keepZero: true, max: extended });
    ranked($('actions'), Object.entries(ACTIONS).map(([key, name]) => {
      const row = counters.get(key);
      const sessions = row ? row.sessions : 0;
      return { name, value: sessions, label: `${percent(sessions, extended)} · ${format(row ? row.total : 0)}×` };
    }).sort((a, b) => b.value - a.value), { keepZero: true, max: extended, cls: 'c3' });
  }

  function renderExports(data) {
    const ids = ['exports-daily', 'encoders', 'export-fails', 'export-options', 'export-res'];
    if (!data.app.ok) return failAll(ids, data.app.error);
    const app = data.app.data;
    const counters = counterMap(app);
    window.Charts.columns($('exports-daily'), {
      data: app.daily.map(row => ({ ...row, other: Math.max(0, row.exports_started - row.exports_completed - row.exports_failed) })),
      series: [
        { key: 'exports_completed', label: 'Completed', color: 3 },
        { key: 'exports_failed', label: 'Failed', color: 2 },
        { key: 'other', label: 'Cancelled or open', color: 4 },
      ],
      label: 'Exports per day',
    });
    const used = app.encoders.filter(row => row.started > 0);
    table($('encoders'), ['Encoder', 'Started', 'Failed', 'Stopped', 'Success', 'Speed'], used.map(row => [
      ENCODERS[row.encoder] || row.encoder, format(row.started),
      { text: format(row.failed), bad: row.failed > 0 }, format(row.cancelled + row.interrupted),
      percent(row.completed, row.started), row.speed === null ? '–' : `${row.speed}×`,
    ]));
    if (!used.length) empty($('encoders'), 'No exports in this period.');
    const list = (labels, cls) => Object.entries(labels).map(([key, name]) => {
      const row = counters.get(key);
      return { name, value: row ? row.total : 0, cls };
    }).sort((a, b) => b.value - a.value);
    ranked($('export-fails'), list(FAILS, 'crit'), { emptyText: 'No failed exports.' });
    ranked($('export-options'), list(OPTIONS, 'c1'), { emptyText: 'No exports in this period.' });
    ranked($('export-res'), list(RESOLUTIONS, 'c1'), { emptyText: 'No exports in this period.' });
  }

  function renderErrors(data) {
    if (data.app.ok) {
      window.Charts.columns($('errors-daily'), {
        data: data.app.data.daily, label: 'Errors per day',
        series: [
          { key: 'unclean_exits', label: 'Crashes', color: 2 },
          { key: 'uncaught_errors', label: 'Uncaught errors', color: 1 },
          { key: 'video_open_failed', label: 'Video open failed', color: 3 },
          { key: 'exports_failed', label: 'Export failed', color: 4 },
        ],
      });
    } else {
      sourceError($('errors-daily'), data.app.error);
    }

    if (data.feedback.ok) {
      ranked($('top-errors'), data.feedback.data.topErrors.map(row => ({ name: row.error, value: row.reports, label: `${row.reports} reports`, cls: 'crit' })),
        { emptyText: 'No error in the reports of this period.' });
    } else {
      sourceError($('top-errors'), data.feedback.error);
    }

    if (data.workers.ok) {
      const workers = data.workers.data;
      const box = h('div');
      const tableBox = h('div');
      table(tableBox, ['Worker', 'Requests', 'Exceptions', 'Rate'], workers.scripts.map(row => [
        row.name, format(row.requests), { text: format(row.errors), bad: row.errors > 0 }, percent(row.errors, row.requests, 2),
      ]));
      box.appendChild(tableBox);
      if (workers.clampedToDays) box.appendChild(h('p', 'hint', `Only the last ${workers.clampedToDays} days: Cloudflare refused the long range.`));
      $('workers').replaceChildren(box);
    } else {
      sourceError($('workers'), data.workers.error);
    }
  }

  function renderFeedback(data) {
    const ids = ['feedback-daily', 'feedback-topics', 'reports'];
    if (!data.feedback.ok) return failAll(ids, data.feedback.error);
    const feedback = data.feedback.data;
    window.Charts.columns($('feedback-daily'), {
      data: feedback.daily, label: 'Feedback reports per day',
      series: Object.entries(TOPICS).map(([key, [label, color]]) => ({ key, label, color })),
    });
    ranked($('feedback-topics'), Object.entries(TOPICS).map(([key, [name, color]]) => ({
      name, value: feedback.byTopic[key] || 0, label: `${feedback.byTopic[key] || 0} · ${percent(feedback.byTopic[key] || 0, feedback.total)}`, cls: `c${color}`,
    })), { keepZero: true, max: feedback.total || 1 });
    renderReports();
  }

  function renderReports() {
    if (!latest || !latest.feedback.ok) return;
    const reports = latest.feedback.data.reports.filter(report => !topicFilter || report.topic === topicFilter);
    if (!reports.length) return empty($('reports'), 'No reports.');
    const cards = reports.slice(0, reportLimit).map(report => {
      const card = h('article', 'report');
      const head = h('div', 'report-head');
      const [topicName, color] = TOPICS[report.topic] || [report.topic, 4];
      const topic = h('span', 'tag');
      topic.append(h('i', `b${color}`), document.createTextNode(topicName));
      head.append(topic, h('span', '', dateTime(report.receivedAt)), h('span', '', `v${report.version}`), h('span', '', report.os));
      if (report.hasLog) head.appendChild(h('span', 'tag', 'log'));
      if (report.hasEmail) head.appendChild(h('span', 'tag', 'email given'));
      if (!report.delivered) head.appendChild(h('span', 'tag warn', '⚠ not in Telegram'));
      head.appendChild(h('span', 'report-id', report.id));
      card.appendChild(head);
      card.appendChild(h('p', 'report-message', report.message + (report.truncated ? ' …' : '')));
      if (report.errorLine) card.appendChild(h('p', 'report-error', report.errorLine));
      return card;
    });
    if (reports.length > reportLimit) {
      const more = h('button', 'ghost', `Show ${reports.length - reportLimit} more`);
      more.type = 'button';
      more.addEventListener('click', () => { reportLimit = Infinity; renderReports(); });
      cards.push(more);
    }
    $('reports').replaceChildren(...cards);
  }

  function renderSources(data) {
    const items = [];
    if (data.downloads.ok) {
      const downloads = data.downloads.data;
      const snapshot = downloads.lastSnapshot;
      items.push(`Downloads: GitHub release totals, snapshot each hour. ${snapshot ? `Last snapshot ${ago(snapshot.ranAt)} (${snapshot.ok ? 'ok' : 'failed'}).` : 'No snapshot yet.'}`);
    }
    if (data.app.ok) {
      const retention = data.app.data.retention;
      items.push(`App usage: analytics Worker (D1), sessions are counted on the day of their first summary. Kept 90 days.${retention ? ` Last clean-up ${ago(retention.ranAt)}.` : ''}`);
    }
    items.push('Feedback: feedback Worker (D1). Kept 90 days. Email addresses are not shown.');
    items.push('Site and Workers: Cloudflare GraphQL Analytics API.');
    const list = h('ul');
    for (const item of items) list.appendChild(h('li', '', item));
    $('sources').replaceChildren(list);
  }

  // ---------- Events ----------

  for (const button of document.querySelectorAll('[data-days]')) {
    button.addEventListener('click', () => {
      view.days = Number(button.dataset.days);
      saveView();
      syncControls();
      load();
    });
  }
  for (const button of document.querySelectorAll('[data-topic]')) {
    button.addEventListener('click', () => {
      topicFilter = button.dataset.topic;
      reportLimit = 10;
      syncControls();
      renderReports();
    });
  }
  $('dev').addEventListener('change', event => {
    view.dev = event.target.checked;
    saveView();
    load();
  });
  $('refresh').addEventListener('click', load);
  $('snapshot').addEventListener('click', async () => {
    const button = $('snapshot');
    button.disabled = true;
    try {
      const response = await fetch(api('/api/snapshot'), { method: 'POST' });
      const body = await response.json();
      button.textContent = response.ok ? `Snapshot done: ${body.files} files` : `Snapshot failed: ${body.error}`;
      await load();
    } catch (error) {
      button.textContent = `Snapshot failed: ${error.message}`;
    } finally {
      button.disabled = false;
    }
  });

  syncControls();
  load();
})();
