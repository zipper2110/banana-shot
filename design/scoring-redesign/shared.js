/*
 * Shared script for the Scoring tab redesign mockups.
 * It keeps the mockup data and state, computes the score (a port of ScoringEngine.timeline),
 * simulates the player inside the selected point, and draws the parts that all options use:
 * the app shell, the video with the scoreboard preview, the point scrub bar, the transport,
 * the speed controls, the points list and the first-visit balloon.
 * The keyboard shortcuts are the same as in SwingScoringPanel.installKeyBindings.
 */
(function () {
  const DURATION = 5761000; // 01:36:01.0

  // ---------- Mock data ----------
  // The points are the same as in design/points-redesign (the first 13 come from the reference screenshot).
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
    list[11].label = 'Long rally';
    list[27].label = 'Net cord';
    return list;
  }

  const SCORED = 44; // The first 44 points have an outcome.
  function buildOutcomes(points) {
    const rnd = prng(7070);
    const map = new Map();
    points.slice(0, SCORED).forEach((p) => {
      const r = rnd();
      map.set(p.id, r < 0.05 ? 'NONE' : r < 0.58 ? 'P1' : 'P2');
    });
    return map;
  }

  const players = [
    { name: 'Alex', color: '#4DA3FF' },
    { name: 'Sam', color: '#FF6B6B' },
  ];
  const TITLE = 'Sunday club match';
  const SPEEDS = [2, 1.5, 1.25, 1, 0.5];
  const SPEED_LABELS = ['2×', '1.5×', '1.25×', '1×', '0.5×'];
  const FRAME_MS = 33; // one frame at 30 fps

  let points = buildPoints();
  const outcomes = buildOutcomes(points);
  const manualGame = new Map();
  const manualSet = new Map();
  const serverMarks = new Map();

  const state = {
    idx: -1,             // selected point index
    t: 0,                // playhead, ms
    playing: false,
    speed: 3,            // index in SPEEDS
    frameStep: false,
    scoring: 'auto',     // mockup switch: auto | manual
    serve: 'none',       // mockup switch: none | marked
    hint: 'off',         // mockup switch: off | on
    modal: false,
  };

  // ---------- Formatting ----------
  const pad = (n, w = 2) => String(n).padStart(w, '0');
  function fmt(ms) {
    ms = Math.max(0, Math.round(ms));
    const h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, s = Math.floor(ms / 1000) % 60;
    return `${pad(h)}:${pad(m)}:${pad(s)}.${Math.floor((ms % 1000) / 100)}`;
  }
  const fmtHms = (ms) => fmt(ms).slice(0, 8);
  const fmtSec = (ms) => (ms / 1000).toFixed(1) + ' s';
  function fmtRel(ms) {
    ms = Math.max(0, ms);
    return `${Math.floor(ms / 60000)}:${pad(Math.floor(ms / 1000) % 60)}.${Math.floor((ms % 1000) / 100)}`;
  }
  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  // ---------- Scoring engine (port of ScoringEngine.timeline with the default MatchRulesV1) ----------
  const RULES = { bestOf: 3, gamesPerSet: 6, setTiebreak: true, tiebreakPoints: 7 };

  function newMatch(manual) {
    const m = {
      p1: 0, p2: 0, g1: 0, g2: 0, s1: 0, s2: 0, tb: false, tbTarget: RULES.tiebreakPoints,
      gameServer: 1, matchWon: null, gameWon: null, setWon: null, completed: [],
    };
    const other = (p) => 3 - p;
    const receiverServes = () => Math.floor((m.p1 + m.p2 + 1) / 2) % 2 === 1;
    m.startPoint = () => { m.gameWon = null; m.setWon = null; };
    m.server = () => (m.tb && receiverServes() ? other(m.gameServer) : m.gameServer);
    m.setServer = (p) => { m.gameServer = m.tb && receiverServes() ? other(p) : p; };
    m.markGame = (p) => {
      if (p === 1) m.g1++; else m.g2++;
      m.p1 = 0; m.p2 = 0; m.gameWon = p; m.gameServer = other(m.gameServer);
    };
    m.completeSet = (p, score) => {
      m.completed.push(score);
      if (p === 1) m.s1++; else m.s2++;
      m.g1 = 0; m.g2 = 0; m.p1 = 0; m.p2 = 0; m.tb = false; m.setWon = p;
      if (manual) return;
      if (Math.max(m.s1, m.s2) >= Math.floor(RULES.bestOf / 2) + 1) m.matchWon = p;
    };
    m.markSet = (p) => m.completeSet(p, { p1: m.g1, p2: m.g2, tb: false });
    m.won = (p) => {
      if (m.matchWon) return;
      if (p === 1) m.p1++; else m.p2++;
      if (manual) return;
      const lead = Math.abs(m.p1 - m.p2);
      const leader = m.p1 > m.p2 ? 1 : 2;
      if (m.tb) {
        if (Math.max(m.p1, m.p2) < m.tbTarget || lead < 2) return;
        m.markGame(leader);
        m.completeSet(leader, { p1: m.g1, p2: m.g2, tb: true });
        return;
      }
      if (Math.max(m.p1, m.p2) >= 4 && lead >= 2) {
        m.markGame(leader);
        const gl = Math.abs(m.g1 - m.g2), gt = Math.max(m.g1, m.g2);
        if (gt >= RULES.gamesPerSet && gl >= 2) m.completeSet(leader, { p1: m.g1, p2: m.g2, tb: false });
        else if (RULES.setTiebreak && m.g1 === RULES.gamesPerSet && m.g2 === RULES.gamesPerSet) {
          m.tb = true; m.p1 = 0; m.p2 = 0;
        }
      }
    };
    m.snap = () => ({
      p1: m.p1, p2: m.p2, g1: m.g1, g2: m.g2, s1: m.s1, s2: m.s2,
      gameWon: m.gameWon, setWon: m.setWon, tb: m.tb,
    });
    return m;
  }

  let timeline = null;
  function computeTimeline(marks = serverMarks) {
    const manual = state.scoring === 'manual';
    const m = newMatch(manual);
    const initial = m.snap();
    const states = [], sets = [], servers = [];
    let marked = false;
    points.forEach((p) => {
      m.startPoint();
      const mark = marks.get(p.id);
      if (mark) {
        if (!marked && m.server() !== mark) servers.forEach((s, i) => { servers[i] = 3 - s; });
        marked = true;
        m.setServer(mark);
      }
      servers.push(m.server());
      const o = outcomes.get(p.id);
      if (o === 'P1') m.won(1); else if (o === 'P2') m.won(2);
      if (manual) {
        if (manualGame.get(p.id)) m.markGame(manualGame.get(p.id));
        if (manualSet.get(p.id)) m.markSet(manualSet.get(p.id));
      }
      states.push(m.snap());
      sets.push(m.completed.slice());
    });
    return { initial, states, sets, servers: marked ? servers : servers.map(() => null) };
  }
  function recompute() { timeline = computeTimeline(); }
  recompute();

  // Same mapping as SwingScoringPanel.updateBottomPanels.
  function pointsText(s, player) {
    const mine = player === 1 ? s.p1 : s.p2, other = player === 1 ? s.p2 : s.p1;
    if (s.tb) return String(mine);
    if (mine < 4 && other < 4) return ['0', '15', '30', '40'][mine];
    return mine === other ? '40' : mine > other ? 'Ad' : '40';
  }

  /** Everything that the scoring controls show for the selected point. */
  function current() {
    const i = state.idx;
    const p = points[i];
    if (!p) return null;
    const after = timeline.states[i];
    const before = i === 0 ? timeline.initial : timeline.states[i - 1];
    return {
      index: i, n: i + 1, total: points.length, point: p,
      outcome: outcomes.get(p.id) || null,
      after, before,
      setsAfter: timeline.sets[i], setsBefore: i === 0 ? [] : timeline.sets[i - 1],
      server: timeline.servers[i], serverMarked: serverMarks.has(p.id),
      manualGame: manualGame.get(p.id) || null, manualSet: manualSet.get(p.id) || null,
      hasPrev: i > 0, hasNext: i < points.length - 1,
    };
  }

  function counts() {
    return {
      total: points.length,
      scored: points.filter((p) => outcomes.has(p.id)).length,
      favs: points.filter((p) => p.fav).length,
    };
  }

  // ---------- Events ----------
  const listeners = [];
  function on(fn) { listeners.push(fn); }
  function emit(reason, extra) { listeners.forEach((fn) => fn(reason, extra || {})); }

  // ---------- Player simulation: the playhead stays inside the selected point ----------
  let timer = null;
  const seg = () => points[state.idx] || null;
  function setPlaying(value) {
    state.playing = value;
    clearInterval(timer);
    if (value) {
      timer = setInterval(() => {
        const p = seg();
        if (!p) return setPlaying(false);
        state.t += 50 * SPEEDS[state.speed];
        if (state.t >= p.end) { state.t = p.end - 1; setPlaying(false); }
        emit('time');
      }, 50);
    }
    emit('play');
  }
  function seekClamped(ms) {
    const p = seg();
    if (!p) return;
    state.t = Math.max(p.start, Math.min(p.end - 1, Math.round(ms)));
    emit('time');
  }

  // ---------- Actions (same rules as SwingScoringPanel) ----------
  function select(i, opts = {}) {
    if (i === state.idx || !points[i]) return;
    state.idx = i;
    setPlaying(false);
    state.t = points[i].start;
    emit('select', { scroll: !opts.user });
    emit('time');
    if (opts.autoplay) setPlaying(true);
  }

  function markServer(server) {
    const c = current();
    if (!c) return;
    const id = c.point.id;
    if (serverMarks.get(id) === server) serverMarks.delete(id);
    else if (c.server === server) return;
    else {
      serverMarks.delete(id);
      const computed = computeTimeline().servers[c.index];
      if (computed !== server) serverMarks.set(id, server);
    }
    recompute();
    emit('data');
  }

  const actions = {
    togglePlay() {
      const p = seg();
      if (!p) return;
      if (!state.playing && state.t >= p.end - 1) state.t = p.start; // Play at the end restarts the point.
      setPlaying(!state.playing);
    },
    nudge(delta) { seekClamped(state.t + delta); },
    arrow(dir, shift) {
      if (shift) return actions.nudge(dir * 5000);
      if (state.frameStep && !state.playing) return actions.nudge(dir * FRAME_MS);
      actions.nudge(dir * 1000);
    },
    scrubTo(ms) { setPlaying(false); seekClamped(ms); },
    select(i) { select(i, { user: true }); },
    /** Opens the Points tab at this point. In the mockup, it opens the Points final mockup. */
    goToPoint(i) {
      if (!points[i]) return;
      setPlaying(false);
      location.href = '../points-redesign/final.html#point=' + encodeURIComponent(points[i].id);
    },
    next() { if (state.idx >= 0 && state.idx + 1 < points.length) select(state.idx + 1, { autoplay: true }); },
    prev() { if (state.idx > 0) select(state.idx - 1, { autoplay: true }); },
    outcome(o) {
      const p = seg();
      if (!p) return;
      outcomes.set(p.id, o);
      recompute();
      emit('data');
    },
    toggleFav(i) {
      const p = points[i ?? state.idx];
      if (!p) return;
      p.fav = !p.fav;
      emit('data');
    },
    serve(player) { markServer(player); },
    switchServe() {
      const c = current();
      if (c) markServer(c.server === 1 ? 2 : 1);
    },
    manualGame(player) { toggleManual(manualGame, player); },
    manualSet(player) { toggleManual(manualSet, player); },
    setSpeed(i) { state.speed = Math.max(0, Math.min(SPEEDS.length - 1, i)); emit('speed'); },
    speedBy(d) { actions.setSpeed(state.speed + d); },
    toggleFrameStep() { state.frameStep = !state.frameStep; emit('speed'); },
    openSettings() {
      stub('Scoring settings', 'Players, Match format and Manual scoring. The dialog stays the same in this proposal.');
      hideBalloon();
    },
    openStyle() { stub('Scoreboard style', 'Style, Title, Players, Position, Size, Background, Bottom line and Accent color, with a live preview. The dialog stays the same in this proposal.'); },
  };

  function toggleManual(map, player) {
    if (state.scoring !== 'manual') return;
    const p = seg();
    if (!p) return;
    if (map.get(p.id) === player) map.delete(p.id); else map.set(p.id, player);
    recompute();
    emit('data');
  }

  // ---------- Dialog stubs ----------
  function stub(title, text) {
    setPlaying(false);
    state.modal = true;
    const back = document.createElement('div');
    back.className = 'modal-back';
    back.innerHTML = `<div class="modal" role="dialog">
      <div class="modal-head"><h3>${escapeHtml(title)}</h3></div>
      <div class="stub-body">${escapeHtml(text)}</div>
      <div class="modal-foot"><span class="grow"></span><button class="btn btn-lime" data-ok>Close</button></div></div>`;
    document.body.appendChild(back);
    const close = () => { back.remove(); state.modal = false; };
    back.querySelector('[data-ok]').onclick = close;
    back.addEventListener('mousedown', (e) => { if (e.target === back) close(); });
    back.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
    back.querySelector('[data-ok]').focus();
  }

  // ---------- Keyboard (same keys as AppShortcuts) ----------
  document.addEventListener('keydown', (e) => {
    if (state.modal) return;
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    if (e.ctrlKey || e.altKey || e.metaKey) return;
    const k = e.key.toLowerCase();
    let done = true;
    if (e.key === ' ') actions.togglePlay();
    else if (k === 'q') actions.outcome('P1');
    else if (k === 'w') actions.outcome('NONE');
    else if (k === 'e') actions.outcome('P2');
    else if (k === 'r') { if (e.shiftKey) actions.prev(); else actions.next(); }
    else if (k === 'a') actions.toggleFav();
    else if (k === 's') actions.switchServe();
    else if (k === 'f') actions.toggleFrameStep();
    else if (e.key === 'ArrowLeft') actions.arrow(-1, e.shiftKey);
    else if (e.key === 'ArrowRight') actions.arrow(1, e.shiftKey);
    else if (e.key === 'ArrowUp') actions.speedBy(-1); // presets go from high to low
    else if (e.key === 'ArrowDown') actions.speedBy(1);
    else done = false;
    if (done) {
      e.preventDefault();
      if (tag === 'SELECT') document.activeElement.blur();
      flashKey(e);
    }
  });

  // A seek hotkey briefly gives its button a lime border.
  function flashKey(e) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    const delta = (e.key === 'ArrowLeft' ? -1 : 1) * (e.shiftKey ? 5000 : 1000);
    document.querySelectorAll(`[data-nudge="${delta}"]`).forEach((b) => {
      b.classList.add('flash');
      clearTimeout(b.flashTimer);
      b.flashTimer = setTimeout(() => b.classList.remove('flash'), 160);
    });
  }

  // ---------- Parts ----------
  const icon = (name, cls = '') => `<span class="material-symbols-outlined ${cls}">${name}</span>`;
  const kbd = (...keys) => keys.map((k) => `<kbd>${k}</kbd>`).join('');
  const pc = (player) => `--pc:${players[player - 1].color}`;

  /** Video frame with the scoreboard that mpv paints on it. The scoreboard shows the score before the point. */
  function video(host) {
    host.classList.add('video');
    host.innerHTML = '<div class="frame"><img src="frame.jpg" alt="Video preview"><div class="sb"></div></div>';
    const frame = host.querySelector('.frame');
    const img = host.querySelector('img');
    img.style.position = 'static';
    img.style.width = '100%';
    img.style.height = '100%';
    const sb = host.querySelector('.sb');
    function layout() {
      const w = host.clientWidth, h = host.clientHeight;
      const fw = Math.min(w, h * 16 / 9), fh = fw * 9 / 16;
      Object.assign(frame.style, { left: (w - fw) / 2 + 'px', top: (h - fh) / 2 + 'px', width: fw + 'px', height: fh + 'px' });
      sb.style.fontSize = Math.max(7, fw * 0.0135) + 'px';
    }
    function render() {
      const c = current();
      sb.hidden = !c;
      if (!c) return;
      const s = c.before;
      const row = (pl) => {
        const sets = c.setsBefore.map((x) => `<div class="sb-cell">${pl === 1 ? x.p1 : x.p2}</div>`).join('');
        return `<div class="sb-row"><div class="sb-name" style="${pc(pl)}"><i></i>${escapeHtml(players[pl - 1].name)}${c.server === pl ? '<span class="sb-ball"></span>' : ''}</div>
          ${sets}<div class="sb-cell games">${pl === 1 ? s.g1 : s.g2}</div><div class="sb-pts">${pointsText(s, pl)}</div></div>`;
      };
      sb.innerHTML = `<div class="sb-title">${escapeHtml(TITLE)}</div>${row(1)}${row(2)}<div class="sb-credit">TennisRecord app</div>`;
    }
    new ResizeObserver(layout).observe(host);
    on((reason) => { if (reason === 'data' || reason === 'select') render(); });
    layout();
    render();
  }

  /** Scrub bar for the selected point. A click or a drag seeks and pauses. */
  function scrub(host, opts = {}) {
    host.classList.add('scrub');
    host.innerHTML = `
      <div class="edge"><small>Point start</small><span class="tc" data-s></span></div>
      <div class="scrub-track" title="Scrub within the selected point">
        <div class="scrub-rail"></div><div class="scrub-fill"></div><div class="scrub-thumb"></div></div>
      <div class="edge end"><small>Point end</small><span class="tc" data-e></span></div>`;
    if (opts.compact) host.querySelectorAll('.edge small').forEach((s) => s.remove());
    const track = host.querySelector('.scrub-track');
    function update() {
      const p = seg();
      host.classList.toggle('disabled', !p);
      // opts.relative: the times count from the point start (0:00.0 to the point length).
      host.querySelector('[data-s]').textContent = !p ? '—' : opts.relative ? fmtRel(0) : fmt(p.start);
      host.querySelector('[data-e]').textContent = !p ? '—' : opts.relative ? fmtRel(p.end - p.start) : fmt(p.end);
      const f = p ? Math.min(1, (state.t - p.start) / (p.end - p.start)) : 0;
      host.querySelector('.scrub-fill').style.width = f * 100 + '%';
      host.querySelector('.scrub-thumb').style.left = f * 100 + '%';
    }
    let drag = false;
    const at = (e) => {
      const r = track.getBoundingClientRect(), p = seg();
      return p.start + Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * (p.end - p.start);
    };
    track.addEventListener('mousedown', (e) => { if (!seg()) return; drag = true; actions.scrubTo(at(e)); });
    window.addEventListener('mousemove', (e) => { if (drag) actions.scrubTo(at(e)); });
    window.addEventListener('mouseup', () => { drag = false; });
    on((reason) => { if (reason === 'time' || reason === 'select') update(); });
    update();
  }

  /** Transport: -5s, -1s, Play/Pause, +1s, +5s. Each key has its own chip, as in the Points final design. */
  function transport(host) {
    host.classList.add('transport');
    host.innerHTML = `
      <button class="btn seek" data-nudge="-5000" title="Seek back 5 seconds [Shift+Left]">−5s<span class="keys">${kbd('⇧', '←')}</span></button>
      <button class="btn seek" data-nudge="-1000" title="Seek back 1 second [Left]">−1s<span class="keys">${kbd('←')}</span></button>
      <button class="play" data-play></button>
      <button class="btn seek" data-nudge="1000" title="Seek forward 1 second [Right]">+1s<span class="keys">${kbd('→')}</span></button>
      <button class="btn seek" data-nudge="5000" title="Seek forward 5 seconds [Shift+Right]">+5s<span class="keys">${kbd('⇧', '→')}</span></button>`;
    host.querySelectorAll('[data-nudge]').forEach((b) => { b.onclick = () => actions.nudge(+b.dataset.nudge); });
    const play = host.querySelector('[data-play]');
    play.onclick = () => actions.togglePlay();
    function update() {
      play.innerHTML = icon(state.playing ? 'pause' : 'play_arrow', 'fill') + '<span class="play-key">SPACE</span>';
      play.title = state.playing ? 'SPACE — Pause' : 'SPACE — Play';
    }
    on((reason) => { if (reason === 'play') update(); });
    update();
  }

  /** Speed dropdown with the Up and Down key chips, and the frame step toggle [F]. */
  function speed(host, opts = {}) {
    host.classList.add('speed');
    host.innerHTML = `
      <label class="speed" title="Use Up/Down to change speed">${opts.label === false ? '' : '<span class="cap">Speed</span>'}
        <select data-speed>${SPEED_LABELS.map((l, i) => `<option value="${i}">${l}</option>`).join('')}</select>
        <span class="keys">${kbd('↑', '↓')}</span></label>
      <button class="btn toggle" data-fstep title="When enabled, Left/Right step a single frame while paused">
        <span class="box">${icon('check')}</span>Frame step ${kbd('F')}</button>`;
    const sel = host.querySelector('[data-speed]');
    sel.onchange = () => { actions.setSpeed(+sel.value); sel.blur(); };
    const fs = host.querySelector('[data-fstep]');
    fs.onclick = () => actions.toggleFrameStep();
    function update() { sel.value = state.speed; fs.classList.toggle('on', state.frameStep); }
    on((reason) => { if (reason === 'speed') update(); });
    update();
  }

  /*
   * Points list. opts.cols is the CSS grid template. opts.columns lists the cells:
   * n (number and label), start, len, won (winner name), marks (serve mark, GAME/SET), fav, status (colored dot).
   */
  function list(host, opts) {
    const headText = { n: '#', start: 'Start', len: 'Length', won: 'Won by', marks: '', fav: '★', label: '', jump: '' };
    const headCls = { len: 'r', fav: 'c', marks: 'r' };
    host.style.setProperty('--cols', opts.cols);
    host.innerHTML = `<div class="plist-head">${opts.columns.map((c) => `<span class="${headCls[c] || ''}">${headText[c]}</span>`).join('')}</div>
      <div class="scroll" data-body style="flex:1;min-height:0"></div>`;
    host.style.display = 'flex';
    host.style.flexDirection = 'column';
    const body = host.querySelector('[data-body]');

    function cell(c, p, i) {
      const o = outcomes.get(p.id);
      const s = timeline.states[i];
      switch (c) {
        case 'n': return `<span class="n" ${p.label ? `title="${escapeHtml(p.label)}"` : ''}>${i + 1}${p.label && opts.labelIn === 'n' ? `<small>${escapeHtml(p.label)}</small>` : ''}</span>`;
        case 'start': return `<span class="tc">${fmtHms(p.start)}</span>`;
        case 'len': return `<span class="tc len r">${fmtSec(p.end - p.start)}</span>`;
        case 'won': {
          const label = p.label ? ` <small class="faint">· ${escapeHtml(p.label)}</small>` : '';
          if (o === 'P1' || o === 'P2') {
            const pl = o === 'P1' ? 1 : 2;
            return `<span class="wonby" style="${pc(pl)}"><i></i><span>${escapeHtml(players[pl - 1].name)}${label}</span></span>`;
          }
          return o === 'NONE' ? `<span class="wonby none"><span>No point${label}</span></span>`
            : `<span class="wonby open" title="Not scored. Choose who won this point"><span>—${label}</span></span>`;
        }
        case 'label': return `<span class="faint" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${escapeHtml(p.label)}</span>`;
        case 'marks': {
          const mark = serverMarks.get(p.id);
          let html = mark ? `<span class="material-symbols-outlined ms-serve" style="${pc(mark)}" title="Serve marked: ${escapeHtml(players[mark - 1].name)} serves">sports_tennis</span>` : '';
          if (s.setWon) html += `<span class="ms set" style="${pc(s.setWon)}" title="Set won by ${escapeHtml(players[s.setWon - 1].name)}">SET</span>`;
          else if (s.gameWon) html += `<span class="ms game" style="${pc(s.gameWon)}" title="Game won by ${escapeHtml(players[s.gameWon - 1].name)}">GAME</span>`;
          return `<span class="marks">${html}</span>`;
        }
        case 'jump': return `<span class="jump-cell"><button class="btn-icon" data-jump="${i}" title="Go to point ${i + 1} in the Points tab">${icon('open_in_new')}</button></span>`;
        case 'fav': return `<span class="fav-cell"><button class="btn-icon fav ${p.fav ? 'on' : ''}" data-fav="${i}" title="Favorite [A]">${icon('star', p.fav ? 'fill' : '')}</button></span>`;
        default: return '<span></span>';
      }
    }

    function render() {
      if (!points.length) {
        body.innerHTML = '<div class="empty">No points yet. Open the Points tab and add point markers.</div>';
        return;
      }
      body.innerHTML = points.map((p, i) => {
        const o = outcomes.get(p.id);
        const oc = o === 'P1' ? players[0].color : o === 'P2' ? players[1].color : o === 'NONE' ? '#D6D6D6' : 'transparent';
        return `<div class="prow ${o ? '' : 'unscored'} ${i === state.idx ? 'sel' : ''}" data-i="${i}" style="--oc:${oc}">
          ${opts.columns.map((c) => cell(c, p, i)).join('')}</div>`;
      }).join('');
    }
    function applySelection(scroll) {
      body.querySelectorAll('.prow.sel').forEach((r) => r.classList.remove('sel'));
      const row = body.querySelector(`[data-i="${state.idx}"]`);
      if (row) { row.classList.add('sel'); if (scroll) row.scrollIntoView({ block: 'nearest' }); }
    }
    body.addEventListener('click', (e) => {
      const fav = e.target.closest('[data-fav]');
      if (fav) return actions.toggleFav(+fav.dataset.fav);
      const jump = e.target.closest('[data-jump]');
      if (jump) return actions.goToPoint(+jump.dataset.jump);
      const row = e.target.closest('[data-i]');
      if (row) actions.select(+row.dataset.i);
    });
    on((reason, x) => {
      if (reason === 'data') { const top = body.scrollTop; render(); body.scrollTop = top; }
      if (reason === 'select') applySelection(x.scroll);
    });
    render();
  }

  // ---------- First-visit balloon at the Scoring settings button ----------
  let balloonEl = null;
  function showBalloon() {
    hideBalloon();
    const anchor = document.getElementById('bSettings');
    if (!anchor) return;
    balloonEl = document.createElement('div');
    balloonEl.className = 'balloon';
    balloonEl.innerHTML = 'You can change the scoring settings at any time with this button.<button aria-label="Close">✕</button>';
    document.body.appendChild(balloonEl);
    balloonEl.querySelector('button').onclick = () => { state.hint = 'off'; hideBalloon(); emit('mock'); };
    place();
  }
  function place() {
    if (!balloonEl) return;
    const anchor = document.getElementById('bSettings');
    const r = anchor.getBoundingClientRect();
    const b = balloonEl.getBoundingClientRect();
    const left = Math.max(8, Math.min(window.innerWidth - b.width - 8, r.left + r.width / 2 - 36));
    balloonEl.style.left = left + 'px';
    balloonEl.style.top = (r.top - b.height - 10) + 'px';
    balloonEl.style.setProperty('--arrow', (r.left + r.width / 2 - left - 6) + 'px');
  }
  function hideBalloon() { if (balloonEl) { balloonEl.remove(); balloonEl = null; } }
  window.addEventListener('resize', place);

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'final.html', id: 'final', label: 'Final' },
    { href: 'option-a.html', id: 'a', label: 'A · Score desk (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Player sides' },
    { href: 'option-c.html', id: 'c', label: 'C · Score panel' },
  ];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML =
        '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span>' +
        '<span class="mock-label">Scoring</span><div class="mock-states" data-group="scoring">' +
        '<button data-v="auto">Automatic</button><button data-v="manual">Manual</button></div>' +
        '<span class="mock-label">Server</span><div class="mock-states" data-group="serve">' +
        '<button data-v="none">Not marked</button><button data-v="marked">Marked</button></div>' +
        '<span class="mock-label">First visit</span><div class="mock-states" data-group="hint">' +
        '<button data-v="off">Off</button><button data-v="on">Balloon</button></div>';
      bar.addEventListener('click', (e) => {
        const b = e.target.closest('.mock-states button');
        if (!b) return;
        setMock(b.parentElement.dataset.group, b.dataset.v);
        b.blur();
      });
      const sync = () => bar.querySelectorAll('.mock-states').forEach((g) =>
        g.querySelectorAll('button').forEach((b) => b.classList.toggle('is-selected', state[g.dataset.group] === b.dataset.v)));
      on(sync);
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = '<span class="logo"></span>Tennis Record — Scoring — PXL_20260913_070045619' +
        '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
    }
    const nav = document.getElementById('nav');
    if (nav) {
      const item = (ic, label, active) =>
        `<a class="nav-item ${active ? 'active' : ''}"><span class="material-symbols-outlined">${ic}</span>${label}</a>`;
      nav.className = 'nav';
      nav.innerHTML =
        '<div class="nav-group">' + item('folder_open', 'Projects') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">MATCH</div>' +
        item('sports_tennis', 'Points') + item('scoreboard', 'Scoring', true) + item('bar_chart', 'Stats') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">VIDEO</div>' +
        item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export') + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
  }

  function setMock(group, value) {
    state[group] = value;
    if (group === 'scoring') {
      manualGame.clear();
      manualSet.clear();
      if (value === 'manual') {
        // The user has marked the same wins that the automatic scoring finds.
        const auto = (state.scoring = 'auto', computeTimeline());
        state.scoring = 'manual';
        points.forEach((p, i) => {
          const s = auto.states[i];
          if (s.gameWon) manualGame.set(p.id, s.gameWon);
          if (s.setWon) manualSet.set(p.id, s.setWon);
        });
      }
    }
    if (group === 'serve') {
      serverMarks.clear();
      if (value === 'marked' && points.length > 30) {
        serverMarks.set(points[0].id, 1);
        const computed = computeTimeline().servers[30];
        serverMarks.set(points[30].id, 3 - computed);
      }
    }
    if (group === 'hint') { if (value === 'on') showBalloon(); else hideBalloon(); }
    recompute();
    emit('data');
    emit('mock');
  }

  // A hash such as #scoring=manual&serve=marked&hint=on&point=12 sets the first state. It helps to take screenshots.
  const hash = new URLSearchParams(location.hash.slice(1));

  function start() {
    if (hash.get('points') === 'none') { points = []; outcomes.clear(); }
    recompute();
    if (hash.get('scoring')) setMock('scoring', hash.get('scoring'));
    if (hash.get('serve')) setMock('serve', hash.get('serve'));
    emit('data');
    const first = points.findIndex((p) => !outcomes.has(p.id));
    const wanted = hash.get('point') ? +hash.get('point') - 1 : (first >= 0 ? first : 0);
    if (points.length) select(Math.max(0, Math.min(points.length - 1, wanted)));
    else { emit('select', {}); emit('time'); }
    emit('mock');
    if (hash.get('hint') === 'on') setTimeout(() => setMock('hint', 'on'), 50);
    // A clicked button gives the focus back, so that the hotkeys keep working (as the app does with the player).
    document.addEventListener('mouseup', (e) => { const b = e.target.closest('button'); if (b) b.blur(); });
  }

  window.Mock = {
    state, players, actions, on, emit, current, counts, pointsText,
    fmt, fmtHms, fmtSec, fmtRel, escapeHtml, icon, kbd, pc,
    shell, video, scrub, transport, speed, list, start,
    get points() { return points; },
  };
})();
