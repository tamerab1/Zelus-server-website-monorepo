# 1. Base image with a pinned JDK version for reproducible builds
FROM eclipse-temurin:24-jdk-noble

# 2. Build argument to toggle the profiler - defaults to false for production safety
ARG ENABLE_PROFILER=false

# 3. Install required packages and create a non-root system user for security hardening
RUN apt-get update && apt-get install -y --no-install-recommends \
    unzip \
    wget \
    ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -m -u 10001 zelus

WORKDIR /app

# 4. Conditional installation of YourKit profiler based on the build argument.
# Whether it actually attaches at runtime is decided by entrypoint.sh (step 6),
# based on whether the agent file is present on disk.
RUN if [ "$ENABLE_PROFILER" = "true" ] ; then \
    wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2025.3-docker.zip -P /tmp/ && \
    unzip /tmp/YourKit-JavaProfiler-2025.3-docker.zip -d /usr/local && \
    rm /tmp/YourKit-JavaProfiler-2025.3-docker.zip ; \
    fi

# 5. Copy pre-built Gradle application artifacts (installDist output)
# Ownership is set directly during copy to save an image layer (Optimized Nit #3)
COPY --chown=zelus:zelus kronos-boot/build/install/kronos-boot /app

# 6. Entrypoint that conditionally attaches the YourKit agent at container
# start, based on whether it's actually present on disk. ENV can't branch on
# ARG at build time (RUN `export` doesn't persist into the image either), so
# the check has to happen at runtime instead.
COPY --chown=zelus:zelus docker/server/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 7. Default environment variables - memory limits are deferred to compose.
# This ENV is only what actually runs when NO environment: override is present
# at all (e.g. a bare `docker run`) -- both docker-compose.yml and
# docker-compose-prod.yml set KRONOS_BOOT_OPTS explicitly and always win over
# this, per normal Docker ENV-vs-environment: precedence. Do not treat this
# value as "what's live in prod" -- check the actual deployed stack instead:
#   docker service inspect zelus_prod_game_server --format '{{json .Spec.TaskTemplate.ContainerSpec.Env}}'
#
# -Xms == -Xmx: avoids heap-resize pauses as the process ramps up to steady
# state (a real G1 pause source, not just a tuning nicety).
# -XX:+HeapDumpOnOutOfMemoryError: previously missing entirely -- an OOM died
# with no artifact to diagnose it afterwards. Written to /data (survives
# container restart) same as the gc log below.
# GC pause logging: written into the /data bind mount (docker-compose-prod.yml maps
# /opt/zelus/data:/data) so it survives container restarts and is inspectable via SSH
# without needing `docker service logs`. Rotates at 20M x5 files (~100M cap) so it can't
# grow unbounded. Added to investigate a suspected periodic GC-pause stall (see
# CoreWorker.savePlayers()) -- correlate any multi-second pause here against player
# reports of ground items/inventory/equipped-appearance going briefly invisible.
ENV KRONOS_BOOT_OPTS="-Xms3200m -Xmx3200m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/data/heapdump.hprof -XX:MaxGCPauseMillis=200 -Xlog:gc*:file=/data/gc.log:time,uptime,level,tags:filecount=5,filesize=20M"

# Switch to the non-root user for execution
USER zelus

# Expose the default game server port
EXPOSE 43594

ENTRYPOINT ["/entrypoint.sh"]
CMD ["bin/kronos-boot"]