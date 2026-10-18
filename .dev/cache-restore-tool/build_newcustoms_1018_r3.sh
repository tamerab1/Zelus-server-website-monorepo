#!/usr/bin/env bash
# New customs round 3 + the Reliquary of Souls (2026-10-18), on top of build_newcustoms_1018_r2.sh:
#   1. face budgets (newcustoms_1018_decimate.txt): the GPU plugin draws <= 6144 faces of the merged player model, and the
#      Crimson Warden body/legs + Azure Fang left nothing for the head / hands / feet merged after them. Worn 61233
#      (body), 61235 (legs), 61237 (Azure Fang / Ascended blade), 61214 (Empowered blade), 61230 (Ascended spire) decimated.
#   2. Ascended spire worn 61230 shrunk 85% about the grip and moved out / down (its lanterns hit the face).
#   3. Ascended blade 31828 = the Azure Fang meshes (soulbound_v3_items.txt) + its worn Check op again.
#   4. Soulbound looks re-framed with zan2d 0 (model widgets draw the item with its own zan2d) and the dark ones lit
#      brighter (soulbound_v3_lighting.txt).
#   5. Reliquary of Souls: loc 62445 / model 61240 (BuildSoulAltar), its loop skeleton/frames 16109 + seq 16216.
# Server: SoulAltar, SoulWeaponCamera, item_info 31828 SCIMITAR, custom_objects.json (62443 Discord Statue, 62445 altar).
# Run with the server STOPPED. Usage: build_newcustoms_1018_r3.sh apply <cachePath> <classpath> <previewDir>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
mkdir -p newcustoms_1018_dec
run DecimateModel "$C" newcustoms_1018_decimate.txt newcustoms_1018_dec | tail -5
run TransformModel "$C" newcustoms_1018_transform.txt newcustoms_1018_dec newcustoms_1018_dec | tail -1
run InsertRawModels replace "$C" newcustoms_1018_dec | tail -1
run CreateItemVariants apply-replace "$C" soulbound_v3_items.txt | tail -1
run SetItemWornOps apply "$C" soulbound_worn_ops.txt | tail -1
run AutoFrameItems apply "$C" soulbound_v2_framing.txt "$P/frame" | tail -1
run SetItemLighting apply "$C" soulbound_v3_lighting.txt | tail -1
run BuildSoulAltar apply "$C" "$P/altar" 62445 61240 | tail -2
run BuildLocSpinAnims apply "$C" 62445,61240,1,16109,16109,16216,192,1,5 | tail -3
echo "BUILD r3 apply OK"
