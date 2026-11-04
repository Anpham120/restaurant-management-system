#!/usr/bin/env bash
# Khôi phục database production từ một bản sao lưu của scripts/backup-db.sh. XÓA TOÀN BỘ dữ liệu hiện tại.
# Dùng khi bản mới làm hỏng dữ liệu hoặc migration Flyway cần quay lại: khôi phục bản pre-deploy-* ngay trước lần
# deploy đó, rồi deploy lại image cũ (quay lui image không hoàn tác được schema).
#   scripts/restore-db.sh /home/coder/rms-backups/pre-deploy-YYYYMMDD-HHMMSS.dump
# Trình tự: sao lưu dữ liệu hiện tại (pre-restore-*) → dừng slot app đang chạy (A và/hoặc B) → xóa schema public và nạp bản sao lưu trong MỘT
# transaction → chạy lại đúng các slot vừa dừng. Lỗi ở bất kỳ câu nào thì transaction hủy, database giữ nguyên như trước.
# staging-test.sh nạp bản sao lưu theo đúng cách này ở mỗi lần build develop, nên cách khôi phục luôn được chạy thử.
set -euo pipefail

FILE=${1:?Dùng: restore-db.sh <file .dump>}
[ -f "$FILE" ] || { echo "Không thấy $FILE"; exit 1; }
DB_CONTAINER=restaurant-management-system-db-1
# Blue-green: app có thể đang chạy ở slot A (app) hoặc slot B (app-b). Dừng mọi slot đang chạy, sau đó chỉ bật lại
# đúng các slot đó (không bật slot đang nghỉ: nó chạy image cũ hơn).
APP_CONTAINERS=(restaurant-management-system-app-1 restaurant-management-system-app-b-1)
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

echo "Sẽ thay TOÀN BỘ dữ liệu production bằng $(basename "$FILE") ($(du -h "$FILE" | cut -f1))."
read -r -p "Gõ KHOI PHUC để tiếp tục: " answer
[ "$answer" = "KHOI PHUC" ] || { echo "Đã hủy, không thay đổi gì."; exit 1; }

"$SCRIPT_DIR/backup-db.sh" pre-restore
RUNNING=()
for c in "${APP_CONTAINERS[@]}"; do
  [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" = true ] && RUNNING+=("$c")
done
if [ ${#RUNNING[@]} -gt 0 ]; then
  docker stop "${RUNNING[@]}" >/dev/null
  echo "[restore] đã dừng: ${RUNNING[*]}"
  trap 'docker start "${RUNNING[@]}" >/dev/null && echo "[restore] đã chạy lại: ${RUNNING[*]}"' EXIT
fi

# Xóa hẳn schema (kể cả bảng do migration mới tạo, thứ --clean bỏ sót) rồi nạp lại, tất cả trong một transaction
{ echo 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'; docker exec -i "$DB_CONTAINER" pg_restore -f - < "$FILE"; } \
  | docker exec -i "$DB_CONTAINER" sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --single-transaction' >/dev/null
echo "[restore] xong: $(basename "$FILE"). Dữ liệu trước khi khôi phục nằm ở bản pre-restore-* mới nhất."
