#!/usr/bin/env bash
# Sao lưu database production bằng pg_dump (định dạng custom, đã nén). Chỉ đọc, không sửa dữ liệu.
#   scripts/backup-db.sh daily       : cron mỗi ngày
#   scripts/backup-db.sh pre-deploy  : deploy.sh gọi trước khi build bản mới
# Giữ KEEP bản gần nhất cho mỗi loại. Khôi phục: xem docs/devops/PIPELINE.md mục "Sao lưu database".
set -euo pipefail

KIND=${1:?Dùng: backup-db.sh <daily|pre-deploy>}
DB_CONTAINER=restaurant-management-system-db-1
BACKUP_DIR=${BACKUP_DIR:-/home/coder/rms-backups}
KEEP=${KEEP:-7}

umask 077
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/$KIND-$(date -u +%Y%m%d-%H%M%S).dump"

# Dùng user và tên DB có sẵn trong container db, không cần đọc .env
if ! docker exec "$DB_CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$FILE.tmp"; then
  rm -f "$FILE.tmp"
  echo "[backup] LỖI: pg_dump thất bại"
  exit 1
fi
mv "$FILE.tmp" "$FILE"
echo "[backup] $(basename "$FILE") $(du -h "$FILE" | cut -f1)"

# Xóa bản cũ, chỉ giữ KEEP bản mới nhất của loại này
ls -1t "$BACKUP_DIR/$KIND-"*.dump | tail -n +$((KEEP + 1)) | xargs -r rm -f
