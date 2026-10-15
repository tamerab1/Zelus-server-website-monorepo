#!/bin/bash
# 2026-10-15 round d: Zelus Market 1161 (pressed-stone tabs/toggles, recessed search, zebra rows with sunken item slots,
# centred footer), Bonds & Auras (recessed selector slots, bronze selected trim), Quick tools brand sprites 24526/24527.
# Usage: build_polish_1015d.sh verify|apply <cacheDir> <classesDir> <classpath>
set -euo pipefail
MODE="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST\|^ *[0-9]* type="; return ${PIPESTATUS[0]}; }
IFM=$([ "$MODE" = apply ] && echo rebuild || echo verify)
run BuildMarketInterface $IFM "$C" | tail -1
run BuildBondsInterface $IFM "$C" | tail -1
run BuildBrandIcons "$MODE" "$C" | tail -1
echo "BUILD $MODE OK"
