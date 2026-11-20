#!/usr/bin/env bash
# GitOps: ghi tag image vừa qua kiểm tra vào 2 overlay Kubernetes rồi đẩy lên develop. Argo CD thấy Git đổi thì tự triển khai:
# rms-staging theo develop ngay, rms-prod khi commit này được promote lên main.
#   GH_TOKEN=... scripts/bump-image.sh <commit sha đầy đủ của image>
# Commit có tiêu đề "deploy: ..." của rms-ci và chỉ sửa 2 file kustomization: Jenkinsfile nhận ra để không test lại (tránh vòng lặp).
# develop đã có commit mới hơn thì bỏ qua: bản mới hơn sẽ tự ghi tag của nó.
set -euo pipefail
SHA=${1:?Dùng: bump-image.sh <commit sha>}
REMOTE="https://x-access-token:${GH_TOKEN:?Thiếu GH_TOKEN}@github.com/Anpham120/restaurant-management-system.git"
export GIT_AUTHOR_NAME=rms-ci GIT_AUTHOR_EMAIL=rms-ci@localhost
export GIT_COMMITTER_NAME=rms-ci GIT_COMMITTER_EMAIL=rms-ci@localhost
FILES="deploy/k8s/overlays/staging/kustomization.yaml deploy/k8s/overlays/prod/kustomization.yaml"

git fetch --quiet "$REMOTE" develop
if [ "$(git rev-parse FETCH_HEAD)" != "$SHA" ]; then
  echo "[bump] develop đã sang $(git rev-parse --short FETCH_HEAD), bỏ qua: bản mới hơn sẽ tự ghi tag"
  exit 0
fi
git checkout --quiet -B develop FETCH_HEAD
for f in $FILES; do
  # chỉ đổi dòng newTag của 2 image rms-app, rms-web (40 ký tự hex = sha đầy đủ)
  sed -i -E "s/^( *newTag: )[0-9a-f]{40}$/\1$SHA/" "$f"
done
if git diff --quiet -- $FILES; then
  echo "[bump] tag đã là ${SHA:0:7}, không cần commit"
  exit 0
fi
git commit --quiet -m "deploy: image ${SHA:0:7}" -- $FILES
git push --quiet "$REMOTE" HEAD:develop
echo "[bump] develop $(git rev-parse --short HEAD): staging và prod dùng image ${SHA:0:7}"
