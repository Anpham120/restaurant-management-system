-- =====================================================================
-- RESTAURANT CORE — Kiểm thử schema v4.1 (bản chốt nghiệp vụ)
-- Chạy SAU 04-schema-v4.1.sql, trên database trống:
--     psql -v ON_ERROR_STOP=1 -d restaurant_core -f 05-test-v4.1.sql
-- Mỗi khối DO là một giao dịch riêng (giống psql -f). Mã ca theo ma trận L1 Master Spec; QĐ-n = quyết định
-- nghiệp vụ n; EXC/ABD/GRD/CON = ca bổ sung; FNC/FNC-EXP/FNC-FEFO = ca kiểm toán từng hàm (docs/07); ACL = phân quyền
-- role. Sai ở đâu → dừng với lỗi 'TEST FAIL: …'; chạy hết không lỗi = đạt.
-- Các ca cần HAI kết nối thật (đua khóa, deadlock, webhook song song) nằm ở 06-test-concurrency-v4.1.mjs.
-- =====================================================================

CREATE SCHEMA rc_test;
CREATE TABLE rc_test.ids (k TEXT PRIMARY KEY, v BIGINT NOT NULL);
-- đếm số kiểm tra đã chạy (in ở cuối file) để chắc chắn không có ca nào bị bỏ qua âm thầm
CREATE TABLE rc_test.stat (kind TEXT PRIMARY KEY, n INT NOT NULL DEFAULT 0);
INSERT INTO rc_test.stat VALUES ('ok', 0), ('fails', 0);

-- Hàm phụ để VOLATILE (mặc định): hàm STABLE có thể không thấy dữ liệu vừa ghi trong cùng giao dịch
CREATE FUNCTION rc_test.put(p_k TEXT, p_v BIGINT) RETURNS BIGINT LANGUAGE sql AS $$
    INSERT INTO rc_test.ids VALUES (p_k, p_v) ON CONFLICT (k) DO UPDATE SET v = EXCLUDED.v RETURNING v
$$;
CREATE FUNCTION rc_test.id(p_k TEXT) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT v INTO v_id FROM rc_test.ids WHERE k = p_k;
    IF v_id IS NULL THEN
        RAISE EXCEPTION 'TEST FAIL: chưa có id %', p_k;
    END IF;
    RETURN v_id;
END $$;
CREATE FUNCTION rc_test.ok(p_cond BOOLEAN, p_msg TEXT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF p_cond IS NOT TRUE THEN
        RAISE EXCEPTION 'TEST FAIL: %', p_msg;
    END IF;
    UPDATE rc_test.stat SET n = n + 1 WHERE kind = 'ok';
END $$;
-- Câu lệnh p_sql phải bị chặn với thông báo khớp p_pattern (và SQLSTATE = p_state nếu có); thay đổi của nó được rollback
CREATE FUNCTION rc_test.fails(p_sql TEXT, p_pattern TEXT, p_msg TEXT, p_state TEXT DEFAULT NULL) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        IF p_pattern IS NOT NULL AND SQLERRM !~* p_pattern THEN
            RAISE EXCEPTION 'TEST FAIL: % — lỗi khác dự kiến: %', p_msg, SQLERRM;
        END IF;
        IF p_state IS NOT NULL AND SQLSTATE <> p_state THEN
            RAISE EXCEPTION 'TEST FAIL: % — SQLSTATE % (dự kiến %): %', p_msg, SQLSTATE, p_state, SQLERRM;
        END IF;
        UPDATE rc_test.stat SET n = n + 1 WHERE kind = 'fails';
        RETURN;
    END;
    RAISE EXCEPTION 'TEST FAIL: % — lẽ ra phải bị chặn', p_msg;
END $$;
-- Như fails nhưng ép kiểm các constraint trigger hoãn (số dư ↔ sổ kho, trạng thái ↔ nhật ký) ngay trong lệnh
CREATE FUNCTION rc_test.fails_commit(p_do_body TEXT, p_pattern TEXT, p_msg TEXT, p_state TEXT DEFAULT NULL) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM rc_test.fails('DO $t$ BEGIN ' || p_do_body || ' SET CONSTRAINTS ALL IMMEDIATE; END $t$', p_pattern, p_msg, p_state);
END $$;

CREATE FUNCTION rc_test.emp(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$ SELECT id FROM employees WHERE employee_code = p $$;
CREATE FUNCTION rc_test.ing(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$ SELECT id FROM ingredients WHERE name = p $$;
CREATE FUNCTION rc_test.kt(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$ SELECT id FROM kitchen_ingredient_types WHERE name = p $$;
CREATE FUNCTION rc_test.tbl(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$ SELECT id FROM dining_tables WHERE code = p $$;
CREATE FUNCTION rc_test.dmi(p TEXT) RETURNS BIGINT LANGUAGE sql AS $$
    SELECT d.id FROM daily_menu_items d
      JOIN menu_items m ON m.id = d.menu_item_id
      JOIN daily_menus dm ON dm.id = d.daily_menu_id
     WHERE m.name = p AND dm.business_date = rc_business_date(now()) $$;
CREATE FUNCTION rc_test.raw(p_k TEXT) RETURNS NUMERIC LANGUAGE sql AS $$
    SELECT quantity_on_hand FROM inventory_batches WHERE id = rc_test.id(p_k) $$;
CREATE FUNCTION rc_test.kb(p_k TEXT) RETURNS NUMERIC LANGUAGE sql AS $$
    SELECT quantity_on_hand FROM kitchen_batches WHERE id = rc_test.id(p_k) $$;
CREATE FUNCTION rc_test.kr(p_k TEXT) RETURNS NUMERIC LANGUAGE sql AS $$
    SELECT reserved_quantity FROM kitchen_batches WHERE id = rc_test.id(p_k) $$;
CREATE FUNCTION rc_test.items(p_dmi BIGINT, p_qty INT) RETURNS JSONB LANGUAGE sql AS $$
    SELECT jsonb_build_array(jsonb_build_object('daily_menu_item_id', p_dmi, 'quantity', p_qty)) $$;
-- Mở bàn p_code (tạo nếu chưa có) bằng WAITER, trả về id đơn
CREATE FUNCTION rc_test.open(p_code TEXT) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_t BIGINT;
    v_s BIGINT;
BEGIN
    INSERT INTO dining_tables (code, capacity) VALUES (p_code, 4) ON CONFLICT (code) DO NOTHING;
    SELECT id INTO v_t FROM dining_tables WHERE code = p_code;
    v_s := rc_open_session(v_t, rc_test.emp('W1'));
    RETURN (SELECT id FROM orders WHERE table_session_id = v_s);
END $$;
-- Gửi một đợt 1 dòng, trả về id dòng món
CREATE FUNCTION rc_test.send(p_order BIGINT, p_key TEXT, p_dish TEXT, p_qty INT) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_b BIGINT;
BEGIN
    v_b := rc_submit_batch(p_order, rc_test.emp('W1'), p_key, rc_test.items(rc_test.dmi(p_dish), p_qty));
    RETURN (SELECT id FROM order_items WHERE order_batch_id = v_b ORDER BY id LIMIT 1);
END $$;
-- Đưa một dòng món qua nhận → nấu → xong → phục vụ
CREATE FUNCTION rc_test.serve(p_item BIGINT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF rc_accept_item(p_item, rc_test.emp('K1')) <> 'ACCEPTED' THEN
        RAISE EXCEPTION 'TEST FAIL: dòng món % không được nhận', p_item;
    END IF;
    PERFORM rc_start_cooking(p_item, rc_test.emp('K1'));
    PERFORM rc_finish_dish(p_item, rc_test.emp('K1'));
    PERFORM rc_serve_item(p_item, rc_test.emp('W1'));
END $$;
CREATE FUNCTION rc_test.state(p_item BIGINT) RETURNS TEXT LANGUAGE sql AS $$ SELECT status FROM order_items WHERE id = p_item $$;

-- ---------------------------------------------------------------------
-- Dữ liệu nền: nhân sự + tài khoản + vai trò, danh mục, công thức, thực đơn hôm nay, bàn
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_dept  BIGINT;
    v_main  BIGINT;
    v_drink BIGINT;
    v_bo    BIGINT;
    v_coca  BIGINT;
    v_r     BIGINT;
    v_menu  BIGINT;
BEGIN
    INSERT INTO departments (department_name) VALUES ('Vận hành') RETURNING id INTO v_dept;
    INSERT INTO employees (employee_code, full_name, department_id) VALUES
        ('M1', 'Quản lý', v_dept), ('W1', 'Phục vụ', v_dept), ('K1', 'Bếp', v_dept), ('C1', 'Thu ngân', v_dept);
    -- schema đã seed 4 vai trò; mỗi nhân viên một tài khoản mang đúng vai trò
    INSERT INTO accounts (employee_id, username, password_hash)
    SELECT id, lower(employee_code), 'bcrypt-hash' FROM employees;
    INSERT INTO account_roles (account_id, role_id)
    SELECT a.id, r.id FROM accounts a JOIN employees e ON e.id = a.employee_id
      JOIN roles r ON r.role_code = CASE e.employee_code WHEN 'M1' THEN 'MANAGER' WHEN 'W1' THEN 'WAITER'
                                                         WHEN 'K1' THEN 'KITCHEN' ELSE 'CASHIER' END;
    INSERT INTO permissions (permission_code, description) VALUES
        ('ORDER_SUBMIT', 'Gửi món'), ('PAYMENT_CASH', 'Thu tiền mặt');
    INSERT INTO dining_tables (code, capacity) VALUES
        ('B01', 4), ('B02', 4), ('B03', 2), ('B04', 4), ('B05', 4), ('B06', 6);
    INSERT INTO ingredients (name, base_unit, min_quantity) VALUES
        ('Thịt bò A', 'kg', 2), ('Hành tây', 'kg', 1), ('Coca lon', 'lon', 24);
    INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type, shelf_life_hours) VALUES
        (rc_test.ing('Thịt bò A'), 'Bò nguyên miếng', 'DIRECT', 24),
        (rc_test.ing('Thịt bò A'), 'Bò thái', 'PREPARED', 48),
        (rc_test.ing('Thịt bò A'), 'Xương bò', 'PREPARED', 48),
        (rc_test.ing('Hành tây'), 'Hành tây', 'DIRECT', NULL),
        (rc_test.ing('Coca lon'), 'Coca lon', 'DIRECT', NULL);
    INSERT INTO menu_categories (name, sort_order) VALUES ('Món chính', 1) RETURNING id INTO v_main;
    INSERT INTO menu_categories (name, sort_order) VALUES ('Đồ uống', 2) RETURNING id INTO v_drink;
    PERFORM rc_test.put('CAT_MAIN', v_main);
    INSERT INTO menu_items (menu_category_id, name, price) VALUES (v_main, 'Bò lúc lắc', 50000) RETURNING id INTO v_bo;
    INSERT INTO menu_items (menu_category_id, name, price) VALUES (v_drink, 'Coca', 15000) RETURNING id INTO v_coca;
    INSERT INTO recipes (menu_item_id, version, is_current) VALUES (v_bo, 1, true) RETURNING id INTO v_r;
    INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES
        (v_r, rc_test.kt('Bò thái'), 0.2), (v_r, rc_test.kt('Hành tây'), 0.05);
    PERFORM rc_test.put('RECIPE_BO', v_r);
    INSERT INTO recipes (menu_item_id, version, is_current) VALUES (v_coca, 1, true) RETURNING id INTO v_r;
    INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES
        (v_r, rc_test.kt('Coca lon'), 1);
    PERFORM rc_test.put('RECIPE_COCA', v_r);
    INSERT INTO daily_menus (business_date, status) VALUES (rc_business_date(now()), 'DRAFT') RETURNING id INTO v_menu;
    PERFORM rc_test.put('MENU_TODAY', v_menu);
    INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) VALUES
        (v_menu, v_bo, rc_test.id('RECIPE_BO'), 3), (v_menu, v_coca, rc_test.id('RECIPE_COCA'), 50);
    UPDATE daily_menus SET status = 'PUBLISHED', published_at = now() WHERE id = v_menu;
END $$;

-- ---------------------------------------------------------------------
-- HR-01: một tài khoản nhiều vai trò; trùng cặp tài khoản–vai trò bị chặn
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_acc BIGINT;
BEGIN
    SELECT id INTO v_acc FROM accounts WHERE username = 'm1';
    INSERT INTO account_roles (account_id, role_id) SELECT v_acc, id FROM roles WHERE role_code = 'CASHIER';
    PERFORM rc_test.ok((SELECT count(*) FROM account_roles WHERE account_id = v_acc) = 2, 'HR-01 gán 2 vai trò');
    PERFORM rc_test.fails(format('INSERT INTO account_roles (account_id, role_id) SELECT %s, id FROM roles WHERE role_code = ''MANAGER''', v_acc),
                          'ux_account_role', 'HR-01 trùng cặp account–role', '23505');
    PERFORM rc_test.fails('INSERT INTO roles (role_code, role_name) VALUES (''MANAGER'', ''Trùng'')', 'roles_role_code_key', 'HR-01 vai trò trùng mã', '23505');
    -- rc_require_role: đúng vai trò, sai vai trò, nhân viên nghỉ việc, tài khoản bị khóa
    PERFORM rc_require_role(rc_test.emp('M1'), 'MANAGER');
    PERFORM rc_test.fails(format('SELECT rc_require_role(%s, ''MANAGER'')', rc_test.emp('K1')), 'không có vai trò', 'RBAC bếp không phải Manager', 'RC403');
    UPDATE accounts SET account_status = 'LOCKED' WHERE username = 'm1';
    PERFORM rc_test.fails(format('SELECT rc_require_role(%s, ''MANAGER'')', rc_test.emp('M1')), 'không có vai trò', 'RBAC tài khoản bị khóa', 'RC403');
    UPDATE accounts SET account_status = 'ACTIVE' WHERE username = 'm1';
    UPDATE employees SET work_status = 'LEFT', end_date = CURRENT_DATE WHERE employee_code = 'M1';
    PERFORM rc_test.fails(format('SELECT rc_require_role(%s, ''MANAGER'')', rc_test.emp('M1')), 'không có vai trò', 'RBAC nhân viên đã nghỉ việc', 'RC403');
    UPDATE employees SET work_status = 'ACTIVE', end_date = NULL WHERE employee_code = 'M1';
    PERFORM rc_require_role(rc_test.emp('M1'), 'MANAGER');
END $$;

-- ---------------------------------------------------------------------
-- HR-04*, HR-02, DB-01..03: chống trùng ca; chấm công 1 lần; điều chỉnh giữ lịch sử
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_day   DATE := rc_business_date(now());
    v_shift BIGINT;
    v_sa    BIGINT;
    v_att   BIGINT;
BEGIN
    INSERT INTO shifts (shift_name, start_time, end_time, break_minutes) VALUES ('Sáng', '08:00', '14:00', 30)
    RETURNING id INTO v_shift;
    INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at, scheduled_break_minutes)
    VALUES (rc_test.emp('W1'), v_shift, v_day, v_day + TIME '08:00', v_day + TIME '14:00', 30) RETURNING id INTO v_sa;
    PERFORM rc_test.fails(format('INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at, scheduled_break_minutes) VALUES (%s, %s, %L, %L, %L, 0)',
                                 rc_test.emp('W1'), v_shift, v_day, v_day + TIME '12:00', v_day + TIME '18:00'),
                          'ex_shift_assignment_no_overlap', 'HR-04 hai ca chồng giờ');
    INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id, confirmed_at)
    VALUES (v_sa, 'ABSENT', 0, rc_test.emp('M1'), now()) RETURNING id INTO v_att;
    UPDATE shift_assignments SET status = 'COMPLETED' WHERE id = v_sa;
    PERFORM rc_test.fails(format('INSERT INTO attendances (shift_assignment_id, attendance_status) VALUES (%s, ''ABSENT'')', v_sa),
                          'duplicate', 'HR-02 chấm công lần 2', '23505');
    INSERT INTO attendance_adjustments (attendance_id, old_status, new_status, new_start_at, new_end_at,
                                        old_worked_minutes, new_worked_minutes, reason, adjusted_by_id)
    VALUES (v_att, 'ABSENT', 'PRESENT', v_day + TIME '08:05', v_day + TIME '14:00', 0, 325, 'Quên quẹt thẻ', rc_test.emp('M1'));
    UPDATE attendances SET attendance_status = 'PRESENT', actual_start_at = v_day + TIME '08:05',
                           actual_end_at = v_day + TIME '14:00', worked_minutes = 325
     WHERE id = v_att;
    PERFORM rc_test.fails(format('UPDATE attendance_adjustments SET reason = ''sửa'' WHERE attendance_id = %s', v_att),
                          'chỉ-thêm', 'HR-02 nhật ký điều chỉnh bất biến');
    PERFORM rc_test.fails(format('UPDATE attendances SET attendance_status = ''ADJUSTED'' WHERE id = %s', v_att),
                          'ck_attendances_status', 'DB-01 không còn trạng thái ADJUSTED', '23514');
    PERFORM rc_test.fails(format('UPDATE attendances SET worked_minutes = 999 WHERE id = %s', v_att),
                          'ck_attendance_worked_le_span', 'DB-01 phút công không vượt khoảng có mặt', '23514');
    PERFORM rc_test.fails(format('UPDATE employees SET work_status = ''LEFT'' WHERE id = %s', rc_test.emp('C1')),
                          'ck_employee_left_end_date', 'DB-03 nghỉ việc phải có end_date', '23514');
END $$;

-- ---------------------------------------------------------------------
-- MENU-01, DB-05..08: công thức thuộc đúng món; công thức đã dùng bất biến; tạm dừng phải đủ thông tin
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_tomorrow BIGINT;
BEGIN
    INSERT INTO daily_menus (business_date) VALUES (rc_business_date(now()) + 1) RETURNING id INTO v_tomorrow;
    PERFORM rc_test.fails(format('INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) SELECT %s, m.id, %s, 10 FROM menu_items m WHERE m.name = ''Coca''',
                                 v_tomorrow, rc_test.id('RECIPE_BO')),
                          'fk_daily_menu_items_recipe_of_item', 'MENU-01 recipe không thuộc món', '23503');
    PERFORM rc_test.fails(format('UPDATE recipe_ingredients SET quantity_per_serving = 0.25 WHERE recipe_id = %s', rc_test.id('RECIPE_BO')),
                          'đã dùng', 'DB-06 sửa công thức đã dùng');
    PERFORM rc_test.fails(format('INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving) VALUES (%s, %s, 0)', rc_test.id('RECIPE_BO'), rc_test.kt('Xương bò')),
                          'ck_recipe_qty_positive|đã dùng', 'DB-05 định lượng 0');
    PERFORM rc_test.fails(format('UPDATE daily_menu_items SET is_paused = true WHERE id = %s', rc_test.dmi('Coca')),
                          'ck_daily_menu_pause_fields', 'DB-07 tạm dừng không lý do', '23514');
    PERFORM rc_test.fails('INSERT INTO menu_items (menu_category_id, name, price) SELECT menu_category_id, ''BÒ LÚC LẮC'', 1 FROM menu_items LIMIT 1',
                          'ux_menu_items_name', 'DB-08 trùng tên món (không phân biệt hoa thường)', '23505');
    -- thực đơn: không công bố khi rỗng; không quay lại DRAFT
    PERFORM rc_test.fails(format('UPDATE daily_menus SET status = ''PUBLISHED'', published_at = now() WHERE id = %s', v_tomorrow),
                          'chưa có món nào', 'thực đơn rỗng không công bố được');
    PERFORM rc_test.fails(format('UPDATE daily_menus SET status = ''DRAFT'', published_at = NULL WHERE id = %s', rc_test.id('MENU_TODAY')),
                          'không chuyển', 'thực đơn đã công bố không quay lại DRAFT');
END $$;

-- ---------------------------------------------------------------------
-- RAW-01: nhập 2 thời điểm, cùng nguyên liệu khác lô/hạn → 2 lô riêng; quy đổi đơn vị; bắt buộc hạn dùng
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_today DATE := rc_business_date(now());
    v_r     BIGINT;
BEGIN
    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('NCC Tươi Sống', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor,
                                     unit_price_per_input_unit, lot_code, expiry_date) VALUES
        (v_r, rc_test.ing('Thịt bò A'), 10, 'kg', 1, 250000, 'BO-L1', v_today + 5),
        (v_r, rc_test.ing('Hành tây'), 2, 'kg', 1, 20000, 'H-L1', v_today + 7),
        (v_r, rc_test.ing('Coca lon'), 1, 'thùng', 24, 192000, 'C-L1', v_today + 180);
    PERFORM rc_confirm_receipt(v_r, rc_test.emp('M1'));
    PERFORM rc_test.put('R1', v_r);
    PERFORM rc_test.fails(format('SELECT rc_confirm_receipt(%s, %s)', v_r, rc_test.emp('M1')), 'không phải DRAFT', 'RAW-01 xác nhận phiếu 2 lần', 'RC409');
    PERFORM rc_test.fails(format('INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit, lot_code, expiry_date) VALUES (%s, %s, 1, ''kg'', 1, 1, ''X'', %L)', v_r, rc_test.ing('Hành tây'), v_today + 9),
                          'dòng chi tiết bị khóa', 'GRD chứng từ đã CONFIRMED không thêm dòng', 'RC409');
    PERFORM rc_test.fails(format('UPDATE stock_receipts SET note = ''sửa'' WHERE id = %s', v_r), 'bất biến', 'GRD chứng từ đã CONFIRMED bất biến', 'RC409');

    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('Chợ đầu mối', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor,
                                     unit_price_per_input_unit, lot_code, expiry_date)
    VALUES (v_r, rc_test.ing('Hành tây'), 1000, 'g', 0.001, 25, 'H-L2', v_today + 2);
    PERFORM rc_confirm_receipt(v_r, rc_test.emp('M1'));

    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('Thiếu hạn', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit)
    VALUES (v_r, rc_test.ing('Coca lon'), 24, 'lon', 1, 8000);
    PERFORM rc_test.fails(format('SELECT rc_confirm_receipt(%s, %s)', v_r, rc_test.emp('M1')), 'expiry_date', 'RAW-01 thiếu hạn dùng', 'RC422');
    UPDATE stock_receipt_items SET expiry_date = v_today - 1 WHERE stock_receipt_id = v_r;
    PERFORM rc_test.fails(format('SELECT rc_confirm_receipt(%s, %s)', v_r, rc_test.emp('M1')), 'đã hết hạn', 'RAW-01 nhập lô quá hạn', 'RC422');
    UPDATE stock_receipts SET status = 'CANCELLED' WHERE id = v_r;

    PERFORM rc_test.put('LOT_BO', (SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = 'BO-L1'));
    PERFORM rc_test.put('LOT_H1', (SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = 'H-L1'));
    PERFORM rc_test.put('LOT_H2', (SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = 'H-L2'));
    PERFORM rc_test.put('LOT_COCA', (SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = 'C-L1'));
    PERFORM rc_test.ok((SELECT count(*) FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
                         WHERE s.ingredient_id = rc_test.ing('Hành tây')) = 2, 'RAW-01 hai lô hành riêng');
    PERFORM rc_test.ok(rc_test.raw('LOT_COCA') = 24, 'RAW-01 quy đổi 1 thùng × 24 = 24 lon');
    PERFORM rc_test.ok(rc_test.raw('LOT_H2') = 1, 'RAW-01 quy đổi 1000 g × 0,001 = 1 kg');
END $$;

-- ---------------------------------------------------------------------
-- RAW-02, ISSUE-01, RAW-03: xuất đúng FEFO; RAW giảm khi ISSUED, bếp chưa tăng; không xuất vượt tồn
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_i BIGINT;
BEGIN
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES
        (v_i, rc_test.id('LOT_BO'), 7, 'PREPARE'),
        (v_i, rc_test.id('LOT_H1'), 0.5, 'DIRECT'),      -- sai FEFO: lô H2 hạn sớm hơn vẫn còn hàng
        (v_i, rc_test.id('LOT_COCA'), 10, 'DIRECT');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'FEFO', 'RAW-02 xuất sai FEFO', 'RC409');
    UPDATE stock_issue_items SET inventory_batch_id = rc_test.id('LOT_H2')
     WHERE stock_issue_id = v_i AND inventory_batch_id = rc_test.id('LOT_H1');
    PERFORM rc_issue_stock(v_i, rc_test.emp('M1'));
    PERFORM rc_test.put('I1', v_i);
    PERFORM rc_test.ok(rc_test.raw('LOT_BO') = 3 AND rc_test.raw('LOT_H2') = 0.5 AND rc_test.raw('LOT_COCA') = 14,
                       'ISSUE-01 RAW giảm đúng lô');
    PERFORM rc_test.ok((SELECT SUM(wip_remaining_quantity) FROM stock_issue_items WHERE stock_issue_id = v_i) = 0
                       AND NOT EXISTS (SELECT 1 FROM kitchen_batches k JOIN stock_issue_items l ON l.id = k.direct_issue_item_id
                                        WHERE l.stock_issue_id = v_i),
                       'ISSUE-01 chưa nhận thì WIP/bếp chưa tăng');
    PERFORM rc_test.ok((SELECT SUM(l.quantity) FROM stock_issue_items l JOIN stock_issues s ON s.id = l.stock_issue_id
                         WHERE s.status = 'ISSUED') = 17.5, 'ISSUE-01 lượng đang chuyển suy ra từ phiếu');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'không phải DRAFT', 'ISSUE xuất lặp cùng phiếu', 'RC409');
    PERFORM rc_test.fails(format('UPDATE stock_issue_items SET quantity = 1 WHERE stock_issue_id = %s', v_i), 'dòng chi tiết bị khóa', 'GRD sửa dòng phiếu đã ISSUED', 'RC409');

    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    VALUES (v_i, rc_test.id('LOT_COCA'), 100, 'DIRECT');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'chỉ còn', 'RAW-03 xuất vượt tồn', 'RC409');
    PERFORM rc_cancel_issue(v_i, rc_test.emp('M1'), 'Lập sai số lượng');

    -- dòng DIRECT phải có loại bếp DIRECT; thêm nguyên liệu không có loại DIRECT
    INSERT INTO ingredients (name, base_unit, is_expiry_tracked) VALUES ('Gia vị X', 'g', false);
    INSERT INTO stock_receipts (created_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit)
    VALUES (v_i, rc_test.ing('Gia vị X'), 100, 'g', 1, 10);
    PERFORM rc_confirm_receipt(v_i, rc_test.emp('M1'));
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    SELECT v_i, b.id, 10, 'DIRECT' FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.ingredient_id = rc_test.ing('Gia vị X');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'loại bếp DIRECT', 'ISSUE dòng DIRECT chưa khai báo loại bếp', 'RC422');
    PERFORM rc_cancel_issue(v_i, rc_test.emp('M1'), 'Chưa cấu hình loại bếp');
