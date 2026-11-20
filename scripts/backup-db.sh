#!/usr/bin/env bash
# Sao lưu database production bằng pg_dump (định dạng custom, đã nén). Chỉ đọc, không sửa dữ liệu.
#   scripts/backup-db.sh daily       : cron mỗi ngày
#   scripts/backup-db.sh pre-deploy  : deploy.sh gọi trước khi build bản mới (chỉ còn dùng khi chạy lại production Docker)
# Nguồn (DB_SOURCE): k8s (mặc định, từ 20/11/2026) = pod primary của CloudNativePG trong namespace rms-prod (máy 1),
#                    docker = container restaurant-management-system-db-1 (production cũ, giữ để quay lại).
# COPY_TO (tùy chọn, chỉ bản daily): chép bản mới nhất sang máy khác, ví dụ coder@10.10.0.2:/home/coder/rms-backups
#   (máy 2: staging tạm thời của pipeline nạp bản này). Bên nhận chỉ giữ 1 bản. Lỗi chép không làm hỏng bản sao lưu.
#   Bản chép được chmod 640: thư mục nhận có ACL cho user ci (Docker rootless của Jenkins) đọc, mask 640 mới có hiệu lực.
# Giữ KEEP bản gần nhất cho mỗi loại. Khôi phục: xem docs/devops/PIPELINE.md mục "Sao lưu database".
set -euo pipefail

KIND=${1:?Dùng: backup-db.sh <daily|pre-deploy>}
DB_SOURCE=${DB_SOURCE:-k8s}
DB_CONTAINER=restaurant-management-system-db-1
# cron không đọc .bashrc: chỉ rõ kubeconfig của user chạy cron
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
BACKUP_DIR=${BACKUP_DIR:-/home/coder/rms-backups}
KEEP=${KEEP:-7}

umask 077
mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/$KIND-$(date -u +%Y%m%d-%H%M%S).dump"

dump() {
  if [ "$DB_SOURCE" = docker ]; then
    # Dùng user và tên DB có sẵn trong container db, không cần đọc .env
    docker exec "$DB_CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc'
  else
    # Pod primary hiện tại (sau failover tên pod có thể đổi): nhãn cnpg.io/instanceRole=primary do CloudNativePG gắn.
    # pg_dump chạy trong container postgres của pod, kết nối socket nội bộ bằng user postgres; không cần mật khẩu.
    local pod
    pod=$(kubectl -n rms-prod get pod -l cnpg.io/cluster=rms-db,cnpg.io/instanceRole=primary -o jsonpath='{.items[0].metadata.name}')
    [ -n "$pod" ] || { echo "[backup] không tìm thấy pod primary" >&2; return 1; }
    kubectl -n rms-prod exec "$pod" -c postgres -- pg_dump -U postgres -d restaurant_core -Fc
  fi
}
if ! dump > "$FILE.tmp" || [ ! -s "$FILE.tmp" ]; then
  rm -f "$FILE.tmp"
  echo "[backup] LỖI: pg_dump thất bại"
  exit 1
fi
mv "$FILE.tmp" "$FILE"
echo "[backup] $(basename "$FILE") $(du -h "$FILE" | cut -f1)"

# Xóa bản cũ, chỉ giữ KEEP bản mới nhất của loại này
ls -1t "$BACKUP_DIR/$KIND-"*.dump | tail -n +$((KEEP + 1)) | xargs -r rm -f

# Chép bản daily mới nhất sang máy 2 cho staging (qua WireGuard, khóa SSH của coder), bên nhận chỉ giữ bản mới nhất
if [ "$KIND" = daily ] && [ -n "${COPY_TO:-}" ]; then
  if scp -q -p "$FILE" "$COPY_TO/" \
     && ssh "${COPY_TO%%:*}" "cd '${COPY_TO#*:}' && chmod 640 '$(basename "$FILE")' && ls -1t daily-*.dump | tail -n +2 | xargs -r rm -f"; then
    echo "[backup] đã chép sang ${COPY_TO%%:*}"
  else
    echo "[backup] không chép được sang ${COPY_TO%%:*}"
  fi
fi
# Báo Prometheus (luật BackupStale). Lỗi ghi số liệu không làm hỏng bản sao lưu vừa tạo.
BACKUP_DIR="$BACKUP_DIR" "$(dirname "$0")/backup-metric.sh" "$KIND" || echo "[backup] không ghi được số liệu cho Prometheus"
