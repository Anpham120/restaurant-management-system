#!/usr/bin/env bash
# Giữ /home/coder/rms-deploy theo nhánh main; khi cấu hình giám sát đổi thì nạp lại hoặc dựng lại container.
# Chạy bởi rms-monitoring-sync.timer (5 phút/lần), user coder. Không đụng monitoring/.env và monitoring/secrets/ (không có trong Git).
#   máy 2: SERVICES rỗng = cả cụm giám sát
#   máy 1: SERVICES=node-exporter (đặt trong unit). Máy 1 cũng cần bản mới của scripts/ (sao lưu chạy bằng cron từ thư mục này).
set -euo pipefail
SERVICES=${SERVICES:-}
cd /home/coder/rms-deploy
git fetch -q origin main
old=$(git rev-parse HEAD); new=$(git rev-parse origin/main)
[ "$old" = "$new" ] && exit 0
changed=$(git diff --name-only "$old" "$new" -- monitoring/)
git reset -q --hard "$new"
echo "cập nhật ${old:0:7} -> ${new:0:7}"
[ -z "$changed" ] && exit 0
cd monitoring
has() { [ -z "$SERVICES" ] || grep -qw "$1" <<<"$SERVICES"; }
if grep -q '^monitoring/compose.yaml$' <<<"$changed"; then
  docker compose up -d $SERVICES     # compose đổi (image, biến môi trường): dựng lại container liên quan
fi
if has prometheus   && grep -q '^monitoring/prometheus/'   <<<"$changed"; then docker compose kill -s SIGHUP prometheus; fi
if has alertmanager && grep -q '^monitoring/alertmanager/' <<<"$changed"; then docker compose kill -s SIGHUP alertmanager; fi
if has blackbox     && grep -q '^monitoring/blackbox/'     <<<"$changed"; then docker compose restart blackbox; fi
if has grafana      && grep -q '^monitoring/grafana/'      <<<"$changed"; then docker compose restart grafana; fi
echo "đã áp dụng: $(tr '\n' ' ' <<<"$changed")"
