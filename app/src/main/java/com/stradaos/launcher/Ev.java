package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.Random;

/**
 * Temas EvOS (1.0): «pieles» sobre la pantalla de inicio de siempre (barra de arriba, tarjeta del vehículo, mapa con
 * Android Auto y CarPlay, reproductor opcional y barra de apps). Cambian colores, fondo, tarjetas, letra, la barra de
 * apps y el instrumento de la tarjeta del vehículo; todo lo demás (mapa, música, mantenimientos, ajustes) es el código
 * de siempre. Aero, Monolith y Pulse tienen versión clara; Neon e Inferno son siempre oscuros.
 */
final class Ev {
    private Ev() { }

    static final String AERO = "aero", MONO = "monolith", PULSE = "pulse", NEON = "neon", INFERNO = "inferno", SHOW = Show.ID;
    static final String[] IDS = {SHOW, AERO, MONO, PULSE, NEON, INFERNO};
    /** Tema en uso; null = Clásico (sin piel). Lo fija Main.applyTheme. */
    static String id;

    static boolean on() { return id != null; }
    static boolean is(String x) { return x.equals(id); }
    static String valid(String x) {
        if (x == null) return null;
        for (String i : IDS) if (i.equals(x)) return i;
        return null;
    }
    static boolean forcesDark(String x) { return NEON.equals(x) || INFERNO.equals(x) || SHOW.equals(x); }
    /** Showroom tiene su propia distribución (menú lateral y secciones), no la piel sobre el inicio de siempre. */
    static boolean show() { return SHOW.equals(id); }

    static String name(String x) {
        return SHOW.equals(x) ? "Showroom" : AERO.equals(x) ? "Aero" : MONO.equals(x) ? "Monolith" : PULSE.equals(x) ? "Pulse" : NEON.equals(x) ? "Neon" : "Inferno";
    }

    /** Descripción (en español: se traduce al mostrarla). */
    static String desc(String x) {
        if (SHOW.equals(x)) return "Como un salón de exposición: menú lateral con Mapa, Música, Vehículo, Apps y Ajustes, el carro sobre un piso con rejilla y la consola de botones abajo. Elige el modelo y el color del carro. Siempre oscuro.";
        if (AERO.equals(x)) return "Tarjetas de vidrio esmerilado sobre un fondo oscuro, acentos verde menta y la velocidad en grande con una barra de energía. Tiene versión clara.";
        if (MONO.equals(x)) return "Instrumento de precisión: negro mate, líneas finas en lugar de tarjetas, la velocidad enorme y una regla con aguja. Tiene versión clara.";
        if (PULSE.equals(x)) return "Cuadrante de auto eléctrico con tres arcos: velocidad, revoluciones y refrigerante, en rosa, violeta y cian. Tiene versión clara.";
        if (NEON.equals(x)) return "Bordes de neón con el color de acento que elijas, barra de apps lateral y el carro con los faros encendidos sobre una rejilla de luz. Siempre oscuro.";
        return "Rojo y negro: llamas en el borde de abajo, fibra de carbono, luces de cambio de marcha y un tacómetro de carreras con zona roja. Siempre oscuro.";
    }

    // ── colores ──

    /** BG, CARD, INNER, BTN, DOCK, TEXT, MUTED, SOFT, CIRCLE, ICON */
    static int[] palette(String x, boolean light) {
        if (SHOW.equals(x))
            return new int[]{0xFF070707, 0xFF141414, 0xFF1A1A1A, 0xFF262626, 0xFF0B0B0B, 0xFFF2F2F2, 0xFF8E8E8E, 0xFFCFCFCF, 0xFF1C1C1C, 0xFFFFFFFF};
        if (AERO.equals(x)) return light
            ? new int[]{0xFFE6ECF2, 0xFFFFFFFF, 0xFFEFF3F8, 0xFFDFE6EE, 0xFFDCE4EC, 0xFF0E1726, 0xFF5B6577, 0xFF2E3A4C, 0xFFFFFFFF, 0xFF1E2A3A}
            : new int[]{0xFF06080D, 0xFF121822, 0xFF1A2130, 0xFF1E2634, 0xFF0A0D14, 0xFFEEF3F8, 0xFF8D99AB, 0xFFC7D0DC, 0xFF161D29, 0xFFFFFFFF};
        if (MONO.equals(x)) return light
            ? new int[]{0xFFF2F0EB, 0xFFF7F5F0, 0xFFEAE7E0, 0xFFE2DED6, 0xFFEDEAE3, 0xFF141413, 0xFF6E6A62, 0xFF33312D, 0xFFF2F0EB, 0xFF1E1D1B}
            : new int[]{0xFF0B0B0A, 0xFF100F0E, 0xFF171614, 0xFF201F1C, 0xFF0B0B0A, 0xFFEDEAE3, 0xFF8C877D, 0xFFC9C5BC, 0xFF141412, 0xFFEDEAE3};
        if (PULSE.equals(x)) return light
            ? new int[]{0xFFF1EFF7, 0xFFFFFFFF, 0xFFF4F2FA, 0xFFE6E2F0, 0xFFE9E6F2, 0xFF14121F, 0xFF676380, 0xFF33304A, 0xFFFFFFFF, 0xFF24213A}
            : new int[]{0xFF06060B, 0xFF12111E, 0xFF1A1928, 0xFF201E30, 0xFF06060B, 0xFFEEF0F6, 0xFF8A8FA3, 0xFFC9CDDA, 0xFF14131F, 0xFFFFFFFF};
        if (NEON.equals(x))
            return new int[]{0xFF04060C, 0xFF0A0E1A, 0xFF111727, 0xFF161D30, 0xFF04060C, 0xFFEEF2F8, 0xFF8A94A8, 0xFFC6CEDB, 0xFF0F1524, 0xFFFFFFFF};
        return new int[]{0xFF070303, 0xFF140B0A, 0xFF1C0F0E, 0xFF261413, 0xFF070303, 0xFFF4ECEA, 0xFFA8948F, 0xFFD8CBC8, 0xFF180C0B, 0xFFFFFFFF};
    }

    /** Color de acento funcional (botones, interruptores): con texto blanco encima se tiene que leer. */
    static int accent(String x, int user) {
        if (SHOW.equals(x)) return user;
        if (AERO.equals(x)) return 0xFF0E9F87;
        if (MONO.equals(x)) return 0xFFF0501A;
        if (PULSE.equals(x)) return 0xFFE0267D;
        if (NEON.equals(x)) return user;
        return 0xFFE5261A;
    }

