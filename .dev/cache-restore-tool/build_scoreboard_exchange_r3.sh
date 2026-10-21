#!/usr/bin/env bash
# Round 3 (2026-10-21), on top of build_scoreboard_exchange_r2.sh:
#   1. Zelus Shops (891): every category tab gets the riveted stone-button frame (AddShopTabStone, 220 + 11t); the
#      selected tab shows the sunken slot (AddShopTabRecess) with an amber bevel line (CustomShopInterface2).
#   2. Souly Wings worn model 60313 rebuilt from the ORIGINAL (pre-r2 backup cache): moved +9 z, not +18 (r2 left a gap
#      behind the back), flowing gold 265 on the glow, and every face on priority 0 under the body (the cape layers
#      5 / 1 let back-and-up glow faces draw over the chest from a raised camera in GPU mode). Do not let
#      AuditWornModels "fix" re-layer it to 5 / 1.
# The home 117HD floor rules (ZELUS_HOME_FLOORS) are client-side: rebuild the client shadowJar.
# Run with the server STOPPED. Usage: build_scoreboard_exchange_r3.sh apply <cachePath> <originalCachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; O="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_ui;out_soultree;out;out_whips;out_bossrig;out_perkers;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run AddShopTabStone apply "$C" | tail -1
rm -rf wings_r3
run ShiftTextureModel "$O" 60313 60313 0,0,9 265 10202 wings_r3 0 | tail -1
run InsertRawModels replace "$C" wings_r3 | tail -1
echo "BUILD r3 OK"
