-- Kiểm thử liên phân hệ (phân hệ 1–5) trên lược đồ PostgreSQL của tài liệu.
-- Chạy trên CSDL trống, sau hai tệp lược đồ (cần PostgreSQL 15 trở lên):
--   psql -v ON_ERROR_STOP=1 -d <csdl_trong> -f docs/01-quan-ly-nhan-vien/schema-postgresql.sql \
--        -f docs/database/schema-phan-he-2-5.sql -f docs/database/test-lien-phan-he.sql
-- hoặc trên PGlite: node docs/tools/run-pg-tests.mjs --pglite <thư mục gói @electric-sql/pglite>
-- Toàn bộ chạy trong một transaction và kết thúc bằng ROLLBACK, không để lại dữ liệu.
-- Mỗi ca ghi vào bảng tạm test_results; mong đợi là OK hoặc "SQLSTATE tên_ràng_buộc" (như test phân hệ 1).
-- Truy vấn mẫu trong README phân hệ 3, 4, 5 chép nguyên văn (check-docs.js kiểm tra), tham số :tên đặt bằng \set.
-- Nhóm ca: M2 thực đơn, M4 kho, M3 đơn hàng và QR, TT tiền mặt và tiền thừa, M5 tài chính,
-- KC khoản chi theo phiếu nhập, ST trạng thái không hợp lệ, Q truy vấn mẫu README, R xóa bị chặn (RESTRICT).

BEGIN;

CREATE TEMP TABLE test_results (
    seq SERIAL PRIMARY KEY,
    case_no TEXT NOT NULL,
    descr TEXT NOT NULL,
    expected TEXT NOT NULL,
    actual TEXT NOT NULL,
    passed BOOLEAN NOT NULL
);

-- Chạy một câu lệnh; ghi OK hoặc "SQLSTATE tên_ràng_buộc". Câu lệnh lỗi tự rollback phần của nó.
-- expected: "OK", "SQLSTATE" hoặc "SQLSTATE tên_ràng_buộc"; nhiều SQLSTATE ngăn bằng "|"
-- (xóa dòng bị RESTRICT chặn: bản 18 trả 23001, bản cũ hơn trả 23503). Không ghi tên thì chỉ so SQLSTATE.
CREATE FUNCTION pg_temp.expect(p_case TEXT, p_descr TEXT, p_expected TEXT, p_stmt TEXT) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    v_actual TEXT;
    v_constraint TEXT;
    v_name TEXT := nullif(split_part(p_expected, ' ', 2), '');
BEGIN
    BEGIN
        EXECUTE p_stmt;
        v_actual := 'OK';
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_constraint = CONSTRAINT_NAME;
        v_actual := trim(SQLSTATE || ' ' || COALESCE(v_constraint, ''));
    END;
    INSERT INTO test_results (case_no, descr, expected, actual, passed)
    VALUES (p_case, p_descr, p_expected, v_actual,
            split_part(v_actual, ' ', 1) = ANY (string_to_array(split_part(p_expected, ' ', 1), '|'))
            AND (v_name IS NULL OR v_name = nullif(split_part(v_actual, ' ', 2), '')));
END;
$$;

CREATE FUNCTION pg_temp.assert_eq(p_case TEXT, p_descr TEXT, p_expected TEXT, p_actual TEXT) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO test_results (case_no, descr, expected, actual, passed)
    VALUES (p_case, p_descr, p_expected, COALESCE(p_actual, 'NULL'), p_actual IS NOT DISTINCT FROM p_expected);
END;
$$;

-- ===== Dữ liệu nền phân hệ 1 (bộ phận có sẵn trong dữ liệu khởi tạo) =====
INSERT INTO employees (id, employee_code, full_name, phone, hire_date, department_id) VALUES
    (1, 'W01', 'Phục vụ A', '0900000001', DATE '2026-01-01', (SELECT id FROM departments WHERE department_name = 'Phục vụ')),
    (2, 'C01', 'Thu ngân B', '0900000002', DATE '2026-01-01', (SELECT id FROM departments WHERE department_name = 'Thu ngân')),
    (3, 'M01', 'Quản lý C', '0900000003', DATE '2026-01-01', (SELECT id FROM departments WHERE department_name = 'Quản lý'));

-- ===== M2: thực đơn, khóa ngoại ghép công thức – món =====
INSERT INTO menu_categories (id, name, sort_order, is_active) VALUES (1, 'Món chính', 1, TRUE);
INSERT INTO menu_items (id, menu_category_id, name, price, is_active) VALUES
    (1, 1, 'Phở bò', 50000, TRUE), (2, 1, 'Cơm rang', 40000, TRUE), (3, 1, 'Bún chả', 45000, TRUE);
INSERT INTO recipes (id, menu_item_id, version, is_current) VALUES (1, 1, 1, TRUE), (2, 2, 1, TRUE), (4, 3, 1, TRUE);
INSERT INTO daily_menus (id, business_date, status) VALUES (1, DATE '2026-10-19', 'PUBLISHED');

SELECT pg_temp.expect('M2-01', 'Dòng thực đơn ngày dùng công thức đúng món', 'OK',
    $q$INSERT INTO daily_menu_items (id, daily_menu_id, menu_item_id, recipe_id, quota_total, is_paused) VALUES (1, 1, 1, 1, 30, FALSE)$q$);
SELECT pg_temp.expect('M2-02', 'Gắn công thức phở bò cho cơm rang bị chặn', '23503 fk_daily_menu_items_recipe_menu_item',
    $q$INSERT INTO daily_menu_items (id, daily_menu_id, menu_item_id, recipe_id, quota_total, is_paused) VALUES (2, 1, 2, 1, 20, FALSE)$q$);
SELECT pg_temp.expect('M2-03', 'Dòng cơm rang với công thức cơm rang', 'OK',
    $q$INSERT INTO daily_menu_items (id, daily_menu_id, menu_item_id, recipe_id, quota_total, is_paused) VALUES (2, 1, 2, 2, 20, FALSE)$q$);
SELECT pg_temp.expect('M2-04', 'Đổi món sang bún chả mà giữ công thức phở bò bị chặn', '23503 fk_daily_menu_items_recipe_menu_item',
    $q$UPDATE daily_menu_items SET menu_item_id = 3 WHERE id = 1$q$);
SELECT pg_temp.expect('M2-05', 'Đổi cả món và công thức cho khớp thì được', 'OK',
    $q$UPDATE daily_menu_items SET menu_item_id = 3, recipe_id = 4 WHERE id = 1$q$);
SELECT pg_temp.expect('M2-06', 'Trả dòng về phở bò với công thức phở bò', 'OK',
    $q$UPDATE daily_menu_items SET menu_item_id = 1, recipe_id = 1 WHERE id = 1$q$);
SELECT pg_temp.expect('M2-07', 'Giá bán âm bị chặn', '23514 ck_menu_items_price',
    $q$UPDATE menu_items SET price = -1 WHERE id = 1$q$);
SELECT pg_temp.expect('M2-08', 'Phiên bản công thức 0 bị chặn', '23514 ck_recipes_version',
    $q$INSERT INTO recipes (id, menu_item_id, version, is_current) VALUES (3, 2, 0, FALSE)$q$);
SELECT pg_temp.expect('M2-09', 'Món có hai công thức hiện hành bị chặn', '23505 ux_recipes_menu_item',
    $q$INSERT INTO recipes (id, menu_item_id, version, is_current) VALUES (5, 1, 2, TRUE)$q$);

