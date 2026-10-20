#!/usr/bin/env bash
# Armor batch fix 1 (2026-10-20), on top of build_armor_1020.sh:
#   1. 31912 Hellfang claws -> Hellfang crossbow: record rebuilt on the Armadyl crossbow (armor_1020_fix1_items.txt,
#      from gen_armor_1020.py) + icon re-framed; item_info.json: ARMADYL_CROSSBOW / ranged_weapon CROSSBOW / 80 Ranged
#   2. Assassin greatsword special "Umbral Execution": black spotanims 4429 / 4430 (armor_1020_fix_gfx.txt) + spec bar
#      enums 906 / 1739 (armor_1020_fix_enums.txt); server AssassinGreatsword
#   3. starter screen 1150 rebuilt (BuildStarterInterface, compiled to out_starter): no gold wireframe -- account rows
#      sink into recessed stone, difficulties are bezel buttons that press in (server: gold name on the selected row)
#   4. Sunstone crystal (Mad Angel): value 750 -> 2000 (high alch 1.2K; SetItemCost), then its record cloned onto its
#      ground stand-in 31168 (GroundItem proxy: was a bond clone, cost 2,000,000 = the "1.2m" on the floor, tradeable)
#   5. the other 62 ground stand-ins with the same bond-clone record: each gets its real item's record
#      (ground_proxies_fix.txt)
# Run with the server STOPPED. Usage: build_armor_1020_fix1.sh apply <cachePath> <classpath>
set -euo pipefail
M="$1"; C="$2"; CP="$3"
[ "$M" = apply ] || { echo "apply only: rehearse on a scratch cache copy"; exit 1; }
run() { java -cp "out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$(cat gson_jar.txt);$CP" "$@" 2>&1 | grep -v "SLF4J"; return ${PIPESTATUS[0]}; }
run CreateItemVariants apply-replace "$C" armor_1020_fix1_items.txt | tail -1
mkdir -p frame_out
run AutoFrameItems apply "$C" armor_1020_fix1_framing.txt frame_out | tail -2
run BuildBossVariants gfx apply "$C" armor_1020_fix_gfx.txt | tail -1
run AppendEnumEntries apply "$C" armor_1020_fix_enums.txt | tail -1
java -cp "out_starter;out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" BuildStarterInterface rebuild "$C" 2>&1 | grep -v SLF4J | tail -1
java -cp "out_free;out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" SetItemCost apply "$C" 34032 2000 2>&1 | grep -v SLF4J | tail -2
java -cp "out_free;out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" CloneItemDefBytes "$C" 34032 31168 2>&1 | grep -v SLF4J | tail -1
grep -v "^#" ground_proxies_fix.txt | while read real proxy name; do
  java -cp "out_free;out_bossrig;out_soultree;out;out_whips;out_perkers;fatjar;$CP" CloneItemDefBytes "$C" "$real" "$proxy" 2>&1 | grep -q "^Cloned" || { echo "proxy $proxy FAILED"; exit 1; }
done
echo "ground stand-ins: $(grep -vc "^#" ground_proxies_fix.txt) cloned"
echo "BUILD armor 1020 fix1 OK"