END $$;

-- ---------------------------------------------------------------------
-- ISSUE-03, DIRECT-01: nhận một lần; dòng DIRECT sinh đúng 1 lô bếp, bỏ qua sơ chế
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_i BIGINT := rc_test.id('I1');
BEGIN
    PERFORM rc_receive_issue(v_i, rc_test.emp('K1'));
    PERFORM rc_test.fails(format('SELECT rc_receive_issue(%s, %s)', v_i, rc_test.emp('K1')), 'không phải ISSUED', 'ISSUE-03 nhận 2 lần', 'RC409');
    PERFORM rc_test.put('WIP_BO', (SELECT id FROM stock_issue_items WHERE stock_issue_id = v_i AND fulfillment_mode = 'PREPARE'));
    PERFORM rc_test.ok((SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = rc_test.id('WIP_BO')) = 7, 'ISSUE-03 WIP = 7');
    PERFORM rc_test.ok((SELECT count(*) FROM kitchen_batches k JOIN stock_issue_items l ON l.id = k.direct_issue_item_id
                         WHERE l.stock_issue_id = v_i) = 2, 'DIRECT-01 hai lô DIRECT');
    PERFORM rc_test.put('KB_HANH', (SELECT id FROM kitchen_batches WHERE kitchen_ingredient_type_id = rc_test.kt('Hành tây')));
    PERFORM rc_test.put('KB_COCA', (SELECT id FROM kitchen_batches WHERE kitchen_ingredient_type_id = rc_test.kt('Coca lon')));
END $$;

-- ---------------------------------------------------------------------
-- PREP-02, PREP-01, PREP-03: sơ chế 7 kg → 6,3 kg; hao hụt suy ra; chặn đầu ra sai/vượt WIP; xác nhận lặp
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_p BIGINT;
BEGIN
    INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, prepared_by_id, request_key)
    VALUES (rc_test.id('WIP_BO'), 1, rc_test.emp('K1'), 'prep-x') RETURNING id INTO v_p;
    INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity)
    VALUES (v_p, rc_test.kt('Bò thái'), 1.5);
    PERFORM rc_test.fails(format('SELECT rc_confirm_preparation(%s, %s)', v_p, rc_test.emp('K1')), 'không vượt đầu vào', 'PREP-02 đầu ra > đầu vào', 'RC422');
    UPDATE kitchen_preparation_outputs SET kitchen_ingredient_type_id = rc_test.kt('Hành tây'), output_quantity = 0.9
     WHERE kitchen_preparation_id = v_p;
    PERFORM rc_test.fails(format('SELECT rc_confirm_preparation(%s, %s)', v_p, rc_test.emp('K1')), 'cùng nguyên liệu gốc', 'PREP-02 đầu ra khác nguyên liệu', 'RC422');
    PERFORM rc_test.ok((SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = rc_test.id('WIP_BO')) = 7, 'PREP-02 dữ liệu không đổi');
    UPDATE kitchen_preparations SET status = 'CANCELLED' WHERE id = v_p;

    INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, loss_reason, prepared_by_id, request_key)
    VALUES (rc_test.id('WIP_BO'), 7, 'Gân, mỡ', rc_test.emp('K1'), 'prep-1') RETURNING id INTO v_p;
    INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity)
    VALUES (v_p, rc_test.kt('Bò thái'), 6.3);
    PERFORM rc_confirm_preparation(v_p, rc_test.emp('K1'));
    PERFORM rc_test.put('PREP1', v_p);
    PERFORM rc_test.put('KB_BO_THAI', (SELECT k.id FROM kitchen_batches k JOIN kitchen_preparation_outputs o ON o.id = k.preparation_output_id
                                        WHERE o.kitchen_preparation_id = v_p));
    PERFORM rc_test.ok((SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = rc_test.id('WIP_BO')) = 0, 'PREP-01 WIP −7');
    PERFORM rc_test.ok(rc_test.kb('KB_BO_THAI') = 6.3, 'PREP-01 bếp +6,3');
    PERFORM rc_test.ok((SELECT p.input_quantity - SUM(o.output_quantity) FROM kitchen_preparations p
                          JOIN kitchen_preparation_outputs o ON o.kitchen_preparation_id = p.id
                         WHERE p.id = v_p GROUP BY p.input_quantity) = 0.7, 'PREP-01 hao hụt 0,7 kg suy ra');
    PERFORM rc_test.ok((SELECT count(*) FROM stock_movements WHERE preparation_id = v_p) = 2, 'PREP-01 không ghi movement hao hụt');
    PERFORM rc_test.ok((SELECT k.usable_until = o.usable_until FROM kitchen_batches k
                          JOIN kitchen_preparation_outputs o ON o.id = k.preparation_output_id
                         WHERE k.id = rc_test.id('KB_BO_THAI')), 'DB-24 hạn lô bếp = hạn dòng đầu ra');
    -- xác nhận lặp không trừ WIP/tạo lô lần hai
    PERFORM rc_test.fails(format('SELECT rc_confirm_preparation(%s, %s)', v_p, rc_test.emp('K1')), 'không phải DRAFT', 'PREP lặp xác nhận phiếu sơ chế', 'RC409');
    PERFORM rc_test.fails(format('INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, prepared_by_id, request_key) VALUES (%s, 1, %s, ''prep-1'')', rc_test.id('WIP_BO'), rc_test.emp('K1')),
                          'request_key', 'PREP lặp request_key tạo phiếu', '23505');
    PERFORM rc_test.fails(format('UPDATE kitchen_preparation_outputs SET output_quantity = 1 WHERE kitchen_preparation_id = %s', v_p), 'dòng chi tiết bị khóa', 'GRD sửa đầu ra phiếu đã CONFIRMED', 'RC409');

    INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, prepared_by_id, request_key)
    VALUES (rc_test.id('WIP_BO'), 0.5, rc_test.emp('K1'), 'prep-2') RETURNING id INTO v_p;
    INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity)
    VALUES (v_p, rc_test.kt('Bò thái'), 0.4);
    PERFORM rc_test.fails(format('SELECT rc_confirm_preparation(%s, %s)', v_p, rc_test.emp('K1')), 'vượt WIP', 'PREP-03 sơ chế vượt WIP', 'RC409');
    UPDATE kitchen_preparations SET status = 'CANCELLED' WHERE id = v_p;
END $$;

-- ---------------------------------------------------------------------
-- TABLE-01, ORDER-01, 2.4: mở bàn; không mở trùng; gửi lặp cùng request_key không ghi trùng; món tạm dừng
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_s  BIGINT;
    v_o  BIGINT;
    v_b  BIGINT;
    v_b2 BIGINT;
BEGIN
    v_s := rc_open_session(rc_test.tbl('B01'), rc_test.emp('W1'));
    PERFORM rc_test.fails(format('SELECT rc_open_session(%s, %s)', rc_test.tbl('B01'), rc_test.emp('W1')), 'đang có khách', 'TABLE-01 mở trùng bàn', 'RC409');
    SELECT id INTO v_o FROM orders WHERE table_session_id = v_s;
    PERFORM rc_test.put('O_B01', v_o);
    UPDATE daily_menu_items SET is_paused = true, pause_reason = 'Hết đá', paused_by_id = rc_test.emp('K1'), paused_at = now()
     WHERE id = rc_test.dmi('Coca');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''b01-paused'', %L::jsonb)', v_o, rc_test.emp('W1'), rc_test.items(rc_test.dmi('Coca'), 1)),
                          'tạm dừng', '2.4 gửi món đang tạm dừng', 'RC409');
    UPDATE daily_menu_items SET is_paused = false, pause_reason = NULL, paused_by_id = NULL, paused_at = NULL
     WHERE id = rc_test.dmi('Coca');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''b01-bad'', %L::jsonb)', v_o, rc_test.emp('W1'), jsonb_build_array(jsonb_build_object('daily_menu_item_id', rc_test.dmi('Coca'), 'quantity', 0))),
                          'lớn hơn 0', 'ORDER số lượng 0', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''b01-bad2'', ''[{"daily_menu_item_id": 999999, "quantity": 1}]''::jsonb)', v_o, rc_test.emp('W1')),
                          'không có trong thực đơn', 'ORDER món không tồn tại', 'RC409');
    v_b := rc_submit_batch(v_o, rc_test.emp('W1'), 'b01-1', jsonb_build_array(
               jsonb_build_object('daily_menu_item_id', rc_test.dmi('Bò lúc lắc'), 'quantity', 1, 'note', 'Ít tiêu'),
               jsonb_build_object('daily_menu_item_id', rc_test.dmi('Coca'), 'quantity', 2)));
    v_b2 := rc_submit_batch(v_o, rc_test.emp('W1'), 'b01-1', '[]'::jsonb);
    PERFORM rc_test.ok(v_b = v_b2 AND (SELECT count(*) FROM order_items WHERE order_batch_id = v_b) = 2, 'ORDER-01 gửi lặp không ghi trùng');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''b01-1'', %L::jsonb)', v_o + 1000, rc_test.emp('W1'), rc_test.items(rc_test.dmi('Coca'), 1)),
                          'đã dùng cho đơn khác', 'ORDER-01 request_key dùng cho đơn khác', 'RC409');
    PERFORM rc_test.put('OI_B01_BO', (SELECT id FROM order_items WHERE order_batch_id = v_b AND item_name_snapshot = 'Bò lúc lắc'));
    PERFORM rc_test.put('OI_B01_COCA', (SELECT id FROM order_items WHERE order_batch_id = v_b AND item_name_snapshot = 'Coca'));
END $$;

