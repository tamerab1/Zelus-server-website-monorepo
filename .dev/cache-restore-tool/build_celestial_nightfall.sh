#!/usr/bin/env bash
# Nightfall bow (celestial) 31818 + Voidmind Hexer pet one size bigger (2026-10-16). Run once (refuses existing ids).
# Usage: build_celestial_nightfall.sh apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only"; exit 1; }
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateRecoloredTextures apply "$C" celestial_nightfall_texture.txt | tail -1
run TextureModelFaces "$C" celestial_nightfall_models.txt "$W" | tail -1
run InsertRawModels apply "$C" "$W" | tail -1
run CreateItemVariants apply "$C" celestial_nightfall_items.txt | tail -1
run BuildBossVariants gfx apply "$C" celestial_nightfall_gfx.txt | tail -1
run AppendEnumEntries apply "$C" celestial_nightfall_enums.txt | tail -1
for n in 30576 30577 30578; do run EditNpcInPlace "$C" $n 105 105 | tail -1; done
run EditNpcInPlace "$C" 30601 140 140 | tail -1
echo "BUILD $M OK"
