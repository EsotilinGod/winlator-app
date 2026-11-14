"""Fondo del launcher a partir del fondo de pantalla original.

1. Quita a Rin de la ilustración (1220 × 2712) con LaMa (inpainting por red neuronal), usando como
   máscara su silueta completa (rin_static.png + breeze_layer.png, con margen).
2. Traspone la imagen (intercambia ejes) -> 2712 × 1220, exactamente la pantalla horizontal del
   Xiaomi 14T, sin reescalar. Las pistas a 0/45/90° siguen siendo pistas y las franjas diagonales
   conservan su inclinación.
3. Extrae los circuitos dorados del fondo horizontal (sin la zona donde se dibuja Rin) y calcula, para
   cada punto de pista, la distancia recorrida A LO LARGO del trazo desde la silueta de Rin
   (Dijkstra). Con eso el shader hace que la corriente de luz nazca de Rin.

Salidas en <assets>: launcher_bg.jpg, circuit_glow.png (RGBA, media resolución),
circuit_flow.png (R = byte alto, G = byte bajo de la distancia, B = válido; media resolución).

Requiere: torch, opencv-python-headless, numpy, pillow y el modelo big-lama.pt (TorchScript).
Uso: python tools/make_launcher_bg.py app/src/main/assets tools/source <big-lama.pt> [preview_dir]
"""
import heapq
import sys

import cv2
import numpy as np

A, SRC, MODEL = sys.argv[1], sys.argv[2], sys.argv[3]
PREVIEW = sys.argv[4] if len(sys.argv) > 4 else None

# Colocación de Rin en pantalla (debe coincidir con RinPanelView: SIZE, CENTER_X, RIBBON_TOP).
SCREEN_W, SCREEN_H = 2712, 1220
CROP_X, CROP_Y, CROP_W, CROP_H = 40, 440, 1140, 2160
SIZE, CENTER_X, RIBBON_TOP = 1.45, 0.76, 75

img = cv2.imread(f"{SRC}/rin_wallpaper.jpg")
H, W = img.shape[:2]
static_a = cv2.imread(f"{A}/rin_static.png", cv2.IMREAD_UNCHANGED)[..., 3]
layer_a = cv2.imread(f"{A}/breeze_layer.png", cv2.IMREAD_UNCHANGED)[..., 3]

# --- 1. Máscara de Rin en la ilustración original e inpainting con LaMa ---
mask = np.zeros((H, W), np.uint8)
mask[CROP_Y:CROP_Y + static_a.shape[0], CROP_X:CROP_X + static_a.shape[1]] = static_a
roi = mask[520:520 + layer_a.shape[0], 180:180 + layer_a.shape[1]]
np.maximum(roi, layer_a, out=roi)
mask = (mask > 20).astype(np.uint8) * 255
n, lab, st, _ = cv2.connectedComponentsWithStats(255 - mask, 4)
for i in range(1, n):  # huecos interiores (p. ej. entre correa y pierna)
    x, y, w, h, area = st[i]
    if area < 20000 and x > 0 and y > 0 and x + w < W and y + h < H:
        mask[lab == i] = 255
# Margen amplio: el dibujo tiene un resplandor blanco alrededor de Rin que también hay que quitar.
mask = cv2.dilate(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (71, 71)))

import torch  # noqa: E402

torch.set_grad_enabled(False)
model = torch.jit.load(MODEL, map_location="cpu").eval()
def lama(bgr, msk):
    h0, w0 = msk.shape
    ph, pw = (8 - h0 % 8) % 8, (8 - w0 % 8) % 8
    rgb = np.pad(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB), ((0, ph), (0, pw), (0, 0)), mode="reflect")
    mp = np.pad(msk, ((0, ph), (0, pw)), mode="reflect")
    t_img = torch.from_numpy(rgb).permute(2, 0, 1)[None].float() / 255
    t_mask = (torch.from_numpy(mp)[None, None].float() > 127).float()
    o = model(t_img, t_mask)[0].permute(1, 2, 0).numpy()
    return cv2.cvtColor(np.clip(o * 255, 0, 255).astype(np.uint8)[:h0, :w0], cv2.COLOR_RGB2BGR)

