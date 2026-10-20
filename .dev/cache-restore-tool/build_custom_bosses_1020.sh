#!/usr/bin/env bash
# Custom bosses 16217-16225 (2026-10-20; source Desktop/Rsps/Modle set/Models/NPCs, spec custom_bosses_1020.txt):
#   1. raw models -> 61281-61289 (BuildCustomBosses models: none textured, none over the 6144-face cap)
#   2. npcs 16217-16225 written from scratch (name, model, size, scale, level, stand / walk, Attack)
# Server: npcs/combat/Custom_bosses_1020.json + CustomBossCombat tunes; ::testallbosses.
# Run with the server STOPPED. Usage: build_custom_bosses_1020.sh apply <cachePath> <rawDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; R="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
rm -rf newbosses_1020/raw
run BuildCustomBosses npcs verify "$C" custom_bosses_1020.txt | tail -1
run BuildCustomBosses models "$C" custom_bosses_1020.txt "$R" newbosses_1020/raw
run InsertRawModels apply "$C" newbosses_1020/raw | tail -1
run BuildCustomBosses npcs apply "$C" custom_bosses_1020.txt | tail -1
echo "BUILD custom bosses 1020 OK"
