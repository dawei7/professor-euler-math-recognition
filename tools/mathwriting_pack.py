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


class Bucket:
    """The inks of one split as they are packed."""

    def __init__(self):
        self.points, self.stroke_ends, self.ink_ends, self.widths, self.symbols, self.labels, self.ids = [], [], [], [], [], [], []
        self.n = 0

    def add(self, kept, label, sample_id):
        """`kept` are strokes as `normalize` returns them."""
        for s in kept:
            self.points.append(s)
            self.n += len(s)
            self.stroke_ends.append(self.n)
        self.ink_ends.append(len(self.stroke_ends))
        self.widths.append(max(float(s[:, 0].max()) for s in kept))
        sizes = [float(np.ptp(s.astype(np.float32), axis=0).max()) for s in kept if len(s) > 1]
        self.symbols.append(float(np.median(sizes)) if sizes else 1.0)
        self.labels.append(label)
        self.ids.append(sample_id)

    def save(self, path):
        np.savez(path, points=np.concatenate(self.points), stroke_ends=np.array(self.stroke_ends, np.int64),
                 ink_ends=np.array(self.ink_ends, np.int64), widths=np.array(self.widths, np.float32),
                 symbols=np.array(self.symbols, np.float32), labels=np.array(self.labels), ids=np.array(self.ids))
        print(f"{path}: {len(self.ids)} inks, {len(self.stroke_ends)} strokes, {self.n} points")


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
            bucket = splits.setdefault(split, Bucket())
            bucket.add(normalize(strokes), label, sample_id)
            if len(bucket.ids) % 50000 == 0:
                print(split, len(bucket.ids), flush=True)
    for split, bucket in splits.items():
        bucket.save(out / f"{split}.npz")


if __name__ == "__main__":
    main()
