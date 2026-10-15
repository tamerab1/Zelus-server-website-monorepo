#!/bin/bash
# 2026-10-15 polish sprint: Workbench 1153 (Add minimum bezel button), Relic Forge 1158 (bezel tabs, recessed selection,
# roomier cards), Titles 1160 (bezel tabs, recessed selection, card bevels). All rebuilt in place; ids unchanged.
# Usage: build_polish_1015.sh verify|rebuild <cacheDir> <classesDir> <classpath>. Stops on the first failure.
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
run BuildWorkbenchInterface "$M" "$C"
run BuildRelicForgeInterface "$M" "$C"
run BuildTitlesInterface "$M" "$C"
echo "BUILD $M OK"
