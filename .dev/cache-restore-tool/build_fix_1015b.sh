#!/bin/bash
# 2026-10-15 fix round (after the master round): pressed-stone active states everywhere (IfKit / Teleports / Scoreboard /
# Relic Forge / Titles / Forge 1168 / Deals / Cape Forge), Forge 1168 6-slot required tray, dashboard 28px buttons,
# Cape Forge Master (NPC 3873) outfit + name/ops, Jackpot Jarvis (280) Talk-to / Lottery ops.
# Usage: build_fix_1015b.sh verify|apply <cacheDir> <classesDir> <classpath> <serverSrcDir>
set -euo pipefail
MODE="$1"; C="$2"; B="$3"; CP="$4"; S="$5"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
IFM=$([ "$MODE" = apply ] && echo rebuild || echo verify)
for b in BuildTeleportInterface BuildWildernessForgeInterface BuildDonatorDealsInterface BuildCapeForgeInterface \
         BuildScoreboardInterface BuildRelicForgeInterface BuildTitlesInterface; do
  run $b $IFM "$C" | tail -1
done
run BuildDashboardInterfaces $IFM "$C" "$S/io/ruin/model/inter/questtab/dashboard/DashboardLayout.java" | tail -2
bash build_cape_forge_master.sh "$MODE" "$C" "$B" "$CP" | tail -3
run EditConfigNameActions "$MODE" "$C" npc 280 0=Talk-to 1=Lottery | tail -2
echo "BUILD $MODE OK"
