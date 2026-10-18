#!/usr/bin/env bash
# New customs + Soulbound v2 meshes + Soul Tree round 4 (2026-10-18). Source: "Desktop/new customs" (25 raw models).
#   1. raw models 61211-61239 (newcustoms_1018_raw, map newcustoms_1018_modelmap.txt)
#   2. tier-3 soul weapons gold-textured (texture 254) -> 61221/61222 (bow), 61229/61230 (spire)
#   3. Soulbound looks rebuilt on the new meshes, same ids (soulbound_v2_items.txt) + their worn Check op again
#      (the Ascended blade 31828 keeps its v1 mesh: no tier-3 melee model was supplied)
#   4. new items 31842-31849 (newcustoms_1018_items.txt): Wyrmwing boots, Crimson Warden platebody / platelegs, Azure Fang
#   5. icon framing for all of them (AutoFrameItems: soulbound_v2_framing.txt, newcustoms_1018_framing.txt)
#   6. objects 62443 Discord Statue / 62444 Donator Platform (newcustoms_1018_objects.txt)
#   7. Soul Tree 1176 rebuilt (round 4: confirmation modal, padded inspector) -- sprites are already in the cache
# Stats: data zelus/data/items/item_info.json (entries 31842-31848). Window cameras: SoulWeaponCamera (server),
# from soulbound_v2_cameras.txt via WidgetModelFrame.
# Run with the server STOPPED. Usage: build_newcustoms_1018.sh verify|apply <cachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
run() { java -cp "out_soultree;out_whips;out;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
if [ "$M" = verify ]; then
  run InsertRawModels verify "$C" newcustoms_1018_raw | tail -1
  echo "(the later steps read the inserted models: rehearse them on a scratch cache copy with apply)"
  exit 0
fi
run InsertRawModels apply "$C" newcustoms_1018_raw | tail -1
mkdir -p newcustoms_1018_tex
run TextureModelFaces "$C" newcustoms_1018_textured.txt newcustoms_1018_tex | tail -4
run InsertRawModels apply "$C" newcustoms_1018_tex | tail -1
run CreateItemVariants apply-replace "$C" soulbound_v2_items.txt | tail -1
run SetItemWornOps apply "$C" soulbound_worn_ops.txt | tail -1
run CreateItemVariants apply "$C" newcustoms_1018_items.txt | tail -1
run AutoFrameItems apply "$C" soulbound_v2_framing.txt frame_out | tail -1
run AutoFrameItems apply "$C" newcustoms_1018_framing.txt frame_out | tail -1
run CreateDecorObjects apply "$C" newcustoms_1018_objects.txt | tail -1
run BuildSoulTreeInterface rebuild "$C" | tail -1
echo "BUILD apply OK"
