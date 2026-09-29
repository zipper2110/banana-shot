/*
 * Shared script for the Points tab redesign mockups.
 * It keeps the mockup data and state, simulates the player, draws the app shell,
 * the timeline and the dialogs, and binds the keyboard shortcuts of the Points tab.
 * The values come from the project PXL_20260913_070045619 on the reference screenshot.
 */
(function () {
  const DURATION = 5761000; // 01:36:01.0

  // ---------- Mock data ----------
  // The first 13 points are the points on the reference screenshot.
  const KNOWN = [
    [1015200, 1022500, 0], [1046700, 1055000, 0], [1066200, 1076900, 0], [1086900, 1100400, 1],
    [1127700, 1131900, 0], [1160700, 1168400, 0], [1221400, 1225200, 0], [1251700, 1255900, 0],
    [1267400, 1277900, 0], [1289400, 1295400, 1], [1306700, 1311400, 0], [1329300, 1345300, 1],
    [1379600, 1390300, 1],
  ];

  function prng(seed) {
    return function () {
      seed |= 0; seed = seed + 0x6D2B79F5 | 0;
      let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
      t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
  }

  function buildPoints() {
    const rnd = prng(20260913);
    const list = KNOWN.map(([start, end, fav], i) => ({ id: 'p' + i, start, end, fav: !!fav, label: '' }));
    let t = 1390300;
    const favIndexes = new Set();
    for (let i = 0; i < 115; i++) {
      const gap = (i % 8 === 7 ? 70000 + rnd() * 60000 : 12000 + rnd() * 18000);
      const dur = 3500 + Math.pow(rnd(), 1.6) * 16000;
      const start = Math.round((t + gap) / 100) * 100;
      const end = Math.round((start + dur) / 100) * 100;
      list.push({ id: 'p' + (13 + i), start, end, fav: false, label: '' });
      t = end;
      if (rnd() < 0.36 && favIndexes.size < 39) favIndexes.add(13 + i);
    }
    for (let i = list.length - 1; favIndexes.size < 39; i--) favIndexes.add(i);
    favIndexes.forEach((i) => { list[i].fav = true; });
    return list;
  }

  const COMMENT_SET = [
    { id: 1, start: 1090000, dur: 5000, color: '#FFC107', text: 'Good split step before the return.' },
    { id: 2, start: 1291500, dur: 6000, color: '#4FC3F7', text: 'Late preparation on the backhand. Turn the shoulders earlier and keep the racket head up.' },
    { id: 3, start: 1333000, dur: 4000, color: '#FF7351', text: 'Great point!' },
  ];

  let points = buildPoints();
  let comments = COMMENT_SET.map((c) => ({ ...c }));
  let nextPointId = 1000;
  let nextCommentId = 4;

  const state = {
    t: 1092000,          // playhead, ms
    playing: false,
    pending: null,       // pending start, ms
    selected: null,      // 'point:<id>' | 'pending' | null
    mark: 'idle',        // mockup switch: idle | pending
    comments: 'some',    // mockup switch: none | some
    modal: false,
  };

  // ---------- Formatting ----------
  const pad = (n, w = 2) => String(n).padStart(w, '0');
  function fmt(ms) {
    ms = Math.max(0, Math.round(ms));
    const h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, s = Math.floor(ms / 1000) % 60;
    return `${pad(h)}:${pad(m)}:${pad(s)}.${Math.floor((ms % 1000) / 100)}`;
  }
  function fmtFull(ms) {
    const h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, s = Math.floor(ms / 1000) % 60;
    return `${pad(h)}:${pad(m)}:${pad(s)}.${pad(ms % 1000, 3)}`;
  }
  const fmtSec = (ms) => (ms / 1000).toFixed(1) + ' s';
  function fmtLong(ms) {
    const s = Math.round(ms / 1000), m = Math.floor(s / 60);
    return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}min` : `${m}min ${s % 60}sec`;
  }
  function parseTime(text) {
    const v = String(text).trim();
    if (/^\d+(\.\d+)?$/.test(v)) return Math.round(parseFloat(v) * 1000);
    const m = v.match(/^(?:(\d+):)?(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?$/);
    if (!m) return null;
    return ((+(m[1] || 0) * 60 + +m[2]) * 60 + +m[3]) * 1000 + (m[4] ? +m[4].padEnd(3, '0') : 0);
  }
  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  // ---------- Model ----------
  function sortedPoints() { return points.slice().sort((a, b) => a.start - b.start); }
  function events() {
    const pts = sortedPoints().map((p, i) => ({ kind: 'point', key: 'point:' + p.id, n: i + 1, ...p }));
    const cms = state.comments === 'some'
      ? comments.map((c) => ({ kind: 'comment', key: 'comment:' + c.id, ...c })) : [];
    return pts.concat(cms).sort((a, b) => a.start - b.start || (a.key < b.key ? -1 : 1));
  }
  function counts() {
    return {
      points: points.length,
      favs: points.filter((p) => p.fav).length,
      comments: state.comments === 'some' ? comments.length : 0,
      length: points.reduce((sum, p) => sum + p.end - p.start, 0),
    };
  }
  const pointAt = (t) => points.find((p) => p.start <= t && t < p.end) || null;
  const pointById = (id) => points.find((p) => p.id === id);
  const ordinal = (id) => sortedPoints().findIndex((p) => p.id === id) + 1;

  // ---------- Events ----------
  const listeners = [];
  function on(fn) { listeners.push(fn); }
  function emit(reason, extra) { listeners.forEach((fn) => fn(reason, extra || {})); }

  function select(key, scroll) {
    if (state.selected === key) { if (scroll) emit('scroll', { key }); return; }
    state.selected = key;
    emit('select', { key, scroll });
  }

  function autoActivate() {
    const sel = state.selected && state.selected.startsWith('point:') ? pointById(state.selected.slice(6)) : null;
    if (sel && sel.start <= state.t && state.t < sel.end) return;
    const p = pointAt(state.t);
    if (p) return select('point:' + p.id, true);
    select(state.pending != null ? 'pending' : null, false);
  }

  // ---------- Player simulation ----------
  let timer = null;
  function setPlaying(value) {
    state.playing = value;
    clearInterval(timer);
    if (value) {
      timer = setInterval(() => {
        state.t = Math.min(DURATION, state.t + 100);
        if (state.t >= DURATION) setPlaying(false);
        autoActivate();
        emit('time');
      }, 100);
    }
    emit('play');
  }
  function seek(ms) {
    state.t = Math.max(0, Math.min(DURATION, Math.round(ms)));
    autoActivate();
    emit('time');
  }

  // ---------- Actions (same rules as PointsDispatcher) ----------
  const round10 = (ms) => Math.round(ms / 10) * 10;
  const actions = {
    togglePlay() { setPlaying(!state.playing); },
    seek,
    nudge(delta) { seek(state.t + delta); },
    start() {
      const t = round10(state.t);
      if (pointAt(t)) return hint(`Cannot place Start at ${t} ms: it's inside an existing point interval`);
      state.pending = t;
      state.mark = 'pending';
      emit('data');
      select('pending', false);
    },
    end() {
      const s = state.pending;
      if (s == null) return hint('Set a Start first (press C) before setting End');
      const e = round10(state.t);
      if (e <= s || e - s < 200) return hint('Invalid point: End must be > Start and duration ≥ 200 ms');
      if (points.some((p) => s < p.end && p.start < e)) return hint(`Overlap blocked for [${s}, ${e}). Adjust boundaries to avoid overlaps.`);
      const id = 'n' + (nextPointId++);
      points.push({ id, start: s, end: e, fav: false, label: '' });
      state.pending = null;
      state.mark = 'idle';
      state.selected = null;
      emit('data');
      select('point:' + id, true);
    },
    toggleFav(id) { const p = pointById(id); if (p) { p.fav = !p.fav; emit('data'); } },
    toggleFavSelected() {
      if (state.selected && state.selected.startsWith('point:')) actions.toggleFav(state.selected.slice(6));
    },
    deletePoint(id) {
      points = points.filter((p) => p.id !== id);
      if (state.selected === 'point:' + id) state.selected = null;
      emit('data');
    },
    deleteSelected() {
      if (state.selected && state.selected.startsWith('point:')) actions.deletePoint(state.selected.slice(6));
    },
    deleteComment(id) { comments = comments.filter((c) => c.id !== id); emit('data'); },
    clickEvent(key) {
      const ev = events().find((x) => x.key === key);
      if (!ev) return;
      if (ev.kind === 'point') select(key, false);
      seek(ev.start);
    },
    editPoint(id) { editPointDialog(pointById(id)); },
    addComment() { commentDialog(null); },
    editComment(id) { commentDialog(comments.find((c) => c.id === id)); },
  };

  // ---------- Dialogs ----------
  function modal(html, bind) {
    state.modal = true;
    const back = document.createElement('div');
    back.className = 'modal-back';
    back.innerHTML = `<div class="modal" role="dialog">${html}</div>`;
    document.body.appendChild(back);
    const close = () => { back.remove(); state.modal = false; };
    back.addEventListener('mousedown', (e) => { if (e.target === back) close(); });
    back.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
    bind(back, close);
    const first = back.querySelector('textarea, input');
    (first || back.querySelector('.btn')).focus();
  }

  function hint(message) {
    if (state.playing) setPlaying(false);
    modal(`
      <div class="modal-head"><h3>Hint</h3></div>
      <div class="hint-body"><span class="material-symbols-outlined">info</span><span>${escapeHtml(message)}</span></div>
      <div class="modal-foot"><span class="grow"></span><button class="btn btn-lime" data-ok>OK</button></div>`,
    (root, close) => { root.querySelector('[data-ok]').onclick = close; });
  }

  function confirmDialog(title, message, okLabel, onOk) {
    modal(`
      <div class="modal-head"><h3>${escapeHtml(title)}</h3></div>
      <div class="hint-body"><span class="material-symbols-outlined">warning</span><span>${escapeHtml(message)}</span></div>
      <div class="modal-foot"><span class="grow"></span><button class="btn" data-cancel>Cancel</button><button class="btn btn-danger" data-ok>${okLabel}</button></div>`,
    (root, close) => {
      root.querySelector('[data-cancel]').onclick = close;
      root.querySelector('[data-ok]').onclick = () => { close(); onOk(); };
    });
  }

  function editPointDialog(p) {
    if (!p) return;
    const n = ordinal(p.id);
    modal(`
      <div class="modal-head"><h3>Edit point #${n}</h3><span class="sub tc">${fmtSec(p.end - p.start)}</span></div>
      <div class="modal-body">
        <div class="fld-row">
          <label class="fld"><span>Start</span><input class="inp tc" data-f="start" value="${fmt(p.start)}"></label>
          <label class="fld"><span>End</span><input class="inp tc" data-f="end" value="${fmt(p.end)}"></label>
        </div>
        <label class="fld"><span>Label <small>optional</small></span><input class="inp" data-f="label" value="${escapeHtml(p.label)}"></label>
        <div class="form-error" data-err></div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-danger" data-del><span class="material-symbols-outlined">delete</span>Delete</button>
        <span class="grow"></span>
        <button class="btn" data-cancel>Cancel</button><button class="btn btn-lime" data-ok>Save</button>
      </div>`,
    (root, close) => {
      const f = (k) => root.querySelector(`[data-f=${k}]`).value;
      root.querySelector('[data-cancel]').onclick = close;
      root.querySelector('[data-del]').onclick = () => {
        close();
        confirmDialog('Confirm delete', `Delete marked point ${fmt(p.start)} - ${fmt(p.end)}?`, 'Delete', () => actions.deletePoint(p.id));
      };
      root.querySelector('[data-ok]').onclick = () => {
        const s = parseTime(f('start')), e = parseTime(f('end'));
        if (s == null || e == null) { root.querySelector('[data-err]').textContent = 'Invalid time format'; return; }
        close();
        if (e <= s || e - s < 200) return hint('Invalid edit: ensure 0 ≤ start < end and duration ≥ 200 ms');
        if (points.some((o) => o.id !== p.id && s < o.end && o.start < e)) return hint('Edit blocked: overlaps with another point. Adjust boundaries.');
        Object.assign(p, { start: s, end: e, label: f('label') });
        emit('data');
        select('point:' + p.id, true);
      };
      root.addEventListener('keydown', (e) => { if (e.key === 'Enter') root.querySelector('[data-ok]').click(); });
    });
  }

  function commentDialog(c) {
    if (state.playing) setPlaying(false);
    const isNew = !c;
    const start = isNew ? state.t : c.start;
    let color = isNew ? '#FFC107' : c.color;
    modal(`
      <div class="modal-head"><h3>${isNew ? 'Add comment' : 'Edit comment #' + c.id}</h3></div>
      <div class="modal-body">
        <label class="fld"><span>Text</span><textarea class="inp" data-f="text">${isNew ? '' : escapeHtml(c.text)}</textarea></label>
        <div class="fld-row">
          <label class="fld" title="hh:mm:ss.mmm, mm:ss.mmm, or seconds"><span>Start</span><input class="inp tc" data-f="start" value="${fmtFull(start)}"></label>
          <label class="fld" title="How long the comment stays on screen, in seconds"><span>Duration <small>seconds</small></span><input class="inp tc" data-f="dur" value="${isNew ? 5 : c.dur / 1000}"></label>
        </div>
        <div class="fld"><span>Text color</span>
          <div><button class="btn swatch-btn" data-color title="${color}"><span class="swatch" style="background:${color}"></span>Change…</button>
          <input type="color" data-picker value="${color}" style="position:absolute;opacity:0;pointer-events:none;width:0;height:0"></div>
        </div>
        <div class="form-error" data-err></div>
      </div>
      <div class="modal-foot"><span class="grow"></span><button class="btn" data-cancel>Cancel</button><button class="btn btn-lime" data-ok>Save</button></div>`,
    (root, close) => {
      const f = (k) => root.querySelector(`[data-f=${k}]`).value;
      const picker = root.querySelector('[data-picker]');
      root.querySelector('[data-color]').onclick = () => picker.click();
      picker.oninput = () => {
        color = picker.value.toUpperCase();
        root.querySelector('.swatch').style.background = color;
        root.querySelector('[data-color]').title = color;
      };
      root.querySelector('[data-cancel]').onclick = close;
      root.querySelector('[data-ok]').onclick = () => {
        const err = root.querySelector('[data-err]');
        const text = f('text').trim();
        const s = parseTime(f('start'));
        const d = Math.round(parseFloat(f('dur')) * 1000);
        if (!text) { err.textContent = 'Enter the comment text.'; return; }
        if (s == null) { err.textContent = 'Start must use the hh:mm:ss.mmm format.'; return; }
        if (!(d > 0)) { err.textContent = isNaN(d) ? 'Duration must be a number of seconds.' : 'Duration must be greater than zero.'; return; }
        close();
        if (isNew) comments.push({ id: nextCommentId++, start: s, dur: d, text, color });
        else Object.assign(c, { start: s, dur: d, text, color });
        state.comments = 'some';
        emit('data');
        emit('scroll', { key: 'comment:' + (isNew ? nextCommentId - 1 : c.id) });
      };
    });
  }

  // ---------- Keyboard (same keys as Keybindings.kt) ----------
  document.addEventListener('keydown', (e) => {
    if (state.modal) return;
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    const k = e.key.toLowerCase();
    let done = true;
    if (e.key === ' ') actions.togglePlay();
    else if (k === 'c' && !e.ctrlKey) actions.start();
    else if (k === 'v' && !e.ctrlKey) actions.end();
    else if (k === 'a' && !e.ctrlKey) actions.toggleFavSelected();
    else if (e.key === 'Delete') actions.deleteSelected();
    else if (e.key === 'ArrowLeft') actions.nudge(e.shiftKey ? -5000 : -1000);
    else if (e.key === 'ArrowRight') actions.nudge(e.shiftKey ? 5000 : 1000);
    else done = false;
    if (done) e.preventDefault();
  });

  // ---------- Timeline ----------
  /*
   * Draws the three-track timeline into `host`.
   * opts.gutter: width of the label column in px.
   * opts.gutterTime: show the playhead time in the top-left cell.
   */
  function timeline(host, opts) {
    opts = Object.assign({ gutter: 96, gutterTime: false }, opts);
    host.classList.add('tl');
    host.style.setProperty('--tl-gutter', opts.gutter + 'px');
    const pct = (ms) => (ms / DURATION) * 100 + '%';

    function commentLayout(width) {
      const ends = [];
      return (state.comments === 'some' ? comments : []).slice().sort((a, b) => a.start - b.start).map((c) => {
        const x = (c.start / DURATION) * width;
        const w = 12 + String(c.id).length * 7 + 7;
        let lane = ends.findIndex((end) => end < x + 3);
        if (lane < 0) { lane = ends.length; ends.push(0); }
        ends[lane] = x + 3 + w;
        return { c, lane };
      });
    }

    function render() {
      const c = counts();
      const lanesWidth = Math.max(100, host.clientWidth - opts.gutter);
      const layout = commentLayout(lanesWidth);
      const laneCount = Math.max(1, ...layout.map((l) => l.lane + 1));
      const ticks = Array.from({ length: 11 }, (_, i) =>
        `<div class="tl-tick ${i === 10 ? 'last' : ''}" style="left:${i * 10}%">${lanesWidth < 1300 && i % 2 ? '' : `<span class="tc">${fmt((DURATION / 10) * i)}</span>`}</div>`).join('');
      const marks = sortedPoints().map((p) =>
        `<div class="tl-mark ${p.fav ? 'fav' : ''}" data-point="${p.id}" style="left:${pct(p.start)};width:${pct(p.end - p.start)}"></div>`).join('');
      const tags = layout.map(({ c: cm, lane }) =>
        `<div class="tl-cline" style="left:${pct(cm.start)};background:${cm.color}"></div>
         <div class="tl-ctag" data-comment="${cm.id}" title="#${cm.id} · ${fmt(cm.start)}\n${escapeHtml(cm.text)}"
              style="left:calc(${pct(cm.start)} + 3px);top:${3 + lane * 19}px;background:${cm.color}">#${cm.id}</div>`).join('');
      host.innerHTML = `
        <div class="tl-row"><div class="tl-gut tl-corner">${opts.gutterTime ? '<span class="tc tl-now"></span>' : ''}</div>
          <div class="tl-lane tl-ruler">${ticks}</div></div>
        <div class="tl-row"><div class="tl-gut"><span class="material-symbols-outlined">movie</span>VIDEO</div>
          <div class="tl-lane tl-track"><div class="tl-video-bar"></div></div></div>
        <div class="tl-row"><div class="tl-gut"><span class="material-symbols-outlined">flag</span>MARKS<span class="cnt num">${c.points}</span></div>
          <div class="tl-lane tl-track tl-marks">${marks}<div class="tl-pending" hidden></div></div></div>
        <div class="tl-row"><div class="tl-gut"><span class="material-symbols-outlined">chat_bubble</span>COMMENTS<span class="cnt num">${c.comments || ''}</span></div>
          <div class="tl-lane tl-comments" style="height:${Math.max(22, laneCount * 19 + 5)}px">${tags}</div></div>
        <div class="tl-overlay" style="position:absolute;top:0;bottom:0;left:${opts.gutter}px;right:0;pointer-events:none">
          <div class="tl-playhead"></div></div>`;
      update();
      updateSelection();
    }

    function update() {
      host.querySelector('.tl-playhead').style.left = pct(state.t);
      const now = host.querySelector('.tl-now');
      if (now) now.textContent = fmt(state.t);
      const pend = host.querySelector('.tl-pending');
      if (state.pending != null) {
        const a = Math.min(state.pending, state.t), b = Math.max(state.pending, state.t);
        pend.hidden = false;
        pend.style.left = pct(a);
        pend.style.width = `max(3px, ${pct(b - a)})`;
      } else pend.hidden = true;
    }

    function updateSelection() {
      host.querySelectorAll('.tl-mark.sel').forEach((m) => m.classList.remove('sel'));
      if (state.selected && state.selected.startsWith('point:')) {
        const m = host.querySelector(`.tl-mark[data-point="${state.selected.slice(6)}"]`);
        if (m) m.classList.add('sel');
      }
    }

    // Mouse: click a mark or a comment tag to seek to it, click or drag elsewhere to scrub.
    let dragging = false;
    const timeAt = (e) => {
      const lane = host.querySelector('.tl-ruler').getBoundingClientRect();
      return ((e.clientX - lane.left) / lane.width) * DURATION;
    };
    host.addEventListener('mousedown', (e) => {
      if (!e.target.closest('.tl-lane')) return;
      const mark = e.target.closest('.tl-mark');
      const tag = e.target.closest('.tl-ctag');
      if (tag) {
        const cm = comments.find((x) => x.id === +tag.dataset.comment);
        seek(cm.start);
        emit('scroll', { key: 'comment:' + cm.id });
        return;
      }
      if (mark) {
        const p = pointById(mark.dataset.point);
        seek(p.start);
        select('point:' + p.id, true);
        return;
      }
      dragging = true;
      seek(timeAt(e));
    });
    window.addEventListener('mousemove', (e) => { if (dragging) seek(timeAt(e)); });
    window.addEventListener('mouseup', () => { dragging = false; });
    window.addEventListener('resize', render);

    on((reason) => {
      if (reason === 'data') render();
      else if (reason === 'time') update();
      else if (reason === 'select') updateSelection();
    });
    render();
  }

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'final.html', id: 'final', label: 'Final' },
    { href: 'option-a.html', id: 'a', label: 'A · Clean toolbar (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Mark panel + table' },
    { href: 'option-c.html', id: 'c', label: 'C · Editor stack' },
  ];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML =
        '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span>' +
        '<span class="mock-label">Point</span><div class="mock-states" data-group="mark">' +
        '<button data-v="idle">Idle</button><button data-v="pending">Start set</button></div>' +
        '<span class="mock-label">Comments</span><div class="mock-states" data-group="comments">' +
        '<button data-v="none">None</button><button data-v="some">3 comments</button></div>';
      bar.addEventListener('click', (e) => {
        const b = e.target.closest('.mock-states button');
        if (!b) return;
        const group = b.parentElement.dataset.group;
        if (group === 'mark') setMark(b.dataset.v);
        else { state.comments = b.dataset.v; emit('data'); }
        b.blur();
      });
      on(() => bar.querySelectorAll('.mock-states').forEach((g) =>
        g.querySelectorAll('button').forEach((b) => b.classList.toggle('is-selected', state[g.dataset.group] === b.dataset.v))));
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = '<span class="logo"></span>Tennis Record — Points — PXL_20260913_070045619' +
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
        item('sports_tennis', 'Points', true) + item('scoreboard', 'Scoring') + item('bar_chart', 'Stats') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">VIDEO</div>' +
        item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export') + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
  }

  function setMark(value) {
    setPlaying(false);
    state.mark = value;
    if (value === 'pending') { state.t = 1112000; state.pending = 1107500; }
    else { state.t = 1092000; state.pending = null; }
    state.selected = null;
    emit('data');
    autoActivate();
    emit('time');
  }

  // A hash such as #mark=pending&comments=none&dialog=edit sets the first state. It helps to take screenshots.
  const hash = new URLSearchParams(location.hash.slice(1));

  function start() {
    if (hash.get('comments')) state.comments = hash.get('comments');
    setMark(hash.get('mark') || 'idle');
    // #point=<id> opens the tab at that point, for example from "Go to point" in the Scoring mockups.
    const target = hash.get('point') && pointById(hash.get('point'));
    if (target) seek(target.start);
    const dialog = hash.get('dialog');
    if (dialog === 'edit') actions.editPoint(points[3].id);
    else if (dialog === 'comment') actions.editComment(2);
    else if (dialog === 'add') actions.addComment();
    else if (dialog === 'hint') actions.end();
  }

  window.Mock = {
    state, DURATION, actions, on, emit, events, counts, fmt, fmtSec, fmtLong, escapeHtml,
    shell, timeline, start, pointById,
  };
})();
