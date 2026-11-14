"""Recorta a Rin (sin fondo) para el launcher.

Entrada: rin_wallpaper.jpg + base_patch.png (fondo reconstruido detrás de las piezas con brisa).
Salida : rin_static.png — RGBA, recorte CROP de la ilustración; Rin sin las piezas que se mecen
         (esas van en breeze_layer.png). Al superponer ambas en reposo se obtiene la Rin original.

GrabCut sobre la imagen con las piezas ya reconstruidas: así la máscara es "Rin sin piezas" y lo que
quede detrás de una pieza al moverse es cuerpo (si lo hay) o transparente (fondo del launcher).
Requiere: opencv-python-headless, numpy, pillow, rembg (modelo isnet-anime).
Uso: python tools/make_rin_cutout.py app/src/main/assets tools/source [preview_dir]
"""
import sys
import cv2
import numpy as np

A = sys.argv[1]          # carpeta de assets de la app (lee breeze_layer.png, escribe rin_static.png)
SRC = sys.argv[2]        # carpeta con rin_wallpaper.jpg y base_patch.png (tools/source)
PREVIEW = sys.argv[3] if len(sys.argv) > 3 else None
img = cv2.imread(f"{SRC}/rin_wallpaper.jpg")
H, W = img.shape[:2]
patch = cv2.imread(f"{SRC}/base_patch.png", cv2.IMREAD_UNCHANGED)
LX, LY = 180, 520
ph, pw = patch.shape[:2]
a = patch[..., 3:4] / 255.0
roi = img[LY:LY + ph, LX:LX + pw].astype(np.float32)
img_p = img.copy()
img_p[LY:LY + ph, LX:LX + pw] = (roi * (1 - a) + patch[..., :3] * a).astype(np.uint8)

CROP = (40, 440, 1180, 2600)  # x0, y0, x1, y1 (igual que RinPanelView)

SIL = [
    (0.32, 0.215), (0.44, 0.19), (0.50, 0.20), (0.585, 0.255), (0.60, 0.30), (0.645, 0.33),
    (0.645, 0.38), (0.665, 0.39), (0.68, 0.41), (0.80, 0.445), (0.915, 0.49), (0.925, 0.53),
    (0.86, 0.55), (0.78, 0.525), (0.71, 0.505), (0.725, 0.56), (0.725, 0.645), (0.70, 0.68),
    (0.80, 0.83), (0.895, 0.86), (0.885, 0.93), (0.775, 0.93), (0.735, 0.855), (0.64, 0.705),
    (0.58, 0.61), (0.50, 0.68), (0.445, 0.80), (0.415, 0.90), (0.405, 0.93), (0.265, 0.93),
    (0.285, 0.87), (0.325, 0.80), (0.375, 0.68), (0.405, 0.58), (0.405, 0.535), (0.33, 0.53),
    (0.165, 0.53), (0.165, 0.46), (0.30, 0.45), (0.405, 0.425), (0.415, 0.395), (0.36, 0.385),
    (0.315, 0.36), (0.335, 0.32), (0.395, 0.30), (0.305, 0.245),
]
poly = np.zeros((H, W), np.uint8)
cv2.fillPoly(poly, [np.array([(x * W, y * H) for x, y in SIL], np.int32)], 255)
outer = cv2.dilate(poly, np.ones((31, 31), np.uint8))
inner = cv2.erode(poly, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (81, 81)))

# Máscara de Rin con el modelo isnet-anime (rembg), sobre la ilustración ORIGINAL (con las piezas).
from rembg import new_session, remove as rembg_remove
from PIL import Image
x0, y0, x1, y1 = CROP
# Fuera de la silueta amplia se pinta blanco: el modelo deja de confundir el dibujo de circuitos
# con el personaje. Luego su resultado se limita a esa misma zona.
zone = outer[y0:y1, x0:x1] > 0
src = img[y0:y1, x0:x1].copy()
src[~zone] = 255
crop_rgb = Image.fromarray(cv2.cvtColor(src, cv2.COLOR_BGR2RGB))
matte = np.asarray(rembg_remove(crop_rgb, session=new_session("isnet-anime"), only_mask=True))
fg = (matte * zone).astype(np.uint8)
# Componente principal (descarta motas sueltas de circuito).
n, lab, st, _ = cv2.connectedComponentsWithStats((fg > 127).astype(np.uint8), 8)
big = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
keep = cv2.dilate((lab == big).astype(np.uint8), np.ones((5, 5), np.uint8))
fg = (fg * keep).astype(np.uint8)

