#!/bin/bash
# Division Rivals round 2 (2026-10-15): capes on the Max cape model (matte, no lava), celestial weapons without
# Dismantle (permanent kit), the Overseer wears a baked Champion cape (model 60134) with ops
# Matchmaking / Profile / Rewards Shop / Leaderboard, ladder window 1169 spacing.
# Usage: build_rivals_r2.sh verify|apply <cacheDir> <classesDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateItemVariants "$M-replace" "$C" rivals_items.txt | tail -1
run BakeModelRecolorRetexture bake "$C" 29616 60134 "$W" 668,6,673,8,675,10,784,2,815,12,902,1,945,14,972,18,522,4,4300,10044,4316,10054,5437,10040,5458,10050,4820,9040,5683,9020,5706,9030,5708,9032,5714,9038 - | tail -1
run InsertRawModels "$M" "$C" "$W" | tail -1
if [ "$M" = apply ]; then
  run SetNpcModelListInPlace apply "$C" 3351 35349,35359,35356,60134,13307,27638,27649 | tail -1
else
  echo "(npc model list applied after model 60134 exists)"
fi
run EditConfigNameActions "$M" "$C" npc 3351 "name=Rivals Overseer" 0=Matchmaking 1=Profile "2=Rewards Shop" 3=Leaderboard | tail -1
run BuildChampionLadderInterface "$([ "$M" = apply ] && echo rebuild || echo verify)" "$C" | tail -1
echo "BUILD $M OK"
