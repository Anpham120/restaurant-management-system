#!/bin/sh
# P0-08: does GitHub Actions deploy this push, or did Jenkins (docs-core/09 section 9.6)?
# Jenkins is the primary deployer and reports on the commit with the status "jenkins/deploy". Actions takes over
# when Jenkins is not set up, does not answer, or says nothing about the commit for 5 minutes. A failure reported
# by Jenkins is a real failure: Actions does not deploy over it.
# Needs GITHUB_REPOSITORY, GITHUB_SHA, GITHUB_OUTPUT, GITHUB_API_URL, GH_TOKEN (set by GitHub Actions) and
# JENKINS_URL (repository variable, empty without Jenkins). Optional: QUIET_LIMIT, PENDING_LIMIT, POLL (seconds).
set -eu

quiet_limit="${QUIET_LIMIT:-300}"
pending_limit="${PENDING_LIMIT:-5400}"
poll="${POLL:-20}"

decide() {
  echo "takeover=$1" >> "$GITHUB_OUTPUT"
  echo "reason=$2" >> "$GITHUB_OUTPUT"
  echo "$2"
  exit "${3:-0}"
}

if [ -z "${JENKINS_URL:-}" ]; then
  decide true "Chưa cấu hình Jenkins (biến JENKINS_URL): GitHub Actions deploy"
fi

waited=0
while :; do
  if ! curl -fsS -o /dev/null --max-time 10 "$JENKINS_URL/login"; then
    decide true "Jenkins không trả lời ở $JENKINS_URL: GitHub Actions deploy thay"
  fi
  # Newest first: the first "jenkins/deploy" entry is the current state of that context.
  state=$(curl -fsS --max-time 10 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
    "$GITHUB_API_URL/repos/$GITHUB_REPOSITORY/commits/$GITHUB_SHA/statuses?per_page=100" \
    | jq -r '[.[] | select(.context == "jenkins/deploy")][0].state // ""')
  case "$state" in
    success)
      decide false "Jenkins đã deploy commit này" ;;
    failure | error)
      decide false "Jenkins báo lỗi khi deploy commit này: xem build trên Jenkins" 1 ;;
    pending)
      if [ "$waited" -ge "$pending_limit" ]; then
        decide false "Jenkins vẫn đang chạy sau $((waited / 60)) phút: không deploy chồng"
      fi ;;
    *)
      if [ "$waited" -ge "$quiet_limit" ]; then
        decide true "Jenkins im lặng $((waited / 60)) phút về commit này: GitHub Actions deploy thay"
      fi ;;
  esac
  sleep "$poll"
  waited=$((waited + poll))
done
