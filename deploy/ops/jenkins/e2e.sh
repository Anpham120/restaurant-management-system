#!/bin/sh
# P0-09: the acceptance scenario (e2e/) against the two images this build just made, from the Jenkinsfile. The stack
# runs under its own Compose project with no port on the host (e2e.yml), and is removed afterwards. Needs
# GHCR_OWNER, GIT_COMMIT and BUILD_TAG, which Jenkins sets.
set -eu
project="e2e-$(printf '%s' "$BUILD_TAG" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9' '-')"
export IMAGE_TAG="$GIT_COMMIT"
compose() { docker compose -p "$project" -f docker-compose.yml -f deploy/ops/jenkins/e2e.yml "$@"; }
trap 'compose down -v --remove-orphans' EXIT
compose up -d --no-build

echo "Waiting for the app"
up=false
for _ in $(seq 1 60); do
  if compose exec -T frontend wget -qO- http://localhost/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    up=true
    break
  fi
  sleep 5
done
if [ "$up" != true ]; then
  compose logs --no-color --tail 100 backend
  echo "The app did not start" >&2
  exit 1
fi

# Playwright's own image for the version in package-lock.json, so the browsers match the test library.
playwright=$(jq -r '.packages["node_modules/@playwright/test"].version' e2e/package-lock.json)
sh deploy/ops/jenkins/in-container.sh "mcr.microsoft.com/playwright:v$playwright-noble" e2e \
  "npm ci --no-audit --no-fund && npx playwright test" \
  --network "${project}_default" -e CI=true -e E2E_BASE_URL=http://frontend -v khoibep-npm:/root/.npm
