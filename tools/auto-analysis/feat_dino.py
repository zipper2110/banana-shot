"""Image embeddings of the 1 fps keyframes with a frozen DINOv2 ViT-S/14
(Apache-2.0). One row for each second.

Writes into <match>/:
  feat_dino.npy       CLS token + mean of the patch tokens (768 columns)
  feat_dinogrid.npy   patch tokens pooled in a 2 x 3 grid (2304 columns)
  feat_dinodelta.npy  cosine distance to the previous second, for each grid
                      cell and for the full frame (7 columns)

Usage: python feat_dino.py [match id ...]
"""
import glob, os, sys, time
import platform_cfg
os.environ.setdefault("HF_HOME", platform_cfg.hf_home())
import numpy as np
import torch
import timm
from PIL import Image
import lab

W, H = 448, 252          # 32 x 18 patches of 14 px
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


class Frames(torch.utils.data.Dataset):
    def __init__(self, files):
        self.files = files

    def __len__(self):
        return len(self.files)

    def __getitem__(self, i):
        im = Image.open(self.files[i])
        im.draft("RGB", (W * 2, H * 2))          # fast JPEG decode at a lower size
        im = im.convert("RGB").resize((W, H), Image.BILINEAR)
        x = (np.asarray(im, dtype=np.float32) / 255.0 - MEAN) / STD
        return torch.from_numpy(x.transpose(2, 0, 1))


def run(mid, model):
    out = lab.mdir(mid)
    files = sorted(glob.glob(os.path.join(out, "kf", "*.jpg")))
    dl = torch.utils.data.DataLoader(Frames(files), batch_size=32, num_workers=6)
    cls_mean, grid = [], []
    t = time.time()
    with torch.no_grad(), torch.autocast("cuda", dtype=torch.float16, enabled=platform_cfg.half()):   # 16-bit floats only on CUDA
        for x in dl:
            tok = model.forward_features(platform_cfg.to_device(x))           # [B, 1 + 18*32, 384]
            cls, patch = tok[:, 0], tok[:, 1:].reshape(-1, 18, 32, 384)
            g = torch.nn.functional.adaptive_avg_pool2d(patch.permute(0, 3, 1, 2), (2, 3))
            cls_mean.append(torch.cat([cls, patch.mean((1, 2))], 1).float().cpu())
            grid.append(g.flatten(1).float().cpu())
    cls_mean, grid = torch.cat(cls_mean).numpy(), torch.cat(grid).numpy()
    g = grid.reshape(len(grid), 384, 6)
    full = cls_mean[:, 384:]
    def cosd(a, b):
        return 1 - (a * b).sum(1) / (np.linalg.norm(a, axis=1) * np.linalg.norm(b, axis=1) + 1e-6)
    delta = np.zeros((len(grid), 7), dtype=np.float32)
    for c in range(6):
        delta[1:, c] = cosd(g[1:, :, c], g[:-1, :, c])
    delta[1:, 6] = cosd(full[1:], full[:-1])
    np.save(os.path.join(out, "feat_dino.npy"), cls_mean.astype(np.float16))
    np.save(os.path.join(out, "feat_dinogrid.npy"), grid.astype(np.float16))
    np.save(os.path.join(out, "feat_dinodelta.npy"), delta)
    print(mid, cls_mean.shape, grid.shape, "%.0f s" % (time.time() - t), flush=True)


if __name__ == "__main__":
    model = platform_cfg.to_device(timm.create_model("vit_small_patch14_dinov2.lvd142m", pretrained=True, img_size=(H, W))).eval()
    for mid in (sys.argv[1:] or lab.LABELED):
        if os.path.exists(os.path.join(lab.mdir(mid), "kf.done")) and not os.path.exists(
                os.path.join(lab.mdir(mid), "feat_dinodelta.npy")):
            run(mid, model)
