#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups/config}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
TARGET_DIR="${BACKUP_DIR}/config_${TIMESTAMP}"
mkdir -p "${TARGET_DIR}"

cp -R ./config "${TARGET_DIR}/config"
cp ./docker-compose.yml "${TARGET_DIR}/docker-compose.yml"
cp ./docker-compose.monitoring.yml "${TARGET_DIR}/docker-compose.monitoring.yml"
cp ./pom.xml "${TARGET_DIR}/pom.xml"

tar -czf "${BACKUP_DIR}/config_${TIMESTAMP}.tar.gz" -C "${BACKUP_DIR}" "config_${TIMESTAMP}"
rm -rf "${TARGET_DIR}"

echo "${BACKUP_DIR}/config_${TIMESTAMP}.tar.gz"
