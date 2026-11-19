package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Franja de widgets debajo de la tarjeta del centro (2.24, apagada por defecto): 1 a 3 mini tarjetas según el ancho.
 * Cada una puede ser «alt» (altitud, pendiente y rumbo), «trip» (viaje), «obd:<dato>» (un dato del escáner) o
 * «apps» (3 accesos directos). Se actualiza cada 2 s mientras se ve.
 */
public class HomeStrip extends LinearLayout {

    public interface Host {
        void openApp(String component);
        void pickApp(int index);
        Drawable icon(String component);
        void openObd();
        void menu();
        /** {título, artista, fuente, "1" si suena} de lo que suena ahora. */
        String[] music();
        /** -1 anterior, 0 reproducir/pausa, 1 siguiente. */
        void media(int which);
        android.graphics.Bitmap asset(String name);
    }

    static final float H = 112, H_MIN = 90, H_MAX = 200, MAP_MIN_H = 180, MIN_W = 120, GAP = 10;
    static final int MAX = 4;
    static final String[] DEFAULTS = {"alt", "trip", "apps"};

    /** Widgets elegidos, de izquierda a derecha (strip_list; antes de 2.24 final eran strip_w1…3). */
    static java.util.List<String> kinds(SharedPreferences p) {
        java.util.List<String> o = new java.util.ArrayList<String>();
        String saved = p.getString("strip_list", null);
        if (saved != null) { for (String k : saved.split(",")) if (k.length() > 0 && o.size() < MAX) o.add(k); }
        else for (int i = 0; i < 3; i++) o.add(p.getString("strip_w" + (i + 1), DEFAULTS[i]));
        if (o.isEmpty()) o.add("alt");
        return o;
    }

    /** Ancho relativo de cada widget (strip_wr); si no coincide con la cantidad, todos iguales. */
    static float[] weights(SharedPreferences p, int n) {
        float[] w = new float[n];
        java.util.Arrays.fill(w, 1);
        String[] parts = p.getString("strip_wr", "").split(",");
        if (parts.length == n) try { for (int i = 0; i < n; i++) w[i] = Math.max(0.05f, Float.parseFloat(parts[i])); } catch (Throwable e) { java.util.Arrays.fill(w, 1); }
        return w;
    }

