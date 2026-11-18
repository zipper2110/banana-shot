"""E13: winner end ("near player wins") with no ball track.

One row for each marked point of the singles matches. Features: the
per-second mel and DINO features (PCA) of the seconds end-4 .. end+7,
the point length, and "the server is at the near end". Leave one match out.
Usage: python winner_baseline.py
"""
import json, os
import numpy as np
from sklearn.decomposition import PCA
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import HistGradientBoostingClassifier
import lab

MIDS = ["m1015", "m1020", "m1021", "m1030"]
PRE, POST = 4, 8


def load(mid, name):
    f = np.load(os.path.join(lab.mdir(mid), f"feat_{name}.npy")).astype(np.float32)
    return (f - f.mean(0)) / (f.std(0) + 1e-6)


def rows(mid, proj):
    E = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
    gt = lab.gt_points(mid)
    n = lab.n_seconds(mid)
    X, y, srv = [], [], []
    for (a, b, _), e in zip(gt, E):
        if e["winnerNear"] is None:
            continue
        s0 = int(round(b))
        idx = np.clip(np.arange(s0 - PRE, s0 + POST), 0, n - 1)
        X.append(np.concatenate([p[idx].ravel() for p in proj] + [[b - a, float(bool(e["serverNearRules"]))]]))
        y.append(int(e["winnerNear"])); srv.append(int(bool(e["serverNearRules"])))
    return np.array(X, np.float32), np.array(y), np.array(srv)


def main():
    raw = {m: {k: load(m, k) for k in ("mel", "dino")} for m in MIDS}
    for feats in (("mel",), ("dino",), ("mel", "dino")):
        accs, n_all, base = [], 0, []
        for test in MIDS:
            train = [m for m in MIDS if m != test]
            pcas = {k: PCA(12).fit(np.concatenate([raw[m][k] for m in train])) for k in feats}
            data = {m: rows(m, [pcas[k].transform(raw[m][k]) for k in feats]) for m in MIDS}
            Xtr = np.concatenate([data[m][0] for m in train]); ytr = np.concatenate([data[m][1] for m in train])
            mu, sd = Xtr.mean(0), Xtr.std(0) + 1e-6
            clf = LogisticRegression(C=0.02, max_iter=3000).fit((Xtr - mu) / sd, ytr)
            Xte, yte, srv = data[test]
            acc = (clf.predict((Xte - mu) / sd) == yte).mean()
            accs.append((test, acc, len(yte), (srv == yte).mean(), max(yte.mean(), 1 - yte.mean())))
        print("features", "+".join(feats))
        for t, a, n, s, mj in accs:
            print("   %s  accuracy %.1f%%  (n %d; rule 'server wins' %.1f%%; majority %.1f%%)" % (t, 100 * a, n, 100 * s, 100 * mj))
        print("   ALL accuracy %.1f%%" % (100 * sum(a * n for _, a, n, _, _ in accs) / sum(n for _, _, n, _, _ in accs)))


if __name__ == "__main__":
    main()
