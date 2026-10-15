#!/bin/bash
# Division Rivals batch (2026-10-15): cape textures 218/219, shop items 31780-31793, celestial spec enums, Rivals
# Overseer (npc 3351, ex-Jeed), Rivals shop interface 1171, matchmaking interface 1172.
# Usage: build_rivals_batch.sh verify|apply <cacheDir> <classesDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
IFM=$([ "$M" = apply ] && echo apply || echo verify)
run CreateRecoloredTextures "$M" "$C" rivals_textures.txt | tail -1
run CreateItemVariants "$M" "$C" rivals_items.txt | tail -1
run AppendEnumEntries "$M" "$C" rivals_spec_enums.txt | tail -1
run SetNpcModelListInPlace "$M" "$C" 3351 35349,35359,35356,33103,13307,27638,27649 | tail -1
run SetNpcModelListInPlace "$M" "$C" 3351 35733 chathead | tail -1
run EditConfigNameActions "$M" "$C" npc 3351 "name=Rivals Overseer" 0=Matchmaking 1=- "2=Rewards Shop" 3=Leaderboard | tail -1
run SetNpcAnimInPlace "$M" "$C" 3351 808 819 | tail -1
run BuildRivalsShopInterface "$IFM" "$C" | tail -1
run BuildRivalsMatchmakingInterface "$IFM" "$C" | tail -1
echo "BUILD $M OK"
