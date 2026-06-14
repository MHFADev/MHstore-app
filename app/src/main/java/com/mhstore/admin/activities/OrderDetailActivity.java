package com.mhstore.admin.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mhstore.admin.R;
import com.mhstore.admin.models.Order;
import com.mhstore.admin.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Order detail view.
 * Shows full customer info, service details, status management,
 * admin notes, and quick WhatsApp contact button.
 */
public class OrderDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String orderId;
    private Order  currentOrder;

    // Views
    private TextView  tvOrderId, tvServiceBadge, tvCreatedAt, tvStatusLabel;
    private TextView  tvCustName, tvCustEmail, tvCustWa, tvCustCity, tvCustBiz;
    private LinearLayout detailsContainer;
    private Spinner  spinStatus;
    private EditText etAdminNotes;
    private MaterialButton btnSave, btnWa, btnCopyPhone;
    private ProgressBar progressBar;
    private View      contentView;
    private ChipGroup chipGroupFeatures;

    private static final String[] STATUS_VALUES = {
        Order.STATUS_PENDING, Order.STATUS_CONFIRMED,
        Order.STATUS_PROGRESS, Order.STATUS_COMPLETED, Order.STATUS_CANCELLED
    };
    private static final String[] STATUS_LABELS = {
        "Pending", "Dikonfirmasi", "Sedang Dikerjakan", "Selesai", "Dibatalkan"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        db = FirebaseFirestore.getInstance();

        orderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        if (orderId == null) { finish(); return; }

        setupToolbar();
        bindViews();
        setupStatusSpinner();
        loadOrder();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Detail Pesanan");
        }
    }

    private void bindViews() {
        progressBar      = findViewById(R.id.progress_bar);
        contentView      = findViewById(R.id.content_scroll);
        tvOrderId        = findViewById(R.id.tv_order_id);
        tvServiceBadge   = findViewById(R.id.tv_service_badge);
        tvCreatedAt      = findViewById(R.id.tv_created_at);
        tvStatusLabel    = findViewById(R.id.tv_status_label);
        tvCustName       = findViewById(R.id.tv_cust_name);
        tvCustEmail      = findViewById(R.id.tv_cust_email);
        tvCustWa         = findViewById(R.id.tv_cust_wa);
        tvCustCity       = findViewById(R.id.tv_cust_city);
        tvCustBiz        = findViewById(R.id.tv_cust_biz);
        detailsContainer = findViewById(R.id.details_container);
        spinStatus       = findViewById(R.id.spin_status);
        etAdminNotes     = findViewById(R.id.et_admin_notes);
        btnSave          = findViewById(R.id.btn_save_status);
        btnWa            = findViewById(R.id.btn_wa_contact);
        btnCopyPhone     = findViewById(R.id.btn_copy_phone);
        chipGroupFeatures= findViewById(R.id.chip_group_features);

        btnSave.setOnClickListener(v -> saveStatusAndNotes());
        btnWa.setOnClickListener(v -> openWhatsApp());
        btnCopyPhone.setOnClickListener(v -> copyPhone());
    }

    private void setupStatusSpinner() {
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, STATUS_LABELS);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinStatus.setAdapter(statusAdapter);
    }

    private void loadOrder() {
        showLoading(true);
        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
            .get()
            .addOnSuccessListener(doc -> {
                showLoading(false);
                if (!doc.exists()) { showSnackbar("Pesanan tidak ditemukan"); finish(); return; }
                try {
                    currentOrder = doc.toObject(Order.class);
                    if (currentOrder != null) {
                        if (currentOrder.getOrderId() == null) currentOrder.setOrderId(doc.getId());
                        populateUI();
                        // Mark as read
                        if (!currentOrder.isRead()) {
                            doc.getReference().update(Constants.FIELD_IS_READ, true);
                        }
                    }
                } catch (Exception e) {
                    showSnackbar("Error: " + e.getMessage());
                }
            })
            .addOnFailureListener(e -> { showLoading(false); showSnackbar("Gagal memuat: " + e.getMessage()); });
    }

    private void populateUI() {
        if (currentOrder == null) return;
        // Header
        tvOrderId.setText("#" + currentOrder.getOrderId());
        tvServiceBadge.setText(currentOrder.getServiceLabel());
        setServiceBadgeColor();

        // Timestamp
        Timestamp ts = currentOrder.getCreatedAt();
        if (ts != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy · HH:mm", new Locale("id","ID"));
            tvCreatedAt.setText(sdf.format(ts.toDate()));
        }

        // Status
        String status = currentOrder.getStatus();
        tvStatusLabel.setText(currentOrder.getStatusLabel());
        setStatusColor(tvStatusLabel, status);
        int spinPos = 0;
        for (int i = 0; i < STATUS_VALUES.length; i++) {
            if (STATUS_VALUES[i].equals(status)) { spinPos = i; break; }
        }
        spinStatus.setSelection(spinPos);

        // Customer
        tvCustName.setText(currentOrder.getCustomerName());
        tvCustEmail.setText(nvl(currentOrder.getCustomerEmail(), "—"));
        tvCustWa.setText(nvl(currentOrder.getCustomerWhatsApp(), "—"));
        tvCustCity.setText(nvl(currentOrder.getCustomerCity(), "—"));

        Map<String, Object> cust = currentOrder.getCustomer();
        String biz = cust != null ? nvl(String.valueOf(cust.getOrDefault("business", "")), "—") : "—";
        tvCustBiz.setText(biz);

        // Admin notes
        etAdminNotes.setText(currentOrder.getAdminNotes());

        // Service details
        populateDetails();
    }

    private void populateDetails() {
        if (detailsContainer == null) return;
        detailsContainer.removeAllViews();
        if (chipGroupFeatures != null) chipGroupFeatures.removeAllViews();

        Map<String, Object> d = currentOrder.getDetails();
        if (d == null) return;

        String svc = currentOrder.getService();

        if (Order.SVC_PROFESSIONAL.equals(svc)) {
            addDetailRow("Jenis Website",   d, "websiteType");
            addDetailRow("Gaya Desain",     d, "designStyle");
            addDetailRow("Domain",          d, "domain");
            addDetailRow("Budget",          d, "budget");
            addDetailRow("Deadline",        d, "deadline");
            addDetailRow("Source Code",     d, "requestSourceCode");
            addDetailRow("Setup SEO",       d, "setupSEO");
            addDetailRow("Nama Domain",     d, "domainName");
            addDetailRow("Referensi",       d, "refLink");
            addDetailRow("Deskripsi",       d, "description");
        } else if (Order.SVC_HOTSPOT.equals(svc)) {
            addDetailRow("Nama Usaha",      d, "businessName");
            addDetailRow("Model MikroTik",  d, "mikrotikModel");
            addDetailRow("Jenis",           d, "usageType");
            addDetailRow("Bahasa",          d, "language");
            addDetailRow("Pengiriman",      d, "delivery");
            addDetailRow("Warna Preferensi",d, "colorPref");
            addDetailRow("Logo",            d, "logo");
            addDetailRow("Catatan",         d, "notes");

            // Features as chips
            Object featsObj = d.get("features");
            if (featsObj instanceof List && chipGroupFeatures != null) {
                List<?> feats = (List<?>) featsObj;
                for (Object f : feats) {
                    Chip chip = new Chip(this);
                    chip.setText(String.valueOf(f));
                    chip.setChipBackgroundColorResource(R.color.primary_container);
                    chip.setTextColor(getColor(R.color.text_primary));
                    chip.setClickable(false);
                    chipGroupFeatures.addView(chip);
                }
            }
        }
    }

    private void addDetailRow(String label, Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null || String.valueOf(val).isEmpty() || "null".equals(String.valueOf(val))) return;

        View row = getLayoutInflater().inflate(R.layout.item_detail_row, detailsContainer, false);
        TextView tvLabel = row.findViewById(R.id.detail_label);
        TextView tvValue = row.findViewById(R.id.detail_value);
        tvLabel.setText(label);

        String valStr = String.valueOf(val);
        if ("true".equals(valStr)) valStr = "✓ Ya";
        else if ("false".equals(valStr)) valStr = "✗ Tidak";
        tvValue.setText(valStr);
        detailsContainer.addView(row);
    }

    private void saveStatusAndNotes() {
        if (currentOrder == null) return;
        int pos    = spinStatus.getSelectedItemPosition();
        String newStatus = STATUS_VALUES[pos];
        String notes     = etAdminNotes.getText().toString().trim();

        Map<String, Object> updates = new HashMap<>();
        updates.put(Constants.FIELD_STATUS,      newStatus);
        updates.put(Constants.FIELD_ADMIN_NOTES, notes);

        btnSave.setEnabled(false);
        btnSave.setText("Menyimpan...");

        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
            .update(updates)
            .addOnSuccessListener(unused -> {
                currentOrder.setStatus(newStatus);
                currentOrder.setAdminNotes(notes);
                tvStatusLabel.setText(currentOrder.getStatusLabel());
                setStatusColor(tvStatusLabel, newStatus);
                showSnackbar("✓ Status diperbarui: " + STATUS_LABELS[pos]);
                btnSave.setEnabled(true);
                btnSave.setText("Simpan Perubahan");
            })
            .addOnFailureListener(e -> {
                showSnackbar("Gagal menyimpan: " + e.getMessage());
                btnSave.setEnabled(true);
                btnSave.setText("Simpan Perubahan");
            });
    }

    private void openWhatsApp() {
        String wa = currentOrder != null ? currentOrder.getCustomerWhatsApp() : "";
        if (wa == null || wa.isEmpty()) { showSnackbar("No. WA tidak tersedia"); return; }
        if (wa.startsWith("0")) wa = "62" + wa.substring(1);
        String clean = wa.replaceAll("[^0-9]", "");
        String name  = currentOrder.getCustomerName();
        String ordId = currentOrder.getOrderId();
        String msg   = Uri.encode("Halo " + name + "! Ini dari tim MHStore mengenai pesanan #" + ordId + ". ");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + clean + "?text=" + msg)));
        } catch (Exception e) {
            showSnackbar("WhatsApp tidak terinstall");
        }
    }

    private void copyPhone() {
        String wa = currentOrder != null ? currentOrder.getCustomerWhatsApp() : "";
        if (wa == null || wa.isEmpty()) { showSnackbar("No. WA tidak tersedia"); return; }
        android.content.ClipboardManager clip = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clip != null) {
            clip.setPrimaryClip(android.content.ClipData.newPlainText("WA", wa));
            showSnackbar("Nomor tersalin: " + wa);
        }
    }

    private void setServiceBadgeColor() {
        if (Order.SVC_HOTSPOT.equals(currentOrder.getService())) {
            tvServiceBadge.setBackgroundResource(R.drawable.bg_badge_green);
        } else {
            tvServiceBadge.setBackgroundResource(R.drawable.bg_badge_blue);
        }
    }

    private void setStatusColor(TextView tv, String status) {
        int color;
        switch (status != null ? status : "") {
            case Order.STATUS_CONFIRMED: color = getColor(R.color.status_confirmed); break;
            case Order.STATUS_PROGRESS:  color = getColor(R.color.status_progress);  break;
            case Order.STATUS_COMPLETED: color = getColor(R.color.status_completed); break;
            case Order.STATUS_CANCELLED: color = getColor(R.color.status_cancelled); break;
            default:                     color = getColor(R.color.status_pending);
        }
        tv.setTextColor(color);
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (contentView  != null) contentView.setVisibility(show ? View.GONE   : View.VISIBLE);
    }

    private void showSnackbar(String msg) {
        View root = findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }

    private String nvl(String s, String def) {
        return (s == null || s.isEmpty() || "null".equals(s)) ? def : s;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
