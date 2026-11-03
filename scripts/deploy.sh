#!/usr/bin/env bash
# CD: triển khai nhánh main lên máy chủ. Chạy trong thư mục triển khai (có .env thật).
# Không build lại: chạy đúng image rms-release-*:<commit> mà build develop đã dựng, smoke test và quét Trivy
# (scripts/tag-release.sh). --no-deps: không đụng container db và volume dữ liệu.
# Hai chế độ:
#   blue-green (khi nginx máy chủ đã cân bằng giữa 2 slot, docs/devops/nginx/): dựng slot rảnh, healthy thì dừng slot cũ.
#              Không gián đoạn; lỗi thì slot cũ vẫn phục vụ.
#   tại chỗ   (nginx chưa cấu hình 2 slot): thay container slot A, lỗi thì quay về image ":previous". Gián đoạn ~15–25 giây.
set -euo pipefail

# Cố định tên project để dùng đúng volume pgdata đang có (mặc định compose lấy theo tên thư mục)
export COMPOSE_PROJECT_NAME=restaurant-management-system
# Production luôn chạy profile prod. Biến shell được compose ưu tiên hơn .env, nên giá trị trong credential không ghi đè được.
export SPRING_PROFILES_ACTIVE=prod
# Cho phép compose thấy app-b/web-b (slot B); các lệnh dưới luôn gọi đích danh service nên không dựng thừa gì
export COMPOSE_PROFILES=slot-b
APP=restaurant-management-system-app
WEB=restaurant-management-system-web

# Mã nguồn main đã được Jenkinsfile đồng bộ vào thư mục này trước khi gọi script
SHA=$(git rev-parse HEAD)
echo "[deploy] commit ${SHA:0:7}"

# main chỉ nhận fast-forward từ develop, nên commit này phải có image phát hành từ build develop.
# Thiếu thì dừng trước khi đụng vào gì: không build tạm một image chưa được kiểm.
for svc in app web; do
  if ! docker image inspect "rms-release-$svc:$SHA" >/dev/null 2>&1; then
    echo "[deploy] DỪNG: không có image rms-release-$svc:${SHA:0:7}. Commit chưa qua build develop (commit thẳng lên main?)"
    echo "[deploy] hoặc image đã bị dọn. Chạy lại build develop của commit này rồi chạy lại build main."
    exit 1
  fi
done

# Sao lưu DB trước: bản mới có thể chạy migration Flyway mà quay lui image không hoàn tác được.
# Không sao lưu được thì dừng, chưa đụng vào gì.
scripts/backup-db.sh pre-deploy

# Slot A = app/web (cổng 8081/8083), slot B = app-b/web-b (8086/8087). Image theo tên mặc định của compose.
svc_app()  { [ "$1" = A ] && echo app  || echo app-b; }
svc_web()  { [ "$1" = A ] && echo web  || echo web-b; }
port_app() { [ "$1" = A ] && echo 8081 || echo 8086; }
port_web() { [ "$1" = A ] && echo 8083 || echo 8087; }
img_app()  { [ "$1" = A ] && echo "$APP:latest" || echo "$APP-b:latest"; }
img_web()  { [ "$1" = A ] && echo "$WEB:latest" || echo "$WEB-b:latest"; }
running()  { [ -n "$(docker compose ps -q --status running "$(svc_app "$1")" 2>/dev/null)" ]; }

healthy() {
  curl -fsS "http://127.0.0.1:$(port_app "$1")/actuator/health" | grep -q '"UP"' \
    && curl -fsS -o /dev/null "http://127.0.0.1:$(port_web "$1")/"
}

check() {
  for _ in $(seq 1 24); do
    healthy "$1" && return 0
    sleep 5
  done
  return 1
}

# Kiểm như người dùng thật: qua nginx + HTTPS của domain (PUBLIC_URL do Jenkinsfile truyền; không có thì bỏ qua).
# Bắt lỗi mà kiểm trong máy không thấy: nginx trỏ sai cổng, chứng chỉ hết hạn, web trả trang lỗi.
public_ok() {
  [ -z "${PUBLIC_URL:-}" ] && return 0
  local ok=0
  curl -fsS --max-time 10 "$PUBLIC_URL/" | grep -q 'Atlas Restaurant' || { echo "[deploy] $PUBLIC_URL/ không trả trang web"; ok=1; }
  curl -fsS --max-time 10 "$PUBLIC_URL/actuator/health" | grep -q '"UP"' || { echo "[deploy] $PUBLIC_URL/actuator/health không UP"; ok=1; }
  [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$PUBLIC_URL/api/tables")" = 401 ] \
    || { echo "[deploy] $PUBLIC_URL/api/tables không trả 401"; ok=1; }
  [ "$ok" = 0 ] && echo "[deploy] kiểm qua domain: web, health, API 401 đều đúng"
  return "$ok"
}

