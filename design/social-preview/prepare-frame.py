"""Makes the frame of the social preview image from a match frame.

1. Grades the colors: more contrast, richer colors, cool shadows, warm
   highlights, and a soft vignette.
2. Puts the original scoreboard back, so it keeps the colors of the app.
3. Replaces the player names and the place label on the scoreboard with
   invented ones, in the same style.
4. Keeps only the left part of the frame. There, the near player (the
   author) has the back to the camera. The far players and their faces are
   not in the output.

Input: a 1920 x 1080 frame of an export of the app. The input is not in Git,
because its scoreboard shows the real names of the players.
Output: design/social-preview/frame.webp.
Run from the repository root:
    python3 design/social-preview/prepare-frame.py <frame.png>
Needs Pillow, NumPy, and the Inter font (Inter-Bold.otf).
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "design/social-preview/frame.webp"
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"

FRAME_SIZE = (1920, 1080)
# The part of the frame that the image shows (left, top, right, bottom).
CROP = (0, 0, 944, 924)

PLACE = "CITY CLUB : COURT 2"
TEAM_1 = "ALEX / EMMA"
TEAM_2 = "LUCAS / NINA"

# The scoreboard, in frame pixels.
SCOREBOARD_TAB = (48, 48, 280, 80)
SCOREBOARD_PANEL = (48, 79, 418, 213)
SCOREBOARD_RADIUS = 6
TAB_TEXT_AREA = (56, 53, 274, 75)
NAME_AREAS = [(76, 92, 254, 121), (76, 144, 254, 172)]
PLACE_BASELINE = (62, 69)
NAME_BASELINES = [(80, 112), (79, 164)]
PLACE_FONT_SIZE = 13.5
PLACE_TEXT_WIDTH = 205  # Width of the original place label, for the letter spacing.
NAME_FONT_SIZE = 20

SCOREBOARD_BLACK = (12, 10, 8)
TAB_LIME = (190, 255, 63)
TAB_TEXT = (6, 28, 4)
NAME_TEXT = (255, 255, 255)


def grade(image: Image.Image) -> Image.Image:
    rgb = np.asarray(image).astype(np.float32) / 255.0

    # Contrast: a soft S-curve around the middle gray.
    s_curve = rgb * rgb * (3.0 - 2.0 * rgb)
    rgb = rgb + 0.55 * (s_curve - rgb)

    # Deeper blacks, and brighter midtones.
    rgb = np.clip((rgb - 0.03) / 0.97, 0.0, 1.0) ** 0.9

    # Richer colors: more saturation, with the same brightness.
    luma = (rgb @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32))[..., None]
    rgb = luma + 1.35 * (rgb - luma)

    # Split toning: cool shadows, warm highlights.
    shadows = np.clip(1.0 - luma * 2.0, 0.0, 1.0)
    highlights = np.clip(luma * 2.0 - 1.0, 0.0, 1.0)
    rgb = rgb + shadows * np.array([-0.015, 0.0, 0.025], dtype=np.float32)
    rgb = rgb + highlights * np.array([0.03, 0.012, -0.03], dtype=np.float32)

    # A soft vignette.
    height, width = rgb.shape[:2]
    ys, xs = np.mgrid[0:height, 0:width].astype(np.float32)
    distance = np.sqrt(((xs / width) - 0.5) ** 2 + ((ys / height) - 0.5) ** 2) / 0.7071
    rgb = rgb * (1.0 - 0.16 * distance[..., None] ** 2.2)

    return Image.fromarray((np.clip(rgb, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8))


def restore_scoreboard(graded: Image.Image, original: Image.Image) -> None:
    mask = Image.new("L", graded.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(SCOREBOARD_TAB, SCOREBOARD_RADIUS, fill=255)
    draw.rounded_rectangle(SCOREBOARD_PANEL, SCOREBOARD_RADIUS, fill=255)
    graded.paste(original, (0, 0), mask)


def place_spacing(font: ImageFont.FreeTypeFont, text: str) -> float:
    return (PLACE_TEXT_WIDTH - font.getlength(text)) / (len(text) - 1)


def draw_spaced(draw: ImageDraw.ImageDraw, x: float, baseline: float, text: str,
                font: ImageFont.FreeTypeFont, fill, spacing: float) -> None:
    for char in text:
        draw.text((x, baseline), char, font=font, fill=fill, anchor="ls")
        x += font.getlength(char) + spacing


def replace_names(image: Image.Image) -> None:
    draw = ImageDraw.Draw(image)
    draw.rectangle(TAB_TEXT_AREA, fill=TAB_LIME)
    for area in NAME_AREAS:
        draw.rectangle(area, fill=SCOREBOARD_BLACK)

    place_font = ImageFont.truetype(FONT, PLACE_FONT_SIZE)
    # Keep the letter spacing of the original label, and never less than 1 px.
    spacing = max(1.0, place_spacing(place_font, "BATUMI : COURT AIRPORT"))
    draw_spaced(draw, *PLACE_BASELINE, PLACE, place_font, TAB_TEXT, spacing)

    name_font = ImageFont.truetype(FONT, NAME_FONT_SIZE)
    for baseline, team in zip(NAME_BASELINES, (TEAM_1, TEAM_2)):
        draw.text(baseline, team, font=name_font, fill=NAME_TEXT, anchor="ls")


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Usage: prepare-frame.py <frame.png>")
    original = Image.open(sys.argv[1]).convert("RGB")
    if original.size != FRAME_SIZE:
        sys.exit(f"The frame must be {FRAME_SIZE[0]} x {FRAME_SIZE[1]}, not {original.size}.")

    image = grade(original)
    restore_scoreboard(image, original)
    replace_names(image)
    image.crop(CROP).save(TARGET, quality=92, method=6)
    print(f"Wrote {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
