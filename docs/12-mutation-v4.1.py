#!/usr/bin/env python3
"""RESTAURANT CORE v4.1 — kiểm thử đột biến (mutation) cho schema, phân quyền và báo cáo.

Mỗi mutant cài một lỗi CỐ Ý (thay đúng một đoạn mã, đoạn gốc phải xuất hiện đúng một lần) vào 04 / 09 / 10, nạp vào
database trống rồi chạy bộ kiểm liên quan; mutant "bị phát hiện" khi bộ kiểm thất bại. Mutant khóa (lock) chỉ bị
bộ nhiều kết nối 06 phát hiện nên chỉ chạy khi có --node-dir.

    python3 12-mutation-v4.1.py --psql "psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres" \
        [--url "postgresql://postgres:pass@127.0.0.1:5432/{db}" --node-dir /đường/dẫn/có/node_modules/pg] [--only M01,M02]

--psql: lệnh psql đọc SQL từ stdin, KHÔNG kèm -d (script tự thêm -d <db>); ví dụ chạy trong container:
        "docker exec -i <container> psql -X -q -v ON_ERROR_STOP=1 -U postgres".
Thoát 0 khi mọi mutant chạy được đều bị phát hiện.
"""
import argparse
import os
import shlex
import shutil
import subprocess
import sys

DOCS = os.path.dirname(os.path.abspath(__file__))
F04, F05, F06, F09, F10, F11 = ('04-schema-v4.1.sql', '05-test-v4.1.sql', '06-test-concurrency-v4.1.mjs',
                                '09-acl-v4.1.sql', '10-bao-cao-v4.1.sql', '11-test-bao-cao-v4.1.sql')

