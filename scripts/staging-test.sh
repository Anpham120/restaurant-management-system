#!/usr/bin/env bash
# Staging tạm thời: dựng bản develop với secret ngẫu nhiên, chạy smoke test rồi hủy.
# Dữ liệu: bản sao lưu production mới nhất (BACKUP_DIR) nếu có, để migration Flyway và các API chạy thử trên dữ liệu
# thật trước khi lên production; không có thì dùng database trống. Database staging chỉ mở ở 127.0.0.1 và bị xóa
# (down -v) khi chạy xong. Cách nạp giống hệt scripts/restore-db.sh, nên mỗi lần chạy cũng là một lần thử khôi phục.
# Chạy trong thư mục staging (mã develop đã được Jenkinsfile đồng bộ). Không dùng secret thật của production.
# Cổng riêng (app 8091, web 8093, db 5439) và compose project riêng nên không đụng production.
set -euo pipefail

export COMPOSE_PROJECT_NAME=rms-staging
# compose.staging.yaml: cho phép baseline Flyway trên database staging mới tạo
export COMPOSE_FILE=compose.yaml:compose.staging.yaml
APP_URL=http://127.0.0.1:8091
WEB_URL=http://127.0.0.1:8093

rand() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-"$1"; }
SMOKE_PASSWORD=$(rand 20)

# Script ghi đè rồi xóa .env. Không chạy ở thư mục có .env thật (thư mục dự án của dev, thư mục deploy).
MARKER='# rms-staging: file tự sinh, bị xóa khi chạy xong'
if [ -f .env ] && [ "$(head -1 .env)" != "$MARKER" ]; then
  echo "[staging] DỪNG: thư mục này có .env không phải do staging tạo. Chạy trong thư mục staging riêng."
  exit 1
fi

cat > .env <<EOF
$MARKER
DB_NAME=restaurant_core
DB_PORT=5439
DB_ADMIN_USER=postgres
DB_ADMIN_PASSWORD=$(rand 24)
DB_APP_USER=rms_api
DB_APP_PASSWORD=$(rand 24)
APP_PORT=8091
WEB_PORT=8093
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=$(rand 48)
CORS_ALLOWED_ORIGINS=$WEB_URL
EOF

# Đặt trap sau khi kiểm tra .env, để lần dừng ở trên không xóa nhầm file thật
cleanup() {
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
  rm -f .env
}
trap cleanup EXIT

echo "[staging] dựng bản $(git rev-parse --short HEAD)"
BACKUP_DIR=${BACKUP_DIR:-/home/coder/rms-backups}
LATEST=$(ls -1t "$BACKUP_DIR"/*.dump 2>/dev/null | head -1 || true)
docker compose up -d --wait db
if [ -n "$LATEST" ]; then
  echo "[staging] nạp dữ liệu từ $(basename "$LATEST")"
  { echo 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'; docker compose exec -T db pg_restore -f - < "$LATEST"; } \
    | docker compose exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --single-transaction' >/dev/null
else
  echo "[staging] không có bản sao lưu, chạy trên database trống"
fi
docker compose up -d --build --wait

# Tài khoản MANAGER đầu tiên để thử đăng nhập
docker compose exec -T db psql -U postgres -d restaurant_core -v ON_ERROR_STOP=1 \
  -v username=smoke -v full_name=Smoke -v password="$SMOKE_PASSWORD" < docker/bootstrap-manager.sql >/dev/null

fail=0
check() {
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then echo "[staging] OK   $name"; else echo "[staging] FAIL $name"; fail=1; fi
}

unauthorized() { [ "$(curl -s -o /dev/null -w '%{http_code}' "$APP_URL/api/tables")" = 401 ]; }
TOKEN=$(curl -fsS -H 'Content-Type: application/json' -d "{\"username\":\"smoke\",\"password\":\"$SMOKE_PASSWORD\"}" \
  "$APP_URL/api/auth/login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' || true)
login() { [ -n "$TOKEN" ]; }
# Tài khoản mới tạo bị buộc đổi mật khẩu (server trả 403 PASSWORD_CHANGE_REQUIRED); đổi xong thì token đang có dùng được
if [ -n "$TOKEN" ]; then
  curl -fsS -o /dev/null -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"currentPassword\":\"$SMOKE_PASSWORD\",\"newPassword\":\"$(rand 20)\"}" "$APP_URL/api/auth/change-password" || true
fi

# Quét đọc mọi module bằng quyền quản lý: bắt lỗi SQL lệch schema hoặc dữ liệu thật mà test tích hợp không có
TO=$(date -u +%F)
FROM=$(date -u -d "@$(( $(date +%s) - 30 * 86400 ))" +%F)       # 30 ngày gần nhất; dạng @giây chạy được cả busybox
R="from=$FROM&to=$TO"
READ_APIS="/api/auth/me /api/departments /api/roles /api/employees /api/shifts /api/shift-assignments?$R /api/attendances?$R
  /api/tables /api/kitchen/queue /api/menu-categories /api/menu-items /api/ingredients /api/kitchen-ingredient-types
  /api/inventory/raw-stock /api/inventory/handover /api/inventory/kitchen-stock /api/inventory/unusable-lots
  /api/inventory/history?from=${FROM}T00:00:00Z&to=${TO}T23:59:59Z /api/inventory/preparation-yield?$R
  /api/inventory/raw-lots /api/inventory/kitchen-lots
  /api/stock-receipts /api/stock-issues /api/stock-counts /api/kitchen-preparations /api/stock-returns /api/stock-adjustments
  /api/reports/revenue-by-day?$R /api/reports/revenue-by-item?$R /api/reports/unpaid-closures?$R
  /api/reports/expenses-by-category?$R /api/expenses?$R"
read_sweep() {
  local bad=0 p code
  for p in $READ_APIS; do
    code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$APP_URL$p")
    [ "$code" = 200 ] || { echo "[staging]      $p → $code" >&2; bad=1; }
  done
  return $bad
}

check "health UP"                      sh -c "curl -fsS $APP_URL/actuator/health | grep -q '\"UP\"'"
check "trang web phục vụ được"         sh -c "curl -fsS $WEB_URL/ | grep -q 'Atlas Restaurant'"
check "API không token trả 401"        unauthorized
check "đăng nhập cấp được token"       login
check "endpoint metrics có số liệu"    sh -c "curl -fsS $APP_URL/actuator/prometheus | grep -q jvm_memory_used_bytes"
if read_sweep; then echo "[staging] OK   đọc dữ liệu mọi module ($(echo $READ_APIS | wc -w) API)"; else echo "[staging] FAIL đọc dữ liệu mọi module"; fail=1; fi

if [ "$fail" -ne 0 ]; then
  echo "[staging] smoke test THẤT BẠI, không promote lên main"
  docker compose logs --tail 40 app || true
  exit 1
fi
echo "[staging] smoke test đạt"
