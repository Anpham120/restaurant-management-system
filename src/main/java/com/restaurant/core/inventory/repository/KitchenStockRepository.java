package com.restaurant.core.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentLineView;
import com.restaurant.core.inventory.dto.KitchenStockDtos.AdjustmentResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.OutputView;
import com.restaurant.core.inventory.dto.KitchenStockDtos.PreparationResponse;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnLineView;
import com.restaurant.core.inventory.dto.KitchenStockDtos.ReturnResponse;

/**
 * Sơ chế, hoàn trả, hủy hàng: phiếu nháp ghi trực tiếp vào các cột rc_app được cấp (docs/09), xác nhận bằng
 * rc_confirm_preparation / rc_confirm_return / rc_confirm_adjustment. Đánh dấu lô hỏng: rc_mark_batch_unusable.
 * Owner: Đỗ Tuấn Anh.
 */
@Repository
public class KitchenStockRepository {

    private final JdbcClient jdbc;

    public KitchenStockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findIdByRequestKey(String table, String requestKey) {
        String sql = switch (table) {
            case "kitchen_preparations" -> "SELECT id FROM kitchen_preparations WHERE request_key = :k";
            case "stock_returns" -> "SELECT id FROM stock_returns WHERE request_key = :k";
            case "stock_adjustments" -> "SELECT id FROM stock_adjustments WHERE request_key = :k";
            default -> throw new IllegalArgumentException(table);
        };
        return jdbc.sql(sql).param("k", requestKey).query(Long.class).optional();
    }

    // ---------- sơ chế
    public long insertPreparation(long issueItemId, BigDecimal inputQuantity, String lossReason, long actorId, String requestKey) {
        return jdbc.sql("""
                        INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, loss_reason, prepared_by_id, request_key)
                        VALUES (:l, :q, :r, :a, :k) RETURNING id""")
                .param("l", issueItemId).param("q", inputQuantity).param("r", lossReason).param("a", actorId).param("k", requestKey)
                .query(Long.class).single();
    }

    public void insertPreparationOutput(long preparationId, long typeId, BigDecimal quantity, Instant usableUntil) {
        jdbc.sql("""
                        INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity, usable_until)
                        VALUES (:p, :t, :q, CAST(:u AS timestamptz))""")
                .param("p", preparationId).param("t", typeId).param("q", quantity)
                .param("u", usableUntil == null ? null : java.sql.Timestamp.from(usableUntil)).update();
    }

    public void confirmPreparation(long id, long actorId) {
        jdbc.sql("SELECT rc_confirm_preparation(:p, :a)").param("p", id).param("a", actorId).query().singleRow();
    }

    public Optional<PreparationResponse.Header> findPreparation(long id) {
        return jdbc.sql("""
                        SELECT id, status, stock_issue_item_id, input_quantity, loss_reason, prepared_by_id, prepared_at, request_key
                          FROM kitchen_preparations WHERE id = :id""")
                .param("id", id).query(PreparationResponse.Header.class).optional();
    }

    public List<OutputView> findPreparationOutputs(long id) {
        return jdbc.sql("""
                        SELECT o.id, o.kitchen_ingredient_type_id, t.name AS type_name, o.output_quantity, o.usable_until,
                               k.id AS kitchen_batch_id
                          FROM kitchen_preparation_outputs o
                          JOIN kitchen_ingredient_types t ON t.id = o.kitchen_ingredient_type_id
                          LEFT JOIN kitchen_batches k ON k.preparation_output_id = o.id
                         WHERE o.kitchen_preparation_id = :p ORDER BY o.id""")
                .param("p", id).query(OutputView.class).list();
    }

    // ---------- hoàn trả
    public long insertReturn(long actorId, String reason, String requestKey) {
        return jdbc.sql("INSERT INTO stock_returns (requested_by_id, reason, request_key) VALUES (:a, :r, :k) RETURNING id")
                .param("a", actorId).param("r", reason).param("k", requestKey).query(Long.class).single();
    }

    public void insertReturnLine(long returnId, long sourceIssueItemId, Long directKitchenBatchId, BigDecimal quantity) {
        jdbc.sql("""
                        INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, direct_kitchen_batch_id, quantity)
                        VALUES (:r, :s, :k, :q)""")
                .param("r", returnId).param("s", sourceIssueItemId).param("k", directKitchenBatchId).param("q", quantity).update();
    }

    public void confirmReturn(long id, long actorId) {
        jdbc.sql("SELECT rc_confirm_return(:r, :a)").param("r", id).param("a", actorId).query().singleRow();
    }