-- ---------------------------------------------------------------------
-- ORDER-02: nhận món giữ nguyên liệu; hủy trước khi nấu → phần giữ về 0, tồn vật lý không tăng
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_qoh NUMERIC := rc_test.kb('KB_COCA');
BEGIN
    PERFORM rc_test.ok(rc_accept_item(rc_test.id('OI_B01_COCA'), rc_test.emp('K1')) = 'ACCEPTED', 'nhận Coca');
    PERFORM rc_test.ok(rc_test.kr('KB_COCA') = 2, 'ORDER-02 giữ 2 lon');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item(%s, %s, '''')', rc_test.id('OI_B01_COCA'), rc_test.emp('K1')), 'bắt buộc có lý do', 'ORDER-02 hủy không lý do', 'RC422');
    PERFORM rc_cancel_item(rc_test.id('OI_B01_COCA'), rc_test.emp('W1'), 'Khách đổi ý');
    PERFORM rc_test.ok(rc_test.kr('KB_COCA') = 0 AND rc_test.kb('KB_COCA') = v_qoh, 'ORDER-02 giữ về 0, tồn không tăng');
    PERFORM rc_test.ok((SELECT release_reason FROM stock_reservations WHERE order_item_id = rc_test.id('OI_B01_COCA')) = 'ITEM_CANCELLED',
                       'ORDER-02 lý do giải phóng');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM stock_movements WHERE order_item_id = rc_test.id('OI_B01_COCA')), 'ORDER-02 không có movement');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item(%s, %s, ''lần 2'')', rc_test.id('OI_B01_COCA'), rc_test.emp('W1')), 'không hủy được', 'ORDER-02 hủy lần hai không giải phóng thêm', 'RC409');
    PERFORM rc_test.ok(rc_test.kr('KB_COCA') = 0, 'ORDER-02 hủy lần hai không đổi số đang giữ');
    PERFORM rc_test.ok(rc_accept_item(rc_test.id('OI_B01_BO'), rc_test.emp('K1')) = 'ACCEPTED', 'nhận Bò lúc lắc');
    PERFORM rc_test.ok(rc_test.kr('KB_BO_THAI') = 0.2 AND rc_test.kr('KB_HANH') = 0.05, 'đặt giữ đúng công thức');
END $$;

-- ---------------------------------------------------------------------
-- ORDER-03: bắt đầu nấu 2 lần chỉ trừ tồn 1 lần
-- ---------------------------------------------------------------------
DO $$
BEGIN
    PERFORM rc_start_cooking(rc_test.id('OI_B01_BO'), rc_test.emp('K1'));
    PERFORM rc_test.fails(format('SELECT rc_start_cooking(%s, %s)', rc_test.id('OI_B01_BO'), rc_test.emp('K1')),
                          'không phải ACCEPTED', 'ORDER-03 nấu 2 lần', 'RC409');
    PERFORM rc_test.ok(rc_test.kb('KB_BO_THAI') = 6.1 AND rc_test.kr('KB_BO_THAI') = 0 AND rc_test.kb('KB_HANH') = 0.45,
                       'ORDER-03 trừ tồn đúng 1 lần');
    PERFORM rc_test.ok((SELECT count(*) FROM stock_movements WHERE order_item_id = rc_test.id('OI_B01_BO') AND movement_type = 'COOK') = 2,
                       'ORDER-03 mỗi nguyên liệu 1 dòng COOK');
    PERFORM rc_finish_dish(rc_test.id('OI_B01_BO'), rc_test.emp('K1'));
END $$;

-- ---------------------------------------------------------------------
-- INV-01, ORDER-04, INV-02, CASH-01: chốt đủ dòng SERVED; đóng băng khi chờ thu; snapshot giá; thu tiền mặt
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_b   BIGINT;
    v_inv BIGINT;
    v_add BIGINT;
BEGIN
    v_b := rc_submit_batch(rc_test.id('O_B01'), rc_test.emp('W1'), 'b01-2', rc_test.items(rc_test.dmi('Coca'), 1));
    SELECT id INTO v_add FROM order_items WHERE order_batch_id = v_b;
    PERFORM rc_test.fails(format('SELECT rc_checkout_invoice(%s, %s)', rc_test.id('O_B01'), rc_test.emp('C1')),
                          'chưa phục vụ xong', 'INV-01 chốt khi còn món chưa xong', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_reject_item(%s, %s, '' '')', v_add, rc_test.emp('K1')), 'bắt buộc có lý do', 'ORDER từ chối không lý do', 'RC422');
    PERFORM rc_reject_item(v_add, rc_test.emp('K1'), 'Hết đá lạnh');
    PERFORM rc_serve_item(rc_test.id('OI_B01_BO'), rc_test.emp('W1'));
    v_inv := rc_checkout_invoice(rc_test.id('O_B01'), rc_test.emp('C1'));
    PERFORM rc_test.put('INV_B01', v_inv);
    PERFORM rc_test.ok((SELECT total FROM invoices WHERE id = v_inv) = 50000
                       AND (SELECT count(*) FROM invoice_items WHERE invoice_id = v_inv) = 1, 'INV-01 chỉ tính dòng SERVED');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''b01-3'', %L::jsonb)', rc_test.id('O_B01'), rc_test.emp('W1'),
                                 rc_test.items(rc_test.dmi('Coca'), 1)),
                          'đang thanh toán', 'ORDER-04 gọi thêm khi PAYMENT_PENDING', 'RC409');
    PERFORM rc_test.fails(format('UPDATE invoice_items SET quantity = 2 WHERE invoice_id = %s', v_inv), 'bị khóa', 'ORDER-04 sửa dòng hóa đơn', 'RC409');
    UPDATE menu_items SET price = 55000 WHERE name = 'Bò lúc lắc';
    PERFORM rc_test.ok((SELECT unit_price FROM invoice_items WHERE invoice_id = v_inv) = 50000, 'INV-02 snapshot giá giữ nguyên');
    PERFORM rc_test.fails(format('SELECT rc_pay_cash(%s, %s, 40000)', v_inv, rc_test.emp('C1')), 'nhỏ hơn số phải thu', 'CASH-01 đưa thiếu tiền', 'RC422');
    PERFORM rc_pay_cash(v_inv, rc_test.emp('C1'), 60000);
    PERFORM rc_test.ok((SELECT change_amount FROM payment_requests WHERE invoice_id = v_inv) = 10000, 'CASH-01 tiền thối 10.000');
    PERFORM rc_test.ok((SELECT amount FROM payments WHERE invoice_id = v_inv) = 50000, 'CASH-01 payment = số phải thu');
    PERFORM rc_test.fails(format('SELECT rc_pay_cash(%s, %s, 60000)', v_inv, rc_test.emp('C1')), 'không chờ thanh toán', 'CASH-01 thu 2 lần', 'RC409');
    PERFORM rc_test.ok((SELECT status FROM dining_tables WHERE code = 'B01') = 'AVAILABLE', 'G10 trả bàn sau thanh toán');
    UPDATE menu_items SET price = 50000 WHERE name = 'Bò lúc lắc';
END $$;

-- ---------------------------------------------------------------------
-- TABLE-02, MENU-02: đóng phiên trống; vượt quota / thiếu tồn → REJECTED, không giữ dở dang
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_s    BIGINT;
    v_o    BIGINT;
    v_b    BIGINT;
    v_item BIGINT;
BEGIN
    v_s := rc_open_session(rc_test.tbl('B03'), rc_test.emp('W1'));
    PERFORM rc_close_empty_session(v_s, rc_test.emp('W1'));
    PERFORM rc_test.ok((SELECT status FROM orders WHERE table_session_id = v_s) = 'CANCELLED', 'TABLE-02 phiên trống → order CANCELLED');
    PERFORM rc_test.fails(format('SELECT rc_close_empty_session(%s, %s)', v_s, rc_test.emp('W1')), 'không mở', 'TABLE-02 đóng phiên đã đóng', 'RC409');

    v_s := rc_open_session(rc_test.tbl('B02'), rc_test.emp('W1'));
    SELECT id INTO v_o FROM orders WHERE table_session_id = v_s;
    -- quota 3, đã nhận 1 (B01): gọi 3 suất bị chặn sớm ở gửi món (kiểm mềm); gọi 2 + 2 đều qua được vì PENDING chưa chiếm suất
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''b02-soft'', %L::jsonb)', v_o, rc_test.emp('W1'), rc_test.items(rc_test.dmi('Bò lúc lắc'), 3)),
                          'không đủ suất: còn 2', 'MENU-02 gửi vượt quota bị chặn sớm', 'RC409');
    v_b := rc_submit_batch(v_o, rc_test.emp('W1'), 'b02-1', rc_test.items(rc_test.dmi('Bò lúc lắc'), 2));
    SELECT id INTO v_item FROM order_items WHERE order_batch_id = v_b;
    PERFORM rc_test.put('OI_B02_FIRST', v_item);
    v_b := rc_submit_batch(v_o, rc_test.emp('W1'), 'b02-1b', rc_test.items(rc_test.dmi('Bò lúc lắc'), 2));
    SELECT id INTO v_item FROM order_items WHERE order_batch_id = v_b;
    PERFORM rc_test.ok(rc_accept_item(rc_test.id('OI_B02_FIRST'), rc_test.emp('K1')) = 'ACCEPTED', 'MENU-02 dòng đầu nhận đủ suất còn lại');
    PERFORM rc_test.ok(rc_accept_item(v_item, rc_test.emp('K1')) = 'REJECTED', 'MENU-02 vượt quota bị từ chối khi nhận (kiểm cứng)');
    PERFORM rc_test.ok((SELECT reason FROM order_item_events WHERE order_item_id = v_item AND to_status = 'REJECTED') LIKE 'Vượt hạn mức%',
                       'MENU-02 có lý do');
    PERFORM rc_cancel_item(rc_test.id('OI_B02_FIRST'), rc_test.emp('W1'), 'Khách đổi ý');
    v_b := rc_submit_batch(v_o, rc_test.emp('W1'), 'b02-2', rc_test.items(rc_test.dmi('Coca'), 12));
    SELECT id INTO v_item FROM order_items WHERE order_batch_id = v_b;
    PERFORM rc_test.ok(rc_accept_item(v_item, rc_test.emp('K1')) = 'REJECTED', 'MENU-02 thiếu tồn bị từ chối');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM stock_reservations WHERE order_item_id = v_item) AND rc_test.kr('KB_COCA') = 0,
                       'MENU-02 không giữ dở dang');
    PERFORM rc_close_empty_session(v_s, rc_test.emp('W1'));
END $$;

-- ---------------------------------------------------------------------
-- TABLE-02, QR-01, QR-02, DB-12: còn hóa đơn thì không đóng trống; webhook sai/lặp/đến muộn
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_s    BIGINT;
    v_o    BIGINT;
    v_item BIGINT;
    v_inv  BIGINT;
    v_ref1 TEXT;
    v_ref2 TEXT;
BEGIN
    v_s := rc_open_session(rc_test.tbl('B04'), rc_test.emp('W1'));
    SELECT id INTO v_o FROM orders WHERE table_session_id = v_s;
    v_item := rc_test.send(v_o, 'b04-1', 'Coca', 1);
    PERFORM rc_test.serve(v_item);
    v_inv := rc_checkout_invoice(v_o, rc_test.emp('C1'));
    PERFORM rc_test.fails(format('SELECT rc_close_empty_session(%s, %s)', v_s, rc_test.emp('W1')), 'đã có hóa đơn',
                          'TABLE-02 không đóng phiên còn nghĩa vụ thanh toán', 'RC409');
    v_ref1 := rc_start_qr(v_inv, rc_test.emp('C1'));
    PERFORM rc_test.fails(format('SELECT rc_start_qr(%s, %s)', v_inv, rc_test.emp('C1')), 'đã có yêu cầu thanh toán đang mở', 'QR chỉ một yêu cầu đang mở', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_pay_cash(%s, %s, 20000)', v_inv, rc_test.emp('C1')), 'yêu cầu QR mở',
                          'không thu tiền mặt khi QR đang mở', 'RC409');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-001', v_ref1, 10000, '{"amount":10000}') = 'MISMATCH', 'QR-01 sai số tiền');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-002', 'KHONGCOMA', 15000, '{}') = 'MISMATCH', 'QR-01 mã tham chiếu lạ');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-002', 'KHONGCOMA', 15000, '{}') = 'MISMATCH'
                       AND (SELECT count(*) FROM payment_webhook_events WHERE provider_transaction_code = 'FT-002') = 1, 'QR-01 webhook lặp mã lạ không ghi lần hai');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = v_inv), 'QR-01 không tạo payment');
    PERFORM rc_test.ok((SELECT count(*) FROM payment_webhook_events WHERE resolution_status = 'OPEN') = 2, 'DB-12 sự cố tự mở');
    PERFORM rc_test.fails(format('SELECT rc_process_verified_qr('' '', %L, 1, ''{}'')', v_ref1), 'thiếu mã giao dịch', 'QR webhook thiếu mã giao dịch', 'RC422');

    PERFORM rc_test.fails(format('SELECT rc_cancel_payment_request((SELECT id FROM payment_requests WHERE payment_reference = %L), %s, '''')', v_ref1, rc_test.emp('C1')),
                          'bắt buộc có lý do', 'QR hủy không lý do', 'RC422');
    PERFORM rc_cancel_payment_request((SELECT id FROM payment_requests WHERE payment_reference = v_ref1), rc_test.emp('C1'), 'Khách đổi tài khoản');
    PERFORM rc_test.fails(format('SELECT rc_cancel_payment_request((SELECT id FROM payment_requests WHERE payment_reference = %L), %s, ''lần 2'')', v_ref1, rc_test.emp('C1')),
                          'không còn mở', 'QR hủy lần hai', 'RC409');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-003', v_ref1, 15000, '{}') = 'AFTER_CANCEL', 'QR-02 QR đến sau khi hủy');
    v_ref2 := rc_start_qr(v_inv, rc_test.emp('C1'));
    PERFORM rc_test.ok(v_ref1 <> v_ref2, 'QR mới có mã tham chiếu mới');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-004', v_ref2, 15000, '{}') = 'PROCESSED', 'QR-02 thanh toán QR');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-004', v_ref2, 15000, '{}') = 'PROCESSED'
                       AND (SELECT count(*) FROM payments WHERE invoice_id = v_inv) = 1, 'QR-02 webhook lặp không tạo payment thứ 2');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-005', v_ref2, 15000, '{}') = 'AFTER_CANCEL', 'QR-02 chuyển khoản lần 2 cho hóa đơn đã trả');
    PERFORM rc_test.ok((SELECT status FROM dining_tables WHERE code = 'B04') = 'AVAILABLE', 'QR thành công trả bàn');

    UPDATE payment_webhook_events
       SET resolution_status = 'RESOLVED', resolved_by_id = rc_test.emp('M1'), resolved_at = now(),
           resolution_note = 'Hoàn tiền thủ công cho khách'
     WHERE provider_transaction_code = 'FT-003';
    PERFORM rc_test.fails('UPDATE payment_webhook_events SET amount = 0 WHERE provider_transaction_code = ''FT-003''',
                          'resolution_', 'DB-12 sửa chứng cứ webhook');
    PERFORM rc_test.fails('DELETE FROM payments', 'chỉ-thêm', 'A3.6 xóa payment');
END $$;

-- ---------------------------------------------------------------------
-- EXC-01 (DB-14), WASTE-01, DB-22: lô đang giữ bị hỏng → bật cờ, chặn nấu, cấp lại; hủy hàng bằng WASTE
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_o    BIGINT;
    v_item BIGINT;
    v_i    BIGINT;
    v_p    BIGINT;
    v_adj  BIGINT;
