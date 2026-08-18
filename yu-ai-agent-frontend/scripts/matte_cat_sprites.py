"""Knock out the paper-white background on companion cat sprites.

Always restores from cat-previews/v5-set before matting, so it is safe to re-run.
"""

from collections import deque
from pathlib import Path
from shutil import copy2

from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "src" / "assets" / "companion" / "cat"
BACKUP = Path(__file__).resolve().parents[2] / "cat-previews" / "v5-set"
QC_DIR = Path(__file__).resolve().parents[1] / "tmp-cat-matte-qc"


def luma_chroma(r, g, b):
    return (r + g + b) / 3.0, max(r, g, b) - min(r, g, b)


def is_paper(r, g, b, a):
    if a < 8:
        return True
    luma, chroma = luma_chroma(r, g, b)
    # Paper is almost white and almost gray. Cream fur is warmer/darker.
    return luma >= 249 and chroma <= 12


def is_paper_fringe(r, g, b, a):
    if a < 8:
        return True
    luma, chroma = luma_chroma(r, g, b)
    return luma >= 247 and chroma <= 14


def is_ground_glow(r, g, b, a, y, h):
    if y < int(h * 0.76):
        return False
    luma, chroma = luma_chroma(r, g, b)
    return luma >= 226 and chroma <= 26


def decontaminate_edge(r, g, b, a):
    luma, chroma = luma_chroma(r, g, b)
    if a <= 8 or chroma > 28 or luma < 225:
        return (r, g, b, a)
    white_mix = max(0.0, min(0.7, (luma - 222) / 36)) * max(0.0, 1 - chroma / 28)
    na = max(28, min(255, int(round(a * (1 - white_mix * 0.6)))))
    k = na / 255
    nr = max(0, min(255, int(round((r - 255 * (1 - k)) / k))))
    ng = max(0, min(255, int(round((g - 255 * (1 - k)) / k))))
    nb = max(0, min(255, int(round((b - 255 * (1 - k)) / k))))
    return (nr, ng, nb, na)


def matte(im):
    src = im.convert("RGBA")
    w, h = src.size
    px = src.load()
    bg = [[False] * w for _ in range(h)]
    queue = deque()

    def seed(x, y):
        r, g, b, a = px[x, y]
        if is_paper(r, g, b, a) and not bg[y][x]:
            bg[y][x] = True
            queue.append((x, y))

    for x in range(w):
        seed(x, 0)
        seed(x, h - 1)
    for y in range(h):
        seed(0, y)
        seed(w - 1, y)

    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h and not bg[ny][nx]:
                r, g, b, a = px[nx, ny]
                if is_paper(r, g, b, a):
                    bg[ny][nx] = True
                    queue.append((nx, ny))

    # Eat leftover paper stuck to fur tips only (not cream fur / lace / face).
    for _ in range(3):
        extra = []
        for y in range(h):
            for x in range(w):
                if bg[y][x]:
                    continue
                r, g, b, a = px[x, y]
                if not is_paper_fringe(r, g, b, a):
                    continue
                for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if 0 <= nx < w and 0 <= ny < h and bg[ny][nx]:
                        extra.append((x, y))
                        break
        if not extra:
            break
        for x, y in extra:
            bg[y][x] = True

    # Remove the baked paper glow under the paws so CSS shadow can sit on the chair.
    for _ in range(6):
        extra = []
        for y in range(h):
            for x in range(w):
                if bg[y][x]:
                    continue
                r, g, b, a = px[x, y]
                if not is_ground_glow(r, g, b, a, y, h):
                    continue
                for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if 0 <= nx < w and 0 <= ny < h and bg[ny][nx]:
                        extra.append((x, y))
                        break
        if not extra:
            break
        for x, y in extra:
            bg[y][x] = True

    out = Image.new("RGBA", (w, h))
    dest = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if bg[y][x]:
                dest[x, y] = (0, 0, 0, 0)
                continue
            near_bg = False
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < w and 0 <= ny < h and bg[ny][nx]:
                    near_bg = True
                    break
            dest[x, y] = decontaminate_edge(r, g, b, a) if near_bg else (r, g, b, a)
    return out


def pin_feet(im, size=1024, pad=36):
    bbox = im.getbbox()
    if not bbox:
        return im
    cropped = im.crop(bbox)
    cw, ch = cropped.size
    scale = min((size - 2 * pad) / cw, (size - 2 * pad) / ch)
    nw, nh = max(1, int(cw * scale)), max(1, int(ch * scale))
    resized = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(resized, ((size - nw) // 2, size - pad - nh), resized)
    return canvas


def qc_magenta(im):
    bg = Image.new("RGBA", im.size, (255, 0, 255, 255))
    return Image.alpha_composite(bg, im)


def restore_originals():
    files = sorted(BACKUP.glob("v5_*.png"))
    if len(files) < 6:
        raise SystemExit(f"Missing originals in {BACKUP}")
    ROOT.mkdir(parents=True, exist_ok=True)
    for src in files:
        copy2(src, ROOT / src.name)
        print(f"restored {src.name}")


def main():
    restore_originals()
    QC_DIR.mkdir(parents=True, exist_ok=True)
    for path in sorted(ROOT.glob("v5_*.png")):
        im = Image.open(path)
        corners = [im.convert("RGBA").getpixel(p) for p in (
            (0, 0), (im.width - 1, 0), (0, im.height - 1), (im.width - 1, im.height - 1)
        )]
        matted = pin_feet(matte(im))
        matted.save(path)
        qc_magenta(matted).convert("RGB").save(QC_DIR / f"{path.stem}_magenta.jpg", quality=85)
        out_corners = [matted.getpixel(p) for p in (
            (0, 0), (matted.width - 1, 0), (0, matted.height - 1), (matted.width - 1, matted.height - 1)
        )]
        print(f"{path.name} {im.size} in={corners[0]} out={out_corners[0]}")


if __name__ == "__main__":
    main()
