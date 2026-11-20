#!/usr/bin/env bash
# Máy 2. Tạo secret cho webhook GitHub -> Argo CD: GitHub báo ngay khi có push, Argo CD đồng bộ trong vài giây thay vì
# chờ lần quét Git định kỳ (60 giây + độ trễ ngẫu nhiên, thực tế có lúc hơn 2 phút).
# Secret lưu ở khóa webhook.github.secret của Secret argocd/argocd-secret (Argo CD từ chối webhook sai chữ ký).
# Script in secret ra màn hình MỘT lần để dán vào GitHub: repo -> Settings -> Webhooks -> Add webhook.
#   ssh -t coder@10.10.0.2 'bash ~/k8s/set-argocd-webhook-secret.sh'
set -euo pipefail
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
SECRET=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-40)
kubectl -n argocd patch secret argocd-secret --type merge \
  -p "{\"stringData\":{\"webhook.github.secret\":\"$SECRET\"}}" >/dev/null
cat <<MSG

Đã lưu secret vào argocd/argocd-secret. Trên GitHub (repo restaurant-management-system -> Settings -> Webhooks -> Add webhook):
  Payload URL : https://argocd.atlasplatform12.tech/api/webhook
  Content type: application/json
  Secret      : $SECRET
  Events      : Just the push event
  Active      : bật
Secret chỉ hiện lần này. Mất thì chạy lại script (secret cũ hết hiệu lực) và sửa lại webhook.
MSG
