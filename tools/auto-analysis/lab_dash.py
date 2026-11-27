"""Lab dashboard (the user, 2026-11-27): a local page that shows the lab state with no chat.

    python tools/auto-analysis/lab_dash.py [--port 8793]

Then open http://localhost:8793/ . The server listens on 127.0.0.1 only.

Sources (read at each request, so the page is live):
- the GPU lock: heavy.lock, the queue tickets, heavy.log, pause.json (gpu_lock.py);
- the plan: docs/auto-analysis/lab-plan.json (milestones, issues, actions; the manager keeps it current);
- the labels: the fragment manifest of the label page and the label files.

Actions (POST, with the header X-Lab-Dash: 1, so another web page cannot send them):
/api/pause {"mode": "queue" | "all"} and /api/unpause, through gpu_lock.pause / gpu_lock.unpause.
Standard library only.
"""
import argparse
import glob
import importlib
import json
import os
import re
import statistics
import sys
import threading
import time
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gpu_lock  # noqa: E402
import lab_status  # noqa: E402

REPO = os.path.dirname(os.path.dirname(HERE))
DOCS = os.path.join(REPO, "docs", "auto-analysis")
PLAN = os.path.join(DOCS, "lab-plan.json")
PAGE = os.path.join(HERE, "lab_dash.html")
LABEL_PAGE = r"D:\bananashot-lab\ws\s4\events\label_events.html"
MANIFEST = r"D:\bananashot-lab\ws\s4\events\manifest.js"
FAULTS = r"D:\bananashot-lab\ws\s4\events\faults.js"
SIDES = r"D:\bananashot-lab\ws\s4\events\sides.js"
LABELS = r"D:\bananashot-lab\ws\s1\cases\labels"
MEDIUM_MIN = 75          # user time for one medium fragment (decision 2026-11-26: about 5 h for the 4)
FMT = "%Y-%m-%d %H:%M:%S"
LOG_RE = re.compile(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d) (START|END  |END|KILLED by pause|PAUSE|UNPAUSE|STALE removed)\s*(.*)$")


def now():
    return datetime.now()


def iso(t):
    return t.strftime(FMT) if t else None


def js_object(path, name):
    """The JSON value of `window.<name> = {...};` in a page data file."""
    with open(path, encoding="utf-8") as f:
        s = f.read()
    s = s[s.index("=", s.index(f"window.{name}")) + 1:].strip()
    return json.loads(s.rstrip().rstrip(";"))


# ---------------------------------------------------------------- GPU lock

def log_jobs(limit=4000):
    """Finished jobs of heavy.log: [{who, what, start, end, sec, rc, killed}], oldest first. The time is START to
    END of the same who and what (the 'Ns' of an END line includes the wait, so it is not used)."""
    try:
        with open(gpu_lock.LOG, encoding="utf-8") as f:
            lines = f.readlines()[-limit:]
    except OSError:
        return [], []
    open_jobs, done, events = {}, [], []
    for line in lines:
        m = LOG_RE.match(line.rstrip("\n"))
        if not m:
            continue
        t = datetime.strptime(m.group(1), FMT)
        kind, rest = m.group(2).strip(), m.group(3)
        if kind == "START":
            open_jobs[rest] = t
        elif kind == "END":
            mm = re.match(r"(.*) rc=(-?\d+) \d+s$", rest)
            key = mm.group(1) if mm else rest
            t0 = open_jobs.pop(key, None)
            done.append({"job": key, "start": t0, "end": t, "sec": (t - t0).total_seconds() if t0 else None,
                         "rc": int(mm.group(2)) if mm else None, "killed": False})
        elif kind == "KILLED by pause":
            key = re.sub(r" \(pid \d+\)$", "", rest)
            t0 = open_jobs.pop(key, None)
            done.append({"job": key, "start": t0, "end": t, "sec": (t - t0).total_seconds() if t0 else None,
                         "rc": None, "killed": True})
        else:
            events.append({"t": iso(t), "kind": kind, "text": rest})
    for d in done:
        who, _, what = d.pop("job").partition(": ")
        d["who"], d["what"] = who, what
    return done, events


