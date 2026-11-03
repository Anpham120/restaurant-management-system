-- Nghiệp vụ chuyển từ hàm rc_* (PL/pgSQL) lên tầng Service Java (Spring Data JPA).
-- Ứng dụng (role rc_app) cần ghi trực tiếp các bảng mà trước đây chỉ hàm rc_* được ghi.
-- Giữ nguyên: CHECK, FOREIGN KEY, UNIQUE và trigger bảo vệ dữ liệu (máy trạng thái, chỉ-thêm, cân sổ kho) —
-- chúng là lưới an toàn cuối cùng, Service phải tự kiểm tra trước để trả lỗi rõ ràng.
-- Không cấp: roles (danh mục cố định, chỉ đọc), bảng lịch sử Flyway. Không cấp quyền DDL.
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT c.oid::regclass AS rel
               FROM pg_class c
              WHERE c.relnamespace = current_schema()::regnamespace
                AND c.relkind = 'r'
                AND c.relowner = 'rc_owner'::regrole
                AND c.relname <> 'roles'
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %s TO rc_app', t.rel);
    END LOOP;
END $$;
