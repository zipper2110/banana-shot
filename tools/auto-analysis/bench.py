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
manifest.json, rows.json (the chain rows of each match). The manifest has the code commit, the switches, and a hash of each model and input file. A second run
with the same manifest must give the same numbers: the report says if an earlier run had the same manifest.

Run the benchmark from a snapshot of one commit, not from the main checkout (other streams merge into it):
  python bench.py snapshot [--ref <commit or branch>]   exports the code to <lab>/ws/s1/code/<sha>/
  python <lab>/ws/s1/code/<sha>/tools/auto-analysis/bench.py run [--tag <text>] [--no-heldout]
A heavy stage against its own labels, under the GPU lock (item 7a; report <lab>/bench/<time>-<commit>-heavy-ball/):
  python <lab>/ws/s1/code/<sha>/tools/auto-analysis/bench.py heavy --stage ball [--weights held-out] [--passes dim]
The stream name on the GPU lock tickets: --who <stream> (before the command), else the env var BENCH_WHO, else "bench".
The net band of the lab matches (S5 item 6 (d), netBand on): env BENCH_NETBAND_DIR=<folder> reads <folder>/<match>/net_band.npz
(for example <lab>/ws/s5/nb). The band file is then an input of the lab flights key and of the manifest. Not set: no band.
The manifest and the report show the band state ("netBand": "off", or the folder and the hash of each band file).
"""
import argparse, glob, hashlib, json, math, os, re, subprocess, sys, time
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
WHO = os.environ.get("BENCH_WHO") or "bench"     # the stream name on the GPU lock tickets (--who overrides it)
# The folder of the net band files of the lab matches (<folder>/<match>/net_band.npz, S5 item 6 (d)); "" = no band.
# Not a switch name in the manifest: when set, the band files are inputs (key and manifest), so a run with no band
# keeps the hash of the runs before the band.
NETBAND_DIR = os.environ.get("BENCH_NETBAND_DIR") or ""
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
    names -= {"APPDATA", "LOCALAPPDATA", "USERPROFILE", "HOME", "HF_HOME", "PATH", "TEMP", "TMP", "HEAVY_LOCK_DIR",
               "BENCH_WHO", "BENCH_NETBAND_DIR"}    # a lock ticket name and the band folder (an input when set): not switches
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
# (tag _e27). Item 6b (manager, 2026-11-26): the lab set also makes its flights again (ball_full.run) from these blobs
# when the key of the flights code changes: the files of the G12 flights key of detect.py, and the switches that
# these files read. Thus a change of the bounce or flight code reaches the lab bounce rows. CPU only, about 15 to
# 25 min for each match, the 4 matches in parallel; the output is cached under the key in <lab>/ws/s1/bench/flights.
# "--frozen-flights": the E38 flights (fullflights_s0.35_ev.json), and only the net events are made again (about
# 1 min for each match; cache <lab>/bench/nets). The fits (shot_chain, rally_end) keep the old files: the shipped
# models were fit on them.
LAB_BLOB_TAG = "_e27"
NET_CODE = ("net_events.py", "net_lab.py", "ball_events.py", "lab.py")
FLIGHT_CODE = ("ball_full.py", "ball_3d.py", "ball_bounce.py", "ball_events.py", "ball_tracks.py", "net_events.py",
               "net_lab.py", "feat_wasb.py", "frame_times.py", "court_refine.py", "lab.py")
FLIGHT_ENV = ("COURT_TRACK", "FAR_FAULT_M", "SELECT_TRIM", "TURN", "LEN_TIME", "BALL_TAG", "BLOB_TAG", "BOUNCE_SAME_TRACK")
NET_COPY = ["pts.npy", "kf_times.csv", "court.json", "court_track.json", "court_track_refined.json"]
LAB_FLIGHTS = "remake"                              # or "frozen" (--frozen-flights)


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
        frozen = ["fullflights_s0.35_ev.json"] if LAB_FLIGHTS == "frozen" else []
        return "lab", [os.path.join(lab.mdir(mid), f) for f in NET_COPY + frozen +
                       ["wasb_%d_%d_s0.35%s_k2.npy" % (*WINDOWS[mid], LAB_BLOB_TAG)]] +             ([band_file(mid)] if NETBAND_DIR else [])
    d = app_folder(mid)
    blobs = glob.glob(os.path.join(d, "wasb_0_*_s0.35_k2.npy"))
    return "app", [os.path.join(d, f) for f in NET_COPY + ["pts_video.npy", "fullflights_s0.35.json"]] + \
        ([max(blobs, key=os.path.getmtime)] if blobs else [])


def band_file(mid):
    """The net band file of lab match mid in NETBAND_DIR, or None when no band folder is set."""
    return os.path.join(NETBAND_DIR, mid, "net_band.npz") if NETBAND_DIR else None


def band_in(dst):
    """The net band file in the cache folder dst of app_nets, or None. It is there only when NETBAND_DIR was set."""
    f = os.path.join(dst, "net_band.npz")
    return f if os.path.exists(f) else None


def net_key(m, code_dir):
    """(kind, input files, flights made again, cache folder) of match m in app_nets. The key is the code files and the
    inputs of the match; with NETBAND_DIR set, the net band file of a lab match is one of the inputs."""
    import lab
    kind, src = net_inputs(m)
    if kind == "lab" and NETBAND_DIR and not os.path.exists(band_file(m)):
        raise FileNotFoundError(f"BENCH_NETBAND_DIR is set, but {band_file(m)} is missing")
    src = [f for f in src if os.path.exists(f)]
    flights = kind == "lab" and LAB_FLIGHTS == "remake"
    h = hashlib.sha256()
    for f in code_files(code_dir, flights) + src:
        h.update(os.path.basename(f).encode() + sha(f).encode())
    if flights:
        h.update(json.dumps({k: os.environ.get(k) for k in FLIGHT_ENV}, sort_keys=True).encode())
        root = os.path.join(os.path.dirname(lab.ROOT), "ws", "s1", "bench", "flights")
    else:
        root = os.path.join(os.path.dirname(lab.ROOT), "bench", "nets")
    return kind, src, flights, os.path.join(root, h.hexdigest()[:16], f"{kind}_{m}")   # not "<match>": see net_lab below


def code_files(code_dir, flights=True):
    """The code files of the lab flights key (flights=True) or of the net events key: the import closure of the
    entry modules of detect.py (code_closure.py, S2 item 13; the flights stage: ball_full, net_events, feat_wasb),
    read in code_dir, and lab.py. A snapshot without code_closure.py (before item 13) uses the old hand lists."""
    if os.path.exists(os.path.join(code_dir, "code_closure.py")):
        import importlib.util
        spec = importlib.util.spec_from_file_location("_closure_" + hashlib.sha1(code_dir.encode()).hexdigest()[:8],
                                                      os.path.join(code_dir, "code_closure.py"))
        cc = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cc)
        entries = ("ball_full", "net_events", "feat_wasb") if flights else ("net_events",)
        fs = cc.files(*entries, folder=code_dir)
        lab_py = os.path.join(code_dir, "lab.py")
        return sorted(set(fs) | {lab_py}, key=os.path.basename)
    return [os.path.join(code_dir, n) for n in (FLIGHT_CODE if flights else NET_CODE)]


def app_nets(mids, code_dir=None):
    """{mid: path of net_events.json made the app way} for lab matches and app folders. The folder of each file also
    has the flights that the chain and the events read (fullflights_s0.35.json). Reads the match folders only: the
    work is in a cache folder for each match, with a key of the code and of the inputs of that match. A lab match
    with LAB_FLIGHTS "remake": the flights too (key FLIGHT_CODE, cache <lab>/ws/s1/bench/flights/<key>/), else only
    the net events (<lab>/bench/nets/<key>/). code_dir: the code of the flights and the net events (default: this
    code; another snapshot for the null check)."""
    import lab, shutil
    code_dir = code_dir or HERE
    out, todo, remake = {}, [], []
    for m in mids:
        kind, src, flights, dst = net_key(m, code_dir)
        out[m] = os.path.join(dst, "net_events.json")
        if not os.path.exists(out[m]):
            os.makedirs(dst, exist_ok=True)
            for f in src:
                n = os.path.basename(f)
                shutil.copy2(f, os.path.join(dst, "fullflights_s0.35.json" if n == "fullflights_s0.35_ev.json" else n))
            (remake if flights else todo).append((m, kind, dst))
    if remake:
        # One CPU process for each match: ball_full.run takes about 15 to 25 min for each lab match. The processes
        # use all cores and much memory, so they run under the GPU lock (manager, 2026-11-26): a GPU job next to
        # them can fail.
        import gpu_lock
        env = dict(os.environ, CUDA_VISIBLE_DEVICES="-1")
        who, what = WHO, f"bench.py: lab flights by code key, {len(remake)} CPU processes (about 25 min)"
        t, procs, bad = time.time(), [], []
        gpu_lock.acquire(who, what)
        try:
            for m, kind, dst in remake:
                log = open(os.path.join(dst, "make.log"), "w")
                p = subprocess.Popen([sys.executable, os.path.abspath(__file__), "flights1", m, dst, "--code", code_dir],
                                     stdout=log, stderr=subprocess.STDOUT, env=env)
                procs.append((m, dst, log, p))
            for m, dst, log, p in procs:
                if p.wait() != 0 or not os.path.exists(os.path.join(dst, "net_events.json")):
                    bad.append(f"{m} (see {os.path.join(dst, 'make.log')})")
                log.close()
        finally:
            for m, dst, log, p in procs:              # a stop of this process also stops the children
                if p.poll() is None:
                    p.kill()
            gpu_lock.release(who, what, 1 if bad else 0, t)
        if bad:
            raise RuntimeError("the lab flights failed: " + ", ".join(bad))
        print(f"lab flights and net events made by the code key: {', '.join(m for m, _, _ in remake)} "
              f"({time.time() - t:.0f} s)", flush=True)
    if todo:
        import net_events
        tag, mdir, cwd = lab.BLOB_TAG, lab.mdir, os.getcwd()
        try:
            for m, kind, dst in todo:
                t = time.time()
                if kind == "lab":
                    # net_lab.Source takes an existing folder with the name of the match as an app folder: work in
                    # the parent of dst, which has no such folder. The lab branch reads lab.mdir and lab.BLOB_TAG.
                    lab.BLOB_TAG = LAB_BLOB_TAG
                    os.chdir(os.path.dirname(dst))
                    lab.mdir = lambda x, _o=mdir, m=m, dst=dst: dst if x == m else _o(x)
                    b = band_in(dst)                   # with no band: the call of the runs before the band
                    path, ev = net_events.run_folder(m, band_file=b) if b else net_events.run_folder(m)
                else:
                    lab.BLOB_TAG = ""                  # the app branch: the newest ball file with no tag
                    path, ev = net_events.run_folder(dst)
                lab.BLOB_TAG, lab.mdir = tag, mdir
                print(f"net events of {m} made the app way: {len(ev)} ({time.time() - t:.0f} s)", flush=True)
        finally:
            lab.BLOB_TAG, lab.mdir = tag, mdir
            os.chdir(cwd)
    return out


def flights1(mid, dst):
    """One lab match of app_nets in its own process: the flights (ball_full.run) and the net events from the copies of
    the frozen lab inputs in dst. The modules come from sys.path (the code of the key)."""
    import lab, ball_full, net_events
    lab.BLOB_TAG = LAB_BLOB_TAG
    os.chdir(os.path.dirname(dst))                    # net_lab.Source: no folder with the bare match name here
    mdir = lab.mdir
    lab.mdir = lambda x: dst if x == mid else mdir(x)
    t = time.time()
    ball_full.run(mid)
    b = band_in(dst)                                  # with no band: the call of the runs before the band
    path, ev = net_events.run_folder(mid, band_file=b) if b else net_events.run_folder(mid)
    print(f"{mid}: flights and {len(ev)} net events in {time.time() - t:.0f} s (code {os.path.dirname(ball_full.__file__)})", flush=True)


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
    pipeline.FLIGHT_FILES.clear()                    # the flights in the folder of each net file (app_nets)
    pipeline.FLIGHT_FILES.update({m: os.path.join(os.path.dirname(f), "fullflights_s0.35.json") for m, f in (nets or {}).items()})
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
    who, what = WHO, f"bench.py: rally and winner stages of {mid} (heavy stages refused)"
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


# G13 (S2 f294b4a4): the faults that the chain survived (the old rule decided). Lab: pipeline.run puts {kind: count}
# in the metrics ("chainFaults"). Held-out: detect.py writes chain.fault_texts() to the warnings of result.json.
FAULT_TEXT = re.compile(r"^The (.+?) failed on (\d+) rall")


def heldout_faults(warnings):
    """{kind: count} of the chain faults in the warnings of result.json ({} = no fault)."""
    return {m.group(1): int(m.group(2)) for w in warnings for m in [FAULT_TEXT.match(str(w))] if m}


def fault_rows(per):
    """The bold report rows of the matches with chain faults (per: perMatch of a set)."""
    return [f"| **Chain faults (the old rule decided)** | **{mid}: " + ", ".join(f"{k} {c}" for k, c in m["chainFaults"].items())
            + "** |" for mid, m in per.items() if m.get("chainFaults")]


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
        result = json.load(open(os.path.join(d, "result.json")))
        rows = result["rallies"]
        m = score.score(mid, rows)
        m["chainFaults"] = heldout_faults(result.get("warnings") or [])
        res[mid] = (m, rows)
    return res, skipped


def sides_file(mid):
    import lab
    return os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "ws", "s1", "cases", "labels", f"sides_{mid}.json")


def sided_ends(mid):
    """ends.json with the near player from the user's side marks (S4 item 11, the user, 2026-11-26), or None when the
    match has no marks. At a marked point the near player is the mark. Between two marks, the near player follows the
    changes of the rule (nearPlayer of ends.json), shifted by the difference at the last mark; before the first mark,
    the shift of the first mark. A mark with near null or "unsure" is not used. winnerNear is then outcome == near."""
    import lab
    f = sides_file(mid)
    if not os.path.exists(f):
        return None
    marks = {m["point"]: m["near"] for m in json.load(open(f, encoding="utf-8"))["marks"]
             if m.get("near") in ("P1", "P2") and not m.get("unsure")}
    ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
    outcome = {p["id"]: p["outcome"] for p in json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))["points"]}
    if not marks:
        return None
    other = {"P1": "P2", "P2": "P1"}
    first = next(e for e in ends if e["id"] in marks)
    flip = first.get("nearPlayer") is not None and first["nearPlayer"] != marks[first["id"]]
    out = []
    for e in ends:
        if e["id"] in marks:
            near, src = marks[e["id"]], "mark"
            if e.get("nearPlayer") is not None:
                flip = e["nearPlayer"] != near
        else:
            r = e.get("nearPlayer")
            near, src = (other[r] if flip else r) if r else None, "rule shifted" if flip else "rule"
        o = outcome.get(e["id"])
        out.append(dict(e, nearPlayer=near, nearFrom=src, winnerNear=(o == near) if o in ("P1", "P2") and near else None))
    return out


SHOW_WITH_NONE = True       # the old numbers (NONE points counted) once more in the report (manager, 2026-11-26)


def faults_file(mid):
    """The user's answers of the Faults step of the label page (S4 E158): faults_<match>.json in the label folder."""
    return os.path.join(os.path.dirname(sides_file(mid)), f"faults_{mid}.json")


