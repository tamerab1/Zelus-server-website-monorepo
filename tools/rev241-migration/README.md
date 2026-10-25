# Rev 231 -> 241 migration tooling (branch `upgrade-rev-241`)

These tools only read caches. They compile against `.dev/cache-restore-tool/fatjar` (plus `SpliceItemOption.java` from `.dev/cache-restore-tool/src`). Run them with the classpath in `cp_with_jna.txt`; vanilla 231 builds need JNA for bzip2.

Inputs live outside the repo in `osrs server/archive/rev241/` (OpenRS2 caches 2746 = rev 241 and 2247 = rev 231, plus keys and SHA256SUMS). The branch server reads the isolated `osrs server/data zelus rev241/`.

- `spikeA/MapCodec.java`: terrain + loc codec. 241 terrain = 231 tile grammar, then a u8 count of extra 64x64 layers. 241 locs = 231 grammar, no XTEA. Index 5 group = regionId; files 0 terrain, 1 locs, 2 environment preset, 3 = [0,0], 4 = [0].
- `spikeA/FinalCheck.java`: the go/no-go proof. Byte round-trips all vanilla 241 + 231 regions, checks against RuneLite's MapLoader, and converts every local region.
- `spikeB/ModelBudget.java`: custom model census, dedupe, and slot budget under 65535.
- `assessment/`: id-collision census, base-build match, and semantic def/model diffs.

## Id remap (`remap/`)
- `BuildRemapManifest.java` -> `remap-241.json`. For each id type it lists our custom ids that collide with vanilla 241 and where each one moves. Rules: a constant shift above 241max + 2x the 231->241 growth wherever it fits (this keeps arithmetic between custom ids, e.g. bond tiers / placeholder +100). Where it doesn't fit (models, objects under 65535), the ids are packed against the limit. Models with the same bytes as a vanilla 241 model reuse it. Varps include "phantom" varps (varbit base varps with no varp definition: 4829-4836). Every target is validated as free, unique and <= limit.
- 16 id types. New vs the first census: sound effects (145, all colliding), clientscripts (57), textures (86), overlay/inv/config18 (1 each), phantom varps (8).
- The cache build must ADD varp definitions up to the new varp ids: the 241 client sizes its varp arrays from the defined varps.

## Save migration (`saves/`)
- `save_migration_dryrun.py <manifest> <data> <report.md> [rules.json] [dumpDir]`: read-only census + simulated remap; writes only the report (and optional copies to dumpDir).
- `rules.json`: path classification, each rule checked against the server code. Special kinds: `varp-index-array` (Player.varps is indexed by varp id: values move slots, the old slot is cleared, the destination must be empty) and `packed-var-key` (savedConfigs keys = id << 16 | isVarbit).
- `dryrun-local-20261025.md`: local dev data verdict READY (1108 files, 532 item fields, 0 violations, 6 NUL-filled orphan files passed through). Synthetic tests of the varp / packed-key / item-key transforms all pass, and the "destination not empty" violation is caught.

## Static data remap (`data/`)
- `remap_data.py <manifest> rules.json <data> <report.md> [--apply]`: remaps ids in the server's static data (drops, spawns, combat, shops, item_info, pets, deals, weapon/shield types, teleport previews) and renames id-named files (`npcs/drops/newDrops/<npc>.json`, `item_icons/<item>.png`). It leaves `runtime/`, `saves/` and `gear_loadouts/` to the save migration, and skips caches, vanilla dumps and `*backup*` dirs. Dry run by default.
- The files are lenient Gson JSON (`#`, `//` and `/* */` comments, unquoted keys), so strict parsers fail. `lenjson.py` tokenizes them and numbers are patched in place by byte span, which leaves comments, spacing and CRLF untouched. Shop YAML is patched line by line under `defaultStock:`.
- `--apply` writes the marker `.rev241-data-remapped` and refuses to run twice. The model map has a chain, so a second pass would not be a no-op.
- The report lists "unruled" numbers: a moving old id at an id-looking key with no rule. Every one must be explained before cutover.
- Verified 2026-10-25 on a copy of the local data: 658 replacements in 39 files, 468 renames, 0 unruled. Every change is a mapped number (non-numeric bytes identical), and a second dry run finds nothing left to move.
