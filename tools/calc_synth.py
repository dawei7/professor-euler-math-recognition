r"""Calculator-style training inks, typeset from single handwritten glyphs cut out of MathWriting's training inks.

MathWriting's formulas come mostly from Wikipedia, so a model trained on it alone reads an unclear digit as a letter.
These inks are the kind a learner checks by hand: arithmetic with `\cdot`, `:` and decimal commas, fractions, powers,
roots, sin, cos, tan, ln and log, and equations in one variable. The formulas of the test sheet are never generated.

Glyphs: a training ink whose label is one row of separate symbols (no fraction, root or accent, and no base with both a
subscript and a superscript) is cut into groups of strokes that overlap horizontally. Where there are exactly as many
groups as symbols, each group is taken as its symbol, and a recognizer trained on MathWriting vets it (see `vet`); a 4
closed at the top is kept on its shape instead (see `closed_four`), and half the writers write every 4 so. The symbols
split adds its single symbols, and the root signs.

Usage: python -m tools.calc_synth --data data/packed [--count 250000] [--vet runs/m1]
Writes `<data>/glyphs.npz` (once) and `<data>/calc.npz`, packed like the MathWriting splits.
"""
import argparse
import multiprocessing
import pathlib
import random
import re
import shutil

import numpy as np

from recognizer.data import Packed
from recognizer.evaluate import comparable, truths
from recognizer.vocab import detokenize, tokenize
from tools.mathwriting_pack import Bucket, normalize

STRUCTURE = {"^", "_", "{", "}"}
NOT_A_ROW = {r"\frac", r"\sqrt", r"\begin", r"\end", r"\overline", r"\underline", r"\hat", r"\vec", r"\dot", r"\bar",
             r"\tilde", r"\widehat", r"\mathbb", r"\left", r"\right", r"\\", "&", r"\ldots", r"\cdots", r"\vdots",
             r"\ddots", r"\sum", r"\int", r"\iint", r"\oint", r"\prod", r"\not", r"\bigcup", r"\bigcap"}
OPERATORS = {"+", "-", "=", r"\cdot", r"\times", r"\div", ":", "/", "*", "<", ">"}
CLOSED_FOUR = "4 closed"  # the 4s of `closed_four`, a pool of their own beside "4", which holds them too


def metrics():
    """How each glyph sits on the line, in units of a digit's height: scaled by its height, width or larger side to
    `size`, and placed with its bottom (b) or its middle (c) at `y`."""
    m = {}
    for t in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZbdfhklt!?":
        m[t] = ("h", 1.0, 0.0, "b")
    for t in "acemnorsuvwxz":
        m[t] = ("h", 0.6, 0.0, "b")
    for t in "gpqy":
        m[t] = ("h", 0.9, -0.3, "b")
    for t in "()[]|":
        m[t] = ("h", 1.3, -0.15, "b")
    m.update({"i": ("h", 0.85, 0.0, "b"), "j": ("h", 1.1, -0.3, "b"), "/": ("h", 1.15, -0.1, "b"),
              r"\%": ("h", 1.0, 0.0, "b"), r"\pi": ("h", 0.62, 0.0, "b"), r"\circ": ("h", 0.45, 0.55, "b"),
              "+": ("m", 0.62, 0.45, "c"), r"\times": ("m", 0.5, 0.45, "c"), r"\div": ("m", 0.6, 0.45, "c"),
              "*": ("m", 0.45, 0.55, "c"), "<": ("m", 0.6, 0.45, "c"), ">": ("m", 0.6, 0.45, "c"),
              "-": ("w", 0.55, 0.45, "c"), "=": ("w", 0.6, 0.42, "c"), r"\cdot": ("m", 0.1, 0.45, "c"),
              ".": ("m", 0.1, 0.05, "c"), ",": ("h", 0.3, -0.18, "b"), ":": ("h", 0.5, 0.4, "c")})
    return m


METRICS = metrics()


# Glyphs