    /** Color de luz (adornos, brillos, arcos). */
    static int glow(String x, int user) {
        if (SHOW.equals(x)) return user;
        if (AERO.equals(x)) return 0xFF5CFFC8;
        if (MONO.equals(x)) return 0xFFFF5A1F;
        if (PULSE.equals(x)) return 0xFFFF2E88;
        if (NEON.equals(x)) return user;
        return 0xFFFF5A1F;
    }

    static float cardRadius() {
        if (SHOW.equals(id)) return 2;
        if (MONO.equals(id)) return 3;
        if (INFERNO.equals(id)) return 6;
        if (NEON.equals(id)) return 22;
        if (PULSE.equals(id)) return 28;
        return 26;
    }

    static Typeface face(boolean medium) {
        if (MONO.equals(id) || INFERNO.equals(id))
            return Typeface.create("sans-serif-condensed", medium ? Typeface.BOLD : Typeface.NORMAL);
        return Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
    }

    /** Letra de los números grandes (velocidad, valores del escáner). */
    static Typeface numFace() {
        if (MONO.equals(id)) return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        if (SHOW.equals(id)) return Typeface.create("sans-serif-condensed", Typeface.BOLD);
        if (INFERNO.equals(id)) return Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
        if (AERO.equals(id)) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static int sensorColor(String key) {
        if ("coolant".equals(key) || "oilt".equals(key)) return 0xFFFF8A3D;
        if ("consumption".equals(key) || "fuel_h".equals(key) || "fuel".equals(key) || "fuel_rate".equals(key)) return 0xFF3DDC84;
        if ("rpm".equals(key) || "volt".equals(key)) return 0xFFFF4D5E;
        if ("speed".equals(key)) return 0xFFE8ECF4;
        if ("intake".equals(key) || "ambient".equals(key)) return 0xFF3DA9FF;
        if ("load".equals(key) || "throttle".equals(key) || "maf".equals(key) || "abs_load".equals(key)) return 0xFF22D3EE;
        if ("timing".equals(key)) return 0xFFFFC53D;
        if ("baro".equals(key) || "odo".equals(key)) return 0xFFC8CDD8;
        if ("torque".equals(key) || "power".equals(key) || "tq_actual".equals(key) || "tq_demand".equals(key)) return 0xFFFF4D5E;
        if ("boost".equals(key) || "turbo_t".equals(key)) return 0xFF22D3EE;
        if ("rail_p".equals(key) || "rail_abs".equals(key) || "fuel_p".equals(key)) return 0xFF3DDC84;
        if (key.startsWith("dpf") || key.startsWith("soot") || "ash".equals(key) || key.startsWith("regen")) return 0xFFFFB547;
        return 0xFFA78BFA;
    }

    static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    // ── tarjetas ──

    /** Fondo de tarjeta del tema. border = solo el borde (marco encima del mapa). */
    static Drawable card(float s, boolean light, int glow, boolean border) { return new Card(id, s, light, glow, border, cardRadius()); }
    static Drawable card(float s, boolean light, int glow, boolean border, float radius) { return new Card(id, s, light, glow, border, radius); }

    private static Bitmap carbonTile;

    static Bitmap carbon() {
        if (carbonTile != null) return carbonTile;
        Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            boolean a = ((x + y) / 2) % 2 == 0, c = ((x - y + 16) / 2) % 2 == 0;
            int v = a ? (c ? 0x16 : 0x11) : (c ? 0x0E : 0x0B);
            b.setPixel(x, y, Color.rgb(v + 4, v, v));
        }
        carbonTile = b;
        return b;
    }

    static final class Card extends Drawable {
        final String st;
        final float s, r;
        final boolean light, border;
        final int glow;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rf = new RectF();
        final Path p = new Path();

        Card(String st, float s, boolean light, int glow, boolean border, float r) {
            this.st = st == null ? AERO : st; this.s = s; this.light = light; this.glow = glow; this.border = border; this.r = r * s;
            line.setStyle(Paint.Style.STROKE);
        }

