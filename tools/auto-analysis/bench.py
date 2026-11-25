"""The benchmark of the detection chain in one command (gap program S1: G1, G3).

Two sets:
  lab LOMO   the 4 marked lab matches through pipeline.py: chain.py with the models that did not see the match;
  held-out   each marked test-only video (matches.json "testOnly", lab.TEST) through detect.py, the path of the
             app, with the shipped models. The heavy stages (ball, flights, pose) must be in the work folder from a
             full run (D:/bananashot-lab/data/<match>); the benchmark runs the rally and winner stages again with
             the code under test.
Report: rallies, ends, short points, faults (where labels exist), winners with the noise (1 standard error), and the
calibration of the winner confidence (G3). Output: <lab>/bench/<time>-<commit>/ report.md, metrics.json,
manifest.json. The manifest has the code commit, the switches, and a hash of each model and input file. A second run
with the same manifest must give the same numbers: the report says if an earlier run had the same manifest.

Run the benchmark from a snapshot of one commit, not from the main checkout (other streams merge into it):
  python bench.py snapshot [--ref <commit or branch>]   exports the code to <lab>/ws/s1/code/<sha>/
  python <lab>/ws/s1/code/<sha>/tools/auto-analysis/bench.py run [--tag <text>] [--no-heldout]
"""
import argparse, glob, hashlib, json, math, os, re, subprocess, sys, time
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
CONF_BINS = [0.5, 0.6, 0.7, 0.8, 0.9, 1.0001]
LIMITS = [50, 55, 60, 65, 70, 75, 80, 85, 90]       # candidates for minWinnerPercent and the "check first" limit
CHECK_FIRST = 0.70                                  # the "check first" limit of the review window now


def sha(path, n=16):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()[:n]


