package com.mhstore.admin.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.Timestamp;
import com.mhstore.admin.R;
import com.mhstore.admin.models.Order;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the order list in MainActivity.
 * Supports tap-to-detail and WhatsApp quick-action per row.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
        void onWhatsAppClick(Order order);
    }

    private final List<Order> orders;
    private final OnOrderClickListener listener;
    private final SimpleDateFormat sdf =
        new SimpleDateFormat("dd MMM · HH:mm", new Locale("id", "ID"));

    public OrderAdapter(List<Order> orders, OnOrderClickListener listener) {
        this.orders   = orders;
        this.listener = listener;
    }

    @NonNull @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                     .inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order);
    }

    @Override public int getItemCount() { return orders.size(); }

    class OrderViewHolder extends RecyclerView.ViewHolder {
        private final TextView  tvOrderId, tvCustName, tvService, tvTime, tvStatus;
        private final ImageButton btnWa;
        private final View    unreadDot;
        private final View    serviceIcon;

        OrderViewHolder(View itemView) {
            super(itemView);
            tvOrderId   = itemView.findViewById(R.id.tv_item_order_id);
            tvCustName  = itemView.findViewById(R.id.tv_item_cust_name);
            tvService   = itemView.findViewById(R.id.tv_item_service);
            tvTime      = itemView.findViewById(R.id.tv_item_time);
            tvStatus    = itemView.findViewById(R.id.tv_item_status);
            btnWa       = itemView.findViewById(R.id.btn_item_wa);
            unreadDot   = itemView.findViewById(R.id.unread_dot);
            serviceIcon = itemView.findViewById(R.id.service_icon_bg);
        }

        void bind(Order order) {
            Context ctx = itemView.getContext();

            tvOrderId.setText("#" + (order.getOrderId() != null ? order.getOrderId().substring(0, Math.min(12, order.getOrderId().length())) : "—"));
            tvCustName.setText(order.getCustomerName());
            tvService.setText(order.getServiceLabel());

            // Time
            Timestamp ts = order.getCreatedAt();
            tvTime.setText(ts != null ? sdf.format(ts.toDate()) : "—");

            // Status badge
            tvStatus.setText(order.getStatusLabel());
            int statusColor = getStatusColor(ctx, order.getStatus());
            tvStatus.setTextColor(statusColor);

            // Status background tint
            tvStatus.setBackgroundResource(getStatusBgRes(order.getStatus()));

            // Service icon background color
            if (serviceIcon != null) {
                serviceIcon.setBackgroundResource(
                    Order.SVC_HOTSPOT.equals(order.getService())
                        ? R.drawable.bg_service_green
                        : R.drawable.bg_service_blue
                );
            }

            // Unread dot
            if (unreadDot != null) {
                unreadDot.setVisibility(order.isRead() ? View.INVISIBLE : View.VISIBLE);
            }

            // Click listeners
            itemView.setOnClickListener(v -> { if (listener != null) listener.onOrderClick(order); });
            if (btnWa != null) btnWa.setOnClickListener(v -> { if (listener != null) listener.onWhatsAppClick(order); });
        }

        private int getStatusColor(Context ctx, String status) {
            switch (status != null ? status : "") {
                case Order.STATUS_CONFIRMED: return ctx.getColor(R.color.status_confirmed);
                case Order.STATUS_PROGRESS:  return ctx.getColor(R.color.status_progress);
                case Order.STATUS_COMPLETED: return ctx.getColor(R.color.status_completed);
                case Order.STATUS_CANCELLED: return ctx.getColor(R.color.status_cancelled);
                default:                     return ctx.getColor(R.color.status_pending);
            }
        }

        private int getStatusBgRes(String status) {
            switch (status != null ? status : "") {
                case Order.STATUS_CONFIRMED: return R.drawable.bg_status_confirmed;
                case Order.STATUS_PROGRESS:  return R.drawable.bg_status_progress;
                case Order.STATUS_COMPLETED: return R.drawable.bg_status_completed;
                case Order.STATUS_CANCELLED: return R.drawable.bg_status_cancelled;
                default:                     return R.drawable.bg_status_pending;
            }
        }
    }
}
