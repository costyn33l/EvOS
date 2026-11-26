package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.io.InputStream;

/**
 * Tema Showroom (EvOS 1.1): un menú lateral (Acasă, Hartă, Media, Vehicul, Aplicații, Setări) con el carro expuesto
 * sobre un piso con rejilla en perspectiva, como en un salón de ventas. Aquí están las piezas de dibujo: los modelos
 * de carro estándar (perfiles vectoriales de assets/showroom/cars.json, los mismos de las maquetas), el piso, los
 * botones de la consola, los cuadrantes de Instrumentos y las curvas de Parámetros. El armado de las pantallas está
 * en Main (buildShowroom).
 */
final class Show {
    private Show() { }

    static final String ID = "showroom";
    static final int BG = 0xFF070707, TX = 0xFFF2F2F2, MU = 0xFF8E8E8E, HAIR = 0xFF1E1E1E;
    static final int[] ACCENTS = {0xFFFF6A1A, 0xFFE5261A, 0xFFF5C518, 0xFF3DDC84, 0xFF2EA8FF, 0xFFA64DFF};

    // ── modelos ──
    // Seis carrocerías a partir de imágenes de estudio de perfil (assets/showroom/<modelo>.png), todas con la misma
    // escala (px/m) y la misma línea del piso, sin emblemas. <modelo>_paint.png es la máscara de la pintura: el color
    // se cambia en el teléfono conservando la luz y los reflejos de la foto (la laca refleja igual en cualquier color).

    static final String PHOTO = "photo";
    static final String[] MODEL_KEYS = {"hatch", "sedan", "wagon", "suv", "coupe", "mpv"};
    /** Nombres (en español: se traducen al mostrarlos). */
    static final String[] MODEL_NAMES = {"Hatchback", "Sedán", "Familiar", "SUV", "Coupé", "Monovolumen"};
    static final String[] PAINT_KEYS = {"white", "silver", "graphite", "black", "red", "blue", "orange"};
    static final String[] PAINT_NAMES = {"Blanco perla", "Plata", "Grafito", "Negro", "Rojo", "Azul", "Naranja"};
    static final int[] PAINT = {0xFFE9ECEF, 0xFFA9B0B8, 0xFF4A5058, 0xFF17191C, 0xFFB3121B, 0xFF1D4F9C, 0xFFE8651E};

    static int paint(String key) {
        for (int i = 0; i < PAINT_KEYS.length; i++) if (PAINT_KEYS[i].equals(key)) return PAINT[i];
        return PAINT[1];
    }
    static String validPaint(String key) {
        for (String k : PAINT_KEYS) if (k.equals(key)) return k;
        return null;
    }
    static String paintName(String key) {
        for (int i = 0; i < PAINT_KEYS.length; i++) if (PAINT_KEYS[i].equals(key)) return PAINT_NAMES[i];
        return PAINT_NAMES[1];
    }
    static String modelName(String key) {
        for (int i = 0; i < MODEL_KEYS.length; i++) if (MODEL_KEYS[i].equals(key)) return MODEL_NAMES[i];
        return "Tu foto";
    }
    static String validModel(String key) {
        if (PHOTO.equals(key)) return key;
        for (String k : MODEL_KEYS) if (k.equals(key)) return k;
        return MODEL_KEYS[0];
    }

    static final class Model {
        String key, src;
        float yr;
        float[][] wheels;
    }

    private static Model[] models;
    static int CW = 768, CH = 300, GROUND = 282;

    static synchronized Model model(Context c, String key) {
        if (models == null) {
            try { models = load(c); } catch (Throwable e) { models = new Model[0]; }
        }
        for (Model m : models) if (m.key.equals(key)) return m;
        return models.length > 0 ? models[0] : null;
    }

    /** Color original de la foto del modelo (sin elegir color se muestra ese). */
    static String srcPaint(Context c, String key) {
        Model m = model(c, key);
        return m != null ? m.src : "silver";
    }

