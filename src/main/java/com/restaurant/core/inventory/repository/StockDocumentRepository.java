package com.restaurant.core.inventory.repository;

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
 * Truy vấn CHỈ ĐỌC phiếu nhập / xuất / kiểm kê (trả thẳng DTO) Ghi sổ kho nằm ở
 * StockDocumentServiceImpl. Owner: Đỗ Tuấn Anh.
 */
@Repository
public class StockDocumentRepository {

    private final JdbcClient jdbc;

    public StockDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- nhập

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


    // ---------- xuất

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
                               l.fulfillment_mode, l.wip_remaining_quantity, k.id AS kitchen_batch_id, g.base_unit
                          FROM stock_issue_items l
                          JOIN inventory_batches b ON b.id = l.inventory_batch_id
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                          LEFT JOIN kitchen_batches k ON k.direct_issue_item_id = l.id
                         WHERE l.stock_issue_id = :i ORDER BY l.id""")
                .param("i", issueId).query(IssueLineView.class).list();
    }

    // ---------- kiểm kê

    public Optional<CountResponse.Header> findCount(long id) {
        return jdbc.sql("SELECT id, status, note, request_key, created_at, created_by_id, confirmed_by_id, confirmed_at FROM stock_counts WHERE id = :id")
                .param("id", id).query(CountResponse.Header.class).optional();
    }

    public List<CountResponse.Header> findCounts(String status, int page, int size) {
        return jdbc.sql("""
                        SELECT id, status, note, request_key, created_at, created_by_id, confirmed_by_id, confirmed_at
                          FROM stock_counts WHERE CAST(:s AS text) IS NULL OR status = :s
                         ORDER BY id DESC LIMIT :size OFFSET :off""")
                .param("s", status).param("size", size).param("off", (long) page * size).query(CountResponse.Header.class).list();
    }

    public List<CountLineView> findCountLines(long countId) {
        return jdbc.sql("""
                        SELECT id, raw_batch_id, wip_issue_item_id, kitchen_batch_id, book_quantity, actual_quantity,
                               actual_quantity - book_quantity AS difference, reason
                          FROM stock_count_items WHERE stock_count_id = :c ORDER BY id""")
                .param("c", countId).query(CountLineView.class).list();
    }
}
