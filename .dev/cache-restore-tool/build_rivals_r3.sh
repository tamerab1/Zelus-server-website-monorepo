#!/usr/bin/env bash
# Division Rivals round 3 (2026-10-15): Champion cape velvet texture + options, Overseer without "Leaderboard",
# the roomier leaderboard 1169, and the Rivals Leaderboard object 62392.
# Usage: build_rivals_r3.sh verify|apply <cachePath> <outDir> <classpath> <previewDir>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"; P="$5"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateRecoloredTextures "$M" "$C" rivals_cape_velvet_tex.txt | tail -1
run TextureModelFaces "$C" rivals_cape_velvet_models.txt "$W" | tail -2
run InsertRawModels "$M" "$C" "$W" | tail -1
if [ "$M" = apply ]; then
  run CreateItemVariants apply-replace "$C" rivals_items.txt | tail -1
else
  echo "(item records re-applied once models 60135/60136 exist)"
fi
run SetItemWornOps "$M" "$C" rivals_capes_ops.txt
run EditConfigNameActions "$M" "$C" npc 3351 3=- | tail -1
run BuildChampionLadderInterface "$([ "$M" = apply ] && echo rebuild || echo verify)" "$C" | tail -1
if [ "$M" = apply ]; then
  run BuildChampionshipBoard apply "$C" "$P" rivals | tail -3
else
  echo "(rivals board verified after texture 220 exists: its textures start at 221)"
fi
echo "BUILD $M OK"