def groups(strokes):
    """The strokes' indices in groups that overlap horizontally, left to right."""
    out = []
    for lo, hi, k in sorted((float(s[:, 0].min()), float(s[:, 0].max()), k) for k, s in enumerate(strokes)):
        if out and lo < out[-1][1]:
            out[-1][1] = max(out[-1][1], hi)
            out[-1][2].append(k)
        else:
            out.append([lo, hi, [k]])
    return [g[2] for g in out]


def plausible(token, strokes):
    w, h = np.ptp(np.concatenate(strokes), axis=0)
    if token == "-":
        return h < 0.35 * w
    if token == "=":
        return len(strokes) >= 2 and h < w
    if token in (".", r"\cdot"):
        return len(strokes) == 1 and max(w, h) < 0.2
    if token == ":":
        return len(strokes) >= 2 and w < h < 0.7 and all(np.ptp(s, axis=0).max() < 0.4 * h for s in strokes)
    if token == "/":
        p = strokes[0]
        return len(strokes) == 1 and h > 0.8 * w and p[p[:, 1].argmin(), 0] > p[p[:, 1].argmax(), 0]
    return max(w, h) > 0.08


def closed_four(glyph):
    """A 4 closed at the top, as in print: two strokes, the upright the full height and an arm that runs diagonally
    down-left from where the upright starts, then along the crossbar. Many hands write it so; about one 4 in fourteen of
    MathWriting's does, and a recognizer trained on MathWriting alone reads it as A, 1 or 7, so `vet` would drop it."""
    if len(glyph) != 2:
        return False
    pts = np.concatenate(glyph)
    (x0, y0), (w, h) = pts.min(axis=0), np.ptp(pts, axis=0)
    arm, upright = sorted(glyph, key=lambda s: s[:, 0].min())
    top, left, start = arm[arm[:, 1].argmin()], arm[arm[:, 0].argmin()], upright[upright[:, 1].argmin()]
    return (np.ptp(upright[:, 1]) > 0.75 * h and top[1] - y0 < 0.2 * h and abs(top[0] - start[0]) < 0.25 * w
            and top[0] - left[0] > 0.45 * w and left[1] - top[1] > 0.35 * h)


def vet(pools, run, rank=5, batch=256):
    """Keeps a cut glyph only where a trained recognizer, reading it alone, counts its symbol among its `rank` likeliest
    first tokens and then takes the formula to end there. That drops most cuts that went wrong (half a 4, a parenthesis
    with a neighbour's stroke) and keeps the unusual ways of writing a symbol, which a recognizer still ranks high.
    Dots, lines, colons and slashes, which alone and enlarged could be several symbols, are left to `plausible`."""
    import torch
    from recognizer.data import collate, render
    from recognizer.evaluate import load
    from recognizer.vocab import BOS, EOS

    model, vocab = load(run, "cuda")
    for token, pool in pools.items():
        if token in (".", r"\cdot", ",", "-", ":", "/") or token not in vocab.index:
            continue
        target, kept = vocab.index[token], []
        for start in range(0, len(pool), batch):
            part = pool[start:start + batch]
            pixels, mask, _ = collate([(torch.from_numpy(render(glyph)), torch.tensor([0])) for glyph in part])
            tokens = torch.tensor([[BOS, target]] * len(part), device="cuda")
            with torch.no_grad(), torch.autocast("cuda", dtype=torch.bfloat16):
                memory, memory_mask = model.encode(pixels.cuda(), mask.cuda())
                logits = model.decode(memory, memory_mask, tokens).float()
            first = (logits[:, 0] > logits[:, 0, target:target + 1]).sum(-1)
            ends = logits[:, 1].argmax(-1) == EOS
            kept += [glyph for glyph, n, e in zip(part, first.tolist(), ends.tolist()) if n < rank and e]
        print(f"{token}: kept {len(kept)} of {len(pool)}", flush=True)
        pools[token] = kept


