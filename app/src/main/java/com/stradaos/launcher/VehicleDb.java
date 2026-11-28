package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Datos propios de cada modelo de auto, de OBDb (github.com/OBDb: un repositorio por marca y modelo, CC BY-SA 4.0):
 * temperatura de la caja, batería, climatizador, neumáticos, kilometraje del tablero… Se baja el archivo del modelo
 * elegido (una vez, y se guarda en el equipo) y, mientras está abierta «Todos los datos», se le pregunta al auto.
 *
 * <p><b>Solo lectura, siempre</b>: únicamente los servicios 22 (ReadDataByIdentifier), 21 (ReadDataByLocalIdentifier)
 * y 01 (datos estándar). Se descartan los comandos que piden cambiar la sesión de diagnóstico ({@code din}/{@code dout}),
 * los que están en prueba en OBDb ({@code dbg}: sin confirmar) y los que no son de CAN. Las órdenes «AT» que se
 * mandan solo configuran el escáner (a quién preguntar y de quién escuchar), no el auto.
 */
final class VehicleDb {
    static final class Cmd {
        String hdr, rax, eax, tst, pri, tmo;
        boolean fcm1;
        int svc;              // 0x22, 0x21 o 0x01
        String param;         // DID (4 hex), local id o PID (2 hex)
        double freq;          // segundos entre lecturas
        List<J1979.Sig> sigs = new ArrayList<J1979.Sig>();
        // estado
        int state;            // 0 sin probar, 1 contesta, -1 no lo tiene
        int fails;
        long at;              // última lectura buena (elapsedRealtime)
        byte[] data;
        String key() { return hdr + "." + (rax != null ? rax : "") + "." + String.format(Locale.US, "%02X", svc) + param; }
    }

    /** Repositorio elegido (p. ej. «Volkswagen-Passat») y sus comandos utilizables. */
    static volatile String repo;
    static volatile List<Cmd> cmds = Collections.emptyList();
    /** Cuántos comandos traía el archivo y por qué se dejaron afuera. */
    static volatile int total, skippedWrite, skippedDebug, skippedYear, skippedProto;
    /** La página «Todos los datos» está abierta: solo entonces se leen (son muchos y no son urgentes). */
    static volatile boolean wanted;
    static volatile String status;

    static SharedPreferences p(Context c) { return c.getSharedPreferences("launcher", 0); }

