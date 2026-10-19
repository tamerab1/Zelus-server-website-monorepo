#!/usr/bin/env bash
# customs19 round 2 (2026-10-19), on top of build_custom_bosses.sh + build_customs19.sh:
#   1. worn models layered like the real pieces they replace (customs19_priorities.txt, TransferPriorities): the
#      Vermilion masori body had 480 faces on priority 0 -- under a cape's lining -- so capes drew through the torso
#   2. hand-made rigs + animations (BossRig, boss_rigs/rig_*.txt; each replaces its model, adds a skeleton / frame group
#      and four sequences idle / walk / attack / death -- applied in sequence-id order):
#        16206 Drak'thor 16111 / 16218+   16210 Vaelok 16112 / 16222+   16211 Kharaz 16113 / 16226+
#        16214 Nidhogg 16114 / 16230+     16200 Astraeus 16115 / 16234+ (extends the player skeleton: wing bones + hover)
#        16204 Malakor 16116 / 16238+ (extends the player skeleton, robe / wings re-lit)   16203 Malik 16117 / 16242+
#        16213 Terrax 16118 / 16246+ (player frames + a tail)   16212 Hulk 16119 / 16250 Ground Smash special + 16251 boulder throw
#      round 3: base player poses subdivided (the 21-cycle stand poses made the hover / wings update twice a second --
#      "frozen" after attacks), Drak'thor airborne + dive bite, Vaelok faster breath, Malik smoother stride
#   3. npc records rewritten (custom_bosses.txt): the new stand / walk, Gorgar / Hulk / Malik / Nidhogg bigger, 16212 Hulk
#   4. Vermilion masori icons re-framed upright (customs19_framing.txt, angles picked with IconAngleGrid)
# Server: npcs/combat/Custom_bosses.json (attack / death ids, Malakor MAGIC), CustomBossCombat (per-boss range, cast
# height and release frame, delayed melee impacts).
# Run with the server STOPPED on the cache as it was BEFORE round 2 (rounds 2 and 3 replay together).
# Usage: build_customs19_r2.sh apply <cachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
rm -rf priorities19
run TransferPriorities "$C" customs19_priorities.txt priorities19
run InsertRawModels replace "$C" priorities19 | tail -1
for r in 16206_drakthor 16210_vaelok 16211_kharaz 16214_nidhogg 16200_astraeus 16204_malakor 16203_malik 16213_terrax 16212_hulk; do
  run BossRig apply "$C" "boss_rigs/rig_$r.txt" | tail -1
done
run BuildCustomBosses npcs replace "$C" custom_bosses.txt | tail -1
mkdir -p frame_out
run AutoFrameItems apply "$C" customs19_framing.txt frame_out | tail -1
echo "BUILD customs19 r2 OK"
