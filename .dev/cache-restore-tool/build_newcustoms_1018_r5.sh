#!/usr/bin/env bash
# Round 5 (2026-10-18), on top of build_newcustoms_1018_r4.sh:
#   1. the fenced yard at 3122-3127 x 3488-3494 removed from map l48_54 (sanctuary_cage_locs.txt; 29 locs incl. an exact
#      duplicate) -- the Soulbound Sanctuary is spawned there (custom_objects.json: flagstones, braziers, altar 3123,3490)
#   2. Azure Fang: its mid cyan shades join the animated texture 261 (azure_fang_models2.txt, in place)
#   3. Ascended spire worn 61230 rebuilt from the untouched mesh: 90 degrees about its REAL shaft line (spire_r5_*.txt);
#      the round 3/4 transforms had moved the grip out of the fist
#   4. Soul Rend phantom slash spotanim 4425 (azure copy of 1171)
#   5. Reliquary model 61240 rebuilt (its mini Fang picks up the new texture)
#   6. the worn-model audit (AuditWornModels fix): every CUSTOM worn model over its slot budget decimated, custom bodies
#      with torso faces on priority >= 8 re-layered to 3 -- a fully geared player stays under the GPU's 6144-face cap
# Run with the server STOPPED. Usage: build_newcustoms_1018_r5.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run RemoveMapLocs apply "$C" $(cat sanctuary_cage_locs.txt) | tail -2
mkdir -p azure_fang_tex2 audit_fix
run TextureModelFaces "$C" azure_fang_models2.txt azure_fang_tex2 | tail -2
run InsertRawModels replace "$C" azure_fang_tex2 | tail -1
run InsertRawModels replace "$C" spire_r5 | tail -1
run BuildBossVariants gfx apply "$C" azure_slash2_gfx.txt | tail -1
run BuildSoulAltar model "$C" "$P/altar" 61240 soul_altar_v2 | tail -1
run InsertRawModels replace "$C" soul_altar_v2 | tail -1
run AuditWornModels fix "$C" "../../../data zelus/data/items/item_info.json" audit_fix | tail -3
run InsertRawModels replace "$C" audit_fix | tail -1
run AuditWornModels report "$C" "../../../data zelus/data/items/item_info.json" audit_fix | tail -1
echo "BUILD r5 apply OK"
