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

## highlights: the doubles highlights

- The same layout as `doubles`, with "BEST POINTS". "HIGHLIGHTS" in big
  letters is too wide: it covers the player.
- `highlights-frame.jpg` is the frame at 4:02 of the export
  `D:\Dima-Yana = Iliya-Marina-favs-balanced-4K.mp4`.
- This scoreboard has fewer columns, so its crop is narrower (805 px).

```bash
F="D:/Dima-Yana = Iliya-Marina-favs-balanced-4K.mp4"
ffmpeg -ss 242.0 -i "$F" -frames:v 1 -vf scale=2560:-2 -q:v 2 highlights-frame.jpg
ffmpeg -ss 242.0 -i "$F" -frames:v 1 -vf crop=805:330:96:96 highlights-scoreboard.png
```

## singles: the singles full match

- The same layout as `doubles`, with "SINGLES".
- `singles-frame.jpg` is the frame at 16:30 of the export
  `D:\Dima - Vika-points-best-4K.mp4`. The frame is zoomed 1.33×, so
  that the player is bigger. The zoom also removes the scoreboard of the
  frame and the black corner at the bottom right.
- `singles-scoreboard.png` has the same scale as in `doubles`. The names
  are short, so the scoreboard is smaller.

```bash
F="D:/Dima - Vika-points-best-4K.mp4"
ffmpeg -ss 990.0 -i "$F" -frames:v 1 -vf crop=2880:1620:400:480,scale=2560:-2 -q:v 2 singles-frame.jpg
ffmpeg -ss 990.0 -i "$F" -frames:v 1 -vf crop=615:265:96:96 singles-scoreboard.png
```

## Render a thumbnail

Change `doubles` to the name of the thumbnail.

```bash
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu \
  --hide-scrollbars --force-device-scale-factor=1 --window-size=1280,720 \
  --screenshot="C:/w/banana-shot/design/youtube-thumbnails/doubles.png" \
  "file:///C:/w/banana-shot/design/youtube-thumbnails/doubles.html"
```

## tutorial: the tutorial video

- The layout is different: a tilted app window at the left, and
  "1 HOUR → 17 MIN" at the right. The numbers are the real numbers of the
  match in the tutorial.
- The background is `singles-frame.jpg` with blur, so that the app window
  is the subject.
- `tutorial-app.jpg` is the app window from the frame that YouTube took
  from the tutorial (1280 × 720), without the Windows taskbar. A frame of
  the original screen recording is sharper.
- The site uses the same image as the poster of the tutorial:
  `site/public/assets/frames/video-tutorial.webp`.

```bash
ffmpeg -i tutorial.png -c:v libwebp -quality 80 ../../site/public/assets/frames/video-tutorial.webp
```

### tutorial-ru: the Russian version

- The same layout as `tutorial`, with "КАК МОНТИРОВАТЬ" and
  "1 ЧАС → 17 МИН".
- YouTube Studio lets you set a separate thumbnail for each language.
  Set this file as the thumbnail of the Russian version.