def code_commit():
    """The commit of the code that runs: the COMMIT file of a snapshot, else git (with "dirty" if the tools differ)."""
    f = os.path.join(REPO, "COMMIT")
    if os.path.exists(f):
        return open(f).read().split()[0], False
    c = subprocess.run(["git", "-C", REPO, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    dirty = bool(subprocess.run(["git", "-C", REPO, "status", "--porcelain", "--", "tools/auto-analysis"],
                                capture_output=True, text=True).stdout.strip())
    return c, dirty


def snapshot(ref):
    import lab
    c = subprocess.run(["git", "-C", REPO, "rev-parse", ref], capture_output=True, text=True, check=True).stdout.strip()
    dst = os.path.join(os.path.dirname(lab.ROOT), "ws", "s1", "code", c[:8])
    if not os.path.exists(os.path.join(dst, "COMMIT")):
        os.makedirs(dst, exist_ok=True)
        z = dst + ".zip"
        subprocess.run(["git", "-C", REPO, "archive", "--format=zip", "-o", z, c, "tools/auto-analysis"], check=True)
        import zipfile
        zipfile.ZipFile(z).extractall(dst)
        os.remove(z)
        open(os.path.join(dst, "COMMIT"), "w").write(c + " " + ref + "\n")
    print(os.path.join(dst, "tools", "auto-analysis", "bench.py"))


def switches():
    """The environment switches that the code reads (value or None = default), and the resolved chain flags."""
    names = set()
    for f in glob.glob(os.path.join(HERE, "*.py")):
        names |= set(re.findall(r"environ\.get\(\"([A-Z][A-Z0-9_]+)\"", open(f, encoding="utf-8").read()))
    names -= {"APPDATA", "LOCALAPPDATA", "USERPROFILE", "HOME", "HF_HOME", "PATH", "TEMP", "TMP", "HEAVY_LOCK_DIR"}
    import chain
    return {"env": {k: os.environ.get(k) for k in sorted(names)},
            "chain": {k: getattr(chain, k) for k in ("CHAIN", "END_MODEL", "FPS_ADAPT", "ID_ABS", "T_CUT_E2E", "SHORT_WAIT")
                      if hasattr(chain, k)}}


LAB_INPUTS = ["fullflights_s0.35_ev.json", "flights_s0.35_ev.json", "net_events.json", "feat_audio.npy", "feat_mel.npy",
              "feat_dino.npy", "feat_wasbf.npy", "pose.npy", "kf_times.csv", "hits.npy", "gt.json", "ends.json", "sides.json"]
HELD_INPUTS = ["fullflights_s0.35.json", "net_events.json", "feat_audio.npy", "feat_mel.npy", "feat_dino.npy", "feat_wasbf.npy",
               "pose.npy", "kf_times.csv", "pts.npy", "hits.npy", "court_track.json", "court_track_refined.json",
               "gt.json", "ends.json", "sides.json"]
HEAVY = ["fullflights_s0.35.json", "feat_wasbf.npy", "pose.npy", "feat_dino.npy", "feat_audio.npy", "kf_times.csv"]


def file_hashes(folder, names):
    out = {n: sha(os.path.join(folder, n)) for n in names if os.path.exists(os.path.join(folder, n))}
    mid = os.path.basename(os.path.normpath(folder))
    for f in sorted(glob.glob(os.path.join(folder, "*labels*.json")) + glob.glob(os.path.join(HERE, f"*labels*{mid}*.json"))):
        out["labels/" + os.path.basename(f)] = sha(f)            # event labels (S4): a change gives a new manifest
    for b in sorted(glob.glob(os.path.join(folder, "wasb_0_*_k2.npy"))):        # the ball candidates of a full run
        out[os.path.basename(b)] = sha(b)
    return out


def model_hashes():
    import lab, platform_cfg
    out = {}
    # Only the models that the benchmark loads: the shipped rally models and the fold models of "ball".
    for f in sorted(glob.glob(os.path.join(lab.ROOT, "models", "rally_ball*.pt")) + [os.path.join(lab.ROOT, "models", "rally_base.pt")]):
        if os.path.exists(f):
            out["models/" + os.path.basename(f)] = sha(f)
    w = os.path.join(platform_cfg.weights_dir(), "ball_e38.pth")
    if os.path.exists(w):
        out["weights/ball_e38.pth"] = sha(w)
    for f in ("rally_end.json",):
        if os.path.exists(os.path.join(HERE, f)):
            out["code/" + f] = sha(os.path.join(HERE, f))
    return out


def se(p, n):
    return math.sqrt(p * (1 - p) / n) if n else float("nan")


def winner_rows(rows):
    """(confidence, right) for each matched row with a known winner; None for a row with no winner (p near 0.5)."""
    out = []
    for r in rows:
        wn = r.get("gtWinnerNear")
        if "gt" not in r or wn is None:
            continue
        p = r["pNearWins"]
        out.append((max(p, 1 - p), (p > 0.5) == wn, abs(p - 0.5) < 0.02))
    return out


def calibration(wr):
    bins = []
    for lo, hi in zip(CONF_BINS, CONF_BINS[1:]):
        x = [ok for c, ok, none in wr if lo <= c < hi and not none]
        bins.append({"bin": f"{100 * lo:.0f}-{min(100, 100 * hi):.0f} %", "n": len(x), "right": (sum(x) / len(x)) if x else None})
    lim = []
    dec = [(c, ok) for c, ok, none in wr if not none]
    for L in LIMITS:
        keep = [ok for c, ok in dec if c >= L / 100]
        lim.append({"limit": L, "kept": len(keep) / max(1, len(wr)), "rightOfKept": (sum(keep) / len(keep)) if keep else None,
                    "wrongPer100": 100 * (len(keep) - sum(keep)) / max(1, len(wr))})
    flag = [ok for c, ok in dec if c < CHECK_FIRST]
    rest = [ok for c, ok in dec if c >= CHECK_FIRST]
    return {"bins": bins, "limits": lim, "checkFirst": {"limit": CHECK_FIRST, "flagged": len(flag) / max(1, len(dec)),
            "rightFlagged": (sum(flag) / len(flag)) if flag else None, "rightOther": (sum(rest) / len(rest)) if rest else None}}


def extras(mid, rows):
    """Short points and faults against the marks (faults only where gt.json has "faults")."""
    import lab
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))
    short = [r for r in rows if r.get("kind") == "short"]
    out = {"shortAdded": len(short), "shortReal": sum("gt" in r for r in short)}
    pts = g["points"]
    sp = [i for i, p in enumerate(pts) if p.get("ending") in ("ACE", "SERVE_FAULT")]
    if sp:                                       # marked short points (ace, double fault): found by any row
        got = {r["gt"] for r in rows if "gt" in r}
        out.update(shortMarked=len(sp), shortMarkedFound=sum(i in got for i in sp))
    if "faults" in g:
        fl = [(f["startMs"] / 1000 - 1.0, f["endMs"] / 1000 + 1.0) for f in g["faults"]]
        cand = [r["fault"]["t"] for r in rows if r.get("fault")]
        out.update(faultsMarked=len(fl), faultCandidates=len(cand),
                   faultCandidatesRight=sum(any(a <= t <= b for a, b in fl) for t in cand),
                   faultsFound=sum(any(a <= t <= b for t in cand) for a, b in fl))
    return out


