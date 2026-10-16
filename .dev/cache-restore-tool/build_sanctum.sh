#!/usr/bin/env bash
# The Sanctum / Obsidian Reliquary (2026-10-16), round 2. Run ONCE on a cache WITHOUT any Sanctum build (restore the
# *.bak_sanctum_20261016_143143 files first): every step refuses ids/archives that already exist.
#  1. BuildSanctumMap: de-snowed God Wars copy, per-wing boss colours -> regions 7850/7851/8106/8107 + themed rock copies
#  2. BuildSanctumHub: ToB Bloat room copy without the central tank -> region 7852 (+ recoloured object copies)
#  3. build_sanctum_portals.sh: hub portals 60020-60023
#  4. shard_fix_items.txt + shard_framing.sh: Combat Pet Shard without the crystal shard's stack models
#  5. sizes: Sanguine Harrowlord 4x4 scale 250, Venomtongue 4x4 scale 200; Ironknuckle + Magmafist pets scale 125
#  6. showcase npcs 30601-30606: T3 pet copies with "Inspect", not followers, display scale
#  7. teleport preview model 61134 (reliquary_preview.txt) + its bounds merged into data/teleport_previews.json
# Usage: build_sanctum.sh apply <cachePath> <outDir> <classpath> <teleportPreviewsJson>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"; JSON="$5"
[ "$M" = apply ] || { echo "apply only"; exit 1; }
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
W=$(mktemp -d)
run BuildSanctumMap apply "$C" | tail -1
run BuildSanctumHub apply "$C" | tail -2
bash build_sanctum_portals.sh apply "$C" "$B" "$CP" | tail -1
run CreateItemVariants apply-replace "$C" shard_fix_items.txt | tail -1
bash shard_framing.sh apply "$C" "$B" "$CP" | tail -1
run EditNpcInPlace "$C" 30594 250 250 - - 4 | tail -1
run EditNpcInPlace "$C" 30597 200 200 - - 4 | tail -1
for n in 30579 30580 30581 30585 30586 30587; do run EditNpcInPlace "$C" $n 125 125 | tail -1; done
run CopyNpcVerbatimBatch apply "$C" "30578:30601,30581:30602,30584:30603,30587:30604,30590:30605,30593:30606" | tail -1
for spec in 30601:120 30602:150 30603:40 30604:150 30605:64 30606:64; do
  n=${spec%%:*}; s=${spec##*:}
  run EditConfigNameActions apply "$C" npc $n "0=Inspect" "2=-" | tail -1
  run EditNpcInPlace "$C" $n $s $s 111,122 | tail -1
done
run BuildTeleportPreviews build "$C" reliquary_preview.txt "$W/prev" "$W/reliquary.json" | tail -1
run InsertRawModels apply "$C" "$W/prev" | tail -1
python -I - "$W/reliquary.json" "$JSON" <<'PY'
import json, sys
new = json.load(open(sys.argv[1], encoding="utf-8"))
p = sys.argv[2]
s = open(p, encoding="utf-8").read().rstrip()
for k in new:
    if f'"{k}"' in s: raise SystemExit(k + " already in " + p)
assert s.endswith("}")
body = ",\n".join(f'  "{k}": ' + json.dumps(v, separators=(", ", ": ")) for k, v in new.items())
s = s[:-1].rstrip() + ",\n" + body + "\n}\n"
json.loads(s)
open(p, "w", encoding="utf-8", newline="\n").write(s)
print("preview bounds merged into", p)
PY
echo "BUILD $M OK"
