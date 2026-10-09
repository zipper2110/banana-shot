# Photo of the author

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
