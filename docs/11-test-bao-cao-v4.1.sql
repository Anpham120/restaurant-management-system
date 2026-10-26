-- =====================================================================
-- RESTAURANT CORE — Kiểm thử số liệu báo cáo và đối soát (v4.1)
-- Chạy trên database TRỐNG riêng (dùng tên/số cố định, không trộn với 05):
--     psql -v ON_ERROR_STOP=1 -d restaurant_core_report -f 04-schema-v4.1.sql
--     psql -v ON_ERROR_STOP=1 -d restaurant_core_report -f 10-bao-cao-v4.1.sql
--     psql -v ON_ERROR_STOP=1 -d restaurant_core_report -f 11-test-bao-cao-v4.1.sql
-- Bộ dữ liệu dựng hoàn toàn qua hàm rc_* trong ngày kinh doanh hiện tại; mọi con số kỳ vọng tính tay trước (bảng dưới).
--
--   Kho  : Bò 10 kg (250 000đ/kg), xuất 7 kg sơ chế → 6,3 kg Bò thái (hao 0,7), xuất thêm 1 kg chưa nhận (đang bàn giao)
--          Coca 24 lon xuất 12 lon DIRECT; lô Coca cũ 6 lon đánh dấu hỏng; trả 1 lon về kho; hủy 1 lon tại bếp
--   Bán  : A 2 Bò + 2 Coca = 130 000 tiền mặt · B 1 Bò + 1 Coca = 65 000 QR (webhook lặp + webhook đến muộn)
--          C 1 Bò khách bỏ về 50 000 (UNCOLLECTED) · D 1 Coca miễn thu 15 000 (WAIVED) · E 1 Bò đã nhận, đang giữ 0,2 kg
--   Chi  : 50 000 tiền mặt từ quỹ quầy, 100 000 chủ chi (không qua quầy)
--   Quỹ  : đầu ngày 500 000, đếm cuối ngày 575 000 → dự kiến 580 000, lệch −5 000
-- =====================================================================
CREATE SCHEMA rt;
CREATE TABLE rt.n (k INT PRIMARY KEY DEFAULT 1, passed INT NOT NULL DEFAULT 0);
INSERT INTO rt.n DEFAULT VALUES;
CREATE FUNCTION rt.eq(p_label TEXT, p_actual ANYELEMENT, p_expected ANYELEMENT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_actual IS DISTINCT FROM p_expected THEN
        RAISE EXCEPTION 'BÁO CÁO SAI: % — thực tế %, kỳ vọng %', p_label, p_actual, p_expected;
    END IF;
    UPDATE rt.n SET passed = passed + 1;
END $$;
CREATE FUNCTION rt.emp(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$ SELECT id FROM employees WHERE employee_code = p $$;
CREATE FUNCTION rt.ing(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$ SELECT id FROM ingredients WHERE name = p $$;
CREATE FUNCTION rt.dmi(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$
    SELECT d.id FROM daily_menu_items d JOIN menu_items m ON m.id = d.menu_item_id WHERE m.name = p $$;
CREATE FUNCTION rt.lot(p_receipt BIGINT, p_ing TEXT) RETURNS BIGINT LANGUAGE sql AS $$
    SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.stock_receipt_id = p_receipt AND s.ingredient_id = rt.ing(p_ing) $$;
-- mở bàn, gọi món (mỗi phần tử một dòng), phục vụ hết; trả id đơn
CREATE FUNCTION rt.table_served(p_code TEXT, p_dishes TEXT[], p_serve BOOLEAN DEFAULT true) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_t BIGINT;
    v_o BIGINT;
    v_b BIGINT;
    d   TEXT;
    i   BIGINT;
BEGIN
    INSERT INTO dining_tables (code, capacity) VALUES (p_code, 4) RETURNING id INTO v_t;
    v_o := rc_open_session(v_t, rt.emp('W1'));                                       -- id phiên
    v_o := (SELECT id FROM orders WHERE table_session_id = v_o);
    FOREACH d IN ARRAY p_dishes LOOP
        v_b := (rc_submit_batch(v_o, rt.emp('W1'), p_code || '-' || d || '-' || (SELECT count(*) FROM order_batches),
                                jsonb_build_array(jsonb_build_object('daily_menu_item_id', rt.dmi(d), 'quantity', 1))) ->> 'batch_id')::bigint;
        i := (SELECT id FROM order_items WHERE order_batch_id = v_b);
        IF rc_accept_item(i, rt.emp('K1')) <> 'ACCEPTED' THEN RAISE EXCEPTION 'BÁO CÁO SAI: không nhận được món %', d; END IF;
        IF p_serve THEN
            PERFORM rc_start_cooking(i, rt.emp('K1'));
            PERFORM rc_finish_dish(i, rt.emp('K1'));
            PERFORM rc_serve_item(i, rt.emp('W1'));
        END IF;
    END LOOP;
    RETURN v_o;
END $$;

-- Dữ liệu nền
DO $$
DECLARE
    v_m BIGINT;
    v_r BIGINT;
BEGIN
    INSERT INTO departments (department_name) VALUES ('Vận hành');
    INSERT INTO employees (employee_code, full_name, department_id)
    SELECT c, n, (SELECT id FROM departments) FROM (VALUES ('M1', 'Quản lý'), ('W1', 'Phục vụ'), ('K1', 'Bếp An'), ('C1', 'Thu ngân')) t(c, n);
    INSERT INTO accounts (employee_id, username, password_hash) SELECT id, lower(employee_code), 'x' FROM employees;
    INSERT INTO account_roles (account_id, role_id)
    SELECT a.id, r.id FROM accounts a JOIN employees e ON e.id = a.employee_id
      JOIN roles r ON r.role_code = CASE e.employee_code WHEN 'M1' THEN 'MANAGER' WHEN 'W1' THEN 'WAITER' WHEN 'K1' THEN 'KITCHEN' ELSE 'CASHIER' END;
    INSERT INTO ingredients (name, base_unit, min_quantity) VALUES ('Thịt bò A', 'kg', 3), ('Coca lon', 'lon', 6);
    INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type, shelf_life_hours) VALUES
        (rt.ing('Thịt bò A'), 'Bò thái', 'PREPARED', 48), (rt.ing('Coca lon'), 'Coca lon', 'DIRECT', NULL);
    INSERT INTO menu_categories (name) VALUES ('Chung') RETURNING id INTO v_m;
    INSERT INTO menu_items (menu_category_id, name, price) VALUES (v_m, 'Bò lúc lắc', 50000), (v_m, 'Coca', 15000);
    INSERT INTO recipes (menu_item_id, version, is_current) SELECT id, 1, true FROM menu_items;
    INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving)
    SELECT r.id, t.id, CASE m.name WHEN 'Bò lúc lắc' THEN 0.2 ELSE 1 END
      FROM recipes r JOIN menu_items m ON m.id = r.menu_item_id
      JOIN kitchen_ingredient_types t ON t.name = CASE m.name WHEN 'Bò lúc lắc' THEN 'Bò thái' ELSE 'Coca lon' END;
    INSERT INTO daily_menus (business_date, status) VALUES (rc_business_date(now()), 'DRAFT') RETURNING id INTO v_m;
    INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) SELECT v_m, menu_item_id, id, NULL FROM recipes;
    UPDATE daily_menus SET status = 'PUBLISHED', published_at = now() WHERE id = v_m;
END $$;

-- Kho: nhập, đánh dấu lô hỏng, xuất, nhận, sơ chế, trả, hủy hàng
DO $$
DECLARE
    v_r   BIGINT;
    v_old BIGINT;
    v_i   BIGINT;
    v_i2  BIGINT;
    v_p   BIGINT;
    v_rt  BIGINT;
    v_a   BIGINT;
    v_kb  BIGINT;
BEGIN
    v_r := rc_create_stock_receipt(rt.emp('M1'), 'rpt-r1', jsonb_build_object('supplier_name', 'NCC', 'items', jsonb_build_array(
               jsonb_build_object('ingredient_id', rt.ing('Thịt bò A'), 'input_quantity', 10, 'input_unit', 'kg', 'conversion_factor', 1,
                                  'unit_price_per_input_unit', 250000, 'lot_code', 'BO-1', 'expiry_date', rc_business_date(now()) + 5),
               jsonb_build_object('ingredient_id', rt.ing('Coca lon'), 'input_quantity', 1, 'input_unit', 'thùng', 'conversion_factor', 24,
                                  'unit_price_per_input_unit', 192000, 'lot_code', 'CO-NEW', 'expiry_date', rc_business_date(now()) + 30),
               jsonb_build_object('ingredient_id', rt.ing('Coca lon'), 'input_quantity', 6, 'input_unit', 'lon', 'conversion_factor', 1,
                                  'unit_price_per_input_unit', 8000, 'lot_code', 'CO-OLD', 'expiry_date', rc_business_date(now()) + 2))));
    PERFORM rc_confirm_receipt(v_r, rt.emp('M1'));
    SELECT b.id INTO v_old FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = 'CO-OLD';
    PERFORM rc_mark_batch_unusable('RAW', v_old, rt.emp('M1'), 'Móp lon');
    v_i := rc_create_stock_issue(rt.emp('M1'), 'rpt-i1', jsonb_build_object('items', jsonb_build_array(
               jsonb_build_object('inventory_batch_id', rt.lot(v_r, 'Thịt bò A'), 'quantity', 7, 'fulfillment_mode', 'PREPARE'),
               jsonb_build_object('inventory_batch_id', (SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = 'CO-NEW'),
                                  'quantity', 12, 'fulfillment_mode', 'DIRECT'))));
    PERFORM rc_issue_stock(v_i, rt.emp('M1'));
    PERFORM rc_receive_issue(v_i, rt.emp('K1'));
    v_i2 := rc_create_stock_issue(rt.emp('M1'), 'rpt-i2', jsonb_build_object('items', jsonb_build_array(
               jsonb_build_object('inventory_batch_id', rt.lot(v_r, 'Thịt bò A'), 'quantity', 1, 'fulfillment_mode', 'PREPARE'))));
    PERFORM rc_issue_stock(v_i2, rt.emp('M1'));                                    -- chưa nhận: đang bàn giao
    INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, loss_reason, prepared_by_id, request_key)
    VALUES ((SELECT id FROM stock_issue_items WHERE stock_issue_id = v_i AND fulfillment_mode = 'PREPARE'), 7, 'Lọc gân mỡ', rt.emp('K1'), 'rpt-p1')
    RETURNING id INTO v_p;
    INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity)
    VALUES (v_p, (SELECT id FROM kitchen_ingredient_types WHERE name = 'Bò thái'), 6.3);
    PERFORM rc_confirm_preparation(v_p, rt.emp('K1'));
    SELECT k.id INTO v_kb FROM kitchen_batches k JOIN kitchen_ingredient_types t ON t.id = k.kitchen_ingredient_type_id WHERE t.name = 'Coca lon';
    INSERT INTO stock_returns (requested_by_id, reason, request_key) VALUES (rt.emp('K1'), 'Dư', 'rpt-rt1') RETURNING id INTO v_rt;
    INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, direct_kitchen_batch_id, quantity)
    VALUES (v_rt, (SELECT id FROM stock_issue_items WHERE stock_issue_id = v_i AND fulfillment_mode = 'DIRECT'), v_kb, 1);
    PERFORM rc_confirm_return(v_rt, rt.emp('M1'));
    INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key) VALUES ('DAMAGE', 'Rơi vỡ', rt.emp('K1'), 'rpt-a1')
    RETURNING id INTO v_a;
    INSERT INTO stock_adjustment_items (stock_adjustment_id, kitchen_batch_id, quantity_delta) VALUES (v_a, v_kb, -1);
    PERFORM rc_confirm_adjustment(v_a, rt.emp('M1'));
