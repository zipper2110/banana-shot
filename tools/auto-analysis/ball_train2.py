"""E38: fine-tune of the WASB ball network, second design (the first one is ball_train.py, E15b).

Why a second design. E15b taught the network that each place with no label is background. The pseudo-labels
cover only a part of the visible balls, so the network became careful and found almost nothing on a new court.
E27 and E37 found the cause of most lost balls: the network finds a ball only if it is brighter than its
background. This training repairs that cause directly, and it protects the general skill of the network:

  1. Ignore area. A frozen copy of the start network (the teacher) reads the clean picture. Where the teacher
     gives a ball and no label is near, the loss is zero: the place is possibly a real ball with no label.
  2. Paste. The labeled ball of a group (3 frames) is copied to a second place of the same pictures, with the
     same motion. The second place is random, so the ball is often in front of a wall, the sky, or a line.
     The copy keeps its own color: in front of a bright background it is darker than the background.
  3. Bright background. Sometimes all pixels that are not a ball become brighter. Then also the true ball is
     not brighter than its background.
  4. Color. Gain for each color channel, gamma, contrast: the court and the light of the training matches are
     not the ones of a new video.
  5. Small steps. The learning rate is 5 times lower than in E15b, and the training is shorter.

Data: the groups of ball_labels.py (3 frames 2 apart + 3 ball positions), D:/bananashot-lab/data/train.
Output: D:/bananashot-lab/ext/weights/<name>.pth (state dict only).
Usage: python ball_train2.py <name> <train match,...> [epochs]
"""
import json, os, sys, time
import numpy as np
import cv2
import torch
import wasb_hrnet

ROOT = "D:/bananashot-lab/data/train"
WOUT = "D:/bananashot-lab/ext/weights"
START = WOUT + "/wasb_tennis.pth.tar"
R, CW, CH = 2.5, 512, 288
MEAN = np.array([0.485, 0.456, 0.406], np.float32)
STD = np.array([0.229, 0.224, 0.225], np.float32)
PATCH, PATCH_R = 7, 4.0            # half size of the copied patch, and the radius of its soft mask (px at scale 0.35)
P_PASTE, P_BRIGHT = 0.6, 0.35
TEACH_P, TEACH_GROW, LABEL_NEAR = 0.3, 7, 8.0    # teacher probability of an ignore area, its growth (px), and "a label is near" (px)
LR_MAX = 2e-4


def disk(px, py, r):
    yy, xx = np.mgrid[0:CH, 0:CW]
    return ((xx - px) ** 2 + (yy - py) ** 2 <= r * r).astype(np.float32)


class Data(torch.utils.data.Dataset):
    def __init__(self, mids):
        self.items = []
        for m in mids:
            lj = json.load(open(os.path.join(ROOT, m, "labels.json")))
            have = set(os.listdir(os.path.join(ROOT, m)))
            for g in lj["groups"]:
                if all(f in have for f in g["frames"]):        # a frame past the end of the window is missing
                    self.items.append((m, g))

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        m, g = self.items[i]
        ims = [cv2.cvtColor(cv2.imread(os.path.join(ROOT, m, f)), cv2.COLOR_BGR2RGB) for f in g["frames"]]
        h, w = ims[0].shape[:2]
        xy = np.array(g["xy"], np.float32)
        rng = np.random
        cx = int(np.clip(xy[1, 0] - rng.randint(32, CW - 32), 0, w - CW))      # a crop that contains the middle label
        cy = int(np.clip(xy[1, 1] - rng.randint(32, CH - 32), 0, h - CH))
        ims = [im[cy:cy + CH, cx:cx + CW] for im in ims]
        xy = xy - [cx, cy]
        if rng.rand() < 0.5:
            ims = [im[:, ::-1] for im in ims]
            xy[:, 0] = CW - 1 - xy[:, 0]
        clean = np.stack(ims).astype(np.float32) / 255.0                        # 3, h, w, 3: the teacher reads this
        x = clean.copy()
        balls = [xy]
        # 2. paste: the same ball with the same motion at a second place
        inside = lambda q: bool(np.all((q[:, 0] >= PATCH + 1) & (q[:, 0] < CW - PATCH - 2) & (q[:, 1] >= PATCH + 1) & (q[:, 1] < CH - PATCH - 2)))
        if rng.rand() < P_PASTE and inside(xy):
            for _ in range(8):
                d = np.array([rng.randint(-CW, CW), rng.randint(-CH, CH)], np.float32)
                q = xy + d
                if inside(q) and np.hypot(*d) > 4 * PATCH:
                    yy, xx = np.mgrid[-PATCH:PATCH + 1, -PATCH:PATCH + 1]
                    mask = np.clip(PATCH_R + 0.5 - np.hypot(xx, yy), 0, 1)[..., None].astype(np.float32)
                    for k in range(3):
                        sx, sy = int(round(xy[k, 0])), int(round(xy[k, 1]))
                        tx, ty = int(round(q[k, 0])), int(round(q[k, 1]))
                        src = clean[k, sy - PATCH:sy + PATCH + 1, sx - PATCH:sx + PATCH + 1]
                        dst = x[k, ty - PATCH:ty + PATCH + 1, tx - PATCH:tx + PATCH + 1]
                        x[k, ty - PATCH:ty + PATCH + 1, tx - PATCH:tx + PATCH + 1] = dst * (1 - mask) + src * mask
                    balls.append(np.stack([np.array([round(q[k, 0]), round(q[k, 1])], np.float32) for k in range(3)]))
                    break
        hm = np.stack([np.max([disk(b[k, 0], b[k, 1], R) for b in balls], 0) for k in range(3)])
        # 3. bright background: each pixel that is not a ball goes toward white
        if rng.rand() < P_BRIGHT:
            a = rng.uniform(0.2, 0.6)
            keep = np.stack([np.max([disk(b[k, 0], b[k, 1], PATCH_R) for b in balls], 0) for k in range(3)])[..., None]
            x = x * keep + (x + (1 - x) * a) * (1 - keep)
        # 4. color: the same change for the 3 frames
        x = x * rng.uniform(0.85, 1.15, 3).astype(np.float32)
        x = np.clip(x, 0, 1) ** rng.uniform(0.7, 1.4)
        x = np.clip((x - 0.5) * rng.uniform(0.8, 1.25) + 0.5 + rng.uniform(-0.06, 0.06), 0, 1)
        near = np.stack([np.max([disk(b[k, 0], b[k, 1], LABEL_NEAR) for b in balls], 0) for k in range(3)])
        to_t = lambda v: torch.from_numpy(np.ascontiguousarray(((v - MEAN) / STD).astype(np.float32).transpose(0, 3, 1, 2).reshape(9, CH, CW)))
        return to_t(x), to_t(clean), torch.from_numpy(hm), torch.from_numpy(near)


