#!/bin/sh
# P0-09: waits up to two and a half minutes for a site's /actuator/health to answer UP, after a deploy or a rollback.
#   health-check.sh <https://.../actuator/health>
set -eu
url="$1"
for _ in $(seq 1 30); do
  if curl -fsS "$url" | grep -q '"status":"UP"'; then
    echo "$url answers UP"
    exit 0
  fi
  sleep 5
done
echo "$url does not answer UP" >&2
exit 1
