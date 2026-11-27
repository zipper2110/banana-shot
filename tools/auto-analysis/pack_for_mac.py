"""Packs the files that are not in git for the copy to another computer (docs/auto-analysis/mac-setup.md).

Run this script on the lab PC. It makes the folder <out>/bananashot-lab with the same layout as the lab
root: copy this folder to the home folder of the Mac (~/bananashot-lab).
  ext/weights/      ball detector weights and the two pose models (ONNX)
  hf/               Hugging Face cache with the DINOv2 model
  data/models/      rally models
  selfcheck/ref/    reference results and clips for selfcheck.py (only with --ref)
  MANIFEST.txt      each file: size, SHA-256, source, license
Usage: python pack_for_mac.py --out <folder> [--ref <reference folder of selfcheck.py>] [--zip]
"""
import argparse, hashlib, os, shutil
import platform_cfg

DINO = "hf/hub/models--timm--vit_small_patch14_dinov2.lvd142m"
RTMLIB = os.path.join(os.path.expanduser("~"), ".cache", "rtmlib", "hub", "checkpoints")
# (path in the pack, source file or folder, source and license)
FILES = [
    ("ext/weights/wasb_tennis.pth.tar", "{lab}/ext/weights/wasb_tennis.pth.tar",
     "WASB-SBDT tennis weights, https://github.com/nttcom/WASB-SBDT, MIT"),
    ("ext/weights/ball_e38.pth", "{lab}/ext/weights/ball_e38.pth",
     "Our fine-tune of the WASB-SBDT tennis weights on our own videos (ball_train2.py, E38)"),
    ("ext/weights/yolox_m_8xb8-300e_humanart-c2c7a14a.onnx", "{rtmlib}/yolox_m_8xb8-300e_humanart-c2c7a14a.onnx",
     "YOLOX-m person detector (ONNX) from the OpenMMLab download server, through rtmlib, Apache-2.0"),
    ("ext/weights/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx",
     "{rtmlib}/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.onnx",
     "RTMPose-m (ONNX) from the OpenMMLab download server, through rtmlib, Apache-2.0"),
    (DINO, "{lab}/" + DINO, "DINOv2 ViT-S/14 (timm/vit_small_patch14_dinov2.lvd142m) from the Hugging Face hub, Apache-2.0"),
    ("data/models/rally_ball_t2_h.pt", "{data}/models/rally_ball_t2_h.pt", "own rally model (rally_model.py, trained on the 4 marked singles matches, head input E192)"),
    ("data/models/rally_base_t2.pt", "{data}/models/rally_base_t2.pt", "own rally model, no ball features (rally_model.py)"),
]


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def copy(src, dst, note, rows, root):
    if os.path.isdir(src):
        for base, dirs, files in os.walk(src):
            dirs[:] = [d for d in dirs if d not in (".locks", "work")]
            for f in files:
                s = os.path.join(base, f)
                copy(s, os.path.join(dst, os.path.relpath(s, src)), note, rows, root)
        return
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)                    # the content only: a link in the cache becomes a normal file
    rows.append((os.path.relpath(dst, root).replace("\\", "/"), os.path.getsize(dst), sha(dst), note))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--ref", default="")
    ap.add_argument("--zip", action="store_true")
    a = ap.parse_args()
    import lab
    root = os.path.join(a.out, "bananashot-lab")
    rows = []
    for rel, src, note in FILES:
        src = src.format(lab=platform_cfg.lab_root(), data=lab.ROOT, rtmlib=RTMLIB)
        if not os.path.exists(src):
            raise SystemExit(f"Missing: {src}")
        copy(src, os.path.join(root, rel), note, rows, root)
    if a.ref:
        copy(a.ref, os.path.join(root, "selfcheck", "ref"), "reference of selfcheck.py: clips of own videos (private) and results of the lab PC", rows, root)
    total = sum(r[1] for r in rows)
    with open(os.path.join(root, "MANIFEST.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("Files for the automatic point detection of BananaShot. Copy this folder to ~/bananashot-lab.\n")
        f.write(f"{len(rows)} files, {total / 1e6:.1f} MB\n\nbytes\tsha256\tfile\tsource and license\n")
        for rel, size, digest, note in rows:
            f.write(f"{size}\t{digest}\t{rel}\t{note}\n")
    groups = {}
    for rel, size, _, note in rows:
        key = rel if not rel.startswith(("hf/", "selfcheck/")) else rel.split("/")[0] + "/..."
        g = groups.setdefault(key, [0, 0, note])
        g[0] += size; g[1] += 1
    for key, (size, count, note) in groups.items():
        print(f"{size / 1e6:8.1f} MB  {key}" + (f" ({count} files)" if count > 1 else "") + f"  [{note}]")
    print(f"{total / 1e6:8.1f} MB  total, {len(rows)} files -> {root}")
    if a.zip:
        print("archive:", shutil.make_archive(root, "zip", a.out, "bananashot-lab"))


if __name__ == "__main__":
    main()
