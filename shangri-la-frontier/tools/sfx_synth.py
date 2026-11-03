"""Small sound-synthesis kit for the Frontier's sound effects (see sfx.py).

Everything is mono float32 at RATE Hz in [-1, 1]. Building blocks: oscillators with pitch
envelopes, noise, one-pole and state-variable filters (with sweeping cutoffs), envelopes,
inharmonic "metal" partials, a small Schroeder reverb, and mixing helpers.
"""
import numpy as np

RATE = 44100
_rng = np.random.default_rng(20261103)


def seed(n):
    """Re-seeds the noise so each sound is the same on every run."""
    global _rng
    _rng = np.random.default_rng(n)


def t(dur):
    return np.arange(int(dur * RATE)) / RATE


def length(dur):
    return int(dur * RATE)


# ---- envelopes ------------------------------------------------------------------

def env(dur, attack=0.005, decay=None, hold=0.0, curve=4.0):
    """Attack (linear), hold, then exponential-ish decay to silence at the end."""
    n = length(dur)
    out = np.zeros(n)
    a = max(1, int(attack * RATE))
    h = int(hold * RATE)
    out[:a] = np.linspace(0, 1, a)
    out[a:a + h] = 1
    rest = n - a - h
    if rest > 0:
        x = np.linspace(0, 1, rest)
        out[a + h:] = (1 - x) ** curve if decay is None else np.exp(-x * rest / RATE / decay) * (1 - x)
    return out


def ramp(dur, start, end, curve=1.0):
    x = np.linspace(0, 1, length(dur)) ** curve
    return start + (end - start) * x


def expramp(dur, start, end):
    return start * (end / start) ** np.linspace(0, 1, length(dur))


def fade(sig, fin=0.002, fout=0.01):
    s = sig.copy()
    i, o = int(fin * RATE), int(fout * RATE)
    if i:
        s[:i] *= np.linspace(0, 1, i)
    if o:
        s[-o:] *= np.linspace(1, 0, o)
    return s


# ---- sources ---------------------------------------------------------------------

def sine(freq, dur=None, phase=0.0):
    """freq is a number or a per-sample array (pitch envelope)."""
    if np.isscalar(freq):
        freq = np.full(length(dur), float(freq))
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE + phase)


def tri(freq, dur=None):
    if np.isscalar(freq):
        freq = np.full(length(dur), float(freq))
    ph = np.cumsum(freq) / RATE
    return 2 * np.abs(2 * (ph - np.floor(ph + 0.5))) - 1


def saw(freq, dur=None):
    if np.isscalar(freq):
        freq = np.full(length(dur), float(freq))
    ph = np.cumsum(freq) / RATE
    return 2 * (ph - np.floor(ph + 0.5))


def noise(dur):
    return _rng.uniform(-1, 1, length(dur))


def pink(dur):
    """Roughly pink noise (Paul Kellet's filter)."""
    w = noise(dur)
    b = np.zeros(7)
    out = np.empty_like(w)
    for i, x in enumerate(w):
        b[0] = 0.99886 * b[0] + x * 0.0555179
        b[1] = 0.99332 * b[1] + x * 0.0750759
        b[2] = 0.96900 * b[2] + x * 0.1538520
        b[3] = 0.86650 * b[3] + x * 0.3104856
        b[4] = 0.55000 * b[4] + x * 0.5329522
        b[5] = -0.7616 * b[5] - x * 0.0168980
        out[i] = b[0] + b[1] + b[2] + b[3] + b[4] + b[5] + b[6] + x * 0.5362
        b[6] = x * 0.115926
    return out / (np.max(np.abs(out)) + 1e-9)


def metal(f0, dur, ratios=(1.0, 2.76, 5.40, 8.93, 13.34), decays=(0.9, 0.6, 0.4, 0.25, 0.15), amps=None, detune=0.0):
    """Inharmonic partials with their own decays: blades, bells, clangs."""
    amps = amps or [1.0 / (i + 1) ** 0.7 for i in range(len(ratios))]
    out = np.zeros(length(dur))
    x = t(dur)
    for r, d, a in zip(ratios, decays, amps):
        f = f0 * r * (1 + detune * _rng.uniform(-1, 1))
        if f >= RATE / 2:
            continue
        out += a * np.sin(2 * np.pi * f * x + _rng.uniform(0, 6.28)) * np.exp(-x / (d * dur))
    return out / (np.max(np.abs(out)) + 1e-9)


# ---- filters ---------------------------------------------------------------------

def svf(sig, cutoff, q=0.7, mode="bp"):
    """Chamberlin state-variable filter; cutoff may be an array (sweep). mode: lp, bp, hp."""
    n = len(sig)
    fc = np.broadcast_to(np.asarray(cutoff, dtype=float), (n,))
    f = 2 * np.sin(np.pi * np.clip(fc, 20, RATE / 6) / RATE)
    damp = 1.0 / q
    low = band = 0.0
    out = np.empty(n)
    for i in range(n):
        high = sig[i] - low - damp * band
        band += f[i] * high
        low += f[i] * band
        out[i] = low if mode == "lp" else band if mode == "bp" else high
    return out


def onepole_lp(sig, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE)
    out = np.empty_like(sig)
    y = 0.0
    for i, x in enumerate(sig):
        y = (1 - a) * x + a * y
        out[i] = y
    return out


def onepole_hp(sig, cutoff):
    return sig - onepole_lp(sig, cutoff)


# ---- effects ---------------------------------------------------------------------

def drive(sig, amount=2.0):
    return np.tanh(sig * amount) / np.tanh(amount)


def reverb(sig, mix=0.25, size=1.0, tail=0.6):
    """Small Schroeder reverb (4 combs + 2 allpasses); appends a tail of `tail` seconds."""
    x = np.concatenate([sig, np.zeros(length(tail))])
    combs = [int(d * size) for d in (1557, 1617, 1491, 1422)]
    wet = np.zeros_like(x)
    for d in combs:
        buf = np.zeros_like(x)
        g = 0.80
        for i in range(len(x)):
            buf[i] = x[i] + (g * buf[i - d] if i >= d else 0.0)
        wet += buf
    for d, g in ((225, 0.5), (556, 0.5)):
        y = np.zeros_like(wet)
        for i in range(len(wet)):
            xd = wet[i - d] if i >= d else 0.0
            yd = y[i - d] if i >= d else 0.0
            y[i] = -g * wet[i] + xd + g * yd
        wet = y
    wet = onepole_lp(wet, 6000) / (np.max(np.abs(wet)) + 1e-9)
    return (1 - mix) * x + mix * wet * np.max(np.abs(sig))


def delay(sig, at, n):
    """Pads `sig` with `at` seconds of silence in front, to length n samples."""
    out = np.zeros(n)
    s = int(at * RATE)
    end = min(n, s + len(sig))
    if s < n:
        out[s:end] = sig[:end - s]
    return out


def mix(*parts, dur=None):
    """Sums (signal, gain, start_seconds) parts into one buffer."""
    n = length(dur) if dur else max(int(p[2] * RATE) + len(p[0]) for p in parts)
    out = np.zeros(n)
    for sig, gain, at in parts:
        out += gain * delay(sig, at, n)
    return out


def normalize(sig, peak=0.89):
    m = np.max(np.abs(sig))
    return sig if m == 0 else sig * (peak / m)


def trim_silence(sig, threshold=1e-4):
    idx = np.nonzero(np.abs(sig) > threshold)[0]
    return sig[: idx[-1] + 1] if len(idx) else sig
