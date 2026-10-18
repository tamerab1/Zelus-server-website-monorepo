#!/usr/bin/env bash
# Round 11 (2026-10-18), on top of build_newcustoms_1018_r10.sh:
#   1. SetFloorUnderlay: the 25 Soul Sanctuary floor tiles all on underlay 6 -- 117HD took its ground material from the
#      mixed underlays (51 = pale sand) and rendered half the floor white
#   2. Reliquary model 61240 v4: carved granite steps (faceted risers, bevels, light treads, cyan inlays + glyphs), granite
#      obelisks with flowing bands, a fluted pedestal with four stone claws cradling the crystal (BuildSoulAltar)
# Run with the server STOPPED. Usage: build_newcustoms_1018_r11.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run SetFloorUnderlay apply "$C" | tail -1
run BuildSoulAltar model "$C" "$P/altar" 61240 soul_altar_v2 | tail -1
run InsertRawModels replace "$C" soul_altar_v2 | tail -1
echo "BUILD r11 apply OK"
