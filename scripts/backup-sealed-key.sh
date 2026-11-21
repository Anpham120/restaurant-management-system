#!/usr/bin/env bash
# Sao lưu khóa riêng của Sealed Secrets (cụm máy 1, namespace kube-system). Mọi SealedSecret trong Git
# (deploy/k8s/overlays/prod/sealed-*.yaml) chỉ giải mã được bằng khóa này: mất cụm mà không có khóa thì phải tạo lại
# từng secret bằng tay. Controller tự sinh khóa mới mỗi 30 ngày (giữ khóa cũ), nên chạy định kỳ (cron hằng tuần).
#   scripts/backup-sealed-key.sh
# Khóa xuất ra được mã hóa AES-256 ngay trong pipe (không bao giờ ghi bản rõ ra đĩa hay log), bằng cùng khóa offsite với
# bản sao lưu database (KEY_FILE). Lưu ở BACKUP_DIR (giữ KEEP bản), chép sang COPY_TO (máy 2) và gửi qua bot Telegram.
# Khôi phục lên cụm mới (cần kubectl vào cụm đó):
#   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:<khóa> -in sealed-secrets-key-<ngày>.yaml.enc | kubectl apply -f -
#   kubectl -n kube-system rollout restart deploy/sealed-secrets-controller
set -euo pipefail

export KUBECONFIG=${KUBECONFIG:-$HOME/.kube/config}
BACKUP_DIR=${BACKUP_DIR:-/home/coder/rms-backups}
KEY_FILE=${KEY_FILE:-/home/coder/rms-backups/.offsite.key}
TOKEN_FILE=${TOKEN_FILE:-/home/coder/rms-deploy/monitoring/secrets/telegram_bot_token}
CHAT_ID=${CHAT_ID:-6822487436}
COPY_TO=${COPY_TO:-}
KEEP=${KEEP:-8}
LABEL=sealedsecrets.bitnami.com/sealed-secrets-key

[ -s "$KEY_FILE" ] || { echo "[sealed-key] thiếu khóa $KEY_FILE"; exit 1; }
n=$(kubectl -n kube-system get secret -l "$LABEL" -o name | wc -l)
[ "$n" -ge 1 ] || { echo "[sealed-key] không tìm thấy khóa nào (nhãn $LABEL)"; exit 1; }

umask 077
OUT="$BACKUP_DIR/sealed-secrets-key-$(date +%Y%m%d).yaml.enc"
kubectl -n kube-system get secret -l "$LABEL" -o yaml \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "file:$KEY_FILE" -out "$OUT.tmp"
mv "$OUT.tmp" "$OUT"
echo "[sealed-key] đã sao lưu $n khóa -> $(basename "$OUT")"
# Chỉ giữ KEEP bản mới nhất
ls -1t "$BACKUP_DIR"/sealed-secrets-key-*.yaml.enc | tail -n +$((KEEP + 1)) | xargs -r rm -f

if [ -n "$COPY_TO" ]; then
  if scp -q -p "$OUT" "$COPY_TO/"; then echo "[sealed-key] đã chép sang ${COPY_TO%%:*}"
  else echo "[sealed-key] không chép được sang ${COPY_TO%%:*}"; fi
fi

if [ -s "$TOKEN_FILE" ]; then
  # Token đưa qua stdin (curl -K -) để không lộ trong danh sách tiến trình hay log
  code=$(printf 'url = "https://api.telegram.org/bot%s/sendDocument"\n' "$(cat "$TOKEN_FILE")" \
    | curl -s -K - -o /dev/null -w '%{http_code}' --max-time 60 \
        -F "chat_id=$CHAT_ID" -F "caption=Khóa Sealed Secrets RMS $(basename "$OUT") (đã mã hóa)" \
        -F "document=@$OUT")
  [ "$code" = 200 ] || { echo "[sealed-key] gửi Telegram lỗi HTTP $code"; exit 1; }
  echo "[sealed-key] đã gửi Telegram"
fi

# Báo Prometheus (luật SealedKeyBackupStale)
BACKUP_DIR="$BACKUP_DIR" "$(dirname "$0")/backup-metric.sh" sealed-key || echo "[sealed-key] không ghi được số liệu cho Prometheus"
