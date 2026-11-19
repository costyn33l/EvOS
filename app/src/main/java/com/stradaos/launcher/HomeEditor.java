package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Editor táctil del inicio (2.24). Todo se ve como bloques y se ajusta con el dedo, dentro de límites en unidades del
 * lienzo de StradaOS (iguales en cualquier resolución):
 * - manija entre dos columnas: cambia el ancho; los vecinos se ajustan en vivo (Main.CAR_MIN…);
 * - arrastrar una columna: la cambia de lugar y las demás se corren;
 * - manija horizontal entre el centro y la franja de widgets: cambia el alto de la franja (HomeStrip.H_MIN…H_MAX);
 * - manijas entre widgets: cambian sus anchos (cada uno al menos HomeStrip.MIN_W);
 * - tocar un widget: elegir qué muestra (o quitarlo); «+»: agregar uno si cabe.
 * Mientras se arrastra solo se dibujan rectángulos; el inicio de verdad se rearma una vez, con «Listo».
 */
public class HomeEditor extends View {

    public interface Cb {
        void onDone(List<String> order, float carW, float playerW, boolean strip, float stripH, List<String> kinds, float[] weights);
        void onReset();
        void onCancel();
        /** Elegir qué muestra el widget i; null = quitarlo. */
        void pickWidget(int i, String current, boolean canRemove, WidgetCb cb);
        String widgetName(String kind);
    }

    public interface WidgetCb { void on(String kind); }

    private final float k;
    private final float x0, avail, top, h;
    private final List<String> order;
    private final HashMap<String, String> names;
    private final boolean hasCar;
    private final Cb cb;
    private float car, player;
    // franja de widgets (debajo de la columna del centro)
    private boolean strip;
    private float stripH;
    private final List<String> kinds;
    private final List<Float> weights = new ArrayList<Float>();

    // arrastre en curso
    private static final int NONE = 0, COL_HANDLE = 1, COL_MOVE = 2, STRIP_H = 3, W_HANDLE = 4, W_TAP = 5, ADD_STRIP = 6, ADD_W = 7;
    private int mode = NONE, index;
    private String dragCol;
    private float downX, downY, startA, startB, grabDx, floatX;
    private boolean atLimit, moved;
    private final RectF r = new RectF(), addBtn = new RectF(), addStripBtn = new RectF();
    private final RectF[] buttons = {new RectF(), new RectF(), new RectF()};
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HomeEditor(Context c, float scale, float x0, float avail, float top, float h, List<String> order,
                      float car, float player, boolean hasCar, HashMap<String, String> names,
                      boolean strip, float stripH, List<String> kinds, float[] w, Cb cb) {
        super(c);
        k = scale;
        this.x0 = x0; this.avail = avail; this.top = top; this.h = h;
        this.order = new ArrayList<String>(order);
        this.car = car; this.player = player; this.hasCar = hasCar;
        this.names = names;
        this.strip = strip;
        this.stripH = stripH;
        this.kinds = new ArrayList<String>(kinds);
        for (int i = 0; i < kinds.size(); i++) weights.add(i < w.length ? w[i] : 1f);
        this.cb = cb;
        stroke.setStyle(Paint.Style.STROKE);
        setClickable(true);
    }

    private float pMin() { return order.contains("car") ? Main.PLAYER_MIN : 300; }

    /** ✕ de cada tarjeta que se puede ocultar y los «+» de las ocultas (coordenadas en px). */
    private final HashMap<String, RectF> closeBtns = new HashMap<String, RectF>(), addBtns = new HashMap<String, RectF>();

    private float width(String col) {
        if ("car".equals(col)) return car;
        if ("player".equals(col)) return player;
        return centerW();
    }

    private float centerW() {
        float used = 0;
        for (String c : order) if (!"map".equals(c)) used += width(c);
        return avail - used - 12 * (order.size() - 1);
    }

