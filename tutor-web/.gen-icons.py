"""Generate PWA icons for the tutor surface.

Black square + yellow brutalist 'J/' mark. Matches the existing
`bg-black text-yellow-300` header strip identity. Outputs:
- public/icon-192.png (homescreen)
- public/icon-512.png (splash)
- public/icon-maskable-512.png (Android adaptive icon — 80% safe zone)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "public"
OUT.mkdir(exist_ok=True)

BLACK = (0, 0, 0, 255)
YELLOW = (253, 224, 71, 255)  # tailwind yellow-300


def font_at(size_px: int) -> ImageFont.FreeTypeFont:
    candidates = [
        "C:/Windows/Fonts/consolab.ttf",
        "C:/Windows/Fonts/consola.ttf",
        "C:/Windows/Fonts/cour.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size_px)
    return ImageFont.load_default()


def draw_icon(size: int, mark_scale: float = 0.55) -> Image.Image:
    img = Image.new("RGBA", (size, size), BLACK)
    draw = ImageDraw.Draw(img)
    font = font_at(int(size * mark_scale))
    text = "J/"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    cx = (size - tw) // 2 - bbox[0]
    cy = (size - th) // 2 - bbox[1]
    draw.text((cx, cy), text, font=font, fill=YELLOW)
    return img


for size in (192, 512):
    draw_icon(size).save(OUT / f"icon-{size}.png")

# Maskable variant: scale mark to 60% so the OS-side rounded mask doesn't clip it.
draw_icon(512, mark_scale=0.42).save(OUT / "icon-maskable-512.png")
print("wrote", [str(p.name) for p in OUT.glob("icon-*.png")])
