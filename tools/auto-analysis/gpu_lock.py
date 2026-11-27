"""One heavy job at a time on the lab PC (gap program, 2026-11-25).

The lab PC has one GPU (6 GB) and 16 GB RAM. Parallel agents must not run two heavy jobs
at the same time. A heavy job is a job that uses the GPU, or decodes a full video.

Use:
    python gpu_lock.py run --who <stream> --what "<short text>" -- <command> [args...]
    python gpu_lock.py status
    python gpu_lock.py pause --mode queue|all     (the user, from the lab dashboard)
    python gpu_lock.py unpause

`run` waits until the lock is free, holds it while the command runs, and then frees it.
Waiting jobs get the lock in the order of their start (a ticket in the `queue` folder).
`status` shows the holder and the queue.
A lock of a process that does not exist now is removed (stale lock).
Each start and end goes to `heavy.log` in the lock folder.
Pause (the user, 2026-11-27): `pause --mode queue` lets the running job end and starts no new job;
`pause --mode all` also stops the running job (its process tree) at once, to free the PC.
A pause is a ticket with the oldest possible name, held by a small detached process. Thus also the older
copies of this file in the code snapshots wait behind it (they keep the queue order). `unpause` removes it.
A stopped job is not started again: its owner starts it again (heavy.log has "KILLED by pause").
Standard library only.
"""
import argparse
import ctypes
import json
import os
import subprocess
import sys
import time

LOCK_DIR = os.environ.get("HEAVY_LOCK_DIR", r"D:\bananashot-lab\locks")
LOCK = os.path.join(LOCK_DIR, "heavy.lock")
LOG = os.path.join(LOCK_DIR, "heavy.log")
QUEUE = os.path.join(LOCK_DIR, "queue")   # one ticket for each waiting job; the oldest live ticket goes first
PAUSE = os.path.join(LOCK_DIR, "pause.json")   # {"mode": "queue" | "all", "since", "by"} while the lab is paused
PAUSE_PREFIX = "0000000000.000000_"            # the name of the pause ticket sorts before all job tickets


def alive(pid):
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False
    k = ctypes.windll.kernel32
    h = k.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
    if not h:
        return False
    code = ctypes.c_ulong()
    ok = k.GetExitCodeProcess(h, ctypes.byref(code))
    k.CloseHandle(h)
    return bool(ok) and code.value == 259  # STILL_ACTIVE


