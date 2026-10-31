"""Packs MathWriting's InkML files into one compact file per split, read straight from the archive.

Each ink is moved to the origin and scaled to a height of 1 (its width kept in proportion), and points closer together
than 0.5 % of that height are dropped, which keeps every stroke's shape. A split becomes `<out>/<split>.npz`:
`points` (float16, n x 2), `stroke_ends` (int64, the end of each stroke in points), `ink_ends` (int64, the end of each
ink in strokes), `widths` and `symbols` (float32, each ink's width and the median size of its strokes, for batching inks
of a similar rendered size) and `labels` / `ids` (the normalized LaTeX and the sample id of each ink).

Usage: python -I tools/mathwriting_pack.py <mathwriting-2024.tgz> <out dir>
"""
import pathlib
import sys
import tarfile
import xml.etree.ElementTree as ET

import numpy as np

NS = "{http://www.w3.org/2003/InkML}"
MIN_STEP = 0.005


def parse(data):
    root = ET.fromstring(data)
    notes = {a.get("type"): (a.text or "") for a in root.iter(NS + "annotation")}
    strokes = []
    for trace in root.iter(NS + "trace"):
        points = [tuple(map(float, p.split()[:2])) for p in trace.text.strip().split(",") if p.strip()]
        if points:
            strokes.append(np.array(points, dtype=np.float64))
    label = notes.get("normalizedLabel") or notes.get("label", "")
    return notes.get("sampleId", ""), label, strokes


def normalize(strokes):
    every = np.concatenate(strokes)
    low = every.min(axis=0)
    height = max(float(every[:, 1].max() - low[1]), 1e-6)
    kept = []
    for s in strokes:
        s = (s - low) / height
        out = [s[0]]
        for p in s[1:]:
            if np.abs(p - out[-1]).max() >= MIN_STEP:
                out.append(p)
        if len(out) > 1 and np.abs(s[-1] - out[-1]).max() > 0:
            out.append(s[-1])
        kept.append(np.array(out, dtype=np.float16))
    return kept


def main():
    archive, out = sys.argv[1], pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    splits = {}
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar:
            if not (member.isfile() and member.name.endswith(".inkml")):
                continue
            split = pathlib.PurePosixPath(member.name).parent.name
            sample_id, label, strokes = parse(tar.extractfile(member).read())
            if not strokes:
                continue
            bucket = splits.setdefault(split, {"points": [], "stroke_ends": [], "ink_ends": [], "widths": [], "symbols": [],
                                               "labels": [], "ids": [], "n": 0})
            kept = normalize(strokes)
            for s in kept:
                bucket["points"].append(s)
                bucket["n"] += len(s)
                bucket["stroke_ends"].append(bucket["n"])
            bucket["ink_ends"].append(len(bucket["stroke_ends"]))
            bucket["widths"].append(max(float(s[:, 0].max()) for s in kept))
            sizes = [float(np.ptp(s.astype(np.float32), axis=0).max()) for s in kept if len(s) > 1]
            bucket["symbols"].append(float(np.median(sizes)) if sizes else 1.0)
            bucket["labels"].append(label)
            bucket["ids"].append(sample_id)
            if len(bucket["ids"]) % 50000 == 0:
                print(split, len(bucket["ids"]), flush=True)
    for split, b in splits.items():
        np.savez(out / f"{split}.npz", points=np.concatenate(b["points"]), stroke_ends=np.array(b["stroke_ends"], np.int64),
                 ink_ends=np.array(b["ink_ends"], np.int64), widths=np.array(b["widths"], np.float32),
                 symbols=np.array(b["symbols"], np.float32), labels=np.array(b["labels"]), ids=np.array(b["ids"]))
        print(f"{split}: {len(b['ids'])} inks, {len(b['stroke_ends'])} strokes, {b['n']} points")


if __name__ == "__main__":
    main()
