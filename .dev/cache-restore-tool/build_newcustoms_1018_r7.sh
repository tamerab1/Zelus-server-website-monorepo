#!/usr/bin/env bash
# Round 7 (2026-10-18), on top of build_newcustoms_1018_r6.sh:
#   1. the walled room read as a bunker: the sanctuary is an open-air dais now (BuildSanctuaryPavilion) -- pillar 62450 /
#      model 61245, balustrade 62451 / 61246, dais step 62452 / 61247, slate apron 62453 / 61248, the flagstone 61242
#      rebuilt as raised slate slabs. Spawns in custom_objects.json; the room's 62448 / 62449 are no longer spawned.
#   2. the audit: flat custom legs above Torva's layer 1 re-layered like Torva platelegs -- the round 6 body re-layer put
#      torsos on 3 and Draconic-style legs on 4 painted over them (red band through the waist, weapons behind the legs)
# Run with the server STOPPED. Usage: build_newcustoms_1018_r7.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
mkdir -p sanctuary_pavilion audit_fix3
run BuildSanctuaryPavilion apply "$C" "$P/altar" sanctuary_pavilion | tail -1
run InsertRawModels replace "$C" sanctuary_pavilion | tail -1
rm -f audit_fix3/*.dat
run AuditWornModels fix "$C" "../../../data zelus/data/items/item_info.json" audit_fix3 | tail -1
run InsertRawModels replace "$C" audit_fix3 | tail -1
run AuditWornModels report "$C" "../../../data zelus/data/items/item_info.json" audit_fix3 | tail -1
echo "BUILD r7 apply OK"
