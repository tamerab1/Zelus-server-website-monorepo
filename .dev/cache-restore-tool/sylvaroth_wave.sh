#!/usr/bin/env bash
# Sylvaroth's Splinter wave (2026-10-16): npc 30607 = Galvek's "Tsunami" 8099 copied with an emerald / golden-sap
# recolour of all 9 model colours (model 35017), so Sylvaroth's special no longer looks like Galvek's.
# Usage: sylvaroth_wave.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
java -cp "$3;$4" SpliceRecolorCopy "$1" "$2" npc 8099 30607 "Splinter wave" \
  "36382,23326,33003,9707,35402,21194,35389,22333,36754,24466,37774,25486,32883,10867,36271,22063,32993,9697" 2>&1 | grep -v SLF4J | tail -2
