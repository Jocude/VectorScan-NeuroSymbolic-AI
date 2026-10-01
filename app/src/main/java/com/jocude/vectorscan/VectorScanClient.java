package com.jocude.vectorscan;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Cliente del servidor de VectorScan: envía el plano y descarga el modelo 3D (GLB). */
public class VectorScanClient {

    /** Error con un mensaje listo para enseñar al usuario. */
    public static class ServerException extends IOException {
        public ServerException(String message) {
            super(message);
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final String baseUrl;
    private final OkHttpClient http;
    private final Handler mainThread;

    public VectorScanClient(String baseUrl) {
        this(baseUrl, HTTP, new Handler(Looper.getMainLooper()));
    }

    VectorScanClient(String baseUrl, OkHttpClient http, Handler mainThread) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.http = http;
        this.mainThread = mainThread;
    }

    /**
     * Sube la imagen a /predict y guarda el GLB en {@code destino}. Bloquea: llamar fuera del
     * hilo principal.
     *
     * @param anchoM ancho real del edificio en metros, o null para que lo estime el servidor
     */
    public ModelSummary generateModel(File imagen, Double anchoM, File destino) throws IOException {
        MultipartBody.Builder body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imagen.getName(),
                        RequestBody.create(imagen, MediaType.parse("image/jpeg")));
        if (anchoM != null) {
            body.addFormDataPart("ancho_m", String.format(Locale.ROOT, "%.2f", anchoM));
        }
        Request request = new Request.Builder().url(baseUrl + "predict").post(body.build()).build();

        try (Response response = http.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                throw new ServerException(errorMessage(response));
            }
            try (InputStream in = responseBody.byteStream(); OutputStream out = new FileOutputStream(destino)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            return ModelSummary.fromHeader(response.header("X-VectorScan-Resumen"));
        } catch (SocketTimeoutException e) {
            throw new ServerException("El servidor tarda demasiado en responder.");
        } catch (ServerException e) {
            throw e;
        } catch (IOException e) {
            throw new ServerException("No se puede conectar con el servidor (" + baseUrl + ").");
        }
    }

    /** Comprueba /health y devuelve la versión del servidor. Bloquea. */
    public String checkHealth() throws IOException {
        Request request = new Request.Builder().url(baseUrl + "health").get().build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new ServerException(errorMessage(response));
            }
            return new JSONObject(body.string()).optString("version", "?");
        } catch (org.json.JSONException e) {
            throw new ServerException("La dirección no corresponde a un servidor de VectorScan.");
        } catch (ServerException e) {
            throw e;
        } catch (IOException e) {
            throw new ServerException("No se puede conectar con el servidor (" + baseUrl + ").");
        }
    }

    public void generateModelAsync(File imagen, Double anchoM, File destino, Callback<ModelSummary> callback) {
        EXECUTOR.execute(() -> {
            try {
                ModelSummary summary = generateModel(imagen, anchoM, destino);
                mainThread.post(() -> callback.onSuccess(summary));
            } catch (IOException e) {
                mainThread.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void checkHealthAsync(Callback<String> callback) {
        EXECUTOR.execute(() -> {
            try {
                String version = checkHealth();
                mainThread.post(() -> callback.onSuccess(version));
            } catch (IOException e) {
                mainThread.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** El servidor (FastAPI) explica los errores en {"detail": "..."}. */
    static String errorMessage(Response response) {
        String detail = null;
        try {
            ResponseBody body = response.body();
            if (body != null) {
                Object value = new JSONObject(body.string()).opt("detail");
                if (value instanceof String) detail = (String) value;
            }
        } catch (Exception ignored) {
            // Respuesta sin JSON: se usa el mensaje genérico.
        }
        if (detail != null && !detail.isEmpty()) return detail;
        switch (response.code()) {
            case 413:
                return "La imagen es demasiado grande.";
            case 422:
                return "No se ha reconocido un plano en la imagen.";
            default:
                return "Error del servidor (" + response.code() + ").";
        }
    }
}
