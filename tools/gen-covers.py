#!/usr/bin/env python3
"""Génère des visuels de présentation 1080x1920 pour la boutique AoE Store."""

import argparse
import json
import math
import os
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps

WIDTH, HEIGHT = 1080, 1920
FONT_CANDIDATES = {
    "bold": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ],
    "regular": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ],
    "mono": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
        "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
    ],
}

WHITE = (245, 247, 255, 255)
MUTED = (166, 175, 199, 255)
DARK = (6, 7, 13)


def hex_to_rgb(value):
    value = (value or "#5B8CFF").strip().lstrip("#")
    if len(value) == 3:
        value = "".join(ch * 2 for ch in value)
    if len(value) != 6:
        value = "5B8CFF"
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def mix(color, other, ratio):
    return tuple(round(color[i] * (1 - ratio) + other[i] * ratio) for i in range(3))


def lighten(color, ratio):
    return mix(color, (255, 255, 255), ratio)


def darken(color, ratio):
    return mix(color, (0, 0, 0), ratio)


def load_font(kind, size):
    for candidate in FONT_CANDIDATES[kind]:
        if os.path.exists(candidate):
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def text_width(draw, text, font):
    box = draw.textbbox((0, 0), text, font=font)
    return box[2] - box[0]


def wrap_text(draw, text, font, max_width):
    words = str(text or "").split()
    if not words:
        return []
    lines = []
    current = words[0]
    for word in words[1:]:
        trial = f"{current} {word}"
        if text_width(draw, trial, font) <= max_width:
            current = trial
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def draw_wrapped(draw, text, xy, font, max_width, fill, spacing=12, align="left"):
    lines = wrap_text(draw, text, font, max_width)
    x, y = xy
    for line in lines:
        width = text_width(draw, line, font)
        line_x = x if align == "left" else x + (max_width - width) / 2
        draw.text((line_x, y), line, font=font, fill=fill)
        y += font.size + spacing
    return y


def fit_font(draw, text, kind, max_size, min_size, max_width):
    size = max_size
    while size > min_size:
        font = load_font(kind, size)
        if text_width(draw, text, font) <= max_width:
            return font
        size -= 4
    return load_font(kind, min_size)


def vertical_gradient(top, bottom):
    strip = Image.new("RGB", (1, HEIGHT))
    pixels = strip.load()
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        t = t * t * (3 - 2 * t)
        pixels[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return strip.resize((WIDTH, HEIGHT))


def radial_glow(color, max_alpha=120):
    small_w, small_h = WIDTH // 6, HEIGHT // 6
    glow = Image.new("L", (small_w, small_h), 0)
    pixels = glow.load()
    cx, cy = small_w / 2, small_h / 2
    radius = min(small_w, small_h) / 2
    for y in range(small_h):
        for x in range(small_w):
            dx = (x - cx) / radius
            dy = (y - cy) / radius
            distance = min(1.0, math.sqrt(dx * dx + dy * dy))
            pixels[x, y] = int(max_alpha * (1 - distance) ** 2.4)
    glow = glow.resize((WIDTH, HEIGHT), Image.BICUBIC)
    layer = Image.new("RGBA", (WIDTH, HEIGHT), color + (0,))
    layer.putalpha(glow)
    return layer


def rounded_icon(path, size, radius_ratio=0.22):
    if path and os.path.exists(path):
        image = Image.open(path).convert("RGBA")
        image = ImageOps.fit(image, (size, size), Image.LANCZOS)
        alpha = image.getchannel("A")
    else:
        image = Image.new("RGBA", (size, size), (255, 255, 255, 0))
        alpha = Image.new("L", (size, size), 255)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size - 1, size - 1), radius=int(size * radius_ratio), fill=255
    )
    image.putalpha(ImageChops.multiply(alpha, mask))
    return image


def paste_icon(canvas, icon_path, center, size, radius_ratio=0.22, shadow=True):
    icon = rounded_icon(icon_path, size, radius_ratio)
    x = int(center[0] - size / 2)
    y = int(center[1] - size / 2)
    if shadow:
        shadow_mask = icon.getchannel("A").filter(ImageFilter.GaussianBlur(size * 0.055))
        shadow = Image.new("RGBA", (size, size), (0, 0, 0, 150))
        shadow.putalpha(shadow_mask)
        canvas.alpha_composite(shadow, (x + int(size * 0.02), y + int(size * 0.045)))
    canvas.alpha_composite(icon, (x, y))


def draw_pill(canvas, draw, xy, text, font, accent, centered=False, width=None):
    padding_x, padding_y = 34, 18
    text_w = text_width(draw, text, font)
    box_w = width or (text_w + padding_x * 2)
    box_h = font.size + padding_y * 2
    x, y = xy
    if centered:
        x -= box_w / 2
    draw.rounded_rectangle(
        (x, y, x + box_w, y + box_h),
        radius=box_h / 2,
        fill=accent + (42,),
        outline=accent + (150,),
        width=2,
    )
    text_x = x + (box_w - text_w) / 2
    draw.text((text_x, y + padding_y - 3), text, font=font, fill=WHITE)
    return box_h


