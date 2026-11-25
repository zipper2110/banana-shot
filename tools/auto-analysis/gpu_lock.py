"""One heavy job at a time on the lab PC (gap program, 2026-11-25).

The lab PC has one GPU (6 GB) and 16 GB RAM. Parallel agents must not run two heavy jobs
at the same time. A heavy job is a job that uses the GPU, or decodes a full video.

Use:
    python gpu_lock.py run --who <stream> --what "<short text>" -- <command> [args...]
    python gpu_lock.py status

`run` waits until the lock is free, holds it while the command runs, and then frees it.
A lock of a process that does not exist now is removed (stale lock).
Each start and end goes to `heavy.log` in the lock folder.
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


def acquire(who, what, poll=30):
    os.makedirs(LOCK_DIR, exist_ok=True)
    waited = False
    while True:
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


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--who", required=True)
    r.add_argument("--what", required=True)
    r.add_argument("command", nargs=argparse.REMAINDER)
    sub.add_parser("status")
    a = ap.parse_args()
    if a.cmd == "status":
        cur = read_lock()
        if cur and alive(int(cur.get("pid", 0))):
            print(f"busy: {cur['who']}: {cur['what']} since {cur['start']}")
        else:
            print("free")
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