    /** El centro no puede quedar más angosto que su mínimo ni que lo que piden los widgets de la franja. */
    private float centerMin() {
        if (!strip || !order.contains("map")) return Main.CENTER_MIN;
        int n = kinds.size();
        return Math.max(Main.CENTER_MIN, n * HomeStrip.MIN_W + (n - 1) * HomeStrip.GAP);
    }

    private HashMap<String, Float> xs(List<String> o) {
        HashMap<String, Float> m = new HashMap<String, Float>();
        float x = x0;
        for (String c : o) { m.put(c, x); x += width(c) + 12; }
        return m;
    }

    private float stripTop() { return top + h - stripH; }

    /** X y ancho de cada widget de la franja (unidades). */
    private float[][] widgetRects(float cx, float cw) {
        int n = kinds.size();
        float[] wd = HomeStrip.widths(cw - (n - 1) * HomeStrip.GAP, arr(), n);
        float[][] out = new float[n][2];
        float x = cx;
        for (int i = 0; i < n; i++) { out[i][0] = x; out[i][1] = wd[i]; x += wd[i] + HomeStrip.GAP; }
        return out;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() / k, y = e.getY() / k;
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            moved = false;
            downX = x; downY = y;
            for (int i = 0; i < 3; i++) if (buttons[i].contains(e.getX(), e.getY())) {
                if (i == 0) cb.onDone(order, car, player, strip, stripH, kinds, arr()); else if (i == 1) cb.onReset(); else cb.onCancel();
                return true;
            }
            // ocultar una tarjeta (las demás se corren y el centro gana el espacio) o volver a mostrarla
            for (java.util.Map.Entry<String, RectF> en : closeBtns.entrySet()) if (en.getValue().contains(e.getX(), e.getY()) && order.size() > 1) {
                order.remove(en.getKey());
                invalidate();
                return true;
            }
            for (java.util.Map.Entry<String, RectF> en : addBtns.entrySet()) if (en.getValue().contains(e.getX(), e.getY())) {
                String c = en.getKey();
                if ("car".equals(c)) { car = Main.CAR_MIN; order.add(0, c); }
                else { player = order.contains("car") ? Main.PLAYER_MIN : 300; order.add(c); }
                invalidate();
                return true;
            }
            HashMap<String, Float> m = xs(order);
            float cx = order.contains("map") ? m.get("map") : -1, cw = centerW();
            if (!strip && addStripBtn.contains(e.getX(), e.getY())) { mode = ADD_STRIP; return true; }
            if (strip && addBtn.contains(e.getX(), e.getY())) { mode = ADD_W; return true; }
            if (y < top - 4 || y > top + h + 4) return true;
            // manijas entre columnas
            for (int i = 0; i + 1 < order.size(); i++) {
                float bx = m.get(order.get(i)) + width(order.get(i)) + 6;
                if (Math.abs(x - bx) < 22) {
                    mode = COL_HANDLE; index = i;
                    String a = order.get(i), b = order.get(i + 1);
                    startA = width(!"map".equals(a) ? a : b);
                    invalidate();
                    return true;
                }
            }
            if (strip && cx >= 0 && x >= cx && x <= cx + cw) {
                // manija del alto de la franja
                if (Math.abs(y - (stripTop() - 6)) < 18) { mode = STRIP_H; startA = stripH; invalidate(); return true; }
                if (y >= stripTop()) {
                    float[][] wr = widgetRects(cx, cw);
                    for (int i = 0; i + 1 < wr.length; i++) {
                        if (Math.abs(x - (wr[i][0] + wr[i][1] + HomeStrip.GAP / 2)) < 16) {
                            mode = W_HANDLE; index = i; startA = wr[i][1]; startB = wr[i + 1][1];
                            invalidate();
                            return true;
                        }
                    }
                    for (int i = 0; i < wr.length; i++) if (x >= wr[i][0] && x <= wr[i][0] + wr[i][1]) { mode = W_TAP; index = i; invalidate(); return true; }
                    return true;
                }
            }
            for (String c : order) {
                float colX = m.get(c);
                if (x >= colX && x <= colX + width(c)) { mode = COL_MOVE; dragCol = c; grabDx = x - colX; floatX = colX; invalidate(); return true; }
            }
            return true;
        }
        if (act == MotionEvent.ACTION_MOVE) {
            if (Math.abs(x - downX) > 6 || Math.abs(y - downY) > 6) moved = true;
            if (mode == COL_HANDLE) {
                String a = order.get(index), b = order.get(index + 1);
                boolean left = !"map".equals(a);
                String t = left ? a : b;
                float want = startA + (left ? 1 : -1) * (x - downX);
                float min = "car".equals(t) ? Main.CAR_MIN : pMin(), max = "car".equals(t) ? Main.CAR_MAX : Main.PLAYER_MAX;
                max = Math.min(max, width(t) + centerW() - centerMin());
                float nw = Math.max(min, Math.min(max, want));
                atLimit = nw != want;
                if ("car".equals(t)) car = nw; else player = nw;
            } else if (mode == COL_MOVE) {
                floatX = x - grabDx;
                List<String> rest = new ArrayList<String>(order);
                rest.remove(dragCol);
                float mid = floatX + width(dragCol) / 2;
                List<String> trial = new ArrayList<String>(rest);
                trial.add(0, dragCol);
                HashMap<String, Float> m = xs(trial);
                int at = 0;
                for (String c : rest) if (mid > m.get(c) + width(c) / 2) at++;
                rest.add(at, dragCol);
                if (!rest.equals(order)) { order.clear(); order.addAll(rest); }
            } else if (mode == STRIP_H) {
                float want = startA - (y - downY);
                float max = Math.min(HomeStrip.H_MAX, h - 12 - HomeStrip.MAP_MIN_H);
                float nh = Math.max(HomeStrip.H_MIN, Math.min(max, want));
                atLimit = nh != want;
                stripH = nh;
            } else if (mode == W_HANDLE) {
                float total = startA + startB, wa = startA + (x - downX);
                float na = Math.max(HomeStrip.MIN_W, Math.min(total - HomeStrip.MIN_W, wa));
                atLimit = na != wa;
                float sum = 0;
                for (float w : weights) sum += w;
                float unit = sum / (centerW() - (kinds.size() - 1) * HomeStrip.GAP);     // peso por unidad de ancho
                float[][] now = widgetRects(0, centerW());
                for (int j = 0; j < weights.size(); j++) weights.set(j, now[j][1] * unit);
                weights.set(index, na * unit);
                weights.set(index + 1, (total - na) * unit);
            }
            invalidate();
            return true;
        }
        if (act == MotionEvent.ACTION_UP) {
            if (mode == W_TAP && !moved) {
                final int i = index;
                cb.pickWidget(i, kinds.get(i), kinds.size() > 1, new WidgetCb() { public void on(String kind) {
                    if (kind == null) { kinds.remove(i); weights.remove(i); }
                    else kinds.set(i, kind);
                    invalidate();
                }});
            } else if (mode == ADD_STRIP && !moved) {
                strip = true;
                if (kinds.isEmpty()) { kinds.add("alt"); weights.add(1f); }
                int n = HomeStrip.fit(centerW(), kinds.size());
                while (kinds.size() > n) { kinds.remove(kinds.size() - 1); weights.remove(weights.size() - 1); }
            } else if (mode == ADD_W && !moved) {
                kinds.add("obd:coolant");
                weights.add(1f);
                final int i = kinds.size() - 1;
                cb.pickWidget(i, kinds.get(i), true, new WidgetCb() { public void on(String kind) {
                    if (kind == null) { kinds.remove(i); weights.remove(i); } else kinds.set(i, kind);
                    invalidate();
                }});
            }
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            mode = NONE;
            dragCol = null;
            atLimit = false;
            invalidate();
        }
        return true;
    }

    private float[] arr() {
        float[] a = new float[weights.size()];
        for (int i = 0; i < a.length; i++) a[i] = weights.get(i);
        return a;
    }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor((Main.BG & 0x00FFFFFF) | 0xF2000000);
        c.drawRect(0, 0, W, H, fill);

        txt.setTypeface(Typeface.DEFAULT);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(13 * k);
        txt.setColor(Main.SOFT);
        c.drawText(I18n.t("Arrastra las manijas para cambiar tamaños · arrastra una tarjeta para moverla · toca un widget para cambiarlo · ✕ oculta una tarjeta"),
            W / 2, (top + h + 34) * k, txt);
        // «+» de las tarjetas ocultas
        addBtns.clear();
        float ax = 18 * k;
        txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(15 * k);
        for (String col : new String[]{"car", "player"}) {
            if (order.contains(col)) continue;
            String t = "+ " + names.get(col);
            float bw = txt.measureText(t) + 30 * k;
            RectF br = new RectF(ax, 12 * k, ax + bw, 52 * k);
            addBtns.put(col, br);
            fill.setColor(Main.BTN);
            c.drawRoundRect(br, 12 * k, 12 * k, fill);
            txt.setColor(Main.TEXT); txt.setTextAlign(Paint.Align.CENTER);
            c.drawText(t, br.centerX(), br.centerY() + 5 * k, txt);
            ax += bw + 10 * k;
        }
        String[] lbl = {I18n.t("Listo"), I18n.t("Restablecer"), I18n.t("Cancelar")};
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        txt.setTextSize(15 * k);
        float bx = W - 16 * k;
        for (int i = 0; i < 3; i++) {
            float bw = txt.measureText(lbl[i]) + 32 * k;
            buttons[i].set(bx - bw, 12 * k, bx, 52 * k);
            bx -= bw + 10 * k;
            fill.setColor(i == 0 ? Main.RED : Main.BTN);
            c.drawRoundRect(buttons[i], 12 * k, 12 * k, fill);
            txt.setColor(i == 0 ? 0xFFFFFFFF : Main.TEXT);
            txt.setTextAlign(Paint.Align.CENTER);
            c.drawText(lbl[i], buttons[i].centerX(), buttons[i].centerY() + 5 * k, txt);
        }

        HashMap<String, Float> m = xs(order);
        closeBtns.clear();
        for (String col : order) if (!col.equals(dragCol)) drawColumn(c, col, m.get(col), width(col), false);
        if (dragCol != null) drawColumn(c, dragCol, floatX, width(dragCol), true);

        // manijas entre columnas
        for (int i = 0; i + 1 < order.size(); i++) {
            float hx = m.get(order.get(i)) + width(order.get(i)) + 6;
            boolean active = mode == COL_HANDLE && i == index;
            handle(c, hx * k, (top + (strip ? (h - stripH - 12) / 2 : h / 2)) * k, true, active);
        }
    }

    private void handle(Canvas c, float cx, float cy, boolean vertical, boolean active) {
        fill.setColor(active && atLimit ? Main.MUTED : Main.RED);
        float t = (active ? 12 : 9) * k, len = (vertical ? 84 : 72) * k;
        if (vertical) r.set(cx - t / 2, cy - len / 2, cx + t / 2, cy + len / 2);
        else r.set(cx - len / 2, cy - t / 2, cx + len / 2, cy + t / 2);
        c.drawRoundRect(r, t / 2, t / 2, fill);
        if (active && atLimit) {
            txt.setTypeface(Typeface.DEFAULT); txt.setTextSize(12 * k); txt.setColor(Main.MUTED); txt.setTextAlign(Paint.Align.CENTER);
            c.drawText(I18n.t("límite"), cx, (vertical ? cy - len / 2 : cy - t) - 8 * k, txt);
        }
    }

    private void drawColumn(Canvas c, String col, float x, float w, boolean dragging) {
        boolean withStrip = strip && "map".equals(col);
        float bh = withStrip ? h - stripH - 12 : h;
        block(c, x, top, w, bh, names.get(col), "map".equals(col) ? I18n.t("se ajusta solo") + " · " + Math.round(w) : I18n.t("ancho") + " " + Math.round(w), dragging);
        if (!"map".equals(col) && !dragging && order.size() > 1) {
            // ✕ para ocultar la tarjeta
            RectF xr = new RectF((x + w - 40) * k, (top + 10) * k, (x + w - 10) * k, (top + 40) * k);
            closeBtns.put(col, xr);
            fill.setColor(Main.BTN);
            c.drawOval(xr, fill);
            txt.setColor(Main.TEXT); txt.setTextAlign(Paint.Align.CENTER); txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(15 * k);
            c.drawText("✕", xr.centerX(), xr.centerY() + 5 * k, txt);
        }
        if ("map".equals(col) && !strip && !dragging) {
            txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(14 * k);
            String t = "+ " + I18n.t("Agregar franja de widgets");
            float tw = txt.measureText(t) + 28 * k, cx = (x + w / 2) * k, by = (top + h - 52) * k;
            addStripBtn.set(cx - tw / 2, by, cx + tw / 2, by + 38 * k);
            fill.setColor(Main.BTN);
            c.drawRoundRect(addStripBtn, 12 * k, 12 * k, fill);
            txt.setColor(Main.TEXT); txt.setTextAlign(Paint.Align.CENTER);
            c.drawText(t, addStripBtn.centerX(), addStripBtn.centerY() + 5 * k, txt);
        }
        if (!withStrip || dragging) return;
        // franja: widgets, manijas entre ellos, manija del alto y «+»
        float[][] wr = widgetRects(x, w);
        for (int i = 0; i < wr.length; i++)
            block(c, wr[i][0], stripTop(), wr[i][1], stripH, cb.widgetName(kinds.get(i)), Math.round(wr[i][1]) + " × " + Math.round(stripH), mode == W_TAP && index == i);
        for (int i = 0; i + 1 < wr.length; i++)
            handle(c, (wr[i][0] + wr[i][1] + HomeStrip.GAP / 2) * k, (stripTop() + stripH / 2) * k, true, mode == W_HANDLE && index == i);
        handle(c, (x + w / 2) * k, (stripTop() - 6) * k, false, mode == STRIP_H);
        int n = kinds.size();
        boolean room = n < HomeStrip.MAX && (n + 1) * HomeStrip.MIN_W + n * HomeStrip.GAP <= w;
        if (room) {
            float ax = (x + w - 34) * k, ay = (stripTop() + 6) * k;
            addBtn.set(ax, ay, ax + 28 * k, ay + 28 * k);
            fill.setColor(Main.RED);
            c.drawRoundRect(addBtn, 14 * k, 14 * k, fill);
            txt.setColor(0xFFFFFFFF); txt.setTextAlign(Paint.Align.CENTER); txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setTextSize(20 * k);
            c.drawText("+", addBtn.centerX(), addBtn.centerY() + 7 * k, txt);
        } else addBtn.setEmpty();
    }

    private void block(Canvas c, float x, float y, float w, float bh, String name, String sub, boolean active) {
        r.set(x * k, y * k, (x + w) * k, (y + bh) * k);
        fill.setColor(Main.CARD);
        c.drawRoundRect(r, 18 * k, 18 * k, fill);
        stroke.setStrokeWidth((active ? 3 : 1.5f) * k);
        stroke.setColor(active ? Main.RED : (Main.MUTED & 0x00FFFFFF) | 0x66000000);
        c.drawRoundRect(r, 18 * k, 18 * k, stroke);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        float big = bh < 140 ? 15 : 19;
        txt.setTextSize(big * k);
        txt.setColor(Main.TEXT);
        String nm = name != null ? name : "";
        while (nm.length() > 3 && txt.measureText(nm) > (w - 16) * k) nm = nm.substring(0, nm.length() - 2) + "…";
        float cx = r.centerX(), cy = r.centerY();
        c.drawText(nm, cx, cy - 4 * k, txt);
        txt.setTypeface(Typeface.DEFAULT);
        txt.setTextSize(13 * k);
        txt.setColor(Main.MUTED);
        c.drawText(sub, cx, cy + 16 * k, txt);
    }
}
