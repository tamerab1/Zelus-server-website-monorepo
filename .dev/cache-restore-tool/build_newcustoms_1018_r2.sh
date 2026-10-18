#!/usr/bin/env bash
# New customs round 2 (2026-10-18), on top of build_newcustoms_1018.sh:
#   1. render priorities (newcustoms_1018_priorities.txt): Crimson Warden platebody torso 3 / arms 10 (all-10 painted over
#      the head and neck under the GPU plugin), platelegs 1, Soulbound spire base / Empowered worn 10 (were 4: sank into
#      the body). Models re-encoded in place (vertex skins kept, full round-trip checked).
#   2. Discord Statue 62443: model 61238 rigged (logos = skin label 1, base ring = 100) and a looping loc spin
#      (skeleton/frames 16108, seq 16215: one turn per 6.4 s with a 3-unit bob) on opcode 24.
#   3. Azure Fang 31848: animated azure texture 261 (sprite 24622) on its two brightest cyan shades (the blade core / edges),
#      models 61236/61237 retextured in place; hit sparkle spotanim 4423 (cyan copy of 436 at 80% size).
# Server side: PlayerCombat / SoulNova cast 1167 for every spire look, 31840 weapon_type TRIDENT (item_info.json),
# AzureFang.onHit (Hit.finish). Run with the server STOPPED. Usage: build_newcustoms_1018_r2.sh apply <cachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run SetModelPriorities "$C" newcustoms_1018_priorities.txt newcustoms_1018_pri | tail -4
run InsertRawModels replace "$C" newcustoms_1018_pri | tail -1
run RigModelByHeight "$C" 61238 -21 1 100 newcustoms_1018_rig | tail -1
run InsertRawModels replace "$C" newcustoms_1018_rig | tail -1
run BuildLocSpinAnims apply "$C" 62443,61238,1,16108,16108,16215,160,1,3 | tail -3
run CreateRecoloredTextures apply "$C" azure_fang_texture.txt | tail -1
mkdir -p azure_fang_tex
run TextureModelFaces "$C" azure_fang_models.txt azure_fang_tex | tail -2
run InsertRawModels replace "$C" azure_fang_tex | tail -1
run BuildBossVariants gfx apply "$C" azure_fang_gfx.txt | tail -1
run SetSpotanimScale apply "$C" 4423:80 | tail -1
echo "BUILD r2 apply OK"
