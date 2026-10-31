"""A DenseNet encoder over the image and a Transformer decoder that writes the LaTeX tokens.

The shape follows the published handwritten-math recognizers (a DenseNet-B encoder as in WAP/DWAP, a Transformer
decoder as in BTTR), written anew here; about 6 million parameters, small enough for a phone.
"""
import math

import torch
import torch.nn.functional as F
from torch import nn

from .vocab import BOS, EOS, PAD


class DenseLayer(nn.Module):
    def __init__(self, channels, growth, dropout):
        super().__init__()
        self.norm1 = nn.BatchNorm2d(channels)
        self.conv1 = nn.Conv2d(channels, 4 * growth, 1, bias=False)
        self.norm2 = nn.BatchNorm2d(4 * growth)
        self.conv2 = nn.Conv2d(4 * growth, growth, 3, padding=1, bias=False)
        self.dropout = dropout

    def forward(self, x):
        y = self.conv1(F.relu(self.norm1(x)))
        y = self.conv2(F.relu(self.norm2(y)))
        if self.dropout:
            y = F.dropout(y, self.dropout, self.training)
        return torch.cat([x, y], 1)


class Encoder(nn.Module):
    """Downsamples 16 times: a 128 px high formula becomes 8 rows of features."""

    def __init__(self, d_model, growth=24, depth=16, dropout=0.2):
        super().__init__()
        channels = 2 * growth
        layers = [nn.Conv2d(1, channels, 7, stride=2, padding=3, bias=False), nn.BatchNorm2d(channels), nn.ReLU(inplace=True),
                  nn.MaxPool2d(2, ceil_mode=True)]
        for block in range(3):
            for _ in range(depth):
                layers.append(DenseLayer(channels, growth, dropout))
                channels += growth
            if block < 2:
                out = channels // 2
                layers += [nn.BatchNorm2d(channels), nn.ReLU(inplace=True), nn.Conv2d(channels, out, 1, bias=False),
                           nn.AvgPool2d(2, ceil_mode=True)]
                channels = out
        layers += [nn.BatchNorm2d(channels), nn.ReLU(inplace=True)]
        self.body = nn.Sequential(*layers)
        self.project = nn.Conv2d(channels, d_model, 1)

    def forward(self, pixels, mask):
        features = self.project(self.body(pixels))
        h, w = features.shape[-2:]
        small = F.interpolate(mask[:, None].float(), size=(h, w), mode="nearest")[:, 0].bool()
        return features, small


class InvertedResidual(nn.Module):
    """MobileNetV2's block: widen, a depthwise 3x3 (strided to downsample), narrow; with a shortcut when shapes match."""

    def __init__(self, cin, cout, stride, expand=4):
        super().__init__()
        mid = cin * expand
        self.body = nn.Sequential(
            nn.Conv2d(cin, mid, 1, bias=False), nn.BatchNorm2d(mid), nn.SiLU(inplace=True),
            nn.Conv2d(mid, mid, 3, stride=stride, padding=1, groups=mid, bias=False), nn.BatchNorm2d(mid), nn.SiLU(inplace=True),
            nn.Conv2d(mid, cout, 1, bias=False), nn.BatchNorm2d(cout))
        self.shortcut = stride == 1 and cin == cout

    def forward(self, x):
        y = self.body(x)
        return x + y if self.shortcut else y


class MobileEncoder(nn.Module):
    """Downsamples 16 times like the DenseNet, with depthwise blocks: far less memory and work, on a phone too."""

    STAGES = ((48, 2), (96, 3), (160, 4))  # channels and blocks at 1/4, 1/8 and 1/16

    def __init__(self, d_model):
        super().__init__()
        layers = [nn.Conv2d(1, 32, 3, stride=2, padding=1, bias=False), nn.BatchNorm2d(32), nn.SiLU(inplace=True),
                  InvertedResidual(32, 32, 1, expand=2)]
        cin = 32
        for cout, blocks in self.STAGES:
            for b in range(blocks):
                layers.append(InvertedResidual(cin, cout, 2 if b == 0 else 1))
                cin = cout
        self.body = nn.Sequential(*layers)
        self.project = nn.Conv2d(cin, d_model, 1)

    def forward(self, pixels, mask):
        features = self.project(self.body(pixels))
        h, w = features.shape[-2:]
        small = F.interpolate(mask[:, None].float(), size=(h, w), mode="nearest")[:, 0].bool()
        return features, small


