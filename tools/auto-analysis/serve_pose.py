"""E10e: first-serve fault candidates from the serve pose (arm up), as a second signal.

Pose data: pose.npy (1 keyframe each second). For each marked point, in the window before the
point (same window as fault_ball.py, at least MIN_GAP s before the marked serve): the highest
wrist of the player at the server end, in torso lengths above the shoulders (server_end.wrist_up).
A value above THR in a second = a serve pose candidate.
Compares with the ball candidates (faults.json): both signals see the same serves only in part,
so the overlap gives an estimate of the true count of first-serve faults (capture-recapture:
N = ball * pose / both).
Writes <match>/faults_pose.json. Usage: python serve_pose.py [thr]
"""
import json, os, sys
import numpy as np
import lab
from ball_wasb import pre_windows
from server_end import near_far, wrist_up

THR = float(sys.argv[1]) if len(sys.argv) > 1 else 0.8
MIN_GAP = 2.0


def main():
    T = {"n": 0, "ball": 0, "pose": 0, "both": 0}
    for mid in ("m1015", "m1020", "m1021", "m1030"):
        out = lab.mdir(mid)
        pose = np.load(os.path.join(out, "pose.npy"))
        pose = pose[np.argsort(pose[:, 0], kind="stable")]
        n = lab.n_seconds(mid)
        st = np.searchsorted(pose[:, 0], np.arange(n + 1))
        faults = json.load(open(os.path.join(out, "faults.json")))
        res, c = [], {"n": 0, "ball": 0, "pose": 0, "both": 0}
        for f, (w0, w1) in zip(faults, pre_windows(mid)):
            best, bt = -9.0, None
            for s in range(int(np.ceil(w0)), int(f["start"] - MIN_GAP) + 1):
                if s >= n:
                    break
                r, fr = near_far(pose[st[s]:st[s + 1]])
                pl = r if f["serverNear"] else fr
                if pl is None:
                    continue
                u = wrist_up(pl)
                if np.isfinite(u) and u > best:
                    best, bt = float(u), s
            pc = best >= THR
            bc = f["candidate"] is not None
            c["n"] += 1; c["ball"] += bc; c["pose"] += pc; c["both"] += bc and pc
            res.append({"point": f["point"], "start": f["start"], "serverNear": f["serverNear"], "poseT": bt if pc else None, "wristUp": best,
                        "ballT": f["candidate"]["t"] if bc else None})
        json.dump(res, open(os.path.join(out, "faults_pose.json"), "w"), indent=0)
        for k in T:
            T[k] += c[k]
        est = c["ball"] * c["pose"] / max(1, c["both"])
        print(f"{mid}: points {c['n']}, ball candidates {c['ball']}, pose candidates {c['pose']}, both {c['both']}, "
              f"one or the other {c['ball'] + c['pose'] - c['both']} ({100 * (c['ball'] + c['pose'] - c['both']) / c['n']:.0f}%), estimate of real faults {est:.0f}")
    est = T["ball"] * T["pose"] / max(1, T["both"])
    print(f"ALL (thr {THR}): ball {T['ball']}, pose {T['pose']}, both {T['both']}, union {T['ball'] + T['pose'] - T['both']} "
          f"({100 * (T['ball'] + T['pose'] - T['both']) / T['n']:.0f}% of the points), estimate {est:.0f} ({100 * est / T['n']:.0f}%); "
          f"pose sees {100 * T['both'] / max(1, T['ball']):.0f}% of the ball candidates, ball sees {100 * T['both'] / max(1, T['pose']):.0f}% of the pose candidates")


if __name__ == "__main__":
    main()
