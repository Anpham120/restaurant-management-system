#!/usr/bin/env bash
# Đẩy image phát hành lên GitHub Container Registry: bản lưu ngoài máy chủ, máy khác (hoặc máy chủ mới) kéo về được.
#   scripts/push-release.sh <commit sha đầy đủ>   → ghcr.io/anpham120/rms-{app,web}:<commit>
# Cần GHCR_TOKEN (token GitHub classic, scope write:packages; credential Jenkins "ghcr-token"). Không có thì bỏ qua:
# deploy vẫn dùng image phát hành có sẵn trên máy (scripts/tag-release.sh).
set -euo pipefail

SHA=${1:?Dùng: push-release.sh <commit sha>}
REG=ghcr.io/anpham120
[ -n "${GHCR_TOKEN:-}" ] || { echo "[ghcr] chưa có GHCR_TOKEN: bỏ qua"; exit 0; }

printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u anpham120 --password-stdin >/dev/null
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
for svc in app web; do
  docker tag "rms-release-$svc:$SHA" "$REG/rms-$svc:$SHA"
  docker push -q "$REG/rms-$svc:$SHA"
  docker rmi "$REG/rms-$svc:$SHA" >/dev/null   # chỉ bỏ tag trên máy, image vẫn còn tag rms-release-*
  echo "[ghcr] $REG/rms-$svc:${SHA:0:7}"
done
