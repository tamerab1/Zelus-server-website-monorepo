#!/usr/bin/env bash
# Round 12 (2026-10-18), on top of build_newcustoms_1018_r11.sh: the Reliquary's soul crystal locked to the altar's axis.
#   1. model 61240: the crystal on its own skin label 2 (BuildSoulAltar), centred on x = z = 0
#   2. BuildAltarAnim: skeleton / frames 16109 replaced in place -- the orbiting weapons (label 1) turn about the axis
#      exactly (type-0 origin offset = -centroid), the crystal (label 2) only bobs y +-10, no turn, no x/z movement.
#      seq 16216 and loc 62445 untouched.
# Run with the server STOPPED. Usage: build_newcustoms_1018_r12.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run BuildSoulAltar model "$C" "$P/altar" 61240 soul_altar_v2 | tail -1
run InsertRawModels replace "$C" soul_altar_v2 | tail -1
run BuildAltarAnim apply "$C" | tail -4
echo "BUILD r12 apply OK"
