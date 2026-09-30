#!/bin/sh
# P0-04: a pg_dump of the database every night at BACKUP_HOUR, the newest BACKUP_KEEP dumps kept (default 7).
# Runs inside the "backup" service of docker-compose.prod.yml; the connection comes from PGHOST, PGUSER,
# PGPASSWORD and PGDATABASE. "sh /backup.sh now" makes one dump and exits.
set -eu

KEEP="${BACKUP_KEEP:-7}"
HOUR="${BACKUP_HOUR:-3}"

dump() {
  file="/backups/rms-$(date +%Y-%m-%d-%H%M).dump"
  pg_dump --format=custom --file="$file.part"
  mv "$file.part" "$file"
  ls -1t /backups/rms-*.dump | tail -n +"$((KEEP + 1))" | xargs -r rm -f
  echo "$(date '+%F %T') saved $file ($(du -h "$file" | cut -f1)), keeping the newest $KEEP"
}

if [ "${1:-}" = "now" ]; then
  dump
  exit 0
fi

echo "Backups every day at $(printf %02d "$HOUR"):00 ($TZ), keeping the newest $KEEP"
while true; do
  now=$(date +%s)
  next=$(date -d "$(date +%Y-%m-%d) $(printf %02d "$HOUR"):00:00" +%s)
  [ "$next" -gt "$now" ] || next=$((next + 86400))
  sleep $((next - now))
  dump || echo "$(date '+%F %T') backup failed" >&2
done
