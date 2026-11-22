"""E15b step 2: fine-tune the WASB ball network on our pseudo-labels.

Data: ball_labels.py groups (3 frames 2 apart + 3 ball positions).
Target: a disk of radius R px at each label in its own heatmap; zero
elsewhere (this also teaches the network to drop false blobs: heads,
insects, fixed dots). Loss: focal-weighted binary cross entropy, as in
WASB/TrackNet. Augmentation: horizontal flip, brightness and contrast,
random crop of 512 x 288 (the network is fully convolutional).
Start: the WASB tennis weights. Output: D:/bananashot-lab/ext/weights/<name>.pth
(state dict only).
Usage: python ball_train.py <name> <train match,...> [epochs]
"""
import json, os, sys, time
import numpy as np
import cv2
import torch
import wasb_hrnet

ROOT = "D:/bananashot-lab/data/train"
WOUT = "D:/bananashot-lab/ext/weights"
R, CW, CH = 2.5, 512, 288
MEAN = np.array([0.485, 0.456, 0.406], np.float32)
STD = np.array([0.229, 0.224, 0.225], np.float32)


class Data(torch.utils.data.Dataset):
    def __init__(self, mids, train=True):
        self.items = []
        for m in mids:
            lj = json.load(open(os.path.join(ROOT, m, "labels.json")))
            have = set(os.listdir(os.path.join(ROOT, m)))
            for g in lj["groups"]:
                if all(f in have for f in g["frames"]):        # a frame past the end of the window is missing
                    self.items.append((m, g))
        self.train = train

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        m, g = self.items[i]
        ims = [cv2.cvtColor(cv2.imread(os.path.join(ROOT, m, f)), cv2.COLOR_BGR2RGB) for f in g["frames"]]
        h, w = ims[0].shape[:2]
        xy = np.array(g["xy"], np.float32)
        rng = np.random
        # crop that contains the middle label
        cx = int(np.clip(xy[1, 0] - rng.randint(32, CW - 32), 0, w - CW))
        cy = int(np.clip(xy[1, 1] - rng.randint(32, CH - 32), 0, h - CH))
        ims = [im[cy:cy + CH, cx:cx + CW] for im in ims]
        xy = xy - [cx, cy]
        if rng.rand() < 0.5:
            ims = [im[:, ::-1] for im in ims]
            xy[:, 0] = CW - 1 - xy[:, 0]
        x = np.stack(ims).astype(np.float32) / 255.0
        a, b = rng.uniform(0.75, 1.25), rng.uniform(-0.08, 0.08)
        x = np.clip(x * a + b, 0, 1)
        x = (x - MEAN) / STD
        x = torch.from_numpy(np.ascontiguousarray(x.transpose(0, 3, 1, 2).reshape(9, CH, CW)))
        yy, xx = np.mgrid[0:CH, 0:CW]
        hm = np.stack([((xx - px) ** 2 + (yy - py) ** 2 <= R * R).astype(np.float32) for px, py in xy])
        return x, torch.from_numpy(hm)


def loss_fn(logit, y):
    p = torch.sigmoid(logit).clamp(1e-4, 1 - 1e-4)
    return -(((1 - p) ** 2) * y * torch.log(p) + (p ** 2) * (1 - y) * torch.log(1 - p)).mean()


def main(name, mids, epochs=6):
    ds = Data(mids)
    dl = torch.utils.data.DataLoader(ds, batch_size=8, shuffle=True, num_workers=4, drop_last=True, persistent_workers=True)
    net = wasb_hrnet.load("D:/bananashot-lab/ext/weights/wasb_tennis.pth.tar").cuda().train()
    opt = torch.optim.AdamW(net.parameters(), lr=3e-4, weight_decay=1e-4)
    steps = epochs * len(dl)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, 1e-3, total_steps=steps, pct_start=0.15)
    scaler = torch.amp.GradScaler()
    t0, k = time.time(), 0
    ck = os.path.join(WOUT, f"{name}_ckpt.pth")              # resume after a stop or a reboot
    start = 0
    if os.path.exists(ck):
        c = torch.load(ck, map_location="cuda", weights_only=True)
        net.load_state_dict(c["net"]); opt.load_state_dict(c["opt"]); sched.load_state_dict(c["sched"]); scaler.load_state_dict(c["scaler"])
        start = int(c["epoch"])
        print(f"{name}: resume after epoch {start}", flush=True)
    for ep in range(start, epochs):
        tot = 0.0
        for x, y in dl:
            x, y = x.cuda(non_blocking=True), y.cuda(non_blocking=True)
            with torch.autocast("cuda", dtype=torch.float16):
                out = net(x)
            loss = loss_fn(out.float(), y)
            opt.zero_grad(); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); sched.step()
            tot += float(loss.detach()); k += 1
        os.makedirs(WOUT, exist_ok=True)
        torch.save({"net": net.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(), "scaler": scaler.state_dict(), "epoch": ep + 1}, ck)
        print(f"{name} epoch {ep + 1}/{epochs} loss {tot / len(dl) * 1000:.3f}e-3 groups {len(ds)} {time.time() - t0:.0f} s", flush=True)
    os.makedirs(WOUT, exist_ok=True)
    torch.save({"model_state_dict": net.state_dict()}, os.path.join(WOUT, f"{name}.pth"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2].split(","), int(sys.argv[3]) if len(sys.argv) > 3 else 6)
