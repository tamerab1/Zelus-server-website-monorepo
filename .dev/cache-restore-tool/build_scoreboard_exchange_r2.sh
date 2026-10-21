#!/usr/bin/env bash
# Scoreboard Exchange round 2 (2026-10-21), on top of build_scoreboard_exchange.sh:
#   1. the Zelus Scoreboard board (object 62391) repainted: its daily / weekly rewards read SbP, not PKP
#      (BuildChampionshipBoard rebuild scoreboard: sprites 24536-24539 / textures 214-217 / model 60133 in place);
#   2. animated textures 265-269 (sbx2_textures.txt, sprites 24627-24631);
#   3. textured model copies 61336-61345 (partyhats, whips) + Souly Wings fixed in place: inventory 60312 textured,
#      worn 60313 moved 18 units back (+z = behind; its roots sat inside the torso) with the flowing gold 265 on its
#      pale-gold energy faces (10202);
#   4. items 31914-31961 (sbx2_items.txt): Crimson / Verdant / Abyssal / Celestial Ankou sets, Starwoven + Gildshadow
#      partyhats, Stormcoil + Magmatide whips (+ placeholders); spec bars in enums 906 / 1739 (sbx2_enums.txt);
#   5. interface 1179 rebuilt: 5 tabs, 36 cards.
# item_info.json entries must already be in place. Run with the server STOPPED.
# Usage: build_scoreboard_exchange_r2.sh apply <cachePath> <workDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; W="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -Xmx3g -cp "out_free;out_ui;out_soultree;out;out_whips;out_bossrig;out_perkers;fatjar;$(cat gson_jar.txt | tr -d '\r\n');$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
rm -rf "$W" && mkdir -p "$W/tex" "$W/new" "$W/repl" "$W/board"
run BuildChampionshipBoard rebuild "$C" "$W/board" scoreboard | tail -1
run CreateRecoloredTextures apply "$C" sbx2_textures.txt | tail -1
run TextureModelFaces "$C" sbx2_models.txt "$W/tex" | tail -1
run ShiftTextureModel "$C" 60313 60313 0,0,18 265 10202 "$W/tex" | tail -1
for f in "$W/tex"/*.dat; do
  case "$(basename "$f" .dat)" in 60312|60313) cp "$f" "$W/repl/" ;; *) cp "$f" "$W/new/" ;; esac
done
run InsertRawModels apply "$C" "$W/new" | tail -1
run InsertRawModels replace "$C" "$W/repl" | tail -1
run CreateItemVariants apply "$C" sbx2_items.txt | tail -1
run AppendEnumEntries apply "$C" sbx2_enums.txt | tail -1
run BuildScoreboardExchangeInterface rebuild "$C" | tail -1
echo "BUILD sbx r2 OK"
# same session: the Zelus Shops (891) active category tab is a sunken stone slot, not the 1px gold frame (AddShopTabRecess)
run AddShopTabRecess apply "$C" | tail -1
echo "BUILD shop tabs OK"
# same session: home floors smudged under 117HD -- sign-extended overlay values (0xFF9D / 0xFFAE) in m48_54 back to
# 157 / 174 (overlays 156 / 173); the 117HD rules for them are in the client (tile_overrides.json ZELUS_HOME_*)
run FixSignExtendedOverlays apply "$C" 48_54 | tail -1
echo "BUILD home floors OK"
# same session: the event boss cavern floor (BuildBossCavern: dais, bedrock blend, lava cracks, rim, edge slopes);
# its decor is data/objects/spawns/event_boss_cavern.json (written from the same run's spawn list)
run BuildBossCavern apply "$C" "$W/cavern" "$W/cavern/decor.json" | tail -1
echo "BUILD cavern OK"
