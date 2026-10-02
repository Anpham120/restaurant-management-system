#!/bin/sh
# P0-04: a pg_dump of the database every night at BACKUP_HOUR, the newest BACKUP_KEEP dumps kept (default 7).
# Runs inside the "backup" service of docker-compose.prod.yml; the connection comes from PGHOST, PGUSER,
# PGPASSWORD and PGDATABASE. "sh /backup.sh now" makes one dump and exits.
set -eu

KEEP="${BACKUP_KEEP:-7}"
HOUR="${BACKUP_HOUR:-3}"

dump() {
  file="/backups/rms-$(date +%Y-%m-%d-%H%M).dump"
  # Checked by hand: set -e does not stop a function called as "dump || ...", as the loop below does.
  # Without this, a failed dump would be kept as the newest one and push a good dump out.
  if ! pg_dump --format=custom --file="$file.part"; then
    rm -f "$file.part"
    return 1
  fi
  mv "$file.part" "$file"
  ls -1t /backups/rms-*.dump | tail -n +"$((KEEP + 1))" | xargs -r rm -f
  # NFR-12: when the last good dump was made, read by the monitoring agent (node exporter textfile format).
  # Written whole, then renamed, so the agent never reads half a file.
  cat > /backups/backup.prom.part <<EOF
# HELP khoibep_backup_last_success_timestamp_seconds When the last good database dump finished.
# TYPE khoibep_backup_last_success_timestamp_seconds gauge
khoibep_backup_last_success_timestamp_seconds $(date +%s)
# HELP khoibep_backup_last_size_bytes Size of the last good database dump.
# TYPE khoibep_backup_last_size_bytes gauge
khoibep_backup_last_size_bytes $(stat -c %s "$file")
EOF
  mv /backups/backup.prom.part /backups/backup.prom
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
