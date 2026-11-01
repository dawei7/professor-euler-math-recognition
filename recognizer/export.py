"""Exports a trained run for ONNX Runtime, as the apps will run it, and checks the export against PyTorch.

Two graphs, from recognizer/fast.py's Reader:
  encoder.onnx  pixels (1, 1, h, w), ink 1 on 0 -> cross (layers, 2, 1, heads, n, dh), each layer's keys and values
  step.onnx     tokens (B, 1), position (), cross, past (layers, 2, B, heads, t, dh) -> logp (B, vocab), past
and `*.int8.onnx`, the same with weights quantized to 8 bits (ONNX Runtime's dynamic quantization). `vocab.json` is
copied beside them.

Usage: python -m recognizer.export --run runs/<name> [--check 300]
Writes `<run>/onnx/`. `--check` reads the test sheet (if there) and that many MathWriting test inks with PyTorch and
with each ONNX pair on one CPU thread, and prints how often the readings agree and how long they take.
"""
import argparse
import json
import pathlib
import shutil
import time

import numpy as np
import onnxruntime as ort
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic

from .data import InkSet, Packed, render
from .evaluate import comparable, edits, load, truths
from .fast import Reader, read
from .vocab import BOS, EOS


class Encoder(torch.nn.Module):
    def __init__(self, reader):
        super().__init__()
        self.reader = reader

    def forward(self, pixels):
        return self.reader.memory(pixels)


class Step(torch.nn.Module):
    def __init__(self, reader):
        super().__init__()
        self.reader = reader

    def forward(self, tokens, position, cross, past):
        return self.reader.step(tokens, position, cross, past)