def harvest(data, run, cap=4000, seed=0):
    pools, closed = {t: [] for t in METRICS}, []
    train = Packed(data / "train.npz")
    order = list(range(len(train)))
    random.Random(seed).shuffle(order)
    for i in order:
        label = train.labels[i]
        tokens = tokenize(label)
        if not tokens or NOT_A_ROW.intersection(tokens) or re.search(r"\}[_^]", label) or not np.isfinite(train.widths[i]):
            continue
        glyphs = [t for t in tokens if t not in STRUCTURE]
        strokes = train.strokes(i)
        parts = groups(strokes)
        if len(parts) != len(glyphs):
            continue
        for token, part in zip(glyphs, parts):
            pool, glyph = pools.get(token), [strokes[k] for k in part]
            if token == "4" and closed_four(glyph):
                if len(closed) < cap:
                    closed.append(glyph)
            elif pool is not None and len(pool) < cap and plausible(token, glyph):
                pool.append(glyph)
    vet(pools, run)
    print(f"4 closed at the top, kept on their shape: {len(closed)}", flush=True)
    pools["4"] += closed
    pools[r"\sqrt"] = []
    symbols = Packed(data / "symbols.npz")
    for i in range(len(symbols)):
        strokes = symbols.strokes(i)
        if symbols.labels[i] in pools and all(np.isfinite(s).all() for s in strokes):
            pools[symbols.labels[i]].append(strokes)
    return pools


def save_glyphs(path, pools):
    points, stroke_ends, glyph_ends, tokens, n = [], [], [], [], 0
    for token, pool in pools.items():
        for glyph in pool:
            low = np.concatenate(glyph).min(axis=0)
            for s in glyph:
                points.append((s - low).astype(np.float32))
                n += len(s)
                stroke_ends.append(n)
            glyph_ends.append(len(stroke_ends))
            tokens.append(token)
    np.savez(path, points=np.concatenate(points), stroke_ends=np.array(stroke_ends, np.int64),
             glyph_ends=np.array(glyph_ends, np.int64), tokens=np.array(tokens))


def load_glyphs(path):
    """Each token's glyphs: lists of strokes, y downwards, the box's corner at 0."""
    z = np.load(path)
    points, stroke_ends, pools, first = z["points"], z["stroke_ends"], {}, 0
    for end, token in zip(z["glyph_ends"], z["tokens"]):
        starts = [stroke_ends[k - 1] if k else 0 for k in range(first, end)]
        glyph = [points[s:e].astype(np.float64) for s, e in zip(starts, stroke_ends[first:end])]
        pools.setdefault(str(token), []).append(glyph)
        first = end
    pools[CLOSED_FOUR] = [g for g in pools.get("4", []) if closed_four(g)]
    return pools


# Formulas, as nodes: ("g", token), ("fn", letters), ("row", [nodes]), ("sup" | "sub", base, row),
# ("frac", row, row), ("sqrt", row) and ("delim", opening, row, closing).

def latex_tokens(node):
    kind = node[0]
    if kind == "g":
        return [node[1]]
    if kind == "fn":
        return list(node[1])
    if kind == "row":
        return [t for n in node[1] for t in latex_tokens(n)]
    if kind in ("sup", "sub"):
        return latex_tokens(node[1]) + ["^" if kind == "sup" else "_", "{"] + latex_tokens(node[2]) + ["}"]
    if kind == "frac":
        return [r"\frac", "{"] + latex_tokens(node[1]) + ["}", "{"] + latex_tokens(node[2]) + ["}"]
    if kind == "sqrt":
        return [r"\sqrt", "{"] + latex_tokens(node[1]) + ["}"]
    return [node[1]] + latex_tokens(node[2]) + [node[3]]


def g(*tokens):
    return [("g", t) for t in tokens]


def row(nodes):
    return ("row", nodes)


class Style:
    """One writer's habits in one formula: the signs for times and division, a decimal point or comma, the variable."""

    def __init__(self, rng):
        self.times = rng.choices((r"\cdot", r"\times", "*"), (6, 3, 1))[0]
        self.divide = rng.choices((":", r"\div", "/"), (4, 3, 3))[0]
        self.comma = rng.random() < 0.35
        self.var = rng.choice("xxxxxyyzabnt")


