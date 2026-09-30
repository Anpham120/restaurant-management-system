#!/bin/sh
# P0-04: restores a dump made by backup.sh. Run it next to docker-compose.prod.yml (~/bnn-rms or ~/bnn-rms-staging).
#   sh restore.sh --check backups/rms-2026-10-05-0300.dump
#       loads the dump into a scratch database, prints the row count of each table, then drops it.
#       The live data is not touched: run it after each backup you want to trust.
#   sh restore.sh backups/rms-2026-10-05-0300.dump
#       replaces the live database with the dump, after asking for confirmation.
set -eu

COMPOSE="${COMPOSE:-docker compose -f docker-compose.prod.yml}"
check=false
if [ "${1:-}" = "--check" ]; then
  check=true
  shift
fi
dump="${1:?usage: sh restore.sh [--check] backups/<file>.dump}"
[ -f "$dump" ] || { echo "No such file: $dump" >&2; exit 1; }

sql() { $COMPOSE exec -T db psql -U rms -v ON_ERROR_STOP=1 -q "$@"; }
load() { $COMPOSE exec -T db pg_restore -U rms --no-owner --exit-on-error -d "$1" < "$dump"; }

if $check; then
  scratch=rms_restore_check
  sql -d postgres -c "DROP DATABASE IF EXISTS $scratch" -c "CREATE DATABASE $scratch"
  load "$scratch"
  sql -d "$scratch" -c "ANALYZE" -c "SELECT c.relname AS bang, c.reltuples::bigint AS so_dong
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relkind = 'r' ORDER BY c.relname"
  sql -d postgres -c "DROP DATABASE $scratch"
  echo "OK: $dump restores cleanly."
  exit 0
fi

echo "This replaces the live database with $dump. Type YES to go on:"
read -r answer
[ "$answer" = "YES" ] || { echo "Stopped, nothing changed."; exit 1; }
$COMPOSE stop backend
sql -d postgres -c "DROP DATABASE rms WITH (FORCE)" -c "CREATE DATABASE rms"
load rms
$COMPOSE start backend
echo "Restored $dump. The backend is starting again."
