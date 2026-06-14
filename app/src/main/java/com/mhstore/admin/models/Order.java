package com.mhstore.admin.models;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order model — maps to Supabase 'orders' table via REST API.
 * Replaces the former Firestore mapping (no Firebase dependency).
 */
public class Order {

    // ── Status constants ────────────────────────────────────────
    public static final String STATUS_PENDING    = "pending";
    public static final String STATUS_CONFIRMED  = "confirmed";
    public static final String STATUS_PROGRESS   = "in_progress";
    public static final String STATUS_COMPLETED  = "completed";
    public static final String STATUS_CANCELLED  = "cancelled";

    public static final String SVC_PROFESSIONAL = "professional";
    public static final String SVC_HOTSPOT      = "hotspot";

    // ── Fields ──────────────────────────────────────────────────
    private String   orderId;
    private String   service;
    private String   status;
    private String   createdAt;          // ISO 8601 date string from Supabase
    private Map<String, Object> customer;
    private Map<String, Object> details;
    private String   adminNotes;
    private boolean  isRead;

    // Required empty constructor
    public Order() {}

    // ── Getters ─────────────────────────────────────────────────
    public String    getOrderId()         { return orderId; }
    public String    getService()         { return service; }
    public String    getStatus()          { return status; }
    public String    getCreatedAt()       { return createdAt; }
    public Map<String, Object> getCustomer() { return customer; }
    public Map<String, Object> getDetails()  { return details; }
    public String    getAdminNotes()      { return adminNotes != null ? adminNotes : ""; }
    public boolean   isRead()             { return isRead; }

    // ── Setters ─────────────────────────────────────────────────
    public void setOrderId(String orderId)           { this.orderId = orderId; }
    public void setService(String service)           { this.service = service; }
    public void setStatus(String status)             { this.status = status; }
    public void setCreatedAt(String createdAt)       { this.createdAt = createdAt; }
    public void setCustomer(Map<String, Object> customer) { this.customer = customer; }
    public void setDetails(Map<String, Object> details)   { this.details = details; }
    public void setAdminNotes(String adminNotes)     { this.adminNotes = adminNotes; }
    public void setRead(boolean read)                { this.isRead = read; }

    // ── Helpers ─────────────────────────────────────────────────
    public String getCustomerName() {
        if (customer != null) {
            Object name = customer.get("name");
            if (name != null) return String.valueOf(name);
        }
        return "Unknown";
    }

    public String getCustomerWhatsApp() {
        if (customer != null) {
            Object wa = customer.get("whatsapp");
            if (wa != null) return String.valueOf(wa);
        }
        return "";
    }

    public String getCustomerEmail() {
        if (customer != null) {
            Object email = customer.get("email");
            if (email != null) return String.valueOf(email);
        }
        return "";
    }

    public String getCustomerCity() {
        if (customer != null) {
            Object city = customer.get("city");
            if (city != null) return String.valueOf(city);
        }
        return "";
    }

    public String getServiceLabel() {
        if (SVC_PROFESSIONAL.equals(service)) return "\uD83D\uDCBB Website Professional";
        if (SVC_HOTSPOT.equals(service))      return "\uD83D\uDCE1 Website Hotspot";
        return service != null ? service : "Unknown";
    }

    public String getStatusLabel() {
        switch (status != null ? status : "") {
            case STATUS_PENDING:   return "Pending";
            case STATUS_CONFIRMED: return "Dikonfirmasi";
            case STATUS_PROGRESS:  return "Dikerjakan";
            case STATUS_COMPLETED: return "Selesai";
            case STATUS_CANCELLED: return "Dibatalkan";
            default:               return "Unknown";
        }
    }

    public String getDetailValue(String key) {
        if (details == null) return "—";
        Object val = details.get(key);
        return val != null ? String.valueOf(val) : "—";
    }

    // ── Supabase JSON → Order ──────────────────────────────────
    @SuppressWarnings("unchecked")
    public static Order fromJson(JsonObject json) {
        Order o = new Order();
        o.orderId    = jsonStr(json, "id");
        o.service    = jsonStr(json, "service");
        o.status     = jsonStr(json, "status");
        o.createdAt  = jsonStr(json, "created_at");
        o.adminNotes = jsonStr(json, "admin_notes");
        o.isRead     = json.has("is_read") && !json.get("is_read").isJsonNull()
                       && json.get("is_read").getAsBoolean();

        // Build customer map from flat Supabase fields
        Map<String, Object> customer = new HashMap<>();
        putIfNotNull(customer, "name",      jsonStr(json, "customer_name"));
        putIfNotNull(customer, "whatsapp",  jsonStr(json, "customer_whatsapp"));
        putIfNotNull(customer, "email",     jsonStr(json, "customer_email"));
        putIfNotNull(customer, "city",      jsonStr(json, "customer_city"));
        putIfNotNull(customer, "business",  jsonStr(json, "customer_business"));
        o.customer = customer;

        // Parse details JSONB
        if (json.has("details") && !json.get("details").isJsonNull()) {
            try {
                o.details = new Gson().fromJson(json.get("details"), Map.class);
            } catch (Exception e) {
                o.details = new HashMap<>();
            }
        } else {
            o.details = new HashMap<>();
        }

        return o;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, value);
    }

    private static String jsonStr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        return o.get(key).getAsString();
    }
}
