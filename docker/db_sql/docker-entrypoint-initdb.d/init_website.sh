#!/bin/bash
# Zelus Website API schema — separate from `reason` (the game's own DB).
# FastAPI's database.py connects here via DB_HOST/DB_USER/DB_PASS/DB_NAME.
#
# NOT executable on purpose: the official MariaDB entrypoint (see
# docker-entrypoint.sh:73-87) *sources* non-executable .sh files instead of
# forking them, which is what gives this script access to the entrypoint's
# own docker_process_sql() helper and the already-exported MARIADB_* env —
# a raw .sql file here would NOT get $WEBSITE_DB_PASSWORD substituted at all
# (docker_process_sql just pipes .sql files to the mysql client verbatim).
#
# WEBSITE_DB_PASSWORD must be present in the container environment — set via
# docker-compose.yml's db_sql service, sourced from docker-compose.env.

set -euo pipefail

if [ -z "${WEBSITE_DB_PASSWORD:-}" ]; then
	mysql_error "WEBSITE_DB_PASSWORD is not set — cannot create the website_api DB user"
fi

docker_process_sql --database=mysql <<-EOSQL
	CREATE DATABASE IF NOT EXISTS \`zelusrsps_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

	CREATE USER IF NOT EXISTS 'website_api'@'%' IDENTIFIED BY '${WEBSITE_DB_PASSWORD}';
	GRANT ALL PRIVILEGES ON \`zelusrsps_db\`.* TO 'website_api'@'%';

	-- Read-only access to the game's own schema, for hiscores queries
	-- (game_database.py / hs_users) — no write access needed or granted.
	GRANT SELECT ON \`reason\`.* TO 'website_api'@'%';

	FLUSH PRIVILEGES;
EOSQL
