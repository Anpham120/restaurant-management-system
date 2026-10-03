#!/bin/sh
# P0-07, P0-09: checks the monitoring configs with the image versions the compose files pin: the alert rules and
# their tests, the Prometheus and Alertmanager configs, the Alloy config and the Grafana dashboards. Run from the
# repository root by the Jenkinsfile (and by ci-cd.yml while it stays). The files reach each container through stdin
# rather than a mount, because under Jenkins the Docker daemon does not see the workspace.
set -eu

image() { grep -m1 "image: $1:" "$2" | awk '{print $2}' | tr -d '\r'; }
prometheus=$(image prom/prometheus deploy/ops/docker-compose.yml)
alertmanager=$(image prom/alertmanager deploy/ops/docker-compose.yml)
alloy=$(image grafana/alloy deploy/agent/docker-compose.yml)

# Runs a shell command as root in an image, in a folder holding this checkout's deploy/.
run() {
  img="$1"
  shift
  tar -cf - deploy | docker run -i --rm -u 0 --entrypoint sh "$img" \
    -c "mkdir -p /tmp/check && cd /tmp/check && tar -xf - && $*"
}

echo "== Alert rules and their tests"
run "$prometheus" 'promtool check rules deploy/ops/prometheus/rules/khoibep.yml &&
  cd deploy/ops/prometheus/tests && promtool test rules khoibep_test.yml'

echo "== Prometheus config, as start.sh writes it"
run "$prometheus" 'cp -r deploy/ops/prometheus/. /etc/prometheus/ &&
  SITE_URL_PRODUCTION=https://example.com/actuator/health SITE_URL_STAGING=https://staging.example.com/actuator/health \
  OPS_NODE_EXPORTER=node-exporter:9100 sh /etc/prometheus/start.sh check'

echo "== Alertmanager config"
run "$alertmanager" 'mkdir -p /etc/alertmanager && cp deploy/ops/alertmanager/* /etc/alertmanager/ &&
  sed -e "s|__TELEGRAM_BOT_TOKEN__|123456789:ci|" -e "s|__TELEGRAM_CHAT_ID__|-1001|" \
    /etc/alertmanager/alertmanager.yml > /tmp/alertmanager.yml &&
  amtool check-config /tmp/alertmanager.yml'

echo "== Alloy config"
run "$alloy" '/bin/alloy validate deploy/agent/config.alloy'

echo "== Grafana dashboards are valid JSON"
jq empty deploy/ops/grafana/dashboards/*.json
echo "All monitoring configs are fine"
