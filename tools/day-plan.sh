#!/usr/bin/env bash
# Runs a multi-office day on the phone through OP Mock Location.
# Usage:  ./day-plan.sh plan.txt [seconds-per-minute]
#   seconds-per-minute: 60 = real time (default), 1 = use with Office Presence Developer mode
# plan.txt lines:  <office name | travel | out>, <minutes>
# Start any mock once from the app first (Android blocks a background start).
set -euo pipefail
PLAN="${1:?plan file needed}"
SPEED="${2:-60}"
CMD="adb shell am broadcast -a com.pwc.mockpresence.CMD"

while IFS=, read -r where mins || [[ -n "${where:-}" ]]; do
  where="$(echo "${where:-}" | xargs)"; mins="$(echo "${mins:-}" | xargs)"
  [[ -z "$where" || "$where" == \#* ]] && continue
  case "$where" in
    travel|out) $CMD --es cmd outside --es meters 2000 > /dev/null ;;
    *)          $CMD --es cmd inside --es office "'$where'" > /dev/null ;;
  esac
  echo "$(date +%H:%M:%S)  $where for $mins min"
  sleep "$(awk "BEGIN { print $mins * $SPEED }")"
done < "$PLAN"

$CMD --es cmd stop > /dev/null
echo "$(date +%H:%M:%S)  done, real GPS back"
