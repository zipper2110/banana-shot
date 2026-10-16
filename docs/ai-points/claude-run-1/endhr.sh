#!/bin/bash
# usage: endhr.sh t_end_seconds name [before=3.5] [after=0.5]
# far-court crop from the 4K source, 6 fps, labelled, 2 cols x 8 rows
T=$1; N=$2; B=${3:-3.5}; A=${4:-0.5}
W=/c/Users/leetv/AppData/Local/Temp/claude/C--w-banana-shot/67302755-7139-43e2-81c7-97a30a4dc5e2/scratchpad/work
SS=$(python -c "print(max(0,$T-$B))"); D=$(python -c "print($B+$A)")
mkdir -p "$W/hr/$N" && rm -f "$W/hr/$N"/*.jpg
ffmpeg -v error -y -ss $SS -hwaccel cuda -i "D:/PXL_20261001_130801998.mp4" -t $D -vf "fps=5,crop=3072:1000:384:0,scale=960:-2" -q:v 3 "$W/hr/$N/%03d.jpg"
cd "$W" && venv/Scripts/python - "$W/hr/$N" "$SS" "$W/hr/$N.jpg" <<'PY'
import sys,os,glob
from PIL import Image,ImageDraw,ImageFont
d,ss,out=sys.argv[1],float(sys.argv[2]),sys.argv[3]
fs=sorted(glob.glob(d+'/*.jpg'))[:20]
f=ImageFont.truetype("arialbd.ttf",20)
ims=[]
for i,p in enumerate(fs):
    im=Image.open(p); t=ss+i/5; dr=ImageDraw.Draw(im)
    dr.rectangle([0,0,80,24],fill="black"); dr.text((3,1),f"{int(t//60)}:{t%60:04.1f}",fill="yellow",font=f); ims.append(im)
w,h=ims[0].size; cols=2; rows=(len(ims)+1)//2
sh=Image.new("RGB",(w*cols,h*rows))
for i,im in enumerate(ims): sh.paste(im,((i%cols)*w,(i//cols)*h))
sh.save(out,quality=82)
PY
