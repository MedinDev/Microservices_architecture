#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups/postgres}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
mkdir -p "${BACKUP_DIR}"

backup_db() {
  local container="$1"
  local user="$2"
  local db="$3"
  local output_file="${BACKUP_DIR}/${db}_${TIMESTAMP}.sql.gz"
  docker exec "${container}" pg_dump -U "${user}" -d "${db}" | gzip > "${output_file}"
  echo "${output_file}"
}

ORDER_BACKUP="$(backup_db postgres-order order_user order_db)"
PAYMENT_BACKUP="$(backup_db postgres-payment payment_user payment_db)"
NOTIFICATION_BACKUP="$(backup_db postgres-notification notification_user notification_db)"

printf "%s\n%s\n%s\n" "${ORDER_BACKUP}" "${PAYMENT_BACKUP}" "${NOTIFICATION_BACKUP}" > "${BACKUP_DIR}/latest-backups.txt"
echo "Backups written under ${BACKUP_DIR}"
