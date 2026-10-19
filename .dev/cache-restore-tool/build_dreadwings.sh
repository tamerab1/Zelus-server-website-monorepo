#!/usr/bin/env bash
# Vermilion Dreadwings (2026-10-19; source Desktop/customs19/custom wings/74025.dat, no drop model). Item 31868 / 31869.
#   1. worn 61279 = the rip as it came: every vertex on the upper torso label 8, so the wings move 1:1 with the chest and
#      stay on the back (a cape-label rig made the cape sway fling them off the back -- removed 2026-10-19);
#      inventory 61280 = the same mesh centred on the ground (TransformModel, dreadwings_transform.txt) -- both in dreadwings_raw
#   2. texture 264 / sprite 24626: lava 59 remapped crimson -> ember (dreadwings_texture.txt), on the red membrane
#   3. item on the Infernal cape record (dreadwings_items.txt); stats in item_info.json (31868)
#   4. worn mesh decimated 3017 -> 450, then DoubleSided -> 900: the single-sided membrane was see-through from one side
#      (ground / armour through the wings); back-facing faces on 10 (over the head / helmet from behind), twins on 1
#   5. icon framing. NOT the worn-model audit: 61279 is AuditWornModels.HAND_BUILT (its cape rule would undo step 4).
# Run with the server STOPPED. Usage: build_dreadwings.sh apply <cache> <cp> <gsonJar> <itemInfoJson>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; G="$4"; J="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run InsertRawModels apply "$C" dreadwings_raw | tail -1
run CreateRecoloredTextures apply "$C" dreadwings_texture.txt | tail -1
rm -rf dreadwings_tex && mkdir dreadwings_tex
run TextureModelFaces "$C" dreadwings_textured.txt dreadwings_tex | tail -1
run InsertRawModels replace "$C" dreadwings_tex | tail -1
run CreateItemVariants apply "$C" dreadwings_items.txt | tail -1
rm -rf dreadwings_dec && run DecimateModel "$C" dreadwings_decimate.txt dreadwings_dec | tail -1
run InsertRawModels replace "$C" dreadwings_dec | tail -1
rm -rf dreadwings_2s && run DoubleSided "$C" 61279 dreadwings_2s 1 10 | tail -1
run InsertRawModels replace "$C" dreadwings_2s | tail -1
mkdir -p frame_out
run AutoFrameItems apply "$C" dreadwings_framing.txt frame_out | tail -1
echo "BUILD dreadwings OK"
