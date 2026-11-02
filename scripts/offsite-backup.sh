#!/usr/bin/env bash
# Gửi bản sao lưu database mới nhất ra ngoài máy chủ: mã hóa AES-256 rồi gửi file qua bot Telegram (cùng bot cảnh báo).
# Máy chủ hỏng ổ đĩa vẫn còn bản ở Telegram. Chạy bằng cron của user coder sau backup-db.sh daily (docs/devops/PIPELINE.md).
# Khóa mã hóa: KEY_FILE (quyền 600). Telegram chỉ thấy file đã mã hóa; giữ một bản khóa ở nơi khác máy chủ, mất khóa là
# không giải mã được. Giải mã:
#   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:<khóa> -in <file>.dump.enc -out <file>.dump
set -euo pipefail

BACKUP_DIR=${BACKUP_DIR:-/home/coder/rms-backups}
KEY_FILE=${KEY_FILE:-/home/coder/rms-backups/.offsite.key}
TOKEN_FILE=${TOKEN_FILE:-/home/coder/projects/RSM/restaurant-management-system/monitoring/secrets/telegram_bot_token}
CHAT_ID=${CHAT_ID:-6822487436}

[ -s "$KEY_FILE" ] || { echo "[offsite] thiếu khóa $KEY_FILE"; exit 1; }
[ -s "$TOKEN_FILE" ] || { echo "[offsite] thiếu token bot $TOKEN_FILE"; exit 1; }
LATEST=$(ls -1t "$BACKUP_DIR"/daily-*.dump 2>/dev/null | head -1 || true)
[ -n "$LATEST" ] || { echo "[offsite] chưa có bản daily-*.dump"; exit 1; }

umask 077
ENC=$(mktemp --suffix=.dump.enc)
trap 'rm -f "$ENC"' EXIT
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "file:$KEY_FILE" -in "$LATEST" -out "$ENC"

# Token đưa qua stdin (curl -K -) để không lộ trong danh sách tiến trình hay log
code=$(printf 'url = "https://api.telegram.org/bot%s/sendDocument"\n' "$(cat "$TOKEN_FILE")" \
  | curl -s -K - -o /dev/null -w '%{http_code}' --max-time 60 \
      -F "chat_id=$CHAT_ID" -F "caption=Sao lưu RMS $(basename "$LATEST") (đã mã hóa)" \
      -F "document=@$ENC;filename=$(basename "$LATEST").enc")
[ "$code" = 200 ] || { echo "[offsite] gửi Telegram lỗi HTTP $code"; exit 1; }
echo "[offsite] đã gửi $(basename "$LATEST").enc"