# Piezas con brisa: fuera del cuerpo, lo que hay detrás es fondo -> transparente.
layer = cv2.imread(f"{A}/breeze_layer.png", cv2.IMREAD_UNCHANGED)[..., 3]
pieces = np.zeros((H, W), np.uint8)
pieces[LY:LY + layer.shape[0], LX:LX + layer.shape[1]] = layer
pieces = cv2.dilate((pieces > 0).astype(np.uint8) * 255, np.ones((5, 5), np.uint8))
BODY = [  # cabeza (sin lazo ni mechón), torso, brazo derecho, short y piernas
    (0.452, 0.318), (0.490, 0.303), (0.560, 0.300), (0.645, 0.330), (0.640, 0.385), (0.689, 0.442), (0.707, 0.456),
    (0.742, 0.476), (0.820, 0.494), (0.873, 0.505), (0.910, 0.515), (0.885, 0.525), (0.852, 0.533),
    (0.820, 0.538), (0.738, 0.526), (0.680, 0.500), (0.648, 0.474), (0.655, 0.505), (0.700, 0.515),
    (0.725, 0.560),
    (0.700, 0.680), (0.800, 0.830), (0.895, 0.860), (0.885, 0.930), (0.775, 0.930), (0.640, 0.705),
    (0.611, 0.631), (0.5625, 0.570), (0.533, 0.631), (0.500, 0.680), (0.415, 0.900), (0.280, 0.935),
    (0.276, 0.925), (0.282, 0.900), (0.325, 0.875), (0.333, 0.800), (0.405, 0.580), (0.420, 0.470),
    (0.440, 0.410), (0.425, 0.380), (0.440, 0.330)]
body = np.zeros((H, W), np.uint8)
cv2.fillPoly(body, [np.array([(x * W, y * H) for x, y in BODY], np.int32)], 255)
remove = ((pieces > 0) & (body == 0))[y0:y1, x0:x1]
fg[remove] = 0
# Solo cuerpo (con margen de 12 px): elimina restos de franjas y circuitos que el modelo dejó pegados.
body_m = cv2.dilate(body, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)))[y0:y1, x0:x1]
fg[body_m == 0] = 0
# Pelado de halos: zonas claras y poco saturadas (como el fondo) que tocan el borde exterior del
# recorte. Lo blanco de Rin (botas, camisa) queda protegido por su contorno oscuro.
hsv_c = cv2.cvtColor(img_p[y0:y1, x0:x1], cv2.COLOR_BGR2HSV)
# Solo junto a las botas (la piel y la camisa también son claras: fuera de estas zonas no se pela).
peel_zone = np.zeros((H, W), np.uint8)
for (ax, ay, bx, by) in ((0.22, 0.83, 0.33, 0.955), (0.68, 0.83, 0.80, 0.955)):
    cv2.rectangle(peel_zone, (int(ax * W), int(ay * H)), (int(bx * W), int(by * H)), 255, -1)
peel_zone = peel_zone[y0:y1, x0:x1] > 0
light = ((hsv_c[..., 1] < 40) & (hsv_c[..., 2] > 180) & (fg > 0) & peel_zone).astype(np.uint8)
light = cv2.erode(light, np.ones((3, 3), np.uint8))
n, lab = cv2.connectedComponents(light, connectivity=4)
edge = cv2.dilate((fg == 0).astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
touching = np.unique(lab[edge & (light > 0)])
halo = np.isin(lab, touching[touching > 0])
halo = cv2.dilate(halo.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
fg[halo] = 0
fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
alpha = cv2.GaussianBlur(fg, (3, 3), 0)

rgba = cv2.cvtColor(img_p[y0:y1, x0:x1], cv2.COLOR_BGR2BGRA)  # bajo las piezas: cuerpo reconstruido
rgba[..., 3] = alpha
rgba[alpha == 0, :3] = 0
cv2.imwrite(f"{A}/rin_static.png", rgba, [cv2.IMWRITE_PNG_COMPRESSION, 9])
print("rin_static.png", rgba.shape, "cobertura", round(float((alpha > 0).mean()), 3))

if PREVIEW:
    def on(bg):
        out = bg.copy().astype(np.float32)
        aa = alpha[..., None] / 255.0
        out = out * (1 - aa) + rgba[..., :3] * aa
        return out.astype(np.uint8)
    h, w = alpha.shape
    dark = np.full((h, w, 3), (60, 40, 30), np.uint8)
    chk = np.indices((h, w)).sum(0) // 40 % 2
    check = np.where(chk[..., None] == 1, 230, 200).astype(np.uint8).repeat(3, 2)
    cv2.imwrite(f"{PREVIEW}/cutout_check.png", cv2.resize(np.hstack([on(dark), on(check)]), None, fx=0.4, fy=0.4))
