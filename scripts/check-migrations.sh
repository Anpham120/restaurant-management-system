#!/usr/bin/env bash
# Luật migration tương thích ngược (expand/contract). Deploy lỗi chỉ quay lui image, không quay lui database, nên bản
# app ĐANG CHẠY phải còn chạy được trên schema sau migration. Script chặn các lệnh làm bản cũ hỏng:
#   DROP TABLE, DROP COLUMN, RENAME (bảng/cột), ALTER COLUMN ... TYPE, SET NOT NULL, ADD COLUMN ... NOT NULL không có DEFAULT
# Postgres cho bỏ chữ COLUMN (ALTER TABLE t DROP c, ALTER c TYPE ...) và lệnh có thể viết trên nhiều dòng, nên script dò
# theo từng mệnh đề SQL chứ không theo từng dòng.
# Cách làm đúng: tách 2 lần phát hành.
#   expand  : thêm cột/bảng mới (cho phép NULL hoặc có DEFAULT), code mới ghi cả hai chỗ
#   contract: ở lần phát hành SAU, khi không còn code nào dùng phần cũ, mới xóa/đổi. File contract ghi dòng
#             "-- contract: <lý do, phần cũ ngừng dùng từ commit nào>" để script cho qua.
# Dùng: scripts/check-migrations.sh [thư mục]   (mặc định src/main/resources/db/migration)
set -euo pipefail

DIR=${1:-src/main/resources/db/migration}
# Từ đứng sau DROP mà không xóa bảng/cột: bỏ ràng buộc, giá trị mặc định, NOT NULL, hoặc xóa đối tượng không phải bảng
SAFE_DROP='CONSTRAINT|DEFAULT|NOT|IDENTITY|EXPRESSION|INDEX|VIEW|FUNCTION|PROCEDURE|TRIGGER|SEQUENCE|TYPE|POLICY|RULE'
# Từ đứng sau ADD mà không phải cột mới
NOT_COLUMN='CONSTRAINT|CHECK|PRIMARY|FOREIGN|UNIQUE|EXCLUDE'
fail=0

# Mỗi mệnh đề SQL một dòng: bỏ chú thích (để câu giải thích không bị bắt nhầm), gộp dòng, tách theo ";",
# rồi tách ALTER TABLE nhiều mệnh đề theo ", ADD|DROP|ALTER|RENAME"
clauses() {
  sed -E 's/--.*$//' "$1" | tr '\n\t\r' '   ' \
    | sed -E 's#/\*([^*]|\*+[^*/])*\*+/# #g; s/;/\n/g' \
    | sed -E 's/[[:space:]]+/ /g; s/^ //; s/ $//' \
    | sed -E 's/ ?, ?(ADD|DROP|ALTER|RENAME) /\n\1 /Ig' \
    | sed '/^$/d'
}

# In lý do nếu mệnh đề $1 làm bản app đang chạy hỏng
breaking() {
  local c=$1 w
  for w in $(grep -oiE '\bDROP( COLUMN)?( IF EXISTS)? [a-z_"]+' <<<"$c" | awk '{print toupper($NF)}' || true); do
    [[ $w =~ ^($SAFE_DROP)$ ]] || echo "xóa bảng/cột"
  done
  for w in $(grep -oiE '\bRENAME [a-z_"]+' <<<"$c" | awk '{print toupper($NF)}' || true); do
    [ "$w" = CONSTRAINT ] || echo "đổi tên bảng/cột"
  done
  if grep -qiE '\bALTER( COLUMN)? [a-z_"][a-z0-9_"]* (SET DATA )?TYPE ' <<<"$c"; then echo "đổi kiểu cột"; fi
  if grep -qiE '\bSET NOT NULL\b' <<<"$c"; then echo "thêm NOT NULL cho cột cũ"; fi
  w=$(grep -oiE '\bADD( COLUMN)?( IF NOT EXISTS)? [a-z_"]+' <<<"$c" | awk '{print toupper($NF)}' | head -1 || true)
  if [ -n "$w" ] && ! [[ $w =~ ^($NOT_COLUMN)$ ]] && grep -qiE '\bNOT NULL\b' <<<"$c" && ! grep -qiE '\bDEFAULT\b' <<<"$c"; then
    echo "cột mới NOT NULL không có DEFAULT"
  fi
}

for f in "$DIR"/V*__*.sql; do
  [ -e "$f" ] || continue
  if grep -qiE '^[[:space:]]*--[[:space:]]*contract:' "$f"; then
    echo "[migration] contract (đã ghi lý do): $(basename "$f")"
    continue
  fi
  hits=""
  while IFS= read -r c; do
    for r in $(breaking "$c" | sort -u | tr ' ' '_'); do
      hits+="    ${r//_/ }: ${c:0:150}"$'\n'
    done
  done < <(clauses "$f")
  if [ -n "$hits" ]; then
    echo "[migration] CHẶN $(basename "$f"): lệnh làm bản app đang chạy hỏng khi quay lui"
    printf '%s' "$hits"
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo "[migration] Tách thành expand rồi contract (xem đầu script hoặc docs/backend/DEVELOPMENT_GUIDE.md, mục Migration)."
  exit 1
fi
echo "[migration] đạt: mọi migration tương thích ngược"
