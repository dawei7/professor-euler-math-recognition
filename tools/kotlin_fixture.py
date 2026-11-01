"""Inks, their images and their readings by the ONNX export, to hold the Kotlin reader (`kotlin/`) to the Python one.

Usage: python -m tools.kotlin_fixture --run runs/<name> [--count 150]
Writes, beside the export in `<run>/onnx/` (which stays out of the repository with the run):
  fixture.json  {"inks": [{"set", "label", "strokes": [[x0, y0, x1, y1, ...], ...], "reading": [token ids]}]}: the test
                sheet's inks if it is there, then `--count` MathWriting test inks; readings by encoder.onnx and
                step.int8mm.onnx, beam 5
  fixture.bin   each ink's image as `recognizer.data.render` draws it: height and width as little-endian int32, then
                the pixels row by row as little-endian float32
"""
import argparse
import json
import pathlib
import struct

import numpy as np

from recognizer.data import InkSet, Packed, render
from recognizer.export import OnnxReader
from recognizer.vocab import Vocab


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    parser.add_argument("--count", type=int, default=150)
    args = parser.parse_args()
    out = pathlib.Path(args.run) / "onnx"
    vocab = Vocab.load(out / "vocab.json")
    inks = []
    sheet = pathlib.Path("data/owner/Erkennungstest-formulas")
    if sheet.is_dir():
        for f in sorted(sheet.glob("*.json"), key=lambda p: int(p.stem)):
            inks.append(("sheet", "", [np.array(s, dtype=np.float64) for s in json.loads(f.read_text(encoding="utf-8"))]))
    for pack, i in InkSet([Packed("data/packed/test.npz")], vocab, train=False, limit=args.count).items:
        inks.append(("mathwriting", pack.labels[i], pack.strokes(i)))
    reader = OnnxReader(out / "encoder.onnx", out / "step.int8mm.onnx")
    records = []
    with open(out / "fixture.bin", "wb") as images:
        for kind, label, strokes in inks:
            image = render(strokes)
            images.write(struct.pack("<ii", *image.shape))
            images.write(image.astype("<f4").tobytes())
            records.append({"set": kind, "label": label, "strokes": [s.reshape(-1).tolist() for s in strokes],
                            "reading": [int(t) for t in reader.read(image)]})
    (out / "fixture.json").write_text(json.dumps({"inks": records}), encoding="utf-8")
    print(f"{len(records)} inks in {out / 'fixture.json'} and fixture.bin")


if __name__ == "__main__":
    main()
