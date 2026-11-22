#!/usr/bin/env bash
# Máy 2: đếm webhook GitHub mà Argo CD từ chối (sai chữ ký HMAC) trong 10 phút gần nhất, ghi thành số liệu cho
# node-exporter (textfile collector). Luật ArgoWebhookRejected báo khi số này > 0 hoặc số liệu cũ quá 30 phút.
# Webhook hỏng thì Argo CD vẫn chạy nhưng chỉ phát hiện commit mới khi tự quét Git (chậm tới 2 phút mỗi lần
# triển khai), không gì khác báo lỗi. Đã xảy ra 20/11/2026: secret bị tạo lại nhưng GitHub giữ secret cũ.
# Chạy bằng cron của user coder mỗi 5 phút (docs/devops/PIPELINE.md). Không ghi nội dung log, chỉ ghi số đếm.
set -euo pipefail
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
DIR=${METRICS_DIR:-/home/coder/rms-backups/metrics}
logs=$(kubectl -n argocd logs deploy/argocd-server --since=10m 2>/dev/null)
rejected=$(printf '%s\n' "$logs" | grep -c 'HMAC verification failed' || true)
handled=$(printf '%s\n' "$logs" | grep -c 'Webhook handler completed' || true)
mkdir -p "$DIR"; chmod 755 "$DIR"
cat > "$DIR/rms_argocd_webhook.prom.tmp" <<METRIC
# HELP rms_argocd_webhook_rejected_10m Số webhook GitHub Argo CD từ chối (sai HMAC) trong 10 phút gần nhất.
# TYPE rms_argocd_webhook_rejected_10m gauge
rms_argocd_webhook_rejected_10m $rejected
# HELP rms_argocd_webhook_handled_10m Số webhook Argo CD xử lý xong trong 10 phút gần nhất.
# TYPE rms_argocd_webhook_handled_10m gauge
rms_argocd_webhook_handled_10m $handled
# HELP rms_argocd_webhook_check_timestamp_seconds Thời điểm chạy kiểm tra gần nhất.
# TYPE rms_argocd_webhook_check_timestamp_seconds gauge
rms_argocd_webhook_check_timestamp_seconds $(date +%s)
METRIC
chmod 644 "$DIR/rms_argocd_webhook.prom.tmp"
mv "$DIR/rms_argocd_webhook.prom.tmp" "$DIR/rms_argocd_webhook.prom"
