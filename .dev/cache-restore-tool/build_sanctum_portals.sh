#!/usr/bin/env bash
# Sanctum hub portals (2026-10-16): objects 60020-60023 = portal 4150 clones, named after their boss, option "Enter",
# recoloured to the boss colour (sanctum_portals_recolor.txt): crimson Harrowlord, void Gloomfang, toxic Venomtongue,
# azure sealed (Saradomin wing, reserved for the Pyretyrant). Server: Sanctum.java; spawns: objects/spawns/sanctum.json.
# Usage: build_sanctum_portals.sh apply <cachePath> <outDir> <classpath>   (apply only: each step needs the previous one)
set -euo pipefail
M="$1"; C="$2"; B="$3"; CP="$4"
[ "$M" = apply ] || { echo "apply only"; exit 1; }
run() { java -cp "$B;$CP" "$@" 2>&1 | grep -v "SLF4J\|^CONST"; return ${PIPESTATUS[0]}; }
names=("Sanguine Harrowlord Portal" "Gloomfang Devourer Portal" "Venomtongue Abomination Portal" "Sealed Portal")
for i in 0 1 2 3; do
  id=$((60020 + i))
  run SpliceNewObject apply "$C" 4150 $id "${names[$i]}" 2318 | tail -1
  run EditConfigNameActions apply "$C" object $id "0=Enter" | tail -1
done
run RecolorObjectSpec apply "$C" sanctum_portals_recolor.txt | tail -2
echo "BUILD $M OK"
