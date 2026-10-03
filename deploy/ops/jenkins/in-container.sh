#!/bin/sh
# P0-09: runs one test step of the Jenkinsfile in a fresh container, on the files of this commit. Jenkins drives the
# server's Docker through its socket, so the containers it starts cannot see the Jenkins workspace: the committed
# tree goes in through stdin (git archive) and is unpacked in /src. Usage:
#   in-container.sh <image> <folder under /src> <command> [docker run options...]
set -eu
image="$1"
dir="$2"
command="$3"
shift 3
git archive --format=tar HEAD | docker run -i --rm "$@" --entrypoint sh "$image" \
  -c "mkdir -p /src && tar -xf - -C /src && cd /src/$dir && $command"
