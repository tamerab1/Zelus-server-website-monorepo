#!/bin/bash
# 2026-10-15 round f: Market 1161 (lighter zebra rows + item slots, arrow sprites on the pager), pager arrow sprites
# 24528-24531 (BuildBrandIcons), Cape Forge Master chathead with a prismatic-textured partyhat head (model 60131).
# Usage: build_fix_1015f.sh verify|apply <cacheDir> <classesDir> <classpath>
set -euo pipefail
MODE="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
IFM=$([ "$MODE" = apply ] && echo rebuild || echo verify)
run BuildMarketInterface $IFM "$C" | tail -1
run BuildBrandIcons "$MODE" "$C" | tail -1
W=$(mktemp -d)
run TextureModelFaces "$C" prismatic_head.txt "$W"
run InsertRawModels "$MODE" "$C" "$W" | tail -1
run SetNpcModelListInPlace "$MODE" "$C" 3873 50,81,60131 chathead | tail -1
echo "BUILD $MODE OK"