def integer(rng, digits=None, low=0):
    digits = digits or rng.choices((1, 2, 3, 4), (35, 35, 20, 10))[0]
    return g(*str(rng.randint(10 ** (digits - 1) if digits > 1 else low, 10 ** digits - 1)))


def factor(rng):
    """A coefficient or a denominator: never 0."""
    return integer(rng, rng.choices((1, 2), (4, 1))[0], low=1)


def number(rng, style):
    if rng.random() < 0.78:
        return integer(rng)
    whole = str(rng.randint(0, 9) if rng.random() < 0.75 else rng.randint(10, 99))
    decimals = [rng.choice("0123456789") for _ in range(rng.randint(1, 3))]
    return g(*whole, "," if style.comma else ".", *decimals)


def exponent(rng, style):
    r = rng.random()
    if r < 0.6:
        return integer(rng, rng.choices((1, 2), (5, 1))[0])
    if r < 0.75:
        return g("-") + integer(rng, 1)
    if r < 0.9:
        return g(style.var)
    return [("frac", row(integer(rng, 1)), row(factor(rng)))]


def atom(rng, style, depth):
    r = rng.random() * (0.6 if depth > 1 else 1.0)
    if r < 0.5:
        return number(rng, style)
    if r < 0.56:
        return g(style.var)
    if r < 0.6:
        return g(r"\pi")
    if r < 0.68:
        return [("delim", "(", row(expression(rng, style, depth + 1)), ")")]
    if r < 0.77:
        return [("frac", row(expression(rng, style, depth + 1, short=True)),
                 row(expression(rng, style, depth + 1, short=True)))]
    if r < 0.83:
        return [("sqrt", row(expression(rng, style, depth + 1, short=True)))]
    if r < 0.92:
        b = rng.random()
        if b < 0.6:
            base = integer(rng, rng.choice((1, 1, 2)))
        elif b < 0.8:
            base = g(style.var)
        else:
            base = [("delim", "(", row(expression(rng, style, depth + 1, short=True)), ")")]
        return [("sup", row(base), row(exponent(rng, style)))]
    if r < 0.95:
        return [("delim", "|", row(expression(rng, style, depth + 1, short=True)), "|")]
    if r < 0.975:
        return integer(rng, 1) + g("!")
    return number(rng, style) + g(r"\%")


def expression(rng, style, depth=0, short=False):
    terms = rng.choices((1, 2, 3, 4), (50, 40, 10, 0) if short or depth else (10, 45, 30, 15))[0]
    nodes = g("-") if rng.random() < 0.08 else []
    for k in range(terms):
        if k:
            nodes += g(rng.choices(("+", "-", style.times, style.divide), (30, 30, 25, 15))[0])
        nodes += atom(rng, style, depth)
    return nodes


def linear(rng, style):
    v, r = style.var, rng.random()
    if r < 0.6:
        nodes = factor(rng) + g(v)
    elif r < 0.75:
        nodes = g(v)
    elif r < 0.85:
        nodes = [("frac", row(g(v)), row(factor(rng)))]
    else:
        nodes = factor(rng) + [("delim", "(", row(g(v, rng.choice("+-")) + integer(rng, 1)), ")")]
    if rng.random() < 0.7:
        nodes += g(rng.choice("+-")) + integer(rng, rng.choice((1, 1, 2)))
    return nodes


def quadratic(rng, style):
    v = style.var
    nodes = (factor(rng) if rng.random() < 0.4 else []) + [("sup", row(g(v)), row(g("2")))]
    if rng.random() < 0.8:
        nodes += g(rng.choice("+-")) + (factor(rng) if rng.random() < 0.8 else []) + g(v)
    if rng.random() < 0.8:
        nodes += g(rng.choice("+-")) + integer(rng, rng.choice((1, 2)))
    return nodes


