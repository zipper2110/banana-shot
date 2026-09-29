/*
 * Shared script for the dialog redesign mockups.
 * It draws the app shell and the still tab behind a dialog, and runs the shared dialog parts:
 * the modal frame, the message box, the color picker, the tooltips and the mockup toasts.
 * The texts of the dialogs come from the current app code.
 */
(function () {
  'use strict';

  const PAGES = [
    { href: 'index.html', id: 'index', label: '← Overview' },
    { href: 'scoring-settings.html', id: 'scoring', label: 'Scoring settings' },
    { href: 'scoreboard-style.html', id: 'scoreboard', label: 'Scoreboard style' },
    { href: 'color-picker.html', id: 'color', label: 'Color picker' },
    { href: 'messages.html', id: 'messages', label: 'Messages' },
    { href: 'help.html', id: 'help', label: 'Help window' },
    { href: 'more.html', id: 'more', label: 'More window' },
  ];

  const APP_NAME = 'BananaShot';
  const PROJECT = 'PXL_20260913_070045619';

  function esc(text) {
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  const icon = (name, cls) => `<span class="material-symbols-outlined ${cls || ''}">${name}</span>`;

  // ---------- Shell ----------
  /**
   * Draws the mockup bar, the title bar and the sidebar.
   * [states] is a list of { label, group, options: [[value, text]] } for the "Mockup state" switches.
   */
  function shell(pageId, opts = {}) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML = PAGES.map((p) => `<a href="${p.href}" class="${p.id === pageId ? 'current' : ''}">${p.label}</a>`).join('') +
        '<span class="spacer"></span>' +
        (opts.states || []).map((s) => `<span class="mock-label">${esc(s.label)}</span><div class="mock-states" data-group="${s.group}">` +
          s.options.map(([v, t]) => `<button data-v="${v}">${esc(t)}</button>`).join('') + '</div>').join('');
      bar.addEventListener('click', (e) => {
        const b = e.target.closest('.mock-states button');
        if (!b) return;
        setState(b.parentElement.dataset.group, b.dataset.v);
        b.blur();
      });
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = `<span class="logo"></span>${APP_NAME} — ${esc(opts.tabTitle || 'Scoring')} — ${PROJECT}` +
        '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
    }
    const nav = document.getElementById('nav');
    if (nav) {
      const active = opts.tab || 'Scoring';
      const item = (ic, label) =>
        `<a class="nav-item ${label === active ? 'active' : ''}" data-nav="${label}">${icon(ic)}${label}</a>`;
      nav.className = 'nav';
      nav.innerHTML =
        '<div class="nav-group">' + item('folder_open', 'Projects') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">MATCH</div>' +
        item('sports_tennis', 'Points') + item('scoreboard', 'Scoring') + item('bar_chart', 'Stats') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">VIDEO</div>' +
        item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export') + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
    initTips();
  }

  // ---------- Mockup states ----------
  const state = {};
  const stateListeners = [];
  function onState(fn) { stateListeners.push(fn); }
  function setState(group, value) {
    state[group] = value;
    document.querySelectorAll(`.mock-states[data-group="${group}"] button`).forEach((b) =>
      b.classList.toggle('is-selected', b.dataset.v === value));
    stateListeners.forEach((fn) => fn(group, value));
  }
  // A hash such as #format=custom&open=1 sets the first states. It helps to take screenshots.
  const hash = new URLSearchParams(location.hash.slice(1));

  // ---------- The still tab behind the dialogs ----------
  /** Draws a still copy of a tab: the video frame and a side column. [buttons] go to the foot of the side column. */
  function behind(host, buttons) {
    host.className = 'behind';
    host.innerHTML = `
      <div class="video"><img src="frame.jpg" alt=""></div>
      <aside class="side">
        <div class="skel" style="height:150px"><div class="skel-line" style="width:40%"></div><div class="skel-line"></div><div class="skel-line" style="width:70%"></div></div>
        <div class="skel" style="flex:1"><div class="skel-line" style="width:30%"></div><div class="skel-line"></div><div class="skel-line"></div><div class="skel-line" style="width:80%"></div><div class="skel-line"></div></div>
        ${buttons ? `<div class="foot">${buttons}</div>` : ''}
      </aside>`;
  }

  // ---------- Modal frame ----------
  let openModals = 0;
  /**
   * Shows [html] as a modal dialog over the app window.
   * Escape clicks [data-cancel] (or closes). Enter clicks [data-primary], except in a text area.
   */
  function modal(html, opts = {}) {
    const win = document.querySelector('.window') || document.body;
    const back = document.createElement('div');
    back.className = 'modal-back';
    back.innerHTML = html;
    win.appendChild(back);
    openModals++;
    const close = () => {
      if (!back.isConnected) return;
      back.remove();
      openModals--;
      if (opts.onClose) opts.onClose();
    };
    back.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        const c = back.querySelector('[data-cancel]');
        if (c) c.click(); else close();
      } else if (e.key === 'Enter' && !e.shiftKey && e.target.tagName !== 'TEXTAREA' && e.target.tagName !== 'BUTTON') {
        const p = back.querySelector('[data-primary]');
        if (p && !p.disabled) { e.preventDefault(); p.click(); }
      }
    });
    back.querySelectorAll('[data-close]').forEach((b) => b.addEventListener('click', close));
    setTimeout(() => {
      const f = back.querySelector('[data-focus]') || back.querySelector('input:not([type=range]):not(.check-input), textarea, select') || back.querySelector('.btn');
      if (f) { f.focus(); if (f.select && f.dataset.focus !== 'end') f.select(); }
    }, 0);
    return { root: back, close };
  }

  /**
   * One message box for all info, hint, warning, error and confirm messages.
   * kind: info | hint | warn | error | ask | danger.
   * buttons: [{ label, kind: 'lime' | 'danger' | '', cancel: true, primary: true, onClick }]. The default is one OK button.
   */
  function message({ kind = 'info', title, body, buttons, width, glyph }) {
    const ICON = { info: 'info', hint: 'lightbulb', warn: 'warning', error: 'error', ask: 'help', danger: 'delete' };
    const list = buttons || [{ label: 'OK', kind: 'lime', primary: true, cancel: true }];
    const html = `
      <div class="modal ${width || 'sm'}" role="${kind === 'ask' || kind === 'danger' ? 'alertdialog' : 'dialog'}" aria-label="${esc(title)}">
        <div class="msg ${kind}">
          <div class="msg-icon">${icon(glyph || ICON[kind], 'fill')}</div>
          <div><h3>${esc(title)}</h3>${body}</div>
        </div>
        <div class="modal-foot"><span class="grow"></span>
          ${list.map((b, i) => `<button class="btn ${b.kind ? 'btn-' + b.kind : ''}" data-i="${i}" ${b.primary ? 'data-primary' : ''} ${b.cancel ? 'data-cancel' : ''} ${b.focus ? 'data-focus' : ''}>${esc(b.label)}</button>`).join('')}
        </div>
      </div>`;
    const m = modal(html);
    m.root.querySelectorAll('.modal-foot .btn').forEach((el) => el.addEventListener('click', () => {
      const b = list[Number(el.dataset.i)];
      m.close();
      if (b.onClick) b.onClick();
    }));
    return m;
  }

  // ---------- Color picker (replaces JColorChooser) ----------
  const recent = [];

  function hsvToRgb(h, s, v) {
    const f = (n) => { const k = (n + h / 60) % 6; return v - v * s * Math.max(0, Math.min(k, 4 - k, 1)); };
    return [f(5), f(3), f(1)].map((x) => Math.round(x * 255));
  }
  function rgbToHsv(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
    let h = 0;
    if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [(h * 60 + 360) % 360, max ? d / max : 0, max];
  }
  function rgbToHsl(r, g, b) {
    const [h] = rgbToHsv(r, g, b);
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b), l = (max + min) / 2, d = max - min;
    return [h, d ? d / (1 - Math.abs(2 * l - 1)) : 0, l];
  }
  function hslToRgb(h, s, l) {
    const a = s * Math.min(l, 1 - l);
    const f = (n) => { const k = (n + h / 30) % 12; return l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1)); };
    return [f(0), f(8), f(4)].map((x) => Math.round(x * 255));
  }
  const toHex = (rgb) => '#' + rgb.map((x) => x.toString(16).padStart(2, '0')).join('').toUpperCase();
  const fromHex = (hex) => {
    const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
    return m ? [0, 2, 4].map((i) => parseInt(m[1].slice(i, i + 2), 16)) : null;
  };

  // A swatch grid like the JColorChooser palette: gray row, then hues in light to dark rows.
  function paletteColors() {
    const rows = [];
    rows.push(Array.from({ length: 12 }, (_, i) => toHex(hsvToRgb(0, 0, 1 - i / 11))));
    [[0.25, 1], [0.5, 1], [0.8, 1], [1, 0.85], [1, 0.6], [1, 0.38]].forEach(([s, v]) =>
      rows.push(Array.from({ length: 12 }, (_, i) => toHex(hsvToRgb(i * 30, s, v)))));
    return rows.flat();
  }

  /**
   * Opens the color picker. [onOk] gets the new #RRGGBB value.
   * The same dialog opens for the player colors, the scoreboard accent color and the comment color.
   */
  function colorPicker({ title, color, onOk, tab }) {
    const initial = (fromHex(color) ? color : '#FFFFFF').toUpperCase();
    let [h, s, v] = rgbToHsv(...fromHex(initial));
    let model = 'RGB';
    let view = tab || 'swatches';
    const m = modal(`
      <div class="modal cp" role="dialog" aria-label="${esc(title)}">
        <div class="modal-head"><h3>${esc(title)}</h3></div>
        <div class="modal-body">
          <div class="cp-compare">
            <div class="cp-chip" data-old title="Current color. Click to use it again."><span>Current</span><b class="mono"></b></div>
            <div class="cp-chip" data-new><span>New</span><b class="mono"></b></div>
          </div>
          <div class="seg" data-view><button data-v="swatches">Swatches</button><button data-v="custom">Custom</button></div>
          <div data-pane="swatches">
            <div class="cp-grid">${paletteColors().map((c) => `<button class="cp-sw" style="background:${c}" data-c="${c}" title="${c}"></button>`).join('')}</div>
            <div class="cp-recent-cap">Recent</div>
            <div class="cp-recent"></div>
          </div>
          <div data-pane="custom">
            <div class="cp-sv"><div class="cp-sv-dot"></div></div>
            <input type="range" class="cp-hue" min="0" max="359" aria-label="Hue">
            <div class="cp-fields">
              <div class="seg cp-model">${['HSV', 'HSL', 'RGB', 'CMYK'].map((x) => `<button data-m="${x}">${x}</button>`).join('')}</div>
              <div class="cp-nums"></div>
              <label class="cp-hex"><span>Hex</span><input class="inp mono" maxlength="7" spellcheck="false"></label>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-quiet" data-reset title="Go back to the current color">${icon('restart_alt')}Reset</button>
          <span class="grow"></span>
          <button class="btn" data-cancel data-close>Cancel</button>
          <button class="btn btn-lime" data-primary>OK</button>
        </div>
      </div>`);
    const r = m.root;
    const q = (sel) => r.querySelector(sel);
    const sv = q('.cp-sv'), dot = q('.cp-sv-dot'), hue = q('.cp-hue'), hexIn = q('.cp-hex input'), nums = q('.cp-nums');

    function fieldsFor(rgb) {
      const [R, G, B] = rgb;
      if (model === 'RGB') return [['Red', R, 255], ['Green', G, 255], ['Blue', B, 255]];
      if (model === 'HSV') return [['Hue', Math.round(h), 359], ['Saturation', Math.round(s * 100), 100], ['Value', Math.round(v * 100), 100]];
      if (model === 'HSL') { const [hh, ss, ll] = rgbToHsl(R, G, B); return [['Hue', Math.round(hh), 359], ['Saturation', Math.round(ss * 100), 100], ['Lightness', Math.round(ll * 100), 100]]; }
      const k = 1 - Math.max(R, G, B) / 255;
      const c = (x) => (k >= 1 ? 0 : Math.round(((1 - x / 255 - k) / (1 - k)) * 100));
      return [['Cyan', c(R), 100], ['Magenta', c(G), 100], ['Yellow', c(B), 100], ['Black', Math.round(k * 100), 100]];
    }
    function fromFields(vals) {
      if (model === 'RGB') return vals;
      if (model === 'HSV') return hsvToRgb(vals[0], vals[1] / 100, vals[2] / 100);
      if (model === 'HSL') return hslToRgb(vals[0], vals[1] / 100, vals[2] / 100);
      const k = vals[3] / 100;
      return vals.slice(0, 3).map((x) => Math.round(255 * (1 - x / 100) * (1 - k)));
    }
    function renderNums(rgb) {
      nums.style.gridTemplateColumns = `repeat(${model === 'CMYK' ? 4 : 3}, 1fr)`;
      nums.innerHTML = fieldsFor(rgb).map(([l, val, max]) =>
        `<label><span>${l}</span><input class="inp num" type="number" min="0" max="${max}" value="${val}"></label>`).join('');
      nums.querySelectorAll('input').forEach((el) => el.addEventListener('input', () => {
        const vals = [...nums.querySelectorAll('input')].map((x) => Math.max(0, Math.min(Number(x.max), Number(x.value) || 0)));
        [h, s, v] = rgbToHsv(...fromFields(vals));
        if (model === 'HSV' || model === 'HSL') h = vals[0];
        render(false);
      }));
    }
    function render(withNums = true) {
      const rgb = hsvToRgb(h, s, v);
      const hex = toHex(rgb);
      q('[data-new]').style.background = hex;
      q('[data-new] b').textContent = hex;
      q('[data-new]').classList.toggle('dark', v > 0.6 && s < 0.6);
      sv.style.background = `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, hsl(${h} 100% 50%))`;
      dot.style.left = s * 100 + '%';
      dot.style.top = (1 - v) * 100 + '%';
      dot.style.background = hex;
      hue.value = Math.round(h);
      if (document.activeElement !== hexIn) hexIn.value = hex;
      r.querySelectorAll('.cp-sw').forEach((b) => b.classList.toggle('is-selected', b.dataset.c === hex));
      if (withNums) renderNums(rgb);
    }
    function setHex(hex) { const rgb = fromHex(hex); if (rgb) { [h, s, v] = rgbToHsv(...rgb); render(); } }
    function showView() {
      r.querySelectorAll('[data-view] button').forEach((b) => b.classList.toggle('is-selected', b.dataset.v === view));
      r.querySelectorAll('[data-pane]').forEach((p) => { p.hidden = p.dataset.pane !== view; });
    }
    function showModel() {
      r.querySelectorAll('.cp-model button').forEach((b) => b.classList.toggle('is-selected', b.dataset.m === model));
      renderNums(hsvToRgb(h, s, v));
    }

    q('[data-old]').style.background = initial;
    q('[data-old] b').textContent = initial;
    q('[data-old]').classList.toggle('dark', (() => { const [, ss, vv] = rgbToHsv(...fromHex(initial)); return vv > 0.6 && ss < 0.6; })());
    q('[data-old]').addEventListener('click', () => setHex(initial));
    q('.cp-recent').innerHTML = recent.length
      ? recent.map((c) => `<button class="cp-sw" style="background:${c}" data-c="${c}" title="${c}"></button>`).join('')
      : '<span class="faint">The colors that you select show here.</span>';
    r.querySelectorAll('.cp-sw').forEach((b) => {
      b.addEventListener('click', () => setHex(b.dataset.c));
      b.addEventListener('dblclick', () => q('[data-primary]').click());
    });
    r.querySelectorAll('[data-view] button').forEach((b) => b.addEventListener('click', () => { view = b.dataset.v; showView(); }));
    r.querySelectorAll('.cp-model button').forEach((b) => b.addEventListener('click', () => { model = b.dataset.m; showModel(); }));
    hue.addEventListener('input', () => { h = Number(hue.value); render(); });
    hexIn.addEventListener('input', () => setHex(hexIn.value));
    const pick = (e) => {
      const b = sv.getBoundingClientRect();
      s = Math.max(0, Math.min(1, (e.clientX - b.left) / b.width));
      v = Math.max(0, Math.min(1, 1 - (e.clientY - b.top) / b.height));
      render();
    };
    sv.addEventListener('pointerdown', (e) => { sv.setPointerCapture(e.pointerId); pick(e); });
    sv.addEventListener('pointermove', (e) => { if (e.buttons) pick(e); });
    q('[data-reset]').addEventListener('click', () => setHex(initial));
    q('[data-primary]').addEventListener('click', () => {
      const hex = toHex(hsvToRgb(h, s, v));
      const i = recent.indexOf(hex);
      if (i >= 0) recent.splice(i, 1);
      recent.unshift(hex);
      recent.length = Math.min(recent.length, 12);
      m.close();
      if (onOk) onOk(hex);
    });
    showView();
    showModel();
    render();
    setTimeout(() => q('[data-primary]').focus(), 0);
    return m;
  }

  // ---------- Tooltips ----------
  let tipEl = null;
  function initTips() {
    if (initTips.done) return;
    initTips.done = true;
    document.addEventListener('mouseover', (e) => {
      const t = e.target.closest('[data-tip]');
      if (!t) { if (tipEl) { tipEl.remove(); tipEl = null; } return; }
      if (!tipEl) { tipEl = document.createElement('div'); tipEl.className = 'tip'; document.body.appendChild(tipEl); }
      tipEl.textContent = t.dataset.tip;
      const b = t.getBoundingClientRect();
      tipEl.style.left = Math.min(window.innerWidth - tipEl.offsetWidth - 8, b.left) + 'px';
      tipEl.style.top = (b.bottom + 6) + 'px';
    });
  }

  // ---------- Toast (mockup only) ----------
  let toastTimer = 0;
  function toast(text) {
    let el = document.querySelector('.toast');
    if (!el) { el = document.createElement('div'); el.className = 'toast'; document.body.appendChild(el); }
    el.innerHTML = icon('check_circle') + `<span>${esc(text)}</span>`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.remove(), 3200);
  }

  window.Mock = {
    APP_NAME, PROJECT, esc, icon, shell, behind, modal, message, colorPicker, toast,
    state, setState, onState, hash, fromHex, toHex, hsvToRgb, rgbToHsv,
    get openModals() { return openModals; },
  };
})();
