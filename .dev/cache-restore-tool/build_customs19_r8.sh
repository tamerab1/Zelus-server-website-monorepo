#!/usr/bin/env bash
# customs19 round 8 (2026-10-19), on the live cache after round 7:
#   1. Vermilion masori body + chaps icons upright (customs19_framing.txt, angles solved by OrientIcon)
#   2. event-boss npcs 16215 Nidhogg (::gb) / 16216 Volcanus (::vb) (custom_bosses_events.txt)
# Server: GlobalNidhogg, VoteVolcanus, rotations in GlobalBosses / VoteBossHandler, npcs/combat/Custom_event_bosses.json.
# Run with the server STOPPED. Usage: build_customs19_r8.sh apply <cache> <cp>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
mkdir -p frame_out
run AutoFrameItems apply "$C" customs19_framing.txt frame_out | grep -E "3185[024]|complete"
run BuildCustomBosses npcs apply "$C" custom_bosses_events.txt | tail -3
echo "BUILD customs19 r8 OK"