# Huecos grandes: LaMa rinde mejor cerca de su escala de entrenamiento -> se reconstruye a 0,45×,
# se reescala y solo se usa dentro de la máscara (fuera quedan los píxeles originales).
SC = 0.45
small = lama(cv2.resize(img, None, fx=SC, fy=SC, interpolation=cv2.INTER_AREA),
             cv2.resize(mask, None, fx=SC, fy=SC, interpolation=cv2.INTER_NEAREST))
up = cv2.resize(small, (W, H), interpolation=cv2.INTER_CUBIC)
# Mezcla con borde difuminado para que no se note la transición.
blend = cv2.GaussianBlur(mask, (0, 0), 6)[..., None] / 255.0
clean = (img * (1 - blend) + up * blend).astype(np.uint8)

# --- 2. Trasponer a horizontal ---
land = cv2.transpose(clean)  # (W × H) -> (H × W) = 2712 × 1220
assert land.shape[1] == SCREEN_W and land.shape[0] == SCREEN_H
cv2.imwrite(f"{A}/launcher_bg.jpg", land, [cv2.IMWRITE_JPEG_QUALITY, 92])

# --- 3. Circuitos del fondo horizontal + distancia a lo largo de las pistas desde Rin ---
# Silueta de Rin tal como se dibuja en pantalla.
s = SCREEN_H * SIZE / CROP_H
fw, fh = int(CROP_W * s), int(CROP_H * s)
left = int(SCREEN_W * CENTER_X - fw / 2)
top = int(SCREEN_H * 0.02 - RIBBON_TOP * s)
full_a = np.zeros((CROP_H, CROP_W), np.uint8)
full_a[:] = static_a
r2 = full_a[520 - CROP_Y:520 - CROP_Y + layer_a.shape[0], 180 - CROP_X:180 - CROP_X + layer_a.shape[1]]
np.maximum(r2, layer_a, out=r2)
fig = cv2.resize(full_a, (fw, fh), interpolation=cv2.INTER_AREA)
sil = np.zeros((SCREEN_H, SCREEN_W), np.uint8)
ys0, ys1 = max(top, 0), min(top + fh, SCREEN_H)
xs0, xs1 = max(left, 0), min(left + fw, SCREEN_W)
sil[ys0:ys1, xs0:xs1] = fig[ys0 - top:ys1 - top, xs0 - left:xs1 - left]
sil = cv2.dilate((sil > 20).astype(np.uint8) * 255, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (31, 31)))

hsv = cv2.cvtColor(land, cv2.COLOR_BGR2HSV).astype(np.float32)
hh_, ss_, vv_ = hsv[..., 0], hsv[..., 1] / 255, hsv[..., 2] / 255
bgr = land.astype(np.float32) / 255
b_, g_, r_ = bgr[..., 0], bgr[..., 1], bgr[..., 2]
ratio = (g_ - b_) / (r_ - b_ + 1e-6)
gold = (ss_ > 0.4) & (vv_ > 0.75) & (r_ >= g_) & (g_ > b_) & (ratio > 0.35) & (ratio < 0.95) & (sil == 0)
m = gold.astype(np.uint8) * 255
m = cv2.dilate(cv2.erode(m, np.ones((5, 5), np.uint8)), np.ones((5, 5), np.uint8))

