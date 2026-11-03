#!/usr/bin/env bash
# Luật migration tương thích ngược (expand/contract). Deploy lỗi chỉ quay lui image, không quay lui database, nên bản
# app ĐANG CHẠY phải còn chạy được trên schema sau migration. Script chặn các lệnh làm bản cũ hỏng:
#   DROP TABLE, DROP COLUMN, RENAME (bảng/cột), ALTER COLUMN ... TYPE, SET NOT NULL, ADD COLUMN ... NOT NULL không có DEFAULT
# Cách làm đúng: tách 2 lần phát hành.
#   expand  : thêm cột/bảng mới (cho phép NULL hoặc có DEFAULT), code mới ghi cả hai chỗ
#   contract: ở lần phát hành SAU, khi không còn code nào dùng phần cũ, mới xóa/đổi. File contract ghi dòng
#             "-- contract: <lý do, phần cũ ngừng dùng từ commit nào>" để script cho qua.
# Dùng: scripts/check-migrations.sh [thư mục]   (mặc định src/main/resources/db/migration)
set -euo pipefail

DIR=${1:-src/main/resources/db/migration}
BREAKING='DROP[[:space:]]+TABLE|DROP[[:space:]]+COLUMN|RENAME[[:space:]]+(TO|COLUMN)|ALTER[[:space:]]+COLUMN[^;]*[[:space:]]TYPE[[:space:]]|SET[[:space:]]+NOT[[:space:]]+NULL'
fail=0

for f in "$DIR"/V*__*.sql; do
  [ -e "$f" ] || continue
  if grep -qiE '^[[:space:]]*--[[:space:]]*contract:' "$f"; then
    echo "[migration] contract (đã ghi lý do): $(basename "$f")"
    continue
  fi
  # Bỏ chú thích "--" trước khi dò để câu giải thích không bị bắt nhầm
  hits=$(sed -E 's/--.*$//' "$f" | grep -niE "$BREAKING" || true)
  addnn=$(sed -E 's/--.*$//' "$f" | grep -niE 'ADD[[:space:]]+(COLUMN[[:space:]]+)?[a-z_]+[^;]*NOT[[:space:]]+NULL' | grep -viE 'DEFAULT' || true)
  if [ -n "$hits$addnn" ]; then
    echo "[migration] CHẶN $(basename "$f"): lệnh làm bản app đang chạy hỏng khi quay lui"
    printf '%s\n' "$hits" "$addnn" | sed '/^$/d; s/^/    dòng /'
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo "[migration] Tách thành expand rồi contract (xem đầu script hoặc docs/backend/DEVELOPMENT_GUIDE.md, mục Migration)."
  exit 1
fi
echo "[migration] đạt: mọi migration tương thích ngược"
