"""Reading as a phone will: the encoder once per formula, then the decoder one token at a time with every layer's keys
and values kept, instead of running the whole reading again for each new token as training does. The same weights and
the same readings, in far less work. Attention is written out in plain operations, so it exports to ONNX as it is.
"""
import math

import torch
import torch.nn.functional as F
from torch import nn

from .model import Recognizer, sinusoid
from .vocab import BOS, EOS


def attend(q, k, v):
    return torch.softmax(q @ k.transpose(-1, -2) / math.sqrt(q.shape[-1]), -1) @ v


class Reader(nn.Module):
    """A trained Recognizer's weights, read step by step. One image at a time, unpadded."""

    def __init__(self, model: Recognizer):
        super().__init__()
        self.model = model.eval()
        self.layers = len(model.decoder.layers)
        self.heads = model.decoder.layers[0].self_attn.num_heads
        self.d = model.d_model

    def split(self, x):
        """(B, n, d) -> (B, heads, n, d / heads)"""
        return x.reshape(x.shape[0], x.shape[1], self.heads, -1).transpose(1, 2)

    def merge(self, x):
        return x.transpose(1, 2).reshape(x.shape[0], -1, self.d)

    def memory(self, pixels):
        """pixels (1, 1, h, w) -> every layer's cross-attention keys and values, (layers, 2, 1, heads, n, dh)."""
        mask = torch.zeros(pixels.shape[0], pixels.shape[2], pixels.shape[3], dtype=torch.bool, device=pixels.device)
        memory, _ = self.model.encode(pixels, mask)
        d, out = self.d, []
        for layer in self.model.decoder.layers:
            w, b = layer.multihead_attn.in_proj_weight, layer.multihead_attn.in_proj_bias
            out.append(torch.stack([self.split(F.linear(memory, w[d:2 * d], b[d:2 * d])),
                                    self.split(F.linear(memory, w[2 * d:], b[2 * d:]))]))
        return torch.stack(out)

    def empty(self, batch=1):
        """The keys and values before the first token."""
        return torch.zeros(self.layers, 2, batch, self.heads, 0, self.d // self.heads)

    def step(self, tokens, position, cross, past):
        """tokens (B, 1), position (a 0-d integer tensor), cross from `memory` (batch 1 or B), past (layers, 2, B, heads,
        t, dh) -> log probabilities of the next token (B, vocab), and past with this token's keys and values added."""
        m, d, batch = self.model, self.d, tokens.shape[0]
        x = m.norm(m.embed(tokens) * math.sqrt(d) + sinusoid(position.reshape(1), d))
        kept = []
        for i, layer in enumerate(m.decoder.layers):
            sa = layer.self_attn
            q, k, v = F.linear(layer.norm1(x), sa.in_proj_weight, sa.in_proj_bias).chunk(3, -1)
            k, v = torch.cat([past[i, 0], self.split(k)], 2), torch.cat([past[i, 1], self.split(v)], 2)
            kept.append(torch.stack([k, v]))
            x = x + sa.out_proj(self.merge(attend(self.split(q), k, v)))
            ca = layer.multihead_attn
            q = self.split(F.linear(layer.norm2(x), ca.in_proj_weight[:d], ca.in_proj_bias[:d]))
            ck, cv = cross[i, 0].expand(batch, -1, -1, -1), cross[i, 1].expand(batch, -1, -1, -1)
            x = x + ca.out_proj(self.merge(attend(q, ck, cv)))
            x = x + layer.linear2(layer.activation(layer.linear1(layer.norm3(x))))
        return m.out(x[:, 0]).log_softmax(-1), torch.stack(kept)


@torch.no_grad()
def read(reader, pixels, beam=5, max_len=200):
    """The best token sequence (without BOS/EOS) for one image, by the same beam search as Recognizer.beam_search."""
    cross, past = reader.memory(pixels), reader.empty()
    alive, done = [([BOS], 0.0)], []
    for t in range(max_len):
        tokens = torch.tensor([[seq[-1]] for seq, _ in alive])
        logp, past = reader.step(tokens, torch.tensor(t), cross, past)
        candidates = []
        for n, ((seq, score), row) in enumerate(zip(alive, logp)):
            top = row.topk(beam)
            candidates += [(seq + [tok], score + val, n) for val, tok in zip(top.values.tolist(), top.indices.tolist())]
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
