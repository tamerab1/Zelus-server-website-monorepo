#!/usr/bin/env bash
# Zelus Blackjack Dealer (2026-10-18): npc 30609, a clone of the Rivals Overseer 3351 (stand 808 / walk 819), dressed as
# a casino dealer: Dark tuxedo jacket + sleeves (31810 / 31801), Dark tuxedo cuffs (31780), Dark trousers (31787, its white faces baked black: model 61210), Dark
# tuxedo shoes (31821), and the signature touch -- the Dark bow tie 31768 baked in Zelus crimson (model 61209). Male
# hair / jaw kits 230 / 249 (the jacket has its own sleeves, so no arm kit); chathead = the kit heads 63 / 81. 3351's
# npc-level recolour is replaced with a no-op pair. Ops: Talk-to / Play Blackjack (casino.BlackjackTable on the server).
# Run with the server STOPPED. Usage: build_blackjack_dealer.sh verify|apply <cachePath> <bakedModelsDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; D="$3"; CP="$4"
run() { java -cp "out_whips;out;$CP" "$@" 2>&1 | grep -v "SLF4J" | tail -1; }
mkdir -p "$D"
# bow tie: black shades 4 / 8 / 12 / 16 -> crimson (hue 0, sat 7) 922 / 930 / 938 / 946
run BakeModelRecolorRetexture bake "$C" 31768 61209 "$D" 4,922,8,930,12,938,16,946 -
# trousers: their white (127) faces showed as white shins below the knee -> tuxedo black (12)
run BakeModelRecolorRetexture bake "$C" 31787 61210 "$D" 127,12 -
run InsertRawModels "$M" "$C" "$D"
run SpliceNewNpc "$M" "$C" 3351 30609 "Blackjack Dealer" 61209
[ "$M" = verify ] && { echo "VERIFY: later steps need the npc; run them on a scratch copy with apply"; exit 0; }
run SetNpcModelListInPlace "$M" "$C" 30609 230,249,31810,31801,31780,61210,31821,61209
run SetNpcModelListInPlace "$M" "$C" 30609 63,81 chathead
run EditNpcRecolorInPlace "$M" "$C" 30609 1,1
run SetNpcActionsInPlace "$M" "$C" 30609 "Talk-to,Play Blackjack,-,-,-"
echo "BUILD $M OK"
