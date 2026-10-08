"""Makes an anonymized copy of the doubles frame for the social preview image.

- Blurs the faces of the three players that face the camera.
- Replaces the player names and the place label on the scoreboard with
  invented ones, in the same style.

Input: site/public/assets/frames/video-doubles.webp (1280 x 720).
Output: design/social-preview/frame-anonymized.webp.
Run from the repository root: python3 design/social-preview/anonymize-frame.py
Needs Pillow and the Inter font (Inter-Bold.otf).
"""

from pathlib import Path
from statistics import median

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "site/public/assets/frames/video-doubles.webp"
TARGET = ROOT / "design/social-preview/frame-anonymized.webp"
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"

# Ellipses (left, top, right, bottom) around the faces, in frame pixels.
FACES = [
    (636, 55, 659, 81),   # far court, yellow shirt
    (782, 16, 800, 37),   # far court, back
    (944, 92, 976, 126),  # near the net, cap
]

PLACE = "CITY CLUB : COURT 2"
TEAM_1 = "ALEX / EMMA"
TEAM_2 = "LUCAS / NINA"

SCOREBOARD_BLACK = (12, 10, 8)
HEADER_TEXT = (6, 28, 4)
NAME_TEXT = (255, 255, 255)


def blur_faces(image: Image.Image) -> None:
    blurred = image.filter(ImageFilter.GaussianBlur(6))
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    for box in FACES:
        draw.ellipse(box, fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(2))
    image.paste(blurred, (0, 0), mask)


def tab_color(image: Image.Image) -> tuple[int, int, int]:
    """The lime color of the place tab, without the text pixels."""
    pixels = [p for p in image.crop((35, 35, 183, 50)).get_flattened_data() if p[1] > 220]
    return tuple(int(median(p[i] for p in pixels)) for i in range(3))


def draw_spaced(draw: ImageDraw.ImageDraw, x: float, baseline: float, text: str,
                font: ImageFont.FreeTypeFont, fill, spacing: float) -> None:
    for char in text:
        draw.text((x, baseline), char, font=font, fill=fill, anchor="ls")
        x += font.getlength(char) + spacing


def replace_names(image: Image.Image) -> None:
    lime = tab_color(image)
    draw = ImageDraw.Draw(image)
    # Clear the old text. The rectangles stay inside the scoreboard cells.
    draw.rectangle((35, 35, 182, 50), fill=lime)
    draw.rectangle((48, 60, 170, 82), fill=SCOREBOARD_BLACK)
    draw.rectangle((48, 93, 195, 116), fill=SCOREBOARD_BLACK)

    header = ImageFont.truetype(FONT, 10)
    names = ImageFont.truetype(FONT, 15)
    draw_spaced(draw, 40.5, 47, PLACE, header, HEADER_TEXT, 0.9)
    draw.text((52, 76.5), TEAM_1, font=names, fill=NAME_TEXT, anchor="ls")
    draw.text((52, 110), TEAM_2, font=names, fill=NAME_TEXT, anchor="ls")


def main() -> None:
    image = Image.open(SOURCE).convert("RGB")
    blur_faces(image)
    replace_names(image)
    image.save(TARGET, quality=92, method=6)
    print(f"Wrote {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
