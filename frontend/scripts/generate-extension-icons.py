from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src" / "extension" / "public" / "icons"
BACKGROUND = (11, 18, 32, 255)
CYAN = (34, 211, 238, 255)
CYAN_DARK = (8, 145, 178, 255)


def draw_icon(size: int) -> Image.Image:
    scale = 8
    canvas_size = size * scale
    image = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    def points(values: list[tuple[float, float]]) -> list[tuple[int, int]]:
        return [(round(x * scale), round(y * scale)) for x, y in values]

    radius = max(2, round(size * 0.22)) * scale
    draw.rounded_rectangle((0, 0, canvas_size - 1, canvas_size - 1), radius=radius, fill=BACKGROUND)

    center = size / 2
    shield = [
        (center, size * 0.13),
        (size * 0.78, size * 0.25),
        (size * 0.74, size * 0.58),
        (center, size * 0.87),
        (size * 0.26, size * 0.58),
        (size * 0.22, size * 0.25),
    ]
    draw.polygon(points(shield), fill=CYAN_DARK)

    eye = [
        (size * 0.30, size * 0.50),
        (size * 0.40, size * 0.41),
        (size * 0.60, size * 0.41),
        (size * 0.70, size * 0.50),
        (size * 0.60, size * 0.59),
        (size * 0.40, size * 0.59),
    ]
    draw.polygon(points(eye), fill=CYAN)
    pupil_radius = max(1, size * 0.085)
    draw.ellipse(
        (
            round((center - pupil_radius) * scale),
            round((center - pupil_radius) * scale),
            round((center + pupil_radius) * scale),
            round((center + pupil_radius) * scale),
        ),
        fill=BACKGROUND,
    )

    return image.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    for size in (16, 32, 48, 128):
        draw_icon(size).save(ROOT / f"icon-{size}.png", optimize=True)


if __name__ == "__main__":
    main()