-- ===== M4: kho – quy đổi làm tròn, lô cùng nguyên liệu, nhiều lô trong một phiếu, FEFO =====
INSERT INTO ingredients (id, name, base_unit, min_quantity, is_expiry_tracked, is_active) VALUES
    (1, 'Thịt bò', 'g', 500, TRUE, TRUE), (2, 'Gạo', 'g', 1000, FALSE, TRUE);
INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity_per_serving) VALUES (1, 1, 1, 100), (2, 2, 2, 150);
INSERT INTO stock_receipts (id, supplier_name, status, created_at) VALUES (1, 'NCC A', 'DRAFT', now()), (2, 'NCC B', 'DRAFT', now());

SELECT pg_temp.assert_eq('M4-01', 'Không làm tròn thì 0.333 × 0.333 khác 0.111 (CHECK bằng nhau không ROUND sẽ sai)', 'false',
    ((0.333::DECIMAL(12,3) * 0.333::DECIMAL(12,3)) = 0.111::DECIMAL(12,3))::TEXT);
SELECT pg_temp.expect('M4-02', 'Quy đổi = ROUND(0.333 × 0.333, 3) = 0.111 được nhận', 'OK',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price)
       VALUES (1, 1, 2, 0.333, 'kg', 0.333, 0.111, 20000)$q$);
SELECT pg_temp.expect('M4-03', 'Số lượng quy đổi sai bị chặn', '23514 ck_stock_receipt_items_quantity',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price, lot_code)
       VALUES (9, 1, 2, 5, 'kg', 1000, 4999, 20000, 'X9')$q$);
INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price, lot_code, expiry_date)
    VALUES (2, 1, 1, 5, 'kg', 1000, 5000, 200000, 'L0', DATE '2026-10-29');
SELECT pg_temp.expect('M4-04', 'Xác nhận phiếu thiếu người xác nhận bị chặn', '23514 ck_stock_receipts_confirmed',
    $q$UPDATE stock_receipts SET status = 'CONFIRMED' WHERE id = 1$q$);
SELECT pg_temp.expect('M4-05', 'Xác nhận phiếu đủ người và thời điểm', 'OK',
    $q$UPDATE stock_receipts SET status = 'CONFIRMED', confirmed_by_id = 3, confirmed_at = now() WHERE id = 1$q$);
SELECT pg_temp.expect('M4-06', 'Lô cùng nguyên liệu với dòng phiếu', 'OK',
    $q$INSERT INTO inventory_batches (id, ingredient_id, stock_receipt_item_id, lot_code, expiry_date, received_at, quantity_on_hand, reserved_quantity, is_usable)
       VALUES (1, 1, 2, 'L0', DATE '2026-10-29', now(), 5000, 0, TRUE)$q$);
SELECT pg_temp.expect('M4-07', 'Lô khác nguyên liệu với dòng phiếu bị chặn', '23503 fk_inventory_batches_receipt_item_ingredient',
    $q$INSERT INTO inventory_batches (id, ingredient_id, stock_receipt_item_id, received_at, quantity_on_hand, reserved_quantity, is_usable)
       VALUES (2, 1, 1, now(), 111, 0, TRUE)$q$);
SELECT pg_temp.expect('M4-08', 'Đánh dấu lô hỏng thiếu lý do bị chặn', '23514 ck_inventory_batches_unusable_reason',
    $q$UPDATE inventory_batches SET is_usable = FALSE WHERE id = 1$q$);
SELECT pg_temp.expect('M4-09', 'Biến động RECEIPT gắn dòng món bị chặn (nguồn khớp loại)', '23514 ck_stock_movements_source',
    $q$INSERT INTO stock_movements (inventory_batch_id, movement_type, quantity_delta, balance_after, order_item_id, created_at)
       VALUES (1, 'RECEIPT', 5000, 5000, 1, now())$q$);
SELECT pg_temp.expect('M4-10', 'Biến động RECEIPT hợp lệ', 'OK',
    $q$INSERT INTO stock_movements (inventory_batch_id, movement_type, quantity_delta, balance_after, created_at) VALUES (1, 'RECEIPT', 5000, 5000, now())$q$);
SELECT pg_temp.expect('M4-11', 'Phiếu 2: thịt bò lô L1', 'OK',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price, lot_code, expiry_date)
       VALUES (11, 2, 1, 1, 'kg', 1000, 1000, 200000, 'L1', DATE '2026-10-24')$q$);
SELECT pg_temp.expect('M4-12', 'Phiếu 2: thêm thịt bò lô L2 (nhiều lô cùng nguyên liệu)', 'OK',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price, lot_code, expiry_date)
       VALUES (12, 2, 1, 1, 'kg', 1000, 1000, 200000, 'L2', DATE '2026-10-21')$q$);
SELECT pg_temp.expect('M4-13', 'Phiếu 2: lặp đúng lô L1 cùng hạn bị chặn', '23505 uq_stock_receipt_items_lot',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price, lot_code, expiry_date)
       VALUES (19, 2, 1, 1, 'kg', 1000, 1000, 200000, 'L1', DATE '2026-10-24')$q$);
SELECT pg_temp.expect('M4-14', 'Phiếu 2: gạo không số lô, không hạn', 'OK',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price)
       VALUES (13, 2, 2, 1, 'kg', 1000, 1000, 20000)$q$);
SELECT pg_temp.expect('M4-15', 'Phiếu 2: dòng gạo thứ hai không số lô, không hạn bị chặn (NULLS NOT DISTINCT)', '23505 uq_stock_receipt_items_lot',
    $q$INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price)
       VALUES (18, 2, 2, 2, 'kg', 1000, 2000, 20000)$q$);
INSERT INTO stock_receipt_items (id, stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, quantity, unit_price, lot_code, expiry_date) VALUES
    (14, 2, 1, 1, 'kg', 1000, 1000, 200000, 'L3', NULL),
    (15, 2, 1, 1, 'kg', 1000, 1000, 200000, 'L4', DATE '2026-10-18'),
    (16, 2, 1, 1, 'kg', 1000, 1000, 200000, 'L5', DATE '2026-10-20');
UPDATE stock_receipts SET status = 'CONFIRMED', confirmed_by_id = 3, confirmed_at = now() WHERE id = 2;
INSERT INTO inventory_batches (id, ingredient_id, stock_receipt_item_id, lot_code, expiry_date, received_at, quantity_on_hand, reserved_quantity, is_usable, unusable_reason) VALUES
    (11, 1, 11, 'L1', DATE '2026-10-24', now(), 1000, 0, TRUE, NULL),
    (12, 1, 12, 'L2', DATE '2026-10-21', now(), 1000, 0, TRUE, NULL),
    (14, 1, 14, 'L3', NULL, now(), 1000, 0, TRUE, NULL),
    (15, 1, 15, 'L4', DATE '2026-10-18', now(), 1000, 0, TRUE, NULL),
    (16, 1, 16, 'L5', DATE '2026-10-20', now(), 1000, 0, FALSE, 'hỏng');

