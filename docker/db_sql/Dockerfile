FROM mariadb:noble

COPY docker/db_sql/docker-entrypoint-initdb.d/ /docker-entrypoint-initdb.d/
COPY docker/db_sql/docker-entrypoint.sh /usr/local/bin/
# Explicit, regardless of the source file's own mode: COPY preserves
# whatever permission bit the build context had, and git's tracked mode for
# this file drifted to non-executable at some point (100644) without
# anyone noticing, since this image had never actually been built by CI
# until now. That silently overwrote the base image's own executable
# entrypoint script at this exact path with a non-executable one --
# "executable file not found in $PATH" at container start, on a real Linux
# build only (Windows/Docker Desktop's build-context packaging masked it
# locally). This RUN makes the image correct no matter what mode the
# source file carries on any future checkout.
RUN chmod +x /usr/local/bin/docker-entrypoint.sh