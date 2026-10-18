#!/usr/bin/env bash
# Round 9 (2026-10-18), on top of build_newcustoms_1018_r8.sh:
#   1. the sanctuary shrunk to a 5x5 dais (BuildSanctuaryDais): loc 62454 / model 61249, one flat marble platform placed as
#      a ground decoration on 3124,3491; 4 corner pillars 62450; braziers / balustrades / steps / apron / flagstones
#      no longer spawned (custom_objects.json)
#   2. the audit's cape rule: only the outer surface (+z) and the collar tops (up-facing, top 12%) on 5; every sideways
#      edge on 1 -- round 8 left raised letters' and trims' edges on 5, which a camera above saw from the front (YouTube
#      letters mirrored on the stomach, the KBD cape's hem over the legs). Checked with a pitched painter simulation.
# The client half (GPU TextureManager.TEXTURE_COUNT 256 -> 384, so textures past 256 animate) ships in the client jar.
# Run with the server STOPPED. Usage: build_newcustoms_1018_r9.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run BuildSanctuaryDais apply "$C" "$P/altar" | tail -1
mkdir -p audit_fix5
rm -f audit_fix5/*.dat
run AuditWornModels fix "$C" "../../../data zelus/data/items/item_info.json" audit_fix5 | tail -1
run InsertRawModels replace "$C" audit_fix5 | tail -1
run AuditWornModels report "$C" "../../../data zelus/data/items/item_info.json" audit_fix5 | tail -1
echo "BUILD r9 apply OK"
