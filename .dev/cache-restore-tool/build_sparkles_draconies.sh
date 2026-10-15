#!/bin/bash
# Draconies restore + Emerald Draconies variants (31768-31777) + weapon-bound sparkles on the Twisted bow / Scythe (2026-10-14).
# Usage: build_sparkles_draconies.sh <cacheDir> <workDir> <classesDir> <classpath>. Stops on the first failure.
set -euo pipefail
C="$1"; W="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
# 1. originals back to their own models (textured copies 60083-60092 stay in the cache for the variants)
run PatchItemModelRefs apply "$C" 60338,60339,60340,60341,60342 \
    60083:57339,60084:57340,60085:49461,60086:57341,60087:57342,60088:57343,60089:57344,60090:57345,60091:57346,60092:57347
# 2. Emerald Draconies on new ids (before the sparkle model edits; the variant source is the restored original)
run CreateItemVariants apply "$C" draconies_items.txt
# 3. sparkles: glitter textures, then gem-studded copies of the worn weapon models
rm -rf "$W/sp_models"
run CreateRecoloredTextures apply "$C" sparkle_textures.txt
run AddSparkles "$C" 32674 60093 "$W/sp_models" 203 9188 8 4 5 11
run AddSparkles "$C" 39561 60094 "$W/sp_models" 203 9188 8 4 5 12
run AddSparkles "$C" 35371 60095 "$W/sp_models" 204 966 8 4 6 13
run AddSparkles "$C" 32906 60096 "$W/sp_models" 204 966 8 4 6 14
run InsertRawModels apply "$C" "$W/sp_models"
# worn models only: the inventory icon and the item itself are unchanged
run PatchItemModelRefs apply "$C" 20997 32674:60093,39561:60094
run PatchItemModelRefs apply "$C" 22325,22664 35371:60095,32906:60096
echo "BUILD OK"
