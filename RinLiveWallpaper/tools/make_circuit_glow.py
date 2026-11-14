"""Genera app/src/main/assets/circuit_glow.png a partir de rin_wallpaper.jpg.

1. Detecta los trazos dorados del fondo por color (tono ámbar, saturación y brillo altos).
2. Excluye la silueta de Rin (pelo, lazo, pañuelo y correa también son amarillos).
3. Elimina el ruido con una apertura morfológica.
4. Guarda una capa RGBA a media resolución: color dorado, alfa = trazo + halo difuminado.

Uso: python3 tools/make_circuit_glow.py app/src/main/assets/rin_wallpaper.jpg app/src/main/assets/circuit_glow.png
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

src, out = sys.argv[1], sys.argv[2]
img = Image.open(src).convert("RGB")
W, H = img.size
a = np.asarray(img).astype(np.float32) / 255
r, g, b = a[..., 0], a[..., 1], a[..., 2]
mx, mn = a.max(-1), a.min(-1)
sat = (mx - mn) / (mx + 1e-6)
ratio = (g - b) / (r - b + 1e-6)
gold = (sat > 0.4) & (mx > 0.75) & (r >= g) & (g > b) & (ratio > 0.35) & (ratio < 0.95)

# Silueta de Rin con margen (coordenadas normalizadas, sentido horario desde el lazo).
SILHOUETTE = [
    (0.32, 0.215), (0.44, 0.19), (0.50, 0.20), (0.585, 0.255), (0.60, 0.30), (0.645, 0.33),
    (0.645, 0.38), (0.665, 0.39), (0.68, 0.41), (0.80, 0.445), (0.915, 0.49), (0.925, 0.53),
    (0.86, 0.55), (0.78, 0.525), (0.71, 0.505), (0.725, 0.56), (0.725, 0.645), (0.70, 0.68),
    (0.80, 0.83), (0.895, 0.86), (0.885, 0.93), (0.775, 0.93), (0.735, 0.855), (0.64, 0.705),
    (0.58, 0.61), (0.50, 0.68), (0.445, 0.80), (0.415, 0.90), (0.405, 0.93), (0.265, 0.93),
    (0.285, 0.87), (0.325, 0.80), (0.375, 0.68), (0.405, 0.58), (0.405, 0.535), (0.33, 0.53),
    (0.165, 0.53), (0.165, 0.46), (0.30, 0.45), (0.405, 0.425), (0.415, 0.395), (0.36, 0.385),
    (0.315, 0.36), (0.335, 0.32), (0.395, 0.30), (0.305, 0.245),
]
sil = Image.new("L", (W, H), 0)
ImageDraw.Draw(sil).polygon([(x * W, y * H) for x, y in SILHOUETTE], fill=255)
sil = sil.filter(ImageFilter.MaxFilter(21))  # margen extra de ~10 px
gold &= np.asarray(sil) == 0

# Apertura: quita motas más finas que los trazos (~10 px de ancho a resolución completa).
m = Image.fromarray((gold * 255).astype(np.uint8))
m = m.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))

hw, hh = W // 2, H // 2
line = m.resize((hw, hh), Image.LANCZOS)
halo = line.filter(ImageFilter.GaussianBlur(7))
la = np.asarray(line).astype(np.float32) / 255
ha = np.asarray(halo).astype(np.float32) / 255
alpha = np.clip(np.maximum(la * 0.85, np.minimum(ha * 2.2, 1.0) * 0.55), 0, 1)

rgba = np.zeros((hh, hw, 4), np.uint8)
rgba[..., 0], rgba[..., 1], rgba[..., 2] = 255, 196, 64  # dorado cálido
rgba[..., 3] = (alpha * 255).astype(np.uint8)
Image.fromarray(rgba, "RGBA").save(out, optimize=True)
print(f"{out}: {hw}x{hh}, cobertura {float((alpha > 0.05).mean()):.3f}")
