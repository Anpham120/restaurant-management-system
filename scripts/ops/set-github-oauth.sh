#!/usr/bin/env bash
# Lưu Client ID / Client secret của GitHub OAuth App cho Grafana hoặc Jenkins. Secret nhập ẩn, không vào Git.
# Chạy trên máy đang chạy công cụ đó (Grafana: máy 2, từ máy 1: ssh -t coder@10.10.0.2 'bash ~/rms-deploy/scripts/ops/set-github-oauth.sh grafana').
#   bash scripts/ops/set-github-oauth.sh grafana  -> /home/coder/rms-deploy/deploy/monitoring/.env (GRAFANA_GITHUB_CLIENT_ID/SECRET), quyền 600
#   bash scripts/ops/set-github-oauth.sh jenkins  -> /opt/jenkins/secrets/github-oauth-client-id, github-oauth-client-secret
#                                                 (chủ uid 1000 = user jenkins trong container, quyền 600; cần sudo). Dùng ở Phần 7.
set -euo pipefail
tool=${1:?Dùng: bash scripts/ops/set-github-oauth.sh grafana|jenkins}

read -rp  "Client ID: " id
read -rsp "Client secret (không hiện khi dán): " secret; echo
[[ $id =~ ^[A-Za-z0-9._-]{10,}$ ]] || { echo "Client ID không hợp lệ"; exit 1; }
[[ ${#secret} -ge 20 && $secret != *[[:space:]]* ]] || { echo "Client secret không hợp lệ"; exit 1; }

case $tool in
  grafana)
    env=/home/coder/rms-deploy/deploy/monitoring/.env
    umask 077
    touch "$env"
    tmp=$(mktemp "$env.XXXX")
    grep -vE '^GRAFANA_GITHUB_CLIENT_(ID|SECRET)=' "$env" > "$tmp" || true
    printf 'GRAFANA_GITHUB_CLIENT_ID=%s\nGRAFANA_GITHUB_CLIENT_SECRET=%s\n' "$id" "$secret" >> "$tmp"
    mv "$tmp" "$env"; chmod 600 "$env"
    echo "Đã lưu vào $env"
    ;;
  jenkins)
    dir=/opt/jenkins/secrets
    sudo install -d -m 700 -o 1000 -g 1000 "$dir"
    printf '%s' "$id"     | sudo install -m 600 -o 1000 -g 1000 /dev/stdin "$dir/github-oauth-client-id"
    printf '%s' "$secret" | sudo install -m 600 -o 1000 -g 1000 /dev/stdin "$dir/github-oauth-client-secret"
    echo "Đã lưu vào $dir/"
    ;;
  *) echo "Chỉ nhận grafana hoặc jenkins"; exit 1 ;;
esac