BEGIN
    v_o := rc_test.open('B05');
    v_item := rc_test.send(v_o, 'b05-1', 'Bò lúc lắc', 1);
    PERFORM rc_accept_item(v_item, rc_test.emp('K1'));
    PERFORM rc_test.fails(format('SELECT rc_mark_batch_unusable(''KITCHEN'', %s, %s, '''')', rc_test.id('KB_BO_THAI'), rc_test.emp('K1')),
                          'bắt buộc có lý do', 'EXC-01 đánh dấu lô không lý do', 'RC422');
    PERFORM rc_test.ok(rc_mark_batch_unusable('KITCHEN', rc_test.id('KB_BO_THAI'), rc_test.emp('K1'), 'Hỏng tủ mát') = 1,
                       'EXC-01 bật cờ cho 1 món đang giữ');
    PERFORM rc_test.fails(format('SELECT rc_start_cooking(%s, %s)', v_item, rc_test.emp('K1')), 'cấp lại', 'EXC-01 chặn nấu', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_reallocate_item(%s, %s)', v_item, rc_test.emp('K1')), 'Thiếu nguyên liệu',
                          'EXC-01 chưa có lô thay thế', 'RC001');

    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    VALUES (v_i, rc_test.id('LOT_BO'), 1, 'PREPARE');
    PERFORM rc_issue_stock(v_i, rc_test.emp('M1'));
    PERFORM rc_receive_issue(v_i, rc_test.emp('K1'));
    INSERT INTO kitchen_preparations (stock_issue_item_id, input_quantity, prepared_by_id, request_key)
    SELECT id, 1, rc_test.emp('K1'), 'prep-3' FROM stock_issue_items WHERE stock_issue_id = v_i RETURNING id INTO v_p;
    INSERT INTO kitchen_preparation_outputs (kitchen_preparation_id, kitchen_ingredient_type_id, output_quantity)
    VALUES (v_p, rc_test.kt('Bò thái'), 0.9);
    PERFORM rc_confirm_preparation(v_p, rc_test.emp('K1'));

    PERFORM rc_reallocate_item(v_item, rc_test.emp('K1'));
    PERFORM rc_test.ok(NOT (SELECT has_stock_exception FROM order_items WHERE id = v_item), 'EXC-01 xóa cờ sau khi cấp lại');
    PERFORM rc_test.ok((SELECT release_reason FROM stock_reservations
                         WHERE order_item_id = v_item AND kitchen_batch_id = rc_test.id('KB_BO_THAI')) = 'BATCH_UNUSABLE',
                       'EXC-01 giải phóng phần giữ trên lô hỏng');
    PERFORM rc_start_cooking(v_item, rc_test.emp('K1'));
    PERFORM rc_finish_dish(v_item, rc_test.emp('K1'));
    PERFORM rc_serve_item(v_item, rc_test.emp('W1'));
    PERFORM rc_pay_cash(rc_checkout_invoice(v_o, rc_test.emp('C1')), rc_test.emp('C1'), 100000);

    INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key)
    VALUES ('DAMAGE', 'Hỏng tủ mát', rc_test.emp('K1'), 'adj-1') RETURNING id INTO v_adj;
    INSERT INTO stock_adjustment_items (stock_adjustment_id, kitchen_batch_id, quantity_delta)
    VALUES (v_adj, rc_test.id('KB_BO_THAI'), -rc_test.kb('KB_BO_THAI'));
    PERFORM rc_test.fails(format('INSERT INTO stock_adjustment_items (stock_adjustment_id, raw_batch_id, quantity_delta) VALUES (%s, %s, 1)',
                                 v_adj, rc_test.id('LOT_BO')),
                          'ck_adjust_delta_negative', 'DB-22 phiếu hủy không được tăng tồn', '23514');
    PERFORM rc_confirm_adjustment(v_adj, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.kb('KB_BO_THAI') = 0, 'WASTE-01 lô hỏng về 0');
    PERFORM rc_test.ok((SELECT SUM(-quantity_delta) FROM stock_movements WHERE movement_type = 'WASTE') = 6.1, 'WASTE-01 ghi WASTE 6,1 kg');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM stock_movements WHERE movement_type = 'WASTE' AND preparation_id IS NOT NULL),
                       'WASTE-01 tách khỏi hao hụt sơ chế');
    PERFORM rc_test.fails(format('SELECT rc_confirm_adjustment(%s, %s)', v_adj, rc_test.emp('M1')), 'không phải DRAFT', 'WASTE lặp xác nhận phiếu hủy hàng', 'RC409');
    PERFORM rc_test.ok((SELECT SUM(-quantity_delta) FROM stock_movements WHERE movement_type = 'WASTE') = 6.1, 'WASTE lặp không ghi hao hụt lần hai');
    PERFORM rc_test.fails(format('INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key) VALUES (''DAMAGE'', ''lặp'', %s, ''adj-1'')', rc_test.emp('K1')),
                          'request_key', 'WASTE lặp request_key tạo phiếu', '23505');
END $$;

-- ---------------------------------------------------------------------
-- RETURN-01, RETURN-02: trả WIP/DIRECT về đúng lô RAW; không trả lô PREPARED hay phần đang giữ; xác nhận lặp
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_i      BIGINT;
    v_line   BIGINT;
    v_ret    BIGINT;
    v_o      BIGINT;
    v_item   BIGINT;
    v_raw_bo NUMERIC;
    v_raw_h2 NUMERIC;
BEGIN
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    VALUES (v_i, rc_test.id('LOT_BO'), 0.5, 'PREPARE') RETURNING id INTO v_line;
    PERFORM rc_issue_stock(v_i, rc_test.emp('M1'));
    PERFORM rc_receive_issue(v_i, rc_test.emp('K1'));
    v_raw_bo := rc_test.raw('LOT_BO');
    v_raw_h2 := rc_test.raw('LOT_H2');
    INSERT INTO stock_returns (requested_by_id, reason, request_key) VALUES (rc_test.emp('K1'), 'Dư sau ca', 'ret-1')
    RETURNING id INTO v_ret;
    INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, quantity) VALUES (v_ret, v_line, 0.5);
    INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, direct_kitchen_batch_id, quantity)
    SELECT v_ret, direct_issue_item_id, id, 0.1 FROM kitchen_batches WHERE id = rc_test.id('KB_HANH');
    PERFORM rc_confirm_return(v_ret, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.raw('LOT_BO') = v_raw_bo + 0.5 AND rc_test.raw('LOT_H2') = v_raw_h2 + 0.1, 'RETURN-01 RAW tăng đúng lô gốc');
    PERFORM rc_test.ok((SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = v_line) = 0, 'RETURN-01 WIP giảm');
    PERFORM rc_test.fails(format('SELECT rc_confirm_return(%s, %s)', v_ret, rc_test.emp('M1')), 'không phải DRAFT', 'RETURN lặp xác nhận phiếu trả', 'RC409');
    PERFORM rc_test.ok(rc_test.raw('LOT_BO') = v_raw_bo + 0.5, 'RETURN lặp không cộng RAW lần hai');
    PERFORM rc_test.fails(format('INSERT INTO stock_returns (requested_by_id, reason, request_key) VALUES (%s, ''lặp'', ''ret-1'')', rc_test.emp('K1')), 'request_key', 'RETURN lặp request_key', '23505');

    INSERT INTO stock_returns (requested_by_id, reason, request_key) VALUES (rc_test.emp('K1'), 'Thử trả sai', 'ret-2')
    RETURNING id INTO v_ret;
    PERFORM rc_test.fails(format('INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, direct_kitchen_batch_id, quantity) VALUES (%s, %s, %s, 1)',
                                 v_ret, rc_test.id('WIP_BO'), rc_test.id('KB_BO_THAI')),
                          'fk_return_direct_batch_of_issue_item', 'RETURN-02 trả lô PREPARED', '23503');
    v_o := rc_test.open('B06');
    v_item := rc_test.send(v_o, 'b06-1', 'Coca', 9);
    PERFORM rc_test.ok(rc_accept_item(v_item, rc_test.emp('K1')) = 'ACCEPTED', 'giữ hết 9 lon còn lại');
    INSERT INTO stock_return_items (stock_return_id, source_issue_item_id, direct_kitchen_batch_id, quantity)
    SELECT v_ret, direct_issue_item_id, id, 1 FROM kitchen_batches WHERE id = rc_test.id('KB_COCA');
    PERFORM rc_test.fails(format('SELECT rc_confirm_return(%s, %s)', v_ret, rc_test.emp('M1')), 'đang giữ', 'RETURN-02 trả phần đang giữ cho món', 'RC409');
    UPDATE stock_returns SET status = 'CANCELLED' WHERE id = v_ret;
    PERFORM rc_cancel_item(v_item, rc_test.emp('W1'), 'Khách về sớm');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));
END $$;

-- ---------------------------------------------------------------------
-- COUNT-01, ISSUE-02: kiểm kê lệch sổ (stale) bị chặn; hủy phiếu ISSUED hoàn RAW, giữ lịch sử; kiểm kê lặp
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_book NUMERIC := rc_test.raw('LOT_BO');
    v_c    BIGINT;
    v_i    BIGINT;
BEGIN
    INSERT INTO stock_counts (created_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_c;
    INSERT INTO stock_count_items (stock_count_id, raw_batch_id, book_quantity, actual_quantity, reason)
    VALUES (v_c, rc_test.id('LOT_BO'), v_book, v_book - 0.1, 'Hao hụt cân');
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    VALUES (v_i, rc_test.id('LOT_BO'), 0.2, 'DIRECT');
    PERFORM rc_issue_stock(v_i, rc_test.emp('M1'));
    PERFORM rc_test.fails(format('SELECT rc_confirm_count(%s, %s)', v_c, rc_test.emp('M1')), 'stale', 'COUNT-01 số sổ đã đổi sau khi lập phiếu', 'RC409');
    PERFORM rc_cancel_issue(v_i, rc_test.emp('M1'), 'Bếp chưa cần');
    PERFORM rc_test.ok(rc_test.raw('LOT_BO') = v_book, 'ISSUE-02 hoàn RAW đúng lô');
    PERFORM rc_test.ok((SELECT count(*) FROM stock_movements m JOIN stock_issue_items l ON l.id = m.ref_issue_item_id
                         WHERE l.stock_issue_id = v_i) = 2
                       AND EXISTS (SELECT 1 FROM stock_issue_items WHERE stock_issue_id = v_i), 'ISSUE-02 sổ bù trừ, không xóa dòng xuất');
    PERFORM rc_test.fails(format('SELECT rc_cancel_issue(%s, %s, ''x'')', v_i, rc_test.emp('M1')), 'không hủy được', 'ISSUE-02 hủy phiếu đã hủy', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_cancel_issue(%s, %s, ''x'')', rc_test.id('I1'), rc_test.emp('M1')), 'hoàn trả',
                          'ISSUE-02 không hủy phiếu đã nhận', 'RC409');
    PERFORM rc_confirm_count(v_c, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.raw('LOT_BO') = v_book - 0.1, 'COUNT-01 điều chỉnh về số thực đếm');
    PERFORM rc_test.ok((SELECT quantity_delta FROM stock_movements WHERE movement_type = 'COUNT_ADJUST') = -0.1, 'COUNT-01 sổ COUNT_ADJUST');
    PERFORM rc_test.fails(format('SELECT rc_confirm_count(%s, %s)', v_c, rc_test.emp('M1')), 'không phải DRAFT', 'COUNT lặp xác nhận phiếu kiểm kê', 'RC409');
    PERFORM rc_test.ok(rc_test.raw('LOT_BO') = v_book - 0.1, 'COUNT lặp không điều chỉnh lần hai');
END $$;

-- =====================================================================
-- CÁC QUYẾT ĐỊNH NGHIỆP VỤ ĐÃ CHỐT
-- =====================================================================
-- Dữ liệu cho các khối QĐ: nguyên liệu Trứng (DIRECT, 80 quả) và các món 1 quả/suất với hạn mức khác nhau
DO $$
DECLARE
    v_r      BIGINT;
    v_i      BIGINT;
    v_menu   BIGINT := rc_test.id('MENU_TODAY');
    v_item   BIGINT;
    v_recipe BIGINT;
    k        RECORD;
BEGIN
    INSERT INTO ingredients (name, base_unit, min_quantity, is_expiry_tracked) VALUES ('Trứng', 'quả', 0, false);
    INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type, minimum_quantity)
    VALUES (rc_test.ing('Trứng'), 'Trứng', 'DIRECT', 0);
    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('NCC trứng', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit)
    VALUES (v_r, rc_test.ing('Trứng'), 80, 'quả', 1, 3000);
    PERFORM rc_confirm_receipt(v_r, rc_test.emp('M1'));
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    SELECT v_i, b.id, 80, 'DIRECT' FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.stock_receipt_id = v_r;
    PERFORM rc_issue_stock(v_i, rc_test.emp('M1'));
    PERFORM rc_receive_issue(v_i, rc_test.emp('K1'));
    PERFORM rc_test.put('KB_EGG', (SELECT id FROM kitchen_batches WHERE kitchen_ingredient_type_id = rc_test.kt('Trứng')));
    FOR k IN SELECT * FROM (VALUES ('NULL', NULL::int), ('ZERO', 0), ('ONE', 1), ('TWO', 2), ('EXC', 1),
                                   ('ABD', NULL), ('INV', NULL), ('CNC', NULL)) AS t(code, quota)
    LOOP
        INSERT INTO menu_items (menu_category_id, name, price) VALUES (rc_test.id('CAT_MAIN'), 'QD-' || k.code, 30000)
        RETURNING id INTO v_item;
        INSERT INTO recipes (menu_item_id, version, is_current) VALUES (v_item, 1, true) RETURNING id INTO v_recipe;
        INSERT INTO recipe_ingredients (recipe_id, kitchen_ingredient_type_id, quantity_per_serving)
        VALUES (v_recipe, rc_test.kt('Trứng'), 1);
        INSERT INTO daily_menu_items (daily_menu_id, menu_item_id, recipe_id, quota_total) VALUES (v_menu, v_item, v_recipe, k.quota);
    END LOOP;
    PERFORM rc_test.ok(rc_test.kb('KB_EGG') = 80, 'QĐ nền: 80 quả trứng sẵn dùng tại bếp');
END $$;

-- ---------------------------------------------------------------------
-- QĐ-1: quota NULL = không giới hạn; 0 = không còn suất; không giới hạn ≠ bỏ kiểm tra nguyên liệu
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_o  BIGINT;
    v_o2 BIGINT;
    a    BIGINT;
    b    BIGINT;
    c    BIGINT;
BEGIN
    PERFORM rc_test.fails(format('UPDATE daily_menu_items SET quota_total = -1 WHERE id = %s', rc_test.dmi('QD-NULL')),
                          'ck_daily_menu_quota_nonneg', 'QĐ-1 quota âm bị chặn', '23514');
    PERFORM rc_test.ok((SELECT quota_total IS NULL FROM daily_menu_items WHERE id = rc_test.dmi('QD-NULL')), 'QĐ-1 NULL (không giới hạn) hợp lệ');
    PERFORM rc_test.ok((SELECT quota_total = 0 FROM daily_menu_items WHERE id = rc_test.dmi('QD-ZERO')), 'QĐ-1 0 hợp lệ');

    -- NULL: nhận 5 suất liên tục, vẫn đặt giữ nguyên liệu
    v_o := rc_test.open('Q01');
    a := rc_test.send(v_o, 'q1-a', 'QD-NULL', 5);
    PERFORM rc_test.ok(rc_accept_item(a, rc_test.emp('K1')) = 'ACCEPTED', 'QĐ-1 NULL nhận 5 suất');
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = 5, 'QĐ-1 NULL vẫn đặt giữ nguyên liệu');
    -- không giới hạn quota ≠ không kiểm tra nguyên liệu
    b := rc_test.send(v_o, 'q1-b', 'QD-NULL', 500);
    PERFORM rc_test.ok(rc_accept_item(b, rc_test.emp('K1')) = 'REJECTED', 'QĐ-1 NULL vẫn bị từ chối khi thiếu nguyên liệu');
    PERFORM rc_test.ok((SELECT reason FROM order_item_events WHERE order_item_id = b AND to_status = 'REJECTED') LIKE 'Thiếu nguyên liệu%',
                       'QĐ-1 lý do từ chối là thiếu nguyên liệu');
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = 5 AND NOT EXISTS (SELECT 1 FROM stock_reservations WHERE order_item_id = b),
                       'QĐ-1 từ chối không để lại phần giữ dở dang');
    PERFORM rc_cancel_item(a, rc_test.emp('W1'), 'Dọn ca kiểm thử');
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = 0, 'QĐ-1 hủy giải phóng phần giữ');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));

    -- quota 0: không còn suất → chặn sớm ở gửi món
    v_o := rc_test.open('Q02');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''q2-a'', %L::jsonb)', v_o, rc_test.emp('W1'), rc_test.items(rc_test.dmi('QD-ZERO'), 1)),
                          'không đủ suất', 'QĐ-1 quota 0 chặn gửi món', 'RC409');
    -- kiểm cứng ở nhận món: món đã PENDING rồi quota bị đặt về 0
    UPDATE daily_menu_items SET quota_total = 5 WHERE id = rc_test.dmi('QD-ZERO');
    a := rc_test.send(v_o, 'q2-b', 'QD-ZERO', 1);
    UPDATE daily_menu_items SET quota_total = 0 WHERE id = rc_test.dmi('QD-ZERO');
    PERFORM rc_test.ok(rc_accept_item(a, rc_test.emp('K1')) = 'REJECTED', 'QĐ-1 quota 0: nhận món bị từ chối');
    PERFORM rc_test.ok((SELECT reason FROM order_item_events WHERE order_item_id = a AND to_status = 'REJECTED') LIKE '%hạn mức 0%', 'QĐ-1 lý do hạn mức 0');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-ZERO')) = 0, 'QĐ-1 REJECTED không chiếm quota');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));

    -- quota còn 1: hai món cùng PENDING, chỉ món nhận trước được suất cuối
    v_o := rc_test.open('Q03');
    v_o2 := rc_test.open('Q04');
    a := rc_test.send(v_o, 'q3-a', 'QD-ONE', 1);
    b := rc_test.send(v_o2, 'q4-a', 'QD-ONE', 1);
    PERFORM rc_test.ok(rc_accept_item(a, rc_test.emp('K1')) = 'ACCEPTED', 'QĐ-1 quota còn 1: món đầu được nhận');
    PERFORM rc_test.ok(rc_accept_item(b, rc_test.emp('K1')) = 'REJECTED', 'QĐ-1 quota còn 1: món sau bị từ chối');
    PERFORM rc_test.ok((SELECT reason FROM order_item_events WHERE order_item_id = b AND to_status = 'REJECTED') LIKE 'Vượt hạn mức%', 'QĐ-1 lý do vượt hạn mức');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-ONE')) = 1, 'QĐ-1 đã chiếm đúng 1 suất');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''q4-b'', %L::jsonb)', v_o2, rc_test.emp('W1'), rc_test.items(rc_test.dmi('QD-ONE'), 1)),
                          'còn 0', 'QĐ-1 hết suất chặn gửi thêm', 'RC409');
    -- không hạ quota dưới số đã chiếm; hạ đúng bằng số đã chiếm hoặc nâng/bỏ giới hạn thì được
    PERFORM rc_test.fails(format('UPDATE daily_menu_items SET quota_total = 0 WHERE id = %s', rc_test.dmi('QD-ONE')),
                          'thấp hơn số suất đã nhận', 'QĐ-1 không hạ quota dưới số đã chiếm', 'RC409');
    UPDATE daily_menu_items SET quota_total = 1 WHERE id = rc_test.dmi('QD-ONE');
    UPDATE daily_menu_items SET quota_total = NULL WHERE id = rc_test.dmi('QD-ONE');
    c := rc_test.send(v_o2, 'q4-c', 'QD-ONE', 1);
    PERFORM rc_test.ok(rc_accept_item(c, rc_test.emp('K1')) = 'ACCEPTED', 'QĐ-1 bỏ giới hạn thì nhận thêm được');
    UPDATE daily_menu_items SET quota_total = 2 WHERE id = rc_test.dmi('QD-ONE');
    PERFORM rc_test.fails(format('UPDATE daily_menu_items SET menu_item_id = (SELECT id FROM menu_items WHERE name = ''Coca'') WHERE id = %s', rc_test.dmi('QD-ONE')),
                          'đã có món gọi', 'QĐ-1 không đổi món của dòng đã có món gọi', 'RC409');
    -- dọn
    PERFORM rc_cancel_item(a, rc_test.emp('W1'), 'Dọn ca kiểm thử');
    PERFORM rc_cancel_item(c, rc_test.emp('W1'), 'Dọn ca kiểm thử');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o2), rc_test.emp('W1'));
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = 0, 'QĐ-1 dọn xong không còn phần giữ');
END $$;

-- ---------------------------------------------------------------------
-- QĐ-8: quota chiếm khi ACCEPTED; REJECTED không chiếm; hủy trước COOKING giải phóng đúng một lần
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_o BIGINT;
    a   BIGINT;
    b   BIGINT;
    c   BIGINT;
    d   BIGINT;
    e   BIGINT;
BEGIN
    v_o := rc_test.open('Q05');
    a := rc_test.send(v_o, 'q5-a', 'QD-TWO', 1);
    b := rc_test.send(v_o, 'q5-b', 'QD-TWO', 1);
    c := rc_test.send(v_o, 'q5-c', 'QD-TWO', 1);
    d := rc_test.send(v_o, 'q5-d', 'QD-TWO', 1);
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 0, 'QĐ-8 PENDING chưa chiếm quota');
    PERFORM rc_reject_item(a, rc_test.emp('K1'), 'Bếp tạm hết dụng cụ');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 0, 'QĐ-8 REJECTED không chiếm quota');
    PERFORM rc_test.ok(rc_accept_item(b, rc_test.emp('K1')) = 'ACCEPTED' AND rc_quota_used(rc_test.dmi('QD-TWO')) = 1, 'QĐ-8 chiếm khi ACCEPTED');
    PERFORM rc_test.ok(rc_accept_item(c, rc_test.emp('K1')) = 'ACCEPTED' AND rc_quota_used(rc_test.dmi('QD-TWO')) = 2, 'QĐ-8 chiếm suất thứ hai');
    PERFORM rc_test.ok(rc_accept_item(d, rc_test.emp('K1')) = 'REJECTED', 'QĐ-8 hết suất: món thứ ba bị từ chối dù a đã bị REJECTED trước đó');
    -- hủy trước COOKING giải phóng đúng một lần
    PERFORM rc_cancel_item(b, rc_test.emp('K1'), 'Khách đổi món');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 1, 'QĐ-8 hủy trước COOKING giải phóng 1 suất');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item(%s, %s, ''lần hai'')', b, rc_test.emp('K1')), 'không hủy được', 'QĐ-8 hủy lần hai', 'RC409');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 1, 'QĐ-8 không giải phóng lần hai');
    -- suất giải phóng bán lại được
    e := rc_test.send(v_o, 'q5-e', 'QD-TWO', 1);
    PERFORM rc_test.ok(rc_accept_item(e, rc_test.emp('K1')) = 'ACCEPTED' AND rc_quota_used(rc_test.dmi('QD-TWO')) = 2, 'QĐ-8 suất giải phóng bán lại được');
    -- từ COOKING không hoàn quota (cả khi hủy ngoại lệ): xem khối QĐ-2
    PERFORM rc_start_cooking(c, rc_test.emp('K1'));
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 2, 'QĐ-8 COOKING vẫn chiếm quota');
    PERFORM rc_cancel_item_exception(c, rc_test.emp('M1'), 'Rơi món khi nấu');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 2, 'QĐ-8 hủy ngoại lệ sau COOKING không hoàn quota');
    PERFORM rc_cancel_item(e, rc_test.emp('K1'), 'Dọn');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-TWO')) = 1, 'QĐ-8 chỉ phần chưa nấu được giải phóng');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));
END $$;

-- ---------------------------------------------------------------------
-- QĐ-2: hủy thường trước COOKING; từ COOKING chỉ Manager hủy ngoại lệ (lý do, lịch sử, không hoàn nguyên liệu/quota)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_o      BIGINT;
    x        BIGINT;
    y        BIGINT;
    v_before NUMERIC;
    v_mov    BIGINT;
BEGIN
    v_o := rc_test.open('X01');
    x := rc_test.send(v_o, 'x1-x', 'QD-EXC', 1);
    y := rc_test.send(v_o, 'x1-y', 'QD-EXC', 1);                 -- quota 1: cả hai PENDING vì PENDING chưa chiếm suất
    PERFORM rc_test.ok(rc_accept_item(x, rc_test.emp('K1')) = 'ACCEPTED', 'QĐ-2 nhận x');
    -- trước COOKING: hủy thường theo quyền (Kitchen/Waiter), có lý do
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''thử'')', x, rc_test.emp('M1')), 'chỉ áp dụng từ COOKING', 'QĐ-2 ACCEPTED không dùng hủy ngoại lệ', 'RC409');
    PERFORM rc_start_cooking(x, rc_test.emp('K1'));
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-EXC')) = 1, 'QĐ-2 x đã chiếm suất duy nhất');
    -- từ COOKING: hủy thường bị chặn, kể cả cho Manager
    PERFORM rc_test.fails(format('SELECT rc_cancel_item(%s, %s, ''cháy'')', x, rc_test.emp('K1')), 'chỉ Manager hủy ngoại lệ', 'QĐ-2 Kitchen không hủy thường sau COOKING', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item(%s, %s, ''cháy'')', x, rc_test.emp('M1')), 'chỉ Manager hủy ngoại lệ', 'QĐ-2 hủy thường sau COOKING bị chặn cả với Manager', 'RC409');
    -- hủy ngoại lệ: chỉ Manager (vai trò), bắt buộc lý do
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''cháy'')', x, rc_test.emp('K1')), 'không có vai trò', 'QĐ-2 Kitchen không hủy ngoại lệ', 'RC403');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''cháy'')', x, rc_test.emp('W1')), 'không có vai trò', 'QĐ-2 Waiter không hủy ngoại lệ', 'RC403');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''cháy'')', x, rc_test.emp('C1')), 'không có vai trò', 'QĐ-2 Cashier không hủy ngoại lệ', 'RC403');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''   '')', x, rc_test.emp('M1')), 'bắt buộc có lý do', 'QĐ-2 hủy ngoại lệ thiếu lý do', 'RC422');
    PERFORM rc_test.ok(rc_test.state(x) = 'COOKING', 'QĐ-2 các lần bị chặn không đổi trạng thái');
    -- Manager hủy ngoại lệ
    v_before := rc_test.kb('KB_EGG');
    v_mov := (SELECT count(*) FROM stock_movements);
    PERFORM rc_cancel_item_exception(x, rc_test.emp('M1'), 'Món cháy, khách không nhận');
    PERFORM rc_test.ok(rc_test.state(x) = 'CANCELLED', 'QĐ-2 trạng thái CANCELLED');
    PERFORM rc_test.ok((SELECT (from_status, to_status, actor_id, reason) = ('COOKING', 'CANCELLED', rc_test.emp('M1'), 'Món cháy, khách không nhận')
                          FROM order_item_events WHERE order_item_id = x ORDER BY id DESC LIMIT 1),
                       'QĐ-2 lưu lịch sử: trạng thái, người thực hiện, lý do');
    PERFORM rc_test.ok(rc_test.kb('KB_EGG') = v_before, 'QĐ-2 không hoàn nguyên liệu đã tiêu hao');
    PERFORM rc_test.ok((SELECT count(*) FROM stock_movements) = v_mov, 'QĐ-2 không ghi sổ kho');
    PERFORM rc_test.ok((SELECT status FROM stock_reservations WHERE order_item_id = x) = 'CONSUMED', 'QĐ-2 reservation vẫn CONSUMED');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-EXC')) = 1, 'QĐ-2 không hoàn quota sau khi đã nấu');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''lần hai'')', x, rc_test.emp('M1')), 'chỉ áp dụng từ COOKING', 'QĐ-2 hủy ngoại lệ lần hai', 'RC409');
    PERFORM rc_test.ok(rc_accept_item(y, rc_test.emp('K1')) = 'REJECTED', 'QĐ-2 suất không được hoàn: món sau bị từ chối');
    PERFORM rc_test.ok((SELECT reason FROM order_item_events WHERE order_item_id = y AND to_status = 'REJECTED') LIKE 'Vượt hạn mức%', 'QĐ-2 lý do vượt hạn mức');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));
END $$;

DO $$
DECLARE
    v_o    BIGINT;
    p      BIGINT;
    q      BIGINT;
    r      BIGINT;
    v_inv  BIGINT;
    v_ref  TEXT;
BEGIN
    -- hóa đơn chưa PAID: món hủy ngoại lệ không tính tiền; hóa đơn đang thu phải mở lại trước
    v_o := rc_test.open('X02');
    p := rc_test.send(v_o, 'x2-p', 'QD-CNC', 1);
    q := rc_test.send(v_o, 'x2-q', 'QD-CNC', 1);
    PERFORM rc_test.serve(p);
    PERFORM rc_accept_item(q, rc_test.emp('K1'));
    PERFORM rc_start_cooking(q, rc_test.emp('K1'));
    PERFORM rc_cancel_item_exception(q, rc_test.emp('M1'), 'Khách rời bàn trước khi món ra');
    v_inv := rc_checkout_invoice(v_o, rc_test.emp('C1'));
    PERFORM rc_test.ok((SELECT total = 30000 FROM invoices WHERE id = v_inv) AND (SELECT count(*) FROM invoice_items WHERE invoice_id = v_inv) = 1,
                       'QĐ-2 món hủy ngoại lệ không tính tiền');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''miễn phí'')', p, rc_test.emp('M1')), 'mở lại bằng rc_reopen_invoice', 'QĐ-2 hóa đơn PAYMENT_PENDING phải mở lại trước', 'RC409');
    v_ref := rc_start_qr(v_inv, rc_test.emp('C1'));
    PERFORM rc_reopen_invoice(v_inv, rc_test.emp('C1'), 'Manager sẽ miễn phí một món');
    PERFORM rc_test.ok((SELECT status = 'CANCELLED' AND cancelled_by_id = rc_test.emp('C1') AND cancel_reason = 'Manager sẽ miễn phí một món'
                          FROM payment_requests WHERE payment_reference = v_ref), 'QĐ-2 mở lại hủy yêu cầu thanh toán hiện hành có lý do/người');
    PERFORM rc_cancel_item_exception(p, rc_test.emp('M1'), 'Khách không hài lòng, miễn phí món');
    PERFORM rc_test.ok((SELECT status = 'DRAFT' AND total = 0 FROM invoices WHERE id = v_inv) AND NOT EXISTS (SELECT 1 FROM invoice_items WHERE invoice_id = v_inv),
                       'QĐ-2 hóa đơn DRAFT: dòng bị loại, tổng tính lại về 0');
    PERFORM rc_test.fails(format('SELECT rc_checkout_invoice(%s, %s)', v_o, rc_test.emp('C1')), 'không có món phải thu', 'QĐ-2 không chốt hóa đơn 0 đồng', 'RC409');
    r := rc_test.send(v_o, 'x2-r', 'QD-CNC', 2);
    PERFORM rc_test.serve(r);
    PERFORM rc_test.ok(rc_checkout_invoice(v_o, rc_test.emp('C1')) = v_inv, 'QĐ-2 chốt lại trên cùng hóa đơn');
    PERFORM rc_test.ok((SELECT total = 60000 FROM invoices WHERE id = v_inv), 'QĐ-2 tổng chỉ gồm món còn hiệu lực');
    PERFORM rc_pay_cash(v_inv, rc_test.emp('C1'), 100000);
    PERFORM rc_test.ok((SELECT amount = 60000 FROM payments WHERE invoice_id = v_inv), 'QĐ-2 thu đúng số tiền sau hủy ngoại lệ');
    -- sau khi PAID: không hủy ngoại lệ
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''muộn'')', r, rc_test.emp('M1')), 'đã CLOSED', 'QĐ-2 không hủy ngoại lệ sau khi hóa đơn PAID', 'RC409');
END $$;

-- QĐ-2 ↔ QĐ-3: hủy ngoại lệ hết món đã phục vụ khi hóa đơn còn DRAFT (0 đồng) → phiên không còn nghĩa vụ nhưng đã có hóa đơn,
-- rc_close_empty_session từ chối; Manager đóng bằng rc_abandon_session: đơn CANCELLED, không UNCOLLECTED, không payments
DO $$
DECLARE
    v_o    BIGINT;
    v_s    BIGINT;
    p      BIGINT;
    v_inv  BIGINT;
BEGIN
    v_o := rc_test.open('X03');
    v_s := (SELECT table_session_id FROM orders WHERE id = v_o);
    p := rc_test.send(v_o, 'x3-p', 'QD-CNC', 1);
    PERFORM rc_test.serve(p);
    v_inv := rc_checkout_invoice(v_o, rc_test.emp('C1'));
    PERFORM rc_reopen_invoice(v_inv, rc_test.emp('C1'), 'Manager sẽ miễn phí toàn bộ');
    PERFORM rc_cancel_item_exception(p, rc_test.emp('M1'), 'Miễn phí toàn bộ: món sai yêu cầu');
    PERFORM rc_test.ok((SELECT status = 'DRAFT' AND total = 0 FROM invoices WHERE id = v_inv), 'QĐ-2/3 hóa đơn DRAFT 0 đồng sau khi miễn phí toàn bộ');
    PERFORM rc_test.fails(format('SELECT rc_close_empty_session(%s, %s)', v_s, rc_test.emp('W1')), 'đã có hóa đơn', 'QĐ-2/3 phiên đã có hóa đơn: không đóng trống', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_checkout_invoice(%s, %s)', v_o, rc_test.emp('C1')), 'không có món phải thu', 'QĐ-2/3 không chốt hóa đơn 0 đồng', 'RC409');
    PERFORM rc_test.ok(rc_abandon_session(v_s, rc_test.emp('M1'), 'Miễn phí toàn bộ, khách rời bàn') IS NULL, 'QĐ-2/3 không còn món phải thu: trả NULL');
    PERFORM rc_test.ok((SELECT status FROM orders WHERE id = v_o) = 'CANCELLED'
                       AND (SELECT status FROM invoices WHERE id = v_inv) = 'DRAFT'
                       AND NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = v_inv)
                       AND (SELECT status FROM table_sessions WHERE id = v_s) = 'CLOSED'
                       AND (SELECT status FROM dining_tables WHERE code = 'X03') = 'AVAILABLE',
                       'QĐ-2/3 đơn CANCELLED, hóa đơn DRAFT không thành nợ, không payments, bàn trống');
END $$;

-- ---------------------------------------------------------------------
-- QĐ-4: chốt hóa đơn — đủ dòng, tự tính tổng, đối chiếu đơn, PAYMENT_PENDING, khóa gọi món, mở lại có kiểm soát
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_o   BIGINT;
    a     BIGINT;
    b     BIGINT;
    c     BIGINT;
    v_inv BIGINT;
    v_ref TEXT;
BEGIN
    v_o := rc_test.open('I01');
    a := rc_test.send(v_o, 'i1-a', 'QD-INV', 2);
    b := rc_test.send(v_o, 'i1-b', 'Coca', 3);
    PERFORM rc_test.serve(a);
    PERFORM rc_test.serve(b);
    v_inv := rc_checkout_invoice(v_o, rc_test.emp('C1'));
    PERFORM rc_test.ok((SELECT total = 105000 AND status = 'PAYMENT_PENDING' AND created_by_id = rc_test.emp('C1') FROM invoices WHERE id = v_inv),
                       'QĐ-4 tổng tự tính 2×30.000 + 3×15.000 và PAYMENT_PENDING, ghi người chốt');
    PERFORM rc_test.ok((SELECT count(*) = 2 AND SUM(line_total) = 105000 FROM invoice_items WHERE invoice_id = v_inv), 'QĐ-4 đủ dòng, line_total tự sinh');
    PERFORM rc_test.fails(format('SELECT rc_checkout_invoice(%s, %s)', v_o, rc_test.emp('C1')), 'đang PAYMENT_PENDING', 'QĐ-4 chốt lần hai không tạo thêm', 'RC409');
    PERFORM rc_test.ok((SELECT count(*) FROM invoices WHERE order_id = v_o) = 1, 'QĐ-4 chỉ một hóa đơn / đơn');
    -- không gửi thêm món vào phiên đang thanh toán
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''i1-x'', %L::jsonb)', v_o, rc_test.emp('W1'), rc_test.items(rc_test.dmi('QD-INV'), 1)),
                          'đang thanh toán', 'QĐ-4 Waiter không gửi món khi PAYMENT_PENDING', 'RC409');
    -- giả mạo trực tiếp bị chặn
    PERFORM rc_test.fails(format('UPDATE invoices SET total = 1 WHERE id = %s', v_inv), 'không đổi total', 'QĐ-4 sửa tổng khi PAYMENT_PENDING', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM invoice_items WHERE invoice_id = %s', v_inv), 'bị khóa', 'QĐ-4 xóa dòng khi PAYMENT_PENDING', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM invoices WHERE id = %s', v_inv), 'không xóa', 'QĐ-4 xóa hóa đơn', 'RC409');
    -- thay đổi trước thanh toán: xử lý yêu cầu thanh toán hiện hành theo quy trình hợp lệ (mở lại)
    v_ref := rc_start_qr(v_inv, rc_test.emp('C1'));
    PERFORM rc_test.fails(format('SELECT rc_reopen_invoice(%s, %s, '''')', v_inv, rc_test.emp('C1')), 'bắt buộc có lý do', 'QĐ-4 mở lại thiếu lý do', 'RC422');
    PERFORM rc_reopen_invoice(v_inv, rc_test.emp('C1'), 'Khách gọi thêm món');
    PERFORM rc_test.ok((SELECT status = 'DRAFT' FROM invoices WHERE id = v_inv)
                       AND (SELECT status = 'CANCELLED' FROM payment_requests WHERE payment_reference = v_ref), 'QĐ-4 mở lại: hóa đơn DRAFT, yêu cầu QR bị hủy');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-I01-LATE', v_ref, 105000, '{}') = 'AFTER_CANCEL', 'QĐ-4 QR đến muộn sau khi mở lại → sự cố, không PAID');
    PERFORM rc_test.ok((SELECT status = 'DRAFT' FROM invoices WHERE id = v_inv) AND NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = v_inv), 'QĐ-4 QR muộn không đổi hóa đơn');
    -- gọi thêm khi hóa đơn DRAFT; chốt lại bao gồm cả dòng cũ và mới
    c := rc_test.send(v_o, 'i1-c', 'QD-INV', 1);
    PERFORM rc_test.fails(format('SELECT rc_checkout_invoice(%s, %s)', v_o, rc_test.emp('C1')), 'chưa phục vụ xong', 'QĐ-4 chốt khi món mới chưa xong', 'RC409');
    PERFORM rc_test.serve(c);
    PERFORM rc_test.ok(rc_checkout_invoice(v_o, rc_test.emp('C1')) = v_inv, 'QĐ-4 chốt lại cùng hóa đơn');
    PERFORM rc_test.ok((SELECT total = 135000 FROM invoices WHERE id = v_inv) AND (SELECT count(*) FROM invoice_items WHERE invoice_id = v_inv) = 3,
                       'QĐ-4 chốt lại: 3 dòng, 135.000');
    -- đối chiếu phát hiện lệch / giả mạo (trong hóa đơn DRAFT)
    PERFORM rc_reopen_invoice(v_inv, rc_test.emp('C1'), 'Kiểm thử đối chiếu');
    PERFORM rc_test.fails_commit(format('DELETE FROM invoice_items WHERE order_item_id = %s; PERFORM rc_invoice_total_checked(%s);', c, v_inv),
                                 'không khớp đơn hàng', 'QĐ-4 thiếu dòng SERVED bị phát hiện', 'RC409');
    PERFORM rc_test.fails_commit(format('DELETE FROM invoice_items WHERE order_item_id = %s; INSERT INTO invoice_items (invoice_id, order_item_id, item_name, quantity, unit_price) VALUES (%s, %s, ''Sai tên'', 1, 30000);', c, v_inv, c),
                                 'bản chụp đúng', 'QĐ-4 dòng hóa đơn phải là snapshot đúng', 'RC409');
    PERFORM rc_test.fails_commit(format('INSERT INTO invoice_items (invoice_id, order_item_id, item_name, quantity, unit_price) SELECT %s, id, item_name_snapshot, quantity, unit_price FROM order_items WHERE status = ''REJECTED'' LIMIT 1;', v_inv),
                                 'không phải dòng SERVED', 'QĐ-4 không đưa dòng không SERVED vào hóa đơn', 'RC409');
    PERFORM rc_test.ok(rc_checkout_invoice(v_o, rc_test.emp('C1')) = v_inv, 'QĐ-4 chốt lại sau kiểm thử đối chiếu');
    PERFORM rc_pay_cash(v_inv, rc_test.emp('C1'), 135000);
    PERFORM rc_test.ok((SELECT status = 'PAID' FROM invoices WHERE id = v_inv) AND (SELECT amount = 135000 FROM payments WHERE invoice_id = v_inv), 'QĐ-4 thanh toán đủ');
