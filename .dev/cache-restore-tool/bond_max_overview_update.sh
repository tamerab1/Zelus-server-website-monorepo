#!/bin/bash
# In-place update of already-created bond variants: T5 names -> "(MAX)", option slot 3 -> "Overview" on all 66 bond ids.
# Usage: bond_max_overview_update.sh verify|apply
C="../../../data zelus/data/cache"; M=$1
N=(); O=()
for pair in "31340 Bond of the Blood Titan" "31350 Bond of the Slayer King" "31360 Bond of the Astral Archmage" "31370 Bond of the Sovereign Monarch" "31380 Bond of the Artisan" "31390 Bond of the Void Deadeye"; do
  set -- $pair; b=$1; shift; name="$*"
  N+=("$((b+3))=$name (MAX)" "$((b+8))=$name (MAX) (attuned)")
  for k in 0 1 2 3 4 5 6 7 8; do O+=("$((b+k))=3:Overview"); done
done
for i in 60277 60278 60279 60280 60281 60282 31153 31154 31155 31156 31157 31158; do O+=("$i=3:Overview"); done
java -cp "fatjar;out_perkers" UpdateDefsInPlace $M "$C" itemname "${N[@]}" 2>&1 | grep -v SLF4J | tail -3
OUT=$(java -cp "fatjar;out_perkers" UpdateDefsInPlace $M "$C" itemop "${O[@]}" 2>&1 | grep -v SLF4J)
echo "$OUT" | grep -c "Overview"; echo "$OUT" | tail -1
