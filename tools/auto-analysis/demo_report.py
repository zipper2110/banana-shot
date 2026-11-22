"""Demo report: one HTML page with the results of each task for the 4 singles matches.

Sections for each match:
  1. Rally detection (rally GRU with audio, image, and ball features, leave one match out) against the marks: numbers and a timeline.
  2. Point winner on the marked points (ball rule + score decoder): numbers and a table.
  3. First-serve fault candidates before the marked points.
  4. Ball tracking: clips of selected points with the ball path, the 3D flights, the speed,
     and the bounce points (demo_clip.py).
  5. Fully automatic run (pipeline.py): detected rallies -> winner -> score.
Output: <lab root>/demo/index.html and <lab root>/demo/clips/*.mp4.
Usage: python demo_report.py [--no-clips] [match id ...]
"""
import html, json, os, sys
import numpy as np
import lab
import match_decode as md
import winner_ball as wb
import demo_clip
from ball_full import WINDOWS

OUT = os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "demo") if os.path.basename(lab.ROOT.rstrip("/\\")) == "data" else os.path.join(lab.ROOT, "demo")
PTS = ["0", "15", "30", "40", "Ad"]


def clock(t):
    return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{int(t % 60):02d}"


def score_text(games, points, tb):
    """games, points: (near, far)."""
    if tb:
        pt = f"{points[0]}-{points[1]}"
    elif points[0] >= 3 and points[1] >= 3:
        pt = "40-40" if points[0] == points[1] else ("Ad-40" if points[0] > points[1] else "40-Ad")
    else:
        pt = f"{PTS[min(points[0], 4)]}-{PTS[min(points[1], 4)]}"
    return f"{games[0]}-{games[1]}, {pt}"


def state_score(s):
    """Decoder state -> score text from the view of the near player."""
    g, p = (s[0], s[1]), (s[2], s[3])
    if not s[6]:
        g, p = g[::-1], p[::-1]
    return score_text(g, p, s[4] >= 0)


def truth_score(sd, near_player):
    g, p = sd["scoreBefore"]["games"], sd["scoreBefore"]["rawPoints"]
    if near_player == "P2":
        g, p = g[::-1], p[::-1]
    p = list(p)
    if not sd["isTiebreak"] and p[0] >= 3 and p[1] >= 3:            # the decoder merges the deuce states
        m = min(p) - 3
        p = [p[0] - m, p[1] - m]
        if abs(p[0] - p[1]) == 0:
            p = [3, 3]
    return score_text(g, p, sd["isTiebreak"])