END $$;

-- ---------------------------------------------------------------------
-- QĐ-3: khách bỏ về chưa thanh toán — Manager, lý do; không payments, không PAID, không doanh thu, không xóa dữ liệu
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_o     BIGINT;
    v_s     BIGINT;
    p       BIGINT;
    a       BIGINT;
    c       BIGINT;
    r       BIGINT;
    s       BIGINT;
    v_inv   BIGINT;
    v_pay   NUMERIC;
    v_mov   BIGINT;
    v_res   NUMERIC;
BEGIN
    v_o := rc_test.open('A01');
    v_s := (SELECT table_session_id FROM orders WHERE id = v_o);
    p := rc_test.send(v_o, 'a1-p', 'QD-ABD', 1);                                                -- PENDING
    a := rc_test.send(v_o, 'a1-a', 'QD-ABD', 1); PERFORM rc_accept_item(a, rc_test.emp('K1'));  -- ACCEPTED
    c := rc_test.send(v_o, 'a1-c', 'QD-ABD', 1); PERFORM rc_accept_item(c, rc_test.emp('K1'));
    PERFORM rc_start_cooking(c, rc_test.emp('K1'));                                             -- COOKING
    r := rc_test.send(v_o, 'a1-r', 'QD-ABD', 1); PERFORM rc_accept_item(r, rc_test.emp('K1'));
    PERFORM rc_start_cooking(r, rc_test.emp('K1')); PERFORM rc_finish_dish(r, rc_test.emp('K1')); -- READY
    s := rc_test.send(v_o, 'a1-s', 'QD-ABD', 1); PERFORM rc_test.serve(s);                      -- SERVED
    v_pay := (SELECT COALESCE(SUM(amount), 0) FROM payments);
    v_mov := (SELECT count(*) FROM stock_movements);
    v_res := rc_test.kr('KB_EGG');
    -- quyền và lý do
    PERFORM rc_test.fails(format('SELECT rc_abandon_session(%s, %s, ''bỏ về'')', v_s, rc_test.emp('K1')), 'không có vai trò', 'QĐ-3 Kitchen không xử lý khách bỏ về', 'RC403');
    PERFORM rc_test.fails(format('SELECT rc_abandon_session(%s, %s, ''bỏ về'')', v_s, rc_test.emp('W1')), 'không có vai trò', 'QĐ-3 Waiter không xử lý khách bỏ về', 'RC403');
    PERFORM rc_test.fails(format('SELECT rc_abandon_session(%s, %s, ''bỏ về'')', v_s, rc_test.emp('C1')), 'không có vai trò', 'QĐ-3 Cashier không xử lý khách bỏ về', 'RC403');
    PERFORM rc_test.fails(format('SELECT rc_abandon_session(%s, %s, '' '')', v_s, rc_test.emp('M1')), 'bắt buộc có lý do', 'QĐ-3 thiếu lý do', 'RC422');
    PERFORM rc_test.ok(rc_test.state(p) = 'PENDING' AND rc_test.state(s) = 'SERVED', 'QĐ-3 lần bị chặn không đổi gì');
    -- Manager xử lý
    v_inv := rc_abandon_session(v_s, rc_test.emp('M1'), 'Khách rời quán không thanh toán');
    PERFORM rc_test.ok(v_inv IS NOT NULL, 'QĐ-3 trả về hóa đơn nghĩa vụ');
    PERFORM rc_test.ok((SELECT status = 'UNCOLLECTED' AND total = 30000 AND uncollected_by_id = rc_test.emp('M1')
                               AND uncollected_reason = 'Khách rời quán không thanh toán' AND uncollected_at IS NOT NULL
                          FROM invoices WHERE id = v_inv), 'QĐ-3 hóa đơn UNCOLLECTED: tổng, người, lý do, giờ');
    PERFORM rc_test.ok((SELECT count(*) FROM invoice_items WHERE invoice_id = v_inv) = 1, 'QĐ-3 nghĩa vụ chỉ gồm dòng SERVED');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = v_inv) AND NOT EXISTS (SELECT 1 FROM payment_requests WHERE invoice_id = v_inv AND status = 'PAID'),
                       'QĐ-3 không tạo payments, không có yêu cầu PAID');
    PERFORM rc_test.ok((SELECT COALESCE(SUM(amount), 0) FROM payments) = v_pay, 'QĐ-3 không ghi doanh thu thực thu');
    PERFORM rc_test.ok(rc_test.state(p) = 'CANCELLED' AND rc_test.state(a) = 'CANCELLED' AND rc_test.state(c) = 'CANCELLED'
                       AND rc_test.state(r) = 'CANCELLED' AND rc_test.state(s) = 'SERVED', 'QĐ-3 dòng còn mở bị hủy, dòng đã phục vụ giữ nguyên');
    PERFORM rc_test.ok((SELECT (from_status, to_status, actor_id) = ('COOKING', 'CANCELLED', rc_test.emp('M1')) AND reason LIKE 'Khách bỏ về: Khách rời quán%'
                          FROM order_item_events WHERE order_item_id = c ORDER BY id DESC LIMIT 1), 'QĐ-3 lịch sử dòng COOKING: người + lý do');
    PERFORM rc_test.ok((SELECT (from_status, to_status) = ('PENDING', 'CANCELLED') FROM order_item_events WHERE order_item_id = p ORDER BY id DESC LIMIT 1), 'QĐ-3 lịch sử dòng PENDING');
    PERFORM rc_test.ok((SELECT count(*) FROM stock_movements) = v_mov, 'QĐ-3 không ghi sổ kho (không hoàn nguyên liệu đã tiêu hao)');
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = v_res - 1, 'QĐ-3 chỉ giải phóng phần giữ của món chưa nấu');
    PERFORM rc_test.ok((SELECT status FROM stock_reservations WHERE order_item_id = a) = 'RELEASED'
                       AND (SELECT status FROM stock_reservations WHERE order_item_id = c) = 'CONSUMED', 'QĐ-3 reservation: chưa nấu RELEASED, đã nấu CONSUMED');
    PERFORM rc_test.ok(rc_quota_used(rc_test.dmi('QD-ABD')) = 3, 'QĐ-3 món đã nấu vẫn chiếm quota (c, r, s)');
    PERFORM rc_test.ok((SELECT status FROM dining_tables WHERE code = 'A01') = 'AVAILABLE'
                       AND (SELECT status = 'CLOSED' AND closed_by_id = rc_test.emp('M1') AND closed_at IS NOT NULL FROM table_sessions WHERE id = v_s)
                       AND (SELECT status FROM orders WHERE id = v_o) = 'CLOSED', 'QĐ-3 phiên đóng bởi Manager, bàn được giải phóng, đơn CLOSED');
    PERFORM rc_test.ok((SELECT count(*) FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id WHERE b.order_id = v_o) = 5
                       AND (SELECT count(*) FROM order_item_events e JOIN order_items oi ON oi.id = e.order_item_id
                              JOIN order_batches b ON b.id = oi.order_batch_id WHERE b.order_id = v_o) = 19, 'QĐ-3 không mất dữ liệu đơn hàng và lịch sử');
    -- hóa đơn UNCOLLECTED là cuối
    PERFORM rc_test.fails(format('SELECT rc_pay_cash(%s, %s, 30000)', v_inv, rc_test.emp('C1')), 'UNCOLLECTED', 'QĐ-3 không thu tiền mặt hóa đơn UNCOLLECTED', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_start_qr(%s, %s)', v_inv, rc_test.emp('C1')), 'UNCOLLECTED', 'QĐ-3 không tạo QR cho hóa đơn UNCOLLECTED', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_reopen_invoice(%s, %s, ''x'')', v_inv, rc_test.emp('C1')), 'UNCOLLECTED', 'QĐ-3 không mở lại hóa đơn UNCOLLECTED', 'RC409');
    PERFORM rc_test.fails(format('UPDATE invoices SET status = ''PAID'' WHERE id = %s', v_inv), 'bất biến', 'QĐ-3 không đánh dấu PAID', 'RC409');
    PERFORM rc_test.fails(format('UPDATE invoices SET total = 1 WHERE id = %s', v_inv), 'bất biến', 'QĐ-3 không sửa tổng', 'RC409');
    PERFORM rc_test.fails(format('INSERT INTO payment_requests (invoice_id, cashier_id, amount_due, cash_received, method, status) VALUES (%s, %s, 30000, 30000, ''CASH'', ''PAID'')', v_inv, rc_test.emp('C1')),
                          'PAYMENT_PENDING', 'QĐ-3 không tạo yêu cầu thu cho hóa đơn UNCOLLECTED', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_submit_batch(%s, %s, ''a1-z'', %L::jsonb)', v_o, rc_test.emp('W1'), rc_test.items(rc_test.dmi('QD-ABD'), 1)), 'không mở', 'QĐ-3 không gọi thêm vào phiên đã đóng', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_abandon_session(%s, %s, ''lần hai'')', v_s, rc_test.emp('M1')), 'đã CLOSED', 'QĐ-3 xử lý lần hai', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_cancel_item_exception(%s, %s, ''muộn'')', s, rc_test.emp('M1')), 'đã CLOSED', 'QĐ-3 không hủy món ngoại lệ sau khi đóng', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM invoices WHERE id = %s', v_inv), 'không xóa', 'QĐ-3 không xóa hóa đơn', 'RC409');
    PERFORM rc_test.fails(format('UPDATE invoices SET uncollected_reason = NULL WHERE id = %s', v_inv), 'bất biến', 'QĐ-3 không xóa lý do', 'RC409');
END $$;

DO $$
DECLARE
    v_o    BIGINT;
    v_s    BIGINT;
    s      BIGINT;
    s2     BIGINT;
    p      BIGINT;
    c      BIGINT;
    v_inv  BIGINT;
    v_ref  TEXT;
    v_pay  NUMERIC := (SELECT COALESCE(SUM(amount), 0) FROM payments);
BEGIN
    -- (B) đang chờ QR: yêu cầu thanh toán bị hủy, hóa đơn UNCOLLECTED, QR đến muộn là sự cố đối soát
    v_o := rc_test.open('A02');
    v_s := (SELECT table_session_id FROM orders WHERE id = v_o);
    s := rc_test.send(v_o, 'a2-s', 'QD-ABD', 1);
    PERFORM rc_test.serve(s);
    v_inv := rc_checkout_invoice(v_o, rc_test.emp('C1'));
    v_ref := rc_start_qr(v_inv, rc_test.emp('C1'));
    PERFORM rc_test.ok(rc_abandon_session(v_s, rc_test.emp('M1'), 'Khách bỏ về khi đang quét QR') = v_inv, 'QĐ-3 (B) cùng hóa đơn');
    PERFORM rc_test.ok((SELECT status = 'UNCOLLECTED' AND total = 30000 FROM invoices WHERE id = v_inv), 'QĐ-3 (B) UNCOLLECTED');
    PERFORM rc_test.ok((SELECT status = 'CANCELLED' AND cancelled_by_id = rc_test.emp('M1') AND cancel_reason LIKE 'Khách bỏ về:%'
                          FROM payment_requests WHERE payment_reference = v_ref), 'QĐ-3 (B) yêu cầu QR bị hủy: người + lý do');
    PERFORM rc_test.ok(rc_process_verified_qr('FT-ABD-LATE', v_ref, 30000, '{}') = 'AFTER_CANCEL', 'QĐ-3 (B) QR đến muộn → AFTER_CANCEL');
    PERFORM rc_test.ok((SELECT resolution_status = 'OPEN' FROM payment_webhook_events WHERE provider_transaction_code = 'FT-ABD-LATE'), 'QĐ-3 (B) mở sự cố đối soát');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM payments WHERE invoice_id = v_inv) AND (SELECT status FROM invoices WHERE id = v_inv) = 'UNCOLLECTED',
                       'QĐ-3 (B) QR muộn không tạo payment, không đổi hóa đơn');

    -- (C) không có món nào đã phục vụ: không có nghĩa vụ → đơn CANCELLED, không hóa đơn
    v_o := rc_test.open('A03');
    v_s := (SELECT table_session_id FROM orders WHERE id = v_o);
    p := rc_test.send(v_o, 'a3-p', 'QD-ABD', 1);
    c := rc_test.send(v_o, 'a3-c', 'QD-ABD', 1);
    PERFORM rc_accept_item(c, rc_test.emp('K1'));
    PERFORM rc_start_cooking(c, rc_test.emp('K1'));
    PERFORM rc_test.ok(rc_abandon_session(v_s, rc_test.emp('M1'), 'Khách rời trước khi có món') IS NULL, 'QĐ-3 (C) không có nghĩa vụ → NULL');
    PERFORM rc_test.ok((SELECT status FROM orders WHERE id = v_o) = 'CANCELLED' AND NOT EXISTS (SELECT 1 FROM invoices WHERE order_id = v_o), 'QĐ-3 (C) đơn CANCELLED, không hóa đơn');
    PERFORM rc_test.ok(rc_test.state(p) = 'CANCELLED' AND rc_test.state(c) = 'CANCELLED' AND (SELECT status FROM dining_tables WHERE code = 'A03') = 'AVAILABLE', 'QĐ-3 (C) dòng hủy, bàn trống');

    -- (D) hóa đơn DRAFT (đã mở lại) + món mới đã phục vụ: nghĩa vụ gồm cả dòng cũ và mới
    v_o := rc_test.open('A04');
    v_s := (SELECT table_session_id FROM orders WHERE id = v_o);
    s := rc_test.send(v_o, 'a4-s', 'QD-ABD', 1);
    PERFORM rc_test.serve(s);
    v_inv := rc_checkout_invoice(v_o, rc_test.emp('C1'));
    PERFORM rc_reopen_invoice(v_inv, rc_test.emp('C1'), 'Khách gọi thêm');
    s2 := rc_test.send(v_o, 'a4-s2', 'QD-ABD', 1);
    PERFORM rc_test.serve(s2);
    PERFORM rc_test.ok(rc_abandon_session(v_s, rc_test.emp('M1'), 'Khách rời sau khi gọi thêm') = v_inv, 'QĐ-3 (D) cùng hóa đơn');
    PERFORM rc_test.ok((SELECT status = 'UNCOLLECTED' AND total = 60000 FROM invoices WHERE id = v_inv)
                       AND (SELECT count(*) FROM invoice_items WHERE invoice_id = v_inv) = 2, 'QĐ-3 (D) nghĩa vụ gồm cả dòng cũ và mới');
    PERFORM rc_test.ok((SELECT COALESCE(SUM(amount), 0) FROM payments) = v_pay, 'QĐ-3 không phát sinh doanh thu ở bất kỳ nhánh nào');
