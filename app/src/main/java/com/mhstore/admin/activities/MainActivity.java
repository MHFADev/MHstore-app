package com.mhstore.admin.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonObject;
import com.mhstore.admin.R;
import com.mhstore.admin.adapters.OrderAdapter;
import com.mhstore.admin.models.Order;
import com.mhstore.admin.network.SupabaseClient;
import com.mhstore.admin.utils.Constants;
import com.mhstore.admin.utils.Logger;
import com.mhstore.admin.utils.PrefManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Main admin dashboard.
 * Shows order list with tab filters and stats cards.
 * Uses SupabaseClient REST API + Realtime WebSocket.
 */
public class MainActivity extends AppCompatActivity implements OrderAdapter.OnOrderClickListener {

    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private List<Order> allOrders   = new ArrayList<>();
    private List<Order> shownOrders = new ArrayList<>();
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private TextView tvEmpty;

    // Stats TextViews
    private TextView tvTotal, tvPending, tvProgress, tvDone;

    private SupabaseClient supabase;
    private int currentTab = Constants.TAB_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        supabase = SupabaseClient.getInstance();

        setupToolbar();
        setupStats();
        setupTabs();
        setupRecyclerView();
        setupSwipeRefresh();
        loadOrders();
        connectRealtime();

        // Handle notification deep link
        String notifOrderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        if (notifOrderId != null) {
            openOrderDetail(notifOrderId);
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("MHStore Admin");
            getSupportActionBar().setSubtitle("Dashboard Pesanan");
        }
    }

    private void setupStats() {
        tvTotal    = findViewById(R.id.tv_stat_total);
        tvPending  = findViewById(R.id.tv_stat_pending);
        tvProgress = findViewById(R.id.tv_stat_progress);
        tvDone     = findViewById(R.id.tv_stat_done);
    }

    private void setupTabs() {
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText("Semua"));
        tabs.addTab(tabs.newTab().setText("Pending"));
        tabs.addTab(tabs.newTab().setText("Dikerjakan"));
        tabs.addTab(tabs.newTab().setText("Selesai"));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                filterAndShow();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        recyclerView  = findViewById(R.id.recycler_orders);
        emptyState    = findViewById(R.id.empty_state);
        tvEmpty       = findViewById(R.id.tv_empty);
        adapter = new OrderAdapter(shownOrders, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.secondary);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.bg_surface);
        swipeRefresh.setOnRefreshListener(() -> {
            loadOrders();
            swipeRefresh.setRefreshing(false);
        });
    }

    // ── Data Loading (Supabase REST) ───────────────────────────
    private void loadOrders() {
        if (!supabase.isConfigured()) return;

        supabase.getOrders(Constants.MAX_ORDERS_PER_LOAD, new SupabaseClient.Callback<List<JsonObject>>() {
            @Override
            public void onSuccess(List<JsonObject> result) {
                allOrders.clear();
                for (JsonObject json : result) {
                    try {
                        Order order = Order.fromJson(json);
                        if (order != null) allOrders.add(order);
                    } catch (Exception e) {
                        Logger.e("MainActivity", "Parse error: " + e.getMessage());
                    }
                }
                updateStats();
                filterAndShow();
            }

            @Override
            public void onError(String error) {
                showSnackbar("Gagal memuat data: " + error);
            }
        });
    }

    // ── Realtime WebSocket ──────────────────────────────────────
    private void connectRealtime() {
        if (!supabase.isConfigured()) return;

        supabase.connectRealtime(new SupabaseClient.RealtimeCallback() {
            @Override
            public void onOrderInserted(JsonObject record) {
                runOnUiThread(() -> {
                    try {
                        Order order = Order.fromJson(record);
                        if (order != null) {
                            allOrders.add(0, order);
                            updateStats();
                            filterAndShow();
                        }
                    } catch (Exception e) {
                        Logger.e("MainActivity", "Realtime parse error: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onOrderUpdated(JsonObject record) {
                runOnUiThread(() -> {
                    try {
                        Order updated = Order.fromJson(record);
                        if (updated != null) {
                            // Replace existing order in list
                            for (int i = 0; i < allOrders.size(); i++) {
                                if (updated.getOrderId() != null
                                        && updated.getOrderId().equals(allOrders.get(i).getOrderId())) {
                                    allOrders.set(i, updated);
                                    break;
                                }
                            }
                            updateStats();
                            filterAndShow();
                        }
                    } catch (Exception e) {
                        Logger.e("MainActivity", "Realtime update parse error: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onConnected() {
                Logger.i("MainActivity", "Realtime connected");
            }

            @Override
            public void onDisconnected() {
                Logger.w("MainActivity", "Realtime disconnected");
            }
        });
    }

    private void filterAndShow() {
        shownOrders.clear();
        for (Order o : allOrders) {
            boolean include;
            switch (currentTab) {
                case Constants.TAB_PENDING:   include = Order.STATUS_PENDING.equals(o.getStatus()) || Order.STATUS_CONFIRMED.equals(o.getStatus()); break;
                case Constants.TAB_PROGRESS:  include = Order.STATUS_PROGRESS.equals(o.getStatus()); break;
                case Constants.TAB_COMPLETED: include = Order.STATUS_COMPLETED.equals(o.getStatus()) || Order.STATUS_CANCELLED.equals(o.getStatus()); break;
                default:                      include = true;
            }
            if (include) shownOrders.add(o);
        }
        adapter.notifyDataSetChanged();

        // Empty state
        boolean empty = shownOrders.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (tvEmpty != null) {
            switch (currentTab) {
                case Constants.TAB_PENDING:   tvEmpty.setText("Tidak ada pesanan pending"); break;
                case Constants.TAB_PROGRESS:  tvEmpty.setText("Tidak ada pesanan sedang dikerjakan"); break;
                case Constants.TAB_COMPLETED: tvEmpty.setText("Tidak ada pesanan selesai"); break;
                default:                      tvEmpty.setText("Belum ada pesanan masuk"); break;
            }
        }
    }

    private void updateStats() {
        int total = allOrders.size();
        int pending = 0, progress = 0, done = 0;
        for (Order o : allOrders) {
            String s = o.getStatus();
            if (Order.STATUS_PENDING.equals(s) || Order.STATUS_CONFIRMED.equals(s)) pending++;
            else if (Order.STATUS_PROGRESS.equals(s)) progress++;
            else if (Order.STATUS_COMPLETED.equals(s)) done++;
        }
        if (tvTotal    != null) tvTotal.setText(String.valueOf(total));
        if (tvPending  != null) tvPending.setText(String.valueOf(pending));
        if (tvProgress != null) tvProgress.setText(String.valueOf(progress));
        if (tvDone     != null) tvDone.setText(String.valueOf(done));
    }

    @Override
    public void onOrderClick(Order order) {
        openOrderDetail(order.getOrderId());
    }

    @Override
    public void onWhatsAppClick(Order order) {
        String wa = order.getCustomerWhatsApp();
        if (wa == null || wa.isEmpty()) {
            showSnackbar("Nomor WhatsApp tidak tersedia");
            return;
        }
        // Clean number format
        if (wa.startsWith("0")) wa = "62" + wa.substring(1);
        String url = "https://wa.me/" + wa.replaceAll("[^0-9]", "");
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        try { startActivity(intent); } catch (Exception e) {
            showSnackbar("WhatsApp tidak tersedia");
        }
    }

    private void openOrderDetail(String orderId) {
        Intent i = new Intent(this, OrderDetailActivity.class);
        i.putExtra(Constants.EXTRA_ORDER_ID, orderId);
        startActivity(i);
    }

    private void showSnackbar(String msg) {
        View root = findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Keluar dari dashboard admin?")
                .setPositiveButton("Logout", (d, w) -> {
                    supabase.disconnectRealtime();
                    startActivity(new Intent(this, LoginActivity.class));
                    finishAffinity();
                })
                .setNegativeButton("Batal", null)
                .show();
            return true;
        }
        if (item.getItemId() == R.id.action_change_pin) {
            PrefManager.getInstance(this).clearPin();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        supabase.disconnectRealtime();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String orderId = intent.getStringExtra(Constants.EXTRA_ORDER_ID);
        if (orderId != null) openOrderDetail(orderId);
    }
}
