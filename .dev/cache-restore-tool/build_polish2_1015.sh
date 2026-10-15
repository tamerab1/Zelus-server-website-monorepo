#!/bin/bash
# 2026-10-15 polish round 2: Teleport Nexus 1152 (recessed selection), Relic Forge 1158 (2-line names, taller tabs),
# Scoreboard 1163 (dark bronze active state, full-width 16px table), Titles 1160 (dark bronze trim). Ids unchanged.
# Usage: build_polish2_1015.sh verify|rebuild <cacheDir> <classesDir> <classpath>. Stops on the first failure.
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
run BuildTeleportInterface "$M" "$C"
run BuildRelicForgeInterface "$M" "$C"
run BuildScoreboardInterface "$M" "$C"
run BuildTitlesInterface "$M" "$C"
echo "BUILD $M OK"
