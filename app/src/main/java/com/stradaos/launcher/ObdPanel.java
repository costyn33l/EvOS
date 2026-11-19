package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel de datos del escáner para el inicio: hasta 6 valores en vivo (RPM, velocidad, refrigerante, batería, carga y
 * consumo por defecto), en 3 columnas o 2 si el recuadro es angosto. Se elige con «Panel de datos» en Qué muestra
 * cada tarjeta; los datos se cambian desde Ajustes → Pantalla de inicio o con la presión larga de la tarjeta.
 */
public class ObdPanel extends LinearLayout implements Obd.Listener {
    static final String DEFAULT = "rpm,speed,coolant,volt,load,consumption";
    static final int MAX = 6;

    private final float k;
    private final SharedPreferences prefs;
    private final List<Obd.Sensor> sensors = new ArrayList<Obd.Sensor>();
    private final List<TextView> values = new ArrayList<TextView>();
    private final TextView state;
    private final LinearLayout grid;
    private int cols = 3;
    private boolean drawnOn;
    private long[] drawn = new long[0];

    public ObdPanel(Context c, float scale) {
        super(c);
        k = scale;
        prefs = Obd.prefs(c);
        setOrientation(VERTICAL);
        setPadding(px(14), px(10), px(14), px(8));
        state = tv("", 11, Main.MUTED, false);
        state.setSingleLine(true);
        state.setEllipsize(TextUtils.TruncateAt.END);
        addView(state);
        grid = new LinearLayout(c);
        grid.setOrientation(VERTICAL);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        build();
    }

    /** Datos elegidos, en orden, sin repetidos y como máximo {@link #MAX}. */
    static String[] keys(SharedPreferences p) {
        String s = p.getString("obd_panel_keys", DEFAULT);
        List<String> out = new ArrayList<String>();
        for (String key : s.split(",")) {
            if (key.length() == 0 || out.contains(key)) continue;
            boolean ok = false;
            for (Obd.Sensor x : Obd.ALL) if (x.key.equals(key)) ok = true;
            if (ok) out.add(key);
            if (out.size() >= MAX) break;
        }
        if (out.isEmpty()) for (String key : DEFAULT.split(",")) out.add(key);
        return out.toArray(new String[0]);
    }

    static void saveKeys(SharedPreferences p, List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < keys.size() && i < MAX; i++) {
            if (i > 0) b.append(',');
            b.append(keys.get(i));
        }
        p.edit().putString("obd_panel_keys", b.length() > 0 ? b.toString() : DEFAULT).apply();
    }

    private static Obd.Sensor sensor(String key) {
        for (Obd.Sensor s : Obd.ALL) if (s.key.equals(key)) return s;
        return null;
    }

    private int px(float v) { return Math.round(v * k); }

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k * Main.FONT);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void build() {
        grid.removeAllViews();
        sensors.clear();
        values.clear();
        String[] keys = keys(prefs);
        for (String key : keys) {
            Obd.Sensor s = sensor(key);
            if (s != null) sensors.add(s);
        }
        int n = sensors.size();
        int c = Math.max(1, Math.min(cols, n));
        int rows = n == 0 ? 1 : (n + c - 1) / c;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < c; i++) {
                int idx = r * c + i;
                LinearLayout cell = new LinearLayout(getContext());
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                cell.setPadding(px(4), px(3), px(4), px(3));
                if (idx < n) {
                    Obd.Sensor s = sensors.get(idx);
                    TextView label = tv(I18n.t(s.label), 10.5f, Main.MUTED, false);
                    TextView value = tv("—", 23, Main.TEXT, true);
                    TextView unit = tv(s.unit, 10.5f, Main.MUTED, false);
                    unit.setPadding(px(4), 0, 0, px(2));
                    LinearLayout line = new LinearLayout(getContext());
                    line.setGravity(Gravity.BOTTOM);
                    line.addView(value);
                    line.addView(unit);
                    cell.addView(label);
                    cell.addView(line);
                    values.add(value);
                }
                row.addView(cell, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
            }
            grid.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        }
        drawn = new long[n];
        java.util.Arrays.fill(drawn, -1);
        drawnOn = false;
        onObd();
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
        int c = w >= px(360) ? 3 : 2;
        if (c != cols) { cols = c; build(); }
    }

    @Override public void onObd() {
        if (state == null) return;
        boolean on = Obd.running();
        int st = Obd.status;
        String msg = !on ? I18n.t("Monitor apagado")
            : st == Obd.LIVE ? "● " + I18n.t("Datos del motor") : I18n.t(Obd.statusMsg);
        if (!msg.contentEquals(state.getText())) state.setText(msg);
        state.setTextColor(!on ? Main.MUTED : st == Obd.LIVE ? ObdActivity.GOOD : st == Obd.ERROR ? ObdActivity.DANGER : Main.MUTED);
        if (drawnOn != on) {
            drawnOn = on;
            java.util.Arrays.fill(drawn, -1);
        }
        for (int i = 0; i < sensors.size() && i < values.size() && i < drawn.length; i++) {
            Obd.Sensor s = sensors.get(i);
            TextView v = values.get(i);
            if (drawn[i] == s.pushes) continue;
            drawn[i] = s.pushes;
            String text = on && s.has() ? s.text() : "—";
            if (!text.contentEquals(v.getText())) v.setText(text);
            int col = !on ? Main.TEXT : s.level == Obd.CRIT ? ObdActivity.DANGER : s.level == Obd.WARN ? ObdActivity.AMBER : Main.TEXT;
            if (v.getCurrentTextColor() != col) v.setTextColor(col);
        }
    }
}