def events(mid, rows):
    """The event measures of a match (priority 2 of the user: hits, hitter, shot count, bounces, in/out, net touches
    against labels). The scorer is score_events.py (S4). Contract: score_events.score(mid, rows) returns None when the
    match has no event labels, else a list of measures {"name": str, "k": int, "n": int} (a rate k of n, pooled over
    the matches of a set) or {"name": str, "value": float, "unit": str} (shown for each match, not pooled)."""
    try:
        import score_events
    except ImportError:
        return None
    return score_events.score(mid, rows)


def pool_events(per_match):
    """Rates pooled over the matches (sum of k and n); other values listed per match."""
    rates, values = {}, {}
    for mid, ms in per_match.items():
        for m in ms or []:
            if "n" in m:
                r = rates.setdefault(m["name"], [0, 0])
                r[0] += m["k"]; r[1] += m["n"]
            else:
                values.setdefault(m["name"], []).append((mid, m.get("value"), m.get("unit", "")))
    return {"rates": {k: {"k": v[0], "n": v[1], "rate": v[0] / v[1] if v[1] else None, "se": se(v[0] / v[1], v[1]) if v[1] else None}
                      for k, v in rates.items()}, "values": values, "matches": [m for m, x in per_match.items() if x]}


def run_lab():
    import pipeline
    pipeline.setup_lomo()
    res = {}
    for mid in pipeline.WINDOWS:
        m, rows = pipeline.run(mid, quiet=True, write=False)
        res[mid] = (m, rows)
    return res


HEAVY_STAGES = ("media", "audio", "image", "court", "court2", "ball", "flights", "pose")


