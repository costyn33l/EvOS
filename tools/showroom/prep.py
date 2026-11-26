"""Pregătește mașinile Showroom din profilele laterale primite: decupare, ascunderea emblemelor (capacele roților),
aceeași scară (px/m) și aceeași linie a solului, masca vopselei pentru recolorare. Scrie în assets/showroom/."""
import json, math, os
import numpy as np, cv2
from PIL import Image

SRC = 'side.jpg'
OUT = '/home/claude/evos/app/src/main/assets/showroom/'
PREV = '/home/claude/work/cars/prev/'
os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)

PXM = 140            # pixeli pe metru în imaginea finală
CW, CH = 768, 300    # pânza comună (5,49 m × 2,14 m)
GROUND = 282         # linia solului pe pânză

# panou (în colaj), roți (cx, cy, r anvelopă) în coordonatele panoului, lungimea reală (m), culoarea originală, tip vopsea
CARS = {
    'hatch': dict(box=(10, 40, 595, 296), wheels=[(142, 143.5, 36), (461, 143.5, 36)], length=4.46, src='red', kind='red'),
    'sedan': dict(box=(606, 40, 1191, 296), wheels=[(119, 144, 37), (445, 144, 37)], length=4.75, src='silver', kind='neutral', thr=105,
                  excl=[[(212, 70), (258, 31), (325, 24), (410, 28), (446, 52), (446, 65), (220, 72)], [(47, 97), (68, 92), (87, 95), (78, 105), (50, 112), (46, 104)], [(505, 73), (522, 69), (546, 71), (547, 87), (536, 89), (511, 80)]]),
    'wagon': dict(box=(10, 338, 595, 594), wheels=[(127.5, 156.5, 37), (452, 156.5, 37)], length=4.78, src='white', kind='neutral', thr=125,
                  excl=[[(176, 81), (234, 41), (320, 38), (450, 39), (476, 48), (495, 60), (491, 71), (470, 73), (192, 85)],
                        [(478, 36), (495, 38), (515, 50), (535, 63), (547, 76), (541, 80), (520, 67), (500, 53), (482, 46)],
                        [(40, 104), (86, 100), (79, 110), (62, 120), (40, 130)], [(521, 100), (530, 86), (552, 85), (553, 96), (545, 101)]]),
    'suv':   dict(box=(606, 338, 1191, 594), wheels=[(141, 164, 39), (451.5, 164, 39)], length=4.60, src='graphite', kind='neutral', thr=62, dark=0.017, dw=0.007,
                  excl=[[(200, 72), (258, 26), (325, 20), (472, 20), (483, 50), (478, 67), (208, 77)], [(42, 98), (55, 91), (80, 87), (91, 90), (81, 99), (60, 104), (42, 104)], [(504, 77), (520, 65), (546, 65), (549, 74), (541, 87), (509, 85)]]),
    'coupe': dict(box=(10, 636, 595, 890), wheels=[(134, 144.5, 37), (443.5, 144.5, 37)], length=4.79, src='blue', kind='blue'),
    'mpv':   dict(box=(606, 636, 1191, 890), wheels=[(131, 158, 36), (456, 158, 36)], length=5.25, src='graphite', kind='neutral', thr=88,
                  excl=[[(148, 54), (188, 19), (290, 10), (522, 10), (522, 77), (285, 82), (195, 82)], [(53, 92), (101, 90), (101, 96), (53, 97)], [(34, 113), (69, 113), (69, 166), (34, 166)], [(535, 28), (544, 28), (552, 80), (552, 123), (539, 123), (537, 60)]]),
}

def srgb2lin(c):
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)

def lin2srgb(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055) * 255


def refine_wheels(img, wheels):
    """Ajustează centrul și raza fiecărei roți (cercul anvelopei) după marginea întunecată din jur."""
    g = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
    g = cv2.GaussianBlur(g, (5, 5), 1.2)
    out = []
    for (cx, cy, r) in wheels:
        cs = cv2.HoughCircles(g, cv2.HOUGH_GRADIENT, dp=1, minDist=50, param1=80, param2=18,
                              minRadius=int(r * 0.85), maxRadius=int(r * 1.12))
        best = (cx, cy, r)
        if cs is not None:
            cand = sorted(cs[0], key=lambda c: (c[0] - cx) ** 2 + (c[1] - cy) ** 2)[0]
            if math.hypot(cand[0] - cx, cand[1] - cy) < 8: best = (float(cand[0]), float(cand[1]), float(cand[2]))
        out.append(best)
    return out


