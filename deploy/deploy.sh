#!/bin/sh
# P0-08, P0-09: runs one commit's images on the application server, for the Jenkinsfile and the rollback job
# (docs-core/09 section 9.6). Both copy this file next to docker-compose.prod.yml, log in to GHCR, then call:
#   sh ~/<folder>/deploy.sh <folder> <image tag>        e.g. deploy.sh khoibep-rms-staging 3f2c1d...
# Only one deploy runs at a time on this server, whoever starts it; a tag that already runs is left alone.
set -eu

folder="$1"
tag="$2"

exec 9>"$HOME/.khoibep-deploy.lock"
if ! flock -n 9; then
  echo "Another deploy is running, waiting for it to finish"
  flock 9
fi

cd "$HOME/$folder"
if [ "$(cat .deployed 2>/dev/null || true)" = "$tag" ]; then
  echo "$folder already runs $tag, nothing to do"
  exit 0
fi

export IMAGE_TAG="$tag"
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
echo "$tag" > .deployed
docker image prune -f
echo "$folder now runs $tag"
