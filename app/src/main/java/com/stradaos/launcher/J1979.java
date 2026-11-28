package com.stradaos.launcher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Todas las señales estándar del modo 01 (SAE J1979), con sus fórmulas, sacadas de OBDb
 * (github.com/OBDb/SAEJ1979, CC BY-SA 4.0: assets/obd/saej1979.json, sin cambios). Sirve para mostrar en «Todos los
 * datos» lo que el auto entrega y EvOS no trae como dato propio (temperaturas del turbo, gases de escape, AdBlue,
 * batería del híbrido, marcha engranada…). Solo lectura: son las mismas consultas del modo 01.
 *
 * <p>Formato de OBDb: {@code bix} = primer bit contando desde el más alto del primer byte de datos, {@code len} =
 * cantidad de bits; valor = crudo × mul / div + add. Una señal con «hermana» {@code ID_SUP} solo vale si ese bit es 1.
 */
final class J1979 {
    static final class Sig {
        String id, name, unit, sup, path;
        int bix, len;
        double mul = 1, div = 1, add, min, max = Double.NaN, nullmin = Double.NaN, nullmax = Double.NaN;
        boolean sign, lsb;
        /** Estados con nombre (mapas de OBDb): crudo → texto. */
        Map<Long, String> states;
    }

    static final class Val {
        String key, name, unit, path;
        double v;
        int pid;
        /** Texto en vez de número (sí/no, estados). */
        String text;
    }

