-- Kiểm thử schema phân hệ 1 (chạy sau schema-postgresql.sql, trên CSDL trống).
-- Toàn bộ chạy trong một transaction và kết thúc bằng ROLLBACK, không để lại dữ liệu.
-- Mỗi ca ghi vào bảng tạm test_results: kết quả mong đợi là OK hoặc "SQLSTATE tên_ràng_buộc".
--   psql -v ON_ERROR_STOP=1 -f test-postgresql.sql   (thoát mã khác 0 nếu có ca thất bại)
-- Khi kiểm thử không phụ thuộc múi giờ phiên: chạy thêm với PGTZ=UTC psql ...

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
-- expected có dạng "OK", "SQLSTATE" hoặc "SQLSTATE tên_ràng_buộc"; SQLSTATE có thể liệt kê nhiều mã
-- ngăn bằng "|" (xóa dòng bị RESTRICT chặn: bản 18.3 trả 23001, bản khác có thể trả 23503).
-- Không ghi tên ràng buộc thì chỉ so SQLSTATE.
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

CREATE FUNCTION pg_temp.emp(p_code TEXT) RETURNS BIGINT LANGUAGE sql STABLE AS
$$ SELECT id FROM employees WHERE employee_code = p_code $$;

CREATE FUNCTION pg_temp.shift(p_name TEXT) RETURNS BIGINT LANGUAGE sql STABLE AS
$$ SELECT id FROM shifts WHERE shift_name = p_name $$;

