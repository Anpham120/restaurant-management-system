#!/usr/bin/env bash
# "Build một lần, deploy nhiều nơi": đánh dấu image staging (đã qua smoke test và Trivy) là bản phát hành của commit
# đang build. Production (scripts/deploy.sh) chỉ chạy đúng image có tag này, không build lại.
#   scripts/tag-release.sh <commit sha đầy đủ>
# Dọn tag cũ theo lịch sử git (không theo ngày tạo image: image lấy từ cache mang ngày tạo cũ):
#   - chỉ bỏ tag của commit là tổ tiên của commit này VÀ nằm ngoài 5 commit develop gần nhất;
#   - giữ tag của commit mới hơn (develop chạy tiếp trong lúc main chưa kịp deploy bản trước).
# Bỏ tag không xóa image production đang chạy: image đó còn tag riêng của production.
# Chạy trong workspace Jenkins của develop (clone đầy đủ, không shallow).
set -euo pipefail

SHA=${1:?Dùng: tag-release.sh <commit sha>}
RECENT=$(git rev-list --first-parent --max-count=5 "$SHA")

for svc in app web; do
  docker image inspect "rms-staging-$svc:latest" >/dev/null
  docker tag "rms-staging-$svc:latest" "rms-release-$svc:$SHA"
  echo "[release] rms-release-$svc:${SHA:0:7}"
  docker images "rms-release-$svc" --format '{{.Tag}}' | while read -r tag; do
    if ! grep -qx "$tag" <<<"$RECENT" && git merge-base --is-ancestor "$tag" "$SHA" 2>/dev/null; then
      docker rmi "rms-release-$svc:$tag" >/dev/null || true
    fi
  done
done
