"""Sonidos originales del menú XMB, sintetizados (no se copian sonidos de ningún producto).

Cada sonido es una suma de parciales senoidales con envolvente de decaimiento exponencial
(a(t) = e^(-t/τ)), un transitorio breve de ruido filtrado para el "ataque" y una reverberación corta
(red de 3 retardos con realimentación). Salida: WAV 16 bit, 44,1 kHz, mono, en app/src/main/res/raw.

Uso: python tools/make_sounds.py app/src/main/res/raw
"""
import sys
import wave
import numpy as np

SR = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(2301)


def tone(freqs_amps, dur, tau, attack=0.0015, start=0.0, total=None):
    total = total or dur
    t = np.arange(int(SR * total)) / SR
    y = np.zeros_like(t)
    tt = t - start
    on = tt >= 0
    env = np.where(on, np.minimum(tt / attack, 1.0) * np.exp(-np.clip(tt, 0, None) / tau), 0.0)
    for f, a in freqs_amps:
        y += a * np.sin(2 * np.pi * f * np.clip(tt, 0, None)) * env
    return y


def click(n_ms, cutoff, total):
    """Transitorio de ruido con paso bajo de un polo (el 'tic' del ataque)."""
    n = int(SR * n_ms / 1000)
    noise = rng.standard_normal(n) * np.exp(-np.arange(n) / (n / 4))
    a = np.exp(-2 * np.pi * cutoff / SR)
    out = np.zeros(n)
    for i in range(n):
        out[i] = (1 - a) * noise[i] + a * (out[i - 1] if i else 0)
    y = np.zeros(int(SR * total))
    y[:n] = out
    return y


def reverb(y, mix=0.22, fb=0.35):
    out = y.copy()
    for d_ms, g in ((23, 1.0), (31, 0.8), (41, 0.6)):
        d = int(SR * d_ms / 1000)
        buf = np.zeros_like(y)
        for i in range(d, len(y)):
            buf[i] = y[i - d] + fb * buf[i - d]
        out += mix * g * buf
    return out


def save(name, y, peak_db=-6.0):
    y = y - np.mean(y)
    fade = int(SR * 0.004)
    y[-fade:] *= np.linspace(1, 0, fade)
    y = y / (np.max(np.abs(y)) + 1e-9) * (10 ** (peak_db / 20))
    pcm = (y * 32767).astype(np.int16)
    with wave.open(f"{OUT}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(name, f"{len(y) / SR * 1000:.0f} ms")


# Moverse entre elementos: "tic" agudo y cristalino, muy corto.
T = 0.14
y = tone([(2350, 1.0), (3520, 0.35), (4700, 0.12)], T, 0.016, total=T) + 0.35 * click(2.5, 5000, T)
save("xmb_nav", reverb(y, 0.18), -10)

# Cambiar de categoría: algo más grave y con más cuerpo.
T = 0.2
y = tone([(1480, 1.0), (2220, 0.45), (2960, 0.15)], T, 0.028, total=T) + 0.3 * click(4, 3000, T)
save("xmb_cat", reverb(y, 0.22), -9)

# Abrir / confirmar: campana de dos notas (Mi6 y Si6) con parciales inarmónicos tipo campana.
T = 0.75
bell = lambda f, s: tone([(f, 1.0), (f * 2.0, 0.28), (f * 2.76, 0.18), (f * 5.4, 0.05)], T, 0.17, 0.003, s, T)
y = bell(1318.5, 0.0) + 0.9 * bell(1975.5, 0.075)
save("xmb_ok", reverb(y, 0.3, 0.4), -7)

# Abrir opciones: tono suave descendente (Do6 -> Sol5).
T = 0.25
t = np.arange(int(SR * T)) / SR
f = 1046.5 * (783.99 / 1046.5) ** np.clip(t / 0.09, 0, 1)
phase = 2 * np.pi * np.cumsum(f) / SR
y = (np.sin(phase) + 0.25 * np.sin(2 * phase)) * np.minimum(t / 0.003, 1) * np.exp(-t / 0.06)
save("xmb_opt", reverb(y, 0.2), -9)