def equation(rng, style):
    v, r = style.var, rng.random()
    if r < 0.45:
        left = linear(rng, style)
        right = integer(rng, rng.choice((1, 2))) if rng.random() < 0.6 else linear(rng, style)
    elif r < 0.75:
        left = quadratic(rng, style)
        right = g("0") if rng.random() < 0.7 else integer(rng, 2)
    else:
        left = rng.choice((
            lambda: [("sup", row(g(v)), row(g(rng.choice("23"))))],
            lambda: [("sup", row([("delim", "(", row(g(v, rng.choice("+-")) + integer(rng, 1)), ")")]), row(g("2")))],
            lambda: [("frac", row(factor(rng)), row(g(v)))],
            lambda: [("sqrt", row(g(v)))],
            lambda: [("sup", row(integer(rng, 1)), row(g(v)))],
        ))()
        right = integer(rng, rng.choice((1, 2)))
    return left + g("=") + right


def function(rng, style):
    name = rng.choices(("sin", "cos", "tan", "ln", "log", "e"), (25, 25, 15, 15, 15, 5))[0]
    if name == "e":
        return [("sup", row(g("e")), row(exponent(rng, style)))]
    head = [("fn", name)]
    if name in ("sin", "cos", "tan"):
        r = rng.random()
        if r < 0.4:
            arg = [("sup", row(integer(rng, rng.choice((1, 2, 2, 3)))), row(g(r"\circ")))]
        elif r < 0.6:
            arg = [("frac", row(g(r"\pi")), row(factor(rng)))]
        elif r < 0.75:
            arg = (factor(rng) if rng.random() < 0.4 else []) + g(r"\pi")
        else:
            arg = g(style.var) if rng.random() < 0.5 else number(rng, style)
    elif name == "ln":
        r = rng.random()
        arg = [("sup", row(g("e")), row(integer(rng, 1)))] if r < 0.4 else g(style.var) if r < 0.6 else number(rng, style)
    else:
        if rng.random() < 0.5:
            head = [("sub", ("fn", "log"), row(factor(rng)))]
        arg = g(style.var) if rng.random() < 0.2 else integer(rng)
    nodes = head + ([("delim", "(", row(arg), ")")] if rng.random() < 0.7 else arg)
    if rng.random() < 0.25:
        nodes = factor(rng) + nodes
    if rng.random() < 0.25:
        nodes += g(rng.choice("+-")) + atom(rng, style, 1)
    return nodes


def formula(rng):
    style, r = Style(rng), rng.random()
    if r < 0.5:
        nodes = expression(rng, style)
        if rng.random() < 0.75:
            nodes += g("=")
    elif r < 0.75:
        nodes = equation(rng, style)
    else:
        nodes = function(rng, style)
        if rng.random() < 0.7:
            nodes += g("=")
    return row(nodes)


# Layout

class Box:
    """Strokes laid out with the baseline at y = 0 and y upwards, from x = 0 to `width`."""

    def __init__(self, strokes=None, width=0.0, top=0.0, bottom=0.0):
        self.strokes, self.width, self.top, self.bottom = strokes or [], width, top, bottom

    def put(self, other, dx, dy):
        self.strokes += [s + (dx, dy) for s in other.strokes]
        self.top = max(self.top, other.top + dy)
        self.bottom = min(self.bottom, other.bottom + dy)
        self.width = max(self.width, dx + other.width)

    def scaled(self, f):
        return Box([s * f for s in self.strokes], self.width * f, self.top * f, self.bottom * f)


