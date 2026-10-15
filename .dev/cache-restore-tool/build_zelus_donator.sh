#!/usr/bin/env bash
# Zelus donator top row (2026-10-15): textures 226 (cosmic crimson, sprite 24548) / 227 (obsidian fissure, sprite
# 24549), textured model copies 60148-60159, and items 31794-31813 (10 items + placeholders).
# Server: item_info.json entries, CustomShop2.DONATOR_STORE top row, PlayerCombat scythe multi-hit for 31808.
# Client: WeaponSparklesOverlay COSMIC_WINGS star motes.
# Usage: build_zelus_donator.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateRecoloredTextures "$M" "$C" zelus_donator_textures.txt | tail -1
if [ "$M" = apply ]; then
  run TextureModelFaces "$C" zelus_donator_models.txt "$W" | tail -12
  run InsertRawModels apply "$C" "$W" | tail -1
else
  echo "(textured models built once textures 226/227 exist)"
fi
run CreateItemVariants "$M" "$C" zelus_donator_items.txt | tail -1
echo "BUILD $M OK"
