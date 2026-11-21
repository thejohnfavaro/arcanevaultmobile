package com.arcanevault.mobile.cloud;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Offline-first transport only. Call from a background executor, NOT the main thread.
 * Does not edit collections, decks, baselines or local sync databases.
 * Do not log token, request body, or response payload.
 */
public final class WixSyncClient {
    private static final int MAX_RESPONSE = 700_000;
    private final String origin;

    public WixSyncClient(String origin) {
        if (!origin.startsWith("https://") || origin.contains("?") || origin.contains("#")) {
            throw new IllegalArgumentException("Cloud sync requires HTTPS origin");
        }
        this.origin = origin.replaceAll("/+$", "");
    }

    public JSONObject pull(String token) throws IOException, JSONException {
        return post("avSyncPull", token, new JSONObject());
    }

    public int push(String token, String deviceId, int expectedRevision, String peerSnapshot)
            throws IOException, JSONException {
        if (!"android".equals(deviceId) && !"windows".equals(deviceId))
            throw new IllegalArgumentException("Unknown device");
        if (expectedRevision < 0) throw new IllegalArgumentException("Invalid revision");
        if (peerSnapshot == null || peerSnapshot.length() > 250000)
            throw new IllegalArgumentException("Snapshot exceeds Wix Light transport limit");
        new JSONObject(peerSnapshot); // Validate before making a network request
        JSONObject request = new JSONObject()
                .put("deviceId", deviceId)
                .put("expectedRevision", expectedRevision)
                .put("payload", peerSnapshot);
        return post("avSyncPush", token, request).getInt("revision");
    }

    private JSONObject post(String endpoint, String token, JSONObject body)
            throws IOException, JSONException {
        if (token == null || token.trim().isEmpty()) throw new IllegalArgumentException("Missing sync token");
        HttpURLConnection connection = (HttpURLConnection)
                new URL(origin + "/_functions/" + endpoint).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setDoOutput(true);
        try {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String message = status == 409 ? "Cloud slot changed; pull again before retrying."
                        : status == 401 ? "Cloud token rejected."
                        : "Wix sync HTTP " + status;
                throw new IOException(message);
            }
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > MAX_RESPONSE) throw new IOException("Cloud response too large");
                    out.write(buffer, 0, count);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }
}