class Writer:
    """Writes one formula: picks a glyph per symbol (often the same one again, as a person writes a 4 the same way twice)
    and spaces them in a style of its own."""

    def __init__(self, pools, rng):
        self.pools, self.rng, self.chosen = pools, rng, {}
        self.spacing = rng.uniform(0.6, 1.5)
        self.closed_four = bool(pools.get(CLOSED_FOUR)) and rng.random() < 0.5

    def pick(self, token):
        if token not in self.chosen or self.rng.random() > 0.6:
            pool = self.pools[CLOSED_FOUR if token == "4" and self.closed_four else token]
            self.chosen[token] = self.rng.choice(pool)
        return self.chosen[token]

    def glyph(self, token, size=None, y=None):
        rng, strokes = self.rng, self.pick(token)
        w, h = np.ptp(np.concatenate(strokes), axis=0)
        by, target, anchor, align = METRICS[token]
        if size is None:
            target *= rng.uniform(0.88, 1.12)
        else:
            target, anchor = size, y
        s = min(target / max({"h": h, "w": w, "m": max(w, h)}[by], 1e-3), 2.5 * target / max(w, h, 1e-3))
        sx = s * rng.uniform(0.88, 1.12)
        placed = [np.column_stack((p[:, 0] * sx, (h - p[:, 1]) * s)) for p in strokes]
        dy = (anchor if align == "b" else anchor - h * s / 2) + rng.gauss(0, 0.03)
        return Box([p + (0, dy) for p in placed], w * sx, dy + h * s, dy)

    def bar(self, width):
        """A fraction line: a minus sign drawn out to `width`, centred on y = 0."""
        strokes = self.pick("-")
        w, h = np.ptp(np.concatenate(strokes), axis=0)
        sx = width / max(w, 1e-3)
        sy = min(sx, 0.55 / max(w, 1e-3))
        return Box([np.column_stack((p[:, 0] * sx, (h / 2 - p[:, 1]) * sy)) for p in strokes], width, h * sy / 2, -h * sy / 2)

    def root(self, inner):
        """A root sign around `inner`: its tick kept in proportion, its line drawn out over the radicand."""
        rng = self.rng
        bottom = min(inner.bottom, 0.0) - rng.uniform(0.05, 0.15)
        top = max(inner.top, 1.0) + rng.uniform(0.1, 0.25)
        hook = (top - bottom) * rng.uniform(0.3, 0.5)
        pad = rng.uniform(0.05, 0.15)
        end = hook + pad + inner.width + rng.uniform(0.05, 0.25)
        strokes = self.pick(r"\sqrt")
        w, h = np.ptp(np.concatenate(strokes), axis=0)
        unit = [np.column_stack((p[:, 0] / max(w, 1e-3), 1 - p[:, 1] / max(h, 1e-3))) for p in strokes]
        every = np.concatenate(unit)
        peak = float(every[every[:, 1] > 0.85, 0].min())
        box = Box(top=top, bottom=bottom)
        if peak > 0.8:  # a tick without its line: the line is a minus sign
            box.strokes = [np.column_stack((u[:, 0] * hook, bottom + u[:, 1] * (top - bottom))) for u in unit]
            box.put(self.bar(end - hook), hook, top)
        else:
            def x(u):
                return np.where(u < peak, u / peak * hook, hook + (u - peak) / (1 - peak) * (end - hook))
            box.strokes = [np.column_stack((x(u[:, 0]), bottom + u[:, 1] * (top - bottom))) for u in unit]
        box.width = max(box.width, end)
        box.put(inner, hook + pad, 0)
        return box

    def line(self, nodes, tight=False):
        rng, box, x = self.rng, Box(), 0.0
        for k, node in enumerate(nodes):
            part = self.lay(node)
            if k:
                gap = rng.uniform(0.03, 0.12) if tight else rng.uniform(0.06, 0.2)
                if not tight and any(n[0] == "g" and n[1] in OPERATORS for n in (node, nodes[k - 1])):
                    gap += rng.uniform(0.05, 0.3)
                x += gap * self.spacing
            box.put(part, x, 0)
            x += part.width
        box.width = x
        return box

    def lay(self, node):
        kind, rng = node[0], self.rng
        if kind == "g":
            return self.glyph(node[1])
        if kind == "fn":
            return self.line(g(*node[1]), tight=True)
        if kind == "row":
            return self.line(node[1])
        if kind in ("sup", "sub"):
            base, f = self.lay(node[1]), rng.uniform(0.5, 0.7)
            script = self.lay(node[2]).scaled(f)
            # A superscript sits as high on a small letter (x², y²) as on a digit.
            dy = max(base.top, 0.85) - f * rng.uniform(0.2, 0.5) if kind == "sup" else -f * rng.uniform(0.3, 0.55)
            box = Box()
            box.put(base, 0, 0)
            box.put(script, base.width + rng.uniform(0.0, 0.12) * self.spacing, dy)
            return box
        if kind == "frac":
            f = rng.uniform(0.75, 1.0)
            num, den = self.lay(node[1]).scaled(f), self.lay(node[2]).scaled(f)
            width = max(num.width, den.width) + rng.uniform(0.1, 0.5)
            middle = rng.uniform(0.38, 0.5)
            box = Box()
            box.put(self.bar(width), 0, middle)
            box.put(num, (width - num.width) / 2 + rng.gauss(0, 0.05), middle + rng.uniform(0.08, 0.25) - num.bottom)
            box.put(den, (width - den.width) / 2 + rng.gauss(0, 0.05), middle - rng.uniform(0.1, 0.28) - den.top)
            return box
        if kind == "sqrt":
            return self.root(self.lay(node[1]))
        inner, size, y = self.lay(node[2]), None, None
        if inner.top - inner.bottom > 1.25:
            size, y = inner.top - inner.bottom + rng.uniform(0.1, 0.3), inner.bottom - rng.uniform(0.05, 0.15)
        box = Box()
        box.put(self.glyph(node[1], size, y), 0, 0)
        x = box.width + rng.uniform(0.04, 0.15) * self.spacing
        box.put(inner, x, 0)
        box.put(self.glyph(node[3], size, y), x + inner.width + rng.uniform(0.04, 0.15) * self.spacing, 0)
        return box