-- Truy vấn FEFO nguyên văn từ README phân hệ 4 (mục "FEFO và chống trừ kho hai lần")
\set nguyen_lieu 1
\set ngay_hien_tai '''2026-10-19'''
SELECT pg_temp.assert_eq('M4-16', 'FEFO (truy vấn README): hạn sớm trước, không theo dõi hạn sau cùng; bỏ lô hết hạn, lô hỏng', '12;11;1;14',
    (SELECT string_agg(f.id::text, ';' ORDER BY f.rn) FROM (SELECT row_number() OVER () AS rn, q.id FROM (
SELECT b.id, b.expiry_date, b.received_at, b.quantity_on_hand - b.reserved_quantity AS kha_dung
FROM inventory_batches b
WHERE b.ingredient_id = :nguyen_lieu
  AND b.is_usable
  AND (b.expiry_date IS NULL OR b.expiry_date >= :ngay_hien_tai)
  AND b.quantity_on_hand - b.reserved_quantity > 0
ORDER BY b.expiry_date ASC NULLS LAST, b.received_at, b.id
FOR UPDATE
    ) q) f));

-- ===== M3: bàn, phiên gắn cố định một bàn, đơn, hóa đơn =====
INSERT INTO dining_tables (id, code, capacity, status) VALUES (1, 'B05', 4, 'OCCUPIED'), (2, 'B06', 2, 'OCCUPIED'), (3, 'B07', 4, 'OCCUPIED');
SELECT pg_temp.expect('M3-01', 'Sức chứa 0 bị chặn', '23514 ck_dining_tables_capacity',
    $q$INSERT INTO dining_tables (id, code, capacity, status) VALUES (9, 'B09', 0, 'AVAILABLE')$q$);
INSERT INTO table_sessions (id, dining_table_id, opened_at, status) VALUES (1, 1, now(), 'OPEN'), (2, 2, now(), 'OPEN'), (3, 3, now(), 'OPEN');
SELECT pg_temp.expect('M3-02', 'Mở phiên thứ hai cho bàn đang có phiên OPEN bị chặn', '23505 ux_table_sessions_dining_table',
    $q$INSERT INTO table_sessions (id, dining_table_id, opened_at, status) VALUES (9, 1, now(), 'OPEN')$q$);
SELECT pg_temp.expect('M3-03', 'Đổi bàn của phiên bị trigger chặn (ORD-15, không có chuyển bàn)', 'P0001',
    $q$UPDATE table_sessions SET dining_table_id = 2 WHERE id = 1$q$);
SELECT pg_temp.expect('M3-04', 'Cập nhật cột khác của phiên vẫn được', 'OK',
    $q$UPDATE table_sessions SET opened_at = opened_at WHERE id = 1$q$);
INSERT INTO orders (id, table_session_id, status) VALUES (1, 1, 'OPEN'), (2, 2, 'OPEN'), (3, 3, 'OPEN');
SELECT pg_temp.expect('M3-05', 'Đơn thứ hai cho cùng phiên bị chặn', '23505 uq_orders_table_session',
    $q$INSERT INTO orders (id, table_session_id, status) VALUES (9, 1, 'OPEN')$q$);
INSERT INTO invoices (id, order_id, total, status) VALUES (1, 1, 0, 'DRAFT'), (2, 2, 0, 'DRAFT'), (3, 3, 0, 'DRAFT');
SELECT pg_temp.expect('M3-06', 'Hóa đơn thứ hai cho cùng đơn bị chặn', '23505 uq_invoices_order',
    $q$INSERT INTO invoices (id, order_id, total, status) VALUES (9, 1, 0, 'DRAFT')$q$);
SELECT pg_temp.expect('M3-07', 'Tổng hóa đơn âm bị chặn', '23514 ck_invoices_total',
    $q$UPDATE invoices SET total = -1 WHERE id = 1$q$);
INSERT INTO order_batches (id, order_id, waiter_id, submitted_at, request_key) VALUES (1, 1, 1, now(), 'rk-1'), (2, 2, 1, now(), 'rk-2'), (3, 3, 1, now(), 'rk-3');
INSERT INTO order_items (id, order_batch_id, daily_menu_item_id, quantity, unit_price, status, has_stock_exception) VALUES
    (1, 1, 1, 2, 50000, 'READY', FALSE), (2, 1, 2, 1, 40000, 'READY', FALSE), (3, 1, 1, 1, 50000, 'CANCELLED', FALSE),
    (4, 2, 1, 1, 50000, 'ACCEPTED', FALSE), (5, 3, 2, 2, 40000, 'READY', FALSE);
SELECT pg_temp.expect('M3-08', 'Đơn giá âm bị chặn', '23514 ck_order_items_quantity',
    $q$INSERT INTO order_items (id, order_batch_id, daily_menu_item_id, quantity, unit_price, status, has_stock_exception) VALUES (9, 1, 1, 1, -5, 'PENDING', FALSE)$q$);
UPDATE invoices SET total = 140000, status = 'PAYMENT_PENDING' WHERE id = 1;
UPDATE invoices SET total = 80000, status = 'PAYMENT_PENDING' WHERE id = 3;

-- ===== TT: thanh toán tiền mặt, tiền thừa = GREATEST(cash_received − amount_due, 0) (PAY-03, PAY-11) =====
SELECT pg_temp.expect('TT-01', 'Waiter gửi yêu cầu tiền mặt 140.000đ, chưa có cột tiền mặt', 'OK',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status) VALUES (1, 1, 1, 140000, 'CASH', 'REQUESTED')$q$);
SELECT pg_temp.expect('TT-02', 'Yêu cầu thứ hai còn hiệu lực cho cùng hóa đơn bị chặn', '23505 ux_payment_requests_invoice',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status) VALUES (9, 1, 1, 140000, 'CASH', 'REQUESTED')$q$);
SELECT pg_temp.expect('TT-03', 'Cashier duyệt, chờ bàn giao tiền mặt', 'OK',
    $q$UPDATE payment_requests SET cashier_id = 2, status = 'WAITING_CASH_HANDOVER' WHERE id = 1$q$);
SELECT pg_temp.expect('TT-04', 'Khách đưa thiếu 100.000đ: tiền thừa 0, được lưu, chưa PAID', 'OK',
    $q$UPDATE payment_requests SET cash_received = 100000, change_amount = 0 WHERE id = 1$q$);
SELECT pg_temp.expect('TT-05', 'Khách đưa thiếu mà ghi tiền thừa âm −40.000đ bị chặn', '23514 ck_payment_requests_change',
    $q$UPDATE payment_requests SET change_amount = -40000 WHERE id = 1$q$);
SELECT pg_temp.expect('TT-06', 'Khách đưa thiếu: Cashier đếm đúng 100.000đ cũng không chuyển PAID được', '23514 ck_payment_requests_cash_paid',
    $q$UPDATE payment_requests SET cash_counted = 100000, status = 'PAID' WHERE id = 1$q$);
SELECT pg_temp.expect('TT-07', 'Thu thêm, khách đưa tổng 150.000đ: tiền thừa 10.000đ', 'OK',
    $q$UPDATE payment_requests SET cash_received = 150000, change_amount = 10000 WHERE id = 1$q$);
SELECT pg_temp.expect('TT-08', 'Tiền thừa tính sai (9.000đ) bị chặn', '23514 ck_payment_requests_change',
    $q$UPDATE payment_requests SET change_amount = 9000 WHERE id = 1$q$);
