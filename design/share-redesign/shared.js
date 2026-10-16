/*
 * Shared script for the Share tab mockups (B-46).
 * It holds the match data, draws the thumbnail as the app will draw it, makes the description text,
 * and draws the app shell. Each option page registers its own render function with Mock.onRender().
 */
(function () {
  const PROJECT = 'Dima-Yana = Iliya-Marina';
  const SITE = 'https://banana-shot-editor.app/';
  const TITLE = 'BATUMI : COURT AIRPORT';
  const TEAMS = [
    { board: 'DIMA / YANA', text: 'Dima / Yana', color: '#8a6a3a' },
    { board: 'ILIYA / MARINA', text: 'Iliya / Marina', color: '#f5e14a' },
  ];
  /** The final score, from the side of the winner (team 1). */
  const RESULT = { winner: 0, sets: ['6–7', '6–4', '10–8'] };
  /** The length of the exported video for each video type, for the YouTube length badge. */
  const LENGTH = { full: '38:12', best: '6:41' };

  // The points of the mockup. sets: the games of each set, the last one is the current set.
  // mtb: a match tiebreak, so the board shows no games for the current set. frame: frame-1.jpg or frame-2.jpg.
  const POINTS = [
    { n: 4, sets: [[1, 0]], pts: ['15', '30'], srv: 1, fav: false, at: 95, len: 6.1, frame: 2 },
    { n: 11, sets: [[2, 2]], pts: ['40', '40'], srv: 0, fav: true, at: 402, len: 14.8, frame: 1 },
    { n: 19, sets: [[3, 4]], pts: ['0', '15'], srv: 1, fav: false, at: 711, len: 5.2, frame: 2 },
    { n: 27, sets: [[5, 5]], pts: ['30', '40'], srv: 0, fav: true, at: 1032, len: 11.4, frame: 2 },
    { n: 36, sets: [[6, 6]], pts: ['4', '5'], srv: 1, fav: false, at: 1388, len: 7.9, frame: 1 },
    { n: 41, sets: [[6, 7], [0, 1]], pts: ['15', '0'], srv: 1, fav: false, at: 1604, len: 4.6, frame: 1 },
    { n: 48, sets: [[6, 7], [2, 1]], pts: ['15', '0'], srv: 0, fav: true, at: 1800, len: 9.3, frame: 1 },
    { n: 55, sets: [[6, 7], [3, 3]], pts: ['40', '30'], srv: 1, fav: false, at: 2093, len: 6.7, frame: 2 },
    { n: 63, sets: [[6, 7], [5, 4]], pts: ['40', '15'], srv: 0, fav: true, at: 2410, len: 12.2, frame: 2 },
    { n: 70, sets: [[6, 7], [6, 4], [0, 0]], pts: ['3', '2'], srv: 0, fav: false, mtb: true, at: 2702, len: 5.5, frame: 1 },
    { n: 74, sets: [[6, 7], [6, 4], [0, 0]], pts: ['7', '6'], srv: 1, fav: true, mtb: true, at: 2861, len: 10.1, frame: 1 },
    { n: 78, sets: [[6, 7], [6, 4], [0, 0]], pts: ['9', '8'], srv: 0, fav: true, mtb: true, at: 3012, len: 13.6, frame: 2 },
  ];
  const MODES = [['normal', 'Normal'], ['nofav', 'No favorites'], ['noscore', 'No scored points'], ['error', 'Frame read error']];

  const state = {
    mode: 'normal',
    type: 'best',          // 'full' or 'best'. The default is 'best' when the project has favorites.
    point: null,           // index in points(); null = the default point
    pos: 0.5,              // the frame in the point: 0 = start, 1 = end
    small: null,           // the small line, when the user edited it
    big: null,             // the large line, when the user edited it
    showResult: true,
    description: null,     // the description, when the user edited it
    saved: null,           // the file name after "Save"
    ytBadge: true,         // the preview shows the YouTube length badge
  };

  let renderFn = null;
  function render() { if (renderFn) renderFn(); }
  function set(patch) { Object.assign(state, patch); render(); }

  // ---------- Data for the current mode ----------
  function points() {
    if (state.mode === 'nofav') return POINTS.map((p) => ({ ...p, fav: false }));
    if (state.mode === 'noscore') return POINTS.map((p) => ({ ...p, unscored: true }));
    return POINTS;
  }
  function scored() { return state.mode !== 'noscore'; }

  /** The middle of the first favorite point. Without favorites, the point near the middle of the match. */
  function defaultPoint() {
    const list = points();
    const fav = list.findIndex((p) => p.fav);
    return fav >= 0 ? fav : Math.floor(list.length / 2);
  }
  function pointIndex() { return state.point == null ? defaultPoint() : state.point; }
  function current() { return points()[pointIndex()]; }

  // ---------- Texts ----------
  const PRESET_SMALL = 'DOUBLES';
  function presetBig() { return state.type === 'full' ? 'FULL MATCH' : 'BEST POINTS'; }
  function smallText() { return state.small == null ? PRESET_SMALL : state.small; }
  function bigText() { return state.big == null ? presetBig() : state.big; }

  function clock(sec) {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}:${String(s).padStart(2, '0')}`;
  }
  function clockTenths(sec) {
    return `${clock(sec)}.${Math.floor((sec * 10) % 10)}`;
  }
  /** "Set 2 · 2–1 · 15–0", from the side of team 1. */
  function scoreLabel(p) {
    if (p.unscored) return 'Not scored';
    const g = p.sets[p.sets.length - 1];
    const games = p.mtb ? 'Match tiebreak' : `${g[0]}–${g[1]}`;
    return `Set ${p.sets.length} · ${games} · ${p.pts[0]}–${p.pts[1]}`;
  }
  function pointLabel(p) {
    return `Point ${p.n} · ${clock(p.at)} · ${scoreLabel(p)}`;
  }

  function autoDescription() {
    const what = state.type === 'full'
      ? 'The full match, without the time between points.'
      : 'The best points of the match.';
    const lines = [`Tennis doubles: ${TEAMS[0].text} vs ${TEAMS[1].text}. ${what}`];
    if (state.showResult && scored()) lines.push(`Result: ${TEAMS[RESULT.winner].text} won ${RESULT.sets.join(', ')}.`);
    lines.push('', 'Edited with BananaShot, a free app for match videos:', SITE);
    return lines.join('\n');
  }
  function description() { return state.description == null ? autoDescription() : state.description; }

  function fileName() {
    return `${PROJECT}-${state.type === 'full' ? 'full-match' : 'best-points'}.png`;
  }

  // ---------- The thumbnail ----------
  const MAX_TEXT_WIDTH = 600;
  const SMALL_SIZE = 64;
  const BIG_SIZE = 128;
  const measure = document.createElement('canvas').getContext('2d');
  function textWidth(text, size, spacing) {
    measure.font = `900 ${size}px "Segoe UI Black", "Segoe UI", sans-serif`;
    return measure.measureText(text).width + spacing * text.length;
  }
  /** The large line in one or two rows, with the split that gives the narrowest block. */
  function bigRows(text) {
    const words = text.trim().split(/\s+/).filter(Boolean);
    if (words.length < 2) return [words.join(' ')];
    let best = null;
    for (let i = 1; i < words.length; i++) {
      const rows = [words.slice(0, i).join(' '), words.slice(i).join(' ')];
      const w = Math.max(...rows.map((r) => textWidth(r, BIG_SIZE, -1)));
      if (!best || w < best.w) best = { rows, w };
    }
    return best.rows;
  }
  /** The font sizes after the fit: long text becomes smaller, so it does not cover the player. */
  function fit() {
    const small = smallText().trim().toUpperCase();
    const rows = bigRows(bigText().toUpperCase());
    const sw = textWidth(small, SMALL_SIZE, 1);
    const bw = Math.max(0, ...rows.map((r) => textWidth(r, BIG_SIZE, -1)));
    const smallSize = sw > MAX_TEXT_WIDTH ? Math.floor(SMALL_SIZE * MAX_TEXT_WIDTH / sw) : SMALL_SIZE;
    const bigSize = bw > MAX_TEXT_WIDTH ? Math.floor(BIG_SIZE * MAX_TEXT_WIDTH / bw) : BIG_SIZE;
    return { small, rows, smallSize, bigSize, shrunk: smallSize < SMALL_SIZE || bigSize < BIG_SIZE };
  }

  function esc(s) {
    return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  function boardHtml(p) {
    const showScore = !p.unscored;
    const done = p.sets.slice(0, -1);
    const cur = p.sets[p.sets.length - 1];
    const cols = showScore ? done.length + (p.mtb ? 0 : 1) : 0;
    const template = `34px auto 30px ${'46px '.repeat(cols)}${showScore ? '88px' : ''}`;
    const row = (t) => {
      const team = TEAMS[t];
      let cells = `<span class="bar" style="--tc:${team.color}"></span><span class="name">${team.board}</span>`;
      cells += showScore && p.srv === t ? '<span class="serve"></span>' : '<span></span>';
      if (showScore) {
        done.forEach((s) => { cells += `<span class="set ${s[t] < s[1 - t] ? 'lost' : ''}">${s[t]}</span>`; });
        if (!p.mtb) cells += `<span class="set">${cur[t]}</span>`;
        cells += `<span class="pts">${p.pts[t]}</span>`;
      }
      return `<div class="row" style="grid-template-columns:${template}">${cells}</div>`;
    };
    return `<div class="board"><div class="title">${TITLE}</div><div class="body">${row(0)}${row(1)}` +
      '<div class="credit">BananaShot app</div></div></div>';
  }

  /** The inner HTML of a 1280 × 720 thumbnail. ytBadge: the preview shows the YouTube length badge (not in the PNG). */
  function thumbHtml(opts = {}) {
    if (state.mode === 'error') {
      return '<div class="msg"><div><span class="material-symbols-outlined">broken_image</span>' +
        'The app cannot read this frame of the video.<br>Select another point or another moment.</div></div>';
    }
    const p = current();
    const f = fit();
    const rows = f.rows.map((r) => `<div class="big" style="font-size:${f.bigSize}px">${esc(r)}</div>`).join('');
    const small = f.small ? `<div class="small" style="font-size:${f.smallSize}px">${esc(f.small)}</div>` : '';
    let html = `<img class="frame" src="frame-${p.frame}.jpg" alt="">` +
      '<div class="shade"></div>' + boardHtml(p) + `<div class="words">${small}${rows}</div>`;
    if (opts.zone) html += '<div class="yt-zone"></div>';
    if (opts.ytBadge) html += `<div class="yt-length">${LENGTH[state.type]}</div>`;
    return html;
  }

  /** Puts a thumbnail in a .thumb-box and keeps its scale equal to the box width. */
  function mountThumb(box, opts) {
    let thumb = box.querySelector('.thumb');
    if (!thumb) {
      thumb = document.createElement('div');
      thumb.className = 'thumb';
      box.appendChild(thumb);
      new ResizeObserver(() => { thumb.style.transform = `scale(${box.clientWidth / 1280})`; }).observe(box);
    }
    thumb.style.transform = `scale(${box.clientWidth / 1280})`;
    thumb.innerHTML = thumbHtml(opts);
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

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'option-a.html', id: 'a', label: 'A · Preview + side column (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Point strip + two cards' },
  ];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML = '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span><span class="mock-label">Mockup state</span><div class="mock-states">' +
        MODES.map(([v, l]) => `<button data-mode="${v}" class="${state.mode === v ? 'is-selected' : ''}">${l}</button>`).join('') + '</div>';
      bar.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => {
        bar.querySelectorAll('[data-mode]').forEach((x) => x.classList.toggle('is-selected', x === b));
        set({ mode: b.dataset.mode, type: b.dataset.mode === 'nofav' ? 'full' : 'best', point: null, saved: null, description: null });
      }));
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = `<span class="logo"></span>BananaShot — Share — ${PROJECT}` +
        '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
    }
    const nav = document.getElementById('nav');
    if (nav) {
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
  }

  // A hash such as #mode=nofav&type=full sets the first state. It helps to take screenshots.
  new URLSearchParams(location.hash.slice(1)).forEach((value, key) => {
    if (key === 'mode') { state.mode = value; if (value === 'nofav') state.type = 'full'; }
    if (key === 'type') state.type = value;
    if (key === 'big' || key === 'small') state[key] = value;
    if (key === 'point') state.point = Number(value);
  });

  window.Mock = {
    PROJECT, LENGTH, state, set, render, points, current, pointIndex, scored, pointLabel, scoreLabel, clockTenths,
    smallText, bigText, presetBig, PRESET_SMALL, fit, description, autoDescription, fileName,
    thumbHtml, mountThumb, icon, esc, toast, shell,
    onRender(fn) { renderFn = fn; },
  };
})();