-- Tạo phân ca như backend: work_date là ngày bắt đầu theo múi giờ Asia/Ho_Chi_Minh
CREATE FUNCTION pg_temp.assign(p_emp TEXT, p_shift TEXT, p_start TEXT, p_end TEXT, p_break INTEGER DEFAULT 0)
RETURNS BIGINT LANGUAGE sql AS $$
    INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at, scheduled_break_minutes)
    VALUES (pg_temp.emp(p_emp), pg_temp.shift(p_shift),
            (p_start::timestamptz AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
            p_start::timestamptz, p_end::timestamptz, p_break)
    RETURNING id
$$;

CREATE TEMP TABLE ids (tag TEXT PRIMARY KEY, id BIGINT NOT NULL);
CREATE FUNCTION pg_temp.aid(p_tag TEXT) RETURNS BIGINT LANGUAGE sql STABLE AS
$$ SELECT id FROM ids WHERE tag = p_tag $$;

-- ===== Dữ liệu nền =====
INSERT INTO employees (employee_code, full_name, phone, hire_date, department_id)
SELECT v.code, v.full_name, v.phone, DATE '2026-01-01', d.id
FROM (VALUES
    ('T001', 'Phục vụ A',  '0900000001', 'Phục vụ'),
    ('T002', 'Phục vụ B',  '0900000002', 'Phục vụ'),
    ('T003', 'Quản lý C',  '0900000003', 'Quản lý'),
    ('T004', 'Thu ngân D', '0900000004', 'Thu ngân'),
    ('T005', 'Đầu bếp E',  '0900000005', 'Bếp'),
    ('T006', 'Phục vụ F',  '0900000006', 'Phục vụ'),
    ('T007', 'Phục vụ G',  '0900000007', 'Phục vụ'),
    ('T008', 'Phục vụ H',  '0900000008', 'Phục vụ'),
    ('T009', 'Phục vụ I',  '0900000009', 'Phục vụ')
) AS v (code, full_name, phone, dept)
JOIN departments d ON d.department_name = v.dept;

INSERT INTO shifts (shift_name, start_time, end_time, break_minutes) VALUES
    ('T-Ca sáng', '08:00', '12:00', 0),
    ('T-Ca tối',  '18:00', '22:00', 0),
    ('T-Ca đêm',  '22:00', '06:00', 30);

INSERT INTO accounts (employee_id, username, password_hash) VALUES
    (pg_temp.emp('T003'), 'quanly',  'bcrypt-placeholder'),
    (pg_temp.emp('T004'), 'thungan', 'bcrypt-placeholder'),
    (pg_temp.emp('T006'), 'nv006',   'bcrypt-placeholder'),
    (pg_temp.emp('T008'), 'nv008',   'bcrypt-placeholder'),
    (pg_temp.emp('T009'), 'nv009',   'bcrypt-placeholder');

-- Tài khoản quanly có hai vai trò: MANAGER và CASHIER
INSERT INTO account_roles (account_id, role_id)
SELECT a.id, r.id
FROM (VALUES ('quanly', 'MANAGER'), ('quanly', 'CASHIER'), ('thungan', 'CASHIER'),
             ('nv006', 'WAITER'), ('nv008', 'WAITER'), ('nv009', 'WAITER')) AS m (username, role_code)
JOIN accounts a ON a.username = m.username
JOIN roles r ON r.role_code = m.role_code;

-- ===== SC: cấu trúc và dữ liệu khởi tạo =====
SELECT pg_temp.assert_eq('SC-01', 'Đủ 11 bảng của phân hệ 1', '11',
    (SELECT count(*)::text FROM information_schema.tables
     WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
       AND table_name IN ('departments', 'employees', 'accounts', 'roles', 'permissions', 'account_roles',
                          'role_permissions', 'shifts', 'shift_assignments', 'attendances', 'attendance_adjustments')));
SELECT pg_temp.assert_eq('SC-02', 'Seed: 4 vai trò, 14 quyền, 19 dòng vai trò – quyền', '4/14/19',
    (SELECT (SELECT count(*) FROM roles) || '/' || (SELECT count(*) FROM permissions) || '/' || (SELECT count(*) FROM role_permissions)));
SELECT pg_temp.assert_eq('SC-03', 'Tài khoản mới bắt buộc đổi mật khẩu (mặc định) và ACTIVE', 'true/ACTIVE',
    (SELECT must_change_password::text || '/' || account_status FROM accounts WHERE username = 'quanly'));

-- ===== OV: chống chồng lịch (EXCLUDE chỉ áp dụng khi SCHEDULED) =====
SELECT pg_temp.expect('OV-00', 'Phân ca 18:00–22:00 ngày 02/03 cho T001', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-02 18:00+07', '2026-03-02 22:00+07')$q$);
SELECT pg_temp.expect('OV-01', 'Trùng hệt (cùng nhân viên, cùng giờ) bị từ chối', '23P01 ex_shift_assignments_no_overlap',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-02 18:00+07', '2026-03-02 22:00+07')$q$);
SELECT pg_temp.expect('OV-02', 'Chồng một phần 20:00–23:00 bị từ chối', '23P01 ex_shift_assignments_no_overlap',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-02 20:00+07', '2026-03-02 23:00+07')$q$);
SELECT pg_temp.expect('OV-03a', 'Liền kề sau 22:00–23:30 được chấp nhận (khoảng nửa mở)', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-02 22:00+07', '2026-03-02 23:30+07')$q$);
SELECT pg_temp.expect('OV-03b', 'Liền kề trước 16:30–18:00 được chấp nhận', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-02 16:30+07', '2026-03-02 18:00+07')$q$);
SELECT pg_temp.expect('OV-04a', 'Ca đêm 22:00 ngày 03/03 đến 06:00 ngày 04/03', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca đêm', '2026-03-03 22:00+07', '2026-03-04 06:00+07', 30)$q$);
SELECT pg_temp.expect('OV-04b', 'Ca sáng 05:00–09:00 ngày 04/03 chồng ca đêm hôm trước bị từ chối', '23P01 ex_shift_assignments_no_overlap',
    $q$SELECT pg_temp.assign('T001', 'T-Ca sáng', '2026-03-04 05:00+07', '2026-03-04 09:00+07')$q$);
SELECT pg_temp.expect('OV-04c', 'Ca sáng 06:00–10:00 ngày 04/03 liền kề ca đêm được chấp nhận', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca sáng', '2026-03-04 06:00+07', '2026-03-04 10:00+07')$q$);
SELECT pg_temp.expect('OV-05a', 'Tạo phân ca 18:00–22:00 ngày 05/03', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-05 18:00+07', '2026-03-05 22:00+07')$q$);
SELECT pg_temp.expect('OV-05b', 'Hủy phân ca đó (CANCELLED, lý do, người hủy, thời điểm)', 'OK',
    $q$UPDATE shift_assignments SET status = 'CANCELLED', cancel_reason = 'Đổi lịch',
              cancelled_by_id = pg_temp.emp('T003'), cancelled_at = TIMESTAMPTZ '2026-03-01 09:00+07'
       WHERE employee_id = pg_temp.emp('T001') AND work_date = DATE '2026-03-05'$q$);
SELECT pg_temp.expect('OV-05c', 'Ca đã hủy không chặn: tạo lại đúng giờ đó được chấp nhận', 'OK',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-05 18:00+07', '2026-03-05 22:00+07')$q$);
SELECT pg_temp.expect('OV-05d', 'Khôi phục ca đã hủy khi giờ đã bị lấp thì bị từ chối', '23P01 ex_shift_assignments_no_overlap',
    $q$UPDATE shift_assignments SET status = 'SCHEDULED', cancel_reason = NULL, cancelled_by_id = NULL, cancelled_at = NULL
       WHERE employee_id = pg_temp.emp('T001') AND work_date = DATE '2026-03-05' AND status = 'CANCELLED'$q$);
SELECT pg_temp.expect('OV-06', 'Nhân viên khác cùng giờ (T002) được chấp nhận', 'OK',
    $q$SELECT pg_temp.assign('T002', 'T-Ca tối', '2026-03-02 18:00+07', '2026-03-02 22:00+07')$q$);
SELECT pg_temp.expect('OV-07', 'Cùng thời điểm tuyệt đối nhưng ghi theo UTC vẫn bị từ chối', '23P01 ex_shift_assignments_no_overlap',
    $q$SELECT pg_temp.assign('T001', 'T-Ca tối', '2026-03-02 11:00+00', '2026-03-02 15:00+00')$q$);

-- ===== UQ: ràng buộc duy nhất =====
SELECT pg_temp.expect('UQ-01', 'Trùng mã nhân viên', '23505 uq_employees_code',
    $q$INSERT INTO employees (employee_code, full_name, phone, hire_date, department_id)
       VALUES ('T001', 'Trùng mã', '0900000099', DATE '2026-01-01', (SELECT id FROM departments LIMIT 1))$q$);
SELECT pg_temp.expect('UQ-02', 'Nhân viên có tài khoản thứ hai', '23505 uq_accounts_employee',
    $q$INSERT INTO accounts (employee_id, username, password_hash) VALUES (pg_temp.emp('T003'), 'quanly2', 'x')$q$);
SELECT pg_temp.expect('UQ-03', 'Trùng tên đăng nhập', '23505 uq_accounts_username',
    $q$INSERT INTO accounts (employee_id, username, password_hash) VALUES (pg_temp.emp('T005'), 'quanly', 'x')$q$);
SELECT pg_temp.expect('UQ-04', 'Tên đăng nhập có chữ hoa bị từ chối (duy nhất không phân biệt hoa thường)', '23514 ck_accounts_username',
    $q$INSERT INTO accounts (employee_id, username, password_hash) VALUES (pg_temp.emp('T005'), 'QuanLy', 'x')$q$);
SELECT pg_temp.expect('UQ-05', 'Gán trùng một vai trò cho tài khoản', '23505 uq_account_roles',
    $q$INSERT INTO account_roles (account_id, role_id)
       SELECT a.id, r.id FROM accounts a, roles r WHERE a.username = 'thungan' AND r.role_code = 'CASHIER'$q$);
SELECT pg_temp.expect('UQ-06', 'Gán trùng một quyền cho vai trò', '23505 uq_role_permissions',
    $q$INSERT INTO role_permissions (role_id, permission_id)
       SELECT r.id, p.id FROM roles r, permissions p WHERE r.role_code = 'CASHIER' AND p.permission_code = 'FIN_RECONCILE'$q$);
SELECT pg_temp.expect('UQ-07', 'Trùng tên ca', '23505 uq_shifts_name',
    $q$INSERT INTO shifts (shift_name, start_time, end_time) VALUES ('T-Ca sáng', '07:00', '11:00')$q$);
SELECT pg_temp.expect('UQ-08', 'Trùng tên bộ phận', '23505 uq_departments_name',
    $q$INSERT INTO departments (department_name) VALUES ('Bếp')$q$);

-- ===== CK: ràng buộc CHECK =====
SELECT pg_temp.expect('CK-01', 'Đã nghỉ việc nhưng thiếu ngày nghỉ', '23514 ck_employees_end_date',
    $q$INSERT INTO employees (employee_code, full_name, phone, hire_date, work_status, department_id)
       VALUES ('X001', 'x', '0900000098', DATE '2026-01-01', 'RESIGNED', (SELECT id FROM departments LIMIT 1))$q$);
SELECT pg_temp.expect('CK-02', 'Đang làm nhưng có ngày nghỉ việc', '23514 ck_employees_end_date',
    $q$INSERT INTO employees (employee_code, full_name, phone, hire_date, end_date, department_id)
       VALUES ('X002', 'x', '0900000097', DATE '2026-01-01', DATE '2026-02-01', (SELECT id FROM departments LIMIT 1))$q$);
SELECT pg_temp.expect('CK-03', 'Ngày nghỉ việc trước ngày vào làm', '23514 ck_employees_end_date',
    $q$INSERT INTO employees (employee_code, full_name, phone, hire_date, end_date, work_status, department_id)
       VALUES ('X003', 'x', '0900000096', DATE '2026-01-01', DATE '2025-12-31', 'RESIGNED', (SELECT id FROM departments LIMIT 1))$q$);
SELECT pg_temp.expect('CK-04', 'Trạng thái làm việc ngoài danh sách', '23514 ck_employees_status',
    $q$INSERT INTO employees (employee_code, full_name, phone, hire_date, work_status, department_id)
       VALUES ('X004', 'x', '0900000095', DATE '2026-01-01', 'FIRED', (SELECT id FROM departments LIMIT 1))$q$);
SELECT pg_temp.expect('CK-05', 'Trạng thái tài khoản ngoài danh sách', '23514 ck_accounts_status',
    $q$INSERT INTO accounts (employee_id, username, password_hash, account_status) VALUES (pg_temp.emp('T005'), 'bep', 'x', 'BLOCKED')$q$);
SELECT pg_temp.expect('CK-06', 'Mẫu ca có giờ bắt đầu bằng giờ kết thúc', '23514 ck_shifts_time',
    $q$INSERT INTO shifts (shift_name, start_time, end_time) VALUES ('X-Ca sai', '08:00', '08:00')$q$);
SELECT pg_temp.expect('CK-07', 'Khoảng nghỉ 300 phút vượt thời lượng ca 240 phút', '23514 ck_shifts_break',
    $q$INSERT INTO shifts (shift_name, start_time, end_time, break_minutes) VALUES ('X-Nghỉ dài', '08:00', '12:00', 300)$q$);
SELECT pg_temp.expect('CK-08', 'Khoảng nghỉ âm', '23514 ck_shifts_break',
    $q$INSERT INTO shifts (shift_name, start_time, end_time, break_minutes) VALUES ('X-Nghỉ âm', '08:00', '12:00', -5)$q$);
SELECT pg_temp.expect('CK-09', 'Khoảng nghỉ đúng bằng thời lượng ca (60/60) được chấp nhận', 'OK',
    $q$INSERT INTO shifts (shift_name, start_time, end_time, break_minutes) VALUES ('X-Biên', '14:00', '15:00', 60)$q$);
SELECT pg_temp.expect('CK-10', 'Tên ca chỉ có khoảng trắng', '23514 ck_shifts_name',
    $q$INSERT INTO shifts (shift_name, start_time, end_time) VALUES ('   ', '06:00', '07:00')$q$);
SELECT pg_temp.expect('CK-11', 'Phân ca có giờ kết thúc không sau giờ bắt đầu', '23514 ck_shift_assignments_time',
    $q$SELECT pg_temp.assign('T002', 'T-Ca tối', '2026-04-01 18:00+07', '2026-04-01 18:00+07')$q$);
SELECT pg_temp.expect('CK-12', 'Phân ca có khoảng nghỉ 300 phút vượt thời lượng 240 phút', '23514 ck_shift_assignments_break',
    $q$SELECT pg_temp.assign('T002', 'T-Ca tối', '2026-04-01 18:00+07', '2026-04-01 22:00+07', 300)$q$);
SELECT pg_temp.expect('CK-13', 'work_date theo UTC (02/04) lệch ngày theo giờ Việt Nam (03/04 00:30)', '23514 ck_shift_assignments_work_date',
    $q$INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at)
       VALUES (pg_temp.emp('T002'), pg_temp.shift('T-Ca tối'), DATE '2026-04-02',
               TIMESTAMPTZ '2026-04-02 17:30+00', TIMESTAMPTZ '2026-04-02 21:30+00')$q$);
SELECT pg_temp.expect('CK-14', 'Phân ca CANCELLED thiếu lý do, người hủy, thời điểm', '23514 ck_shift_assignments_cancel',
    $q$INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at, status)
       VALUES (pg_temp.emp('T002'), pg_temp.shift('T-Ca tối'), DATE '2026-04-03',
               TIMESTAMPTZ '2026-04-03 18:00+07', TIMESTAMPTZ '2026-04-03 22:00+07', 'CANCELLED')$q$);
SELECT pg_temp.expect('CK-15', 'Phân ca SCHEDULED mang lý do hủy', '23514 ck_shift_assignments_cancel',
    $q$INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at, cancel_reason)
       VALUES (pg_temp.emp('T002'), pg_temp.shift('T-Ca tối'), DATE '2026-04-03',
               TIMESTAMPTZ '2026-04-03 18:00+07', TIMESTAMPTZ '2026-04-03 22:00+07', 'x')$q$);
SELECT pg_temp.expect('CK-16', 'Trạng thái phân ca ngoài danh sách', '23514',
    $q$INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at, status)
       VALUES (pg_temp.emp('T002'), pg_temp.shift('T-Ca tối'), DATE '2026-04-03',
               TIMESTAMPTZ '2026-04-03 18:00+07', TIMESTAMPTZ '2026-04-03 22:00+07', 'DONE')$q$);

-- ===== FK, NOT NULL, giữ lịch sử (ON DELETE RESTRICT) =====
SELECT pg_temp.expect('FK-01', 'Nhân viên tham chiếu bộ phận không tồn tại', '23503 fk_employees_department',
    $q$INSERT INTO employees (employee_code, full_name, phone, hire_date, department_id)
       VALUES ('X005', 'x', '0900000094', DATE '2026-01-01', 999999)$q$);
SELECT pg_temp.expect('FK-02', 'Không xóa bộ phận đang có nhân viên', '23001|23503 fk_employees_department',
    $q$DELETE FROM departments WHERE department_name = 'Phục vụ'$q$);
SELECT pg_temp.expect('FK-03', 'Không xóa nhân viên đã có phân ca', '23001|23503',
    $q$DELETE FROM employees WHERE employee_code = 'T001'$q$);
SELECT pg_temp.expect('FK-04', 'Không xóa ca đã được phân', '23001|23503 fk_shift_assignments_shift',
    $q$DELETE FROM shifts WHERE shift_name = 'T-Ca tối'$q$);
SELECT pg_temp.expect('FK-05', 'Không xóa vai trò đang được gán', '23001|23503',
    $q$DELETE FROM roles WHERE role_code = 'CASHIER'$q$);
SELECT pg_temp.expect('FK-06', 'Người hủy phân ca phải là nhân viên tồn tại', '23503 fk_shift_assignments_cancelled_by',
    $q$UPDATE shift_assignments SET status = 'CANCELLED', cancel_reason = 'x', cancelled_by_id = 999999, cancelled_at = TIMESTAMPTZ '2026-03-01 09:00+07'
       WHERE employee_id = pg_temp.emp('T002') AND work_date = DATE '2026-03-02'$q$);
SELECT pg_temp.expect('NN-01', 'Tài khoản bắt buộc có nhân viên (employee_id NOT NULL)', '23502',
    $q$INSERT INTO accounts (employee_id, username, password_hash) VALUES (NULL, 'khongnv', 'x')$q$);
SELECT pg_temp.expect('NN-02', 'Phân ca bắt buộc có giờ bắt đầu đã lên lịch', '23502',
    $q$INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at)
       VALUES (pg_temp.emp('T002'), pg_temp.shift('T-Ca tối'), DATE '2026-04-05', NULL, TIMESTAMPTZ '2026-04-05 22:00+07')$q$);

-- ===== CC: chấm công, bảng công, điều chỉnh =====
INSERT INTO ids VALUES
    ('A1', pg_temp.assign('T002', 'T-Ca sáng', '2026-03-10 08:00+07', '2026-03-10 12:00+07')),
    ('A2', pg_temp.assign('T002', 'T-Ca tối',  '2026-03-10 18:00+07', '2026-03-10 22:00+07')),
    ('A3', pg_temp.assign('T002', 'T-Ca sáng', '2026-03-11 08:00+07', '2026-03-11 12:00+07')),
    ('A4', pg_temp.assign('T005', 'T-Ca đêm',  '2026-03-12 22:00+07', '2026-03-13 06:00+07', 30)),
    ('A5', pg_temp.assign('T002', 'T-Ca sáng', '2026-03-13 08:00+07', '2026-03-13 12:00+07')),
    ('A6', pg_temp.assign('T007', 'T-Ca sáng', '2026-03-14 08:00+07', '2026-03-14 12:00+07')),
    ('A7', pg_temp.assign('T007', 'T-Ca sáng', '2026-03-21 08:00+07', '2026-03-21 12:00+07'));

-- Dữ liệu chấm công không hợp lệ trên A5 (không dòng nào được lưu)
SELECT pg_temp.expect('CC-01', 'Có mặt đúng lịch không lưu giờ thực tế', '23514 ck_attendances_times',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, actual_start_at, actual_end_at, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'PRESENT', TIMESTAMPTZ '2026-03-13 08:00+07', TIMESTAMPTZ '2026-03-13 12:00+07', 240, pg_temp.emp('T003'))$q$);
SELECT pg_temp.expect('CC-02', 'Làm khác giờ thiếu giờ vào và ra', '23514 ck_attendances_times',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'DIFFERENT_HOURS', 0, pg_temp.emp('T003'))$q$);
SELECT pg_temp.expect('CC-03', 'Làm khác giờ có giờ ra bằng giờ vào', '23514 ck_attendances_times',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, actual_start_at, actual_end_at, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'DIFFERENT_HOURS', TIMESTAMPTZ '2026-03-13 09:00+07', TIMESTAMPTZ '2026-03-13 09:00+07', 0, pg_temp.emp('T003'))$q$);
SELECT pg_temp.expect('CC-04', 'Vắng mặt nhưng có số phút làm việc', '23514 ck_attendances_absent',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'ABSENT', 30, pg_temp.emp('T003'))$q$);
SELECT pg_temp.expect('CC-05', 'Số phút làm việc âm', '23514 ck_attendances_minutes',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'PRESENT', -1, pg_temp.emp('T003'))$q$);
SELECT pg_temp.expect('CC-06', 'Số phút làm việc 200 lớn hơn khoảng giờ vào–ra thực tế 180 phút', '23514 ck_attendances_bound',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, actual_start_at, actual_end_at, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'DIFFERENT_HOURS', TIMESTAMPTZ '2026-03-13 09:00+07', TIMESTAMPTZ '2026-03-13 12:00+07', 200, pg_temp.emp('T003'))$q$);
SELECT pg_temp.expect('CC-07', 'Trạng thái chấm công ngoài danh sách', '23514 ck_attendances_status',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A5'), 'LATE', 0, pg_temp.emp('T003'))$q$);
SELECT pg_temp.assert_eq('CC-08', 'Sau các ca lỗi, A5 vẫn chưa có dòng chấm công nào', '0',
    (SELECT count(*)::text FROM attendances WHERE shift_assignment_id = pg_temp.aid('A5')));

-- Xác nhận có mặt hàng loạt cho A1 và A4 trong một câu lệnh; phút = thời lượng ca đã lên lịch − nghỉ
SELECT pg_temp.expect('CC-10', 'Xác nhận có mặt hàng loạt (A1, A4) trong một câu lệnh', 'OK',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id, confirmed_at)
       SELECT sa.id, 'PRESENT',
              (EXTRACT(EPOCH FROM (sa.scheduled_end_at - sa.scheduled_start_at)) / 60)::integer - sa.scheduled_break_minutes,
              pg_temp.emp('T003'), TIMESTAMPTZ '2026-03-20 09:00+07'
       FROM shift_assignments sa
       WHERE sa.id IN (pg_temp.aid('A1'), pg_temp.aid('A4'))
         AND sa.status = 'SCHEDULED'
         AND sa.scheduled_end_at <= TIMESTAMPTZ '2026-03-20 09:00+07'$q$);
