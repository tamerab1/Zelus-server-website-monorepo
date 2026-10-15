#!/bin/bash
# Tiered combat pets + tier effects + Ascended Bestiary (2026-10-12, round 2). Run on a cache WITHOUT the earlier round. Usage: build_tier_pets.sh <cacheDirWindowsPath> <workDir> <classesDir> <classpath>
# Specs: tier_pets_texture.txt, tier_pets.txt, bestiary_model.txt, bestiary_item.txt. Stops on the first failure.
set -euo pipefail
C="$1"; W="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
rm -rf "$W/tier_models" "$W/book_model"
run CreateRecoloredTextures apply "$C" tier_pets_texture.txt
run BuildTierPets texmodels "$C" tier_pets.txt "$W/tier_models"
run BuildTierPets merge "$C" tier_pets.txt "$W/tier_models"
run BuildTierPets gfxmodels "$C" tier_pets.txt "$W/tier_models"
run TextureModelFaces "$C" bestiary_model.txt "$W/book_model"
cp "$W/book_model/"*.dat "$W/tier_models/"
run InsertRawModels apply "$C" "$W/tier_models"
run BuildTierPets npcs apply "$C" tier_pets.txt | grep -v "not present"
run BuildTierPets itemspec "$C" tier_pets.txt tier_pet_items.txt
run CreateItemVariants apply "$C" tier_pet_items.txt
run BuildTierPets frame apply "$C" tier_pets.txt
run BuildTierPets gfx apply "$C" tier_pets.txt
run CreateItemVariants apply "$C" bestiary_item.txt
run BuildBestiaryInterface apply "$C"
echo "BUILD OK"
