#!/usr/bin/env bash
# The Challenge Bell (2026-10-17): object 62396 = The Cloister Bell 31684 scaled to 42/128 (545 -> ~179 tall, ~1.3 tiles wide; was 34, user asked one size bigger),
# renamed, Ring kept, recoloured toward a war bell (challenge_bell_recolor.txt). Run with the server STOPPED.
# Usage: build_challenge_bell.sh verify|apply <cachePath> <outDir> <classpath>   (verify = full rehearsal on a scratch copy)
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
if [ "$M" = verify ]; then
  W="${SCRATCH:?set SCRATCH to an empty scratch dir}"; mkdir -p "$W"
  cp "$C"/main_file_cache.dat2 "$W"/; for i in $(seq 0 22) 255; do cp "$C"/main_file_cache.idx$i "$W"/; done
  C="$W"
fi
run SpliceScaledObject apply "$C" 31684 62396 "The Challenge Bell" Ring 42 | tail -2
run RecolorObjectSpec apply "$C" challenge_bell_recolor.txt | tail -2
run DumpObjectFull "$C" 62396 | head -7
echo "BUILD $M OK"
