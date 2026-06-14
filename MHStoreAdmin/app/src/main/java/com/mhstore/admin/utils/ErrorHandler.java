package com.mhstore.admin.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Global uncaught exception handler.
 * Logs crash to Supabase, then restarts the app gracefully.
 */
public class ErrorHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "ErrorHandler";
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    public ErrorHandler(Context context) {
        this.context        = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /** Install as the global handler. Call once in Application.onCreate(). */
    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new ErrorHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            Logger.crash(TAG, "Uncaught exception on thread: " + thread.getName(), throwable);

            // Give the async log call 2 seconds to fire before dying
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                restartApp();
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }, 2000);

        } catch (Exception e) {
            // Last resort — delegate to default handler
            if (defaultHandler != null) defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private void restartApp() {
        try {
            Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            }
        } catch (Exception ignored) {}
    }
}
