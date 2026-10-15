#!/bin/bash
# 2026-10-15 master round: Teleports 1152 (preview components offsetY2d 0) + 71 merged/baked preview models
# (BuildTeleportPreviews -> teleport_previews.json), Wilderness Forge 1168 (new), Scoreboard 1163 (footer margin),
# journal dashboards 1143-1147 (bezel buttons with icons; writes DashboardLayout.java).
# Usage: build_master_1015.sh verify|apply <cacheDir> <classesDir> <classpath> <dataDir> <serverSrcDir>
set -euo pipefail
MODE="$1"; C="$2"; B="$3"; CP="$4"; D="$5"; S="$6"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
IF_MODE=$([ "$MODE" = apply ] && echo rebuild || echo verify)
W=$(mktemp -d)
if [ "$MODE" = apply ]; then
  run BuildTeleportPreviews build "$C" teleport_previews.txt "$W/preview_models" "$D/teleport_previews.json" | tail -3
  run InsertRawModels apply "$C" "$W/preview_models" | tail -2
else
  run BuildTeleportPreviews verify "$C" teleport_previews.txt "$W/preview_models" "$W/tp.json" | tail -2
fi
run BuildTeleportInterface $IF_MODE "$C"
run BuildWildernessForgeInterface $([ "$MODE" = apply ] && echo apply || echo verify) "$C"
run BuildScoreboardInterface $IF_MODE "$C"
run BuildDashboardInterfaces $IF_MODE "$C" "$S/io/ruin/model/inter/questtab/dashboard/DashboardLayout.java" | tail -3
echo "BUILD $MODE OK"
