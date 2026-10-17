#!/usr/bin/env bash
# Statue of Fortune (locs 62393-62395, 3x3, models 61136-61138, skeleton/frames 16102-16104, seqs 16203-16205) and its window
# (interface 1173), 2026-10-17. Run once with the server stopped (each builder refuses ids that already exist; use the
# builders' rebuild modes to replace them in place).
# Usage: build_fortune.sh verify|apply <cachePath> <classesDir> <classpath> <previewDir>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"; P="$5"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
run BuildFortuneStatue "$M" "$C" "$P" | tail -2
run BuildFortuneInterface "$M" "$C" | tail -1
echo "BUILD $M OK"
