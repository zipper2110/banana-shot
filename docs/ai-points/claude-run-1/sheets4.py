# usage: sheets4.py f4dir outdir start_s end_s step cols rows tilew [name]
# f4 frame n (1-based) is at t = (n-1)/4 s. step = frames of 0.25 s.
import sys, os
from PIL import Image, ImageDraw, ImageFont
d, out = sys.argv[1], sys.argv[2]
a, b = float(sys.argv[3]), float(sys.argv[4])
step, cols, rows, tw = int(sys.argv[5]), int(sys.argv[6]), int(sys.argv[7]), int(sys.argv[8])
name = sys.argv[9] if len(sys.argv) > 9 else None
os.makedirs(out, exist_ok=True)
import bisect
H = [float(x) for x in open('nearhits.txt')]
font = ImageFont.truetype("arialbd.ttf", 17)
per = cols*rows
n0, n1 = int(round(a*4)), int(round(b*4))
idx = list(range(n0, n1, step))
for k in range(0, len(idx), per):
    tiles = []
    for n in idx[k:k+per]:
        p = os.path.join(d, f"{n+1:06d}.jpg")
        if not os.path.exists(p): break
        im = Image.open(p); im = im.resize((tw, im.height*tw//im.width))
        t = n/4; lab = f"{int(t//60)}:{t%60:04.1f}"
        dr = ImageDraw.Draw(im); dr.rectangle([0,0,70,20], fill="black"); dr.text((3,1), lab, fill="yellow", font=font)
        i = bisect.bisect_left(H, t-0.25)
        if i < len(H) and H[i] < t+0.25: dr.rectangle([72,0,92,20], fill="red"); dr.text((76,1), "H", fill="white", font=font)
        tiles.append(im)
    if not tiles: break
    th = tiles[0].height
    sh = Image.new("RGB", (cols*tw, rows*th))
    for i, im in enumerate(tiles): sh.paste(im, ((i%cols)*tw, (i//cols)*th))
    fn = name if (name and len(idx) <= per) else f"t{idx[k]/4:07.1f}"
    sh.save(os.path.join(out, fn + ".jpg"), quality=80)
