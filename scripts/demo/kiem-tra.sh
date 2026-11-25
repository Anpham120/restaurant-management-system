#!/usr/bin/env bash
# Kiểm tra nhanh trước buổi demo (chạy trên máy 1, user coder; cần SSH sang máy 2 qua WireGuard).
# In OK/LỖI cho từng mục: domain, blue-green, PostgreSQL, sao lưu, cấu hình k3s và nginx so với Git, Argo CD, cảnh báo đang bật.
#
# Dùng: bash ~/rms-deploy/scripts/demo/kiem-tra.sh
set -uo pipefail
REPO=$(dirname "$(readlink -f "$0")")/../..
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
MAY2=${MAY2:-coder@10.10.0.2}
loi=0
ok()  { echo "  OK   $*"; }
bad() { echo "  LỖI  $*"; loi=1; }

echo "== Production (máy 1)"
h=$(curl -fsS --max-time 10 https://rms.atlasplatform12.tech/actuator/health 2>/dev/null)
[[ "$h" == *'"status":"UP"'* ]] && ok "health UP" || bad "health: ${h:-không trả lời}"
r=$(kubectl argo rollouts status rms-app -n rms-prod --timeout 5s 2>&1 | tail -1)
[ "$r" = Healthy ] && ok "Rollout rms-app Healthy" || bad "Rollout rms-app: $r"
c=$(kubectl -n rms-prod get cluster.postgresql.cnpg.io rms-db -o jsonpath='{.status.phase}/{.status.readyInstances}' 2>&1)
[ "$c" = "Cluster in healthy state/2" ] && ok "PostgreSQL 2/2 instance sẵn sàng" || bad "PostgreSQL: $c"
d=$(ls -t /home/coder/rms-backups/daily-*.dump 2>/dev/null | head -1)
if [ -n "$d" ] && [ -n "$(find "$d" -mmin -1560)" ]; then ok "pg_dump gần nhất: $(basename "$d")"; else bad "không có pg_dump trong 26 giờ"; fi
b=$(kubectl -n rms-prod get backups.postgresql.cnpg.io --sort-by=.metadata.creationTimestamp -o jsonpath='{.items[-1:].metadata.name} {.items[-1:].status.phase}' 2>&1)
[[ "$b" == *completed ]] && ok "bản nền WAL gần nhất: $b" || bad "bản nền WAL: $b"
bash "$REPO/deploy/k3s/apply.sh" >/dev/null 2>&1 && ok "cấu hình k3s máy 1 khớp Git" || bad "cấu hình k3s máy 1 lệch Git: bash deploy/k3s/apply.sh để xem"
bash "$REPO/deploy/nginx/apply.sh" >/dev/null 2>&1 && ok "cấu hình nginx máy 1 khớp Git" || bad "cấu hình nginx máy 1 lệch Git: bash deploy/nginx/apply.sh để xem"

echo "== Máy 2 (Argo CD, giám sát)"
ssh -o ConnectTimeout=5 -o BatchMode=yes "$MAY2" '
  KUBECONFIG=$HOME/.kube/config kubectl -n argocd get applications -o jsonpath="{range .items[*]}{.metadata.name} {.status.sync.status} {.status.health.status}{\"\n\"}{end}"
  echo "---"
  curl -s 127.0.0.1:9090/api/v1/alerts | python3 -c "import json,sys; a=[x[\"labels\"][\"alertname\"] for x in json.load(sys.stdin)[\"data\"][\"alerts\"] if x[\"state\"]==\"firing\" and x[\"labels\"][\"alertname\"]!=\"Watchdog\"]; print(\" \".join(a))"
' > /tmp/kiem-tra-may2.$$ 2>&1 || bad "không SSH được sang máy 2"
if [ -s /tmp/kiem-tra-may2.$$ ]; then
  while read -r app sync health; do
    [ "$app" = --- ] && break
    [ "$sync $health" = "Synced Healthy" ] && ok "Argo CD $app Synced Healthy" || bad "Argo CD $app $sync $health"
  done < /tmp/kiem-tra-may2.$$
  canh=$(sed -n '/^---$/,$p' /tmp/kiem-tra-may2.$$ | tail -n +2)
  [ -z "$canh" ] && ok "không có cảnh báo nào đang bật (trừ Watchdog)" || bad "cảnh báo đang bật: $canh"
fi
rm -f /tmp/kiem-tra-may2.$$
ssh -o ConnectTimeout=5 -o BatchMode=yes "$MAY2" 'bash ~/rms-deploy/deploy/k3s/apply.sh' >/dev/null 2>&1 \
  && ok "cấu hình k3s máy 2 khớp Git" || bad "cấu hình k3s máy 2 lệch Git: bash ~/rms-deploy/deploy/k3s/apply.sh trên máy 2 để xem"
ssh -o ConnectTimeout=5 -o BatchMode=yes "$MAY2" 'bash ~/rms-deploy/deploy/nginx/apply.sh' >/dev/null 2>&1 \
  && ok "cấu hình nginx máy 2 khớp Git" || bad "cấu hình nginx máy 2 lệch Git: bash ~/rms-deploy/deploy/nginx/apply.sh trên máy 2 để xem"

echo
[ "$loi" = 0 ] && echo "SẴN SÀNG DEMO" || echo "CÓ MỤC LỖI: xử lý trước khi demo"
exit "$loi"