SELECT pg_temp.expect('TT-09', 'Có tiền khách đưa nhưng bỏ trống tiền thừa bị chặn', '23514 ck_payment_requests_change_presence',
    $q$UPDATE payment_requests SET change_amount = NULL WHERE id = 1$q$);
SELECT pg_temp.expect('TT-10', 'Có tiền thừa mà chưa có tiền khách đưa bị chặn', '23514 ck_payment_requests_change_presence',
    $q$UPDATE payment_requests SET cash_received = NULL, change_amount = 0 WHERE id = 1$q$);
SELECT pg_temp.expect('TT-11', 'Cashier đếm lệch 140.000đ: giữ WAITING_CASH_HANDOVER để đối chiếu', 'OK',
    $q$UPDATE payment_requests SET cash_counted = 140000 WHERE id = 1$q$);
SELECT pg_temp.expect('TT-12', 'Số đếm lệch số khai thì không chuyển PAID được', '23514 ck_payment_requests_cash_paid',
    $q$UPDATE payment_requests SET status = 'PAID' WHERE id = 1$q$);
SELECT pg_temp.expect('TT-13', 'Chưa kiểm đếm (cash_counted trống) thì không chuyển PAID được', '23514 ck_payment_requests_cash_paid',
    $q$UPDATE payment_requests SET cash_counted = NULL, status = 'PAID' WHERE id = 1$q$);
SELECT pg_temp.expect('TT-14', 'Cashier đếm đủ 150.000đ khớp số khai, giao tiền thừa, xác nhận PAID', 'OK',
    $q$UPDATE payment_requests SET cash_counted = 150000, status = 'PAID' WHERE id = 1$q$);
SELECT pg_temp.assert_eq('TT-15', 'Yêu cầu PAID lưu đủ bốn số tiền: phải trả / khách đưa / Cashier đếm / tiền thừa', '140000.00/150000.00/150000.00/10000.00',
    (SELECT amount_due || '/' || cash_received || '/' || cash_counted || '/' || change_amount FROM payment_requests WHERE id = 1));
SELECT pg_temp.expect('TT-16', 'Ghi khoản thanh toán tiền mặt bằng số phải trả 140.000đ, không webhook', 'OK',
    $q$INSERT INTO payments (id, payment_request_id, amount, method, paid_at, business_date) VALUES (1, 1, 140000, 'CASH', now(), DATE '2026-10-19')$q$);
UPDATE invoices SET status = 'PAID' WHERE id = 1;
SELECT pg_temp.expect('TT-17', 'Yêu cầu QR có cột tiền mặt bị chặn', '23514 ck_payment_requests_qr_no_cash',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, cash_received, change_amount, method, status)
       VALUES (9, 2, 1, 50000, 1000, 0, 'QR', 'REQUESTED')$q$);
SELECT pg_temp.expect('TT-18', 'Yêu cầu QR không có cột tiền mặt: ràng buộc tiền mặt không áp dụng', 'OK',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, cashier_id, amount_due, method, status, payment_reference)
       VALUES (3, 3, 1, 2, 80000, 'QR', 'WAITING_TRANSFER', 'RMS-INV3')$q$);

-- ===== M3: yêu cầu thanh toán và QR, chống ghi trùng, webhook đến muộn =====
SELECT pg_temp.expect('M3-09', 'Yêu cầu tiền mặt có mã tham chiếu QR bị chặn', '23514 ck_payment_requests_payment_reference',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status, payment_reference) VALUES (9, 2, 1, 50000, 'CASH', 'REQUESTED', 'X')$q$);
SELECT pg_temp.expect('M3-10', 'QR chờ chuyển khoản thiếu mã tham chiếu bị chặn', '23514 ck_payment_requests_payment_reference',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, cashier_id, amount_due, method, status) VALUES (9, 2, 1, 2, 50000, 'QR', 'WAITING_TRANSFER')$q$);
SELECT pg_temp.expect('M3-11', 'Hủy yêu cầu thiếu lý do bị chặn', '23514 ck_payment_requests_cancel_reason',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status) VALUES (9, 2, 1, 50000, 'CASH', 'CANCELLED')$q$);
SELECT pg_temp.expect('M3-12', 'Trùng mã tham chiếu QR bị chặn', '23505 uq_payment_requests_payment_reference',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, cashier_id, amount_due, method, status, payment_reference)
       VALUES (9, 2, 1, 2, 50000, 'QR', 'WAITING_TRANSFER', 'RMS-INV3')$q$);
INSERT INTO payment_webhook_events (id, payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, received_at)
    VALUES (101, 3, 'SEPAY-1', 'RMS-INV3', 80000, 'PROCESSED', now());
SELECT pg_temp.expect('M3-13', 'Webhook gửi lặp cùng mã giao dịch bị chặn', '23505 uq_payment_webhook_events_provider_transaction_code',
    $q$INSERT INTO payment_webhook_events (id, payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, received_at)
       VALUES (109, 3, 'SEPAY-1', 'RMS-INV3', 80000, 'PROCESSED', now())$q$);

-- Câu ghi webhook chống lặp nguyên văn từ README phân hệ 3 (mục "Chuẩn hóa và khóa ngoại")
\set yeu_cau 3
\set ma_giao_dich '''SEPAY-1'''
\set ma_tham_chieu '''RMS-INV3'''
\set so_tien 80000
\set ket_qua '''PROCESSED'''
\set noi_dung '''{}'''
WITH ins AS (
INSERT INTO payment_webhook_events (payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, received_at, raw_payload)
VALUES (:yeu_cau, :ma_giao_dich, :ma_tham_chieu, :so_tien, :ket_qua, now(), :noi_dung)
ON CONFLICT (provider_transaction_code) DO NOTHING
RETURNING id
)
SELECT pg_temp.assert_eq('M3-14', 'Câu ghi webhook README: mã giao dịch đã có thì không trả dòng nào', '0', count(*)::text) FROM ins;
\set yeu_cau NULL
\set ma_giao_dich '''SEPAY-9'''
\set ket_qua '''MISMATCH'''
WITH ins AS (
INSERT INTO payment_webhook_events (payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, received_at, raw_payload)
VALUES (:yeu_cau, :ma_giao_dich, :ma_tham_chieu, :so_tien, :ket_qua, now(), :noi_dung)
ON CONFLICT (provider_transaction_code) DO NOTHING
RETURNING id
)
SELECT pg_temp.assert_eq('M3-15', 'Câu ghi webhook README: mã giao dịch mới thì ghi đúng một dòng', '1', count(*)::text) FROM ins;

SELECT pg_temp.expect('M3-16', 'Webhook PROCESSED không gắn yêu cầu bị chặn', '23514 ck_payment_webhook_events_processed_request',
    $q$INSERT INTO payment_webhook_events (id, provider_transaction_code, amount, processing_result, received_at) VALUES (108, 'SEPAY-8', 1, 'PROCESSED', now())$q$);
SELECT pg_temp.expect('M3-17', 'Thanh toán QR không trỏ webhook bị chặn', '23514 ck_payments_qr_webhook',
    $q$INSERT INTO payments (id, payment_request_id, amount, method, paid_at, business_date) VALUES (9, 3, 80000, 'QR', now(), DATE '2026-10-19')$q$);
SELECT pg_temp.expect('M3-18', 'Thanh toán tiền mặt mà trỏ webhook bị chặn', '23514 ck_payments_qr_webhook',
    $q$INSERT INTO payments (id, payment_request_id, payment_webhook_event_id, amount, method, paid_at, business_date) VALUES (9, 3, 101, 80000, 'CASH', now(), DATE '2026-10-19')$q$);
