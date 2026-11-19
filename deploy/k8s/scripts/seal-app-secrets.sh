#!/usr/bin/env bash
# Tạo SealedSecret cho app và tài khoản DB của một môi trường. Không in giá trị bí mật ra màn hình.
#   rms-app-secrets : JWT_SECRET, SEPAY_API_KEY
#   rms-db-api      : mật khẩu login rms_api   (kiểu basic-auth, CloudNativePG đọc)
#   rms-db-sepay    : mật khẩu login rms_sepay (kiểu basic-auth)
# prod: JWT_SECRET và SEPAY_API_KEY lấy từ container production đang chạy (giữ nguyên để token đăng nhập
#       và webhook SePay không đổi khi chuyển); mật khẩu DB sinh mới.
# staging: sinh ngẫu nhiên tất cả.
# Chạy trên máy 1:  bash deploy/k8s/scripts/seal-app-secrets.sh prod|staging
set -euo pipefail
env=${1:?prod hoặc staging}
cd "$(dirname "$0")/.."
ns=rms-$env cert=certs/$env.pem out=overlays/$env
rand() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | cut -c1-"$1"; }
seal() { kubeseal --cert "$cert" --format yaml; }

if [ "$env" = prod ]; then
  c=restaurant-management-system-app-b-1
  docker ps --format '{{.Names}}' | grep -qx "$c" || c=restaurant-management-system-app-1
  JWT=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$c" | sed -n 's/^JWT_SECRET=//p')
  SEPAY=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$c" | sed -n 's/^SEPAY_API_KEY=//p')
  [ -n "$JWT" ] && [ -n "$SEPAY" ] || { echo "Không đọc được JWT_SECRET/SEPAY_API_KEY từ $c"; exit 1; }
else
  JWT=$(rand 64); SEPAY=$(rand 40)
fi

kubectl create secret generic rms-app-secrets -n "$ns" \
  --from-literal=JWT_SECRET="$JWT" --from-literal=SEPAY_API_KEY="$SEPAY" \
  --dry-run=client -o yaml | seal > "$out/sealed-app-secrets.yaml"
for login in api sepay; do
  kubectl create secret generic "rms-db-$login" -n "$ns" --type=kubernetes.io/basic-auth \
    --from-literal=username="rms_$login" --from-literal=password="$(rand 32)" \
    --dry-run=client -o yaml | seal > "$out/sealed-db-$login.yaml"
done
unset JWT SEPAY
echo "Đã tạo $out/sealed-app-secrets.yaml, sealed-db-api.yaml, sealed-db-sepay.yaml"
