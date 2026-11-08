package com.restaurant.core.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.response.KitchenStockResponses.AdjustmentLineView;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.AdjustmentResponse;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.OutputView;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.PreparationResponse;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.ReturnLineView;
import com.restaurant.core.inventory.dto.response.KitchenStockResponses.ReturnResponse;

/** Truy vấn CHỈ ĐỌC phiếu sơ chế, hoàn trả, hủy hàng (trả thẳng DTO). Ghi: các JpaRepository. Owner: Đỗ Tuấn Anh. */
@Repository
public class KitchenStockQueryRepository {

    private final JdbcClient jdbc;

    public KitchenStockQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }


    // ---------- sơ chế


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
}