def draw_brand(draw, accent):
    font = load_font("bold", 30)
    text = "AOE STORE"
    width = text_width(draw, text, font)
    draw.rounded_rectangle((64, 64, 64 + width + 52, 64 + 58), radius=29, fill=(255, 255, 255, 18))
    draw.ellipse((84, 84, 102, 102), fill=lighten(accent, 0.15) + (255,))
    draw.text((118, 78), text, font=font, fill=(224, 230, 248, 235))


def draw_footer(draw, accent, developer, category):
    font = load_font("regular", 28)
    small = load_font("bold", 24)
    draw.line((64, HEIGHT - 148, WIDTH - 64, HEIGHT - 148), fill=(255, 255, 255, 28), width=2)
    draw.text((64, HEIGHT - 118), f"{developer}  •  {category}", font=font, fill=MUTED)
    label = "DISPONIBLE SUR AOE STORE"
    width = text_width(draw, label, small)
    draw.text((WIDTH - 64 - width, HEIGHT - 116), label, font=small, fill=lighten(accent, 0.25) + (255,))


def background(accent):
    top = mix(accent, DARK, 0.82)
    bottom = mix(darken(accent, 0.35), (3, 4, 9), 0.9)
    canvas = vertical_gradient(top, bottom).convert("RGBA")
    canvas.alpha_composite(radial_glow(accent, 105).resize((WIDTH, HEIGHT)))
    draw = ImageDraw.Draw(canvas, "RGBA")
    for index in range(5):
        radius = 240 + index * 150
        draw.ellipse(
            (WIDTH - 260 - radius, 120 - radius, WIDTH - 260 + radius, 120 + radius),
            outline=(255, 255, 255, 12),
            width=2,
        )
    draw.polygon(
        [(WIDTH - 300, 0), (WIDTH, 0), (WIDTH, 520), (WIDTH - 520, 0)],
        fill=accent + (18,),
    )
    return canvas, draw


def cover_hero(icon, name, tagline, version, accent, developer, category):
    canvas, draw = background(accent)
    draw_brand(draw, accent)
    paste_icon(canvas, icon, (WIDTH / 2, 560), 600)
    draw = ImageDraw.Draw(canvas, "RGBA")
    title_font = fit_font(draw, name, "bold", 92, 54, WIDTH - 160)
    title_w = text_width(draw, name, title_font)
    draw.text(((WIDTH - title_w) / 2, 1000), name, font=title_font, fill=WHITE)
    tagline_font = load_font("regular", 42)
    tagline_bottom = draw_wrapped(
        draw, tagline, (110, 1130), tagline_font, WIDTH - 220, MUTED, spacing=14, align="center"
    )
    pill_font = load_font("bold", 34)
    pill_text = f"Version {version}"
    draw_pill(canvas, draw, (WIDTH / 2, max(1420, tagline_bottom + 60)), pill_text, pill_font, accent, centered=True)
    category_font = load_font("bold", 26)
    label = f"{category.upper()}  •  {developer.upper()}"
    label_w = text_width(draw, label, category_font)
    draw.text(((WIDTH - label_w) / 2, max(1560, tagline_bottom + 180)), label, font=category_font, fill=lighten(accent, 0.3) + (255,))
    draw_footer(draw, accent, developer, category)
    return canvas


def cover_version(icon, name, tagline, version, accent, developer, category):
    canvas, draw = background(accent)
    draw_brand(draw, accent)
    label_font = load_font("bold", 32)
    draw.text((72, 300), "NOUVELLE VERSION", font=label_font, fill=lighten(accent, 0.35) + (255,))
    version_font = fit_font(draw, f"v{version}", "bold", 170, 90, 620)
    draw.text((62, 350), f"v{version}", font=version_font, fill=WHITE)
    paste_icon(canvas, icon, (845, 700), 280, radius_ratio=0.24)
    draw = ImageDraw.Draw(canvas, "RGBA")
    draw.line((72, 920, WIDTH - 72, 920), fill=(255, 255, 255, 36), width=2)
    name_font = fit_font(draw, name, "bold", 82, 50, WIDTH - 144)
    draw.text((72, 970), name, font=name_font, fill=WHITE)
    tagline_font = load_font("regular", 40)
    bottom = draw_wrapped(draw, tagline, (72, 1090), tagline_font, WIDTH - 180, MUTED, spacing=14)
    draw_pill(
        canvas,
        draw,
        (72, max(1320, bottom + 70)),
        "Mise à jour disponible",
        load_font("bold", 32),
        accent,
    )
    info_font = load_font("regular", 30)
    y = max(1470, bottom + 190)
    for line in (f"Version {version}", category, developer):
        draw.ellipse((76, y + 11, 90, y + 25), fill=lighten(accent, 0.25) + (255,))
        draw.text((112, y), line, font=info_font, fill=(206, 214, 235, 255))
        y += 62
    draw_footer(draw, accent, developer, category)
    return canvas


