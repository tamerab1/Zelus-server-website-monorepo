#!/usr/bin/env bash
# Division Rivals Rug (2026-10-17): objects 62397-62441 + models 61142-61186 (one rug piece per tile, clones of Rug 6761, type 22), sprites 24558-24573,
# textures 237-252; round 7-tile rug, see src/BuildRivalsRug.java. Spawned at 3360,3232 type 22 in custom_objects.json.
# Run with the server STOPPED. Usage: build_rivals_rug.sh verify|apply <cachePath> <previewDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; P="$3"; CP="$4"
java -cp "out_whips;out;$CP" BuildRivalsRug "$M" "$C" "$P" 62397 61142 24558 3360 3232 2>&1 | grep -v "SLF4J" | tail -3
echo "BUILD $M OK"
