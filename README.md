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
| `kotlin/` | The reader in Kotlin for the apps (`reader/`: the same drawing to the pixel, ONNX Runtime and the same beam search, on Android and on desktop Java), its tests against the Python reader on the fixture of `tools/kotlin_fixture.py`; `calc/`: the calculator that answers a reading, exactly with fractions where it can, solving equations in one unknown, with no dependencies; and an Android app that times it on a device (`bench/`). |
| `reference/texteller_read.py` | Reads the same inks with [TexTeller](https://github.com/OleehyO/TexTeller) (about 300 M parameters), for comparison with a large open model. |
| `tools/sheet_lines.py`, `tools/numbered_sheet.py` | Cut a Professor Euler canvas sheet into lines or numbered formulas. |
| `test-sheet/formulas.md`, `formulas-2.md` | Calculator-style formulas to write by hand as a test (38 and 40): arithmetic, fractions, powers, roots, functions and equations. The second was written after the first had shaped the training data, so it is the blind one. |

## Use it

```
python -m venv .venv
.venv/Scripts/python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
.venv/Scripts/python -m pip install pillow numpy numba onnx onnxruntime
curl -L -o data/mathwriting-2024.tgz https://storage.googleapis.com/mathwriting_data/mathwriting-2024.tgz
.venv/Scripts/python -I tools/mathwriting_pack.py data/mathwriting-2024.tgz data/packed
.venv/Scripts/python -m recognizer.train --data data/packed --out runs/m1
.venv/Scripts/python -m recognizer.evaluate --run runs/m1 --packed data/packed/test.npz
```

(On Linux or macOS the interpreter is `.venv/bin/python`. The TexTeller comparison also needs `transformers`.) Packing
takes about 16 minutes. On an RTX 4070 Ti SUPER the
default run of 120,000 steps takes about three hours.

The Kotlin reader (JDK 17 or later, and the Android SDK for the bench):

```
.venv/Scripts/python -m recognizer.export --run runs/m6
.venv/Scripts/python -m tools.kotlin_fixture --run runs/m6
cd kotlin
./gradlew :reader:test -Pfixture=../runs/m6/onnx
./gradlew :bench:assembleRelease -PbenchAssets=../runs/m6/onnx
```

## Results so far

Every run uses the MobileNetV2-style encoder and is read with beam search of 5.

- `m1`: MathWriting only, 120,000 steps (about three hours).
- `m2` to `m5`: each trained on from the run before it for 40,000 more steps (about 75 minutes), with 250,000
  calculator-style formulas from `tools/calc_synth.py`, made anew for each run, as about 28 % of what it sees:
  `--init runs/m4/best.pt --packs train,synthetic,symbols,calc --steps 40000 --lr 2e-4 --warmup 1000`.
  - `m2`: the first calculator-style formulas.
  - `m3`: half the writers write every 4 closed at the top.
  - `m4`: a third of the writers write the 4 as a 1 with a long flag and a crossbar.
  - `m5`: one writer in three has a continental European hand (1s with a long flag, crossed 7s, crossed 4s), and an
    angle π/n is mostly π/2, π/3, π/4 or π/6.
  - `m6`: `m5`'s data, drawn as the apps draw it (`recognizer/data.py`: each pixel's ink from its distance to the
    stroke, which Kotlin draws to the same pixel). `m1` to `m5` were trained and are measured on PIL's drawing,
    `render_pil`, whose lines are thinner across than along an axis and which the apps could not match.

| | Exact | At most 1 token wrong | At most 2 |
| --- | --- | --- | --- |
| MathWriting test, a fixed random 2,000 of its inks: `m1` | 51.0 % | 69.9 % | 80.0 % |
| the same: `m2` | 52.1 % | 70.2 % | 80.0 % |
| the same: `m3` | 52.3 % | 71.6 % | 80.7 % |
| the same: `m4` | 52.6 % | 71.2 % | 80.8 % |
| the same: `m5` | 51.8 % | 70.2 % | 80.0 % |
| the same: `m6` | 52.8 % | 70.7 % | 80.7 % |
| The 38 formulas of the test sheet, in one person's handwriting: `m1` | 18 (47 %) | 33 (87 %) | 35 (92 %) |
| the same: `m2` | 26 (68 %) | 36 (95 %) | 37 (97 %) |
| the same: `m3` | 25 (66 %) | 36 (95 %) | 37 (97 %) |
| the same: `m4` | 31 (82 %) | 35 (92 %) | 36 (95 %) |
| the same: `m5` | 33 (87 %) | 36 (95 %) | 37 (97 %) |
| the same: `m6` | 34 (89 %) | 36 (95 %) | 37 (97 %) |
| TexTeller on the same 38 | 31 (82 %) | 36 (95 %) | |

The calculator-style formulas taught `m2` that a formula is mostly numbers: it no longer reads a 2 as Z, `2x` as `Z_{x}`,
`4y` as `A_{y}`, x as χ or a degree sign as 8. Eight of its twelve misses on the test sheet were one digit, the sheet's
4, which it read as 7. That 4 is a 1 with a long flag and a crossbar, beside 1s with a long flag and crossed 7s: a
continental European hand, which MathWriting hardly has. `m4` learned the 4 but took a 1 with a short stroke at its foot
for a 4; `m5`, which sees that hand's 1s beside fraction lines and minus signs, reads both. Its five misses: the dot
between two fractions read as a minus, the lower half of a double fraction lost, the 5 of `5!` read as S, `|−7|` read
as `1-71` (as TexTeller does with the last two), and the small 4 of `\tan(\frac{\pi}{4})` still read as 7. `m6` reads
the dot too.

**The test sheet is no longer a blind test.** The ways of writing a 4, a 1 and a 7 that `m3` to `m5` learn were chosen
after looking at how its writer writes them; its formulas themselves are never generated (`--exclude`). MathWriting's
test set stays blind: `m5` reads 76 of its inks right that `m4` missed and misses 92 that `m4` read, a difference within
its noise. A blind measure of the European hand needs a second sheet of new formulas: `test-sheet/formulas-2.md`.

### Speed

`recognizer/fast.py` decodes one token at a time with each layer's keys and values kept: the same readings as the
model's own beam search, about three times faster. `recognizer/export.py` writes it as two ONNX graphs. `m6` on one
thread of a desktop CPU (an otherwise idle machine), beam 5, the test sheet and 150 MathWriting test inks:

| | Size | Median | 90th percentile | Slowest | Same readings as PyTorch |
| --- | --- | --- | --- | --- | --- |
| PyTorch, float32 | 17 MB | 47 ms | 97 ms | 179 ms | |
| ONNX Runtime, float32 | 17.2 MB | 18 ms | 41 ms | 90 ms | 188 of 188 |
| ONNX Runtime, float32 encoder, 8-bit decoder (`step.int8mm.onnx`) | 8.8 MB | 14 ms | 35 ms | 73 ms | 187 of 188 |
| ONNX Runtime, all 8-bit | 4.6 MB | 117 ms | 249 ms | 597 ms | 170 of 188 |

The all-8-bit encoder is slower, because ONNX Runtime has no fast 8-bit convolution on this processor. The Kotlin reader
(`kotlin/reader`, ONNX Runtime 1.31.1 for Java) reads the same 188 inks to the same tokens as the Python one, from the
same pixels. Phones and tablets are not measured yet; `kotlin/bench` is the app that measures them.

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
