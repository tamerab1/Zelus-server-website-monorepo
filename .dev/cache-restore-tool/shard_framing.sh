#!/usr/bin/env bash
# Combat Pet Shard 31816 icon framing (2026-10-16): the crystal-shard framing (zoom 1032, edge-on) made it a dark
# sliver; this face-on framing matches the Astral Spore's size and brightness. Safe to re-run (in-place override).
# Usage: shard_framing.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
java -cp "$3;$4" UpdateItemFraming "$1" "$2" 31816 560 300 1800 0 2>&1 | grep -v SLF4J