def read_lock():
    try:
        with open(LOCK, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def log(line):
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(time.strftime("%Y-%m-%d %H:%M:%S") + " " + line + "\n")


def ticket_first(mine):
    """True when the ticket `mine` is the oldest ticket of a live process (first come, first served)."""
    live = []
    for name in sorted(os.listdir(QUEUE)):
        pid = int(name.split("_")[1].split(".")[0])
        if name == mine or alive(pid):
            live.append(name)
        else:
            try:
                os.remove(os.path.join(QUEUE, name))
            except OSError:
                pass
    return bool(live) and live[0] == mine


def acquire(who, what, poll=30):
    os.makedirs(QUEUE, exist_ok=True)
    mine = f"{time.time():017.6f}_{os.getpid()}.json"
    with open(os.path.join(QUEUE, mine), "w", encoding="utf-8") as f:
        json.dump({"who": who, "what": what}, f)
    try:
        _acquire(who, what, poll, mine)
    finally:
        try:
            os.remove(os.path.join(QUEUE, mine))
        except OSError:
            pass


def _acquire(who, what, poll, mine):
    waited = False
    while True:
        if not ticket_first(mine):
            if not waited:
                p = read_pause()
                print(f"[gpu_lock] paused by the user ({p.get('mode')}, since {p.get('since')}); waiting" if p
                      else "[gpu_lock] in the queue behind older jobs", flush=True)
                waited = True
            time.sleep(poll)
            continue
        try:
            fd = os.open(LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        except FileExistsError:
            cur = read_lock()
            if cur is None or not alive(int(cur.get("pid", 0))):
                try:
                    os.remove(LOCK)
                    log(f"STALE removed {cur}")
                except OSError:
                    pass
                continue
            if not waited:
                print(f"[gpu_lock] waiting for {cur.get('who')}: {cur.get('what')} "
                      f"(since {cur.get('start')})", flush=True)
                waited = True
            time.sleep(poll)
            continue
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump({"who": who, "what": what, "pid": os.getpid(),
                       "start": time.strftime("%Y-%m-%d %H:%M:%S")}, f)
        log(f"START {who}: {what}")
        return


def release(who, what, rc, t0):
    cur = read_lock()
    if cur and int(cur.get("pid", 0)) == os.getpid():
        os.remove(LOCK)
    log(f"END   {who}: {what} rc={rc} {time.time() - t0:.0f}s")


def read_pause():
    try:
        with open(PAUSE, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def pause_tickets():
    return [n for n in os.listdir(QUEUE) if n.startswith(PAUSE_PREFIX)] if os.path.isdir(QUEUE) else []


def hold():
    """The detached process of a pause: its ticket stays first in the queue while pause.json exists."""
    os.makedirs(QUEUE, exist_ok=True)
    mine = os.path.join(QUEUE, f"{PAUSE_PREFIX}{os.getpid()}.json")
    with open(mine, "w", encoding="utf-8") as f:
        json.dump({"who": "pause", "what": "paused by the user"}, f)
    try:
        while os.path.exists(PAUSE) and os.path.exists(mine):
            time.sleep(2)
    finally:
        try:
            os.remove(mine)
        except OSError:
            pass


def command_line(pid):
    """The command line of the process pid (Windows), or "" when it is not known."""
    try:
        out = subprocess.run(["powershell", "-NoProfile", "-Command",
                              f"(Get-CimInstance Win32_Process -Filter 'ProcessId={int(pid)}').CommandLine"],
                             capture_output=True, text=True, timeout=20)
        return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def kill_holder():
    """Stops the job that holds the lock (its process tree). Returns a text for the caller."""
    cur = read_lock()
    if not cur or not alive(int(cur.get("pid", 0))):
        return "no running job"
    pid = int(cur["pid"])
    cmd = command_line(pid)
    if "python" not in cmd.lower():   # a reused pid is not our job
        return f"not stopped: pid {pid} is not a python process ({cmd[:80]!r})"
    subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True, timeout=30)
    for _ in range(20):
        if not alive(pid):
            break
        time.sleep(0.5)
    if alive(pid):
        return f"not stopped: pid {pid} is still alive"
    try:
        os.remove(LOCK)
    except OSError:
        pass
    log(f"KILLED by pause {cur.get('who')}: {cur.get('what')} (pid {pid})")
    return f"stopped {cur.get('who')}: {cur.get('what')}"


def pause(mode, by="user"):
    """Pauses the queue (mode queue) or the queue and the running job (mode all). Returns a text."""
    if mode not in ("queue", "all"):
        raise ValueError(mode)
    os.makedirs(QUEUE, exist_ok=True)
    old = read_pause()
    with open(PAUSE, "w", encoding="utf-8") as f:
        json.dump({"mode": mode, "since": (old or {}).get("since") or time.strftime("%Y-%m-%d %H:%M:%S"),
                   "by": by}, f)
    if not any(alive(int(n[len(PAUSE_PREFIX):].split(".")[0])) for n in pause_tickets()):
        flags = 0x00000008 | 0x00000200 | 0x08000000 if os.name == "nt" else 0  # DETACHED, NEW_GROUP, NO_WINDOW
        subprocess.Popen([sys.executable, os.path.abspath(__file__), "hold"], creationflags=flags,
                         stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                         close_fds=True)
        for _ in range(20):                     # wait for the ticket, so no job starts after the return
            if pause_tickets():
                break
            time.sleep(0.25)
    if old is None or old.get("mode") != mode:
        log(f"PAUSE {mode} by {by}")
    return kill_holder() if mode == "all" else "the running job ends; no new job starts"


def unpause(by="user"):
    if read_pause() is None and not pause_tickets():
        return "not paused"
    try:
        os.remove(PAUSE)
    except OSError:
        pass
    for n in pause_tickets():
        try:
            os.remove(os.path.join(QUEUE, n))
        except OSError:
            pass
        pid = int(n[len(PAUSE_PREFIX):].split(".")[0])   # the hold process: stop it when it only waits
        cmd = command_line(pid) if alive(pid) else ""
        if "python" in cmd.lower() and ("hold" in cmd or "sleep" in cmd):
            subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True, timeout=30)
    log(f"UNPAUSE by {by}")
    return "the queue goes on"


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--who", required=True)
    r.add_argument("--what", required=True)
    r.add_argument("command", nargs=argparse.REMAINDER)
    sub.add_parser("status")
    p = sub.add_parser("pause")
    p.add_argument("--mode", choices=["queue", "all"], required=True)
    p.add_argument("--by", default="user")
    sub.add_parser("unpause")
    sub.add_parser("hold")
    a = ap.parse_args()
    if a.cmd == "hold":
        hold()
        return 0
    if a.cmd == "pause":
        print(pause(a.mode, a.by))
        return 0
    if a.cmd == "unpause":
        print(unpause())
        return 0
    if a.cmd == "status":
        pz = read_pause()
        if pz:
            print(f"PAUSED ({pz['mode']}) since {pz['since']} by {pz.get('by')}")
        cur = read_lock()
        if cur and alive(int(cur.get("pid", 0))):
            print(f"busy: {cur['who']}: {cur['what']} since {cur['start']}")
        else:
            print("free")
        if os.path.isdir(QUEUE):
            for name in sorted(os.listdir(QUEUE)):
                if alive(int(name.split("_")[1].split(".")[0])):
                    try:
                        t = json.load(open(os.path.join(QUEUE, name), encoding="utf-8"))
                    except (OSError, ValueError):
                        continue
                    print(f"  waits: {t['who']}: {t['what']}")
        return 0
    cmd = a.command[1:] if a.command[:1] == ["--"] else a.command
    if not cmd:
        ap.error("no command")
    acquire(a.who, a.what)
    t0 = time.time()
    rc = -1
    try:
        rc = subprocess.call(cmd)
    finally:
        release(a.who, a.what, rc, t0)
    return rc


if __name__ == "__main__":
    sys.exit(main())