SELECT pg_temp.expect('M3-19', 'Thanh toán QR trỏ webhook của chính yêu cầu', 'OK',
    $q$INSERT INTO payments (id, payment_request_id, payment_webhook_event_id, amount, method, paid_at, business_date) VALUES (3, 3, 101, 80000, 'QR', now(), DATE '2026-10-19')$q$);
UPDATE payment_requests SET status = 'PAID' WHERE id = 3;
UPDATE invoices SET status = 'PAID' WHERE id = 3;
SELECT pg_temp.expect('M3-20', 'Webhook đến muộn (khách chuyển lần hai cho yêu cầu đã PAID) lưu MISMATCH', 'OK',
    $q$INSERT INTO payment_webhook_events (id, payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, received_at)
       VALUES (120, 3, 'SEPAY-2', 'RMS-INV3', 80000, 'MISMATCH', now())$q$);
SELECT pg_temp.expect('M3-21', 'Không tạo khoản thanh toán thứ hai cho yêu cầu đã PAID', '23505 uq_payments_payment_request',
    $q$INSERT INTO payments (id, payment_request_id, payment_webhook_event_id, amount, method, paid_at, business_date) VALUES (9, 3, 120, 80000, 'QR', now(), DATE '2026-10-19')$q$);
INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status, payment_reference, cancel_reason)
    VALUES (4, 2, 1, 50000, 'QR', 'CANCELLED', 'RMS-INV2', 'khách đổi ý');
SELECT pg_temp.expect('M3-22', 'Webhook đến sau khi yêu cầu đã hủy lưu AFTER_CANCEL', 'OK',
    $q$INSERT INTO payment_webhook_events (id, payment_request_id, provider_transaction_code, payment_reference, amount, processing_result, received_at)
       VALUES (130, 4, 'SEPAY-3', 'RMS-INV2', 50000, 'AFTER_CANCEL', now())$q$);
INSERT INTO payment_requests (id, invoice_id, waiter_id, cashier_id, amount_due, method, status, payment_reference)
    VALUES (5, 2, 1, 2, 50000, 'QR', 'WAITING_TRANSFER', 'RMS-INV2-B');
SELECT pg_temp.expect('M3-23', 'Thanh toán trỏ webhook của yêu cầu khác bị chặn', '23503 fk_payments_webhook_request',
    $q$INSERT INTO payments (id, payment_request_id, payment_webhook_event_id, amount, method, paid_at, business_date) VALUES (9, 5, 130, 50000, 'QR', now(), DATE '2026-10-19')$q$);

-- ===== M4: không trừ kho, không đặt giữ hai lần; điều kiện đóng đặt giữ, lý do chênh lệch kiểm kê =====
SELECT pg_temp.expect('M4-17', 'Tiêu hao dòng món 1 trên lô L2', 'OK',
    $q$INSERT INTO stock_movements (inventory_batch_id, movement_type, quantity_delta, balance_after, order_item_id, created_at) VALUES (12, 'CONSUMPTION', -100, 900, 1, now())$q$);
SELECT pg_temp.expect('M4-18', 'Tiêu hao lần hai cùng dòng món, cùng lô bị chặn', '23505 ux_stock_movements_order_item_inventory_batch',
    $q$INSERT INTO stock_movements (inventory_batch_id, movement_type, quantity_delta, balance_after, order_item_id, created_at) VALUES (12, 'CONSUMPTION', -100, 800, 1, now())$q$);
SELECT pg_temp.expect('M4-19', 'Đặt giữ dòng món 4 trên lô L1', 'OK',
    $q$INSERT INTO stock_reservations (order_item_id, inventory_batch_id, quantity, status, created_at) VALUES (4, 11, 100, 'ACTIVE', now())$q$);
SELECT pg_temp.expect('M4-20', 'Đặt giữ ACTIVE lần hai cùng dòng món, cùng lô bị chặn', '23505 ux_stock_reservations_order_item_inventory_batch',
    $q$INSERT INTO stock_reservations (order_item_id, inventory_batch_id, quantity, status, created_at) VALUES (4, 11, 100, 'ACTIVE', now())$q$);
SELECT pg_temp.expect('M4-21', 'Đặt giữ RELEASED thiếu thời điểm đóng bị chặn', '23514 ck_stock_reservations_closed_at',
    $q$INSERT INTO stock_reservations (order_item_id, inventory_batch_id, quantity, status, release_reason, created_at)
       VALUES (4, 12, 50, 'RELEASED', 'ITEM_EDITED', now())$q$);
SELECT pg_temp.expect('M4-22', 'Đặt giữ RELEASED có lý do và thời điểm đóng', 'OK',
    $q$INSERT INTO stock_reservations (order_item_id, inventory_batch_id, quantity, status, release_reason, created_at, closed_at)
       VALUES (4, 12, 50, 'RELEASED', 'ITEM_EDITED', now(), now())$q$);
INSERT INTO stock_counts (id, status, created_at) VALUES (1, 'DRAFT', now());
SELECT pg_temp.expect('M4-23', 'Dòng kiểm kê lệch −10 g mà không có lý do bị chặn', '23514 ck_stock_count_items_reason',
    $q$INSERT INTO stock_count_items (id, stock_count_id, inventory_batch_id, book_quantity, actual_quantity, difference) VALUES (1, 1, 11, 1000, 990, -10)$q$);
SELECT pg_temp.expect('M4-24', 'Dòng kiểm kê lệch có lý do', 'OK',
    $q$INSERT INTO stock_count_items (id, stock_count_id, inventory_batch_id, book_quantity, actual_quantity, difference, reason) VALUES (1, 1, 11, 1000, 990, -10, 'hao hụt')$q$);

-- ===== KC: khoản chi liên kết phiếu nhập (FIN-04) =====
SELECT pg_temp.assert_eq('KC-01', 'Xác nhận hai phiếu nhập không tự sinh khoản chi', '0',
    (SELECT count(*)::text FROM operating_expenses WHERE stock_receipt_id IN (1, 2)));
SELECT pg_temp.expect('KC-02', 'Khoản chi nguyên liệu tiền mặt từ quầy 30.000đ, liên kết phiếu 1', 'OK',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (1, DATE '2026-10-19', 'INGREDIENT', 'Trả tiền thịt bò', 30000, 'CASH', 'COUNTER', 1, 3, now(), now(), 'rk-exp-1', 'RECORDED')$q$);
SELECT pg_temp.expect('KC-03', 'Khoản chi thứ hai còn hiệu lực cho cùng phiếu 1 bị chặn', '23505 ux_operating_expenses_stock_receipt',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'INGREDIENT', 'Trả thêm đợt 2', 20000, 'CASH', 'COUNTER', 1, 3, now(), now(), 'rk-exp-9', 'RECORDED')$q$);
SELECT pg_temp.expect('KC-04', 'Hủy khoản chi thiếu lý do, người hủy, thời điểm bị chặn', '23514 ck_operating_expenses_voided',
    $q$UPDATE operating_expenses SET status = 'VOIDED' WHERE id = 1$q$);
