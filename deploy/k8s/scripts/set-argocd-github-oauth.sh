#!/usr/bin/env bash
# Lưu Client ID/Secret của GitHub OAuth App cho Argo CD vào Secret "argocd-github-oauth" (namespace argocd, máy 2).
# Secret nhập từ bàn phím, không hiện, không ghi file, không vào Git. Argo CD đọc qua tham chiếu
# $argocd-github-oauth:clientSecret trong cấu hình Dex (label part-of=argocd là bắt buộc để Argo CD được đọc).
# Chạy trên máy 2:  bash set-argocd-github-oauth.sh
set -euo pipefail
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
read -rp  "GitHub OAuth App - Client ID: " ID
read -rsp "GitHub OAuth App - Client secret (không hiện): " SECRET; echo
[ -n "$ID" ] && [ -n "$SECRET" ] || { echo "Thiếu giá trị, dừng."; exit 1; }
kubectl -n argocd create secret generic argocd-github-oauth \
  --from-literal=clientID="$ID" --from-literal=clientSecret="$SECRET" \
  --dry-run=client -o yaml \
  | kubectl label --local -f - app.kubernetes.io/part-of=argocd -o yaml \
  | kubectl apply -f -
unset SECRET
echo "Đã lưu Secret argocd/argocd-github-oauth."
