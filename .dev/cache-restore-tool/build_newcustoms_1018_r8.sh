#!/usr/bin/env bash
# Round 8 (2026-10-18), on top of build_newcustoms_1018_r7.sh -- the worn-model audit, two more rules:
#   5. capes / wings: outer surface (normals away from the body) on 5, inner lining on 1, as real Fire / Infernal / Max
#      capes do; ripped capes and wings had both sides on one layer (lining over the chest, or the torso over the cape)
#   held items: every custom weapon / shield face on 10, not only flat-layered models
#   Soulbound blade T1 / T2: animated textures 262 violet / 263 crimson on their glow faces (soulblade_*.txt) and hit
#   sparkles 4426 / 4427 (the Ascended blade uses the Fang's cyan 4423) -- the T1 / T2 meshes were never textured
# (hide_arms on a non-body item is now ignored server-side: item_info.java; the sparkle map is in AzureFang.java.)
# Run with the server STOPPED. Usage: build_newcustoms_1018_r8.sh apply <cachePath> <classpath> <previewDir> <gsonJar>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; P="$4"; G="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_soultree;out;out_whips;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
mkdir -p audit_fix4
rm -f audit_fix4/*.dat
run AuditWornModels fix "$C" "../../../data zelus/data/items/item_info.json" audit_fix4 | tail -1
run InsertRawModels replace "$C" audit_fix4 | tail -1
run AuditWornModels report "$C" "../../../data zelus/data/items/item_info.json" audit_fix4 | tail -1
run CreateRecoloredTextures apply "$C" soulblade_textures.txt | tail -1
mkdir -p soulblade_tex
run TextureModelFaces "$C" soulblade_models.txt soulblade_tex | tail -1
run InsertRawModels replace "$C" soulblade_tex | tail -1
run BuildBossVariants gfx apply "$C" soulblade_gfx.txt | tail -1
run SetSpotanimScale apply "$C" 4426:80 4427:80 | tail -1
echo "BUILD r8 apply OK"
