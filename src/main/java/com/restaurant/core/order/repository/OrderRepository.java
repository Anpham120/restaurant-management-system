package com.restaurant.core.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.order.dto.ItemEventResponse;
import com.restaurant.core.order.dto.OrderItemResponse;

/**
 * Đơn hàng, lượt gọi món và dòng món. Mọi chuyển trạng thái dòng món đi qua một hàm rc_* (state machine, quota,
 * đặt giữ / tiêu hao nguyên liệu FEFO đều do PostgreSQL làm) — Java không cập nhật order_items trực tiếp.
 */
@Repository
public class OrderRepository {

    public record OrderRow(long id, String status, long tableSessionId) {
    }

    private static final String ITEM_SELECT = """
            SELECT oi.id, b.order_id, oi.order_batch_id AS batch_id, oi.daily_menu_item_id, oi.item_name_snapshot AS item_name,
                   oi.quantity, oi.unit_price, oi.status, oi.has_stock_exception, oi.note, b.submitted_at, t.code AS table_code
              FROM order_items oi
              JOIN order_batches b ON b.id = oi.order_batch_id
              JOIN orders o ON o.id = b.order_id
              JOIN table_sessions s ON s.id = o.table_session_id
              JOIN dining_tables t ON t.id = s.dining_table_id
            """;

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OrderRow> findOrder(long orderId) {
        return jdbc.sql("SELECT id, status, table_session_id FROM orders WHERE id = :id").param("id", orderId)
                .query(OrderRow.class).optional();
    }

    public long findOrderIdOfSession(long sessionId) {
        return jdbc.sql("SELECT id FROM orders WHERE table_session_id = :s").param("s", sessionId).query(Long.class).single();
    }

    public List<OrderItemResponse> findItemsOfOrder(long orderId) {
        return jdbc.sql(ITEM_SELECT + " WHERE b.order_id = :o ORDER BY b.submitted_at, oi.id").param("o", orderId)
                .query(OrderItemResponse.class).list();
    }

    public List<OrderItemResponse> findItemsOfBatch(long batchId) {
        return jdbc.sql(ITEM_SELECT + " WHERE oi.order_batch_id = :b ORDER BY oi.id").param("b", batchId)
                .query(OrderItemResponse.class).list();
    }

    public Optional<OrderItemResponse> findItem(long itemId) {
        return jdbc.sql(ITEM_SELECT + " WHERE oi.id = :id").param("id", itemId).query(OrderItemResponse.class).optional();
    }

    /** Hàng chờ bếp: dòng món ở các trạng thái cho trước của đơn còn OPEN, cũ nhất trước. */
    public List<OrderItemResponse> findKitchenQueue(List<String> statuses) {
        return jdbc.sql(ITEM_SELECT + " WHERE o.status = 'OPEN' AND oi.status IN (:statuses) ORDER BY b.submitted_at, oi.id")
                .param("statuses", statuses).query(OrderItemResponse.class).list();
    }

    public List<ItemEventResponse> findItemEvents(long itemId) {
        return jdbc.sql("""
                        SELECT e.id, e.from_status, e.to_status, e.actor_id, emp.full_name AS actor_name, e.reason, e.created_at
                          FROM order_item_events e JOIN employees emp ON emp.id = e.actor_id
                         WHERE e.order_item_id = :i ORDER BY e.id
                        """)
                .param("i", itemId).query(ItemEventResponse.class).list();
    }

    /** Lý do của sự kiện REJECTED gần nhất (do rc_accept_item hoặc rc_reject_item ghi). */
    public Optional<String> findRejectReason(long itemId) {
        return jdbc.sql("SELECT reason FROM order_item_events WHERE order_item_id = :i AND to_status = 'REJECTED' ORDER BY id DESC LIMIT 1")
                .param("i", itemId).query(String.class).optional();
    }

    /** rc_submit_batch(p_order, p_actor, p_request_key, p_items jsonb) → jsonb {"batch_id","replayed","warnings"} (trả dạng text). */
    public String submitBatch(long orderId, long actorId, String requestKey, String itemsJson) {
        return jdbc.sql("SELECT rc_submit_batch(:o, :a, :k, CAST(:items AS jsonb))::text")
                .param("o", orderId).param("a", actorId).param("k", requestKey).param("items", itemsJson)
                .query(String.class).single();
    }

    /** rc_accept_item(p_item, p_actor) → 'ACCEPTED' | 'REJECTED' (quota + nguyên liệu kiểm và đặt giữ nguyên tử). */
    public String acceptItem(long itemId, long actorId) {
        return jdbc.sql("SELECT rc_accept_item(:i, :a)").param("i", itemId).param("a", actorId).query(String.class).single();
    }

    public void rejectItem(long itemId, long actorId, String reason) {
        jdbc.sql("SELECT rc_reject_item(:i, :a, :r)").param("i", itemId).param("a", actorId).param("r", reason).query().singleRow();
    }

    public void startCooking(long itemId, long actorId) {
        jdbc.sql("SELECT rc_start_cooking(:i, :a)").param("i", itemId).param("a", actorId).query().singleRow();
    }

    public void finishDish(long itemId, long actorId) {
        jdbc.sql("SELECT rc_finish_dish(:i, :a)").param("i", itemId).param("a", actorId).query().singleRow();
    }

    public void serveItem(long itemId, long actorId) {
        jdbc.sql("SELECT rc_serve_item(:i, :a)").param("i", itemId).param("a", actorId).query().singleRow();
    }

    public void reallocateItem(long itemId, long actorId) {
        jdbc.sql("SELECT rc_reallocate_item(:i, :a)").param("i", itemId).param("a", actorId).query().singleRow();
    }

    /** Hủy thường (trước COOKING): giải phóng phần giữ nguyên liệu và suất đúng một lần. */
    public void cancelItem(long itemId, long actorId, String reason) {
        jdbc.sql("SELECT rc_cancel_item(:i, :a, :r)").param("i", itemId).param("a", actorId).param("r", reason).query().singleRow();
    }

    /** Hủy ngoại lệ (từ COOKING, chỉ Manager): không hoàn nguyên liệu/suất, không tính tiền. */
    public void cancelItemException(long itemId, long actorId, String reason) {
        jdbc.sql("SELECT rc_cancel_item_exception(:i, :a, :r)").param("i", itemId).param("a", actorId).param("r", reason).query().singleRow();
    }
}