def detect_light(code_dir, mid, d):
    """detect.py of this code on the folder d, through the GPU lock (gpu_lock.py), with a guard: when a heavy stage
    starts (its cache is missing, for example after a change of the cache file names), the process tree stops at once.
    The benchmark only reads a cache; a full run is a separate job. Returns (rc, heavy stage or None, log file)."""
    import lab, gpu_lock
    log = os.path.join(d, "bench-detect.log")
    cmd = [sys.executable, os.path.join(code_dir, "detect.py"), "--video", lab.MATCHES[mid]["video"], "--work", d]
    who, what = "s1", f"bench.py: rally and winner stages of {mid} (heavy stages refused)"
    heavy, rc, t0 = None, -1, time.time()
    gpu_lock.acquire(who, what)                      # in this process, so that a stop of detect.py still frees the lock
    try:
        with open(log, "w") as f:
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            for line in p.stdout:
                f.write(line)
                m = re.match(r"@stage (\S+) start", line)
                if m and m.group(1) in HEAVY_STAGES:
                    heavy = m.group(1)
                    if os.name == "nt":            # the venv python starts a child: stop the tree
                        subprocess.call(["taskkill", "/T", "/F", "/PID", str(p.pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    else:
                        p.kill()
                    break
            rc = p.wait()
    finally:
        gpu_lock.release(who, what, rc, t0)
    return rc, heavy, log


def run_heldout(code_dir):
    """detect.py from this code on each marked test-only video with a full run in its folder: the rally and winner
    stages run again, the heavy stages come from the cache."""
    import lab, score
    res, skipped = {}, {}
    for mid in lab.TEST:
        d = lab.mdir(mid)
        miss = [f for f in HEAVY + ["ends.json", "gt.json"] if not os.path.exists(os.path.join(d, f))]
        if miss or not glob.glob(os.path.join(d, "wasb_0_*_k2.npy")):
            skipped[mid] = "missing: " + ", ".join(miss or ["ball candidates"])
            continue
        rc, heavy, log = detect_light(code_dir, mid, d)
        if rc != 0 or heavy:
            skipped[mid] = (f"detect.py wanted the heavy stage '{heavy}': the cache of this code is not in the folder "
                            f"(stopped at once; see {log})" if heavy else f"detect.py rc={rc} (see {log})")
            continue
        rows = json.load(open(os.path.join(d, "result.json")))["rallies"]
        res[mid] = (score.score(mid, rows), rows)
    return res, skipped


def summary(res):
    import score
    ms = [m for m, _ in res.values()]
    n, ok, kn = (sum(m[k] for m in ms) for k in ("n", "ok", "known"))
    wr = [x for _, rows in res.values() for x in winner_rows(rows)]
    ex = {mid: extras(mid, rows) for mid, (_, rows) in res.items()}
    tot = {"matches": list(res), "marked": sum(m["marked"] for m in ms), "detected": sum(m["detected"] for m in ms),
           "recall": sum(m["matched"] for m in ms) / max(1, sum(m["marked"] for m in ms)),
           "precision": sum(m["matched"] for m in ms) / max(1, sum(m["detected"] for m in ms)),
           "winnerMatched": ok / max(1, n), "winnerMatchedSE": se(ok / max(1, n), n),
           "endToEnd": ok / max(1, kn), "endToEndSE": se(ok / max(1, kn), kn), "n": n, "known": kn,
           "startAbsMed": float(np.median([m["startAbsMed"] for m in ms])) if ms else None,
           "endAbsMed": float(np.median([m["endAbsMed"] for m in ms])) if ms else None}
    for k in ("shortAdded", "shortReal", "shortMarked", "shortMarkedFound", "faultsMarked", "faultCandidates",
              "faultCandidatesRight", "faultsFound"):
        v = [e[k] for e in ex.values() if k in e]
        if v:
            tot[k] = sum(v)
    ev = pool_events({mid: events(mid, rows) for mid, (_, rows) in res.items()})
    return {"total": tot, "events": ev, "perMatch": {mid: dict(m, **ex[mid]) for mid, (m, _) in res.items()},
            "calibration": calibration(wr), "lines": [score.line(m) for m, _ in res.values()]}


def pct(x):
    return "-" if x is None else f"{100 * x:.1f} %"


def report(man, sets, skipped, same):
    L = [f"# Benchmark {man['time']}", "", f"Code: `{man['commit'][:12]}`{' (dirty)' if man['dirty'] else ''}. "
         f"Switches: CHAIN={man['switches']['chain'].get('CHAIN')}, END_MODEL={man['switches']['chain'].get('END_MODEL')}, "
         f"FPS_ADAPT={man['switches']['chain'].get('FPS_ADAPT')}. Set env: "
         + (", ".join(f"{k}={v}" for k, v in man['switches']['env'].items() if v is not None) or "none") + ".",
         f"Manifest `{man['hash']}`. " + (f"An earlier run with the same manifest: {same['run']} ({'same numbers' if same['same'] else '**other numbers**'})."
                                          if same else "No earlier run with the same manifest."), ""]
    for name, s in sets.items():
        t = s["total"]
        L += [f"## {name}: {', '.join(t['matches'])}", "",
              "| Measure | Value |", "|---|---|",
              f"| Rallies: recall / precision | {pct(t['recall'])} / {pct(t['precision'])} ({t['detected']} detected, {t['marked']} marked) |",
              f"| Start / end error, median of matches | {t['startAbsMed']:.2f} s / {t['endAbsMed']:.2f} s |"]
        # Priority 2 of the user: the events of each rally against labels come before the winner (a check only).
        ev = s.get("events") or {}
        if ev.get("matches"):
            L.append(f"| **Events** (labels of {', '.join(ev['matches'])}) | |")
            L += [f"| {k} | {pct(v['rate'])} ± {pct(v['se'])} ({v['k']} of {v['n']}) |" for k, v in ev["rates"].items()]
            L += [f"| {k} | " + ", ".join(f"{mid} {val:.2f} {u}".rstrip() for mid, val, u in vs if val is not None) + " |"
                  for k, vs in ev["values"].items()]
        else:
            L.append("| Events | no event labels for these matches |")
        L += [f"| Winner right on matched points (check) | {pct(t['winnerMatched'])} ± {pct(t['winnerMatchedSE'])} (n {t['n']}) |",
              f"| Winner right, end to end (check) | {pct(t['endToEnd'])} ± {pct(t['endToEndSE'])} (of {t['known']} marked winners) |",
              f"| Short points added: real | {t.get('shortReal', 0)} of {t.get('shortAdded', 0)} |"]
        if "shortMarked" in t:
            L.append(f"| Marked aces and double faults found | {t['shortMarkedFound']} of {t['shortMarked']} |")
        if "faultsMarked" in t:
            L.append(f"| First-serve faults: candidates right / marked found | {t['faultCandidatesRight']} of {t['faultCandidates']} / "
                     f"{t['faultsFound']} of {t['faultsMarked']} |")
        L += ["", "Per match:", ""] + [f"- {x}" for x in s["lines"]]
        c = s["calibration"]
        L += ["", "Calibration (matched points with a marked winner):", "", "| Confidence | n | Right |", "|---|---|---|"]
        L += [f"| {b['bin']} | {b['n']} | {pct(b['right'])} |" for b in c["bins"]]
        L += ["", "| Limit (minWinnerPercent) | Winner kept | Right of kept | Wrong winners per 100 points |", "|---|---|---|---|"]
        L += [f"| {x['limit']} % | {pct(x['kept'])} | {pct(x['rightOfKept'])} | {x['wrongPer100']:.1f} |" for x in c["limits"]]
        cf = c["checkFirst"]
        L += ["", f"\"Check first\" under {100 * cf['limit']:.0f} %: {pct(cf['flagged'])} of the decided points; right "
              f"{pct(cf['rightFlagged'])} there, {pct(cf['rightOther'])} for the others.", ""]
    for mid, why in skipped.items():
        L.append(f"Held-out {mid} skipped: {why}")
    return "\n".join(L) + "\n"


def run(a):
    import lab
    t0 = time.time()
    commit, dirty = code_commit()
    sets, inputs = {}, {}
    if not a.no_lab:
        sets["Lab LOMO"] = run_lab()
        for mid in sets["Lab LOMO"]:
            inputs[mid] = file_hashes(lab.mdir(mid), LAB_INPUTS)
    skipped = {}
    if not a.no_heldout:
        held, skipped = run_heldout(HERE)
        if held:
            sets["Held-out"] = held
            for mid in held:
                inputs[mid] = file_hashes(lab.mdir(mid), HELD_INPUTS)
    man = {"commit": commit, "dirty": dirty, "switches": switches(), "models": model_hashes(), "inputs": inputs,
           "sets": {k: list(v) for k, v in sets.items()}}
    man["hash"] = hashlib.sha256(json.dumps(man, sort_keys=True, default=str).encode()).hexdigest()[:16]
    man["time"] = time.strftime("%Y-%m-%d %H:%M:%S")
    out = {k: summary(v) for k, v in sets.items()}
    root = os.path.join(os.path.dirname(lab.ROOT), "bench")
    same = None
    for f in sorted(glob.glob(os.path.join(root, "*", "manifest.json")), reverse=True):
        if json.load(open(f)).get("hash") == man["hash"]:
            old = json.load(open(os.path.join(os.path.dirname(f), "metrics.json")))
            same = {"run": os.path.basename(os.path.dirname(f)),
                    "same": json.dumps(old, sort_keys=True, default=str) == json.dumps(json.loads(json.dumps(out, default=float)), sort_keys=True, default=str)}
            break
    d = os.path.join(root, time.strftime("%Y%m%d-%H%M%S") + "-" + commit[:8] + (f"-{a.tag}" if a.tag else ""))
    os.makedirs(d)
    json.dump(man, open(os.path.join(d, "manifest.json"), "w"), indent=1, default=str)
    json.dump(out, open(os.path.join(d, "metrics.json"), "w"), indent=1, default=float)
    txt = report(man, out, skipped, same)
    open(os.path.join(d, "report.md"), "w", encoding="utf-8").write(txt)
    print(txt)
    print(f"written: {d} ({time.time() - t0:.0f} s)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("snapshot")
    s.add_argument("--ref", default="HEAD")
    r = sub.add_parser("run")
    r.add_argument("--tag", default="")
    r.add_argument("--no-heldout", action="store_true")
    r.add_argument("--no-lab", action="store_true")
    a = ap.parse_args()
    sys.path.insert(0, HERE)
    snapshot(a.ref) if a.cmd == "snapshot" else run(a)
