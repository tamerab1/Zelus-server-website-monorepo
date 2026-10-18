#!/usr/bin/env bash
# Zelus Commands (2026-10-18, read-only round 2; rebuilds 1177 in place): interface 1177 for ::commands / ::help (BuildCommandCodexInterface). The server half is
# io.ruin.model.inter.handlers.CommandCodex (registered in StaticInit, opened by CommandHandlerRegular "commands"/"help").
# Run with the server STOPPED. Usage: build_command_codex.sh apply <cachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
java -cp "out_soultree;out;out_whips;$CP" BuildCommandCodexInterface rebuild "$C" 2>&1 | grep -v SLF4J | tail -1
echo "BUILD command codex apply OK"
