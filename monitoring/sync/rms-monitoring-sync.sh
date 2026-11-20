#!/usr/bin/env bash
# Máy 2: giữ /home/coder/rms-deploy theo nhánh main; khi cấu hình giám sát đổi thì nạp lại hoặc dựng lại container.
# Chạy bởi rms-monitoring-sync.timer (5 phút/lần), user coder. Không đụng monitoring/.env và monitoring/secrets/ (không có trong Git).
set -euo pipefail
cd /home/coder/rms-deploy
git fetch -q origin main
old=$(git rev-parse HEAD); new=$(git rev-parse origin/main)
[ "$old" = "$new" ] && exit 0
changed=$(git diff --name-only "$old" "$new" -- monitoring/)
git reset -q --hard "$new"
echo "cập nhật ${old:0:7} -> ${new:0:7}"
[ -z "$changed" ] && exit 0
cd monitoring
if grep -q '^monitoring/compose.yaml$' <<<"$changed"; then
  docker compose up -d          # compose đổi (image, biến môi trường): dựng lại container liên quan
fi
grep -q '^monitoring/prometheus/'   <<<"$changed" && docker compose kill -s SIGHUP prometheus
grep -q '^monitoring/alertmanager/' <<<"$changed" && docker compose kill -s SIGHUP alertmanager
grep -q '^monitoring/blackbox/'     <<<"$changed" && docker compose restart blackbox
grep -q '^monitoring/grafana/'      <<<"$changed" && docker compose restart grafana
echo "đã áp dụng: $(tr '\n' ' ' <<<"$changed")"
