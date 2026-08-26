#!/usr/bin/env bash
#
# Zelus production backup: MariaDB (all databases) + MongoDB, gzip-compressed,
# with retention-based rotation. Runs against the live Swarm stack via `docker
# exec`, pulling credentials directly from the target containers' own runtime
# environment (already injected by `docker stack deploy` from the
# DOCKER_COMPOSE_ENV secret) -- this script never stores or echoes a password.
#
# Deploy: scp this file to the VPS at /opt/zelus/backup.sh, chmod +x it, then
# add the crontab line at the bottom of this file. Test with a manual run
# first (see README note below) before trusting the cron schedule.

set -euo pipefail

STACK_NAME="${STACK_NAME:-zelus_prod}"
BACKUP_DIR="${BACKUP_DIR:-/opt/zelus/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
LOCKFILE="/tmp/zelus-backup.lock"

# Optional: set to a Discord webhook URL to get pinged on failure only.
# (Reuses the same webhook pattern already used elsewhere in this project --
# see kronos-webhooks -- but this script is standalone, no Java dependency.)
FAILURE_WEBHOOK_URL="${FAILURE_WEBHOOK_URL:-}"

notify_failure() {
    local message="$1"
    echo "[$(date -Iseconds)] FAILURE: ${message}" >&2
    if [[ -n "${FAILURE_WEBHOOK_URL}" ]]; then
        curl -fsS -m 10 -H "Content-Type: application/json" \
            -d "{\"content\": \"🔴 Zelus backup failed: ${message}\"}" \
            "${FAILURE_WEBHOOK_URL}" >/dev/null 2>&1 || true
    fi
}

trap 'notify_failure "backup.sh exited non-zero (line $LINENO)"' ERR

exec 200>"${LOCKFILE}"
if ! flock -n 200; then
    echo "[$(date -Iseconds)] Another backup run is already in progress, skipping." >&2
    exit 0
fi

mkdir -p "${BACKUP_DIR}"

sql_container="$(docker ps --filter "name=${STACK_NAME}_db_sql" --filter "status=running" -q | head -n1)"
mongo_container="$(docker ps --filter "name=${STACK_NAME}_db_mongo" --filter "status=running" -q | head -n1)"

if [[ -z "${sql_container}" ]]; then
    notify_failure "no running ${STACK_NAME}_db_sql container found"
    exit 1
fi
if [[ -z "${mongo_container}" ]]; then
    notify_failure "no running ${STACK_NAME}_db_mongo container found"
    exit 1
fi

echo "[$(date -Iseconds)] Dumping MariaDB (${sql_container})..."
mysql_out="${BACKUP_DIR}/mysql_${TIMESTAMP}.sql.gz"
docker exec "${sql_container}" sh -c \
    'exec mysqldump -uroot -p"$MARIADB_ROOT_PASSWORD" --all-databases --single-transaction --quick --routines --triggers' \
    | gzip -9 > "${mysql_out}.tmp"
mv "${mysql_out}.tmp" "${mysql_out}"

echo "[$(date -Iseconds)] Dumping MongoDB (${mongo_container})..."
mongo_out="${BACKUP_DIR}/mongo_${TIMESTAMP}.archive.gz"
docker exec "${mongo_container}" sh -c \
    'exec mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip' \
    > "${mongo_out}.tmp"
mv "${mongo_out}.tmp" "${mongo_out}"

# Sanity check: neither dump should be suspiciously tiny (an auth failure or
# empty DB still produces gzip's ~20-byte empty-stream header -- catch that
# rather than silently rotating in a worthless backup).
mysql_size=$(stat -c%s "${mysql_out}")
mongo_size=$(stat -c%s "${mongo_out}")
if (( mysql_size < 1024 )); then
    notify_failure "mysql dump suspiciously small (${mysql_size} bytes) -- check credentials/output"
    exit 1
fi
if (( mongo_size < 1024 )); then
    notify_failure "mongo dump suspiciously small (${mongo_size} bytes) -- check credentials/output"
    exit 1
fi

echo "[$(date -Iseconds)] OK: mysql=${mysql_size} bytes, mongo=${mongo_size} bytes"

# --- Optional offsite copy -------------------------------------------------
# Set RCLONE_REMOTE (e.g. "b2:zelus-backups") to also push both files offsite.
# Leave unset to skip -- local rotation below still applies either way.
if [[ -n "${RCLONE_REMOTE:-}" ]] && command -v rclone >/dev/null 2>&1; then
    echo "[$(date -Iseconds)] Pushing to ${RCLONE_REMOTE}..."
    rclone copy "${mysql_out}" "${RCLONE_REMOTE}" --quiet || notify_failure "rclone push of mysql dump failed"
    rclone copy "${mongo_out}" "${RCLONE_REMOTE}" --quiet || notify_failure "rclone push of mongo dump failed"
fi

echo "[$(date -Iseconds)] Rotating backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -maxdepth 1 -name "*.gz" -mtime "+${RETENTION_DAYS}" -print -delete

echo "[$(date -Iseconds)] Backup complete."

# --- Crontab (add via `crontab -e` on the VPS) ------------------------------
# Daily at 03:15 server time, log to a rotating file next to the backups:
#   15 3 * * * STACK_NAME=zelus_prod /opt/zelus/backup.sh >> /opt/zelus/backups/backup.log 2>&1
#
# Test restore periodically (a backup nobody has restored isn't proven):
#   gunzip -c /opt/zelus/backups/mysql_<ts>.sql.gz | docker exec -i <sql_container> sh -c 'mysql -uroot -p"$MARIADB_ROOT_PASSWORD"'
#   docker exec -i <mongo_container> sh -c 'mongorestore --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip' < /opt/zelus/backups/mongo_<ts>.archive.gz
