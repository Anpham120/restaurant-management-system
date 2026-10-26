#!/usr/bin/env bash
# Chạy MỘT LẦN khi volume PostgreSQL còn trống (cơ chế /docker-entrypoint-initdb.d), sau 04 → 09 → 10.
# Tạo tài khoản đăng nhập cho ứng dụng trong role nhóm đã chốt ở docs/09-acl-v4.1.sql; không cấp thêm quyền nào.
set -euo pipefail

: "${DB_APP_USER:?thiếu DB_APP_USER}"
: "${DB_APP_PASSWORD:?thiếu DB_APP_PASSWORD}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_user="$DB_APP_USER" -v app_password="$DB_APP_PASSWORD" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password' IN ROLE rc_app;
SQL

# Tài khoản cho bộ xử lý webhook SePay (role rc_webhook): chỉ tạo khi có cấu hình.
if [[ -n "${DB_WEBHOOK_USER:-}" && -n "${DB_WEBHOOK_PASSWORD:-}" ]]; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
       -v hook_user="$DB_WEBHOOK_USER" -v hook_password="$DB_WEBHOOK_PASSWORD" <<'SQL'
CREATE ROLE :"hook_user" LOGIN PASSWORD :'hook_password' IN ROLE rc_webhook;
SQL
fi
