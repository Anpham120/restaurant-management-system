#!/usr/bin/env bash
# Quay về bản trước bằng tay khi deploy blue-green đã qua kiểm tra nhưng phát hiện lỗi sau đó (lỗi nghiệp vụ, chậm...).
# Deploy blue-green chỉ dừng slot cũ, không xóa: bật lại slot cũ (image cũ còn nguyên), healthy thì dừng slot mới.
# Không chạm database: migration expand/contract (scripts/check-migrations.sh) bảo đảm bản cũ chạy được trên schema mới.
#   cd /home/coder/rms-deploy && scripts/rollback.sh
# Dùng docker start/stop theo tên container, không cần .env (deploy xóa .env sau khi chạy).
# Lần deploy sau tự nhận slot đang chạy là slot cũ, không cần làm gì thêm.
set -euo pipefail

# Biến môi trường chỉ để chạy thử trên bản sao (tên project, cổng, CHECK_TRIES); production dùng giá trị mặc định
P=${RMS_PROJECT:-restaurant-management-system}
PUBLIC_URL=${PUBLIC_URL:-https://rms.atlasplatform12.tech}
app() { [ "$1" = A ] && echo "$P-app-1" || echo "$P-app-b-1"; }
web() { [ "$1" = A ] && echo "$P-web-1" || echo "$P-web-b-1"; }
port_app() { [ "$1" = A ] && echo "${APP_PORT:-8081}" || echo "${APP_B_PORT:-8086}"; }
port_web() { [ "$1" = A ] && echo "${WEB_PORT:-8083}" || echo "${WEB_B_PORT:-8087}"; }
running() { [ "$(docker inspect -f '{{.State.Running}}' "$(app "$1")" 2>/dev/null)" = true ]; }
image() { docker inspect -f '{{.Image}}' "$(app "$1")" | cut -c8-19; }

if running A && ! running B; then cur=A; old=B
elif running B && ! running A; then cur=B; old=A
else echo "[rollback] DỪNG: cần đúng một slot đang chạy (A: $(running A && echo chạy || echo dừng), B: $(running B && echo chạy || echo dừng))"; exit 1
fi
docker inspect "$(app "$old")" "$(web "$old")" >/dev/null 2>&1 \
  || { echo "[rollback] DỪNG: không còn container slot $old để quay về"; exit 1; }
echo "[rollback] slot $cur (image app $(image "$cur")) → slot $old (image app $(image "$old"))"

docker start "$(app "$old")" "$(web "$old")" >/dev/null
ok=1
for _ in $(seq 1 ${CHECK_TRIES:-24}); do
  if curl -fsS "http://127.0.0.1:$(port_app "$old")/actuator/health" | grep -q '"UP"' \
     && curl -fsS -o /dev/null "http://127.0.0.1:$(port_web "$old")/"; then ok=0; break; fi
  sleep 5
done
if [ "$ok" != 0 ]; then
  echo "[rollback] slot $old không healthy: dừng lại, slot $cur vẫn phục vụ"
  docker stop "$(app "$old")" "$(web "$old")" >/dev/null || true
  exit 1
fi

# Spring tắt êm (xử lý nốt request đang chạy); nginx chuyển sang slot còn lại
docker stop "$(app "$cur")" "$(web "$cur")" >/dev/null
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$PUBLIC_URL/api/tables" || true)
curl -fsS --max-time 10 "$PUBLIC_URL/actuator/health" | grep -q '"UP"' && [ "$code" = 401 ] \
  || echo "[rollback] CẢNH BÁO: kiểm qua domain chưa đúng (API trả $code), kiểm tra tay"
echo "[rollback] xong: slot $old đang phục vụ, slot $cur đã dừng (bật lại slot $cur bằng chính script này)"
