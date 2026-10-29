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
echo "== integrity"; run IntegrityCheck241 "$out" "$v241" "$here/cache/integrity-report.json" "$data/cache" "$v231"
echo "== done: $out"