SELECT pg_temp.assert_eq('CC-11', 'Phút công nhận: ca sáng 240; ca đêm 480 − nghỉ 30 = 450', '240/450',
    (SELECT (SELECT worked_minutes FROM attendances WHERE shift_assignment_id = pg_temp.aid('A1')) || '/' ||
            (SELECT worked_minutes FROM attendances WHERE shift_assignment_id = pg_temp.aid('A4'))));
SELECT pg_temp.expect('CC-12', 'Làm khác giờ 18:30–21:30 (A2), 180 phút', 'OK',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, actual_start_at, actual_end_at, worked_minutes, confirmed_by_id, confirmed_at)
       VALUES (pg_temp.aid('A2'), 'DIFFERENT_HOURS', TIMESTAMPTZ '2026-03-10 18:30+07', TIMESTAMPTZ '2026-03-10 21:30+07', 180,
               pg_temp.emp('T003'), TIMESTAMPTZ '2026-03-20 09:00+07')$q$);
SELECT pg_temp.expect('CC-13', 'Vắng mặt (A3), 0 phút', 'OK',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id, confirmed_at)
       VALUES (pg_temp.aid('A3'), 'ABSENT', 0, pg_temp.emp('T003'), TIMESTAMPTZ '2026-03-20 09:00+07')$q$);
