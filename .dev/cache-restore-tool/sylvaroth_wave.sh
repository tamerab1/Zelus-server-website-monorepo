#!/usr/bin/env bash
# Sylvaroth's Splinter wave (2026-10-16): npc 30607 = Galvek's "Tsunami" 8099 copied verbatim (sorted insert), renamed, and
# given an emerald / golden-sap recolour of all 9 model colours (model 35017), so it no longer looks like Galvek's.
# NOT SpliceRecolorCopy: it appended the id after 100018 and the reference table's 2-byte id delta wrapped (30607 was
# stored as 161679), so the server never had npc 30607.
# Usage: sylvaroth_wave.sh apply <cachePath> <outDir> <classpath>
set -euo pipefail
[ "$1" = apply ] || { echo "apply only"; exit 1; }
C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
run CopyNpcVerbatimBatch apply "$C" "8099:30607" | tail -1
run EditConfigNameActions apply "$C" npc 30607 "name=Splinter wave" | tail -1
run EditNpcRecolorInPlace apply "$C" 30607 \
  "36382,23326,33003,9707,35402,21194,35389,22333,36754,24466,37774,25486,32883,10867,36271,22063,32993,9697" | tail -1