def process(key, c):
    im = np.asarray(Image.open(SRC).convert('RGB'))
    x0, y0, x1, y1 = c['box']
    img = im[y0:y1, x0:x1].copy()
    wheels = c["wheels"]
    ground = max(w[1] + w[2] for w in wheels) + 0.5
    H, W = img.shape[:2]
    lum = img.max(axis=2).astype(np.float32)
    # silueta: mai deschis decât fundalul, deasupra solului
    sil = (lum > 22).astype(np.uint8)
    sil[int(math.ceil(ground)):, :] = 0
    yy, xx = np.mgrid[0:H, 0:W]
    for (cx, cy, r) in wheels:                     # roțile întregi, până la sol
        sil[((xx - cx) ** 2 + (yy - cy) ** 2) <= (r + 0.5) ** 2] = 1
    sil = cv2.morphologyEx(sil, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(sil, 8)
    big = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
    sil = (lab == big).astype(np.uint8)
    for (cx, cy, r) in wheels:                     # roțile rămân chiar dacă nu ating caroseria (pasaje negre)
        sil[((xx - cx) ** 2 + (yy - cy) ** 2) <= (r + 0.5) ** 2] = 1
    # golurile din interior (geamuri închise la culoare, umbre) fac parte din mașină
    ff = sil.copy(); h2, w2 = ff.shape
    fm = np.zeros((h2 + 2, w2 + 2), np.uint8)
    cv2.floodFill(ff, fm, (0, 0), 1)
    sil = sil | (1 - ff)
    # pe fiecare coloană, de la primul la ultimul pixel al mașinii (pasajele roților, plasticul negru de jos)
    for x in range(W):
        ys = np.where(sil[:, x])[0]
        if len(ys) > 1: sil[ys[0]:ys[-1] + 1, x] = 1
    # partea de jos dintre roți: până la prag (fundul mașinii e negru; nu luăm umbra de pe sol)
    alpha = cv2.GaussianBlur(sil.astype(np.float32), (3, 3), 0.7)
    # embleme: capacele centrale ale roților devin un disc neutru
    for (cx, cy, r) in wheels:
        cap = r * 0.17
        m = ((xx - cx) ** 2 + (yy - cy) ** 2) <= cap ** 2
        ring = (((xx - cx) ** 2 + (yy - cy) ** 2) <= (cap + 1.2) ** 2) & ~m
        img[m] = (38, 39, 41)
        img[ring] = (70, 71, 74)
    # zgomotul JPEG de pe vopsea: netezire care păstrează muchiile
    sm = cv2.bilateralFilter(img, 5, 14, 3)
    img = sm
    # masca vopselei
    f = img.astype(np.float32)
    R, G, B = f[..., 0], f[..., 1], f[..., 2]
    mx, mn = f.max(axis=2), f.min(axis=2)
    sat = (mx - mn) / np.maximum(mx, 1)
    if c['kind'] == 'red':
        paint = np.clip(((R - np.maximum(G, B)) / 255 - 0.06) / 0.14, 0, 1)
    elif c['kind'] == 'blue':
        paint = np.clip(((B - np.maximum(R, G)) / 255 - 0.05) / 0.12, 0, 1)
    else:
        # carcasa întreagă, fără geamuri, faruri, stopuri și plasticul negru (foarte închis)
        L0 = srgb2lin(f); Y0 = 0.2126 * L0[..., 0] + 0.7152 * L0[..., 1] + 0.0722 * L0[..., 2]
        paint = np.clip((Y0 - c.get('dark', 0.010)) / c.get('dw', 0.015), 0, 1) * np.clip((0.30 - sat) / 0.12, 0, 1)
        ex = np.zeros((H, W), np.uint8)
        for pl in c.get('excl', []):
            cv2.fillPoly(ex, [np.array(pl, np.int32)], 1)
        paint *= (1 - ex)
    wheelmask = np.zeros_like(paint)
    for (cx, cy, r) in wheels:
        wheelmask[((xx - cx) ** 2 + (yy - cy) ** 2) <= (r + 1.5) ** 2] = 1
    # golurile mici din vopsea (reflexii închise, mânere, rosturi) sunt tot caroserie; geamurile și plasticul mare nu
    cand = ((paint > 0.5) & (wheelmask == 0) & (sil > 0)).astype(np.uint8)
    cand = cv2.morphologyEx(cand, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    rest = ((cand == 0) & (sil > 0) & (wheelmask == 0)).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(rest, 4)
    for i in range(1, n if c['kind'] != 'neutral' else 1):
        if st[i, cv2.CC_STAT_AREA] < c.get('hole', 900):
            paint[lab == i] = 1
    paint *= (1 - wheelmask) * sil
    paint = cv2.GaussianBlur(paint, (3, 3), 0.6)
    # culoarea de referință: mediana vopselei (liniar)
    lin = srgb2lin(f)
    Y = 0.2126 * lin[..., 0] + 0.7152 * lin[..., 1] + 0.0722 * lin[..., 2]
    sel = paint > 0.8
    Yr = float(np.median(Y[sel]))
    # bounding box al mașinii, scară comună
    cols = np.where(sil.any(axis=0))[0]; rows = np.where(sil.any(axis=1))[0]
    cx0, cx1, cy0 = cols[0], cols[-1] + 1, rows[0]
    k = PXM * c['length'] / (cx1 - cx0)
    rgba = np.dstack([img, (alpha * 255).astype(np.uint8)])
    crop = rgba[cy0:int(math.ceil(ground)), cx0:cx1]
    pcrop = (paint[cy0:int(math.ceil(ground)), cx0:cx1] * 255).astype(np.uint8)
    nw, nh = int(round(crop.shape[1] * k)), int(round(crop.shape[0] * k))
    car = Image.fromarray(crop, 'RGBA').resize((nw, nh), Image.LANCZOS)
    pm = Image.fromarray(pcrop, 'L').resize((nw, nh), Image.LANCZOS)
    canvas = Image.new('RGBA', (CW, CH), (0, 0, 0, 0)); mask = Image.new('L', (CW, CH), 0)
    ox, oy = (CW - nw) // 2, GROUND - nh
    canvas.paste(car, (ox, oy)); mask.paste(pm, (ox, oy))
    canvas.save(OUT + key + '.png', optimize=True)
    mask.save(OUT + key + '_paint.png', optimize=True)
    return dict(key=key, src=c['src'], yr=round(Yr, 5), kind=c['kind'],
                wheels=[[round(ox + (w[0] - cx0) * k, 1), round(oy + (w[1] - cy0) * k, 1), round(w[2] * k, 1)] for w in wheels])


if __name__ == '__main__':
    meta = {'w': CW, 'h': CH, 'ground': GROUND, 'pxm': PXM, 'cars': []}
    for k, c in CARS.items():
        meta['cars'].append(process(k, c))
        print(meta['cars'][-1])
    json.dump(meta, open(OUT + 'cars.json', 'w'), indent=1)