SELECT pg_temp.expect('CC-14', 'Xác nhận lại một ca đã có chấm công', '23505 uq_attendances_assignment',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       VALUES (pg_temp.aid('A1'), 'PRESENT', 240, pg_temp.emp('T003'))$q$);

-- Hàng loạt là một câu lệnh: một dòng trùng thì không dòng nào được lưu
SELECT pg_temp.expect('CC-15', 'Xác nhận hàng loạt (A1 đã xác nhận, A6 chưa) thất bại toàn bộ', '23505 uq_attendances_assignment',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       SELECT sa.id, 'PRESENT', 240, pg_temp.emp('T003') FROM shift_assignments sa
       WHERE sa.id IN (pg_temp.aid('A6'), pg_temp.aid('A1')) ORDER BY (sa.id = pg_temp.aid('A6')) DESC$q$);
SELECT pg_temp.assert_eq('CC-16', 'A6 không được lưu sau khi cả lô bị từ chối', '0',
    (SELECT count(*)::text FROM attendances WHERE shift_assignment_id = pg_temp.aid('A6')));

-- Có mặt đúng lịch chỉ khi ca đã kết thúc: ca A7 (21/03) chưa kết thúc tại thời điểm xác nhận 20/03
SELECT pg_temp.expect('CC-17', 'Câu lệnh hàng loạt có điều kiện "ca đã kết thúc" chạy được với ca A7 chưa kết thúc', 'OK',
    $q$INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id)
       SELECT sa.id, 'PRESENT', 240, pg_temp.emp('T003') FROM shift_assignments sa
       WHERE sa.id = pg_temp.aid('A7') AND sa.status = 'SCHEDULED'
         AND sa.scheduled_end_at <= TIMESTAMPTZ '2026-03-20 09:00+07'$q$);
