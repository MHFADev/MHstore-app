package com.mhstore.admin.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.gson.JsonObject;
import com.mhstore.admin.MHStoreApp;
import com.mhstore.admin.R;
import com.mhstore.admin.activities.OrderDetailActivity;
import com.mhstore.admin.database.AppDatabase;
import com.mhstore.admin.database.OrderEntity;
import com.mhstore.admin.network.SupabaseClient;
import com.mhstore.admin.utils.Constants;
import com.mhstore.admin.utils.Logger;
import com.mhstore.admin.utils.PrefManager;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Foreground Service — monitors orders in real-time via Supabase WebSocket
 * and polls every 30 seconds as fallback.
 *
 * Notification flow (no Firebase):
 *   1. Supabase Realtime WebSocket → instant INSERT/UPDATE detection
 *   2. REST polling every 30s → fallback sync
 *   3. WorkManager (15 min) → when app is fully closed
 */
public class OrderPollingService extends Service {

    private static final String TAG          = "PollingService";
    private static final int    FOREGROUND_ID = 9001;
    private static final long   POLL_INTERVAL = 30L; // seconds

    private ScheduledExecutorService scheduler;
    private AppDatabase db;
    private SupabaseClient supabase;
    private Handler mainHandler;
    private NotificationManager notifManager;

