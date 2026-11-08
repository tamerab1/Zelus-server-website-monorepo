#!/usr/bin/env bash
# Rebuilds the rev 241 cache from scratch: fresh vanilla 241 + every stage, in order.
# Re-runnable: the inputs (our 231 cache, the vanilla caches, the manifest) are never modified.
#
#   tools/rev241-migration/build_cache241.sh <dataDir> <vanilla231CacheDir> <vanilla241CacheDir> [mapOverrides]
#
# dataDir      = the isolated data copy (contains cache/ = our rev 231 cache, region_keys.json, xteas.json);
#                output goes to <dataDir>/cache241
# vanilla dirs = extracted OpenRS2 caches 2247 (231) and 2746 (241); the 241 zip is archive/rev241/openrs2
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
data="$1"; v231="$2"; v241="$3"; overrides="${4:-4672,4904,12342}"
root="$(cd "$here/../../.." && pwd)"   # the "osrs server" folder
tool="$root/server zelus/.dev/cache-restore-tool"
keys="$root/archive/rev241/openrs2"
out="$data/cache241"
cls="$(mktemp -d)"

cp_jna="$(tr -d '\r\n' < "$tool/cp_with_jna.txt")"
gson="$(ls ~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.12.1/*/gson-2.12.1.jar | head -1)"
if command -v cygpath >/dev/null; then
	# Git Bash converts lone path arguments for Windows programs, but not ;-separated lists
	gson="$(cygpath -w "$gson")"; cls="$(cygpath -w "$cls")"; keys="$(cygpath -m "$keys")"; sep=";"
else sep=":"; fi
CP="$cp_jna$sep$gson"

