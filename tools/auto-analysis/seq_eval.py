"""Leave-one-match-out test of a small sequence model (bidirectional GRU)
on the per-second features. Same inputs and metrics as train_eval.py.

Usage: python seq_eval.py --feats audio,dino [--save run-name] [--epochs 40]
"""
import argparse, json, os
import numpy as np
import torch, torch.nn as nn
from sklearn.decomposition import PCA
from sklearn.metrics import roc_auc_score
import lab
from train_eval import load


class Net(nn.Module):
    def __init__(self, d, h=64):
        super().__init__()
        self.inp = nn.Sequential(nn.Dropout(0.2), nn.Linear(d, h), nn.ReLU())
        self.gru = nn.GRU(h, h, num_layers=2, batch_first=True, bidirectional=True, dropout=0.2)
        self.out = nn.Linear(2 * h, 1)

    def forward(self, x):
        return self.out(self.gru(self.inp(x))[0]).squeeze(-1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--feats", default="audio,dino")
    ap.add_argument("--pca", type=int, default=32)
    ap.add_argument("--epochs", type=int, default=40)
    ap.add_argument("--win", type=int, default=240)
    ap.add_argument("--save", default="")
    ap.add_argument("--matches", default="")
    a = ap.parse_args()
    feats = a.feats.split(",")
    mids = a.matches.split(",") if a.matches else [m for m in lab.LABELED if all(
        os.path.exists(os.path.join(lab.mdir(m), f"feat_{n}.npy")) for n in feats)]
    lab.check_train(mids)
    raw = {m: load(m, feats, a.pca) for m in mids}
    y = {m: lab.labels(m) for m in mids}
    mask = {m: lab.span_mask(m) for m in mids}
    dev = "cuda"
    rows, probs = [], {}
    for test in mids:
        train = [m for m in mids if m != test]
        X = {m: [] for m in mids}
        for k, name in enumerate(feats):
            fs = {m: raw[m][k][1] for m in mids}
            if name in ("players", "ball"):                 # physical units: scale with the training matches
                tr = np.concatenate([fs[m][mask[m]] for m in train])
                mu, sd = np.nanmean(tr, 0), np.nanstd(tr, 0) + 1e-6
                fs = {m: (f - mu) / sd for m, f in fs.items()}
            if fs[test].shape[1] > 64:
                pca = PCA(a.pca).fit(np.concatenate([fs[m][mask[m]] for m in train]))
                fs = {m: pca.transform(f) for m, f in fs.items()}
                sd = np.concatenate([fs[m] for m in train]).std(0) + 1e-6
                fs = {m: f / sd for m, f in fs.items()}
            for m in mids:
                f = fs[m]
                miss = np.isnan(f).astype(np.float32)
                X[m].append(np.nan_to_num(f).astype(np.float32))
                if miss.any():
                    X[m].append(miss)
        X = {m: np.concatenate(v, 1) for m, v in X.items()}
        torch.manual_seed(0); np.random.seed(0)
        net = Net(X[test].shape[1]).to(dev)
        opt = torch.optim.AdamW(net.parameters(), lr=2e-3, weight_decay=1e-2)
        steps = a.epochs * 40
        sched = torch.optim.lr_scheduler.OneCycleLR(opt, 4e-3, total_steps=steps)
        net.train()
        for step in range(steps):
            xb, yb, mb = [], [], []
            for _ in range(16):
                m = train[np.random.randint(len(train))]
                s = np.random.randint(0, len(X[m]) - a.win)
                xb.append(X[m][s:s + a.win]); yb.append(y[m][s:s + a.win]); mb.append(mask[m][s:s + a.win])
            xb = torch.tensor(np.stack(xb)).to(dev); yb = torch.tensor(np.stack(yb)).float().to(dev)
            mb = torch.tensor(np.stack(mb)).float().to(dev)
            loss = (nn.functional.binary_cross_entropy_with_logits(net(xb), yb, reduction="none") * mb).sum() / mb.sum().clamp(min=1)
            opt.zero_grad(); loss.backward(); opt.step(); sched.step()
        net.eval()
        with torch.no_grad():
            p = torch.sigmoid(net(torch.tensor(X[test][None]).to(dev)))[0].cpu().numpy()
        probs[test] = p
        auc = roc_auc_score(y[test][mask[test]], p[mask[test]])
        m = lab.point_metrics(lab.segment(p, smooth=1, hi=0.5, lo=0.35, min_len=2.0), test)
        m["auc"] = auc
        rows.append((test, m))
        print(f"{test} AUC {auc:.3f}  {lab.fmt(m)}", flush=True)
    tg = sum(m["gt"] for _, m in rows); tp = sum(m["pred"] for _, m in rows); th = sum(m["hit"] for _, m in rows)
    print(f"ALL   AUC {np.mean([m['auc'] for _, m in rows]):.3f}  recall {100*th/tg:.1f}%  precision {100*th/max(1,tp):.1f}%"
          f"  start |med| {np.mean([m['start_abs_med'] for _, m in rows]):.2f}  end |med| {np.mean([m['end_abs_med'] for _, m in rows]):.2f}")
    if a.save:
        out = os.path.join(lab.ROOT, "runs", a.save)
        os.makedirs(out, exist_ok=True)
        for m, p in probs.items():
            np.save(os.path.join(out, f"{m}_prob.npy"), p.astype(np.float32))
        json.dump({"args": vars(a), "rows": rows}, open(os.path.join(out, "metrics.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
