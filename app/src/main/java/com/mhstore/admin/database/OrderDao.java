package com.mhstore.admin.database;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

/**
 * Data Access Object for the orders table.
 * All LiveData queries automatically update the UI on data change.
 */
@Dao
public interface OrderDao {

    // ── Insert / Upsert ───────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(OrderEntity order);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<OrderEntity> orders);

    // ── Update ────────────────────────────────────────────────
    @Update
    void update(OrderEntity order);

    @Query("UPDATE orders SET status = :status, updated_at = :updatedAt WHERE id = :id")
    void updateStatus(String id, String status, String updatedAt);

    @Query("UPDATE orders SET admin_notes = :notes WHERE id = :id")
    void updateAdminNotes(String id, String notes);

    @Query("UPDATE orders SET admin_message = :message WHERE id = :id")
    void updateAdminMessage(String id, String message);

    @Query("UPDATE orders SET is_read = 1 WHERE id = :id")
    void markAsRead(String id);

    @Query("UPDATE orders SET is_notified = 1 WHERE id = :id")
    void markAsNotified(String id);

    @Query("UPDATE orders SET is_read = 1")
    void markAllAsRead();

    // ── Query: All orders ─────────────────────────────────────
    @Query("SELECT * FROM orders ORDER BY created_at DESC")
    LiveData<List<OrderEntity>> getAllLive();

    @Query("SELECT * FROM orders ORDER BY created_at DESC")
    List<OrderEntity> getAll();

    @Query("SELECT * FROM orders ORDER BY created_at DESC LIMIT :limit")
    List<OrderEntity> getRecent(int limit);

    // ── Query: By status ──────────────────────────────────────
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY created_at DESC")
    LiveData<List<OrderEntity>> getByStatusLive(String status);

    @Query("SELECT * FROM orders WHERE status IN (:statuses) ORDER BY created_at DESC")
    LiveData<List<OrderEntity>> getByStatusesLive(List<String> statuses);

    // ── Query: Single order ───────────────────────────────────
    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    OrderEntity getById(String id);

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    LiveData<OrderEntity> getByIdLive(String id);

    // ── Query: Unread / unnotified ────────────────────────────
    @Query("SELECT * FROM orders WHERE is_read = 0 ORDER BY created_at DESC")
    List<OrderEntity> getUnread();

    @Query("SELECT * FROM orders WHERE is_notified = 0 ORDER BY created_at DESC")
    List<OrderEntity> getUnnotified();

    @Query("SELECT COUNT(*) FROM orders WHERE is_read = 0")
    int getUnreadCount();

    // ── Query: Stats ──────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM orders")
    int getTotalCount();

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'pending' OR status = 'confirmed'")
    int getPendingCount();

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'in_progress'")
    int getInProgressCount();

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'completed'")
    int getCompletedCount();

    @Query("SELECT COALESCE(SUM(price_total), 0) FROM orders WHERE status = 'completed'")
    int getCompletedRevenue();

    // ── Query: Check existence ────────────────────────────────
    @Query("SELECT COUNT(*) FROM orders WHERE id = :id")
    int exists(String id);

    // ── Delete ────────────────────────────────────────────────
    @Query("DELETE FROM orders WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM orders")
    void deleteAll();

    // ── Latest synced order for polling ──────────────────────
    @Query("SELECT created_at FROM orders ORDER BY created_at DESC LIMIT 1")
    String getLatestCreatedAt();
}
