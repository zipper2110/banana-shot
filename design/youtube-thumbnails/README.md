# YouTube thumbnails

Each video has an HTML source and a PNG of 1280 × 720. YouTube accepts
files up to 2 MB.

- Do not put text in the bottom-right corner. YouTube shows the video
  length there.
- Each thumbnail must be readable at a small size (about 360 px wide).
  Use 2 or 3 big words in the lime color of the app.

## doubles: the doubles full match

- `doubles-frame.jpg` is the frame at 30:00 of the export
  `D:\Dima-Yana = Iliya-Marina-points-balanced-4K.mp4`.
- `doubles-scoreboard.png` is the scoreboard of the same frame at 4K, so
  that it stays sharp when it is bigger.
- The frame and the scoreboard show the real names. The video on YouTube
  shows them too.

```bash
F="D:/Dima-Yana = Iliya-Marina-points-balanced-4K.mp4"
ffmpeg -ss 1800 -i "$F" -frames:v 1 -vf scale=2560:-2 -q:v 2 doubles-frame.jpg
ffmpeg -ss 1800 -i "$F" -frames:v 1 -vf crop=900:330:96:96 doubles-scoreboard.png
```

## Render a thumbnail

```bash
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu \
  --hide-scrollbars --force-device-scale-factor=1 --window-size=1280,720 \
  --screenshot="C:/w/banana-shot/design/youtube-thumbnails/doubles.png" \
  "file:///C:/w/banana-shot/design/youtube-thumbnails/doubles.html"
```
