#!/usr/bin/env bash
# Chờ Argo CD triển khai xong một commit: Application ở đúng revision, Synced và Healthy (Rollout blue-green đã qua
# AnalysisTemplate và chuyển traffic). Degraded thì dừng ngay. Đọc trạng thái bằng kubectl (ServiceAccount chỉ đọc).
#   KUBECONFIG=... scripts/argocd-wait.sh <rms-staging|rms-prod> <commit sha> [giây, mặc định 900]
set -euo pipefail
APP=${1:?Dùng: argocd-wait.sh <app> <sha> [timeout]}
SHA=${2:?Thiếu commit sha}
TIMEOUT=${3:-900}
end=$(( $(date +%s) + TIMEOUT ))
last=""
while :; do
  st=$(kubectl -n argocd get application "$APP" \
        -o jsonpath='{.status.sync.revision} {.status.sync.status} {.status.health.status} {.status.operationState.phase}' 2>/dev/null || true)
  read -r rev sync health phase <<<"$st"
  line="$APP: revision ${rev:0:7} · $sync · $health · ${phase:-}"
  [ "$line" != "$last" ] && echo "[argocd] $line" && last=$line
  if [ "$rev" = "$SHA" ] && [ "$sync" = Synced ] && [ "$health" = Healthy ]; then
    echo "[argocd] $APP đã chạy ${SHA:0:7}"
    exit 0
  fi
  if [ "$rev" = "$SHA" ] && { [ "$health" = Degraded ] || [ "$phase" = Failed ] || [ "$phase" = Error ]; }; then
    echo "[argocd] $APP LỖI ở ${SHA:0:7}. Thông báo:"
    kubectl -n argocd get application "$APP" -o jsonpath='{.status.operationState.message}{"\n"}{range .status.resources[?(@.health.status=="Degraded")]}{.kind}/{.name}: {.health.message}{"\n"}{end}' || true
    exit 1
  fi
  if [ "$(date +%s)" -ge "$end" ]; then
    echo "[argocd] HẾT GIỜ (${TIMEOUT}s): $APP chưa Healthy ở ${SHA:0:7}"
    exit 1
  fi
  sleep 10
done
