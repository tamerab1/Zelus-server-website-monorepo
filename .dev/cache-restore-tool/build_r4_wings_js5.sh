#!/usr/bin/env bash
# Round 4 (2026-10-21):
#   1. Souly Wings worn model 60313, rebuilt from the ORIGINAL (pre-r2 backup cache): +12 z (r2 +18 floated, r3 +9 sat
#      tight) and a split render layer -- faces pointing back (+z, not mostly up) on 10, depth-sorted with the arms, so
#      from behind the wings draw over the body; the rest on 0 so the torso covers them from the front (r3 put ALL on 0:
#      body, arms and legs then painted over the wings from behind). Its rig is fine: every vertex is on skin label 8,
#      the upper-back group real capes use for their top. Do not let AuditWornModels "fix" re-layer it.
#   2. FixUnservableArchives: the archives the JS5 server could never answer (sprites 24464 / 24465 listed with no data,
#      index 16 with no reference table) get valid minimal data, so a full cache download can finish. Server side, the
#      kronos-api FileStore archive cap went 1 MB -> 10 MB (sprites 17014 / 17015 are 1,026,075 bytes).
#   3. the Slayer Master Codex interface 1180 (rewards + task management + supplies + prestige in one window).
# Run with the server STOPPED. Usage: build_r4_wings_js5.sh apply <cachePath> <originalCachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; O="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_ui;out_soultree;out;out_whips;out_bossrig;out_perkers;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
rm -rf wings_r4
run ShiftTextureModel "$O" 60313 60313 0,0,12 265 10202 wings_r4 split:10:0 | tail -1
run InsertRawModels replace "$C" wings_r4 | tail -1
run FixUnservableArchives apply "$C" | tail -1
# 3. the Slayer Master Codex, interface 1180 (BuildSlayerCodexInterface; server io.ruin.model.skills.slayer.SlayerCodex)
run BuildSlayerCodexInterface apply "$C" | tail -1
echo "BUILD r4 OK"
