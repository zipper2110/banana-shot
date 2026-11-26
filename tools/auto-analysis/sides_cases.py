"""Input of the sides step of the label page (gap program S4 item 11): the candidate points and their frames.

The reference of "who is near" (ends.json nearPlayer) comes from the end-change rule, and players do not always follow
it. The user marks the near player at the start and after each possible end change. This script finds the candidate
points of each singles video with point marks (no NONE point) and takes two frames of each candidate from the source video:
  - the first point of the match;
  - each point where the rule (ends.json nearPlayer) changes;
  - each point where the chain identity (p1Near of the chain rows, by the largest time overlap) changes;
  - about 5 random points as controls (a fixed seed for each match);
  - the extra points of EXTRA (cases of a known doubt).
The page does not show which source proposed a candidate: sides.js has no source. The sources go to
<out>/../sides/candidates_<match>.json for the agents.

Frames (ffmpeg seek, one frame each, scaled to 1920 wide): "mid" = the middle of the candidate point (the first rally
after the change), "serve" = point start + 1 s.

Usage:
  python sides_cases.py <rows folder> <page folder> [match ...]
    rows folder: <match>_rows.json (lab, {"rallies": [...]}) or <match>_result.json (app result), rows with start,
    end (s) and p1Near.
  The page folder gets sides.js and sides/<match>/*.jpg. Open label_events.html#sides in that folder.
"""
import json, os, random, shutil, subprocess, sys
import lab

MATCHES = ["m1015", "m1020", "m1021", "m1030", "m1116"]
EXTRA = {"m1021": [4569.07, 4601.13, 4733.89]}      # start times (s): points after a long break (manager, item 11)
N_CONTROL = 5
FRAME_W = 1920


def project_names(mid):
    m = lab.MATCHES[mid]
    root = os.path.expandvars(lab.CFG["projectsRoot"].replace("%APPDATA%", "${APPDATA}"))
    s = json.load(open(os.path.join(root, m["project"], "score.json"), encoding="utf-8"))
    return [s["player1Name"], s["player2Name"]]


def chain_rows(rows_dir, mid):
    for name in (f"{mid}_rows.json", f"{mid}_result.json"):
        p = os.path.join(rows_dir, name)
        if os.path.exists(p):
            return [r for r in json.load(open(p, encoding="utf-8"))["rallies"] if r.get("p1Near") is not None]
    raise FileNotFoundError(f"no chain rows of {mid} in {rows_dir}")


def chain_near(points, rows):
    """p1Near of the chain row with the largest time overlap with each point, or None."""
    out = []
    for p in points:
        a, b = p["startMs"] / 1000, p["endMs"] / 1000
        best = max(rows, key=lambda r: min(b, r["end"]) - max(a, r["start"]), default=None)
        out.append(best["p1Near"] if best and min(b, best["end"]) - max(a, best["start"]) > 0 else None)
    return out


def candidates(mid, rows_dir):
    pts = sorted(json.load(open(os.path.join(lab.mdir(mid), "gt.json")))["points"], key=lambda p: p["startMs"])
    ends = {e["id"]: e for e in json.load(open(os.path.join(lab.mdir(mid), "ends.json")))}
    rule = [ends.get(p["id"], {}).get("nearPlayer") for p in pts]
    chain = chain_near(pts, chain_rows(rows_dir, mid))
    # NONE points are out (the user, 2026-11-26): a change is between two real points, and no candidate is a NONE point
    real = [i for i, p in enumerate(pts) if p.get("outcome") != "NONE"]
    src = {real[0]: {"start"}}
    for a, b in zip(real[:-1], real[1:]):
        if rule[b] != rule[a]:
            src.setdefault(b, set()).add("rule")
    last = None
    for i in real:
        if chain[i] is None:
            continue
        if last is not None and chain[i] != last:
            src.setdefault(i, set()).add("chain")
        last = chain[i]
    for t in EXTRA.get(mid, []):
        i = min(real, key=lambda k: abs(pts[k]["startMs"] / 1000 - t))
        src.setdefault(i, set()).add("extra")
    rest = [i for i in real if i not in src]
    for i in random.Random(mid).sample(rest, min(N_CONTROL, len(rest))):
        src.setdefault(i, set()).add("control")
    return pts, rule, chain, src


def grab(video, t, path):
    if os.path.exists(path):
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{t:.3f}", "-i", video, "-an", "-frames:v", "1",
                    "-vf", f"scale={FRAME_W}:-2", "-q:v", "3", "-y", path], check=True)


def main(rows_dir, out, mids):
    doc = {"version": 1, "created": None, "matches": []}
    for mid in mids:
        m = lab.MATCHES[mid]
        pts, rule, chain, src = candidates(mid, rows_dir)
        items, agent = [], []
        for i in sorted(src):
            p = pts[i]
            a, b = p["startMs"] / 1000, p["endMs"] / 1000
            fr = {"mid": round((a + b) / 2, 3), "serve": round(min(a + 1.0, b), 3)}
            files = {}
            for k, t in fr.items():
                rel = f"sides/{mid}/{i + 1:03d}_{k}.jpg"
                grab(m["video"], t, os.path.join(out, rel))
                files[k] = rel
            items.append({"point": p["id"], "pointNo": i + 1, "startMs": p["startMs"], "endMs": p["endMs"],
                          "t": fr["mid"], "tServe": fr["serve"], "frames": [files["mid"], files["serve"]]})
            agent.append({"point": p["id"], "pointNo": i + 1, "startMs": p["startMs"], "sources": sorted(src[i]),
                          "rule": rule[i], "chainP1Near": chain[i]})
        doc["matches"].append({"match": mid, "project": m["project"], "players": project_names(mid),
                               "testOnly": bool(m.get("testOnly")), "points": len(pts), "items": items})
        side = os.path.join(os.path.dirname(os.path.abspath(out)), "sides")
        os.makedirs(side, exist_ok=True)
        json.dump({"match": mid, "rowsDir": rows_dir, "candidates": agent},
                  open(os.path.join(side, f"candidates_{mid}.json"), "w"), indent=1)
        n = {s: sum(s in x for x in src.values()) for s in ("start", "rule", "chain", "extra", "control")}
        print(f"{mid}: {len(items)} candidates of {len(pts)} points {n}")
    from datetime import datetime, timezone
    doc["created"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    with open(os.path.join(out, "sides.js"), "w", encoding="utf-8") as f:
        f.write("window.SIDES = " + json.dumps(doc, ensure_ascii=False) + ";\n")
    shutil.copy2(os.path.join(os.path.dirname(os.path.abspath(__file__)), "label_events.html"), os.path.join(out, "label_events.html"))


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) < 2:
        print(__doc__)
    else:
        main(a[0], os.path.abspath(a[1]), a[2:] or MATCHES)
