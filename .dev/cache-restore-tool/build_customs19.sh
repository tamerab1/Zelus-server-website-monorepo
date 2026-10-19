#!/usr/bin/env bash
# customs19 batch (2026-10-19; source Desktop/customs19, models customs19_modelmap.txt -> customs19_raw):
#   1. raw models 61250-61263 (the Zaryte bow's worn mesh doubles as its inventory model 61257: no ground model came)
#   2. items 31850-31863 (customs19_items.txt): Vermilion masori mask / body / chaps (Masori (f) records), Zaryte
#      Stormpiercer (Bow of faerdhinen record, ammo-free), Vorkath mask / platebody / platelegs (Torva records)
#   3. icon framing (customs19_framing.txt)
#   4. AuditWornModels fix (face budgets + layering, as in round r9) -> audit_fix19, replaced in place
#   5. dedicated bank placeholders (template 27692): Voidwaker 12765 -> 31864, Ghommal's avernic defender 5 / 6
#      27550 / 27552 -> 31865 / 31866
# Stats: data zelus/data/items/item_info.json (31850-31862). Server: ZaryteStormpiercer refuses Drop.
# Run with the server STOPPED. Usage: build_customs19.sh apply <cachePath> <classpath> <gsonJar> <itemInfoJson>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; G="$4"; J="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run InsertRawModels apply "$C" customs19_raw | tail -1
run CreateItemVariants apply "$C" customs19_items.txt | tail -1
mkdir -p frame_out
run AutoFrameItems apply "$C" customs19_framing.txt frame_out | tail -1
rm -rf audit_fix19 && mkdir audit_fix19
run AuditWornModels fix "$C" "$J" audit_fix19 | tail -1
run InsertRawModels replace "$C" audit_fix19 | tail -1
run AuditWornModels report "$C" "$J" audit_fix19 | tail -1
run AddDedicatedPlaceholders apply "$C" 27692 12765:31864 27550:31865 27552:31866 | tail -1
echo "BUILD customs19 OK"
