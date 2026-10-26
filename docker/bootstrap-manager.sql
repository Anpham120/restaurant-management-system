-- Tạo tài khoản MANAGER đầu tiên trên database MỚI (không có tài khoản nào để đăng nhập tạo người khác).
-- Chạy bằng tài khoản quản trị, mật khẩu truyền qua biến psql (không ghi vào file):
--   docker compose exec -T db psql -U postgres -d restaurant_core -v ON_ERROR_STOP=1 \
--       -v username=manager -v full_name='Quản lý' -v password='<mật khẩu>' < docker/bootstrap-manager.sql
-- Băm mật khẩu bằng bcrypt (pgcrypto, cùng định dạng $2a$ với BCryptPasswordEncoder); extension được cài tạm vào
-- schema riêng rồi gỡ ngay trong cùng transaction nên schema v4.1 không đổi. Tài khoản bị buộc đổi mật khẩu.
BEGIN;
CREATE SCHEMA rc_bootstrap_tmp;
CREATE EXTENSION pgcrypto SCHEMA rc_bootstrap_tmp;

INSERT INTO departments (department_name) VALUES ('Quản lý') ON CONFLICT (department_name) DO NOTHING;
WITH emp AS (
    INSERT INTO employees (employee_code, full_name, department_id)
    SELECT 'MGR-' || upper(:'username'), :'full_name', id FROM departments WHERE department_name = 'Quản lý'
    RETURNING id
), acc AS (
    INSERT INTO accounts (employee_id, username, password_hash, must_change_password)
    SELECT id, :'username', rc_bootstrap_tmp.crypt(:'password', rc_bootstrap_tmp.gen_salt('bf', 10)), true FROM emp
    RETURNING id
)
INSERT INTO account_roles (account_id, role_id)
SELECT acc.id, r.id FROM acc, roles r WHERE r.role_code = 'MANAGER';

DROP EXTENSION pgcrypto;
DROP SCHEMA rc_bootstrap_tmp;
COMMIT;