def cover_device(icon, name, tagline, version, accent, developer, category):
    canvas, draw = background(accent)
    draw_brand(draw, accent)
    phone_x, phone_y, phone_w, phone_h = 610, 360, 400, 880
    draw.rounded_rectangle(
        (phone_x - 10, phone_y - 10, phone_x + phone_w + 10, phone_y + phone_h + 10),
        radius=62,
        fill=(0, 0, 0, 90),
    )
    draw.rounded_rectangle(
        (phone_x, phone_y, phone_x + phone_w, phone_y + phone_h),
        radius=54,
        fill=(12, 14, 24, 255),
        outline=accent + (170,),
        width=3,
    )
    screen = vertical_gradient(mix(accent, DARK, 0.72), (9, 11, 20))
    screen = screen.resize((phone_w - 28, phone_h - 28))
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.size[0] - 1, screen.size[1] - 1), radius=42, fill=255)
    canvas.paste(screen, (phone_x + 14, phone_y + 14), mask)
    draw = ImageDraw.Draw(canvas, "RGBA")
    draw.rounded_rectangle(
        (phone_x + phone_w / 2 - 46, phone_y + 26, phone_x + phone_w / 2 + 46, phone_y + 40),
        radius=7,
        fill=(0, 0, 0, 160),
    )
    paste_icon(canvas, icon, (phone_x + phone_w / 2, phone_y + 330), 220, radius_ratio=0.23)
    draw = ImageDraw.Draw(canvas, "RGBA")
    screen_name = fit_font(draw, name, "bold", 40, 26, phone_w - 80)
    name_w = text_width(draw, name, screen_name)
    draw.text((phone_x + (phone_w - name_w) / 2, phone_y + 470), name, font=screen_name, fill=WHITE)
    screen_meta = load_font("regular", 24)
    meta = f"v{version}  •  {category}"
    meta_w = text_width(draw, meta, screen_meta)
    draw.text((phone_x + (phone_w - meta_w) / 2, phone_y + 530), meta, font=screen_meta, fill=MUTED)
    button_w, button_h = 230, 62
    button_x = phone_x + (phone_w - button_w) / 2
    button_y = phone_y + 610
    draw.rounded_rectangle(
        (button_x, button_y, button_x + button_w, button_y + button_h),
        radius=31,
        fill=lighten(accent, 0.12) + (255,),
    )
    button_font = load_font("bold", 26)
    button_text = "Installer"
    text_w = text_width(draw, button_text, button_font)
    draw.text(
        (button_x + (button_w - text_w) / 2, button_y + 13),
        button_text,
        font=button_font,
        fill=(8, 10, 20, 255),
    )

    left_x, left_width = 72, 480
    label_font = load_font("bold", 28)
    draw.text((left_x, 390), "DÉCOUVRIR", font=label_font, fill=lighten(accent, 0.35) + (255,))
    name_font = fit_font(draw, name, "bold", 78, 46, left_width)
    y = 440
    for line in wrap_text(draw, name, name_font, left_width):
        draw.text((left_x, y), line, font=name_font, fill=WHITE)
        y += name_font.size + 8
    tagline_font = load_font("regular", 34)
    y = draw_wrapped(draw, tagline, (left_x, y + 30), tagline_font, left_width, MUTED, spacing=12)
    y += 50
    feature_font = load_font("regular", 28)
    for feature in (f"Version {version}", category, developer):
        draw.ellipse((left_x + 2, y + 10, left_x + 16, y + 24), fill=lighten(accent, 0.25) + (255,))
        draw.text((left_x + 34, y), feature, font=feature_font, fill=(208, 216, 237, 255))
        y += 58
    draw_footer(draw, accent, developer, category)
    return canvas


def main():
    parser = argparse.ArgumentParser(description="Génère des visuels de présentation AoE Store")
    parser.add_argument("--icon", help="Chemin de l'icône PNG/JPEG")
    parser.add_argument("--output-dir", required=True, help="Dossier de sortie")
    parser.add_argument("--name", required=True)
    parser.add_argument("--tagline", default="")
    parser.add_argument("--version", default="1.0.0")
    parser.add_argument("--accent", default="#5B8CFF")
    parser.add_argument("--developer", default="")
    parser.add_argument("--category", default="Applications")
    args = parser.parse_args()

    accent = hex_to_rgb(args.accent)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    covers = [
        cover_hero(args.icon, args.name, args.tagline, args.version, accent, args.developer, args.category),
        cover_version(args.icon, args.name, args.tagline, args.version, accent, args.developer, args.category),
        cover_device(args.icon, args.name, args.tagline, args.version, accent, args.developer, args.category),
    ]
    paths = []
    for index, cover in enumerate(covers, start=1):
        destination = output_dir / f"{index}.png"
        cover.convert("RGB").save(destination, "PNG", optimize=True)
        paths.append(str(destination))

    print(json.dumps({"covers": paths, "accent": args.accent, "width": WIDTH, "height": HEIGHT}))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:  # noqa: BLE001
        print(f"gen-covers: {error}", file=sys.stderr)
        sys.exit(1)
