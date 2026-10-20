#!/usr/bin/env bash
# Interface redesign 2026-10-20 (all on IfKit's OSRS stone buttons; previews via MockInterface, which now draws with the
# cache's own fonts and sprites):
#   1161 Trading Post (BuildTradingPostInterface; server tradepost ZelusMarket) -- after Desktop/marketex.jpg
#   1178 Zelus Codex  (BuildCodexInterface; server io.ruin.model.content.codex.ZelusCodex) -- replaces achievements 849
#   1151 Zelus Perks  (BuildPerkInterface, same 865 ids; restyle: recessed selection, stone palette, red delete, pills)
#   1158 Relic Forge  (BuildRelicForgeInterface; server RelicForge)
#   1153 Workbench    (BuildWorkbenchInterface; server WorkbenchHub)
# Compiled to out_ui. Run with the server STOPPED. Usage: build_ui_1020.sh apply <cachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_ui;out_free;out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J" | tail -1; }
run BuildTradingPostInterface rebuild "$C"
run BuildCodexInterface rebuild "$C"
run BuildPerkInterface rebuild "$C"
run BuildRelicForgeInterface rebuild "$C"
run BuildWorkbenchInterface rebuild "$C"
echo "BUILD ui 1020 OK"