END $$;

-- =====================================================================
-- GRD: guard trigger — DML trực tiếp (bỏ qua hàm rc_*) không phá được trạng thái, snapshot, sổ kho, chứng từ
-- =====================================================================
DO $$
DECLARE
    v_o BIGINT;
    v_s BIGINT;
    x   BIGINT;
BEGIN
    v_o := rc_test.open('G01');
    v_s := (SELECT table_session_id FROM orders WHERE id = v_o);
    x := rc_test.send(v_o, 'g1-x', 'QD-NULL', 1);
    -- dòng món: snapshot bất biến, không xóa, không nhảy trạng thái
    PERFORM rc_test.fails(format('UPDATE order_items SET unit_price = 1 WHERE id = %s', x), 'snapshot bất biến', 'GRD sửa giá snapshot', 'RC409');
    PERFORM rc_test.fails(format('UPDATE order_items SET quantity = 9 WHERE id = %s', x), 'snapshot bất biến', 'GRD sửa số lượng', 'RC409');
    PERFORM rc_test.fails(format('UPDATE order_items SET item_name_snapshot = ''x'' WHERE id = %s', x), 'snapshot bất biến', 'GRD sửa tên', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM order_items WHERE id = %s', x), 'không xóa dữ liệu giao dịch', 'GRD xóa dòng món', 'RC409');
    PERFORM rc_test.fails(format('UPDATE order_items SET status = ''SERVED'' WHERE id = %s', x), 'không chuyển PENDING → SERVED', 'GRD nhảy PENDING → SERVED', 'RC409');
    PERFORM rc_test.fails(format('UPDATE order_items SET status = ''READY'' WHERE id = %s', x), 'không chuyển PENDING → READY', 'GRD nhảy PENDING → READY', 'RC409');
    PERFORM rc_test.fails(format('UPDATE order_items SET status = ''COOKING'' WHERE id = %s', x), 'không chuyển PENDING → COOKING', 'GRD nhảy PENDING → COOKING (bỏ qua tiêu hao)', 'RC409');
    -- đổi trạng thái hợp lệ nhưng không ghi sự kiện: bị bắt khi COMMIT
    PERFORM rc_test.fails_commit(format('UPDATE order_items SET status = ''ACCEPTED'' WHERE id = %s;', x), 'không có sự kiện tương ứng', 'GRD đổi trạng thái không ghi nhật ký', 'RC500');
    -- nhật ký: chỉ thêm, đúng chuỗi, khớp trạng thái hiện tại
    PERFORM rc_test.fails(format('INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status) VALUES (%s, %s, ''ACCEPTED'', ''COOKING'')', x, rc_test.emp('K1')),
                          'không nối tiếp', 'GRD sự kiện sai chuỗi', 'RC409');
    PERFORM rc_test.fails(format('INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status) VALUES (%s, %s, ''PENDING'', ''ACCEPTED'')', x, rc_test.emp('K1')),
                          'phải khớp trạng thái hiện tại', 'GRD sự kiện không khớp trạng thái dòng', 'RC409');
    PERFORM rc_test.fails(format('INSERT INTO order_item_events (order_item_id, actor_id, from_status, to_status, reason) VALUES (%s, %s, ''PENDING'', ''CANCELLED'', '' '')', x, rc_test.emp('K1')),
                          'ck_order_item_event_reason|phải khớp', 'GRD hủy không lý do', NULL);
    PERFORM rc_test.fails(format('UPDATE order_item_events SET reason = ''sửa'' WHERE order_item_id = %s', x), 'chỉ-thêm', 'GRD sửa nhật ký', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM order_item_events WHERE order_item_id = %s', x), 'chỉ-thêm', 'GRD xóa nhật ký', 'RC409');
    -- đơn / phiên / bàn: không đóng sai điều kiện
    PERFORM rc_test.fails(format('UPDATE orders SET status = ''CLOSED'' WHERE id = %s', v_o), 'chỉ đóng sau khi hóa đơn', 'GRD đóng đơn khi chưa có hóa đơn', 'RC409');
    PERFORM rc_test.fails(format('UPDATE orders SET status = ''CANCELLED'' WHERE id = %s', v_o), 'còn món', 'GRD hủy đơn còn món', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM orders WHERE id = %s', v_o), 'không xóa dữ liệu giao dịch', 'GRD xóa đơn', 'RC409');
    PERFORM rc_test.fails(format('UPDATE table_sessions SET status = ''CLOSED'', closed_at = now(), closed_by_id = %s WHERE id = %s', rc_test.emp('M1'), v_s),
                          'đơn còn OPEN', 'GRD đóng phiên khi đơn còn mở', 'RC409');
    PERFORM rc_test.fails(format('UPDATE table_sessions SET opened_by_id = %s WHERE id = %s', rc_test.emp('K1'), v_s), 'bất biến', 'GRD sửa người mở phiên', 'RC409');
    PERFORM rc_test.fails('UPDATE dining_tables SET status = ''AVAILABLE'' WHERE code = ''G01''', 'còn phiên OPEN', 'GRD trả bàn khi còn phiên mở', 'RC409');
    PERFORM rc_test.fails('UPDATE dining_tables SET status = ''OCCUPIED'' WHERE code = ''B01''', 'chỉ OCCUPIED khi có phiên OPEN', 'GRD đánh dấu bàn có khách khi không có phiên', 'RC409');
    PERFORM rc_cancel_item(x, rc_test.emp('W1'), 'Dọn ca kiểm thử');
    PERFORM rc_close_empty_session(v_s, rc_test.emp('W1'));
END $$;

DO $$
BEGIN
    -- số dư luôn bằng sổ kho (kiểm ở COMMIT): sửa tay số dư, thêm movement lẻ, sửa số đang giữ
    PERFORM rc_test.fails_commit(format('UPDATE inventory_batches SET quantity_on_hand = quantity_on_hand + 1 WHERE id = %s;', rc_test.id('LOT_BO')),
                                 'số dư.*tổng sổ kho', 'GRD sửa tay số dư RAW', 'RC500');
    PERFORM rc_test.fails_commit(format('UPDATE kitchen_batches SET quantity_on_hand = quantity_on_hand - 1 WHERE id = %s;', rc_test.id('KB_EGG')),
                                 'số dư.*tổng sổ kho', 'GRD sửa tay số dư bếp', 'RC500');
    PERFORM rc_test.fails_commit(format('UPDATE stock_issue_items SET wip_remaining_quantity = 1 WHERE id = %s;', rc_test.id('WIP_BO')),
                                 'số dư.*tổng sổ kho', 'GRD sửa tay số WIP', 'RC500');
    PERFORM rc_test.fails_commit(format('INSERT INTO stock_movements (movement_type, raw_batch_id, quantity_delta, event_key) VALUES (''RECEIPT'', %s, 5, ''grd-1'');', rc_test.id('LOT_BO')),
                                 'số dư.*tổng sổ kho', 'GRD movement lẻ không đổi số dư', 'RC500');
    PERFORM rc_test.fails_commit(format('UPDATE kitchen_batches SET reserved_quantity = 1 WHERE id = %s;', rc_test.id('KB_EGG')),
                                 'reserved_quantity.*tổng reservation ACTIVE', 'GRD sửa tay số đang giữ', 'RC500');
    PERFORM rc_test.fails_commit(format('UPDATE kitchen_batches SET quantity_on_hand = -1 WHERE id = %s;', rc_test.id('KB_EGG')),
                                 'ck_kitchen_batches_quantity_on_hand_nonneg', 'GRD tồn âm', '23514');
    -- reservation đã kết thúc bất biến; sổ kho chỉ-thêm; lô không xóa
    PERFORM rc_test.fails('UPDATE stock_reservations SET quantity = 99 WHERE status = ''CONSUMED''', 'bất biến', 'GRD sửa reservation đã CONSUMED', 'RC409');
    PERFORM rc_test.fails('DELETE FROM stock_reservations', 'không xóa dữ liệu giao dịch', 'GRD xóa reservation', 'RC409');
    PERFORM rc_test.fails('UPDATE stock_movements SET quantity_delta = 0', 'chỉ-thêm', 'GRD sửa sổ kho', 'RC409');
    PERFORM rc_test.fails('DELETE FROM stock_movements', 'chỉ-thêm', 'GRD xóa sổ kho', 'RC409');
    PERFORM rc_test.fails('DELETE FROM inventory_batches', 'không xóa dữ liệu giao dịch', 'GRD xóa lô RAW', 'RC409');
    PERFORM rc_test.fails('DELETE FROM kitchen_batches', 'không xóa dữ liệu giao dịch', 'GRD xóa lô bếp', 'RC409');
    -- chứng từ đã chốt: bất biến, không xóa, không lùi trạng thái
    PERFORM rc_test.fails(format('DELETE FROM stock_receipts WHERE id = %s', rc_test.id('R1')), 'không xóa', 'GRD xóa phiếu nhập đã CONFIRMED', 'RC409');
    PERFORM rc_test.fails(format('UPDATE stock_receipts SET status = ''DRAFT'' WHERE id = %s', rc_test.id('R1')), 'không chuyển CONFIRMED → DRAFT', 'GRD lùi trạng thái phiếu nhập', 'RC409');
    PERFORM rc_test.fails(format('UPDATE stock_issues SET status = ''ISSUED'' WHERE id = %s', rc_test.id('I1')), 'không chuyển RECEIVED → ISSUED', 'GRD lùi trạng thái phiếu xuất', 'RC409');
    PERFORM rc_test.fails(format('UPDATE kitchen_preparations SET input_quantity = 1 WHERE id = %s', rc_test.id('PREP1')), 'bất biến', 'GRD sửa phiếu sơ chế đã CONFIRMED', 'RC409');
    PERFORM rc_test.fails('UPDATE stock_returns SET reason = ''sửa'' WHERE status = ''CONFIRMED''', 'bất biến', 'GRD sửa phiếu trả đã CONFIRMED', 'RC409');
    PERFORM rc_test.fails('UPDATE stock_counts SET note = ''sửa'' WHERE status = ''CONFIRMED''', 'bất biến', 'GRD sửa phiếu kiểm kê đã CONFIRMED', 'RC409');
    PERFORM rc_test.fails('UPDATE stock_adjustments SET reason = ''sửa'' WHERE status = ''CONFIRMED''', 'bất biến', 'GRD sửa phiếu hủy hàng đã CONFIRMED', 'RC409');
    -- thanh toán: chỉ-thêm; chỉ ghi cho hóa đơn PAYMENT_PENDING; yêu cầu đã kết thúc bất biến
    PERFORM rc_test.fails('UPDATE payments SET amount = 1', 'chỉ-thêm', 'GRD sửa payments', 'RC409');
    PERFORM rc_test.fails('INSERT INTO payments (invoice_id, payment_request_id, amount, method, business_date) SELECT invoice_id, id, 1, ''CASH'', rc_business_date(now()) FROM payment_requests WHERE status = ''PAID'' LIMIT 1',
                          'chỉ thu được khi PAYMENT_PENDING', 'GRD ghi payment cho hóa đơn đã PAID', 'RC409');
    PERFORM rc_test.fails('UPDATE payment_requests SET amount_due = 1 WHERE status IN (''PAID'', ''CANCELLED'')', 'bất biến', 'GRD sửa yêu cầu thanh toán đã kết thúc', 'RC409');
    PERFORM rc_test.fails('DELETE FROM payment_requests', 'không xóa dữ liệu giao dịch', 'GRD xóa yêu cầu thanh toán', 'RC409');
    PERFORM rc_test.fails('DELETE FROM payment_webhook_events', 'không được xóa', 'GRD xóa webhook', 'RC409');
    PERFORM rc_test.fails('UPDATE invoices SET status = ''DRAFT'' WHERE status = ''PAID''', 'bất biến', 'GRD mở lại hóa đơn PAID', 'RC409');
    PERFORM rc_test.fails('UPDATE invoices SET order_id = order_id + 1000 WHERE status = ''PAID''', 'bất biến|định danh', 'GRD đổi đơn của hóa đơn', 'RC409');
END $$;

-- ---------------------------------------------------------------------
-- CON: ràng buộc NOT NULL / FK / UNIQUE / CHECK không hợp lệ
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_cat BIGINT := rc_test.id('CAT_MAIN');
    v_rc  BIGINT;
BEGIN
    -- NOT NULL (23502)
    PERFORM rc_test.fails('INSERT INTO employees (employee_code, department_id) VALUES (''N1'', 1)', 'full_name', 'CON NOT NULL employees.full_name', '23502');
    PERFORM rc_test.fails(format('INSERT INTO menu_items (menu_category_id, name) VALUES (%s, ''N-no-price'')', v_cat), 'price', 'CON NOT NULL menu_items.price', '23502');
    PERFORM rc_test.fails(format('INSERT INTO accounts (employee_id, username) VALUES (%s, ''n-acc'')', rc_test.emp('M1')), 'password_hash', 'CON NOT NULL accounts.password_hash', '23502');
    PERFORM rc_test.fails(format('UPDATE kitchen_batches SET quantity_on_hand = NULL WHERE id = %s', rc_test.id('KB_EGG')), 'quantity_on_hand', 'CON NOT NULL kitchen_batches.quantity_on_hand', '23502');
    PERFORM rc_test.fails('INSERT INTO operating_expenses (business_date, category, description, payment_method, fund_source, recorded_by_id, paid_at, request_key) VALUES (rc_business_date(now()), ''OTHER'', ''x'', ''CASH'', ''OWNER'', 1, now(), ''n-exp'')',
                          'amount', 'CON NOT NULL operating_expenses.amount', '23502');
    -- FK (23503)
    PERFORM rc_test.fails(format('INSERT INTO order_batches (order_id, waiter_id, request_key) VALUES (999999, %s, ''fk-1'')', rc_test.emp('W1')), 'violates foreign key', 'CON FK order_batches.order_id', '23503');
    PERFORM rc_test.fails(format('INSERT INTO table_sessions (dining_table_id, opened_by_id) VALUES (999999, %s)', rc_test.emp('W1')), 'violates foreign key', 'CON FK table_sessions.dining_table_id', '23503');
    PERFORM rc_test.fails('INSERT INTO recipes (menu_item_id, version) VALUES (999999, 9)', 'violates foreign key', 'CON FK recipes.menu_item_id', '23503');
    PERFORM rc_test.fails('INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES (999999, 1, 1, ''DIRECT'')', 'violates foreign key', 'CON FK stock_issue_items.stock_issue_id', '23503');
    PERFORM rc_test.fails(format('INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, request_key) VALUES (rc_business_date(now()), ''OTHER'', ''x'', 1, ''CASH'', ''OWNER'', 999999, %s, now(), ''n-exp2'')', rc_test.emp('M1')),
                          'violates foreign key', 'CON FK operating_expenses.stock_receipt_id', '23503');
    PERFORM rc_test.fails(format('DELETE FROM employees WHERE id = %s', rc_test.emp('W1')), 'violates foreign key', 'CON FK không xóa nhân viên đã có lịch sử', '23503');
    -- UNIQUE (23505)
    PERFORM rc_test.fails('INSERT INTO dining_tables (code, capacity) VALUES (''B01'', 4)', 'dining_tables_code_key', 'CON UNIQUE dining_tables.code', '23505');
    PERFORM rc_test.fails('INSERT INTO daily_menus (business_date) VALUES (rc_business_date(now()))', 'daily_menus_business_date_key', 'CON UNIQUE daily_menus.business_date', '23505');
    PERFORM rc_test.fails(format('INSERT INTO accounts (employee_id, username, password_hash) VALUES (%s, ''dup'', ''x'')', rc_test.emp('M1')), 'accounts_employee_id_key', 'CON UNIQUE accounts.employee_id', '23505');
    PERFORM rc_test.fails(format('INSERT INTO order_batches (order_id, waiter_id, request_key) VALUES (%s, %s, ''b01-1'')', rc_test.id('O_B01'), rc_test.emp('W1')), 'order_batches_request_key_key', 'CON UNIQUE order_batches.request_key', '23505');
    PERFORM rc_test.fails('INSERT INTO payment_webhook_events (provider_transaction_code, payment_reference, amount, processing_result, raw_payload) VALUES (''FT-001'', ''X'', 1, ''MISMATCH'', ''{}'')',
                          'provider_transaction_code_key', 'CON UNIQUE webhook.provider_transaction_code', '23505');
    PERFORM rc_test.fails(format('INSERT INTO kitchen_ingredient_types (raw_ingredient_id, name, handling_type) VALUES (%s, ''Hành 2'', ''DIRECT'')', rc_test.ing('Hành tây')),
                          'ux_kitchen_direct_one', 'CON UNIQUE mỗi nguyên liệu một loại DIRECT', '23505');
    PERFORM rc_test.fails('INSERT INTO recipes (menu_item_id, version, is_current) SELECT menu_item_id, 9, true FROM recipes WHERE is_current LIMIT 1',
                          'ux_recipes_current', 'CON UNIQUE một công thức hiện hành / món', '23505');
    PERFORM rc_test.fails(format('INSERT INTO stock_reservations (order_item_id, kitchen_batch_id, quantity) SELECT order_item_id, kitchen_batch_id, 1 FROM stock_reservations LIMIT 1'),
                          'ux_stock_reservation_batch', 'CON UNIQUE reservation theo (dòng món, lô bếp)', '23505');
    -- CHECK (23514)
    PERFORM rc_test.fails(format('INSERT INTO menu_items (menu_category_id, name, price) VALUES (%s, ''N-neg'', -1)', v_cat), 'ck_price_nonnegative', 'CON CHECK giá âm', '23514');
    PERFORM rc_test.fails('INSERT INTO dining_tables (code, capacity) VALUES (''N-cap'', 0)', 'ck_dining_tables_capacity_positive', 'CON CHECK sức chứa 0', '23514');
    PERFORM rc_test.fails('INSERT INTO ingredients (name, base_unit, min_quantity) VALUES (''N-min'', ''kg'', -1)', 'ck_inventory_minimum_nonnegative', 'CON CHECK định mức âm', '23514');
    PERFORM rc_test.fails('UPDATE dining_tables SET status = ''BROKEN'' WHERE code = ''B01''', 'ck_dining_tables_status', 'CON CHECK miền trạng thái bàn', '23514');
    PERFORM rc_test.fails('INSERT INTO order_items (order_batch_id, daily_menu_item_id, quantity, item_name_snapshot, unit_price) SELECT order_batch_id, daily_menu_item_id, 0, ''x'', 1 FROM order_items LIMIT 1',
                          'ck_order_items_quantity_positive', 'CON CHECK số lượng món 0', '23514');
    INSERT INTO stock_receipts (created_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_rc;
    PERFORM rc_test.fails(format('INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit) VALUES (%s, %s, 1, ''kg'', 0, 1)', v_rc, rc_test.ing('Hành tây')),
                          'ck_stock_receipt_items_conversion_factor_positive', 'CON CHECK hệ số quy đổi 0', '23514');
    PERFORM rc_test.fails(format('INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit) VALUES (%s, %s, -1, ''kg'', 1, 1)', v_rc, rc_test.ing('Hành tây')),
                          'ck_stock_receipt_items_input_quantity_positive', 'CON CHECK số lượng nhập âm', '23514');
    UPDATE stock_receipts SET status = 'CANCELLED' WHERE id = v_rc;
    PERFORM rc_test.fails('INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) SELECT stock_issue_id, inventory_batch_id, 1, ''DIRECT'' FROM stock_issue_items LIMIT 1',
                          'dòng chi tiết bị khóa|ux_stock_issue_batch_mode', 'CON chứng từ không nhận thêm dòng khi đã khóa/trùng', NULL);
    PERFORM rc_test.fails('INSERT INTO invoices (order_id, total, created_by_id) SELECT id, -5, 1 FROM orders LIMIT 1', 'ck_invoices_total_nonneg|duplicate', 'CON CHECK tổng hóa đơn âm', NULL);
    PERFORM rc_test.fails('UPDATE invoices SET uncollected_reason = ''x'' WHERE status = ''PAID''', 'bất biến', 'CON hóa đơn PAID không mang lý do bỏ về', 'RC409');
END $$;

-- ---------------------------------------------------------------------
-- FNC (kiểm toán hàm, docs/07): mỗi hàm rc_* có ca thành công VÀ ca thất bại có mã lỗi — máy trạng thái món, chứng từ kho
-- rỗng / không tồn tại / thiếu lý do, kiểm kê và hủy hàng trên cả ba nguồn RAW / WIP / kho bếp (không tạo payments)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_ghost CONSTANT BIGINT := 987654321;
    v_kb    BIGINT := rc_test.id('KB_EGG');
    v_o     BIGINT;
    v_item  BIGINT;
    v_item2 BIGINT;
    v_on    NUMERIC;
    v_res   NUMERIC;
    v_r     BIGINT;
    v_lot   BIGINT;
    v_i     BIGINT;
    v_line  BIGINT;
    v_c     BIGINT;
    v_adj   BIGINT;
BEGIN
    -- 1. máy trạng thái món: sai trạng thái nguồn → RC409; không tồn tại → RC422; từ chối cần lý do
    v_o := rc_test.open('F01');
    v_item := rc_test.send(v_o, 'f01-1', 'QD-NULL', 2);
    v_item2 := rc_test.send(v_o, 'f01-2', 'QD-NULL', 1);
    PERFORM rc_test.fails(format('SELECT rc_accept_item(%s, %s)', v_ghost, rc_test.emp('K1')), 'không tồn tại', 'FNC rc_accept_item món không tồn tại', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_start_cooking(%s, %s)', v_ghost, rc_test.emp('K1')), 'không tồn tại', 'FNC rc_start_cooking món không tồn tại', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_start_cooking(%s, %s)', v_item, rc_test.emp('K1')), 'không phải ACCEPTED', 'FNC nấu món chưa nhận', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_finish_dish(%s, %s)', v_item, rc_test.emp('K1')), 'không chuyển sang READY', 'FNC xong món chưa nấu', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_serve_item(%s, %s)', v_item, rc_test.emp('W1')), 'không chuyển sang SERVED', 'FNC phục vụ món chưa xong', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_reject_item(%s, %s, '' '')', v_item2, rc_test.emp('K1')), 'bắt buộc có lý do', 'FNC từ chối món không lý do', 'RC422');
    PERFORM rc_test.ok(rc_accept_item(v_item, rc_test.emp('K1')) = 'ACCEPTED', 'FNC nhận món');
    PERFORM rc_test.fails(format('SELECT rc_accept_item(%s, %s)', v_item, rc_test.emp('K1')), 'không phải PENDING', 'FNC nhận món hai lần', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_reject_item(%s, %s, ''x'')', v_item, rc_test.emp('K1')), 'không chuyển sang REJECTED', 'FNC từ chối món đã nhận', 'RC409');
    PERFORM rc_reject_item(v_item2, rc_test.emp('K1'), 'Hết nguyên liệu phụ');
    PERFORM rc_test.ok(rc_test.state(v_item2) = 'REJECTED' AND rc_quota_used(rc_test.dmi('QD-NULL')) >= 2, 'FNC từ chối món: REJECTED không chiếm suất');
    PERFORM rc_test.fails(format('SELECT rc_reject_item(%s, %s, ''lần 2'')', v_item2, rc_test.emp('K1')), 'không chuyển sang REJECTED', 'FNC từ chối món hai lần', 'RC409');

    -- 2. kho bếp đang có phần giữ: kiểm kê / hủy hàng không được hạ dưới phần đang giữ
    SELECT quantity_on_hand, reserved_quantity INTO v_on, v_res FROM kitchen_batches WHERE id = v_kb;
    PERFORM rc_test.ok(v_res >= 2, 'FNC kho trứng đang giữ cho món vừa nhận');
    INSERT INTO stock_counts (created_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_c;
    INSERT INTO stock_count_items (stock_count_id, kitchen_batch_id, book_quantity, actual_quantity, reason)
    VALUES (v_c, v_kb, v_on, v_res - 1, 'Đếm thiếu');
    PERFORM rc_test.fails(format('SELECT rc_confirm_count(%s, %s)', v_c, rc_test.emp('M1')), 'nhỏ hơn lượng đang giữ',
                          'FNC kiểm kê bếp thấp hơn phần đang giữ', 'RC409');
    UPDATE stock_count_items SET actual_quantity = v_on - 1 WHERE stock_count_id = v_c;
    PERFORM rc_confirm_count(v_c, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.kb('KB_EGG') = v_on - 1 AND rc_test.kr('KB_EGG') = v_res, 'FNC kiểm kê kho bếp: tồn −1, phần giữ nguyên');
    INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key)
    VALUES ('DAMAGE', 'Đổ vỡ', rc_test.emp('K1'), 'fnc-adj-k') RETURNING id INTO v_adj;
    INSERT INTO stock_adjustment_items (stock_adjustment_id, kitchen_batch_id, quantity_delta)
    VALUES (v_adj, v_kb, -(rc_test.kb('KB_EGG') - rc_test.kr('KB_EGG') + 1));
    PERFORM rc_test.fails(format('SELECT rc_confirm_adjustment(%s, %s)', v_adj, rc_test.emp('M1')), 'vượt lượng khả dụng',
                          'FNC hủy hàng kho bếp vượt phần khả dụng', 'RC409');
    UPDATE stock_adjustment_items SET quantity_delta = -1 WHERE stock_adjustment_id = v_adj;
    PERFORM rc_confirm_adjustment(v_adj, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.kb('KB_EGG') = v_on - 2 AND rc_test.kr('KB_EGG') = v_res, 'FNC hủy hàng kho bếp: tồn −1, phần giữ nguyên');

    -- 3. RAW → bàn giao PREPARE → WIP: phiếu xuất rỗng / chưa xuất / thiếu lý do; kiểm kê và hủy hàng WIP + RAW
    INSERT INTO ingredients (name, base_unit, min_quantity, is_expiry_tracked) VALUES ('Muối', 'kg', 0, false);
    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('NCC muối', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit)
    VALUES (v_r, rc_test.ing('Muối'), 10, 'kg', 1, 1000);
    PERFORM rc_confirm_receipt(v_r, rc_test.emp('M1'));
    SELECT b.id INTO v_lot FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.stock_receipt_id = v_r;
    PERFORM rc_test.put('LOT_MUOI', v_lot);
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'rỗng', 'FNC xuất kho phiếu rỗng', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_receive_issue(%s, %s)', v_i, rc_test.emp('K1')), 'không phải ISSUED', 'FNC nhận phiếu chưa xuất', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_receive_issue(%s, %s)', v_ghost, rc_test.emp('K1')), 'không tồn tại', 'FNC nhận phiếu không tồn tại', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_cancel_issue(%s, %s, '' '')', v_i, rc_test.emp('M1')), 'bắt buộc có lý do', 'FNC hủy phiếu xuất không lý do', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_cancel_issue(%s, %s, ''x'')', v_ghost, rc_test.emp('M1')), 'không tồn tại', 'FNC hủy phiếu xuất không tồn tại', 'RC422');
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode)
    VALUES (v_i, v_lot, 4, 'PREPARE') RETURNING id INTO v_line;
    PERFORM rc_issue_stock(v_i, rc_test.emp('M1'));
    PERFORM rc_receive_issue(v_i, rc_test.emp('K1'));
    PERFORM rc_test.ok(rc_test.raw('LOT_MUOI') = 6 AND (SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = v_line) = 4,
                       'FNC xuất rồi nhận: RAW −4, WIP +4');

    INSERT INTO stock_counts (created_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_c;
    PERFORM rc_test.fails(format('SELECT rc_confirm_count(%s, %s)', v_c, rc_test.emp('M1')), 'rỗng', 'FNC kiểm kê phiếu rỗng', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_confirm_count(%s, %s)', v_ghost, rc_test.emp('M1')), 'không tồn tại', 'FNC kiểm kê không tồn tại', 'RC422');
    INSERT INTO stock_count_items (stock_count_id, wip_issue_item_id, book_quantity, actual_quantity, reason) VALUES (v_c, v_line, 4, 3.5, 'Rơi vãi');
    INSERT INTO stock_count_items (stock_count_id, raw_batch_id, book_quantity, actual_quantity, reason) VALUES (v_c, v_lot, 6, 6.2, 'Cân lại');
    PERFORM rc_confirm_count(v_c, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.raw('LOT_MUOI') = 6.2 AND (SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = v_line) = 3.5,
                       'FNC kiểm kê WIP và RAW về số thực đếm');
    PERFORM rc_test.ok((SELECT SUM(quantity_delta) FROM stock_movements
                         WHERE movement_type = 'COUNT_ADJUST' AND (wip_issue_item_id = v_line OR raw_batch_id = v_lot)) = -0.3,
                       'FNC kiểm kê ghi COUNT_ADJUST đúng chênh lệch (−0,5 WIP, +0,2 RAW)');

    INSERT INTO stock_adjustments (adjustment_type, reason, created_by_id, request_key)
    VALUES ('DAMAGE', 'Ẩm mốc', rc_test.emp('K1'), 'fnc-adj-r') RETURNING id INTO v_adj;
    PERFORM rc_test.fails(format('SELECT rc_confirm_adjustment(%s, %s)', v_adj, rc_test.emp('M1')), 'rỗng', 'FNC hủy hàng phiếu rỗng', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_confirm_adjustment(%s, %s)', v_ghost, rc_test.emp('M1')), 'không tồn tại', 'FNC hủy hàng không tồn tại', 'RC422');
    INSERT INTO stock_adjustment_items (stock_adjustment_id, raw_batch_id, quantity_delta) VALUES (v_adj, v_lot, -7);
    PERFORM rc_test.fails(format('SELECT rc_confirm_adjustment(%s, %s)', v_adj, rc_test.emp('M1')), 'vượt lượng khả dụng', 'FNC hủy hàng RAW vượt tồn', 'RC409');
    UPDATE stock_adjustment_items SET quantity_delta = -1.2 WHERE stock_adjustment_id = v_adj;
    INSERT INTO stock_adjustment_items (stock_adjustment_id, wip_issue_item_id, quantity_delta) VALUES (v_adj, v_line, -0.5);
    PERFORM rc_confirm_adjustment(v_adj, rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.raw('LOT_MUOI') = 5 AND (SELECT wip_remaining_quantity FROM stock_issue_items WHERE id = v_line) = 3,
                       'FNC hủy hàng RAW và WIP');

    -- 4. phiếu nhập, lô RAW, bàn, đối soát, yêu cầu thanh toán: đầu vào sai
    INSERT INTO stock_receipts (created_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_r;
    PERFORM rc_test.fails(format('SELECT rc_confirm_receipt(%s, %s)', v_r, rc_test.emp('M1')), 'rỗng', 'FNC nhập kho phiếu rỗng', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_confirm_receipt(%s, %s)', v_ghost, rc_test.emp('M1')), 'không tồn tại', 'FNC nhập kho không tồn tại', 'RC422');
    UPDATE ingredients SET is_active = false WHERE name = 'Muối';
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor, unit_price_per_input_unit)
    VALUES (v_r, rc_test.ing('Muối'), 1, 'kg', 1, 1000);
    PERFORM rc_test.fails(format('SELECT rc_confirm_receipt(%s, %s)', v_r, rc_test.emp('M1')), 'ngừng sử dụng', 'FNC nhập nguyên liệu đã ngừng sử dụng', 'RC422');
    UPDATE ingredients SET is_active = true WHERE name = 'Muối';
    PERFORM rc_test.fails(format('SELECT rc_mark_batch_unusable(''BEP'', %s, %s, ''x'')', v_lot, rc_test.emp('K1')), 'RAW hoặc KITCHEN', 'FNC đánh dấu lô sai loại', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_mark_batch_unusable(''RAW'', %s, %s, ''x'')', v_ghost, rc_test.emp('M1')), 'không tồn tại hoặc đã đánh dấu', 'FNC đánh dấu lô không tồn tại', 'RC409');
    PERFORM rc_test.ok(rc_mark_batch_unusable('RAW', v_lot, rc_test.emp('M1'), 'Ẩm mốc') = 0, 'FNC đánh dấu lô RAW không dùng được');
    PERFORM rc_test.fails(format('SELECT rc_mark_batch_unusable(''RAW'', %s, %s, ''lần 2'')', v_lot, rc_test.emp('M1')), 'đã đánh dấu', 'FNC đánh dấu lô hai lần', 'RC409');
    INSERT INTO dining_tables (code, capacity, is_active) VALUES ('F-OFF', 2, false);
    PERFORM rc_test.fails(format('SELECT rc_open_session(%s, %s)', rc_test.tbl('F-OFF'), rc_test.emp('W1')), 'ngừng sử dụng', 'FNC mở phiên ở bàn ngừng sử dụng', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_reconcile_cash(%L, NULL, 1, %s)', rc_business_date(now()), rc_test.emp('M1')), 'thiếu quỹ', 'FNC đối soát thiếu quỹ đầu ngày', 'RC422');
    PERFORM rc_test.fails(format('SELECT rc_cancel_payment_request(%s, %s, ''x'')', v_ghost, rc_test.emp('C1')), 'không tồn tại', 'FNC hủy yêu cầu thanh toán không tồn tại', 'RC422');

    -- 5. dọn: món đi hết vòng đời qua các hàm, rồi Manager hủy ngoại lệ (không thu tiền) và đóng phiên trống
    PERFORM rc_start_cooking(v_item, rc_test.emp('K1'));
    PERFORM rc_finish_dish(v_item, rc_test.emp('K1'));
    PERFORM rc_serve_item(v_item, rc_test.emp('W1'));
    PERFORM rc_test.ok(rc_test.state(v_item) = 'SERVED' AND rc_test.kr('KB_EGG') = v_res - 2, 'FNC món đi hết vòng đời; nấu tiêu hao phần giữ');
    PERFORM rc_cancel_item_exception(v_item, rc_test.emp('M1'), 'Dọn ca kiểm thử');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));
END $$;

-- ---------------------------------------------------------------------
-- FNC-EXP: không xuất từ lô RAW hết hạn / không dùng được; không cấp, không nấu từ lô bếp hết hạn (hạn trôi qua được mô phỏng
-- bằng cách lùi hạn dùng của lô; chứng từ nhập đã khóa nên trigger khóa dòng được tắt trong đúng giao dịch kiểm thử này)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_kb    BIGINT := rc_test.id('KB_EGG');
    v_until TIMESTAMPTZ;
    v_res   NUMERIC;
    v_r     BIGINT;
    v_bad   BIGINT;
    v_old   BIGINT;
    v_i     BIGINT;
    v_o     BIGINT;
    v_item  BIGINT;
    v_item2 BIGINT;
    v_item3 BIGINT;
BEGIN
    INSERT INTO ingredients (name, base_unit, min_quantity, is_expiry_tracked) VALUES ('Tiêu', 'kg', 0, true);
    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('NCC tiêu', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor,
                                     unit_price_per_input_unit, lot_code, expiry_date)
    VALUES (v_r, rc_test.ing('Tiêu'), 5, 'kg', 1, 1000, 'T-BAD', rc_business_date(now()) + 30),
           (v_r, rc_test.ing('Tiêu'), 5, 'kg', 1, 1000, 'T-OLD', rc_business_date(now()) + 30);
    PERFORM rc_confirm_receipt(v_r, rc_test.emp('M1'));
    SELECT b.id INTO v_bad FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.stock_receipt_id = v_r AND s.lot_code = 'T-BAD';
    SELECT b.id INTO v_old FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id
     WHERE s.stock_receipt_id = v_r AND s.lot_code = 'T-OLD';

    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES (v_i, v_bad, 1, 'PREPARE');
    PERFORM rc_mark_batch_unusable('RAW', v_bad, rc_test.emp('M1'), 'Ẩm mốc');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'không dùng được hoặc đã hết hạn',
                          'FNC-EXP không xuất từ lô RAW không dùng được', 'RC409');

    ALTER TABLE stock_receipt_items DISABLE TRIGGER trg_stock_receipt_items_lines;
    UPDATE stock_receipt_items SET expiry_date = rc_business_date(now()) - 1 WHERE lot_code = 'T-OLD';
    ALTER TABLE stock_receipt_items ENABLE TRIGGER trg_stock_receipt_items_lines;
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES (v_i, v_old, 1, 'PREPARE');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', v_i, rc_test.emp('M1')), 'không dùng được hoặc đã hết hạn',
                          'FNC-EXP không xuất từ lô RAW đã hết hạn', 'RC409');
    PERFORM rc_test.ok((SELECT quantity_on_hand FROM inventory_batches WHERE id = v_old) = 5
                       AND (SELECT status FROM stock_issues WHERE id = v_i) = 'DRAFT', 'FNC-EXP xuất bị chặn không trừ kho');

    -- lô bếp: hết hạn thì không được cấp cho món mới, không nấu được, không cấp lại được
    SELECT usable_until, reserved_quantity INTO v_until, v_res FROM kitchen_batches WHERE id = v_kb;
    v_o := rc_test.open('F02');
    v_item := rc_test.send(v_o, 'f02-1', 'QD-NULL', 1);
    UPDATE kitchen_batches SET usable_until = now() - interval '1 hour' WHERE id = v_kb;
    PERFORM rc_test.ok(rc_accept_item(v_item, rc_test.emp('K1')) = 'REJECTED', 'FNC-EXP lô bếp hết hạn không được cấp cho món mới');
    PERFORM rc_test.ok((SELECT reason FROM order_item_events WHERE order_item_id = v_item AND to_status = 'REJECTED') LIKE 'Thiếu nguyên liệu%'
                       AND rc_test.kr('KB_EGG') = v_res, 'FNC-EXP từ chối vì thiếu nguyên liệu, không để lại phần giữ');
    UPDATE kitchen_batches SET usable_until = v_until WHERE id = v_kb;
    v_item2 := rc_test.send(v_o, 'f02-2', 'QD-NULL', 1);
    PERFORM rc_test.ok(rc_accept_item(v_item2, rc_test.emp('K1')) = 'ACCEPTED', 'FNC-EXP lô còn hạn: nhận được');
    UPDATE kitchen_batches SET usable_until = now() - interval '1 hour' WHERE id = v_kb;
    PERFORM rc_test.fails(format('SELECT rc_start_cooking(%s, %s)', v_item2, rc_test.emp('K1')), 'hết hạn/không dùng được',
                          'FNC-EXP không nấu từ lô đang giữ đã hết hạn', 'RC409');
    PERFORM rc_test.fails(format('SELECT rc_reallocate_item(%s, %s)', v_item2, rc_test.emp('K1')), 'Thiếu nguyên liệu',
                          'FNC-EXP không cấp lại từ lô hết hạn', 'RC001');
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = v_res + 1, 'FNC-EXP cấp lại thất bại hoàn tác trọn vẹn: phần giữ nguyên');
    UPDATE kitchen_batches SET usable_until = v_until WHERE id = v_kb;
    -- lô bếp bị đánh dấu không dùng được (còn tồn): không cấp cho món mới; món đang giữ bị bật cờ ngoại lệ
    v_item3 := rc_test.send(v_o, 'f02-3', 'QD-NULL', 1);
    PERFORM rc_test.ok(rc_mark_batch_unusable('KITCHEN', v_kb, rc_test.emp('K1'), 'Hỏng tủ mát') >= 1, 'FNC-EXP đánh dấu lô bếp: bật cờ cho món đang giữ');
    PERFORM rc_test.ok(rc_accept_item(v_item3, rc_test.emp('K1')) = 'REJECTED', 'FNC-EXP lô bếp không dùng được không được cấp cho món mới');
    PERFORM rc_cancel_item(v_item2, rc_test.emp('W1'), 'Dọn ca kiểm thử');
    PERFORM rc_close_empty_session((SELECT table_session_id FROM orders WHERE id = v_o), rc_test.emp('W1'));
    PERFORM rc_test.ok(rc_test.kr('KB_EGG') = v_res, 'FNC-EXP dọn xong: phần giữ về như cũ');
END $$;

-- ---------------------------------------------------------------------
-- FNC-FEFO (spec M9): lô có hạn dùng được xuất trước lô không hạn (NULL xếp sau); cùng không hạn thì lô nhận trước xuất trước
-- ---------------------------------------------------------------------
CREATE FUNCTION rc_test.issue_one(p_lot BIGINT, p_qty NUMERIC) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_i BIGINT;
BEGIN
    INSERT INTO stock_issues (issued_by_id) VALUES (rc_test.emp('M1')) RETURNING id INTO v_i;
    INSERT INTO stock_issue_items (stock_issue_id, inventory_batch_id, quantity, fulfillment_mode) VALUES (v_i, p_lot, p_qty, 'PREPARE');
    RETURN v_i;
END $$;
CREATE FUNCTION rc_test.lot(p_code TEXT) RETURNS BIGINT LANGUAGE sql AS $$
    SELECT b.id FROM inventory_batches b JOIN stock_receipt_items s ON s.id = b.stock_receipt_item_id WHERE s.lot_code = p_code $$;
CREATE FUNCTION rc_test.raw_by_lot(p_code TEXT) RETURNS NUMERIC LANGUAGE sql AS $$
    SELECT quantity_on_hand FROM inventory_batches WHERE id = rc_test.lot(p_code) $$;
DO $$
DECLARE
    v_r BIGINT;
BEGIN
    INSERT INTO ingredients (name, base_unit, min_quantity, is_expiry_tracked) VALUES ('Dầu ăn', 'lít', 0, false);
    INSERT INTO stock_receipts (supplier_name, created_by_id) VALUES ('NCC dầu', rc_test.emp('M1')) RETURNING id INTO v_r;
    INSERT INTO stock_receipt_items (stock_receipt_id, ingredient_id, input_quantity, input_unit, conversion_factor,
                                     unit_price_per_input_unit, lot_code, expiry_date)
    VALUES (v_r, rc_test.ing('Dầu ăn'), 3, 'lít', 1, 1000, 'DAU-N1', NULL),
           (v_r, rc_test.ing('Dầu ăn'), 3, 'lít', 1, 1000, 'DAU-D1', rc_business_date(now()) + 20),
           (v_r, rc_test.ing('Dầu ăn'), 3, 'lít', 1, 1000, 'DAU-N2', NULL);
    PERFORM rc_confirm_receipt(v_r, rc_test.emp('M1'));
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', rc_test.issue_one(rc_test.lot('DAU-N1'), 1), rc_test.emp('M1')),
                          'FEFO', 'FNC-FEFO lô không hạn không được xuất khi lô có hạn còn hàng', 'RC409');
    PERFORM rc_issue_stock(rc_test.issue_one(rc_test.lot('DAU-D1'), 3), rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.raw_by_lot('DAU-D1') = 0, 'FNC-FEFO lô có hạn xuất trước và hết');
    PERFORM rc_test.fails(format('SELECT rc_issue_stock(%s, %s)', rc_test.issue_one(rc_test.lot('DAU-N2'), 1), rc_test.emp('M1')),
                          'FEFO', 'FNC-FEFO cùng không hạn: lô nhận sau không được xuất trước lô nhận trước', 'RC409');
    PERFORM rc_issue_stock(rc_test.issue_one(rc_test.lot('DAU-N1'), 3), rc_test.emp('M1'));
    PERFORM rc_issue_stock(rc_test.issue_one(rc_test.lot('DAU-N2'), 3), rc_test.emp('M1'));
    PERFORM rc_test.ok(rc_test.raw_by_lot('DAU-N1') = 0 AND rc_test.raw_by_lot('DAU-N2') = 0, 'FNC-FEFO xuất hết theo đúng thứ tự');
