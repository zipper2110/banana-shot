"""A39 (S2, E202): adopt the flights and netband keys of a detect.py work folder after the key rule of A39.

A39 changed the key formula of these stages: the code part is now the files of code_closure.reach (the modules that
the run can use), not of code_closure.modules (the full import closure). So the recorded key of each folder is not
the key of now, and detect.py would run the flights and the net band again, also when no file that the run uses has
changed.

Proof for a stage of a folder: a code version V (a commit of this folder's git history, or a code folder) exists for
which both are true:
- the key of the formula before A39 (code_closure.modules), with the code of V and the inputs of the folder, is the
  recorded key (so the recorded output was made by the code of V or by code with the same import closure);
- the key of the A39 formula (code_closure.reach), with the code of V, is the key of now (so each file that the run
  can use has the same bytes in V and now).
Then the output is the output of the code of now, and the tool records the key of now. It changes only
stage_keys.json (the old file stays as stage_keys.a39.json) and no output file. The netband part of the flights key
must be the adopted (or current) netband key.

Usage: python adopt_reach.py [--dry] [--versions N] <work folder> ...    (N: the newest code commits to try; 200)"""
import json, os, shutil, subprocess, sys, tarfile, tempfile, time
import code_closure, detect

HERE = os.path.dirname(os.path.abspath(__file__))
STAGES = ("netband", "flights")


