#!/usr/bin/env bash
# Round 6 (2026-10-18), on top of build_newcustoms_1018_r5.sh:
#   1. the enclosed Soulbound Sanctuary: wall 62448 / model 61243, walk-through archway 62449 / model 61244, the flagstone
#      model 61242 rebuilt as polished obsidian with glowing grout (BuildSanctuaryRoom); a tree + fern on the east wall line
#      (3128,3491-3492, incl. an exact duplicate) removed from map l48_54 (sanctuary_room_locs.txt)
#   2. Reliquary model 61240 v3: 16-sided tiers, animated cyan / gold trims and glyphs
#   3. the worn-model audit, now from model 57320 (Draconic + Drakonbone): over-budget decimated, flat-layered bodies get
#      Torva layering (torso <= 3, arms 10), flat held items 10, skinless gear rigged from a real OSRS reference model
#   4. Soul Tree 1176: the Preview button under the caption; the dark looks lit brighter (soulbound_v6_lighting.txt)
# Run with the server STOPPED. Usage: build_newcustoms_1018_r6.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run RemoveMapLocs apply "$C" $(cat sanctuary_room_locs.txt) | tail -2
mkdir -p sanctuary_room audit_fix2
run BuildSanctuaryRoom apply "$C" "$P/altar" sanctuary_room | tail -1
run InsertRawModels replace "$C" sanctuary_room | tail -1
run BuildSoulAltar model "$C" "$P/altar" 61240 soul_altar_v2 | tail -1
run InsertRawModels replace "$C" soul_altar_v2 | tail -1
rm -f audit_fix2/*.dat
run AuditWornModels fix "$C" "../../../data zelus/data/items/item_info.json" audit_fix2 | tail -2
run InsertRawModels replace "$C" audit_fix2 | tail -1
run AuditWornModels report "$C" "../../../data zelus/data/items/item_info.json" audit_fix2 | tail -1
run BuildSoulTreeInterface rebuild "$C" | tail -1
run SetItemLighting apply "$C" soulbound_v6_lighting.txt | tail -1
echo "BUILD r6 apply OK"
