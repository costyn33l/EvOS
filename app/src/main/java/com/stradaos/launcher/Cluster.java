package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuadros de instrumentos para Vehículo › Instrumentos y para el inicio de los temas con velocidad: Telemetría
 * (barras y gráfico, letra condensada), Sport (arco grande y número en cursiva) y Clásico (reloj redondo fino).
 * Cada uno tiene una versión completa y una compacta (cuando el espacio es chico). Solo dibuja los datos que el
 * carro de verdad entrega: lo que falta no aparece (la marcha no se muestra porque el OBD-II estándar no la da).
 */
final class Cluster extends View {
    static final String THEME = "theme", EVOS = "evos", TELE = "telemetry", SPORT = "sport", CLASSIC = "classic";
    static final String[] STYLES = {TELE, SPORT, CLASSIC};
    static final String[] NAMES = {"Telemetría", "Sport", "Clásico"};

    static String name(String st) {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(st)) return NAMES[i];
        return EVOS.equals(st) ? "EvOS" : "Del tema";
    }

    /** Lo que muestran los cuadros (lo llena Main). NaN = no hay dato. */
    static final class Data {
        float speed = Float.NaN, rpm = Float.NaN, thr = Float.NaN, load = Float.NaN, cool = Float.NaN, intake = Float.NaN,
            volt = Float.NaN, cons = Float.NaN, avg = Float.NaN, fuelPct = Float.NaN, tankL = 0, amb = Float.NaN;
        boolean mph, speedObd, obd;
        String clock = "", unitDist = "km";
        float rpmMax = 8000, redFrom = 6000;
        final float[] hSpeed = new float[60], hThr = new float[60];
        int hN;
        void sample() {
            if (hN == 60) { System.arraycopy(hSpeed, 1, hSpeed, 0, 59); System.arraycopy(hThr, 1, hThr, 0, 59); hN--; }
            hSpeed[hN] = Float.isNaN(speed) ? 0 : speed;
            hThr[hN] = Float.isNaN(thr) ? -1 : thr;
            hN++;
        }
        float fuelL() { return !Float.isNaN(fuelPct) && tankL > 0 ? fuelPct / 100f * tankL : Float.NaN; }
        float range() {
            float l = fuelL(), c = !Float.isNaN(avg) ? avg : Float.NaN;
            return !Float.isNaN(l) && !Float.isNaN(c) && c > 0.5f ? l / c * 100f : Float.NaN;
        }
    }

    static final Data D = new Data();

    final String style;
    final float s;
    final int accent;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    Cluster(Context c, String style, float s, int accent) {
        super(c);
        this.style = style; this.s = s; this.accent = accent;
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // sombras de texto y degradados en arcos también en Android 4
        fonts(c);
    }

    // ── letras (OFL, en assets/fonts) ──
    private static Typeface FB, FM, XBI, XMI, IL, IR;

    private static synchronized void fonts(Context c) {
        if (FB != null) return;
        FB = font(c, "barlowc_sb", Typeface.create("sans-serif-condensed", Typeface.BOLD));
        FM = font(c, "barlowc_m", Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        XBI = font(c, "exo2_xbi", Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
        XMI = font(c, "exo2_mi", Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC));
        IL = font(c, "inter_l", Typeface.create("sans-serif-light", Typeface.NORMAL));
        IR = font(c, "inter_r", Typeface.SANS_SERIF);
    }

    private static Typeface font(Context c, String n, Typeface fb) {
        try { return Typeface.createFromAsset(c.getAssets(), "fonts/" + n + ".ttf"); } catch (Throwable e) { return fb; }
    }

    private static final int TX = 0xFFF2F2F2, MU = 0xFF7E848C, RED = 0xFFE5463B;

    private void text(Canvas c, String t, float x, float y, float size, Typeface tf, int color, Paint.Align al, float spacing) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(al);
        if (android.os.Build.VERSION.SDK_INT >= 21) p.setLetterSpacing(spacing);
        c.drawText(t, x, y, p);
        if (android.os.Build.VERSION.SDK_INT >= 21) p.setLetterSpacing(0);
    }

    private float width(String t, float size, Typeface tf) {
        p.setTypeface(tf); p.setTextSize(size);
        return p.measureText(t);
    }

    private static String fmt(float v, int dec) {
        if (Float.isNaN(v)) return "—";
        return dec == 0 ? String.valueOf(Math.round(v)) : String.format(I18n.num, "%." + dec + "f", v);
    }

    private static String up(String es) { return I18n.t(es).toUpperCase(I18n.loc); }

    /** Celdas de la fila de abajo: solo con datos. */
    private List<String[]> cells(boolean withClock) {
        List<String[]> l = new ArrayList<String[]>();
        Data d = D;
        if (!Float.isNaN(d.cool)) l.add(new String[]{"Refrigerante", fmt(d.cool, 0), "°C", "c"});
        if (!Float.isNaN(d.intake)) l.add(new String[]{"Admisión", fmt(d.intake, 0), "°C", ""});
        if (!Float.isNaN(d.volt)) l.add(new String[]{"Batería", fmt(d.volt, 1), "V", ""});
        if (!Float.isNaN(d.cons)) l.add(new String[]{"Consumo", fmt(d.cons, 1), "L/100", ""});
        if (!Float.isNaN(d.range())) l.add(new String[]{"Autonomía", fmt(d.range(), 0), d.unitDist, ""});
        if (withClock) l.add(new String[]{"Hora", d.clock, "", ""});
        return l;
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        boolean compact = w < 560 * s || h < 300 * s;
        if (TELE.equals(style)) { if (compact) teleSmall(c, w, h); else tele(c, w, h); }
        else if (SPORT.equals(style)) { if (compact) sportSmall(c, w, h); else sport(c, w, h); }
        else { if (compact) classicSmall(c, w, h); else classic(c, w, h); }
    }

    // ══════════ Telemetría ══════════

    private void rpmBar(Canvas c, float x, float y, float w, float h, boolean labels) {
        Data d = D;
        int n = 44;
        float gap = Math.max(1.5f, w * 0.0045f), sw = (w - gap * (n - 1)) / n;
        float frac = Float.isNaN(d.rpm) ? 0 : Math.min(1, d.rpm / d.rpmMax);
        int red = Math.round(d.redFrom / d.rpmMax * n);
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1), bh = h * (0.42f + 0.58f * t), bx = x + i * (sw + gap), by = y + h - bh;
            boolean on = (i + 0.5f) / n <= frac, r = i >= red;
            p.setShader(null);
            p.setColor(on ? (r ? RED : 0xFFF4F4F4) : (r ? 0xFF4A1A16 : 0xFF23262B));
            path.reset();
            float sk = sw * 0.35f;
            path.moveTo(bx + sk, by); path.lineTo(bx + sw + sk, by); path.lineTo(bx + sw, y + h); path.lineTo(bx, y + h); path.close();
            c.drawPath(path, p);
        }
        p.setColor(0xFF2E3238);
        c.drawRect(x, y + h + 3 * s, x + w, y + h + 3 * s + Math.max(1, s), p);
        if (!labels) return;
        int k = Math.round(d.rpmMax / 1000);
        for (int i = 1; i <= k; i++) {
            float lx = x + w * i / k - (i == k ? 2 * s : 0);
            boolean r = i * 1000 >= d.redFrom;
            p.setColor(r ? RED : 0xFF5C626A);
            c.drawRect(lx - 0.5f * s, y + h + 5 * s, lx + 0.5f * s, y + h + 10 * s, p);
            text(c, String.valueOf(i), lx, y + h + 26 * s, 15 * s, FM, r ? RED : 0xFFB8BEC6, Paint.Align.CENTER, 0);
        }
        text(c, "×1000", x, y + h + 26 * s, 11 * s, FM, 0xFF5C626A, Paint.Align.LEFT, 0);
    }

    private void miniBar(Canvas c, float x, float y, float w, String label, float v, int color) {
        text(c, up(label), x, y, 12 * s, FM, MU, Paint.Align.LEFT, 0.12f);
        text(c, fmt(v, 0) + " %", x + w, y, 14 * s, FB, TX, Paint.Align.RIGHT, 0);
        p.setColor(0xFF2A2E34);
        c.drawRect(x, y + 7 * s, x + w, y + 9 * s, p);
        p.setColor(color);
        c.drawRect(x, y + 7 * s, x + w * Math.max(0, Math.min(1, v / 100f)), y + 9 * s, p);
    }

    private void bigWithUnit(Canvas c, String num, String unit, float x, float base, float size, Typeface tf, Typeface uf, int col, int ucol, Paint.Align al) {
        float us = Math.max(size * 0.2f, Math.min(size * 0.36f, 12 * s));
        float nw = width(num, size, tf), uw = unit.length() > 0 ? width(unit, us, uf) + size * 0.06f : 0;
        float start = al == Paint.Align.LEFT ? x : al == Paint.Align.RIGHT ? x - nw - uw : x - (nw + uw) / 2;
        text(c, num, start, base, size, tf, col, Paint.Align.LEFT, 0);
        if (uw > 0) text(c, unit, start + nw + size * 0.06f, base, us, uf, ucol, Paint.Align.LEFT, 0.02f);
    }

    private void tele(Canvas c, float w, float h) {
        Data d = D;
        float k = Math.min(w / 800f, h / 480f) * 1f, px = 26 * k;
        // fondo con viñeta muy suave
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF0F1215, 0xFF0A0C0E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        boolean obd = d.obd;
        float barY = 18 * k, barH = 50 * k;
        if (obd) rpmBar(c, px, barY, w - 2 * px, barH, true);
        float top = obd ? barY + barH + 52 * k : 30 * k;
        float colW = (w - 2 * px) / 3;
        // izquierda: revoluciones con clapeta y carga
        if (obd && !Float.isNaN(d.rpm)) {
            text(c, up("Revoluciones"), px, top + 8 * k, 12 * k, FM, MU, Paint.Align.LEFT, 0.16f);
            bigWithUnit(c, fmt(d.rpm, 0), "rpm", px, top + 82 * k, 88 * k, FB, FM, TX, MU, Paint.Align.LEFT);
            float by = top + 118 * k;
            if (!Float.isNaN(d.thr)) { miniBar(c, px, by, colW * 0.88f, "Acelerador", d.thr, 0xFFFFA31A); by += 32 * k; }
            if (!Float.isNaN(d.load)) miniBar(c, px, by, colW * 0.88f, "Carga", d.load, 0xFFE8E8E8);
            p.setColor(0xFF2A2E34);
            c.drawRect(px + colW - 1, top, px + colW, top + 160 * k, p);
            c.drawRect(px + 2 * colW - 1, top, px + 2 * colW, top + 160 * k, p);
        }
        // centro: la velocidad (donde la foto lleva la marcha: el OBD no la entrega)
        float cx = obd && !Float.isNaN(d.rpm) ? px + colW * 1.5f : w * 0.36f;
        String sp = Float.isNaN(d.speed) ? "--" : fmt(d.speed, 0);
        float ss = 170 * k;
        while (width(sp, ss, FB) > colW * 0.95f && ss > 20) ss *= 0.92f;
        text(c, sp, cx, top + 150 * k, ss, FB, TX, Paint.Align.CENTER, 0);
        text(c, (d.mph ? "MPH" : "KM/H") + " · " + (d.speedObd ? "OBD" : "GPS"), cx, top + 176 * k, 13 * k, FM, MU, Paint.Align.CENTER, 0.2f);
        // derecha: consumo instantáneo y la hora
        float rx = w - px;
        text(c, up("Hora"), rx, top + 8 * k, 12 * k, FM, MU, Paint.Align.RIGHT, 0.16f);
        text(c, d.clock, rx, top + 70 * k, 62 * k, FB, TX, Paint.Align.RIGHT, 0);
        if (!Float.isNaN(d.amb)) text(c, fmt(d.amb, 0) + " °C " + I18n.t("afuera"), rx, top + 96 * k, 15 * k, FM, MU, Paint.Align.RIGHT, 0.05f);
        if (!Float.isNaN(d.cons)) {
            text(c, up("Consumo"), rx, top + 126 * k, 12 * k, FM, MU, Paint.Align.RIGHT, 0.16f);
            bigWithUnit(c, fmt(d.cons, 1), "L/100", rx, top + 168 * k, 44 * k, FB, FM, TX, MU, Paint.Align.RIGHT);
        }
        // gráfico de los últimos 60 s
        float gy = top + 206 * k, gh = Math.max(40 * k, h - gy - 112 * k);
        if (gh > 30 * k && d.hN > 1) {
            if (obd && !Float.isNaN(d.thr)) { p.setColor(0xFFFFA31A); c.drawRect(px, gy - 9 * k, px + 10 * k, gy - 6 * k, p); }
            float lx = px + 16 * k;
            if (obd && !Float.isNaN(d.thr)) {
                String lt = up("Acelerador") + " %";
                text(c, lt, lx, gy - 3 * k, 11 * k, FM, MU, Paint.Align.LEFT, 0.14f);
                lx += width(lt, 11 * k, FM) * 1.18f + 30 * k;
            } else lx = px + 16 * k;
            p.setColor(TX); c.drawRect(lx - 16 * k, gy - 9 * k, lx - 6 * k, gy - 6 * k, p);
            text(c, up("Velocidad"), lx, gy - 3 * k, 11 * k, FM, MU, Paint.Align.LEFT, 0.14f);
            text(c, I18n.t("últimos 60 s").toUpperCase(I18n.loc), w - px, gy - 3 * k, 11 * k, FM, 0xFF5C626A, Paint.Align.RIGHT, 0.14f);
            float y0 = gy + 8 * k, y1 = y0 + gh;
            p.setColor(0xFF1E2227);
            for (int i = 0; i <= 8; i++) { float xx = px + (w - 2 * px) * i / 8f; c.drawRect(xx, y0, xx + Math.max(1, s * 0.6f), y1, p); }
            c.drawRect(px, y1, w - px, y1 + Math.max(1, s), p);
            float mx = 40;
            for (int i = 0; i < d.hN; i++) mx = Math.max(mx, d.hSpeed[i]);
            mx = (float) Math.ceil(mx / 20f) * 20f;
            line(c, d.hSpeed, d.hN, px, y0, w - 2 * px, y1 - y0, mx, TX, 2.2f * k, false);
            if (obd) line(c, d.hThr, d.hN, px, y0, w - 2 * px, y1 - y0, 100, 0xFFFFA31A, 2 * k, true);
            if (d.hN > 0) {
                float yy = y1 - (y1 - y0) * Math.min(1, d.hSpeed[d.hN - 1] / mx);
                p.setColor(TX); c.drawCircle(w - px - (w - 2 * px) * (60 - d.hN) / 59f, yy, 4 * k, p);
            }
        }
        // fila de abajo
        List<String[]> cl = cells(false);
        if (!cl.isEmpty()) {
            float ry = h - 70 * k, cw = (w - 2 * px) / Math.max(cl.size(), 4);
            p.setColor(0xFF2A2E34); c.drawRect(px, ry - 14 * k, w - px, ry - 13 * k, p);
            for (int i = 0; i < cl.size(); i++) {
                String[] x = cl.get(i);
                float cx2 = px + i * cw + (i > 0 ? 14 * k : 0);
                if (i > 0) { p.setColor(0xFF2A2E34); c.drawRect(px + i * cw, ry - 2 * k, px + i * cw + Math.max(1, s), ry + 46 * k, p); }
                text(c, up(x[0]), cx2, ry + 10 * k, 12 * k, FM, MU, Paint.Align.LEFT, 0.14f);
                bigWithUnit(c, x[1], x[2], cx2, ry + 48 * k, 36 * k, FB, FM, TX, MU, Paint.Align.LEFT);
                if ("c".equals(x[3])) {
                    float f = Math.max(0, Math.min(1, (D.cool - 40) / 90f));
                    p.setColor(0xFF2A2E34); c.drawRect(cx2, ry + 56 * k, cx2 + cw * 0.7f, ry + 58 * k, p);
                    p.setColor(D.cool > 105 ? RED : TX); c.drawRect(cx2, ry + 56 * k, cx2 + cw * 0.7f * f, ry + 58 * k, p);
                }
            }
        }
    }

    private void line(Canvas c, float[] v, int n, float x, float y, float w, float h, float max, int color, float sw, boolean fill) {
        if (n < 2) return;
        path.reset();
        boolean started = false;
        float last = x;
        for (int i = 0; i < n; i++) {
            if (v[i] < 0) continue;
            float xx = x + w - w * (n - 1 - i) / 59f, yy = y + h - h * Math.min(1, v[i] / max);
            if (!started) { path.moveTo(xx, yy); started = true; } else path.lineTo(xx, yy);
            last = xx;
        }
        if (!started) return;
        p.setShader(null);
        if (fill) {
            Path f = new Path(path);
            f.lineTo(last, y + h); f.lineTo(x + w - w * (n - 1) / 59f, y + h); f.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, y, 0, y + h, Show.alpha(color, 0x55), Show.alpha(color, 0x08), Shader.TileMode.CLAMP));
            c.drawPath(f, p);
            p.setShader(null);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void teleSmall(Canvas c, float w, float h) {
        Data d = D;
        float k = Math.min(w / 320f, h / 220f), px = 6 * k;
        boolean obd = d.obd;
        float y = 4 * k;
        if (obd) { rpmBar(c, px, y, w - 2 * px, 26 * k, false); y += 42 * k; }
        String sp = Float.isNaN(d.speed) ? "--" : fmt(d.speed, 0);
        float ss = 96 * k;
        text(c, sp, px, y + 78 * k, ss, FB, TX, Paint.Align.LEFT, 0);
        float nw = width(sp, ss, FB);
        text(c, d.mph ? "MPH" : "KM/H", px + nw + 6 * k, y + 76 * k, 14 * k, FM, MU, Paint.Align.LEFT, 0.12f);
        if (obd && !Float.isNaN(d.rpm)) {
            text(c, up("Revoluciones"), w - px, y + 18 * k, 11 * k, FM, MU, Paint.Align.RIGHT, 0.14f);
            text(c, fmt(d.rpm, 0), w - px, y + 52 * k, 36 * k, FB, TX, Paint.Align.RIGHT, 0);
        }
        List<String[]> cl = cells(true);
        int n = Math.min(3, cl.size());
        if (n == 0) return;
        float ry = h - 44 * k, cw = (w - 2 * px) / n;
        p.setColor(0xFF2A2E34); c.drawRect(px, ry - 10 * k, w - px, ry - 9 * k, p);
        for (int i = 0; i < n; i++) {
            String[] x = cl.get(i);
            float cx = px + i * cw + (i > 0 ? 10 * k : 0);
            if (i > 0) { p.setColor(0xFF2A2E34); c.drawRect(px + i * cw, ry - 2 * k, px + i * cw + Math.max(1, s), ry + 36 * k, p); }
            text(c, up(x[0]), cx, ry + 6 * k, 10 * k, FM, MU, Paint.Align.LEFT, 0.12f);
            bigWithUnit(c, x[1], x[2], cx, ry + 36 * k, 26 * k, FB, FM, TX, MU, Paint.Align.LEFT);
        }
    }

    // ══════════ Sport ══════════

    private static final int MAG = 0xFFB4228C, ORA = 0xFFFF6A2B, CYAN = 0xFF3DD1FF;

    /** Arco segmentado de revoluciones: centro (cx, cy), radio r, de a0 a a1 grados. */
    private void sportArc(Canvas c, float cx, float cy, float r, float a0, float a1, float thick, boolean labels, float k) {
        Data d = D;
        int n = 64;
        float frac = Float.isNaN(d.rpm) ? 0 : Math.min(1, d.rpm / d.rpmMax);
        float span = a1 - a0, seg = span / n;
        int red = Math.round(d.redFrom / d.rpmMax * n);
        rf.set(cx - r, cy - r, cx + r, cy + r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(thick);
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n;
            boolean on = t <= frac;
            int col;
            if (on) col = i >= red ? RED : Show.mix(MAG, ORA, Math.min(1, t / Math.max(0.05f, frac)));
            else col = i >= red ? 0xFF3A1420 : 0xFF241E2A;
            p.setColor(col);
            c.drawArc(rf, a0 + i * seg + seg * 0.12f, seg * 0.76f, false, p);
        }
        // borde fino exterior y marcas
        p.setStrokeWidth(Math.max(1, 1.2f * k));
        p.setColor(0x553A3448);
        float ro = r + thick / 2 + 8 * k;
        rf.set(cx - ro, cy - ro, cx + ro, cy + ro);
        c.drawArc(rf, a0, span, false, p);
        int kk = Math.round(d.rpmMax / 1000);
        for (int i = 0; i <= kk; i++) {
            double a = Math.toRadians(a0 + span * i / kk);
            boolean rr = i * 1000 >= d.redFrom;
            p.setColor(rr ? RED : 0xFFDADADA);
            p.setStrokeWidth(2 * k);
            float r1 = ro, r2 = ro + 12 * k;
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
            if (labels) {
                float rl = r - thick / 2 - 26 * k;
                p.setStyle(Paint.Style.FILL);
                text(c, String.valueOf(i), cx + (float) Math.cos(a) * rl, cy + (float) Math.sin(a) * rl + 9 * k, 24 * k, XBI, rr ? RED : TX, Paint.Align.CENTER, 0);
                p.setStyle(Paint.Style.STROKE);
            }
        }
        // aguja
        if (!Float.isNaN(d.rpm)) {
            double a = Math.toRadians(a0 + span * frac);
            float r1 = r - thick / 2 - 6 * k, r2 = r + thick / 2 + 14 * k;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Show.alpha(ORA, 0x66));
            p.setStrokeWidth(10 * k);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
            p.setColor(0xFFFFE2D0);
            p.setStrokeWidth(3.5f * k);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
            p.setStrokeCap(Paint.Cap.BUTT);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void sportBg(Canvas c, float w, float h) {
        p.setShader(new RadialGradient(w * 0.45f, h * 0.62f, Math.max(w, h) * 0.7f, new int[]{0xFF241029, 0xFF120A16, 0xFF08070A},
            new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void sport(Canvas c, float w, float h) {
        Data d = D;
        float k = Math.min(w / 800f, h / 480f), px = 24 * k;
        sportBg(c, w, h);
        if (d.obd) {
            float r = Math.max(w, h * 1.4f) * 0.62f, cx = w * 0.56f, cy = h * 0.40f + r * 0.92f;
            float half = (float) Math.toDegrees(Math.asin(Math.min(1, (w * 0.58f) / r)));
            sportArc(c, cx, cy, r, 270 - half, 270 + half, 26 * k, true, k);
        }
        text(c, d.clock, px, 46 * k, 40 * k, XMI, TX, Paint.Align.LEFT, 0);
        if (!Float.isNaN(d.amb)) text(c, fmt(d.amb, 0) + " °C  " + up("afuera"), px, 72 * k, 14 * k, XMI, MU, Paint.Align.LEFT, 0.16f);
        // columna derecha: solo lo que hay
        float ry = 44 * k;
        String[][] right = {
            {"Refrigerante", fmt(d.cool, 0), "°C"}, {"Consumo", fmt(d.cons, 1), "L/100"}, {"Autonomía", fmt(d.range(), 0), d.unitDist}};
        float[] vals = {d.cool, d.cons, d.range()};
        for (int i = 0; i < 3; i++) {
            if (Float.isNaN(vals[i])) continue;
            float vw = width(right[i][1], 34 * k, XBI);
            text(c, right[i][2], w - px, ry, 13 * k, XMI, MU, Paint.Align.RIGHT, 0);
            text(c, right[i][1], w - px - width(right[i][2], 13 * k, XMI) - 6 * k, ry, 34 * k, XBI, TX, Paint.Align.RIGHT, 0);
            text(c, up(right[i][0]), w - px, ry + 18 * k, 12 * k, XMI, MU, Paint.Align.RIGHT, 0.2f);
            p.setColor(0x223A3448); c.drawRect(w - px - vw - 70 * k, ry + 26 * k, w - px, ry + 27 * k, p);
            ry += 58 * k;
        }
        // velocidad
        String sp = Float.isNaN(d.speed) ? "--" : fmt(d.speed, 0);
        float ss = 190 * k, sx = w * 0.43f, sb = h * 0.78f;
        p.setShadowLayer(18 * k, 0, 0, 0x55FF6A2B);
        text(c, sp, sx, sb, ss, XBI, 0xFFF7F2EE, Paint.Align.RIGHT, 0);
        p.clearShadowLayer();
        float ux = sx + 18 * k;
        if (!Float.isNaN(d.rpm)) {
            text(c, fmt(d.rpm, 0), ux, sb - 60 * k, 30 * k, XBI, 0xFFF1B79E, Paint.Align.LEFT, 0);
            text(c, "RPM", ux + width(fmt(d.rpm, 0), 30 * k, XBI) + 8 * k, sb - 60 * k, 13 * k, XMI, MU, Paint.Align.LEFT, 0.16f);
        }
        text(c, d.mph ? "MPH" : "KM/H", ux, sb - 26 * k, 24 * k, XBI, CYAN, Paint.Align.LEFT, 0.06f);
        p.setShader(new LinearGradient(sx - 240 * k, 0, sx + 140 * k, 0, new int[]{0, ORA, 0}, null, Shader.TileMode.CLAMP));
        c.drawRect(sx - 240 * k, sb + 14 * k, sx + 140 * k, sb + 16 * k, p);
        p.setShader(null);
    }

    private void sportSmall(Canvas c, float w, float h) {
        Data d = D;
        float k = Math.min(w / 320f, h / 220f);
        if (d.obd) {
            float r = w * 0.78f, cx = w * 0.5f, cy = h * 0.30f + r;
            float half = (float) Math.toDegrees(Math.asin(Math.min(1, (w * 0.47f) / r)));
            sportArc(c, cx, cy, r, 270 - half, 270 + half, 12 * k, false, k * 0.8f);
        }
        String sp = Float.isNaN(d.speed) ? "--" : fmt(d.speed, 0);
        float sb = h * 0.80f, sx = w * 0.56f;
        p.setShadowLayer(10 * k, 0, 0, 0x55FF6A2B);
        text(c, sp, sx, sb, 104 * k, XBI, 0xFFF7F2EE, Paint.Align.RIGHT, 0);
        p.clearShadowLayer();
        if (!Float.isNaN(d.rpm)) text(c, fmt(d.rpm, 0) + " RPM", sx + 10 * k, sb - 36 * k, 15 * k, XBI, 0xFFF1B79E, Paint.Align.LEFT, 0);
        text(c, d.mph ? "MPH" : "KM/H", sx + 10 * k, sb - 12 * k, 15 * k, XBI, CYAN, Paint.Align.LEFT, 0.06f);
        if (!Float.isNaN(d.cool)) {
            text(c, fmt(d.cool, 0) + "°", w - 6 * k, 26 * k, 22 * k, XBI, TX, Paint.Align.RIGHT, 0);
            text(c, up("Refrigerante"), w - 6 * k, 40 * k, 9 * k, XMI, MU, Paint.Align.RIGHT, 0.16f);
        }
    }

    // ══════════ Clásico ══════════

    private static final int CREAM = 0xFFF3E6C8, AMBER = 0xFFE3B35C, AMBER_D = 0xFF8A6A33;

    private void dial(Canvas c, float cx, float cy, float r, float k, boolean full) {
        Data d = D;
        // anillo exterior
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, 1.4f * k));
        p.setColor(0xFF3A3A3A);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xFF242424);
        c.drawCircle(cx, cy, r - 8 * k, p);
        float a0 = 135, span = 270;
        int kk = Math.round(d.rpmMax / 1000);
        // escala: mayores, medias y menores
        for (int i = 0; i <= kk * 10; i++) {
            float val = i * 100f;
            double a = Math.toRadians(a0 + span * val / d.rpmMax);
            boolean major = i % 10 == 0, half = i % 5 == 0;
            boolean rr = val >= d.redFrom;
            float r1 = r - (major ? 30 : half ? 22 : 16) * k, r2 = r - 10 * k;
            p.setStrokeWidth((major ? 2.6f : 1.1f) * k);
            p.setColor(rr ? (major ? RED : 0xFF7A2A24) : (major ? 0xFFE8E2D6 : 0xFF6A665E));
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
            if (major && full) {
                float rl = r - 56 * k;
                p.setStyle(Paint.Style.FILL);
                text(c, String.valueOf(i / 10), cx + (float) Math.cos(a) * rl, cy + (float) Math.sin(a) * rl + 9 * k, 26 * k, IL, rr ? RED : 0xFFE8E2D6, Paint.Align.CENTER, 0);
                p.setStyle(Paint.Style.STROKE);
            }
        }
        // arco rojo interior desde la zona roja
        float rr2 = r - 36 * k;
        rf.set(cx - rr2, cy - rr2, cx + rr2, cy + rr2);
        p.setStrokeWidth(2 * k);
        p.setColor(RED);
        float ra = a0 + span * d.redFrom / d.rpmMax;
        c.drawArc(rf, ra, a0 + span - ra, false, p);
        // arco ámbar hasta las revoluciones y la aguja
        if (d.obd && !Float.isNaN(d.rpm)) {
            float f = Math.min(1, d.rpm / d.rpmMax);
            float ar = r - 20 * k;
            rf.set(cx - ar, cy - ar, cx + ar, cy + ar);
            p.setStrokeWidth(5 * k);
            p.setColor(Show.alpha(AMBER, 0xB0));
            c.drawArc(rf, a0, span * f, false, p);
            double a = Math.toRadians(a0 + span * f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(4 * k);
            p.setColor(CREAM);
            float n1 = r * 0.30f, n2 = r - 12 * k;
            c.drawLine(cx + (float) Math.cos(a) * n1, cy + (float) Math.sin(a) * n1, cx + (float) Math.cos(a) * n2, cy + (float) Math.sin(a) * n2, p);
            p.setStrokeCap(Paint.Cap.BUTT);
        }
        p.setStyle(Paint.Style.FILL);
        if (full) text(c, "× 1000 rpm", cx, cy - r * 0.34f, 13 * k, IR, 0xFF8C8679, Paint.Align.CENTER, 0.04f);
        String sp = Float.isNaN(d.speed) ? "--" : fmt(d.speed, 0);
        float ss = r * 0.52f;
        text(c, sp, cx, cy + ss * 0.34f, ss, IL, 0xFFF6EFE2, Paint.Align.CENTER, -0.02f);
        text(c, d.mph ? "MPH" : "KM/H", cx, cy + ss * 0.34f + 26 * k, 13 * k, IR, AMBER, Paint.Align.CENTER, 0.25f);
    }

    /** Arco lateral con punto: a la izquierda (140° abajo → 220° arriba) o a la derecha (40° abajo → −40° arriba). */
    private void sideArc(Canvas c, float cx, float cy, float r, boolean left, float frac, float k, String hi, String lo, int warnCol,
                         float warnFrom, boolean warnHigh) {
        float base = left ? 140 : -40;
        rf.set(cx - r, cy - r, cx + r, cy + r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * k);
        p.setColor(0xFF4A4740);
        c.drawArc(rf, base, 80, false, p);
        p.setColor(warnCol);
        if (left) { if (warnHigh) c.drawArc(rf, 140 + 80 * warnFrom, 80 * (1 - warnFrom), false, p); else c.drawArc(rf, 140, 80 * warnFrom, false, p); }
        else { if (warnHigh) c.drawArc(rf, -40, 80 * (1 - warnFrom), false, p); else c.drawArc(rf, 40 - 80 * warnFrom, 80 * warnFrom, false, p); }
        p.setStyle(Paint.Style.FILL);
        if (!Float.isNaN(frac)) {
            float f = Math.max(0, Math.min(1, frac));
            double a = Math.toRadians(left ? 140 + 80 * f : 40 - 80 * f);
            p.setColor(0xFFF6EFE2);
            c.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 5 * k, p);
        }
        double at = Math.toRadians(left ? 220 : -40), ab = Math.toRadians(left ? 140 : 40);
        float off = (left ? -10 : 10) * k;
        Paint.Align al = left ? Paint.Align.RIGHT : Paint.Align.LEFT;
        text(c, hi, cx + (float) Math.cos(at) * r + off, cy + (float) Math.sin(at) * r, 11 * k, IR, 0xFF8C8679, al, 0);
        text(c, lo, cx + (float) Math.cos(ab) * r + off, cy + (float) Math.sin(ab) * r + 10 * k, 11 * k, IR, 0xFF8C8679, al, 0);
    }

    private void classic(Canvas c, float w, float h) {
        Data d = D;
        float k = Math.min(w / 800f, h / 480f), px = 30 * k;
        p.setShader(new RadialGradient(w / 2, h * 0.5f, Math.max(w, h) * 0.65f, 0xFF1A1917, 0xFF0C0C0B, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float cx = w / 2, cy = h * 0.53f, r = Math.min(h * 0.44f, w * 0.27f);
        dial(c, cx, cy, r, k, true);
        // arcos laterales
        float sr = r + 46 * k;
        if (!Float.isNaN(d.cool)) {
            sideArc(c, cx, cy, sr, true, (d.cool - 50) / 80f, k, "130", "50", RED, 0.8f, true);
            float lx = cx - sr - 30 * k;
            text(c, fmt(d.cool, 0) + "°", lx, cy + 6 * k, 28 * k, IL, 0xFFF6EFE2, Paint.Align.RIGHT, 0);
            text(c, up("Refrigerante"), lx, cy + 28 * k, 11 * k, IR, 0xFF8C8679, Paint.Align.RIGHT, 0.2f);
        }
        float fl = d.fuelL();
        if (!Float.isNaN(d.fuelPct)) {
            sideArc(c, cx, cy, sr, false, d.fuelPct / 100f, k, "1/1", "0", AMBER_D, 0.15f, false);
            float rx = cx + sr + 30 * k;
            text(c, !Float.isNaN(fl) ? fmt(fl, 1) + " L" : fmt(d.fuelPct, 0) + " %", rx, cy + 6 * k, 28 * k, IL, 0xFFF6EFE2, Paint.Align.LEFT, 0);
            text(c, up("Tanque"), rx, cy + 28 * k, 11 * k, IR, 0xFF8C8679, Paint.Align.LEFT, 0.2f);
        }
        // esquinas
        if (d.obd && !Float.isNaN(d.rpm)) {
            text(c, fmt(d.rpm, 0), px, 64 * k, 52 * k, IL, 0xFFF6EFE2, Paint.Align.LEFT, -0.02f);
            text(c, "rpm", px, 86 * k, 14 * k, IR, 0xFF8C8679, Paint.Align.LEFT, 0);
        }
        text(c, d.clock, w - px, 64 * k, 52 * k, IL, 0xFFF6EFE2, Paint.Align.RIGHT, -0.02f);
        if (!Float.isNaN(d.amb)) text(c, fmt(d.amb, 0) + " °C " + I18n.t("afuera"), w - px, 86 * k, 14 * k, IR, 0xFF8C8679, Paint.Align.RIGHT, 0);
        if (!Float.isNaN(d.cons)) {
            text(c, up("Consumo"), px, h - 92 * k, 11 * k, IR, 0xFF8C8679, Paint.Align.LEFT, 0.2f);
            bigWithUnit(c, fmt(d.cons, 1), "L/100", px, h - 42 * k, 52 * k, IL, IR, 0xFFF6EFE2, 0xFF8C8679, Paint.Align.LEFT);
            StringBuilder sub = new StringBuilder();
            if (!Float.isNaN(d.avg)) sub.append(I18n.t("medio")).append(' ').append(fmt(d.avg, 1)).append(" L/100");
            if (!Float.isNaN(d.range())) sub.append(sub.length() > 0 ? "  ·  " : "").append(I18n.t("autonomía")).append(' ').append(fmt(d.range(), 0)).append(' ').append(d.unitDist);
            if (sub.length() > 0) text(c, sub.toString(), px, h - 18 * k, 13 * k, IR, 0xFF8C8679, Paint.Align.LEFT, 0);
        }
        if (!Float.isNaN(d.volt)) {
            text(c, up("Batería"), w - px, h - 92 * k, 11 * k, IR, 0xFF8C8679, Paint.Align.RIGHT, 0.2f);
            bigWithUnit(c, fmt(d.volt, 1), "V", w - px, h - 42 * k, 52 * k, IL, IR, 0xFFF6EFE2, 0xFF8C8679, Paint.Align.RIGHT);
        }
    }

    private void classicSmall(Canvas c, float w, float h) {
        float k = Math.min(w, h) / 300f;
        float r = Math.min(w, h) / 2 - 8 * k;
        dial(c, w / 2, h / 2, r, k * 0.9f, r > 110 * s);
    }
}
