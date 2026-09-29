/*
 * Shared script for the Colors tab redesign mockups.
 * It keeps the six color values, simulates the player and the live preview,
 * draws the app shell and the playback bar, and binds the sliders and the Reset button.
 * The texts and the tooltips come from SwingColorAdjustmentsPanel.kt.
 */
(function () {
  const DURATION = 5761000; // 01:36:01

  // ---------- Controls ----------
  // The tooltips are the same as in the current tab.
  const CONTROLS = {
    brightness: {
      label: 'Brightness', icon: 'light_mode', low: 'Darker', high: 'Brighter',
      tip: 'Brightness [-100..+100], default 0',
      grad: 'linear-gradient(90deg, #080808, #6a6a6a 50%, #f4f4f4)',
    },
    contrast: {
      label: 'Contrast', icon: 'contrast', low: 'Flatter', high: 'Stronger',
      tip: 'Contrast [-100..+100], default 0',
      grad: 'linear-gradient(90deg, #5e5e5e, #2e2e2e 50%, #000) top / 100% 50% no-repeat, ' +
        'linear-gradient(90deg, #8a8a8a, #c2c2c2 50%, #fff) bottom / 100% 50% no-repeat',
    },
    shadows: {
      label: 'Shadows', icon: 'dark_mode', low: 'Deeper', high: 'Lifted',
      tip: 'Shadows [-100..+100], default 0. Positive lifts dark areas, negative deepens them.',
      grad: 'linear-gradient(90deg, #000, #242424 50%, #5c5c5c)',
    },
    highlights: {
      label: 'Highlights', icon: 'flare', low: 'Darker', high: 'Brighter',
      tip: 'Highlights [-100..+100], default 0. Negative pulls bright areas down, positive brightens them.',
      grad: 'linear-gradient(90deg, #7c7c7c, #c4c4c4 50%, #fff)',
    },
    saturation: {
      label: 'Saturation', icon: 'water_drop', low: 'Gray', high: 'Vivid',
      tip: 'Saturation [-100..+100], default 0',
      grad: 'linear-gradient(90deg, #808080, rgba(128,128,128,.55) 50%, rgba(128,128,128,0)), ' +
        'linear-gradient(90deg, #ff4f4f, #ffd24f, #57e07a, #4fb6ff, #b25cff)',
    },
    temperature: {
      label: 'Temperature', icon: 'thermostat', low: 'Cooler', high: 'Warmer',
      tip: 'Temperature [-100..+100], default 0',
      grad: 'linear-gradient(90deg, #3d7bff, #cdd3dc 50%, #ff9a2a)',
    },
  };
  const ORDER = ['brightness', 'contrast', 'shadows', 'highlights', 'saturation', 'temperature'];
  const GROUPS = [
    { id: 'light', title: 'Light', ids: ['brightness', 'contrast', 'shadows', 'highlights'] },
    { id: 'color', title: 'Color', ids: ['saturation', 'temperature'] },
  ];
  // Same text as the tooltip that the current tab adds when player.isAdjustSupported() is false.
  const UNSUPPORTED_TIP =
    'Live preview for this control may not be available on this system; values will still be saved for export.';

  const PRESETS = {
    default: { brightness: 0, contrast: 0, shadows: 0, highlights: 0, saturation: 0, temperature: 0 },
    adjusted: { brightness: 6, contrast: 14, shadows: 18, highlights: -24, saturation: 22, temperature: 12 },
  };

  // ---------- State ----------
  const state = {
    values: { ...PRESETS.default },
    preset: 'default',    // mockup switch: default | adjusted | custom
    support: 'live',      // mockup switch: live | none
    playing: false,
    t: 1092000,
  };

  // ---------- Formatting ----------
  const pad = (n) => String(n).padStart(2, '0');
  function fmt(ms) {
    const s = Math.floor(Math.max(0, ms) / 1000);
    return `${pad(Math.floor(s / 3600))}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}`;
  }
  const signed = (v) => (v > 0 ? '+' + v : v < 0 ? '−' + Math.abs(v) : '0');
  const changedIds = () => ORDER.filter((id) => state.values[id] !== 0);

  // ---------- Events ----------
  const listeners = [];
  const on = (fn) => listeners.push(fn);
  const emit = (reason) => listeners.forEach((fn) => fn(reason));

  function set(id, value) {
    state.values[id] = Math.max(-100, Math.min(100, Math.round(value)));
    state.preset = matchPreset();
    render();
  }
  function reset() {
    state.values = { ...PRESETS.default };
    state.preset = 'default';
    render();
  }
  function matchPreset() {
    return Object.keys(PRESETS).find((p) => ORDER.every((id) => PRESETS[p][id] === state.values[id])) || 'custom';
  }

  // ---------- Live preview (an SVG filter on the frame) ----------
  // The formula is an estimate for the mockup only. It is not the mpv filter chain.
  function curve(channelGain) {
    const v = state.values;
    const b = v.brightness / 100, c = v.contrast / 100, sh = v.shadows / 100, hl = v.highlights / 100;
    const out = [];
    for (let i = 0; i <= 32; i++) {
      const x = i / 32;
      let y = x + 0.28 * sh * 6.75 * x * (1 - x) * (1 - x) + 0.28 * hl * 6.75 * x * x * (1 - x);
      y += b * 0.35;
      y = (y - 0.5) * (1 + c) + 0.5;
      y *= channelGain;
      out.push(Math.max(0, Math.min(1, y)).toFixed(3));
    }
    return out.join(' ');
  }
  function applyPreview() {
    let svg = document.getElementById('grade-svg');
    if (!svg) {
      svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.id = 'grade-svg';
      svg.setAttribute('width', '0');
      svg.setAttribute('height', '0');
      svg.style.position = 'absolute';
      svg.innerHTML =
        '<filter id="grade" color-interpolation-filters="sRGB"><feComponentTransfer>' +
        '<feFuncR type="table"/><feFuncG type="table"/><feFuncB type="table"/></feComponentTransfer>' +
        '<feColorMatrix type="saturate" values="1"/></filter>';
      document.body.appendChild(svg);
    }
    const t = state.values.temperature / 100;
    svg.querySelector('feFuncR').setAttribute('tableValues', curve(1 + 0.16 * t));
    svg.querySelector('feFuncG').setAttribute('tableValues', curve(1 + 0.02 * t));
    svg.querySelector('feFuncB').setAttribute('tableValues', curve(1 - 0.18 * t));
    svg.querySelector('feColorMatrix').setAttribute('values', String(1 + state.values.saturation / 100));
    const live = state.support === 'live';
    document.querySelectorAll('.video img').forEach((img) => { img.style.filter = live ? 'url(#grade)' : 'none'; });
  }

  // ---------- Slider markup ----------
  function tipFor(id) {
    return CONTROLS[id].tip + (state.support === 'none' ? '\n' + UNSUPPORTED_TIP : '');
  }
  function slider(id, opts = {}) {
    const c = CONTROLS[id];
    return `<div class="cslider ${opts.grad ? 'grad' : ''}" data-cs="${id}" style="--grad:${c.grad}">
      <div class="track"></div>
      <input type="range" min="-100" max="100" step="1" value="0" data-ctl="${id}" aria-label="${c.label}">
    </div>`;
  }

  // ---------- Rendering ----------
  function render() {
    const changed = changedIds();
    ORDER.forEach((id) => {
      const v = state.values[id];
      const p = (v + 100) / 2; // percent of the track
      document.querySelectorAll(`[data-cs="${id}"]`).forEach((el) => {
        el.style.setProperty('--lo', Math.min(50, p) + '%');
        el.style.setProperty('--hi', Math.max(50, p) + '%');
        el.classList.toggle('is-changed', v !== 0);
        el.title = tipFor(id);
      });
      document.querySelectorAll(`input[data-ctl="${id}"]`).forEach((el) => {
        if (Number(el.value) !== v) el.value = v;
        el.title = tipFor(id);
      });
      document.querySelectorAll(`[data-val="${id}"]`).forEach((el) => {
        el.textContent = signed(v);
        el.classList.toggle('is-changed', v !== 0);
      });
      document.querySelectorAll(`[data-row="${id}"]`).forEach((el) => el.classList.toggle('is-changed', v !== 0));
    });
    document.querySelectorAll('[data-changed]').forEach((el) => {
      el.textContent = changed.length ? `${changed.length} of 6 changed` : 'All at default';
      el.classList.toggle('is-changed', changed.length > 0);
    });
    document.querySelectorAll('[data-reset]').forEach((el) => el.classList.toggle('is-quiet', changed.length === 0));
    document.querySelectorAll('[data-when]').forEach((el) => {
      const [key, value] = el.dataset.when.split(':');
      el.hidden = String(state[key]) !== value;
    });
    applyPreview();
    emit('render');
  }

  function bind() {
    document.addEventListener('input', (ev) => {
      const id = ev.target.dataset && ev.target.dataset.ctl;
      if (id) set(id, Number(ev.target.value));
    });
    document.addEventListener('click', (ev) => {
      if (ev.target.closest('[data-reset]')) reset();
    });
  }

  // ---------- Player simulation and playback bar ----------
  let timer = null;
  function setPlaying(value) {
    state.playing = value;
    clearInterval(timer);
    if (value) {
      timer = setInterval(() => {
        state.t = Math.min(DURATION, state.t + 200);
        if (state.t >= DURATION) setPlaying(false);
        renderTransport();
      }, 200);
    }
    renderTransport();
  }
  const togglePlay = () => setPlaying(!state.playing);

  function renderTransport() {
    document.querySelectorAll('[data-play]').forEach((b) => {
      b.innerHTML = `<span class="material-symbols-outlined fill">${state.playing ? 'pause' : 'play_arrow'}</span><span class="play-key">SPACE</span>`;
      b.title = state.playing ? 'SPACE - Pause' : 'SPACE - Play';
    });
    document.querySelectorAll('[data-now]').forEach((el) => { el.textContent = fmt(state.t); });
    document.querySelectorAll('[data-scrub]').forEach((el) => el.style.setProperty('--pos', (state.t / DURATION) * 100 + '%'));
  }

  function transport(el) {
    el.classList.add('playbar');
    el.innerHTML =
      '<button class="play" data-play aria-label="Play or Pause"></button>' +
      '<span class="time tc" data-now title="Current time"></span>' +
      '<div class="scrub" data-scrub title="Seek"><span class="knob"></span></div>' +
      `<span class="time total tc" title="Video length">${fmt(DURATION)}</span>`;
    el.querySelector('[data-play]').addEventListener('click', (ev) => { togglePlay(); ev.currentTarget.blur(); });
    const scrub = el.querySelector('[data-scrub]');
    const seekTo = (ev) => {
      const r = scrub.getBoundingClientRect();
      state.t = Math.round(Math.max(0, Math.min(1, (ev.clientX - r.left) / r.width)) * DURATION);
      renderTransport();
    };
    scrub.addEventListener('pointerdown', (ev) => { scrub.setPointerCapture(ev.pointerId); seekTo(ev); });
    scrub.addEventListener('pointermove', (ev) => { if (scrub.hasPointerCapture(ev.pointerId)) seekTo(ev); });
    renderTransport();
  }

  // SPACE plays or pauses, as AppShortcuts.PLAY_PAUSE in the tab.
  document.addEventListener('keydown', (ev) => {
    if (ev.key !== ' ') return;
    ev.preventDefault();
    togglePlay();
  });

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'option-a.html', id: 'a', label: 'A · Grouped panel (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Narrow panel + gradients' },
    { href: 'option-c.html', id: 'c', label: 'C · Faders' },
  ];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML =
        '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span>' +
        '<span class="mock-label">Values</span><div class="mock-states" data-group="preset">' +
        '<button data-v="default">Default</button><button data-v="adjusted">Adjusted</button></div>' +
        '<span class="mock-label">Live preview</span><div class="mock-states" data-group="support">' +
        '<button data-v="live">Supported</button><button data-v="none">Not supported</button></div>';
      bar.addEventListener('click', (ev) => {
        const b = ev.target.closest('.mock-states button');
        if (!b) return;
        const group = b.parentElement.dataset.group;
        if (group === 'preset') { state.values = { ...PRESETS[b.dataset.v] }; state.preset = b.dataset.v; }
        else state.support = b.dataset.v;
        b.blur();
        render();
      });
      on(() => bar.querySelectorAll('.mock-states').forEach((g) =>
        g.querySelectorAll('button').forEach((b) => b.classList.toggle('is-selected', state[g.dataset.group] === b.dataset.v))));
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = '<span class="logo"></span>Tennis Record — Colors — PXL_20260913_070045619' +
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
        item('palette', 'Colors', true) + item('crop_rotate', 'Transform') + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export') + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
  }

  // A hash such as #preset=adjusted&support=none sets the first state. It helps to take screenshots.
  function start() {
    const params = new URLSearchParams(location.hash.slice(1));
    if (params.get('preset') in PRESETS) { state.preset = params.get('preset'); state.values = { ...PRESETS[state.preset] }; }
    if (['live', 'none'].includes(params.get('support'))) state.support = params.get('support');
    bind();
    render();
  }

  window.Mock = {
    state, CONTROLS, ORDER, GROUPS, UNSUPPORTED_TIP,
    on, set, reset, render, start, shell, transport, slider, signed, tipFor, changedIds, togglePlay,
  };
})();
