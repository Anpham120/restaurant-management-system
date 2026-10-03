#!/bin/sh
# P0-07, P0-09: Prometheus reads no environment variables in its config file. Write the two site addresses from .env
# into a copy, and the target list of this server's node exporter: none when OPS_NODE_EXPORTER is empty (one server,
# where the agent already reads the machine). Then run Prometheus on the copy; metrics are kept 15 days and the
# agent pushes through remote write. "start.sh check" checks the copy instead (scripts/check-monitoring.sh).
set -eu
sed -e "s|__SITE_URL_PRODUCTION__|${SITE_URL_PRODUCTION}|" -e "s|__SITE_URL_STAGING__|${SITE_URL_STAGING}|" \
  /etc/prometheus/prometheus.yml > /prometheus/prometheus.yml
mkdir -p /prometheus/targets
if [ -n "${OPS_NODE_EXPORTER:-}" ]; then
  printf '%s\n' "- targets: [\"$OPS_NODE_EXPORTER\"]" "  labels: {server: ops, instance: ops}" \
    > /prometheus/targets/ops-node.yml
else
  echo '[]' > /prometheus/targets/ops-node.yml
fi
if [ "${1:-}" = check ]; then
  exec promtool check config /prometheus/prometheus.yml
fi
exec /bin/prometheus \
  --config.file=/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention.time=15d \
  --web.enable-remote-write-receiver \
  --web.enable-lifecycle
