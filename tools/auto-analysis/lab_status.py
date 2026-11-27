"""Live status of the lab workers for the dashboard (the user, 2026-11-27: the worker list must be current).

The streams work in their own worktrees, so they cannot edit docs/auto-analysis/lab-plan.json of the main checkout.
They write their status here instead. The file is outside git and append-only, so many writers are safe:
    D:\\bananashot-lab\\status\\status.jsonl     (LAB_STATUS_DIR changes the folder)
lab_dash.py reads it at each request and puts the last entry of each action over the plan.

Use (one call at each change; it takes well under a second):
    python lab_status.py action A31 --who S3 --status running [--note "bench of E193, variant e"]
    python lab_status.py action A31 --who S3 --status waiting --waiting-for "the unpause"
    python lab_status.py action A31 --who S3 --status done --real 3 --outcome "fault clips 7 to 9 of 12; ..."
    python lab_status.py now --who S3 --text "writes the journal row of E193"     (what the worker does now)
    python lab_status.py idle --who S3                                             (the worker has no work)
    python lab_status.py show [--who S3]

Status words: running, waiting (on hold: a decision, the unpause, another action), queued, planned, done, dropped.
An action id that is not in the plan is shown as an unplanned action of that worker (give --title).
Standard library only.
"""
import argparse
import json
import os
import re
import sys
import time

DIR = os.environ.get("LAB_STATUS_DIR", r"D:\bananashot-lab\status")
LOG = os.path.join(DIR, "status.jsonl")
STATUSES = ("running", "waiting", "queued", "planned", "done", "dropped")


def write(entry):
    os.makedirs(DIR, exist_ok=True)
    entry = dict(entry, t=time.strftime("%Y-%m-%d %H:%M:%S"))
    line = json.dumps(entry, ensure_ascii=False) + "\n"
    # one write call in append mode: lines of parallel writers do not mix
    fd = os.open(LOG, os.O_WRONLY | os.O_APPEND | os.O_CREAT)
    try:
        os.write(fd, line.encode("utf-8"))
    finally:
        os.close(fd)
    return entry


def read():
    """All entries, oldest first. A broken line (a writer stopped in the middle) is skipped."""
    out = []
    try:
        with open(LOG, encoding="utf-8") as f:
            for line in f:
                try:
                    out.append(json.loads(line))
                except ValueError:
                    pass
    except OSError:
        pass
    return out


def latest():
    """{"actions": {id: merged entry}, "workers": {who: last now/idle entry}}. Fields of later entries of an action
    replace the fields of earlier ones; "started" and "finished" come from the first running and the done entry."""
    acts, workers = {}, {}
    for e in read():
        who = e.get("who") or ""
        who = who.upper() if re.fullmatch(r"[sS]\d", who) else who
        if e.get("kind") == "action":
            a = acts.setdefault(e["action"], {})
            for k, v in e.items():
                if v is not None and k not in ("kind",):
                    a[k] = v
            a["who"] = who
            if e.get("status") == "running" and "started" not in a:
                a["started"] = e["t"]
            if e.get("status") in ("done", "dropped"):
                a["finished"] = e["t"]
            if e.get("status") != "waiting" and e.get("waitingFor") is None:
                a.pop("waitingFor", None)
        elif e.get("kind") in ("now", "idle"):
            workers[who] = e
    return {"actions": acts, "workers": workers}


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("action")
    a.add_argument("action")
    a.add_argument("--who", required=True)
    a.add_argument("--status", required=True, choices=STATUSES)
    a.add_argument("--note")
    a.add_argument("--waiting-for")
    a.add_argument("--title")
    a.add_argument("--real", type=int, choices=range(0, 11))
    a.add_argument("--outcome")
    a.add_argument("--net-min", type=float, help="the net minutes still needed")
    n = sub.add_parser("now")
    n.add_argument("--who", required=True)
    n.add_argument("--text", required=True)
    i = sub.add_parser("idle")
    i.add_argument("--who", required=True)
    s = sub.add_parser("show")
    s.add_argument("--who")
    x = ap.parse_args()
    if x.cmd == "action":
        if x.status == "waiting" and not x.waiting_for:
            ap.error("--status waiting needs --waiting-for")
        if x.status == "done" and (x.real is None or not x.outcome):
            ap.error("--status done needs --real and --outcome")
        e = write({"kind": "action", "action": x.action, "who": x.who, "status": x.status, "note": x.note,
                   "waitingFor": x.waiting_for, "title": x.title, "real": x.real, "outcome": x.outcome,
                   "netMin": x.net_min})
        print(f"ok {e['t']} {x.who} {x.action} {x.status}")
    elif x.cmd == "now":
        e = write({"kind": "now", "who": x.who, "text": x.text})
        print(f"ok {e['t']} {x.who} now: {x.text}")
    elif x.cmd == "idle":
        e = write({"kind": "idle", "who": x.who})
        print(f"ok {e['t']} {x.who} idle")
    else:
        L = latest()
        for k, v in sorted(L["workers"].items()):
            if not x.who or k.lower() == x.who.lower():
                print(f"{k}: {v.get('kind')} {v.get('text', '')} ({v['t']})")
        for k, v in sorted(L["actions"].items()):
            if not x.who or (v.get("who") or "").lower() == x.who.lower():
                print(f"{k} {v.get('who')} {v.get('status')} {v.get('note') or v.get('waitingFor') or ''} ({v['t']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
