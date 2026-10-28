package com.restaurant.core.inventory.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.InventoryReportDtos.HandoverRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.KitchenStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.PreparationYieldRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawLotRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.RawStockRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.StockHistoryRow;
import com.restaurant.core.inventory.dto.InventoryReportDtos.UnusableLotRow;

/** Báo cáo kho = hàm rpt_* đã kiểm số liệu (docs/10, docs/11); danh sách lô để chọn khi lập chứng từ. Owner: Đỗ Tuấn Anh. */
@Repository
public class InventoryReportRepository {

    private final JdbcClient jdbc;

    public InventoryReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RawStockRow> rawStock() {
        return jdbc.sql("SELECT * FROM rpt_raw_stock()").query(RawStockRow.class).list();
    }

    public List<HandoverRow> handoverAndWip() {
        return jdbc.sql("SELECT * FROM rpt_handover_and_wip()").query(HandoverRow.class).list();
    }

    public List<KitchenStockRow> kitchenStock() {
        return jdbc.sql("SELECT * FROM rpt_kitchen_stock()").query(KitchenStockRow.class).list();
    }

    public List<UnusableLotRow> unusableLots() {
        return jdbc.sql("SELECT * FROM rpt_unusable_lots()").query(UnusableLotRow.class).list();
    }

    public List<StockHistoryRow> stockHistory(Instant from, Instant to, Long ingredientId) {
        return jdbc.sql("SELECT * FROM rpt_stock_history(CAST(:f AS timestamptz), CAST(:t AS timestamptz), CAST(:g AS bigint))")
                .param("f", java.sql.Timestamp.from(from)).param("t", java.sql.Timestamp.from(to)).param("g", ingredientId)
                .query(StockHistoryRow.class).list();
    }

    public List<PreparationYieldRow> preparationYield(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT * FROM rpt_preparation_yield(:f, :t)").param("f", from).param("t", to).query(PreparationYieldRow.class).list();
    }

    /** Lô RAW còn hàng, xếp FEFO (hạn sớm trước, cùng hạn thì nhận trước) — đúng thứ tự rc_issue_stock đòi hỏi. */
    public List<RawLotRow> rawLots(Long ingredientId) {
        return jdbc.sql("""
                        SELECT b.id AS inventory_batch_id, s.ingredient_id, g.name AS ingredient_name, s.lot_code, s.expiry_date,
                               b.received_at, b.quantity_on_hand,
                               b.is_usable AND (s.expiry_date IS NULL OR s.expiry_date >= rc_business_date(now())) AS usable
                          FROM inventory_batches b
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                         WHERE b.quantity_on_hand > 0 AND (CAST(:g AS bigint) IS NULL OR s.ingredient_id = :g)
                         ORDER BY g.name, s.expiry_date NULLS LAST, b.received_at, b.id""")
                .param("g", ingredientId).query(RawLotRow.class).list();
    }

    public List<KitchenLotRow> kitchenLots(Long typeId) {
        return jdbc.sql("""
                        SELECT k.id AS kitchen_batch_id, k.kitchen_ingredient_type_id, t.name AS type_name, k.quantity_on_hand,
                               k.reserved_quantity, k.usable_until,
                               k.is_usable AND (k.usable_until IS NULL OR k.usable_until > now()) AS usable,
                               k.direct_issue_item_id
                          FROM kitchen_batches k JOIN kitchen_ingredient_types t ON t.id = k.kitchen_ingredient_type_id
                         WHERE k.quantity_on_hand > 0 AND (CAST(:t AS bigint) IS NULL OR k.kitchen_ingredient_type_id = :t)
                         ORDER BY t.name, k.usable_until NULLS LAST, k.id""")
                .param("t", typeId).query(KitchenLotRow.class).list();
    }
}
