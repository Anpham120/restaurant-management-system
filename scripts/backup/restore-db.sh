#!/usr/bin/env bash
# Khôi phục database production (CloudNativePG rms-db, namespace rms-prod, máy 1) từ một bản pg_dump của
# scripts/backup/backup-db.sh. XÓA TOÀN BỘ dữ liệu hiện tại và thay bằng bản sao lưu.
#   scripts/backup/restore-db.sh /home/coder/rms-backups/daily-YYYYMMDD-HHMMSS.dump
# Dùng khi dữ liệu hỏng do lỗi nghiệp vụ hoặc migration và có sẵn bản pg_dump phù hợp. Muốn về một THỜI ĐIỂM bất kỳ
# trong 14 ngày (không chỉ lúc 02:30 mỗi đêm) thì khôi phục từ WAL trên SeaweedFS (PITR, docs/devops/PIPELINE.md).
# Trình tự: sao lưu dữ liệu hiện tại (pre-restore-*) → xóa schema public và nạp bản sao lưu trong MỘT transaction trên
# pod primary (replica tự nhận qua WAL) → khởi động lại app (blue-green, không đổi image) để bỏ kết nối cũ.
# Lỗi ở bất kỳ câu nào thì transaction hủy, database giữ nguyên như trước. Trong lúc nạp (vài giây), request ghi phải chờ.
# staging-test.sh nạp bản sao lưu theo đúng cách này ở mỗi lần build develop, nên cách khôi phục luôn được chạy thử.
set -euo pipefail

FILE=${1:?Dùng: restore-db.sh <file .dump>}
[ -f "$FILE" ] || { echo "Không thấy $FILE"; exit 1; }
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
NAMESPACE=${NAMESPACE:-rms-prod}   # rms-staging (cụm máy 2) để chạy thử
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
export BACKUP_DIR=${BACKUP_DIR:-/home/coder/rms-backups}   # bản pre-restore-* ghi vào đây

POD=$(kubectl -n "$NAMESPACE" get pod -l cnpg.io/cluster=rms-db,cnpg.io/instanceRole=primary -o jsonpath='{.items[0].metadata.name}')
[ -n "$POD" ] || { echo "[restore] không tìm thấy pod primary"; exit 1; }

echo "Sẽ thay TOÀN BỘ dữ liệu namespace $NAMESPACE (pod $POD) bằng $(basename "$FILE") ($(du -h "$FILE" | cut -f1))."
read -r -p "Gõ KHOI PHUC để tiếp tục: " answer
[ "$answer" = "KHOI PHUC" ] || { echo "Đã hủy, không thay đổi gì."; exit 1; }

"$SCRIPT_DIR/backup-db.sh" pre-restore

# Xóa hẳn schema (kể cả bảng do migration mới tạo, thứ --clean bỏ sót) rồi nạp lại, tất cả trong một transaction.
# pg_restore chuyển file custom thành SQL ngay trong pod; psql chạy bằng user postgres qua socket nội bộ.
{ echo 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
  kubectl -n "$NAMESPACE" exec -i "$POD" -c postgres -- pg_restore -f - < "$FILE"; } \
  | kubectl -n "$NAMESPACE" exec -i "$POD" -c postgres -- \
      psql -q -U postgres -d restaurant_core -v ON_ERROR_STOP=1 --single-transaction >/dev/null
echo "[restore] đã nạp $(basename "$FILE")"

# Khởi động lại app (cùng image) để bỏ kết nối cũ: đặt spec.restartAt, Argo Rollouts thay lần lượt từng pod, luôn còn pod
# phục vụ. Đây là đúng việc "kubectl argo rollouts restart" làm, không cần cài plugin.
kubectl -n "$NAMESPACE" patch rollout rms-app --type merge -p "{\"spec\":{\"restartAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}}"
echo "[restore] xong. Dữ liệu trước khi khôi phục nằm ở bản pre-restore-* mới nhất trong $BACKUP_DIR."
