# Professor Euler math recognition

A small model that reads a handwritten formula and writes it as LaTeX, trained on Google's
[MathWriting](https://github.com/google-research/google-research/tree/master/mathwriting) dataset and meant to run on a
phone or tablet. It is being built for [Professor Euler](https://euler.dawei7.com), a free mathematics study app: on a canvas
note, the learner circles a formula and gets back what was read and its result.

Work in progress: the model is in training and no weights are published yet.

## What is here

| Path | What it does |
| --- | --- |
| `tools/mathwriting_pack.py` | Packs MathWriting's 630,000 InkML files into one compact file per split, read straight from the archive. |
| `recognizer/` | The recognizer: strokes rendered as an image, a MobileNetV2-style encoder (or a DenseNet-B one, as in WAP/DWAP) and a Transformer decoder that writes LaTeX tokens. About 4.3 M parameters. Training (`train.py`) and evaluation with beam search (`evaluate.py`). |
| `reference/texteller_read.py` | Reads the same inks with [TexTeller](https://github.com/OleehyO/TexTeller) (about 300 M parameters), for comparison with a large open model. |
| `tools/sheet_lines.py`, `tools/numbered_sheet.py` | Cut a Professor Euler canvas sheet into lines or numbered formulas. |
| `test-sheet/formulas.md` | 38 calculator-style formulas to write by hand as a test: arithmetic, fractions, powers, roots, functions and equations. |

## Use it

```
python -m venv .venv
.venv/Scripts/python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
.venv/Scripts/python -m pip install pillow numpy
curl -L -o data/mathwriting-2024.tgz https://storage.googleapis.com/mathwriting_data/mathwriting-2024.tgz
.venv/Scripts/python -I tools/mathwriting_pack.py data/mathwriting-2024.tgz data/packed
.venv/Scripts/python -m recognizer.train --data data/packed --out runs/m1
.venv/Scripts/python -m recognizer.evaluate --run runs/m1 --packed data/packed/test.npz
```

(On Linux or macOS the interpreter is `.venv/bin/python`. The TexTeller comparison also needs `transformers`.) Packing
takes about 16 minutes. On an RTX 4070 Ti SUPER the
default run of 120,000 steps takes about three hours.

## Results so far

On 38 formulas of the test sheet in one person's handwriting, TexTeller read 31 exactly and 36 with at most one wrong
token. Its mistakes: a 2 read as z, a 5 as s, a low multiplication dot as a decimal point, `|−7|` as `1-71`, and the lower
half of a double fraction lost. The small model's figures follow when its training ends.

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
