package com.mhstore.admin.network;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mhstore.admin.BuildConfig;
import com.mhstore.admin.models.Order;
import com.mhstore.admin.utils.Logger;
import okhttp3.*;
import okio.ByteString;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Supabase REST API + Realtime WebSocket client.
 * Replaces Firebase Firestore entirely.
 *
 * REST docs: https://supabase.com/docs/guides/api
 * Realtime:  https://supabase.com/docs/guides/realtime
 */
public class SupabaseClient {

    private static final String TAG = "SupabaseClient";
    private static volatile SupabaseClient instance;

    private final String baseUrl;
    private final String anonKey;
    private final OkHttpClient http;
    private final Gson gson;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private WebSocket realtimeSocket;
    private RealtimeCallback realtimeCallback;
    private boolean isRealtimeConnected = false;
    private int heartbeatRef = 0;

    // ── Singleton ────────────────────────────────────────────────
    public static SupabaseClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) instance = new SupabaseClient();
            }
        }
        return instance;
    }

    private SupabaseClient() {
        this.baseUrl = BuildConfig.SUPABASE_URL;
        this.anonKey = BuildConfig.SUPABASE_ANON_KEY;
        this.gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create();
        this.http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                Request req = chain.request().newBuilder()
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer " + anonKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build();
                return chain.proceed(req);
            })
            .build();
    }

    // ── Callbacks ────────────────────────────────────────────────
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public interface RealtimeCallback {
        void onOrderInserted(JsonObject record);
        void onOrderUpdated(JsonObject record);
        void onConnected();
        void onDisconnected();
    }

    // ── REST: GET Orders ─────────────────────────────────────────
    public void getOrders(int limit, Callback<List<JsonObject>> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/orders"
                + "?order=created_at.desc&limit=" + limit
                + "&select=*";
            Request req = new Request.Builder().url(url).get().build();
            executeRequest(req, callback, body -> {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                List<JsonObject> list = new ArrayList<>();
                for (JsonElement el : arr) list.add(el.getAsJsonObject());
                return list;
            });
        });
    }

    // ── REST: GET Single Order ───────────────────────────────────
    public void getOrder(String orderId, Callback<JsonObject> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/orders?id=eq." + orderId
                + "&select=*,admin_messages(*)";
            Request req = new Request.Builder().url(url).get().build();
            executeRequest(req, callback, body -> {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                if (arr.size() == 0) throw new RuntimeException("Order tidak ditemukan");
                return arr.get(0).getAsJsonObject();
            });
        });
    }

    // ── REST: INSERT Order ───────────────────────────────────────
    public void insertOrder(JsonObject orderData, Callback<JsonObject> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/orders";
            RequestBody body = RequestBody.create(
                gson.toJson(orderData), MediaType.parse("application/json"));
            Request req = new Request.Builder().url(url).post(body).build();
            executeRequest(req, callback, responseBody -> {
                JsonElement el = JsonParser.parseString(responseBody);
                if (el.isJsonArray()) return el.getAsJsonArray().get(0).getAsJsonObject();
                return el.getAsJsonObject();
            });
        });
    }

    // ── REST: UPDATE Order Status + Notes ────────────────────────
    public void updateOrder(String orderId, String status, String adminNotes, boolean isRead,
                            Callback<Void> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/orders?id=eq." + orderId;
            JsonObject patch = new JsonObject();
            if (status != null)     patch.addProperty("status", status);
            if (adminNotes != null) patch.addProperty("admin_notes", adminNotes);
            patch.addProperty("is_read", isRead);

            RequestBody body = RequestBody.create(
                gson.toJson(patch), MediaType.parse("application/json"));
            Request req = new Request.Builder().url(url)
                .patch(body).build();
            executeRequest(req, new Callback<Void>() {
                @Override public void onSuccess(Void r) { callback.onSuccess(null); }
                @Override public void onError(String e) { callback.onError(e); }
            }, b -> null);
        });
    }

    // ── REST: Send Admin Message ──────────────────────────────────
    public void sendAdminMessage(String orderId, String message, Callback<Void> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/admin_messages";
            JsonObject payload = new JsonObject();
            payload.addProperty("order_id", orderId);
            payload.addProperty("message", message);
            payload.addProperty("sender", "admin");

            RequestBody body = RequestBody.create(
                gson.toJson(payload), MediaType.parse("application/json"));
            Request req = new Request.Builder().url(url).post(body).build();
            executeRequest(req, new Callback<Void>() {
                @Override public void onSuccess(Void r) { callback.onSuccess(null); }
                @Override public void onError(String e) { callback.onError(e); }
            }, b -> null);
        });
    }

    // ── REST: Get Messages for Order ─────────────────────────────
    public void getMessages(String orderId, Callback<List<JsonObject>> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/admin_messages?order_id=eq." + orderId
                + "&order=created_at.asc";
            Request req = new Request.Builder().url(url).get().build();
            executeRequest(req, callback, body -> {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                List<JsonObject> list = new ArrayList<>();
                for (JsonElement el : arr) list.add(el.getAsJsonObject());
                return list;
            });
        });
    }

    // ── REST: Get Summary Stats ───────────────────────────────────
    public void getStats(Callback<JsonObject> callback) {
        executor.execute(() -> {
            String url = baseUrl + "/rest/v1/orders_summary";
            Request req = new Request.Builder().url(url).get().build();
            executeRequest(req, callback, body -> {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                return arr.size() > 0 ? arr.get(0).getAsJsonObject() : new JsonObject();
            });
        });
    }

    // ── REST: Log to Supabase ─────────────────────────────────────
    public void postLog(String level, String tag, String message, String extra) {
        executor.execute(() -> {
            try {
                String url = baseUrl + "/rest/v1/app_logs";
                JsonObject payload = new JsonObject();
                payload.addProperty("level", level);
                payload.addProperty("tag", tag);
                payload.addProperty("message", message);
                if (extra != null) payload.addProperty("extra", extra);

                RequestBody body = RequestBody.create(
                    gson.toJson(payload), MediaType.parse("application/json"));
                Request req = new Request.Builder().url(url).post(body).build();
                try (Response resp = http.newCall(req).execute()) {
                    // fire-and-forget log upload
                }
            } catch (Exception e) {
                // Don't crash on log failures
            }
        });
    }

    // ── Realtime WebSocket ────────────────────────────────────────
    public void connectRealtime(RealtimeCallback callback) {
        this.realtimeCallback = callback;
        if (isRealtimeConnected) return;

        String wsUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
            + "/realtime/v1/websocket?apikey=" + anonKey + "&vsn=1.0.0";

        Request req = new Request.Builder().url(wsUrl).build();

        realtimeSocket = http.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isRealtimeConnected = true;
                Logger.i(TAG, "Realtime connected");
                subscribeToOrders(ws);
                startHeartbeat(ws);
                mainHandler.post(() -> { if (realtimeCallback != null) realtimeCallback.onConnected(); });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleRealtimeMessage(text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                handleRealtimeMessage(bytes.utf8());
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isRealtimeConnected = false;
                Logger.e(TAG, "Realtime error: " + t.getMessage());
                mainHandler.post(() -> { if (realtimeCallback != null) realtimeCallback.onDisconnected(); });
                // Auto-reconnect after 5 seconds
                mainHandler.postDelayed(() -> connectRealtime(realtimeCallback), 5000);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isRealtimeConnected = false;
                mainHandler.post(() -> { if (realtimeCallback != null) realtimeCallback.onDisconnected(); });
            }
        });
    }

    public void disconnectRealtime() {
        if (realtimeSocket != null) {
            realtimeSocket.close(1000, "User disconnected");
            realtimeSocket = null;
            isRealtimeConnected = false;
        }
    }

    private void subscribeToOrders(WebSocket ws) {
        // Subscribe to postgres_changes for the orders table
        JsonObject config = new JsonObject();
        JsonArray changes = new JsonArray();
        JsonObject change = new JsonObject();
        change.addProperty("event", "*");
        change.addProperty("schema", "public");
        change.addProperty("table", "orders");
        changes.add(change);
        config.add("postgres_changes", changes);

        JsonObject payload = new JsonObject();
        payload.add("config", config);

        JsonObject msg = new JsonObject();
        msg.addProperty("topic", "realtime:orders");
        msg.addProperty("event", "phx_join");
        msg.add("payload", payload);
        msg.addProperty("ref", String.valueOf(++heartbeatRef));

        ws.send(msg.toString());
    }

    private void startHeartbeat(WebSocket ws) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRealtimeConnected && ws != null) {
                    JsonObject heartbeat = new JsonObject();
                    heartbeat.addProperty("topic", "phoenix");
                    heartbeat.addProperty("event", "heartbeat");
                    heartbeat.add("payload", new JsonObject());
                    heartbeat.addProperty("ref", String.valueOf(++heartbeatRef));
                    ws.send(heartbeat.toString());
                    mainHandler.postDelayed(this, 30000); // every 30s
                }
            }
        }, 30000);
    }

    private void handleRealtimeMessage(String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            String event = msg.has("event") ? msg.get("event").getAsString() : "";

            if ("postgres_changes".equals(event) || "INSERT".equals(event) || "UPDATE".equals(event)) {
                JsonObject payload = msg.has("payload") ? msg.getAsJsonObject("payload") : null;
                if (payload == null) return;

                JsonObject data = payload.has("data") ? payload.getAsJsonObject("data") : payload;
                String type = data.has("type") ? data.get("type").getAsString() : "";
                JsonObject record = data.has("record") ? data.getAsJsonObject("record") : null;
                if (record == null) return;

                mainHandler.post(() -> {
                    if (realtimeCallback == null) return;
                    if ("INSERT".equals(type)) realtimeCallback.onOrderInserted(record);
                    else if ("UPDATE".equals(type)) realtimeCallback.onOrderUpdated(record);
                });
            }
        } catch (Exception e) {
            Logger.e(TAG, "Realtime parse error: " + e.getMessage());
        }
    }

    // ── Generic HTTP executor ─────────────────────────────────────
    private interface ResponseParser<T> {
        T parse(String body) throws Exception;
    }

    private <T> void executeRequest(Request req, Callback<T> callback, ResponseParser<T> parser) {
        try (Response response = http.newCall(req).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.isSuccessful()) {
                T result = parser.parse(body);
                mainHandler.post(() -> callback.onSuccess(result));
            } else {
                String errMsg = "HTTP " + response.code() + ": " + body;
                Logger.e(TAG, errMsg);
                mainHandler.post(() -> callback.onError(errMsg));
            }
        } catch (IOException e) {
            Logger.e(TAG, "Network error: " + e.getMessage());
            mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
        } catch (Exception e) {
            Logger.e(TAG, "Parse error: " + e.getMessage());
            mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
        }
    }

    public boolean isConfigured() {
        return !BuildConfig.SUPABASE_URL.equals("https://placeholder.supabase.co")
            && !BuildConfig.SUPABASE_ANON_KEY.equals("placeholder_key");
    }
}
