#!/usr/bin/env bash
set -euo pipefail

SERVICE_TO_FAIL="${1:-service-registry-1}"
RECOVERY_WAIT_SECONDS="${RECOVERY_WAIT_SECONDS:-20}"

docker compose stop "${SERVICE_TO_FAIL}"
sleep "${RECOVERY_WAIT_SECONDS}"
docker compose up -d "${SERVICE_TO_FAIL}"

curl -fsS http://localhost:8080/actuator/health > /dev/null
curl -fsS http://localhost:8081/actuator/health > /dev/null
curl -fsS http://localhost:8082/actuator/health > /dev/null
curl -fsS http://localhost:8083/actuator/health > /dev/null

echo "Failover drill completed for ${SERVICE_TO_FAIL}"