END $$;

-- ---------------------------------------------------------------------
-- FIN-01, DB-30: khoản chi — một phiếu nhập tối đa một khoản chi còn hiệu lực; quỹ quầy chỉ chi tiền mặt; chỉ hủy, không sửa
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_e BIGINT;
BEGIN
    INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source,
                                    stock_receipt_id, recorded_by_id, paid_at, request_key)
    VALUES (rc_business_date(now()), 'INGREDIENT', 'Trả NCC phiếu R1', 2500000, 'BANK_TRANSFER', 'BANK',
            rc_test.id('R1'), rc_test.emp('M1'), now(), 'exp-1') RETURNING id INTO v_e;
    PERFORM rc_test.fails(format('INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source, stock_receipt_id, recorded_by_id, paid_at, request_key) VALUES (rc_business_date(now()), ''INGREDIENT'', ''Trả trùng'', 2500000, ''CASH'', ''CASH_DRAWER'', %s, %s, now(), ''exp-2'')',
                                 rc_test.id('R1'), rc_test.emp('M1')),
                          'ux_expense_receipt_live', 'FIN-01 hai khoản chi còn hiệu lực cho 1 phiếu nhập', '23505');
    PERFORM rc_test.fails(format('INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, request_key) VALUES (rc_business_date(now()), ''OTHER'', ''lặp'', 1000, ''CASH'', ''OWNER'', %s, now(), ''exp-1'')', rc_test.emp('M1')),
                          'request_key', 'FIN-01 lặp request_key', '23505');
    PERFORM rc_test.fails(format('INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, request_key) VALUES (rc_business_date(now()) + 1, ''OTHER'', ''lệch ngày'', 1000, ''CASH'', ''OWNER'', %s, now(), ''exp-9'')', rc_test.emp('M1')),
                          'ngày kinh doanh', 'FIN-01 business_date phải theo paid_at', 'RC409');
    PERFORM rc_test.fails(format('UPDATE operating_expenses SET amount = 1 WHERE id = %s', v_e), 'chỉ được hủy', 'FIN-01 không sửa nội dung khoản chi', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM operating_expenses WHERE id = %s', v_e), 'không xóa dữ liệu giao dịch', 'FIN-01 không xóa khoản chi', 'RC409');
    UPDATE operating_expenses SET status = 'VOIDED', void_reason = 'Ghi nhầm tài khoản', voided_by_id = rc_test.emp('M1'), voided_at = now()
     WHERE id = v_e;
    PERFORM rc_test.fails(format('UPDATE operating_expenses SET status = ''PAID'' WHERE id = %s', v_e), 'VOIDED', 'FIN-01 khoản chi VOIDED bất biến', 'RC409');
    INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source,
                                    stock_receipt_id, recorded_by_id, paid_at, request_key)
    VALUES (rc_business_date(now()), 'INGREDIENT', 'Trả NCC phiếu R1', 2500000, 'BANK_TRANSFER', 'OWNER',
            rc_test.id('R1'), rc_test.emp('M1'), now(), 'exp-3');
    INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source,
                                    recorded_by_id, paid_at, request_key)
    VALUES (rc_business_date(now()), 'SUPPLIES', 'Mua đá', 50000, 'CASH', 'CASH_DRAWER', rc_test.emp('M1'), now(), 'exp-4');
    PERFORM rc_test.fails(format('INSERT INTO operating_expenses (business_date, category, description, amount, payment_method, fund_source, recorded_by_id, paid_at, request_key) VALUES (rc_business_date(now()), ''OTHER'', ''Sai quỹ'', 1000, ''BANK_TRANSFER'', ''CASH_DRAWER'', %s, now(), ''exp-5'')', rc_test.emp('M1')),
                          'ck_expense_cash_drawer_is_cash', 'DB-30 chi từ két phải là tiền mặt', '23514');
