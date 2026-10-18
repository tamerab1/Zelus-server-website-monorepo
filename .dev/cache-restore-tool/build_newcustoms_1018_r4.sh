#!/usr/bin/env bash
# Round 4 (2026-10-18), on top of build_newcustoms_1018_r3.sh:
#   1. Soul Rend's cyan slash arc: spotanim 4424 (azure copy of 248), azure_slash_gfx.txt.
#   2. Ascended spire worn 61230 turned 90 degrees about its shaft (newcustoms_1018_transform2.txt): lantern arms front/back.
#   3. Reliquary of Souls model 61240 rebuilt in place (BuildSoulAltar model): blue-black obsidian, glowing trims + glyphs,
#      a translucent spectral crystal around an azure core, the three Ascended weapons' real models orbiting with trails.
#   4. Sanctuary props: Soul brazier 62446 / 61241 (cyan flame loop: skeleton/frames 16110, seq 16217) and the arcane
#      flagstone 62447 / 61242 (type 22, contoured).
#   5. Soul Tree 1176 rebuilt (evolution Preview bezel + milestone text; the modal moved to 159).
# Server: SoulRend, SoulTreeInterface, custom_objects.json. Client plugin: TREE_MODAL 159.
# Run with the server STOPPED. Usage: build_newcustoms_1018_r4.sh apply <cachePath> <classpath> <previewDir>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run BuildBossVariants gfx apply "$C" azure_slash_gfx.txt | tail -1
mkdir -p newcustoms_1018_r4 soul_altar_v2
run TransformModel "$C" newcustoms_1018_transform2.txt newcustoms_1018_dec newcustoms_1018_r4 | tail -1
run InsertRawModels replace "$C" newcustoms_1018_r4 | tail -1
run BuildSoulAltar model "$C" "$P/altar" 61240 soul_altar_v2 | tail -1
run InsertRawModels replace "$C" soul_altar_v2 | tail -1
run BuildSanctuaryProps apply "$C" "$P/altar" | tail -2
run BuildLocSpinAnims apply "$C" 62446,61241,1,16110,16110,16217,64,2,2 | tail -3
run BuildSoulTreeInterface rebuild "$C" | tail -1
echo "BUILD r4 apply OK"