def text_estimate(what):
    """Minutes from the job text: 'about 25 min', 'about 2 to 5 min', 'about 4.5 h', 'under 1 min'."""
    s = what.lower()
    m = re.search(r"(?:about|under|max)\s+(\d+(?:\.\d+)?)(?:\s*(?:to|-)\s*(\d+(?:\.\d+)?))?\s*(min|h)\b", s)
    if not m:
        return None
    a = float(m.group(1))
    b = float(m.group(2)) if m.group(2) else a
    v = (a + b) / 2
    return v * 60 if m.group(3) == "h" else v


def history_estimate(who, what, done):
    """Median minutes of the earlier runs of the same job text (rc 0, not killed)."""
    xs = [d["sec"] / 60 for d in done if d["what"] == what and d["sec"] and d["rc"] == 0 and not d["killed"]]
    return statistics.median(xs) if xs else None


def estimate(who, what, done):
    h = history_estimate(who, what, done)
    if h is not None:
        return h, "history"
    t = text_estimate(what)
    if t is not None:
        return t, "job text"
    return None, None


def journal_ids(text):
    return sorted(set(re.findall(r"\bE\d{2,3}[a-z]?\b", text)))


def lock_state(plan):
    done, events = log_jobs()
    action_of = {}
    for a in plan.get("actions", []):
        for j in a.get("journal", []):
            action_of.setdefault(j, a["id"])

    def link(what):
        ids = journal_ids(what)
        acts = sorted({action_of[i] for i in ids if i in action_of})
        return ids, acts

    t = now()
    pz = gpu_lock.read_pause()
    live = []
    for n in gpu_lock.pause_tickets():
        if gpu_lock.alive(int(n[len(gpu_lock.PAUSE_PREFIX):].split(".")[0])):
            try:
                with open(os.path.join(gpu_lock.QUEUE, n), encoding="utf-8") as f:
                    live.append(json.load(f))
            except (OSError, ValueError):
                live.append({})
    pause_live = bool(live)
    if pz is None and live:            # a pause ticket made without pause.json (by hand): a queue pause
        since = datetime.fromtimestamp(os.path.getmtime(os.path.join(gpu_lock.QUEUE, gpu_lock.pause_tickets()[0])))
        pz = {"mode": "queue", "since": iso(since), "by": live[0].get("who", "?"), "text": live[0].get("what"),
              "external": True}
    if pz and not pause_live and not pz.get("external"):          # the hold process died (a reboot): make the pause real again
        try:
            gpu_lock.pause(pz.get("mode", "queue"), pz.get("by", "user"))
            pause_live = True
        except Exception as e:   # noqa: BLE001 (show the problem on the page)
            pz["error"] = str(e)

    holder = None
    cur = gpu_lock.read_lock()
    cursor = t
    if cur and gpu_lock.alive(int(cur.get("pid", 0))):
        start = datetime.strptime(cur["start"], FMT)
        est, src = estimate(cur["who"], cur["what"], done)
        el = (t - start).total_seconds() / 60
        end = start + timedelta(minutes=est) if est else None
        ids, acts = link(cur["what"])
        holder = {"who": cur["who"], "what": cur["what"], "pid": cur.get("pid"), "start": cur["start"],
                  "elapsedMin": round(el, 1), "estMin": round(est, 1) if est else None, "estFrom": src,
                  "progress": min(el / est, 0.99) if est else None, "over": bool(est and el > est),
                  "eta": iso(end), "journal": ids, "actions": acts}
        cursor = max(end, t) if end else None

    queue = []
    if os.path.isdir(gpu_lock.QUEUE):
        for name in sorted(os.listdir(gpu_lock.QUEUE)):
            if name.startswith(gpu_lock.PAUSE_PREFIX):
                continue
            try:
                pid = int(name.split("_")[1].split(".")[0])
            except (IndexError, ValueError):
                continue
            if not gpu_lock.alive(pid):
                continue
            try:
                with open(os.path.join(gpu_lock.QUEUE, name), encoding="utf-8") as f:
                    tk = json.load(f)
            except (OSError, ValueError):
                continue
            est, src = estimate(tk.get("who", ""), tk.get("what", ""), done)
            since = datetime.fromtimestamp(float(name.split("_")[0]))
            ids, acts = link(tk.get("what", ""))
            row = {"who": tk.get("who"), "what": tk.get("what"), "waitingSince": iso(since),
                   "estMin": round(est, 1) if est else None, "estFrom": src, "journal": ids, "actions": acts,
                   "expStart": None, "expEnd": None}
            if pz:
                row["blocked"] = "paused"
            elif cursor is not None:
                row["expStart"] = iso(cursor)
                cursor = cursor + timedelta(minutes=est) if est else None
                row["expEnd"] = iso(cursor)
            queue.append(row)

    recent = []
    for d in reversed(done[-40:]):
        ids, acts = link(d["what"])
        recent.append({"who": d["who"], "what": d["what"], "start": iso(d["start"]), "end": iso(d["end"]),
                       "min": round(d["sec"] / 60, 1) if d["sec"] is not None else None, "rc": d["rc"],
                       "killed": d["killed"], "journal": ids, "actions": acts})
    killed_last = None
    if pz:
        ks = [r for r in recent if r["killed"] and r["end"] and r["end"] >= pz.get("since", "")]
        killed_last = ks[0] if ks else None
    return {"pause": pz, "pauseLive": pause_live, "holder": holder, "queue": queue, "recent": recent,
            "events": events[-10:], "killedByPause": killed_last}


