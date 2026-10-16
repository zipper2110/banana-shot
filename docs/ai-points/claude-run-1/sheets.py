# usage: sheets.py kfdir outdir start_s end_s cols rows tilew
import sys, os
from PIL import Image, ImageDraw, ImageFont
kf, out, a, b, cols, rows, tw = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6]), int(sys.argv[7])
os.makedirs(out, exist_ok=True)
font = ImageFont.truetype("arialbd.ttf", 18)
per = cols*rows
for s0 in range(a, b, per):
    tiles = []
    for t in range(s0, min(s0+per, b)):
        p = os.path.join(kf, f"{t+1:05d}.jpg")
        if not os.path.exists(p): break
        im = Image.open(p); im = im.resize((tw, im.height*tw//im.width))
        d = ImageDraw.Draw(im); lab = f"{t//60}:{t%60:02d}"
        d.rectangle([0,0,62,21], fill="black"); d.text((3,1), lab, fill="yellow", font=font)
        tiles.append(im)
    if not tiles: break
    th = tiles[0].height
    sheet = Image.new("RGB", (cols*tw, rows*th))
    for i, im in enumerate(tiles): sheet.paste(im, ((i%cols)*tw, (i//cols)*th))
    sheet.save(os.path.join(out, f"s{s0:05d}.jpg"), quality=80)
