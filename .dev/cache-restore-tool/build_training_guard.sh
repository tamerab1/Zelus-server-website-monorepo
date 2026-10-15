#!/usr/bin/env bash
# The ::train area's Training Guard, npc 20500 (2026-10-15): a Bandit 690 clone wearing a Black santa hat 13343,
# Steel platebody (t) 20184, Steel platelegs (t) 20187, Steel kiteshield (t) 20196, Steel scimitar 1325, Steel boots
# 4123 and Steel gloves 7456. The items' recolours are baked into model copies 60142, 60143, 60145, 60146 and 60147
# (training_guard_models/, from BakeModelRecolorRetexture); the sleeves 164, boots 4954 and scimitar 490 are used as-is.
# Server data: npcs/combat/Training_guard.json, drops/newDrops/20500.json, spawns/TrainGuards.json.
# Usage: build_training_guard.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run InsertRawModels "$M" "$C" training_guard_models | tail -1
run BuildTrainingGuard "$M" "$C" 20500 "Training Guard" 15 215,248,60142,60143,164,60145,60146,60147,4954,490 | tail -2
echo "BUILD $M OK"
