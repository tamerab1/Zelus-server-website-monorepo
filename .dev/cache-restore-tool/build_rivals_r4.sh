#!/usr/bin/env bash
# Division Rivals round 4 (2026-10-15): both capes as untextured recolours with fold contrast (the Champion's velvet
# texture 220 / models 60135-60136 are no longer used), the Overseer's baked cape 60134 rebaked to match, cape options
# re-applied after the record rebuild, and the 4-bracket matchmaking window 1172.
# Usage: build_rivals_r4.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateItemVariants "$M-replace" "$C" rivals_items.txt | tail -1
run SetItemWornOps "$M" "$C" rivals_capes_ops.txt
run BakeModelRecolorRetexture rebake "$C" 60135 60134 "$W" 668,6,673,8,675,10,784,2,815,12,902,1,945,14,972,18,522,4,4300,10044,4316,10054,5437,10040,5458,10050,4820,9040,5683,9020,5706,9030,5708,9032,5714,9038 - | tail -1
run InsertRawModels "$([ "$M" = apply ] && echo replace || echo replace-verify)" "$C" "$W" | tail -1
run BuildRivalsMatchmakingInterface "$([ "$M" = apply ] && echo rebuild || echo verify)" "$C" | tail -1
echo "BUILD $M OK"
