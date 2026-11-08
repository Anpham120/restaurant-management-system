package com.restaurant.core.inventory.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.inventory.dto.response.InventoryReportRows.HandoverRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.KitchenLotRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.KitchenStockRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.PreparationYieldRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.RawLotRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.RawStockRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.StockHistoryRow;
import com.restaurant.core.inventory.dto.response.InventoryReportRows.UnusableLotRow;

/**
 * Truy vấn CHỈ ĐỌC cho báo cáo kho và danh sách lô để chọn khi lập chứng từ. Mốc "hôm nay" / "bây giờ" do Service
 * truyền vào (BusinessDateProvider), không tính trong SQL. Owner: Đỗ Tuấn Anh.
 */
@Repository
public class InventoryReportRepository {

    private final JdbcClient jdbc;

    public InventoryReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Tồn kho chính theo nguyên liệu: dùng được / bị đánh dấu hỏng / quá hạn (so với ngày kinh doanh {@code today}). */
    public List<RawStockRow> findRawStock(LocalDate today) {
        return jdbc.sql("""
                        SELECT g.id AS ingredient_id, g.name AS ingredient_name, g.base_unit,
                               COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE b.is_usable AND (s.expiry_date IS NULL OR s.expiry_date >= :today)), 0)
                                   AS usable_quantity,
                               COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE NOT b.is_usable), 0) AS unusable_quantity,
                               COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE b.is_usable AND s.expiry_date < :today), 0) AS expired_quantity,
                               g.min_quantity, false AS below_minimum
                          FROM ingredients g
                          LEFT JOIN stock_receipt_items s ON s.ingredient_id = g.id
                          LEFT JOIN inventory_batches b ON b.stock_receipt_item_id = s.id
                         GROUP BY g.id ORDER BY g.id""")
                .param("today", today).query(RawStockRow.class).list();
    }

    /** Hàng đang bàn giao (phiếu đã xuất, bếp chưa nhận) và hàng chờ sơ chế (đã nhận, còn WIP). */
    public List<HandoverRow> findHandoverAndWip() {
        return jdbc.sql("""
                        SELECT CASE WHEN si.status = 'ISSUED' THEN 'IN_TRANSIT' ELSE 'WIP' END AS stage, g.id AS ingredient_id,
                               g.name AS ingredient_name, si.id AS stock_issue_id, l.id AS stock_issue_item_id, l.inventory_batch_id,
                               CASE WHEN si.status = 'ISSUED' THEN l.quantity ELSE l.wip_remaining_quantity END AS quantity, g.base_unit
                          FROM stock_issue_items l
                          JOIN stock_issues si ON si.id = l.stock_issue_id
                          JOIN inventory_batches b ON b.id = l.inventory_batch_id
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                         WHERE si.status = 'ISSUED'
                            OR (si.status = 'RECEIVED' AND l.fulfillment_mode = 'PREPARE' AND l.wip_remaining_quantity > 0)
                         ORDER BY 1, si.id, l.id""")
                .query(HandoverRow.class).list();
    }

    /** Tồn bếp theo loại: dùng được (còn hạn lúc {@code now}), đang giữ cho món, hỏng/hết hạn. */
    public List<KitchenStockRow> findKitchenStock(Instant now) {
        return jdbc.sql("""
                        SELECT t.id AS kitchen_ingredient_type_id, t.name AS type_name, g.base_unit,
                               COALESCE(SUM(k.quantity_on_hand) FILTER (WHERE k.is_usable AND (k.usable_until IS NULL OR k.usable_until > :now)), 0)
                                   AS usable_on_hand,
                               COALESCE(SUM(k.reserved_quantity), 0) AS reserved_quantity,
                               COALESCE(SUM(k.quantity_on_hand - k.reserved_quantity)
                                        FILTER (WHERE k.is_usable AND (k.usable_until IS NULL OR k.usable_until > :now)), 0) AS available_quantity,
                               COALESCE(SUM(k.quantity_on_hand) FILTER (WHERE NOT k.is_usable OR k.usable_until <= :now), 0) AS unusable_or_expired,
                               t.minimum_quantity
                          FROM kitchen_ingredient_types t
                          JOIN ingredients g ON g.id = t.raw_ingredient_id
                          LEFT JOIN kitchen_batches k ON k.kitchen_ingredient_type_id = t.id
                         GROUP BY t.id, g.base_unit ORDER BY t.id""")
                .param("now", Timestamp.from(now)).query(KitchenStockRow.class).list();
    }