# (mã, file, đoạn gốc, đoạn đột biến, bộ kiểm, mô tả)
MUTANTS = [
    ('M01', F04, "AND r.role_code = ANY (p_roles)) THEN", ") THEN", '05',
     'QĐ-BM rc_authorize bỏ kiểm vai trò'),
    ('M02', F04, "a.employee_id = e.id AND a.account_status = 'ACTIVE'", "a.employee_id = e.id", '05',
     'QĐ-BM rc_authorize chấp nhận tài khoản bị khóa'),
    ('M03', F04, "    PERFORM rc_authorize(p_actor, 'MANAGER');\n    IF p_reason IS NULL OR btrim(p_reason) = '' THEN\n        PERFORM rc_raise('RC422', 'miễn thu bắt buộc có lý do');",
     "    IF p_reason IS NULL OR btrim(p_reason) = '' THEN\n        PERFORM rc_raise('RC422', 'miễn thu bắt buộc có lý do');", '05',
     'QĐ-MT rc_waive_invoice không cưỡng chế Manager'),
    ('M04', F04, "    IF OLD.status IN ('PAID', 'UNCOLLECTED', 'WAIVED') THEN", "    IF OLD.status IN ('UNCOLLECTED', 'WAIVED') THEN", '05',
     'QĐ-MT/QĐ-3 hóa đơn PAID không còn bất biến (PAID → WAIVED)'),
    ('M05', F04, "             THEN num_nonnulls(waived_reason, waived_by_id, waived_at) = 3 AND btrim(waived_reason) <> ''",
     "             THEN true", '05', 'QĐ-MT WAIVED không bắt buộc người/lý do/thời điểm'),
    ('M06', F04, "       SET status = 'CANCELLED', cancel_reason = 'Miễn thu: ' || btrim(p_reason), cancelled_at = now(), cancelled_by_id = p_actor\n     WHERE invoice_id = v_invoice.id AND status IN ('REQUESTED', 'WAITING_TRANSFER');",
     "       SET status = 'CANCELLED', cancel_reason = 'Miễn thu: ' || btrim(p_reason), cancelled_at = now(), cancelled_by_id = p_actor\n     WHERE false;",
     '05', 'QĐ-MT miễn thu không hủy yêu cầu QR đang mở'),
    ('M07', F04, "                WHERE b.order_id = p_order AND oi.status NOT IN ('SERVED', 'CANCELLED', 'REJECTED')) THEN\n        PERFORM rc_raise('RC409', format('order %s còn món chưa kết thúc",
     "                WHERE false) THEN\n        PERFORM rc_raise('RC409', format('order %s còn món chưa kết thúc", '05',
     'QĐ-MT miễn thu khi còn món đang xử lý'),
    ('M08', F04, "        IF v_total > 0 THEN                                                     -- còn nghĩa vụ thu: UNCOLLECTED",
     "        IF true THEN", '05', 'QĐ-3 bỏ về với hóa đơn 0 đồng ghi UNCOLLECTED thay vì WAIVED'),
    ('M09', F04, "     WHERE w.quantity > GREATEST(w.quota_total - w.used, 0)", "     WHERE w.quantity >= GREATEST(w.quota_total - w.used, 0)", '05',
     'QĐ-4 cảnh báo quota sai biên (vừa đủ vẫn cảnh báo)'),
    ('M10', F04, "        IF v_used + v_item.quantity > v_dmi.quota_total THEN", "        IF v_used + v_item.quantity > v_dmi.quota_total + 1 THEN", '05',
     'QĐ-4 Kitchen nhận vượt quota 1 suất'),
    ('M11', F04, "    IF v_id IS NOT NULL AND v_hash <> p_hash THEN", "    IF false THEN", '05',
     'QĐ-ID cùng khóa khác payload vẫn trả chứng từ cũ'),
    ('M12', F04, "    IF rc_doc_effect_missing(TG_TABLE_NAME, OLD.id, OLD.status, NEW.status) THEN", "    IF false THEN", '05',
     'GRD chứng từ chuyển trạng thái có hiệu lực mà không có bút toán'),
    ('M13', F04, "    SELECT ((p_at AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '4 hours')::date", "    SELECT ((p_at AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '3 hours')::date",
     '05', 'QĐ-NG mốc ngày kinh doanh 03:00'),
    ('M14', F04, "    SELECT ((p_at AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '4 hours')::date", "    SELECT ((p_at::timestamp) - INTERVAL '4 hours')::date",
     '05', 'QĐ-NG ngày kinh doanh phụ thuộc TimeZone của phiên'),
    ('M15', F04, "        UPDATE stock_reservations SET status = 'RELEASED', release_reason = p_reason, closed_at = now() WHERE id = r.id;",
     "        UPDATE stock_reservations SET release_reason = NULL WHERE id = r.id;", '05', 'QĐ-5/QĐ-8 hủy món không đóng phần giữ (hoàn hai lần)'),
    ('M16', F04, "        OR (OLD.status = 'ACCEPTED' AND NEW.status IN ('COOKING', 'CANCELLED'))",
     "        OR (OLD.status = 'ACCEPTED' AND NEW.status IN ('COOKING', 'CANCELLED'))\n        OR (OLD.status = 'CANCELLED' AND NEW.status = 'PENDING')", '05',
     'QĐ-5 cho dùng lại dòng CANCELLED'),
    ('M17', F04, "        IF NEW.status = 'CLOSED' AND NOT EXISTS (SELECT 1 FROM invoices WHERE order_id = OLD.id AND status IN ('PAID', 'UNCOLLECTED', 'WAIVED')) THEN",
     "        IF NEW.status = 'CLOSED' AND NOT EXISTS (SELECT 1 FROM invoices WHERE order_id = OLD.id) THEN", '05',
     'Đơn CLOSED khi hóa đơn còn DRAFT'),
    ('M18', F09, "GRANT UPDATE (note), DELETE ON stock_issues, stock_counts TO rc_app;", "GRANT UPDATE (note, status), DELETE ON stock_issues, stock_counts TO rc_app;", '05',
     'ACL rc_app được sửa trạng thái phiếu xuất/kiểm kê'),
    ('M19', F09, "GRANT EXECUTE ON FUNCTION rc_process_verified_qr(TEXT, TEXT, NUMERIC, TEXT) TO rc_webhook;",
     "GRANT EXECUTE ON FUNCTION rc_process_verified_qr(TEXT, TEXT, NUMERIC, TEXT) TO rc_webhook;\nGRANT EXECUTE ON FUNCTION rc_item_transition(BIGINT, TEXT[], TEXT, BIGINT, TEXT) TO rc_app;",
     '05', 'ACL rc_app gọi được hàm nội bộ rc_item_transition'),
    ('M20', F09, "GRANT SELECT (id, employee_id, username, must_change_password, account_status) ON accounts TO rc_readonly;",
     "GRANT SELECT ON accounts TO rc_readonly;", '05', 'ACL rc_readonly đọc được password_hash'),
    ('M21', F10, "           COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'QR'), 0),",
     "           (SELECT COALESCE(SUM(w.amount), 0) FROM payment_webhook_events w WHERE rc_business_date(w.received_at) = p.business_date),",
     'rpt', 'Báo cáo doanh thu QR đếm webhook (trùng/đến muộn)'),
    ('M22', F10, "      FROM payments p\n      JOIN invoice_items ii ON ii.invoice_id = p.invoice_id",
     "      FROM invoices p0 CROSS JOIN LATERAL (SELECT p0.id AS invoice_id, rc_business_date(p0.created_at) AS business_date) p\n      JOIN invoice_items ii ON ii.invoice_id = p.invoice_id",
     'rpt', 'Báo cáo theo món cộng cả UNCOLLECTED/WAIVED'),
    ('M23', F10, "                 WHERE business_date = p_date AND status = 'PAID' AND fund_source = 'CASH_DRAWER') AS expenses)",
     "                 WHERE business_date = p_date AND status = 'PAID') AS expenses)", 'rpt', 'Đối soát trừ cả khoản chi không qua quầy'),
    ('M24', F10, "           COALESCE(SUM(k.quantity_on_hand - k.reserved_quantity) FILTER", "           COALESCE(SUM(k.quantity_on_hand) FILTER", 'rpt',
     'Tồn bếp khả dụng không trừ phần đang giữ'),
    # mutant khóa: chỉ 06 (nhiều kết nối) phát hiện được
    ('L01', F04, "    PERFORM pg_advisory_xact_lock(hashtext(p_table), hashtext(p_request_key));\n", "", '06',
     'QĐ-ID bỏ khóa tư vấn theo request_key (đua tạo nháp trùng)'),
    ('L02', F04, "    SELECT * INTO v_order FROM orders WHERE id = p_order FOR UPDATE;                     -- mức 1\n    IF NOT FOUND THEN\n        PERFORM rc_raise('RC422', format('order %s không tồn tại', p_order));\n    END IF;\n    SELECT * INTO v_invoice FROM invoices WHERE order_id = p_order FOR UPDATE;           -- mức 2",
     "    SELECT * INTO v_invoice FROM invoices WHERE order_id = p_order FOR UPDATE;           -- mức 2\n    SELECT * INTO v_order FROM orders WHERE id = p_order FOR UPDATE;                     -- mức 1\n    IF NOT FOUND THEN\n        PERFORM rc_raise('RC422', format('order %s không tồn tại', p_order));\n    END IF;",
     '06', 'QĐ-MT miễn thu khóa invoices trước orders (đảo thứ tự khóa)'),
    ('L03', F04, "    SELECT * INTO v_order FROM orders WHERE id = p_order FOR UPDATE;                     -- mức 1\n    IF NOT FOUND THEN\n        PERFORM rc_raise('RC422', format('order %s không tồn tại', p_order));\n    END IF;\n    SELECT * INTO v_invoice FROM invoices WHERE order_id = p_order FOR UPDATE;           -- mức 2",
     "    SELECT * INTO v_order FROM orders WHERE id = p_order;                                -- mức 1\n    IF NOT FOUND THEN\n        PERFORM rc_raise('RC422', format('order %s không tồn tại', p_order));\n    END IF;\n    SELECT * INTO v_invoice FROM invoices WHERE order_id = p_order;                      -- mức 2",
     '06', 'QĐ-MT miễn thu không khóa (đua với thu tiền)'),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--psql', required=True)
    ap.add_argument('--url')
    ap.add_argument('--node-dir')
    ap.add_argument('--only')
    a = ap.parse_args()
    only = set(a.only.split(',')) if a.only else None
    src = {f: open(os.path.join(DOCS, f), encoding='utf-8').read() for f in (F04, F05, F06, F09, F10, F11)}

    def psql(db, sql):
        p = subprocess.run(shlex.split(a.psql) + ['-d', db], input=sql.encode(), capture_output=True)
        return p.returncode == 0, (p.stdout + p.stderr).decode(errors='replace')

    def fresh(db, files):
        psql('postgres', f'DROP DATABASE IF EXISTS {db} WITH (FORCE)')
        ok, out = psql('postgres', f"CREATE DATABASE {db} TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'en_US.utf8' LC_CTYPE 'en_US.utf8'")
        if not ok:
            sys.exit('không tạo được database: ' + out)
        return psql(db, files[F04])

    def suite_05(db, files):
        body = files[F05].replace('\\ir 09-acl-v4.1.sql', files[F09])
        return psql(db, body)

    killed = survived = skipped = 0
    for mid, target, old, new, suite, note in MUTANTS:
        if only and mid not in only:
            continue
        n = src[target].count(old)
        if n != 1:
            print(f'{mid} LỖI ĐỊNH NGHĨA: đoạn gốc xuất hiện {n} lần trong {target}')
            survived += 1
            continue
        if suite == '06' and not (a.url and a.node_dir):
            print(f'{mid} BỎ QUA (cần --url và --node-dir): {note}')
            skipped += 1
            continue
        files = dict(src)
        files[target] = src[target].replace(old, new)
        db = 'rc_mutation'
        ok, out = fresh(db, files)
        detected = not ok
        if ok and suite == '05':
            ok, out = suite_05(db, files)
            detected = not ok
        elif ok and suite == 'rpt':
            ok, out = psql(db, files[F10])
            ok = ok and psql(db, files[F11])[0]
            detected = not ok
        elif ok and suite == '06':
            shutil.copy(os.path.join(DOCS, F06), os.path.join(a.node_dir, '06-mutation.mjs'))
            p = subprocess.run(['node', '06-mutation.mjs', a.url.format(db=db)], cwd=a.node_dir, capture_output=True,
                               env=dict(os.environ, ITER=os.environ.get('ITER', '10')))
            out = (p.stdout + p.stderr).decode(errors='replace')
            detected = p.returncode != 0
        reason = next((l.strip() for l in out.splitlines() if 'TEST FAIL' in l or 'BÁO CÁO SAI' in l or '✗ FAIL' in l or 'ERROR' in l), '')
        print(f"{mid} {'PHÁT HIỆN' if detected else 'SỐNG SÓT'} [{suite}] {note}" + (f' — {reason[:160]}' if detected else ''))
        killed += detected
        survived += not detected
    print(f'\nMUTATION: {killed} phát hiện / {killed + survived} chạy, {skipped} bỏ qua')
    sys.exit(0 if survived == 0 else 1)


if __name__ == '__main__':
    main()
