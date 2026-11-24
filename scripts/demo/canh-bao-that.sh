#!/usr/bin/env bash
# Demo cảnh báo THẬT về Telegram (chạy trên máy 2, user coder). Production không bị ảnh hưởng.
#
#   bash scripts/demo/canh-bao-that.sh node   tạm dừng node-exporter máy 2 → luật NodeExporterDown (for 2m) bắn,
#                                               Alertmanager gửi Telegram; bật lại → Telegram nhận tin "đã khôi phục".
#                                               Mất khoảng 3–4 phút. Dừng dưới 5 phút nên không kéo theo luật khác.
#   bash scripts/demo/canh-bao-that.sh gia    gửi thẳng 1 cảnh báo thử vào Alertmanager (không qua luật Prometheus):
#                                               Telegram nhận ngay, dùng khi không đủ thời gian chờ.
set -uo pipefail
AM=127.0.0.1:9093
PROM=127.0.0.1:9090
CONTAINER=monitoring-node-exporter-1
gio() { TZ=Asia/Ho_Chi_Minh date +%H:%M:%S; }
# Trạng thái luật NodeExporterDown của máy 2 trong Prometheus: inactive / pending / firing
trang_thai() {
  curl -s "$PROM/api/v1/alerts" | python3 -c '
import json,sys
a=[x["state"] for x in json.load(sys.stdin)["data"]["alerts"] if x["labels"].get("alertname")=="NodeExporterDown" and x["labels"].get("instance")=="may2-mgmt"]
print(a[0] if a else "inactive")'
}
# Alertmanager đã nhận và đang giữ cảnh báo (tức là đã/đang gửi Telegram)
am_co() { curl -s "$AM/api/v2/alerts?filter=alertname%3D%22NodeExporterDown%22" | grep -q may2-mgmt; }

node() {
  echo "[$(gio)] docker stop $CONTAINER (máy 2 ngừng xuất số liệu CPU, RAM, ổ đĩa)"
  docker stop "$CONTAINER" >/dev/null
  # Luôn bật lại node-exporter, kể cả khi bấm Ctrl+C giữa chừng
  trap 'docker start "$CONTAINER" >/dev/null; echo "[$(gio)] đã bật lại $CONTAINER"' EXIT
  local t0 cu moi; t0=$(date +%s); cu=""
  while :; do
    moi=$(trang_thai)
    [ "$moi" != "$cu" ] && echo "[$(gio)] +$(( $(date +%s) - t0 ))s  Prometheus: NodeExporterDown $moi" && cu=$moi
    am_co && { echo "[$(gio)] +$(( $(date +%s) - t0 ))s  Alertmanager đã nhận, tin đang gửi tới Telegram"; break; }
    [ $(( $(date +%s) - t0 )) -gt 280 ] && { echo "Quá 280 giây chưa bắn, dừng thử để không kéo theo luật khác"; exit 1; }
    sleep 5
  done
  docker start "$CONTAINER" >/dev/null; trap - EXIT
  echo "[$(gio)] docker start $CONTAINER, chờ cảnh báo tự hết"
  t0=$(date +%s)
  while am_co && [ $(( $(date +%s) - t0 )) -lt 300 ]; do sleep 5; done
  echo "[$(gio)] +$(( $(date +%s) - t0 ))s  Cảnh báo đã hết; Telegram nhận tin \"đã khôi phục\" (có thể trễ thêm vài chục giây)"
}

gia() {
  local het; het=$(date -u -d '+5 minutes' +%Y-%m-%dT%H:%M:%SZ)
  curl -s -XPOST "$AM/api/v2/alerts" -H 'Content-Type: application/json' -d "[{
    \"labels\": {\"alertname\": \"DemoCanhBao\", \"severity\": \"warning\", \"instance\": \"demo\"},
    \"annotations\": {\"summary\": \"Cảnh báo thử khi demo\", \"description\": \"Gửi thẳng vào Alertmanager lúc $(gio), không phải sự cố.\"},
    \"endsAt\": \"$het\"}]" && echo "[$(gio)] Đã gửi DemoCanhBao vào Alertmanager, Telegram nhận trong vài giây; tự hết sau 5 phút"
}

case "${1:-}" in
  node) node ;;
  gia) gia ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