def git_versions(root_dir, n=200):
    """[(commit, folder)] of the newest n commits that change a .py file of this folder. Each folder is a copy of the
    .py files of that commit in root_dir (the caller deletes root_dir)."""
    root = subprocess.run(["git", "-C", HERE, "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                          check=True).stdout.strip()
    rel = os.path.relpath(HERE, root).replace("\\", "/")
    # item 13 (the first commit with code_closure.py): an older key has a hand list, not the import closure
    first = subprocess.run(["git", "-C", root, "log", "--diff-filter=A", "--format=%H", "--", rel + "/code_closure.py"],
                           capture_output=True, text=True, check=True).stdout.split()[-1]
    shas = subprocess.run(["git", "-C", root, "rev-list", "-n", str(n), "HEAD", "^" + first + "^", "--", rel + "/*.py"],
                          capture_output=True, text=True, check=True).stdout.split()
    out = []
    for sha in shas:
        d = os.path.join(root_dir, sha[:12])
        tar = subprocess.run(["git", "-C", root, "archive", "--format=tar", sha, "--", rel + "/*.py"],
                             capture_output=True, check=True).stdout
        t = d + ".tar"
        open(t, "wb").write(tar)
        with tarfile.open(t) as tf:
            tf.extractall(d, filter="data")
        os.remove(t)
        out.append((sha[:8], os.path.join(d, *rel.split("/"))))
    return out


_PARTS = {}


def code_parts(folder):
    """{stage: (code files before A39, code files of A39)} of the code in folder."""
    if folder in _PARTS:
        return _PARTS[folder]
    out = _PARTS[folder] = {}
    for st, entries in (("netband", detect.STAGE_ENTRIES["netband"]), ("flights", detect.FLIGHTS_ENTRIES)):
        if not all(os.path.exists(os.path.join(folder, m + ".py")) for m in entries):
            out[st] = None                            # an entry module is missing: no proof with this version
            continue
        out[st] = ([os.path.join(folder, m + ".py") for m in code_closure.modules(*entries, folder=folder)],
                   code_closure.files(*entries, folder=folder))
    return out


def changed(files_v, files_now):
    """The names of the A39 key files that differ between a version and now (or are only in one of them)."""
    a = {os.path.basename(f): f for f in files_v}
    b = {os.path.basename(f): f for f in files_now}
    rd = lambda f: open(f, "rb").read().replace(bytes([13, 10]), bytes([10]))
    return sorted(n for n in set(a) | set(b) if n not in a or n not in b or rd(a[n]) != rd(b[n]))


def court_track(work):
    """The candidates of cfg["courtTrack"] (a part of the flights key): the value of result.json, and the default."""
    c = [""]
    try:
        v = json.load(open(os.path.join(work, "result.json"))).get("settings", {}).get("courtTrack")
        if isinstance(v, str) and v not in c:
            c.insert(0, v)
    except (OSError, ValueError, AttributeError):
        pass
    return c


def split_flights(rec, netband):
    """(ball tag, code key, netband part or None) of a recorded flights key, or None if it has a netband part that
    is not the recorded netband key."""
    nb = None
    if "_nb" in rec:
        if not netband or not rec.endswith("_nb" + netband):
            return None
        rec, nb = rec[:-len("_nb" + netband)], netband
    if len(rec) < 9 or rec[-9] != "_":
        return None
    return rec[:-9], rec[-8:], nb


def plan(work, versions, now_code=HERE):
    """{stage: {"state": current|adopt|stale|none, ...}} for the folder work. versions: iterable of (name, code folder);
    now_code: the code of now (a test can give another folder)."""
    keys = json.load(open(os.path.join(work, "stage_keys.json")))
    court = [os.path.join(work, f) for f in ("court_track.json", "court_track_refined.json")]
    now = code_parts(now_code)
    out, todo = {}, {}
    nb_rec, mask = keys.get("netband"), keys.get("netmask")
    if not nb_rec:
        out["netband"] = {"state": "none"}
    elif not mask or not nb_rec.startswith(mask + "_"):
        out["netband"] = {"state": "stale", "why": "the netband key does not start with the netmask key"}
    else:
        h = nb_rec[len(mask) + 1:]
        new = detect.content_key(files=now["netband"][1])
        if h == new:
            out["netband"] = {"state": "current"}
        else:
            todo["netband"] = (lambda fs, h=h: detect.content_key(files=fs) == h, lambda fs, new=new: detect.content_key(files=fs) == new,
                               mask + "_" + new)
    fl = keys.get("flights")
    parts = split_flights(fl, nb_rec) if fl else None
    if not fl:
        out["flights"] = {"state": "none"}
    elif parts is None:
        out["flights"] = {"state": "stale", "why": "the netband part is not the recorded netband key"}
    else:
        tag, h, nb = parts
        for c in court_track(work):
            new = detect.content_key(c, files=court + now["flights"][1])
            if h == new:
                out["flights"] = {"state": "current"}
                break
            todo.setdefault("flights", []).append((c, new))
    made = {}
    if todo:
        for name, folder in versions:
            v = code_parts(folder)
            if "netband" in todo and "netband" not in out and v["netband"]:
                old_ok, new_ok, key = todo["netband"]
                if old_ok(v["netband"][0]) and new_ok(v["netband"][1]):
                    out["netband"] = {"state": "adopt", "from": nb_rec, "to": key, "proof": name}
            if "flights" in todo and "flights" not in out and v["flights"]:
                for c, new in todo["flights"]:
                    if detect.content_key(c, files=court + v["flights"][0]) == h and \
                            detect.content_key(c, files=court + v["flights"][1]) == new:
                        out["flights"] = {"state": "adopt", "from": fl, "new": new, "proof": name, "courtTrack": c}
                        break
            for st in todo:                              # the diagnosis: the version that made the key, if any
                if st in out or st in made or not v[st]:
                    continue
                if st == "netband" and todo[st][0](v[st][0]) or st == "flights" and any(
                        detect.content_key(c, files=court + v[st][0]) == h for c, _ in todo[st]):
                    made[st] = (name, changed(v[st][1], now[st][1]))
            if all(s in out for s in todo):
                break
    for s in STAGES:
        if s in made:
            out.setdefault(s, {"state": "stale", "why": f"made by {made[s][0]}; changed since then: {', '.join(made[s][1])}"})
        out.setdefault(s, {"state": "stale", "why": "no code version of the history made the recorded key"})
    f = out["flights"]
    if f["state"] == "adopt":
        tag, _, nb = parts
        if nb is not None:
            nb_now = out["netband"].get("to") if out["netband"]["state"] == "adopt" else nb_rec
            if out["netband"]["state"] not in ("adopt", "current"):
                out["flights"] = {"state": "stale", "why": "its netband key is not adopted"}
                return out
            f["to"] = tag + "_" + f.pop("new") + "_nb" + nb_now
        else:
            f["to"] = tag + "_" + f.pop("new")
    return out


def apply(work, out):
    """Writes the adopted keys into stage_keys.json (a copy of the old file: stage_keys.a39.json)."""
    p = os.path.join(work, "stage_keys.json")
    keys = json.load(open(p))
    done = {s: o for s, o in out.items() if o["state"] == "adopt"}
    if not done:
        return False
    back = os.path.join(work, "stage_keys.a39.json")
    if not os.path.exists(back):
        shutil.copy2(p, back)
    for s, o in done.items():
        assert keys[s] == o["from"]
        keys[s] = o["to"]
    keys["keysAdoptedA39"] = dict({s: {"from": o["from"], "proof": o["proof"]} for s, o in done.items()},
                                  time=time.strftime("%Y-%m-%dT%H:%M:%S"))
    json.dump(keys, open(p, "w"), indent=1)
    return True


def plan_parts(work, versions, now_code=HERE):
    """A45 (E204): the part keys of the flights stage (detect.FLIGHT_PARTS) for a folder with one flights key of
    before A45 that is not the key of now. Proof: a code version V made the recorded one key (the formula before
    A39 or the A39 formula), and the A45 key files of a part (code_closure.reach of its entry module) have the same
    bytes in V and now. A part is adopted only if each part before it is adopted; the net events part also needs
    the netband part of the one key: the recorded netband key, or the key that E203 adopted from it.
    Returns {"parts": {part: key of now}, "run": [parts], "proof": V or None, "why": text}."""
    keys = json.load(open(os.path.join(work, "stage_keys.json")))
    names = [p for p, _, _ in detect.FLIGHT_PARTS]
    fl, mask = keys.get("flights"), keys.get("netmask")
    out = {"parts": {}, "run": names, "proof": None, "why": ""}
    if "netevents" in keys or not fl or not mask:
        out["why"] = "no one flights key of before A45 (or no netmask key)"
        return out
    tag = mask[:-9]
    if not fl.startswith(tag + "_") or len(fl) < len(tag) + 9:
        out["why"] = "the flights key does not have the ball tag of the netmask key"
        return out
    h, rest = fl[len(tag) + 1:len(tag) + 9], fl[len(tag) + 9:]
    nb_rec = rest[3:] if rest.startswith("_nb") else None
    nb_now = keys.get("netband")
    adopted_nb = keys.get("keysAdoptedA39", {}).get("netband", {}).get("from")
    nb_ok = nb_rec is None or nb_rec == nb_now or (adopted_nb == nb_rec and nb_now is not None)
    if nb_now is not None and nb_now != mask + "_" + detect.content_key(
            files=code_closure.files(*detect.STAGE_ENTRIES["netband"], folder=now_code)):
        nb_ok = False                                   # the net band runs again: so do the net events
    court = [os.path.join(work, f) for f in ("court_track.json", "court_track_refined.json")]
    for c in court_track(work):
        for name, folder in versions:
            v = code_parts(folder)
            if not v["flights"] or not any(detect.content_key(c, files=court + fs) == h for fs in v["flights"]):
                continue
            out["proof"], same = name, []
            for p, entry, _ in detect.FLIGHT_PARTS:
                diff = changed(code_closure.files(entry, folder=folder), code_closure.files(entry, folder=now_code))
                if diff or (p == "netevents" and not nb_ok):
                    out["why"] = f"made by {name}; {p}: " + (", ".join(diff) + " changed" if diff else "another netband key")
                    break
                same.append(p)
            now = detect.flight_keys(tag, c, court, nb_now, folder=now_code)
            out["parts"] = {p: now[p] for p in same}
            out["run"] = [p for p in names if p not in same]
            return out
    out["why"] = "no code version of the history made the recorded key"
    return out


def apply_parts(work, out):
    """Writes the adopted part keys into stage_keys.json (a copy of the old file: stage_keys.a45.json)."""
    if not out["parts"]:
        return False
    p = os.path.join(work, "stage_keys.json")
    back = os.path.join(work, "stage_keys.a45.json")
    if not os.path.exists(back):
        shutil.copy2(p, back)
    keys = json.load(open(p))
    keys["flightsSplit"] = {"from": keys.get("flights"), "proof": out["proof"], "adopted": sorted(out["parts"]),
                            "time": time.strftime("%Y-%m-%dT%H:%M:%S")}
    keys.update(out["parts"])
    json.dump(keys, open(p, "w"), indent=1)
    return True


def main(args):
    dry = "--dry" in args
    parts = "--parts" in args
    n = 200
    if "--versions" in args:
        n = int(args[args.index("--versions") + 1])
        args = args[:args.index("--versions")] + args[args.index("--versions") + 2:]
    works = [a for a in args if a not in ("--dry", "--parts")]
    tmp = tempfile.mkdtemp(prefix="ar_")
    try:
        versions = git_versions(tmp, n)
        print(len(versions), "code versions:", versions[0][0] if versions else "-", "to", versions[-1][0] if versions else "-")
        plans = {w: (plan_parts if parts else plan)(w, versions) for w in works}
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    for w, out in plans.items():
        print(w)
        if parts:
            print("  ", json.dumps(out))
            if not dry and apply_parts(w, out):
                print("   written")
            continue
        for s in STAGES:
            print("  ", s, json.dumps(out[s]))
        if not dry and apply(w, out):
            print("   written")
    return plans


if __name__ == "__main__":
    main(sys.argv[1:])
