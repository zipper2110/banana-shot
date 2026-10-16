# usage: dual.py outdir start_s end_s step_s cols rows tilew [name]
# tile = far crop (far1, keyframes, 1 fps) above the full frame (f4, 4 fps). red H = loud near hit.
# Run it from the work dir: it reads far1/, f4/ and nearhits.txt.
import sys, os, bisect
from PIL import Image, ImageDraw, ImageFont
out=sys.argv[1]; a=float(sys.argv[2]); b=float(sys.argv[3]); st=float(sys.argv[4])
cols,rows,tw=int(sys.argv[5]),int(sys.argv[6]),int(sys.argv[7]); name=sys.argv[8] if len(sys.argv)>8 else None
os.makedirs(out,exist_ok=True)
H=[float(x) for x in open('nearhits.txt')]
font=ImageFont.truetype("arialbd.ttf",17)
def tile(t):
    fp=f"far1/{int(round(t))+1:05d}.jpg"; np_=f"f4/{int(round(t*4))+1:06d}.jpg"
    if not os.path.exists(np_): return None
    n=Image.open(np_); n=n.resize((tw,n.height*tw//n.width))
    if os.path.exists(fp):
        f=Image.open(fp); f=f.resize((tw,f.height*tw//f.width))
    else: f=Image.new("RGB",(tw,tw*1000//3072),"gray")
    im=Image.new("RGB",(tw,f.height+n.height)); im.paste(f,(0,0)); im.paste(n,(0,f.height))
    d=ImageDraw.Draw(im); d.rectangle([0,0,70,20],fill="black"); d.text((3,1),f"{int(t//60)}:{t%60:04.1f}",fill="yellow",font=font)
    i=bisect.bisect_left(H,t-st/2)
    if i<len(H) and H[i]<t+st/2: d.rectangle([72,0,92,20],fill="red"); d.text((76,1),"H",fill="white",font=font)
    return im
ts=[]; t=a
while t<b-1e-6: ts.append(round(t,2)); t+=st
per=cols*rows
for k in range(0,len(ts),per):
    tl=[x for x in (tile(t) for t in ts[k:k+per]) if x]
    if not tl: break
    w,h=tl[0].size; sh=Image.new("RGB",(cols*w,rows*h))
    for i,im in enumerate(tl): sh.paste(im,((i%cols)*w,(i//cols)*h))
    fn=name if (name and len(ts)<=per) else f"d{ts[k]:07.1f}"
    sh.save(os.path.join(out,fn+".jpg"),quality=80)
