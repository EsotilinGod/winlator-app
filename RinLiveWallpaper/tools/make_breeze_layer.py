"""Separa las piezas que se mecen (lazo, mechón izquierdo, pañuelo, correa) del fondo.

Salidas (en el directorio de assets indicado):
  breeze_layer.png : RGBA recortado; solo los píxeles de las piezas móviles.
  base_patch.png   : RGBA recortado; fondo reconstruido (inpainting) detrás de las piezas, solo donde
                     estas se mueven. Se pega una vez sobre la ilustración original al cargarla.
Imprime el desplazamiento (offset) del recorte, que debe coincidir con BreezeMesh.LAYER_X/Y.

Requiere opencv-python-headless, numpy y pillow.
Uso: python tools/make_breeze_layer.py app/src/main/assets/rin_wallpaper.jpg app/src/main/assets [preview_dir]
"""
import math
import sys
import cv2
import numpy as np

SRC, OUT = sys.argv[1], sys.argv[2]
PREVIEW = sys.argv[3] if len(sys.argv) > 3 else None
bgr = cv2.imread(SRC)
H, W = bgr.shape[:2]
hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)

# Recorte de la capa (múltiplos de 2 por limpieza). Debe coincidir con BreezeMesh.kt.
LX, LY, LW, LH = 180, 520, 720, 1280


def poly_mask(pts, shape=(H, W)):
    m = np.zeros(shape, np.uint8)
    cv2.fillPoly(m, [np.array(pts, np.int32)], 255)
    return m


def grabcut(zone_pts, fg_pts_list, iters=6):
    """GrabCut dentro de una zona poligonal: fuera = fondo seguro; polígonos fg = primer plano seguro."""
    zone = poly_mask(zone_pts)
    x, y, w, h = cv2.boundingRect(np.array(zone_pts, np.int32))
    pad = 30
    x0, y0 = max(x - pad, 0), max(y - pad, 0)
    x1, y1 = min(x + w + pad, W), min(y + h + pad, H)
    m = np.full((y1 - y0, x1 - x0), cv2.GC_BGD, np.uint8)
    m[zone[y0:y1, x0:x1] > 0] = cv2.GC_PR_FGD
    for pts in fg_pts_list:
        m[poly_mask(pts)[y0:y1, x0:x1] > 0] = cv2.GC_FGD
    bg_model = np.zeros((1, 65), np.float64); fg_model = np.zeros((1, 65), np.float64)
    cv2.grabCut(bgr[y0:y1, x0:x1], m, None, bg_model, fg_model, iters, cv2.GC_INIT_WITH_MASK)
    out = np.zeros((H, W), np.uint8)
    out[y0:y1, x0:x1] = np.where((m == cv2.GC_FGD) | (m == cv2.GC_PR_FGD), 255, 0)
    return out


def largest_components(m, keep=1, min_area=0):
    n, lab, stats, _ = cv2.connectedComponentsWithStats(m, 8)
    order = sorted(range(1, n), key=lambda i: -stats[i, cv2.CC_STAT_AREA])
    out = np.zeros_like(m)
    for i in order[:keep]:
        if stats[i, cv2.CC_STAT_AREA] >= min_area:
            out[lab == i] = 255
    return out


def fill_holes(m):
    inv = cv2.bitwise_not(m)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(inv, 4)
    out = m.copy()
    for i in range(1, n):
        x, y, w, h, a = stats[i]
        if x > 0 and y > 0 and x + w < W and y + h < H and a < 4000:
            out[lab == i] = 255
    return out


# --- Lazo (blanco sobre fondo claro: GrabCut) ---
ribbon = grabcut(
    zone_pts=[(392, 590), (470, 606), (522, 530), (560, 548), (665, 638), (708, 690), (704, 748),
              (668, 790), (605, 795), (560, 775), (500, 740), (445, 680)],
    fg_pts_list=[[(430, 612), (520, 655), (585, 745), (560, 750), (480, 690)],
                 [(545, 575), (640, 650), (680, 700), (660, 745), (610, 740), (570, 660)]],
)
ribbon = largest_components(ribbon, 1)

# --- Mechón izquierdo: zona que empieza donde terminan los circuitos; pelo = todo lo que no es
#     fondo claro y poco saturado (blanco/gris) dentro de la zona ---
hair_zone = poly_mask([(436, 860), (478, 828), (522, 838), (545, 878), (535, 940), (505, 1002),
                       (462, 1008), (440, 965)])
sat, val = hsv[..., 1], hsv[..., 2]
hair = (((sat > 45) | (val < 170)) & (hair_zone > 0)).astype(np.uint8) * 255
hair = cv2.morphologyEx(hair, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3)))
hair = cv2.morphologyEx(hair, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
hair = largest_components(hair, 1)

# --- Pañuelo (amarillo saturado, dos colas). Apertura grande para descartar los trazos de circuito
#     (~10 px) y reconstrucción geodésica limitada para recuperar las puntas finas. ---
scarf_zone = poly_mask([(190, 1240), (330, 1195), (470, 1105), (560, 1075), (605, 1110), (565, 1165),
                        (600, 1170), (545, 1280), (445, 1372), (335, 1438), (215, 1452), (178, 1410)])
yel = cv2.bitwise_and(cv2.inRange(hsv, (12, 70, 120), (35, 255, 255)), scarf_zone)
seed = cv2.morphologyEx(yel, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15)))
seed = largest_components(seed, 2, min_area=2000)
k3 = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
scarf = seed
for _ in range(8):
    scarf = cv2.bitwise_and(cv2.dilate(scarf, k3), yel)
