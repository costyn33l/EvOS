package com.stradaos.launcher;

import java.util.Calendar;

/**
 * El VIN que el auto entrega (modo 09, PID 02): validación (17 caracteres, sin I, O ni Q) y lo que se puede leer de
 * él sin internet: el fabricante (los 3 primeros, WMI) y el año del modelo (el décimo carácter).
 */
final class Vin {
    private Vin() { }

    static boolean valid(String v) {
        if (v == null || v.length() != 17) return false;
        for (char c : v.toCharArray()) {
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
            if (!ok || c == 'I' || c == 'O' || c == 'Q') return false;
        }
        // todo igual (0000…, ZZZZ…) = el computador no lo tiene grabado
        boolean same = true;
        for (char c : v.toCharArray()) if (c != v.charAt(0)) same = false;
        return !same;
    }

    /** Grupo Volkswagen (VW, Audi, Škoda, SEAT, Cupra): sus computadores diésel entregan el hollín del DPF por UDS. */
    static boolean vag(String v) {
        if (!valid(v)) return false;
        String w = v.substring(0, 3);
        return w.equals("WVW") || w.equals("WV1") || w.equals("WV2") || w.equals("WV3") || w.equals("WVG") || w.equals("WAU")
            || w.equals("WA1") || w.equals("TRU") || w.equals("TMB") || w.equals("TMP") || w.equals("VSS") || w.equals("VS6")
            || w.equals("3VW") || w.equals("1VW") || w.equals("9BW") || w.equals("AAV");
    }

    private static final String[][] WMI = {
        {"WVW", "Volkswagen"}, {"WV1", "Volkswagen"}, {"WV2", "Volkswagen"}, {"WV3", "Volkswagen"}, {"WVG", "Volkswagen"}, {"3VW", "Volkswagen"},
        {"1VW", "Volkswagen"}, {"9BW", "Volkswagen"}, {"AAV", "Volkswagen"}, {"WAU", "Audi"}, {"WA1", "Audi"}, {"TRU", "Audi"},
        {"TMB", "Škoda"}, {"TMP", "Škoda"}, {"VSS", "SEAT"}, {"VS6", "SEAT"}, {"WBA", "BMW"}, {"WBS", "BMW M"}, {"WBY", "BMW"},
        {"WMW", "MINI"}, {"WDB", "Mercedes-Benz"}, {"WDD", "Mercedes-Benz"}, {"WDC", "Mercedes-Benz"}, {"W1K", "Mercedes-Benz"},
        {"W1N", "Mercedes-Benz"}, {"W1V", "Mercedes-Benz"}, {"WME", "smart"}, {"W0L", "Opel"}, {"W0V", "Opel"}, {"VXK", "Opel"},
        {"WF0", "Ford"}, {"1FA", "Ford"}, {"1FM", "Ford"}, {"1FT", "Ford"}, {"WP0", "Porsche"}, {"WP1", "Porsche"},
        {"VF1", "Renault"}, {"VF6", "Renault"}, {"UU1", "Dacia"}, {"UU6", "Dacia"}, {"VF3", "Peugeot"}, {"VR3", "Peugeot"},
        {"VF7", "Citroën"}, {"VR7", "Citroën"}, {"VR1", "DS"}, {"ZFA", "Fiat"}, {"ZFC", "Fiat"}, {"ZAR", "Alfa Romeo"},
        {"ZCF", "Iveco"}, {"ZFF", "Ferrari"}, {"ZHW", "Lamborghini"}, {"ZLA", "Lancia"}, {"SAJ", "Jaguar"}, {"SAL", "Land Rover"},
        {"SCC", "Lotus"}, {"YV1", "Volvo"}, {"YV4", "Volvo"}, {"YS3", "Saab"}, {"VSK", "Nissan"}, {"SJN", "Nissan"},
        {"JN1", "Nissan"}, {"JN8", "Nissan"}, {"JT2", "Toyota"}, {"JTD", "Toyota"}, {"JTE", "Toyota"}, {"JTN", "Toyota"},
        {"SB1", "Toyota"}, {"NMT", "Toyota"}, {"VNK", "Toyota"}, {"JHM", "Honda"}, {"SHH", "Honda"}, {"SHS", "Honda"},
        {"JMZ", "Mazda"}, {"JM1", "Mazda"}, {"JS2", "Suzuki"}, {"TSM", "Suzuki"}, {"JMB", "Mitsubishi"}, {"JA3", "Mitsubishi"},
        {"JF1", "Subaru"}, {"JF2", "Subaru"}, {"KMH", "Hyundai"}, {"TMA", "Hyundai"}, {"KNA", "Kia"}, {"KNE", "Kia"},
        {"KND", "Kia"}, {"U5Y", "Kia"}, {"KPT", "SsangYong"}, {"XTA", "Lada"}, {"LSV", "SAIC Volkswagen"}, {"LFV", "FAW-Volkswagen"},
        {"5YJ", "Tesla"}, {"LRW", "Tesla"}, {"XP7", "Tesla"}, {"1G1", "Chevrolet"}, {"KL1", "Chevrolet"}, {"1C4", "Jeep / Chrysler"},
        {"1J4", "Jeep"}, {"ZAC", "Jeep"}, {"VSE", "Suzuki (Santana)"}, {"VWV", "Volkswagen"}, {"VNV", "Nissan"}, {"VF8", "Matra"},
    };

    /** Fabricante según los 3 primeros caracteres; null si no está en la tabla. */
    static String maker(String v) {
        if (!valid(v)) return null;
        String w = v.substring(0, 3);
        for (String[] e : WMI) if (e[0].equals(w)) return e[1];
        return null;
    }

    private static final String YEARS = "ABCDEFGHJKLMNPRSTVWXY123456789";

    /**
     * Año del modelo según el décimo carácter (ciclo de 30 años: A = 1980 o 2010). Se elige el más reciente que no pase
     * del año que viene. Muchos autos europeos no lo codifican (ponen «Z» u otro): entonces 0.
     */
    static int year(String v) {
        if (!valid(v)) return 0;
        int i = YEARS.indexOf(v.charAt(9));
        if (i < 0) return 0;
        int now = Calendar.getInstance().get(Calendar.YEAR);
        int y = 1980 + i;
        while (y + 30 <= now + 1) y += 30;
        return y;
    }

    /** Tipo de combustible del PID 51 (SAE J1979), en español; null si no se sabe. */
    static String fuel(int t) {
        switch (t) {
            case 1: return "Gasolina";
            case 2: return "Metanol";
            case 3: return "Etanol";
            case 4: return "Diésel";
            case 5: return "GLP";
            case 6: return "GNC";
            case 8: return "Eléctrico";
            case 9: return "Bicombustible (gasolina)";
            case 0x10: return "Híbrido (gasolina)";
            case 0x11: return "Híbrido (etanol)";
            case 0x12: return "Híbrido (diésel)";
            case 0x13: return "Híbrido eléctrico";
            default: return null;
        }
    }
}
