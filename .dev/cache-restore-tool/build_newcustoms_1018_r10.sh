#!/usr/bin/env bash
# Round 10 (2026-10-18), on top of build_newcustoms_1018_r9.sh: the Soul Sanctuary floor goes into the MAP.
#   1. texture 54 (the one free id under 256): texture 43's square flagstones recoloured to dark obsidian with cyan
#      glints (sanctuary_floor_texture.txt; CreateRecoloredTextures now inserts in id order, so it may fill a gap)
#   2. BuildSanctuaryFloor: overlay 436 (texture 54), terrain m48_54 -- the 5x5 3122-3126 x 3489-3493 floored, the old
#      cobblestone (84) around it removed to grass -- and loc 62455 "Soul brazier" (Brazier 33454, flame recoloured cyan)
# The 5x5 dais model 62454 is no longer spawned (a ground decoration bigger than a tile is overdrawn by later tiles).
# Run with the server STOPPED. Usage: build_newcustoms_1018_r10.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run CreateRecoloredTextures apply "$C" sanctuary_floor_texture.txt | tail -1
run BuildSanctuaryFloor apply "$C" | tail -1
echo "BUILD r10 apply OK"
