# YouTube banner: personal channel

`banner.html` is the source of `banner.png`. This is the banner of the
personal YouTube channel of the author (@leetvin). The author posts the
outreach comments from this channel (`docs/marketing/strategy.md`,
section 5), so the banner shows the link to BananaShot.

- Size: 2048 × 1152. The safe area and the desktop band are the same as in
  `../youtube-banner/README.md`. Open `banner.html?guides` to see them.
- The court is `../youtube-thumbnails/singles-frame.jpg`, a real frame of
  the singles match. The scoreboard is drawn again with the real score of
  that frame.
- The font is Segoe UI Variable (Windows 11).

## Render the image

```bash
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu \
  --hide-scrollbars --force-device-scale-factor=1 --window-size=2048,1152 \
  --screenshot="C:/w/banana-shot/design/youtube-banner-personal/banner.png" \
  "file:///C:/w/banana-shot/design/youtube-banner-personal/banner.html"
```

YouTube accepts files up to 6 MB.
