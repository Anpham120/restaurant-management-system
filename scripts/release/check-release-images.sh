#!/usr/bin/env bash
# Kiểm image phát hành mà 2 overlay Kubernetes đang trỏ tới (newTag) còn trên GHCR. Dùng cho commit chỉ đổi tag (quay lui
# bằng git revert): tag sai hoặc image đã bị xóa thì dừng ngay, không để Argo CD kéo image thất bại ở staging.
#   GHCR_TOKEN=… scripts/release/check-release-images.sh
# Cần GHCR_TOKEN (credential Jenkins "ghcr-token", scope đọc package). Đăng nhập vào thư mục cấu hình Docker tạm, xóa khi
# xong, để không ghi đè đăng nhập của Jenkins.
set -euo pipefail

REG=ghcr.io/anpham120
: "${GHCR_TOKEN:?Thiếu GHCR_TOKEN}"

DOCKER_CONFIG=$(mktemp -d)
export DOCKER_CONFIG
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u anpham120 --password-stdin >/dev/null

tags=$(sed -nE 's/^ *newTag: ([0-9a-f]{7,40}).*/\1/p' deploy/k8s/overlays/staging/kustomization.yaml \
         deploy/k8s/overlays/prod/kustomization.yaml | sort -u)
[ -n "$tags" ] || { echo "[ghcr] không đọc được newTag trong overlay"; exit 1; }

loi=0
for tag in $tags; do
  for svc in app web; do
    if docker manifest inspect "$REG/rms-$svc:$tag" >/dev/null 2>&1; then
      echo "[ghcr] có $REG/rms-$svc:${tag:0:7}"
    else
      echo "[ghcr] KHÔNG có $REG/rms-$svc:$tag"; loi=1
    fi
  done
done
exit "$loi"
