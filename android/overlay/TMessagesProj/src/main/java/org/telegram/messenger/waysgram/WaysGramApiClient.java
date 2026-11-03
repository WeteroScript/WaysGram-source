package org.telegram.messenger.waysgram;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authenticated JSON client for WaysGram's own REST API.
 * API sessions are read from Android Keystore-backed WaysGramSessionStore.
 */
public final class WaysGramApiClient implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message, int statusCode);
    }

    private final WaysGramSessionStore sessionStore;
    private final ExecutorService ioExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WaysGramApiClient(WaysGramSessionStore sessionStore) {
        if (sessionStore == null) {
            throw new IllegalArgumentException("WaysGramSessionStore is required");
        }
        this.sessionStore = sessionStore;
        ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WaysGramApi");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void get(String path, Callback callback) {
        execute("GET", path, null, callback);
    }

    public void post(String path, JSONObject payload, Callback callback) {
        execute("POST", path, payload == null ? new JSONObject() : payload, callback);
    }

    public void delete(String path, Callback callback) {
        execute("DELETE", path, null, callback);
    }

    private void execute(String method, String path, JSONObject payload, Callback callback) {
        ioExecutor.execute(() -> {
            try {
                JSONObject response = request(method, path, payload);
                deliverSuccess(callback, response);
            } catch (ApiException exception) {
                deliverError(callback, exception.getMessage(), exception.statusCode);
            } catch (Exception exception) {
                deliverError(callback, "Не удалось связаться с сервером WaysGram", 0);
            }
        });
    }

    private JSONObject request(String method, String path, JSONObject payload) throws IOException {
        String baseUrl = sessionStore.getApiBaseUrl();
        String token = sessionStore.getWaysGramSessionToken();
        if (baseUrl == null || token == null || token.length() == 0) {
            throw new IOException("Нет активной сессии WaysGram");
        }
        if (path == null || !path.startsWith("/") || path.startsWith("//")
                || path.contains("\\") || path.contains("\n") || path.contains("\r")) {
            throw new IOException("Некорректный путь WaysGram API");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            byte[] bytes = stream == null ? new byte[0] : readLimited(stream);
            JSONObject response;
            try {
                response = bytes.length == 0
                        ? new JSONObject()
                        : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception exception) {
                throw new IOException("WaysGram вернул некорректный JSON", exception);
            }
            if (status < 200 || status >= 300) {
                String message = response.optString("detail", "");
                if (message.length() == 0 || message.length() > 240) {
                    message = "Ошибка WaysGram API: HTTP " + status;
                }
                throw new ApiException(status, message);
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("WaysGram response is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void deliverSuccess(Callback callback, JSONObject response) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(response));
        }
    }

    private void deliverError(Callback callback, String message, int statusCode) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message, statusCode));
        }
    }

    @Override
    public void close() {
        ioExecutor.shutdownNow();
    }

    private static final class ApiException extends IOException {
        final int statusCode;

        ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
