# Photo of the author

`photo.jpg` is also the source of `src/main/resources/author/portrait.jpg`.
The app shows it in a circle in the personal requests of the author: keep
the scoreboard credit line, and keep the essential statistics on.

- The app image is a square crop of the head: the box (790, 115) to
  (1070, 395) of `photo.jpg`.
- Size: 168 × 168, JPEG. The popups show it at 56 × 56. Thus, it is sharp at
  300 % display scale.

`photo.jpg` is the source of `site/public/assets/author.webp`. The home page
shows this photo in the section "The author".

- The site image is a square crop of the head and shoulders: the box
  (735, 130) to (1135, 530) of `photo.jpg`.
- Size: 320 × 320. The page shows it at 160 × 160 in a circle. Thus, it is
  sharp on high-density screens.

## Make the site image

From the repository root:

```bash
python3 -c "
from PIL import Image
im = Image.open('design/author/photo.jpg').convert('RGB')
im.crop((735, 130, 1135, 530)).resize((320, 320), Image.LANCZOS) \
  .save('site/public/assets/author.webp', quality=82, method=6)
"
```
