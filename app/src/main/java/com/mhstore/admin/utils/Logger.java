package com.mhstore.admin.utils;

import android.util.Log;
import com.mhstore.admin.BuildConfig;
import com.mhstore.admin.network.SupabaseClient;

/**
 * Centralized logging utility.
 * - Debug/INFO: Android Logcat only
 * - WARN/ERROR/CRASH: Android Logcat + posted to Supabase app_logs table
 */
public final class Logger {

    private Logger() {}

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String APP_TAG = "MHStore";

    public static void d(String tag, String msg) {
        if (DEBUG) Log.d(tag(tag), msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag(tag), msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag(tag), msg);
        postRemote("WARN", tag, msg, null);
    }

    public static void e(String tag, String msg) {
        Log.e(tag(tag), msg);
        postRemote("ERROR", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag(tag), msg, t);
        postRemote("ERROR", tag, msg, t != null ? t.toString() : null);
    }

    public static void crash(String tag, String msg, Throwable t) {
        Log.e(tag(tag), "[CRASH] " + msg, t);
        postRemote("CRASH", tag, msg, t != null ? stackTraceToString(t) : null);
    }

    // ── Remote logging (fire-and-forget) ─────────────────────
    private static void postRemote(String level, String tag, String message, String extra) {
        try {
            SupabaseClient.getInstance().postLog(level, tag, message, extra);
        } catch (Exception ignored) {
            // Never let logging break the app
        }
    }

    private static String tag(String t) {
        return APP_TAG + "/" + (t != null ? t : "App");
    }

    private static String stackTraceToString(Throwable t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append('\n');
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("  at ").append(el.toString()).append('\n');
            if (sb.length() > 2000) { sb.append("  ... truncated"); break; }
        }
        return sb.toString();
    }
}
