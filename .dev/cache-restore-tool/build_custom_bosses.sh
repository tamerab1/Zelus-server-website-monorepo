#!/usr/bin/env bash
# Custom bosses 16200-16214 (2026-10-19; source Desktop/new customs/bosses, spec custom_bosses.txt):
#   1. raw models -> 61264-61278 (BuildCustomBosses models: 13558's textures are not in this cache -> texturing dropped)
#   2. the three over the GPU plugin's 6144-face cap decimated to 5600 (custom_bosses_decimate.txt)
#   3. npcs 16200-16214 written from scratch (name, model, size, scale, level, stand / walk, Attack)
# Server: npcs/combat/Custom_bosses.json + io.ruin.model.activities.bosses.custom.CustomBossCombat; ::testallbosses.
# Run with the server STOPPED. Usage: build_custom_bosses.sh apply <cachePath> <rawBossDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; R="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
rm -rf custom_bosses_raw custom_bosses_dec
run BuildCustomBosses models "$C" custom_bosses.txt "$R" custom_bosses_raw
run InsertRawModels apply "$C" custom_bosses_raw | tail -1
run DecimateModel "$C" custom_bosses_decimate.txt custom_bosses_dec
run InsertRawModels replace "$C" custom_bosses_dec | tail -1
run BuildCustomBosses npcs apply "$C" custom_bosses.txt | tail -1
echo "BUILD custom bosses OK"
