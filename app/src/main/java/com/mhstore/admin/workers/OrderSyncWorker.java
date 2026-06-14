package com.mhstore.admin.workers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.JsonObject;
import com.mhstore.admin.MHStoreApp;
import com.mhstore.admin.R;
import com.mhstore.admin.activities.MainActivity;
import com.mhstore.admin.database.AppDatabase;
import com.mhstore.admin.database.OrderEntity;
import com.mhstore.admin.network.SupabaseClient;
import com.mhstore.admin.utils.Constants;
import com.mhstore.admin.utils.Logger;
import com.mhstore.admin.utils.PrefManager;

import java.util.List;

/**
 * WorkManager Worker — runs every 15 minutes even when app is closed.
 * Polls Supabase for new orders, saves to Room, shows notification.
 *
 * Registered in MHStoreApp.onCreate() as PeriodicWorkRequest.
 * Minimum interval enforced by Android OS is 15 minutes.
 */
public class OrderSyncWorker extends Worker {

    private static final String TAG = "OrderSyncWorker";
    public  static final String WORK_NAME = "mhstore_order_sync";

    private final AppDatabase db;
    private final SupabaseClient supabase;
    private final Context ctx;

    public OrderSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.ctx      = context.getApplicationContext();
        this.db       = AppDatabase.getInstance(ctx);
        this.supabase = SupabaseClient.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!supabase.isConfigured()) {
            Logger.d(TAG, "Supabase not configured — skipping sync");
            return Result.success();
        }

        Logger.i(TAG, "Background sync started");
        final boolean[] success = {false};
        final Object lock = new Object();

        supabase.getOrders(Constants.MAX_ORDERS_PER_LOAD, new SupabaseClient.Callback<List<JsonObject>>() {
            @Override
            public void onSuccess(List<JsonObject> orders) {
                processOrders(orders);
                success[0] = true;
                synchronized (lock) { lock.notifyAll(); }
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Sync failed: " + error);
                success[0] = false;
                synchronized (lock) { lock.notifyAll(); }
            }
        });

        // Wait for async callback (max 20 seconds)
        synchronized (lock) {
            try { lock.wait(20_000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.retry();
            }
        }

        return success[0] ? Result.success() : Result.retry();
    }

    private void processOrders(List<JsonObject> orders) {
        int newCount = 0;
        for (JsonObject raw : orders) {
            String id = jsonStr(raw, "id");
            if (id == null || id.isEmpty()) continue;

            boolean isNew = db.orderDao().exists(id) == 0;
            OrderEntity entity = jsonToEntity(raw);
            db.orderDao().insert(entity);

            if (isNew) {
                showNotification(entity);
                db.orderDao().markAsNotified(id);
                PrefManager.getInstance(ctx).incrementBadge();
                newCount++;
            }
        }
        Logger.i(TAG, "Sync complete — new orders: " + newCount + " / total: " + orders.size());
    }

    private void showNotification(OrderEntity order) {
        NotificationManager nm = (NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.putExtra(Constants.EXTRA_ORDER_ID, order.id);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(ctx, order.id.hashCode(), intent, piFlags);

        String title = "🆕 Pesanan Baru! " + order.getServiceLabel();
        String body  = (order.customerName != null ? order.customerName : "Customer")
                     + " · " + order.getFormattedPrice()
                     + "\n#" + order.id;

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, MHStoreApp.CHANNEL_ORDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(0xFF2563EB)
            .setContentIntent(pi);

        nm.notify(Math.abs(order.id.hashCode()), nb.build());
    }

    // ── JSON helpers ──────────────────────────────────────────
    private OrderEntity jsonToEntity(JsonObject raw) {
        OrderEntity e = new OrderEntity();
        e.id               = jsonStr(raw, "id");
        e.service          = jsonStr(raw, "service");
        e.packageType      = jsonStr(raw, "package_type");
        e.status           = jsonStr(raw, "status");
        e.createdAt        = jsonStr(raw, "created_at");
        e.updatedAt        = jsonStr(raw, "updated_at");
        e.customerName     = jsonStr(raw, "customer_name");
        e.customerWhatsapp = jsonStr(raw, "customer_whatsapp");
        e.websiteName      = jsonStr(raw, "website_name");
        e.websiteDescription = jsonStr(raw, "website_description");
        e.domain           = jsonStr(raw, "domain");
        e.priceBase        = jsonInt(raw, "price_base");
        e.priceDomain      = jsonInt(raw, "price_domain");
        e.priceTotal       = jsonInt(raw, "price_total");
        e.deliveryMethod   = jsonStr(raw, "delivery_method");
        e.additionalNotes  = jsonStr(raw, "additional_notes");
        e.adminNotes       = jsonStr(raw, "admin_notes");
        e.isRead           = raw.has("is_read") && !raw.get("is_read").isJsonNull()
                             && raw.get("is_read").getAsBoolean();
        if (raw.has("details") && !raw.get("details").isJsonNull())
            e.detailsJson = raw.get("details").toString();
        if (raw.has("attachments") && !raw.get("attachments").isJsonNull())
            e.attachmentsJson = raw.get("attachments").toString();
        e.syncedAt   = System.currentTimeMillis();
        e.isNotified = false;
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
