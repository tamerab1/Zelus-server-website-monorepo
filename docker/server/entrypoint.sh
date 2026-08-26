#!/bin/sh
set -e

YOURKIT_AGENT="/usr/local/YourKit-JavaProfiler-2025.3/bin/linux-x86-64/libyjpagent.so"

if [ -f "$YOURKIT_AGENT" ]; then
    export JAVA_TOOL_OPTIONS="-agentpath:${YOURKIT_AGENT}=port=10001,listen=all"
fi

exec "$@"