SELECT pg_temp.assert_eq('CC-18', 'Ca chưa kết thúc không được chèn dòng chấm công nào', '0',
    (SELECT count(*)::text FROM attendances WHERE shift_assignment_id = pg_temp.aid('A7')));

-- Bảng công: view tổng hợp theo ngày
SELECT pg_temp.assert_eq('TS-01', 'Ngày 10/03 của T002 có hai ca nhưng chỉ một dòng bảng công', '1',
    (SELECT count(*)::text FROM v_daily_timesheets WHERE employee_id = pg_temp.emp('T002') AND work_date = DATE '2026-03-10'));
SELECT pg_temp.assert_eq('TS-02', 'Ngày 10/03: 2 ca đã làm, 420 phút (240 + 180)', '2/420',
    (SELECT shifts_worked || '/' || worked_minutes FROM v_daily_timesheets
     WHERE employee_id = pg_temp.emp('T002') AND work_date = DATE '2026-03-10'));
SELECT pg_temp.assert_eq('TS-03', 'Ngày 11/03 vắng: 0 ca làm, 1 ca vắng, 0 phút', '0/1/0',
    (SELECT shifts_worked || '/' || shifts_absent || '/' || worked_minutes FROM v_daily_timesheets
     WHERE employee_id = pg_temp.emp('T002') AND work_date = DATE '2026-03-11'));
