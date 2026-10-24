# Rev 231 -> 241 migration tooling (branch `upgrade-rev-241`)

These tools only read caches. They compile against `.dev/cache-restore-tool/fatjar` (plus `SpliceItemOption.java` from `.dev/cache-restore-tool/src`). Run them with the classpath in `cp_with_jna.txt`; vanilla 231 builds need JNA for bzip2.

Inputs live outside the repo in `osrs server/archive/rev241/` (OpenRS2 caches 2746 = rev 241 and 2247 = rev 231, plus keys and SHA256SUMS). The branch server reads the isolated `osrs server/data zelus rev241/`.

- `spikeA/MapCodec.java`: terrain + loc codec. 241 terrain = 231 tile grammar, then a u8 count of extra 64x64 layers. 241 locs = 231 grammar, no XTEA. Index 5 group = regionId; files 0 terrain, 1 locs, 2 environment preset, 3 = [0,0], 4 = [0].
- `spikeA/FinalCheck.java`: the go/no-go proof. Byte round-trips all vanilla 241 + 231 regions, checks against RuneLite's MapLoader, and converts every local region.
- `spikeB/ModelBudget.java`: custom model census, dedupe, and slot budget under 65535.
- `assessment/`: id-collision census, base-build match, and semantic def/model diffs.
