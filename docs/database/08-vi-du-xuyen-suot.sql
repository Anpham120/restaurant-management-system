-- =====================================================================
-- RESTAURANT CORE — Ví dụ xuyên suốt của docs/01 §11, chạy thật qua các hàm rc_* của schema v4.1
-- Chạy SAU 04-schema-v4.1.sql, trên database TRỐNG (không trộn với 05-test-v4.1.sql vì dùng id cố định):
--     psql -v ON_ERROR_STOP=1 -d restaurant_core_example -f 04-schema-v4.1.sql
--     psql -v ON_ERROR_STOP=1 -d restaurant_core_example -f 08-vi-du-xuyen-suot.sql
-- Mỗi con số trong bảng 01 §11 được so sánh bằng ex.eq(); sai ở đâu dừng với 'VÍ DỤ SAI: …'. Cuối file in số phép so sánh.
-- =====================================================================
CREATE SCHEMA ex;
CREATE TABLE ex.n (k INT PRIMARY KEY DEFAULT 1, passed INT NOT NULL DEFAULT 0);
INSERT INTO ex.n DEFAULT VALUES;
CREATE FUNCTION ex.eq(p_label TEXT, p_actual TEXT, p_expected TEXT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_actual IS DISTINCT FROM p_expected THEN
        RAISE EXCEPTION 'VÍ DỤ SAI: % — thực tế %, kỳ vọng %', p_label, p_actual, p_expected;
    END IF;
    UPDATE ex.n SET passed = passed + 1;
END $$;

-- Dữ liệu nền: 4 nhân viên (mỗi người một tài khoản đúng vai trò), nguyên liệu "Thịt bò A", loại bếp "Bò thái" (PREPARED),
-- món "Bò lúc lắc" 50.000đ (0,2 kg/suất), thực đơn hôm nay quota 30, bàn B01
INSERT INTO departments (department_name) VALUES ('Vận hành');
INSERT INTO employees (employee_code, full_name, department_id) VALUES
    ('M1', 'Quản lý', 1), ('W1', 'Phục vụ', 1), ('K1', 'Bếp', 1), ('C1', 'Thu ngân', 1);
INSERT INTO accounts (employee_id, username, password_hash) SELECT id, lower(employee_code), 'x' FROM employees;
INSERT INTO account_roles (account_id, role_id)
SELECT a.id, r.id FROM accounts a JOIN employees e ON e.id = a.employee_id
  JOIN roles r ON r.role_code = CASE e.employee_code WHEN 'M1' THEN 'MANAGER' WHEN 'W1' THEN 'WAITER'
                                                     WHEN 'K1' THEN 'KITCHEN' ELSE 'CASHIER' END;
INSERT INTO ingredients (name, base_unit) VALUES ('Thịt bò A', 'kg');
INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type, shelf_life_hours) VALUES (1, 'Bò thái', 'PREPARED', 48);
INSERT INTO menu_categories (name) VALUES ('Món chính');
INSERT INTO menu_items (menu_category_id, name, price) VALUES (1, 'Bò lúc lắc', 50000);
INSERT INTO recipes (menu_item_id, version, is_current) VALUES (1, 1, true);
INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES (1, 1, 0.2);
INSERT INTO daily_menus (business_date, status, published_at) VALUES (rc_business_date(now()), 'PUBLISHED', now());
INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) VALUES (1, 1, 1, 30);
INSERT INTO dining_tables (code, capacity) VALUES ('B01', 4);

