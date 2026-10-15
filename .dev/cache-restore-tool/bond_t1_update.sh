#!/bin/bash
# Renames the 12 T1 bond ids to "(T1)" and sets options [Attune, Upgrade, Inspect, Overview, Drop]. Usage: bond_t1_update.sh verify|apply
C="../../../data zelus/data/cache"; M=$1
N=(); O=()
for pair in "60277 31153 Bond of the Blood Titan" "60278 31154 Bond of the Slayer King" "60279 31155 Bond of the Astral Archmage" "60280 31156 Bond of the Sovereign Monarch" "60281 31157 Bond of the Artisan" "60282 31158 Bond of the Void Deadeye"; do
  set -- $pair; a=$1; b=$2; shift 2; name="$*"
  for i in $a $b; do N+=("$i=$name (T1)"); O+=("$i=0:Attune" "$i=1:Upgrade" "$i=2:Inspect" "$i=3:Overview"); done
done
java -cp "fatjar;out_perkers" UpdateDefsInPlace $M "$C" itemname "${N[@]}" 2>&1 | grep -v SLF4J | tail -2 && \
java -cp "fatjar;out_perkers" UpdateDefsInPlace $M "$C" itemop "${O[@]}" 2>&1 | grep -v SLF4J | tail -4
