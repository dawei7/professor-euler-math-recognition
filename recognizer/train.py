"""Trains the recognizer on packed MathWriting (train, synthetic and symbols), checking it on part of valid as it goes.

Usage: python -m recognizer.train --data data/packed --out runs/<name> [--steps 120000]
A run writes `vocab.json`, `log.tsv`, `last.pt` and `best.pt` (the best exact-match rate on the check set).
"""
import argparse
import json
import math
import pathlib
import time

import torch
import torch.nn.functional as F
from torch.utils.data import DataLoader

from .data import InkSet, Packed, SizedBatches, collate
from .model import Recognizer
from .vocab import BOS, EOS, PAD, Vocab


@torch.no_grad()
def greedy(model, pixels, mask, max_len=200):
    memory, memory_mask = model.encode(pixels, mask)
    tokens = torch.full((pixels.shape[0], 1), BOS, dtype=torch.long, device=pixels.device)
    finished = torch.zeros(pixels.shape[0], dtype=torch.bool, device=pixels.device)
    for _ in range(max_len):
        nxt = model.decode(memory, memory_mask, tokens)[:, -1].argmax(-1)
        nxt[finished] = PAD
        tokens = torch.cat([tokens, nxt[:, None]], 1)
        finished |= nxt == EOS
        if finished.all():
            break
    return tokens[:, 1:]


def strip(ids):
    out = []
    for i in ids:
        if i in (EOS, PAD):
            break
        out.append(i)
    return out


def check(model, loader, device):
    model.eval()
    right = total = 0
    for pixels, mask, tokens in loader:
        pixels, mask = pixels.to(device), mask.to(device)
        with torch.autocast("cuda", dtype=torch.bfloat16):
            guesses = greedy(model, pixels, mask, max_len=tokens.shape[1] + 10)
        for guess, truth in zip(guesses.tolist(), tokens.tolist()):
            right += strip(guess) == strip(truth[1:])
            total += 1
    model.train()
    return right / max(total, 1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/packed")
    parser.add_argument("--out", required=True)
    parser.add_argument("--steps", type=int, default=120000)
    parser.add_argument("--lr", type=float, default=4e-4)
    parser.add_argument("--warmup", type=int, default=3000)
    parser.add_argument("--budget", type=int, default=2_400_000, help="pixels per batch, padding included")
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--check-every", type=int, default=5000)
    parser.add_argument("--check-size", type=int, default=2000)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    data = pathlib.Path(args.data)
    train_packs = [Packed(data / f"{s}.npz") for s in ("train", "synthetic", "symbols")]
    vocab_path = out / "vocab.json"
    if vocab_path.exists():
        vocab = Vocab.load(vocab_path)
    else:
        vocab = Vocab.build(label for p in train_packs for label in p.labels)
        vocab.save(vocab_path)
    train_set = InkSet(train_packs, vocab, train=True)
    check_set = InkSet([Packed(data / "valid.npz")], vocab, train=False, limit=args.check_size)
    loader = DataLoader(train_set, batch_sampler=SizedBatches(train_set, args.budget), collate_fn=collate,
                        num_workers=args.workers, persistent_workers=True, pin_memory=True, prefetch_factor=4)
    check_loader = DataLoader(check_set, batch_sampler=SizedBatches(check_set, args.budget), collate_fn=collate,
                              num_workers=4, persistent_workers=True)
    print(f"vocab {len(vocab)}, training inks {len(train_set)}, check inks {len(check_set)}, batches per pass {len(loader)}",
          flush=True)

    device = "cuda"
    model = Recognizer(len(vocab)).to(device)
    print(f"parameters {sum(p.numel() for p in model.parameters()) / 1e6:.2f} M", flush=True)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    schedule = torch.optim.lr_scheduler.LambdaLR(optimizer, lambda s: min(1.0, (s + 1) / args.warmup) *
                                                 0.5 * (1 + math.cos(math.pi * min(s, args.steps) / args.steps)))
    step, best = 0, -1.0
    if args.resume and (out / "last.pt").exists():
        state = torch.load(out / "last.pt", map_location=device, weights_only=True)
        model.load_state_dict(state["model"])
        optimizer.load_state_dict(state["optimizer"])
        schedule.load_state_dict(state["schedule"])
        step, best = state["step"], state["best"]
    log = open(out / "log.tsv", "a", encoding="utf-8")
    config = {"vocab_size": len(vocab), "steps": args.steps, "lr": args.lr, "budget": args.budget}
    (out / "config.json").write_text(json.dumps(config, indent=1), encoding="utf-8")

    model.train()
    started, seen, running = time.time(), 0, 0.0
    while step < args.steps:
        for pixels, mask, tokens in loader:
            pixels, mask, tokens = pixels.to(device, non_blocking=True), mask.to(device, non_blocking=True), tokens.to(device)
            with torch.autocast("cuda", dtype=torch.bfloat16):
                logits = model(pixels, mask, tokens[:, :-1])
                loss = F.cross_entropy(logits.float().reshape(-1, logits.shape[-1]), tokens[:, 1:].reshape(-1),
                                       ignore_index=PAD, label_smoothing=0.1)
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            schedule.step()
            step += 1
            seen += pixels.shape[0]
            running = 0.98 * running + 0.02 * loss.item() if step > 1 else loss.item()
            if step % 200 == 0:
                rate = seen / (time.time() - started)
                print(f"step {step} loss {running:.3f} lr {schedule.get_last_lr()[0]:.2e} {rate:.0f} inks/s", flush=True)
            if step % args.check_every == 0 or step == args.steps:
                score = check(model, check_loader, device)
                log.write(f"{step}\t{running:.4f}\t{score:.4f}\n")
                log.flush()
                print(f"step {step} check exact {score:.2%}", flush=True)
                state = {"model": model.state_dict(), "optimizer": optimizer.state_dict(), "schedule": schedule.state_dict(),
                         "step": step, "best": max(best, score)}
                torch.save(state, out / "last.pt")
                if score > best:
                    best = score
                    torch.save({"model": model.state_dict(), "vocab_size": len(vocab), "step": step, "score": score},
                               out / "best.pt")
            if step >= args.steps:
                break


if __name__ == "__main__":
    main()