SELECT pg_temp.expect('KC-05', 'Manager hủy khoản chi ghi sai (VOIDED, có lý do)', 'OK',
    $q$UPDATE operating_expenses SET status = 'VOIDED', void_reason = 'ghi sai số tiền', voided_by_id = 3, voided_at = now() WHERE id = 1$q$);
SELECT pg_temp.expect('KC-06', 'Ghi lại khoản chi đúng 32.000đ cho phiếu 1 sau khi đã hủy khoản cũ', 'OK',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (3, DATE '2026-10-19', 'INGREDIENT', 'Trả tiền thịt bò (ghi lại)', 32000, 'CASH', 'COUNTER', 1, 3, now(), now(), 'rk-exp-3', 'RECORDED')$q$);
SELECT pg_temp.assert_eq('KC-07', 'Lịch sử phiếu 1 giữ cả khoản đã hủy: một RECORDED, một VOIDED', 'RECORDED:1;VOIDED:1',
    (SELECT string_agg(status || ':' || n, ';' ORDER BY status)
       FROM (SELECT status, count(*) AS n FROM operating_expenses WHERE stock_receipt_id = 1 GROUP BY status) x));
SELECT pg_temp.expect('KC-08', 'Đưa khoản chi đã hủy về RECORDED khi phiếu đã có khoản còn hiệu lực bị chặn', '23505 ux_operating_expenses_stock_receipt',
    $q$UPDATE operating_expenses SET status = 'RECORDED', void_reason = NULL, voided_by_id = NULL, voided_at = NULL WHERE id = 1$q$);
SELECT pg_temp.expect('KC-09', 'Phiếu 2 có khoản chi riêng (chuyển khoản)', 'OK',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (4, DATE '2026-10-19', 'INGREDIENT', 'Trả phiếu 2', 500000, 'TRANSFER', 'OTHER', 2, 3, now(), now(), 'rk-exp-4', 'RECORDED')$q$);
SELECT pg_temp.expect('KC-10', 'Khoản chi loại OTHER gắn phiếu nhập bị chặn', '23514 ck_operating_expenses_receipt_ingredient',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'OTHER', 'x', 100, 'CASH', 'COUNTER', 2, 3, now(), now(), 'rk-exp-9', 'RECORDED')$q$);

-- ===== M5: khoản chi khác, đối soát tiền mặt =====
SELECT pg_temp.expect('M5-01', 'Khoản chi vận chuyển chuyển khoản 150.000đ, không liên kết phiếu', 'OK',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (2, DATE '2026-10-19', 'TRANSPORT', 'Phí vận chuyển', 150000, 'TRANSFER', 'OTHER', 3, now(), now(), 'rk-exp-2', 'RECORDED')$q$);
SELECT pg_temp.expect('M5-02', 'Gửi lặp cùng request_key không tạo khoản chi thứ hai', '23505 uq_operating_expenses_request_key',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'TRANSPORT', 'Phí vận chuyển', 150000, 'TRANSFER', 'OTHER', 3, now(), now(), 'rk-exp-2', 'RECORDED')$q$);
SELECT pg_temp.expect('M5-03', 'Chi từ quầy bằng chuyển khoản bị chặn', '23514 ck_operating_expenses_counter_cash',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'SUPPLIES', 'x', 100, 'TRANSFER', 'COUNTER', 3, now(), now(), 'rk-exp-9', 'RECORDED')$q$);
SELECT pg_temp.expect('M5-04', 'Thời điểm đã chi sau thời điểm ghi (chưa thực trả) bị chặn', '23514 ck_operating_expenses_paid_at',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'SUPPLIES', 'x', 100, 'CASH', 'OTHER', 3, now() + interval '1 hour', now(), 'rk-exp-9', 'RECORDED')$q$);
SELECT pg_temp.assert_eq('M5-05', 'cash_sales: tiền mặt PAID theo ngày kinh doanh, số thực giữ (không phải số khách đưa)', '140000.00',
    (SELECT COALESCE(SUM(amount), 0)::text FROM payments WHERE business_date = DATE '2026-10-19' AND method = 'CASH'));
SELECT pg_temp.assert_eq('M5-06', 'cash_expenses: chỉ khoản CASH, COUNTER, RECORDED (bỏ khoản đã hủy và chuyển khoản)', '32000.00',
    (SELECT COALESCE(SUM(amount), 0)::text FROM operating_expenses
      WHERE business_date = DATE '2026-10-19' AND payment_method = 'CASH' AND fund_source = 'COUNTER' AND status = 'RECORDED'));
SELECT pg_temp.expect('M5-07', 'Tiền dự kiến sai công thức bị chặn', '23514 ck_daily_cash_reconciliations_expected_cash',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reconciled_by_id, reconciled_at)
       VALUES (9, DATE '2026-10-19', 1, 'CURRENT', 500000, 140000, 32000, 999, 999, 0, 2, now())$q$);
SELECT pg_temp.expect('M5-08', 'Chênh lệch khác 0 thiếu lý do bị chặn', '23514 ck_daily_cash_reconciliations_reason',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reconciled_by_id, reconciled_at)
       VALUES (9, DATE '2026-10-19', 1, 'CURRENT', 500000, 140000, 32000, 608000, 600000, -8000, 2, now())$q$);
SELECT pg_temp.expect('M5-09', 'Doanh thu tiền mặt chụp lại âm bị chặn', '23514 ck_daily_cash_reconciliations_cash_sales',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reconciled_by_id, reconciled_at)
       VALUES (9, DATE '2026-10-22', 1, 'CURRENT', 500000, -1, 0, 499999, 499999, 0, 2, now())$q$);
SELECT pg_temp.expect('M5-10', 'Đối soát phiên bản 1 đúng công thức, có lý do chênh lệch', 'OK',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reason, reconciled_by_id, reconciled_at)
       VALUES (1, DATE '2026-10-19', 1, 'CURRENT', 500000, 140000, 32000, 608000, 600000, -8000, 'thiếu tiền lẻ', 2, now())$q$);
SELECT pg_temp.expect('M5-11', 'Hai bản CURRENT cùng ngày bị chặn', '23505 ux_daily_cash_reconciliations_business_date',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, revision_reason, reconciled_by_id, reconciled_at)
       VALUES (2, DATE '2026-10-19', 2, 'CURRENT', 500000, 140000, 32000, 608000, 608000, 0, 'đếm lại', 2, now())$q$);
SELECT pg_temp.expect('M5-12', 'Sửa số liệu bản đối soát đã lưu bị trigger chặn', 'P0001',
    $q$UPDATE daily_cash_reconciliations SET actual_cash = 608000, difference = 0 WHERE id = 1$q$);
SELECT pg_temp.expect('M5-13', 'Xóa bản đối soát bị trigger chặn', 'P0001',
    $q$DELETE FROM daily_cash_reconciliations WHERE id = 1$q$);
SELECT pg_temp.expect('M5-14', 'Chuyển phiên bản 1 sang SUPERSEDED được', 'OK',
    $q$UPDATE daily_cash_reconciliations SET status = 'SUPERSEDED' WHERE id = 1$q$);
SELECT pg_temp.expect('M5-15', 'Phiên bản 2 thiếu lý do đối soát lại bị chặn', '23514 ck_daily_cash_reconciliations_revision_reason',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reconciled_by_id, reconciled_at)
       VALUES (2, DATE '2026-10-19', 2, 'CURRENT', 500000, 140000, 32000, 608000, 608000, 0, 2, now())$q$);
