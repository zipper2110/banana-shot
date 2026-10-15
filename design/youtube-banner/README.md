# YouTube channel banner

`banner.html` is the source of `banner.png`. This is the banner of the
BananaShot YouTube channel.

- Size: 2048 × 1152 (the size that YouTube recommends).
- Each device shows a different part of the banner:
  - TV: all of it.
  - Desktop: the center band, 2048 × 338.
  - Phone: the safe area, 1235 × 338 in the center.
  Thus, the logo, the name, and the text stay inside the safe area.
- Open `banner.html?guides` to see the band (red) and the safe area (yellow).
- The court frame is `../social-preview/frame.webp`. The mask hides the
  scoreboard in the frame. The page draws the scoreboard again, inside the
  safe area.
- The font is Segoe UI Variable (Windows 11).

## Render the image

```bash
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu \
  --hide-scrollbars --force-device-scale-factor=1 --window-size=2048,1152 \
  --screenshot="C:/w/banana-shot/design/youtube-banner/banner.png" \
  "file:///C:/w/banana-shot/design/youtube-banner/banner.html"
```

Then look at the image. YouTube accepts files up to 6 MB.