echo "== compile"
javac -cp "$CP" -d "$cls" "$here"/cache/*.java "$here/spikeA/MapCodec.java" "$tool/src/SpliceItemOption.java"
run() { java -Xmx10g -cp "$cls$sep$CP" "$@"; }

echo "== fresh vanilla 241 copy -> $out"
rm -rf "$out"; mkdir -p "$out"
cp "$v241"/main_file_cache.* "$out/"

m="$here/remap/remap-241.json"
k231="$keys/openrs2-2247-keys.json;$keys/openrs2-2238-keys.json;$keys/openrs2-2228-keys.json;$keys/openrs2-2223-keys.json;$keys/openrs2-2216-keys.json;$keys/openrs2-2211-keys.json;$keys/openrs2-2206-keys.json"
echo "== stage 1: configs";      run BuildConfigs241    "$data/cache" "$v231" "$out" "$m" "$here/cache/build-configs-report.json"
echo "== stage 2: binary";       run BuildBinary241     "$data/cache" "$v231" "$out" "$m" "$here/cache/build-binary-report.json"
echo "== stage 3: maps";         run BuildMaps241       "$data/cache" "$v231" "$out" "$m" "$data/region_keys.json" "$data/xteas.json" "$k231" "$here/cache/build-maps-report.json" "$overrides" "$v241"
echo "== stage 4: interfaces";   run AlignOurs231 "$data/cache" "$v231" "$here/remap/ours-alignment-231.json"
                                run BuildInterfaces241 "$data/cache" "$v231" "$v241" "$out" "$m" "$here/cache/build-interfaces-report.json" "$here/remap/ours-alignment-231.json" "$here/remap/component-map-241.json"
echo "== stage 5: clientscripts"; run BuildScripts241   "$data/cache" "$v231" "$v241" "$out" "$m" "$here/cache/build-interfaces-report.json" "$here/cache/build-scripts-report.json" "$here/remap/ours-alignment-231.json" "$here/remap/component-map-241.json" "$here/cache/build-binary-report.json"
echo "== stage 6: raid 4 spotanims"; run PatchRaid4Spotanims "$out"
echo "== stage 7: customs27 (soul T15 weapons, Vorgath, Baelgor)"; run PatchCustoms27 apply "$here/assets/customs27" "$out"
echo "== stage 8: elite void knights"; run PatchEliteVoid apply "$out" "$here/assets/elitevoid"
echo "== stage 9: spellbook autocast + home teleport Animation options"; run PatchSpellbookAutocast apply "$out"
echo "== stage 10: pvm ticket"; run PatchPvmTicket apply "$out"
# stage 11 decodes rev 241 npc/obj definitions, so the rev 241 cache library goes ahead of the old one
lib241="$root/client zelus/client rev241/cache/build/libs/cache-1.13.4.jar"
if command -v cygpath >/dev/null; then lib241="$(cygpath -w "$lib241")"; fi
echo "== stage 11: pet recolour broker + shadow pets"
java -Xmx10g -cp "$cls$sep$lib241$sep$CP" PatchPetShadow apply "$out" "$data/pets/pet_recolor_exchange.json"
echo "== stage 12: Solarmane boss + pet"; run PatchSolarmane apply "$here/assets/solarmane" "$out"
echo "== stage 13: Solarmane set"; run PatchSolarmaneSet apply "$out" "$here/assets/solarmane_set"
echo "== stage 14: Chamber of Ascension arrow-head holes"; run PatchChamberArrowHoles apply "$out"
echo "== stage 15: model bank item pack"; run PatchModelBankPack apply "$out" "$here/assets/model_bank_pack"
echo "== stage 16: egg / relic equipment slot"; run PatchEggSlot apply "$out"
echo "== stage 20: egg-slot icons (12 designs x 5 tiers; after 15, before 17)"; run PatchEggIcons apply "$out" "$here/assets/egg_icons"
echo "== stage 17: bank placeholders for our new items"; run PatchPlaceholders apply "$out"
echo "== stage 18: GPU-safe board face priorities"; run PatchBoardPriorities apply "$out" "$here/assets/board_priorities"
echo "== stage 19: spec bars (enums 906 / 1739)"; run PatchSpecBars apply "$out"
echo "== stage 21: Colosseum of Trials (Reliquary object 29559, trial picker interface 1182)"; run PatchColosseum apply "$out" "$here/assets/colosseum"
echo "== stage 21b: Colosseum hall pillars removed for the trial hosts (user, 2026-11-07)"; run PatchRemoveLoc "$out" 29548,3252,5861,0,10,0 29548,3252,5849,0,10,0 33453,3237,5843,0,10,1 33453,3240,5843,0,10,1 33453,3243,5843,0,10,0 29548,3246,5843,0,10,0 29548,3225,5834,0,10,0 33453,3222,5843,0,10,1 33453,3219,5843,0,10,1 29548,3216,5843,0,10,0 29549,3210,5852,0,10,0 29549,3210,5858,0,10,0
echo "== stage 22: bond slot (after 16)"; run PatchBondSlot apply "$out"
echo "== stage 23: Morvath one size up (after 21)"; run PatchMorvathSize apply "$out"
echo "== stage 24: Solarmane room closed (the two openings, region 12100, user 2026-11-08)"; run PatchRemoveLoc "$out" +21884,3029,4402,0,0,0 +21884,3029,4401,0,0,0 +21884,3029,4400,0,0,0 +21884,3029,4399,0,0,0 +21884,3029,4398,0,0,0 +21884,3029,4397,0,0,0 +21884,3029,4396,0,0,0 +21886,3029,4395,0,3,0 +21884,3028,4395,0,0,1 +21884,3027,4395,0,0,1 +21884,3018,4392,0,0,1 +21884,3019,4392,0,0,1 +21884,3020,4392,0,0,1
echo "== stage 25: Beast icon in crimson (after 15 and 20)"; run PatchBeastRecolour apply "$out" "$here/assets/beast_recolour"
echo "== stage 26: Drak'thor hatchling, the Colosseum shop pet"; run PatchDrakthorPet apply "$out"
echo "== integrity"; run IntegrityCheck241 "$out" "$v241" "$here/cache/integrity-report.json" "$data/cache" "$v231"
echo "== done: $out"
