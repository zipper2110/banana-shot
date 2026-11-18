"""Leave-one-match-out test of a per-second "point in play" classifier.

Each feature set is a file <match>/feat_<name>.npy with one row for each
second. The script standardizes each feature for each match, stacks a time
context, trains on all other matches, predicts the held-out match, changes
the probability curve into points, and prints the metrics.

Usage: python train_eval.py --feats audio,dino --ctx 8 [--model hgb|logreg]
       [--matches m1030,m1020] [--save run-name]
"""
import argparse, json, os
import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.decomposition import PCA
from sklearn.metrics import roc_auc_score
import lab


RAW = {"players", "ball"}      # feature sets in physical units: no standardization


def load(mid, feats, pca_dims):
    parts = []
    for name in feats:
        f = np.load(os.path.join(lab.mdir(mid), f"feat_{name}.npy")).astype(np.float32)
        n = lab.n_seconds(mid)
        f = f[:n] if len(f) >= n else np.pad(f, ((0, n - len(f)), (0, 0)))
        if name not in RAW:
            f = (f - np.nanmean(f, 0)) / (np.nanstd(f, 0) + 1e-6)   # per-match standardization
        parts.append((name, f))
    return parts


def context(f, ctx):
    n = len(f)
    cols = []
    for d in range(-ctx, ctx + 1):
        idx = np.clip(np.arange(n) + d, 0, n - 1)
        cols.append(f[idx])
    return np.concatenate(cols, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--feats", default="audio")
    ap.add_argument("--ctx", type=int, default=8)
    ap.add_argument("--model", default="hgb")
    ap.add_argument("--pca", type=int, default=24, help="PCA size for feature sets wider than 64 columns")
    ap.add_argument("--matches", default="")
    ap.add_argument("--save", default="")
    a = ap.parse_args()
    feats = a.feats.split(",")
    mids = a.matches.split(",") if a.matches else [m for m in lab.LABELED if all(
        os.path.exists(os.path.join(lab.mdir(m), f"feat_{n}.npy")) for n in feats)]
    raw = {m: load(m, feats, a.pca) for m in mids}
    y = {m: lab.labels(m) for m in mids}
    mask = {m: lab.span_mask(m) for m in mids}
    rows, probs = [], {}
    for test in mids:
        train = [m for m in mids if m != test] or [test]
        X = {m: [] for m in mids}
        for k, name in enumerate(feats):
            if raw[test][k][1].shape[1] > 64:
                pca = PCA(a.pca).fit(np.concatenate([raw[m][k][1][mask[m]] for m in train]))
                for m in mids:
                    X[m].append(pca.transform(raw[m][k][1]))
            else:
                for m in mids:
                    X[m].append(raw[m][k][1])
        X = {m: context(np.concatenate(v, 1), a.ctx) for m, v in X.items()}
        Xtr = np.concatenate([X[m][mask[m]] for m in train])
        ytr = np.concatenate([y[m][mask[m]] for m in train])
        if a.model == "hgb":
            clf = HistGradientBoostingClassifier(max_iter=300, learning_rate=0.06, max_leaf_nodes=31,
                                                 l2_regularization=1.0, random_state=0)
        else:
            clf = LogisticRegression(C=0.3, max_iter=2000)
        clf.fit(Xtr, ytr)
        p = clf.predict_proba(X[test])[:, 1]
        probs[test] = p
        auc = roc_auc_score(y[test][mask[test]], p[mask[test]])
        m = lab.point_metrics(lab.segment(p), test)
        m["auc"] = auc
        rows.append((test, m))
        print(f"{test} AUC {auc:.3f}  {lab.fmt(m)}", flush=True)
    tot_gt = sum(m["gt"] for _, m in rows); tot_pred = sum(m["pred"] for _, m in rows); tot_hit = sum(m["hit"] for _, m in rows)
    print(f"ALL   AUC {np.mean([m['auc'] for _, m in rows]):.3f}  recall {100*tot_hit/tot_gt:.1f}%  precision {100*tot_hit/max(1,tot_pred):.1f}%"
          f"  start |med| {np.mean([m['start_abs_med'] for _, m in rows]):.2f}  end |med| {np.mean([m['end_abs_med'] for _, m in rows]):.2f}")
    if a.save:
        out = os.path.join(lab.ROOT, "runs", a.save)
        os.makedirs(out, exist_ok=True)
        for m, p in probs.items():
            np.save(os.path.join(out, f"{m}_prob.npy"), p.astype(np.float32))
        json.dump({"args": vars(a), "rows": rows}, open(os.path.join(out, "metrics.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
