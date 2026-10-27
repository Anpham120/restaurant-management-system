package com.restaurant.core.inventory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.StockDocumentDtos.CountLineView;
import com.restaurant.core.inventory.dto.StockDocumentDtos.CountResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueLineView;
import com.restaurant.core.inventory.dto.StockDocumentDtos.IssueResponse;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptLineView;
import com.restaurant.core.inventory.dto.StockDocumentDtos.ReceiptResponse;

/**
 * Phiếu nhập / xuất / kiểm kê. Tạo bằng rc_create_* (header + dòng nguyên tử, idempotent theo request_key), xác nhận bằng
 * rc_confirm_receipt, rc_issue_stock, rc_receive_issue, rc_cancel_issue, rc_confirm_count — số dư và sổ kho chỉ đổi trong
 * các hàm này. Owner: Đỗ Tuấn Anh.
 */
@Repository
public class StockDocumentRepository {

    private final JdbcClient jdbc;

    public StockDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- nhập
    public long createReceipt(long actorId, String requestKey, String payloadJson) {
        return jdbc.sql("SELECT rc_create_stock_receipt(:a, :k, CAST(:p AS jsonb))").param("a", actorId).param("k", requestKey)
                .param("p", payloadJson).query(Long.class).single();
    }

    public void confirmReceipt(long receiptId, long actorId, Instant receivedAt) {
        jdbc.sql("SELECT rc_confirm_receipt(:r, :a, COALESCE(CAST(:at AS timestamptz), now()))").param("r", receiptId)
                .param("a", actorId).param("at", receivedAt == null ? null : java.sql.Timestamp.from(receivedAt)).query().singleRow();
    }

    public Optional<ReceiptResponse.Header> findReceipt(long id) {
        return jdbc.sql("""
                        SELECT id, status, supplier_name, note, request_key, created_at, created_by_id, received_at, confirmed_by_id, confirmed_at
                          FROM stock_receipts WHERE id = :id""")
                .param("id", id).query(ReceiptResponse.Header.class).optional();
    }

    public List<ReceiptResponse.Header> findReceipts(String status, int page, int size) {
        return jdbc.sql("""
                        SELECT id, status, supplier_name, note, request_key, created_at, created_by_id, received_at, confirmed_by_id, confirmed_at
                          FROM stock_receipts WHERE CAST(:s AS text) IS NULL OR status = :s
                         ORDER BY id DESC LIMIT :size OFFSET :off""")
                .param("s", status).param("size", size).param("off", (long) page * size).query(ReceiptResponse.Header.class).list();
    }

    public List<ReceiptLineView> findReceiptLines(long receiptId) {
        return jdbc.sql("""
                        SELECT l.id, l.ingredient_id, g.name AS ingredient_name, g.base_unit, l.input_quantity, l.input_unit,
                               l.conversion_factor, l.input_quantity * l.conversion_factor AS base_quantity,
                               l.unit_price_per_input_unit, l.input_quantity * l.unit_price_per_input_unit AS line_amount,
                               l.lot_code, l.expiry_date, b.id AS inventory_batch_id
                          FROM stock_receipt_items l
                          JOIN ingredients g ON g.id = l.ingredient_id
                          LEFT JOIN inventory_batches b ON b.stock_receipt_item_id = l.id
                         WHERE l.stock_receipt_id = :r ORDER BY l.id""")
                .param("r", receiptId).query(ReceiptLineView.class).list();
    }

    /** Bỏ phiếu nháp (guard DB chỉ cho xóa khi DRAFT). Trả số phiếu bị xóa. */
    public int deleteDraftReceipt(long id) {
        jdbc.sql("DELETE FROM stock_receipt_items WHERE stock_receipt_id = :id AND EXISTS (SELECT 1 FROM stock_receipts WHERE id = :id AND status = 'DRAFT')")
                .param("id", id).update();
        return jdbc.sql("DELETE FROM stock_receipts WHERE id = :id AND status = 'DRAFT'").param("id", id).update();
    }

    // ---------- xuất
    public long createIssue(long actorId, String requestKey, String payloadJson) {
        return jdbc.sql("SELECT rc_create_stock_issue(:a, :k, CAST(:p AS jsonb))").param("a", actorId).param("k", requestKey)
                .param("p", payloadJson).query(Long.class).single();
    }

    public void issue(long issueId, long actorId) {
        jdbc.sql("SELECT rc_issue_stock(:i, :a)").param("i", issueId).param("a", actorId).query().singleRow();
    }

    public void receive(long issueId, long actorId) {
        jdbc.sql("SELECT rc_receive_issue(:i, :a)").param("i", issueId).param("a", actorId).query().singleRow();
    }

    public void cancelIssue(long issueId, long actorId, String reason) {
        jdbc.sql("SELECT rc_cancel_issue(:i, :a, :r)").param("i", issueId).param("a", actorId).param("r", reason).query().singleRow();
    }

    private static final String ISSUE_HEADER = """
            SELECT id, status, note, request_key, created_at, issued_by_id, issued_at, received_by_id, received_at,
                   cancel_reason, cancelled_at FROM stock_issues""";

    public Optional<IssueResponse.Header> findIssue(long id) {
        return jdbc.sql(ISSUE_HEADER + " WHERE id = :id").param("id", id).query(IssueResponse.Header.class).optional();
    }

    public List<IssueResponse.Header> findIssues(String status, int page, int size) {
        return jdbc.sql(ISSUE_HEADER + " WHERE CAST(:s AS text) IS NULL OR status = :s ORDER BY id DESC LIMIT :size OFFSET :off")
                .param("s", status).param("size", size).param("off", (long) page * size).query(IssueResponse.Header.class).list();
    }

    public List<IssueLineView> findIssueLines(long issueId) {
        return jdbc.sql("""
                        SELECT l.id, l.inventory_batch_id, s.ingredient_id, g.name AS ingredient_name, s.lot_code, l.quantity,
                               l.fulfillment_mode, l.wip_remaining_quantity, k.id AS kitchen_batch_id
                          FROM stock_issue_items l
                          JOIN inventory_batches b ON b.id = l.inventory_batch_id
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                          LEFT JOIN kitchen_batches k ON k.direct_issue_item_id = l.id
                         WHERE l.stock_issue_id = :i ORDER BY l.id""")
                .param("i", issueId).query(IssueLineView.class).list();
    }

    // ---------- kiểm kê
    public long createCount(long actorId, String requestKey, String payloadJson) {
        return jdbc.sql("SELECT rc_create_stock_count(:a, :k, CAST(:p AS jsonb))").param("a", actorId).param("k", requestKey)
                .param("p", payloadJson).query(Long.class).single();
    }

    public void confirmCount(long countId, long actorId) {
        jdbc.sql("SELECT rc_confirm_count(:c, :a)").param("c", countId).param("a", actorId).query().singleRow();
    }

    public Optional<CountResponse.Header> findCount(long id) {
        return jdbc.sql("SELECT id, status, note, request_key, created_at, created_by_id, confirmed_by_id, confirmed_at FROM stock_counts WHERE id = :id")
                .param("id", id).query(CountResponse.Header.class).optional();
    }

    public List<CountLineView> findCountLines(long countId) {
        return jdbc.sql("""
                        SELECT id, raw_batch_id, wip_issue_item_id, kitchen_batch_id, book_quantity, actual_quantity,
                               actual_quantity - book_quantity AS difference, reason
                          FROM stock_count_items WHERE stock_count_id = :c ORDER BY id""")
                .param("c", countId).query(CountLineView.class).list();
    }
}
