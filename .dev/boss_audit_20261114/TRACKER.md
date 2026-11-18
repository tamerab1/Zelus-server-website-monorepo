# Karn 1:1 OSRS sweep - every boss + raid (started 2026-11-14, user: "fix everything, nothing missing")
Per item: [ids] cache-id coverage (boss_audit.py) | [anim] combat json anims (anim_audit.py) | [reach] interactables reachable (RegionGrid audit) | [drops] vs wiki | [clog] vs cache enum (DONE all) | [mech] attacks/specials/gfx vs wiki+cache | [npcs/dialog] area npcs + handlers
Legend: OK / FIX(done) / TODO / N/A(custom)

## Done this session
- New bosses: Royal Titans, Obor, Moons, Hueycoatl (+camp), Brutus, Maggot King, Bryophyta coords. Collection logs: all 125 cache pages.
- Reach: Huey slope + Maggot exit fixed (skipReachCheck). Home apple trees 3100/3103 x 3477-3481 unreachable (TODO).

## Queue (OSRS bosses)
Wilderness: Callisto/Artio, Vet'ion/Calvar'ion, Venenatis/Spindel, Chaos Elemental, Chaos Fanatic, Crazy Archaeologist, Scorpia, KBD, Revenants
GWD: Graardor, Zilyana, Kree'arra, K'ril, Nex
Slayer: Cerberus, Kraken, Thermy, Sire, Hydra, Grotesque Guardians, Araxxor, Shaman, Gargoyle sup, Hespori
Others: Zulrah, Vorkath, DKs, KQ, Giant Mole, Sarachnis, Corp, Nightmare/Phosani, Muspah, DT2 (Duke, Leviathan, Vardorvis, Whisperer), Scurrius, Tempoross, Wintertodt, Zalcano, Gauntlet/Corrupted, Fight Caves/Jad, Inferno/Zuk, Colosseum/Sol, Yama, Barrows, Skotizo, Deranged arch, Mimic, Obor/Bryo(done), Barrelchest
Raids: CoX (xeric/chambersrework), ToB, ToA, TFA (raid 4)
## Findings to fix
- anim: Parasite 9468 (attack 8554/8555, death 8559), Spawn of Sarachnis 8714, Demonic Brutus ghost 15629 json anims -1