# ---------------------------------------------------------------- labels

def label_state():
    out = {"page": LABEL_PAGE, "folder": LABELS}
    try:
        frags = js_object(MANIFEST, "CASES")["fragments"]
    except (OSError, ValueError) as e:
        out["error"] = f"manifest: {e}"
        return out
    by_level = {}
    secs, rows = [], []
    for fr in frags:
        lvl = fr.get("level", "?")
        c = by_level.setdefault(lvl, {"total": 0, "done": 0, "skipped": 0, "stale": 0, "mapped": 0, "pending": []})
        c["total"] += 1
        p = os.path.join(LABELS, f"{fr['id']}.events.json")
        state = "pending"
        if os.path.isfile(p):
            try:
                with open(p, encoding="utf-8") as f:
                    j = json.load(f)
                if (j.get("manifest") or {}).get("row") != fr.get("rowHash"):
                    state = "stale"
                elif j.get("skip"):
                    state = "skipped"
                else:
                    state = "done"
                    if j.get("labelSeconds") and lvl == "small":
                        secs.append(float(j["labelSeconds"]))
                rows.append({"id": fr["id"], "state": state, "events": len(j.get("events") or []),
                             "sec": j.get("labelSeconds"), "saved": j.get("savedAt")})
            except (OSError, ValueError):
                state = "pending"
        if state == "done":
            c["done"] += 1
        elif state == "skipped":
            c["skipped"] += 1
        else:
            if state == "stale":
                c["stale"] += 1
            if os.path.isfile(os.path.join(LABELS, f"{fr['id']}.mapped.json")):
                c["mapped"] += 1
            c["pending"].append(fr["id"])
    med = statistics.median(secs) / 60 if secs else None
    left = 0.0
    for lvl, c in by_level.items():
        c["leftMin"] = round(len(c["pending"]) * (MEDIUM_MIN if lvl == "medium" else (med or 5)))
        left += c["leftMin"]
    out.update({"levels": by_level, "minPerSmall": round(med, 1) if med else None, "minPerMedium": MEDIUM_MIN,
                "leftMin": round(left), "labeledMin": round(sum(secs) / 60), "recent": sorted(
                    rows, key=lambda r: r.get("saved") or "", reverse=True)[:6]})
    # the Faults step and the Sides step
    try:
        fm = js_object(FAULTS, "FAULTS")["matches"]
        total = sum(len(m["rows"]) for m in fm)
        answered = 0
        for m in fm:
            fp = os.path.join(LABELS, f"faults_{m['match']}.json")
            if os.path.isfile(fp):
                with open(fp, encoding="utf-8") as f:
                    a = json.load(f)
                hashes = {r["rowHash"] for r in m["rows"]}
                answered += sum(1 for x in a.get("answers", []) if x.get("rowHash") in hashes)
        out["faults"] = {"rows": total, "answered": answered, "estMin": 8}
    except (OSError, ValueError, KeyError) as e:
        out["faults"] = {"error": str(e)}
    try:
        sm = js_object(SIDES, "SIDES")["matches"]
        done_m = sum(1 for m in sm if os.path.isfile(os.path.join(LABELS, f"sides_{m['match']}.json")))
        out["sides"] = {"matches": len(sm), "done": done_m, "estMin": 12}
    except (OSError, ValueError, KeyError) as e:
        out["sides"] = {"error": str(e)}
    return out