SELECT pg_temp.expect('M5-16', 'Phiên bản 2 có lý do đối soát lại', 'OK',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, revision_reason, reconciled_by_id, reconciled_at)
       VALUES (2, DATE '2026-10-19', 2, 'CURRENT', 500000, 140000, 32000, 608000, 608000, 0, 'đếm lại', 2, now())$q$);
SELECT pg_temp.expect('M5-17', 'Đưa bản SUPERSEDED về CURRENT bị trigger chặn', 'P0001',
    $q$UPDATE daily_cash_reconciliations SET status = 'CURRENT' WHERE id = 1$q$);

-- ===== ST: trạng thái, phương thức, loại ngoài tập giá trị (mỗi cột một ca; phân hệ 1 ở test riêng) =====
SELECT pg_temp.expect('ST-01', 'daily_menus.status ARCHIVED', '23514 ck_daily_menus_status',
    $q$INSERT INTO daily_menus (id, business_date, status) VALUES (9, DATE '2026-10-20', 'ARCHIVED')$q$);
SELECT pg_temp.expect('ST-02', 'dining_tables.status RESERVED (đặt bàn ngoài Core)', '23514 ck_dining_tables_status',
    $q$UPDATE dining_tables SET status = 'RESERVED' WHERE id = 1$q$);
SELECT pg_temp.expect('ST-03', 'dining_tables.status để trống', '23502',
    $q$INSERT INTO dining_tables (id, code, capacity, status) VALUES (9, 'B09', 4, NULL)$q$);
SELECT pg_temp.expect('ST-04', 'table_sessions.status TRANSFERRED (chuyển bàn ngoài Core)', '23514 ck_table_sessions_status',
    $q$UPDATE table_sessions SET status = 'TRANSFERRED' WHERE id = 1$q$);
SELECT pg_temp.expect('ST-05', 'orders.status MERGED (gộp bàn ngoài Core)', '23514 ck_orders_status',
    $q$UPDATE orders SET status = 'MERGED' WHERE id = 1$q$);
SELECT pg_temp.expect('ST-06', 'order_items.status SERVED', '23514 ck_order_items_status',
    $q$UPDATE order_items SET status = 'SERVED' WHERE id = 1$q$);
SELECT pg_temp.expect('ST-07', 'order_item_events.to_status SERVED', '23514 ck_order_item_events_to_status',
    $q$INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status, kitchen_confirmed, created_at) VALUES (1, 1, 'READY', 'SERVED', FALSE, now())$q$);
SELECT pg_temp.expect('ST-08', 'order_item_events.from_status chữ thường "ready" (phân biệt hoa thường)', '23514 ck_order_item_events_from_status',
    $q$INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status, kitchen_confirmed, created_at) VALUES (1, 1, 'ready', 'READY', FALSE, now())$q$);
SELECT pg_temp.expect('ST-09', 'Lịch sử dòng món hợp lệ COOKING → READY', 'OK',
    $q$INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status, kitchen_confirmed, created_at) VALUES (1, 1, 'COOKING', 'READY', FALSE, now())$q$);
SELECT pg_temp.expect('ST-10', 'invoices.status CANCELLED', '23514 ck_invoices_status',
    $q$UPDATE invoices SET status = 'CANCELLED' WHERE id = 2$q$);
SELECT pg_temp.expect('ST-11', 'payment_requests.method CARD', '23514 ck_payment_requests_method',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status) VALUES (9, 2, 1, 50000, 'CARD', 'REQUESTED')$q$);
SELECT pg_temp.expect('ST-12', 'payment_requests.status REFUNDED', '23514 ck_payment_requests_status',
    $q$INSERT INTO payment_requests (id, invoice_id, waiter_id, amount_due, method, status) VALUES (9, 2, 1, 50000, 'CASH', 'REFUNDED')$q$);
SELECT pg_temp.expect('ST-13', 'payments.method CARD', '23514 ck_payments_method',
    $q$INSERT INTO payments (id, payment_request_id, amount, method, paid_at, business_date) VALUES (9, 5, 50000, 'CARD', now(), DATE '2026-10-19')$q$);
SELECT pg_temp.expect('ST-14', 'payment_webhook_events.processing_result DUPLICATE', '23514 ck_payment_webhook_events_processing_result',
    $q$INSERT INTO payment_webhook_events (id, provider_transaction_code, amount, processing_result, received_at) VALUES (190, 'SEPAY-ST', 1, 'DUPLICATE', now())$q$);
SELECT pg_temp.expect('ST-15', 'stock_receipts.status CANCELLED', '23514 ck_stock_receipts_status',
    $q$INSERT INTO stock_receipts (id, supplier_name, status, created_at) VALUES (9, 'x', 'CANCELLED', now())$q$);
SELECT pg_temp.expect('ST-16', 'stock_movements.movement_type TRANSFER', '23514 ck_stock_movements_movement_type',
    $q$INSERT INTO stock_movements (inventory_batch_id, movement_type, quantity_delta, balance_after, created_at) VALUES (1, 'TRANSFER', 10, 5010, now())$q$);
SELECT pg_temp.expect('ST-17', 'stock_reservations.status EXPIRED', '23514 ck_stock_reservations_status',
    $q$INSERT INTO stock_reservations (order_item_id, inventory_batch_id, quantity, status, created_at) VALUES (4, 14, 10, 'EXPIRED', now())$q$);
SELECT pg_temp.expect('ST-18', 'stock_reservations.release_reason OTHER', '23514 ck_stock_reservations_release_reason',
    $q$INSERT INTO stock_reservations (order_item_id, inventory_batch_id, quantity, status, release_reason, created_at, closed_at) VALUES (4, 14, 10, 'RELEASED', 'OTHER', now(), now())$q$);
SELECT pg_temp.expect('ST-19', 'stock_counts.status APPROVED', '23514 ck_stock_counts_status',
    $q$INSERT INTO stock_counts (id, status, created_at) VALUES (9, 'APPROVED', now())$q$);
SELECT pg_temp.expect('ST-20', 'operating_expenses.category SALARY (lương ngoài Core)', '23514 ck_operating_expenses_category',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'SALARY', 'x', 100, 'CASH', 'OTHER', 3, now(), now(), 'rk-st-1', 'RECORDED')$q$);
SELECT pg_temp.expect('ST-21', 'operating_expenses.payment_method CARD', '23514 ck_operating_expenses_payment_method',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'SUPPLIES', 'x', 100, 'CARD', 'OTHER', 3, now(), now(), 'rk-st-2', 'RECORDED')$q$);
SELECT pg_temp.expect('ST-22', 'operating_expenses.fund_source BANK', '23514 ck_operating_expenses_fund_source',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'SUPPLIES', 'x', 100, 'TRANSFER', 'BANK', 3, now(), now(), 'rk-st-3', 'RECORDED')$q$);
SELECT pg_temp.expect('ST-23', 'operating_expenses.status DELETED', '23514 ck_operating_expenses_status',
    $q$INSERT INTO operating_expenses (id, business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, created_at, request_key, status)
       VALUES (9, DATE '2026-10-19', 'SUPPLIES', 'x', 100, 'CASH', 'OTHER', 3, now(), now(), 'rk-st-4', 'DELETED')$q$);
