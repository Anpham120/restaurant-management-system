#!/usr/bin/env bash
# Chờ Argo CD triển khai xong một commit: Application ở đúng revision, Synced và Healthy (Rollout blue-green đã qua
# AnalysisTemplate và chuyển traffic). Đọc trạng thái bằng kubectl (ServiceAccount chỉ đọc).
#   KUBECONFIG=... scripts/release/argocd-wait.sh <rms-staging|rms-prod> <commit sha> [giây, mặc định 900]
# Degraded tạm thời là bình thường (pod đang khởi động lại, DB đang chuyển primary): chỉ báo lỗi khi Degraded kéo dài quá
# DEGRADED_GRACE giây (mặc định 180), hoặc ngay khi Argo Rollouts hủy bản mới (abort: AnalysisTemplate không đạt),
# hoặc khi lần đồng bộ của Argo CD thất bại.
set -euo pipefail
APP=${1:?Dùng: argocd-wait.sh <app> <sha> [timeout]}
SHA=${2:?Thiếu commit sha}
TIMEOUT=${3:-900}
GRACE=${DEGRADED_GRACE:-180}
end=$(( $(date +%s) + TIMEOUT ))
last="" degraded_since=""

loi() {
  echo "[argocd] $APP LỖI ở ${SHA:0:7}: $1"
  kubectl -n argocd get application "$APP" -o jsonpath='{.status.operationState.message}{"\n"}{range .status.resources[?(@.health.status=="Degraded")]}{.kind}/{.name}: {.health.message}{"\n"}{end}' || true
  exit 1
}

while :; do
  st=$(kubectl -n argocd get application "$APP" \
        -o jsonpath='{.status.sync.revision} {.status.sync.status} {.status.health.status} {.status.operationState.phase}' 2>/dev/null || true)
  read -r rev sync health phase <<<"$st"
  line="$APP: revision ${rev:0:7} · $sync · $health · ${phase:-}"
  [ "$line" != "$last" ] && echo "[argocd] $line" && last=$line
  now=$(date +%s)
  if [ "$rev" = "$SHA" ]; then
    if [ "$sync" = Synced ] && [ "$health" = Healthy ]; then
      echo "[argocd] $APP đã chạy ${SHA:0:7}"
      exit 0
    fi
    [ "$phase" = Failed ] || [ "$phase" = Error ] && loi "đồng bộ thất bại"
    if kubectl -n argocd get application "$APP" -o jsonpath='{range .status.resources[?(@.kind=="Rollout")]}{.health.message}{end}' 2>/dev/null | grep -qi abort; then
      loi "Argo Rollouts đã hủy bản mới (không qua AnalysisTemplate), bản cũ vẫn phục vụ"
    fi
    if [ "$health" = Degraded ]; then
      degraded_since=${degraded_since:-$now}
      [ $(( now - degraded_since )) -ge "$GRACE" ] && loi "Degraded liên tục quá ${GRACE}s"
    else
      degraded_since=""
    fi
  fi
  [ "$now" -ge "$end" ] && loi "hết giờ (${TIMEOUT}s), chưa Healthy"
  sleep 10
done