# ---------------------------------------------------------------- plan and workers

def load_plan():
    try:
        with open(PLAN, encoding="utf-8") as f:
            plan = json.load(f)
        plan["_mtime"] = iso(datetime.fromtimestamp(os.path.getmtime(PLAN)))
        return plan
    except (OSError, ValueError) as e:
        return {"error": f"lab-plan.json: {e}", "milestones": [], "actions": [], "workers": []}


def parse_time(s):
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            return datetime.strptime(s, fmt)
        except (TypeError, ValueError):
            pass
    return None


def workers_state(plan, lock, labels):
    """For each worker: its open actions in order, with the net time and a finish estimate (the actions one after
    the other from now; 'after' waits for the finish of another action, 'notBefore' for a date). A GPU job can also
    wait in the lock queue: the estimate does not include that wait."""
    out = []
    eta_of = {}
    for _ in range(2):                 # pass 2 uses the finish times of the other workers ('after')
        out = workers_pass(plan, lock, labels, eta_of)
    return out


def workers_pass(plan, lock, labels, eta_of):
    t = now()
    acts = plan.get("actions", [])
    out = []
    for w in plan.get("workers", []):
        recent = t - timedelta(hours=DONE_HOURS)
        mine = [a for a in acts if a.get("owner") == w["id"] and (a.get("status") in
                ("running", "queued", "waiting", "planned") or (a.get("status") in ("done", "dropped")
                and (parse_time(a.get("finished")) or datetime.min) >= recent))]
        order = {"running": 0, "waiting": 1, "queued": 2, "planned": 3, "done": 4, "dropped": 4}
        mine.sort(key=lambda a: order[a["status"]])
        cursor, net_total, rows = t, 0.0, []
        for a in mine:
            est = (a.get("estimate") or {}).get("netMin")
            lvl = (a.get("estimate") or {}).get("fromLabels")
            if lvl:
                est = ((labels.get("levels") or {}).get(lvl if isinstance(lvl, str) else "small") or {}).get("leftMin", est)
            if a["status"] in ("done", "dropped"):
                rows.append({"id": a["id"], "title": a["title"], "kind": a["kind"], "status": a["status"],
                             "finished": a.get("finished"), "real": a.get("real"), "note": a.get("note"),
                             "unplanned": a.get("unplanned")})
                continue
            begin = cursor
            nb = parse_time(a.get("notBefore"))
            if nb and nb > begin:
                begin = nb
            for dep in a.get("after", []):
                d = eta_of.get(dep)
                if d and d > begin:
                    begin = d
            if a["status"] == "running" and a.get("started") and est:
                end = max(parse_time(a["started"]) + timedelta(minutes=est), t)
            else:
                end = begin + timedelta(minutes=est) if est else None
            if end:
                eta_of[a["id"]] = end
            if est:
                net_total += est
            rows.append({"id": a["id"], "title": a["title"], "kind": a["kind"], "status": a["status"],
                         "netMin": est, "eta": iso(end), "waitingFor": a.get("waitingFor"),
                         "waitingOn": waiting_on(a) if a["status"] == "waiting" else None,
                         "jobs": a.get("jobs", []), "note": a.get("note"), "unplanned": a.get("unplanned"),
                         "live": a.get("live"), "doneWhen": a.get("doneWhen") or []})
            if end:
                cursor = end
        gpu = [q for q in lock["queue"] if q["who"] and q["who"].lower() == w["id"].lower()]
        if lock["holder"] and lock["holder"]["who"].lower() == w["id"].lower():
            gpu.insert(0, dict(lock["holder"], running=True))
        out.append({"id": w["id"], "name": w["name"], "focus": w.get("focus"), "actions": rows,
                    "netMin": round(net_total), "gpu": gpu})
    return out


LIVE_FIELDS = ("status", "waitingFor", "waitingOn", "note", "started", "finished", "outcome")
DONE_HOURS = 3        # a finished action stays on its worker card for this time ("Done recently")


