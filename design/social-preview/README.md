# Social preview image

`og-image.html` is the source of `site/public/assets/og-image.jpg`. Chats,
social networks, and forums show this image when a person shares a link to
the site.

- Size: 1200 × 630 (the Open Graph size).
- The frame is `frame-anonymized.webp`: a real export of the app
  (`site/public/assets/frames/video-doubles.webp`) with no personal data.
  `anonymize-frame.py` makes it. The script blurs the faces of the players
  and writes invented names and an invented place on the scoreboard. The
  frame on the home page does not change.
- The logo is `site/public/assets/logo.svg`.
- The font is Inter. If Inter is not installed, the browser uses a different
  font, and the text can wrap differently.

## Make the anonymized frame

Do this only when the source frame or the invented names change.

```bash
python3 design/social-preview/anonymize-frame.py
```

Then look at the frame: the faces must be blurred, and the text must stay
inside the scoreboard cells. The face positions are in pixels in the script.
A different source frame needs new positions.

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
