"""Inks as the model sees them: strokes rendered as an anti-aliased grayscale image, ink white on black.

The scale comes from the writing, not the box: the median size of a stroke is drawn about SYMBOL_PX high, so a short
`a+b` and a tall double fraction show their symbols at the same size, as on a real sheet.

Each pixel's ink is how far its centre lies within a line of the pen's width with round ends (`draw`), so the apps draw
exactly the same pixels in Kotlin without a drawing library. `render_pil`, the drawing of m1 to m5, is kept to measure
them as they were trained.
"""
import math
import pathlib
import random

import numpy as np
import torch
from numba import njit
from PIL import Image, ImageDraw
from torch.utils.data import Dataset

from .vocab import PAD

SYMBOL_PX = 28
MAX_H, MAX_W = 256, 1280
MARGIN = 6
SUPERSAMPLE = 2
PEN = 2.4  # the pen's width in pixels when reading; training varies it


def symbol_size(strokes):
    sizes = [float(np.ptp(s, axis=0).max()) for s in strokes if len(s) > 1]
    return float(np.median(sizes)) if sizes else 1.0


def augment(strokes, rng):
    """A small random slant, rotation and stretch of the whole ink, and a little wobble along each stroke."""
    angle = math.radians(rng.uniform(-4, 4))
    shear = rng.uniform(-0.2, 0.2)
    sx = rng.uniform(0.85, 1.15)
    m = np.array([[math.cos(angle), -math.sin(angle)], [math.sin(angle), math.cos(angle)]]) @ np.array([[sx, shear], [0, 1]])
    size = symbol_size(strokes)
    out = []
    for s in strokes:
        s = s @ m.T
        if len(s) > 3:
            wobble = np.cumsum(np.random.default_rng(rng.getrandbits(32)).normal(0, 0.004 * size, s.shape), axis=0)
            s = s + wobble - wobble.mean(axis=0)
        out.append(s)
    return out


@njit(cache=True)
def draw(image, pts, r):
    """Inks one stroke, (n, 2) pixel coordinates: each pixel whose centre lies within r + 0.5 of a segment gets
    r + 0.5 less that distance, at most 1, where that is more than it has."""
    h, w = image.shape
    n = pts.shape[0]
    for k in range(max(n - 1, 1)):
        ax, ay = pts[k, 0], pts[k, 1]
        bx, by = (pts[k + 1, 0], pts[k + 1, 1]) if n > 1 else (ax, ay)
        dx, dy = bx - ax, by - ay
        length = dx * dx + dy * dy
        x0, x1 = max(int(math.floor(min(ax, bx) - r - 1)), 0), min(int(math.ceil(max(ax, bx) + r + 1)), w - 1)
        y0, y1 = max(int(math.floor(min(ay, by) - r - 1)), 0), min(int(math.ceil(max(ay, by) + r + 1)), h - 1)
        for y in range(y0, y1 + 1):
            py = y + 0.5
            for x in range(x0, x1 + 1):
                px = x + 0.5
                t = 0.0
                if length > 0:
                    t = min(max(((px - ax) * dx + (py - ay) * dy) / length, 0.0), 1.0)
                ex, ey = px - (ax + t * dx), py - (ay + t * dy)
                c = min(r + 0.5 - math.sqrt(ex * ex + ey * ey), 1.0)
                if c > image[y, x]:
                    image[y, x] = c