    private static Model[] load(Context c) throws Exception {
        InputStream in = c.getAssets().open("showroom/cars.json");
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        org.json.JSONObject root = new org.json.JSONObject(bo.toString("UTF-8"));
        CW = root.optInt("w", 768); CH = root.optInt("h", 300); GROUND = root.optInt("ground", 282);
        org.json.JSONArray a = root.getJSONArray("cars");
        Model[] out = new Model[a.length()];
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.getJSONObject(i);
            Model m = new Model();
            m.key = o.getString("key");
            m.src = o.optString("src", "silver");
            m.yr = (float) o.getDouble("yr");
            org.json.JSONArray w = o.getJSONArray("wheels");
            m.wheels = new float[w.length()][];
            for (int j = 0; j < w.length(); j++) {
                org.json.JSONArray e = w.getJSONArray(j);
                m.wheels[j] = new float[]{(float) e.getDouble(0), (float) e.getDouble(1), (float) e.getDouble(2)};
            }
            out[i] = m;
        }
        return out;
    }

    private static Bitmap asset(Context c, String path, int sample) {
        try {
            InputStream in = c.getAssets().open(path);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Throwable e) { return null; }
    }

    private static float[] LIN;
    private static byte[] SRGB;

    private static void tables() {
        if (LIN != null) return;
        float[] l = new float[256];
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            l[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        byte[] s = new byte[4097];
        for (int i = 0; i <= 4096; i++) {
            double c = i / 4096.0;
            double v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
            s[i] = (byte) Math.max(0, Math.min(255, Math.round(v * 255)));
        }
        SRGB = s; LIN = l;
    }

    private static int enc(float v) { return SRGB[(int) (Math.max(0f, Math.min(1f, v)) * 4096 + 0.5f)] & 0xFF; }

    private static String cacheKey;
    private static Bitmap cacheBm;

    /**
     * El modelo con la pintura elegida. Sombra = luminancia / luminancia de la pintura original: se multiplica por el
     * color nuevo; lo que brilla más que la pintura (reflejos de la laca) queda blanco. Misma fórmula que las maquetas.
     * sample = 1 a tamaño completo, 4 para las miniaturas.
     */
    static synchronized Bitmap render(Context c, String key, String paintKey, int sample) {
        String ck = key + ":" + paintKey + ":" + sample;
        if (ck.equals(cacheKey) && cacheBm != null && !cacheBm.isRecycled()) return cacheBm;
        Model m = model(c, key);
        if (m == null) return null;
        Bitmap base = asset(c, "showroom/" + key + ".png", sample);
        if (base == null) return null;
        if (paintKey == null || paintKey.equals(m.src)) { if (sample == 1) { cacheKey = ck; cacheBm = base; } return base; }
        Bitmap mask = asset(c, "showroom/" + key + "_paint.png", sample);
        if (mask == null) return base;
        tables();
        int w = base.getWidth(), h = base.getHeight();
        int[] px = new int[w * h], mk = new int[w * h];
        base.getPixels(px, 0, w, 0, 0, w, h);
        mask.getPixels(mk, 0, w, 0, 0, Math.min(w, mask.getWidth()), Math.min(h, mask.getHeight()));
        int col = paint(paintKey);
        float tr = LIN[Color.red(col)], tg = LIN[Color.green(col)], tb = LIN[Color.blue(col)];
        float yt = 0.2126f * tr + 0.7152f * tg + 0.0722f * tb;
        float yr = m.yr, specK = (yr < 0.3f ? 1f : 0.6f) * (1 - Math.min(0.6f, yt * 2));
        for (int i = 0; i < px.length; i++) {
            int a = (mk[i] >> 16) & 0xFF;          // máscara en gris: cualquier canal
            if (a == 0) continue;
            int p = px[i];
            float r = LIN[(p >> 16) & 0xFF], g = LIN[(p >> 8) & 0xFF], b = LIN[p & 0xFF];
            float y = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            float sh = y / yr, sp = Math.max(0f, y - yr * 1.15f) * specK;
            float or = tr * sh + sp, og = tg * sh + sp, ob = tb * sh + sp;
            float ex = Math.max(0f, Math.max(or, Math.max(og, ob)) - 1) * 0.4f;
            or = Math.min(1f, or) + ex; og = Math.min(1f, og) + ex; ob = Math.min(1f, ob) + ex;
            float t = a / 255f;
            px[i] = (p & 0xFF000000) | (enc(r + (or - r) * t) << 16) | (enc(g + (og - g) * t) << 8) | enc(b + (ob - b) * t);
        }
        mask.recycle();
        Bitmap out = base.isMutable() ? base : base.copy(Bitmap.Config.ARGB_8888, true);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (out != base) base.recycle();
        if (sample == 1) {
            if (cacheBm != null && cacheBm != out && !ck.equals(cacheKey)) { /* el anterior puede seguir en pantalla: no se recicla */ }
            cacheKey = ck; cacheBm = out;
        }
        return out;
    }

    static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    static int mix(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    static final float REFL = 72;

    /** Sombra de contacto bajo el carro (las fotos están recortadas sin piso). k = escala, (ox, oy) = esquina de la pantalla. */
    static void shadow(Canvas c, Model m, float ox, float oy, float k) {
        if (m == null || m.wheels.length < 2) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[] f = m.wheels[0], r = m.wheels[m.wheels.length - 1];
        float x0 = f[0] - f[2] * 1.6f, x1 = r[0] + r[2] * 1.5f, gy = oy + GROUND * k;
        p.setShader(new RadialGradient(0, 0, 1, new int[]{0xCC000000, 0x55000000, 0}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.save(); c.translate(ox + (x0 + x1) / 2 * k, gy); c.scale((x1 - x0) / 2 * k * 1.08f, 9 * k); c.drawCircle(0, 0, 1, p); c.restore();
        for (float[] w : m.wheels) {
            c.save(); c.translate(ox + w[0] * k, gy); c.scale(w[2] * 0.95f * k, 3.2f * k); c.drawCircle(0, 0, 1, p); c.restore();
        }
    }

    /**
     * El carro expuesto: un modelo con su color, o la foto del usuario (recortada). Con sombra y reflejo en el piso.
     * Se pinta una vez en un bitmap (también en Android 4.1, sin aceleración por hardware).
     */
    static final class Car extends View {
        String key, paintKey;
        Bitmap photo;
        boolean reflect = true, thumb = false;
        private Bitmap frame;

        Car(Context c) { super(c); }

        /** key = modelo (null con foto); paintKey = color (null = el original de la foto del modelo). */
        void set(String key, String paintKey, Bitmap ph) {
            this.key = key; this.paintKey = paintKey; photo = ph;
            frame = null;
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); frame = null; }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (frame == null) {
                try { frame = render(w, h); } catch (Throwable e) { frame = null; }
            }
            if (frame != null) canvas.drawBitmap(frame, 0, 0, null);
        }

        private Bitmap render(int w, int h) {
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            if (photo != null && key == null) { drawPhoto(c, w, h); return out; }
            if (key == null) return out;
            Model m = model(getContext(), key);
            Bitmap car = Show.render(getContext(), key, paintKey, thumb ? 4 : 1);
            if (m == null || car == null) return out;
            float bh = CH + (reflect ? REFL : 0);
            float k = Math.min(w / (float) CW, h / bh);
            float ox = (w - CW * k) / 2, oy = (h - bh * k) / 2;
            Paint pf = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            RectF dst = new RectF(ox, oy, ox + CW * k, oy + CH * k);
            float gy = oy + GROUND * k;
            if (reflect) {
                Bitmap rb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas rc = new Canvas(rb);
                rc.save(); rc.translate(0, 2 * gy); rc.scale(1, -1);
                rc.drawBitmap(car, null, dst, pf);
                rc.restore();
                Paint mk = new Paint();
                mk.setShader(new LinearGradient(0, gy, 0, gy + REFL * 0.8f * k, 0x2EFFFFFF, 0, Shader.TileMode.CLAMP));
                mk.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                rc.drawRect(0, 0, w, h, mk);
                Paint clr = new Paint();
                clr.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                rc.drawRect(0, 0, w, gy, clr);
                c.drawBitmap(rb, 0, 0, null);
                rb.recycle();
            }
            shadow(c, m, ox, oy, k);
            c.drawBitmap(car, null, dst, pf);
            return out;
        }

        private void drawPhoto(Canvas c, int w, int h) {
            Bitmap ph = photo;
            boolean cut = false;
            try { cut = CarPhoto.hasTransparency(ph); } catch (Throwable ignored) { }
            float area = reflect && cut ? h * 0.80f : h;
            float k = Math.min(w / (float) ph.getWidth(), area / ph.getHeight());
            float dw = ph.getWidth() * k, dh = ph.getHeight() * k;
            float l = (w - dw) / 2, t = area - dh;
            Paint pf = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            RectF dst = new RectF(l, t, l + dw, t + dh);
            if (cut) {
                Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
                sh.setShader(new RadialGradient(0, 0, 1, new int[]{0xB3000000, 0x40000000, 0}, new float[]{0, 0.7f, 1}, Shader.TileMode.CLAMP));
                c.save(); c.translate(w / 2f, t + dh - 2); c.scale(dw * 0.52f, Math.max(4, dh * 0.05f)); c.drawCircle(0, 0, 1, sh); c.restore();
                if (reflect) {
                    Bitmap rb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas rc = new Canvas(rb);
                    rc.save(); rc.translate(0, 2 * (t + dh)); rc.scale(1, -1);
                    rc.drawBitmap(ph, null, dst, pf);
                    rc.restore();
                    Paint mk = new Paint();
                    mk.setShader(new LinearGradient(0, t + dh, 0, h, 0x29FFFFFF, 0, Shader.TileMode.CLAMP));
                    mk.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                    rc.drawRect(0, 0, w, h, mk);
                    c.drawBitmap(rb, 0, 0, null);
                    rb.recycle();
                }
                c.drawBitmap(ph, null, dst, pf);
            } else {
                Bitmap cr = CarPhoto.cardCrop(ph, Math.max(1, (int) dw), Math.max(1, (int) dh), 6);
                c.drawBitmap(cr, l, t, pf);
            }
        }
    }

    /** Piso del salón: rejilla en perspectiva que se desvanece hacia el horizonte, con una luz de techo suave. */
    static final class Floor extends View {
        final float hz, vxr;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Floor(Context c, float hz, float vxr) { super(c); this.hz = hz; this.vxr = vxr; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float vx = w * vxr, vy = h * hz;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(vx, h * 0.38f, Math.max(w, h) * 0.5f, new int[]{0x1AFFFFFF, 0x08FFFFFF, 0}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            c.save(); c.scale(1, 0.8f, vx, h * 0.38f); c.drawCircle(vx, h * 0.38f, Math.max(w, h) * 0.5f, p); c.restore();
            p.setShader(new LinearGradient(0, vy, 0, h, new int[]{0x003C3C3C, 0x6B3C3C3C, 0x994A4A4A}, new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, h / 340f));
            for (int i = -14; i <= 14; i++) c.drawLine(vx, vy, vx + i * w * 0.11f, h + 40, p);
            for (int j = 1; j <= 8; j++) {
                float t = j / 8f, yy = vy + (h - vy) * t * t;
                c.drawLine(0, yy, w, yy, p);
            }
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * Fondo de botón de la consola y del menú: degradado gris, filo claro arriba y la línea de color abajo.
     * line = alto de la línea (0 = sin línea).
     */
    static final class Tile extends Drawable {
        final float s, line;
        final int top, bottom, accent;
        final boolean hairTop;
        final Paint p = new Paint();
        Tile(float s, int top, int bottom, int accent, float line, boolean hairTop) {
            this.s = s; this.top = top; this.bottom = bottom; this.accent = accent; this.line = line; this.hairTop = hairTop;
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            p.setShader(null);
            if (hairTop) { p.setColor(0x14FFFFFF); c.drawRect(b.left, b.top, b.right, b.top + Math.max(1, s), p); }
            if (line > 0) { p.setColor(accent); c.drawRect(b.left, b.bottom - line * s, b.right, b.bottom, p); }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    static Drawable tile(float s, int accent) { return new Tile(s, 0xFF333333, 0xFF262626, accent, 3, true); }
    static Drawable tileOn(float s, int accent) { return new Tile(s, 0xFF3A3A3A, 0xFF2A2A2A, accent, 4, true); }
    static Drawable railOn(float s, int accent) { return new Tile(s, 0xFF2C2C2C, 0xFF1E1E1E, accent, 4, false); }

    /** Fila elegida en una lista: degradado y la raya de color a la izquierda. */
    static final class RowOn extends Drawable {
        final float s; final int accent; final Paint p = new Paint();
        RowOn(float s, int accent) { this.s = s; this.accent = accent; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            p.setShader(new LinearGradient(b.left, 0, b.right, 0, 0xFF262626, 0xFF1A1A1A, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            p.setShader(null);
            p.setColor(accent);
            c.drawRect(b.left, b.top, b.left + 3 * s, b.bottom, p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    static Typeface cond(boolean bold) { return Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL); }

    /** Cuadrante de Instrumentos (turómetro o velocímetro) con zona roja, aguja y el número grande en el centro. */
    static final class Dial extends View {
        final float s, vmax, step, minor, redFrom, labelDiv;
        final int accent;
        final String unit;
        float value = Float.NaN;
        String sub = "";
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rf = new RectF();
        Dial(Context c, float s, float vmax, float step, float minor, float redFrom, float labelDiv, String unit, int accent) {
            super(c);
            this.s = s; this.vmax = vmax; this.step = step; this.minor = minor; this.redFrom = redFrom; this.labelDiv = labelDiv;
            this.unit = unit; this.accent = accent;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        void set(float v, String sub) {
            if (v == value || (Float.isNaN(v) && Float.isNaN(value))) { if (!sub.equals(this.sub)) { this.sub = sub; invalidate(); } return; }
            value = v; this.sub = sub; invalidate();
        }
        private float px(float a, float r, float cx) { return cx + r * (float) Math.cos(Math.toRadians(a)); }
        private float py(float a, float r, float cy) { return cy + r * (float) Math.sin(Math.toRadians(a)); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2, R = Math.min(w, h) / 2 - 15 * s;
            if (R <= 0) return;
            float k = R / 150f, start = 135, sweep = 270;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy - R * 0.2f, R + 14 * k, 0xFF181818, 0xFF0A0A0A, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, R + 14 * k, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * k);
            p.setColor(0xFF262626);
            c.drawCircle(cx, cy, R + 14 * k, p);
            float ar = R - 3 * k;
            rf.set(cx - ar, cy - ar, cx + ar, cy + ar);
            p.setStrokeWidth(6 * k);
            p.setColor(0xFF1F1F1F);
            c.drawArc(rf, start, sweep, false, p);
            if (redFrom > 0) {
                float a0 = start + sweep * redFrom / vmax;
                p.setColor(0xFFE5261A);
                c.drawArc(rf, a0, start + sweep - a0, false, p);
            }
            boolean ok = !Float.isNaN(value);
            float v = ok ? Math.max(0, Math.min(vmax, value)) : 0;
            float va = start + sweep * v / vmax;
            if (ok && v > 0) { p.setColor(alpha(accent, 0xE6)); c.drawArc(rf, start, va - start, false, p); }
            int n = Math.round(vmax / minor);
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i <= n; i++) {
                float val = i * minor, a = start + sweep * val / vmax;
                boolean major = Math.abs(val / step - Math.round(val / step)) < 1e-4f;
                boolean red = redFrom > 0 && val >= redFrom;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth((major ? 2.6f : 1.4f) * k);
                p.setColor(major ? (red ? 0xFFE5261A : 0xFFEDEDED) : (red ? 0xFF7A1A10 : 0xFF555555));
                float r0 = R - (major ? 22 : 14) * k, r1 = R - 9 * k;
                c.drawLine(px(a, r0, cx), py(a, r0, cy), px(a, r1, cx), py(a, r1, cy), p);
                if (major) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTypeface(cond(true));
                    p.setTextSize(17 * k);
                    p.setColor(red ? 0xFFE5261A : 0xFFCFCFCF);
                    float lr = R - 40 * k;
                    c.drawText(String.valueOf(Math.round(val / labelDiv)), px(a, lr, cx), py(a, lr, cy) + 6 * k, p);
                }
            }
            if (ok) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(4 * k);
                p.setColor(accent);
                c.drawLine(px(va, R * 0.30f, cx), py(va, R * 0.30f, cy), px(va, R - 10 * k, cx), py(va, R - 10 * k, cy), p);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF0A0A0A);
            c.drawCircle(cx, cy, R * 0.36f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * k);
            p.setColor(0xFF2C2C2C);
            c.drawCircle(cx, cy, R * 0.36f, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(cond(true));
            p.setColor(TX);
            String big = ok ? String.valueOf(Math.round(value)) : "--";
            float ts = (big.length() > 3 ? 58 : 76) * k;
            p.setTextSize(ts);
            while (p.measureText(big) > R * 0.66f && ts > 10) { ts *= 0.9f; p.setTextSize(ts); }
            c.drawText(big, cx, cy + ts * 0.30f, p);
            p.setTextSize(14 * k);
            p.setColor(MU);
            c.drawText(sub, cx, cy + ts * 0.30f + 24 * k, p);
        }
    }

    /** Curva chica de los últimos minutos de un dato (Parámetros). */
    static final class Spark extends View {
        final Obd.Sensor sensor;
        final int color;
        final float s;
        final float[] v = new float[3000];
        final long[] t = new long[3000];
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        Spark(Context c, Obd.Sensor sensor, int color, float s) {
            super(c);
            this.sensor = sensor; this.color = color; this.s = s;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            int n;
            try { n = sensor.since(System.currentTimeMillis() - 180000, v, t); } catch (Throwable e) { n = 0; }
            if (n < 2) return;
            float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) { if (Float.isNaN(v[i])) continue; mn = Math.min(mn, v[i]); mx = Math.max(mx, v[i]); }
            if (mn == Float.MAX_VALUE) return;
            float rng = mx - mn;
            if (rng < (sensor.max - sensor.min) * 0.02f) { float mid = (mx + mn) / 2; rng = (sensor.max - sensor.min) * 0.02f; mn = mid - rng / 2; }
            if (rng <= 0) rng = 1;
            long t0 = t[0], t1 = t[n - 1];
            float span = Math.max(1, t1 - t0);
            path.reset();
            boolean first = true;
            int stepI = Math.max(1, n / 120);
            for (int i = 0; i < n; i += stepI) {
                if (Float.isNaN(v[i])) continue;
                float x = (t[i] - t0) / span * w, y = h - 2 * s - (v[i] - mn) / rng * (h - 4 * s);
                if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
            }
            p.setStrokeWidth(1.6f * s);
            p.setColor(color);
            c.drawPath(path, p);
        }
    }

    /** Barra de revoluciones en segmentos (Acasă) o barra fina de progreso. */
    static final class Segs extends View {
        final int n, accent;
        final float s;
        float frac = 0, redFrac = 0.79f;
        final Paint p = new Paint();
        Segs(Context c, int n, int accent, float s) { super(c); this.n = n; this.accent = accent; this.s = s; }
        void set(float f) { f = Math.max(0, Math.min(1, f)); if (Math.abs(f - frac) > 0.004f) { frac = f; invalidate(); } }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), gap = 3 * s, sw = (w - gap * (n - 1)) / n;
            int on = Math.round(frac * n), red = Math.round(redFrac * n);
            for (int i = 0; i < n; i++) {
                p.setColor(i < on ? (i >= red ? 0xFFE5261A : accent) : (i >= red ? 0xFF3A1A10 : 0xFF262626));
                float x = i * (sw + gap);
                c.drawRect(x, 0, x + sw, h, p);
            }
        }
    }

    static final class Bar extends View {
        float frac;
        int color;
        final Paint p = new Paint();
        Bar(Context c, int color) { super(c); this.color = color; }
        void set(float f, int col) { frac = Math.max(0, Math.min(1, f)); color = col; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(HAIR);
            c.drawRect(0, 0, w, h, p);
            p.setColor(color);
            c.drawRect(0, 0, w * frac, h, p);
        }
    }

    /** Gráfico de la velocidad del viaje (Călătorie). */
    static final class Line extends View {
        float[] data = new float[0];
        final int color;
        final float s;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        Line(Context c, int color, float s) { super(c); this.color = color; this.s = s; }
        void set(float[] d) { data = d; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, s));
            p.setColor(0xFF1C1C1C);
            c.drawLine(0, h - 1, w, h - 1, p);
            c.drawLine(0, h / 2, w, h / 2, p);
            if (data.length < 2) return;
            float mx = 1;
            for (float v : data) mx = Math.max(mx, v);
            mx = (float) Math.ceil(mx / 20f) * 20f;
            path.reset();
            for (int i = 0; i < data.length; i++) {
                float x = i * w / (data.length - 1), y = h - 2 * s - data[i] / mx * (h - 6 * s);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            p.setStrokeWidth(2 * s);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            c.drawPath(path, p);
        }
    }

    /** Círculo de color para elegir la pintura. */
    static final class Swatch extends View {
        final int color;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Swatch(Context c, int color) { super(c); this.color = color; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2 - 1;
            p.setShader(new LinearGradient(0, 0, 0, h, mix(color, Color.WHITE, 0.15f), mix(color, Color.BLACK, 0.35f), Shader.TileMode.CLAMP));
            c.drawCircle(w / 2, h / 2, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(0xFF3A3A3A);
            c.drawCircle(w / 2, h / 2, r, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** Matriz identidad reutilizable (evita crear objetos al dibujar). */
    static final Matrix ID_M = new Matrix();
}