def timeline(mid, gt, pred, pairs):
    a, b = lab.eval_span(mid)
    rows_n = int(np.ceil((b - a) / 900.0))                          # 15 minutes in each row
    wpx, rh = 1100, 46
    hit_g = {g for g, _ in pairs}; hit_p = {j for _, j in pairs}
    o = [f'<svg viewBox="0 0 {wpx} {rows_n * rh + 4}" class="tl" role="img" aria-label="Timeline of marked and detected rallies">']
    for r in range(rows_n):
        y = r * rh
        o.append(f'<text x="0" y="{y + 12}" class="tlt">{clock(a + r * 900)}</text>')
        o.append(f'<rect x="60" y="{y + 2}" width="{wpx - 60}" height="14" class="lane"/><rect x="60" y="{y + 20}" width="{wpx - 60}" height="14" class="lane"/>')
    def bar(t0, t1, lane, cls, tip):
        t0, t1 = max(t0, a), min(t1, b)
        while t0 < t1:
            r = int((t0 - a) // 900)
            te = min(t1, a + (r + 1) * 900)
            x = 60 + (t0 - a - r * 900) / 900 * (wpx - 60)
            w = max(1.5, (te - t0) / 900 * (wpx - 60))
            o.append(f'<rect x="{x:.1f}" y="{r * rh + 2 + lane * 18}" width="{w:.1f}" height="14" class="{cls}"><title>{tip}</title></rect>')
            t0 = te
    for i, (s, e, _) in enumerate(gt):
        bar(s, e, 0, "g" if i in hit_g else "gm", f"Marked point {i + 1}, {clock(s)}" + ("" if i in hit_g else " (not detected)"))
    for j, (s, e) in enumerate(pred):
        bar(s, e, 1, "p" if j in hit_p else "pf", f"Detected rally, {clock(s)}" + ("" if j in hit_p else " (no marked point)"))
    o.append("</svg>")
    return "\n".join(o)


def pick_clips(mid, col, ends, n_each=2):
    """Points for the clips: the best tracked points with a right winner, and one with a wrong winner."""
    pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
    right, wrong = [], []
    for i, (pt, pw, pp, e) in enumerate(zip(pts, col["pw"], col["post"], ends)):
        if e["winnerNear"] is None or pt["end"] - pt["start"] > 25:
            continue
        ok = (pp > 0.5) == e["winnerNear"]
        cov = pt["coverage"]
        if ok and pw != 0.5 and cov > 0.4:
            right.append((-cov, i))
        if not ok and pw != 0.5:
            wrong.append((-cov, i))
    return [i for _, i in sorted(right)[:n_each]] + [i for _, i in sorted(wrong)[:1]]


def match_section(mid, col, clips=True):
    gt = lab.gt_points(mid)
    ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
    sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
    faults = json.load(open(os.path.join(lab.mdir(mid), "faults.json")))
    fpose = json.load(open(os.path.join(lab.mdir(mid), "faults_pose.json")))
    pipe = json.load(open(os.path.join(lab.mdir(mid), "pipeline.json")))
    pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
    a, b = lab.eval_span(mid)
    pred = [(r["start"], r["end"]) for r in pipe["rallies"] if r["end"] > a and r["start"] < b]     # GRU segments and short points from serve events (pipeline.py)
    pairs = lab.match_points(pred, gt)
    m = lab.point_metrics(pred, mid)
    # winner on the marked points
    n = ok = okb = dec = sc_ok = sc_n = pt_ok = 0
    rows = []
    path = col["states"]            # the most probable state of the decoder for each point (it finds the score again after an error)
    for i, (pt, e, sd) in enumerate(zip(pts, ends, sides)):
        pw, pp, st = col["pw"][i], col["post"][i], path[i]
        wn = e["winnerNear"]
        f = faults[i]["candidate"]
        ts = truth_score(sd, e["nearPlayer"]) if e["nearPlayer"] else None
        ds = state_score(st)
        if ts is not None:
            sc_n += 1; sc_ok += ts == ds; pt_ok += ts.split(", ")[1] == ds.split(", ")[1]
        if wn is not None:
            n += 1; ok += (pp > 0.5) == wn
            if pw != 0.5:
                dec += 1; okb += (pw > 0.5) == wn
        good = [x for x in pt["flights"] if wb.good(x) and pt["start"] <= x["t0"] <= pt["end"]]
        top = max((3.6 * float(np.linalg.norm(x["v0"])) for x in good), default=None)
        cls = "" if wn is None else ("ok" if (pp > 0.5) == wn else "bad")
        rows.append(f'<tr><td>{i + 1}</td><td>{clock(pt["start"])}</td><td>{pt["end"] - pt["start"]:.0f} s</td>'
                    f'<td>{len(good)}</td><td>{"" if top is None else f"{top:.0f}"}</td>'
                    f'<td>{"-" if pw == 0.5 else ("near" if pw > 0.5 else "far")}</td>'
                    f'<td class="{cls}">{"near" if pp > 0.5 else "far"} {100 * max(pp, 1 - pp):.0f}%</td>'
                    f'<td>{"" if wn is None else ("near" if wn else "far")}</td>'
                    f'<td class="{"" if ts is None else ("ok" if ts == ds else "bad")}">{ds}</td><td>{ts or ""}</td>'
                    f'<td>{"" if not f else f"{f["t"] - pt["start"]:+.0f} s, {f["speed"]:.0f} km/h" + (", pose agrees" if fpose[i]["poseT"] is not None else "")}</td></tr>')
    nf = sum(1 for f in faults if f["candidate"])
    nfp = sum(1 for f, g in zip(faults, fpose) if f["candidate"] and g["poseT"] is not None)
    pm = pipe["metrics"]
    cov = float(np.mean([p["coverage"] for p in pts]))
    o = [f'<section id="{mid}"><h2>Match {mid}</h2>',
         f'<p class="sub">{len(gt)} marked points, {clock(gt[0][0])} to {clock(gt[-1][1])} of the video.</p>',
         '<div class="cards">',
         f'<div class="card"><h3>1. Rally detection</h3><p class="big">{100 * m["recall"]:.0f}% <span>found</span></p>'
         f'<p>{m["hit"]} of {m["gt"]} marked points found. {m["pred"] - m["hit"]} of {m["pred"]} detected rallies have no mark '
         f'(precision {100 * m["precision"]:.0f}%). Median error: start {m["start_abs_med"]:.1f} s, end {m["end_abs_med"]:.1f} s.</p></div>',
         f'<div class="card"><h3>2. Point winner</h3><p class="big">{100 * ok / max(1, n):.0f}% <span>right</span></p>'
         f'<p>On the marked points. The ball rule alone decides {100 * dec / max(1, n):.0f}% of the points and is right for '
         f'{100 * okb / max(1, dec):.0f}% of them. The score decoder fills the rest. Score in the game right before the point: '
         f'{100 * pt_ok / max(1, sc_n):.0f}%; with the games: {100 * sc_ok / max(1, sc_n):.0f}%.</p></div>',
         f'<div class="card"><h3>3. First-serve fault</h3><p class="big">{nf} <span>candidates</span></p>'
         f'<p>A serve from the server end before the marked serve, at {100 * nf / len(gt):.0f}% of the points. '
         f'The serve pose (arm up) agrees for {nfp} of them. By eye on samples: about 9 of 10 candidates are real serves; about two thirds of the real faults are found.</p></div>',
         f'<div class="card"><h3>4. Ball tracking</h3><p class="big">{100 * cov:.0f}% <span>of frames</span></p>'
         f'<p>Share of the frames of a point with a ball on the selected path. '
         f'{sum(len(p["flights"]) for p in pts)} fitted 3D flights in the marked points.</p></div>',
         f'<div class="card"><h3>5. Fully automatic</h3><p class="big">{100 * pm["winnerEndToEnd"]:.0f}% <span>of points</span></p>'
         f'<p>Detected rallies, then winner and score with no marks: the rally is found and the winner is right. '
         f'Winner right on the found points: {100 * pm["winnerMatched"]:.0f}%.</p></div>',
         '</div>',
         '<h3>Timeline: marked points (top lane) and detected rallies (bottom lane)</h3>',
         '<p class="legend"><span class="k g"></span>marked, found <span class="k gm"></span>marked, not found '
         '<span class="k p"></span>detected, matches a mark <span class="k pf"></span>detected, no mark</p>',
         timeline(mid, gt, pred, pairs)]
    if clips:
        sel = pick_clips(mid, col, ends)
        o.append('<h3>Clips: ball path, 3D flights, speed, bounce points, and the winner</h3><div class="clips">')
        for i in sel:
            pr = {"pNearWins": col["post"][i], "truthNear": ends[i]["winnerNear"]}
            fn = demo_clip.render(mid, i, os.path.join(OUT, "clips"), pr)
            o.append(f'<figure><video controls preload="metadata" src="clips/{os.path.basename(fn)}"></video>'
                     f'<figcaption>Point {i + 1}, {clock(gt[i][0])}.</figcaption></figure>')
            print("clip", fn, flush=True)
        fc = [f for f in faults if f["candidate"]]
        for f in fc[len(fc) // 3::max(1, len(fc) // 3)][:2]:
            i, t = f["point"], f["candidate"]["t"]
            fn = demo_clip.render_fault(mid, i, os.path.join(OUT, "clips"), t)
            cap = f'First-serve fault candidate, {clock(t)}: a serve {gt[i][0] - t:.0f} s before the marked serve of point {i + 1}.'
            o.append(f'<figure><video controls preload="metadata" src="clips/{os.path.basename(fn)}"></video><figcaption>{html.escape(cap)}</figcaption></figure>')
            print("clip", fn, flush=True)
        o.append("</div>")
    o.append('<details><summary>All marked points: winner, score, serve fault candidate</summary><div class="tw"><table>'
             '<tr><th>#</th><th>Time</th><th>Length</th><th>Shots</th><th>Top speed km/h</th><th>Ball rule</th><th>Winner (auto)</th><th>Winner (mark)</th>'
             '<th>Score (auto), near-far</th><th>Score (mark)</th><th>Fault candidate</th></tr>' + "\n".join(rows) + "</table></div></details></section>")
    stats = {"gt": m["gt"], "hit": m["hit"], "pred": m["pred"], "n": n, "ok": ok, "nf": nf, "e2e_ok": pm["ok"], "e2e_known": pm["known"], "e2e_n": pm["n"], "sc_ok": sc_ok, "sc_n": sc_n, "pt_ok": pt_ok}
    return "\n".join(o), stats


CSS = """
:root{--bg:#f6f7f9;--fg:#16202b;--mut:#5b6875;--card:#fff;--line:#d9dee4;--acc:#0b6bcb;--ok:#1a7f37;--bad:#c62828;--lane:#e9edf1;--g:#2e7d32;--gm:#c62828;--p:#0b6bcb;--pf:#f39c12}
@media (prefers-color-scheme:dark){:root{--bg:#12161b;--fg:#e6eaee;--mut:#9aa7b4;--card:#1b2129;--line:#2c3540;--acc:#62a8f0;--ok:#57c06b;--bad:#ff7b72;--lane:#232b35;--g:#57c06b;--gm:#ff7b72;--p:#62a8f0;--pf:#f5b84a}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,Segoe UI,sans-serif}
main{max-width:1180px;margin:0 auto;padding:24px 16px 60px}h1{font-size:26px;margin:0 0 6px}h2{font-size:21px;margin:40px 0 2px;border-top:1px solid var(--line);padding-top:22px}
h3{font-size:15px;margin:20px 0 6px}.sub,.legend{color:var(--mut);margin:0 0 10px}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}.card h3{margin:0;color:var(--mut);font-weight:600;font-size:13px}
.card p{margin:4px 0 0;font-size:13px;color:var(--mut)}.card .big{font-size:30px;font-weight:700;color:var(--fg);margin:2px 0}.big span{font-size:13px;font-weight:400;color:var(--mut)}
.tl{width:100%;height:auto;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:8px}.tlt{font-size:11px;fill:var(--mut)}.lane{fill:var(--lane)}
.tl .g{fill:var(--g)}.tl .gm{fill:var(--gm)}.tl .p{fill:var(--p)}.tl .pf{fill:var(--pf)}
.k{display:inline-block;width:12px;height:12px;border-radius:2px;margin:0 5px 0 12px;vertical-align:-1px}.k.g{background:var(--g)}.k.gm{background:var(--gm)}.k.p{background:var(--p)}.k.pf{background:var(--pf)}
.clips{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:12px}figure{margin:0}video{width:100%;border-radius:8px;background:#000}figcaption{font-size:12px;color:var(--mut)}
details{margin-top:16px}summary{cursor:pointer;color:var(--acc)}.tw{overflow-x:auto}table{border-collapse:collapse;font-size:12.5px;margin-top:8px;width:100%}
th,td{padding:3px 8px;border-bottom:1px solid var(--line);text-align:left;white-space:nowrap}td.ok{color:var(--ok)}td.bad{color:var(--bad);font-weight:600}
.note{background:var(--card);border-left:4px solid var(--acc);padding:10px 14px;border-radius:6px;margin:14px 0}ul{margin:6px 0;padding-left:20px}nav a{color:var(--acc);margin-right:14px}
"""


def main(mids, clips=True):
    col = {}
    md.use_lomo_cases()                       # winner probabilities measured on the other 3 matches
    md.main("both", "flip", collect=col, quiet=True)
    secs, T = [], {}
    for mid in mids:
        s, st = match_section(mid, col[mid], clips)
        secs.append(s)
        for k, v in st.items():
            T[k] = T.get(k, 0) + v
    head = f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Automatic tennis analysis: prototype demo</title><style>{CSS}</style></head><body><main>
<h1>Automatic tennis analysis: prototype demo</h1>
<p class="sub">Four singles matches from one fixed camera. All numbers are measured against the hand marks of the projects. Local processing only.</p>
<div class="cards">
<div class="card"><h3>Rally detection</h3><p class="big">{100 * T['hit'] / T['gt']:.0f}% <span>found</span></p><p>{T['hit']} of {T['gt']} marked points. Precision {100 * T['hit'] / T['pred']:.0f}%. Rally model with audio, image, and ball features, plus short points from serve events. The model never saw the test match.</p></div>
<div class="card"><h3>Point winner (marked points)</h3><p class="big">{100 * T['ok'] / T['n']:.0f}% <span>right</span></p><p>Ball rule, server votes, shirt color, and the score decoder.</p></div>
<div class="card"><h3>Score in the game</h3><p class="big">{100 * T['pt_ok'] / T['sc_n']:.0f}% <span>right</span></p><p>Points score before each marked point (example: 30-15). With the games count also right: {100 * T['sc_ok'] / T['sc_n']:.0f}%. One fix by the user of a wrong winner puts the score right again.</p></div>
<div class="card"><h3>First-serve fault candidates</h3><p class="big">{T['nf']}</p><p>At {100 * T['nf'] / T['gt']:.0f}% of the points. About 9 of 10 are real serves; recall about two thirds (by eye, small samples).</p></div>
<div class="card"><h3>Fully automatic</h3><p class="big">{100 * T['e2e_ok'] / T['e2e_known']:.0f}% <span>of points</span></p><p>Rally found and winner right, with no marks. Winner right on the found points: {100 * T['e2e_ok'] / T['e2e_n']:.0f}%.</p></div>
</div>
<div class="note"><b>How to read this page.</b> Tasks 1 to 4 are shown alone: each task uses the hand marks where it needs point boundaries.
Task 5 chains all tasks with no marks. In the clips: yellow dots = ball detections, cyan line = fitted 3D flight, ring = bounce or landing point
(green in, red out), small court = top view. The clips run at half speed.</div>
<nav>{''.join(f'<a href="#{m}">{m}</a>' for m in mids)}</nav>"""
    os.makedirs(OUT, exist_ok=True)
    fn = os.path.join(OUT, "index.html")
    open(fn, "w", encoding="utf-8").write(head + "\n".join(secs) + "</main></body></html>")
    print("wrote", fn, {k: int(v) for k, v in T.items()})


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    main(args or list(WINDOWS), "--no-clips" not in sys.argv)
