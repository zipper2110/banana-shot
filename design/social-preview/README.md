# Social preview image

`og-image.html` is the source of `site/public/assets/og-image.jpg`. Chats,
social networks, and forums show this image when a person shares a link to
the site.

- Size: 1200 × 630 (the Open Graph size).
- The frame is a real export of the app: `site/public/assets/frames/video-doubles.webp`.
- The logo is `site/public/assets/logo.svg`.
- The font is Inter. If Inter is not installed, the browser uses a different
  font, and the text can wrap differently.

## Render the image

Use Chromium in headless mode. In headless mode, the visible area is shorter
than the window. Thus, render a taller window and crop it to 1200 × 630.

```bash
cd design/social-preview
chromium --headless --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1200,900 --screenshot=og-tall.png "file://$PWD/og-image.html"
python3 -c "
from PIL import Image
im = Image.open('og-tall.png').convert('RGB').crop((0, 0, 1200, 630))
im.save('../../site/public/assets/og-image.jpg', quality=88, optimize=True, progressive=True)"
rm og-tall.png
```

Then look at the image. Keep the file under about 300 KB.

## Check after a deploy

- Paste a link to the site into a chat (for example Telegram or WhatsApp).
  The preview must show the image.
- Chats keep old previews for some days. A new image can show only for new
  links.