scarf = cv2.morphologyEx(scarf, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
scarf = fill_holes(scarf)
# Puntas finas de las dos colas: píxeles que no son fondo claro, conectados al pañuelo.
tips = poly_mask([(200, 1250), (295, 1250), (300, 1312), (200, 1310)])
tips |= poly_mask([(200, 1372), (325, 1366), (330, 1432), (200, 1432)])
notbg = (((hsv[..., 1] > 110) | (hsv[..., 2] < 150)) & (tips > 0)).astype(np.uint8) * 255
grown = scarf.copy()
k3r = np.ones((3, 3), np.uint8)
for _ in range(200):  # reconstrucción geodésica acotada a las cajas de las puntas
    nxt = cv2.bitwise_or(scarf, cv2.bitwise_and(cv2.dilate(grown, k3r), notbg))
    if np.array_equal(nxt, grown):
        break
    grown = nxt
scarf = cv2.morphologyEx(grown, cv2.MORPH_CLOSE, k3)

# --- Correa (banda amarilla fina sobre la pierna) ---
strap_zone = poly_mask([(690, 1480), (720, 1470), (735, 1520), (700, 1600), (690, 1690), (720, 1720),
                        (790, 1690), (835, 1625), (848, 1555), (835, 1470), (885, 1470), (885, 1610),
                        (830, 1700), (760, 1745), (690, 1745), (655, 1700), (640, 1620), (670, 1540)])
strap = cv2.bitwise_and(cv2.inRange(hsv, (8, 90, 100), (35, 255, 255)), strap_zone)
strap = cv2.morphologyEx(strap, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
strap = largest_components(strap, 2, min_area=300)

pieces = cv2.bitwise_or(cv2.bitwise_or(ribbon, hair), cv2.bitwise_or(scarf, strap))
# Incluir el contorno oscuro de cada pieza.
pieces = cv2.dilate(pieces, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))

# --- Pesos de movimiento (misma fórmula que BreezeMesh.kt) para saber dónde hay desplazamiento ---
FLAPS = [  # cx, cy, rx, ry, ax, ay, L
    (.470, .240, .125, .062, .495, .290, 230), (.390, .340, .100, .040, .475, .318, 210),
    (.310, .465, .190, .068, .470, .410, 450), (.620, .590, .100, .078, .600, .530, 290)]
FACE = (.505, .350, .062, .036)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
nx, ny = xx / W, yy / H
face = smooth(1.0, 1.6, np.hypot((nx - FACE[0]) / FACE[2], (ny - FACE[1]) / FACE[3]))
wmax = np.zeros((H, W), np.float32)
for cx, cy, rx, ry, ax, ay, L in FLAPS:
    ell = 1 - smooth(0.75, 1.15, np.hypot((nx - cx) / rx, (ny - cy) / ry))
    d = np.clip(np.hypot(xx - ax * W, yy - ay * H) / L, 0, 1) ** 1.1
    wmax = np.maximum(wmax, ell * d * face)
moving = (wmax > 0.01).astype(np.uint8) * 255

# Las piezas que no se mueven no necesitan ir en la capa (evita bordes innecesarios).
layer_mask = cv2.bitwise_and(pieces, cv2.dilate(moving, np.ones((25, 25), np.uint8)))

# --- Fondo reconstruido: solo donde la pieza se mueve, con margen para el desplazamiento máximo ---
hole = cv2.bitwise_and(cv2.dilate(layer_mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))), moving)
inpainted = cv2.inpaint(bgr, hole, 9, cv2.INPAINT_TELEA)
patch_alpha = cv2.GaussianBlur(hole, (5, 5), 0)

# Alfa de la capa: borde suavizado 1 px.
layer_alpha = cv2.GaussianBlur(layer_mask, (3, 3), 0)

assert layer_mask[:LY].max() == 0 and layer_mask[LY + LH:].max() == 0, "la capa excede el recorte en Y"
assert layer_mask[:, :LX].max() == 0 and layer_mask[:, LX + LW:].max() == 0, "la capa excede el recorte en X"

def save_rgba(path, rgb_bgr, alpha):
    rgba = cv2.cvtColor(rgb_bgr, cv2.COLOR_BGR2BGRA)
    rgba[..., 3] = alpha
    rgba[alpha == 0, :3] = 0
    crop = rgba[LY:LY + LH, LX:LX + LW]
    cv2.imwrite(path, crop, [cv2.IMWRITE_PNG_COMPRESSION, 9])

save_rgba(f"{OUT}/breeze_layer.png", bgr, layer_alpha)
save_rgba(f"{OUT}/base_patch.png", inpainted, patch_alpha)
print(f"offset LX={LX} LY={LY} size={LW}x{LH}; píxeles capa={int((layer_mask > 0).sum())}, hueco={int((hole > 0).sum())}")

if PREVIEW:
    ov = bgr.copy()
    for m, col in ((ribbon, (255, 0, 255)), (hair, (255, 0, 0)), (scarf, (0, 0, 255)), (strap, (0, 160, 0))):
        ov[m > 0] = (0.5 * ov[m > 0] + 0.5 * np.array(col)).astype(np.uint8)
    cv2.imwrite(f"{PREVIEW}/seg_overlay.png", cv2.resize(ov[LY:LY + LH, LX:LX + LW], (LW // 2 * 2 // 2, LH // 2)))
    base = bgr.copy()
    a = patch_alpha[..., None] / 255.0
    base = (base * (1 - a) + inpainted * a).astype(np.uint8)
    cv2.imwrite(f"{PREVIEW}/base_sin_piezas.png", cv2.resize(base[LY:LY + LH, LX:LX + LW], (LW // 2, LH // 2)))