END $$;

-- Bán hàng: A tiền mặt, B QR (webhook lặp + đến muộn), C bỏ về, D miễn thu, E đang giữ nguyên liệu
DO $$
DECLARE
    v_o   BIGINT;
    v_inv BIGINT;
    v_ref TEXT;
BEGIN
    v_o := rt.table_served('A', ARRAY['Bò lúc lắc', 'Bò lúc lắc', 'Coca', 'Coca']);
    v_inv := rc_checkout_invoice(v_o, rt.emp('C1'));
    PERFORM rc_pay_cash(v_inv, rt.emp('C1'), 200000);
    v_o := rt.table_served('B', ARRAY['Bò lúc lắc', 'Coca']);
    v_inv := rc_checkout_invoice(v_o, rt.emp('C1'));
    v_ref := rc_start_qr(v_inv, rt.emp('C1'));
    PERFORM rt.eq('QR thành công', rc_process_verified_qr('SEPAY-1', v_ref, 65000, '{}'), 'PROCESSED');
    PERFORM rt.eq('QR lặp', rc_process_verified_qr('SEPAY-1', v_ref, 65000, '{}'), 'PROCESSED');
    PERFORM rt.eq('QR chuyển lần hai', rc_process_verified_qr('SEPAY-2', v_ref, 65000, '{}'), 'AFTER_CANCEL');
    v_o := rt.table_served('C', ARRAY['Bò lúc lắc']);
    PERFORM rc_abandon_session((SELECT table_session_id FROM orders WHERE id = v_o), rt.emp('M1'), 'Khách đi không trả');
    v_o := rt.table_served('D', ARRAY['Coca']);
    PERFORM rc_waive_invoice(v_o, rt.emp('M1'), 'Đền bù phục vụ chậm');
    PERFORM rt.table_served('E', ARRAY['Bò lúc lắc'], false);
    INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, request_key)
    VALUES (rc_business_date(now()), 'SUPPLIES', 'Đá viên', 50000, 'CASH', 'CASH_DRAWER', rt.emp('M1'), now(), 'rpt-e1'),
           (rc_business_date(now()), 'UTILITY', 'Tiền điện', 100000, 'BANK_TRANSFER', 'OWNER', rt.emp('M1'), now(), 'rpt-e2');
    PERFORM rc_reconcile_cash(rc_business_date(now()), 500000, 575000, rt.emp('M1'), 'Thiếu 5 000 chưa rõ lý do');