SELECT pg_temp.assert_eq('TS-04', 'Số ngày đi làm của T002 trong 10–11/03 là 1 (không phải 2 hay 3)', '1',
    (SELECT count(*)::text FROM v_daily_timesheets
     WHERE employee_id = pg_temp.emp('T002') AND work_date BETWEEN DATE '2026-03-10' AND DATE '2026-03-11' AND shifts_worked > 0));
SELECT pg_temp.assert_eq('TS-05', 'Ca đêm tính vào ngày bắt đầu 12/03 (kết thúc 13/03); T005 là bếp không có tài khoản', '2026-03-12/450/0',
    (SELECT work_date || '/' || worked_minutes || '/' ||
            (SELECT count(*) FROM accounts WHERE employee_id = pg_temp.emp('T005'))
     FROM v_daily_timesheets WHERE employee_id = pg_temp.emp('T005')));

-- Điều chỉnh công: ghi nhật ký rồi cập nhật dòng chấm công (backend đặt hai câu trong một transaction)
SELECT pg_temp.expect('AD-01', 'Nhật ký điều chỉnh bắt buộc có lý do', '23514 ck_attendance_adjustments_reason',
    $q$INSERT INTO attendance_adjustments (attendance_id, old_status, new_status, old_worked_minutes, new_worked_minutes, reason, adjusted_by_id)
       SELECT id, 'PRESENT', 'ABSENT', 240, 0, '   ', pg_temp.emp('T003') FROM attendances WHERE shift_assignment_id = pg_temp.aid('A1')$q$);
