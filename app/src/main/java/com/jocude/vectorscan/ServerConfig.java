package com.jocude.vectorscan;

import android.content.Context;

import okhttp3.HttpUrl;

/** Dirección del servidor de VectorScan, configurable desde Ajustes. */
public final class ServerConfig {

    static final String PREFS_NAME = "vectorscan_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final int DEFAULT_PORT = 8000;

    private ServerConfig() {
    }

    public static String getUrl(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SERVER_URL, BuildConfig.DEFAULT_SERVER_URL);
    }

    /** Guarda la URL ya normalizada (ver {@link #normalize}). */
    public static void setUrl(Context context, String normalizedUrl) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_URL, normalizedUrl)
                .apply();
    }

    /**
     * Deja la URL lista para usar: añade http:// si falta, el puerto 8000 si se ha escrito solo
     * una IP o un nombre con http, y la barra final. Devuelve null si no es una URL válida.
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String url = input.trim();
        if (url.isEmpty()) return null;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return null;

        String authority = url.substring(url.indexOf("://") + 3).split("/", 2)[0];
        boolean explicitPort = authority.contains(":");
        HttpUrl.Builder builder = parsed.newBuilder();
        if (!explicitPort && parsed.scheme().equals("http")) {
            builder.port(DEFAULT_PORT);
        }
        String result = builder.build().toString();
        return result.endsWith("/") ? result : result + "/";
    }
}
