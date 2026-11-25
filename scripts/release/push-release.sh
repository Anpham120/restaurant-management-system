#!/usr/bin/env bash
# Đẩy image phát hành lên GitHub Container Registry: bản lưu ngoài máy chủ, máy khác (hoặc máy chủ mới) kéo về được.
#   scripts/release/push-release.sh <commit sha đầy đủ>   → ghcr.io/anpham120/rms-{app,web}:<commit>
# Cần GHCR_TOKEN (token GitHub classic, scope write:packages; credential Jenkins "ghcr-token"). Không có thì bỏ qua:
# Kubernetes kéo image từ GHCR nên Jenkinsfile coi bước này là bắt buộc.
set -euo pipefail

SHA=${1:?Dùng: push-release.sh <commit sha>}
REG=ghcr.io/anpham120
[ -n "${GHCR_TOKEN:-}" ] || { echo "[ghcr] chưa có GHCR_TOKEN: bỏ qua"; exit 0; }

# Mạng từ daemon Docker rootless ra GHCR thỉnh thoảng chập chờn ("TLS handshake timeout"): thử lại tối đa 4 lần,
# chờ 15, 30, 45 giây giữa các lần, thay vì để một lần timeout làm hỏng cả bản phát hành.
thu_lai() {
  local lan=1
  until "$@"; do
    [ "$lan" -ge 4 ] && { echo "[ghcr] thất bại sau $lan lần: $1 $2"; return 1; }
    echo "[ghcr] lần $lan lỗi, thử lại sau $(( lan * 15 ))s"
    sleep $(( lan * 15 ))
    lan=$(( lan + 1 ))
  done
}
dang_nhap() { printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u anpham120 --password-stdin >/dev/null; }

thu_lai dang_nhap
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
for svc in app web; do
  docker tag "rms-release-$svc:$SHA" "$REG/rms-$svc:$SHA"
  thu_lai docker push -q "$REG/rms-$svc:$SHA"
  docker rmi "$REG/rms-$svc:$SHA" >/dev/null   # chỉ bỏ tag trên máy, image vẫn còn tag rms-release-*
  echo "[ghcr] $REG/rms-$svc:${SHA:0:7}"
done
