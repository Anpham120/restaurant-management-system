#!/usr/bin/env bash
# Cấu hình k3s của 2 máy, lưu trong Git: may1-config.yaml (cụm production) và may2-config.yaml (cụm quản lý).
# Trên máy, k3s đọc /etc/rancher/k3s/config.yaml khi khởi động (k3s.service chỉ chạy "k3s server", không có cờ nào khác,
# nên file này là toàn bộ cấu hình). Bản trong Git phải giống từng byte với file trên máy.
#
# Vì sao không để Argo CD tự áp như phần còn lại: Argo CD chạy trên chính k3s và điều khiển cụm qua API server mà file này
# cấu hình. Một cấu hình sai làm k3s không lên được thì Argo CD cũng mất đường vào để sửa. Nên lớp nền này áp có chủ đích,
# bằng tay, qua script này; đổi cấu hình phải khởi động lại k3s (gián đoạn vài giây đến vài chục giây).
#
# Dùng (trên máy 1 hoặc máy 2, user coder có sudo; thư mục ~/rms-deploy tự cập nhật theo main mỗi 5 phút):
#   bash ~/rms-deploy/deploy/k3s/apply.sh             so sánh file trên máy với Git, in khác biệt. Khớp: thoát 0, lệch: thoát 1
#   bash ~/rms-deploy/deploy/k3s/apply.sh --ap-dung   lệch thì sao lưu file cũ, chép bản Git vào, khởi động lại k3s,
#                                                     chờ node Ready
# Quy trình đổi cấu hình: sửa file *-config.yaml trên nhánh feature/*, push, chờ lên main (pipeline), rồi chạy --ap-dung.
# scripts/demo/kiem-tra.sh gọi script này (không --ap-dung) để báo khi máy lệch Git.
set -euo pipefail
DIR=$(dirname "$(readlink -f "$0")")
DICH=/etc/rancher/k3s/config.yaml

# Nhận ra máy theo IP WireGuard
case "$(ip -4 -o addr show wg0 2>/dev/null)" in
  *" 10.10.0.1/"*) MAY=may1 ;;
  *" 10.10.0.2/"*) MAY=may2 ;;
  *) echo "Không nhận ra máy: wg0 không có 10.10.0.1 hoặc 10.10.0.2"; exit 2 ;;
esac
NGUON=$DIR/$MAY-config.yaml

if cmp -s "$NGUON" "$DICH"; then
  echo "[$MAY] $DICH khớp Git ($MAY-config.yaml)"
  exit 0
fi
echo "[$MAY] $DICH lệch Git. Khác biệt (- trên máy, + trong Git):"
diff -u "$DICH" "$NGUON" || true

if [ "${1:-}" != --ap-dung ]; then
  echo "Chạy lại với --ap-dung để áp bản Git vào máy (khởi động lại k3s)"
  exit 1
fi

NODE=$(sed -nE 's/^node-name: *"?([^"]+)"?$/\1/p' "$NGUON")
BAK="$DICH.bak-$(date +%Y%m%d-%H%M%S)"
sudo cp -p "$DICH" "$BAK"
sudo install -m 644 -o root -g root "$NGUON" "$DICH"
echo "[$MAY] đã chép bản Git vào $DICH (bản cũ: $BAK). Khởi động lại k3s..."
sudo systemctl restart k3s

# Chờ API server trả lời rồi chờ node Ready (tối đa 5 phút)
export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
for _ in $(seq 1 60); do
  kubectl get node "$NODE" >/dev/null 2>&1 && break
  sleep 5
done
kubectl wait --for=condition=Ready "node/$NODE" --timeout=300s
echo "[$MAY] k3s đã chạy lại với cấu hình trong Git. Nếu có lỗi: sudo cp -p $BAK $DICH && sudo systemctl restart k3s"
