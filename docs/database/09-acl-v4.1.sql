-- =====================================================================
-- RESTAURANT CORE — Phân quyền PostgreSQL chính thức (v4.1, QĐ-BM)
-- Chạy SAU 04-schema-v4.1.sql, bằng superuser (hoặc role có CREATEROLE và đang sở hữu các đối tượng), trên cùng database:
--     psql -v ON_ERROR_STOP=1 -d restaurant_core -f 09-acl-v4.1.sql
-- Chạy lại được (idempotent). Role là đối tượng của cả cụm: tạo nếu chưa có, không xóa.
--
-- Mô hình tin cậy
--   rc_owner    NOLOGIN, không superuser — sở hữu bảng và hàm. Hàm rc_* là SECURITY DEFINER nên chạy với quyền
--               rc_owner; search_path cố định (đặt ở cuối 04) nên không bị chiếm bằng đối tượng cùng tên.
--   rc_app      quyền của backend Spring Boot. Ghi dữ liệu nghiệp vụ nhạy cảm (bán hàng, tiền, sổ kho, số dư,
--               trạng thái chứng từ) CHỈ qua hàm rc_* nghiệp vụ; DML trực tiếp chỉ cho danh mục/nhân sự và phần
--               nháp của chứng từ (theo cột). Không EXECUTE hàm nội bộ (rc_item_transition, rc_settle_invoice…).
--   rc_webhook  bộ xử lý webhook SePay: chỉ EXECUTE rc_process_verified_qr, không đọc bảng.
--   rc_readonly báo cáo: SELECT (trừ password_hash) + hàm báo cáo rpt_* (10-bao-cao-v4.1.sql cấp thêm).
--   Tài khoản đăng nhập thật do vận hành tạo và gán vào role nhóm, ví dụ:
--     CREATE ROLE rms_api LOGIN PASSWORD '…' IN ROLE rc_app;
--     CREATE ROLE rms_sepay LOGIN PASSWORD '…' IN ROLE rc_webhook;
--   Không cấp rc_owner cho tài khoản đăng nhập nào; migration chạy bằng role quản trị riêng.
--
-- Danh tính người dùng cuối (p_actor) KHÔNG đến từ PostgreSQL: backend xác thực JWT và truyền employees.id của
-- principal đã xác thực. DB kiểm vai trò của p_actor trong từng hàm (rc_authorize) — xem docs/07 §10.
-- =====================================================================
BEGIN;

DO $$
DECLARE
    r TEXT;
