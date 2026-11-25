#!/usr/bin/env bash
# Cấu hình nginx của 2 máy, lưu trong Git theo đúng thư mục trên máy:
#   may1/sites-available/*.conf -> /etc/nginx/sites-available/ (bật bằng symlink trong sites-enabled/)
#   may1/conf.d/*.conf          -> /etc/nginx/conf.d/
#   may2/...                       tương tự cho máy 2
# nginx chạy trên máy chủ, ngoài cụm Kubernetes, và là cửa vào HTTPS của chính Argo CD, Jenkins, Grafana: cấu hình sai
# thì mất đường vào để sửa. Nên không để công cụ tự áp; áp có chủ đích bằng script này, luôn qua "nginx -t" trước khi reload.
# Định tuyến ứng dụng không nằm ở đây mà ở Traefik trong cụm (deploy/k8s/overlays/prod/ingress.yaml, Argo CD quản lý).
#
# Dùng (trên máy 1 hoặc máy 2, user coder có sudo; ~/rms-deploy tự cập nhật theo main mỗi 5 phút):
#   bash ~/rms-deploy/deploy/nginx/apply.sh             so sánh máy với Git, in khác biệt. Khớp: thoát 0, lệch: thoát 1
#   bash ~/rms-deploy/deploy/nginx/apply.sh --ap-dung   sao lưu file sắp thay vào /etc/nginx/rms-bak/<giờ>/, chép bản Git,
#                                                       bật symlink còn thiếu, nginx -t; đạt thì reload (không ngắt kết nối
#                                                       đang có), lỗi thì trả lại bản cũ và thoát 1
# Quy trình đổi cấu hình: sửa file trên nhánh feature/*, push, chờ lên main (pipeline), rồi chạy --ap-dung trên máy đó.
# File trên máy không có trong Git (default, site của dự án khác) thì bỏ qua, không đụng tới.
set -euo pipefail
DIR=$(dirname "$(readlink -f "$0")")

case "$(ip -4 -o addr show wg0 2>/dev/null)" in
  *" 10.10.0.1/"*) MAY=may1 ;;
  *" 10.10.0.2/"*) MAY=may2 ;;
  *) echo "Không nhận ra máy: wg0 không có 10.10.0.1 hoặc 10.10.0.2"; exit 2 ;;
esac
GOC=$DIR/$MAY

# Danh sách cặp "đường dẫn tương đối" có trong Git
mapfile -t DS < <(cd "$GOC" && find . -type f -name '*.conf' | sed 's#^\./##' | sort)
lech=()
for f in "${DS[@]}"; do
  dich=/etc/nginx/$f
  if ! cmp -s "$GOC/$f" "$dich"; then
    lech+=("$f")
    echo "[$MAY] lệch: $dich (- trên máy, + trong Git)"
    diff -u "$dich" "$GOC/$f" 2>/dev/null || [ -e "$dich" ] || echo "    (chưa có trên máy)"
  fi
  case "$f" in
    sites-available/*)
      ten=${f#sites-available/}
      [ "$(readlink -f "/etc/nginx/sites-enabled/$ten")" = "$dich" ] || { echo "[$MAY] chưa bật: sites-enabled/$ten"; lech+=("bat:$ten"); }
      ;;
  esac
done

if [ ${#lech[@]} -eq 0 ]; then
  echo "[$MAY] nginx khớp Git (${#DS[@]} file)"
  exit 0
fi
if [ "${1:-}" != --ap-dung ]; then
  echo "Chạy lại với --ap-dung để áp bản Git vào máy (nginx -t rồi reload)"
  exit 1
fi

BAK=/etc/nginx/rms-bak/$(date +%Y%m%d-%H%M%S)   # ngoài sites-enabled và conf.d để nginx không nạp nhầm bản sao lưu
sudo mkdir -p "$BAK"
moi=()   # file hoặc symlink trước đó chưa có: trả lại nghĩa là gỡ đi
tra_lai() {
  echo "[$MAY] nginx -t lỗi: trả lại bản cũ từ $BAK"
  for f in "${DS[@]}"; do
    if [ -e "$BAK/$f" ]; then sudo cp -p "$BAK/$f" "/etc/nginx/$f"; fi
  done
  for f in "${moi[@]}"; do sudo rm -f "$f"; done
  sudo nginx -t
  exit 1
}
for x in "${lech[@]}"; do
  case "$x" in
    bat:*)
      l=/etc/nginx/sites-enabled/${x#bat:}
      [ -e "$l" ] || [ -L "$l" ] || moi+=("$l")
      sudo ln -sfn "/etc/nginx/sites-available/${x#bat:}" "$l" ;;
    *)
      if [ -e "/etc/nginx/$x" ]; then sudo install -D -m 644 "/etc/nginx/$x" "$BAK/$x"; else moi+=("/etc/nginx/$x"); fi
      sudo install -D -m 644 -o root -g root "$GOC/$x" "/etc/nginx/$x" ;;
  esac
done
sudo nginx -t || tra_lai
sudo systemctl reload nginx
echo "[$MAY] đã áp ${#lech[@]} thay đổi và reload nginx (bản cũ: $BAK)"
