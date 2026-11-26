"""Recolorarea (aceeași formulă ca Show.recolor în aplicație) și previzualizarea 6 modele × 7 culori."""
import json, numpy as np
from PIL import Image
A = '/home/claude/evos/app/src/main/assets/showroom/'
meta = json.load(open(A + 'cars.json'))
PAINT = {'white': (0xE9, 0xEC, 0xEF), 'silver': (0xA9, 0xB0, 0xB8), 'graphite': (0x4A, 0x50, 0x58), 'black': (0x17, 0x19, 0x1C),
         'red': (0xB3, 0x12, 0x1B), 'blue': (0x1D, 0x4F, 0x9C), 'orange': (0xE8, 0x65, 0x1E)}
def lin(c): c = c / 255.0; return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
def srgb(c): c = np.clip(c, 0, 1); return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055) * 255

def recolor(key, paint):
    m = [c for c in meta['cars'] if c['key'] == key][0]
    im = np.asarray(Image.open(A + key + '.png')).astype(np.float32)
    mk = np.asarray(Image.open(A + key + '_paint.png')).astype(np.float32)[..., None] / 255
    if paint == m['src']: return im.astype(np.uint8)
    L = lin(im[..., :3])
    Y = (0.2126 * L[..., 0] + 0.7152 * L[..., 1] + 0.0722 * L[..., 2])[..., None]
    T = lin(np.array(PAINT[paint], np.float32))
    Yt = 0.2126 * T[0] + 0.7152 * T[1] + 0.0722 * T[2]
    shade = Y / m['yr']
    # contrastul luminii: o vopsea închisă arată mai mult reflexiile, una deschisă mai puțin
    out = T * shade
    # reflexiile: ce e mai luminos decât vopseaua rămâne alb (lacul reflectă la fel orice culoare)
    spec = np.clip(Y - m['yr'] * 1.15, 0, None) * (1.0 if m['yr'] < 0.3 else 0.6)
    out = out + spec * (1 - np.clip(Yt * 2, 0, 0.6))
    ex = np.clip(out.max(axis=2, keepdims=True) - 1, 0, None)
    out = np.clip(out, 0, 1) + ex * 0.4
    res = im.copy()
    res[..., :3] = srgb(L * (1 - mk) + out * mk)
    return res.astype(np.uint8)

if __name__ == '__main__':
    cols = list(PAINT)
    W, H = meta['w'] // 2, meta['h'] // 2
    g = Image.new('RGB', (W * 7, H * 6), (7, 7, 7))
    for r, c in enumerate(meta['cars']):
        for i, p in enumerate(cols):
            t = Image.fromarray(recolor(c['key'], p), 'RGBA').resize((W, H), Image.LANCZOS)
            g.paste(t, (i * W, r * H), t)
    g.save('/home/claude/work/cars/grid.jpg', quality=88)