SELECT pg_temp.expect('AD-02', 'Ghi nhật ký: A1 từ Có mặt 240 phút sang Làm khác giờ 120 phút', 'OK',
    $q$INSERT INTO attendance_adjustments (attendance_id, old_status, new_status, old_start_at, new_start_at, old_end_at, new_end_at,
                                           old_worked_minutes, new_worked_minutes, reason, adjusted_by_id, adjusted_at)
       SELECT id, attendance_status, 'DIFFERENT_HOURS', actual_start_at, TIMESTAMPTZ '2026-03-10 09:00+07',
              actual_end_at, TIMESTAMPTZ '2026-03-10 11:00+07', worked_minutes, 120, 'Đến muộn, về sớm', pg_temp.emp('T003'),
              TIMESTAMPTZ '2026-03-21 09:00+07'
       FROM attendances WHERE shift_assignment_id = pg_temp.aid('A1')$q$);
SELECT pg_temp.expect('AD-03', 'Cập nhật dòng chấm công A1 theo giá trị mới', 'OK',
    $q$UPDATE attendances SET attendance_status = 'DIFFERENT_HOURS',
              actual_start_at = TIMESTAMPTZ '2026-03-10 09:00+07', actual_end_at = TIMESTAMPTZ '2026-03-10 11:00+07', worked_minutes = 120
       WHERE shift_assignment_id = pg_temp.aid('A1')$q$);
SELECT pg_temp.assert_eq('AD-04', 'Bảng công tính lại ngay: 120 + 180 = 300 phút', '300',
    (SELECT worked_minutes::text FROM v_daily_timesheets WHERE employee_id = pg_temp.emp('T002') AND work_date = DATE '2026-03-10'));
SELECT pg_temp.expect('AD-05', 'Không sửa dòng nhật ký điều chỉnh', 'P0001',
    $q$UPDATE attendance_adjustments SET reason = 'sửa lén'$q$);
SELECT pg_temp.expect('AD-06', 'Không xóa dòng nhật ký điều chỉnh', 'P0001',
    $q$DELETE FROM attendance_adjustments$q$);
SELECT pg_temp.expect('AD-07', 'Không xóa chấm công đã có nhật ký điều chỉnh', '23001|23503 fk_attendance_adjustments_attendance',
    $q$DELETE FROM attendances WHERE shift_assignment_id = pg_temp.aid('A1')$q$);
SELECT pg_temp.expect('AD-08', 'Không xóa phân ca đã có chấm công', '23001|23503 fk_attendances_assignment',
    $q$DELETE FROM shift_assignments WHERE id = pg_temp.aid('A1')$q$);

-- ===== RB: phân quyền nhiều vai trò, khóa tài khoản =====
SELECT pg_temp.assert_eq('RB-01', 'Tài khoản hai vai trò MANAGER + CASHIER có đủ 14 quyền (hợp quyền)', '14',
    (SELECT count(*)::text FROM v_account_permissions WHERE account_id = (SELECT id FROM accounts WHERE username = 'quanly')));
SELECT pg_temp.assert_eq('RB-02', 'Thu ngân chỉ có 6 quyền, không có FIN_REPORT_VIEW', '6/0',
    (SELECT count(*) || '/' || count(*) FILTER (WHERE permission_code = 'FIN_REPORT_VIEW')
     FROM v_account_permissions WHERE account_id = (SELECT id FROM accounts WHERE username = 'thungan')));
SELECT pg_temp.assert_eq('RB-03', 'Phục vụ chưa có quyền nào trong seed của phân hệ 1 và 5', '0',
    (SELECT count(*)::text FROM v_account_permissions WHERE account_id = (SELECT id FROM accounts WHERE username = 'nv008')));
SELECT pg_temp.assert_eq('RB-04', 'Quy tắc Manager cuối cùng: nếu khóa quanly thì còn 0 Manager ACTIVE khác (backend phải chặn)', '0',
    (SELECT count(*)::text FROM accounts a
     JOIN account_roles ar ON ar.account_id = a.id JOIN roles r ON r.id = ar.role_id
     WHERE r.role_code = 'MANAGER' AND a.account_status = 'ACTIVE' AND a.employee_id <> pg_temp.emp('T003')));

