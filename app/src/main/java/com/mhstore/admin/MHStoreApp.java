package com.mhstore.admin;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.multidex.MultiDexApplication;
import androidx.work.*;
import com.mhstore.admin.utils.ErrorHandler;
import com.mhstore.admin.utils.Logger;
import com.mhstore.admin.workers.OrderSyncWorker;

import java.util.concurrent.TimeUnit;

/**
 * Application class — initialises:
 *   1. Global crash handler (ErrorHandler)
 *   2. Notification channels (order alerts + service channel)
 *   3. WorkManager periodic background sync (every 15 min)
 *
 * ForegroundService (OrderPollingService) is started from MainActivity
 * when the user logs in, so it's alive only while the app is open.
 */
public class MHStoreApp extends MultiDexApplication {

    // ── Channel IDs ───────────────────────────────────────────
    public static final String CHANNEL_ORDER   = "ch_order_notifications";
    public static final String CHANNEL_SERVICE = "ch_foreground_service";

    // ── FCM topic (kept for future use) ─────────────────────
    public static final String FCM_TOPIC = "mhstore_admin";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Global crash handler — logs to Supabase app_logs
        ErrorHandler.install(this);

        // 2. Notification channels (Android 8.0+)
        createNotificationChannels();

        // 3. WorkManager — background sync every 15 min
        scheduleBackgroundSync();

        Logger.i("MHStoreApp", "Application started — v" + BuildConfig.VERSION_NAME);
    }

    // ── Notification Channels ─────────────────────────────────
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // High-priority channel for new order alerts
        NotificationChannel orderChannel = new NotificationChannel(
            CHANNEL_ORDER,
            "Order Notifications",
            NotificationManager.IMPORTANCE_HIGH
        );
        orderChannel.setDescription("Notifikasi pesanan baru dari MHStore");
        orderChannel.enableVibration(true);
        orderChannel.setVibrationPattern(new long[]{0, 250, 100, 250});
        orderChannel.enableLights(true);
        orderChannel.setLightColor(0xFF2563EB);
        nm.createNotificationChannel(orderChannel);

        // Low-priority channel for the foreground service persistent notification
        NotificationChannel serviceChannel = new NotificationChannel(
            CHANNEL_SERVICE,
            "Background Service",
            NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("Layanan pemantauan pesanan berjalan di latar belakang");
        serviceChannel.setShowBadge(false);
        nm.createNotificationChannel(serviceChannel);
    }

    // ── WorkManager Periodic Sync ─────────────────────────────
    private void scheduleBackgroundSync() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
            OrderSyncWorker.class,
            15, TimeUnit.MINUTES   // Android OS minimum — actual interval may be longer
        )
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
        .addTag("mhstore_sync")
        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            OrderSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // don't restart if already scheduled
            syncRequest
        );

        Logger.i("MHStoreApp", "WorkManager background sync scheduled (15 min interval)");
    }
}
