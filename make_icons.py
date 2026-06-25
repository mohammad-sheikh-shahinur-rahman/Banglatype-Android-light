"""Generate BanglaType legacy launcher icons (pre-API-26 fallback) with PIL.
Draws a blue rounded-square tile with a white keyboard tablet, a stylized Bangla
glyph and key dashes -- a raster approximation of the adaptive vector icon."""
import os
from PIL import Image, ImageDraw, ImageFont

RES = "C:/Users/shahi/Downloads/bnaglatype/java/res"
# mipmap density -> launcher icon px size
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
BLUE_A = (30, 99, 240, 255)
BLUE_B = (21, 81, 214, 255)
WHITE = (255, 255, 255, 255)

# Try to find a Bangla-capable font for the glyph.
FONT_CANDIDATES = [
    "C:/Windows/Fonts/Nirmala.ttc",
    "C:/Windows/Fonts/Nirmala.ttf",
    "C:/Windows/Fonts/vrinda.ttf",
]


def find_font(size):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return None


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def gradient_rounded(size):
    """Blue diagonal-gradient rounded square, full tile."""
    S = size * 4  # supersample
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = Image.new("RGBA", (S, S))
    px = grad.load()
    for y in range(S):
        for x in range(S):
            t = (x + y) / (2 * S)
            px[x, y] = lerp(BLUE_A, BLUE_B, t)
    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    radius = int(S * 0.22)
    md.rounded_rectangle([0, 0, S - 1, S - 1], radius=radius, fill=255)
    img.paste(grad, (0, 0), mask)
    return img.resize((size, size), Image.LANCZOS), S


def draw_icon(size):
    img, S = gradient_rounded(size)
    big = img.resize((S, S), Image.LANCZOS)
    d = ImageDraw.Draw(big)

    # Keyboard tablet outline (white stroke rounded rect) in the center.
    m = S * 0.20
    tab = [m, S * 0.17, S - m, S - S * 0.17]
    sw = max(2, int(S * 0.018))
    d.rounded_rectangle(tab, radius=int(S * 0.10), outline=WHITE, width=sw)

    # Bangla glyph (ট) drawn with a font, centered in upper part of the tablet.
    font = find_font(int(S * 0.42))
    if font is not None:
        glyph = "ট"  # ট
        bbox = d.textbbox((0, 0), glyph, font=font)
        gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
        gx = (S - gw) / 2 - bbox[0]
        gy = S * 0.20 - bbox[1]
        d.text((gx, gy), glyph, font=font, fill=WHITE)

    # Two short rows of keyboard keys near the bottom.
    key_h = S * 0.045
    r = key_h / 2
    row1_y = S * 0.66
    row2_y = row1_y + key_h * 1.7
    xs = [S * 0.34, S * 0.46, S * 0.58]
    kw = S * 0.085
    for x in xs:
        d.rounded_rectangle([x, row1_y, x + kw, row1_y + key_h], radius=r, fill=WHITE)
    # wide spacebar-ish row
    d.rounded_rectangle([S * 0.40, row2_y, S * 0.40 + kw * 2.0, row2_y + key_h],
                        radius=r, fill=WHITE)

    return big.resize((size, size), Image.LANCZOS)


def round_mask(img):
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


for folder, px in SIZES.items():
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    icon = draw_icon(px)
    icon.save(os.path.join(d, "ic_launcher.png"))
    round_mask(icon).save(os.path.join(d, "ic_launcher_round.png"))
    print("wrote", folder, px)

print("done")
