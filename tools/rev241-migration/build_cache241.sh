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
echo "== stage 28: the Karn banners (objects 65042 K, 65043 KARN; textures 509-512)"; run PatchKarnBanners apply "$out" "$here/assets/banners"
echo "== stage 29: the home map from RSPSi (region 12342, assets/home_map, user 2026-11-12: land3/obj3, exported from the patched map; u: = blocked flags RSPSi left where the old bank booths/walls stood; 2026-11-14: x3082-3086,y3474 by the Pit and the old Karn Teleports square 3088-3089,3484-3485)"; run PatchRegionMap apply "$out" 12342 "$here/assets/home_map/land3.dat" "$here/assets/home_map/obj3.dat" "$m" u:3084,3516,0 u:3085,3516,0 u:3093,3516,0 u:3094,3516,0 u:3095,3516,0 u:3085,3515,0 u:3093,3515,0 u:3094,3515,0 u:3085,3514,0 u:3085,3513,0 u:3093,3513,0 u:3094,3513,0 u:3085,3512,0 u:3093,3512,0 u:3094,3512,0 u:3085,3511,0 u:3093,3511,0 u:3094,3511,0 u:3082,3474,0 u:3083,3474,0 u:3084,3474,0 u:3085,3474,0 u:3086,3474,0 u:3088,3485,0 u:3089,3485,0 u:3088,3484,0 u:3089,3484,0
echo "== stage 30: the Karn look (round 2 2026-11-12): Teleports 1152, Soul Tree 1176, starter 1150 + soul pick 1175, Titles 1160, streaks 1109, Relic Forge 1158, Perks 1151, Karn Forge 1153, assets/karn_ui"; run PatchKarnUi apply "$out" "$here/assets/karn_ui" "$m"
echo "== stage 31: home npcs that could not turn (Osman 1810, Title Manager 1867)"; run PatchNpcTurn apply "$out" 1810 1867
echo "== stage 32: the home Portal Nexus becomes Karn Teleports (33410: name + Karn recolour)"; run PatchKarnNexus apply "$out"
echo "== stage 33: the Ornate pool of Rejuvenation in the Karn colours (29241, recoloured in place: POH pools too)"; run PatchKarnPool apply "$out"
echo "== stage 34: the Statue of Fortune becomes the Karn Statue (65473-65475: name + Karn recolour)"; run PatchKarnStatue apply "$out"
echo "== stage 35: merged Teleports previews for the PvM overhaul bosses (Moons 62778, Hueycoatl 62779)"; run PatchTeleportPreviews apply "$out" "$here/assets/teleport_previews_pvm"
echo "== stage 36: the starter Soulbound recurve (42630) wears the Antler Bow (40004) look (user 2026-11-14)"; run PatchRecurveLook apply "$out"
echo "== stage 37: the Karn master cape (42766, Attack master cape with the K emblem; models from assets/karn_cape, after 28)"; run PatchKarnMasterCape apply "$out" "$here/assets/karn_cape"
echo "== stage 38: Ava's of Karn (42767, Ava's assembler 22109 recoloured crimson / gold)"; run PatchKarnAva apply "$out"
echo "== stage 39: the Achievement Master (npc 30004, takes over from Zezu; after 37)"; run PatchAchievementMaster apply "$out" "$here/assets/achievement_master"
echo "== stage 40: Valkyrie set face priorities (models from assets/valkyrie_fix, after 15)"; run PatchModelFiles apply "$out" "$here/assets/valkyrie_fix"
echo "== stage 41: master / ranged capes worn as capes (wearpos 1, no wearpos2/3; after 37)"; run PatchCapeSlots apply "$out"
echo "== stage 42: the Karn hub windows (Welcome 1185, Battle Pass 1186, Best in Slot 1187; sprites 25220-25255, assets/karn_hub)"; run PatchKarnHub apply "$out" "$here/assets/karn_hub"
echo "== stage 43: the official Royal Titans rewards our old custom ids shadowed (30622/30626/30627/30628 -> 42768-42771)"; run PatchTitanRewards apply "$out" "$v241"
echo "== stage 44: the Karn sword (42773: the Fleshrender Sword 59537 recoloured crimson / gold, clean inventory framing)"; run PatchKarnSword apply "$out"
echo "== stage 45: official rev 241 items our records left blank (30000-30999: Hueycoatl rewards, Varlamore potions, ...) restored in place"; run PatchRestoreBlankOfficial apply "$out" "$v241"
echo "== stage 46: the Karn weapons' animated textures (270/271, models 62821-62824; the Volcanic bow becomes the Karn bow; after 44)"; run PatchKarnWeapons apply "$out" "$here/assets/karn_weapons"
echo "== stage 47: the Karn blackjack card back (sprite 5830)"; run PatchKarnCards apply "$out" "$here/assets/karn_cards"
echo "== stage 48: item renames (40092 Donator mystery box (u) -> Battle pass box + Open, user 2026-11-16)"; run PatchItemNames apply "$out"
echo "== stage 49: region 13128 (Clan Wars arena copy, unused) cleared of its 20 tables (loc 3834, user 2026-11-16)"; run PatchRemoveLoc "$out" 3834,3275,4619,0,10,1 3834,3275,4627,0,10,3 3834,3275,4635,0,10,1 3834,3275,4643,0,10,3 3834,3275,4651,0,10,1 3834,3275,4659,0,10,3 3834,3280,4619,0,10,1 3834,3280,4627,0,10,3 3834,3280,4635,0,10,1 3834,3280,4643,0,10,3 3834,3280,4651,0,10,1 3834,3280,4659,0,10,3 3834,3310,4619,0,10,3 3834,3310,4627,0,10,1 3834,3310,4651,0,10,3 3834,3310,4659,0,10,1 3834,3315,4619,0,10,3 3834,3315,4627,0,10,1 3834,3315,4651,0,10,3 3834,3315,4659,0,10,1
echo "== stage 50: the Five Seals Minigame Manager (npc 30003 from Taala 13996, user 2026-11-16)"; run PatchFiveSealsNpc apply "$out"
echo "== stage 51: The Five Seals Karnwood tree/stump + Bloodgold rocks (objects 63307-63310), Karnwood logs / Bloodgold ore (42775/42776), Crimson Ankou npc 30261, textures 272/273 (assets/karnwood, user 2026-11-16)"; run PatchKarnwood apply "$out" "$here/assets/karnwood"
echo "== stage 52: The Five Seals creatures (npcs 30262-30266) + conduits / shadow nodes / supply altar / vault / Seal altar (objects 63311-63317), Karn recolours (user 2026-11-16)"; run PatchFiveSealsArt apply "$out"
echo "== stage 53: region 13128's five room altars (7812) become the Karn Seal altar (63317, after 52)"; run PatchRemoveLoc "$out" 7812,3272,4623,0,10,1 7812,3272,4639,0,10,1 7812,3272,4655,0,10,1 7812,3319,4623,0,10,3 7812,3319,4655,0,10,3 +63317,3272,4623,0,10,1 +63317,3272,4639,0,10,1 +63317,3272,4655,0,10,1 +63317,3319,4623,0,10,3 +63317,3319,4655,0,10,3
echo "== stage 54: the Hall of Seals board (object 63318, model 62825, sprites 25310-25313, textures 274-277; BuildChampionshipBoard export -> assets/seals_board, user 2026-11-16)"; run PatchSealsBoard apply "$out" "$here/assets/seals_board"
echo "== stage 55: the Ascension sigil, tiers 1-5 (items 42777-42781 from the Deadman sigil 25996, Karn recolours; replaces the Karn partyhat in The Five Seals, user 2026-11-17)"; run PatchAscensionSigil apply "$out"
echo "== stage 56: The Sovereign Vault loot window (interface 1193 = tfa_chests 973 retitled; The Five Seals full-clear rewards, user 2026-11-17)"; run PatchSealsVaultIf apply "$out"
echo "== stage 57: the Hellfang crossbow held like a crossbow (worn model 65472 rotated upright, assets/hellfang_fix, RotateHellfang; user 2026-11-17)"; run PatchModelFiles apply "$out" "$here/assets/hellfang_fix"
echo "== stage 58: Lil' Vorgath, The Five Seals pet (npc 30267 + item 42782, user 2026-11-17)"; run PatchVorgathPet apply "$out"
echo "== stage 59: the Hellfang set's own bank placeholders 42707-42713 (they borrowed the cannonballs', user 2026-11-17)"; run PatchHellfangPlaceholders apply "$out"
echo "== stage 60: The Five Seals hub: the ladder hut (3308-3311,4639-4643, both planes) removed; the Hall of Seals board 63318 in the middle of the sunken square (3309,4639, facing west) and the exit ladder 24082 free-standing behind it (3312,4640) (user 2026-11-17)"; run PatchRemoveLoc "$out" 3165,3308,4639,1,10,1 3430,3308,4641,0,2,3 3167,3308,4641,1,2,3 3153,3308,4642,0,0,0 3167,3308,4642,1,0,0 3152,3308,4643,0,2,0 3167,3308,4643,1,2,0 3163,3309,4640,0,10,2 3161,3309,4641,0,0,3 3167,3309,4641,1,0,3 3167,3309,4642,1,22,0 3152,3309,4643,0,0,1 3167,3309,4643,1,0,1 738,3310,4639,0,22,0 3164,3310,4640,0,10,0 3160,3310,4641,0,0,3 3167,3310,4641,1,0,3 24082,3310,4642,0,10,3 24084,3310,4642,1,10,3 3152,3310,4643,0,0,1 3167,3310,4643,1,0,1 3430,3311,4641,0,2,2 3167,3311,4641,1,2,2 3152,3311,4642,0,0,2 3167,3311,4642,1,0,2 3152,3311,4643,0,2,1 3167,3311,4643,1,2,1 +63318,3309,4639,0,10,2 +24082,3312,4640,0,10,3
echo "== stage 63: The Five Seals shop rewards: Duskwarden / Hellfang ornament kits + (or) pieces 42803-42815, Sealbreaker cape 42805, Gilded Lil' Vorgath 42806 / npc 30268 (before 61, which gives them placeholders; user 2026-11-18)"; run PatchSealsRewards apply "$out"
echo "== stage 61: item def repairs: one Wield/Wear option instead of two (400xx customs) + every custom item's own bank placeholder (ids from 64000, kept in assets/item_placeholders.txt; after every item stage, user 2026-11-18)"; run PatchItemFixes apply "$out" "$v241" "$here/assets/item_placeholders.txt"
echo "== stage 62: the Keeper of the Seals (npc 30003) as a man in the Duskwarden helm / platebody / platelegs + Starweave boots (after 50 and 61, user 2026-11-18)"; run PatchKeeperOutfit apply "$out"
echo "== stage 27: Zelus -> Karn rebrand (every config / interface / script string; keep last)"; run PatchRebrandKarn apply "$out"
echo "== integrity"; run IntegrityCheck241 "$out" "$v241" "$here/cache/integrity-report.json" "$data/cache" "$v231"
echo "== done: $out"
