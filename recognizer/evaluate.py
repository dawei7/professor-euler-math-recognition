"""Reads inks with a trained recognizer and scores the readings.

Usage:
  python -m recognizer.evaluate --run runs/<name> --packed data/packed/test.npz [--limit 2000]
  python -m recognizer.evaluate --run runs/<name> --lines data/owner/<sheet> --truth test-sheet/formulas.md
`--lines` is a folder of `<n>.json` lines from tools/sheet_lines.py; `--truth` gives line n the formula numbered n.
Writes `<run>/readings-<name>.tsv` (truth, reading, edits) and prints the share read exactly and within one or two edits.
"""
import argparse
import json
import pathlib
import re

import numpy as np
import torch

from .data import InkSet, Packed, collate, render
from .model import Recognizer
from .vocab import Vocab, tokenize

FUNCTIONS = ("sin", "cos", "tan", "cot", "log", "ln", "exp", "lim", "max", "min", "det", "deg")


def comparable(latex):
    """Tokens as MathWriting's normalized labels write them: function names as letters, no \\left / \\right."""
    latex = re.sub(r"\\(left|right)(?![a-zA-Z])", "", latex)
    latex = re.sub(r"\\(" + "|".join(FUNCTIONS) + r")(?![a-zA-Z])", r"\1", latex)
    return tokenize(latex)


def edits(a, b):
    row = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        prev, row[0] = row[0], i
        for j, y in enumerate(b, 1):
            prev, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, prev + (x != y))
    return row[-1]


def truths(path):
    """The Expected LaTeX column of the test sheet's table, by formula number."""
    out = {}
    for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) >= 3 and cells[0].isdigit():
            out[int(cells[0])] = cells[-1].strip("`")
    return out


def load(run, device):
    run = pathlib.Path(run)
    vocab = Vocab.load(run / "vocab.json")
    state = torch.load(run / "best.pt", map_location=device, weights_only=True)
    model = Recognizer(len(vocab)).to(device)
    model.load_state_dict(state["model"])
    model.eval()
    return model, vocab


def read(model, vocab, strokes_list, device, beam):
    readings = []
    for strokes in strokes_list:
        image = torch.from_numpy(render(strokes))
        pixels, mask, _ = collate([(image, torch.tensor([0]))])
        with torch.autocast("cuda", dtype=torch.bfloat16):
            ids = model.beam_search(pixels.to(device), mask.to(device), beam=beam)[0]
        readings.append(vocab.decode(ids))
    return readings


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    parser.add_argument("--packed")
    parser.add_argument("--lines")
    parser.add_argument("--truth")
    parser.add_argument("--limit", type=int, default=2000)
    parser.add_argument("--beam", type=int, default=5)
    args = parser.parse_args()
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model, vocab = load(args.run, device)

    if args.packed:
        pack = Packed(args.packed)
        chosen = InkSet([pack], vocab, train=False, limit=args.limit).items
        strokes_list = [pack.strokes(i) for _, i in chosen]
        expected = [pack.labels[i] for _, i in chosen]
        name = pathlib.Path(args.packed).stem
    else:
        files = sorted(pathlib.Path(args.lines).glob("*.json"), key=lambda p: int(p.stem))
        strokes_list = [[np.array(s, dtype=np.float64) for s in json.loads(f.read_text(encoding="utf-8"))] for f in files]
        table = truths(args.truth) if args.truth else {}
        expected = [table.get(int(f.stem), "") for f in files]
        name = pathlib.Path(args.lines).name

    readings = read(model, vocab, strokes_list, device, args.beam)
    rows = [(t, r, edits(comparable(t), comparable(r))) for t, r in zip(expected, readings)]
    out = pathlib.Path(args.run) / f"readings-{name}.tsv"
    out.write_text("".join(f"{t}\t{r}\t{e}\n" for t, r, e in rows), encoding="utf-8")
    scored = [e for t, _, e in rows if t]
    if scored:
        n = len(scored)
        print(f"{name}: {n} inks, exact {sum(e == 0 for e in scored) / n:.1%}, within 1 edit {sum(e <= 1 for e in scored) / n:.1%}, "
              f"within 2 {sum(e <= 2 for e in scored) / n:.1%}")
    print(f"readings in {out}")


if __name__ == "__main__":
    main()
