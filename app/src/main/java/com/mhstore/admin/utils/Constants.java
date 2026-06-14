package com.mhstore.admin.utils;

/** App-wide constants — updated for 4-service / 3-package system. */
public final class Constants {
    private Constants() {}

    // ── Intent extras ─────────────────────────────────────────
    public static final String EXTRA_ORDER_ID   = "extra_order_id";
    public static final String EXTRA_FROM_NOTIF = "extra_from_notification";

    // ── Supabase table names ──────────────────────────────────
    public static final String TABLE_ORDERS   = "orders";
    public static final String TABLE_MESSAGES = "admin_messages";
    public static final String TABLE_LOGS     = "app_logs";
    public static final String TABLE_SETTINGS = "settings";

    // ── Order statuses ────────────────────────────────────────
    public static final String STATUS_PENDING    = "pending";
    public static final String STATUS_CONFIRMED  = "confirmed";
    public static final String STATUS_PROGRESS   = "in_progress";
    public static final String STATUS_COMPLETED  = "completed";
    public static final String STATUS_CANCELLED  = "cancelled";

    public static final String[] ALL_STATUSES = {
        STATUS_PENDING, STATUS_CONFIRMED, STATUS_PROGRESS,
        STATUS_COMPLETED, STATUS_CANCELLED
    };
    public static final String[] STATUS_LABELS = {
        "Pending", "Dikonfirmasi", "Dikerjakan", "Selesai", "Dibatalkan"
    };

    // ── Service types ─────────────────────────────────────────
    public static final String SVC_PORTFOLIO   = "portfolio";
    public static final String SVC_COMPANY     = "company_profile";
    public static final String SVC_LANDING     = "landing_page";
    public static final String SVC_HOTSPOT     = "hotspot";

    // ── Package types ─────────────────────────────────────────
    public static final String PKG_BRONZE      = "bronze";
    public static final String PKG_PLATINUM    = "platinum";
    public static final String PKG_GRANDMASTER = "grandmaster";

    // ── Tab indices in MainActivity ────────────────────────────
    public static final int TAB_ALL       = 0;
    public static final int TAB_PENDING   = 1;
    public static final int TAB_PROGRESS  = 2;
    public static final int TAB_COMPLETED = 3;

    // ── WA number ────────────────────────────────────────────
    public static final String WA_NUMBER = "6281574647741";

    // ── Polling ───────────────────────────────────────────────
    public static final int MAX_ORDERS_PER_LOAD = 50;

    // ── Permissions ───────────────────────────────────────────
    public static final int REQ_NOTIFICATION_PERM = 1001;
}