    public Optional<ReturnResponse.Header> findReturn(long id) {
        return jdbc.sql("""
                        SELECT id, status, reason, requested_by_id, confirmed_by_id, created_at, confirmed_at, request_key
                          FROM stock_returns WHERE id = :id""")
                .param("id", id).query(ReturnResponse.Header.class).optional();
    }

    public List<ReturnLineView> findReturnLines(long id) {
        return jdbc.sql("SELECT id, source_issue_item_id, direct_kitchen_batch_id, quantity FROM stock_return_items WHERE stock_return_id = :r ORDER BY id")
                .param("r", id).query(ReturnLineView.class).list();
    }

    // ---------- hủy hàng
    public long insertAdjustment(long actorId, String type, String reason, String requestKey) {
        return jdbc.sql("INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key) VALUES (:t, :r, :a, :k) RETURNING id")
                .param("t", type).param("r", reason).param("a", actorId).param("k", requestKey).query(Long.class).single();
    }

    public void insertAdjustmentLine(long adjustmentId, Long rawBatchId, Long wipIssueItemId, Long kitchenBatchId, BigDecimal delta, String note) {
        jdbc.sql("""
                        INSERT INTO stock_adjustment_items (stock_adjustment_id, raw_batch_id, wip_issue_item_id, kitchen_batch_id, quantity_delta, note)
                        VALUES (:a, :r, :w, :k, :d, :n)""")
                .param("a", adjustmentId).param("r", rawBatchId).param("w", wipIssueItemId).param("k", kitchenBatchId)
                .param("d", delta).param("n", note).update();
    }

    public void confirmAdjustment(long id, long actorId) {
        jdbc.sql("SELECT rc_confirm_adjustment(:a, :u)").param("a", id).param("u", actorId).query().singleRow();
    }

    public Optional<AdjustmentResponse.Header> findAdjustment(long id) {
        return jdbc.sql("""
                        SELECT id, status, adjustment_type, reason, created_by_id, confirmed_by_id, created_at, confirmed_at, request_key
                          FROM stock_adjustments WHERE id = :id""")
                .param("id", id).query(AdjustmentResponse.Header.class).optional();
    }

    public List<AdjustmentLineView> findAdjustmentLines(long id) {
        return jdbc.sql("""
                        SELECT id, raw_batch_id, wip_issue_item_id, kitchen_batch_id, quantity_delta, note
                          FROM stock_adjustment_items WHERE stock_adjustment_id = :a ORDER BY id""")
                .param("a", id).query(AdjustmentLineView.class).list();
    }

    // ---------- danh sách (mới nhất trước)
    public List<PreparationResponse.Header> findPreparations(String status, int page, int size) {
        return jdbc.sql("""
                        SELECT id, status, stock_issue_item_id, input_quantity, loss_reason, prepared_by_id, prepared_at, request_key
                          FROM kitchen_preparations WHERE CAST(:s AS text) IS NULL OR status = :s
                         ORDER BY id DESC LIMIT :size OFFSET :off""")
                .param("s", status).param("size", size).param("off", (long) page * size).query(PreparationResponse.Header.class).list();
    }

    public List<ReturnResponse.Header> findReturns(String status, int page, int size) {
        return jdbc.sql("""
                        SELECT id, status, reason, requested_by_id, confirmed_by_id, created_at, confirmed_at, request_key
                          FROM stock_returns WHERE CAST(:s AS text) IS NULL OR status = :s
                         ORDER BY id DESC LIMIT :size OFFSET :off""")
                .param("s", status).param("size", size).param("off", (long) page * size).query(ReturnResponse.Header.class).list();
    }

    public List<AdjustmentResponse.Header> findAdjustments(String status, int page, int size) {
        return jdbc.sql("""
                        SELECT id, status, adjustment_type, reason, created_by_id, confirmed_by_id, created_at, confirmed_at, request_key
                          FROM stock_adjustments WHERE CAST(:s AS text) IS NULL OR status = :s
                         ORDER BY id DESC LIMIT :size OFFSET :off""")
                .param("s", status).param("size", size).param("off", (long) page * size).query(AdjustmentResponse.Header.class).list();
    }

    // ---------- lô hỏng
    /** rc_mark_batch_unusable(p_kind 'RAW'|'KITCHEN', p_batch, p_actor, p_reason) → số món đang giữ lô này bị gắn cờ. */
    public int markUnusable(String kind, long batchId, long actorId, String reason) {
        return jdbc.sql("SELECT rc_mark_batch_unusable(:k, :b, :a, :r)").param("k", kind).param("b", batchId).param("a", actorId)
                .param("r", reason).query(Integer.class).single();
    }
}