    /** Lô kho chính còn hàng nhưng bị đánh dấu hỏng hoặc đã quá hạn; expiryDate để Service tính mốc hết hạn. */
    public record RawUnusableLot(long batchId, String itemName, BigDecimal quantity, boolean usable, String unusableReason,
                                 LocalDate expiryDate) {
    }

    public List<RawUnusableLot> findRawUnusableLots(LocalDate today) {
        return jdbc.sql("""
                        SELECT b.id AS batch_id, g.name AS item_name, b.quantity_on_hand AS quantity, b.is_usable AS usable,
                               b.unusable_reason, s.expiry_date
                          FROM inventory_batches b
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                         WHERE b.quantity_on_hand > 0 AND (NOT b.is_usable OR s.expiry_date < :today)
                         ORDER BY b.id""")
                .param("today", today).query(RawUnusableLot.class).list();
    }

    /** Lô bếp còn hàng nhưng bị đánh dấu hỏng hoặc đã hết hạn dùng lúc {@code now}. */
    public record KitchenUnusableLot(long batchId, String itemName, BigDecimal quantity, boolean usable, String unusableReason,
                                     Instant usableUntil) {
    }

    public List<KitchenUnusableLot> findKitchenUnusableLots(Instant now) {
        return jdbc.sql("""
                        SELECT k.id AS batch_id, t.name AS item_name, k.quantity_on_hand AS quantity, k.is_usable AS usable,
                               k.unusable_reason, k.usable_until
                          FROM kitchen_batches k
                          JOIN kitchen_ingredient_types t ON t.id = k.kitchen_ingredient_type_id
                         WHERE k.quantity_on_hand > 0 AND (NOT k.is_usable OR k.usable_until <= :now)
                         ORDER BY k.id""")
                .param("now", Timestamp.from(now)).query(KitchenUnusableLot.class).list();
    }

    /** Một dòng sổ kho đã quy về nguyên liệu gốc. */
    public record MovementRow(long movementId, Instant createdAt, String movementType, String location, long ingredientId,
                              String ingredientName, BigDecimal quantityDelta, String reference, String reason, String baseUnit) {
    }

    public List<MovementRow> findMovements(Instant from, Instant to, Long ingredientId) {
        return jdbc.sql("""
                        WITH m AS (
                            SELECT m.*,
                                   CASE WHEN m.raw_batch_id IS NOT NULL THEN 'RAW' WHEN m.wip_issue_item_id IS NOT NULL THEN 'WIP' ELSE 'KITCHEN' END AS loc,
                                   COALESCE(sr.ingredient_id, sw.ingredient_id, kt.raw_ingredient_id) AS ing
                              FROM stock_movements m
                              LEFT JOIN inventory_batches rb ON rb.id = m.raw_batch_id
                              LEFT JOIN stock_receipt_items sr ON sr.id = rb.stock_receipt_item_id
                              LEFT JOIN stock_issue_items wl ON wl.id = m.wip_issue_item_id
                              LEFT JOIN inventory_batches wb ON wb.id = wl.inventory_batch_id
                              LEFT JOIN stock_receipt_items sw ON sw.id = wb.stock_receipt_item_id
                              LEFT JOIN kitchen_batches kb ON kb.id = m.kitchen_batch_id
                              LEFT JOIN kitchen_ingredient_types kt ON kt.id = kb.kitchen_ingredient_type_id
                             WHERE m.created_at >= :f AND m.created_at < :t)
                        SELECT m.id AS movement_id, m.created_at, m.movement_type, m.loc AS location, m.ing AS ingredient_id,
                               g.name AS ingredient_name, m.quantity_delta, m.event_key AS reference, m.reason, g.base_unit
                          FROM m JOIN ingredients g ON g.id = m.ing
                         WHERE CAST(:g AS bigint) IS NULL OR m.ing = :g
                         ORDER BY m.created_at, m.id""")
                .param("f", Timestamp.from(from)).param("t", Timestamp.from(to)).param("g", ingredientId)
                .query(MovementRow.class).list();
    }

