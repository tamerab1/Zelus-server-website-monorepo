#!/usr/bin/env bash
# customs27 (2026-10-27): rebuilds the rig / texture work for stage 7 on a FRESH copy of our rev 231 cache, then exports
# the pieces to assets/customs27 (PatchCustoms27 export). The order matters:
#   raw models -> decimate the bosses (GPU 6144-face cap) -> rig them (BossRig relabels the decimated meshes) ->
#   render priorities (bosses one layer; held weapons 10) -> new textures -> textured weapon models.
# Usage: build_customs27_scratch.sh <scratchDir> <our231CacheDir> <customs27Dir> <stageClassDir>
set -euo pipefail
W="$1"; SRC="$2"; R="$3"; STAGE="$4"
here="$(cd "$(dirname "$0")" && pwd)"
T="$(cd "$here/../../../../server zelus/.dev/cache-restore-tool" && pwd)"
CP="$(tr -d '\r\n' < "$T/cp_with_jna.txt")"
w() { cygpath -w "$1"; }
rm -rf "$W"; mkdir -p "$W/c231" "$W/raw" "$W/dec" "$W/prio" "$W/tex" "$W/irm" "$W/shift"
cp "$SRC"/main_file_cache.dat2 "$SRC"/main_file_cache.idx[0-9]* "$W/c231/"
C="$(w "$W/c231")"
cd "$T"
javac -nowarn -cp "out;fatjar;$CP" -d "$(w "$W/irm")" src/InsertRawModels.java
run() { java -cp "$(w "$W/irm");out_bossrig;out_soultree;out_ui;out_batch2;out;out_whips;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J\|^label"; return ${PIPESTATUS[0]}; }

cp "$R/boss1/49027.dat" "$W/raw/62776.dat"
cp "$R/boss2/97431.dat" "$W/raw/62777.dat"
cp "$R/tier15 soulweapon/50228.dat" "$W/raw/62778.dat"
cp "$R/tier 15 soul mage/wear/91266.dat" "$W/raw/62779.dat"
cp "$R/tier 15 soul mage/drop/91267.dat" "$W/raw/62780.dat"
cp "$R/tier15 soul range/wear/139736.dat" "$W/raw/62781.dat"
cp "$R/tier15 soul range/drop/139735.dat" "$W/raw/62782.dat"
run InsertRawModels apply "$C" "$(w "$W/raw")" | tail -1

printf "model 62776 5600\nmodel 62777 5600\n" > "$W/dec.txt"
run DecimateModel "$C" "$(w "$W/dec.txt")" "$(w "$W/dec")" | tail -2
run InsertRawModels replace "$C" "$(w "$W/dec")" | tail -1

run BossRig apply "$C" "$(w "$here/customs27_rigs/rig_vorgath.txt")" | tail -1
run BossRig apply "$C" "$(w "$here/customs27_rigs/rig_baelgor.txt")" | tail -1

run CreateRecoloredTextures apply "$C" "$(w "$here/customs27_rigs/t15_textures.txt")" | tail -1
run TextureModelFaces "$C" "$(w "$here/customs27_rigs/t15_models.txt")" "$(w "$W/tex")" | tail -1
run InsertRawModels apply "$C" "$(w "$W/tex")" | tail -1

# Radiant Soulblade worn model (2026-10-28): a one-handed sword now (longsword stance), so the worn copy 62788 moves the
# grip from the godsword two-hand hold onto the longsword hand (measured against Dragon longsword 564 / Saeldor 38270:
# guard to z -4, grip to y -97); the inventory model 62783 keeps its icon framing
run ShiftTextureModel "$C" 62783 62788 2,-13,16 - - "$(w "$W/shift")" | tail -1
# Soul Tree showcase copies (2026-10-29): every look's inventory model with its bounding box centred on the origin (the
# stage's model widget turns a mesh around its origin; the blades, held at the grip, orbited off the dais as specks).
# 62789-62791 the Radiant looks (the Soulblade's item recolours baked in: a model shown by id gets none), then the
# blade / recurve / spire base, empowered and ascended looks (their 231 model ids; rev241 remaps them to 6535x-65377). SoulWeaponCamera maps look -> showcase model.
run ShiftTextureModel "$C" 62783 62789 center - - "$(w "$W/shift")" recolor:7503,9030,7645,9050,7347,38076,7365,38096 | tail -1
for pair in 62787:62790 62785:62791 61211:62792 61213:62793 61236:62794 61215:62795 61217:62796 61221:62797 61223:62798 61225:62799 61229:62800; do
	run ShiftTextureModel "$C" "${pair%%:*}" "${pair##*:}" center - - "$(w "$W/shift")" | tail -1
done
run InsertRawModels apply "$C" "$(w "$W/shift")" | tail -1

# held weapons on layer 10 (the spire's raw 15 hid it, the bow's 4 sank it into the torso); Baelgor on one layer (its
# ripped per-face table drew the wings through the body at some angles)
printf "model 62784 global 10\nmodel 62786 global 10\nmodel 62777 global 5\n" > "$W/prio.txt"
run SetModelPriorities "$C" "$(w "$W/prio.txt")" "$(w "$W/prio")" | tail -1
run InsertRawModels replace "$C" "$(w "$W/prio")" | tail -1

java -cp "$(w "$STAGE");$CP" PatchCustoms27 export "$C" "$(w "$here/customs27")" 2>&1 | grep RESULT
