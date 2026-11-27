"""Final rally model for a new video (app integration, see docs/auto-analysis/app-integration.md).

This script trains one model on ALL marked singles matches, and keeps it in a file:
  <labRoot>/models/rally_<name>_t2.pt   (network weights, and for each feature set the PCA and scales)
"_t2" = time base 2 (chain-inventory G2): the picture features (DINO) of second s come from the picture of the
true time s + 0.5, not from picture row s. A model of the other time base gets shifted inputs, so the name changes.
predict() gives the per-second rally probability of a match that was not in the training.
It also trains the fold models of the lab benchmark (pipeline.py): the same method, with one match left out:
  <labRoot>/models/rally_<name>_t2_x<match>.pt
Train the full model and the fold models together, so that the benchmark measures the shipped method.
"_h" = head input (E192): a model with the ball features (wasbf) is trained on the feat_wasbf.npy that feat_wasb
makes from the lab flights of the code key (bench.lab_wasbf), as the app makes it from its own flights. The files
of the frozen lab input (E20 to E191) keep their names without "_h"; HEAD_INPUT = False uses them again.

Models:  ball  = audio, mel, dino, wasbf   (E20, needs the ball pass)
         base  = audio, mel, dino          (E6b, no ball pass)
Usage: python rally_model.py train [--folds-only] [--seed=K] [ball|base ...]   (K > 0: the seed files of A37)
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
TIME_BASE = "_t2"
# E192 (manager decision after E191): the "ball" models are trained on the head GRU input, the input that the app
# makes. False = the old files (frozen lab input), for the bench and the app. Do not change it from the environment:
# the app reads no lab variable (G12).
HEAD_INPUT = True
# A37 (E196): the rally GRU as the mean of SEEDS models. Seed 0 = the files of path() with no seed; seed k > 0 = the
# same name with "_s<k>". 1 = one model. Not from the environment (G12). On: 3 (manager decision c32f996f after E196).
SEEDS = 3
SEED_DIR = None             # the folder of the seed files k > 0; None = the models folder (the bench sets a lab folder)


def input_tag(name):
    """"_h" for a model with the ball features when HEAD_INPUT, else ""."""
    return "_h" if HEAD_INPUT and "wasbf" in MODELS.get(name, ()) else ""


def path(name, fold=None, seed=0):
    """The model file. fold = the match that the model did not see (a fold model of the benchmark), or None.
    seed = the training seed (A37); 0 = the file with no seed in its name."""
    base = f"rally_{name}{TIME_BASE}{input_tag(name)}" + ("" if fold is None else f"_x{fold}")
    if seed == 0:
        return os.path.join(lab.ROOT, "models", f"{base}.pt")
    return os.path.join(SEED_DIR or os.path.join(lab.ROOT, "models"), f"{base}_s{seed}.pt")


def model_files(name, fold=None):
    """The model files that predict() reads: one file, or SEEDS files (A37)."""
    return [path(name, fold)] + [path(name, fold, k) for k in range(1, SEEDS)]


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


def head_wasbf(mids):
    """{mid: folder of feat_wasbf.npy} of the head input (E192): feat_wasb on the lab flights of the code key, the
    same folders as the bench lab set (bench.app_nets, bench.lab_wasbf). Missing flights are made again (heavy)."""
    import bench
    assert bench.LAB_FLIGHTS == "remake", bench.LAB_FLIGHTS
    return bench.lab_wasbf(bench.app_nets(mids))


def load_wasbf(mid, feats, pca, folder):
    """load() with feat_wasbf.npy from folder; the other feature parts from the lab folder of mid."""
    parts = load(mid, feats, pca)
    if folder is None or "wasbf" not in feats:
        return parts
    mdir = lab.mdir
    lab.mdir = lambda x: folder if x == mid else mdir(x)
    try:
        new = dict(load(mid, ["wasbf"], pca))["wasbf"]
    finally:
        lab.mdir = mdir
    return [(n, new if n == "wasbf" else f) for n, f in parts]


def train(name, fold=None, wasbf=None, seed=0):
    """Trains on TRAIN, or on TRAIN without the match fold. wasbf: {mid: folder of feat_wasbf.npy}; None = the head
    input (head_wasbf) for a model with "_h", else the lab files. seed: the random seed and the file (A37)."""
    feats = MODELS[name]
    TRAIN = lab.check_train([m for m in globals()["TRAIN"] if m != fold])
    if wasbf is None and input_tag(name):
        wasbf = head_wasbf(globals()["TRAIN"])        # with the held-out match, for its test below
    raw = {m: load_wasbf(m, feats, PCA_DIMS, (wasbf or {}).get(m)) for m in TRAIN}
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
    torch.manual_seed(seed); np.random.seed(seed)
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
    os.makedirs(os.path.dirname(path(name, fold, seed)), exist_ok=True)
    torch.save({"feats": feats, "prep": prep, "dim": X[TRAIN[0]].shape[1], "net": net.state_dict(), "train": TRAIN,
                "wasbf": {m: (wasbf or {}).get(m) for m in TRAIN}, "seed": seed}, path(name, fold, seed))
    net.eval()
    for m in TRAIN:                                    # a check on the training matches (not a test)
        with torch.no_grad():
            p = torch.sigmoid(net(torch.tensor(X[m][None]).cuda()))[0].cpu().numpy()
        print(name, m, "(training match)", lab.fmt(lab.point_metrics(lab.segment(p, smooth=1, hi=0.5, lo=0.35, min_len=2.0), m)), flush=True)
    if fold is not None:                               # the held-out match: a test
        p = predict_one(fold, name, fold, wasbf.get(fold) if wasbf else None, seed)
        print(name, fold, "(held out)", lab.fmt(lab.point_metrics(lab.segment(p, smooth=1, hi=0.5, lo=0.35, min_len=2.0), fold)), flush=True)


def mean_prob(probs):
    """A37: the per-second mean of the probabilities of the seed models."""
    return np.mean(np.stack(probs), axis=0).astype(np.float32)


def predict(mid, name, fold=None, wasbf=None):
    """Per-second rally probability of match mid (its feat_<name>.npy files must exist). fold: use the fold
    model that did not see that match. wasbf: a folder of feat_wasbf.npy (None = the folder of mid).
    SEEDS > 1: the mean of the seed models (A37)."""
    if SEEDS <= 1:
        return predict_one(mid, name, fold, wasbf)
    return mean_prob([predict_one(mid, name, fold, wasbf, k) for k in range(SEEDS)])


def predict_one(mid, name, fold=None, wasbf=None, seed=0):
    """The probability of one model file (path with seed)."""
    ck = torch.load(path(name, fold, seed), map_location="cpu", weights_only=False)     # our own file
    X = stack(load_wasbf(mid, ck["feats"], PCA_DIMS, wasbf), ck["prep"])
    dev = platform_cfg.small_device()
    net = Net(ck["dim"]).to(dev)
    net.load_state_dict(ck["net"])
    net.eval()
    with torch.no_grad():
        return torch.sigmoid(net(torch.tensor(X[None]).to(dev)))[0].cpu().numpy()


if __name__ == "__main__":
    if sys.argv[1] == "train":
        folds_only = "--folds-only" in sys.argv
        seed = next((int(a.split("=")[1]) for a in sys.argv if a.startswith("--seed=")), 0)
        for n in ([a for a in sys.argv[2:] if not a.startswith("--")] or list(MODELS)):
            if not folds_only:
                train(n, seed=seed)
            for m in TRAIN:
                train(n, m, seed=seed)
