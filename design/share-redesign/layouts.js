/*
 * Script of the final Share tab design (final.html).
 * It holds the match data, the text fields with their presets, and the layout styles of the thumbnail.
 * Each layout style draws its texts in the 1280 × 720 coordinates of the PNG.
 */
(function () {
  const PROJECT = 'Dima-Yana = Iliya-Marina';
  const LENGTH = { full: '38:12', best: '6:41' };

  // The points of the mockup. frame: frame-1.jpg or frame-2.jpg.
  const POINTS = [
    { n: 4, score: 'Set 1 · 1–0 · 15–30', fav: false, at: 95, len: 6.1, frame: 2 },
    { n: 11, score: 'Set 1 · 2–2 · 40–40', fav: true, at: 402, len: 14.8, frame: 1 },
    { n: 19, score: 'Set 1 · 3–4 · 0–15', fav: false, at: 711, len: 5.2, frame: 2 },
    { n: 27, score: 'Set 1 · 5–5 · 30–40', fav: true, at: 1032, len: 11.4, frame: 2 },
    { n: 36, score: 'Set 1 · Tiebreak · 4–5', fav: false, at: 1388, len: 7.9, frame: 1 },
    { n: 41, score: 'Set 2 · 0–1 · 15–0', fav: false, at: 1604, len: 4.6, frame: 1 },
    { n: 48, score: 'Set 2 · 2–1 · 15–0', fav: true, at: 1800, len: 9.3, frame: 1 },
    { n: 55, score: 'Set 2 · 3–3 · 40–30', fav: false, at: 2093, len: 6.7, frame: 2 },
    { n: 63, score: 'Set 2 · 5–4 · 40–15', fav: true, at: 2410, len: 12.2, frame: 2 },
    { n: 70, score: 'Match tiebreak · 3–2', fav: false, at: 2702, len: 5.5, frame: 1 },
    { n: 74, score: 'Match tiebreak · 7–6', fav: true, at: 2861, len: 10.1, frame: 1 },
    { n: 78, score: 'Match tiebreak · 9–8', fav: true, at: 3012, len: 13.6, frame: 2 },
  ];

  /**
   * The text fields. preset(state) gives the preset value, source tells the user where the preset comes from.
   * The layouts show a field only when it is not empty.
   */
  const FIELDS = [
    { key: 'head', label: 'Large words', source: 'From the video type', max: 24,
      preset: (s) => (s.type === 'full' ? 'FULL MATCH' : 'BEST POINTS') },
    { key: 'kind', label: 'Match type', source: 'From the match', max: 24, preset: () => 'DOUBLES' },
    { key: 'team1', label: 'Player or team 1', source: 'From the players', max: 40,
      preset: (s) => (s.mode === 'long' ? 'Aleksandra Konstantinova / Margarita Belousova' : 'Dima / Yana') },
    { key: 'team2', label: 'Player or team 2', source: 'From the players', max: 40,
      preset: (s) => (s.mode === 'long' ? 'Iliya Vladimirovich / Marina Starostina' : 'Iliya / Marina') },
    { key: 'event', label: 'League or event', source: 'From your last thumbnail', max: 40,
      preset: (s) => (s.mode === 'first' ? '' : 'Friday doubles league'), placeholder: 'For example: Club league, round 3' },
    { key: 'court', label: 'Court', source: 'From the scoreboard title', max: 40, preset: () => 'Batumi : Court Airport' },
    { key: 'date', label: 'Date', source: 'From the video file', max: 24, preset: () => '11 Oct 2026' },
  ];

  // set: the default color set of the style. use: the color roles of the style, with the parts that use each role.
  const LAYOUTS = [
    { id: 'words', name: 'Big words', fields: ['head', 'kind'], set: 'lime',
      use: { text: 'Match type', acc: 'Large words', shade: 'Behind the words' } },
    { id: 'versus', name: 'Versus', fields: ['head', 'team1', 'team2'], set: 'lime',
      use: { text: 'Players', acc: 'Tag and "VS"', onAcc: 'Words on the tag', shade: 'At the left' } },
    { id: 'card', name: 'Match card', fields: ['head', 'event', 'court', 'date', 'team1', 'team2'], set: 'lime',
      use: { acc: 'Edge, league, large words', pan: 'Card', onPan: 'Players, court, date' } },
    { id: 'split', name: 'Split', fields: ['head', 'kind', 'event'], set: 'lime',
      use: { acc: 'Panel', onAcc: 'All texts' } },
    { id: 'tv', name: 'Broadcast', fields: ['head', 'team1', 'team2', 'event', 'court'], set: 'tv',
      use: { acc: 'Tag and "vs"', onAcc: 'Words on the tag', pan: 'Player bar, info text', onPan: 'Players, info bar', shade: 'At the bottom' } },
    { id: 'poster', name: 'Poster', fields: ['head', 'kind', 'date', 'team1', 'team2'], set: 'lime',
      use: { text: 'Large words, players', acc: 'Match type, date, "vs"', shade: 'Top and bottom' } },
  ];

  /**
   * The color roles. All styles use the same roles. The Colors tab shows only the roles of the style.
   * A role with auto can be "Auto": the app selects dark or white, from the contrast with the color under it.
   */
  const INK = [['#ffffff', 'White'], ['#141414', 'Black'], ['#0b1e3a', 'Navy'], ['#c8ec46', 'Lime'], ['#ffd54a', 'Yellow']];
  const ROLES = [
    { key: 'text', name: 'Text', swatches: [['#ffffff', 'White'], ['#d9d9d9', 'Light gray'], ['#141414', 'Black'], ['#c8ec46', 'Lime'], ['#ffd54a', 'Yellow']] },
    { key: 'acc', name: 'Accent', swatches: [['#c8ec46', 'Lime'], ['#ffd54a', 'Yellow'], ['#ff8a3d', 'Orange'], ['#ff4d4d', 'Red'],
      ['#ff5fa2', 'Pink'], ['#3dd6f5', 'Cyan'], ['#4d8bff', 'Blue'], ['#ffffff', 'White']] },
    { key: 'onAcc', name: 'Text on accent', auto: 'acc', swatches: INK },
    { key: 'pan', name: 'Panel', swatches: [['#0e1014', 'Black'], ['#0b1e3a', 'Navy'], ['#0f2a1c', 'Dark green'], ['#3a0d14', 'Dark red'],
      ['#24123d', 'Purple'], ['#ffffff', 'White']] },
    { key: 'onPan', name: 'Text on panel', auto: 'pan', swatches: INK },
    { key: 'shade', name: 'Shade', strength: true, swatches: [['#0a0e14', 'Black'], ['#0b1e3a', 'Navy'], ['#0f2a1c', 'Dark green'], ['#3a0d14', 'Dark red'], ['#ffffff', 'White']] },
  ];

  /** The color sets. A set gives a value to each role. The "Text on" roles are Auto. */
  const COLOR_SETS = [
    { id: 'lime', name: 'Lime', text: '#ffffff', acc: '#c8ec46', pan: '#0e1014', shade: '#0a0e14' },
    { id: 'tv', name: 'Broadcast', text: '#ffffff', acc: '#ffd54a', pan: '#0b1e3a', shade: '#0a0e14' },
    { id: 'sunset', name: 'Sunset', text: '#ffffff', acc: '#ff8a3d', pan: '#3a0d14', shade: '#3a0d14' },
    { id: 'ocean', name: 'Ocean', text: '#ffffff', acc: '#3dd6f5', pan: '#0b1e3a', shade: '#0b1e3a' },
    { id: 'red', name: 'Red', text: '#ffffff', acc: '#ff4d4d', pan: '#0e1014', shade: '#0a0e14' },
    { id: 'light', name: 'Light', text: '#141414', acc: '#4d8bff', pan: '#ffffff', shade: '#ffffff' },
  ];

  const MODES = [['normal', 'Normal'], ['first', 'First thumbnail'], ['long', 'Long names'], ['nofav', 'No favorites'], ['error', 'Frame read error']];

  const state = {
    mode: 'normal',
    layout: 'words',
    type: 'best',      // 'full' or 'best'. The default is 'best' when the project has favorites.
    point: null,       // index in points(); null = the default point
    pos: 0.5,          // the moment in the point: 0 = start, 1 = end
    values: {},        // the fields that the user edited
    colors: {},        // the colors of each style: { card: { set: 'ocean', acc: '#ff4d4d', k: 0.8 } }. A role value overrides the set.
    saved: null,
    tab: 'texts',      // the tab of the side column: 'texts' or 'colors'
  };

  let renderFn = null;
  function render() { if (renderFn) renderFn(); }
  function set(patch) { Object.assign(state, patch); render(); }

  function points() {
    return state.mode === 'nofav' ? POINTS.map((p) => ({ ...p, fav: false })) : POINTS;
  }
  /** The middle of the first favorite point. Without favorites, the point near the middle of the match. */
  function defaultPoint() {
    const fav = points().findIndex((p) => p.fav);
    return fav >= 0 ? fav : Math.floor(points().length / 2);
  }
  function pointIndex() { return state.point == null ? defaultPoint() : state.point; }
  function current() { return points()[pointIndex()]; }

  function field(key) { return FIELDS.find((f) => f.key === key); }
  function value(key) { return key in state.values ? state.values[key] : field(key).preset(state); }
  function edited(key) { return key in state.values && state.values[key] !== field(key).preset(state); }
  function layout() { return LAYOUTS.find((l) => l.id === state.layout); }

  function clock(sec) { return `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`; }
  function clockTenths(sec) { return `${clock(sec)}.${Math.floor((sec * 10) % 10)}`; }
  function pointLabel(p) { return `Point ${p.n} · ${clock(p.at)} · ${p.score}`; }
  function fileName() { return `${PROJECT}-${state.type === 'full' ? 'full-match' : 'best-points'}.png`; }

  function esc(s) {
    return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  // ---------- Text rows ----------
  const measure = document.createElement('canvas').getContext('2d');
  function textWidth(text, size) {
    measure.font = `900 ${size}px "Segoe UI Black", "Segoe UI", sans-serif`;
    return measure.measureText(text).width;
  }
  /** The large words in one or two rows, with the split that gives the narrowest block. */
  function rows(text) {
    const words = text.trim().split(/\s+/).filter(Boolean);
    if (words.length < 2) return [words.join(' ')];
    let best = null;
    for (let i = 1; i < words.length; i++) {
      const r = [words.slice(0, i).join(' '), words.slice(i).join(' ')];
      const w = Math.max(...r.map((x) => textWidth(x, 100)));
      if (!best || w < best.w) best = { r, w };
    }
    return best.r;
  }

  // ---------- Layout styles ----------
  // fit(max): the app makes the text smaller until it is not wider than max (in PNG pixels).
  const fit = (max) => `data-fit="${max}"`;
  const up = (key) => esc(value(key).trim().toUpperCase());
  const has = (key) => value(key).trim() !== '';

  const DRAW = {
    words() {
      const head = rows(value('head').toUpperCase()).map((r) => `<div><span class="fit black big" ${fit(620)}>${esc(r)}</span></div>`).join('');
      const kind = has('kind') ? `<div><span class="fit black small" ${fit(620)}>${up('kind')}</span></div>` : '';
      return `<div class="shade"></div><div class="words">${kind}${head}</div>`;
    },
    versus() {
      const tag = has('head') ? `<span class="tag black fit" ${fit(640)}>${up('head')}</span>` : '';
      const t1 = has('team1') ? `<span class="team black fit" ${fit(640)}>${up('team1')}</span>` : '';
      const t2 = has('team2') ? `<span class="team black fit" ${fit(640)}>${up('team2')}</span>` : '';
      const vs = t1 && t2 ? '<span class="vs black">VS</span>' : '';
      return `<div class="shade"></div>${tag}<div class="names">${t1}${vs}${t2}</div>`;
    },
    card() {
      const meta = [value('court'), value('date')].map((x) => x.trim()).filter(Boolean).map(esc).join('  ·  ');
      let html = '';
      if (has('event')) html += `<div><span class="event fit" ${fit(508)}>${esc(value('event'))}</span></div>`;
      if (meta) html += `<div><span class="meta fit" ${fit(508)}>${meta}</span></div>`;
      if (html) html += '<hr>';
      if (has('team1')) html += `<div><span class="team black fit" ${fit(508)}>${esc(value('team1'))}</span></div>`;
      if (has('team1') && has('team2')) html += '<div class="vs">vs</div>';
      if (has('team2')) html += `<div><span class="team black fit" ${fit(508)}>${esc(value('team2'))}</span></div>`;
      if (has('head')) html += `<div><span class="head black fit" ${fit(508)}>${up('head')}</span></div>`;
      return `<div class="card">${html}</div>`;
    },
    split() {
      const kind = has('kind') ? `<div><span class="small black fit" ${fit(440)}>${up('kind')}</span></div>` : '';
      const head = rows(value('head').toUpperCase()).map((r) => `<div><span class="big black fit" ${fit(460)}>${esc(r)}</span></div>`).join('');
      const event = has('event') ? `<span class="event fit" ${fit(410)}>${esc(value('event'))}</span>` : '';
      return `<div class="panel"></div><div class="text">${kind}${head}</div>${event}`;
    },
    tv() {
      const tag = has('head') ? `<span class="tag black fit" ${fit(800)}>${up('head')}</span>` : '';
      const teams = [value('team1'), value('team2')].map((x) => x.trim()).filter(Boolean).map(esc);
      // Long names go in two rows, so that they stay large.
      const long = teams.join('').length > 30;
      const bar = !teams.length ? '' : long
        ? teams.map((t, i) => `<span class="bar fit" ${fit(900)}>${i ? '<span class="vs">vs</span>' : ''}${t}</span>`).join('')
        : `<span class="bar fit" ${fit(900)}>${teams.join('<span class="vs">vs</span>')}</span>`;
      const info = [value('event'), value('court')].map((x) => x.trim()).filter(Boolean).map(esc).join('  ·  ');
      return `<div class="shade"></div><div class="stack">${tag}${bar}${info ? `<span class="info fit" ${fit(900)}>${info}</span>` : ''}</div>`;
    },
    poster() {
      const line = [value('kind'), value('date')].map((x) => x.trim()).filter(Boolean).map((x) => esc(x.toUpperCase())).join('  ·  ');
      const teams = [value('team1'), value('team2')].map((x) => x.trim()).filter(Boolean).map(esc);
      return '<div class="shade"></div><div class="top">' +
        (has('head') ? `<span class="head black fit" ${fit(1180)}>${up('head')}</span>` : '') +
        (line ? `<span class="line">${line}</span>` : '') + '</div>' +
        (teams.length ? `<span class="teams fit" ${fit(900)}>${teams.join('<span class="vs">vs</span>')}</span>` : '');
    },
  };

  /** Makes each [data-fit] text smaller until it is not wider than its limit. */
  function applyFit(root) {
    let shrunk = false;
    root.querySelectorAll('[data-fit]').forEach((el) => {
      const max = Number(el.dataset.fit);
      let size = parseFloat(getComputedStyle(el).fontSize);
      for (let i = 0; i < 40 && el.scrollWidth > max; i++) {
        size *= 0.95;
        el.style.fontSize = size + 'px';
        shrunk = true;
      }
    });
    return shrunk;
  }

  // ---------- Colors ----------
  function layoutById(id) { return LAYOUTS.find((x) => x.id === id); }
  function userColors(layoutId) { return state.colors[layoutId] || {}; }
  /** The color set of the style: the set that the user selected, or the default set. */
  function activeSet(layoutId) {
    const id = userColors(layoutId).set || layoutById(layoutId).set;
    return COLOR_SETS.find((x) => x.id === id);
  }
  /** True when the user changed a role after the set. */
  function customized(layoutId) {
    return ROLES.some((r) => r.key in userColors(layoutId)) || 'k' in userColors(layoutId);
  }
  /** The value of a role as the user selected it: a color, or "auto". */
  function roleValue(layoutId, key) {
    const user = userColors(layoutId);
    if (key in user) return user[key];
    if (key === 'onAcc' || key === 'onPan') return 'auto';
    return activeSet(layoutId)[key];
  }
  function strength(layoutId) { const k = userColors(layoutId).k; return k == null ? 1 : k; }
  /** All colors of a style, with "auto" changed to dark or white. */
  function colors(layoutId) {
    const c = {};
    ROLES.forEach((r) => { c[r.key] = roleValue(layoutId, r.key); });
    if (c.onAcc === 'auto') c.onAcc = onColor(c.acc);
    if (c.onPan === 'auto') c.onPan = onColor(c.pan);
    c.k = strength(layoutId);
    return c;
  }
  function colorsEdited(layoutId) { return !!state.colors[layoutId]; }
  function setColor(role, valueOrAuto) {
    set({ colors: { ...state.colors, [state.layout]: { ...userColors(state.layout), [role]: valueOrAuto } }, saved: null });
  }
  /** Selects a color set. It removes the role changes of the style. */
  function applySet(id) {
    set({ colors: { ...state.colors, [state.layout]: { set: id } }, saved: null });
  }
  function resetColors() {
    const colors = { ...state.colors };
    delete colors[state.layout];
    set({ colors, saved: null });
  }
  function luminance(hex) {
    const ch = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
      .map((v) => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)));
    return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
  }
  function contrast(a, b) {
    const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
    return (x + 0.05) / (y + 0.05);
  }
  /** The text color on a background: dark or white, the one with more contrast. */
  function onColor(bg) { return contrast(bg, '#ffffff') >= contrast(bg, '#141414') ? '#ffffff' : '#141414'; }
  function rgb(hex) { return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)).join(', '); }

  /** The inner HTML of a 1280 × 720 thumbnail in a layout style. */
  function thumbHtml(layoutId, opts = {}) {
    if (state.mode === 'error') {
      return '<div class="msg"><div><span class="material-symbols-outlined">broken_image</span>' +
        'The app cannot read this frame of the video.<br>Select another point or another moment.</div></div>';
    }
    const c = colors(layoutId);
    const vars = `--text:${c.text};--acc:${c.acc};--on-acc:${c.onAcc};--pan:${c.pan};--on-pan:${c.onPan};--sh:${rgb(c.shade)};--k:${c.k}`;
    let html = `<img class="frame" src="frame-${current().frame}.jpg" alt="">` +
      `<div class="L L-${layoutId}" style="${vars}">${DRAW[layoutId]()}</div>`;
    if (opts.ytBadge) html += `<div class="yt-length">${LENGTH[state.type]}</div>`;
    return html;
  }

  /** Puts a thumbnail in a .thumb-box, keeps its scale equal to the box width, and returns true when a text was made smaller. */
  function mountThumb(box, layoutId, opts) {
    let thumb = box.querySelector('.thumb');
    if (!thumb) {
      thumb = document.createElement('div');
      thumb.className = 'thumb';
      box.appendChild(thumb);
      new ResizeObserver(() => { thumb.style.transform = `scale(${box.clientWidth / 1280})`; }).observe(box);
    }
    thumb.style.transform = `scale(${box.clientWidth / 1280})`;
    thumb.innerHTML = thumbHtml(layoutId, opts);
    return applyFit(thumb);
  }

  // ---------- Small parts ----------
  function icon(name, cls = '') { return `<span class="material-symbols-outlined ${cls}">${name}</span>`; }

  let toastTimer = null;
  function toast(text, iconName = 'check_circle') {
    let el = document.querySelector('.toast');
    if (!el) {
      el = document.createElement('div');
      el.className = 'toast';
      document.body.appendChild(el);
    }
    el.innerHTML = icon(iconName) + esc(text);
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 2200);
  }

  function shell() {
    const bar = document.getElementById('mock-bar');
    bar.className = 'mock-bar';
    bar.innerHTML = '<a href="index.html">← Overview</a><a class="current" href="final.html">Final · Layout styles</a>' +
      '<span class="spacer"></span><span class="mock-label">Mockup state</span><div class="mock-states">' +
      MODES.map(([v, l]) => `<button data-mode="${v}" class="${state.mode === v ? 'is-selected' : ''}">${l}</button>`).join('') + '</div>';
    bar.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => {
      bar.querySelectorAll('[data-mode]').forEach((x) => x.classList.toggle('is-selected', x === b));
      set({ mode: b.dataset.mode, type: b.dataset.mode === 'nofav' ? 'full' : 'best', point: null, saved: null, values: {}, colors: {} });
    }));
    const title = document.getElementById('titlebar');
    title.className = 'titlebar';
    title.innerHTML = `<span class="logo"></span>BananaShot — Share — ${PROJECT}` +
      '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
    const nav = document.getElementById('nav');
    const item = (i, label, active) => `<a class="nav-item ${active ? 'active' : ''}">${icon(i)}${label}</a>`;
    nav.className = 'nav';
    nav.innerHTML =
      '<div class="nav-group">' + item('folder_open', 'Projects') + '</div>' +
      '<div class="nav-group"><div class="nav-caption">MATCH</div>' +
      item('sports_tennis', 'Points') + item('scoreboard', 'Scoring') + item('bar_chart', 'Stats') + '</div>' +
      '<div class="nav-group"><div class="nav-caption">VIDEO</div>' + item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
      '<div class="nav-group">' + item('movie', 'Export') + item('share', 'Share', true) + '</div>' +
      '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
  }

  // A hash such as #layout=card&mode=long sets the first state. It helps to take screenshots.
  new URLSearchParams(location.hash.slice(1)).forEach((v, key) => {
    if (key === 'mode') { state.mode = v; if (v === 'nofav') state.type = 'full'; }
    if (key === 'layout' || key === 'type') state[key] = v;
    if (key === 'set') state.colors[state.layout] = { set: v };
    if (key === 'acc' || key === 'pan' || key === 'text') state.colors[state.layout] = { ...state.colors[state.layout], [key]: '#' + v };
    if (key === 'tab') state.tab = v;
  });

  window.Mock = {
    LAYOUTS, FIELDS, ROLES, COLOR_SETS, colors, colorsEdited, customized, activeSet, roleValue, strength,
    setColor, applySet, resetColors, state, set, render, points, current, pointIndex, pointLabel, clockTenths,
    field, value, edited, layout, fileName, mountThumb, icon, esc, toast, shell,
    onRender(fn) { renderFn = fn; },
  };
})();