-- Các bước 1–4: kho
DO $$
BEGIN
    -- tạo phiếu bằng hàm idempotent (QĐ-ID): request_key do client sinh một lần cho mỗi lần bấm "Tạo"
    PERFORM ex.eq('1 tạo phiếu nhập', rc_create_stock_receipt(1, 'ex-receipt-1', jsonb_build_object('supplier_name', 'NCC',
                      'items', jsonb_build_array(jsonb_build_object('ingredient_id', 1, 'input_quantity', 10, 'input_unit', 'kg',
                      'conversion_factor', 1, 'unit_price_per_input_unit', 250000, 'lot_code', 'L1',
                      'expiry_date', rc_business_date(now()) + 5))))::text, '1');
    PERFORM rc_confirm_receipt(1, 1);
    PERFORM ex.eq('1 RAW sau nhập', (SELECT quantity_on_hand::text FROM inventory_batches WHERE id = 1), '10.000');

    PERFORM ex.eq('2 tạo phiếu xuất', rc_create_stock_issue(1, 'ex-issue-1', jsonb_build_object('items', jsonb_build_array(
                      jsonb_build_object('inventory_batch_id', 1, 'quantity', 7, 'fulfillment_mode', 'PREPARE'))))::text, '1');
    PERFORM rc_issue_stock(1, 1);
    PERFORM ex.eq('2 RAW sau xuất', (SELECT quantity_on_hand::text FROM inventory_batches WHERE id = 1), '3.000');
    PERFORM ex.eq('2 đang chuyển', (SELECT SUM(l.quantity)::text FROM stock_issue_items l JOIN stock_issues s ON s.id = l.stock_issue_id
                                     WHERE s.status = 'ISSUED'), '7.000');

    PERFORM rc_receive_issue(1, 3);
    PERFORM ex.eq('3 WIP sau khi bếp nhận', (SELECT wip_remaining_quantity::text FROM stock_issue_items WHERE id = 1), '7.000');

    INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, prepared_by_id, request_key) VALUES (1, 7, 3, 'p1');
    INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity) VALUES (1, 1, 6.3);
    PERFORM rc_confirm_preparation(1, 3);
    PERFORM ex.eq('4 WIP sau sơ chế', (SELECT wip_remaining_quantity::text FROM stock_issue_items WHERE id = 1), '0.000');
    PERFORM ex.eq('4 tồn bếp', (SELECT quantity_on_hand::text FROM kitchen_batches WHERE id = 1), '6.300');
    PERFORM ex.eq('4 hao hụt = đầu vào − Σ đầu ra',
                  (SELECT (p.input_quantity - SUM(o.output_quantity))::text FROM kitchen_preparations p
                     JOIN kitchen_preparation_outputs o ON o.kitchen_preparation_id = p.id GROUP BY p.input_quantity), '0.700');
END $$;

-- Các bước 5–11: bán hàng và thanh toán tiền mặt
DO $$
BEGIN
    PERFORM rc_open_session(1, 2);
    PERFORM ex.eq('5 bàn B01', (SELECT status FROM dining_tables WHERE id = 1), 'OCCUPIED');
    PERFORM rc_submit_batch(1, 2, 'b1', '[{"daily_menu_item_id": 1, "quantity": 1}]'::jsonb);
    PERFORM ex.eq('6 dòng món', (SELECT status || ' ' || unit_price FROM order_items WHERE id = 1), 'PENDING 50000.00');
    PERFORM ex.eq('7 nhận món', rc_accept_item(1, 3), 'ACCEPTED');
    PERFORM ex.eq('7 bếp tồn/giữ', (SELECT quantity_on_hand || '/' || reserved_quantity FROM kitchen_batches WHERE id = 1), '6.300/0.200');
    PERFORM rc_start_cooking(1, 3);
    PERFORM ex.eq('8 bếp tồn/giữ sau nấu', (SELECT quantity_on_hand || '/' || reserved_quantity FROM kitchen_batches WHERE id = 1), '6.100/0.000');
    PERFORM rc_finish_dish(1, 3);
    PERFORM rc_serve_item(1, 2);
    PERFORM rc_checkout_invoice(1, 4);
    PERFORM ex.eq('10 hóa đơn', (SELECT status || ' ' || total FROM invoices WHERE id = 1), 'PAYMENT_PENDING 50000.00');
    PERFORM rc_pay_cash(1, 4, 60000);
    PERFORM ex.eq('11 tiền thối', (SELECT change_amount::text FROM payment_requests WHERE id = 1), '10000.00');
    PERFORM ex.eq('11 payment = số phải thu', (SELECT amount::text FROM payments WHERE id = 1), '50000.00');
    PERFORM ex.eq('11 bàn B01 trống', (SELECT status FROM dining_tables WHERE id = 1), 'AVAILABLE');
    PERFORM ex.eq('11 hóa đơn PAID', (SELECT status FROM invoices WHERE id = 1), 'PAID');
END $$;

