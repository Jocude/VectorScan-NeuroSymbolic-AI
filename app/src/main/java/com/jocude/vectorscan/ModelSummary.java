package com.jocude.vectorscan;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Resumen de lo que el servidor ha detectado en el plano (cabecera X-VectorScan-Resumen). */
public final class ModelSummary {

    public final int walls;
    public final int doors;
    public final int windows;
    public final int rooms;
    public final double widthM;
    public final double depthM;

    public ModelSummary(int walls, int doors, int windows, int rooms, double widthM, double depthM) {
        this.walls = walls;
        this.doors = doors;
        this.windows = windows;
        this.rooms = rooms;
        this.widthM = widthM;
        this.depthM = depthM;
    }

    /** Devuelve null si la cabecera falta o no se entiende: el resumen es opcional. */
    public static ModelSummary fromHeader(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            return new ModelSummary(
                    o.optInt("paredes"),
                    o.optInt("puertas"),
                    o.optInt("ventanas"),
                    o.optInt("habitaciones"),
                    o.optDouble("ancho_m", 0),
                    o.optDouble("fondo_m", 0));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Ej.: "5 estancias · 5 puertas · 3 ventanas · 12,0 × 8,3 m". */
    public String describe() {
        Locale es = Locale.forLanguageTag("es-ES");
        return String.format(es, "%s · %s · %s · %.1f × %.1f m",
                plural(rooms, "estancia", "estancias"),
                plural(doors, "puerta", "puertas"),
                plural(windows, "ventana", "ventanas"),
                widthM, depthM);
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
