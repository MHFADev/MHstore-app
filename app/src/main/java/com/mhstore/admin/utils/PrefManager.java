package com.mhstore.admin.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences wrapper for app settings and PIN storage
 */
public class PrefManager {

    private static final String PREF_NAME = "MHStoreAdminPrefs";
    private static final String KEY_PIN          = "admin_pin";
    private static final String KEY_PIN_SET      = "pin_set";
    private static final String KEY_FCM_TOKEN    = "fcm_token";
    private static final String KEY_LAST_SYNC    = "last_sync";
    private static final String KEY_BADGE_COUNT  = "badge_count";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    private static PrefManager instance;

    public static PrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefManager(context.getApplicationContext());
        }
        return instance;
    }

    private PrefManager(Context context) {
        prefs  = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // ── PIN ─────────────────────────────────────────────────────
    public void setPin(String pin)        { editor.putString(KEY_PIN, pin).putBoolean(KEY_PIN_SET, true).apply(); }
    public String getPin()                { return prefs.getString(KEY_PIN, ""); }
    public boolean isPinSet()             { return prefs.getBoolean(KEY_PIN_SET, false); }
    public boolean verifyPin(String input){ return getPin().equals(input); }
    public void clearPin()                { editor.remove(KEY_PIN).remove(KEY_PIN_SET).apply(); }

    // ── FCM ─────────────────────────────────────────────────────
    public void setFcmToken(String token) { editor.putString(KEY_FCM_TOKEN, token).apply(); }
    public String getFcmToken()           { return prefs.getString(KEY_FCM_TOKEN, ""); }

    // ── Notifications ────────────────────────────────────────────
    public boolean isNotificationsEnabled()            { return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true); }
    public void setNotificationsEnabled(boolean value) { editor.putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply(); }

    // ── Badge count ──────────────────────────────────────────────
    public int  getBadgeCount()                { return prefs.getInt(KEY_BADGE_COUNT, 0); }
    public void setBadgeCount(int count)       { editor.putInt(KEY_BADGE_COUNT, count).apply(); }
    public void incrementBadge()               { setBadgeCount(getBadgeCount() + 1); }
    public void clearBadge()                   { setBadgeCount(0); }

    // ── Misc ─────────────────────────────────────────────────────
    public void setLastSync(long ms)  { editor.putLong(KEY_LAST_SYNC, ms).apply(); }
    public long getLastSync()         { return prefs.getLong(KEY_LAST_SYNC, 0); }

    public void clearAll() {
        editor.clear().apply();
        instance = null;
    }
}
