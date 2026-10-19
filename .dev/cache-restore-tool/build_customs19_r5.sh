#!/usr/bin/env bash
# customs19 round 5 (2026-10-19), on the live cache AFTER build_customs19_r2.sh (rounds 2-4):
#   1. Vermilion Dreadwings 31868 (build_dreadwings.sh: rigged worn wings, crimson texture 264, item, audit, icon)
#   2. Zaryte Stormpiercer special "Glacial Evoke": ice copy of the Evoke bolt -> spotanim 4428 (stormpiercer_gfx.txt)
#      + spec bar enums 906 / 1739 (stormpiercer_spec_enums.txt); server GlacialEvoke
#   3. Vermilion masori body icon flipped upright (customs19_framing.txt: 1536,1792)
# Server-side (no cache): Vorkath set drops (newDrops 8059 / 8061, 1/1000 each, broadcast) + Vorkath collection log.
# Run with the server STOPPED. Usage: build_customs19_r5.sh apply <cache> <cp> <gsonJar> <itemInfoJson>
set -euo pipefail
M="$1"; C="$2"; CP="$3"; G="$4"; J="$5"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$G;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
./build_dreadwings.sh apply "$C" "$CP" "$G" "$J"
run BuildBossVariants gfx apply "$C" stormpiercer_gfx.txt | tail -1
run AppendEnumEntries apply "$C" stormpiercer_spec_enums.txt | tail -1
mkdir -p frame_out
run AutoFrameItems apply "$C" customs19_framing.txt frame_out | grep -E "31852|complete"
echo "BUILD customs19 r5 OK"
