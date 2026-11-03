-- Nghiệp vụ và báo cáo đã chuyển hết lên tầng Service Java (V2 cấp quyền ghi cho ứng dụng).
-- Xóa các hàm nghiệp vụ rc_* và hàm báo cáo rpt_* không còn ai gọi.
-- GIỮ LẠI các hàm mà trigger bảo vệ dữ liệu cần (rc_guard_*, rc_raise, rc_check_balance, rc_trg_balance,
-- rc_check_item_event_sync, rc_doc_effect_missing, rc_forbid_change, rc_forbid_delete, rc_business_date, rc_quota_used):
-- trigger là lưới an toàn cuối cùng về toàn vẹn dữ liệu, không phải nơi viết nghiệp vụ.
-- Không dùng CASCADE: nếu còn đối tượng nào phụ thuộc, migration dừng và rollback, không xóa nhầm.
-- Định nghĩa gốc vẫn còn trong docs/04-schema-v4.1.sql và docs/10-bao-cao-v4.1.sql để tra cứu.
DO $$
DECLARE
    f RECORD;
BEGIN
    FOR f IN SELECT p.oid::regprocedure AS signature
               FROM pg_proc p
              WHERE p.pronamespace = current_schema()::regnamespace
                AND (p.proname LIKE 'rpt\_%' OR p.proname = ANY (ARRAY[
                    'rc_abandon_session', 'rc_accept_item', 'rc_allocate_item', 'rc_authorize', 'rc_batch_result',
                    'rc_cancel_issue', 'rc_cancel_item', 'rc_cancel_item_exception', 'rc_cancel_payment_request',
                    'rc_checkout_invoice', 'rc_close_empty_session', 'rc_confirm_adjustment', 'rc_confirm_count',
                    'rc_confirm_preparation', 'rc_confirm_receipt', 'rc_confirm_return', 'rc_create_stock_count',
                    'rc_create_stock_issue', 'rc_create_stock_receipt', 'rc_finish_dish', 'rc_invoice_total_checked',
                    'rc_issue_stock', 'rc_item_transition', 'rc_lock_invoice', 'rc_mark_batch_unusable', 'rc_open_session',
                    'rc_pay_cash', 'rc_process_verified_qr', 'rc_reallocate_item', 'rc_receive_issue', 'rc_reconcile_cash',
                    'rc_reject_item', 'rc_release_item_reservations', 'rc_reopen_invoice', 'rc_request_hash',
                    'rc_request_lookup', 'rc_serve_item', 'rc_settle_invoice', 'rc_start_cooking', 'rc_start_qr',
                    'rc_submit_batch', 'rc_usable_until', 'rc_waive_invoice']))
    LOOP
        EXECUTE format('DROP FUNCTION %s', f.signature);
    END LOOP;
END $$;
