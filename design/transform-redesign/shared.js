/*
 * Shared script for the Transform tab redesign mockups.
 * It keeps the transform values, simulates the player and the crop editor on the video,
 * draws the app shell and the playback bar, and binds the sliders, the value fields and the Reset button.
 * The texts, the ranges and the tooltips come from CropTransformControls.kt and SwingCropRotatePanel.kt.
 * The editor geometry is a port of CropEditorOverlay.kt and CropGeometryMath.kt.
 */
(function () {
  const DURATION = 5761000; // 01:36:01
  const MIN_OVERLAY = 24;
  const HANDLE = 10;
  const HANDLE_HIT = 14;
  const ROTATION_HANDLE_OFFSET = 24;

  // ---------- Controls ----------
  // The slider ranges and the tooltips are the same as in the current tab.
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const plus = (text, v) => (v > 0 ? '+' + text : text);
  const CONTROLS = {
    zoom: {
      label: 'Zoom', icon: 'zoom_in', tip: 'Zoom', min: 10, max: 400, def: 100, unit: '%', low: '10%', high: '400%',
      toText: (v) => String(v), fromText: (x) => Math.trunc(x),
    },
    panX: {
      label: 'Pan X', icon: 'swap_horiz', tip: 'Pan X', min: -100, max: 100, def: 0, unit: '', low: 'Left', high: 'Right',
      toText: (v) => plus(String(v), v), fromText: (x) => Math.trunc(x),
    },
    panY: {
      label: 'Pan Y', icon: 'swap_vert', tip: 'Pan Y', min: -100, max: 100, def: 0, unit: '', low: 'Down', high: 'Up',
      toText: (v) => plus(String(v), v), fromText: (x) => Math.trunc(x),
    },
    rotation: {
      label: 'Rotation', icon: 'rotate_right', tip: 'Rotation', min: -360, max: 360, def: 0, unit: '°', low: '−180°', high: '+180°',
      ticks: [-180, 180], // −90° and +90°
      toText: (v) => plus((v / 2).toFixed(1), v), fromText: (x) => Math.trunc(x * 2),
    },
    fine: {
      label: 'Fine rotation', icon: 'straighten', tip: 'Fine Rotation', min: -50, max: 50, def: 0, unit: '°', low: '−5°', high: '+5°',
      toText: (v) => plus((v / 10).toFixed(1), v), fromText: (x) => Math.trunc(x * 10),
    },
  };
  const ORDER = ['zoom', 'panX', 'panY', 'rotation', 'fine'];
  const GROUPS = [
    { id: 'crop', title: 'Crop', icon: 'crop', ids: ['zoom', 'panX', 'panY'] },
    { id: 'rotation', title: 'Rotation', icon: 'rotate_90_degrees_cw', ids: ['rotation', 'fine'] },
  ];

  const PRESETS = {
    default: { zoom: 1, panX: 0, panY: 0, coarse: 0, fine: 0 },
    adjusted: { zoom: 1.4, panX: -0.25, panY: 0.12, coarse: 4, fine: 3 },
  };

  // ---------- State ----------
  const state = {
    model: { zoom: 1, panX: 0, panY: 0 }, // AdjustmentsV1 zoom, panX, panY
    rot: { coarse: 0, fine: 0 },          // the two rotation sliders
    preset: 'default',                    // mockup switch: default | adjusted | custom
    hover: 'NONE',
    drag: 'NONE',
    focus: false,                         // the video has the keyboard focus
    playing: false,
    t: 1092000,
  };

  // ---------- Formatting ----------
  const pad = (n) => String(n).padStart(2, '0');
  function fmt(ms) {
    const s = Math.floor(Math.max(0, ms) / 1000);
    return `${pad(Math.floor(s / 3600))}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}`;
  }
  const angle = () => clamp(state.rot.coarse / 2 + state.rot.fine / 10, -180, 180);
  const signedAngle = (a = angle()) => (a > 0 ? '+' : a < 0 ? '−' : '') + Math.abs(a).toFixed(1) + '°';

  function sliderValue(id) {
    const m = state.model;
    switch (id) {
      case 'zoom': return clamp(Math.trunc(Math.round(m.zoom * 1000) / 10), 10, 400);
      case 'panX': return clamp(Math.trunc(Math.round(m.panX * 1000) / 10), -100, 100);
      case 'panY': return clamp(Math.trunc(Math.round(m.panY * 1000) / 10), -100, 100);
      case 'rotation': return state.rot.coarse;
      case 'fine': return state.rot.fine;
    }
    return 0;
  }
  const isChanged = (id) => sliderValue(id) !== CONTROLS[id].def;
  // The status counts the four values of the transform: zoom, pan X, pan Y and the angle.
  function changedCount() {
    const m = state.model;
    return [Math.abs(m.zoom - 1) > 0.0005, Math.abs(m.panX) > 0.0005, Math.abs(m.panY) > 0.0005, angle() !== 0]
      .filter(Boolean).length;
  }

  // ---------- Events ----------
  const listeners = [];
  const on = (fn) => listeners.push(fn);

  function setSlider(id, raw) {
    const c = CONTROLS[id];
    const v = clamp(Math.round(raw), c.min, c.max);
    switch (id) {
      case 'zoom': state.model.zoom = clamp(v / 100, 0.1, 4); break;
      case 'panX': state.model.panX = v / 100; break;
      case 'panY': state.model.panY = v / 100; break;
      case 'rotation': state.rot.coarse = v; break;
      case 'fine': state.rot.fine = v; break;
    }
    state.preset = 'custom';
    render();
  }
  // An angle from the rotation handle goes back into the two sliders, as renderRotation() in the tab.
  function setAngle(deg) {
    const a = clamp(deg, -180, 180);
    const coarse = clamp(Math.round(a * 2), -360, 360);
    state.rot.coarse = coarse;
    state.rot.fine = clamp(Math.round((a - coarse / 2) * 10), -50, 50);
    state.preset = 'custom';
    render();
  }
  function setModel(next) {
    Object.assign(state.model, next);
    state.preset = 'custom';
    render();
  }
  function applyPreset(name) {
    const p = PRESETS[name];
    state.model = { zoom: p.zoom, panX: p.panX, panY: p.panY };
    state.rot = { coarse: p.coarse, fine: p.fine };
    state.preset = name;
    render();
  }
  const reset = () => applyPreset('default');

  // ---------- Geometry (CropGeometryMath) ----------
  function normalizeRotation(d) {
    while (d > 180) d -= 360;
    while (d < -180) d += 360;
    return d;
  }
  function overlayFromModel(W, H) {
    const m = state.model;
    const zoom = clamp(m.zoom, 0.1, 4);
    const cw = clamp(W / zoom, Math.min(MIN_OVERLAY, W), W);
    const ch = clamp(H / zoom, Math.min(MIN_OVERLAY, H), H);
    const tx = Math.max(0, W - cw);
    const ty = Math.max(0, H - ch);
    const cx = W / 2 + clamp(m.panX, -1, 1) * tx / 2;
    const cy = H / 2 - clamp(m.panY, -1, 1) * ty / 2;
    return { x: clamp(cx - cw / 2, 0, tx), y: clamp(cy - ch / 2, 0, ty), w: cw, h: ch };
  }
  function modelFromOverlay(W, H, r) {
    const c = clampInside(r, { x: 0, y: 0, w: W, h: H }, W / H);
    return {
      zoom: clamp(W / c.w, 0.1, 4),
      panX: c.w < W - 0.5 ? clamp((2 * c.x + c.w - W) / (W - c.w), -1, 1) : 0,
      panY: c.h < H - 0.5 ? clamp((H - (2 * c.y + c.h)) / (H - c.h), -1, 1) : 0,
    };
  }
  function coerce(v, lo, hi) { return hi <= lo ? lo : clamp(v, lo, hi); }
  function clampInside(r, b, aspect) {
    const maxW = Math.max(Math.min(b.w, b.h * aspect), Math.min(MIN_OVERLAY, b.w));
    const w = clamp(r.w, Math.min(MIN_OVERLAY, maxW), maxW);
    const h = Math.min(w / aspect, b.h);
    return { x: coerce(r.x, b.x, b.x + b.w - w), y: coerce(r.y, b.y, b.y + b.h - h), w, h };
  }
  function centeredResize(s, b, aspect, dw) {
    const cx = s.x + s.w / 2, cy = s.y + s.h / 2, w = s.w + dw;
    return clampInside({ x: cx - w / 2, y: cy - w / aspect / 2, w, h: w / aspect }, b, aspect);
  }
  function inscribed(W, H, deg, aspect) {
    const n = normalizeRotation(deg);
    if (Math.abs(n) < 0.001 || Math.abs(Math.abs(n) - 180) < 0.001) return { x: 0, y: 0, w: W, h: H };
    const rad = n * Math.PI / 180, cos = Math.cos(rad), sin = Math.sin(rad);
    const fits = (cw) => {
      const chh = cw / aspect;
      return [[-cw / 2, -chh / 2], [cw / 2, -chh / 2], [cw / 2, chh / 2], [-cw / 2, chh / 2]].every(([x, y]) =>
        Math.abs(x * cos + y * sin) <= W / 2 + 0.001 && Math.abs(-x * sin + y * cos) <= H / 2 + 0.001);
    };
    let lo = 0, hi = Math.min(W, H * aspect);
    for (let i = 0; i < 40; i++) { const mid = (lo + hi) / 2; if (fits(mid)) lo = mid; else hi = mid; }
    const w = Math.max(Math.min(MIN_OVERLAY, W), lo), h = Math.min(w / aspect, H);
    return { x: (W - w) / 2, y: (H - h) / 2, w, h };
  }
  function snapRotation(deg, force) {
    const n = normalizeRotation(deg);
    const closest = [-180, -90, 0, 90, 180].reduce((a, b) => (Math.abs(b - n) < Math.abs(a - n) ? b : a));
    return force || Math.abs(closest - n) <= 2 ? normalizeRotation(closest) : n;
  }

  // ---------- Crop editor on the video (CropEditorOverlay) ----------
  const stages = [];

  function stage(el, opts = {}) {
    el.tabIndex = 0;
    el.setAttribute('aria-label', 'Video with the crop rectangle');
    el.innerHTML =
      '<div class="frame-box"><img src="frame.jpg" alt="Video preview"></div>' +
      '<svg class="editor"></svg>' +
      (opts.focusChip === false ? '' : '<span class="focus-chip" data-focus-chip></span>') +
      (opts.extra || '');
    const s = { el, box: null, opts, img: el.querySelector('img'), svg: el.querySelector('svg'), drag: null };
    stages.push(s);

    const layout = () => {
      const W = el.clientWidth, H = el.clientHeight;
      const aspect = s.img.naturalWidth ? s.img.naturalWidth / s.img.naturalHeight : 16 / 9;
      let w = W, h = W / aspect;
      if (h > H) { h = H; w = H * aspect; }
      s.box = { x: (W - w) / 2, y: (H - h) / 2, w, h };
      Object.assign(el.querySelector('.frame-box').style, { left: s.box.x + 'px', top: s.box.y + 'px', width: w + 'px', height: h + 'px' });
      draw(s);
    };
    s.img.addEventListener('load', layout);
    new ResizeObserver(layout).observe(el);

    const point = (ev) => { const r = el.getBoundingClientRect(); return { x: ev.clientX - r.left, y: ev.clientY - r.top }; };
    el.addEventListener('pointerdown', (ev) => {
      if (ev.button !== 0) return;
      el.focus();
      const p = point(ev);
      const hit = hitTest(s, p.x, p.y);
      if (hit === 'NONE') return;
      el.setPointerCapture(ev.pointerId);
      s.drag = { hit, start: p, rect: overlayRect(s.box), angle: angle() };
      state.drag = hit;
      draw(s);
      ev.preventDefault();
    });
    el.addEventListener('pointermove', (ev) => {
      const p = point(ev);
      if (s.drag) { handleDrag(s, p, ev.shiftKey); return; }
      updateHover(s, hitTest(s, p.x, p.y));
    });
    const end = (ev) => {
      if (!s.drag) return;
      s.drag = null;
      state.drag = 'NONE';
      const p = point(ev);
      updateHover(s, hitTest(s, p.x, p.y), true);
    };
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
    el.addEventListener('pointerleave', () => { if (!s.drag) updateHover(s, 'NONE'); });
    el.addEventListener('focus', () => { state.focus = true; render(); });
    el.addEventListener('blur', () => { state.focus = false; render(); });
    // The arrow keys move the crop rectangle only when the video has the focus.
    el.addEventListener('keydown', (ev) => {
      const d = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[ev.key];
      if (!d) return;
      ev.preventDefault();
      const k = ev.shiftKey ? 10 : 1;
      nudge(s, d[0] * k, d[1] * k);
    });
    return s;
  }

  const aspectOf = (box) => (box.h > 0 ? box.w / box.h : 16 / 9);
  function allowedBounds(box) {
    const l = inscribed(box.w, box.h, angle(), aspectOf(box));
    return { x: box.x + l.x, y: box.y + l.y, w: l.w, h: l.h };
  }
  function overlayRect(box) {
    const m = overlayFromModel(box.w, box.h);
    return clampInside({ x: m.x + box.x, y: m.y + box.y, w: m.w, h: m.h }, allowedBounds(box), aspectOf(box));
  }
  function modelFrom(box, r) {
    return modelFromOverlay(box.w, box.h, { x: r.x - box.x, y: r.y - box.y, w: r.w, h: r.h });
  }
  function handleRects(o, size) {
    const half = size / 2, cx = o.x + o.w / 2, cy = o.y + o.h / 2, l = o.x, r = o.x + o.w, t = o.y, b = o.y + o.h;
    const rect = (x, y) => ({ x: x - half, y: y - half, w: size, h: size });
    return [['NW', rect(l, t)], ['N', rect(cx, t)], ['NE', rect(r, t)], ['E', rect(r, cy)],
      ['SE', rect(r, b)], ['S', rect(cx, b)], ['SW', rect(l, b)], ['W', rect(l, cy)]];
  }
  const rotationHandle = (o) => ({ x: o.x + o.w / 2, y: o.y - ROTATION_HANDLE_OFFSET });

  function hitTest(s, x, y) {
    if (!s.box) return 'NONE';
    const o = overlayRect(s.box);
    const rh = rotationHandle(o);
    if (Math.hypot(rh.x - x, rh.y - y) <= HANDLE_HIT) return 'ROTATE';
    for (const [hit, r] of handleRects(o, HANDLE_HIT)) {
      if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) return hit;
    }
    if (x >= o.x && x <= o.x + o.w && y >= o.y && y <= o.y + o.h) return 'MOVE';
    return 'NONE';
  }
  const CURSORS = { MOVE: 'move', N: 'ns-resize', S: 'ns-resize', E: 'ew-resize', W: 'ew-resize', NW: 'nwse-resize', SE: 'nwse-resize', NE: 'nesw-resize', SW: 'nesw-resize', ROTATE: 'pointer', NONE: 'default' };
  function updateHover(s, hit, force) {
    if (hit === state.hover && !force) return;
    state.hover = hit;
    s.el.style.cursor = CURSORS[hit];
    draw(s);
  }

  function handleDrag(s, p, shift) {
    const box = s.box, d = s.drag, aspect = aspectOf(box), bounds = allowedBounds(box);
    const dx = p.x - d.start.x, dy = p.y - d.start.y;
    if (d.hit === 'ROTATE') {
      const o = overlayRect(box);
      const deg = Math.atan2(p.y - (o.y + o.h / 2), p.x - (o.x + o.w / 2)) * 180 / Math.PI + 90;
      setAngle(snapRotation(deg, shift));
      return;
    }
    const r = d.rect;
    let next = r;
    if (d.hit === 'MOVE') next = clampInside({ ...r, x: r.x + dx, y: r.y + dy }, bounds, aspect);
    else if (d.hit === 'N' || d.hit === 'S') next = centeredResize(r, bounds, aspect, -2 * (d.hit === 'N' ? dy : -dy));
    else if (d.hit === 'E' || d.hit === 'W') next = centeredResize(r, bounds, aspect, 2 * (d.hit === 'E' ? dx : -dx));
    else {
      const sx = d.hit === 'NE' || d.hit === 'SE' ? dx : -dx;
      const sy = d.hit === 'SW' || d.hit === 'SE' ? dy : -dy;
      next = centeredResize(r, bounds, aspect, 2 * (Math.abs(sx) > Math.abs(sy) ? sx : sy * aspect));
    }
    setModel(modelFrom(box, next));
  }

  function nudge(s, dx, dy) {
    if (!s.box) return;
    const o = overlayRect(s.box);
    setModel(modelFrom(s.box, clampInside({ ...o, x: o.x + dx, y: o.y + dy }, allowedBounds(s.box), aspectOf(s.box))));
  }

  function draw(s) {
    if (!s.box) return;
    const b = s.box, o = overlayRect(b);
    const active = state.hover !== 'NONE' || state.drag !== 'NONE';
    const stroke = active ? '#a1fe00' : '#ffffff';
    const rh = rotationHandle(o);
    const handles = handleRects(o, HANDLE).map(([, r]) =>
      `<rect x="${r.x}" y="${r.y}" width="${r.w}" height="${r.h}" fill="#0e0e0e" stroke="${stroke}" stroke-width="1.5"/>`).join('');
    let svg =
      `<path fill="#000" fill-opacity="0.58" fill-rule="evenodd" d="M${b.x},${b.y}h${b.w}v${b.h}h${-b.w}z M${o.x},${o.y}h${o.w}v${o.h}h${-o.w}z"/>` +
      `<rect x="${o.x}" y="${o.y}" width="${o.w}" height="${o.h}" fill="none" stroke="${stroke}" stroke-width="2"/>` +
      handles +
      `<line x1="${o.x + o.w / 2}" y1="${o.y}" x2="${rh.x}" y2="${rh.y}" stroke="${stroke}" stroke-width="1.4"/>` +
      `<circle cx="${rh.x}" cy="${rh.y}" r="${HANDLE / 2}" fill="#0e0e0e" stroke="${stroke}" stroke-width="1.5"/>`;
    if (s.opts.badge) {
      // Option B: a value label under the crop rectangle.
      const text = `${sliderValue('zoom')}%  ·  ${signedAngle()}`;
      const w = text.length * 6.6 + 16, x = o.x + o.w / 2 - w / 2, y = Math.min(o.y + o.h + 10, b.y + b.h - 26);
      svg += `<g class="rect-badge"><rect x="${x}" y="${y}" width="${w}" height="20" rx="10" fill="rgba(0,0,0,.78)" stroke="${active ? 'rgba(161,254,0,.6)' : '#3a3a3a'}"/>` +
        `<text x="${x + w / 2}" y="${y + 14}" text-anchor="middle" fill="#e4e4e4" font-size="11.5" font-weight="600" style="font-variant-numeric:tabular-nums">${text}</text></g>`;
    }
    s.svg.innerHTML = svg;
    s.img.style.transform = `rotate(${angle()}deg)`;
  }

  // ---------- Slider and field markup ----------
  const pct = (c, v) => ((v - c.min) / (c.max - c.min)) * 100;
  function slider(id, opts = {}) {
    const c = CONTROLS[id];
    const ticks = `<span class="tick" style="left:${pct(c, c.def)}%"></span>` +
      (c.ticks || []).map((v) => `<span class="tick minor" style="left:${pct(c, v)}%"></span>`).join('');
    return `<div class="tslider ${opts.sm ? 'sm' : ''}" data-ts="${id}" title="${c.tip}">
      <div class="track"><span class="fill"></span>${ticks}</div>
      <input type="range" min="${c.min}" max="${c.max}" step="1" value="${c.def}" data-ctl="${id}" aria-label="${c.label}">
    </div>`;
  }
  function field(id) {
    const c = CONTROLS[id];
    return `<label class="vfield" data-vf="${id}" title="${c.tip}"><input type="text" inputmode="decimal" data-field="${id}" aria-label="${c.label} value" spellcheck="false"><span class="unit">${c.unit}</span></label>`;
  }

  // ---------- Rendering ----------
  function render() {
    ORDER.forEach((id) => {
      const c = CONTROLS[id], v = sliderValue(id), changed = v !== c.def;
      const p = pct(c, v), d = pct(c, c.def);
      document.querySelectorAll(`[data-ts="${id}"]`).forEach((el) => {
        el.style.setProperty('--lo', Math.min(d, p) + '%');
        el.style.setProperty('--hi', Math.max(d, p) + '%');
        el.classList.toggle('is-changed', changed);
      });
      document.querySelectorAll(`input[data-ctl="${id}"]`).forEach((el) => { if (Number(el.value) !== v) el.value = v; });
      document.querySelectorAll(`input[data-field="${id}"]`).forEach((el) => {
        if (document.activeElement !== el) el.value = c.toText(v);
      });
      document.querySelectorAll(`[data-vf="${id}"]`).forEach((el) => el.classList.toggle('is-changed', changed));
      document.querySelectorAll(`[data-val="${id}"]`).forEach((el) => {
        el.textContent = c.toText(v) + c.unit;
        el.classList.toggle('is-changed', changed);
      });
      document.querySelectorAll(`[data-row="${id}"]`).forEach((el) => el.classList.toggle('is-changed', changed));
    });
    const n = changedCount();
    document.querySelectorAll('[data-changed]').forEach((el) => {
      el.textContent = n ? `${n} of 4 changed` : 'All at default';
      el.classList.toggle('is-changed', n > 0);
    });
    document.querySelectorAll('[data-angle]').forEach((el) => {
      el.textContent = signedAngle();
      el.classList.toggle('is-changed', angle() !== 0);
    });
    document.querySelectorAll('[data-reset]').forEach((el) => el.classList.toggle('is-quiet', n === 0));
    document.querySelectorAll('[data-focus-chip]').forEach((el) => {
      el.innerHTML = state.focus
        ? '<span class="material-symbols-outlined">keyboard</span><kbd>←</kbd><kbd>↑</kbd><kbd>→</kbd><kbd>↓</kbd> move 1 px · <kbd>Shift</kbd> 10 px'
        : '<span class="material-symbols-outlined">keyboard</span>Click the video to move the crop with the arrow keys';
    });
    stages.forEach((s) => { s.el.classList.toggle('has-focus', state.focus); draw(s); });
    listeners.forEach((fn) => fn());
  }

  function bind() {
    document.addEventListener('input', (ev) => {
      const t = ev.target;
      if (!t.dataset) return;
      if (t.dataset.ctl) { setSlider(t.dataset.ctl, Number(t.value)); return; }
      if (t.dataset.field) {
        // The same as the DocumentListener in the tab: each valid number moves the slider at once.
        const parsed = parseFloat(t.value.trim().replace('−', '-'));
        if (Number.isNaN(parsed)) return;
        setSlider(t.dataset.field, CONTROLS[t.dataset.field].fromText(parsed));
      }
    });
    document.addEventListener('focusout', (ev) => { if (ev.target.dataset && ev.target.dataset.field) setTimeout(render); });
    document.addEventListener('keydown', (ev) => {
      if (ev.key === 'Enter' && ev.target.dataset && ev.target.dataset.field) ev.target.blur();
    });
    document.addEventListener('click', (ev) => { if (ev.target.closest('[data-reset]')) reset(); });
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
      state.t = Math.round(clamp((ev.clientX - r.left) / r.width, 0, 1) * DURATION);
      renderTransport();
    };
    scrub.addEventListener('pointerdown', (ev) => { scrub.setPointerCapture(ev.pointerId); seekTo(ev); });
    scrub.addEventListener('pointermove', (ev) => { if (scrub.hasPointerCapture(ev.pointerId)) seekTo(ev); });
    renderTransport();
  }

  // SPACE plays or pauses, as AppShortcuts.PLAY_PAUSE in the tab. A value field keeps its SPACE.
  document.addEventListener('keydown', (ev) => {
    if (ev.key !== ' ' || (ev.target.dataset && ev.target.dataset.field)) return;
    ev.preventDefault();
    togglePlay();
  });

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'option-a.html', id: 'a', label: 'A · Grouped panel (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Narrow panel + video labels' },
    { href: 'option-c.html', id: 'c', label: 'C · Dial and ruler' },
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
        '<button data-v="default">Default</button><button data-v="adjusted">Adjusted</button></div>';
      bar.addEventListener('click', (ev) => {
        const b = ev.target.closest('.mock-states button');
        if (!b) return;
        applyPreset(b.dataset.v);
        b.blur();
      });
      on(() => bar.querySelectorAll('.mock-states button').forEach((b) => b.classList.toggle('is-selected', state.preset === b.dataset.v)));
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = '<span class="logo"></span>BananaShot — Transform — PXL_20260913_070045619' +
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
        item('palette', 'Colors') + item('crop_rotate', 'Transform', true) + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export') + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
  }

  // The mouse and key hints. The texts come from the Transform help (HelpCatalog.kt).
  const HINTS = [
    { icon: 'open_with', what: 'Drag inside the rectangle', does: 'to move it.' },
    { icon: 'aspect_ratio', what: 'Drag a square handle', does: 'to resize. The aspect ratio and the center stay.' },
    { icon: 'rotate_right', what: 'Drag the round handle', does: 'to rotate. Hold <kbd>Shift</kbd> to snap to 90°.' },
    { icon: 'keyboard', what: 'Click the video, then', does: '<kbd>←</kbd><kbd>↑</kbd><kbd>→</kbd><kbd>↓</kbd> move 1 px. <kbd>Shift</kbd> moves 10 px.' },
  ];
  const hintList = () =>
    '<ul class="hint-list">' + HINTS.map((h) =>
      `<li><span class="material-symbols-outlined">${h.icon}</span><span><b>${h.what}</b> ${h.does}</span></li>`).join('') + '</ul>';

  // A hash such as #preset=adjusted sets the first state. It helps to take screenshots.
  function start() {
    const params = new URLSearchParams(location.hash.slice(1));
    bind();
    if (params.get('preset') in PRESETS) applyPreset(params.get('preset'));
    else render();
  }

  window.Mock = {
    state, CONTROLS, ORDER, GROUPS, HINTS,
    on, render, start, shell, transport, stage, slider, field, hintList,
    setSlider, setAngle, reset, angle, signedAngle, sliderValue, isChanged, togglePlay,
  };
})();
