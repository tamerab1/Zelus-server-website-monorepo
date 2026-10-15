#!/usr/bin/env bash
# Zelus donator r2 (2026-10-15): the Relic set (31800-31806) and Celestial Halo (31812) get the animated cyan-gilded
# texture 228 (sprite 24550) on model copies 60160-60174; the items are rebuilt in place (apply-replace).
# Usage: build_zelus_donator_r2.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateRecoloredTextures "$M" "$C" zelus_cyangilt_texture.txt | tail -1
if [ "$M" = apply ]; then
  run TextureModelFaces "$C" zelus_cyangilt_models.txt "$W" | tail -15
  run InsertRawModels apply "$C" "$W" | tail -1
else
  echo "(textured models built once texture 228 exists)"
fi
run CreateItemVariants "$M-replace" "$C" zelus_donator_items.txt | tail -1
echo "BUILD $M OK"
