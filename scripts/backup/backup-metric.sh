#!/usr/bin/env bash
# Ghi thời điểm sao lưu thành công cho Prometheus (textfile collector của node-exporter, deploy/monitoring/compose.yaml).
# Luật BackupStale / OffsiteBackupStale (deploy/monitoring/prometheus/alerts.yml) báo khi quá 26 giờ không có bản mới.
#   scripts/backup/backup-metric.sh <daily|pre-deploy|offsite>
# Ghi file tạm rồi đổi tên: node-exporter không bao giờ đọc phải file ghi dở.
set -euo pipefail

KIND=${1:?Dùng: backup-metric.sh <daily|pre-deploy|offsite|sealed-key>}
DIR=${METRICS_DIR:-${BACKUP_DIR:-/home/coder/rms-backups}/metrics}
FILE="$DIR/rms_backup_$KIND.prom"

mkdir -p "$DIR"
chmod 755 "$DIR"   # node-exporter chạy bằng nobody
cat > "$FILE.tmp" <<METRIC
# HELP rms_backup_last_success_timestamp_seconds Thời điểm (Unix) sao lưu thành công gần nhất theo loại.
# TYPE rms_backup_last_success_timestamp_seconds gauge
rms_backup_last_success_timestamp_seconds{kind="$KIND"} $(date +%s)
METRIC
chmod 644 "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