-- Các bước 12–13: chi phí và đối soát
DO $$
BEGIN
    INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source,
                                    stock_receipt_id, recorded_by_id, paid_at, request_key)
    VALUES (rc_business_date(now()), 'INGREDIENT', 'Trả NCC', 2500000, 'BANK_TRANSFER', 'BANK', 1, 1, now(), 'e1'),
           (rc_business_date(now()), 'SUPPLIES', 'Mua đá', 50000, 'CASH', 'CASH_DRAWER', NULL, 1, now(), 'e2');
    PERFORM rc_reconcile_cash(rc_business_date(now()), 1000000, 1000000, 1);
    PERFORM ex.eq('13 cash_sales', (SELECT cash_sales::text FROM daily_cash_reconciliations WHERE id = 1), '50000.00');
    PERFORM ex.eq('13 cash_expenses (chỉ tiền đá từ két)', (SELECT cash_expenses::text FROM daily_cash_reconciliations WHERE id = 1), '50000.00');
    PERFORM ex.eq('13 expected_cash', (SELECT expected_cash::text FROM daily_cash_reconciliations WHERE id = 1), '1000000.00');
    PERFORM ex.eq('13 difference', (SELECT difference::text FROM daily_cash_reconciliations WHERE id = 1), '0.00');
END $$;

-- Kiểm tra chéo sổ kho và các bất biến liên quan
DO $$
DECLARE
    v_q TEXT;
    v_n TEXT;
BEGIN
    PERFORM ex.eq('sổ RAW lô#1 = +10 −7', (SELECT SUM(quantity_delta)::text FROM stock_movements WHERE raw_batch_id = 1), '3.000');
    PERFORM ex.eq('sổ WIP dòng#1 = +7 −7', (SELECT SUM(quantity_delta)::text FROM stock_movements WHERE wip_issue_item_id = 1), '0.000');
    PERFORM ex.eq('sổ bếp lô#1 = +6,3 −0,2', (SELECT SUM(quantity_delta)::text FROM stock_movements WHERE kitchen_batch_id = 1), '6.100');
    PERFORM ex.eq('không có movement −0,7 (hao hụt sơ chế là giá trị suy ra)', (SELECT count(*)::text FROM stock_movements WHERE quantity_delta = -0.7), '0');
    PERFORM ex.eq('suất đã chiếm', rc_quota_used(1)::text, '1');
    FOREACH v_q IN ARRAY ARRAY[
        'SELECT b.id FROM inventory_batches b LEFT JOIN stock_movements m ON m.raw_batch_id = b.id GROUP BY b.id HAVING b.quantity_on_hand <> COALESCE(SUM(m.quantity_delta), 0)',
        'SELECT i.id FROM stock_issue_items i LEFT JOIN stock_movements m ON m.wip_issue_item_id = i.id GROUP BY i.id HAVING i.wip_remaining_quantity <> COALESCE(SUM(m.quantity_delta), 0)',
        'SELECT k.id FROM kitchen_batches k WHERE k.quantity_on_hand <> (SELECT COALESCE(SUM(m.quantity_delta), 0) FROM stock_movements m WHERE m.kitchen_batch_id = k.id)',
        'SELECT v.id FROM invoices v LEFT JOIN invoice_items ii ON ii.invoice_id = v.id GROUP BY v.id HAVING v.total <> COALESCE(SUM(ii.line_total), 0)',
        'SELECT p.id FROM payments p JOIN payment_requests r ON r.id = p.payment_request_id JOIN invoices v ON v.id = p.invoice_id WHERE p.amount <> r.amount_due OR p.amount <> v.total OR v.status <> ''PAID''',
        'SELECT r.business_date FROM daily_cash_reconciliations r WHERE r.status = ''CURRENT'' AND r.cash_sales <> (SELECT COALESCE(SUM(p.amount), 0) FROM payments p WHERE p.business_date = r.business_date AND p.method = ''CASH'')'
    ] LOOP
        EXECUTE 'SELECT count(*)::text FROM (' || v_q || ') x' INTO v_n;
        PERFORM ex.eq('bất biến phải trả 0 dòng: ' || left(v_q, 50), v_n, '0');
    END LOOP;
    RAISE NOTICE 'VÍ DỤ XUYÊN SUỐT ĐẠT: % phép so sánh', (SELECT passed FROM ex.n);
END $$;

DROP SCHEMA ex CASCADE;
