/*
 * Shared script for the Export tab redesign mockups.
 * It draws the app shell, keeps the mockup state, and calculates the file size estimates.
 * The values come from the project PXL_20260913_070045619 on the reference screenshot.
 */
(function () {
  // ---------- Mock data ----------
  const SOURCE = { durationSec: 5761, resolution: '4K', fps: 60, avgFps: '36.90', bitrateK: 65000 };
  const CONTENT = {
    full: { title: 'Full video', sec: 5761, length: '1h 36min 1sec' },
    points: { title: 'Only points', sec: 1181, length: '19min 41sec', count: 128 },
    favorites: { title: 'Only favorites', sec: 523, length: '8min 43sec', count: 43 },
  };
  const PRESETS = {
    best: { title: 'Original quality', res: '4K', fps: '60 fps', bitrateK: 65000, time: 'slow' },
    balanced: { title: 'Balanced', res: '4K', fps: '60 fps', bitrateK: 48750, time: 'medium' },
    fast: { title: 'Fast export', res: '1080p', fps: '60 fps', bitrateK: 32500, time: 'fast' },
  };
  const ENCODERS = {
    nvenc: 'NVIDIA NVENC',
    qsv: 'Intel Quick Sync',
    x264: 'Software (x264)',
  };
  const RES = { '720p': '720p', '1080p': '1080p', '4k': '4K' };
  const FPS = { '24': '24 fps', '30': '30 fps', '60': '60 fps' };
  const STATS_CARD_SEC = 12;
  const SET_CARDS_SEC = 24;

  const JOBS = {
    active: {
      dir: 'D:\\', file: 'PXL_20260913_070045619-best-4K.mp4', project: 'PXL_20260913_070045619',
      content: 'Only points (128 points) · Scoreboard: on · Comments: on',
      video: '4K · 60 FPS · 65 Mbit/s · H.264 (NVENC)',
      size: '3.12 GB / ~9.70 GB', progress: 32, timeLeft: '0:07:48',
      error: 'ffmpeg ended with exit code 1. The encoder could not open the output file.',
    },
    queued: [
      {
        dir: 'D:\\', file: 'PXL_20260913_070045619-balanced-4K.mp4', project: 'PXL_20260913_070045619',
        content: 'Only favorites (43 points) · Scoreboard: on · Comments: on',
        video: '4K · 60 FPS · 48.75 Mbit/s · H.264 (NVENC)', size: '~3.19 GB',
      },
      {
        dir: 'D:\\exports\\', file: 'PXL_20260828_100310593-fast-1080p.mp4', project: 'PXL_20260828_100310593',
        content: 'Only points (96 points) · Scoreboard: on · Comments: off',
        video: '1080p · 30 FPS · 8 Mbit/s · H.264 (NVENC)', size: '~512 MB',
      },
    ],
    completed: [
      {
        dir: 'D:\\', file: 'PXL_20260913_070045619-custom-4K.mp4', project: 'PXL_20260913_070045619',
        content: 'Only points (128 points) · Scoreboard: on · Comments: on',
        video: 'Custom · 4K · 60 FPS · 50 Mbit/s · H.264 (NVENC)', size: '7.44 GB', expected: '~7.55 GB',
        day: 'Today', date: '26 Sep 2026', time: '01:13',
      },
      {
        dir: 'D:\\', file: 'PXL_20260913_070045619-custom-4K.mp4', project: 'PXL_20260913_070045619',
        content: 'Only points (128 points) · Scoreboard: on · Comments: on',
        video: 'Custom · 4K · 60 FPS · 50 Mbit/s · H.264 (NVENC)', size: '7.45 GB', expected: '~7.82 GB',
        day: 'Yesterday', date: '25 Sep 2026', time: '17:24',
      },
      {
        dir: 'D:\\', file: 'PXL_20260828_100310593-data-saver-1080p.mp4', project: 'PXL_20260828_100310593',
        content: 'Only points · Scoreboard: on · Comments: off',
        video: '1080p · 30 FPS · H.264 (NVENC)', size: '445.93 MB',
        day: '24 Sep 2026', date: '24 Sep 2026', time: '15:21',
      },
      {
        dir: 'D:\\', file: 'PXL_20260913_070045619-high-4K.mp4', project: 'PXL_20260913_070045619',
        content: 'Only favorites · Scoreboard: on · Comments: on',
        video: '4K · 60 FPS · H.264 (NVENC)', size: '2.63 GB',
        day: '24 Sep 2026', date: '24 Sep 2026', time: '01:31',
      },
      {
        dir: 'D:\\', file: 'dfdf (2)-data-saver-1080p.mp4', project: 'dfdf (2)',
        content: 'Only points · Scoreboard: off · Comments: off',
        video: '1080p · 60 FPS · H.264 (NVENC)', size: '17.83 MB',
        day: '21 Sep 2026', date: '21 Sep 2026', time: '22:18',
      },
      {
        dir: 'D:\\', file: 'dfdf (2)-data-saver-1080p.mp4', project: 'dfdf (2)',
        content: 'Only points · Scoreboard: off · Comments: off',
        video: '1080p · 60 FPS · H.264 (NVENC)', size: '17.83 MB',
        day: '21 Sep 2026', date: '21 Sep 2026', time: '22:17',
      },
      {
        dir: 'D:\\', file: 'dfdf (2)-data-saver-1080p.mp4', project: 'dfdf (2)',
        content: 'Only points · Scoreboard: off · Comments: off',
        video: '1080p · 60 FPS · H.264 (NVENC)', size: '17.83 MB',
        day: '21 Sep 2026', date: '21 Sep 2026', time: '22:17',
      },
    ],
  };

  // ---------- State ----------
  const state = {
    state: 'idle',        // idle | running | queue | failed
    content: 'points',    // full | points | favorites
    quality: 'simple',    // simple | advanced
    preset: 'best',       // best | balanced | fast
    res: '4k',
    fps: '60',
    encoder: 'nvenc',
    bitrate: 65000,
    scoreboard: true,
    comments: true,
    stats: true,
    sets: false,
    open: 'content',      // accordion section of option C
    history: 'all',
  };

  // ---------- Calculations ----------
  function outputSec() {
    let sec = CONTENT[state.content].sec;
    if (state.stats) sec += STATS_CARD_SEC;
    if (state.sets && state.content !== 'full') sec += SET_CARDS_SEC;
    return sec;
  }

  function formatSize(bytes) {
    const gb = bytes / 1e9;
    if (gb >= 1) return gb.toFixed(2) + ' GB';
    return (bytes / 1e6).toFixed(0) + ' MB';
  }

  function sizeFor(bitrateK) {
    return '~' + formatSize((bitrateK + 192) * 125 * outputSec());
  }

  function formatBitrate(k) {
    const m = k / 1000;
    return (Number.isInteger(m) ? m : m.toFixed(1)) + ' Mbit/s';
  }

  function activeExports() {
    return state.state === 'running' || state.state === 'queue';
  }

  function qualityParts() {
    if (state.quality === 'simple') {
      const p = PRESETS[state.preset];
      return { name: p.title, res: p.res, fps: p.fps, bitrateK: p.bitrateK, encoder: ENCODERS.nvenc };
    }
    return { name: 'Custom', res: RES[state.res], fps: FPS[state.fps], bitrateK: state.bitrate, encoder: ENCODERS[state.encoder] };
  }

  function includedList() {
    const names = [];
    if (state.scoreboard) names.push('Scoreboard');
    if (state.comments) names.push('Comments');
    if (state.stats) names.push('Statistics card');
    if (state.sets && state.content !== 'full') names.push('Set summaries');
    return names;
  }

  const TEXT = {
    'size:best': () => sizeFor(PRESETS.best.bitrateK),
    'size:balanced': () => sizeFor(PRESETS.balanced.bitrateK),
    'size:fast': () => sizeFor(PRESETS.fast.bitrateK),
    'size:slider': () => sizeFor(state.bitrate),
    'size:current': () => sizeFor(qualityParts().bitrateK),
    'bitrate': () => formatBitrate(state.bitrate),
    'bitrate:balanced': () => formatBitrate(PRESETS.balanced.bitrateK),
    'bitrate:fast': () => formatBitrate(PRESETS.fast.bitrateK),
    'start': () => (activeExports() ? 'Enqueue export' : 'Start export'),
    'content:title': () => CONTENT[state.content].title,
    'content:summary': () => {
      const c = CONTENT[state.content];
      return c.title + ' · ' + c.length + (c.count ? ' · ' + c.count + ' points' : '');
    },
    'include:summary': () => includedList().join(', ') || 'Nothing added',
    'include:count': () => includedList().length + ' of 4',
    'quality:summary': () => {
      const q = qualityParts();
      return q.name + ' · ' + q.res + ' · ' + q.fps + ' · ' + sizeFor(q.bitrateK);
    },
    'quality:short': () => {
      const q = qualityParts();
      return q.res + ' · ' + q.fps + ' · ' + formatBitrate(q.bitrateK);
    },
    'encoder:current': () => qualityParts().encoder,
    'output:summary': () => {
      const q = qualityParts();
      return CONTENT[state.content].title + ' · ' + q.res + ' · ' + q.fps;
    },
  };

  // ---------- Rendering of the state ----------
  function matches(expr) {
    return expr.trim().split(/\s+/).every((cond) => {
      const neg = cond.startsWith('!');
      const [key, values] = (neg ? cond.slice(1) : cond).split(':');
      const current = String(state[key]);
      const hit = values.split(',').includes(current);
      return neg ? !hit : hit;
    });
  }

  function apply() {
    document.querySelectorAll('[data-when]').forEach((el) => { el.hidden = !matches(el.dataset.when); });
    document.querySelectorAll('[data-set]').forEach((el) => {
      const [key, value] = el.dataset.set.split(':');
      const selected = String(state[key]) === value;
      el.classList.toggle('is-selected', selected);
      el.setAttribute('aria-pressed', String(selected));
    });
    document.querySelectorAll('[data-disable-when]').forEach((el) => {
      const off = matches(el.dataset.disableWhen);
      el.classList.toggle('is-disabled', off);
      el.querySelectorAll('input').forEach((input) => { input.disabled = off; });
    });
    document.querySelectorAll('[data-bool]').forEach((el) => { el.checked = !!state[el.dataset.bool]; });
    document.querySelectorAll('[data-text]').forEach((el) => {
      const fn = TEXT[el.dataset.text];
      if (fn) el.textContent = fn();
    });
    document.querySelectorAll('input[type=range][data-num]').forEach((el) => {
      el.value = state[el.dataset.num];
      const fill = ((el.value - el.min) / (el.max - el.min)) * 100;
      el.style.setProperty('--fill', fill + '%');
    });
    Object.keys(state).forEach((key) => { document.body.dataset[key] = state[key]; });
    if (window.onMockApply) window.onMockApply(state);
  }

  document.addEventListener('click', (event) => {
    const el = event.target.closest('[data-set]');
    if (!el || el.disabled || el.classList.contains('is-disabled')) return;
    const [key, value] = el.dataset.set.split(':');
    state[key] = value;
    apply();
  });
  document.addEventListener('change', (event) => {
    const el = event.target;
    if (el.dataset.bool) { state[el.dataset.bool] = el.checked; apply(); }
  });
  document.addEventListener('input', (event) => {
    const el = event.target;
    if (el.dataset.num) { state[el.dataset.num] = Number(el.value); apply(); }
  });

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'final.html', id: 'final', label: 'Final' },
    { href: 'option-a.html', id: 'a', label: 'A · Guided steps (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Compact + table' },
    { href: 'option-c.html', id: 'c', label: 'C · Accordion + timeline' },
  ];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML =
        '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span>' +
        '<span class="mock-label">Mockup state</span>' +
        '<div class="mock-states">' +
        [['idle', 'Idle'], ['running', 'Running'], ['queue', 'Running + queue'], ['failed', 'Failed']]
          .map(([v, l]) => `<button data-set="state:${v}">${l}</button>`).join('') +
        '</div>';
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = '<span class="logo"></span>Tennis Record — Export — PXL_20260913_070045619' +
        '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
    }
    const nav = document.getElementById('nav');
    if (nav) {
      const item = (icon, label, active) =>
        `<a class="nav-item ${active ? 'active' : ''}"><span class="material-symbols-outlined">${icon}</span>${label}</a>`;
      nav.className = 'nav';
      nav.innerHTML =
        '<div class="nav-group">' + item('folder_open', 'Projects') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">MATCH</div>' +
        item('sports_tennis', 'Points') + item('scoreboard', 'Scoring') + item('bar_chart', 'Stats') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">VIDEO</div>' +
        item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export', true) + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  // A hash such as #state=queue&quality=advanced sets the first state. It helps to take screenshots.
  new URLSearchParams(location.hash.slice(1)).forEach((value, key) => {
    if (!(key in state)) return;
    state[key] = typeof state[key] === 'boolean' ? value === 'true' : typeof state[key] === 'number' ? Number(value) : value;
  });

  window.Mock = { state, apply, shell, JOBS, CONTENT, PRESETS, ENCODERS, SOURCE, escapeHtml, sizeFor };
})();