def render(strokes, rng=None):
    """A float32 image in 0..1, ink 1. `rng` set means training: size and pen width vary too."""
    strokes = [np.asarray(s, dtype=np.float64) for s in strokes]
    if rng is not None:
        strokes = augment(strokes, rng)
    every = np.concatenate(strokes)
    low = every.min(axis=0)
    extent = np.maximum(every.max(axis=0) - low, 1e-6)
    scale = SYMBOL_PX * (rng.uniform(0.75, 1.3) if rng else 1.0) / max(symbol_size(strokes), 1e-6)
    scale = min(scale, (MAX_W - 2 * MARGIN) / extent[0], (MAX_H - 2 * MARGIN) / extent[1])
    w = int(math.ceil(extent[0] * scale)) + 2 * MARGIN
    h = max(int(math.ceil(extent[1] * scale)) + 2 * MARGIN, 32)
    pen = rng.uniform(1.6, 3.6) if rng else PEN
    offset = np.array([MARGIN, (h - extent[1] * scale) / 2])
    image = np.zeros((h, w), np.float64)
    for s in strokes:
        draw(image, np.ascontiguousarray((s - low) * scale + offset), pen / 2)
    return image.astype(np.float32)


def render_pil(strokes, rng=None):
    """m1 to m5's drawing: PIL's lines at twice the size, scaled down. A line across is thinner than one along an axis."""
    strokes = [np.asarray(s, dtype=np.float64) for s in strokes]
    if rng is not None:
        strokes = augment(strokes, rng)
    every = np.concatenate(strokes)
    low = every.min(axis=0)
    extent = np.maximum(every.max(axis=0) - low, 1e-6)
    scale = SYMBOL_PX * (rng.uniform(0.75, 1.3) if rng else 1.0) / max(symbol_size(strokes), 1e-6)
    scale = min(scale, (MAX_W - 2 * MARGIN) / extent[0], (MAX_H - 2 * MARGIN) / extent[1])
    w = int(math.ceil(extent[0] * scale)) + 2 * MARGIN
    h = int(math.ceil(extent[1] * scale)) + 2 * MARGIN
    h = max(h, 32)
    pen = (rng.uniform(1.6, 3.6) if rng else 2.4) * SUPERSAMPLE
    image = Image.new("L", (w * SUPERSAMPLE, h * SUPERSAMPLE), 0)
    draw = ImageDraw.Draw(image)
    offset = np.array([MARGIN, (h - extent[1] * scale) / 2])
    for s in strokes:
        pts = ((s - low) * scale + offset) * SUPERSAMPLE
        pts = [tuple(p) for p in pts]
        if len(pts) == 1 or max(abs(a - b) for p in pts for a, b in zip(p, pts[0])) < 1:
            x, y = pts[0]
            draw.ellipse((x - pen / 2, y - pen / 2, x + pen / 2, y + pen / 2), fill=255)
        else:
            draw.line(pts, fill=255, width=int(round(pen)), joint="curve")
            for x, y in (pts[0], pts[-1]):
                draw.ellipse((x - pen / 2, y - pen / 2, x + pen / 2, y + pen / 2), fill=255)
    image = image.resize((w, h), Image.LANCZOS)
    return np.asarray(image, dtype=np.float32) / 255.0


class Packed:
    """One packed split (see tools/mathwriting_pack.py). The large arrays are mapped from `.npy` files beside it, so the
    loader's worker processes share them instead of each holding a copy."""

    MAPPED = ("points", "stroke_ends", "ink_ends")

    def __init__(self, path):
        self.path = pathlib.Path(path)
        folder = self.path.with_suffix(".mapped")
        z = np.load(self.path)
        if not folder.is_dir():
            part = folder.with_suffix(".part")
            part.mkdir(exist_ok=True)
            for name in self.MAPPED:
                np.save(part / f"{name}.npy", z[name])
            part.rename(folder)
        self.widths = z["widths"]
        self.symbols = z["symbols"]
        self.labels = [str(x) for x in z["labels"]]
        self.ids = [str(x) for x in z["ids"]]
        self._map()

    def _map(self):
        folder = self.path.with_suffix(".mapped")
        for name in self.MAPPED:
            setattr(self, name, np.load(folder / f"{name}.npy", mmap_mode="r"))

    def __getstate__(self):
        return {k: v for k, v in self.__dict__.items() if k not in self.MAPPED}

    def __setstate__(self, state):
        self.__dict__.update(state)
        self._map()

    def __len__(self):
        return len(self.labels)

    def strokes(self, i):
        first = self.ink_ends[i - 1] if i else 0
        ends = self.stroke_ends[first:self.ink_ends[i]]
        start = self.stroke_ends[first - 1] if first else 0
        out = []
        for end in ends:
            out.append(self.points[start:end].astype(np.float64))
            start = end
        return out


