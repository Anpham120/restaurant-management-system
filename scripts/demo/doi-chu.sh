#!/usr/bin/env bash
# Demo quy trình: một thay đổi nhìn thấy được đi từ commit tới production (~8 phút).
# Đổi tiêu đề trang đăng nhập thành "Đăng nhập · demo HH:MM", tạo nhánh feature/demo-doi-chu-HHMM từ origin/main,
# commit và push. Pipeline tự chạy: feature (test, Trivy) → develop (staging, E2E, GHCR) → main (blue-green).
#
# Dùng (trong thư mục repo, cây làm việc sạch):
#   bash scripts/demo/doi-chu.sh               # tạo thay đổi và push
#   bash scripts/demo/doi-chu.sh --khong-push  # chỉ tạo commit ở máy, để xem trước (xóa bằng: git switch main && git branch -D <nhánh>)
#   bash scripts/demo/doi-chu.sh --tra-lai     # sau buổi demo: đưa tiêu đề về "Đăng nhập" (cũng đi qua pipeline)
set -euo pipefail

FILE=frontend/src/pages/auth/LoginPage.tsx
PUSH=1; TRA_LAI=0
for a in "$@"; do
  case "$a" in
    --khong-push) PUSH=0 ;;
    --tra-lai) TRA_LAI=1 ;;
    *) echo "Tham số không hợp lệ: $a" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$(readlink -f "$0")")/../.."   # gốc repo, chạy từ thư mục nào cũng được
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Cây làm việc còn thay đổi chưa commit. Commit hoặc stash trước khi demo." >&2; exit 1
fi

GIO=$(TZ=Asia/Ho_Chi_Minh date +%H:%M)
if [ "$TRA_LAI" = 1 ]; then
  NHANH="feature/demo-tra-lai-$(TZ=Asia/Ho_Chi_Minh date +%H%M)"; TIEU_DE="Đăng nhập"
  MSG="feat(web): dua tieu de trang dang nhap ve mac dinh sau buoi demo"
else
  NHANH="feature/demo-doi-chu-$(TZ=Asia/Ho_Chi_Minh date +%H%M)"; TIEU_DE="Đăng nhập · demo $GIO"
  MSG="feat(web): tieu de trang dang nhap hien gio demo $GIO"
fi

git fetch -q origin
git switch -q -c "$NHANH" origin/main
# Thay đúng thuộc tính title của AuthLayout (chấp nhận cả tiêu đề đã đổi ở lần demo trước)
sed -i -E "s/<AuthLayout title=\"Đăng nhập[^\"]*\"/<AuthLayout title=\"$TIEU_DE\"/" "$FILE"
if git diff --quiet; then echo "Không có gì thay đổi (tiêu đề đã là \"$TIEU_DE\")." >&2; git switch -q -; git branch -q -D "$NHANH"; exit 1; fi
git diff --stat
git commit -q -am "$MSG"
echo "Commit: $(git log -1 --format='%h %s')"

if [ "$PUSH" = 1 ]; then
  git push -q -u origin "$NHANH"
  echo "Đã push $NHANH lúc $(TZ=Asia/Ho_Chi_Minh date +%H:%M:%S). Theo dõi: Jenkins → nhánh $NHANH; xong khoảng 8 phút."
  echo "Kết quả: tải lại https://rms.atlasplatform12.tech, tiêu đề là \"$TIEU_DE\"."
else
  echo "Chưa push (--khong-push). Nhánh $NHANH chỉ có ở máy này."
fi
