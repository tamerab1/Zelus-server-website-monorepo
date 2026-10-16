#!/usr/bin/env bash
# Forge obsidian re-skin + Bestiary charges (2026-10-16).
#  - textures 229-234 (sprites 24551-24556): lava 59 remapped obsidian -> blood crimson with per-family tips
#  - Wilderness Forge items 31500-31596 rebuilt in place: obsidian recolour + lava texture on accent faces
#    (pk_obsidian_items.txt / pk_obsidian_models.txt, generated ONCE by GenForgeObsidian from pk_obsidian_theme.txt
#    on the pre-build cache -- regenerate only on a cache without this build, the model ids depend on it)
#  - spotanims 4400-4415: crimson copies of the forge weapons' spec effects (pk_obsidian_gfx.txt)
#  - Ascended Bestiary 31746 new ops, Ascended Bestiary (empty) 31814, Combat Pet Shard 31816 (model 60175)
# Usage: build_forge_obsidian.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateRecoloredTextures "$M" "$C" pk_obsidian_textures.txt | tail -1
run TextureModelFaces "$C" bestiary_charge_models.txt "$W" | tail -1
if [ "$M" = apply ]; then
  run TextureModelFaces "$C" pk_obsidian_models.txt "$W" | tail -2
fi
[ "$M" = apply ] && run InsertRawModels apply "$C" "$W" | tail -1
run CreateItemVariants "$M-replace" "$C" pk_obsidian_items.txt | tail -1
run CreateItemVariants "$M-replace" "$C" bestiary_charge_items.txt | tail -1
run BuildBossVariants gfx "$M" "$C" pk_obsidian_gfx.txt | tail -1
echo "BUILD $M OK"
