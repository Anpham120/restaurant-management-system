#!/usr/bin/env bash
# Demo các tính năng Kubernetes trên production (chạy trên máy 1, user coder). Mỗi mục an toàn, chạy dưới 2 phút,
# khách không bị gián đoạn: app và web có 2 pod, PodDisruptionBudget giữ tối thiểu 1.
#
#   bash scripts/demo/k8s.sh tu-phuc-hoi   xóa 1 pod rms-app: ReplicaSet dựng pod mới, web vẫn trả lời suốt quá trình
#   bash scripts/demo/k8s.sh gitops        sửa tay số pod rms-app 2 → 3: Argo CD (selfHeal) đưa về đúng Git là 2
#   bash scripts/demo/k8s.sh mang          NetworkPolicy: pod lạ không vào được PostgreSQL, pod app thì được
#   bash scripts/demo/k8s.sh secret        SealedSecret: Git chỉ có bản mã hóa, cụm tự giải mã thành Secret
#   bash scripts/demo/k8s.sh db            PostgreSQL 2 instance: pod nào là primary, replica, trạng thái sao lưu WAL
#   bash scripts/demo/k8s.sh tong-quan     mọi tài nguyên của namespace rms-prod
#   bash scripts/demo/k8s.sh tat-ca        chạy lần lượt mọi mục trên
set -uo pipefail
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
NS=rms-prod
URL=https://rms.atlasplatform12.tech/actuator/health
GIT=${GIT:-$HOME/rms-deploy}   # bản clone repo theo nhánh main
k() { kubectl -n "$NS" "$@"; }
gio() { TZ=Asia/Ho_Chi_Minh date +%H:%M:%S; }
tieu_de() { echo; echo "==================== $* ===================="; }

tu_phuc_hoi() {
  tieu_de "Tự phục hồi: xóa 1 pod của rms-app"
  k get pods -l app.kubernetes.io/name=rms-app -o wide
  local pod; pod=$(k get pods -l app.kubernetes.io/name=rms-app -o jsonpath='{.items[0].metadata.name}')
  # Gọi domain thật mỗi giây trong lúc pod bị xóa và dựng lại, đếm lần lỗi
  local log; log=$(mktemp)
  ( for i in $(seq 1 90); do curl -fsS -o /dev/null --max-time 2 "$URL" && echo ok || echo loi; sleep 1; done ) > "$log" &
  local probe=$!
  echo "[$(gio)] kubectl delete pod $pod"
  k delete pod "$pod" --wait=false
  local t0; t0=$(date +%s)
  until [ "$(k get pods -l app.kubernetes.io/name=rms-app --field-selector=status.phase=Running -o jsonpath='{range .items[*]}{.status.containerStatuses[0].ready}{"\n"}{end}' | grep -c true)" = 2 ] \
        && ! k get pod "$pod" >/dev/null 2>&1; do
    sleep 3
  done
  echo "[$(gio)] Đủ lại 2 pod sẵn sàng sau $(( $(date +%s) - t0 )) giây (pod mới phải chờ Spring Boot khởi động và readiness probe đạt)"
  kill "$probe" 2>/dev/null; wait "$probe" 2>/dev/null
  echo "Gọi $URL mỗi giây trong lúc đó: $(grep -c ok "$log") lần OK, $(grep -c loi "$log") lần lỗi"
  rm -f "$log"
  k get pods -l app.kubernetes.io/name=rms-app -o wide
}

gitops() {
  tieu_de "GitOps tự sửa lệch: sửa tay số pod, Argo CD đưa về đúng Git"
  echo "Trong Git (deploy/k8s/base/app-rollout.yaml): replicas: $(grep -m1 -E '^\s+replicas:' "$GIT/deploy/k8s/base/app-rollout.yaml" | awk '{print $2}')"
  echo "[$(gio)] kubectl patch rollout rms-app: replicas 2 → 3 (thay đổi tay, không qua Git)"
  k patch rollout rms-app --type merge -p '{"spec":{"replicas":3}}'
  local t0; t0=$(date +%s)
  echo "[$(gio)] Rollout ngay sau khi sửa: replicas=$(k get rollout rms-app -o jsonpath='{.spec.replicas}')"
  until [ "$(k get rollout rms-app -o jsonpath='{.spec.replicas}')" = 2 ] || [ $(( $(date +%s) - t0 )) -gt 180 ]; do sleep 2; done
  echo "[$(gio)] Argo CD đưa về replicas=$(k get rollout rms-app -o jsonpath='{.spec.replicas}') sau $(( $(date +%s) - t0 )) giây (selfHeal: true trong deploy/k8s/argocd/rms-prod.yaml)"
  sleep 5; k get pods -l app.kubernetes.io/name=rms-app
}

