package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini panel del monitor OBD en la tarjeta «Mi vehículo» del inicio: estado, refrigerante, batería y RPM, y las
 * alertas más graves (las que quepan). Solo se ve mientras el monitor está encendido; apagado, la tarjeta queda como
 * siempre (con la foto). Al tocarlo se abre la pantalla Vehículo con todo.
 */
public class ObdMini extends LinearLayout implements Obd.Listener {

    private final float k;                  // escala del lienzo de StradaOS
    private final TextView state;
    private final TextView[] vals = new TextView[3];
    private final LinearLayout list;
    private int lines = 3;
    private String shown = "";
    /** Tarjeta propia del inicio (en vez del reproductor o el mapa): se ve también con el monitor apagado. */
    private final boolean always;

    public ObdMini(Context c, float scale) { this(c, scale, false); }

    public ObdMini(Context c, float scale, boolean always) {
        super(c);
        k = scale;
        this.always = always;
        setOrientation(VERTICAL);
        setPadding(px(12), px(10), px(12), px(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Main.INNER);
        bg.setCornerRadius(px(16));
        setBackground(bg);

        state = tv("", 12, Main.MUTED, false);
        state.setSingleLine(true);
        state.setEllipsize(TextUtils.TruncateAt.END);
        addView(state);

        // refrigerante · batería · RPM
        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, px(4), 0, px(4));
        String[] units = {"°C", "V", "rpm"};
        for (int i = 0; i < 3; i++) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(VERTICAL);
            vals[i] = tv("—", 21, Main.TEXT, true);
            vals[i].setSingleLine(true);
            cell.addView(vals[i]);
            cell.addView(tv(units[i], 11, Main.MUTED, false));
            row.addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        addView(row);

        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        setVisibility(always || Obd.running() ? VISIBLE : GONE);
    }

    private int px(float v) { return Math.round(v * k); }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k * Main.FONT);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Obd.addListener(this);
        onObd();
    }

    @Override protected void onDetachedFromWindow() {
        Obd.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // cuántas alertas caben debajo de los valores (cada línea ~20 de alto)
        int n = Math.max(1, Math.min(always ? 10 : 4, (int) ((h / k - 92) / 20)));
        if (n != lines) { lines = n; shown = ""; post(new Runnable() { public void run() { onObd(); } }); }
    }

    @Override public void onObd() {
        boolean on = Obd.running();
        boolean vis = on || always;
        if (vis != (getVisibility() == VISIBLE)) setVisibility(vis ? VISIBLE : GONE);
        if (!on) {
            if (!always || "off".equals(shown)) return;
            shown = "off";
            state.setText(I18n.t("Monitor apagado · toca para conectar"));
            state.setTextColor(Main.MUTED);
            for (TextView v : vals) { v.setText("—"); v.setTextColor(Main.TEXT); }
            list.removeAllViews();
            return;
        }
        int st = Obd.status;
        state.setText(st == Obd.LIVE ? "● " + I18n.t("Monitor del vehículo") : I18n.t(Obd.statusMsg));
        state.setTextColor(st == Obd.LIVE ? ObdActivity.GOOD : st == Obd.ERROR ? ObdActivity.DANGER : Main.MUTED);
        Obd.Sensor[] ss = {Obd.COOLANT, Obd.VOLT, Obd.RPM};
        for (int i = 0; i < 3; i++) {
            String t = ss[i].text();
            if (!t.contentEquals(vals[i].getText())) vals[i].setText(t);
            int col = ss[i].level == Obd.CRIT ? ObdActivity.DANGER : ss[i].level == Obd.WARN ? ObdActivity.AMBER : Main.TEXT;
            if (vals[i].getCurrentTextColor() != col) vals[i].setTextColor(col);
        }

        // las más graves primero; si no hay alertas, los códigos guardados; si no, «todo normal»
        List<Obd.Alert> al;
        synchronized (Obd.alerts) { al = new ArrayList<Obd.Alert>(Obd.alerts); }
        java.util.Collections.sort(al, new java.util.Comparator<Obd.Alert>() {
            public int compare(Obd.Alert a, Obd.Alert b) { return a.level != b.level ? b.level - a.level : (b.at > a.at ? 1 : b.at < a.at ? -1 : 0); }
        });
        StringBuilder key = new StringBuilder();
        int n = Math.min(lines, al.size());
        for (int i = 0; i < n; i++) key.append(al.get(i).level).append(al.get(i).title).append('|');
        if (al.size() > n) key.append('+').append(al.size() - n);
        if (al.isEmpty()) key.append(Obd.mil).append(Obd.dtcs);
        if (key.toString().equals(shown)) return;
        shown = key.toString();
        list.removeAllViews();
        for (int i = 0; i < n; i++) {
            Obd.Alert a = al.get(i);
            boolean last = i == n - 1 && al.size() > n;
            TextView t = tv((a.level == Obd.CRIT ? "▲ " : "● ") + a.title + (last ? "  +" + (al.size() - n) : ""), 12,
                a.level == Obd.CRIT ? ObdActivity.DANGER : ObdActivity.AMBER, a.level == Obd.CRIT);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, px(4), 0, 0);
            list.addView(t);
        }
        if (n == 0) {
            TextView t = tv(Obd.mil ? I18n.t("Luz del motor") + " · " + Obd.dtcs.size() + " " + I18n.t(Obd.dtcs.size() == 1 ? "código" : "códigos")
                : I18n.t("Todo normal por ahora."), 12, Obd.mil ? ObdActivity.AMBER : Main.MUTED, false);
            t.setPadding(0, px(4), 0, 0);
            list.addView(t);
        }
    }
}
