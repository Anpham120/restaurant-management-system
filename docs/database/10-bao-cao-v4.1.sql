-- =====================================================================
-- RESTAURANT CORE — Truy vấn báo cáo và đối soát (v4.1, spec 5.2–5.5)
-- Chạy SAU 04-schema-v4.1.sql (và 09-acl-v4.1.sql nếu có):
--     psql -v ON_ERROR_STOP=1 -d restaurant_core -f 10-bao-cao-v4.1.sql
-- Hàm rpt_* chỉ đọc (STABLE, SECURITY INVOKER): người gọi cần SELECT trên bảng (rc_readonly / rc_app có sẵn).
-- Quy ước:
--   * Doanh thu thực thu CHỈ từ payments (mỗi hóa đơn tối đa một payment; payment QR gắn đúng một webhook PROCESSED) →
--     webhook lặp/đến muộn/sai số tiền không bao giờ được đếm; UNCOLLECTED và WAIVED không phải doanh thu.
--   * Ngày kinh doanh: payments.business_date / operating_expenses.business_date (đã ép theo rc_business_date của paid_at);
--     các mốc thời gian khác quy về ngày kinh doanh bằng rc_business_date().
--   * Không có báo cáo "lợi nhuận": chênh lệch doanh thu − chi phí trong Core chỉ là dòng tiền, không phải lợi nhuận ròng.
-- =====================================================================
BEGIN;

-- 5.2a Doanh thu thực thu theo ngày kinh doanh
CREATE FUNCTION rpt_revenue_by_day(p_from DATE, p_to DATE)
RETURNS TABLE (business_date DATE, cash_amount NUMERIC, qr_amount NUMERIC, total_amount NUMERIC, paid_invoices BIGINT)
LANGUAGE sql STABLE AS $$
    SELECT p.business_date,
           COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'CASH'), 0),
           COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'QR'), 0),
           SUM(p.amount),
           count(*)
      FROM payments p
     WHERE p.business_date BETWEEN p_from AND p_to
     GROUP BY p.business_date
     ORDER BY p.business_date
$$;

-- 5.2b Doanh thu và số suất hợp lệ theo món: dòng hóa đơn của hóa đơn ĐÃ THU (có payment trong kỳ), theo giá snapshot
CREATE FUNCTION rpt_revenue_by_item(p_from DATE, p_to DATE)
RETURNS TABLE (menu_item_id BIGINT, item_name TEXT, servings BIGINT, revenue NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT d.menu_item_id, max(ii.item_name), SUM(ii.quantity), SUM(ii.line_total)
      FROM payments p
      JOIN invoice_items ii ON ii.invoice_id = p.invoice_id
      JOIN order_items oi ON oi.id = ii.order_item_id
      JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
     WHERE p.business_date BETWEEN p_from AND p_to
     GROUP BY d.menu_item_id
     ORDER BY SUM(ii.line_total) DESC, d.menu_item_id
$$;

-- 5.2c Hóa đơn kết thúc không thu tiền (không phải doanh thu): khách bỏ về / miễn thu, theo ngày kinh doanh ghi nhận
CREATE FUNCTION rpt_unpaid_closures(p_from DATE, p_to DATE)
RETURNS TABLE (business_date DATE, status TEXT, invoice_count BIGINT, amount NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT rc_business_date(COALESCE(v.uncollected_at, v.waived_at)), v.status, count(*), SUM(v.total)
      FROM invoices v
     WHERE v.status IN ('UNCOLLECTED', 'WAIVED')
       AND rc_business_date(COALESCE(v.uncollected_at, v.waived_at)) BETWEEN p_from AND p_to
     GROUP BY 1, 2
     ORDER BY 1, 2
$$;

-- 5.4a Tồn nguyên liệu thô (RAW) theo nguyên liệu: dùng được / không dùng được (đánh dấu hỏng) / quá hạn
CREATE FUNCTION rpt_raw_stock()
RETURNS TABLE (ingredient_id BIGINT, ingredient_name TEXT, base_unit TEXT, usable_quantity NUMERIC,
               unusable_quantity NUMERIC, expired_quantity NUMERIC, min_quantity NUMERIC, below_minimum BOOLEAN)
LANGUAGE sql STABLE AS $$
    SELECT g.id, g.name, g.base_unit,
           COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE b.is_usable AND (s.expiry_date IS NULL OR s.expiry_date >= rc_business_date(now()))), 0),
           COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE NOT b.is_usable), 0),
           COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE b.is_usable AND s.expiry_date < rc_business_date(now())), 0),
           g.min_quantity,
           COALESCE(SUM(b.quantity_on_hand) FILTER (WHERE b.is_usable AND (s.expiry_date IS NULL OR s.expiry_date >= rc_business_date(now()))), 0) < g.min_quantity
      FROM ingredients g
      LEFT JOIN stock_receipt_items s ON s.ingredient_id = g.id
      LEFT JOIN inventory_batches b ON b.stock_receipt_item_id = s.id
     GROUP BY g.id
     ORDER BY g.id
$$;

