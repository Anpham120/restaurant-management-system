#!/usr/bin/env bash
# Demo pipeline chặn lỗi: giả lập lập trình viên sửa nhầm code để lỗi "không đủ quyền" (mã RC403 từ database)
# trả HTTP 200 thay vì 403. Test PostgresErrorTranslatorTest bắt được, Jenkins đỏ ở stage "Test backend",
# Telegram báo lỗi kèm link log; nhánh không được merge nên develop, staging, production không đổi.
#
# Dùng (trong thư mục repo, cây làm việc sạch):
#   bash scripts/demo/gay-loi.sh               # tạo commit lỗi và push nhánh feature/demo-loi-HHMM
#   bash scripts/demo/gay-loi.sh --khong-push  # chỉ tạo commit ở máy để xem trước
# Xong demo: bash scripts/demo/don-dep.sh (xóa các nhánh demo-loi trên GitHub và ở máy).
set -euo pipefail

FILE=src/main/java/com/restaurant/core/common/exception/PostgresErrorTranslator.java
PUSH=1
for a in "$@"; do
  case "$a" in
    --khong-push) PUSH=0 ;;
    *) echo "Tham số không hợp lệ: $a" >&2; exit 2 ;;
  esac
done

cd "$(git rev-parse --show-toplevel)"
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Cây làm việc còn thay đổi chưa commit. Commit hoặc stash trước khi demo." >&2; exit 1
fi

NHANH="feature/demo-loi-$(TZ=Asia/Ho_Chi_Minh date +%H%M)"
git fetch -q origin
git switch -q -c "$NHANH" origin/main
sed -i 's/case "RC403" -> new Translated(HttpStatus.FORBIDDEN,/case "RC403" -> new Translated(HttpStatus.OK,/' "$FILE"
if git diff --quiet; then echo "Không tìm thấy dòng RC403 để sửa; kiểm tra lại $FILE." >&2; git switch -q -; git branch -q -D "$NHANH"; exit 1; fi
git diff
git commit -q -am "fix(common): doi ma loi RC403 (demo: commit sai co chu dich)"
echo "Commit: $(git log -1 --format='%h %s')"

if [ "$PUSH" = 1 ]; then
  git push -q -u origin "$NHANH"
  echo "Đã push $NHANH lúc $(TZ=Asia/Ho_Chi_Minh date +%H:%M:%S). Khoảng 3 phút sau Jenkins đỏ ở stage Test backend:"
  echo "  PostgresErrorTranslatorTest.businessCodesKeepDatabaseMessage: expected 403 FORBIDDEN but was 200 OK"
  echo "Xong demo chạy: bash scripts/demo/don-dep.sh"
else
  echo "Chưa push (--khong-push). Nhánh $NHANH chỉ có ở máy này."
fi
git switch -q main 2>/dev/null || git switch -q -c main origin/main