        private void chamfer(RectF b, float c) {
            p.reset();
            p.moveTo(b.left, b.top);
            p.lineTo(b.right - c, b.top);
            p.lineTo(b.right, b.top + c);
            p.lineTo(b.right, b.bottom);
            p.lineTo(b.left + c, b.bottom);
            p.lineTo(b.left, b.bottom - c);
            p.close();
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            float px = Math.max(1, s);
            if (NEON.equals(st)) {
                float g = 6 * s;
                for (int i = 0; i < 3; i++) {
                    float in = (i + 0.5f) * g / 3;
                    rf.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
                    line.setStrokeWidth(g / 3 + px);
                    line.setColor(alpha(glow, 0x14 + i * 0x16));
                    c.drawRoundRect(rf, r, r, line);
                }
                rf.set(b.left + g, b.top + g, b.right - g, b.bottom - g);
                if (!border) {
                    fill.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xF20B1020, 0xF205070D, Shader.TileMode.CLAMP));
                    c.drawRoundRect(rf, r - g / 2, r - g / 2, fill);
                    fill.setShader(null);
                    RectF in2 = new RectF(rf.left + 3 * s, rf.top + 3 * s, rf.right - 3 * s, rf.bottom - 3 * s);
                    line.setStrokeWidth(6 * s);
                    line.setColor(alpha(glow, 0x14));
                    c.drawRoundRect(in2, r - g, r - g, line);
                }
                line.setStrokeWidth(1.6f * s);
                line.setColor(alpha(glow, 0xDD));
                c.drawRoundRect(rf, r - g / 2, r - g / 2, line);
                return;
            }
            rf.set(b.left + px / 2, b.top + px / 2, b.right - px / 2, b.bottom - px / 2);
            if (INFERNO.equals(st)) {
                float ch = Math.min(22 * s, Math.min(w, h) / 4);
                chamfer(rf, ch);
                if (border) {
                    // encima del mapa: las esquinas cortadas se tapan con el color del fondo
                    fill.setColor(0xFF0A0404);
                    Path k = new Path();
                    k.moveTo(b.right - ch - px, b.top); k.lineTo(b.right, b.top); k.lineTo(b.right, b.top + ch + px); k.close();
                    k.moveTo(b.left, b.bottom - ch - px); k.lineTo(b.left, b.bottom); k.lineTo(b.left + ch + px, b.bottom); k.close();
                    c.drawPath(k, fill);
                }
                if (!border) {
                    BitmapShader bs = new BitmapShader(carbon(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                    android.graphics.Matrix m = new android.graphics.Matrix();
                    m.setScale(Math.max(1, s * 0.75f), Math.max(1, s * 0.75f));
                    bs.setLocalMatrix(m);
                    fill.setShader(bs);
                    c.drawPath(p, fill);
                    fill.setShader(new RadialGradient(b.left + w / 2, b.top + h * 0.45f, Math.max(w, h) * 0.6f,
                        0x30FF3B1F, 0x00FF3B1F, Shader.TileMode.CLAMP));
                    c.drawPath(p, fill);
                    fill.setShader(null);
                }
                line.setStrokeWidth(px);
                line.setColor(0xFF4A1812);
                c.drawPath(p, line);
                line.setStrokeWidth(2.2f * s);
                line.setColor(glow);
                c.drawLine(rf.left, rf.top + s, rf.right - ch, rf.top + s, line);
                return;
            }
            if (SHOW.equals(st)) {
                if (!border) { fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFF181818, 0xFF111111, Shader.TileMode.CLAMP)); c.drawRect(rf, fill); fill.setShader(null); }
                line.setStrokeWidth(px);
                line.setColor(0xFF242424);
                c.drawRect(rf, line);
                return;
            }
            if (MONO.equals(st)) {
                if (!border) { fill.setColor(light ? 0xFFF7F5F0 : 0xFF100F0E); c.drawRoundRect(rf, r, r, fill); }
                line.setStrokeWidth(px);
                line.setColor(light ? 0xFFD3CFC6 : 0xFF2A2824);
                c.drawRoundRect(rf, r, r, line);
                return;
            }
            if (!border) {
                int t, bo;
                if (PULSE.equals(st)) { t = light ? 0xF5FFFFFF : 0xE81A1830; bo = light ? 0xF5F7F5FC : 0xE80B0A14; }
                else { t = light ? 0xEEFFFFFF : 0xDC1A2232; bo = light ? 0xE6F4F7FA : 0xD00B0F17; }
                fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, t, bo, Shader.TileMode.CLAMP));
                c.drawRoundRect(rf, r, r, fill);
                fill.setShader(null);
            }
            line.setStrokeWidth(px);
            line.setColor(light ? 0x16000000 : 0x1CFFFFFF);
            c.drawRoundRect(rf, r, r, line);
            if (!light && !border) {
                line.setColor(0x12FFFFFF);
                c.drawLine(rf.left + r, rf.top + px, rf.right - r, rf.top + px, line);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Botón de la barra de apps (Inferno: paralelogramo; Neon: aro de luz; los demás, sin fondo). */
    static Drawable dockButton(float s, boolean light, int glow) {
        if (INFERNO.equals(id)) return new Skew(s, glow);
        if (NEON.equals(id)) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(0xFF0C1322);
            g.setStroke(Math.max(1, (int) (1.2f * s)), alpha(glow, 0x99));
            return g;
        }
        if (MONO.equals(id)) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setColor(0x00000000);
            g.setStroke(Math.max(1, (int) s), light ? 0xFFCFCBC2 : 0xFF34322D);
            g.setCornerRadius(2 * s);
            return g;
        }
        return null;
    }

    static final class Skew extends Drawable {
        final float s; final int glow;
        final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG), l = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path p = new Path();
        Skew(float s, int glow) { this.s = s; this.glow = glow; l.setStyle(Paint.Style.STROKE); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float k = b.width() * 0.16f;
            p.reset();
            p.moveTo(b.left + k, b.top); p.lineTo(b.right, b.top); p.lineTo(b.right - k, b.bottom); p.lineTo(b.left, b.bottom); p.close();
            f.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xF2201010, 0xF20C0606, Shader.TileMode.CLAMP));
            c.drawPath(p, f);
            l.setStrokeWidth(Math.max(1, s));
            l.setColor(0xFF5A1A12);
            c.drawPath(p, l);
            l.setStrokeWidth(2 * s);
            l.setColor(glow);
            c.drawLine(b.left + k, b.top + s, b.right, b.top + s, l);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ── fondo ──

    private static Bitmap bgCache;
    private static String bgKey;

    /** Fondo del tema a media resolución (se guarda hasta que cambie el tamaño, el modo o el color). */
    static Bitmap background(int w, int h, boolean light, int glow) {
        int bw = Math.max(64, w / 2), bh = Math.max(64, h / 2);
        String key = id + ":" + bw + "x" + bh + ":" + light + ":" + glow;
        if (bgCache != null && !bgCache.isRecycled() && key.equals(bgKey)) return bgCache;
        Bitmap b;
        try { b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); }
        catch (Throwable e) { b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); b.eraseColor(palette(id, light)[0]); return b; }
        Canvas c = new Canvas(b);
        paint(c, id, bw, bh, light, glow);
        bgCache = b;
        bgKey = key;
        return b;
    }

    private static void radial(Canvas c, float cx, float cy, float r, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, Math.max(1, r), color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
    }

    static void paint(Canvas c, String st, int w, int h, boolean light, int glow) {
        int base = palette(st, light)[0];
        c.drawColor(base);
        if (AERO.equals(st)) {
            radial(c, w * 0.5f, -h * 0.15f, w * 0.75f, light ? 0xAAFFFFFF : 0x9A15223A);
            radial(c, w * 0.5f, h * 1.15f, w * 0.5f, light ? 0x305CC8A8 : 0x205CFFC8);
        } else if (PULSE.equals(st)) {
            radial(c, w * 0.18f, h * 0.52f, w * 0.38f, light ? 0x30B450FF : 0x36B450FF);
            radial(c, w * 0.9f, h * 0.45f, w * 0.32f, light ? 0x242EC9FF : 0x222EC9FF);
        } else if (NEON.equals(st)) {
            radial(c, w * 0.25f, h * 0.6f, w * 0.45f, alpha(glow, 0x2A));
            radial(c, w * 0.85f, h * 0.2f, w * 0.35f, alpha(glow, 0x16));
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setStrokeWidth(Math.max(1, w / 900f));
            float hz = h * 0.72f, vx = w * 0.5f;
            for (int i = -10; i <= 10; i++) {
                g.setColor(alpha(glow, 0x18));
                c.drawLine(vx, hz, vx + i * w * 0.12f, h, g);
            }
            for (int i = 1; i <= 6; i++) {
                float y = hz + (h - hz) * (i * i) / 36f;
                g.setColor(alpha(glow, 0x10 + i * 3));
                c.drawLine(0, y, w, y, g);
            }
        } else if (INFERNO.equals(st)) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            c.save();
            c.scale(1, 0.45f, w * 0.5f, h);
            radial(c, w * 0.5f, h * 1.05f, w * 0.75f, 0x70FF3B1F);
            c.restore();
            radial(c, w * 0.12f, 0, w * 0.4f, 0x408C140A);
            flames(c, w, h, p);
        } else if (SHOW.equals(st)) {
            radial(c, w * 0.42f, h * 0.55f, w * 0.55f, 0x10FFFFFF);
        } else if (MONO.equals(st)) {
            Paint p = new Paint();
            p.setColor(light ? 0x0A000000 : 0x08FFFFFF);
            for (int y = 0; y < h; y += Math.max(3, h / 150)) c.drawLine(0, y, w, y, p);
        }
    }

    /** Llamas en el borde de abajo (tres capas) y chispas que suben. */
    static void flames(Canvas c, int w, int h, Paint p) {
        float fh = h * 0.22f;
        int[][] layers = {{0x00B3170A, 0xC0B3170A, 0xFFE0300F}, {0x00FF6A1A, 0xD0FF6A1A, 0xFFFF9A2E}, {0x00FFD36B, 0x90FFD36B, 0xD0FFE08A}};
        float[] amp = {1f, 0.66f, 0.35f};
        int[] count = {20, 28, 38};
        for (int L = 0; L < 3; L++) {
            Random r = new Random(11 + L * 7);
            Path path = new Path();
            path.moveTo(0, h);
            float step = w / (float) count[L], x = 0;
            for (int i = 0; i < count[L]; i++) {
                float peak = h - fh * amp[L] * (0.45f + r.nextFloat() * 0.55f);
                float px = x + step * (0.35f + r.nextFloat() * 0.3f), lean = (r.nextFloat() - 0.5f) * step * 0.5f;
                path.cubicTo(x + step * 0.15f, h - (h - peak) * 0.35f, px - step * 0.25f + lean, peak + (h - peak) * 0.35f, px + lean, peak);
                path.cubicTo(px + step * 0.15f + lean, peak + (h - peak) * 0.4f, x + step * 0.85f, h - (h - peak) * 0.25f, x + step, h - r.nextFloat() * fh * 0.12f);
                x += step;
            }
            path.lineTo(w, h);
            path.close();
            p.setShader(new LinearGradient(0, h - fh * amp[L], 0, h, layers[L], new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
        }
        p.setShader(null);
        Random r = new Random(5);
        for (int i = 0; i < 34; i++) {
            float x = r.nextFloat() * w, y = h * (0.55f + r.nextFloat() * 0.45f), rad = Math.max(0.6f, w / 1024f * (0.8f + r.nextFloat() * 1.6f));
            p.setColor(Color.argb((int) (90 + r.nextFloat() * 150), 255, 179, 71));
            c.drawCircle(x, y, rad, p);
        }
    }

    // ── instrumento de la tarjeta del vehículo ──

    /**
     * Velocidad con el instrumento propio de cada tema: Aero (número fino y barra de energía), Monolith (número
     * enorme y regla con aguja), Pulse (tres arcos: velocidad, revoluciones del escáner y refrigerante), Neon (rejilla
     * de luz y carro de frente si no hay foto) e Inferno (luces de cambio y tacómetro con zona roja).
     */
    static final class Gauge extends View {
        final float s;
        final boolean light, mph, drawCar;
        final int text, muted, glow, track;
        float speed;
        boolean ok;
        String src = "GPS";
        int limit;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rf = new RectF();
        final Path path = new Path();

        Gauge(Context c, float s, boolean light, boolean mph, int text, int muted, int glow, int track, boolean drawCar) {
            super(c);
            this.s = s; this.light = light; this.mph = mph; this.text = text; this.muted = muted; this.glow = glow; this.track = track;
            this.drawCar = drawCar;
            t.setTextAlign(Paint.Align.CENTER);
            // por software: trazos punteados en arcos y sombras de texto se ven igual en Android 4.x
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setSpeed(float v, boolean ok, boolean obd, int limit) {
            int nv = Math.round(v);
            if (nv == Math.round(speed) && ok == this.ok && limit == this.limit && (obd ? "OBD" : "GPS").equals(src)) return;
            speed = v; this.ok = ok; src = obd ? "OBD" : "GPS"; this.limit = limit;
            invalidate();
        }

        private float max() { return mph ? 120 : 200; }
        private String num() { return ok ? String.valueOf(Math.round(speed)) : "--"; }
        private String unit() { return mph ? "MPH" : "KM/H"; }

        private void text(Canvas c, String v, float x, float y, float size, int color, Typeface f, Paint.Align a) {
            t.setTypeface(f);
            t.setTextSize(size);
            t.setColor(color);
            t.setTextAlign(a);
            c.drawText(v, x, y, t);
        }

        private float arcPt(double deg, float cx, float r, boolean xAxis, float cy) {
            double a = Math.toRadians(deg);
            return xAxis ? (float) (cx + r * Math.cos(a)) : (float) (cy + r * Math.sin(a));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float frac = Math.max(0, Math.min(1, speed / max()));
            if (!ok) frac = 0;
            if (MONO.equals(id)) drawMono(c, w, h, frac);
            else if (PULSE.equals(id)) drawPulse(c, w, h, frac);
            else if (INFERNO.equals(id)) drawInferno(c, w, h);
            else if (NEON.equals(id)) drawNeon(c, w, h);
            else drawAero(c, w, h, frac);
        }

        private void sign(Canvas c, float cx, float cy, float r) {
            if (limit <= 0) return;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.22f);
            p.setColor(0xFFE5323B);
            c.drawCircle(cx, cy, r - r * 0.11f, p);
            p.setStyle(Paint.Style.FILL);
            text(c, String.valueOf(limit), cx, cy + r * 0.32f, r * 0.85f, 0xFF111418, Typeface.DEFAULT_BOLD, Paint.Align.CENTER);
        }

        private void drawAero(Canvas c, float w, float h, float frac) {
            float big = Math.min(h * 0.72f, 84 * s);
            text(c, num(), 2 * s, big * 0.84f, big, text, numFace(), Paint.Align.LEFT);
            t.setTypeface(numFace());
            t.setTextSize(big);
            float nw = t.measureText(num());
            text(c, unit(), nw + 12 * s, big * 0.5f, 13 * s, muted, Typeface.DEFAULT, Paint.Align.LEFT);
            p.setStyle(Paint.Style.FILL);
            p.setColor(glow);
            c.drawCircle(nw + 15 * s, big * 0.5f + 14 * s, 3.5f * s, p);
            text(c, src, nw + 23 * s, big * 0.5f + 18 * s, 11 * s, muted, Typeface.DEFAULT, Paint.Align.LEFT);
            sign(c, w - 24 * s, big * 0.45f, 21 * s);
            int n = 20;
            float gap = 3 * s, sw = (w - gap * (n - 1)) / n, y = h - 8 * s;
            int lit = Math.round(frac * n);
            for (int i = 0; i < n; i++) {
                rf.set(i * (sw + gap), y, i * (sw + gap) + sw, y + 6 * s);
                if (i < lit) p.setColor(alpha(glow, 0x70 + (0x8F * (i + 1)) / Math.max(1, lit)));
                else p.setColor(light ? 0x14000000 : 0x16FFFFFF);
                c.drawRoundRect(rf, 2 * s, 2 * s, p);
            }
        }

        private void drawMono(Canvas c, float w, float h, float frac) {
            float rulerH = 46 * s, big = Math.min(h - rulerH - 8 * s, 180 * s);
            big = Math.min(big, (w - 64 * s) / (Math.max(2, num().length()) * 0.5f));   // que «120» quepa con la unidad
            text(c, num(), -2 * s, big * 0.8f, big * 1.05f, text, numFace(), Paint.Align.LEFT);
            t.setTypeface(numFace());
            t.setTextSize(big * 1.05f);
            float nw = t.measureText(num());
            Typeface mono = Typeface.MONOSPACE;
            text(c, unit(), nw + 10 * s, 18 * s, 11 * s, muted, mono, Paint.Align.LEFT);
            p.setStyle(Paint.Style.FILL);
            p.setColor("OBD".equals(src) ? 0xFF3DDC84 : text);
            c.drawRect(nw + 10 * s, 30 * s, nw + 16 * s, 36 * s, p);
            text(c, src, nw + 21 * s, 37 * s, 11 * s, muted, mono, Paint.Align.LEFT);
            if (limit > 0) text(c, limit + " LIM", nw + 10 * s, 56 * s, 11 * s, muted, mono, Paint.Align.LEFT);
            float y0 = h - rulerH, lx = w - 2 * s;
            p.setStrokeWidth(Math.max(1, s));
            int ticks = 20;
            for (int i = 0; i <= ticks; i++) {
                float x = 1 + (lx - 1) * i / ticks;
                boolean major = i % 4 == 0;
                p.setColor(major ? muted : (light ? 0xFFB9B4AA : 0xFF5A5650));
                c.drawLine(x, y0 + (major ? 12 : 18) * s, x, y0 + 28 * s, p);
                if (major) text(c, String.valueOf((int) (max() * i / ticks)), x, y0 + 42 * s, 9 * s, muted, mono,
                    i == 0 ? Paint.Align.LEFT : i == ticks ? Paint.Align.RIGHT : Paint.Align.CENTER);
            }
            float nx = 1 + (lx - 1) * frac;
            p.setColor(alpha(glow, 0x60));
            c.drawRect(1, y0 + 20 * s, nx, y0 + 23 * s, p);
            p.setColor(glow);
            p.setStrokeWidth(2 * s);
            c.drawLine(nx, y0 + 4 * s, nx, y0 + 30 * s, p);
            path.reset();
            path.moveTo(nx - 5 * s, y0);
            path.lineTo(nx + 5 * s, y0);
            path.lineTo(nx, y0 + 6 * s);
            path.close();
            c.drawPath(path, p);
        }

        private void arc(Canvas c, float cx, float cy, float r, float start, float sweep, float sw, int color, boolean round, float dash) {
            rf.set(cx - r, cy - r, cx + r, cy + r);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setStrokeCap(round ? Paint.Cap.ROUND : Paint.Cap.BUTT);
            p.setColor(color);
            if (dash > 0) p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dash, dash * 0.3f}, 0));
            c.drawArc(rf, start, sweep, false, p);
            p.setPathEffect(null);
            p.setShader(null);
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawPulse(Canvas c, float w, float h, float frac) {
            float r = Math.min(w, h) / 2 - 12 * s, cx = w / 2, cy = h / 2;
            float sw = 11 * s;
            // velocidad: arco izquierdo de 125° a 265°
            arc(c, cx, cy, r, 125, 140, sw, alpha(0xFFB48CFF, light ? 0x30 : 0x2E), true, 0);
            if (frac > 0) {
                p.setShader(new LinearGradient(0, cy + r, 0, cy - r, 0xFFFF2E88, 0xFFB48CFF, Shader.TileMode.CLAMP));
                rf.set(cx - r, cy - r, cx + r, cy + r);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawArc(rf, 125, 140 * frac, false, p);
                p.setShader(null);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
            // revoluciones del escáner: arco derecho segmentado de 275° a 415°
            float rpm = Obd.RPM.has() ? Math.max(0, Math.min(1, Obd.RPM.value / 7000f)) : 0;
            arc(c, cx, cy, r, 275, 140, sw, light ? 0x14000000 : 0xFF1C1E2E, false, 12 * s);
            if (rpm > 0) {
                p.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFFE8FFF6, 0xFF2EC9FF, Shader.TileMode.CLAMP));
                rf.set(cx - r, cy - r, cx + r, cy + r);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{12 * s, 3.6f * s}, 0));
                c.drawArc(rf, 275, 140 * rpm, false, p);
                p.setPathEffect(null);
                p.setShader(null);
            }
            // refrigerante: arco corto abajo (50 a 130 °C)
            float cool = Obd.COOLANT.has() ? Math.max(0, Math.min(1, (Obd.COOLANT.value - 50) / 80f)) : 0;
            arc(c, cx, cy, r, 70, 40, 7 * s, light ? 0x14000000 : 0xFF1C1E2E, true, 0);
            if (cool > 0) arc(c, cx, cy, r, 110 - 40 * cool, 40 * cool, 7 * s, 0xFF2EC9FF, true, 0);
            // centro
            float ir = r * 0.66f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy - ir * 0.2f, ir, light ? 0xFFFFFFFF : 0xFF2A1F4A, light ? 0xFFF1EFF7 : 0xFF0D0C17, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, ir, p);
            p.setShader(null);
            text(c, src + (limit > 0 ? " · " + limit : ""), cx, cy - ir * 0.42f, 10 * s, muted, Typeface.DEFAULT, Paint.Align.CENTER);
            text(c, num(), cx, cy + ir * 0.28f, ir * 0.78f, text, numFace(), Paint.Align.CENTER);
            text(c, unit(), cx, cy + ir * 0.55f, 11 * s, muted, Typeface.DEFAULT, Paint.Align.CENTER);
            Typeface bold = Typeface.DEFAULT_BOLD;
            if (Obd.RPM.has()) text(c, "RPM " + String.format(I18n.num, "%.1fK", Obd.RPM.value / 1000f), cx + r * 0.55f, cy - r - 2 * s, 10 * s, 0xFF39C8B0, bold, Paint.Align.CENTER);
            if (Obd.COOLANT.has()) text(c, Math.round(Obd.COOLANT.value) + "°C", cx, cy + r + 10 * s, 10 * s, 0xFF2EC9FF, bold, Paint.Align.CENTER);
        }

        private void drawNeon(Canvas c, float w, float h) {
            float floorY = h * 0.52f, vx = w / 2;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, s));
            for (int i = -6; i <= 6; i++) {
                p.setColor(alpha(glow, 0x55));
                c.drawLine(vx, floorY - 30 * s, vx + i * w * 0.22f, h * 0.62f + 40 * s, p);
            }
            for (int i = 1; i <= 4; i++) {
                float y = floorY + (h * 0.62f + 40 * s - floorY) * i * i / 16f;
                p.setColor(alpha(glow, 0x40));
                c.drawLine(0, y, w, y, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, floorY - 34 * s, 0, floorY + 10 * s, light ? 0xFFE6ECF2 : 0xFF05070D, 0x0005070D, Shader.TileMode.CLAMP));
            c.drawRect(0, floorY - 34 * s, w, floorY + 10 * s, p);
            p.setShader(new RadialGradient(vx, h * 0.6f, w * 0.42f, alpha(glow, 0x70), alpha(glow, 0), Shader.TileMode.CLAMP));
            c.save();
            c.scale(1, 0.16f, vx, h * 0.6f);
            c.drawCircle(vx, h * 0.6f, w * 0.42f, p);
            c.restore();
            p.setShader(null);
            if (drawCar) carFront(c, vx, h * 0.6f, Math.min(w * 0.78f, h * 0.9f));
            float big = Math.min(h * 0.3f, 72 * s);
            t.setTypeface(numFace());
            t.setTextSize(big);
            float nw = t.measureText(num());
            float x0 = (w - nw - 50 * s) / 2;
            t.setShadowLayer(18 * s, 0, 0, alpha(glow, 0xAA));
            text(c, num(), x0, h - 6 * s, big, text, numFace(), Paint.Align.LEFT);
            t.clearShadowLayer();
            text(c, unit().toLowerCase(java.util.Locale.US), x0 + nw + 8 * s, h - 14 * s, 15 * s, muted, Typeface.DEFAULT_BOLD, Paint.Align.LEFT);
        }

        /** Carro de frente (sin foto): parabrisas, capó, faros con el color de acento y la barra de luz. */
        private void carFront(Canvas c, float cx, float base, float cw) {
            float k = cw / 220f, x0 = cx - 110 * k, y0 = base - 118 * k;
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(x0 + 70 * k, y0 + 20 * k); path.quadTo(x0 + 110 * k, y0 + 10 * k, x0 + 150 * k, y0 + 20 * k);
            path.lineTo(x0 + 166 * k, y0 + 52 * k); path.lineTo(x0 + 54 * k, y0 + 52 * k); path.close();
            p.setShader(new LinearGradient(0, y0 + 10 * k, 0, y0 + 52 * k, 0xFF3A4658, 0xFF0A0E16, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            path.reset();
            path.moveTo(x0 + 30 * k, y0 + 62 * k); path.quadTo(x0 + 34 * k, y0 + 50 * k, x0 + 54 * k, y0 + 50 * k);
            path.lineTo(x0 + 166 * k, y0 + 50 * k); path.quadTo(x0 + 186 * k, y0 + 50 * k, x0 + 190 * k, y0 + 62 * k);
            path.lineTo(x0 + 200 * k, y0 + 80 * k); path.quadTo(x0 + 204 * k, y0 + 94 * k, x0 + 198 * k, y0 + 110 * k);
            path.lineTo(x0 + 22 * k, y0 + 110 * k); path.quadTo(x0 + 16 * k, y0 + 94 * k, x0 + 20 * k, y0 + 80 * k); path.close();
            p.setShader(new LinearGradient(0, y0 + 50 * k, 0, y0 + 110 * k, 0xFFF4F7FB, 0xFF5D6878, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            p.setColor(0xFFA9B4C4);
            c.drawOval(new RectF(x0 + 35 * k, y0 + 49 * k, x0 + 53 * k, y0 + 59 * k), p);
            c.drawOval(new RectF(x0 + 167 * k, y0 + 49 * k, x0 + 185 * k, y0 + 59 * k), p);
            p.setColor(glow);
            path.reset(); path.moveTo(x0 + 28 * k, y0 + 68 * k); path.lineTo(x0 + 76 * k, y0 + 72 * k); path.lineTo(x0 + 71 * k, y0 + 81 * k); path.lineTo(x0 + 32 * k, y0 + 78 * k); path.close();
            c.drawPath(path, p);
            path.reset(); path.moveTo(x0 + 192 * k, y0 + 68 * k); path.lineTo(x0 + 144 * k, y0 + 72 * k); path.lineTo(x0 + 149 * k, y0 + 81 * k); path.lineTo(x0 + 188 * k, y0 + 78 * k); path.close();
            c.drawPath(path, p);
            p.setStrokeWidth(2 * k);
            c.drawLine(x0 + 76 * k, y0 + 73 * k, x0 + 144 * k, y0 + 73 * k, p);
            p.setColor(0xFF0B0F16);
            path.reset(); path.moveTo(x0 + 86 * k, y0 + 86 * k); path.lineTo(x0 + 134 * k, y0 + 86 * k); path.lineTo(x0 + 128 * k, y0 + 100 * k); path.lineTo(x0 + 92 * k, y0 + 100 * k); path.close();
            c.drawPath(path, p);
            c.drawRoundRect(new RectF(x0 + 26 * k, y0 + 104 * k, x0 + 48 * k, y0 + 124 * k), 4 * k, 4 * k, p);
            c.drawRoundRect(new RectF(x0 + 172 * k, y0 + 104 * k, x0 + 194 * k, y0 + 124 * k), 4 * k, 4 * k, p);
        }

        private void drawInferno(Canvas c, float w, float h) {
            // luces de cambio de marcha: 12 (4 verdes, 4 ámbar, 4 rojas) según las revoluciones del escáner
            float rpm = Obd.RPM.has() ? Obd.RPM.value : 0;
            int lit = (int) Math.floor(Math.max(0, Math.min(1, rpm / 7000f)) * 12 + 0.0001f);
            float gap = 4 * s, lw = (w - gap * 11) / 12, lh = 11 * s;
            int[] on = {0xFF3DFF7A, 0xFFFFC53D, 0xFFFF2A14};
            int[] off = {0xFF14240F, 0xFF2A2208, 0xFF2A0A08};
            for (int i = 0; i < 12; i++) {
                rf.set(i * (lw + gap), 0, i * (lw + gap) + lw, lh);
                p.setStyle(Paint.Style.FILL);
                p.setColor(i < lit ? on[i / 4] : off[i / 4]);
                c.drawRoundRect(rf, 3 * s, 3 * s, p);
            }
            // tacómetro de 0 a 8 (×1000) con zona roja desde 6
            float top = lh + 6 * s;
            float r = Math.min(w / 2 - 6 * s, (h - top) * 0.56f), cx = w / 2, cy = top + r + 8 * s;
            float frac = Math.max(0, Math.min(1, rpm / 8000f));
            arc(c, cx, cy, r, 150, 240, 9 * s, 0xFF1E1010, false, 0);
            p.setShader(new LinearGradient(cx + r, cy - r, cx + r, cy + r, 0xFFFF2A14, 0xFF7A0A04, Shader.TileMode.CLAMP));
            rf.set(cx - r, cy - r, cx + r, cy + r);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(9 * s);
            c.drawArc(rf, 330, 60, false, p);
            p.setShader(null);
            if (frac > 0) {
                p.setShader(new LinearGradient(cx - r, cy + r, cx + r, cy - r, 0xFFFFB347, 0xFFFF2A14, Shader.TileMode.CLAMP));
                c.drawArc(rf, 150, 240 * frac, false, p);
                p.setShader(null);
            }
            Typeface it = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
            for (int k = 0; k <= 8; k++) {
                double a = Math.toRadians(150 + 30 * k);
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                p.setStrokeWidth(3 * s);
                p.setColor(k >= 6 ? 0xFFFF3B1F : 0xFFE8DCD8);
                c.drawLine(cx + (r - 22 * s) * ca, cy + (r - 22 * s) * sa, cx + (r - 10 * s) * ca, cy + (r - 10 * s) * sa, p);
                text(c, String.valueOf(k), cx + (r - 34 * s) * ca, cy + (r - 34 * s) * sa + 5 * s, 13 * s, k >= 6 ? 0xFFFF3B1F : 0xFF9A8A86, it, Paint.Align.CENTER);
                if (k < 8) for (int m = 1; m <= 4; m++) {
                    double b = Math.toRadians(150 + 30 * k + 6 * m);
                    float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
                    p.setStrokeWidth(1.5f * s);
                    p.setColor(k >= 6 ? 0xFF7A1A10 : 0xFF4A3E3B);
                    c.drawLine(cx + (r - 16 * s) * cb, cy + (r - 16 * s) * sb, cx + (r - 10 * s) * cb, cy + (r - 10 * s) * sb, p);
                }
            }
            double na = Math.toRadians(150 + 240 * frac);
            float ncos = (float) Math.cos(na), nsin = (float) Math.sin(na);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(6 * s);
            p.setColor(0x66FF3B1F);
            c.drawLine(cx + r * 0.42f * ncos, cy + r * 0.42f * nsin, cx + (r - 8 * s) * ncos, cy + (r - 8 * s) * nsin, p);
            p.setStrokeWidth(2.6f * s);
            p.setColor(Color.WHITE);
            c.drawLine(cx + r * 0.42f * ncos, cy + r * 0.42f * nsin, cx + (r - 8 * s) * ncos, cy + (r - 8 * s) * nsin, p);
            p.setStrokeCap(Paint.Cap.BUTT);
            float big = r * 0.62f;
            text(c, num(), cx + 3 * s, cy + big * 0.3f, big, text, it, Paint.Align.CENTER);
            text(c, unit() + " · " + src, cx, cy + big * 0.3f + 18 * s, 11 * s, muted, it, Paint.Align.CENTER);
            String rs = Obd.RPM.has() ? I18n.integer(Math.round(rpm)) + " RPM · ×1000" : "RPM ×1000";
            text(c, rs, cx, Math.min(h - 2 * s, cy + r * 0.86f), 11 * s, 0xFFFF7A4D, it, Paint.Align.CENTER);
        }
    }

    /** Silueta lateral de un auto eléctrico (Aero, sin foto): carrocería plateada, faro de luz y piloto rojo. */
    static final class CarSide extends View {
        final int glow;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        CarSide(Context c, int glow) { super(c); this.glow = glow; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float k = Math.min(w / 216f, h / 86f), x0 = (w - 216 * k) / 2, y0 = (h - 86 * k) / 2;
            p.setShader(new RadialGradient(w / 2, y0 + 80 * k, 100 * k, alpha(glow, 0x55), alpha(glow, 0), Shader.TileMode.CLAMP));
            c.save(); c.scale(1, 0.18f, w / 2, y0 + 80 * k); c.drawCircle(w / 2, y0 + 80 * k, 100 * k, p); c.restore();
            path.reset();
            path.moveTo(x0 + 10 * k, y0 + 64 * k);
            path.cubicTo(x0 + 12 * k, y0 + 52 * k, x0 + 28 * k, y0 + 48 * k, x0 + 52 * k, y0 + 46 * k);
            path.lineTo(x0 + 86 * k, y0 + 34 * k);
            path.cubicTo(x0 + 100 * k, y0 + 27 * k, x0 + 130 * k, y0 + 26 * k, x0 + 150 * k, y0 + 30 * k);
            path.lineTo(x0 + 176 * k, y0 + 44 * k);
            path.cubicTo(x0 + 192 * k, y0 + 46 * k, x0 + 204 * k, y0 + 52 * k, x0 + 206 * k, y0 + 62 * k);
            path.lineTo(x0 + 206 * k, y0 + 68 * k);
            path.lineTo(x0 + 10 * k, y0 + 68 * k);
            path.close();
            p.setShader(new LinearGradient(0, y0 + 26 * k, 0, y0 + 68 * k, 0xFFF3F6FA, 0xFF8E9AAD, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            p.setColor(0xFF141A24);
            path.reset();
            path.moveTo(x0 + 92 * k, y0 + 36 * k); path.lineTo(x0 + 118 * k, y0 + 31 * k); path.lineTo(x0 + 146 * k, y0 + 32 * k);
            path.lineTo(x0 + 166 * k, y0 + 44 * k); path.lineTo(x0 + 88 * k, y0 + 45 * k); path.close();
            c.drawPath(path, p);
            p.setStrokeWidth(3 * k);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(glow);
            c.drawLine(x0 + 196 * k, y0 + 52 * k, x0 + 206 * k, y0 + 52 * k, p);
            p.setColor(0xFFFF4D5E);
            c.drawLine(x0 + 12 * k, y0 + 58 * k, x0 + 22 * k, y0 + 58 * k, p);
            p.setStrokeCap(Paint.Cap.BUTT);
            for (float wx : new float[]{52, 168}) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0B0F16);
                c.drawCircle(x0 + wx * k, y0 + 68 * k, 13 * k, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * k);
                p.setColor(0xFF7D8899);
                c.drawCircle(x0 + wx * k, y0 + 68 * k, 6 * k, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    /** Miniatura del tema para Personalización: el fondo, las tres tarjetas y la barra de apps. */
    static final class Thumb extends View {
        final String st;
        final boolean light;
        final int glow;
        final float s;
        Bitmap bg;
        Thumb(Context c, String st, boolean light, int glow, float s) {
            super(c);
            this.st = st; this.light = light; this.glow = glow; this.s = s;
        }
        @Override protected void onMeasure(int wm, int hm) {
            int w = MeasureSpec.getSize(wm);
            setMeasuredDimension(w, (int) (w * 0.586f));
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0) return;
            if (bg == null || bg.getWidth() != w || bg.getHeight() != h) {
                try { bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); } catch (Throwable e) { return; }
                Canvas bc = new Canvas(bg);
                paint(bc, st, w, h, light, glow);
                drawMini(bc, w, h);
            }
            // esquinas redondas sin clipPath (Android 4.1–4.2 con aceleración no lo soporta)
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setShader(new BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(0, 0, w, h), 10 * s, 10 * s, sh);
        }

        private void drawMini(Canvas c, int w, int h) {
            if (SHOW.equals(st)) { drawShow(c, w, h); return; }
            String keep = id;
            id = st;
            float k = w / 1024f;
            int[] pal = palette(st, light);
            boolean side = NEON.equals(st);
            float ox = side ? 100 * k : 0;
            Card car = new Card(st, k, light, glow, false, cardRadius());
            car.setBounds((int) (ox + 16 * k), (int) (64 * k), (int) (ox + 300 * k), (int) (440 * k));
            car.draw(c);
            Paint map = new Paint(Paint.ANTI_ALIAS_FLAG);
            map.setColor(light ? 0xFFDCE2E8 : 0xFF151A24);
            RectF mr = new RectF(ox + 312 * k, 64 * k, (side ? 1008 : 752) * k, 440 * k);
            c.drawRoundRect(mr, cardRadius() * k, cardRadius() * k, map);
            map.setColor(glow);
            map.setStrokeWidth(Math.max(2, 6 * k));
            c.drawLine(mr.centerX(), mr.bottom - 90 * k, mr.centerX(), mr.top + 120 * k, map);
            c.drawLine(mr.centerX(), mr.top + 120 * k, mr.right - 40 * k, mr.top + 120 * k, map);
            map.setColor(accent(st, glow));
            c.drawRoundRect(new RectF(mr.left + 16 * k, mr.bottom - 80 * k, mr.centerX() - 8 * k, mr.bottom - 16 * k), 14 * k, 14 * k, map);
            if (!side) {
                Card pl = new Card(st, k, light, glow, false, cardRadius());
                pl.setBounds((int) (764 * k), (int) (64 * k), (int) (1008 * k), (int) (440 * k));
                pl.draw(c);
            } else {
                Card rail = new Card(st, k, light, glow, false, cardRadius());
                rail.setBounds((int) (12 * k), (int) (12 * k), (int) (96 * k), h - (int) (12 * k));
                rail.draw(c);
            }
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setColor(glow);
            float gx = ox + 158 * k, gy = 250 * k, gr = 80 * k;
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(Math.max(2, 10 * k));
            c.drawArc(new RectF(gx - gr, gy - gr, gx + gr, gy + gr), 150, 120, false, g);
            g.setStyle(Paint.Style.FILL);
            g.setColor(pal[5]);
            if (!side) for (int i = 0; i < 7; i++) c.drawCircle((110 + i * 134) * k, 528 * k, 18 * k, g);
            id = keep;
        }

        private void drawShow(Canvas c, int w, int h) {
            float k = w / 1024f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF101010);
            c.drawRect(0, 0, 112 * k, h, p);
            p.setColor(0xFF0D0D0D);
            c.drawRect(112 * k, 0, w, 60 * k, p);
            p.setColor(0xFF262626);
            c.drawRect(0, 64 * k, 112 * k, 148 * k, p);
            p.setColor(glow);
            c.drawRect(0, 142 * k, 112 * k, 148 * k, p);
            p.setColor(0xFF9A9A9A);
            for (int i = 0; i < 4; i++) c.drawRect(36 * k, (180 + i * 84) * k, 76 * k, (200 + i * 84) * k, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, 2 * k));
            p.setColor(0x553C3C3C);
            float vx = 500 * k, vy = 210 * k;
            for (int i = -6; i <= 6; i++) c.drawLine(vx, vy, vx + i * 130 * k, h, p);
            for (int j = 1; j <= 4; j++) { float t = j / 4f, yy = vy + (h - vy) * t * t; c.drawLine(112 * k, yy, w, yy, p); }
            p.setStyle(Paint.Style.FILL);
            Bitmap car = Show.render(getContext(), "hatch", "silver", 2);
            if (car != null) {
                float cw = 560 * k, ch = cw * Show.CH / Show.CW;
                c.drawBitmap(car, null, new RectF(130 * k, 430 * k - ch * Show.GROUND / Show.CH, 130 * k + cw, 430 * k - ch * Show.GROUND / Show.CH + ch),
                    new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            }
            p.setColor(0xFFF2F2F2);
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            p.setTextSize(150 * k);
            c.drawText("48", 700 * k, 230 * k, p);
            p.setColor(0xFF2E2E2E);
            for (int i = 0; i < 6; i++) {
                c.drawRect((178 + i * 136) * k, 470 * k, (306 + i * 136) * k, 548 * k, p);
            }
            p.setColor(glow);
            for (int i = 0; i < 6; i++) c.drawRect((178 + i * 136) * k, 542 * k, (306 + i * 136) * k, 548 * k, p);
        }
    }
}
