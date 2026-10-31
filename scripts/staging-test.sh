#!/usr/bin/env bash
# Staging tạm thời: dựng bản develop với database trống và secret ngẫu nhiên, chạy smoke test rồi hủy.
# Chạy trong thư mục staging (mã develop đã được Jenkinsfile đồng bộ). Không dùng secret thật của production.
# Cổng riêng (app 8091, web 8093, db 5439) và compose project riêng nên không đụng production.
set -euo pipefail

export COMPOSE_PROJECT_NAME=rms-staging
APP_URL=http://127.0.0.1:8091
WEB_URL=http://127.0.0.1:8093

rand() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-"$1"; }
SMOKE_PASSWORD=$(rand 20)

cat > .env <<EOF
DB_NAME=restaurant_core
DB_PORT=5439
DB_ADMIN_USER=postgres
DB_ADMIN_PASSWORD=$(rand 24)
DB_APP_USER=rms_api
DB_APP_PASSWORD=$(rand 24)
APP_PORT=8091
WEB_PORT=8093
FLYWAY_BASELINE_ON_MIGRATE=true
JWT_SECRET=$(rand 48)
CORS_ALLOWED_ORIGINS=$WEB_URL
EOF

cleanup() {
  docker compose down -v --remove-orphans >/dev/null 2>&1 || true
  rm -f .env
}
trap cleanup EXIT

echo "[staging] dựng bản $(git rev-parse --short HEAD)"
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
login() {
  curl -fsS -H 'Content-Type: application/json' \
    -d "{\"username\":\"smoke\",\"password\":\"$SMOKE_PASSWORD\"}" "$APP_URL/api/auth/login" | grep -q accessToken
}

check "health UP"                      sh -c "curl -fsS $APP_URL/actuator/health | grep -q '\"UP\"'"
check "trang web phục vụ được"         sh -c "curl -fsS $WEB_URL/ | grep -q 'Atlas Restaurant'"
check "API không token trả 401"        unauthorized
check "đăng nhập cấp được token"       login
check "endpoint metrics có số liệu"    sh -c "curl -fsS $APP_URL/actuator/prometheus | grep -q jvm_memory_used_bytes"

if [ "$fail" -ne 0 ]; then
  echo "[staging] smoke test THẤT BẠI, không promote lên main"
  docker compose logs --tail 40 app || true
  exit 1
fi
echo "[staging] smoke test đạt"
