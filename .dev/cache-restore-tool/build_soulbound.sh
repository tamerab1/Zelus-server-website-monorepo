#!/usr/bin/env bash
# Soulbound Living Weapons (2026-10-18): 9 untradeable weapons, 3 lines x 3 looks, each look on its OWN mesh:
#   Blade   31824 base (Dragon longsword) / 31826 Empowered (Blade of saeldor) / 31828 Ascended (Voidwaker)
#   Recurve 31830 (Crystal bow) / 31832 (Bow of faerdhinen) / 31834 (Twisted bow)
#   Spire   31836 (Ancient staff) / 31838 (Sanguinesti staff) / 31840 (Tumeken's shadow)   (placeholders = id + 1)
# Base (tiers 1-3) = soul-steel recolour (soulbound_base_theme.txt -> GenThemeRecolors -> soulbound_base_items.txt).
# Empowered (4-7) = obsidian + animated teal 253; Ascended (8-10) = gold body + animated gold 254 (sprites 24574/24575)
# (soulbound_glow_theme.txt -> GenForgeObsidian -> soulbound_glow_items.txt + soulbound_glow_models.txt, models 61191-61207).
# Then: worn "Check" option (soulbound_worn_ops.txt) and the Ascended special attack bars (soulbound_spec_enums.txt).
# The generated files were made ONCE on the pre-build cache; regenerate only on a cache without this build.
# Run with the server STOPPED. Usage: build_soulbound.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run CreateRecoloredTextures "$M" "$C" soulbound_textures.txt | tail -1
if [ "$M" = apply ]; then
  run TextureModelFaces "$C" soulbound_glow_models.txt "$W" | tail -2
  run InsertRawModels apply "$C" "$W" | tail -1
else
  echo "(glow models are textured + inserted on apply, once textures 253/254 exist)"
fi
run CreateItemVariants "$M" "$C" soulbound_base_items.txt | tail -1
[ "$M" = apply ] && run CreateItemVariants apply "$C" soulbound_glow_items.txt | tail -1 || echo "(glow items reference the new models: checked on apply)"
run SetItemWornOps "$M" "$C" soulbound_worn_ops.txt | tail -1
run AppendEnumEntries "$M" "$C" soulbound_spec_enums.txt | tail -1
echo "BUILD $M OK"
