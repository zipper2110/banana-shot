/*
 * Shared script for the Stats tab redesign mockups.
 * It simulates a scored match, calculates the statistics rows as org.litvin.stats.StatRows does,
 * draws the statistics card as org.litvin.stats.StatsCard does, and draws the app shell.
 * Each option page registers its own render function with Mock.onRender().
 */
(function () {
  const PLAYERS = [
    { name: 'Alex', color: '#4DA3FF' },
    { name: 'Sam', color: '#FF6B6B' },
  ];
  const CREDIT = 'BananaShot app';
  const ROWS_PER_PAGE = 7;

  // ---------- The statistics, in display order (MatchStat) ----------
  const GROUPS = ['Overview', 'Serve', 'Pressure', 'Point length', 'Momentum', 'Time'];
  const STATS = [
    ['points_won', 'Overview', 'Points won', true],
    ['games_won', 'Overview', 'Games won'],
    ['sets_won', 'Overview', 'Sets won'],
    ['tiebreaks_won', 'Overview', 'Tiebreaks won'],
    ['service_points_won', 'Serve', 'Points won on serve', true],
    ['return_points_won', 'Serve', 'Points won on return', true],
    ['service_games_won', 'Serve', 'Service games won'],
    ['return_games_won', 'Serve', 'Return games won'],
    ['break_points_won', 'Pressure', 'Break points won', true],
    ['break_points_saved', 'Pressure', 'Break points saved'],
    ['set_points_won', 'Pressure', 'Set points won'],
    ['match_points_won', 'Pressure', 'Match points won'],
    ['deuce_points_won', 'Pressure', 'Deuce points won'],
    ['average_point_won', 'Point length', 'Average point won'],
    ['short_points_won', 'Point length', 'Short points won'],
    ['long_points_won', 'Point length', 'Long points won'],
    ['longest_point_run', 'Momentum', 'Most points in a row', true],
    ['longest_game_run', 'Momentum', 'Most games in a row'],
    ['largest_point_lead', 'Momentum', 'Largest point lead'],
    ['duration', 'Time', 'Match duration', true],
    ['playing_time', 'Time', 'Playing time'],
    ['average_point', 'Time', 'Average point'],
    ['longest_point', 'Time', 'Longest point'],
    ['average_gap', 'Time', 'Average time between points'],
  ].map(([key, group, label, def]) => ({ key, group, label, def: !!def }));

  const NEEDS_SERVER = 'Mark the server of one point in the Scoring tab.';
  const NO_POINTS_OF_LENGTH = 'No scored point has this length.';
  const NO_VALUE = 'There are not sufficient points.';

  // ---------- Match simulation ----------
  function rng(seed) {
    return function () {
      seed |= 0; seed = seed + 0x6D2B79F5 | 0;
      let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
      t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
  }
  function shuffle(r, list) {
    for (let i = list.length - 1; i > 0; i--) {
      const j = Math.floor(r() * (i + 1));
      [list[i], list[j]] = [list[j], list[i]];
    }
    return list;
  }

  /** The game winners of one set that end at the target score. A 7-6 set ends with a tiebreak game. */
  function gameSequence(r, target, firstServer) {
    const tb = Math.max(...target) === 7 && Math.min(...target) === 6;
    for (let attempt = 0; attempt < 50000; attempt++) {
      const seq = [];
      const g = [0, 0];
      let srv = firstServer;
      for (;;) {
        if (tb && g[0] === 6 && g[1] === 6) {
          const w = target[0] > target[1] ? 1 : 2;
          seq.push({ winner: w, server: srv, tb: true });
          g[w - 1]++;
          srv = 3 - srv;
          break;
        }
        const w = r() < 0.7 ? srv : 3 - srv;
        g[w - 1]++;
        seq.push({ winner: w, server: srv, tb: false });
        srv = 3 - srv;
        const hi = Math.max(...g), lo = Math.min(...g);
        if ((hi >= 6 && hi - lo >= 2) || hi === 7) break;
      }
      if (g[0] === target[0] && g[1] === target[1]) return { seq, next: srv };
    }
    throw new Error('No game sequence for ' + target);
  }

  function gamePoints(r, w) {
    const l = 3 - w;
    const x = r();
    if (x < 0.6) {
      const lp = x < 0.18 ? 0 : x < 0.4 ? 1 : 2;
      return [...shuffle(r, [w, w, w, ...Array(lp).fill(l)]), w];
    }
    const seq = shuffle(r, [w, w, w, l, l, l]);
    const extra = Math.floor(r() * 3);
    for (let i = 0; i < extra; i++) seq.push(...(r() < 0.5 ? [w, l] : [l, w]));
    return [...seq, w, w];
  }

  function simulate(seed, targets, tbScore) {
    const r = rng(seed);
    const points = [];
    const games = [];
    const sets = [];
    const setsWon = [0, 0];
    let server = 1;
    let t = 6000;
    let n = 0;
    targets.forEach((target, si) => {
      const set = si + 1;
      const { seq, next } = gameSequence(r, target, server);
      const g = [0, 0];
      seq.forEach((game, gi) => {
        const winners = game.tb
          ? [...shuffle(r, [...Array(tbScore[0] - 1).fill(game.winner), ...Array(tbScore[1]).fill(3 - game.winner)]), game.winner]
          : gamePoints(r, game.winner);
        const gp = [0, 0];
        const gameRecord = { set, winner: game.winner, server: game.server, tb: game.tb, first: n };
        winners.forEach((pw, pi) => {
          const srv = game.tb ? (Math.floor((pi + 1) / 2) % 2 === 0 ? game.server : 3 - game.server) : game.server;
          const ret = 3 - srv;
          const gameFor = (x) => {
            const a = gp[x - 1], b = gp[2 - x];
            return game.tb ? a >= 6 && a - b >= 1 : a >= 3 && a - b >= 1;
          };
          const setFor = (x) => gameFor(x) && (game.tb || (g[x - 1] + 1 >= 6 && g[x - 1] + 1 - g[2 - x] >= 2));
          const deuce = !game.tb && gp[0] >= 3 && gp[0] === gp[1];
          const dur = Math.round((2.6 + Math.pow(r(), 1.7) * 21 + (deuce ? 2.5 : 0)) * 1000);
          n++;
          points.push({
            id: 'pt-' + n, n, set, game: games.length, server: srv, winner: pw, tb: game.tb,
            startMs: t, endMs: t + dur, dur,
            bpFor: !game.tb && gameFor(ret) ? ret : 0,
            spFor: [setFor(1), setFor(2)],
            mpFor: [setFor(1) && setsWon[0] === 1, setFor(2) && setsWon[1] === 1],
            deuce,
          });
          gp[pw - 1]++;
          t += dur + 13000 + Math.round(r() * 15000);
        });
        g[game.winner - 1]++;
        gameRecord.last = n - 1;
        games.push(gameRecord);
        t += 7000;
        if ((g[0] + g[1]) % 2 === 1) t += 65000 + Math.round(r() * 30000);
      });
      const winner = g[0] > g[1] ? 1 : 2;
      setsWon[winner - 1]++;
      const tbGame = seq[seq.length - 1].tb;
      sets.push({ set, games: g.slice(), winner, tb: tbGame, score: `${g[0]}-${g[1]}` });
      server = tbGame ? 3 - seq[seq.length - 1].server : next;
      t += 100000;
    });
    return { points, games, sets };
  }

  const MATCHES = {
    three: simulate(20260913, [[6, 4], [3, 6], [7, 6]], [7, 5]),
    one: simulate(20260828, [[6, 3]], [7, 5]),
  };

  // ---------- State ----------
  const state = {
    mode: 'normal',       // normal | gray | coverage | oneset | noscore | noproject | error
    scope: 0,             // 0 is the full match, 1 and more are the sets
    inVideo: new Set(STATS.filter((s) => s.def).map((s) => s.key)),
    momentum: true,
    transparency: 18,
    shortMax: 7,
    longMin: 15,
    page: 0,
  };

  const match = () => (state.mode === 'oneset' ? MATCHES.one : MATCHES.three);

  // ---------- Formatting ----------
  const pct = (won, total) => (total > 0 ? Math.round((won * 100) / total) + '%' : null);
  const share = (won, total) => ({ text: pct(won, total) || '—', detail: `${won}/${total}` });
  const fraction = (won, total) => ({ text: `${won}/${total}`, detail: pct(won, total) });
  const count = (v) => ({ text: String(v) });
  const seconds = (ms) => (ms / 1000).toFixed(1) + ' s';
  function clock(ms) {
    const s = Math.floor(Math.max(0, ms) / 1000);
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    const two = (v) => String(v).padStart(2, '0');
    return h > 0 ? `${h}:${two(m)}:${two(sec)}` : `${m}:${two(sec)}`;
  }

  function limitLabel(stat) {
    if (stat.key === 'short_points_won') return `${stat.label} (≤ ${state.shortMax} s)`;
    if (stat.key === 'long_points_won') return `${stat.label} (≥ ${state.longMin} s)`;
    return stat.label;
  }

  function description(stat) {
    if (stat.key === 'short_points_won') return `The points that last ${state.shortMax} s or less, from the start mark to the end mark.`;
    if (stat.key === 'long_points_won') return `The points that last ${state.longMin} s or more, from the start mark to the end mark.`;
    if (stat.key === 'return_games_won') return 'The service games of the opponent that the player won (breaks).';
    if (stat.key === 'duration') return 'The time from the start of the first point with a winner to the end of the last point with a winner. It is not the length of the video.';
    return null;
  }

  // ---------- Statistics of one scope ----------
  function rows(scope = state.scope) {
    const m = match();
    const pts = scope === 0 ? m.points : m.points.filter((p) => p.set === scope);
    const gms = scope === 0 ? m.games : m.games.filter((g) => g.set === scope);
    const sts = scope === 0 ? m.sets : [m.sets[scope - 1]];
    const noServer = state.mode === 'gray';
    const both = (f) => [f(1), f(2)];
    const won = (list, x) => list.filter((p) => p.winner === x).length;

    function run(list, key) {
      let best = [0, 0], start = [null, null], cur = 0, curStart = null, last = 0;
      list.forEach((item) => {
        const w = item.winner;
        if (w === last) cur++; else { cur = 1; curStart = item; last = w; }
        if (cur > best[w - 1]) { best[w - 1] = cur; start[w - 1] = curStart; }
      });
      return { best, start: start.map((s) => (s && key ? s[key] : null)) };
    }
    function lead(list) {
      let d = 0; const best = [0, 0], at = [null, null];
      list.forEach((p) => {
        d += p.winner === 1 ? 1 : -1;
        if (d > best[0]) { best[0] = d; at[0] = p.id; }
        if (-d > best[1]) { best[1] = -d; at[1] = p.id; }
      });
      return { best, at };
    }

    const out = [];
    const perPlayer = (stat, values, bar, pointIds) => out.push({ stat, values, bar, pointIds });
    const shared = (stat, value, pointId) =>
      value ? out.push({ stat, shared: value, sharedPointId: pointId }) : out.push({ stat, reason: NO_VALUE });
    const unavailable = (stat, reason) => out.push({ stat, reason });
    const S = Object.fromEntries(STATS.map((s) => [s.key, s]));

    const pw = both((x) => won(pts, x));
    perPlayer(S.points_won, pw.map((v) => ({ text: String(v), detail: pct(v, pts.length) })), pw);
    const gw = both((x) => gms.filter((g) => g.winner === x).length);
    perPlayer(S.games_won, gw.map((v) => ({ text: String(v), detail: pct(v, gms.length) })), gw);
    const sw = both((x) => sts.filter((s) => s.winner === x).length);
    perPlayer(S.sets_won, sw.map(count), sw);
    const tw = both((x) => sts.filter((s) => s.tb && s.winner === x).length);
    perPlayer(S.tiebreaks_won, tw.map(count), tw);

    const shares = (stat, pairs) => perPlayer(stat, pairs.map(([a, b]) => share(a, b)), pairs.map(([a, b]) => (b ? Math.round((a * 100) / b) : 0)));
    const fractions = (stat, pairs) => perPlayer(stat, pairs.map(([a, b]) => fraction(a, b)), pairs.map(([a]) => a));
    const regular = gms.filter((g) => !g.tb);
    if (noServer) {
      ['service_points_won', 'return_points_won', 'service_games_won', 'return_games_won'].forEach((k) => unavailable(S[k], NEEDS_SERVER));
    } else {
      shares(S.service_points_won, both((x) => { const l = pts.filter((p) => p.server === x); return [won(l, x), l.length]; }));
      shares(S.return_points_won, both((x) => { const l = pts.filter((p) => p.server !== x); return [won(l, x), l.length]; }));
      shares(S.service_games_won, both((x) => { const l = regular.filter((g) => g.server === x); return [l.filter((g) => g.winner === x).length, l.length]; }));
      shares(S.return_games_won, both((x) => { const l = regular.filter((g) => g.server !== x); return [l.filter((g) => g.winner === x).length, l.length]; }));
    }
    if (noServer) {
      unavailable(S.break_points_won, NEEDS_SERVER);
      unavailable(S.break_points_saved, NEEDS_SERVER);
    } else {
      fractions(S.break_points_won, both((x) => { const l = pts.filter((p) => p.bpFor === x); return [won(l, x), l.length]; }));
      fractions(S.break_points_saved, both((x) => { const l = pts.filter((p) => p.bpFor === 3 - x); return [won(l, x), l.length]; }));
    }
    fractions(S.set_points_won, both((x) => { const l = pts.filter((p) => p.spFor[x - 1]); return [won(l, x), l.length]; }));
    fractions(S.match_points_won, both((x) => { const l = pts.filter((p) => p.mpFor[x - 1]); return [won(l, x), l.length]; }));
    const dw = both((x) => won(pts.filter((p) => p.deuce), x));
    perPlayer(S.deuce_points_won, dw.map(count), dw);

    perPlayer(S.average_point_won, both((x) => {
      const l = pts.filter((p) => p.winner === x);
      return { text: l.length ? seconds(l.reduce((a, p) => a + p.dur, 0) / l.length) : '—' };
    }));
    const lengthShares = (stat, list) => (list.length ? shares(stat, both((x) => [won(list, x), list.length])) : unavailable(stat, NO_POINTS_OF_LENGTH));
    lengthShares(S.short_points_won, pts.filter((p) => p.dur <= state.shortMax * 1000));
    lengthShares(S.long_points_won, pts.filter((p) => p.dur >= state.longMin * 1000));

    const pr = run(pts, 'id');
    perPlayer(S.longest_point_run, pr.best.map(count), pr.best, pr.start);
    const gr = run(gms, null);
    perPlayer(S.longest_game_run, gr.best.map(count), gr.best);
    const ld = lead(pts);
    perPlayer(S.largest_point_lead, ld.best.map(count), ld.best, ld.at);

    // Match duration: from the first point with a winner to the last point with a winner.
    const scored = pts.filter((p) => p.winner);
    const first = scored[0], last = scored[scored.length - 1];
    const playing = pts.reduce((a, p) => a + p.dur, 0);
    const longest = pts.reduce((a, p) => (p.dur > a.dur ? p : a), pts[0]);
    let gaps = 0;
    for (let i = 1; i < pts.length; i++) gaps += pts[i].startMs - pts[i - 1].endMs;
    shared(S.duration, { text: clock(last.endMs - first.startMs) });
    shared(S.playing_time, { text: clock(playing) });
    shared(S.average_point, { text: seconds(playing / pts.length) });
    shared(S.longest_point, { text: seconds(longest.dur) }, longest.id);
    shared(S.average_gap, pts.length > 1 ? { text: seconds(gaps / (pts.length - 1)) } : null);

    return out.map((row) => ({
      ...row,
      key: row.stat.key,
      group: row.stat.group,
      label: limitLabel(row.stat),
      description: description(row.stat),
      available: !row.reason,
      inVideo: state.inVideo.has(row.stat.key),
    }));
  }

  /** The rows grouped in display order, for the table. */
  function groups(scope = state.scope) {
    const all = rows(scope);
    return GROUPS.map((title) => ({ title, rows: all.filter((r) => r.group === title) })).filter((g) => g.rows.length);
  }

  // ---------- Header data ----------
  function setScores() { return match().sets.map((s) => s.score); }
  function scopes() {
    const m = match();
    if (m.sets.length < 2) return [];
    return [{ index: 0, title: 'Match', score: setScores().join('  ') }].concat(m.sets.map((s, i) => ({ index: i + 1, title: `Set ${i + 1}`, score: s.score })));
  }
  function notesHtml() {
    const lines = coverage();
    return lines.length ? `<div class="note">${icon('info')}<div>${lines.map((t) => `<p>${esc(t)}</p>`).join('')}</div></div>` : '';
  }
  function coverage() {
    if (state.mode !== 'coverage') return [];
    const lines = [];
    const m = match();
    const scored = state.scope === 0 ? m.points.length : m.points.filter((p) => p.set === state.scope).length;
    if (state.scope === 0 || state.scope === 2) lines.push(`Based on ${scored} of ${scored + 6} points. The other points have no winner.`);
    lines.push('The statistics do not use the 2 points after the end of the match.');
    return lines;
  }

  // ---------- Momentum (org.litvin.stats.Momentum) ----------
  function momentum(scope = state.scope) {
    const m = match();
    const pts = scope === 0 ? m.points : m.points.filter((p) => p.set === scope);
    let d = 0, lastSet = 0;
    const points = [], setStarts = [];
    pts.forEach((p) => {
      if (lastSet && p.set !== lastSet) setStarts.push(points.length);
      lastSet = p.set;
      d += p.winner === 1 ? 1 : -1;
      points.push({ id: p.id, number: p.n, set: p.set, winner: p.winner, difference: d });
    });
    const maxLead = [Math.max(0, ...points.map((p) => p.difference)), Math.max(0, ...points.map((p) => -p.difference))];
    return { points, setStarts, maxLead };
  }

  // ---------- Statistics card (org.litvin.stats.StatsCard) ----------
  function splitRows(list, pageCount) {
    if (pageCount <= 0 || !list.length) return [];
    const starts = [];
    list.forEach((r, i) => { if (i > 0 && r.group !== list[i - 1].group) starts.push(i); });
    const sizes = (breaks) => [0, ...breaks].map((s, i) => (breaks.concat(list.length))[i] - s);
    let best = null;
    (function search(from, breaks) {
      if (breaks.length === pageCount - 1) {
        const max = Math.max(...sizes(breaks));
        if (max <= ROWS_PER_PAGE && (!best || max < Math.max(...sizes(best)))) best = breaks;
        return;
      }
      for (let i = from; i < starts.length; i++) search(i + 1, breaks.concat(starts[i]));
    })(0, []);
    if (!best) {
      const size = Math.ceil(list.length / pageCount);
      const chunks = [];
      for (let i = 0; i < list.length; i += size) chunks.push(list.slice(i, i + size));
      return chunks;
    }
    return [0, ...best].map((s, i) => list.slice(s, best.concat(list.length)[i]));
  }

  function card(scope = state.scope) {
    const cardRows = rows(scope).filter((r) => r.available && r.inVideo);
    const chunks = splitRows(cardRows, Math.ceil(cardRows.length / ROWS_PER_PAGE));
    const mo = momentum(scope);
    const chart = state.momentum && mo.points.length >= 2 ? mo : null;
    const pages = chunks.map((r) => ({ kind: 'rows', rows: r }));
    if (chart) pages.push({ kind: 'chart', momentum: chart });
    const mostRows = Math.max(0, ...chunks.map((c) => c.length));
    return {
      title: scope === 0 ? 'Match statistics' : `Set ${scope} statistics`,
      score: scope === 0 ? setScores().join('   ') : setScores()[scope - 1],
      pages,
      rowCount: cardRows.length,
      slots: chart ? Math.max(mostRows, 4) : mostRows,
    };
  }

  function pageCount() { return card().pages.length; }
  function clampPage() {
    const count = pageCount();
    state.page = Math.max(0, Math.min(state.page, count - 1));
  }

  const esc = (text) => String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /** One page of the card over the video frame, as an SVG in 1080p units. */
  function cardSvg(pageIndex = state.page, options = {}) {
    const W = 1920, H = 1080;
    const c = card();
    const page = c.pages[pageIndex];
    let s = `<svg viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg" font-family="Segoe UI, Inter, sans-serif">`;
    s += `<image href="frame.jpg" x="0" y="0" width="${W}" height="${H}" preserveAspectRatio="xMidYMid slice"/>`;
    if (!page) {
      s += `<rect width="${W}" height="${H}" fill="#000" opacity=".59"/>`;
      if (!options.thumb) s += `<text x="${W / 2}" y="${H / 2}" font-size="40" fill="#adaaaa" text-anchor="middle" dominant-baseline="central">Select at least one row with a value.</text>`;
      return s + '</svg>';
    }
    const colors = PLAYERS.map((p) => p.color);
    const opacity = 1 - state.transparency / 100;
    const panelW = 1240, header = 170, rowH = 104, footer = 70, pad = 64;
    const panelH = header + rowH * c.slots + footer;
    const left = (W - panelW) / 2, top = (H - panelH) / 2, right = left + panelW, center = W / 2;
    const inner = left + pad, innerRight = right - pad;
    const text = (x, y, value, size, fill, anchor, bold = true, extra = '') =>
      `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" text-anchor="${anchor}" dominant-baseline="central" font-weight="${bold ? 700 : 400}" ${extra}>${esc(value)}</text>`;
    s += `<rect width="${W}" height="${H}" fill="#000" opacity=".45"/>`;
    s += `<rect x="${left}" y="${top}" width="${panelW}" height="${panelH}" rx="28" fill="#0B0F14" opacity="${opacity}"/>`;
    s += text(inner, top + 78, PLAYERS[0].name, 58, colors[0], 'start');
    s += text(innerRight, top + 78, PLAYERS[1].name, 58, colors[1], 'end');
    s += text(center, top + 50, c.title.toUpperCase(), 26, '#A8A8A8', 'middle', false, 'letter-spacing="3"');
    s += text(center, top + 100, c.score, 44, '#fff', 'middle', true, 'xml:space="preserve"');
    s += `<rect x="${inner}" y="${top + header - 22}" width="${innerRight - inner}" height="2" fill="#fff" opacity=".16"/>`;
    const areaTop = top + header, areaBottom = areaTop + rowH * c.slots;
    if (page.kind === 'rows') {
      page.rows.forEach((row, i) => {
        const y = areaTop + i * rowH;
        s += text(center, y + 30, row.label, 30, '#D6D6D6', 'middle', false);
        if (row.shared) {
          s += text(center, y + 72, row.shared.text, 46, '#fff', 'middle');
        } else {
          s += text(inner, y + 52, row.values[0].text, 54, '#fff', 'start');
          s += text(innerRight, y + 52, row.values[1].text, 54, '#fff', 'end');
          if (row.bar) {
            const x0 = inner + 190, x1 = innerRight - 190, total = row.bar[0] + row.bar[1];
            const part = total > 0 ? row.bar[0] / total : 0.5, op = total > 0 ? 1 : 0.3;
            const width = x1 - x0 - 6, w1 = width * part, w2 = width - w1;
            if (w1 > 0.5) s += `<rect x="${x0}" y="${y + 62}" width="${w1}" height="10" rx="5" fill="${colors[0]}" opacity="${op}"/>`;
            if (w2 > 0.5) s += `<rect x="${x1 - w2}" y="${y + 62}" width="${w2}" height="10" rx="5" fill="${colors[1]}" opacity="${op}"/>`;
          }
        }
      });
    } else {
      s += cardChart(page.momentum, inner, innerRight, areaTop, areaBottom, colors, text);
    }
    const footerY = top + panelH - footer / 2;
    if (c.pages.length > 1) s += text(center, footerY, `${pageIndex + 1} / ${c.pages.length}`, 26, '#A8A8A8', 'middle', false);
    s += text(innerRight, footerY, CREDIT, 24, '#A8A8A8', 'end', false, 'opacity=".8"');
    return s + '</svg>';
  }

  function cardChart(mo, left, right, top, bottom, colors, text) {
    let s = '';
    const lead = mo.maxLead, pts = mo.points;
    const plotTop = top + 58, plotBottom = bottom - 58;
    const above = Math.max(lead[0], 1), below = Math.max(lead[1], 1);
    const unit = (plotBottom - plotTop) / (above + below);
    const zeroY = plotTop + above * unit;
    const x = (pos) => left + (pos * (right - left)) / pts.length;
    const y = (d) => zeroY - d * unit;
    s += text(right, bottom - 26, 'Point difference', 26, '#A8A8A8', 'end', false);
    s += text(left, top + 26, `${PLAYERS[0].name} +${lead[0]}`, 30, colors[0], 'start');
    s += text(left, bottom - 26, `${PLAYERS[1].name} +${lead[1]}`, 30, colors[1], 'start');
    mo.setStarts.forEach((start) => {
      const lx = x(start);
      s += `<line x1="${lx}" y1="${plotTop}" x2="${lx}" y2="${plotBottom}" stroke="#A8A8A8" stroke-width="2" stroke-dasharray="10 8" opacity=".7"/>`;
      s += text(lx, plotTop - 18, `Set ${pts[start].set}`, 26, '#A8A8A8', 'middle', false);
    });
    const line = [[0, 0]];
    pts.forEach((p, i) => {
      const [x0, d0] = line[line.length - 1];
      const d1 = p.difference;
      if (d0 * d1 < 0) line.push([x0 + d0 / (d0 - d1), 0]);
      line.push([i + 1, d1]);
    });
    const poly = (select) => line.map(([pos, d]) => `${x(pos)},${y(select(d))}`).join(' ') + ` ${x(pts.length)},${zeroY}`;
    if (lead[0] > 0) s += `<polygon points="${poly((d) => Math.max(d, 0))}" fill="${colors[0]}" opacity=".55"/>`;
    if (lead[1] > 0) s += `<polygon points="${poly((d) => Math.min(d, 0))}" fill="${colors[1]}" opacity=".55"/>`;
    s += `<rect x="${left}" y="${zeroY - 1}" width="${right - left}" height="2" fill="#fff" opacity=".3"/>`;
    s += `<polyline points="${line.map(([pos, d]) => `${x(pos)},${y(d)}`).join(' ')}" fill="none" stroke="#fff" stroke-width="4" stroke-linejoin="round" stroke-linecap="round"/>`;
    return s;
  }

  // ---------- Momentum chart of the tab (org.litvin.ui.tabs.stats.MomentumChart) ----------
  /** Draws the chart in [el] and keeps it at the width of [el]. */
  function mountChart(el, height = 170) {
    el.classList.add('chart');
    el.style.height = height + 'px';
    let hover = -1;
    const tip = document.createElement('div');
    tip.className = 'chart-tip';
    tip.hidden = true;

    function geometry() {
      const w = el.clientWidth || 560;
      const labelH = 19, pad = 4;
      return { w, h: height, x: pad, y: labelH + pad, pw: w - 2 * pad, ph: height - 2 * labelH - 2 * pad };
    }
    function draw() {
      const mo = momentum();
      const g = geometry();
      const pts = mo.points;
      const above = Math.max(mo.maxLead[0], 1), below = Math.max(mo.maxLead[1], 1);
      const middle = g.y + (above * g.ph) / (above + below);
      const x = (pos) => g.x + (pos * g.pw) / pts.length;
      const y = (d) => middle - (d * g.ph) / (above + below);
      let s = `<svg viewBox="0 0 ${g.w} ${g.h}" height="${g.h}" xmlns="http://www.w3.org/2000/svg" font-family="Segoe UI, sans-serif" font-size="12">`;
      s += `<defs><clipPath id="c-above"><rect x="${g.x}" y="${g.y - 1}" width="${g.pw}" height="${middle - g.y + 1}"/></clipPath>` +
        `<clipPath id="c-below"><rect x="${g.x}" y="${middle}" width="${g.pw}" height="${g.y + g.ph - middle + 1}"/></clipPath></defs>`;
      s += `<text x="${g.x}" y="${g.y - 6}" fill="${PLAYERS[0].color}">${PLAYERS[0].name} +${mo.maxLead[0]}</text>`;
      s += `<text x="${g.x}" y="${g.y + g.ph + 16}" fill="${PLAYERS[1].color}">${PLAYERS[1].name} +${mo.maxLead[1]}</text>`;
      s += `<line x1="${g.x}" y1="${middle}" x2="${g.x + g.pw}" y2="${middle}" stroke="#3a3a3a"/>`;
      mo.setStarts.forEach((start) => {
        const lx = x(start);
        s += `<line x1="${lx}" y1="${g.y}" x2="${lx}" y2="${g.y + g.ph}" stroke="#6a6a6a" stroke-dasharray="4 4"/>`;
        s += `<text x="${lx}" y="${g.y - 6}" fill="#adaaaa" text-anchor="middle">Set ${pts[start].set}</text>`;
      });
      const line = `M${x(0)},${middle} ` + pts.map((p, i) => `L${x(i + 1)},${y(p.difference)}`).join(' ');
      const area = `${line} L${x(pts.length)},${middle} Z`;
      s += `<path d="${area}" fill="${PLAYERS[0].color}" fill-opacity=".35" clip-path="url(#c-above)"/>`;
      s += `<path d="${area}" fill="${PLAYERS[1].color}" fill-opacity=".35" clip-path="url(#c-below)"/>`;
      s += `<path d="${line}" fill="none" stroke="#d8d8d8" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`;
      if (hover >= 0 && hover < pts.length) {
        const hx = x(hover + 1), hy = y(pts[hover].difference);
        s += `<line x1="${hx}" y1="${g.y}" x2="${hx}" y2="${g.y + g.ph}" stroke="#adaaaa"/>`;
        s += `<circle cx="${hx}" cy="${hy}" r="5" fill="${PLAYERS[pts[hover].winner - 1].color}"/>`;
      }
      el.innerHTML = s + '</svg>';
      el.appendChild(tip);
    }
    function pointAt(mouseX) {
      const pts = momentum().points;
      if (!pts.length) return -1;
      const g = geometry();
      if (mouseX < 0 || mouseX > g.w) return -1;
      const pos = Math.round(((mouseX - g.x) * pts.length) / g.pw);
      return Math.max(0, Math.min(pts.length - 1, pos - 1));
    }
    el.addEventListener('mousemove', (e) => {
      const rect = el.getBoundingClientRect();
      const index = pointAt(e.clientX - rect.left);
      if (index !== hover) { hover = index; draw(); }
      const p = momentum().points[index];
      if (!p) { tip.hidden = true; return; }
      const winner = PLAYERS[p.winner - 1].name;
      const pts = (v) => (Math.abs(v) === 1 ? '1 point' : `${v} points`);
      const leadText = p.difference > 0 ? `${PLAYERS[0].name} leads by ${pts(p.difference)}.`
        : p.difference < 0 ? `${PLAYERS[1].name} leads by ${pts(-p.difference)}.` : 'The points won are equal.';
      tip.innerHTML = `Point ${p.number}, set ${p.set}: ${winner} won the point.<br>${leadText}<br><span class="faint">Click to open the point in the Scoring tab.</span>`;
      tip.hidden = false;
      const x = e.clientX - rect.left;
      tip.style.left = Math.min(x + 14, rect.width - 290) + 'px';
      tip.style.top = (e.clientY - rect.top + 16) + 'px';
    });
    el.addEventListener('mouseleave', () => { hover = -1; tip.hidden = true; draw(); });
    el.addEventListener('click', (e) => {
      const rect = el.getBoundingClientRect();
      const p = momentum().points[pointAt(e.clientX - rect.left)];
      if (p) toast(`Opens point ${p.number} in the Scoring tab.`);
    });
    new ResizeObserver(draw).observe(el);
    draw();
    return { draw };
  }

  // ---------- Small render helpers for the options ----------
  const icon = (name, cls = '') => `<span class="material-symbols-outlined ${cls}">${name}</span>`;

  /** The main text and the detail of a value. A value from one point opens the point. */
  function valueHtml(value, pointId, cls = '') {
    const detail = value.detail ? ` <span class="v-detail">${esc(value.detail)}</span>` : '';
    if (pointId) {
      const p = match().points.find((q) => q.id === pointId);
      return `<span class="point-link ${cls}" data-open-point="${p.n}" data-tip="Open this point in the Scoring tab.">` +
        `<b class="v-main">${esc(value.text)}</b>${icon('play_circle')}</span>${detail}`;
    }
    return `<b class="v-main ${cls}">${esc(value.text)}</b>${detail}`;
  }

  /** The value of the player that leads the row: 1, 2, or 0 for equal values or rows without a bar. */
  function leader(row) {
    if (!row.bar || row.bar[0] === row.bar[1]) return 0;
    return row.bar[0] > row.bar[1] ? 1 : 2;
  }

  function rowTip(row) {
    if (row.available) return row.description || '';
    return [row.reason, row.description].filter(Boolean).join(' ');
  }
  function checkTip(row) {
    return row.available ? 'Show this row in the exported video.'
      : 'Show this row in the exported video. The video leaves out the row while it has no value.';
  }

  function limitsHtml() {
    const stepper = (which, value) =>
      `<span class="stepper"><button data-limit="${which}:-1" aria-label="Decrease">${icon('remove')}</button>` +
      `<input type="number" min="1" max="120" value="${value}" data-limit-input="${which}"><button data-limit="${which}:1" aria-label="Increase">${icon('add')}</button></span>`;
    return { short: stepper('short', state.shortMax), long: stepper('long', state.longMin) };
  }

  const HELP = [
    'The exported video shows this card after the last point.',
    `The card shows the rows that you select and that have a value, ${ROWS_PER_PAGE} rows on each page. A group of rows stays on one page when possible.`,
    'When you select In video for the momentum chart, the last page shows the chart.',
  ];

  function emptyHtml() {
    const m = state.mode;
    if (m === 'noproject') return empty('folder_open', 'No project', 'Open a project in the Projects tab.');
    if (m === 'error') return empty('error', 'Cannot read the statistics', '<code>score.json: Unexpected end of JSON input at line 214</code>', false, true);
    if (m === 'noscore') return empty('scoreboard', 'No scored points', 'Score the points in the Scoring tab. The statistics then show here.', true);
    return null;
  }
  function empty(iconName, title, text, scoring = false, error = false) {
    return `<div class="empty"><div class="empty-box ${error ? 'error' : ''}"><div class="icon">${icon(iconName)}</div>` +
      `<h2>${title}</h2><p>${text}</p>` +
      (scoring ? `<button class="btn btn-lime" data-toast="Opens the Scoring tab.">${icon('scoreboard')}Open Scoring</button>` : '') +
      '</div></div>';
  }

  // ---------- Tooltip and toast ----------
  const tipEl = document.createElement('div');
  tipEl.className = 'tip';
  tipEl.hidden = true;
  document.addEventListener('DOMContentLoaded', () => document.body.appendChild(tipEl));
  document.addEventListener('mouseover', (e) => {
    const el = e.target.closest('[data-tip]');
    if (!el || !el.dataset.tip) { tipEl.hidden = true; return; }
    tipEl.textContent = el.dataset.tip;
    tipEl.hidden = false;
    const r = el.getBoundingClientRect();
    const w = Math.min(320, tipEl.offsetWidth);
    tipEl.style.left = Math.max(8, Math.min(r.left, window.innerWidth - w - 8)) + 'px';
    tipEl.style.top = (r.bottom + 6) + 'px';
  });

  let toastTimer = 0;
  function toast(text) {
    let el = document.querySelector('.toast');
    if (!el) { el = document.createElement('div'); el.className = 'toast'; document.body.appendChild(el); }
    el.innerHTML = `${icon('info')}<span><b>Mockup:</b> ${esc(text)}</span>`;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 2200);
  }

  // ---------- Events ----------
  let renderFn = () => {};
  let previewFn = () => {};
  function render() {
    clampPage();
    const scrolls = [...document.querySelectorAll('[data-keep-scroll]')].map((el) => [el.dataset.keepScroll, el.scrollTop]);
    renderFn();
    scrolls.forEach(([key, top]) => { const el = document.querySelector(`[data-keep-scroll="${key}"]`); if (el) el.scrollTop = top; });
    document.querySelectorAll('.mock-states button').forEach((b) => b.classList.toggle('is-selected', b.dataset.mode === state.mode));
    document.querySelectorAll('input[type=range]').forEach(fillRange);
  }
  function fillRange(el) { el.style.setProperty('--fill', ((el.value - el.min) * 100) / (el.max - el.min) + '%'); }

  document.addEventListener('click', (e) => {
    const t = e.target.closest('[data-mode],[data-scope],[data-page],[data-page-step],[data-limit],[data-open-point],[data-toast]');
    if (!t || t.disabled) return;
    if (t.dataset.mode) { state.mode = t.dataset.mode; state.scope = 0; state.page = 0; }
    else if (t.dataset.scope != null) { state.scope = Number(t.dataset.scope); }
    else if (t.dataset.page != null) { state.page = Number(t.dataset.page); }
    else if (t.dataset.pageStep) { state.page += Number(t.dataset.pageStep); }
    else if (t.dataset.limit) {
      const [which, step] = t.dataset.limit.split(':');
      setLimit(which, (which === 'short' ? state.shortMax : state.longMin) + Number(step));
    }
    else if (t.dataset.openPoint) { toast(`Opens point ${t.dataset.openPoint} in the Scoring tab.`); return; }
    else if (t.dataset.toast) { toast(t.dataset.toast); return; }
    render();
  });
  document.addEventListener('change', (e) => {
    const t = e.target;
    if (t.dataset.video) { if (t.checked) state.inVideo.add(t.dataset.video); else state.inVideo.delete(t.dataset.video); render(); }
    else if (t.dataset.momentum != null) { state.momentum = t.checked; render(); }
    else if (t.dataset.limitInput) { setLimit(t.dataset.limitInput, Number(t.value) || 1); render(); }
  });
  document.addEventListener('input', (e) => {
    const t = e.target;
    if (t.dataset.transparency != null) {
      state.transparency = Number(t.value);
      fillRange(t);
      document.querySelectorAll('[data-transparency-value]').forEach((el) => { el.textContent = `${state.transparency} %`; });
      previewFn();
    }
  });
  /** The limits stay in their ranges, and a long point is always longer than a short point. */
  function setLimit(which, value) {
    if (which === 'short') state.shortMax = Math.max(1, Math.min(119, value));
    else state.longMin = Math.max(1, Math.min(120, value));
    state.longMin = Math.max(state.shortMax + 1, state.longMin);
  }

  // ---------- App shell ----------
  const OPTIONS = [
    { href: 'option-a.html', id: 'a', label: 'A · Scope bar + card panel (main)' },
    { href: 'option-b.html', id: 'b', label: 'B · Rows like the card' },
    { href: 'option-c.html', id: 'c', label: 'C · Wide chart + page strip' },
    { href: 'final.html', id: 'final', label: 'Final' },
  ];
  const MODES = [
    ['normal', 'Normal'], ['gray', 'No server mark'], ['coverage', 'Not all scored'], ['oneset', 'One set'],
    ['noscore', 'No scored points'], ['noproject', 'No project'], ['error', 'Read error'],
  ];

  function shell(optionId) {
    const bar = document.getElementById('mock-bar');
    if (bar) {
      bar.className = 'mock-bar';
      bar.innerHTML = '<a href="index.html">← Overview</a>' +
        OPTIONS.map((o) => `<a href="${o.href}" class="${o.id === optionId ? 'current' : ''}">${o.label}</a>`).join('') +
        '<span class="spacer"></span><span class="mock-label">Mockup state</span><div class="mock-states">' +
        MODES.map(([v, l]) => `<button data-mode="${v}">${l}</button>`).join('') + '</div>';
    }
    const title = document.getElementById('titlebar');
    if (title) {
      title.className = 'titlebar';
      title.innerHTML = '<span class="logo"></span>BananaShot — Stats — PXL_20260913_070045619' +
        '<span class="win-buttons"><span>—</span><span>☐</span><span>✕</span></span>';
    }
    const nav = document.getElementById('nav');
    if (nav) {
      const item = (i, label, active) => `<a class="nav-item ${active ? 'active' : ''}">${icon(i)}${label}</a>`;
      nav.className = 'nav';
      nav.innerHTML =
        '<div class="nav-group">' + item('folder_open', 'Projects') + '</div>' +
        '<div class="nav-group"><div class="nav-caption">MATCH</div>' +
        item('sports_tennis', 'Points') + item('scoreboard', 'Scoring') + item('bar_chart', 'Stats', true) + '</div>' +
        '<div class="nav-group"><div class="nav-caption">VIDEO</div>' + item('palette', 'Colors') + item('crop_rotate', 'Transform') + '</div>' +
        '<div class="nav-group">' + item('movie', 'Export') + '</div>' +
        '<div class="nav-group push">' + item('help', 'Help') + item('more_horiz', 'More') + '</div>';
    }
  }

  // A hash such as #mode=gray&scope=2 sets the first state. It helps to take screenshots.
  new URLSearchParams(location.hash.slice(1)).forEach((value, key) => {
    if (key === 'mode') state.mode = value;
    if (key === 'scope' || key === 'page') state[key] = Number(value);
  });

  window.Mock = {
    PLAYERS, GROUPS, HELP, ROWS_PER_PAGE,
    state, rows, groups, setScores, scopes, coverage, notesHtml, momentum, card, cardSvg, mountChart,
    valueHtml, leader, rowTip, checkTip, limitsHtml, emptyHtml, icon, esc, toast, shell, render,
    onRender(fn) { renderFn = fn; },
    onPreview(fn) { previewFn = fn; },
  };
})();