def waiting_on(a):
    """Who must act for a waiting action: the live field, else a guess from the text (plan entries)."""
    if a.get("waitingOn"):
        return a["waitingOn"]
    txt = (a.get("waitingFor") or "").lower()
    # the unpause is a step of the user (only the user pauses and unpauses the lab queue)
    for key, who in (("manager", "manager"), ("user", "user"), ("unpause", "user"), ("queue", "queue"),
                     ("gpu", "queue")):
        if key in txt:
            return who
    m = re.search(r"\bs([1-5])\b", txt)
    return f"S{m.group(1)}" if m else None


def apply_live(plan, live):
    """Puts the live status of the streams (lab_status.py) over the plan actions. A live entry that is newer than
    the plan file wins. An action id that is not in the plan becomes an unplanned action of its worker."""
    plan_t = parse_time(plan.get("_mtime", "").replace("T", " ")[:19])
    acts = {a["id"]: a for a in plan.get("actions", [])}
    for aid, e in live["actions"].items():
        t = parse_time(e.get("t"))
        a = acts.get(aid)
        if a is None:
            a = {"id": aid, "title": e.get("title") or aid, "kind": "work", "owner": e.get("who"),
                 "status": e.get("status"), "unplanned": True}
        if e.get("doneWhen"):
            a["doneWhen"] = [e["doneWhen"]] if isinstance(e["doneWhen"], str) else e["doneWhen"]
            plan.setdefault("actions", []).append(a)
            acts[aid] = a
        elif plan_t and t and t < plan_t and a.get("status") in ("done", "dropped"):
            continue                       # the manager closed it in the plan after the last live entry
        for k in LIVE_FIELDS:
            if k in e:
                a[k] = e[k]
        if e.get("status") != "waiting":
            a.pop("waitingFor", None)
            a.pop("waitingOn", None)
        if e.get("status") not in ("done", "dropped"):
            a.pop("finished", None)
        if "note" not in e:
            a.pop("note", None)
        if e.get("netMin") is not None:
            a["estimate"] = {"netMin": e["netMin"]}
        if e.get("real") is not None:
            a["real"] = dict(a.get("real") or {}, impact=e["real"], outcome=e.get("outcome"))
        a["live"] = {"t": e.get("t"), "status": e.get("status"), "note": e.get("note")}


def current_line(e):
    """The worker line from one log entry: {"state": working|waiting|idle|finished, "text"}."""
    if e.get("kind") == "idle":
        return {"state": "idle", "text": "no work", "t": e.get("t")}
    if e.get("kind") == "now":
        return {"state": "working", "text": e.get("text"), "t": e.get("t")}
    st, aid = e.get("status"), e.get("action")
    if st == "running":
        return {"state": "working", "text": f"{aid}" + (f": {e['note']}" if e.get("note") else ""), "t": e.get("t")}
    if st == "waiting":
        return {"state": "waiting", "text": f"{aid} waits for {e.get('waitingOn') or '?'}: {e.get('waitingFor')}",
                "t": e.get("t")}
    if st in ("done", "dropped"):
        return {"state": "finished", "text": f"{aid} {st}" + (f": {e['outcome']}" if e.get("outcome") else ""),
                "t": e.get("t")}
    return {"state": "idle", "text": f"{aid} {st}", "t": e.get("t")}


def state():
    plan = load_plan()
    live = lab_status.latest()
    apply_live(plan, live)
    lock = lock_state(plan)
    labels = label_state()
    workers = workers_state(plan, lock, labels)
    for w in workers:
        hist = live.get("history", {}).get(w["id"], [])
        w["current"] = current_line(hist[-1]) if hist else None
        w["history"] = [dict(current_line(e), t=e["t"]) for e in reversed(hist[:-1])][:4]
        w["lastUpdate"] = hist[-1]["t"] if hist else None
        ses = live.get("sessions", {}).get(w["id"])
        w["session"] = {"state": ses.get("state"), "t": ses.get("t")} if ses else None
        # actions of others that wait for this worker (the user or the manager): they must act
        w["waitsForMe"] = [{"id": a["id"], "title": a["title"], "owner": a.get("owner"),
                            "waitingFor": a.get("waitingFor")}
                           for a in plan.get("actions", []) if a.get("status") == "waiting"
                           and a.get("owner") != w["id"] and waiting_on(a) == w["id"]]
    return {"now": iso(now()), "lock": lock, "labels": labels, "plan": plan, "workers": workers}


