"""Final rally model for a new video (app integration, see docs/auto-analysis/app-integration.md).

This script trains one model on ALL marked singles matches, and keeps it in a file:
  <labRoot>/models/rally_<name>.pt   (network weights, and for each feature set the PCA and scales)
predict() gives the per-second rally probability of a match that was not in the training.
It also trains the fold models of the lab benchmark (pipeline.py): the same method, with one match left out:
  <labRoot>/models/rally_<name>_x<match>.pt
Train the full model and the fold models together, so that the benchmark measures the shipped method.

Models:  ball  = audio, mel, dino, wasbf   (E20, needs the ball pass)
         base  = audio, mel, dino          (E6b, no ball pass)
Usage: python rally_model.py train [--folds-only] [ball|base ...]
"""
import os, sys
import numpy as np
import torch, torch.nn as nn
from sklearn.decomposition import PCA
import lab
import platform_cfg
from seq_eval import Net
from train_eval import load

MODELS = {"ball": ["audio", "mel", "dino", "wasbf"], "base": ["audio", "mel", "dino"]}
TRAIN = ["m1015", "m1020", "m1021", "m1030"]
PCA_DIMS, EPOCHS, WIN = 32, 40, 240


def path(name, fold=None):
    """The model file. fold = the match that the model did not see (a fold model of the benchmark), or None."""
    return os.path.join(lab.ROOT, "models", f"rally_{name}.pt" if fold is None else f"rally_{name}_x{fold}.pt")


def stack(parts, prep):
    """Feature parts of one match -> the input matrix. prep = for each feature set the PCA and scale
    of the training matches, and if the model has "missing" columns for it."""
    cols = []
    for (name, f), pr in zip(parts, prep):
        if pr["pca_mean"] is not None:
            f = (np.nan_to_num(f) - pr["pca_mean"]) @ pr["pca_comp"].T / pr["sd"]
        miss = np.isnan(f).astype(np.float32)
        cols.append(np.nan_to_num(f).astype(np.float32))
        if pr["miss"]:
            cols.append(miss)
    return np.concatenate(cols, 1)


def train(name, fold=None):
    """Trains on TRAIN, or on TRAIN without the match fold."""
    feats = MODELS[name]
    TRAIN = lab.check_train([m for m in globals()["TRAIN"] if m != fold])
    raw = {m: load(m, feats, PCA_DIMS) for m in TRAIN}
    y = {m: lab.labels(m) for m in TRAIN}
    mask = {m: lab.span_mask(m) for m in TRAIN}
    prep = []
    for k, fname in enumerate(feats):
        fs = {m: raw[m][k][1] for m in TRAIN}
        pr = {"name": fname, "pca_mean": None, "pca_comp": None, "sd": None, "miss": any(np.isnan(f).any() for f in fs.values())}
        if fs[TRAIN[0]].shape[1] > 64:
            pca = PCA(PCA_DIMS).fit(np.concatenate([fs[m][mask[m]] for m in TRAIN]))
            pr["pca_mean"], pr["pca_comp"] = pca.mean_.astype(np.float32), pca.components_.astype(np.float32)
            pr["sd"] = (np.concatenate([pca.transform(fs[m]) for m in TRAIN]).std(0) + 1e-6).astype(np.float32)
        prep.append(pr)
    X = {m: stack(raw[m], prep) for m in TRAIN}
    torch.manual_seed(0); np.random.seed(0)
    net = Net(X[TRAIN[0]].shape[1]).cuda()
    opt = torch.optim.AdamW(net.parameters(), lr=2e-3, weight_decay=1e-2)
    steps = EPOCHS * 40
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, 4e-3, total_steps=steps)
    net.train()
    for _ in range(steps):
        xb, yb, mb = [], [], []
        for _ in range(16):
            m = TRAIN[np.random.randint(len(TRAIN))]
            s = np.random.randint(0, len(X[m]) - WIN)
            xb.append(X[m][s:s + WIN]); yb.append(y[m][s:s + WIN]); mb.append(mask[m][s:s + WIN])
        xb = torch.tensor(np.stack(xb)).cuda(); yb = torch.tensor(np.stack(yb)).float().cuda()
        mb = torch.tensor(np.stack(mb)).float().cuda()
        loss = (nn.functional.binary_cross_entropy_with_logits(net(xb), yb, reduction="none") * mb).sum() / mb.sum().clamp(min=1)
        opt.zero_grad(); loss.backward(); opt.step(); sched.step()
    os.makedirs(os.path.dirname(path(name)), exist_ok=True)
    torch.save({"feats": feats, "prep": prep, "dim": X[TRAIN[0]].shape[1], "net": net.state_dict(), "train": TRAIN}, path(name, fold))
    net.eval()
    for m in TRAIN:                                    # a check on the training matches (not a test)
        with torch.no_grad():
            p = torch.sigmoid(net(torch.tensor(X[m][None]).cuda()))[0].cpu().numpy()
        print(name, m, "(training match)", lab.fmt(lab.point_metrics(lab.segment(p, smooth=1, hi=0.5, lo=0.35, min_len=2.0), m)), flush=True)
    if fold is not None:                               # the held-out match: a test
        p = predict(fold, name, fold)
        print(name, fold, "(held out)", lab.fmt(lab.point_metrics(lab.segment(p, smooth=1, hi=0.5, lo=0.35, min_len=2.0), fold)), flush=True)


def predict(mid, name, fold=None):
    """Per-second rally probability of match mid (its feat_<name>.npy files must exist). fold: use the fold
    model that did not see that match."""
    ck = torch.load(path(name, fold), map_location="cpu", weights_only=False)     # our own file
    X = stack(load(mid, ck["feats"], PCA_DIMS), ck["prep"])
    dev = platform_cfg.small_device()
    net = Net(ck["dim"]).to(dev)
    net.load_state_dict(ck["net"])
    net.eval()
    with torch.no_grad():
        return torch.sigmoid(net(torch.tensor(X[None]).to(dev)))[0].cpu().numpy()


if __name__ == "__main__":
    if sys.argv[1] == "train":
        folds_only = "--folds-only" in sys.argv
        for n in ([a for a in sys.argv[2:] if not a.startswith("--")] or list(MODELS)):
            if not folds_only:
                train(n)
            for m in TRAIN:
                train(n, m)