def fault_answers(mid):
    """{row hash: answer F, P, N or Q} of faults_<match>.json, or None when there is no file. The answers in "stale"
    (changed or removed rows) are not used; an answer counts only for a row with the same row hash."""
    f = faults_file(mid)
    if not os.path.exists(f):
        return None
    d = json.load(open(f, encoding="utf-8"))
    if d.get("kind") != "bananashot-faults" or d.get("version") != 1 or d.get("match") != mid:
        raise RuntimeError(f"{f}: not a version 1 faults file of {mid}")
    return {a["rowHash"]: a["answer"] for a in d.get("answers") or [] if a.get("answer") in ("F", "P", "N", "Q")}


def no_none(mid, rows, m):
    """The rally measures without the NONE points (the user, 2026-11-26): a marked point with the outcome NONE is not a
    missed rally, and a detected rally on a NONE point (the match rule of lab.match_points) is not a false rally. The
    numbers with the NONE points stay in m as "<key>WithNone". Returns a new dict."""
    import lab
    import chain
    gt = lab.gt_points(mid)
    pairs = lab.match_points([(r["start"], r["end"]) for r in rows], gt)
    none = {gi for gi, g in enumerate(gt) if g[2] == "NONE"}
    real = [(gi, j) for gi, j in pairs if gi not in none]
    on_none = {j for gi, j in pairs if gi in none}
    # First-serve faults (the user, gap-program e912f7e2: a point starts at the first serve; the detection writes a
    # first-serve fault as a fault clip). Lab marks (no "faults" in gt.json) start at the second serve: a row that
    # matches no marked point, overlaps none, and ends FAULT_NEXT s or less before the next marked point (not NONE)
    # is "fault, not checked", not a false rally. Where gt.json has fault clips (Igor), a row of this kind on a marked
    # fault clip (+-1 s) is a fault ("faultClipRows"); the other rows stay false rallies.
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))
    clips = [(f["startMs"] / 1000 - 1.0, f["endMs"] / 1000 + 1.0) for f in g.get("faults", [])]
    used = {j for _, j in pairs}
    starts = sorted(x[0] for x in gt if x[2] != "NONE")
    fault_nc, fault_clip = [], []
    for j, r in enumerate(rows):
        if j in used or any(r["end"] > x[0] and r["start"] < x[1] for x in gt):
            continue
        if "faults" in g:
            if any(r["end"] > a and r["start"] < b for a, b in clips):
                fault_clip.append(j)
            continue
        nxt = next((t for t in starts if t >= r["end"]), None)
        if nxt is not None and nxt - r["end"] <= chain.FAULT_NEXT:
            fault_nc.append(j)
    # The user's answers of the Faults step (S4 E158, faults_<match>.json): F = a fault clip (as faultClipRows),
    # P = a found point with a missing mark (one more marked and matched point), N = a false rally, Q or no answer =
    # "not checked" as before. No file: the numbers of before.
    answers = fault_answers(mid) if "faults" not in g else None
    fa, points_p = None, 0
    if answers is not None:
        import faults_cases
        marks = [(p, x) for p, x in zip(g["points"], gt) if x[2] != "NONE"]
        fa, keep = {"F": 0, "P": 0, "N": 0, "Q": 0, "none": 0}, []
        for j in fault_nc:
            r = rows[j]
            nxt = next(p for p, x in marks if x[0] >= r["end"])     # the next marked point (faults_cases.fault_rows)
            a = answers.get(faults_cases.row_hash(mid, r["start"], r["end"], nxt["id"]))
            fa[a or "none"] += 1
            if a == "F":
                fault_clip.append(j)
            elif a not in ("P", "N"):
                keep.append(j)
        points_p, fault_nc = fa["P"], keep
    marked, detected = len(gt) - len(none) + points_p, len(rows) - len(on_none) - len(fault_nc) - len(fault_clip)
    ds = [rows[j]["start"] - gt[gi][0] for gi, j in real]
    de = [rows[j]["end"] - gt[gi][1] for gi, j in real]
    out = dict(m, **{k + "WithNone": m[k] for k in ("marked", "detected", "matched", "recall", "precision", "startAbsMed", "endAbsMed")})
    if fa is not None:                                 # the numbers with no answers, for the report row
        out.update(faultAnswers=fa, markedNoAnswers=marked - points_p,
                   detectedNoAnswers=detected - fa["P"] - fa["N"], matchedNoAnswers=len(real))
    out.update(marked=marked, detected=detected, matched=len(real) + points_p, recall=(len(real) + points_p) / max(1, marked),
               precision=(len(real) + points_p) / max(1, detected), noneMarked=len(none), noneDetected=len(on_none),
               faultNotChecked=len(fault_nc), faultClipRows=len(fault_clip),
               faultNotCheckedShots=[rows[j].get("shots") for j in fault_nc],
               precisionWithFaults=(len(real) + points_p) / max(1, detected + len(fault_nc) + len(fault_clip)),
               # chain shots (len(row["chain"]["shots"]), the count of chain.FAULT_SHOTS and S3 E145); "shots" of a row counts
               # the good flights in the row
               fault3Shots=sum(len((rows[j].get("chain") or {}).get("shots") or []) > chain.FAULT_SHOTS for j in fault_nc),
               fault3Flights=sum((rows[j].get("shots") or 0) >= 3 for j in fault_nc),
               startAbsMed=float(np.median(np.abs(ds))) if ds else None, endAbsMed=float(np.median(np.abs(de))) if de else None)
    return out


