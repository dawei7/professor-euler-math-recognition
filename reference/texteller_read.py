"""Reads formula strokes with TexTeller (Apache 2.0, about 300 M parameters), as a reference for what a large open model
reads. Its input is prepared as TexTeller's own code does (texteller/utils/image.py): grayscale, white border trimmed,
resized to fit 448 x 448, normalized, padded.

Usage: python -m reference.texteller_read <lines dir> <truth.md> [<model dir>]   (model dir defaults to models/texteller)
"""
import json
import pathlib
import sys

import numpy as np
import torch
from PIL import Image
from torchvision.transforms import v2
from transformers import RobertaTokenizerFast, VisionEncoderDecoderModel

from recognizer.data import render
from recognizer.evaluate import comparable, edits, truths

MEAN, STD, SIZE = 0.9545467, 0.15394445, 448


def prepare(strokes):
    ink = render(strokes)  # ink 1 on 0, symbols about 28 px high
    image = Image.fromarray((255 - ink * 255).astype(np.uint8))
    rows, cols = np.nonzero(np.asarray(image) < 240)
    image = image.crop((cols.min(), rows.min(), cols.max() + 1, rows.max() + 1))
    x = v2.Compose([v2.ToImage(), v2.ToDtype(torch.uint8, scale=True),
                    v2.Resize(size=SIZE - 1, interpolation=v2.InterpolationMode.BICUBIC, max_size=SIZE, antialias=True),
                    v2.ToDtype(torch.float32, scale=True), v2.Normalize(mean=[MEAN], std=[STD])])(image)
    return v2.functional.pad(x, padding=[0, 0, SIZE - x.shape[2], SIZE - x.shape[1]])


def main():
    lines, truth = pathlib.Path(sys.argv[1]), truths(sys.argv[2])
    model_dir = sys.argv[3] if len(sys.argv) > 3 else "models/texteller"
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = VisionEncoderDecoderModel.from_pretrained(model_dir, use_safetensors=True).to(device).eval()
    tokenizer = RobertaTokenizerFast.from_pretrained(model_dir)
    rows = []
    for f in sorted(lines.glob("*.json"), key=lambda p: int(p.stem)):
        strokes = [np.array(s, dtype=np.float64) for s in json.loads(f.read_text(encoding="utf-8"))]
        with torch.no_grad():
            out = model.generate(prepare(strokes)[None].to(device), max_new_tokens=256, num_beams=3,
                                 pad_token_id=tokenizer.pad_token_id, eos_token_id=tokenizer.eos_token_id,
                                 bos_token_id=tokenizer.bos_token_id)
        reading = tokenizer.decode(out[0], skip_special_tokens=True).replace("\\displaystyle", "").strip()
        expected = truth.get(int(f.stem), "")
        rows.append((int(f.stem), expected, reading, edits(comparable(expected), comparable(reading))))
    for n, t, r, e in rows:
        print(f"{n}\t{e}\t{t}\t{r}")
    n = len(rows)
    print(f"exact {sum(e == 0 for *_, e in rows)}/{n}, within 1 edit {sum(e <= 1 for *_, e in rows)}/{n}")


if __name__ == "__main__":
    main()
