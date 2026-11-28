package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Información de tráfico de Alemania sobre el mapa (solo lectura de datos públicos, sin clave):
 * <ul>
 * <li>Autobahn GmbH (verkehr.autobahn.de): avisos (atascos, accidentes, peligros), cierres, salidas cerradas, obras,
 * cargadores y áreas de descanso de las autopistas que pasan cerca del carro o de la ruta.</li>
 * <li>Tankerkönig (creativecommons.tankerkoenig.de): precios de combustible alrededor del carro, con la clave personal
 * del usuario y solo cuando él lo pide (botón en el mapa), como piden sus reglas: como mucho una consulta por minuto,
 * sin consultas automáticas, sin filtrar estaciones, con la atribución «Tankerkönig.de, CC BY 4.0, MTS-K» a la
 * vista y sin pasar los datos a nadie.</li>
 * <li>InfraNode (infranode.dev): ocupación de estacionamientos y avisos meteorológicos oficiales (DWD) de la ciudad
 * más cercana (84 ciudades grandes), con la atribución que trae cada dato. Sus precios de combustible no se usan:
 * Tankerkönig no permite pasar sus datos a terceros.</li>
 * </ul>
 * Fuera de Alemania no pide nada. Cada capa se prende o se apaga en Ajustes › Mapa. Todo se pide con un
 * User-Agent que dice qué es EvOS y se guarda solo en memoria (nada se publica ni se reenvía).
 */
final class Infra {
    static final int WARN = 0, CLOSED = 1, RAMP = 2, WORKS = 3, CHARGE = 4, REST = 5, FUEL = 6, PARK = 7;
    static final String[] TYPE_ES = {"Aviso de tráfico", "Vía cerrada", "Salida o entrada cerrada", "Obras", "Cargador eléctrico",
        "Área de descanso", "Gasolinera", "Estacionamiento"};

    static final class Item {
        int type;
        String id, road, title, sub;
        String[] desc = new String[0];
        double lat, lon;
        float[] line;                  // lat, lon, lat, lon… (tramo afectado), o null
        boolean blocked;
        boolean heavy;                 // cierre solo para camiones (por peso o por «LKW»): no se avisa a un auto
        String event;                  // aviso: lo que pasa, ya en español (o el texto alemán si no se conoce)
        int delayMin = -1, avgKmh = -1;
        float price = Float.NaN;       // gasolinera: precio del combustible elegido (€/L)
        int free = -1, cap = -1;       // estacionamiento
        boolean cheapest;
        float distKm;                  // al carro, en la última revisión
        float routeKm = -1;            // por la ruta, desde el carro (navegando); -1 si no está en la ruta
        boolean closed;                // gasolinera cerrada ahora
    }

    // ── lo que se ve ──
    static volatile List<Item> items = Collections.emptyList();
    static volatile String city, cityName, roadsNow = "", lastError, wxEvent;
    /** Atribución de cada fuente que se está mostrando (la que exige su licencia). */
    static volatile String wxAttr, parkAttr;
    static volatile int wxLevel;
    static volatile String fuelKindNow;
    static volatile long updatedAt;              // System.currentTimeMillis()
    static volatile boolean inGermany;
    static volatile Item alert;                  // aviso por delante (se muestra unos segundos)
    /** Hay un aviso de la ruta para decir en voz alta (lo anuncia la descarga, en otro hilo). */
    static volatile boolean speakPending;
    static volatile long alertAt;
    static volatile int gen;
    /** Última posición del GPS que llegó (para el botón de precios). */
    static volatile double posLa = Double.NaN, posLo = Double.NaN;                     // cambia con cada actualización (para redibujar)
    /** Solo en la versión de prueba: un aviso inventado por delante para la captura. */
    static volatile boolean demo;

    // ── índice de autopistas y ciudades (assets/infra) ──
    private static double LAT0, LON0, DLA, DLO;
    private static int ROWS, COLS;
    private static final Map<String, java.util.HashSet<Integer>> ROAD_CELLS = new LinkedHashMap<String, java.util.HashSet<Integer>>();
    private static final List<String[]> CITIES = new ArrayList<String[]>();
    private static boolean loaded;

    private static final class Road {
        List<Item> warn = new ArrayList<Item>(), works = new ArrayList<Item>(), fixed = new ArrayList<Item>();
        long tWarn, tWorks, tFixed;
    }
    private static final Map<String, Road> ROADS = new HashMap<String, Road>();
    private static List<Item> park = new ArrayList<Item>();
    private static long tPark, tWx;
    private static String parkCity, wxCity;
    /** Avisos de la ruta ya anunciados (una vez por suceso). */
    private static final java.util.Set<String> announced = new java.util.HashSet<String>();
    private static double cLat = Double.NaN, cLon = Double.NaN;    // donde se armó la lista
    private static long lastRun;
    private static volatile boolean busy;
    private static final java.util.Set<String> alerted = new java.util.HashSet<String>();
    private static final Map<String, Float> lastDist = new HashMap<String, Float>();

    private static final String AB = "https://verkehr.autobahn.de/o/autobahn/";
    private static final String IN = "https://infranode.dev/api/v1/cities/";

    // ───────────────────────── ajustes ─────────────────────────

    static SharedPreferences p(Context c) { return c.getSharedPreferences("launcher", 0); }
    static boolean on(Context c) { return p(c).getBoolean("infra_on", true); }
    static boolean layer(Context c, String k) {
        boolean def = !"infra_charge".equals(k) && !"infra_rest".equals(k) && !"infra_park".equals(k) && !"infra_voice".equals(k);
        return p(c).getBoolean(k, def);
    }
    /** Minutos entre actualizaciones de avisos y cierres (5, 10 o 15). */
    static int every(Context c) { return p(c).getInt("infra_every", 5); }
    /** Combustible para los precios: e5, e10, diesel; «auto» = el que dice el auto por OBD (diésel o gasolina E5). */
    static String fuelKind(Context c) {
        String k = p(c).getString("infra_fuel_kind", "auto");
        if (!"auto".equals(k)) return k;
        int ft = Obd.fuelType;
        if (ft == 4 || ft == 0x0B || ft == 0x13) return "diesel";
        return "e5";
    }
    /** Distancia (km) a la que avisa de un incidente por delante. */
    static int alertKm(Context c) { return p(c).getInt("infra_alert_km", 3); }

