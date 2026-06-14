package com.mhstore.admin.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity — mirrors the Supabase 'orders' table.
 * New fields: package_type, website_name, website_description,
 *             domain, price_base, price_domain, price_total,
 *             delivery_method, url_reference, additional_notes.
 */
@Entity(tableName = "orders")
public class OrderEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "service")
    public String service;             // portfolio | company_profile | landing_page | hotspot

    @ColumnInfo(name = "package_type")
    public String packageType;         // bronze | platinum | grandmaster | null

    @ColumnInfo(name = "status")
    public String status = "pending";  // pending | confirmed | in_progress | completed | cancelled

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    // ── Customer ──────────────────────────────────────────────
    @ColumnInfo(name = "customer_name")
    public String customerName;

    @ColumnInfo(name = "customer_whatsapp")
    public String customerWhatsapp;

    // ── Website info ──────────────────────────────────────────
    @ColumnInfo(name = "website_name")
    public String websiteName;

    @ColumnInfo(name = "website_description")
    public String websiteDescription;

    @ColumnInfo(name = "domain")
    public String domain;              // vercel.app | web.id | my.id | biz.id

    // ── Pricing ───────────────────────────────────────────────
    @ColumnInfo(name = "price_base")
    public int priceBase;

    @ColumnInfo(name = "price_domain")
    public int priceDomain;

    @ColumnInfo(name = "price_total")
    public int priceTotal;

    @ColumnInfo(name = "delivery_method")
    public String deliveryMethod;      // whatsapp | gdrive | github

    // ── References ────────────────────────────────────────────
    @ColumnInfo(name = "url_reference")
    public String urlReference;

    @ColumnInfo(name = "additional_notes")
    public String additionalNotes;

    // ── Service-specific details (serialized JSON string) ─────
    @ColumnInfo(name = "details_json")
    public String detailsJson;         // JSONB stored as TEXT locally

    // ── Attachments (serialized JSON array) ───────────────────
    @ColumnInfo(name = "attachments_json")
    public String attachmentsJson = "[]";

    // ── Admin ─────────────────────────────────────────────────
    @ColumnInfo(name = "admin_notes")
    public String adminNotes = "";

    @ColumnInfo(name = "admin_message")
    public String adminMessage = "";

    @ColumnInfo(name = "is_read")
    public boolean isRead;

    // ── Local-only flags ─────────────────────────────────────
    @ColumnInfo(name = "is_notified")
    public boolean isNotified;         // true after local notification shown

    @ColumnInfo(name = "synced_at")
    public long syncedAt;             // System.currentTimeMillis() of last sync

    // ── Helpers ──────────────────────────────────────────────
    public String getServiceLabel() {
        if (service == null) return "Unknown";
        switch (service) {
            case "portfolio":       return "🎨 Portfolio Website";
            case "company_profile": return "🏢 Company Profile";
            case "landing_page":    return "🚀 Landing Page";
            case "hotspot":         return "📡 Hotspot Mikrotik";
            default:                return service;
        }
    }

    public String getPackageLabel() {
        if (packageType == null) return "";
        switch (packageType) {
            case "bronze":      return "🥉 Bronze";
            case "platinum":    return "🥈 Platinum";
            case "grandmaster": return "🏆 Grandmaster";
            default:            return packageType;
        }
    }

    public String getStatusLabel() {
        if (status == null) return "Unknown";
        switch (status) {
            case "pending":     return "Pending";
            case "confirmed":   return "Dikonfirmasi";
            case "in_progress": return "Dikerjakan";
            case "completed":   return "Selesai";
            case "cancelled":   return "Dibatalkan";
            default:            return status;
        }
    }

    public String getFormattedPrice() {
        if (priceTotal <= 0) return "—";
        return "Rp " + String.format("%,d", priceTotal).replace(',', '.');
    }
}