    // ── Lifecycle ─────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        db          = AppDatabase.getInstance(this);
        supabase    = SupabaseClient.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
        notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Logger.i(TAG, "OrderPollingService created");
        startForeground(FOREGROUND_ID, buildForegroundNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connectRealtime();
        startPolling();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        supabase.disconnectRealtime();
        stopPolling();
        Logger.i(TAG, "OrderPollingService destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Supabase Realtime WebSocket ────────────────────────────
    private void connectRealtime() {
        if (!supabase.isConfigured()) return;

        supabase.connectRealtime(new SupabaseClient.RealtimeCallback() {
            @Override
            public void onOrderInserted(JsonObject record) {
                // New order — show notification immediately
                mainHandler.post(() -> {
                    Logger.i(TAG, "Realtime: new order detected");
                    try {
                        OrderEntity entity = jsonToEntity(record);
                        db.orderDao().insert(entity);
                        if (!entity.isNotified) {
                            showOrderNotification(entity);
                            db.orderDao().markAsNotified(entity.id);
                            PrefManager.getInstance(OrderPollingService.this).incrementBadge();
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, "Realtime insert parse error: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onOrderUpdated(JsonObject record) {
                // Order status changed — update local DB
                mainHandler.post(() -> {
                    try {
                        OrderEntity entity = jsonToEntity(record);
                        db.orderDao().insert(entity); // upsert
                        Logger.i(TAG, "Realtime: order updated " + entity.id);
                    } catch (Exception e) {
                        Logger.e(TAG, "Realtime update parse error: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onConnected() {
                Logger.i(TAG, "Realtime WebSocket connected — instant notifications active");
            }

            @Override
            public void onDisconnected() {
                Logger.w(TAG, "Realtime WebSocket disconnected — falling back to polling");
            }
        });
    }

    // ── Polling (fallback) ────────────────────────────────────
    private void startPolling() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::poll, 0, POLL_INTERVAL, TimeUnit.SECONDS);
        Logger.i(TAG, "Polling started — every " + POLL_INTERVAL + "s (fallback)");
    }

    private void stopPolling() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void poll() {
        if (!supabase.isConfigured()) return;

        supabase.getOrders(Constants.MAX_ORDERS_PER_LOAD, new SupabaseClient.Callback<List<JsonObject>>() {
            @Override
            public void onSuccess(List<JsonObject> orders) {
                processNewOrders(orders);
            }

            @Override
            public void onError(String error) {
                Logger.w(TAG, "Poll failed: " + error);
            }
        });
    }

    private void processNewOrders(List<JsonObject> orders) {
        Executors.newSingleThreadExecutor().execute(() -> {
            int newCount = 0;
            for (JsonObject raw : orders) {
                String id = jsonStr(raw, "id");
                if (id == null || id.isEmpty()) continue;

                boolean exists = db.orderDao().exists(id) > 0;
                OrderEntity entity = jsonToEntity(raw);
                db.orderDao().insert(entity); // upsert

                if (!exists && !entity.isNotified) {
                    showOrderNotification(entity);
                    db.orderDao().markAsNotified(id);
                    PrefManager.getInstance(OrderPollingService.this).incrementBadge();
                    newCount++;
                }
            }
            if (newCount > 0) Logger.i(TAG, "New orders found: " + newCount);
        });
    }

    // ── Notification ──────────────────────────────────────────
    private void showOrderNotification(OrderEntity order) {
        String title = "🆕 Pesanan Baru! " + order.getServiceLabel();
        String body  = (order.customerName != null ? order.customerName : "Customer")
                     + " · WA: " + (order.customerWhatsapp != null ? order.customerWhatsapp : "—")
                     + "\n" + order.getFormattedPrice()
                     + "\n#" + order.id;

        Intent intent = new Intent(this, OrderDetailActivity.class);
        intent.putExtra(Constants.EXTRA_ORDER_ID, order.id);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, order.id.hashCode(), intent, piFlags);

        Notification n = new NotificationCompat.Builder(this, MHStoreApp.CHANNEL_ORDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFF2563EB)
            .setContentIntent(pi)
            .build();

        if (notifManager != null) {
            notifManager.notify(Math.abs(order.id.hashCode()), n);
        }
    }

    private Notification buildForegroundNotification() {
        Intent intent = new Intent(this, com.mhstore.admin.activities.MainActivity.class);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, piFlags);

        return new NotificationCompat.Builder(this, MHStoreApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MHStore Admin")
            .setContentText("Memantau pesanan baru...")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build();
    }

    // ── JSON → Entity ─────────────────────────────────────────
    private OrderEntity jsonToEntity(JsonObject raw) {
        OrderEntity e = new OrderEntity();
        e.id                  = jsonStr(raw, "id");
        e.service             = jsonStr(raw, "service");
        e.packageType         = jsonStr(raw, "package_type");
        e.status              = jsonStr(raw, "status");
        e.createdAt           = jsonStr(raw, "created_at");
        e.updatedAt           = jsonStr(raw, "updated_at");
        e.customerName        = jsonStr(raw, "customer_name");
        e.customerWhatsapp    = jsonStr(raw, "customer_whatsapp");
        e.websiteName         = jsonStr(raw, "website_name");
        e.websiteDescription  = jsonStr(raw, "website_description");
        e.domain              = jsonStr(raw, "domain");
        e.priceBase           = jsonInt(raw, "price_base");
        e.priceDomain         = jsonInt(raw, "price_domain");
        e.priceTotal          = jsonInt(raw, "price_total");
        e.deliveryMethod      = jsonStr(raw, "delivery_method");
        e.urlReference        = jsonStr(raw, "url_reference");
        e.additionalNotes     = jsonStr(raw, "additional_notes");
        e.adminNotes          = jsonStr(raw, "admin_notes");
        e.adminMessage        = jsonStr(raw, "admin_message");
        e.isRead              = raw.has("is_read") && !raw.get("is_read").isJsonNull() && raw.get("is_read").getAsBoolean();
        if (raw.has("details") && !raw.get("details").isJsonNull())
            e.detailsJson     = raw.get("details").toString();
        if (raw.has("attachments") && !raw.get("attachments").isJsonNull())
            e.attachmentsJson = raw.get("attachments").toString();
        e.syncedAt            = System.currentTimeMillis();
        e.isNotified          = false;
        return e;
    }

    private String jsonStr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        return o.get(key).getAsString();
    }

    private int jsonInt(JsonObject o, String key) {
        try { return (!o.has(key) || o.get(key).isJsonNull()) ? 0 : o.get(key).getAsInt(); }
        catch (Exception e) { return 0; }
    }
}
