"""Cuts a sheet whose formulas are numbered at the left edge ("1)", "2)", ...) into its formulas, without the numbers.

Every stroke that starts within LABEL_MM of the left edge begins a number; numbers whose strokes overlap vertically are
one. Each other stroke belongs to the number nearest to its vertical middle, and a formula's number is everything left
of the first gap of at least GAP_MM. Writes `<n>.json` (strokes in mm, as tools/sheet_lines.py) and a `<n>.png` to
look at.

Usage: python -I tools/numbered_sheet.py <sheet.json> <out dir>
"""
import json
import pathlib
import sys

from sheet_lines import UNITS_PER_MM, pen_strokes, render

LABEL_MM = 6.0
GAP_MM = 1.8


def box(stroke):
    xs = [x for x, _ in stroke]
    ys = [y for _, y in stroke]
    return min(xs), min(ys), max(xs), max(ys)


def main():
    strokes = pen_strokes(sys.argv[1])
    out = pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    left = min(box(s)[0] for s in strokes)
    starts = sorted((box(s) for s in strokes if box(s)[0] - left < LABEL_MM * UNITS_PER_MM), key=lambda b: b[1])
    numbers = []
    for b in starts:
        if numbers and b[1] < numbers[-1][1]:
            numbers[-1] = (numbers[-1][0], max(numbers[-1][1], b[3]))
        else:
            numbers.append((b[1], b[3]))
    middles = [(top + bottom) / 2 for top, bottom in numbers]
    lines = [[] for _ in numbers]
    for s in strokes:
        _, top, _, bottom = box(s)
        middle = (top + bottom) / 2
        lines[min(range(len(middles)), key=lambda n: abs(middles[n] - middle))].append(s)
    for n, line in enumerate(lines, start=1):
        line.sort(key=lambda s: box(s)[0])
        reach, cut = box(line[0])[2], len(line)
        for k, s in enumerate(line[1:], start=1):
            if box(s)[0] - reach >= GAP_MM * UNITS_PER_MM:
                cut = k
                break
            reach = max(reach, box(s)[2])
        formula = line[cut:]
        x0 = min(box(s)[0] for s in formula)
        y0 = min(box(s)[1] for s in formula)
        mm = [[[round((x - x0) / UNITS_PER_MM, 3), round((y - y0) / UNITS_PER_MM, 3)] for x, y in s] for s in formula]
        (out / f"{n}.json").write_text(json.dumps(mm), encoding="utf-8")
        render(formula, height_px=96).save(out / f"{n}.png")
    print(f"{len(lines)} numbered formulas written to {out}")


if __name__ == "__main__":
    main()
