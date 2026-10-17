#!/usr/bin/env bash
# Emerald Draconies Warhammer 31774 spec bar (2026-10-17): cache enums 906 (cost 500) + 1739 (description).
# Server side: model/combat/special/melee/EmeraldDraconiesWarhammer.java. Run with the server STOPPED.
# Usage: build_emerald_warhammer_spec.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
java -cp "$B;$CP" AppendEnumEntries "$M" "$C" emerald_warhammer_spec_enums.txt 2>&1 | grep -v "SLF4J" | tail -3
echo "BUILD $M OK"
