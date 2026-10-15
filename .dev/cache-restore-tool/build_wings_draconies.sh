#!/bin/bash
# Fallen Angel Wings variants (items 31760-31766), Draconies set retexture (in place) and the stardust GFX (2026-10-14).
# Usage: build_wings_draconies.sh <cacheDir> <workDir> <classesDir> <classpath>
# Specs: wings_draconies_textures.txt, wings_draconies_models.txt, wings_items.txt, stardust_gfx.txt. Stops on the first failure.
set -euo pipefail
C="$1"; W="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
rm -rf "$W/wd_models"
run CreateRecoloredTextures apply "$C" wings_draconies_textures.txt
run TextureModelFaces "$C" wings_draconies_models.txt "$W/wd_models"
run InsertRawModels apply "$C" "$W/wd_models"
run CreateItemVariants apply "$C" wings_items.txt
# Draconies (60338-60342): repoint inventory / worn / head models to the textured copies, same item ids
run PatchItemModelRefs apply "$C" 60338,60339,60340,60341,60342 \
    57339:60083,57340:60084,49461:60085,57341:60086,57342:60087,57343:60088,57344:60089,57345:60090,57346:60091,57347:60092
run BuildBossVariants gfx apply "$C" stardust_gfx.txt
echo "BUILD OK"