# ---------------------------------------------------------------- server

DOC_OK = re.compile(r"^[A-Za-z0-9_.-]+\.(md|json)$")


# ---------------------------------------------------------------- live code reload
# The user (2026-11-27): the page must show all updates with no manual refresh. So the server loads changed code of
# this file, lab_status.py and gpu_lock.py at the next request (no restart), and the page reloads itself when
# lab_dash.html changes (pageVersion). The HTTP handler itself stays as it was at the start.
_CODE = [os.path.join(HERE, n) for n in ("gpu_lock.py", "lab_status.py", "lab_dash.py")]
_core = {"mod": None, "stamp": None, "error": None}
_core_lock = threading.Lock()


def code_stamp():
    return tuple(os.path.getmtime(f) if os.path.isfile(f) else 0 for f in _CODE)


def core():
    """The module with the newest code. A file with an error keeps the last good code; state() names the error."""
    with _core_lock:
        stamp = code_stamp()
        if _core["mod"] is None or stamp != _core["stamp"]:
            try:
                importlib.reload(gpu_lock)
                importlib.reload(lab_status)
                if _core["mod"] is None:
                    import lab_dash as mod   # this file as a module (not __main__), so a reload is possible
                else:
                    mod = importlib.reload(_core["mod"])
                _core["mod"], _core["error"] = mod, None
            except Exception as e:  # noqa: BLE001 (the page shows it)
                _core["error"] = f"code reload failed: {type(e).__name__}: {e}"
                if _core["mod"] is None:
                    _core["mod"] = sys.modules[__name__]
            _core["stamp"] = stamp
        return _core["mod"], _core["error"]


def live_state():
    mod, err = core()
    s = mod.state()
    s["pageVersion"] = str(os.path.getmtime(PAGE))
    if err:
        s["codeError"] = err
    return s


# ---------------------------------------------------------------- HTTP (the routes reload with the code)
def route_get(h, path):
    if path == "/":
        with open(PAGE, "rb") as f:
            return h._send(200, f.read(), "text/html; charset=utf-8")
    if path == "/api/state":
        return h._send(200, json.dumps(live_state(), default=str))
    if path == "/api/plan":
        with open(PLAN, "rb") as f:
            return h._send(200, f.read())
    if path == "/log":
        with open(gpu_lock.LOG, encoding="utf-8") as f:
            return h._send(200, "".join(f.readlines()[-300:]), "text/plain; charset=utf-8")
    if path.startswith("/doc/"):
        name = path[5:]
        if DOC_OK.match(name) and os.path.isfile(os.path.join(DOCS, name)):
            with open(os.path.join(DOCS, name), "rb") as f:
                return h._send(200, f.read(), "text/plain; charset=utf-8")
    return h._send(404, "not found", "text/plain")


def route_post(h, path, body):
    if path == "/api/pause":
        mode = body.get("mode")
        if mode not in ("queue", "all"):
            return h._send(400, json.dumps({"error": "mode must be queue or all"}))
        return h._send(200, json.dumps({"result": gpu_lock.pause(mode, "user (dashboard)")}))
    if path == "/api/unpause":
        return h._send(200, json.dumps({"result": gpu_lock.unpause("user (dashboard)")}))
    return h._send(404, json.dumps({"error": "not found"}))


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):   # quiet
        pass

    def do_GET(self):
        try:
            return core()[0].route_get(self, self.path.split("?")[0])
        except Exception as e:  # noqa: BLE001 (the page shows it)
            return self._send(500, json.dumps({"error": f"{type(e).__name__}: {e}"}))

    def do_POST(self):
        if self.headers.get("X-Lab-Dash") != "1":
            return self._send(403, json.dumps({"error": "missing X-Lab-Dash header"}))
        n = int(self.headers.get("Content-Length") or 0)
        try:
            body = json.loads(self.rfile.read(n) or b"{}")
        except ValueError:
            body = {}
        try:
            return core()[0].route_post(self, self.path.split("?")[0], body if isinstance(body, dict) else {})
        except Exception as e:  # noqa: BLE001
            return self._send(500, json.dumps({"error": f"{type(e).__name__}: {e}"}))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8793)
    a = ap.parse_args()
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), Handler)
    print(f"lab dashboard: http://localhost:{a.port}/", flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
