#!/usr/bin/env bash
# Tạo SealedSecret "ghcr-pull" (quyền kéo image private từ ghcr.io) cho production và staging.
# Token GitHub đọc từ bàn phím: không hiện lên màn hình, không ghi ra file, không vào lịch sử lệnh.
# Kết quả là file ĐÃ MÃ HÓA, an toàn để commit:
#   deploy/k8s/overlays/prod/sealed-ghcr-pull.yaml     (chỉ cụm máy 1 giải mã được)
#   deploy/k8s/overlays/staging/sealed-ghcr-pull.yaml  (chỉ cụm máy 2 giải mã được)
# Chạy trên máy 1:  bash deploy/k8s/scripts/seal-ghcr-pull-secret.sh
set -euo pipefail
cd "$(dirname "$0")/.."                       # về thư mục deploy/k8s

read -rsp "Dán token GitHub (chỉ quyền read:packages) rồi Enter: " TOKEN; echo
[ -n "$TOKEN" ] || { echo "Token trống, dừng."; exit 1; }

for env in prod staging; do
  kubectl create secret docker-registry ghcr-pull \
      --namespace "rms-$env" \
      --docker-server=ghcr.io \
      --docker-username=Anpham120 \
      --docker-password="$TOKEN" \
      --dry-run=client -o yaml \
    | kubeseal --cert "certs/$env.pem" --format yaml \
    > "overlays/$env/sealed-ghcr-pull.yaml"
  echo "Đã tạo overlays/$env/sealed-ghcr-pull.yaml"
done
unset TOKEN