END $$;

-- ---------------------------------------------------------------------
-- FIN-02, DB-31: đối soát có phiên bản; chỉ tiền mặt của ngày; một bản CURRENT; bản cũ bất biến
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_day      DATE := rc_business_date(now());
    v_cash     NUMERIC;
    v_exp      NUMERIC;
    v_expected NUMERIC;
    v1         BIGINT;
    v2         BIGINT;
BEGIN
    v_cash := (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE business_date = v_day AND method = 'CASH');
    v_exp := (SELECT COALESCE(SUM(amount), 0) FROM operating_expenses
               WHERE business_date = v_day AND status = 'PAID' AND payment_method = 'CASH' AND fund_source = 'CASH_DRAWER');
    PERFORM rc_test.ok(v_cash > 0 AND v_exp = 50000, 'FIN-02 có thu tiền mặt và chi tiền mặt từ két');
    PERFORM rc_test.ok(EXISTS (SELECT 1 FROM payments WHERE method = 'QR') AND v_cash < (SELECT SUM(amount) FROM payments WHERE business_date = v_day),
                       'FIN-02 doanh thu QR không nằm trong tiền mặt');
    PERFORM rc_test.ok(NOT EXISTS (SELECT 1 FROM invoices i JOIN payments p ON p.invoice_id = i.id WHERE i.status = 'UNCOLLECTED'),
                       'FIN-02 hóa đơn UNCOLLECTED không có trong doanh thu thực thu');
    v_expected := 1000000 + v_cash - v_exp;
    PERFORM rc_test.fails(format('SELECT rc_reconcile_cash(%L, 1000000, 1000000, %s)', v_day + 1, rc_test.emp('M1')), 'tương lai', 'FIN-02 ngày tương lai', 'RC422');
    v1 := rc_reconcile_cash(v_day, 1000000, v_expected, rc_test.emp('M1'));
    PERFORM rc_test.ok((SELECT cash_sales = v_cash AND cash_expenses = v_exp AND expected_cash = v_expected AND difference = 0
                          FROM daily_cash_reconciliations WHERE id = v1), 'FIN-02 khớp quỹ: chỉ tiền mặt, chỉ chi từ két');
    PERFORM rc_test.fails(format('SELECT rc_reconcile_cash(%L, 1000000, %s, %s)', v_day, v_expected - 10000, rc_test.emp('M1')),
                          'ck_recon_reason_when_diff|ck_recon_revision_reason', 'FIN-02 bản sửa thiếu lý do', '23514');
    v2 := rc_reconcile_cash(v_day, 1000000, v_expected - 10000, rc_test.emp('M1'), 'Thiếu 10.000 chưa rõ', 'Đếm lại két');
    PERFORM rc_test.ok((SELECT status FROM daily_cash_reconciliations WHERE id = v1) = 'SUPERSEDED'
                       AND (SELECT status FROM daily_cash_reconciliations WHERE id = v2) = 'CURRENT', 'FIN-02 chỉ một bản CURRENT');
    PERFORM rc_test.ok((SELECT difference FROM daily_cash_reconciliations WHERE id = v2) = -10000, 'FIN-02 chênh lệch −10.000');
    PERFORM rc_test.fails(format('UPDATE daily_cash_reconciliations SET actual_cash = 0 WHERE id = %s', v1), 'không sửa số liệu', 'FIN-02 sửa bản đối soát lịch sử', 'RC409');
    PERFORM rc_test.fails(format('DELETE FROM daily_cash_reconciliations WHERE id = %s', v1), 'không xóa', 'FIN-02 xóa bản đối soát', 'RC409');
END $$;

-- ---------------------------------------------------------------------
-- ACL (spec I4, M7): role ứng dụng chỉ có SELECT + EXECUTE các hàm rc_* → ghi được qua hàm (SECURITY DEFINER), nhưng DML
-- trực tiếp bị 42501; role webhook chỉ chạy được rc_process_verified_qr và không đọc được bảng tiền.
-- Role là đối tượng của cả cụm nên được xóa ngay cuối khối (và dọn nếu lần chạy trước bỏ dở).
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_t BIGINT;
    v_s BIGINT;
    r   RECORD;
BEGIN
    FOR r IN SELECT rolname FROM pg_roles WHERE rolname IN ('rc_app_probe', 'rc_webhook_probe') LOOP
        EXECUTE format('DROP OWNED BY %I', r.rolname);
        EXECUTE format('DROP ROLE %I', r.rolname);
    END LOOP;
    CREATE ROLE rc_app_probe NOLOGIN;
    CREATE ROLE rc_webhook_probe NOLOGIN;
    GRANT USAGE ON SCHEMA public, rc_test TO rc_app_probe, rc_webhook_probe;
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO rc_app_probe;
    GRANT SELECT ON ALL TABLES IN SCHEMA rc_test TO rc_app_probe, rc_webhook_probe;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO rc_app_probe;
    GRANT EXECUTE ON FUNCTION rc_process_verified_qr(TEXT, TEXT, NUMERIC, TEXT) TO rc_webhook_probe;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rc_test TO rc_app_probe, rc_webhook_probe;
    INSERT INTO dining_tables (code, capacity) VALUES ('ACL1', 2) RETURNING id INTO v_t;

    -- role ứng dụng: gọi hàm thì ghi được; DML trực tiếp vào bảng số dư / tiền / trạng thái thì không
    SET LOCAL ROLE rc_app_probe;
    v_s := rc_open_session(v_t, rc_test.emp('W1'));
    PERFORM rc_close_empty_session(v_s, rc_test.emp('W1'));
    BEGIN UPDATE order_items SET status = 'SERVED' WHERE false; RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL app UPDATE order_items'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN INSERT INTO payments (invoice_id, payment_request_id, amount, method, business_date) SELECT 1, 1, 1, 'CASH', CURRENT_DATE WHERE false;
          RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL app INSERT payments'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN DELETE FROM stock_movements WHERE false; RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL app DELETE stock_movements'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN UPDATE inventory_batches SET quantity_on_hand = 0 WHERE false; RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL app UPDATE inventory_batches'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN UPDATE invoices SET status = 'PAID' WHERE false; RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL app UPDATE invoices'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    RESET ROLE;
    UPDATE rc_test.stat SET n = n + 1 WHERE kind = 'ok';
    UPDATE rc_test.stat SET n = n + 5 WHERE kind = 'fails';

    -- role webhook: chỉ chạy rc_process_verified_qr; không chạy hàm khác, không đọc bảng tiền
    SET LOCAL ROLE rc_webhook_probe;
    IF rc_process_verified_qr('ACL-FT-1', 'KHONG-CO-MA', 1000, '{}') <> 'MISMATCH' THEN
        RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL webhook gọi hàm được cấp quyền';
    END IF;
    BEGIN PERFORM rc_pay_cash(1, 1, 1); RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL webhook gọi rc_pay_cash'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    BEGIN PERFORM 1 FROM payments LIMIT 1; RESET ROLE; RAISE EXCEPTION 'TEST FAIL: ACL webhook đọc payments'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
    RESET ROLE;
    UPDATE rc_test.stat SET n = n + 1 WHERE kind = 'ok';
    UPDATE rc_test.stat SET n = n + 2 WHERE kind = 'fails';

    DROP OWNED BY rc_app_probe, rc_webhook_probe;
    DROP ROLE rc_app_probe, rc_webhook_probe;
END $$;

-- ---------------------------------------------------------------------
-- Bất biến số dư / tiền / trạng thái (docs/02 §9): sau toàn bộ kịch bản, mọi truy vấn phải trả 0 dòng
-- ---------------------------------------------------------------------
DO $$
DECLARE
    v_q TEXT;
    v_n BIGINT;
BEGIN
    FOREACH v_q IN ARRAY ARRAY[
        -- I-1 RAW: số dư lô = tổng sổ
        'SELECT b.id FROM inventory_batches b LEFT JOIN stock_movements m ON m.raw_batch_id = b.id
          GROUP BY b.id HAVING b.quantity_on_hand <> COALESCE(SUM(m.quantity_delta), 0)',
        -- I-2 WIP: lượng chờ sơ chế = tổng sổ
        'SELECT i.id FROM stock_issue_items i LEFT JOIN stock_movements m ON m.wip_issue_item_id = i.id
          GROUP BY i.id HAVING i.wip_remaining_quantity <> COALESCE(SUM(m.quantity_delta), 0)',
        -- I-3 Bếp: tồn = tổng sổ; đang giữ = tổng reservation ACTIVE
        'SELECT k.id FROM kitchen_batches k
          WHERE k.quantity_on_hand <> (SELECT COALESCE(SUM(m.quantity_delta), 0) FROM stock_movements m WHERE m.kitchen_batch_id = k.id)
             OR k.reserved_quantity <> (SELECT COALESCE(SUM(r.quantity), 0) FROM stock_reservations r
                                         WHERE r.kitchen_batch_id = k.id AND r.status = ''ACTIVE'')',
        -- I-4a Hóa đơn: tổng = Σ dòng (mọi trạng thái)
        'SELECT v.id FROM invoices v LEFT JOIN invoice_items ii ON ii.invoice_id = v.id
          GROUP BY v.id HAVING v.total <> COALESCE(SUM(ii.line_total), 0)',
        -- I-4b không sót dòng SERVED khi hóa đơn đã chốt (không còn DRAFT)
        'SELECT oi.id FROM invoices v JOIN order_batches ob ON ob.order_id = v.order_id
           JOIN order_items oi ON oi.order_batch_id = ob.id AND oi.status = ''SERVED''
           LEFT JOIN invoice_items ii ON ii.order_item_id = oi.id
          WHERE v.status <> ''DRAFT'' AND ii.id IS NULL',
        -- I-4c dòng hóa đơn là snapshot đúng của dòng SERVED cùng đơn
        'SELECT ii.id FROM invoice_items ii JOIN invoices v ON v.id = ii.invoice_id
           JOIN order_items oi ON oi.id = ii.order_item_id JOIN order_batches ob ON ob.id = oi.order_batch_id
          WHERE ob.order_id <> v.order_id OR oi.status <> ''SERVED''
             OR ii.item_name <> oi.item_name_snapshot OR ii.quantity <> oi.quantity OR ii.unit_price <> oi.unit_price',
        -- I-5 trạng thái món = sự kiện cuối cùng
        'SELECT oi.id FROM order_items oi
           JOIN LATERAL (SELECT e.to_status FROM order_item_events e WHERE e.order_item_id = oi.id
                          ORDER BY e.created_at DESC, e.id DESC LIMIT 1) last_ev ON true
          WHERE last_ev.to_status <> oi.status',
        -- I-6 bàn OCCUPIED <=> có phiên OPEN; phiên OPEN <=> đơn OPEN
        'SELECT t.id FROM dining_tables t
          WHERE (t.status = ''OCCUPIED'') <> EXISTS (SELECT 1 FROM table_sessions s WHERE s.dining_table_id = t.id AND s.status = ''OPEN'')',
        'SELECT s.id FROM table_sessions s JOIN orders o ON o.table_session_id = s.id
          WHERE (s.status = ''OPEN'') <> (o.status = ''OPEN'')',
        -- I-7 thanh toán khớp yêu cầu và hóa đơn; hóa đơn PAID <=> có đúng một payment bằng số phải thu
        'SELECT p.id FROM payments p JOIN payment_requests r ON r.id = p.payment_request_id JOIN invoices v ON v.id = p.invoice_id
          WHERE p.amount <> r.amount_due OR p.amount <> v.total OR p.method <> r.method OR r.status <> ''PAID'' OR v.status <> ''PAID''',
        'SELECT v.id FROM invoices v LEFT JOIN payments p ON p.invoice_id = v.id
          WHERE (v.status = ''PAID'') <> (p.id IS NOT NULL)',
        -- I-8 không bán vượt quota (QĐ-1, QĐ-8): số suất chiếm ≤ quota khi có giới hạn
        'SELECT d.id FROM daily_menu_items d WHERE d.quota_total IS NOT NULL AND rc_quota_used(d.id) > d.quota_total',
        -- I-9 món đã nấu được trừ đủ nguyên liệu theo công thức; không trừ hai lần
        'SELECT oi.id FROM order_items oi
           JOIN daily_menu_items d ON d.id = oi.daily_menu_item_id
           JOIN recipe_ingredients ri ON ri.recipe_id = d.recipe_id
           LEFT JOIN (stock_movements m JOIN kitchen_batches k ON k.id = m.kitchen_batch_id)
                  ON m.order_item_id = oi.id AND m.movement_type = ''COOK''
                 AND k.kitchen_ingredient_type_id = ri.kitchen_ingredient_type_id
          WHERE oi.id IN (SELECT order_item_id FROM order_item_events WHERE to_status = ''COOKING'')
          GROUP BY oi.id, ri.kitchen_ingredient_type_id, oi.quantity, ri.quantity_per_serving
         HAVING COALESCE(-SUM(m.quantity_delta), 0) <> oi.quantity * ri.quantity_per_serving',
        -- I-10 bản đối soát CURRENT không lỗi thời
        'SELECT r.business_date FROM daily_cash_reconciliations r
          WHERE r.status = ''CURRENT''
            AND (r.cash_sales <> (SELECT COALESCE(SUM(p.amount), 0) FROM payments p
                                   WHERE p.business_date = r.business_date AND p.method = ''CASH'')
              OR r.cash_expenses <> (SELECT COALESCE(SUM(e.amount), 0) FROM operating_expenses e
                                      WHERE e.business_date = r.business_date AND e.status = ''PAID''
                                        AND e.payment_method = ''CASH'' AND e.fund_source = ''CASH_DRAWER''))',
        -- I-11 khách bỏ về (QĐ-3): hóa đơn UNCOLLECTED không có payment, đơn CLOSED, phiên CLOSED do người đã ghi nhận
        'SELECT v.id FROM invoices v JOIN orders o ON o.id = v.order_id JOIN table_sessions s ON s.id = o.table_session_id
          WHERE v.status = ''UNCOLLECTED''
            AND (EXISTS (SELECT 1 FROM payments p WHERE p.invoice_id = v.id) OR o.status <> ''CLOSED'' OR s.status <> ''CLOSED''
              OR s.closed_by_id IS DISTINCT FROM v.uncollected_by_id)',
        -- I-12 đơn CLOSED <=> hóa đơn PAID/UNCOLLECTED; đơn CANCELLED không còn món/hóa đơn có nghĩa vụ
        'SELECT o.id FROM orders o
          WHERE (o.status = ''CLOSED'') <> EXISTS (SELECT 1 FROM invoices v WHERE v.order_id = o.id AND v.status IN (''PAID'', ''UNCOLLECTED''))',
        'SELECT o.id FROM orders o
          WHERE o.status = ''CANCELLED''
            AND (EXISTS (SELECT 1 FROM invoices v WHERE v.order_id = o.id AND v.status <> ''DRAFT'')
              OR EXISTS (SELECT 1 FROM order_items oi JOIN order_batches b ON b.id = oi.order_batch_id
                          WHERE b.order_id = o.id AND oi.status NOT IN (''CANCELLED'', ''REJECTED'')))',
        -- I-13 reservation CONSUMED luôn có sổ COOK tương ứng (không trừ hai lần, không thiếu)
        'SELECT r.id FROM stock_reservations r
          WHERE (r.status = ''CONSUMED'') <> EXISTS (SELECT 1 FROM stock_movements m WHERE m.event_key = ''COOK:'' || r.id)',
        -- I-14 hủy ngoại lệ (từ COOKING trở đi) chỉ do người mang vai trò MANAGER thực hiện và luôn có lý do
        'SELECT e.id FROM order_item_events e
          WHERE e.to_status = ''CANCELLED'' AND e.from_status IN (''COOKING'', ''READY'', ''SERVED'')
            AND (btrim(COALESCE(e.reason, '''')) = ''''
              OR NOT EXISTS (SELECT 1 FROM accounts a JOIN account_roles ar ON ar.account_id = a.id JOIN roles r ON r.id = ar.role_id
                              WHERE a.employee_id = e.actor_id AND r.role_code = ''MANAGER''))'
    ] LOOP
        EXECUTE 'SELECT count(*) FROM (' || v_q || ') x' INTO v_n;
        PERFORM rc_test.ok(v_n = 0, 'Bất biến bị vi phạm: ' || left(regexp_replace(v_q, '\s+', ' ', 'g'), 100));
    END LOOP;
END $$;

DO $$
BEGIN
    RAISE NOTICE 'TEST SUMMARY: % kiểm tra dương (assert), % kiểm tra âm (phải bị chặn)',
        (SELECT n FROM rc_test.stat WHERE kind = 'ok'), (SELECT n FROM rc_test.stat WHERE kind = 'fails');
END $$;

DROP SCHEMA rc_test CASCADE;

-- CONC-01, CONC-02 và các ca đua khóa (hai Kitchen cùng suất cuối, đặt giữ nguyên liệu cuối, hai lần trừ một lô,
-- Kitchen nhận món ↔ Cashier chốt hóa đơn, tiền mặt ↔ webhook QR, webhook trùng, hủy ↔ nấu, bỏ về ↔ thanh toán,
-- deadlock) cần hai kết nối thật: chạy `node 06-test-concurrency-v4.1.mjs <connection-string>`.
