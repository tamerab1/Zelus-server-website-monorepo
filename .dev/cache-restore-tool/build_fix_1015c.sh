#!/bin/bash
# 2026-10-15 fix round c: Teleports 1152 (category labels 12pt/66px, invisible active trim), Battle Presets 1155 (bronze
# hover), Cape Forge Master chathead + partyhat head (50,81,29), preview bounds over the stand animation (json).
# Usage: build_fix_1015c.sh verify|apply <cacheDir> <classesDir> <classpath> <dataDir>
set -euo pipefail
MODE="$1"; C="$2"; B="$3"; CP="$4"; D="$5"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
IFM=$([ "$MODE" = apply ] && echo rebuild || echo verify)
run BuildTeleportInterface $IFM "$C" | tail -1
run BuildBattlePresetsInterface $IFM "$C" | tail -1
run SetNpcModelListInPlace "$MODE" "$C" 3873 50,81,29 chathead | tail -1
if [ "$MODE" = apply ]; then
  run BuildTeleportPreviews json "$C" teleport_previews.txt "$(mktemp -d)" "$D/teleport_previews.json" | tail -1
fi
echo "BUILD $MODE OK"
