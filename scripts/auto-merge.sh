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

# Thử tối đa 3 lần: GitHub thỉnh thoảng trả lỗi tạm thời (develop #40: "remote rejected (Internal Server Error)").
# Mỗi lần lấy lại nhánh đích mới nhất rồi merge lại, nên lặp lại an toàn: nếu lần trước thực ra đã lên GitHub thì
# merge báo "Already up to date" và push không đổi gì. Merge lỗi (xung đột, không fast-forward được) vẫn dừng ngay
# (set -e): đó là lỗi thật, thử lại không có ích.
ok=0
for attempt in 1 2 3; do
  if git fetch --quiet "$REMOTE" "$TARGET"; then
    git checkout --quiet -B "$TARGET" FETCH_HEAD
    if [ "$TARGET" = develop ]; then
      git merge --no-ff --quiet -m "Merge $SOURCE_NAME vào develop [auto-merge]" "$SOURCE_SHA"
    else
      git merge --ff-only --quiet "$SOURCE_SHA"
    fi
    if git push --quiet "$REMOTE" "$TARGET"; then ok=1; break; fi
  fi
  [ "$attempt" -lt 3 ] && { echo "[auto-merge] GitHub lỗi (lần $attempt/3), thử lại sau $((attempt * 10)) giây"; sleep $((attempt * 10)); }
done
[ "$ok" = 1 ] || { echo "[auto-merge] LỖI: không push được $TARGET sau 3 lần"; exit 1; }
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
