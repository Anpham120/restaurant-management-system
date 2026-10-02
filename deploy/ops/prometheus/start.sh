#!/bin/sh
# P0-07: Prometheus reads no environment variables in its config file. Write the two site addresses from .env into
# a copy, then run Prometheus on that copy. Metrics are kept 15 days; the agent pushes through remote write.
set -eu
sed -e "s|__SITE_URL_PRODUCTION__|${SITE_URL_PRODUCTION}|" -e "s|__SITE_URL_STAGING__|${SITE_URL_STAGING}|" \
  /etc/prometheus/prometheus.yml > /prometheus/prometheus.yml
exec /bin/prometheus \
  --config.file=/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention.time=15d \
  --web.enable-remote-write-receiver \
  --web.enable-lifecycle
