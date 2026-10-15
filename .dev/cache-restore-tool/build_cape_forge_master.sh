#!/bin/bash
# NPC 3873 ('Lecherous' Lee) -> "Cape Forge Master" (2026-10-15): Prismatic partyhat, Radiant oathplate chest + legs,
# Starweave boots, Cosmic max cape, Twisted bow (celestial). The boots / cape / bow carry item-level recolours (and the
# cape a retexture 59->155), baked into copies 60128-60130 so they show right on an NPC. Hair 211, jaw 249, hands 179 kept;
# the torso / arm kits are dropped (the oathplate chest is sleeved, +-38 wide).
# Usage: build_cape_forge_master.sh verify|apply <cacheDir> <classesDir> <classpath>
set -euo pipefail
MODE="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run BakeModelRecolorRetexture bake "$C" 29249 60128 "$W" 695,9143,-24281,-13651,-22510,-13674,-22361,-13651,-22326,-13613,-22248,-13668,-22242,-13661,-10839,9129 -
run BakeModelRecolorRetexture bake "$C" 33102 60129 "$W" 0,-14078,8,-14072,12,-14070,20,-14064,24,-14061,33,-14055,41,-14049,522,-13942,902,-13556,945,-13528,960,10042,968,10050,978,10060,-22479,9778 59,155
run BakeModelRecolorRetexture bake "$C" 32674 60130 "$W" 0,-20992,8,-20984,16,-20976,24,-20968,33,-20959,41,-20951,10318,9038,10334,9054,13223,9148,14236,9133 -
run InsertRawModels "$MODE" "$C" "$W"
if [ "$MODE" = apply ]; then
  run SetNpcModelListInPlace apply "$C" 3873 211,249,59849,56303,56302,179,60128,60129,60130
  run EditConfigNameActions apply "$C" npc 3873 "name=Cape Forge Master" 0=Talk-to 1=Upgrade
else
  echo "(model list + name/ops are applied after the models exist)"
  run EditConfigNameActions verify "$C" npc 3873 "name=Cape Forge Master" 0=Talk-to 1=Upgrade
fi
echo "BUILD $MODE OK"