# nginx máy chủ đã có upstream 2 slot? Jenkinsfile mount /etc/nginx chỉ đọc vào container deploy.
NGINX_SITE=${NGINX_SITE:-/etc/nginx/sites-enabled/rms.atlasplatform12.tech.conf}
blue_green_ready() { grep -qs "127.0.0.1:$(port_app B)" "$NGINX_SITE"; }

deploy_blue_green() {
  local old=A new=B
  if running B; then old=B; new=A; fi
  echo "[deploy] blue-green: slot $old đang phục vụ, dựng slot $new"
  docker tag "rms-release-app:$SHA" "$(img_app "$new")"
  docker tag "rms-release-web:$SHA" "$(img_web "$new")"

  if ! docker compose up -d --no-deps --no-build --force-recreate --wait "$(svc_app "$new")" "$(svc_web "$new")" \
      || ! check "$new"; then
    echo "[deploy] slot $new không healthy: dừng slot mới, slot $old vẫn phục vụ"
    docker compose stop "$(svc_app "$new")" "$(svc_web "$new")" || true
    return 1
  fi

  # Vài giây cả hai slot cùng phục vụ: migration expand/contract (scripts/check-migrations.sh) bảo đảm bản cũ chạy được
  # trên schema mới. Dừng slot cũ: Spring tắt êm (xử lý nốt request đang chạy); nginx chuyển request sang slot mới.
  docker compose stop "$(svc_app "$old")" "$(svc_web "$old")"
  if ! public_ok; then
    echo "[deploy] kiểm qua domain lỗi: bật lại slot $old, dừng slot $new"
    { docker compose start "$(svc_app "$old")" "$(svc_web "$old")" && check "$old"; } \
      || echo "[deploy] bật lại slot $old cũng lỗi, cần kiểm tra tay"
    docker compose stop "$(svc_app "$new")" "$(svc_web "$new")" || true
    return 1
  fi
  # Giữ container slot cũ (đã dừng): quay về bản trước bằng tay chỉ cần start slot cũ rồi stop slot mới.
  echo "[deploy] slot $new đang phục vụ, slot $old đã dừng"
}

# ---------- tại chỗ (chưa có nginx 2 slot)
deploy_in_place() {
  # Giữ bản đang chạy bằng tag riêng ":previous" để quay lui. Không dùng ID image: sau khi build lại, image cũ
  # mất tag và có thể bị Docker dọn, lúc đó quay lui báo "No such image".
  local have_prev=0
  if docker image inspect "$APP:latest" >/dev/null 2>&1 && docker image inspect "$WEB:latest" >/dev/null 2>&1; then
    docker tag "$APP:latest" "$APP:previous"
    docker tag "$WEB:latest" "$WEB:previous"
    have_prev=1
  fi
  docker tag "rms-release-app:$SHA" "$APP:latest"
  docker tag "rms-release-web:$SHA" "$WEB:latest"

  if docker compose up -d --no-deps --no-build --wait app web && check A && public_ok; then
    # Thành công: bỏ tag quay lui để image cũ được dọn, không chiếm ổ đĩa
    docker rmi "$APP:previous" "$WEB:previous" >/dev/null 2>&1 || true
    return 0
  fi
  echo "[deploy] LỖI, quay lui về image trước"
  if [ "$have_prev" = 1 ]; then
    docker tag "$APP:previous" "$APP:latest"
    docker tag "$WEB:previous" "$WEB:latest"
    docker compose up -d --no-deps --no-build --wait app web || echo "[deploy] quay lui cũng lỗi, cần kiểm tra tay"
  else
    echo "[deploy] chưa có image trước để quay lui (lần triển khai đầu)"
  fi
  return 1
}

if blue_green_ready; then
  deploy_blue_green || exit 1
else
  echo "[deploy] nginx chưa cân bằng 2 slot ($NGINX_SITE): deploy tại chỗ"
  deploy_in_place || exit 1
fi
docker image prune -f >/dev/null

# Giám sát chạy từ thư mục này (monitoring/compose.yaml): nạp lại cấu hình vừa cập nhật (luật cảnh báo, target).
# Chạy từ nơi khác (thư mục dev) thì không đụng, tránh nạp cấu hình của một nhánh đang sửa dở.
for c in monitoring-prometheus-1 monitoring-alertmanager-1; do
  if [ "$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' "$c" 2>/dev/null)" = "$PWD/monitoring" ]; then
    docker kill -s HUP "$c" >/dev/null && echo "[deploy] nạp lại cấu hình $c"
  fi
done
echo "[deploy] xong: ${SHA:0:7}"
