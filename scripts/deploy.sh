#!/usr/bin/env bash
# CD: triển khai nhánh main lên máy chủ. Chạy trong thư mục triển khai (có .env thật).
# Build image mới rồi chờ healthy. Lỗi ở bước nào thì quay về image đang chạy trước đó.
# --no-deps: không đụng container db và volume dữ liệu.
set -euo pipefail

# Cố định tên project để dùng đúng volume pgdata đang có (mặc định compose lấy theo tên thư mục)
export COMPOSE_PROJECT_NAME=restaurant-management-system
APP=restaurant-management-system-app
WEB=restaurant-management-system-web

# Mã nguồn main đã được Jenkinsfile đồng bộ vào thư mục này trước khi gọi script
SHA=$(git rev-parse --short HEAD)
echo "[deploy] commit $SHA"

# Ghi nhớ image đang chạy để quay lui
PREV_APP=$(docker inspect --format '{{.Image}}' "$APP" 2>/dev/null || true)
PREV_WEB=$(docker inspect --format '{{.Image}}' "$WEB" 2>/dev/null || true)

healthy() {
  curl -fsS http://127.0.0.1:8081/actuator/health | grep -q '"UP"' && curl -fsS -o /dev/null http://127.0.0.1:8083/
}

check() {
  for _ in $(seq 1 24); do
    healthy && return 0
    sleep 5
  done
  return 1
}

rollback() {
  echo "[deploy] LỖI, quay lui về image trước"
  if [ -n "$PREV_APP" ] && [ -n "$PREV_WEB" ]; then
    docker tag "$PREV_APP" "$APP:latest"
    docker tag "$PREV_WEB" "$WEB:latest"
    docker compose up -d --no-deps --no-build --wait app web || echo "[deploy] quay lui cũng lỗi, cần kiểm tra tay"
  else
    echo "[deploy] chưa có image trước để quay lui (lần triển khai đầu)"
  fi
}

if docker compose up -d --no-deps --build --wait app web && check; then
  docker image prune -f >/dev/null
  echo "[deploy] xong: $SHA"
else
  rollback
  exit 1
fi
