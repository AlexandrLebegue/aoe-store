#!/usr/bin/env python3
"""Génère les icônes de lancement du client Android AoE Store."""

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
FOREGROUND_DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

TOP = (24, 32, 66)
BOTTOM = (92, 66, 210)
ACCENT = (124, 156, 255)
WHITE = (255, 255, 255, 255)


def gradient(size):
    image = Image.new("RGB", (1, size))
    pixels = image.load()
    for y in range(size):
        t = y / max(1, size - 1)
        t = t * t * (3 - 2 * t)
        pixels[0, y] = tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
    return image.resize((size, size))


def glyph(size, color=WHITE):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    unit = size / 100
    left, right = 18 * unit, 82 * unit
    top, bottom = 30 * unit, 76 * unit
    draw.rounded_rectangle((left, 40 * unit, right, bottom), radius=8 * unit, fill=color)
    draw.polygon(
        [(left - 2 * unit, 40 * unit), (right + 2 * unit, 40 * unit), (74 * unit, 24 * unit), (26 * unit, 24 * unit)],
        fill=color,
    )
    for x in (34, 50, 66):
        draw.rounded_rectangle(
            (x * unit - 2.2 * unit, 47 * unit, x * unit + 2.2 * unit, 69 * unit),
            radius=2 * unit,
            fill=(TOP[0], TOP[1], TOP[2], 190),
        )
    draw.rounded_rectangle((44 * unit, 56 * unit, 56 * unit, 76 * unit), radius=2 * unit, fill=(TOP[0], TOP[1], TOP[2], 210))
    return layer


def legacy_icon(size, rounded=True):
    image = gradient(size).convert("RGBA")
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse(
        (-size * 0.2, -size * 0.35, size * 0.9, size * 0.75),
        fill=ACCENT + (55,),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.12))
    image.alpha_composite(glow)
    icon = glyph(size)
    image.alpha_composite(icon)
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    if rounded:
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
    else:
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    image.putalpha(mask)
    return image


def foreground(size):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glyph_size = int(size * 0.62)
    layer = glyph(glyph_size)
    offset = (size - glyph_size) // 2
    shadow = layer.getchannel("A").filter(ImageFilter.GaussianBlur(size * 0.02))
    shadow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shadow_layer.paste(Image.new("RGBA", (glyph_size, glyph_size), (0, 0, 0, 90)), (offset, offset + int(size * 0.012)), shadow)
    image.alpha_composite(shadow_layer)
    image.alpha_composite(layer, (offset, offset))
    return image


def main():
    for density, size in DENSITIES.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        legacy_icon(size, rounded=True).save(directory / "ic_launcher.png")
        legacy_icon(size, rounded=False).save(directory / "ic_launcher_round.png")
    for density, size in FOREGROUND_DENSITIES.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        foreground(size).save(directory / "ic_launcher_foreground.png")
    store_icon = legacy_icon(512, rounded=True)
    store_icon.save(ROOT / "android" / "store-icon.png")
    print("icônes générées")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:  # noqa: BLE001
        print(f"gen-android-icons: {error}", file=sys.stderr)
        sys.exit(1)
