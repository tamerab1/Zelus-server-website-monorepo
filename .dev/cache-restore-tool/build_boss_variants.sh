#!/bin/bash
# Boss variants (2026-10-13). Usage: build_boss_variants.sh <cacheDirWindowsPath> <workDir> <classesDir> <classpath>
# Specs: boss_variants_texture.txt, boss_variants.txt. Stops on the first failure.
set -euo pipefail
C="$1"; W="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
rm -rf "$W/boss_models"
run CreateRecoloredTextures apply "$C" boss_variants_texture.txt
run BuildBossVariants texmodels "$C" boss_variants.txt "$W/boss_models"
run InsertRawModels apply "$C" "$W/boss_models"
run BuildBossVariants npcs apply "$C" boss_variants.txt
run BuildBossVariants gfx apply "$C" boss_variants.txt
echo "BUILD OK"
