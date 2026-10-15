#!/bin/bash
# Boxing Tournament items (2026-10-13): glove tiers T2-T5 (31750-31757) + Boxing Mystery Chest (31758/31759),
# textures 195-197 (sprites 24511-24513), textured models 60065-60071.
# Usage: build_boxing.sh <cacheDirWindowsPath> <workDir> <classesDir> <classpath>
# Specs: boxing_texture.txt, boxing_models.txt, boxing_items.txt. Stops on the first failure.
set -euo pipefail
C="$1"; W="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
rm -rf "$W/boxing_models"
run CreateRecoloredTextures apply "$C" boxing_texture.txt
run TextureModelFaces "$C" boxing_models.txt "$W/boxing_models"
run InsertRawModels apply "$C" "$W/boxing_models"
run CreateItemVariants apply "$C" boxing_items.txt
echo "BUILD OK"