def export(run, out):
    model, vocab = load(run, "cpu")
    reader = Reader(model)
    out.mkdir(parents=True, exist_ok=True)
    pixels = torch.from_numpy(render([np.array([[0, 0], [1, 1.0]]), np.array([[0, 1], [1, 0.0]])]))[None, None]
    cross = reader.memory(pixels)
    tokens, position, past = torch.tensor([[BOS], [BOS]]), torch.tensor(0), reader.empty(2)
    # The exporter puts each wrapper back in the mode it had, and a new module is in training mode: dropout would stay on.
    with torch.no_grad():
        torch.onnx.export(Encoder(reader).eval(), (pixels,), out / "encoder.onnx", input_names=["pixels"],
                          output_names=["cross"], dynamic_axes={"pixels": {2: "h", 3: "w"}, "cross": {4: "n"}},
                          opset_version=17, dynamo=False)
        torch.onnx.export(Step(reader).eval(), (tokens, position, cross, past), out / "step.onnx",
                          input_names=["tokens", "position", "cross", "past"], output_names=["logp", "past_out"],
                          dynamic_axes={"tokens": {0: "b"}, "cross": {4: "n"}, "past": {2: "b", 4: "t"},
                                        "logp": {0: "b"}, "past_out": {2: "b", 4: "t1"}},
                          opset_version=17, dynamo=False)
    for name in ("encoder", "step"):
        quantize_dynamic(out / f"{name}.onnx", out / f"{name}.int8.onnx", weight_type=QuantType.QInt8)
    quantize_dynamic(out / "step.onnx", out / "step.int8mm.onnx", weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul"])
    shutil.copy(run / "vocab.json", out / "vocab.json")
    for f in sorted(out.iterdir()):
        print(f"{f.name}: {f.stat().st_size / 1e6:.2f} MB")
    return model, vocab, reader


class OnnxReader:
    """The beam search of fast.read over an ONNX pair, in NumPy: what the apps will do."""

    def __init__(self, encoder, step, threads=1):
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads
        options.inter_op_num_threads = 1
        self.encoder = ort.InferenceSession(str(encoder), options, providers=["CPUExecutionProvider"])
        self.step = ort.InferenceSession(str(step), options, providers=["CPUExecutionProvider"])
        shape = self.step.get_inputs()[3].shape
        self.layers, self.heads, self.dh = shape[0], shape[3], shape[5]

    def read(self, image, beam=5, max_len=200):
        cross = self.encoder.run(None, {"pixels": image[None, None].astype(np.float32)})[0]
        past = np.zeros((self.layers, 2, 1, self.heads, 0, self.dh), np.float32)
        alive, done = [([BOS], 0.0)], []
        for t in range(max_len):
            tokens = np.array([[seq[-1]] for seq, _ in alive], np.int64)
            logp, past = self.step.run(None, {"tokens": tokens, "position": np.array(t, np.int64), "cross": cross,
                                              "past": past})
            candidates = []
            for n, ((seq, score), row) in enumerate(zip(alive, logp)):
                top = np.argsort(-row, kind="stable")[:beam]
                candidates += [(seq + [int(tok)], score + float(row[tok]), n) for tok in top]
            candidates.sort(key=lambda c: c[1], reverse=True)
            alive, parents = [], []
            for seq, score, n in candidates:
                if seq[-1] == EOS:
                    done.append((seq[1:-1], score / (len(seq) - 1)))
                else:
                    alive.append((seq, score))
                    parents.append(n)
                if len(alive) == beam:
                    break
            if not alive or (len(done) >= beam and max(s for _, s in done) > alive[0][1] / len(alive[0][0])):
                break
            past = past[:, :, parents]
        done += [(seq[1:], score / len(seq)) for seq, score in alive]
        return max(done, key=lambda d: d[1])[0]


def check(run, out, vocab, reader, count, beam):
    sheet = pathlib.Path("data/owner/Erkennungstest-formulas")
    inks, expected = [], []
    if sheet.is_dir():
        truth = truths(sheet / "truth.md")
        for f in sorted(sheet.glob("*.json"), key=lambda p: int(p.stem)):
            inks.append([np.array(s) for s in json.loads(f.read_text(encoding="utf-8"))])
            expected.append(truth.get(int(f.stem), ""))
    test = InkSet([Packed("data/packed/test.npz")], vocab, train=False, limit=count)
    for pack, i in test.items:
        inks.append(pack.strokes(i))
        expected.append(pack.labels[i])
    images = [render(s) for s in inks]
    torch.set_num_threads(1)
    readers = {"pytorch": lambda im: read(reader, torch.from_numpy(im)[None, None], beam=beam)}
    for name, encoder, step in (("onnx float32", "encoder", "step"), ("onnx int8", "encoder.int8", "step.int8"),
                                ("onnx float32 encoder, int8 decoder", "encoder", "step.int8mm")):
        onnx = OnnxReader(out / f"{encoder}.onnx", out / f"{step}.onnx")
        readers[name] = lambda im, onnx=onnx: onnx.read(im, beam=beam)
    reference = None
    for name, fn in readers.items():
        times, readings = [], []
        for im in images:
            t = time.perf_counter()
            readings.append(fn(im))
            times.append(time.perf_counter() - t)
        exact = sum(edits(comparable(e), comparable(vocab.decode(r))) == 0 for e, r in zip(expected, readings))
        agree = "" if reference is None else f", same as pytorch {sum(a == b for a, b in zip(reference, readings))}/{len(images)}"
        reference = reference or readings
        ms = np.array(times) * 1000
        print(f"{name}: exact {exact}/{len(images)}{agree}; median {np.median(ms):.0f} ms, 90th percentile "
              f"{np.percentile(ms, 90):.0f} ms, max {ms.max():.0f} ms", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    parser.add_argument("--check", type=int, default=0, help="MathWriting test inks to compare on")
    parser.add_argument("--beam", type=int, default=5)
    args = parser.parse_args()
    run = pathlib.Path(args.run)
    out = run / "onnx"
    model, vocab, reader = export(run, out)
    if args.check:
        check(run, out, vocab, reader, args.check, args.beam)


if __name__ == "__main__":
    main()