    /** Los ~540 modelos de OBDb conocidos (assets/obd/obdb_repos.txt). */
    static List<String> known(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("obd/obdb_repos.txt"), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) { l = l.trim(); if (l.length() > 0 && !l.startsWith("#")) out.add(l); }
            r.close();
        } catch (Throwable ignored) { }
        return out;
    }

    /** Marcas cuyo nombre lleva guion (en OBDb, «Marca-Modelo»). */
    private static final String[] HYPHEN_MAKES = {"Mercedes-Benz", "Alfa-Romeo", "Aston-Martin", "Land-Rover", "Rolls-Royce"};

    static String makeOf(String repo) {
        for (String m : HYPHEN_MAKES) if (repo.equals(m) || repo.startsWith(m + "-")) return m;
        int i = repo.indexOf('-');
        return i > 0 ? repo.substring(0, i) : repo;
    }

    static String modelOf(String repo) {
        String m = makeOf(repo);
        return repo.length() > m.length() ? repo.substring(m.length() + 1).replace('-', ' ') : "";
    }

    /**
     * El modelo según el VIN, cuando se puede saber: en el grupo VW los caracteres 7-8 son el código del modelo
     * (WVWZZZ<b>3G</b>…: Passat B8). Devuelve el repositorio de OBDb, o null.
     */
    static String guess(String vin) {
        if (vin == null || vin.length() != 17) return null;
        String mk = Vin.maker(vin);
        if (mk == null) return null;
        String code = vin.substring(6, 8);
        if (Vin.vag(vin)) {
            String[][] t = {
                {"Volkswagen", "1K", "Golf"}, {"Volkswagen", "5K", "Golf"}, {"Volkswagen", "AU", "Golf"}, {"Volkswagen", "5G", "Golf"}, {"Volkswagen", "BA", "Golf"}, {"Volkswagen", "CD", "Golf"},
                {"Volkswagen", "3C", "Passat"}, {"Volkswagen", "3G", "Passat"}, {"Volkswagen", "CB", "Passat"}, {"Volkswagen", "5N", "Tiguan"}, {"Volkswagen", "AD", "Tiguan"},
                {"Volkswagen", "BJ", "Tiguan"}, {"Volkswagen", "CT", "Tiguan"}, {"Volkswagen", "1T", "Touran"}, {"Volkswagen", "5T", "Touran"}, {"Volkswagen", "6R", "Polo"},
                {"Volkswagen", "6C", "Polo"}, {"Volkswagen", "AW", "Polo"}, {"Volkswagen", "A1", "T-Roc"}, {"Volkswagen", "D1", "T-Roc"}, {"Volkswagen", "C1", "T-Cross"},
                {"Volkswagen", "E1", "ID3"}, {"Volkswagen", "E2", "ID4"}, {"Volkswagen", "7N", "Sharan"}, {"Volkswagen", "3H", "Arteon"}, {"Volkswagen", "7P", "Touareg"},
                {"Volkswagen", "CR", "Touareg"}, {"Volkswagen", "AA", "Up"}, {"Volkswagen", "5C", "Beetle"}, {"Volkswagen", "13", "Scirocco"}, {"Volkswagen", "16", "Jetta"},
                {"Volkswagen", "BU", "Jetta"}, {"Volkswagen", "2H", "Amarok"}, {"Volkswagen", "7H", "T5"}, {"Volkswagen", "7E", "Transporter"}, {"Volkswagen", "SG", "Multivan"},
                {"Audi", "8V", "A3"}, {"Audi", "8Y", "A3"}, {"Audi", "8P", "A3"}, {"Audi", "8K", "A4"}, {"Audi", "8W", "A4"}, {"Audi", "8T", "A5"}, {"Audi", "F5", "A5"},
                {"Audi", "4G", "A6"}, {"Audi", "4A", "A6"}, {"Audi", "4K", "A7"}, {"Audi", "GA", "Q2"}, {"Audi", "8U", "Q3"}, {"Audi", "F3", "Q3"}, {"Audi", "8R", "Q5"},
                {"Audi", "FY", "Q5"}, {"Audi", "4L", "Q7"}, {"Audi", "4M", "Q7"}, {"Audi", "GE", "e-tron"}, {"Audi", "8J", "TT"}, {"Audi", "FV", "TT"}, {"Audi", "8X", "A1"}, {"Audi", "GB", "A1"},
                {"Skoda", "1Z", "Octavia"}, {"Skoda", "5E", "Octavia"}, {"Skoda", "NX", "Octavia"}, {"Skoda", "5J", "Fabia"}, {"Skoda", "NJ", "Fabia"}, {"Skoda", "PJ", "Fabia"},
                {"Skoda", "3T", "Superb"}, {"Skoda", "3V", "Superb"}, {"Skoda", "NS", "Kodiaq"}, {"Skoda", "NU", "Karoq"}, {"Skoda", "NW", "Scala"}, {"Skoda", "NH", "Rapid"},
                {"Skoda", "NY", "Enyaq"}, {"Skoda", "NA", "Kamiq"},
                {"Seat", "6J", "Ibiza"}, {"Seat", "KJ", "Ibiza"}, {"Seat", "1P", "Leon"}, {"Seat", "5F", "Leon"}, {"Seat", "KL", "Leon"}, {"Seat", "KH", "Arona"}, {"Seat", "5F", "Leon"},
                {"Seat", "5P", "Altea"}, {"Seat", "7N", "Alhambra"}, {"Seat", "KN", "Tarraco"}, {"Seat", "5N", "Ateca"},
            };
            String vagMake = mk.startsWith("Volkswagen") ? "Volkswagen" : mk.startsWith("Audi") ? "Audi" : mk.startsWith("Škoda") || mk.startsWith("Skoda") ? "Skoda"
                : mk.startsWith("Seat") || mk.startsWith("SEAT") ? "Seat" : mk.startsWith("Cupra") ? "Cupra" : null;
            if (vagMake != null) for (String[] e : t) if (e[0].equals(vagMake) && e[1].equals(code)) return e[0] + "-" + e[2];
        }
        return null;
    }

    /** Marca del VIN en el formato de OBDb (para filtrar la lista), o null. */
    static String vinMake(String vin) {
        String mk = vin != null ? Vin.maker(vin) : null;
        if (mk == null) return null;
        mk = mk.replace("Škoda", "Skoda").replace("Citroën", "Citroen").replace(" ", "-");
        int sp = mk.indexOf('(');
        if (sp > 0) mk = mk.substring(0, sp).replaceAll("-+$", "");
        return mk;
    }

    // ───────────────────────── archivo del modelo ─────────────────────────

    private static File file(Context c, String repo) {
        File d = new File(c.getFilesDir(), "obdb");
        d.mkdirs();
        return new File(d, repo.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    /**
     * Baja el archivo del modelo de GitHub (OBDb). Si el repositorio tiene un archivo para un rango de años
     * (p. ej. «2012-2020.json») que incluye el año del auto, se usa ese; si no, «default.json». En otro hilo.
     * Devuelve null si salió bien, o el motivo.
     */
    static String download(Context c, String rp, int year) {
        String name = "default.json";
        try {
            // lista de archivos (API de GitHub, sin clave: 60 consultas por hora alcanzan de sobra)
            JSONArray ls = new JSONArray(new String(Net.get("https://api.github.com/repos/OBDb/" + rp + "/contents/signalsets/v3", 20000), "UTF-8"));
            if (year > 0) for (int i = 0; i < ls.length(); i++) {
                String n = ls.getJSONObject(i).optString("name", "");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})-(\\d{4})\\.json").matcher(n);
                if (m.matches() && year >= Integer.parseInt(m.group(1)) && year <= Integer.parseInt(m.group(2))) { name = n; break; }
            }
        } catch (Throwable ignored) { }      // sin la lista, el archivo general
        byte[] b = null;
        Exception last = null;
        for (String branch : new String[]{"main", "master"}) {
            try {
                b = Net.get("https://raw.githubusercontent.com/OBDb/" + rp + "/" + branch + "/signalsets/v3/" + name, 30000);
                break;
            } catch (Exception e) { last = e; }
        }
        if (b == null) return I18n.t("No se pudo bajar el archivo del modelo") + (last != null ? ": " + last.getMessage() : "");
        try {
            new JSONObject(new String(b, "UTF-8")).getJSONArray("commands");     // que sea un archivo de OBDb
            FileOutputStream o = new FileOutputStream(file(c, rp));
            o.write(b);
            o.close();
            p(c).edit().putString("obdb_repo", rp).putString("obdb_file", name).putLong("obdb_at", System.currentTimeMillis()).remove("obdb_bad_" + rp).apply();
            VehicleDb.repo = null;   // se vuelve a cargar
            return null;
        } catch (Throwable e) {
            return I18n.t("El archivo del modelo no es válido");
        }
    }

    /** Carga (si cambió) el archivo del modelo elegido, para el año del auto. En el hilo del OBD. */
    static synchronized void load(Context c, int year) {
        String want = p(c).getString("obdb_repo", "");
        if (want.length() == 0) { repo = null; cmds = Collections.emptyList(); return; }
        if (want.equals(repo)) return;
        List<Cmd> out = new ArrayList<Cmd>();
        int tot = 0, sw = 0, sd = 0, sy = 0, sp = 0;
        try {
            File f = file(c, want);
            if (!f.exists()) { repo = want; cmds = out; status = "Falta bajar el archivo del modelo"; return; }
            FileInputStream in = new FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONArray a = new JSONObject(bo.toString("UTF-8")).getJSONArray("commands");
            java.util.Set<String> bad = new java.util.HashSet<String>(java.util.Arrays.asList(p(c).getString("obdb_bad_" + want, "").split(",")));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                tot++;
                JSONObject cmd = o.optJSONObject("cmd");
                if (cmd == null || cmd.length() != 1) { sw++; continue; }
                String svc = cmd.keys().next();
                // solo lectura: 22, 21 y 01; nada que cambie la sesión de diagnóstico
                if (!("22".equals(svc) || "21".equals(svc) || "01".equals(svc)) || o.has("din") || o.has("dout")) { sw++; continue; }
                if (o.optBoolean("dbg", false)) { sd++; continue; }
                String proto = o.optString("proto", "");
                if (proto.startsWith("9141") || proto.startsWith("14230") || o.has("baud") || o.has("init")) { sp++; continue; }
                JSONObject fl = o.optJSONObject("filter");
                if (fl != null && year > 0 && !inYears(fl, year)) { sy++; continue; }
                Cmd k = new Cmd();
                k.hdr = o.getString("hdr").toUpperCase(Locale.US);
                k.rax = o.has("rax") ? o.getString("rax").toUpperCase(Locale.US) : null;
                k.eax = o.has("eax") ? o.getString("eax").toUpperCase(Locale.US) : null;
                k.tst = o.has("tst") ? o.getString("tst").toUpperCase(Locale.US) : null;
                k.pri = o.has("pri") ? o.getString("pri").toUpperCase(Locale.US) : null;
                k.tmo = o.has("tmo") ? o.getString("tmo").toUpperCase(Locale.US) : null;
                k.fcm1 = o.optBoolean("fcm1", false);
                k.svc = Integer.parseInt(svc, 16);
                k.param = cmd.getString(svc).toUpperCase(Locale.US);
                if (!k.param.matches("[0-9A-F]{2}|[0-9A-F]{4}") || !k.hdr.matches("[0-9A-F]{3}|[0-9A-F]{4}")) { sw++; continue; }
                k.freq = Math.max(1, o.optDouble("freq", 5));
                JSONArray sg = o.getJSONArray("signals");
                java.util.Set<String> ids = new java.util.HashSet<String>();
                for (int j = 0; j < sg.length(); j++) ids.add(sg.getJSONObject(j).getString("id"));
                for (int j = 0; j < sg.length(); j++) k.sigs.add(J1979.parseSig(sg.getJSONObject(j), ids));
                if (bad.contains(k.key())) k.state = -1;
                out.add(k);
            }
            status = null;
        } catch (Throwable e) {
            status = "El archivo del modelo no es válido";
            Obd.log("OBDb " + want + ": " + e);
        }
        total = tot; skippedWrite = sw; skippedDebug = sd; skippedYear = sy; skippedProto = sp;
        repo = want;
        cmds = Collections.unmodifiableList(out);
        Obd.log("OBDb " + want + ": " + out.size() + " de " + tot + " comandos (descartados: " + sw + " no son de lectura, " + sd
            + " sin confirmar, " + sy + " de otros años, " + sp + " de línea K)");
    }

    private static boolean inYears(JSONObject f, int y) {
        JSONArray ys = f.optJSONArray("years");
        if (ys != null) for (int i = 0; i < ys.length(); i++) if (ys.optInt(i) == y) return true;
        boolean hasFrom = f.has("from"), hasTo = f.has("to");
        if (hasFrom && hasTo) {
            int from = f.optInt("from"), to = f.optInt("to");
            return from <= to ? y >= from && y <= to : y >= from || y <= to;
        }
        if (hasFrom) return y >= f.optInt("from");
        if (hasTo) return y <= f.optInt("to");
        return ys == null;
    }

    /** Los próximos comandos para leer (los no probados primero, después los que ya tocan), del mismo computador. */
    static List<Cmd> next(int max) {
        List<Cmd> out = new ArrayList<Cmd>();
        long now = SystemClock.elapsedRealtime();
        List<Cmd> list = cmds;
        String hdr = null;
        for (int pass = 0; pass < 2 && out.size() < max; pass++) {
            for (Cmd k : list) {
                if (out.size() >= max) break;
                boolean due = pass == 0 ? k.state == 0 : k.state == 1 && now - k.at >= k.freq * 1000;
                if (!due || out.contains(k)) continue;
                if (hdr == null) hdr = k.hdr;
                if (!k.hdr.equals(hdr)) continue;      // del mismo computador: menos cambios de encabezado
                out.add(k);
            }
        }
        return out;
    }

    /** Resultado de una lectura: datos (bytes después del identificador) o null si no contestó / no lo tiene. */
    static void result(Context c, Cmd k, byte[] data, boolean refused) {
        if (data != null) {
            k.data = data;
            k.at = SystemClock.elapsedRealtime();
            k.state = 1;
            k.fails = 0;
            return;
        }
        if (++k.fails >= (refused ? 1 : 2) && k.state != 1) {
            k.state = -1;
            // se recuerda para la próxima vez (no se vuelve a preguntar)
            SharedPreferences sp = p(c);
            String key = "obdb_bad_" + repo;
            String old = sp.getString(key, "");
            if (old.length() < 60000) sp.edit().putString(key, old + (old.length() > 0 ? "," : "") + k.key()).apply();
        }
    }

    /** Los valores leídos hace poco, agrupados por tema (path) y revisados. */
    static List<J1979.Val> values() {
        List<J1979.Val> out = new ArrayList<J1979.Val>();
        long now = SystemClock.elapsedRealtime();
        for (Cmd k : cmds) {
            if (k.state != 1 || k.data == null || now - k.at > k.freq * 3000 + 30000) continue;
            out.addAll(J1979.decodeAll(k.sigs, k.data, "v:" + k.key() + ":", true));
        }
        Collections.sort(out, new java.util.Comparator<J1979.Val>() {
            public int compare(J1979.Val a, J1979.Val b) {
                int c = String.valueOf(a.path).compareTo(String.valueOf(b.path));
                return c != 0 ? c : a.name.compareTo(b.name);
            }
        });
        return out;
    }

    static int count(int state) {
        int n = 0;
        for (Cmd k : cmds) if (k.state == state) n++;
        return n;
    }

    /** Nombre del tema (path de OBDb) en el idioma de la interfaz. */
    static String pathName(String p) {
        if (p == null || p.length() == 0) return I18n.t("Otros");
        Map<String, String> m = new HashMap<String, String>();
        String[][] t = {{"Engine", "Motor"}, {"Battery", "Batería"}, {"Climate", "Climatizador"}, {"Doors", "Puertas"}, {"Transmission", "Caja de cambios"},
            {"Tires", "Neumáticos"}, {"Trips", "Viajes"}, {"Movement", "Movimiento"}, {"Fuel", "Combustible"}, {"Lights", "Luces"}, {"Control", "Mandos"},
            {"Seatbelts", "Cinturones"}, {"Charging", "Carga"}, {"Brakes", "Frenos"}, {"Exhaust", "Escape"}, {"Emissions", "Emisiones"}, {"Steering", "Dirección"},
            {"Suspension", "Suspensión"}, {"Electrical", "Electricidad"}, {"Body", "Carrocería"}, {"ADAS", "Asistencias"}, {"Airbags", "Airbags"},
            {"Cooling", "Refrigeración"}, {"DTCs", "Códigos de falla"}, {"Maintenance", "Mantenimiento"}, {"Windows", "Ventanillas"}, {"Infotainment", "Multimedia"}};
        for (String[] e : t) m.put(e[0], e[1]);
        String top = p.contains(".") ? p.substring(0, p.indexOf('.')) : p;
        String es = m.get(top);
        return es != null ? I18n.t(es) : top;
    }
}
