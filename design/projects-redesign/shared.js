/*
 * Shared script for the Projects tab redesign mockups.
 * It keeps the mock data and state, draws the app shell, and runs the dialogs:
 * Select Source Video, New project, Rename project, Delete project and the error message.
 * The texts of the dialogs and the messages come from the current app code.
 * Each option page draws the tab content in its own render function (Mock.on).
 */
(function () {
  // ---------- Mock data ----------
  const PAGE_SIZE = 10; // DefaultProjectsPresenter.pageSize
  const VIDEO_EXTENSIONS = ['mp4', 'mov', 'mkv', 'avi', 'm4v', 'wmv'];
  const INVALID_CHARS = ['<', '>', ':', '"', '/', '\\', '|', '?', '*'];
  const RESERVED = ['CON', 'PRN', 'AUX', 'NUL', 'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9',
    'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9'];

  // Files in the fake file picker. The path check of the New project dialog uses this list as the disk.
  const DISK = [
    { dir: 'D:\\video\\', file: 'PXL_20260927_091512004.mp4', size: '9.84 GB', date: '27.09.2026' },
    { dir: 'D:\\video\\', file: 'PXL_20260926_174233981.mp4', size: '6.12 GB', date: '26.09.2026' },
    { dir: 'D:\\video\\', file: 'GX010511.MP4', size: '3.90 GB', date: '25.09.2026' },
    { dir: 'D:\\video\\', file: 'notes.txt', size: '2 KB', date: '20.09.2026' },
  ];

  function project(name, path, duration, size, scored, total, fav, extra) {
    return Object.assign({
      id: 'p' + (++project.n), name, path, duration, size, scored, total, fav,
      manifest: 'C:\\Users\\me\\TennisRecord\\projects\\' + name.replace(/[^\w]+/g, '-').toLowerCase() + '\\project.json',
    }, extra || {});
  }
  project.n = 0;

  function seedProjects() {
    project.n = 0;
    const list = [
      project('PXL_20260913_070045619', 'D:\\video\\PXL_20260913_070045619.mp4', '1:36:01', '12.43 GB', 96, 128, 43),
      project('Club final vs Marco', 'D:\\video\\2026\\PXL_20260920_153210877.mp4', '1:52:44', '14.61 GB', 142, 142, 38),
      project('PXL_20260828_100310593', 'D:\\video\\PXL_20260828_100310593.mp4', '1:08:15', '8.87 GB', 96, 96, 21),
      project('Saturday doubles', 'E:\\camera\\DCIM\\100GOPRO\\GX010482.MP4', '—', '—', 54, 60, 12, { missing: true }),
      project('Serve practice 11 Sep', 'D:\\video\\PXL_20260911_181502113.mp4', '0:42:09', '5.02 GB', 0, 61, 9),
      project('dfdf (2)', 'D:\\video\\test\\dfdf.mp4', '0:04:12', '312.40 MB', 3, 5, 1),
      project('Old import', null, '—', '—', 0, 0, 0, { noVideo: true }),
    ];
    // More camera files, so the list has three pages.
    const days = ['0826', '0824', '0821', '0819', '0817', '0814', '0812', '0810', '0807', '0805', '0803', '0731', '0729', '0727', '0724', '0722'];
    days.forEach((d, i) => {
      const n = `PXL_2026${d}_${String(70000 + i * 13377).padStart(6, '0')}${100 + i * 7}`;
      const min = 38 + ((i * 29) % 70);
      const total = 40 + ((i * 17) % 90);
      const scored = i % 3 === 0 ? total : Math.round(total * ((i * 37) % 100) / 100);
      list.push(project(n, `D:\\video\\${n}.mp4`, `${Math.floor(min / 60)}:${String(min % 60).padStart(2, '0')}:${String((i * 23) % 60).padStart(2, '0')}`,
        (min * 0.128).toFixed(2) + ' GB', scored, total, Math.round(scored * 0.3)));
    });
    return list;
  }

  // ---------- State ----------
  const state = {
    scenario: 'open',   // open | none | empty | loading
    projects: seedProjects(),
    current: null,      // id of the open project
    page: 1,
    loading: false,     // true: the presenter did not load the figures yet
    hot: null,          // id of the row under the pointer or with focus (for some options)
  };
  const listeners = [];
  function on(fn) { listeners.push(fn); }
  function emit() { listeners.forEach((fn) => fn(state)); }

  function setScenario(s) {
    state.scenario = s;
    state.projects = s === 'empty' ? [] : seedProjects();
    state.current = s === 'open' || s === 'loading' ? state.projects[0].id : null;
    state.loading = s === 'loading';
    state.page = 1;
    emit();
  }

  // ---------- Queries ----------
  function totalPages() { return Math.max(1, Math.ceil(state.projects.length / PAGE_SIZE)); }
  function visible() {
    state.page = Math.min(Math.max(1, state.page), totalPages());
    return state.projects.slice((state.page - 1) * PAGE_SIZE, state.page * PAGE_SIZE);
  }
  function current() { return state.projects.find((p) => p.id === state.current) || null; }
  function byId(id) { return state.projects.find((p) => p.id === id); }
  function isCurrent(p) { return p && p.id === state.current; }

  /** The text under the name: the video path, or the manifest path when the project has no video. */
  function secondary(p) { return p.path || p.manifest; }
  function splitPath(path) {
    const i = path.lastIndexOf('\\');
    return { dir: path.slice(0, i + 1), file: path.slice(i + 1) };
  }
  function esc(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  const TIPS = {
    open: 'Open the project and continue in the Points tab. Double-click the row also opens it.',
    rename: 'Rename project',
    del: 'Delete project',
    delDisabled: 'You cannot delete the open project.',
    missing: 'The video is not on the disk anymore.',
    importMatch: 'Select a match video and create a new project',
  };

  /** HTML of the path line. Shows the red missing-video icon and its tooltip when the video is not on the disk. */
  function pathHtml(p, opts) {
    const o = opts || {};
    const { dir, file } = splitPath(secondary(p));
    if (p.missing) {
      return `<div class="path missing" title="${esc(TIPS.missing)}"><span class="material-symbols-outlined">videocam_off</span>` +
        `<span class="miss">Video not found</span><span class="faint">·</span>` +
        `<span class="path-text"><span class="path-file">${esc(file)}</span> <span class="path-dir">${esc(dir)}</span></span></div>`;
    }
    const icon = p.noVideo ? 'description' : 'movie';
    const dirHtml = o.noDir ? '' : ` <span class="path-dir">${esc(dir)}</span>`;
    return `<div class="path" title="${esc(secondary(p))}"><span class="material-symbols-outlined">${icon}</span>` +
      `<span class="path-text"><span class="path-file">${esc(file)}</span>${dirHtml}</span></div>`;
  }

  /** Figure text, or a loading placeholder while the presenter loads the figures. */
  function fig(p, key, width) {
    if (state.loading) return `<span class="skel" style="width:${width || 40}px"></span>`;
    if (key === 'scored') return `${p.scored}<span class="faint">/${p.total}</span>`;
    return esc(p[key]);
  }
  function scoredBar(p) {
    if (state.loading) return '<div class="bar none"><i></i></div>';
    const pct = p.total ? Math.round((p.scored / p.total) * 100) : 0;
    const cls = pct === 100 ? '' : pct === 0 ? 'none' : 'part';
    return `<div class="bar ${cls}" title="${p.scored} of ${p.total} points have a score"><i style="width:${pct}%"></i></div>`;
  }

  // ---------- Actions ----------
  function toast(html) {
    let el = document.getElementById('toast');
    if (!el) { el = document.createElement('div'); el.id = 'toast'; el.className = 'toast'; document.body.appendChild(el); }
    el.innerHTML = html;
    el.hidden = false;
    clearTimeout(toast.t);
    toast.t = setTimeout(() => { el.hidden = true; }, 3200);
  }

  function makeCurrent(p) {
    state.current = p.id;
    // The repository puts the opened project first in the recent list.
    state.projects = [p].concat(state.projects.filter((x) => x !== p));
    state.page = 1;
    if (state.scenario === 'none') state.scenario = 'open';
    emit();
    toast(`<b>${esc(p.name)}</b> is open. In the app, the Points tab opens now.`);
  }

  function openProject(id) {
    const p = byId(id);
    if (!p) return;
    if (p.noVideo) {
      picker(`Select Source Video for Project: ${p.name}`, (path) => { p.path = path; p.noVideo = false; makeCurrent(p); });
      return;
    }
    if (p.missing) {
      alertBox('error', 'Cannot open project',
        `<p>The video of the project is not on the disk anymore:</p><p><code>${esc(p.path)}</code></p><p>Put the video back at this location to open the project.</p>`);
      return;
    }
    makeCurrent(p);
  }

  function importMatch() {
    picker('Select Source Video', (path) => newProjectDialog(path));
  }

  function renameProject(id) {
    const p = byId(id);
    if (!p) return;
    const m = modal(`
      <div class="modal sm" role="dialog" aria-label="Rename project">
        <div class="modal-head"><h3>Rename project</h3></div>
        <div class="modal-body">
          <label class="fld"><span>Project name</span><input class="inp" id="rn-name" value="${esc(p.name)}"></label>
          <div class="form-error" id="rn-err"></div>
        </div>
        <div class="modal-foot"><span class="grow"></span>
          <button class="btn" data-close>Cancel</button>
          <button class="btn btn-lime btn-sm" id="rn-ok">Rename</button>
        </div>
      </div>`);
    const input = m.querySelector('#rn-name');
    const ok = m.querySelector('#rn-ok');
    const check = () => {
      const err = nameError(input.value);
      m.querySelector('#rn-err').textContent = err || '';
      input.classList.toggle('bad', !!err);
      ok.disabled = !!err;
      return !err;
    };
    input.addEventListener('input', check);
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') ok.click(); });
    ok.addEventListener('click', () => {
      if (!check()) return;
      p.name = input.value.trim();
      close();
      emit();
    });
    check();
    input.select();
  }

  function deleteProject(id) {
    const p = byId(id);
    if (!p || isCurrent(p)) return;
    const m = alertBox('delete', 'Delete project',
      `<p>Delete the project "<b style="color:var(--fg)">${esc(p.name)}</b>"?</p>` +
      '<p>The app removes the points, the scores, and the settings of the project. The video file stays on the disk.</p>',
      '<button class="btn" data-close>Cancel</button><button class="btn btn-danger-fill" id="del-ok">Delete project</button>');
    m.querySelector('#del-ok').addEventListener('click', () => {
      state.projects = state.projects.filter((x) => x !== p);
      close();
      emit();
    });
  }

  function goPage(n) { state.page = n; emit(); }

  // ---------- Validation (NewProjectRules) ----------
  function nameError(name) {
    const t = name.trim();
    if (!t) return 'Type a project name.';
    if (t.length > 80) return 'Use a maximum of 80 characters in the project name.';
    if ([...t].some((c) => INVALID_CHARS.includes(c) || c.charCodeAt(0) < 32)) {
      return 'Do not use these characters in the project name: ' + INVALID_CHARS.join(' ');
    }
    if (t.endsWith('.')) return 'Do not use a period at the end of the project name.';
    if (RESERVED.includes(t.split('.')[0].toUpperCase())) return `Windows reserves the name "${t}". Use a different name.`;
    return null;
  }
  function videoError(path) {
    const t = path.trim();
    if (!t) return 'Select the match video.';
    const known = DISK.some((f) => (f.dir + f.file).toLowerCase() === t.toLowerCase());
    if (!known) return 'The video file does not exist.';
    const ext = t.split('.').pop().toLowerCase();
    if (!VIDEO_EXTENSIONS.includes(ext)) return `Select a video file (${VIDEO_EXTENSIONS.map((x) => x.toUpperCase()).join(', ')}).`;
    return null;
  }
  function suggestedName(path) {
    const file = splitPath(path.trim()).file;
    const i = file.lastIndexOf('.');
    return (i > 0 ? file.slice(0, i) : file).trim() || 'New project';
  }

  // ---------- Dialogs ----------
  let back = null;
  function modal(html) {
    close();
    back = document.createElement('div');
    back.className = 'modal-back';
    back.innerHTML = html;
    back.addEventListener('click', (e) => { if (e.target === back || e.target.closest('[data-close]')) close(); });
    document.body.appendChild(back);
    return back;
  }
  function close() { if (back) { back.remove(); back = null; } }
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });

  function alertBox(kind, title, bodyHtml, buttonsHtml) {
    const icon = kind === 'delete'
      ? '<span class="material-symbols-outlined" style="color:var(--red)">delete</span>'
      : '<span class="material-symbols-outlined" style="color:var(--red)">error</span>';
    return modal(`
      <div class="modal sm" role="alertdialog" aria-label="${esc(title)}">
        <div class="alert-body">${icon}<div><h3>${esc(title)}</h3>${bodyHtml}</div></div>
        <div class="modal-foot"><span class="grow"></span>${buttonsHtml || '<button class="btn" data-close>OK</button>'}</div>
      </div>`);
  }

  /** A fake system file picker. It is only here to show the order of the steps. */
  function picker(title, onPick) {
    let sel = 0;
    const m = modal(`
      <div class="picker" role="dialog" aria-label="${esc(title)}">
        <div class="picker-title"><span>${esc(title)}</span><span>✕</span></div>
        <div class="picker-dir">This PC › Data (D:) › video</div>
        <div class="picker-list">${DISK.map((f, i) => `
          <div class="picker-row" data-i="${i}"><span>${f.file.endsWith('.txt') ? '📄' : '🎞'}</span><span>${esc(f.file)}</span><span>${f.size}</span></div>`).join('')}
        </div>
        <div class="picker-foot"><button data-close>Cancel</button><button class="go" id="pk-ok">Open</button></div>
        <div class="picker-note">System dialog. It is not part of the redesign.</div>
      </div>`);
    const rows = m.querySelectorAll('.picker-row');
    const mark = () => rows.forEach((r, i) => r.classList.toggle('sel', i === sel));
    rows.forEach((r) => {
      r.addEventListener('click', () => { sel = Number(r.dataset.i); mark(); });
      r.addEventListener('dblclick', () => m.querySelector('#pk-ok').click());
    });
    m.querySelector('#pk-ok').addEventListener('click', () => { const f = DISK[sel]; close(); onPick(f.dir + f.file); });
    mark();
  }

  function newProjectDialog(path, keepName) {
    let suggested = suggestedName(path);
    const name = keepName != null ? keepName : suggested;
    const m = modal(`
      <div class="modal" role="dialog" aria-label="New project">
        <div class="modal-head"><h3>New project</h3></div>
        <div class="modal-body">
          <div class="steps">
            <span class="s done"><b>✓</b>Select the video</span><span class="sep"></span>
            <span class="s now"><b>2</b>Check the name</span><span class="sep"></span>
            <span class="s"><b>3</b>Mark the points</span>
          </div>
          <label class="fld"><span>Project name</span><input class="inp" id="np-name" value="${esc(name)}"></label>
          <div class="form-error" id="np-name-err"></div>
          <div class="fld"><span>Match video</span>
            <div class="inp-row"><input class="inp" id="np-video" value="${esc(path)}"><button class="btn" id="np-browse" title="Select a different match video">Browse…</button></div>
          </div>
          <div class="form-error" id="np-video-err"></div>
        </div>
        <div class="modal-foot"><span class="grow"></span>
          <button class="btn" data-close>Cancel</button>
          <button class="btn btn-lime btn-sm" id="np-ok"><span class="material-symbols-outlined">add</span>Create project</button>
        </div>
      </div>`);
    const nameIn = m.querySelector('#np-name');
    const videoIn = m.querySelector('#np-video');
    const ok = m.querySelector('#np-ok');
    const check = () => {
      const ne = nameError(nameIn.value);
      const ve = videoError(videoIn.value);
      m.querySelector('#np-name-err').textContent = ne || '';
      m.querySelector('#np-video-err').textContent = ve || '';
      nameIn.classList.toggle('bad', !!ne);
      videoIn.classList.toggle('bad', !!ve);
      ok.disabled = !!(ne || ve);
      return !(ne || ve);
    };
    nameIn.addEventListener('input', check);
    videoIn.addEventListener('input', check);
    [nameIn, videoIn].forEach((el) => el.addEventListener('keydown', (e) => { if (e.key === 'Enter') ok.click(); }));
    m.querySelector('#np-browse').addEventListener('click', () => {
      const typed = nameIn.value;
      const follow = typed.trim() === suggested.trim();
      picker('Select Source Video', (picked) => newProjectDialog(picked, follow ? null : typed));
    });
    ok.addEventListener('click', () => {
      if (!check()) return;
      const p = project(nameIn.value.trim(), videoIn.value.trim(), '1:12:30', '9.84 GB', 0, 0, 0);
      state.projects.unshift(p);
      close();
      makeCurrent(p);
    });
    check();
    nameIn.select();
  }

  // ---------- Common events ----------
  // Elements with data-act="open|rename|delete|import|prev|next" and data-id run the actions.
  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-act]');
    if (!el || el.disabled) return;
    e.stopPropagation();
    const id = el.dataset.id;
    switch (el.dataset.act) {
      case 'open': openProject(id); break;
      case 'rename': renameProject(id); break;
      case 'delete': deleteProject(id); break;
      case 'import': importMatch(); break;
      case 'prev': goPage(state.page - 1); break;
      case 'next': goPage(state.page + 1); break;
    }
  });
  // A double-click on a row opens the project, as in the app.
  document.addEventListener('dblclick', (e) => {
    if (e.target.closest('button')) return;
    const row = e.target.closest('[data-row]');
    if (row) openProject(row.dataset.row);
  });

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'final.html', id: 'final', label: 'Final' },
    { href: 'option-a.html', id: 'a', label: 'A · Focused list (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Project tiles' },
    { href: 'option-c.html', id: 'c', label: 'C · Start panel + list' },
  ];
  const SCENARIOS = [['open', 'Project open'], ['none', 'No project open'], ['empty', 'First start'], ['loading', 'Figures loading']];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML =
        '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span><span class="mock-label">Mockup state</span>' +
        '<div class="mock-states">' + SCENARIOS.map(([v, l]) => `<button data-sc="${v}">${l}</button>`).join('') + '</div>';
      bar.addEventListener('click', (e) => {
        const b = e.target.closest('[data-sc]');
        if (b) { setScenario(b.dataset.sc); b.blur(); }
      });
      on(() => bar.querySelectorAll('[data-sc]').forEach((b) => b.classList.toggle('is-selected', b.dataset.sc === state.scenario)));
    }
    const title = document.getElementById('titlebar');
    const nav = document.getElementById('nav');
    on(() => {
      const cur = current();
      if (title) {
        title.className = 'titlebar';
        title.innerHTML = '<span class="logo"></span>Tennis Record — Projects' + (cur ? ' — ' + esc(cur.name) : '') +
          '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
      }
      if (nav) {
        const item = (icon, label, active) =>
          `<a class="nav-item ${active ? 'active' : ''}"><span class="material-symbols-outlined">${icon}</span>${label}</a>`;
        nav.className = 'nav';
        // Without an open project, the app hides the Match and Video groups and Export.
        nav.innerHTML =
          '<div class="nav-group">' + item('folder_open', 'Projects', true) + '</div>' +
          (cur
            ? '<div class="nav-group"><div class="nav-caption">MATCH</div>' +
              item('sports_tennis', 'Points') + item('scoreboard', 'Scoring') + item('bar_chart', 'Stats') + '</div>' +
              '<div class="nav-group"><div class="nav-caption">VIDEO</div>' +
              item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
              '<div class="nav-group">' + item('movie', 'Export') + '</div>'
            : '') +
          '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
      }
    });
  }

  // A hash such as #scenario=none&page=2 sets the first state. It helps to take screenshots.
  function start() {
    const params = new URLSearchParams(location.hash.slice(1));
    setScenario(params.get('scenario') || 'open');
    if (params.get('page')) goPage(Number(params.get('page')));
    // #dialog=new|rename|delete|error opens a dialog at the start.
    const dialog = params.get('dialog');
    const other = state.projects.find((p) => !isCurrent(p) && !p.missing && !p.noVideo);
    if (dialog === 'new') newProjectDialog(DISK[0].dir + DISK[0].file);
    if (dialog === 'rename' && other) renameProject(other.id);
    if (dialog === 'delete' && other) deleteProject(other.id);
    if (dialog === 'error') { const m = state.projects.find((p) => p.missing); if (m) openProject(m.id); }
  }

  window.Mock = {
    state, on, emit, start, shell, TIPS,
    visible, totalPages, current, isCurrent, byId,
    secondary, splitPath, pathHtml, fig, scoredBar, esc,
  };
})();