def summary(res, ev=None):
    import score
    res = {mid: (no_none(mid, rows, m), rows) for mid, (m, rows) in res.items()}
    ms = [m for m, _ in res.values()]
    n, ok, kn = (sum(m[k] for m in ms) for k in ("n", "ok", "known"))
    wr = [x for _, rows in res.values() for x in winner_rows(rows)]
    ex = {mid: extras(mid, rows) for mid, (_, rows) in res.items()}
    tot = {"matches": list(res), "marked": sum(m["marked"] for m in ms), "detected": sum(m["detected"] for m in ms),
           "recall": sum(m["matched"] for m in ms) / max(1, sum(m["marked"] for m in ms)),
           "precision": sum(m["matched"] for m in ms) / max(1, sum(m["detected"] for m in ms)),
           "winnerMatched": ok / max(1, n), "winnerMatchedSE": se(ok / max(1, n), n),
           "endToEnd": ok / max(1, kn), "endToEndSE": se(ok / max(1, kn), kn), "n": n, "known": kn,
           "startAbsMed": float(np.median([m["startAbsMed"] for m in ms if m["startAbsMed"] is not None] or [np.nan])),
           "endAbsMed": float(np.median([m["endAbsMed"] for m in ms if m["endAbsMed"] is not None] or [np.nan])),
           "noneMarked": sum(m["noneMarked"] for m in ms), "noneDetected": sum(m["noneDetected"] for m in ms),
           "faultNotChecked": sum(m["faultNotChecked"] for m in ms), "faultClipRows": sum(m["faultClipRows"] for m in ms),
           "faultNotChecked3Shots": sum(m["fault3Shots"] for m in ms),
           "faultNotChecked3Flights": sum(m["fault3Flights"] for m in ms),
           # the manager: rows with 3 shots or more are not proven faults; the second number hides only the short rows
           "precisionShortFaults": sum(m["matched"] for m in ms) / max(1, sum(m["detected"] + m["fault3Shots"] for m in ms)),
           "precisionWithFaults": sum(m["matched"] for m in ms) / max(1, sum(m["detected"] + m["faultNotChecked"] + m["faultClipRows"] for m in ms)),
           "markedWithNone": sum(m["markedWithNone"] for m in ms), "detectedWithNone": sum(m["detectedWithNone"] for m in ms),
           "recallWithNone": sum(m["matchedWithNone"] for m in ms) / max(1, sum(m["markedWithNone"] for m in ms)),
           "precisionWithNone": sum(m["matchedWithNone"] for m in ms) / max(1, sum(m["detectedWithNone"] for m in ms))}
    am = [m for m in ms if "faultAnswers" in m]
    if am:                                             # the user's fault answers (S4 E158): counts, and the numbers without them
        def no_ans(m, k):
            return m.get(k + "NoAnswers", m[k])
        tot["faultAnswers"] = {k: sum(m["faultAnswers"][k] for m in am) for k in ("F", "P", "N", "Q", "none")}
        tot["faultAnswerMatches"] = [mid for mid, (m, _) in res.items() if "faultAnswers" in m]
        tot["recallNoAnswers"] = sum(no_ans(m, "matched") for m in ms) / max(1, sum(no_ans(m, "marked") for m in ms))
        tot["precisionNoAnswers"] = sum(no_ans(m, "matched") for m in ms) / max(1, sum(no_ans(m, "detected") for m in ms))
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
         + (f" Lab net events: {man['labNets']}." if man.get("labNets") else "")
         + (f" Lab flights: {man['labFlights']}." if man.get("labFlights") else "")
         + (f" Net band of the lab matches: {man['netBand']['folder']} (files "
            + ", ".join(f"{m} {h}" for m, h in man["netBand"]["files"].items()) + ")."
            if isinstance(man.get("netBand"), dict) else " Net band: off." if man.get("netBand") == "off" else "")
         + (" Near player from the user's side marks: " + ", ".join(f"{m} ({v['marks']} marks, {v['changed']} points shifted)"
                                                                   for m, v in man["sides"].items()) + "." if man.get("sides") else ""),
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
              (f"| Rallies: recall / precision (NONE points out; fault rows hidden: none / only 2 chain shots or less / all) | {pct(t['recall'])} / "
               f"{pct(t['precisionWithFaults'])} / {pct(t['precisionShortFaults'])} / {pct(t['precision'])} ({t['detected']} detected, {t['marked']} marked; "
               f"{t.get('noneMarked', 0)} NONE points, {t.get('noneDetected', 0)} detected rallies on them; hidden fault rows "
               f"{t['faultNotChecked'] + t['faultClipRows']}: 3 or more chain shots {t['faultNotChecked3Shots']}, "
               f"3 or more good flights {t['faultNotChecked3Flights']}) |"
               if "precisionShortFaults" in t else
               f"| Rallies: recall / precision (NONE points out) | {pct(t['recall'])} / {pct(t['precision'])} ({t['detected']} detected, "
               f"{t['marked']} marked; {t.get('noneMarked', 0)} NONE points, {t.get('noneDetected', 0)} detected rallies on them) |")]
        L += fault_rows(s.get("perMatch") or {})
        if "faultAnswers" in t:
            fa = t["faultAnswers"]
            L += [f"| Fault rows answered by the user ({', '.join(t['faultAnswerMatches'])}) | F {fa['F']} (fault clips), P {fa['P']} "
                  f"(points with no mark), N {fa['N']} (false rallies), Q {fa['Q']}, no answer {fa['none']}; recall / precision with "
                  f"the answers {pct(t['recall'])} / {pct(t['precision'])}, without them {pct(t['recallNoAnswers'])} / "
                  f"{pct(t['precisionNoAnswers'])} |"]
        if t.get("faultNotChecked") or t.get("faultClipRows"):
            L += [f"| Rows left out as first-serve faults | {t.get('faultNotChecked', 0)} not checked (lab marks start at the second "
                  f"serve; {t.get('faultNotChecked3Shots', 0)} of them with 3 chain shots or more), {t.get('faultClipRows', 0)} on a marked "
                  f"fault clip; precision with them as false rallies {pct(t.get('precisionWithFaults'))} |"]
        if SHOW_WITH_NONE and "recallWithNone" in t:
            L += [f"| Rallies with the NONE points (the old way) | {pct(t['recallWithNone'])} / {pct(t['precisionWithNone'])} "
                  f"({t['detectedWithNone']} detected, {t['markedWithNone']} marked) |"]
        L += [
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


# ---- heavy stages (item 7a): the ball detector against the hand labels of the ball (E27a) ----------------------------
BALL_LABELS = os.path.join(HERE, "ball_labels_e27.json")
BALL_FOLD = {"m1021": "e38_f1.pth", "m1020": "e38_f2.pth", "m1015": "e38_f3.pth", "m1030": "e38_f4.pth"}   # E38 folds
BALL_SHIPPED = "ball_e38.pth"
BALL_SETUPS = {"dim": ["dim"], "one": ["one"], "crop": []}   # dim: detect.py now; one: one pass, full crop; crop: old crop
M0913W = {"video": r"D:\PXL_20260913_1_Vlad.mp4", "folder": r"D:\bananashot-lab\e27\work"}   # the E27 work copy of m0913


def ball_score(win, blobs):
    """E27a rule (D:/bananashot-lab/e27/ev.py): one dict for each labeled frame that is not unsure or dead. A ball label
    (s "v" seen, "h" hidden) is found when a blob is within max(10, 0.6 * blur length) px; each other blob of the frame is
    a false blob. blobs: rows (label frame, x, y, score, peak)."""
    by = {}
    for b in blobs:
        by.setdefault(int(b[0]), []).append(b)
    out = []
    for lb in win["frames"]:
        if lb["s"] == "u" or lb.get("dead"):
            continue
        bs = by.get(lb["k"], [])
        if lb["s"] in "vh":
            e = [float(np.hypot(b[1] - lb["x"], b[2] - lb["y"])) for b in bs]
            ok = [x for x in e if x <= max(10.0, 0.6 * lb.get("len", 0))]
            out.append({"ball": True, "hit": bool(ok), "fp": len(e) - len(ok), "err": min(ok) if ok else None,
                        "wasbLabel": lb.get("src", "").startswith("wasb")})
        else:
            out.append({"ball": False, "hit": False, "fp": len(bs), "err": None, "wasbLabel": False})
    return out


def ball_groups(rows):
    """Pooled measures for each group of windows. The frames of a window are not independent: the standard error is
    too small."""
    groups = [("m0913 noted", lambda r: r["mid"] == "m0913w" and r["set"] == "noted"),
              ("m0913 other", lambda r: r["mid"] == "m0913w" and r["set"] == "other")]
    groups += [(m, (lambda m: lambda r: r["mid"] == m)(m)) for m in BALL_FOLD] + [("lab all", lambda r: r["set"] == "lab")]
    out = {}
    for name, g in groups:
        sel = [r for r in rows if g(r)]
        if not sel:
            continue
        n, vis, hit, fp = (sum(r[k] for r in sel) for k in ("n", "vis", "hit", "fp"))
        err = np.array([e for r in sel for e in r["errs"]])
        out[name] = {"windows": len(sel), "frames": n, "ballFrames": vis, "found": hit, "recall": hit / max(1, vis),
                     "recallSe": se(hit / max(1, vis), vis), "falsePerFrame": fp / max(1, n),
                     "errMed": float(np.median(err)) if len(err) else None,
                     "errP90": float(np.percentile(err, 90)) if len(err) else None,
                     "weights": sorted({r["weights"] for r in sel}), "inTraining": any(r["inTraining"] for r in sel),
                     "sec": round(sum(r["sec"] for r in sel), 1)}
    return out


def heavy_ball(a):
    """bench.py heavy --stage ball: ball_wasb.run (the code of detect.py) on each window of the ball hand labels, under the
    GPU lock. Weights "held-out": each lab match with the E38 fold that did not train on it, m0913 with ball_e38.pth (no
    training saw m0913); else one weights file for all windows. The lab windows run at stride 2 (60 fps), m0913 at
    30 fps and stride 1, as detect.py runs a 30 fps video."""
    import torch  # noqa: F401  (before ONNX Runtime: the rule of the lab)
    import lab, platform_cfg, gpu_lock
    t0 = time.time()
    commit, dirty = code_commit()
    lab.MATCHES["m0913w"] = {"id": "m0913w", "video": M0913W["video"]}
    mdir = lab.mdir
    lab.mdir = lambda m: M0913W["folder"] if m == "m0913w" else mdir(m)
    import ball_wasb
    wins = json.load(open(a.labels, encoding="utf-8"))["windows"]
    wdir = os.path.join(os.path.dirname(platform_cfg.weights_dir()), "weights")

    def weights_of(mid):
        if a.weights != "held-out":
            return a.weights
        return BALL_FOLD.get(mid, BALL_SHIPPED)
    used = sorted({weights_of(w["mid"]) for w in wins})
    man = {"stage": "ball", "commit": commit, "dirty": dirty, "setup": a.passes, "weightsRule": a.weights,
           "weights": {f: sha(os.path.join(wdir, f)) for f in used}, "labels": sha(a.labels),
           "videos": {m: lab.MATCHES[m]["video"] for m in sorted({w["mid"] for w in wins})},
           "device": str(platform_cfg.device()), "decode": platform_cfg.decode()}
    man["hash"] = hashlib.sha256(json.dumps(man, sort_keys=True, default=str).encode()).hexdigest()[:16]
    who, what = WHO, f"bench.py heavy ball: {len(wins)} label windows, setup {a.passes}, weights {a.weights} (about 5 min)"
    rows, nets, rc = [], {}, 1
    gpu_lock.acquire(who, what)
    tl = time.time()
    try:
        for w in wins:
            wt = weights_of(w["mid"])
            if wt not in nets:
                ball_wasb.WEIGHTS = wt
                nets[wt] = ball_wasb.load_model()
            ball_wasb.PASSES = list(BALL_SETUPS[a.passes])
            ball_wasb.FPS = 30 if w["mid"] == "m0913w" else None
            t = time.time()
            try:
                b = ball_wasb.run(w["mid"], w["start"], w["dur"], stride=w["step"], save=False, net=nets[wt], quiet=True)
            finally:
                ball_wasb.PASSES, ball_wasb.FPS = [], None
            if w["step"] == 2:                       # blob frames of 1/60 s -> label frames of 1/30 s
                b = b[b[:, 0] % 2 == 0].copy()
                b[:, 0] /= 2
            sc = ball_score(w, b)
            vis = [r for r in sc if r["ball"]]
            rows.append({"mid": w["mid"], "start": w["start"], "dur": w["dur"], "set": w["set"], "weights": wt,
                         "inTraining": w["mid"] in BALL_FOLD and wt != BALL_FOLD[w["mid"]],
                         "n": len(sc), "vis": len(vis), "hit": sum(r["hit"] for r in vis), "fp": sum(r["fp"] for r in sc),
                         "errs": [r["err"] for r in vis if r["hit"] and not r["wasbLabel"]],
                         "hits": [int(r["hit"]) for r in vis], "sec": round(time.time() - t, 2)})
            print(f"{w['mid']} {w['start']}+{w['dur']}s {wt}: found {rows[-1]['hit']} of {rows[-1]['vis']}, "
                  f"false {rows[-1]['fp']} in {rows[-1]['n']} frames ({rows[-1]['sec']} s)", flush=True)
        rc = 0
    finally:
        gpu_lock.release(who, what, rc, tl)
    out = ball_groups(rows)
    root = os.path.join(os.path.dirname(lab.ROOT), "bench")
    d = os.path.join(root, time.strftime("%Y%m%d-%H%M%S") + "-" + commit[:8] + "-heavy-ball" + (f"-{a.tag}" if a.tag else ""))
    os.makedirs(d)
    man["time"] = time.strftime("%Y-%m-%d %H:%M:%S")
    json.dump(man, open(os.path.join(d, "manifest.json"), "w"), indent=1, default=str)
    json.dump(out, open(os.path.join(d, "metrics.json"), "w"), indent=1, default=float)
    with open(os.path.join(d, "rows.jsonl"), "w") as f:
        for r in rows:
            f.write(json.dumps(r, default=float) + "\n")
    L = [f"# Heavy stage: ball, {man['time']}", "",
         f"Code: `{commit[:12]}`{' (dirty)' if dirty else ''}. Setup: {a.passes}. Weights: {a.weights} "
         f"({', '.join(used)}). Labels: `{os.path.basename(a.labels)}` (E27a). Manifest `{man['hash']}`.", "",
         "Ball found (recall) = labeled ball frames (seen or hidden) with a blob near the label; false blobs = the other "
         "blobs, for each labeled frame. Position error: labels that do not come from a WASB blob. The frames of a window "
         "are not independent: the ± is too small.", "",
         "| Group | Windows | Ball found (recall) | False blobs per frame | Position error, median / p90 | Weights |",
         "|---|---|---|---|---|---|"]
    for k, v in out.items():
        e = f"{v['errMed']:.1f} / {v['errP90']:.1f} px" if v["errMed"] is not None else "-"
        L.append(f"| {k} | {v['windows']} | {pct(v['recall'])} ± {100 * v['recallSe']:.1f} % ({v['found']} of {v['ballFrames']}) | "
                 f"{v['falsePerFrame']:.2f} | {e} | {', '.join(v['weights'])}{' (in training)' if v['inTraining'] else ''} |")
    L += ["", f"Time: {time.time() - tl:.0f} s under the lock, model and decode {sum(r['sec'] for r in rows):.0f} s."]
    txt = "\n".join(L) + "\n"
    open(os.path.join(d, "report.md"), "w", encoding="utf-8").write(txt)
    print(txt)
    print(f"written: {d} ({time.time() - t0:.0f} s)")


def run(a):
    import lab
    t0 = time.time()
    commit, dirty = code_commit()
    sets, inputs, nets = {}, {}, None
    import score
    sides = {}                                         # the user's side marks: the near player of the winner rows
    for mid in lab.MATCHES:
        e = sided_ends(mid)
        if e:
            score.ENDS[mid] = e
            sides[mid] = {"file": sha(sides_file(mid)), "marks": sum(x["nearFrom"] == "mark" for x in e),
                          "changed": sum(x["nearFrom"] == "rule shifted" for x in e)}
    if not a.no_lab:
        from ball_full import WINDOWS
        nets = None if a.old_nets else app_nets(list(WINDOWS) + [m for m in lab.MATCHES if m not in WINDOWS and app_folder(m)])
        sets["Lab LOMO"] = run_lab(nets)
        for mid in sets["Lab LOMO"]:
            inputs[mid] = file_hashes(lab.mdir(mid), LAB_INPUTS)
            if nets:                                   # the chain and the events read these files, not the lab files
                inputs[mid]["net_events.json"] = "app:" + sha(nets[mid])
                inputs[mid]["fullflights_s0.35_ev.json"] = LAB_FLIGHTS + ":" + sha(os.path.join(os.path.dirname(nets[mid]), "fullflights_s0.35.json"))
                b = band_in(os.path.dirname(nets[mid]))
                if b:                                  # only with BENCH_NETBAND_DIR (the band state: man["netBand"])
                    inputs[mid]["net_band.npz"] = "band:" + sha(b)
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
           "labels": label_hashes(), "sides": sides, "labDevice": LAB_DEVICE, "labNets": "old lab file" if nets is None else "app way (E85)",
           "labFlights": "old lab file" if nets is None else {"remake": "made again by the code key (item 6b)", "frozen": "E38 file"}[LAB_FLIGHTS], "sets": {k: list(v) for k, v in sets.items()}, "eventsOnly": other}
    answer_files = {m: sha(faults_file(m)) for m in lab.MATCHES if os.path.exists(faults_file(m))}
    if answer_files:                                   # only with answers: a run with no answer file keeps its manifest
        man["faultAnswerFiles"] = answer_files
    # The band state: "off", or the folder and the hash of the band file of each lab match.
    man["netBand"] = {"folder": NETBAND_DIR, "files": {m: v["net_band.npz"][5:] for m, v in inputs.items()
                                                       if "net_band.npz" in v}} if NETBAND_DIR and nets else "off"
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
    # The chain rows of each match (start, end, p1Near, winner, ...), for the other streams (S4 item 11).
    json.dump({k: {mid: rows for mid, (_, rows) in v.items()} for k, v in sets.items()},
              open(os.path.join(d, "rows.json"), "w"), default=float)
    txt = report(man, out, skipped, same)
    open(os.path.join(d, "report.md"), "w", encoding="utf-8").write(txt)
    print(txt)
    print(f"written: {d} ({time.time() - t0:.0f} s)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--who", default=WHO, help="the stream name on the GPU lock tickets (default: env BENCH_WHO, else bench)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("snapshot")
    s.add_argument("--ref", default="HEAD")
    r = sub.add_parser("run")
    r.add_argument("--tag", default="")
    r.add_argument("--no-heldout", action="store_true")
    r.add_argument("--no-lab", action="store_true")
    r.add_argument("--old-nets", action="store_true", help="the lab set reads the old lab net_events.json (before E85)")
    r.add_argument("--frozen-flights", action="store_true", help="the lab set reads the E38 flights; only the net events are made again")
    h = sub.add_parser("heavy", help="a heavy stage against its labels, under the GPU lock (item 7a: ball)")
    h.add_argument("--stage", choices=["ball"], required=True)
    h.add_argument("--weights", default="held-out", help="held-out (E38 folds; m0913: ball_e38.pth) or a weights file for all windows")
    h.add_argument("--passes", choices=list(BALL_SETUPS), default="dim", help="dim: the setup of detect.py now")
    h.add_argument("--labels", default=BALL_LABELS)
    h.add_argument("--tag", default="")
    f = sub.add_parser("flights1")                    # internal: one lab match of app_nets in its own process
    f.add_argument("mid"); f.add_argument("dst"); f.add_argument("--code", default=HERE)
    a = ap.parse_args()
    WHO = a.who
    sys.path.insert(0, a.code if a.cmd == "flights1" else HERE)
    if a.cmd == "snapshot":
        snapshot(a.ref)
    elif a.cmd == "flights1":
        flights1(a.mid, a.dst)
    elif a.cmd == "heavy":
        heavy_ball(a)
    else:
        LAB_FLIGHTS = "frozen" if a.frozen_flights else "remake"
        run(a)
