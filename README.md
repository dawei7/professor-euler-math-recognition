# Professor Euler math recognition

A small model that reads a handwritten formula and writes it as LaTeX, trained on Google's
[MathWriting](https://github.com/google-research/google-research/tree/master/mathwriting) dataset and meant to run on a
phone or tablet. It is being built for [Professor Euler](https://euler.dawei7.com), a free mathematics study app: on a canvas
note, the learner circles a formula and gets back what was read and its result.

Work in progress: no weights are published yet, and the apps do not use it yet.

## What is here

| Path | What it does |
| --- | --- |
| `tools/mathwriting_pack.py` | Packs MathWriting's 630,000 InkML files into one compact file per split, read straight from the archive. |
| `tools/calc_synth.py` | Calculator-style training formulas (arithmetic with `·`, `:` and decimal commas, fractions, powers, roots, sin/cos/tan/ln/log, equations), typeset from single handwritten symbols cut out of MathWriting's training inks and vetted by a trained recognizer. The test sheet's formulas are never generated. |
| `recognizer/` | The recognizer: strokes rendered as an image, a MobileNetV2-style encoder (or a DenseNet-B one, as in WAP/DWAP) and a Transformer decoder that writes LaTeX tokens. About 4.3 M parameters. Training (`train.py`) and evaluation with beam search (`evaluate.py`). |
| `recognizer/fast.py`, `recognizer/export.py` | Reading as the apps will: the encoder once, then the decoder one token at a time with its keys and values kept (the same readings as training's decoder). The export writes two ONNX graphs for ONNX Runtime, with 8-bit versions, and checks them against PyTorch. |
| `reference/texteller_read.py` | Reads the same inks with [TexTeller](https://github.com/OleehyO/TexTeller) (about 300 M parameters), for comparison with a large open model. |
| `tools/sheet_lines.py`, `tools/numbered_sheet.py` | Cut a Professor Euler canvas sheet into lines or numbered formulas. |
| `test-sheet/formulas.md` | 38 calculator-style formulas to write by hand as a test: arithmetic, fractions, powers, roots, functions and equations. |

## Use it

```
python -m venv .venv
.venv/Scripts/python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
.venv/Scripts/python -m pip install pillow numpy onnx onnxruntime
curl -L -o data/mathwriting-2024.tgz https://storage.googleapis.com/mathwriting_data/mathwriting-2024.tgz
.venv/Scripts/python -I tools/mathwriting_pack.py data/mathwriting-2024.tgz data/packed
.venv/Scripts/python -m recognizer.train --data data/packed --out runs/m1
.venv/Scripts/python -m recognizer.evaluate --run runs/m1 --packed data/packed/test.npz
```

(On Linux or macOS the interpreter is `.venv/bin/python`. The TexTeller comparison also needs `transformers`.) Packing
takes about 16 minutes. On an RTX 4070 Ti SUPER the
default run of 120,000 steps takes about three hours.

## Results so far

Two runs, both with the MobileNetV2-style encoder and read with beam search of 5:

- `m1`: MathWriting only, 120,000 steps (about three hours).
- `m2`: `m1` trained on for 40,000 more steps (about 80 minutes), with 250,000 calculator-style formulas from
  `tools/calc_synth.py` as about 28 % of what it sees:
  `--init runs/m1/best.pt --packs train,synthetic,symbols,calc --steps 40000 --lr 2e-4 --warmup 1000`.

| | Exact | At most 1 token wrong | At most 2 |
| --- | --- | --- | --- |
| MathWriting test, a fixed random 2,000 of its inks: `m1` | 51.0 % | 69.9 % | 80.0 % |
| the same: `m2` | 52.1 % | 70.2 % | 80.0 % |
| The 38 formulas of the test sheet, in one person's handwriting: `m1` | 18 (47 %) | 33 (87 %) | 35 (92 %) |
| the same: `m2` | 26 (68 %) | 36 (95 %) | 37 (97 %) |
| TexTeller on the same 38 | 31 (82 %) | 36 (95 %) | |

The calculator-style formulas taught `m2` that a formula is mostly numbers: it no longer reads a 2 as Z, `2x` as `Z_{x}`,
`4y` as `A_{y}`, x as χ, a degree sign as 8 or the dot of `15\%\cdot80` as a minus. Eight of its twelve misses on the
test sheet are one digit: a 4 with a closed, pointed top, which it now reads as 7 (`m1` read it as A, 1 or 7). It still
reads a 5 as S and the dot between two fractions as a minus, loses the lower half of a double fraction and reads
`|−7|` as `1-71`, as TexTeller does with the last two.

### Speed

`recognizer/fast.py` decodes one token at a time with each layer's keys and values kept: the same readings as the
model's own beam search, about three times faster. `recognizer/export.py` writes it as two ONNX graphs. `m2` on one
thread of a desktop CPU (an otherwise idle machine), beam 5, the test sheet and 150 MathWriting test inks:

| | Size | Median | 90th percentile | Slowest | Same readings as PyTorch |
| --- | --- | --- | --- | --- | --- |
| PyTorch, float32 | 17 MB | 55 ms | 130 ms | 305 ms | |
| ONNX Runtime, float32 | 17.2 MB | 21 ms | 53 ms | 115 ms | 188 of 188 |
| ONNX Runtime, float32 encoder, 8-bit decoder (`step.int8mm.onnx`) | 8.8 MB | 19 ms | 45 ms | 108 ms | 185 of 188, all 38 of the test sheet |
| ONNX Runtime, all 8-bit | 4.6 MB | 129 ms | 285 ms | 697 ms | 168 of 188 |

The all-8-bit encoder is slower, because ONNX Runtime has no fast 8-bit convolution on this processor. Phones and
tablets are not measured yet.

## Design choices

- **On the device.** The model is small enough for a phone, so handwriting never has to leave it.
- **Rendered images, not raw strokes.** The ink is drawn at a size set by its own strokes, so a short `a+b` and a tall
  double fraction show their symbols at the same size, as on paper.
- **No GPL code**, so the model and its code can go into apps whose source is closed.

## Licences

- **This code:** MIT (see `LICENSE`).
- **MathWriting data:** © Google LLC, [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). Models
  trained on it will be published under the same licence: noncommercial use, with attribution, shared alike.
- **TexTeller:** Apache 2.0. It is only downloaded for the comparison, never redistributed here.

The encoder and decoder were written anew after the published designs (WAP/DWAP, BTTR), not copied. CoMER, PosFormer and
TAMER are not used, because their repositories publish no licence.

## Data

`data/`, `models/` and `runs/` stay out of the repository. Handwriting from real users is never committed. Scripts that
read downloaded archives run with `python -I`.
