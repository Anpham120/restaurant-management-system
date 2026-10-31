#!/usr/bin/env bash
# Tự merge commit đang build vào nhánh đích, không cần duyệt tay.
#   feature/* → develop : --no-ff (giữ rõ từng tính năng trong lịch sử)
#   develop   → main    : --ff-only (main chỉ nhận đúng bản develop đã qua CI)
# Dùng: GH_TOKEN=... scripts/auto-merge.sh <develop|main>
set -euo pipefail

TARGET=${1:?Dùng: auto-merge.sh <develop|main>}
REMOTE="https://x-access-token:${GH_TOKEN:?Thiếu GH_TOKEN}@github.com/Anpham120/restaurant-management-system.git"
SOURCE_SHA=$(git rev-parse HEAD)
SOURCE_NAME=${BRANCH_NAME:-$SOURCE_SHA}

export GIT_AUTHOR_NAME=rms-ci GIT_AUTHOR_EMAIL=rms-ci@localhost
export GIT_COMMITTER_NAME=rms-ci GIT_COMMITTER_EMAIL=rms-ci@localhost

git fetch --quiet "$REMOTE" "$TARGET"
git checkout --quiet -B "$TARGET" FETCH_HEAD

if [ "$TARGET" = develop ]; then
  git merge --no-ff --quiet -m "Merge $SOURCE_NAME vào develop [auto-merge]" "$SOURCE_SHA"
else
  git merge --ff-only --quiet "$SOURCE_SHA"
fi

git push --quiet "$REMOTE" "$TARGET"
echo "[auto-merge] $SOURCE_NAME → $TARGET xong"

# Nhánh feature đã vào develop thì xóa trên GitHub. --force-with-lease: chỉ xóa nếu nhánh vẫn đúng commit vừa merge,
# có ai push thêm trong lúc build thì giữ lại để lần build sau merge tiếp. Xóa lỗi không làm hỏng build.
if [ "$TARGET" = develop ] && [[ "$SOURCE_NAME" == feature/* ]]; then
  if git push --quiet --force-with-lease="refs/heads/$SOURCE_NAME:$SOURCE_SHA" "$REMOTE" ":refs/heads/$SOURCE_NAME"; then
    echo "[auto-merge] đã xóa nhánh $SOURCE_NAME"
  else
    echo "[auto-merge] giữ nhánh $SOURCE_NAME (có commit mới hoặc đã bị xóa)"
  fi
fi
