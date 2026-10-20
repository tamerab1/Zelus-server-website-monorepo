#!/usr/bin/env bash
# Armor sets 2026-10-20 (source Desktop/Rsps/Modle set/Models/Items; table + names + stats in gen_armor_1020.py, which
# writes armor_1020_*.txt, armor_1020_modelmap.txt -> armor_1020_raw, and the item_info.json entries):
#   Celestial 31870-76 (magic), Assassin 31878-84 (melee, greatsword), Elderthorn 31886-94 (ranged, was Forestwood v2),
#   Duskwarden 31896-904 (Purple: tank melee, blade + kiteshield), Hellfang 31906-12 (Red: strength melee, claws)
#   1. raw models 61290-61333 (inventory, worn per item; pieces without an inventory model use the worn mesh)
#   2. items (CreateItemVariants on Torva / Masori / Ancestral / Zaryte / Pegasian / weapon records)
#   3. worn models layered like the pieces they replace (TransferPriorities), then the AuditWornModels fix
#   4. icon framing
# Run with the server STOPPED, AFTER the entries are in item_info.json (the audit reads it).
# Usage: build_armor_1020.sh apply <cachePath> <classpath> <itemInfoJson>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; J="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$(cat gson_jar.txt);$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run InsertRawModels apply "$C" armor_1020_raw | tail -1
run CreateItemVariants apply "$C" armor_1020_items.txt | tail -1
rm -rf priorities_armor1020
run TransferPriorities "$C" armor_1020_priorities.txt priorities_armor1020 | tail -1
run InsertRawModels replace "$C" priorities_armor1020 | tail -1
rm -rf audit_fix_armor1020 && mkdir audit_fix_armor1020
run AuditWornModels fix "$C" "$J" audit_fix_armor1020 | tail -3
if [ -n "$(ls audit_fix_armor1020)" ]; then run InsertRawModels replace "$C" audit_fix_armor1020 | tail -1; fi
run AuditWornModels report "$C" "$J" audit_fix_armor1020 | tail -3
mkdir -p frame_out
run AutoFrameItems apply "$C" armor_1020_framing.txt frame_out | tail -1
echo "BUILD armor 1020 OK"