# Chạy 1 pod curl tạm với nhãn $1, thử mở kết nối tới PostgreSQL, in đúng dòng kết quả của curl
# Chờ 8 giây trước khi kết nối: kube-router cần vài giây đưa IP của pod mới vào luật cho phép; thử ngay thì pod nào
# cũng bị chặn (đã gặp khi thử 24/11), kết quả sẽ sai.
thu_ket_noi() {
  # Namespace bật PodSecurity "restricted": pod tạm phải chạy user thường, bỏ mọi capability
  k run "demo-mang-$2" --rm -i --quiet --restart=Never --image=curlimages/curl:8.11.1 --labels="app.kubernetes.io/name=$1" \
    --overrides='{"spec":{"securityContext":{"runAsNonRoot":true,"runAsUser":100,"seccompProfile":{"type":"RuntimeDefault"}},
      "containers":[{"name":"c","image":"curlimages/curl:8.11.1","command":["sh","-c","sleep 8; curl -v --connect-timeout 5 --max-time 3 telnet://rms-db-rw:5432"],
      "securityContext":{"allowPrivilegeEscalation":false,"capabilities":{"drop":["ALL"]}}}]}}' 2>&1 \
    | grep -E "Connected to|Failed to connect|timed out" | head -1 | sed 's/^/   curl: /'
}

mang() {
  tieu_de "NetworkPolicy: ai được vào PostgreSQL (rms-db-rw:5432)"
  echo "Luật: default-deny-ingress chặn mọi kết nối vào pod; allow-db chỉ mở 5432 cho pod có nhãn app.kubernetes.io/name=rms-app."
  echo "Hai lần thử dưới đây dùng CÙNG image, CÙNG lệnh, chỉ khác nhãn của pod."
  echo
  echo "[$(gio)] 1) Pod nhãn app.kubernetes.io/name=demo-mang:"
  thu_ket_noi demo-mang la
  echo "   → bị từ chối ngay: kube-router (bộ thực thi NetworkPolicy của k3s) trả REJECT"
  echo
  echo "[$(gio)] 2) Pod nhãn app.kubernetes.io/name=rms-app (giống backend):"
  thu_ket_noi rms-app app
  echo "   → kết nối được (curl dừng sau 3 giây vì không nói giao thức PostgreSQL, việc đó không quan trọng ở đây)"
  echo "Pod tạm không nhận traffic của khách: Service rms-app còn chọn theo rollouts-pod-template-hash."
}

secret() {
  tieu_de "SealedSecret: secret trong Git đã mã hóa"
  k get sealedsecrets
  echo
  echo "Trong Git ($GIT/deploy/k8s/overlays/prod/sealed-app-secrets.yaml), chỉ có bản mã hóa:"
  grep -A3 "encryptedData" "$GIT/deploy/k8s/overlays/prod/sealed-app-secrets.yaml" | cut -c1-110
  echo
  echo "Trong cụm, controller giải mã thành Secret thường (chỉ in tên khóa, không in giá trị):"
  k get secret rms-app-secrets -o go-template='{{range $k, $v := .data}}   {{$k}}{{"\n"}}{{end}}'
  echo "Chỉ cụm máy 1 có khóa riêng để giải mã; khóa được sao lưu mã hóa mỗi tuần (scripts/backup/backup-sealed-key.sh)."
}

db() {
  tieu_de "PostgreSQL (CloudNativePG): 2 instance"
  k get cluster.postgresql.cnpg.io rms-db
  k get pods -l cnpg.io/cluster=rms-db -L cnpg.io/instanceRole
  echo
  k get cluster.postgresql.cnpg.io rms-db -o jsonpath='{range .status.conditions[*]}   {.type}: {.status} ({.reason}){"\n"}{end}'
  echo
  echo "Bản nền WAL gần nhất:"
  k get backups.postgresql.cnpg.io --sort-by=.metadata.creationTimestamp | tail -3
}

tong_quan() {
  tieu_de "Tài nguyên namespace $NS"
  k get rollout,deploy,svc,ingress,pdb,networkpolicy,cluster.postgresql.cnpg.io,sealedsecret
  echo
  k get pods -o wide --field-selector=status.phase=Running
}

case "${1:-}" in
  tu-phuc-hoi) tu_phuc_hoi ;;
  gitops) gitops ;;
  mang) mang ;;
  secret) secret ;;
  db) db ;;
  tong-quan) tong_quan ;;
  tat-ca) tong_quan; tu_phuc_hoi; gitops; mang; secret; db ;;
  *) sed -n '2,13p' "$0"; exit 2 ;;
esac
