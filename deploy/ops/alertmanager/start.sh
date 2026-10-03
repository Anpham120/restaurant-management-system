#!/bin/sh
# P0-07: Alertmanager reads no environment variables in its config file. Write the Telegram bot token and chat id
# from .env into a copy inside the container (never into Git), then run Alertmanager on that copy.
set -eu
sed -e "s|__TELEGRAM_BOT_TOKEN__|${TELEGRAM_BOT_TOKEN}|" -e "s|__TELEGRAM_CHAT_ID__|${TELEGRAM_CHAT_ID}|" \
  /etc/alertmanager/alertmanager.yml > /alertmanager/alertmanager.yml
exec /bin/alertmanager \
  --config.file=/alertmanager/alertmanager.yml \
  --storage.path=/alertmanager \
  --cluster.listen-address=
