#!/usr/bin/env bash
# Division Rivals round 6 (2026-10-15): the Challenger cape on the Max cape mesh with its own painted woven texture
# 225 (sprite 24547; no Fire cape lava), cape options re-applied after the record rebuild, and 1172's cards with
# BOTH options enabled (clickMask 2|4: "Configure layout" was hidden). The Champion cape is untouched.
# Usage: build_rivals_r6.sh verify|apply <cachePath> <outDir> <classpath> <previewPng>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"; P="$5"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run BuildWovenTexture "$M" "$C" 225 24547 1 1 "$P" | tail -1
if [ "$M" = apply ]; then
  run TextureModelFaces "$C" rivals_challenger_woven_models.txt "$W" | tail -3
  run InsertRawModels apply "$C" "$W" | tail -1
  run CreateItemVariants apply-replace "$C" rivals_items.txt | tail -1
else
  echo "(cape models / records applied once texture 225 exists)"
fi
run SetItemWornOps "$M" "$C" rivals_capes_ops.txt
run BuildRivalsMatchmakingInterface "$([ "$M" = apply ] && echo rebuild || echo verify)" "$C" | tail -1
echo "BUILD $M OK"
