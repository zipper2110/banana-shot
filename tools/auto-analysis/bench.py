"""The benchmark of the detection chain in one command (gap program S1: G1, G3).

Two sets:
  lab LOMO   the 4 marked lab matches through pipeline.py: chain.py with the models that did not see the match;
  held-out   each marked test-only video (matches.json "testOnly", lab.TEST) through detect.py, the path of the
             app, with the shipped models. The heavy stages (ball, flights, pose) must be in the work folder from a
             full run (D:/bananashot-lab/data/<match>); the benchmark runs the rally and winner stages again with
             the code under test.
Report: rallies, ends, the events against labels (score_events.py, S4; before the winners), short points, faults
(where labels exist), winners as a check with the noise (1 standard error), and the
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


EVENT_SECTIONS = ("hits", "bounces", "nets", "lastShot")


def events_all(nets=None):
    """The event measures of each match with event labels (priority 2 of the user: hits, hitter, shot count, bounces,
    in/out, net touches against labels). The scorer is score_events.py (S4, E75). It builds the chain shots on the
    window of each labeled clip from the flights and net files, so it does not use the rally rows: the measure does
    not depend on the rally detection (priority 1 has its own measures). Contract: score_events.measures(mid) returns
    None (no event labels or no flights) or a list of measures: a rate {"name", "k", "n"} (pooled over the matches of
    a set) or a value {"name", "value", "unit"} (shown for each match). Returns {mid: (measures, input file hashes)},
    or None when this code has no score_events.py. nets: {mid: net_events.json made the app way} (app_nets): the
    measure of these matches reads the folder of that file (it has a copy of the flights too)."""
    try:
        import score_events, lab
    except ImportError:
        return None
    out = {}
    for mid in lab.MATCHES:
        folder = os.path.dirname(nets[mid]) if nets and mid in nets else None
        r = score_events.measures(mid, folder=folder)   # the flat list (manager, 2026-11-26: fixed interface)
        if r:
            d = score_events.load(mid, folder)
            out[mid] = (r, d["files"] if d else {})
    return out


def label_hashes():
    """The hash of each event label file: a change of the labels gives a new manifest."""
    try:
        import score_events
    except ImportError:
        return {}
    fs = [os.path.join(HERE, n) for n in ("bounce_labels.json", "net_labels.json")]
    fs += glob.glob(os.path.join(score_events.HIT_LABELS_DIR, "hit_labels_*.json"))
    return {os.path.basename(f): sha(f) for f in sorted(fs) if os.path.exists(f)}


def events_of(ev, mids):
    """The event part of one set: the matches of the set with labels, their pooled total and the report lines."""
    if ev is None:
        return None
    rates, values, per = {}, {}, {}
    for mid in mids:
        if mid not in ev:
            continue
        per[mid] = ev[mid][0]
        for m in ev[mid][0]:
            if "n" in m:
                r = rates.setdefault(m["name"], [0, 0])
                r[0] += m["k"]; r[1] += m["n"]
            else:
                values.setdefault(m["name"], []).append([mid, m["value"], m.get("unit", "")])
    return {"matches": list(per), "perMatch": per, "values": values,
            "rates": {k: {"k": v[0], "n": v[1], "rate": v[0] / v[1], "se": se(v[0] / v[1], v[1])} for k, v in rates.items() if v[1]}}


# The bounce and net labels (E17d, E16f) were made on detector candidates: a bounce or net touch that the detector
# never proposed has no label, so their recall is optimistic (manager, 2026-11-26). The hit labels are made by hand.
OPTIMISTIC = ("Bounces found", "Net touches found")


# E85 (S5): the lab file net_events.json came from the old ball blobs and the old flights. The lab set makes the net
# events again with the code under test from the frozen lab inputs, as detect.py makes them: the E38 "dim" blobs
# (tag _e27) and the E38 flights (fullflights_s0.35_ev.json). CPU only, about 1 to 1.5 min per match, no decode.
# The output is cached by a key of the code and the inputs. The fits (shot_chain, rally_end) keep the old file: the
# shipped models were fit on it.
LAB_BLOB_TAG = "_e27"
NET_CODE = ("net_events.py", "net_lab.py", "ball_events.py", "lab.py")
NET_COPY = ["pts.npy", "kf_times.csv", "court.json", "court_track.json", "court_track_refined.json"]


def app_folder(mid):
    """The app work folder of a match with event labels and no lab flights (m0913), else None."""
    import lab
    d = os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "app", mid)
    return d if os.path.exists(os.path.join(d, "fullflights_s0.35.json")) else None


def net_inputs(mid):
    """("lab" or "app", the input files of the net events of match mid). A lab match: the frozen lab inputs. An app
    folder (m0913): its newest ball file with no tag, its flights, frame times and court files."""
    import lab
    from ball_full import WINDOWS
    if mid in WINDOWS:
        return "lab", [os.path.join(lab.mdir(mid), f) for f in NET_COPY + ["fullflights_s0.35_ev.json",
                                                                         "wasb_%d_%d_s0.35%s_k2.npy" % (*WINDOWS[mid], LAB_BLOB_TAG)]]
    d = app_folder(mid)
    blobs = glob.glob(os.path.join(d, "wasb_0_*_s0.35_k2.npy"))
    return "app", [os.path.join(d, f) for f in NET_COPY + ["pts_video.npy", "fullflights_s0.35.json"]] + \
        ([max(blobs, key=os.path.getmtime)] if blobs else [])


def app_nets(mids):
    """{mid: path of net_events.json made the app way} for lab matches and app folders. Reads their folders only:
    the work is in a cache folder for each match, <lab>/bench/nets/<key>/<lab|app>_<match>/, with a key of the
    code and of the inputs of that match."""
    import lab, shutil
    code = [os.path.join(HERE, n) for n in NET_CODE]
    out, todo = {}, []
    for m in mids:
        kind, src = net_inputs(m)
        src = [f for f in src if os.path.exists(f)]
        h = hashlib.sha256()
        for f in code + src:
            h.update(os.path.basename(f).encode() + sha(f).encode())
        dst = os.path.join(os.path.dirname(lab.ROOT), "bench", "nets", h.hexdigest()[:16], f"{kind}_{m}")  # not "<match>"
        out[m] = os.path.join(dst, "net_events.json")
        if not os.path.exists(out[m]):
            todo.append((m, kind, src, dst))
    if todo:
        import net_events
        tag, mdir, cwd = lab.BLOB_TAG, lab.mdir, os.getcwd()
        try:
            for m, kind, src, dst in todo:
                os.makedirs(dst, exist_ok=True)
                for f in src:
                    n = os.path.basename(f)
                    shutil.copy2(f, os.path.join(dst, "fullflights_s0.35.json" if n == "fullflights_s0.35_ev.json" else n))
                t = time.time()
                if kind == "lab":
                    # net_lab.Source takes an existing folder with the name of the match as an app folder: work in
                    # the parent of dst, which has no such folder. The lab branch reads lab.mdir and lab.BLOB_TAG.
                    lab.BLOB_TAG = LAB_BLOB_TAG
                    os.chdir(os.path.dirname(dst))
                    lab.mdir = lambda x, _o=mdir, m=m, dst=dst: dst if x == m else _o(x)
                    path, ev = net_events.run_folder(m)
                else:
                    lab.BLOB_TAG = ""                  # the app branch: the newest ball file with no tag
                    path, ev = net_events.run_folder(dst)
                lab.BLOB_TAG, lab.mdir = tag, mdir
                print(f"net events of {m} made the app way: {len(ev)} ({time.time() - t:.0f} s)", flush=True)
        finally:
            lab.BLOB_TAG, lab.mdir = tag, mdir
            os.chdir(cwd)
    return out


LAB_DEVICE = "cpu"


def run_lab(nets=None):
    """The lab set does not take the GPU lock (it is short), so it must not use the GPU: a CUDA context next to a
    ball stage of another job gave that job an out-of-memory stop (6 GB card). The rally model runs on the CPU.
    nets: {mid: net events file} for the chain (app_nets); None = the old lab file."""
    import platform_cfg
    platform_cfg._cache["device"] = LAB_DEVICE
    import pipeline, chain
    chain.NET_FILES.clear()
    chain.NET_FILES.update(nets or {})
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


def summary(res, ev=None):
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
    return {"total": tot, "events": events_of(ev, list(res)), "perMatch": {mid: dict(m, **ex[mid]) for mid, (m, _) in res.items()},
            "calibration": calibration(wr), "lines": [score.line(m) for m, _ in res.values()]}


def pct(x):
    return "-" if x is None else f"{100 * x:.1f} %"


def report(man, sets, skipped, same):
    L = [f"# Benchmark {man['time']}", "", f"Code: `{man['commit'][:12]}`{' (dirty)' if man['dirty'] else ''}. "
         f"Switches: CHAIN={man['switches']['chain'].get('CHAIN')}, END_MODEL={man['switches']['chain'].get('END_MODEL')}, "
         f"FPS_ADAPT={man['switches']['chain'].get('FPS_ADAPT')}. Set env: "
         + (", ".join(f"{k}={v}" for k, v in man['switches']['env'].items() if v is not None) or "none") + "."
         + (f" Lab net events: {man['labNets']}." if man.get("labNets") else ""),
         f"Manifest `{man['hash']}`. " + (f"An earlier run with the same manifest: {same['run']} ({'same numbers' if same['same'] else '**other numbers**'})."
                                          if same else "No earlier run with the same manifest."), ""]
    def event_rows(ev):
        """Priority 2 of the user: the events against labels. They come before the winner rows (a check only)."""
        if ev is None:
            return ["| Events | this code has no score_events.py |"]
        if not ev["matches"]:
            return ["| Events | no event labels for these matches |"]
        rows = [f"| **Events** (labels of {', '.join(ev['matches'])}) | |"]
        for k, v in ev["rates"].items():
            note = " (optimistic: labels from detector candidates)" if k.startswith(OPTIMISTIC) else ""
            rows.append(f"| {k}{note} | {pct(v['rate'])} ± {pct(v['se'])} ({v['k']} of {v['n']}) |")
        for k, vs in ev["values"].items():
            rows.append(f"| {k} | " + ", ".join(f"{m} {x:.3g} {u}".rstrip() for m, x, u in vs) + " |")
        return rows

    for name, s in sets.items():
        t = s["total"]
        if t is None:                                   # a set with event labels only (no marked points in a benchmark set)
            ev = s["events"]
            L += [f"## {name}: {', '.join(ev['matches'])}", "", "| Measure | Value |", "|---|---|"] + event_rows(ev)
            L += [""]
            continue
        L += [f"## {name}: {', '.join(t['matches'])}", "",
              "| Measure | Value |", "|---|---|",
              f"| Rallies: recall / precision | {pct(t['recall'])} / {pct(t['precision'])} ({t['detected']} detected, {t['marked']} marked) |",
              f"| Start / end error, median of matches | {t['startAbsMed']:.2f} s / {t['endAbsMed']:.2f} s |"]
        L += event_rows(s.get("events"))
        L += [f"| Winner right on matched points (check) | {pct(t['winnerMatched'])} ± {pct(t['winnerMatchedSE'])} (n {t['n']}) |",
              f"| Winner right, end to end (check) | {pct(t['endToEnd'])} ± {pct(t['endToEndSE'])} (of {t['known']} marked winners) |",
              f"| Short points added: real | {t.get('shortReal', 0)} of {t.get('shortAdded', 0)} |"]
        if "shortMarked" in t:
            L.append(f"| Marked aces and double faults found | {t['shortMarkedFound']} of {t['shortMarked']} |")
        if "faultsMarked" in t:
            L.append(f"| First-serve faults: candidates right / marked found | {t['faultCandidatesRight']} of {t['faultCandidates']} / "
                     f"{t['faultsFound']} of {t['faultsMarked']} |")
        L += ["", "Per match:", ""] + [f"- {x}" for x in s["lines"]]
        for m, p in ((s.get("events") or {}).get("perMatch") or {}).items():
            L.append(f"- {m} events: " + "; ".join(f"{x['name']} {x['k']} of {x['n']}" if "n" in x else
                                                    f"{x['name']} {x['value']:.3g} {x['unit']}" for x in p))
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
    sets, inputs, nets = {}, {}, None
    if not a.no_lab:
        from ball_full import WINDOWS
        nets = None if a.old_nets else app_nets(list(WINDOWS) + [m for m in lab.MATCHES if m not in WINDOWS and app_folder(m)])
        sets["Lab LOMO"] = run_lab(nets)
        for mid in sets["Lab LOMO"]:
            inputs[mid] = file_hashes(lab.mdir(mid), LAB_INPUTS)
            if nets:                                   # the chain and the events read this file, not the lab file
                inputs[mid]["net_events.json"] = "app:" + sha(nets[mid])
    skipped = {}
    if not a.no_heldout:
        held, skipped = run_heldout(HERE)
        if held:
            sets["Held-out"] = held
            for mid in held:
                inputs[mid] = file_hashes(lab.mdir(mid), HELD_INPUTS)
    ev = events_all(nets)
    other =[m for m in (ev or {}) if not any(m in v for v in sets.values())]
    for mid in other:                                  # event inputs of a match outside the sets (m0913: app folder)
        inputs[mid] = ev[mid][1]
    man = {"commit": commit, "dirty": dirty, "switches": switches(), "models": model_hashes(), "inputs": inputs,
           "labels": label_hashes(), "labDevice": LAB_DEVICE, "labNets": "old lab file" if nets is None else "app way (E85)", "sets": {k: list(v) for k, v in sets.items()}, "eventsOnly": other}
    man["hash"] = hashlib.sha256(json.dumps(man, sort_keys=True, default=str).encode()).hexdigest()[:16]
    man["time"] = time.strftime("%Y-%m-%d %H:%M:%S")
    out = {k: summary(v, ev) for k, v in sets.items()}
    if other:
        out["Event labels only"] = {"total": None, "events": events_of(ev, other)}
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
    r.add_argument("--old-nets", action="store_true", help="the lab set reads the old lab net_events.json (before E85)")
    a = ap.parse_args()
    sys.path.insert(0, HERE)
    snapshot(a.ref) if a.cmd == "snapshot" else run(a)
