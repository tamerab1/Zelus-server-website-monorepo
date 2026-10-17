#!/usr/bin/env bash
# Spinning/bobbing tops for the Zelus Upgrader (loc 60015, ankh) and Zelus Workbench (loc 60016, orb), 2026-10-17.
# Adds skeletons + frame groups 16100/16101 (index 1/0), seqs 16201/16202 (config 12), loc opcode 24 (config 6).
# Run once (refuses ids that already exist). Usage: build_loc_spin.sh verify|apply <cachePath> <classesDir> <classpath>
set -euo pipefail
java -cp "$3;$4" BuildLocSpinAnims "$1" "$2" 2>&1 | grep -v SLF4J
