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

# ---------------------------------------------------------------------------------------------
# Mapa de "corriente" (opcional, 3er argumento): distancia medida A LO LARGO de los trazos desde
# el borde de la silueta de Rin hacia afuera. El shader de la app lo usa para que un pulso de luz
# recorra los circuitos siguiendo su forma.
#   R = byte alto, G = byte bajo de la distancia (px a media resolución), B = 255 si es válido.
# Requiere opencv-python-headless.
if len(sys.argv) > 3:
    import heapq
    import cv2

    flow_out = sys.argv[3]
    core = (np.asarray(line) > 100).astype(np.uint8)
    sil_h = (np.asarray(sil.resize((hw, hh), Image.NEAREST)) > 0).astype(np.uint8)
    # Distancia euclídea de cada píxel a la silueta (origen de la corriente).
    d_sil = cv2.distanceTransform((1 - sil_h).astype(np.uint8), cv2.DIST_L2, 5)

    n, lab = cv2.connectedComponents(core, connectivity=8)
    INF = np.float32(1e9)
    dist = np.full((hh, hw), INF, np.float32)
    heap = []
    for i in range(1, n):
        ys, xs = np.nonzero(lab == i)
        if len(ys) < 15:
            continue
        ds = d_sil[ys, xs]
        sel = ds <= ds.min() + 1.5  # extremo del trazo más cercano a Rin
        for y, x, d0 in zip(ys[sel], xs[sel], ds[sel]):
            dist[y, x] = d0
            heapq.heappush(heap, (float(d0), int(y), int(x)))
    steps = [(-1, -1, 1.4142), (-1, 0, 1.0), (-1, 1, 1.4142), (0, -1, 1.0), (0, 1, 1.0),
             (1, -1, 1.4142), (1, 0, 1.0), (1, 1, 1.4142)]
    while heap:  # Dijkstra restringido a los píxeles del trazo
        d, y, x = heapq.heappop(heap)
        if d > dist[y, x]:
            continue
        for dy, dx, w in steps:
            yy, xx = y + dy, x + dx
            if 0 <= yy < hh and 0 <= xx < hw and core[yy, xx] and d + w < dist[yy, xx]:
                dist[yy, xx] = d + w
                heapq.heappush(heap, (d + w, yy, xx))
    valid_core = core.astype(bool) & (dist < INF)

    # El halo hereda la distancia del punto de trazo más cercano.
    src = np.where(valid_core, 0, 255).astype(np.uint8)
    _, labels = cv2.distanceTransformWithLabels(src, cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
    zy, zx = np.nonzero(src == 0)  # las etiquetas siguen el orden de recorrido por filas
    lut = np.concatenate([[0], dist[zy, zx]]).astype(np.float32)
    full = lut[labels]
    near = alpha > 0.02
    d16 = np.clip(np.round(full), 0, 65535).astype(np.uint16)
    rgb = np.zeros((hh, hw, 3), np.uint8)
    rgb[..., 0] = (d16 >> 8).astype(np.uint8)
    rgb[..., 1] = (d16 & 255).astype(np.uint8)
    rgb[..., 2] = np.where(near, 255, 0)
    rgb[~near, :2] = 0
    Image.fromarray(rgb, "RGB").save(flow_out, optimize=True)
    print(f"{flow_out}: distancia máx. {float(full[near].max()):.0f} px, componentes {n - 1}")
