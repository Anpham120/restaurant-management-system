#!/bin/sh
# P0-09: deploys one commit's images on the application server over SSH, for the Jenkinsfile and the rollback job.
# Runs inside sshagent (the deploy user's key), with GH_USER and GH_TOKEN (to pull from GHCR), APP_SERVER_HOST, and
# the compose file and scripts to use in deploy/. deploy.sh on the server lets one deploy run at a time.
#   deploy-remote.sh <folder on the server> <image tag>
set -eu
dir="$1"
tag="$2"
opts="-o StrictHostKeyChecking=accept-new"
scp $opts deploy/docker-compose.prod.yml deploy/backup.sh deploy/restore.sh deploy/deploy.sh \
  "deploy@$APP_SERVER_HOST:$dir/"
printf '%s' "$GH_TOKEN" | ssh $opts "deploy@$APP_SERVER_HOST" "docker login ghcr.io -u $GH_USER --password-stdin"
ssh $opts "deploy@$APP_SERVER_HOST" "sh ~/$dir/deploy.sh $dir $tag; status=\$?; docker logout ghcr.io; exit \$status"