SELECT pg_temp.expect('ST-24', 'daily_cash_reconciliations.status DRAFT', '23514 ck_daily_cash_reconciliations_status',
    $q$INSERT INTO daily_cash_reconciliations (id, business_date, version, status, opening_cash, cash_sales, cash_expenses, expected_cash, actual_cash, difference, reconciled_by_id, reconciled_at)
       VALUES (9, DATE '2026-10-21', 1, 'DRAFT', 500000, 0, 0, 500000, 500000, 0, 2, now())$q$);

-- ===== Q: truy vấn mẫu nguyên văn từ README phân hệ 5 (mục "Truy vấn mẫu theo đường truy xuất") =====
\set tu_ngay '''2026-10-19'''
\set den_ngay '''2026-10-19'''
\set ngay '''2026-10-19'''
SELECT pg_temp.assert_eq('Q-01', 'Báo cáo B: kế hoạch, thực bán (READY), đang xử lý theo món', '2026-10-19|Cơm rang|20|3|0;2026-10-19|Phở bò|30|2|1',
    (SELECT string_agg(r.business_date || '|' || r.mon || '|' || r.ke_hoach || '|' || r.thuc_ban || '|' || r.dang_xu_ly, ';' ORDER BY r.rn)
       FROM (SELECT row_number() OVER () AS rn, q.* FROM (
SELECT dm.business_date, mi.name AS mon, dmi.quota_total AS ke_hoach,
       COALESCE(SUM(oi.quantity) FILTER (WHERE oi.status = 'READY'), 0) AS thuc_ban,
       COALESCE(SUM(oi.quantity) FILTER (WHERE oi.status IN ('ACCEPTED', 'IN_KITCHEN', 'COOKING')), 0) AS dang_xu_ly
FROM daily_menu_items dmi
JOIN daily_menus dm ON dm.id = dmi.daily_menu_id
JOIN menu_items mi ON mi.id = dmi.menu_item_id
LEFT JOIN order_items oi ON oi.daily_menu_item_id = dmi.id
WHERE dm.business_date BETWEEN :tu_ngay AND :den_ngay
GROUP BY dm.business_date, mi.name, dmi.quota_total
ORDER BY dm.business_date, mi.name
       ) q) r));
CREATE TEMP TABLE report_d AS
SELECT row_number() OVER () AS rn, q.* FROM (
SELECT mi.name AS mon, SUM(oi.quantity) AS so_luong, SUM(oi.quantity * oi.unit_price) AS doanh_thu,
       ROUND(100 * SUM(oi.quantity * oi.unit_price) / SUM(SUM(oi.quantity * oi.unit_price)) OVER (), 1) AS ty_trong
FROM payments p
JOIN payment_requests pr ON pr.id = p.payment_request_id
JOIN invoices i ON i.id = pr.invoice_id
JOIN order_batches ob ON ob.order_id = i.order_id
JOIN order_items oi ON oi.order_batch_id = ob.id
JOIN daily_menu_items dmi ON dmi.id = oi.daily_menu_item_id
JOIN menu_items mi ON mi.id = dmi.menu_item_id
WHERE p.business_date BETWEEN :tu_ngay AND :den_ngay
  AND oi.status NOT IN ('CANCELLED', 'REJECTED')
GROUP BY mi.name
ORDER BY doanh_thu DESC, mi.name
) q;
SELECT pg_temp.assert_eq('Q-02', 'Báo cáo D: doanh thu theo món, tỷ trọng', 'Cơm rang|3|120000.00|54.5;Phở bò|2|100000.00|45.5',
    (SELECT string_agg(mon || '|' || so_luong || '|' || doanh_thu || '|' || ty_trong, ';' ORDER BY rn) FROM report_d));
SELECT pg_temp.assert_eq('Q-03', 'Tổng báo cáo D bằng tổng đã thu của báo cáo A (FIN-19)', '220000.00',
    (SELECT SUM(doanh_thu)::text FROM report_d));
SELECT pg_temp.assert_eq('Q-04', 'Tổng đã thu (báo cáo A) cùng ngày', '220000.00',
    (SELECT SUM(amount)::text FROM payments WHERE business_date = DATE '2026-10-19'));
SELECT pg_temp.assert_eq('Q-05', 'Truy xuất khoản thanh toán về hóa đơn và món đã bán', '2026-10-19|CASH|1|Cơm rang|1|40000.00;2026-10-19|CASH|1|Phở bò|2|100000.00;2026-10-19|QR|3|Cơm rang|2|80000.00',
    (SELECT string_agg(r.business_date || '|' || r.method || '|' || r.invoice_id || '|' || r.mon || '|' || r.so_luong || '|' || r.thanh_tien, ';' ORDER BY r.rn)
       FROM (SELECT row_number() OVER () AS rn, q.* FROM (
SELECT p.business_date, p.method, i.id AS invoice_id, mi.name AS mon,
       SUM(oi.quantity) AS so_luong, SUM(oi.quantity * oi.unit_price) AS thanh_tien
FROM payments p
JOIN payment_requests pr ON pr.id = p.payment_request_id
JOIN invoices i ON i.id = pr.invoice_id
JOIN order_batches ob ON ob.order_id = i.order_id
JOIN order_items oi ON oi.order_batch_id = ob.id
JOIN daily_menu_items dmi ON dmi.id = oi.daily_menu_item_id
JOIN menu_items mi ON mi.id = dmi.menu_item_id
WHERE p.business_date = :ngay
  AND oi.status NOT IN ('CANCELLED', 'REJECTED')
GROUP BY p.business_date, p.method, i.id, mi.name
ORDER BY i.id, mi.name
       ) q) r));

-- ===== R: không xóa cứng dữ liệu đã phát sinh (ON DELETE RESTRICT) =====
SELECT pg_temp.expect('R-01', 'Xóa món đã có công thức và trong thực đơn ngày bị chặn', '23001|23503',
    $q$DELETE FROM menu_items WHERE id = 1$q$);
SELECT pg_temp.expect('R-02', 'Xóa nhân viên đã ghi khoản chi, xác nhận phiếu bị chặn', '23001|23503',
    $q$DELETE FROM employees WHERE id = 3$q$);
SELECT pg_temp.expect('R-03', 'Xóa dòng phiếu đã tạo lô bị chặn', '23001|23503',
    $q$DELETE FROM stock_receipt_items WHERE id = 2$q$);
SELECT pg_temp.expect('R-04', 'Xóa webhook đã xác nhận khoản thanh toán bị chặn', '23001|23503',
    $q$DELETE FROM payment_webhook_events WHERE id = 101$q$);
SELECT pg_temp.expect('R-05', 'Xóa phiếu nhập đã có khoản chi (kể cả khoản đã hủy) bị chặn', '23001|23503',
    $q$DELETE FROM stock_receipts WHERE id = 1$q$);

-- ===== Kết quả =====
SELECT CASE WHEN passed THEN 'PASS' ELSE 'FAIL' END AS result, case_no, descr, expected, actual
FROM test_results ORDER BY seq;

SELECT count(*) FILTER (WHERE passed) AS passed_count,
       count(*) FILTER (WHERE NOT passed) AS failed_count,
       count(*) AS total_count
FROM test_results;

-- @@CHECK@@
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM test_results WHERE NOT passed) THEN
        RAISE EXCEPTION 'Có ca kiểm thử thất bại, xem bảng kết quả ở trên';
    END IF;
END;
$$;

ROLLBACK;