-- 5.4b Nguyên liệu đang bàn giao (phiếu xuất ISSUED, bếp chưa nhận) và đang chờ sơ chế (WIP đã nhận còn lại)
CREATE FUNCTION rpt_handover_and_wip()
RETURNS TABLE (stage TEXT, ingredient_id BIGINT, ingredient_name TEXT, stock_issue_id BIGINT, stock_issue_item_id BIGINT,
               inventory_batch_id BIGINT, quantity NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT CASE WHEN si.status = 'ISSUED' THEN 'IN_TRANSIT' ELSE 'WIP' END, g.id, g.name, si.id, l.id, l.inventory_batch_id,
           CASE WHEN si.status = 'ISSUED' THEN l.quantity ELSE l.wip_remaining_quantity END
      FROM stock_issue_items l
      JOIN stock_issues si ON si.id = l.stock_issue_id
      JOIN inventory_batches b ON b.id = l.inventory_batch_id
      JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
      JOIN ingredients g ON g.id = s.ingredient_id
     WHERE si.status = 'ISSUED'
        OR (si.status = 'RECEIVED' AND l.fulfillment_mode = 'PREPARE' AND l.wip_remaining_quantity > 0)
     ORDER BY 1, si.id, l.id
$$;

-- 5.4c Tồn bếp theo loại nguyên liệu bếp: vật lý dùng được, đang giữ cho món, khả dụng = dùng được − đang giữ;
--      phần không dùng được (hỏng/hết hạn dùng) tách riêng
CREATE FUNCTION rpt_kitchen_stock()
RETURNS TABLE (kitchen_ingredient_type_id BIGINT, type_name TEXT, base_unit TEXT, usable_on_hand NUMERIC,
               reserved_quantity NUMERIC, available_quantity NUMERIC, unusable_or_expired NUMERIC, minimum_quantity NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT t.id, t.name, g.base_unit,
           COALESCE(SUM(k.quantity_on_hand) FILTER (WHERE k.is_usable AND (k.usable_until IS NULL OR k.usable_until > now())), 0),
           COALESCE(SUM(k.reserved_quantity), 0),
           COALESCE(SUM(k.quantity_on_hand - k.reserved_quantity) FILTER (WHERE k.is_usable AND (k.usable_until IS NULL OR k.usable_until > now())), 0),
           COALESCE(SUM(k.quantity_on_hand) FILTER (WHERE NOT k.is_usable OR k.usable_until <= now()), 0),
           t.minimum_quantity
      FROM kitchen_ingredient_types t
      JOIN ingredients g ON g.id = t.raw_ingredient_id
      LEFT JOIN kitchen_batches k ON k.kitchen_ingredient_type_id = t.id
     GROUP BY t.id, g.base_unit
     ORDER BY t.id
$$;

-- 5.4d Lô hết hạn hoặc không dùng được còn tồn (RAW và bếp)
CREATE FUNCTION rpt_unusable_lots()
RETURNS TABLE (location TEXT, batch_id BIGINT, item_name TEXT, quantity NUMERIC, reason TEXT, expires TIMESTAMPTZ)
LANGUAGE sql STABLE AS $$
    SELECT 'RAW', b.id, g.name, b.quantity_on_hand,
           CASE WHEN NOT b.is_usable THEN b.unusable_reason ELSE 'Quá hạn' END,
           (s.expiry_date + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
      FROM inventory_batches b
      JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
      JOIN ingredients g ON g.id = s.ingredient_id
     WHERE b.quantity_on_hand > 0 AND (NOT b.is_usable OR s.expiry_date < rc_business_date(now()))
    UNION ALL
    SELECT 'KITCHEN', k.id, t.name, k.quantity_on_hand,
           CASE WHEN NOT k.is_usable THEN k.unusable_reason ELSE 'Hết hạn dùng tại bếp' END, k.usable_until
      FROM kitchen_batches k
      JOIN kitchen_ingredient_types t ON t.id = k.kitchen_ingredient_type_id
     WHERE k.quantity_on_hand > 0 AND (NOT k.is_usable OR k.usable_until <= now())
     ORDER BY 1, 2
$$;

-- 5.4e Lịch sử biến động kho: nhập, xuất, hoàn trả, tiêu hao (nấu, sơ chế), hủy hàng, điều chỉnh kiểm kê.
--      Mỗi dòng sổ quy về nguyên liệu gốc; p_ingredient NULL = mọi nguyên liệu.
CREATE FUNCTION rpt_stock_history(p_from TIMESTAMPTZ, p_to TIMESTAMPTZ, p_ingredient BIGINT DEFAULT NULL)
RETURNS TABLE (movement_id BIGINT, created_at TIMESTAMPTZ, business_date DATE, category TEXT, movement_type TEXT,
               location TEXT, ingredient_id BIGINT, ingredient_name TEXT, quantity_delta NUMERIC, reference TEXT, reason TEXT)
LANGUAGE sql STABLE AS $$
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
         WHERE m.created_at >= p_from AND m.created_at < p_to)
    SELECT m.id, m.created_at, rc_business_date(m.created_at),
           CASE m.movement_type
               WHEN 'RECEIPT' THEN 'NHẬP'
               WHEN 'ISSUE_OUT' THEN 'XUẤT' WHEN 'ISSUE_REVERSAL' THEN 'XUẤT' WHEN 'ISSUE_RECEIVED' THEN 'XUẤT' WHEN 'DIRECT_IN' THEN 'XUẤT'
               WHEN 'RETURN' THEN 'HOÀN TRẢ'
               WHEN 'COOK' THEN 'TIÊU HAO' WHEN 'PREP_INPUT' THEN 'TIÊU HAO' WHEN 'PREP_OUTPUT' THEN 'TIÊU HAO'
               WHEN 'WASTE' THEN 'HỦY HÀNG'
               ELSE 'ĐIỀU CHỈNH' END,
           m.movement_type, m.loc, m.ing, g.name, m.quantity_delta, m.event_key, m.reason
      FROM m JOIN ingredients g ON g.id = m.ing
     WHERE p_ingredient IS NULL OR m.ing = p_ingredient
     ORDER BY m.created_at, m.id
$$;

-- 5.4f Hao hụt sơ chế: đầu vào, thành phẩm, hao hụt, tỷ lệ thu hồi/hao hụt — theo phiếu (lọc ngày kinh doanh;
--      tổng hợp theo nguyên liệu / lô / nhân viên bằng GROUP BY trên kết quả)
CREATE FUNCTION rpt_preparation_yield(p_from DATE, p_to DATE)
RETURNS TABLE (preparation_id BIGINT, business_date DATE, prepared_at TIMESTAMPTZ, ingredient_id BIGINT, ingredient_name TEXT,
               inventory_batch_id BIGINT, lot_code TEXT, prepared_by_id BIGINT, prepared_by TEXT,
               input_quantity NUMERIC, output_quantity NUMERIC, loss_quantity NUMERIC, yield_ratio NUMERIC, loss_ratio NUMERIC,
               loss_reason TEXT)
LANGUAGE sql STABLE AS $$
    SELECT kp.id, rc_business_date(kp.prepared_at), kp.prepared_at, g.id, g.name, b.id, s.lot_code, e.id, e.full_name,
           kp.input_quantity, o.out_q, kp.input_quantity - o.out_q,
           round(o.out_q / kp.input_quantity, 4), round((kp.input_quantity - o.out_q) / kp.input_quantity, 4), kp.loss_reason
      FROM kitchen_preparations kp
      JOIN LATERAL (SELECT SUM(output_quantity) AS out_q FROM kitchen_preparation_outputs WHERE kitchen_preparation_id = kp.id) o ON true
      JOIN stock_issue_items l ON l.id = kp.stock_issue_item_id
      JOIN inventory_batches b ON b.id = l.inventory_batch_id
      JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
      JOIN ingredients g ON g.id = s.ingredient_id
      JOIN employees e ON e.id = kp.prepared_by_id
     WHERE kp.status = 'CONFIRMED' AND rc_business_date(kp.prepared_at) BETWEEN p_from AND p_to
     ORDER BY kp.prepared_at, kp.id
$$;

-- 5.5 Đối soát tiền mặt một ngày kinh doanh: số liệu TƯƠI (payments CASH, chi tiền mặt từ quỹ quầy) đặt cạnh bản
--     đối soát CURRENT đã ghi; stale = bản đã ghi lệch số tươi (phát sinh thu/chi sau khi đối soát → lập phiên bản mới)
CREATE FUNCTION rpt_cash_reconciliation(p_date DATE)
RETURNS TABLE (business_date DATE, opening_cash NUMERIC, cash_sales NUMERIC, cash_expenses NUMERIC, expected_cash NUMERIC,
               actual_cash NUMERIC, difference NUMERIC, reason TEXT, recon_version INT, reconciled_by_id BIGINT, stale BOOLEAN)
LANGUAGE sql STABLE AS $$
    WITH live AS (
        SELECT (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE business_date = p_date AND method = 'CASH') AS sales,
               (SELECT COALESCE(SUM(amount), 0) FROM operating_expenses
                 WHERE business_date = p_date AND status = 'PAID' AND fund_source = 'CASH_DRAWER') AS expenses)
    SELECT p_date, r.opening_cash, live.sales, live.expenses, r.opening_cash + live.sales - live.expenses,
           r.actual_cash, r.actual_cash - (r.opening_cash + live.sales - live.expenses), r.reason, r.version, r.reconciled_by_id,
           r.id IS NOT NULL AND (r.cash_sales <> live.sales OR r.cash_expenses <> live.expenses)
      FROM live
      LEFT JOIN daily_cash_reconciliations r ON r.business_date = p_date AND r.status = 'CURRENT'
$$;

-- Quyền: chỉ người đọc báo cáo (và backend) được gọi
DO $$
DECLARE
    f RECORD;
BEGIN
    FOR f IN SELECT p.oid::regprocedure AS sig FROM pg_proc p
              WHERE p.pronamespace = current_schema()::regnamespace AND p.proname LIKE 'rpt\_%' LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f.sig);
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rc_readonly') THEN
            EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO rc_readonly, rc_app', f.sig);
        END IF;
    END LOOP;
END $$;

COMMIT;