    static void save(SharedPreferences p, java.util.List<String> kinds, float[] w) {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < kinds.size(); i++) {
            a.append(i > 0 ? "," : "").append(kinds.get(i));
            b.append(i > 0 ? "," : "").append(String.format(java.util.Locale.US, "%.3f", w[i]));
        }
        p.edit().putString("strip_list", a.toString()).putString("strip_wr", b.toString()).apply();
    }

    /**
     * Anchos reales de los widgets en «usable» unidades: en proporción a sus pesos, pero ninguno menos de MIN_W
     * (lo que le falta a uno se descuenta, en proporción, de los que tienen de sobra).
     */
    static float[] widths(float usable, float[] w, int n) {
        float sum = 0;
        for (int i = 0; i < n; i++) sum += w[i];
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = usable * w[i] / sum;
        for (int pass = 0; pass < n; pass++) {
            float deficit = 0, spare = 0;
            for (int i = 0; i < n; i++) { if (out[i] < MIN_W) deficit += MIN_W - out[i]; else spare += out[i] - MIN_W; }
            if (deficit <= 0.01f || spare <= 0) break;
            float take = Math.min(1f, deficit / spare);
            for (int i = 0; i < n; i++) out[i] = out[i] < MIN_W ? MIN_W : out[i] - (out[i] - MIN_W) * take;
        }
        return out;
    }

    /** Cuántos widgets caben en un ancho (cada uno al menos MIN_W). */
    static int fit(float width, int wanted) {
        int n = wanted;
        while (n > 1 && n * MIN_W + (n - 1) * GAP > width) n--;
        return n;
    }

    private final float k;
    private final SharedPreferences prefs;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final java.util.List<Runnable> refreshers = new java.util.ArrayList<Runnable>();
    private boolean attached;

    /** Cuánto crece el texto con el alto de la franja (1 = alto de siempre, hasta 1,6 en una franja de 200). */
    private final float grow;

    public HomeStrip(Context c, float scale, float widthUnits, float heightUnits, SharedPreferences prefs, Host host) {
        super(c);
        k = scale;
        grow = Math.max(1f, Math.min(1.6f, heightUnits / H));
        this.prefs = prefs;
        this.host = host;
        setOrientation(HORIZONTAL);
        java.util.List<String> ks = kinds(prefs);
        int n = fit(widthUnits, ks.size());                                   // si no caben, sobran los últimos
        float[] wd = widths(widthUnits - (n - 1) * GAP, weights(prefs, ks.size()), n);
        for (int i = 0; i < n; i++) {
            View v = widget(ks.get(i), wd[i]);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, wd[i]);
            if (i > 0) lp.leftMargin = px(GAP);
            addView(v, lp);
        }
    }

    private int px(float v) { return Math.round(v * k); }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k * Main.FONT);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout box() {
        LinearLayout b = new LinearLayout(getContext());
        b.setOrientation(VERTICAL);
        b.setGravity(Gravity.CENTER_VERTICAL);      // en una franja alta el contenido queda centrado
        b.setPadding(px(14), px(12), px(12), px(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Main.CARD);
        g.setCornerRadius(px(20));
        b.setBackground(g);
        b.setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { host.menu(); return true; } });
        return b;
    }

    private View widget(String kind, float widthUnits) {
        if ("apps".equals(kind)) return apps(widthUnits);
        if ("music".equals(kind)) return music(widthUnits);
        final LinearLayout b = box();
        final TextView title = tv("", 11 * Math.min(grow, 1.2f), Main.MUTED, true), value = tv("—", 26 * grow, Main.TEXT, true),
            sub = tv("", 12 * Math.min(grow, 1.25f), Main.SOFT, false);
        value.setPadding(0, px(8), 0, px(6));
        final float avail = (widthUnits - 28) * k, base = 26 * grow * k * Main.FONT;
        b.addView(title);
        b.addView(value);
        b.addView(sub);
        Runnable r;
        if ("trip".equals(kind)) {
            title.setText(I18n.t("VIAJE"));
            r = new Runnable() { public void run() {
                double km = Trip.meters / 1000;
                value.setText(String.format(I18n.num, km < 100 ? "%.1f km" : "%.0f km", km));
                fitText(value, base, avail);
                long min = Trip.movingMs / 60000;
                String t = (min / 60) + ":" + String.format(java.util.Locale.US, "%02d", min % 60) + " h";
                String avg = Trip.movingMs > 60000 ? " · " + Math.round(Trip.meters / (Trip.movingMs / 1000.0) * 3.6) + " km/h" : "";
                String fuel = Trip.fuelL > 0.05 && km > 0.5 ? " · " + String.format(I18n.num, "%.1f", Trip.fuelL / km * 100) + " L/100" : "";
                sub.setText(t + avg + fuel);
            }};
            b.setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { host.menu(); return true; } });
            b.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                android.widget.Toast.makeText(getContext(), I18n.t("Mantén presionado para reiniciar el viaje o cambiar los widgets"), android.widget.Toast.LENGTH_SHORT).show();
            }});
        } else if ("alt".equals(kind)) {
            title.setText(I18n.t("ALTITUD"));
            r = new Runnable() { public void run() {
                value.setText(Float.isNaN(Trip.alt) ? "—" : I18n.integer(Math.round(Trip.alt)) + " m");
                fitText(value, base, avail);
                String g = Float.isNaN(Trip.grade) ? I18n.t("pendiente —") : (Trip.grade > 1 ? "↗ " : Trip.grade < -1 ? "↘ " : "→ ") + Math.abs(Math.round(Trip.grade)) + " %";
                sub.setText(g + " · " + Trip.compass(Trip.bearing));
                // cuesta fuerte: el número se pinta ámbar (es cuando más sube la temperatura del motor y de la caja)
                value.setTextColor(!Float.isNaN(Trip.grade) && Math.abs(Trip.grade) >= 8 ? ObdActivity.AMBER : Main.TEXT);
            }};
        } else {
            final Obd.Sensor s = sensor(kind.startsWith("obd:") ? kind.substring(4) : "coolant");
            title.setText(I18n.t(s.label).toUpperCase(I18n.loc));
            r = new Runnable() { public void run() {
                boolean on = Obd.running();
                value.setText(on && s.has() ? s.text() + " " + s.unit : "—");
                fitText(value, base, avail);
                value.setTextColor(s.level == Obd.CRIT ? ObdActivity.DANGER : s.level == Obd.WARN ? ObdActivity.AMBER : Main.TEXT);
                sub.setText(!on ? I18n.t("monitor apagado") : s.level == Obd.CRIT ? I18n.t("alerta") : s.level == Obd.WARN ? I18n.t("aviso") : I18n.t("normal"));
            }};
            b.setOnClickListener(new OnClickListener() { public void onClick(View v) { host.openObd(); } });
        }
        refreshers.add(r);
        r.run();
        return b;
    }

    /** El número grande se achica (hasta la mitad) si no cabe en el ancho del widget, en vez de cortarse con «…». */
    private static void fitText(TextView t, float basePx, float availPx) {
        android.graphics.Paint p = new android.graphics.Paint(t.getPaint());
        float size = basePx;
        p.setTextSize(size);
        String s = t.getText().toString();
        while (size > basePx * 0.5f && p.measureText(s) > availPx) { size -= 1; p.setTextSize(size); }
        if (Math.abs(t.getTextSize() - size) > 0.5f) t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    private static Obd.Sensor sensor(String key) {
        for (Obd.Sensor s : Obd.ALL) if (s.key.equals(key)) return s;
        return Obd.COOLANT;
    }

    /** Música en chico: canción, artista y anterior / reproducir / siguiente (lo mismo que la tarjeta grande). */
    private View music(float widthUnits) {
        LinearLayout b = box();
        final TextView title = tv(I18n.t("MÚSICA"), 11 * Math.min(grow, 1.2f), Main.MUTED, true);
        final TextView song = tv("", 17 * Math.min(grow, 1.4f), Main.TEXT, true), artist = tv("", 12 * Math.min(grow, 1.25f), Main.SOFT, false);
        song.setPadding(0, px(6), 0, px(2));
        b.addView(title);
        b.addView(song);
        b.addView(artist);
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(6), 0, 0);
        final ImageView[] btns = new ImageView[3];
        String[] icons = {"skip_previous", "play_arrow", "skip_next"};
        float size = Math.min(40 * Math.min(grow, 1.3f), (widthUnits - 28 - 16) / 3);
        for (int i = 0; i < 3; i++) {
            final int which = i - 1;
            FrameLayout cell = new FrameLayout(getContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == 1 ? Main.RED : Main.CIRCLE);
            cell.setBackground(g);
            ImageView iv = new ImageView(getContext());
            iv.setImageBitmap(host.asset("ic/" + icons[i] + ".png"));
            btns[i] = iv;
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(px(size * 0.55f), px(size * 0.55f));
            ilp.gravity = Gravity.CENTER;
            cell.addView(iv, ilp);
            cell.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                host.media(which);
                h.postDelayed(new Runnable() { public void run() { refreshMusic(song, artist, btns[1]); } }, 700);
            }});
            LayoutParams lp = new LayoutParams(px(size), px(size));
            if (i > 0) lp.leftMargin = px(8);
            row.addView(cell, lp);
        }
        b.addView(row);
        Runnable r = new Runnable() { public void run() { refreshMusic(song, artist, btns[1]); } };
        refreshers.add(r);
        r.run();
        return b;
    }

    private void refreshMusic(TextView song, TextView artist, ImageView play) {
        String[] m = host.music();
        boolean has = m[0] != null || m[1] != null;
        song.setText(has ? (m[0] != null ? m[0] : "") : I18n.t("Nada sonando"));
        artist.setText(has ? (m[1] != null ? m[1] : (m[2] != null ? I18n.t(m[2]) : "")) : I18n.t("Radio, Bluetooth o USB"));
        play.setImageBitmap(host.asset("1".equals(m[3]) ? "ic/pause.png" : "ic/play_arrow.png"));
    }

    /** Accesos directos (los que quepan, hasta 6): toque = abrir; presión larga = elegir la app. */
    private View apps(float widthUnits) {
        int count = Math.max(1, Math.min(6, (int) ((widthUnits - 26 + 8) / 54)));
        LinearLayout b = box();
        b.addView(tv(I18n.t("APPS"), 11, Main.MUTED, true));
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(8), 0, 0);
        for (int i = 0; i < count; i++) {
            final int idx = i;
            final String comp = prefs.getString("strip_app" + (i + 1), "");
            FrameLayout cell = new FrameLayout(getContext());
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Main.CIRCLE);
            cell.setBackground(g);
            Drawable d = comp.length() > 0 ? host.icon(comp) : null;
            if (d != null) {
                ImageView iv = new ImageView(getContext());
                iv.setImageDrawable(d);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(30), px(30));
                lp.gravity = Gravity.CENTER;
                cell.addView(iv, lp);
            } else {
                TextView plus = tv("+", 22, Main.MUTED, false);
                plus.setGravity(Gravity.CENTER);
                cell.addView(plus, new FrameLayout.LayoutParams(-1, -1));
            }
            cell.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                if (comp.length() > 0) host.openApp(comp); else host.pickApp(idx);
            }});
            cell.setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { host.pickApp(idx); return true; } });
            LayoutParams lp = new LayoutParams(px(46 * Math.min(grow, 1.3f)), px(46 * Math.min(grow, 1.3f)));
            if (i > 0) lp.leftMargin = px(8);
            row.addView(cell, lp);
        }
        b.addView(row);
        return b;
    }

    private final Runnable tick = new Runnable() { public void run() {
        if (!attached) return;
        for (Runnable r : refreshers) r.run();
        h.postDelayed(this, 2000);
    }};

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; h.postDelayed(tick, 2000); }
    @Override protected void onDetachedFromWindow() { attached = false; h.removeCallbacks(tick); super.onDetachedFromWindow(); }
}
