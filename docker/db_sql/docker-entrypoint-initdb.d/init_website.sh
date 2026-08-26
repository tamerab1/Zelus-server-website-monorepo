#!/bin/bash
# Zelus Website API schema — separate from `reason` (the game's own DB).
# FastAPI's database.py connects here via DB_HOST/DB_USER/DB_PASS/DB_NAME.
#
# Deliberately self-sufficient — does NOT depend on the official MariaDB
# entrypoint's docker_process_sql() helper. The entrypoint only exposes that
# function to init scripts it *sources* (non-executable .sh files), not
# ones it forks as a subprocess (executable .sh files) — and whether this
# file ends up executable depends on how the build context's file mode gets
# packed, which is not reliable across platforms (confirmed differing
# locally on Windows/Docker Desktop vs. what git itself tracks, 100644, so
# this one gets sourced on a real Linux checkout). A raw .sql file has the
# opposite problem — no $WEBSITE_DB_PASSWORD substitution at all, since
# docker_process_sql just pipes .sql files to the client verbatim. Invoking
# mariadb directly here works regardless of sourced vs. executed.
#
# Being sourced (not executed) is exactly why `set -euo pipefail` and the
# actual work below are wrapped in a subshell: sourcing runs in the SAME
# shell as the parent docker-entrypoint.sh, so a bare `set -euo pipefail`
# at this file's top level would persist into the rest of THAT script's
# execution after this file returns — e.g. docker_temp_server_stop()'s
# `wait "$MARIADB_PID"` on a just-killed process commonly returns non-zero,
# which would then abort the entrypoint under a leaked `set -e`. The
# subshell scopes strict mode to just this file's own work; the exit-code
# check afterward still hard-fails the whole init on a real error.
#
# WEBSITE_DB_PASSWORD must be present in the container environment — set via
# docker-compose.yml's db_sql service, sourced from docker-compose.env.
#
# MYSQL_PWD + --protocol=socket -uroot -hlocalhost matches exactly how the
# entrypoint's own docker_exec_client() authenticates during init (the temp
# server runs with --skip-networking, so TCP isn't available at this stage —
# the socket is the only option). Socket path confirmed via this image's own
# default `socket` system variable value, not guessed.

if ! (
	set -euo pipefail

	if [ -z "${WEBSITE_DB_PASSWORD:-}" ]; then
		echo >&2 "init_website.sh: WEBSITE_DB_PASSWORD is not set — cannot create the website_api DB user"
		exit 1
	fi

	MYSQL_PWD="${MARIADB_ROOT_PASSWORD}" mariadb --protocol=socket -uroot -hlocalhost --socket=/run/mysqld/mysqld.sock <<-EOSQL
		CREATE DATABASE IF NOT EXISTS \`zelusrsps_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

		CREATE USER IF NOT EXISTS 'website_api'@'%' IDENTIFIED BY '${WEBSITE_DB_PASSWORD}';
		GRANT ALL PRIVILEGES ON \`zelusrsps_db\`.* TO 'website_api'@'%';

		-- Read-only access to the game's own schema, for hiscores queries
		-- (game_database.py / hs_users) — no write access needed or granted.
		GRANT SELECT ON \`reason\`.* TO 'website_api'@'%';

		FLUSH PRIVILEGES;
	EOSQL
); then
	echo >&2 "init_website.sh: failed — aborting container init"
	exit 1
fi
