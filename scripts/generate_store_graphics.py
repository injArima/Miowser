"""Generate reproducible Play icon and feature art from the app's browser/play mark."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "release-assets"
OUT.mkdir(exist_ok=True)
SCALE = 3
BG = (51, 71, 121)
WHITE = (255, 255, 255)
LIGHT = (196, 213, 255)


def mark(draw: ImageDraw.ImageDraw, x: int, y: int, size: int, color=WHITE) -> None:
    """The same simple window/play silhouette as the adaptive icon vector."""
    s = size / 108
    px = lambda v: round(x + v * s)
    py = lambda v: round(y + v * s)
    width = max(1, round(4.5 * s))
    draw.rounded_rectangle((px(25), py(26), px(83), py(82)),
                           radius=round(7 * s), outline=color, width=width)
    draw.rectangle((px(28), py(37), px(80), py(41)), fill=color)
    for cx in (34, 41):
        draw.ellipse((px(cx - 2), py(30), px(cx + 2), py(34)), fill=color)
    draw.polygon([(px(44), py(46)), (px(69), py(60)), (px(44), py(74))], fill=color)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    filename = "segoeuib.ttf" if bold else "segoeui.ttf"
    return ImageFont.truetype(str(Path("C:/Windows/Fonts") / filename), size)


icon = Image.new("RGBA", (512 * SCALE, 512 * SCALE), BG + (255,))
icon_draw = ImageDraw.Draw(icon)
mark(icon_draw, 0, 0, 512 * SCALE)
icon.resize((512, 512), Image.Resampling.LANCZOS).save(OUT / "play-icon-512.png", optimize=True)

graphic = Image.new("RGB", (1024 * SCALE, 500 * SCALE), BG)
d = ImageDraw.Draw(graphic)
d.rounded_rectangle((80 * SCALE, 68 * SCALE, 444 * SCALE, 432 * SCALE),
                    radius=88 * SCALE, fill=(67, 91, 150))
mark(d, 80 * SCALE, 68 * SCALE, 364 * SCALE)
d.text((470 * SCALE, 151 * SCALE), "Miowser", font=font(86 * SCALE, bold=True), fill=WHITE)
d.text((476 * SCALE, 276 * SCALE), "Browse. Save. Watch.",
       font=font(35 * SCALE), fill=LIGHT)
graphic.resize((1024, 500), Image.Resampling.LANCZOS).save(
    OUT / "feature-graphic-1024x500.png", optimize=True
)
