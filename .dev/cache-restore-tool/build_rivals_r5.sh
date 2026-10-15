#!/usr/bin/env bash
# Division Rivals round 5 (2026-10-15): textures back on both capes -- the Challenger as originally built (Fire cape
# clone, animated lava texture 218), the Champion on the velvet models 60135/60136 (texture 220) -- the Overseer's cape
# 60134 rebaked from 60135 so it matches, cape options re-applied after the record rebuild, and 1172 with the cards'
# "Configure layout" option.
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