def sinusoid(positions, dim):
    """positions (...,) -> (..., dim)"""
    half = dim // 2
    freq = torch.exp(torch.arange(half, device=positions.device, dtype=torch.float32) * (-math.log(10000.0) / half))
    angles = positions[..., None].float() * freq
    return torch.cat([angles.sin(), angles.cos()], -1)


def image_positions(mask, dim):
    """2D sine positions on the real part of each image, normalized to its own height and width."""
    real = (~mask).float()
    y = real.cumsum(1) / (real.sum(1, keepdim=True) + 1e-6)
    x = real.cumsum(2) / (real.sum(2, keepdim=True) + 1e-6)
    return torch.cat([sinusoid(y * 100, dim // 2), sinusoid(x * 100, dim // 2)], -1)


class Recognizer(nn.Module):
    def __init__(self, vocab_size, encoder="mobile", d_model=256, heads=8, layers=3, ff=1024, dropout=0.3):
        super().__init__()
        self.d_model = d_model
        self.encoder = MobileEncoder(d_model) if encoder == "mobile" else Encoder(d_model)
        self.embed = nn.Embedding(vocab_size, d_model, padding_idx=PAD)
        self.norm = nn.LayerNorm(d_model)
        layer = nn.TransformerDecoderLayer(d_model, heads, ff, dropout, batch_first=True, norm_first=True)
        self.decoder = nn.TransformerDecoder(layer, layers)
        self.out = nn.Linear(d_model, vocab_size)

    def encode(self, pixels, mask):
        features, small = self.encoder(pixels, mask)
        b, d, h, w = features.shape
        memory = features.permute(0, 2, 3, 1) + image_positions(small, d)
        return memory.reshape(b, h * w, d), small.reshape(b, h * w)

    def decode(self, memory, memory_mask, tokens):
        n = tokens.shape[1]
        x = self.embed(tokens) * math.sqrt(self.d_model) + sinusoid(torch.arange(n, device=tokens.device), self.d_model)
        causal = torch.triu(torch.ones(n, n, dtype=torch.bool, device=tokens.device), 1)
        y = self.decoder(self.norm(x), memory, tgt_mask=causal, tgt_key_padding_mask=tokens == PAD,
                         memory_key_padding_mask=memory_mask)
        return self.out(y)

    def forward(self, pixels, mask, tokens):
        memory, memory_mask = self.encode(pixels, mask)
        return self.decode(memory, memory_mask, tokens)

    @torch.no_grad()
    def beam_search(self, pixels, mask, beam=5, max_len=200):
        """The best token sequence for each image (without BOS/EOS), by length-normalized log probability."""
        memory, memory_mask = self.encode(pixels, mask)
        results = []
        for i in range(pixels.shape[0]):
            mem, mm = memory[i:i + 1], memory_mask[i:i + 1]
            alive = [([BOS], 0.0)]
            done = []
            for _ in range(max_len):
                tokens = torch.tensor([seq for seq, _ in alive], device=pixels.device)
                logp = self.decode(mem.expand(len(alive), -1, -1), mm.expand(len(alive), -1), tokens)[:, -1].log_softmax(-1)
                candidates = []
                for (seq, score), row in zip(alive, logp):
                    top = row.topk(beam)
                    candidates += [(seq + [t], score + v) for v, t in zip(top.values.tolist(), top.indices.tolist())]
                candidates.sort(key=lambda c: c[1], reverse=True)
                alive = []
                for seq, score in candidates:
                    if seq[-1] == EOS:
                        done.append((seq[1:-1], score / (len(seq) - 1)))
                    else:
                        alive.append((seq, score))
                    if len(alive) == beam:
                        break
                if not alive or (len(done) >= beam and max(s for _, s in done) > alive[0][1] / len(alive[0][0])):
                    break
            done += [(seq[1:], score / len(seq)) for seq, score in alive]
            results.append(max(done, key=lambda d: d[1])[0])
        return results