END $$;

-- So sánh với số tính trước
DO $$
DECLARE
    d DATE := rc_business_date(now());
    r RECORD;
BEGIN
    -- 5.2 doanh thu
    SELECT * INTO r FROM rpt_revenue_by_day(d, d);
    PERFORM rt.eq('DT tiền mặt', r.cash_amount, 130000::numeric);
    PERFORM rt.eq('DT QR (không đếm webhook lặp/đến muộn)', r.qr_amount, 65000::numeric);
    PERFORM rt.eq('DT tổng', r.total_amount, 195000::numeric);
    PERFORM rt.eq('Số hóa đơn đã thu', r.paid_invoices, 2::bigint);
    PERFORM rt.eq('DT kỳ khác = 0 dòng', (SELECT count(*) FROM rpt_revenue_by_day(d - 7, d - 1)), 0::bigint);
    SELECT * INTO r FROM rpt_revenue_by_item(d, d) WHERE item_name = 'Bò lúc lắc';
    PERFORM rt.eq('Bò: suất hợp lệ (không gồm bỏ về/đang giữ)', r.servings, 3::bigint);
    PERFORM rt.eq('Bò: doanh thu', r.revenue, 150000::numeric);
    SELECT * INTO r FROM rpt_revenue_by_item(d, d) WHERE item_name = 'Coca';
    PERFORM rt.eq('Coca: suất hợp lệ (không gồm miễn thu)', r.servings, 3::bigint);
    PERFORM rt.eq('Coca: doanh thu', r.revenue, 45000::numeric);
    PERFORM rt.eq('Tổng theo món = tổng theo ngày', (SELECT SUM(revenue) FROM rpt_revenue_by_item(d, d)), 195000::numeric);
    PERFORM rt.eq('Bỏ về (không phải doanh thu)', (SELECT amount FROM rpt_unpaid_closures(d, d) WHERE status = 'UNCOLLECTED'), 50000::numeric);
    PERFORM rt.eq('Miễn thu (không phải doanh thu)', (SELECT amount FROM rpt_unpaid_closures(d, d) WHERE status = 'WAIVED'), 15000::numeric);
    -- 5.4 kho
    SELECT * INTO r FROM rpt_raw_stock() WHERE ingredient_name = 'Thịt bò A';
    PERFORM rt.eq('RAW bò dùng được (10 − 7 − 1)', r.usable_quantity, 2.000::numeric);
    PERFORM rt.eq('RAW bò dưới tồn tối thiểu 3', r.below_minimum, true);
    SELECT * INTO r FROM rpt_raw_stock() WHERE ingredient_name = 'Coca lon';
    PERFORM rt.eq('RAW coca dùng được (24 − 12 + 1 trả)', r.usable_quantity, 13.000::numeric);
    PERFORM rt.eq('RAW coca hỏng', r.unusable_quantity, 6.000::numeric);
    PERFORM rt.eq('Đang bàn giao', (SELECT quantity FROM rpt_handover_and_wip() WHERE stage = 'IN_TRANSIT'), 1.000::numeric);
    PERFORM rt.eq('WIP chờ sơ chế', (SELECT count(*) FROM rpt_handover_and_wip() WHERE stage = 'WIP'), 0::bigint);
    SELECT * INTO r FROM rpt_kitchen_stock() WHERE type_name = 'Bò thái';
    PERFORM rt.eq('Bếp bò: tồn (6,3 − 4 × 0,2 đã nấu)', r.usable_on_hand, 5.500::numeric);
    PERFORM rt.eq('Bếp bò: đang giữ cho bàn E', r.reserved_quantity, 0.200::numeric);
    PERFORM rt.eq('Bếp bò: khả dụng', r.available_quantity, 5.300::numeric);
    SELECT * INTO r FROM rpt_kitchen_stock() WHERE type_name = 'Coca lon';
    PERFORM rt.eq('Bếp coca: 12 − 4 bán − 1 trả − 1 vỡ', r.usable_on_hand, 6.000::numeric);
    PERFORM rt.eq('Bếp coca: khả dụng', r.available_quantity, 6.000::numeric);
    PERFORM rt.eq('Lô không dùng được', (SELECT string_agg(location || ':' || quantity, ',') FROM rpt_unusable_lots()), 'RAW:6.000');
    PERFORM rt.eq('Sổ kho: số dòng theo nhóm',
                  (SELECT string_agg(category || '=' || n, ',' ORDER BY category)
                     FROM (SELECT category, count(*) n FROM rpt_stock_history(now() - interval '1 day', now() + interval '1 day') GROUP BY category) x),
                  'HOÀN TRẢ=2,HỦY HÀNG=1,NHẬP=3,TIÊU HAO=10,XUẤT=5');
    PERFORM rt.eq('Sổ kho bò: tổng biến động RAW = tồn RAW',
                  (SELECT SUM(quantity_delta) FROM rpt_stock_history(now() - interval '1 day', now() + interval '1 day', rt.ing('Thịt bò A')) WHERE location = 'RAW'),
                  2.000::numeric);
    -- hao hụt
    SELECT * INTO r FROM rpt_preparation_yield(d, d);
    PERFORM rt.eq('Sơ chế: đầu vào', r.input_quantity, 7.000::numeric);
    PERFORM rt.eq('Sơ chế: thành phẩm', r.output_quantity, 6.300::numeric);
    PERFORM rt.eq('Sơ chế: hao hụt', r.loss_quantity, 0.700::numeric);
    PERFORM rt.eq('Sơ chế: tỷ lệ thu hồi', r.yield_ratio, 0.9000::numeric);
    PERFORM rt.eq('Sơ chế: tỷ lệ hao hụt', r.loss_ratio, 0.1000::numeric);
    PERFORM rt.eq('Sơ chế: truy xuất lô và người', r.lot_code || '/' || r.prepared_by, 'BO-1/Bếp An');
    -- 5.5 đối soát
    SELECT * INTO r FROM rpt_cash_reconciliation(d);
    PERFORM rt.eq('Quỹ đầu ngày', r.opening_cash, 500000::numeric);
    PERFORM rt.eq('Thu tiền mặt', r.cash_sales, 130000::numeric);
    PERFORM rt.eq('Chi tiền mặt quỹ quầy', r.cash_expenses, 50000::numeric);
    PERFORM rt.eq('Tiền dự kiến', r.expected_cash, 580000::numeric);
    PERFORM rt.eq('Tiền đếm', r.actual_cash, 575000::numeric);
    PERFORM rt.eq('Chênh lệch', r.difference, -5000::numeric);
    PERFORM rt.eq('Bản đối soát còn khớp số tươi', r.stale, false);
    PERFORM rt.eq('Bản đối soát lưu = báo cáo', (SELECT expected_cash::text || '/' || difference FROM daily_cash_reconciliations WHERE status = 'CURRENT'),
                  '580000.00/-5000.00');
    RAISE NOTICE 'BÁO CÁO ĐẠT: % phép so sánh', (SELECT passed FROM rt.n);
END $$;

DROP SCHEMA rt CASCADE;