    /** Una señal de OBDb (formato «fmt»). */
    static Sig parseSig(JSONObject so, java.util.Set<String> ids) throws org.json.JSONException {
        JSONObject f = so.getJSONObject("fmt");
        Sig s = new Sig();
        s.id = so.getString("id");
        s.name = so.optString("name", s.id);
        s.path = so.optString("path", "");
        s.unit = f.has("unit") ? f.getString("unit") : null;
        s.bix = f.optInt("bix", 0);
        s.len = f.optInt("len", 8);
        s.mul = f.optDouble("mul", 1);
        s.div = f.optDouble("div", 1);
        s.add = f.optDouble("add", 0);
        s.min = f.optDouble("min", 0);
        s.max = f.optDouble("max", Double.NaN);
        s.nullmin = f.optDouble("nullmin", Double.NaN);
        s.nullmax = f.optDouble("nullmax", Double.NaN);
        s.sign = f.optBoolean("sign", false);
        s.lsb = f.optBoolean("blsb", false);
        JSONObject map = f.optJSONObject("map");
        if (map != null) {
            s.states = new HashMap<Long, String>();
            java.util.Iterator<String> it = map.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject e = map.optJSONObject(k);
                if (e == null) continue;
                String v = e.optString("value", ""), d = e.optString("description", v);
                try { s.states.put(Long.parseLong(k), v.length() > 0 && v.length() <= 14 ? v : d); } catch (NumberFormatException ignored) { }
            }
        }
        if (ids != null && ids.contains(s.id + "_SUP")) s.sup = s.id + "_SUP";
        return s;
    }

    /** Bits con los bytes al revés (blsb: el byte menos significativo primero). */
    static long bitsLsb(byte[] b, int bix, int len) {
        if (len <= 8 || bix % 8 != 0) return bits(b, bix, len);
        int start = bix / 8, n = (len + 7) / 8;
        if (start + n > b.length) return -1;
        byte[] r = b.clone();
        for (int i = 0; i < n; i++) r[start + i] = b[start + n - 1 - i];
        return bits(r, bix, len);
    }

    /**
     * Las señales de una respuesta, revisadas: dentro del rango de OBDb, sin el valor «no disponible» y, si la señal
     * tiene su bit _SUP, solo si ese bit dice que existe. Sí/no y estados se dan como texto.
     */
    static List<Val> decodeAll(List<Sig> list, byte[] b, String keyPrefix, boolean texts) {
        List<Val> out = new ArrayList<Val>();
        if (list == null || b == null) return out;
        Map<String, Long> supBits = new HashMap<String, Long>();
        for (Sig s : list) if (s.id.endsWith("_SUP")) supBits.put(s.id, bits(b, s.bix, s.len));
        for (Sig s : list) {
            if (s.id.endsWith("_SUP") || COVERED.contains(s.id)) continue;
            if (s.sup != null) { Long sb = supBits.get(s.sup); if (sb == null || sb != 1) continue; }
            long raw = s.lsb ? bitsLsb(b, s.bix, s.len) : bits(b, s.bix, s.len);
            if (raw < 0) continue;
            boolean flag = "noyes".equals(s.unit) || "yesno".equals(s.unit) || "offon".equals(s.unit) || "onoff".equals(s.unit);
            if (s.states != null || flag) {
                if (!texts) continue;
                String t = s.states != null ? s.states.get(raw) : null;
                if (t == null && flag) {
                    boolean on = "yesno".equals(s.unit) || "onoff".equals(s.unit) ? raw == 0 : raw != 0;
                    t = I18n.t("offon".equals(s.unit) || "onoff".equals(s.unit) ? (on ? "Encendido" : "Apagado") : (on ? "sí" : "no"));
                }
                if (t == null) continue;
                Val x = new Val();
                x.key = keyPrefix + s.id; x.name = s.name; x.unit = ""; x.text = t; x.path = s.path;
                out.add(x);
                continue;
            }
            if (s.unit == null || "hex".equals(s.unit)) continue;
            if (s.sign && s.len < 64 && (raw & (1L << (s.len - 1))) != 0) raw -= 1L << s.len;
            double v = raw * s.mul / s.div + s.add;
            if (!Double.isNaN(s.nullmax) && v >= s.nullmax - 1e-9) continue;
            if (!Double.isNaN(s.nullmin) && v <= s.nullmin + 1e-9) continue;
            if (!Double.isNaN(s.max) && s.max > s.min && (v > s.max + 1e-6 || v < s.min - 1e-6)) continue;
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            Val x = new Val();
            x.key = keyPrefix + s.id;
            x.name = s.name;
            x.path = s.path;
            x.unit = unit(s.unit);
            if ("seconds".equals(s.unit) && v >= 3600) { v /= 3600; x.unit = "h"; }
            if ("fahrenheit".equals(s.unit)) { v = (v - 32) * 5 / 9; x.unit = "°C"; }
            if ("miles".equals(s.unit)) { v *= 1.609344; x.unit = "km"; }
            if ("milesPerHour".equals(s.unit)) { v *= 1.609344; x.unit = "km/h"; }
            if ("psi".equals(s.unit)) { v *= 0.0689476; x.unit = "bar"; }
            x.v = v;
            out.add(x);
        }
        return out;
    }

    private static Map<Integer, List<Sig>> byPid;
    private static final Map<String, String> RO = new HashMap<String, String>();

    /** Señales que EvOS ya muestra como dato propio (con alertas, gráfico y emblema): no se repiten. */
    private static final java.util.Set<String> COVERED = new java.util.HashSet<String>(java.util.Arrays.asList(
        "LOAD_PCT", "ECT", "SHRTFT1", "LONGFT1", "FP", "MAP", "RPM", "VSS", "SPARKADV", "IAT", "MAF", "TP", "O2S11", "O2S12",
        "RUNTM", "MIL_DIST", "FRP", "EGR_PCT", "EVAP_PCT", "FLI", "WARM_UPS", "CLR_DIST", "BARO", "CATEMP11", "CATEMP12", "VPWR",
        "LOAD_ABS", "EQ_RAT", "TP_R", "AAT", "TP_B", "APP_D", "APP_E", "TAC_PCT", "MIL_TIME", "CLR_TIME", "ALCH_PCT", "STSO2FT1",
        "LGSO2FT1", "FRP_ABS", "APP_R", "BAT_SOC", "EOT", "FUEL_TIMING", "FUEL_RATE", "TQ_DD", "TQ_ACT", "BP_A_ACT", "DPF1_DP",
        "DPF1_INT", "DPF1_OUTT", "PF_REGEN_PCT", "ODO"));

    /** Lo que no es un número para mostrar (indicadores sí/no, códigos, mapas de estados). */
    private static boolean skip(Sig s) {
        return s.unit == null || COVERED.contains(s.id) || s.id.endsWith("_SUP") || "noyes".equals(s.unit) || "yesno".equals(s.unit)
            || "offon".equals(s.unit) || "hex".equals(s.unit);
    }

    static synchronized void load(Context c) {
        if (byPid != null) return;
        Map<Integer, List<Sig>> m = new HashMap<Integer, List<Sig>>();
        try {
            InputStream in = c.getAssets().open("obd/saej1979.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONArray cmds = new JSONObject(bo.toString("UTF-8")).getJSONArray("commands");
            for (int i = 0; i < cmds.length(); i++) {
                JSONObject cmd = cmds.getJSONObject(i);
                JSONObject cc = cmd.optJSONObject("cmd");
                if (cc == null || !cc.has("01")) continue;
                int pid = Integer.parseInt(cc.getString("01"), 16);
                JSONArray sigs = cmd.getJSONArray("signals");
                List<Sig> list = new ArrayList<Sig>();
                java.util.Set<String> ids = new java.util.HashSet<String>();
                for (int k = 0; k < sigs.length(); k++) ids.add(sigs.getJSONObject(k).getString("id"));
                for (int k = 0; k < sigs.length(); k++) {
                    JSONObject so = sigs.getJSONObject(k);
                    if (so.getJSONObject("fmt").has("map")) continue;
                    list.add(parseSig(so, ids));
                }
                m.put(pid, list);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("obd/j1979_ro.tsv"), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("#")) continue;
                int t = l.indexOf('\t');
                if (t > 0) RO.put(l.substring(0, t), l.substring(t + 1));
            }
            r.close();
        } catch (Throwable e) {
            Obd.log("SAE J1979 (OBDb): " + e);
        }
        byPid = m;
    }

    /** Bits [bix, bix+len) contando desde el bit más alto del primer byte; -1 si la respuesta es más corta. */
    static long bits(byte[] b, int bix, int len) {
        if (bix + len > b.length * 8) return -1;
        long v = 0;
        for (int i = 0; i < len; i++) {
            int bit = bix + i;
            v = v << 1 | ((b[bit / 8] >> (7 - bit % 8)) & 1);
        }
        return v;
    }

    /**
     * Las señales que trae una respuesta del modo 01, ya revisadas: solo las que el auto dice tener (bit _SUP), dentro
     * del rango del estándar y sin el valor de «no disponible».
     */
    static List<Val> decode(int pid, byte[] b) {
        List<Val> out = new ArrayList<Val>();
        Map<Integer, List<Sig>> m = byPid;
        if (m == null || b == null) return out;
        List<Sig> list = m.get(pid);
        if (list == null) return out;
        Map<String, Long> supBits = new HashMap<String, Long>();
        for (Sig s : list) if (s.id.endsWith("_SUP")) supBits.put(s.id, bits(b, s.bix, s.len));
        for (Sig s : list) {
            if (skip(s)) continue;
            if (s.sup != null) { Long sb = supBits.get(s.sup); if (sb == null || sb != 1) continue; }
            long raw = bits(b, s.bix, s.len);
            if (raw < 0) continue;
            if (s.sign && s.len < 64 && (raw & (1L << (s.len - 1))) != 0) raw -= 1L << s.len;
            double v = raw * s.mul / s.div + s.add;
            if (!Double.isNaN(s.nullmax) && v >= s.nullmax) continue;
            if (!Double.isNaN(s.nullmin) && v <= s.nullmin) continue;
            if (!Double.isNaN(s.max) && (v > s.max + 1e-9 || v < s.min - 1e-9)) continue;
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            Val x = new Val();
            x.key = String.format(java.util.Locale.US, "%02X:%s", pid, s.id);
            x.name = name(s.name);
            x.unit = unit(s.unit);
            if ("seconds".equals(s.unit) && v >= 3600) { v /= 3600; x.unit = "h"; }      // contadores de horas de motor
            x.v = v;
            x.pid = pid;
            out.add(x);
        }
        return out;
    }

    static String name(String en) {
        if ("ro".equals(I18n.lang)) { String r = RO.get(en); if (r != null) return r; }
        return en;
    }

    static String unit(String u) {
        if (u == null) return "";
        switch (u) {
            case "celsius": return "°C";
            case "percent": return "%";
            case "kilopascal": return "kPa";
            case "volts": return "V";
            case "amps": return "A";
            case "milliamps": return "mA";
            case "kilowatts": return "kW";
            case "kilowattHours": return "kWh";
            case "kilometers": return "km";
            case "hours": return "h";
            case "minutes": return "min";
            case "seconds": return "s";
            case "rpm": return "rpm";
            case "gramsPerSecond": return "g/s";
            case "newtonMeters": return "Nm";
            case "kilogramsPerHour": return "kg/h";
            case "litersPerHour": return "L/h";
            case "kilometersPerHour": return "km/h";
            case "degrees": return "°";
            case "gramsPerLiter": return "g/L";
            case "milligramsPerStroke": return "mg/cursă";
            case "liters": return "L";
            case "bars": return "bar";
            case "millimeters": return "mm";
            case "centimeters": return "cm";
            case "meters": return "m";
            case "milliseconds": return "ms";
            case "millivolts": return "mV";
            case "ampereHours": return "Ah";
            case "metersPerSecondSquared": return "m/s²";
            case "watts": return "W";
            case "gravity": return "g";
            case "kiloohms": return "kΩ";
            case "ohms": return "Ω";
            case "milliohms": return "mΩ";
            case "hertz": return "Hz";
            case "radians": return "rad";
            case "kilowattHoursPer100Kilometers": return "kWh/100 km";
            default: return "";
        }
    }

    /** El número con los decimales que tiene sentido según su resolución. */
    static String text(Val v) {
        double a = Math.abs(v.v);
        String f = a >= 100 || v.v == Math.rint(v.v) ? "%.0f" : a >= 10 ? "%.1f" : "%.2f";
        return String.format(I18n.num, f, v.v);
    }
}
