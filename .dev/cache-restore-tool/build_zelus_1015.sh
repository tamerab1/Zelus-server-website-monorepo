#!/bin/bash
# 2026-10-15 batch: Referrals 1165, Donator Deals 1166 + the 891 "Deals" button, Cape Forge 1167, cyan/gold home
# reskin (pool 29241, Portal Nexus 33410) + cyan pool splash gfx 4295, NPC 5523 "Deals" op, object 60015 "Cape Forge" op.
# Usage: build_zelus_1015.sh verify|apply <cacheDir> <classesDir> <classpath>. Stops on the first failure.
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
run BuildReferralInterface "$M" "$C"
run BuildDonatorDealsInterface "$M" "$C"
run BuildCapeForgeInterface "$M" "$C"
run AddShopDealsButton "$M" "$C"
run RecolorObjectSpec "$M" "$C" zelus_home_reskin.txt
run BuildBossVariants gfx "$M" "$C" pool_cyan_gfx.txt
run EditConfigNameActions "$M" "$C" npc 5523 2=Deals
run EditConfigNameActions "$M" "$C" object 60015 "2=Cape Forge"
echo "BUILD $M OK"