    /** Phiếu sơ chế đã xác nhận trong khoảng thời gian, kèm tổng đầu ra. */
    public record PreparationRow(long preparationId, Instant preparedAt, long ingredientId, String ingredientName, long inventoryBatchId,
                                 String lotCode, long preparedById, String preparedBy, BigDecimal inputQuantity, BigDecimal outputQuantity,
                                 String lossReason, String baseUnit) {
    }

    public List<PreparationRow> findConfirmedPreparations(Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT kp.id AS preparation_id, kp.prepared_at, g.id AS ingredient_id, g.name AS ingredient_name,
                               b.id AS inventory_batch_id, s.lot_code, e.id AS prepared_by_id, e.full_name AS prepared_by,
                               kp.input_quantity, o.out_q AS output_quantity, kp.loss_reason, g.base_unit
                          FROM kitchen_preparations kp
                          JOIN LATERAL (SELECT SUM(output_quantity) AS out_q FROM kitchen_preparation_outputs
                                         WHERE kitchen_preparation_id = kp.id) o ON true
                          JOIN stock_issue_items l ON l.id = kp.stock_issue_item_id
                          JOIN inventory_batches b ON b.id = l.inventory_batch_id
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                          JOIN employees e ON e.id = kp.prepared_by_id
                         WHERE kp.status = 'CONFIRMED' AND kp.prepared_at >= :f AND kp.prepared_at < :t
                         ORDER BY kp.prepared_at, kp.id""")
                .param("f", Timestamp.from(from)).param("t", Timestamp.from(to)).query(PreparationRow.class).list();
    }

    /** Lô RAW còn hàng, xếp FEFO (hạn sớm trước, cùng hạn thì nhận trước) — đúng thứ tự xuất kho FEFO đòi hỏi. */
    public List<RawLotRow> rawLots(Long ingredientId, LocalDate today) {
        return jdbc.sql("""
                        SELECT b.id AS inventory_batch_id, s.ingredient_id, g.name AS ingredient_name, s.lot_code, s.expiry_date,
                               b.received_at, b.quantity_on_hand, g.base_unit,
                               b.is_usable AND (s.expiry_date IS NULL OR s.expiry_date >= :today) AS usable
                          FROM inventory_batches b
                          JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                          JOIN ingredients g ON g.id = s.ingredient_id
                         WHERE b.quantity_on_hand > 0 AND (CAST(:g AS bigint) IS NULL OR s.ingredient_id = :g)
                         ORDER BY g.name, s.expiry_date NULLS LAST, b.received_at, b.id""")
                .param("g", ingredientId).param("today", today).query(RawLotRow.class).list();
    }

    public List<KitchenLotRow> kitchenLots(Long typeId) {
        return jdbc.sql("""
                        SELECT k.id AS kitchen_batch_id, k.kitchen_ingredient_type_id, t.name AS type_name, k.quantity_on_hand,
                               k.reserved_quantity, k.usable_until,
                               k.is_usable AND (k.usable_until IS NULL OR k.usable_until > now()) AS usable,
                               k.direct_issue_item_id, g.base_unit
                          FROM kitchen_batches k JOIN kitchen_ingredient_types t ON t.id = k.kitchen_ingredient_type_id
                          JOIN ingredients g ON g.id = t.raw_ingredient_id
                         WHERE k.quantity_on_hand > 0 AND (CAST(:t AS bigint) IS NULL OR k.kitchen_ingredient_type_id = :t)
                         ORDER BY t.name, k.usable_until NULLS LAST, k.id""")
                .param("t", typeId).query(KitchenLotRow.class).list();
    }
}
