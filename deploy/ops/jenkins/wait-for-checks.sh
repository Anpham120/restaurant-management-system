#!/bin/sh
# P0-08: waits until every required GitHub Actions job has passed on this commit, from the Jenkinsfile.
# Needs REPO, GIT_COMMIT and REQUIRED_CHECKS (job names separated by |); GH_TOKEN raises the API rate limit.
# Only the newest run of each job counts, so a job that failed once and passed on re-run is a pass.
set -eu

api="https://api.github.com/repos/$REPO/commits/$GIT_COMMIT/check-runs?per_page=100"

while :; do
  if [ -n "${GH_TOKEN:-}" ]; then
    runs=$(curl -fsS -H "Accept: application/vnd.github+json" -H "Authorization: Bearer $GH_TOKEN" "$api")
  else
    runs=$(curl -fsS -H "Accept: application/vnd.github+json" "$api")
  fi
  verdict=$(printf '%s' "$runs" | jq -r --arg names "$REQUIRED_CHECKS" '
    ($names | split("|")) as $required
    | [.check_runs[] | select(.name as $name | $required | index($name))]
    | group_by(.name) | map(max_by(.id)) as $latest
    | if any($latest[]; .status == "completed" and .conclusion != "success") then "failed"
      elif ([$latest[] | select(.conclusion == "success")] | length) == ($required | length) then "passed"
      else "waiting" end')
  case "$verdict" in
    passed)
      echo "Every required check passed on GitHub"
      exit 0 ;;
    failed)
      echo "A required check failed on GitHub: not deploying this commit" >&2
      exit 1 ;;
  esac
  echo "Waiting for the tests on GitHub..."
  sleep 20
done
