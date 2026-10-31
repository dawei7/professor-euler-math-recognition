# Professor Euler math recognition

Reads a handwritten formula on a Professor Euler canvas note and works out its result. This repository holds the model
work: data preparation, training, evaluation and the export of the finished model. The app side (running the model, the
calculator, the tool on the canvas) is built in the Professor Euler repository once the model is good enough.

## Owner decisions (31 October 2026)

- **Canvas notes only.** No recognition on exercise, frame or timestamp sheets: there an answer would appear before Check.
- **Circle a formula.** The learner circles a formula with the Calculate tool and gets what was read (typeset) and its
  result. No live recognition while writing.
- **A switch, off by default.**
- **On the device**, on Android and Windows. No server: the learner's handwriting never leaves the device for this.
- **No GPL code** in the app (the app's source is not public): the calculator is written in Kotlin or taken from an
  MIT/Apache library.

## Licences

| What | Licence | Consequence |
| --- | --- | --- |
| [MathWriting](https://github.com/google-research/google-research/tree/master/mathwriting) data (Google, 2024) | CC BY-NC-SA 4.0 | Noncommercial use only, which suits Professor Euler. A model trained on it is published under the same licence, with attribution in the app. |
| MathWriting example code | Apache 2.0 | |
| [BTTR](https://github.com/Green-Wood/BTTR) | MIT | May be used as a starting point, keeping its notice. Its published weights are trained on CROHME, whose data is offered for research, so they are a reference only. |
| [TexTeller](https://github.com/OleehyO/TexTeller) | Apache 2.0 | Too large for the device; a reference for how well open models read. |
| CoMER, PosFormer, TAMER | none published | Not used: without a licence, all rights are reserved. |

## Data policy

- `data/`, `models/`, `runs/` and `.venv/` are not committed (see `.gitignore`).
- **The owner's handwriting is learner data and is never committed.** It is read only, from the app's own files, into
  `data/owner/`.
- Downloaded archives stay in `data/`; scripts that read them run with `python -I`.

## Plan

1. **Feasibility.** Read the MathWriting test set and the owner's test sheet (`test-sheet/formulas.md`) with the open
   reference models. This shows how far open recognition gets on this handwriting.
2. **Train a small model** on MathWriting (train, synthetic, symbols), the strokes rendered as images, with an encoder
   and a decoder that writes LaTeX.
3. **Export** to ONNX, quantize, and measure size, speed and accuracy on the tablet, the phone and the oldest supported
   Android (API 29).
4. **App integration** in the Professor Euler repository: ONNX Runtime, the calculator, the Calculate tool, the switch,
   21 languages, tests.

Targets for the model (the owner may change them): at least 90 % of the test sheet's lines read exactly, at most 20 MB,
and at most 300 ms per formula on the tablet.

## Setup

```
python -m venv .venv
.venv/Scripts/python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
.venv/Scripts/python -m pip install pillow numpy onnx onnxruntime
curl -L -o data/mathwriting-2024.tgz https://storage.googleapis.com/mathwriting_data/mathwriting-2024.tgz
```
