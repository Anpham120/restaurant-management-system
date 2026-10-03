#!/bin/sh
# P0-09: prints the backend's line coverage from JaCoCo's CSV report, after ./mvnw verify (the Jenkinsfile).
set -eu
awk -F, 'NR > 1 { missed += $8; covered += $9 }
  END { printf "Độ phủ backend (JaCoCo): %.1f%% số dòng (%d/%d), tối thiểu 70%%\n",
        100 * covered / (missed + covered), covered, missed + covered }' "${1:-target/site/jacoco/jacoco.csv}"
