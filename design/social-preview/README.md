# Social preview image

`og-image.html` is the source of `site/public/assets/og-image.jpg`. Chats,
social networks, and forums show this image when a person shares a link to
the site.

- Size: 1200 × 630 (the Open Graph size).
- The frame is `frame.webp`: a color-graded part of a real export of the
  app, from a doubles match of the author. `prepare-frame.py` makes it.
- The scoreboard shows invented names and an invented place. The source
  frame is not in Git, because its scoreboard shows the real names.
- The frame shows only the left part of the court. There, the near player
  has the back to the camera, and no face shows. The far players are not in
  `frame.webp`.
- The logo is `site/public/assets/logo.svg`.
- The font is Inter. If Inter is not installed, the browser uses a different
  font, and the text can wrap differently.

## Make the frame

Do this only when the source frame, the grade, or the invented names change.
The input is a 1920 × 1080 frame of an export of the app.

```bash
python3 design/social-preview/prepare-frame.py <frame.png>
```

Then look at the frame:

- The text must stay inside the scoreboard cells.
- The scoreboard must keep the colors of the app. The script grades only the
  video, not the scoreboard.
- No face must show.

The positions of the scoreboard, the text, and the crop are in pixels in the
script. A different source frame needs new positions.

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
