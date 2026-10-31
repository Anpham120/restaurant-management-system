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

# Sao lưu DB trước: bản mới có thể chạy migration Flyway mà quay lui image không hoàn tác được.
# Không sao lưu được thì dừng, chưa đụng vào gì.
scripts/backup-db.sh pre-deploy

# Giữ bản đang chạy bằng tag riêng ":previous" để quay lui. Không dùng ID image: sau khi build lại, image cũ
# mất tag và có thể bị Docker dọn, lúc đó quay lui báo "No such image".
tag_previous() {
  docker image inspect "$1:latest" >/dev/null 2>&1 && docker tag "$1:latest" "$1:previous"
}
HAVE_PREV=0
if tag_previous "$APP" && tag_previous "$WEB"; then HAVE_PREV=1; fi

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
  if [ "$HAVE_PREV" = 1 ]; then
    docker tag "$APP:previous" "$APP:latest"
    docker tag "$WEB:previous" "$WEB:latest"
    docker compose up -d --no-deps --no-build --wait app web || echo "[deploy] quay lui cũng lỗi, cần kiểm tra tay"
  else
    echo "[deploy] chưa có image trước để quay lui (lần triển khai đầu)"
  fi
}

if docker compose up -d --no-deps --build --wait app web && check; then
  # Thành công: bỏ tag quay lui để image cũ được dọn, không chiếm ổ đĩa
  docker rmi "$APP:previous" "$WEB:previous" >/dev/null 2>&1 || true
  docker image prune -f >/dev/null
  echo "[deploy] xong: $SHA"
else
  rollback
  exit 1
fi
