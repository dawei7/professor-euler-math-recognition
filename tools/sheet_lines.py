"""Cuts a Professor Euler sheet into its lines of writing and renders each one.

A sheet is the app's JSON (`work/<id>/sheets/<name>.json`): strokes of points in sheet units, 345 to the inch. Pen strokes
are grouped into lines by gaps in their vertical extent, so a stacked fraction stays one line. Each line is written as
`<n>.png` (black ink on white) and `<n>.json` (its strokes, in millimetres from the line's top left).

Usage: python -I tools/sheet_lines.py <sheet.json> <out dir> [--gap-mm 3.5]
"""
import argparse
import json
import pathlib

from PIL import Image, ImageDraw

UNITS_PER_MM = 345 / 25.4


def pen_strokes(sheet_path):
    root = json.loads(pathlib.Path(sheet_path).read_text(encoding="utf-8"))
    strokes = root["strokes"]
    if isinstance(strokes, str):
        strokes = json.loads(strokes)
    return [[(p["x"], p["y"]) for p in s["pts"]] for s in strokes if s.get("tool", "PEN") == "PEN" and s.get("pts")]


def lines_of(strokes, gap_units):
    """Strokes grouped into lines, top to bottom: a new line starts where nothing written spans a gap of gap_units."""
    spans = sorted(((min(y for _, y in s), max(y for _, y in s), s) for s in strokes), key=lambda t: t[0])
    lines, bottom = [], None
    for top, low, stroke in spans:
        if bottom is None or top - bottom > gap_units:
            lines.append([])
            bottom = low
        lines[-1].append(stroke)
        bottom = max(bottom, low)
    return lines


def render(strokes, height_px=128, margin_px=8, width_px=3):
    xs = [x for s in strokes for x, _ in s]
    ys = [y for s in strokes for _, y in s]
    x0, y0 = min(xs), min(ys)
    scale = (height_px - 2 * margin_px) / max(max(ys) - y0, 1.0)
    width = int((max(xs) - x0) * scale) + 2 * margin_px
    image = Image.new("L", (max(width, height_px), height_px), 255)
    draw = ImageDraw.Draw(image)
    for s in strokes:
        points = [((x - x0) * scale + margin_px, (y - y0) * scale + margin_px) for x, y in s]
        if len(points) == 1:
            (x, y), = points
            draw.ellipse((x - width_px / 2, y - width_px / 2, x + width_px / 2, y + width_px / 2), fill=0)
        else:
            draw.line(points, fill=0, width=width_px, joint="curve")
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sheet")
    parser.add_argument("out")
    parser.add_argument("--gap-mm", type=float, default=3.5)
    args = parser.parse_args()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    lines = lines_of(pen_strokes(args.sheet), args.gap_mm * UNITS_PER_MM)
    for n, strokes in enumerate(lines, start=1):
        render(strokes).save(out / f"{n}.png")
        x0 = min(x for s in strokes for x, _ in s)
        y0 = min(y for s in strokes for _, y in s)
        mm = [[[round((x - x0) / UNITS_PER_MM, 3), round((y - y0) / UNITS_PER_MM, 3)] for x, y in s] for s in strokes]
        (out / f"{n}.json").write_text(json.dumps(mm), encoding="utf-8")
    print(f"{len(lines)} lines written to {out}")


if __name__ == "__main__":
    main()
