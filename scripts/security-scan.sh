#!/usr/bin/env bash
# Quét bảo mật bằng Trivy. Lỗi (dừng pipeline) khi có lỗ hổng HIGH/CRITICAL đã có bản vá, hoặc secret lộ trong mã.
#   scripts/security-scan.sh fs           : thư viện Maven, npm và secret trong workspace Jenkins
#   scripts/security-scan.sh image IMG... : hệ điều hành nền và thư viện trong image đã build
# Bỏ qua lỗ hổng chưa có bản vá (--ignore-unfixed): chưa sửa được thì chặn pipeline cũng không giải quyết gì.
set -euo pipefail

# Ghim theo digest, không tin tag: Trivy 0.69.4–0.69.6 từng bị cài mã độc (CVE-2026-33634), 0.69.3 là bản sạch
TRIVY=aquasec/trivy:0.69.3@sha256:bcc376de8d77cfe086a917230e818dc9f8528e3c852f7b1aff648949b6258d1c

# Volume rms-trivy-cache giữ CSDL lỗ hổng (~130 MB) giữa các lần chạy.
# Cache kết quả quét để trong RAM, tránh hai build song song tranh khóa file cache.
OPTS=(--skip-version-check --no-progress --cache-backend memory
      --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1)

case "${1:-}" in
  fs)
    # rms-m2-cache có sẵn thư viện Maven (stage Test backend), Trivy đọc để biết cả thư viện con
    docker run --rm -v rms-trivy-cache:/root/.cache/trivy -v rms-m2-cache:/root/.m2:ro \
      --volumes-from "$JENKINS_CONTAINER" -w "$WORKSPACE" \
      "$TRIVY" fs "${OPTS[@]}" --scanners vuln,secret \
      --skip-dirs target --skip-dirs frontend/node_modules --skip-dirs frontend/dist .
    ;;
  image)
    shift
    [ $# -gt 0 ] || { echo "thiếu tên image"; exit 2; }
    # Quét hết các image rồi mới báo lỗi, để một lần chạy thấy đủ mọi lỗ hổng
    rc=0
    for img in "$@"; do
      echo "[scan] $img"
      docker run --rm -v rms-trivy-cache:/root/.cache/trivy -v /var/run/docker.sock:/var/run/docker.sock \
        "$TRIVY" image "${OPTS[@]}" --scanners vuln "$img" || rc=1
    done
    exit $rc
    ;;
  *)
    echo "dùng: $0 fs | image IMG..."; exit 2
    ;;
esac
