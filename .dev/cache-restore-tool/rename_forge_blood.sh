#!/usr/bin/env bash
# Renames forge items to fit the obsidian/blood-crimson re-skin (forge_blood_renames.txt), name only (SetItemName op2).
# Usage: rename_forge_blood.sh verify|apply <cachePath> <outDir> <classpath>
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
grep -v '^#' forge_blood_renames.txt | grep -v '^$' | while IFS='|' read -r id name; do
  java -cp "$B;$CP" SetItemName "$M" "$C" "$id" "$name" 2>&1 | grep -v "SLF4J" | tail -1
done
echo "RENAME $M OK"