    // ───────────────────────── índice ─────────────────────────

    private static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("infra/autobahn.txt"), "UTF-8"));
            String l;
            boolean head = false;
            while ((l = r.readLine()) != null) {
                if (l.startsWith("#") || l.trim().length() == 0) continue;
                String[] a = l.trim().split(" ");
                if (!head) {
                    LAT0 = Double.parseDouble(a[0]); LON0 = Double.parseDouble(a[1]);
                    DLA = Double.parseDouble(a[2]); DLO = Double.parseDouble(a[3]);
                    ROWS = Integer.parseInt(a[4]); COLS = Integer.parseInt(a[5]);
                    head = true;
                    continue;
                }
                java.util.HashSet<Integer> s = new java.util.HashSet<Integer>();
                for (int i = 1; i < a.length; i++) s.add(Integer.parseInt(a[i], 36));
                ROAD_CELLS.put(a[0], s);
            }
            r.close();
            r = new BufferedReader(new InputStreamReader(c.getAssets().open("infra/cities.txt"), "UTF-8"));
            while ((l = r.readLine()) != null) {
                if (l.startsWith("#") || l.trim().length() == 0) continue;
                String[] a = l.trim().split(" ", 4);
                if (a.length == 4) CITIES.add(a);
            }
            r.close();
        } catch (Throwable e) { lastError = "índice: " + e; }
    }

    private static int cell(double la, double lo) {
        int r = (int) Math.floor((la - LAT0) / DLA), k = (int) Math.floor((lo - LON0) / DLO);
        return r >= 0 && r < ROWS && k >= 0 && k < COLS ? r * COLS + k : -1;
    }

    /** Autopistas que pasan a ~10 km del punto, de la más segura a la menos (celdas que coinciden). */
    private static List<String> roadsNear(double la, double lo, Map<String, Integer> score, int weight) {
        List<String> out = new ArrayList<String>();
        int r0 = (int) Math.floor((la - LAT0) / DLA), k0 = (int) Math.floor((lo - LON0) / DLO);
        for (Map.Entry<String, java.util.HashSet<Integer>> e : ROAD_CELLS.entrySet()) {
            int hit = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dk = -1; dk <= 1; dk++) {
                int r = r0 + dr, k = k0 + dk;
                if (r < 0 || r >= ROWS || k < 0 || k >= COLS) continue;
                if (e.getValue().contains(r * COLS + k)) hit += dr == 0 && dk == 0 ? 3 : 1;
            }
            if (hit > 0) {
                out.add(e.getKey());
                Integer was = score.get(e.getKey());
                score.put(e.getKey(), (was != null ? was : 0) + hit * weight);
            }
        }
        return out;
    }

    /** Dentro de Alemania (aprox.): cerca de alguna autopista o de una de las 84 ciudades. */
    private static boolean germany(double la, double lo) {
        if (la < LAT0 || la > LAT0 + ROWS * DLA || lo < LON0 || lo > LON0 + COLS * DLO) return false;
        int r0 = (int) Math.floor((la - LAT0) / DLA), k0 = (int) Math.floor((lo - LON0) / DLO);
        for (java.util.HashSet<Integer> s : ROAD_CELLS.values())
            for (int dr = -3; dr <= 3; dr++) for (int dk = -3; dk <= 3; dk++) if (s.contains((r0 + dr) * COLS + k0 + dk)) return true;
        return nearestCity(la, lo, 40) != null;
    }

    private static String[] nearestCity(double la, double lo, double maxKm) {
        String[] best = null;
        double bd = maxKm;
        for (String[] c : CITIES) {
            double d = km(la, lo, Double.parseDouble(c[1]), Double.parseDouble(c[2]));
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    static double km(double la1, double lo1, double la2, double lo2) {
        double dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLa / 2) * Math.sin(dLa / 2) + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static float bearing(double la1, double lo1, double la2, double lo2) {
        double y = Math.sin(Math.toRadians(lo2 - lo1)) * Math.cos(Math.toRadians(la2));
        double x = Math.cos(Math.toRadians(la1)) * Math.sin(Math.toRadians(la2))
            - Math.sin(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.cos(Math.toRadians(lo2 - lo1));
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    // ───────────────────────── cada posición ─────────────────────────

    /**
     * Con cada posición del GPS (hilo principal): decide si hay que actualizar (en otro hilo) y revisa si hay un aviso
     * por delante. Devuelve true si apareció un aviso nuevo.
     */
    static boolean tick(final Context c, final double la, final double lo, float br, float kmh) {
        if (!on(c)) {
            if (!items.isEmpty() || wxEvent != null) { items = Collections.<Item>emptyList(); wxEvent = null; gen++; }
            inGermany = false;
            return false;
        }
        load(c);
        posLa = la; posLo = lo;
        if (ROWS == 0) return false;
        boolean de = germany(la, lo);
        if (de != inGermany) { inGermany = de; gen++; }
        if (!de && !demo) return false;
        long now = SystemClock.elapsedRealtime();
        boolean moved = Double.isNaN(cLat) || km(la, lo, cLat, cLon) > 8;
        if (!busy && (moved || now - lastRun > 60000)) {
            busy = true;
            lastRun = now;
            final Context app = c.getApplicationContext();
            new Thread(new Runnable() { public void run() {
                try { refresh(app, la, lo); } catch (Throwable e) { lastError = String.valueOf(e); }
                finally { busy = false; }
            }}, "infra").start();
        }
        return checkAhead(c, la, lo, br, kmh);
    }

    /** Aviso por delante: avisos y cierres a menos de N km, en la dirección de marcha (±30°) y acercándose. */
    private static boolean checkAhead(Context c, double la, double lo, float br, float kmh) {
        if (!layer(c, "infra_alert")) return false;
        List<Item> list = items;
        float lim = alertKm(c);
        boolean fresh = false;
        for (Item it : list) {
            if (it.heavy || !(it.type == WARN || (it.type == CLOSED || it.type == WORKS) && it.blocked)) continue;
            float d = (float) km(la, lo, it.lat, it.lon);
            Float was = lastDist.put(it.id, d);
            if (alerted.contains(it.id) || d > lim || Float.isNaN(br) || kmh < 30) continue;
            float diff = Math.abs(((bearing(la, lo, it.lat, it.lon) - br) + 540) % 360 - 180);
            if (diff > 30 || was == null || was <= d) continue;
            alerted.add(it.id);
            it.distKm = d;
            alert = it;
            alertAt = SystemClock.elapsedRealtime();
            fresh = true;
            gen++;
        }
        return fresh;
    }

    /** Texto corto del aviso, para la pantalla y la voz. */
    static String alertText(Item it) {
        String what = it.event != null ? I18n.t(it.event) : I18n.t(TYPE_ES[it.type]);
        return I18n.f("%s en %s km", what, String.format(I18n.num, "%.1f", it.distKm));
    }

    // ───────────────────────── descarga ─────────────────────────

    private static void refresh(Context c, double la, double lo) throws Exception {
        long now = System.currentTimeMillis();
        long ev = every(c) * 60000L;
        // autopistas: las de cerca del carro y las de la ruta (hasta 150 km por delante), máximo 6
        Map<String, Integer> score = new HashMap<String, Integer>();
        List<String> nearCar = roadsNear(la, lo, score, 10);
        // navegando: las autopistas de toda la ruta que queda por delante (avisos y cierres de todo el camino)
        Nav.Route rt = Nav.active;
        double[] ahead = rt != null && rt.pts != null && rt.pts.length >= 4 ? routeAhead(rt, la, lo) : null;
        if (ahead != null) for (int i = 0; i + 2 < ahead.length; i += 3) roadsNear(ahead[i], ahead[i + 1], score, 1);
        List<Map.Entry<String, Integer>> rank = new ArrayList<Map.Entry<String, Integer>>(score.entrySet());
        Collections.sort(rank, new java.util.Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); }
        });
        List<String> roads = new ArrayList<String>();
        for (int i = 0; i < rank.size() && roads.size() < (ahead != null ? 10 : 6); i++) roads.add(rank.get(i).getKey());
        boolean wWarn = layer(c, "infra_warn") || layer(c, "infra_closed") || layer(c, "infra_alert");
        boolean wWorks = layer(c, "infra_works");
        boolean wFixed = layer(c, "infra_charge") || layer(c, "infra_rest");
        String err = null;
        for (String r : roads) {
            Road rd = ROADS.get(r);
            if (rd == null) { rd = new Road(); ROADS.put(r, rd); }
            try {
                if (wWarn && now - rd.tWarn > ev) {
                    List<Item> l = new ArrayList<Item>();
                    parseAb(r, "warning", WARN, l);
                    parseAb(r, "closure", CLOSED, l);
                    rd.warn = l; rd.tWarn = now;
                }
                if (wWorks && nearCar.contains(r) && now - rd.tWorks > 30 * 60000L) {     // obras (pesadas): solo las de cerca
                    List<Item> l = new ArrayList<Item>();
                    parseAb(r, "roadworks", WORKS, l);
                    rd.works = l; rd.tWorks = now;
                }
                if (wFixed && now - rd.tFixed > 6 * 3600000L) {
                    List<Item> l = new ArrayList<Item>();
                    parseAb(r, "electric_charging_station", CHARGE, l);
                    parseAb(r, "parking_lorry", REST, l);
                    rd.fixed = l; rd.tFixed = now;
                }
            } catch (Throwable e) { err = "Autobahn " + r + ": " + e.getMessage(); }
        }
        // ciudad más cercana (InfraNode): precios, estacionamientos, avisos meteorológicos
        String[] ct = nearestCity(la, lo, 30);
        city = ct != null ? ct[0] : null;
        cityName = ct != null ? ct[3] : null;
        try {
            if (ct != null && layer(c, "infra_park") && km(la, lo, Double.parseDouble(ct[1]), Double.parseDouble(ct[2])) < 15
                    && (!ct[0].equals(parkCity) || now - tPark > 5 * 60000L)) {
                park = parsePark(get(IN + ct[0] + "/parking"));
                parkCity = ct[0]; tPark = now;
            }
            if (ct != null && layer(c, "infra_wx") && (!ct[0].equals(wxCity) || now - tWx > 15 * 60000L)) {
                parseWx(get(IN + ct[0] + "/weather-warnings"));
                wxCity = ct[0]; tWx = now;
            }
        } catch (Throwable e) { err = "InfraNode " + (ct != null ? ct[0] : "") + ": " + e.getMessage(); }
        if (ct == null || !layer(c, "infra_wx")) { wxEvent = null; wxLevel = 0; }

        // lo que se ve: solo cerca (60 km; salidas cerradas, cargadores y descansos a 30 km)
        List<Item> out = new ArrayList<Item>();
        for (String r : roads) {
            Road rd = ROADS.get(r);
            if (rd == null) continue;
            for (Item it : rd.warn) {
                if (it.type == RAMP ? !layer(c, "infra_closed") : it.type == CLOSED ? !layer(c, "infra_closed") : !layer(c, "infra_warn") && !layer(c, "infra_alert")) continue;
                onRoute(it, ahead);
                if (it.routeKm >= 0 && it.type != RAMP) { it.distKm = (float) km(la, lo, it.lat, it.lon); out.add(it); }
                else add(out, it, la, lo, it.type == RAMP ? 30 : 60);
            }
            if (wWorks) for (Item it : rd.works) { onRoute(it, ahead); add(out, it, la, lo, 30); }
            for (Item it : rd.fixed) if (layer(c, it.type == CHARGE ? "infra_charge" : "infra_rest")) add(out, it, la, lo, 30);
        }
        // navegando: el suceso más cercano de la ruta que no se anunció todavía («En tu ruta, a 42 km: atasco»)
        if (ahead != null && layer(c, "infra_alert")) {
            Item first = null;
            for (Item it : out) {
                if (it.routeKm < 0 || it.heavy || announced.contains(it.id)) continue;
                if (!(it.type == WARN || it.type == CLOSED && it.blocked)) continue;
                if (first == null || it.routeKm < first.routeKm) first = it;
            }
            for (Item it : out) if (it.routeKm >= 0 && (it.type == WARN || it.type == CLOSED)) announced.add(it.id);
            if (first != null && first.routeKm > alertKm(c)) {
                first.distKm = first.routeKm;
                alert = first;
                alertAt = SystemClock.elapsedRealtime();
                speakPending = true;
            }
        } else announced.clear();
        // precios pedidos por el usuario (Tankerkönig): se muestran 30 min, después ya no son de fiar
        if (layer(c, "infra_fuel") && System.currentTimeMillis() - tkAt < 30 * 60000L) out.addAll(tkItems);
        if (layer(c, "infra_park") && city != null && city.equals(parkCity)) for (Item it : park) add(out, it, la, lo, 15);
        if (demo) {
            out.add(demoItem(la, lo));
            // gasolineras inventadas (solo en la prueba: la clave de Tankerkönig no se publica)
            {   // se rehacen alrededor del carro en cada vuelta (solo en la prueba)
                out.removeAll(tkItems);
                List<Item> f = new ArrayList<Item>();
                float[][] st = {{0.004f, -0.006f, 1.839f}, {-0.006f, 0.004f, 1.799f}, {0.007f, 0.009f, Float.NaN}};
                for (int i = 0; i < st.length; i++) {
                    Item it = new Item();
                    it.type = FUEL; it.id = "demo-fuel" + i; it.title = "Demo " + (i + 1);
                    it.lat = la + st[i][0]; it.lon = lo + st[i][1]; it.price = st[i][2];
                    it.cheapest = i == 1; it.closed = i == 2; it.desc = new String[]{"Demo"};
                    f.add(it);
                }
                tkItems = f;
                tkAt = System.currentTimeMillis();
                fuelKindNow = "diesel";
            }
            if (!tkItems.isEmpty() && !out.contains(tkItems.get(0))) out.addAll(tkItems);
        }
        StringBuilder rs = new StringBuilder();
        for (String r : roads) rs.append(rs.length() > 0 ? ", " : "").append(r);
        roadsNow = rs.toString();
        items = Collections.unmodifiableList(out);
        cLat = la; cLon = lo;
        updatedAt = now;
        lastError = err;
        gen++;
    }

    /**
     * Puntos de la ruta que quedan por delante del carro, cada ~1 km: lat, lon, km desde el carro (por la ruta).
     */
    private static double[] routeAhead(Nav.Route rt, double la, double lo) {
        double[] p = rt.pts;
        int n = p.length / 2, best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = fastKm(la, lo, p[i * 2], p[i * 2 + 1]);
            if (d < bd) { bd = d; best = i; }
        }
        List<Double> out = new ArrayList<Double>();
        double acc = 0, since = 1e9;
        for (int i = best; i < n; i++) {
            if (i > best) {
                double step = fastKm(p[i * 2 - 2], p[i * 2 - 1], p[i * 2], p[i * 2 + 1]);
                acc += step;
                since += step;
            }
            if (since >= 1 || i == n - 1) { out.add(p[i * 2]); out.add(p[i * 2 + 1]); out.add(acc); since = 0; }
        }
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** Distancia aproximada (equirectangular): rápida y precisa a pocos km. */
    private static double fastKm(double la1, double lo1, double la2, double lo2) {
        double x = (lo2 - lo1) * Math.cos(Math.toRadians((la1 + la2) / 2)) * 111.32, y = (la2 - la1) * 110.57;
        return Math.sqrt(x * x + y * y);
    }

    /** Marca el suceso como «en la ruta» (a menos de 1 km de un punto de la ruta por delante) y a cuántos km. */
    private static void onRoute(Item it, double[] ahead) {
        it.routeKm = -1;
        if (ahead == null) return;
        double best = 1.0;
        for (int i = 0; i + 2 < ahead.length; i += 3) {
            if (Math.abs(ahead[i] - it.lat) > 0.05) continue;
            double d = fastKm(ahead[i], ahead[i + 1], it.lat, it.lon);
            if (d < best) { best = d; it.routeKm = (float) ahead[i + 2]; }
        }
    }

    private static void add(List<Item> out, Item it, double la, double lo, double maxKm) {
        double d = km(la, lo, it.lat, it.lon);
        if (d > maxKm && it.line != null) {      // un tramo largo puede empezar lejos y pasar cerca
            for (int i = 0; i + 1 < it.line.length; i += 2) d = Math.min(d, km(la, lo, it.line[i], it.line[i + 1]));
        }
        if (d <= maxKm) { it.distKm = (float) d; out.add(it); }
    }

    private static String get(String url) throws Exception {
        return new String(Net.get(url, 30000), "UTF-8");
    }

    // ───────────────────────── Autobahn ─────────────────────────

    private static void parseAb(String road, String svc, int type, List<Item> out) throws Exception {
        JSONObject o = new JSONObject(get(AB + road + "/services/" + svc));
        JSONArray a = o.optJSONArray(svc);
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.optJSONObject(i);
            if (j == null) continue;
            if (j.optBoolean("future", false) || "true".equals(j.optString("future"))) continue;   // todavía no empieza
            JSONObject co = j.optJSONObject("coordinate");
            if (co == null) continue;
            Item it = new Item();
            it.type = type;
            String dt = j.optString("display_type", "");
            if (type == CLOSED && dt.startsWith("CLOSURE_ENTRY_EXIT")) it.type = RAMP;
            it.road = road;
            it.id = svc + ":" + j.optString("identifier", road + i);
            it.lat = co.optDouble("lat", Double.NaN);
            it.lon = co.optDouble("long", Double.NaN);
            if (Double.isNaN(it.lat) || Double.isNaN(it.lon) || Math.abs(it.lat) > 90) continue;
            it.title = j.optString("title", road).trim();
            it.sub = j.optString("subtitle", "").trim();
            it.blocked = "true".equals(j.optString("isBlocked")) || j.optBoolean("isBlocked", false);
            JSONArray d = j.optJSONArray("description");
            if (d != null) {
                it.desc = new String[d.length()];
                for (int k = 0; k < d.length(); k++) it.desc[k] = d.optString(k, "");
            }
            it.delayMin = parseInt(j.optString("delayTimeValue", ""));
            it.avgKmh = parseInt(j.optString("averageSpeed", ""));
            if (type == CLOSED && (dt.startsWith("CLOSURE_LOAD") || joined(it.desc).contains("Schwerlastverkehr") || joined(it.desc).contains("LKW-Sperrung"))) {
                it.heavy = true;
                it.event = "Cerrado para camiones";
                String vz = j.optString("icon", "");
                if (vz.startsWith("VZ ")) it.event = I18n.t("Cerrado para camiones") + " (> " + vz.substring(3).trim() + " t)";
            }
            if (type == REST) {             // título «A1 | Saarbrücken» = sentido; el nombre viene en el subtítulo
                String dir = it.title.contains("|") ? it.title.substring(it.title.indexOf('|') + 1).trim() : "";
                it.title = (it.sub.length() > 0 ? it.sub : road) + " | " + road;
                it.sub = dir;
            }
            if (type == CHARGE) it.sub = "";
            if (type == WARN) {
                for (String l : it.desc) if (l.startsWith("- ")) {
                    String e = event(l.substring(2).trim());
                    if (e != null) { it.event = e; break; }
                }
                if (it.event == null) it.event = "Aviso de tráfico";
            }
            JSONObject g = j.optJSONObject("geometry");
            JSONArray cs = g != null ? g.optJSONArray("coordinates") : null;
            if (cs != null && "LineString".equals(g.optString("type")) && cs.length() >= 2) {
                int n = cs.length(), step = Math.max(1, n / 60);
                List<Float> f = new ArrayList<Float>();
                for (int k = 0; k < n; k += step) {
                    JSONArray pt = cs.optJSONArray(k);
                    if (pt != null && pt.length() >= 2) { f.add((float) pt.optDouble(1)); f.add((float) pt.optDouble(0)); }
                }
                JSONArray last = cs.optJSONArray(n - 1);
                if (last != null && (n - 1) % step != 0) { f.add((float) last.optDouble(1)); f.add((float) last.optDouble(0)); }
                it.line = new float[f.size()];
                for (int k = 0; k < f.size(); k++) it.line[k] = f.get(k);
            }
            out.add(it);
        }
    }

    private static String joined(String[] a) {
        StringBuilder b = new StringBuilder();
        for (String x : a) b.append(x).append(' ');
        return b.toString();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable e) { return -1; }
    }

    /** Qué pasa, de alemán a una frase en español (que luego se traduce); null si es un dato y no un suceso. */
    static String event(String de) {
        String l = de.toLowerCase(Locale.GERMAN);
        if (l.startsWith("durchschnittsgeschwindigkeit") || l.startsWith("reisezeitverlust")) return null;
        if (l.contains("falschfahrer")) return "Conductor en sentido contrario";
        if (l.contains("unfall")) return "Accidente";
        if (l.contains("vollsperrung") || l.contains("gesperrt") && !l.contains("fahrstreifen")) return "Vía cerrada";
        if (l.contains("fahrstreifen") && l.contains("gesperrt")) return "Carril cerrado";
        if (l.contains("stockend")) return "Tráfico lento";
        if (l.contains("stau")) return "Atasco";
        if (l.contains("pannenfahrzeug") || l.contains("liegengebliebene")) return "Vehículo averiado";
        if (l.contains("gegenst")) return "Objetos en la vía";
        if (l.contains("personen auf")) return "Personas en la vía";
        if (l.contains("tiere auf")) return "Animales en la vía";
        if (l.contains("glätte") || l.contains("glatteis")) return "Vía resbaladiza";
        if (l.contains("nebel") || l.contains("sichtbehinderung")) return "Visibilidad reducida";
        if (l.contains("brückenschäden")) return "Daños en un puente";
        if (l.contains("fahrbahnschäden")) return "Daños en la calzada";
        if (l.contains("baustelle") || l.contains("arbeiten")) return "Obras";
        if (l.contains("gefahr")) return "Peligro";
        return de;   // desconocido: el texto original (en alemán)
    }

    /**
     * Detalle para mostrar (líneas ya traducidas): lo que pasa, el sentido, el tramo, las fechas, el largo, el
     * retraso; y al final el texto original en alemán.
     */
    static String details(Item it) {
        StringBuilder b = new StringBuilder();
        if (it.sub != null && it.sub.length() > 0) b.append(I18n.t(it.type == REST ? "Hacia" : "Sentido")).append(": ").append(it.sub.replace("->", "→")).append('\n');
        if (it.heavy && it.event != null) b.append("• ").append(I18n.t(it.event)).append('\n');
        if (it.type == WARN || it.type == CLOSED || it.type == RAMP || it.type == WORKS) {
            List<String> ev = new ArrayList<String>();
            for (String l : it.desc) if (l.startsWith("- ")) {
                String e = event(l.substring(2).trim());
                if (e != null && !ev.contains(e)) ev.add(e);
            }
            for (String e : ev) b.append("• ").append(I18n.t(e)).append('\n');
            if (it.avgKmh > 0) b.append(I18n.f("Velocidad media: %d km/h", it.avgKmh)).append('\n');
            if (it.delayMin > 0) b.append(I18n.f("Retraso: %d min", it.delayMin)).append('\n');
            if (it.blocked) b.append(I18n.t("La vía está cerrada")).append('\n');
            for (String l : it.desc) {
                String t = l.trim();
                if (t.startsWith("Beginn:")) b.append(I18n.t("Desde")).append(": ").append(when(t.substring(7))).append('\n');
                else if (t.startsWith("Ende:")) b.append(I18n.t("Hasta")).append(": ").append(when(t.substring(5))).append('\n');
                else if (t.startsWith("Länge:")) {
                    for (String part : t.split("\\|")) {
                        String q = part.trim();
                        if (q.startsWith("Länge:") && !q.contains("nicht")) b.append(I18n.t("Largo")).append(": ").append(q.substring(6).trim()).append('\n');
                        else if (q.startsWith("Maximale Durchfahrtsbreite:")) b.append(I18n.t("Ancho máximo")).append(": ").append(q.substring(27).trim()).append('\n');
                    }
                }
            }
        } else if (it.type == CHARGE) {
            int pts = 0;
            for (String l : it.desc) if (l.trim().matches("Ladepunkt \\d+:")) pts++;
            if (pts > 0) b.append(I18n.f("Puntos de carga: %s", "" + pts)).append('\n');
            for (String l : it.desc) {
                String t = l.trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*Ladepunkte?").matcher(t);
                if (m.find()) b.append(I18n.f("Puntos de carga: %s", m.group(1))).append('\n');
                else if (t.matches("\\d+\\+?\\s*kW")) b.append(I18n.t("Potencia")).append(": ").append(t).append('\n');
                else if (t.contains("CCS") || t.contains("Typ 2") || t.contains("CHAdeMO")) b.append(t.replace("Kupplung", "").replace("Stecker", "").trim()).append('\n');
                else if (t.startsWith("Ladesäulenbetreiber:")) b.append(I18n.t("Operador")).append(": ").append(t.substring(20).trim()).append('\n');
            }
        } else if (it.type == REST) {
            for (String l : it.desc) {
                String t = l.trim();
                if (t.startsWith("PKW Stellplätze:")) b.append(I18n.f("Lugares para autos: %s", t.substring(16).trim())).append('\n');
                else if (t.startsWith("LKW Stellplätze:")) b.append(I18n.f("Lugares para camiones: %s", t.substring(16).trim())).append('\n');
            }
        } else if (it.type == FUEL) {
            b.append(fuelLabel(fuelKindNow)).append(": ").append(Float.isNaN(it.price) ? I18n.t("no lo vende o sin precio")
                : String.format(I18n.num, "%.3f €", it.price)).append('\n');
            if (it.closed) b.append(I18n.t("Cerrada ahora")).append('\n');
            for (String l : it.desc) if (l.length() > 0) b.append(l).append('\n');
            if (tkAt > 0) b.append(I18n.t("Precios de las")).append(' ')
                .append(new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date(tkAt))).append('\n');
            b.append(I18n.t("Precios: Tankerkönig.de, CC BY 4.0, MTS-K")).append('\n');
        } else if (it.type == PARK) {
            if (it.free >= 0) b.append(I18n.f("Libres: %d de %d", it.free, Math.max(it.cap, it.free))).append('\n');
            for (String l : it.desc) b.append(l).append('\n');
            if (parkAttr != null) b.append(I18n.t("Fuente")).append(": ").append(parkAttr).append(" (InfraNode)").append('\n');
        }
        if (it.routeKm >= 0) b.append(I18n.f("En tu ruta, a %s km", String.format(I18n.num, "%.0f", it.routeKm))).append('\n');
        else if (it.distKm > 0) b.append(I18n.f("A %s km de ti", String.format(I18n.num, "%.1f", it.distKm))).append('\n');
        if (it.type <= WORKS || it.type == CHARGE || it.type == REST) {
            b.append('\n').append(I18n.t("Texto original (alemán), Die Autobahn GmbH des Bundes:")).append('\n');
            for (String l : it.desc) if (l.trim().length() > 0) b.append(l.trim()).append('\n');
        }
        return b.toString().trim();
    }

    /** «09.11.26 um 03:00 Uhr» → «09.11.26 03:00». */
    private static String when(String s) {
        return s.replace(" um ", " ").replace(" Uhr", "").trim();
    }

    static String fuelLabel(String k) {
        return "diesel".equals(k) ? I18n.t("Diésel") : "e10".equals(k) ? I18n.t("Gasolina E10") : I18n.t("Gasolina E5");
    }

    // ───────────────────────── InfraNode ─────────────────────────

    // ───────────────────────── Tankerkönig ─────────────────────────

    static volatile List<Item> tkItems = Collections.emptyList();
    static volatile long tkAt;                 // cuándo llegaron los precios (System.currentTimeMillis)
    private static volatile long tkAsked;      // última consulta (aunque haya fallado)
    private static double tkLat = Double.NaN, tkLon = Double.NaN;
    private static String tkKind;
    static volatile boolean tkBusy;
    /** Tankerkönig dijo que no (clave mala o bloqueada): no se le pregunta más hasta que cambie la clave. */
    static volatile String tkRefused;
    private static String tkRefusedKey;

    static String tkKey(Context c) { return p(c).getString("tk_key", "").trim(); }
    static boolean tkKeyOk(String k) { return k.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"); }
    /** Radio de búsqueda en km (Tankerkönig permite hasta 25). */
    static int tkRadius(Context c) { return Math.max(1, Math.min(25, p(c).getInt("tk_rad", 10))); }

    /** El botón de precios del mapa: capa encendida, clave puesta y en Alemania. */
    static boolean fuelButton(Context c) {
        return on(c) && layer(c, "infra_fuel") && (tkKeyOk(tkKey(c)) && inGermany || demo);
    }

    /**
     * Precios alrededor del carro, porque el usuario apretó el botón: una consulta list.php (todas las estaciones del
     * radio, sin filtrar, ordenadas por distancia). Como mucho una por minuto; si ya hay precios de hace menos de
     * 5 minutos para el mismo lugar y combustible, se usan esos sin preguntar de nuevo. Devuelve el mensaje para
     * mostrar (null si todo bien); en otro hilo.
     */
    static String requestFuel(Context c, double la, double lo) {
        String key = tkKey(c);
        if (!tkKeyOk(key)) return "Escribe tu clave de Tankerkönig en Ajustes › Mapa";
        if (tkRefused != null && key.equals(tkRefusedKey)) return I18n.t("Tankerkönig rechazó la clave") + ": " + tkRefused;
        String fk = fuelKind(c);
        int rad = tkRadius(c);
        long now = System.currentTimeMillis();
        boolean near = !Double.isNaN(tkLat) && km(la, lo, tkLat, tkLon) < rad / 3.0;
        if (near && fk.equals(tkKind) && now - tkAt < 5 * 60000L) { publish(); return null; }
        if (now - tkAsked < 60000L) return "Tankerkönig permite una consulta por minuto: espera un momento";
        if (tkBusy) return null;
        tkBusy = true;
        tkAsked = now;
        try {
            String url = "https://creativecommons.tankerkoenig.de/json/list.php?lat=" + String.format(Locale.US, "%.3f", la)
                + "&lng=" + String.format(Locale.US, "%.3f", lo) + "&rad=" + rad + "&sort=dist&type=all&apikey=" + key;
            JSONObject o = new JSONObject(get(url));
            if (!o.optBoolean("ok", false)) {
                tkRefused = o.optString("message", "?");
                tkRefusedKey = key;
                return I18n.t("Tankerkönig rechazó la clave") + ": " + tkRefused;
            }
            tkItems = Collections.unmodifiableList(parseTk(o, fk, la, lo));
            tkAt = now;
            tkLat = la; tkLon = lo; tkKind = fk;
            fuelKindNow = fk;
            publish();
            return tkItems.isEmpty() ? I18n.f("No hay gasolineras a %d km", rad) : null;
        } catch (Throwable e) {
            // el mensaje nunca lleva la dirección (tiene la clave)
            String m = e.getMessage() != null ? e.getMessage().replace(key, "***") : e.getClass().getSimpleName();
            return I18n.t("No se pudieron traer los precios") + ": " + (m.startsWith("http") ? e.getClass().getSimpleName() : m);
        } finally {
            tkBusy = false;
        }
    }

    /** Pone los precios nuevos en el mapa sin esperar la próxima actualización de tráfico. */
    private static synchronized void publish() {
        List<Item> out = new ArrayList<Item>();
        for (Item it : items) if (it.type != FUEL) out.add(it);
        out.addAll(tkItems);
        items = Collections.unmodifiableList(out);
        gen++;
    }

    private static List<Item> parseTk(JSONObject o, String kind, double la, double lo) {
        List<Item> out = new ArrayList<Item>();
        JSONArray st = o.optJSONArray("stations");
        if (st == null) return out;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < st.length(); i++) {
            JSONObject s = st.optJSONObject(i);
            if (s == null) continue;
            Item it = new Item();
            it.type = FUEL;
            it.id = "tk:" + s.optString("id", "" + i);
            it.lat = s.optDouble("lat", Double.NaN);
            it.lon = s.optDouble("lng", Double.NaN);
            if (Double.isNaN(it.lat) || Double.isNaN(it.lon)) continue;
            it.closed = !s.optBoolean("isOpen", true);
            String brand = s.optString("brand", "").trim();
            String name = s.optString("name", "").trim();
            it.title = brand.length() > 0 && !"null".equals(brand) ? brand : name;
            String street = (s.optString("street", "") + " " + s.optString("houseNumber", "")).replace("null", "").trim();
            String place = (s.optString("postCode", "") + " " + s.optString("place", "")).replace("null", "").trim();
            it.desc = new String[]{name, street + (place.length() > 0 ? ", " + place : "")};
            double pr = s.optDouble(kind, Double.NaN);      // false (no lo vende) → NaN
            if (!Double.isNaN(pr) && pr > 0.5 && pr < 4) it.price = (float) pr;   // precio imposible: sin precio
            it.distKm = (float) s.optDouble("dist", km(la, lo, it.lat, it.lon));
            if (!it.closed && !Float.isNaN(it.price)) min = Math.min(min, it.price);
            out.add(it);
        }
        for (Item it : out) it.cheapest = !it.closed && !Float.isNaN(it.price) && it.price <= min + 0.0005f;
        return out;
    }

    /** Las fuentes de lo que está en el mapa, como piden sus licencias (una línea corta). */
    static String attribution() {
        List<Item> list = items;
        boolean ab = false, tk = false, pk = false;
        for (Item it : list) {
            if (it.type <= REST) ab = true;
            else if (it.type == FUEL) tk = true;
            else if (it.type == PARK) pk = true;
        }
        StringBuilder b = new StringBuilder();
        if (ab) b.append(I18n.t("Tráfico: Die Autobahn GmbH des Bundes"));
        if (tk) b.append(b.length() > 0 ? " · " : "").append(I18n.t("Precios: Tankerkönig.de, CC BY 4.0, MTS-K"));
        if (pk && parkAttr != null) b.append(b.length() > 0 ? " · " : "").append(parkAttr);
        if (wxEvent != null && wxAttr != null) b.append(b.length() > 0 ? " · " : "").append(wxAttr);
        return b.toString();
    }

    private static String attr(JSONObject data) {
        JSONObject a = data.optJSONObject("attribution");
        String t = a != null ? a.optString("text", "") : "";
        return t.length() > 0 ? t : null;
    }

    private static List<Item> parsePark(String json) throws Exception {
        List<Item> out = new ArrayList<Item>();
        JSONObject data = new JSONObject(json).getJSONObject("data");
        parkAttr = attr(data);
        JSONObject pl = data.optJSONObject("payload");
        JSONArray fa = pl != null ? pl.optJSONArray("facilities") : null;
        if (fa == null) return out;
        for (int i = 0; i < fa.length(); i++) {
            JSONObject f = fa.optJSONObject(i);
            if (f == null || "closed".equals(f.optString("opening_status"))) continue;
            Item it = new Item();
            it.type = PARK;
            it.id = "park:" + f.optString("facility_id", "" + i);
            it.lat = f.optDouble("lat", Double.NaN);
            it.lon = f.optDouble("lon", Double.NaN);
            if (Double.isNaN(it.lat)) continue;
            it.title = f.optString("name", "");
            it.cap = f.optInt("capacity", -1);
            it.free = f.isNull("free") ? -1 : f.optInt("free", -1);
            if (it.free < 0 || it.cap > 0 && it.free > it.cap * 1.05) continue;   // sin dato o imposible
            it.desc = new String[0];
            out.add(it);
        }
        return out;
    }

    private static void parseWx(String json) throws Exception {
        JSONObject data = new JSONObject(json).getJSONObject("data");
        wxAttr = attr(data);
        JSONObject pl = data.optJSONObject("payload");
        JSONArray w = pl != null ? pl.optJSONArray("warnings") : null;
        String best = null;
        int lv = 0;
        if (w != null) for (int i = 0; i < w.length(); i++) {
            JSONObject o = w.optJSONObject(i);
            if (o == null) continue;
            int l = o.optInt("level", 0);
            if (l > lv) { lv = l; best = o.optString("event", ""); }
        }
        wxLevel = lv;
        wxEvent = best != null ? wxName(best) : null;
    }

    /** Aviso del DWD (en alemán y mayúsculas) → español. */
    static String wxName(String de) {
        String l = de.toUpperCase(Locale.GERMAN);
        if (l.contains("GLATTEIS")) return "Lluvia helada";
        if (l.contains("GLÄTTE")) return "Vía resbaladiza";
        if (l.contains("STRENGER FROST")) return "Helada fuerte";
        if (l.contains("FROST")) return "Helada";
        if (l.contains("NEBEL")) return "Niebla";
        if (l.contains("SCHNEEVERWEHUNG")) return "Nieve con viento";
        if (l.contains("SCHNEE")) return "Nevada";
        if (l.contains("ORKAN")) return "Vientos huracanados";
        if (l.contains("STURM")) return "Tormenta de viento";
        if (l.contains("WIND")) return "Ráfagas de viento";
        if (l.contains("GEWITTER")) return "Tormenta eléctrica";
        if (l.contains("HAGEL")) return "Granizo";
        if (l.contains("STARKREGEN") || l.contains("DAUERREGEN")) return "Lluvia intensa";
        if (l.contains("TAUWETTER")) return "Deshielo";
        if (l.contains("HITZE")) return "Calor";
        return de;
    }

    /** Solo para la captura de prueba: un atasco inventado 2 km por delante (hacia el sureste). */
    private static Item demoItem(double la, double lo) {
        Item it = new Item();
        it.type = WARN;
        it.id = "demo";
        it.road = "A3";
        it.lat = la - 0.011;
        it.lon = lo + 0.022;
        it.title = "A3 | Demo";
        it.sub = "Köln -> Frankfurt";
        it.event = "Atasco";
        it.delayMin = 7;
        it.avgKmh = 20;
        it.desc = new String[]{"Ereignismeldung:", "- Stau", "- Reisezeitverlust: 7 Minuten"};
        it.line = new float[]{(float) it.lat, (float) it.lon, (float) (it.lat - 0.008), (float) (it.lon + 0.02)};
        it.distKm = (float) km(la, lo, it.lat, it.lon);
        return it;
    }

    /** Para el diagnóstico del mapa (ya traducido). */
    static String status() {
        StringBuilder b = new StringBuilder(I18n.t("Tráfico de Alemania"));
        b.append("\n").append(I18n.t("En Alemania")).append(": ").append(I18n.t(inGermany ? "sí" : "no"));
        if (roadsNow.length() > 0) b.append("\n").append(I18n.t("Autopistas")).append(": ").append(roadsNow);
        if (cityName != null) b.append("\n").append(I18n.t("Ciudad")).append(": ").append(cityName);
        b.append("\n").append(I18n.t("Marcas en el mapa")).append(": ").append(items.size());
        if (updatedAt > 0) b.append("\n").append(I18n.t("Actualizado")).append(": ")
            .append(new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date(updatedAt)));
        if (lastError != null) b.append("\n").append(I18n.t("Error")).append(": ").append(lastError);
        return b.toString();
    }

    /** Después de cambiar un ajuste: se vuelve a pedir y a armar todo. */
    static synchronized void reset() {
        ROADS.clear();
        tPark = tWx = 0;
        parkCity = wxCity = null;
        cLat = Double.NaN;
        lastRun = 0;
        items = Collections.<Item>emptyList();
        wxEvent = null;
        gen++;
    }
}
