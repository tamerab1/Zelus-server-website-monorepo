#!/usr/bin/env bash
# Scoreboard Exchange (2026-10-21): interface 1179 (BuildScoreboardExchangeInterface, compiled to out_ui) and the
# Scoreboard Quartermaster npc 30610, a clone of the Rivals Overseer 3351 (stand 808 / walk 819) dressed in:
# Bunnyman mask 23448 (worn 21860 / chathead 37256, its 2378 -> 90 item recolour baked: models 61334 / 61335),
# Epic cape 59532 (59705), Clown bow tie 22692 (28479), Clown gown 22695 (36025, has sleeves: no arm kit), Clown
# trousers 22698 (36024), Clown shoes 22701 (36026) and Barrows gloves 7462 (13307). The mask covers the head, so no
# hair / jaw kits. 3351's npc-level recolour is replaced with a no-op pair. Ops: Talk-to / Trade (server:
# io.ruin.model.activities.scoreboard.ScoreboardExchange). Spawn 3085,3497 facing E (data/npcs/spawns).
# Run with the server STOPPED. Usage: build_scoreboard_exchange.sh verify|apply <cachePath> <bakedModelsDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; D="$3"; CP="$4"
run() { java -cp "out_ui;out_free;out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J" | tail -1; }
mkdir -p "$D"
run BuildScoreboardExchangeInterface "$M" "$C"
run BakeModelRecolorRetexture bake "$C" 21860 61334 "$D" 2378,90 -
run BakeModelRecolorRetexture bake "$C" 37256 61335 "$D" 2378,90 -
run InsertRawModels "$M" "$C" "$D"
run SpliceNewNpc "$M" "$C" 3351 30610 "Scoreboard Quartermaster" 61334
[ "$M" = verify ] && { echo "VERIFY: later steps need the npc; run them on a scratch copy with apply"; exit 0; }
run SetNpcModelListInPlace "$M" "$C" 30610 61334,59705,28479,36025,36024,36026,13307
run SetNpcModelListInPlace "$M" "$C" 30610 61335 chathead
run EditNpcRecolorInPlace "$M" "$C" 30610 1,1
run SetNpcActionsInPlace "$M" "$C" 30610 "Talk-to,Trade,-,-,-"
# same session: the PK Hub tab icon in the resizable-modern frame sat 8px low (y-centred comp with the fixed-mode +8)
run SetIfComponentY "$M" "$C" 164 45 0
echo "BUILD $M OK"