def loss_fn(logit, y, weight):
    p = torch.sigmoid(logit).clamp(1e-4, 1 - 1e-4)
    return -((((1 - p) ** 2) * y * torch.log(p) + (p ** 2) * (1 - y) * torch.log(1 - p)) * weight).sum() / weight.sum()


def main(name, mids, epochs=3):
    import lab
    lab.check_train(mids)
    ds = Data(mids)
    dl = torch.utils.data.DataLoader(ds, batch_size=8, shuffle=True, num_workers=4, drop_last=True, persistent_workers=True)
    net = wasb_hrnet.load(START).cuda().train()
    teacher = wasb_hrnet.load(START).cuda().eval()
    for p in teacher.parameters():
        p.requires_grad_(False)
    opt = torch.optim.AdamW(net.parameters(), lr=LR_MAX / 10, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, LR_MAX, total_steps=epochs * len(dl), pct_start=0.15)
    scaler = torch.amp.GradScaler()
    t0 = time.time()
    ck = os.path.join(WOUT, f"{name}_ckpt.pth")              # resume after a stop or a reboot
    start = 0
    if os.path.exists(ck):
        c = torch.load(ck, map_location="cuda", weights_only=True)
        net.load_state_dict(c["net"]); opt.load_state_dict(c["opt"]); sched.load_state_dict(c["sched"]); scaler.load_state_dict(c["scaler"])
        start = int(c["epoch"])
        print(f"{name}: resume after epoch {start}", flush=True)
    for ep in range(start, epochs):
        tot, ign, n = 0.0, 0.0, 0
        for x, clean, y, near in dl:
            x, clean, y, near = x.cuda(non_blocking=True), clean.cuda(non_blocking=True), y.cuda(non_blocking=True), near.cuda(non_blocking=True)
            with torch.no_grad(), torch.autocast("cuda", dtype=torch.float16):
                tp = torch.sigmoid(teacher(clean).float())
            fire = torch.nn.functional.max_pool2d((tp > TEACH_P).float(), 2 * TEACH_GROW + 1, 1, TEACH_GROW)
            weight = 1.0 - fire * (1.0 - near)               # 1. no loss where the teacher gives a ball and no label is near
            with torch.autocast("cuda", dtype=torch.float16):
                out = net(x)
            loss = loss_fn(out.float(), y, weight)
            opt.zero_grad(); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); sched.step()
            tot += float(loss.detach()); ign += float((1 - weight).mean()); n += 1
            if n % 200 == 0:
                print(f"{name} epoch {ep + 1} step {n}/{len(dl)} loss {tot / n * 1000:.3f}e-3 {time.time() - t0:.0f} s", flush=True)
        os.makedirs(WOUT, exist_ok=True)
        torch.save({"net": net.state_dict(), "opt": opt.state_dict(), "sched": sched.state_dict(), "scaler": scaler.state_dict(), "epoch": ep + 1}, ck)
        torch.save({"model_state_dict": net.state_dict()}, os.path.join(WOUT, f"{name}_ep{ep + 1}.pth"))
        print(f"{name} epoch {ep + 1}/{epochs} loss {tot / n * 1000:.3f}e-3 ignore area {100 * ign / n:.2f}% groups {len(ds)} {time.time() - t0:.0f} s", flush=True)
    torch.save({"model_state_dict": net.state_dict()}, os.path.join(WOUT, f"{name}.pth"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2].split(","), int(sys.argv[3]) if len(sys.argv) > 3 else 3)