class InkSet(Dataset):
    def __init__(self, packs, vocab, train, max_tokens=160, limit=None):
        # A lone flat stroke (a minus, an overline) scaled to a height of 1 overflows float16: such inks are left out.
        self.items = [(p, i) for p in packs for i in range(len(p))
                      if len(vocab.encode(p.labels[i])) <= max_tokens and math.isfinite(p.widths[i]) and math.isfinite(p.symbols[i])]
        if limit:
            self.items = random.Random(0).sample(self.items, min(limit, len(self.items)))
        self.vocab = vocab
        self.train = train

    def __len__(self):
        return len(self.items)

    def size(self, k):
        """The rendered size of item k without augmentation, (h, w) in pixels."""
        pack, i = self.items[k]
        scale = SYMBOL_PX / max(float(pack.symbols[i]), 1e-6)
        scale = min(scale, (MAX_W - 2 * MARGIN) / max(float(pack.widths[i]), 1e-6), MAX_H - 2 * MARGIN)
        return max(scale + 2 * MARGIN, 32), float(pack.widths[i]) * scale + 2 * MARGIN

    def __getitem__(self, k):
        pack, i = self.items[k]
        rng = random.Random() if self.train else None
        image = render(pack.strokes(i), rng)
        return torch.from_numpy(image), torch.tensor(self.vocab.encode(pack.labels[i]), dtype=torch.long)


class SizedBatches:
    """Batches of inks of a similar size, each within a pixel budget (padding is the largest image's size), in random order."""

    def __init__(self, dataset, budget, max_batch=64, seed=0):
        self.sizes = [dataset.size(k) for k in range(len(dataset))]
        self.budget = budget
        self.max_batch = max_batch
        self.rng = random.Random(seed)
        self.count = len(self.batches(random.Random(seed)))

    def batches(self, rng=None):
        rng = rng or self.rng
        order = list(range(len(self.sizes)))
        rng.shuffle(order)
        out = []
        for c in range(0, len(order), 4096):
            chunk = sorted(order[c:c + 4096], key=lambda k: (self.sizes[k][0], self.sizes[k][1]))
            batch, h, w = [], 0, 0
            for k in chunk:
                nh, nw = max(h, self.sizes[k][0]), max(w, self.sizes[k][1])
                if batch and (len(batch) == self.max_batch or nh * nw * (len(batch) + 1) > self.budget):
                    out.append(batch)
                    batch, nh, nw = [], self.sizes[k][0], self.sizes[k][1]
                batch.append(k)
                h, w = nh, nw
            if batch:
                out.append(batch)
        rng.shuffle(out)
        return out

    def __iter__(self):
        return iter(self.batches())

    def __len__(self):
        return self.count


def collate(batch):
    """Images padded to the largest in the batch (with a mask of the real part), token sequences padded with PAD."""
    images, targets = zip(*batch)
    h = max(x.shape[0] for x in images)
    w = max(x.shape[1] for x in images)
    pixels = torch.zeros(len(images), 1, h, w)
    mask = torch.ones(len(images), h, w, dtype=torch.bool)
    for n, x in enumerate(images):
        pixels[n, 0, :x.shape[0], :x.shape[1]] = x
        mask[n, :x.shape[0], :x.shape[1]] = False
    length = max(len(t) for t in targets)
    tokens = torch.full((len(targets), length), PAD, dtype=torch.long)
    for n, t in enumerate(targets):
        tokens[n, :len(t)] = t
    return pixels, mask, tokens
