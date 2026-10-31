r"""LaTeX tokens: a command (`\frac`), an escaped character (`\{`, `\%`) or any other single character."""
import json
import re

TOKEN = re.compile(r"\\[a-zA-Z]+|\\.|\S")
SPECIALS = ["<pad>", "<s>", "</s>", "<unk>"]
PAD, BOS, EOS, UNK = range(4)


def tokenize(latex):
    return TOKEN.findall(latex)


def detokenize(tokens):
    r"""LaTeX again: a command followed by a letter keeps a space, so `\pi x` does not become `\pix`."""
    out = ""
    for t in tokens:
        if out and re.search(r"\\[a-zA-Z]+$", out) and re.match(r"[a-zA-Z]", t):
            out += " "
        out += t
    return out


class Vocab:
    def __init__(self, tokens):
        self.tokens = list(tokens)
        self.index = {t: i for i, t in enumerate(self.tokens)}

    @classmethod
    def build(cls, labels, min_count=1):
        counts = {}
        for label in labels:
            for t in tokenize(label):
                counts[t] = counts.get(t, 0) + 1
        return cls(SPECIALS + sorted(t for t, c in counts.items() if c >= min_count))

    @classmethod
    def load(cls, path):
        with open(path, encoding="utf-8") as f:
            return cls(json.load(f))

    def save(self, path):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.tokens, f, ensure_ascii=False, indent=0)

    def __len__(self):
        return len(self.tokens)

    def encode(self, latex):
        return [BOS] + [self.index.get(t, UNK) for t in tokenize(latex)] + [EOS]

    def decode(self, ids):
        tokens = []
        for i in ids:
            if i == EOS:
                break
            if i not in (PAD, BOS):
                tokens.append(self.tokens[i])
        return detokenize(tokens)