# Making the inks

POOLS, EXCLUDED = None, None


def start(glyph_path, excluded):
    global POOLS, EXCLUDED
    POOLS, EXCLUDED = load_glyphs(glyph_path), excluded


def make(job):
    """`count` inks from `seed`: (strokes as packed, label)."""
    seed, count = job
    rng, out = random.Random(seed), []
    while len(out) < count:
        node = formula(rng)
        tokens = latex_tokens(node)
        label = detokenize(tokens)
        if not 2 <= sum(t not in STRUCTURE for t in tokens) <= 32 or tuple(comparable(label)) in EXCLUDED:
            continue
        box = Writer(POOLS, rng).lay(node)
        out.append((normalize([np.column_stack((s[:, 0], -s[:, 1])) for s in box.strokes]), label))
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/packed")
    parser.add_argument("--count", type=int, default=250_000)
    parser.add_argument("--exclude", default="test-sheet/formulas.md", help="a test sheet whose formulas are never made")
    parser.add_argument("--vet", default="runs/m1", help="a trained run that vets the cut glyphs (see `vet`)")
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args()
    data = pathlib.Path(args.data)
    glyph_path = data / "glyphs.npz"
    if not glyph_path.exists():
        save_glyphs(glyph_path, harvest(data, args.vet))
    pools = load_glyphs(glyph_path)
    print("glyphs:", ", ".join(f"{t} {len(p)}" for t, p in sorted(pools.items())), flush=True)
    missing = [t for t in list(METRICS) + [r"\sqrt"] if not pools.get(t)]
    excluded = {tuple(comparable(t)) for t in truths(args.exclude).values()}
    jobs = [(args.seed * 1_000_003 + k, 1000) for k in range(args.count // 1000)]
    bucket = Bucket()
    with multiprocessing.Pool(args.workers, initializer=start, initargs=(glyph_path, excluded)) as pool:
        for chunk in pool.imap(make, jobs):
            for kept, label in chunk:
                bucket.add(kept, label, f"calc-{len(bucket.ids)}")
            if len(bucket.ids) % 50000 == 0:
                print(len(bucket.ids), flush=True)
    if missing:
        print("no glyphs for", missing)
    shutil.rmtree(data / "calc.mapped", ignore_errors=True)
    bucket.save(data / "calc.npz")


if __name__ == "__main__":
    main()
