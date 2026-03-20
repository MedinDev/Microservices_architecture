#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <service: order|payment|notification> <backup_file.sql.gz>"
  exit 1
fi

SERVICE="$1"
BACKUP_FILE="$2"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "Backup file not found: ${BACKUP_FILE}"
  exit 1
fi

case "${SERVICE}" in
  order)
    CONTAINER="postgres-order"
    USER="order_user"
    DB="order_db"
    ;;
  payment)
    CONTAINER="postgres-payment"
    USER="payment_user"
    DB="payment_db"
    ;;
  notification)
    CONTAINER="postgres-notification"
    USER="notification_user"
    DB="notification_db"
    ;;
  *)
    echo "Unsupported service: ${SERVICE}"
    exit 1
    ;;
esac

gzip -dc "${BACKUP_FILE}" | docker exec -i "${CONTAINER}" psql -U "${USER}" -d "${DB}"
echo "Restore completed for ${SERVICE}"
