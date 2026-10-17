#!/usr/bin/env bash
# Division Rivals Herald (2026-10-17): npc 30608, a clone of the Rivals Overseer 3351, dressed in the Zelus Partyhat
# (model 60148), baked Zelus Relic coat / legs (61187 / 61188, the items' recolours baked in) and a baked Rivals
# Challenger cape (61189), Eternal boots (or) (59385), plus male hair / jaw / hand kits (230 / 249 / 176); the coat has
# its own sleeves (+-38 wide), so no arm kit. 3351's npc-level recolour (4550 -> 6705 ...) would hit the coat, so it is
# replaced with a no-op pair. Ops: Talk-to / Explain Rewards / Quick-Guide (RivalsHerald on the server).
# Run with the server STOPPED. Usage: build_rivals_herald.sh apply <cachePath> <bakedModelsDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; D="$3"; CP="$4"
run() { java -cp "out_whips;out;$CP" "$@" 2>&1 | grep -v "SLF4J" | tail -1; }
run InsertRawModels "$M" "$C" "$D"
run SpliceNewNpc "$M" "$C" 3351 30608 "Division Rivals Herald" 61187
run SetNpcModelListInPlace "$M" "$C" 30608 230,249,60148,61187,61188,61189,176,59385
run TextureModelFaces "$C" herald_head_models.txt "$D"  # chathead partyhat 29 -> 61190 (texture 226), inserted with the models above
run SetNpcModelListInPlace "$M" "$C" 30608 63,81,61190 chathead
run EditNpcRecolorInPlace "$M" "$C" 30608 1,1
run SetNpcActionsInPlace "$M" "$C" 30608 "Talk-to,Explain Rewards,Quick-Guide,-,-"
echo "BUILD $M OK"
