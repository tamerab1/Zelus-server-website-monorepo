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

# 7. Default environment variables - memory limits are deferred to compose
ENV KRONOS_BOOT_OPTS="-Xmx8g"

# Switch to the non-root user for execution
USER zelus

# Expose the default game server port
EXPOSE 43594

ENTRYPOINT ["/entrypoint.sh"]
CMD ["bin/kronos-boot"]