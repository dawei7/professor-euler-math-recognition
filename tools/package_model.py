"""Packs a run's export as the file the apps download: `math-reader-<id>-<sha8>.zip`.

It holds `encoder.onnx` (float32), `step.onnx` (the decoder with 8-bit matrix products, the export's `step.int8mm.onnx`),
`vocab.json`, the weights' licence (CC BY-NC-SA 4.0, as MathWriting's) and a model card. Its bytes depend only on its
contents (fixed times and order), so packing the same export twice gives the same file and the same SHA-256, which the
library catalog names (`recognizer`: id, asset, bytes, sha256).

Usage: python -m tools.package_model --run runs/m6 --id m6
Writes `<run>/package/<asset>` and prints the catalog entry.
"""
import argparse
import hashlib
import json
import pathlib
import zipfile

LICENSE = """The weights of this model are licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0
International licence: https://creativecommons.org/licenses/by-nc-sa/4.0/

They were trained on MathWriting, (c) Google LLC, licensed CC BY-NC-SA 4.0:
https://github.com/google-research/google-research/tree/master/mathwriting

Model and code: https://github.com/dawei7/professor-euler-math-recognition (the code is MIT-licensed).
"""

CARD = """# Professor Euler handwriting model {id}

Reads a handwritten formula, drawn as `recognizer/data.py` (or `kotlin/reader`) draws its strokes, and writes it as LaTeX
tokens. A MobileNetV2-style encoder (`encoder.onnx`, float32) and a Transformer decoder read one token at a time with
each layer's keys and values kept (`step.onnx`, 8-bit matrix products), with beam search of 5 (`kotlin/reader`,
`recognizer/export.py`). About 4.3 M parameters.

Trained on MathWriting and on calculator-style formulas typeset from its handwritten symbols (`tools/calc_synth.py`).
Results are in the repository's README. It reads on the device; it sends nothing anywhere.

Licence: CC BY-NC-SA 4.0 (see LICENSE).
"""

FILES = [("encoder.onnx", "encoder.onnx"), ("step.int8mm.onnx", "step.onnx"), ("vocab.json", "vocab.json")]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    parser.add_argument("--id", required=True, help="the model's name in the apps, such as m6")
    args = parser.parse_args()
    export = pathlib.Path(args.run) / "onnx"
    out = pathlib.Path(args.run) / "package"
    out.mkdir(exist_ok=True)
    entries = [(name, (export / source).read_bytes()) for source, name in FILES]
    entries += [("LICENSE", LICENSE.encode()), ("MODEL.md", CARD.format(id=args.id).encode())]
    staging = out / "staging.zip"
    with zipfile.ZipFile(staging, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in entries:
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)
    data = staging.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    asset = f"math-reader-{args.id}-{sha[:8]}.zip"
    target = out / asset
    staging.replace(target)
    print(json.dumps({"id": args.id, "asset": asset, "bytes": len(data), "sha256": sha}, indent=2))
    print(f"wrote {target}")


if __name__ == "__main__":
    main()