hw, hh = SCREEN_W // 2, SCREEN_H // 2
line = cv2.resize(m, (hw, hh), interpolation=cv2.INTER_AREA)
halo = cv2.GaussianBlur(line, (0, 0), 7)
la, ha = line / 255.0, halo / 255.0
alpha = np.clip(np.maximum(la * 0.85, np.minimum(ha * 2.2, 1.0) * 0.55), 0, 1)
glow = np.zeros((hh, hw, 4), np.uint8)
glow[..., 0], glow[..., 1], glow[..., 2] = 64, 196, 255  # BGR dorado cálido
glow[..., 3] = (alpha * 255).astype(np.uint8)
cv2.imwrite(f"{A}/circuit_glow.png", glow, [cv2.IMWRITE_PNG_COMPRESSION, 9])

core = (line > 100).astype(np.uint8)
sil_h = (cv2.resize(sil, (hw, hh), interpolation=cv2.INTER_NEAREST) > 0).astype(np.uint8)
d_sil = cv2.distanceTransform(1 - sil_h, cv2.DIST_L2, 5)
n, lab = cv2.connectedComponents(core, connectivity=8)
INF = np.float32(1e9)
dist = np.full((hh, hw), INF, np.float32)
heap = []
for i in range(1, n):
    ys, xs = np.nonzero(lab == i)
    if len(ys) < 15:
        continue
    ds = d_sil[ys, xs]
    sel = ds <= ds.min() + 1.5
    for y, x, d0 in zip(ys[sel], xs[sel], ds[sel]):
        dist[y, x] = d0
        heapq.heappush(heap, (float(d0), int(y), int(x)))
steps = [(-1, -1, 1.4142), (-1, 0, 1.0), (-1, 1, 1.4142), (0, -1, 1.0), (0, 1, 1.0),
         (1, -1, 1.4142), (1, 0, 1.0), (1, 1, 1.4142)]
while heap:
    d, y, x = heapq.heappop(heap)
    if d > dist[y, x]:
        continue
    for dy, dx, wgt in steps:
        yy, xx = y + dy, x + dx
        if 0 <= yy < hh and 0 <= xx < hw and core[yy, xx] and d + wgt < dist[yy, xx]:
            dist[yy, xx] = d + wgt
            heapq.heappush(heap, (d + wgt, yy, xx))
valid = core.astype(bool) & (dist < INF)
src = np.where(valid, 0, 255).astype(np.uint8)
_, labels = cv2.distanceTransformWithLabels(src, cv2.DIST_L2, 5, labelType=cv2.DIST_LABEL_PIXEL)
zy, zx = np.nonzero(src == 0)
lut = np.concatenate([[0], dist[zy, zx]]).astype(np.float32)
fulld = lut[labels]
near = alpha > 0.02
d16 = np.clip(np.round(fulld), 0, 65535).astype(np.uint16)
flow = np.zeros((hh, hw, 3), np.uint8)  # BGR: B = válido, G = byte bajo, R = byte alto
flow[..., 2] = np.where(near, d16 >> 8, 0).astype(np.uint8)
flow[..., 1] = np.where(near, d16 & 255, 0).astype(np.uint8)
flow[..., 0] = np.where(near, 255, 0)
cv2.imwrite(f"{A}/circuit_flow.png", flow, [cv2.IMWRITE_PNG_COMPRESSION, 9])
print(f"fondo {land.shape[1]}x{land.shape[0]}; circuito: distancia máx. {float(fulld[near].max()):.0f} px, "
      f"cobertura {float((alpha > 0.05).mean()):.3f}")

if PREVIEW:
    cv2.imwrite(f"{PREVIEW}/bg_portrait_clean.png", cv2.resize(clean, None, fx=0.35, fy=0.35))
    cv2.imwrite(f"{PREVIEW}/bg_land.png", cv2.resize(land, None, fx=0.5, fy=0.5))
    col = cv2.applyColorMap(np.clip(fulld * 255 / max(float(fulld[near].max()), 1), 0, 255).astype(np.uint8), cv2.COLORMAP_TURBO)
    col[~near] = 0
    col[cv2.resize(sil, (hw, hh)) > 0] = (80, 80, 80)
    cv2.imwrite(f"{PREVIEW}/bg_flow.png", col)
