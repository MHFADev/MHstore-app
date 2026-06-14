package com.mhstore.admin.models;

import com.google.firebase.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Order model mapping to Firestore document structure
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
    private Timestamp createdAt;
    private Map<String, Object> customer;
    private Map<String, Object> details;
    private String   adminNotes;
    private boolean  isRead;

    // Required empty constructor for Firestore
    public Order() {}

    public Order(String orderId, String service, String status,
                 Timestamp createdAt, Map<String, Object> customer,
                 Map<String, Object> details) {
        this.orderId   = orderId;
        this.service   = service;
        this.status    = status;
        this.createdAt = createdAt;
        this.customer  = customer;
        this.details   = details;
        this.adminNotes = "";
        this.isRead    = false;
    }

    // ── Getters ─────────────────────────────────────────────────
    public String    getOrderId()    { return orderId; }
    public String    getService()    { return service; }
    public String    getStatus()     { return status; }
    public Timestamp getCreatedAt()  { return createdAt; }
    public Map<String, Object> getCustomer() { return customer; }
    public Map<String, Object> getDetails()  { return details; }
    public String    getAdminNotes() { return adminNotes != null ? adminNotes : ""; }
    public boolean   isRead()        { return isRead; }

    // ── Setters ─────────────────────────────────────────────────
    public void setOrderId(String orderId)       { this.orderId = orderId; }
    public void setService(String service)       { this.service = service; }
    public void setStatus(String status)         { this.status = status; }
    public void setCreatedAt(Timestamp createdAt){ this.createdAt = createdAt; }
    public void setCustomer(Map<String, Object> customer) { this.customer = customer; }
    public void setDetails(Map<String, Object> details)   { this.details = details; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public void setRead(boolean read)            { this.isRead = read; }

    // ── Helpers ─────────────────────────────────────────────────
    public String getCustomerName() {
        if (customer == null) return "Unknown";
        Object name = customer.get("name");
        return name != null ? String.valueOf(name) : "Unknown";
    }

    public String getCustomerWhatsApp() {
        if (customer == null) return "";
        Object wa = customer.get("whatsapp");
        return wa != null ? String.valueOf(wa) : "";
    }

    public String getCustomerEmail() {
        if (customer == null) return "";
        Object email = customer.get("email");
        return email != null ? String.valueOf(email) : "";
    }

    public String getCustomerCity() {
        if (customer == null) return "";
        Object city = customer.get("city");
        return city != null ? String.valueOf(city) : "";
    }

    public String getServiceLabel() {
        if (SVC_PROFESSIONAL.equals(service)) return "💻 Website Professional";
        if (SVC_HOTSPOT.equals(service))      return "📡 Website Hotspot";
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
}
