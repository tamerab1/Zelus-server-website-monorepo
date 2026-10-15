#!/bin/bash
# Fire cape themes (2026-10-15): textures 205-207 (copies of the Fire cape lava 40) + retexture/recolour 59983-59985.
# On a cache that already has 205-207 (local, 2026-10-15) run fire_cape_themes_r2.txt instead of fire_cape_themes.txt.
# Usage: build_fire_cape_themes.sh verify|apply <cacheDir> <classesDir> <classpath>. Stops on the first failure.
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v SLF4J; return ${PIPESTATUS[0]}; }
run CreateRecoloredTextures "$M" "$C" fire_cape_themes.txt
run SetItemColoursInPlace "$M" "$C" fire_cape_items.txt
echo "BUILD $M OK"
