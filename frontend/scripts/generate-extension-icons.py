from pathlib import Path
from shutil import copyfile

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src" / "extension" / "public" / "icons"
WEB_MARK = Path(__file__).resolve().parents[1] / "public" / "brand" / "linksentry-mark.png"
BACKGROUND = (34, 211, 238, 255)
MARK = (3, 19, 26, 255)


def load_mark() -> Image.Image:
    mark = Image.open(WEB_MARK).convert("RGBA")
    alpha = mark.getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("LinkSentry logo has no visible pixels")
    return mark.crop(bounds)


def draw_icon(size: int) -> Image.Image:
    scale = 8
    canvas_size = size * scale
    image = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    radius = max(2, round(size * 0.22)) * scale
    draw.rounded_rectangle((0, 0, canvas_size - 1, canvas_size - 1), radius=radius, fill=BACKGROUND)

    mark = load_mark()
    mark_size = round(canvas_size * 0.76)
    mark.thumbnail((mark_size, mark_size), Image.Resampling.LANCZOS)
    alpha = mark.getchannel("A")
    coloured_mark = Image.new("RGBA", mark.size, MARK)
    coloured_mark.putalpha(alpha)
    offset = ((canvas_size - mark.width) // 2, (canvas_size - mark.height) // 2)
    image.alpha_composite(coloured_mark, offset)

    return image.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    copyfile(WEB_MARK, ROOT / "logo-mark.png")
    for size in (16, 32, 48, 128):
        draw_icon(size).save(ROOT / f"icon-{size}.png", optimize=True)


if __name__ == "__main__":
    main()
