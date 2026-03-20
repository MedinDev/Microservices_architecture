#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULT_DIR="${ROOT_DIR}/results"
mkdir -p "${RESULT_DIR}"

SCENARIO="${1:-baseline}"

case "${SCENARIO}" in
  baseline)
    k6 run "${ROOT_DIR}/phase9-baseline.js" --summary-export "${RESULT_DIR}/baseline-summary.json"
    ;;
  peak)
    k6 run "${ROOT_DIR}/phase9-peak-load.js" --summary-export "${RESULT_DIR}/peak-summary.json"
    ;;
  black-friday)
    k6 run "${ROOT_DIR}/phase9-black-friday.js" --summary-export "${RESULT_DIR}/black-friday-summary.json"
    ;;
  gradual-ramp)
    k6 run "${ROOT_DIR}/phase9-gradual-ramp.js" --summary-export "${RESULT_DIR}/gradual-ramp-summary.json"
    ;;
  spike)
    k6 run "${ROOT_DIR}/phase9-spike.js" --summary-export "${RESULT_DIR}/spike-summary.json"
    ;;
  *)
    echo "Unsupported scenario: ${SCENARIO}"
    exit 1
    ;;
esac