BEGIN
    FOREACH r IN ARRAY ARRAY['rc_owner', 'rc_app', 'rc_webhook', 'rc_readonly'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS', r);
        END IF;
    END LOOP;
END $$;

-- 1. Chủ sở hữu: mọi bảng (sequence identity đi theo bảng) và mọi hàm rc_* về rc_owner
DO $$
DECLARE
    o RECORD;
BEGIN
    FOR o IN SELECT c.oid::regclass AS t FROM pg_class c
              WHERE c.relnamespace = current_schema()::regnamespace AND c.relkind = 'r' LOOP
        EXECUTE format('ALTER TABLE %s OWNER TO rc_owner', o.t);
    END LOOP;
    FOR o IN SELECT p.oid::regprocedure AS f FROM pg_proc p
              WHERE p.pronamespace = current_schema()::regnamespace AND p.proname LIKE 'rc\_%' LOOP
        EXECUTE format('ALTER FUNCTION %s OWNER TO rc_owner', o.f);
    END LOOP;
END $$;

-- 2. Đặt lại về không có quyền nào rồi cấp tối thiểu
DO $$
BEGIN
    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC', current_schema());
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM PUBLIC, rc_app, rc_webhook, rc_readonly', current_schema());
    EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA %I FROM PUBLIC, rc_app, rc_webhook, rc_readonly', current_schema());
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO rc_owner, rc_app, rc_webhook, rc_readonly', current_schema());
END $$;
DO $$
DECLARE
    f RECORD;
BEGIN
    -- chỉ hàm của hệ thống (rc_*); hàm của extension (btree_gist) giữ nguyên quyền mặc định
    FOR f IN SELECT p.oid::regprocedure AS sig FROM pg_proc p
              WHERE p.pronamespace = current_schema()::regnamespace AND p.proname LIKE 'rc\_%' LOOP
        EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, rc_app, rc_webhook, rc_readonly', f.sig);
    END LOOP;
END $$;
DO $$
BEGIN
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO rc_app', current_schema());
END $$;

-- 3. rc_app — hàm nghiệp vụ (32) + hàm tiện ích chỉ đọc
GRANT EXECUTE ON FUNCTION
    rc_business_date(TIMESTAMPTZ), rc_quota_used(BIGINT),
    rc_open_session(BIGINT, BIGINT), rc_close_empty_session(BIGINT, BIGINT),
    rc_submit_batch(BIGINT, BIGINT, TEXT, JSONB),
    rc_accept_item(BIGINT, BIGINT), rc_reject_item(BIGINT, BIGINT, TEXT), rc_start_cooking(BIGINT, BIGINT),
    rc_finish_dish(BIGINT, BIGINT), rc_serve_item(BIGINT, BIGINT),
    rc_cancel_item(BIGINT, BIGINT, TEXT), rc_cancel_item_exception(BIGINT, BIGINT, TEXT),
    rc_mark_batch_unusable(TEXT, BIGINT, BIGINT, TEXT), rc_reallocate_item(BIGINT, BIGINT),
    rc_checkout_invoice(BIGINT, BIGINT), rc_reopen_invoice(BIGINT, BIGINT, TEXT),
    rc_pay_cash(BIGINT, BIGINT, NUMERIC), rc_start_qr(BIGINT, BIGINT), rc_cancel_payment_request(BIGINT, BIGINT, TEXT),
    rc_abandon_session(BIGINT, BIGINT, TEXT), rc_waive_invoice(BIGINT, BIGINT, TEXT),
    rc_create_stock_receipt(BIGINT, TEXT, JSONB), rc_create_stock_issue(BIGINT, TEXT, JSONB),
    rc_create_stock_count(BIGINT, TEXT, JSONB),
    rc_confirm_receipt(BIGINT, BIGINT, TIMESTAMPTZ), rc_issue_stock(BIGINT, BIGINT), rc_receive_issue(BIGINT, BIGINT),
    rc_cancel_issue(BIGINT, BIGINT, TEXT), rc_confirm_preparation(BIGINT, BIGINT),
    rc_confirm_return(BIGINT, BIGINT), rc_confirm_adjustment(BIGINT, BIGINT), rc_confirm_count(BIGINT, BIGINT),
    rc_reconcile_cash(DATE, NUMERIC, NUMERIC, BIGINT, TEXT, TEXT)
TO rc_app;

-- 3b. rc_app — DML trực tiếp: danh mục, nhân sự, thực đơn (guard trigger vẫn kiểm bất biến)
GRANT INSERT, UPDATE, DELETE ON
    departments, employees, accounts, account_roles, permissions, role_permissions,
    shifts, shift_assignments, attendances,
    ingredients, kitchen_ingredient_types, menu_categories, menu_items, recipes, recipe_ingredients,
    daily_menus, daily_menu_items
TO rc_app;
GRANT INSERT ON attendance_adjustments TO rc_app;                                   -- chỉ-thêm
-- bàn: trạng thái AVAILABLE/OCCUPIED chỉ đổi trong rc_*
GRANT INSERT (code, capacity, is_active), UPDATE (code, capacity, is_active) ON dining_tables TO rc_app;
-- khoản chi: ghi mới; chỉ được hủy (VOIDED), không sửa nội dung
GRANT INSERT (business_date, category, description, amount, payment_method, fund_source, stock_receipt_id,
              recorded_by_id, paid_at, request_key),
      UPDATE (status, void_reason, voided_by_id, voided_at) ON operating_expenses TO rc_app;
-- sự cố webhook: Manager xử lý, chỉ các cột resolution_*
GRANT UPDATE (resolution_status, resolved_by_id, resolved_at, resolution_note) ON payment_webhook_events TO rc_app;

-- 3c. rc_app — phần nháp của chứng từ kho. Header nhập/xuất/kiểm kê tạo bằng rc_create_* (QĐ-ID); không cột trạng
--     thái / người xác nhận nào được cấp UPDATE → xác nhận chỉ qua rc_*. Nháp bỏ đi bằng DELETE (guard: chỉ DRAFT).
GRANT UPDATE (supplier_name, note), DELETE ON stock_receipts TO rc_app;
GRANT UPDATE (note), DELETE ON stock_issues, stock_counts TO rc_app;
GRANT INSERT, UPDATE, DELETE ON stock_receipt_items TO rc_app;
GRANT INSERT, DELETE, UPDATE (inventory_batch_id, quantity, fulfillment_mode) ON stock_issue_items TO rc_app;
GRANT INSERT (stock_count_id, raw_batch_id, wip_issue_item_id, kitchen_batch_id, book_quantity, actual_quantity, reason),
      UPDATE (actual_quantity, reason), DELETE ON stock_count_items TO rc_app;
GRANT INSERT (stock_issue_item_id, input_quantity, loss_reason, prepared_by_id, request_key),
      UPDATE (input_quantity, loss_reason), DELETE ON kitchen_preparations TO rc_app;
GRANT INSERT, UPDATE, DELETE ON kitchen_preparation_outputs TO rc_app;
GRANT INSERT (requested_by_id, reason, request_key), UPDATE (reason), DELETE ON stock_returns TO rc_app;
GRANT INSERT, UPDATE, DELETE ON stock_return_items TO rc_app;
GRANT INSERT (adjustment_type, reason, created_by_id, request_key), UPDATE (adjustment_type, reason), DELETE
    ON stock_adjustments TO rc_app;
GRANT INSERT, UPDATE, DELETE ON stock_adjustment_items TO rc_app;
-- KHÔNG cấp DML (chỉ SELECT): table_sessions, orders, order_batches, order_items, order_item_events, invoices,
-- invoice_items, payment_requests, payments, payment_webhook_events (ngoài resolution_*), stock_movements,
-- inventory_batches, kitchen_batches, stock_reservations, daily_cash_reconciliations, roles.

-- 4. rc_webhook — đúng một hàm
GRANT EXECUTE ON FUNCTION rc_process_verified_qr(TEXT, TEXT, NUMERIC, TEXT) TO rc_webhook;

-- 5. rc_readonly — đọc mọi bảng trừ mật khẩu băm
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT c.oid::regclass AS rel FROM pg_class c
              WHERE c.relnamespace = current_schema()::regnamespace AND c.relkind = 'r' AND c.relname <> 'accounts' LOOP
        EXECUTE format('GRANT SELECT ON %s TO rc_readonly', t.rel);
    END LOOP;
END $$;
GRANT SELECT (id, employee_id, username, must_change_password, account_status) ON accounts TO rc_readonly;
GRANT EXECUTE ON FUNCTION rc_business_date(TIMESTAMPTZ) TO rc_readonly;

-- 6. Tự kiểm: dừng (rollback) nếu cấu hình bảo mật sai
DO $$
DECLARE
    v_bad TEXT;
BEGIN
    SELECT string_agg(p.oid::regprocedure::text, ', ') INTO v_bad FROM pg_proc p
     WHERE p.pronamespace = current_schema()::regnamespace AND p.proname LIKE 'rc\_%'
       AND (NOT p.prosecdef OR NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c WHERE c LIKE 'search\_path=%'));
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION 'ACL: hàm thiếu SECURITY DEFINER hoặc search_path cố định: %', v_bad;
    END IF;
    SELECT string_agg(p.oid::regprocedure::text, ', ') INTO v_bad FROM pg_proc p
     WHERE p.pronamespace = current_schema()::regnamespace AND p.proname LIKE 'rc\_%'
       AND (p.proowner <> 'rc_owner'::regrole OR has_function_privilege('public', p.oid, 'EXECUTE'));
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION 'ACL: hàm không thuộc rc_owner hoặc PUBLIC còn EXECUTE: %', v_bad;
    END IF;
    SELECT string_agg(c.relname, ', ') INTO v_bad FROM pg_class c
     WHERE c.relnamespace = current_schema()::regnamespace AND c.relkind = 'r' AND c.relowner <> 'rc_owner'::regrole;
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION 'ACL: bảng không thuộc rc_owner: %', v_bad;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('rc_owner', 'rc_app', 'rc_webhook', 'rc_readonly')
                                       AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcanlogin)) THEN
        RAISE EXCEPTION 'ACL: role nhóm rc_* không được LOGIN/SUPERUSER/BYPASSRLS/CREATEROLE';
    END IF;
    SELECT string_agg(t, ', ') INTO v_bad
      FROM unnest(ARRAY['table_sessions', 'orders', 'order_batches', 'order_items', 'order_item_events', 'invoices',
                        'invoice_items', 'payment_requests', 'payments', 'stock_movements', 'inventory_batches',
                        'kitchen_batches', 'stock_reservations', 'daily_cash_reconciliations', 'roles']) t
     WHERE has_table_privilege('rc_app', t, 'INSERT') OR has_table_privilege('rc_app', t, 'UPDATE')
        OR has_table_privilege('rc_app', t, 'DELETE') OR has_any_column_privilege('rc_app', t, 'UPDATE');
    IF v_bad IS NOT NULL THEN
        RAISE EXCEPTION 'ACL: rc_app còn quyền ghi trực tiếp bảng nhạy cảm: %', v_bad;
    END IF;
END $$;

COMMIT;
