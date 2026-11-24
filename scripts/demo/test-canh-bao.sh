#!/usr/bin/env bash
# Kiểm tra cả 23 luật cảnh báo bằng promtool, không đụng hệ thống thật: đưa số liệu giả vào từng luật
# (deploy/monitoring/prometheus/tests/alerts.test.yml), kiểm luật BẬT đúng lúc, đúng nhãn, đúng nội dung tin,
# và KHÔNG bật ở trạng thái bình thường. Chạy vài giây, cần Docker (dùng đúng image Prometheus đang chạy ở máy 2).
#
# Dùng (trong thư mục repo, máy nào có Docker cũng được, ví dụ máy 2):
#   bash scripts/demo/test-canh-bao.sh
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/../../deploy/monitoring/prometheus"   # chạy từ thư mục nào cũng được
IMAGE=prom/prometheus:v3.5.0   # cùng bản với deploy/monitoring/compose.yaml

echo "== Cú pháp luật"
docker run --rm -v "$PWD:/p:ro" -w /p --entrypoint promtool "$IMAGE" check rules alerts.yml
echo
echo "== Danh sách ca test"
grep -E "^- name: " tests/alerts.test.yml | sed 's/^- name: /   /'
echo
echo "== Chạy test"
docker run --rm -v "$PWD:/p:ro" -w /p/tests --entrypoint promtool "$IMAGE" test rules alerts.test.yml