-- Tạm nghỉ rồi đi làm lại: chỉ mở lại tài khoản SUSPENDED, không mở LOCKED
SELECT pg_temp.expect('LV-01', 'T008 tạm nghỉ, tạm vô hiệu hóa tài khoản (ACTIVE → SUSPENDED)', 'OK',
    $q$UPDATE accounts SET account_status = 'SUSPENDED' WHERE employee_id = pg_temp.emp('T008') AND account_status = 'ACTIVE'$q$);
SELECT pg_temp.expect('LV-02', 'T009 bị Manager khóa thủ công vì an ninh (LOCKED)', 'OK',
    $q$UPDATE accounts SET account_status = 'LOCKED' WHERE employee_id = pg_temp.emp('T009')$q$);
SELECT pg_temp.expect('LV-03', 'T008 và T009 chuyển Tạm nghỉ', 'OK',
    $q$UPDATE employees SET work_status = 'ON_LEAVE' WHERE employee_code IN ('T008', 'T009')$q$);
SELECT pg_temp.expect('LV-04', 'T008 và T009 chuyển lại Đang làm', 'OK',
    $q$UPDATE employees SET work_status = 'ACTIVE' WHERE employee_code IN ('T008', 'T009')$q$);
SELECT pg_temp.expect('LV-05', 'Đi làm lại: chỉ mở lại tài khoản đang SUSPENDED', 'OK',
    $q$UPDATE accounts SET account_status = 'ACTIVE' WHERE account_status = 'SUSPENDED'
         AND employee_id IN (pg_temp.emp('T008'), pg_temp.emp('T009'))$q$);
SELECT pg_temp.assert_eq('LV-06', 'T008 mở lại ACTIVE; T009 vẫn LOCKED (không tự mở)', 'ACTIVE/LOCKED',
    (SELECT (SELECT account_status FROM accounts WHERE username = 'nv008') || '/' ||
            (SELECT account_status FROM accounts WHERE username = 'nv009')));

-- ===== RS: nghỉ việc =====
INSERT INTO ids VALUES
    ('R1', pg_temp.assign('T006', 'T-Ca sáng', '2026-03-18 08:00+07', '2026-03-18 12:00+07')),
    ('R2', pg_temp.assign('T006', 'T-Ca sáng', '2026-03-19 08:00+07', '2026-03-19 12:00+07')),
    ('R3', pg_temp.assign('T006', 'T-Ca tối',  '2026-03-21 18:00+07', '2026-03-21 22:00+07'));
INSERT INTO attendances (shift_assignment_id, attendance_status, worked_minutes, confirmed_by_id, confirmed_at)
VALUES (pg_temp.aid('R1'), 'PRESENT', 240, pg_temp.emp('T003'), TIMESTAMPTZ '2026-03-18 13:00+07');

SELECT pg_temp.expect('RS-01', 'Ghi nghỉ việc hiệu lực 19/03', 'OK',
    $q$UPDATE employees SET work_status = 'RESIGNED', end_date = DATE '2026-03-19' WHERE employee_code = 'T006'$q$);
SELECT pg_temp.expect('RS-02', 'Vô hiệu hóa tài khoản của người nghỉ việc', 'OK',
    $q$UPDATE accounts SET account_status = 'DISABLED' WHERE employee_id = pg_temp.emp('T006')$q$);
SELECT pg_temp.expect('RS-03', 'Hủy phân ca SCHEDULED chưa chấm công từ ngày hiệu lực (kèm lý do, người hủy, thời điểm)', 'OK',
    $q$UPDATE shift_assignments SET status = 'CANCELLED', cancel_reason = 'Nghỉ việc từ 2026-03-19',
              cancelled_by_id = pg_temp.emp('T003'), cancelled_at = TIMESTAMPTZ '2026-03-20 09:00+07'
       WHERE employee_id = pg_temp.emp('T006') AND status = 'SCHEDULED' AND work_date >= DATE '2026-03-19'
         AND NOT EXISTS (SELECT 1 FROM attendances a WHERE a.shift_assignment_id = shift_assignments.id)$q$);
SELECT pg_temp.assert_eq('RS-04', 'Ca 18/03 đã chấm công giữ nguyên; ca 19/03 và 21/03 bị hủy', 'SCHEDULED/CANCELLED/CANCELLED',
    (SELECT string_agg(status, '/' ORDER BY work_date) FROM shift_assignments WHERE employee_id = pg_temp.emp('T006')));
SELECT pg_temp.assert_eq('RS-05', 'Chấm công cũ của người nghỉ việc vẫn còn; tài khoản DISABLED', '1/DISABLED',
    (SELECT (SELECT count(*) FROM attendances a JOIN shift_assignments sa ON sa.id = a.shift_assignment_id
             WHERE sa.employee_id = pg_temp.emp('T006')) || '/' ||
            (SELECT account_status FROM accounts WHERE employee_id = pg_temp.emp('T006'))));

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
