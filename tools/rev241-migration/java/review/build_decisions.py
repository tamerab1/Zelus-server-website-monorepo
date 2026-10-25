"""jm4.tsv (classifier v3) + reviewed overrides -> decisions.tsv
Each row: file, line, value, occurrence-on-line, semantic type (or none), source."""
import sys, collections, re

IN, OUT = sys.argv[1], sys.argv[2]
NONE = {'coord', 'amount', 'struct'}

# (basename, line, value) -> type. Reviewed by hand 2026-10-25 (session 2); line numbers are as of
# server commit 1aeaa8a.
O = {}
def o(f, lines, values, t):
    for ln in (lines if isinstance(lines, (list, tuple, range)) else [lines]):
        for v in (values if isinstance(values, (list, tuple, range)) else [values]):
            O[(f, ln, v)] = t

o('Database.java', 95, 60000, 'none')
o('BountyHunter.java', 245, 60000, 'none')
o('BondPerks.java', 251, 60000, 'none')
o('CommandHandlerRegular.java', 917, 60000, 'none')
o('NPCType.java', 37, [10631, 10619], 'npc')
o('VoteBossHandler.java', 50, 16216, 'npc')
o('CustomBossCombat.java', 51, 16212, 'npc')
o('CustomBossCombat.java', range(55, 80), list(range(16200, 16226)), 'npc')
o('Vorath.java', 78, 16201, 'npc')
o('InstanceMaps.java', 55, 4405, 'none')
o('MadAngelIds.java', [41, 43, 45, 47], [16305, 16306, 16307, 16308], 'npc')
o('herbCleaner.java', [82, 85], [4400, 4200], 'none')
o('EdgevillePvpRewards.java', 71, 10606, 'clientscript')
o('RevCaves.java', 235, 10196, 'none')
o('Wintertodt.java', range(350, 365), list(range(10030, 10080)), 'item')
o('ForgeSpecGfx.java', range(21, 37), list(range(4400, 4416)), 'spotanim')
o('AssassinGreatsword.java', 20, [4429, 4430], 'spotanim')
o('StarfallVolley.java', 29, [4416, 4418, 4419, 4293], 'spotanim')
o('AzureFang.java', [24, 25], [4426, 4427], 'spotanim')
o('AzureFang.java', 26, 31828, 'item')
o('CapeForge.java', [68, 70, 72], [59983, 59984, 59985], 'item')
o('Stardust.java', 17, [4293, 4294], 'spotanim')
o('FortuneInterface.java', [40, 41, 42], [16203, 16204, 16205], 'seq')
o('RivalsItems.java', 32, 59626, 'item')
o('UpgradeItems.java', [53, 60, 67], [59588, 59589, 59590], 'item')
o('KillCounter.java', 54, 16305, 'npc')
o('ServerTeleports.java', [187, 194, 201, 430], [60346, 60345, 60037, 60347], 'model')
o('ServerTeleports.java', 211, 14453, 'seq')
o('ServerTeleports.java', 223, 31653, 'model')
o('Preset.java', 63, 4417, 'item')
o('Containers.java', 64, 4286, 'item')
o('DonatorBond.java', 17, 59600, 'item')
o('BraceletOfEthereum.java', 22, 16000, 'none')
o('Locations.java', 17, 4830, 'none')
o('MultiZone.java', 78, 10034, 'none')
o('ScratchMachine.java', 10, 60013, 'obj')
o('ZelusChest.java', 15, 59960, 'item')
o('WaterbirthDungeon.java', [92, 98], 4407, 'none')
o('Costume.java', range(38, 50), list(range(10030, 10080)), 'item')
o('Costume.java', 110, list(range(4300, 4420)), 'item')
o('Costume.java', [141, 142], [10069, 10071], 'item')
o('ShootingStars.java', [154, 306], [16000, 14000], 'none')
o('Wisps.java', [32, 33, 34, 35], [15023, 15024, 15025, 15026], 'npc')
o('PickPocket.java', 1108, 10075, 'item')
o('Stall.java', [147, 158], [4277, 4278], 'obj')
o('CommandHandlerAdmin.java', 780, [16200, 16214, 16217, 16225], 'npc')
o('SetEffect.java', [34, 94, 105], [31504, 31520, 31512], 'item')
o('Buildable.java', 600, 31554, 'obj')
o('NPCActionHandler.java', 99, 16215, 'npc')
o('PrayerAltar.java', 77, 31858, 'obj')
o('AppearanceHandler.java', 17, 31839, 'none')

rows = []
uncovered = []
seen = collections.Counter()
for line in open(IN, encoding='utf-8'):
    verdict, typ, how, moving, f, ln, v, src = line.rstrip('\n').split('\t', 7)
    ln, v = int(ln), int(v)
    base = f.rsplit('/', 1)[-1]
    seen[(f, ln, v)] += 1
    occ = seen[(f, ln, v)]
    key = (base, ln, v)
    if key in O:
        t, source = O[key], 'reviewed'
    elif verdict == 'REVIEW':
        if typ == 'item/obj':
            t, source = 'item', 'default:item-range'
        elif typ in ('item/model', 'item/model/obj'):
            # 59xxx-60xxx: custom items unless the context says object/model (those are overridden)
            t, source = 'item', 'default:custom-item-range'
        else:
            uncovered.append(line.rstrip('\n'))
            continue
    else:
        t = 'none' if typ in NONE else typ
        source = how
    rows.append((f, ln, v, occ, t, source, src.strip()))

with open(OUT, 'w', encoding='utf-8') as o_:
    o_.write('# file\tline\tvalue\toccurrence\ttype\tsource\tcode\n')
    for r in rows:
        o_.write('\t'.join(str(x) for x in r) + '\n')
print('decisions', len(rows), 'uncovered REVIEW', len(uncovered))
for u in uncovered:
    p = u.split('\t')
    print('  %-24s %5s %6s %-26s %s' % (p[4].rsplit('/', 1)[-1], p[5], p[6], p[1], p[7][:90]))
unused = [k for k in O if not any(r[0].endswith('/' + k[0]) and r[1] == k[1] and r[2] == k[2] for r in rows)]
print('override keys that matched nothing:', len(unused))
