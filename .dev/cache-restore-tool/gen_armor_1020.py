# Armor sets 2026-10-20 (source Desktop/Rsps/Modle set/Models/Items/{Celestial,Assassin,Forestwood/v2,Purple,Red}).
# One table -> armor_1020_modelmap.txt / _items.txt / _priorities.txt / _framing.txt / _item_info.json (entries to append).
# Slots from WornSlotGuess, inventory / worn pairs by vertex count (armor_1020/*.png). Pieces with no inventory model use
# their worn mesh as the icon (like the Zaryte Stormpiercer). Red 36565 (inventory-only blade, no worn model) is left out.
import json, os

# Icon angles: the rips' inventory models lie flat (OrientIcon: up = +z), so they stand up at xan2d 512; picked
# from AutoFrameItems previews (armor_1020/icons.png). 37065 (flat, starry) was no robe bottom: the worn mesh is its icon.
FRAME = {31872: '512,0', 31880: '0,0', 31892: '512,0', 31896: '0,0', 31898: '512,0', 31900: '512,0', 31906: '0,0',
         31908: '512,0', 31910: '512,0'}

# Templates: (item, inventory model, male worn, female worn)
T = {
    'torva_helm': (26382, 49456, 49446, 49452), 'torva_body': (26384, 49458, 49450, 49454),
    'torva_legs': (26386, 49459, 49448, 49453), 'anc_bottom': (21024, 32787, 32653, 32662),
    'masori_mask': (27235, 46466, 45249, 45260), 'masori_body': (27238, 46469, 45258, 45271),
    'masori_chaps': (27241, 46475, 45255, 45268), 'zaryte_vambs': (26235, 43237, 43123, 43159),
    'pegasian': (13237, 29396, 29252, 29253), 'nightmare_staff': (24422, 39073, 39056, 39063),
    'ags': (11802, 28075, 27649, 42644), 'saeldor': (23995, 37980, 38270, 38280),
    'dclaws': (13652, 32784, 29191, 29191), 'acb': (11785, 19967, 19839, 42611), 'elysian': (12817, 11072, 11048, 35755),
}
SLOT = {'head': 0, 'weapon': 3, 'body': 4, 'shield': 5, 'legs': 7, 'hands': 9, 'feet': 10}
WEAR = '-|Wear|-|-|Drop'
WIELD = '-|Wield|-|-|Drop'
# The Assassin set can't be dropped, only destroyed (user, 2026-10-20): op4 is "Destroy", which TabInventory sends to
# its destroy dialogue instead of dropping.
DESTROY = {'Assassin'}

def b(stab=0, slash=0, crush=0, mag=0, rng=0, dstab=0, dslash=0, dcrush=0, dmag=0, drng=0, str_=0, rstr=0, mdmg=0, pray=0):
    out = {}
    for k, v in (('stab_attack_bonus', stab), ('slash_attack_bonus', slash), ('crush_attack_bonus', crush),
                 ('magic_attack_bonus', mag), ('range_attack_bonus', rng), ('stab_defence_bonus', dstab),
                 ('slash_defence_bonus', dslash), ('crush_defence_bonus', dcrush), ('magic_defence_bonus', dmag),
                 ('range_defence_bonus', drng), ('melee_strength_bonus', str_), ('ranged_strength_bonus', rstr),
                 ('magic_damage_bonus', mdmg), ('prayer_bonus', pray)):
        if v: out[k] = v
    return out

# (set, folder, id, name, slot, template, inv raw, worn raw, reqs, bonuses, extra json, examine)
MAGE = {'magic_level': 80, 'defence_level': 70}
ROGUE = {'attack_level': 80, 'defence_level': 70}
RANGE = {'ranged_level': 80, 'defence_level': 70}
TANK = {'defence_level': 80}
BRUTE = {'attack_level': 80, 'defence_level': 75}
HH = {'hide_hair': True, 'hide_beard': True}
P = [
    ('Celestial', 'Celestial', 31870, 'Celestial crown', 'head', 'torva_helm', 37061, 37062, MAGE,
     b(mag=10, rng=-2, dstab=14, dslash=13, dcrush=15, dmag=7, mdmg=3, pray=1), HH, 'A crown of starlit gold, humming with cosmic power.'),
    ('Celestial', 'Celestial', 31872, 'Celestial robe top', 'body', 'torva_body', 37064, 37063, MAGE,
     b(mag=36, rng=-8, dstab=44, dslash=33, dcrush=52, dmag=30, mdmg=3, pray=1), {'hide_arms': True}, 'Robes woven from the night sky itself.'),
    ('Celestial', 'Celestial', 31874, 'Celestial robe bottom', 'legs', 'anc_bottom', 37066, 37066, MAGE,
     b(mag=27, rng=-7, dstab=28, dslash=25, dcrush=31, dmag=21, mdmg=3, pray=1), {}, 'Star-flecked robes that drift like a nebula.'),
    ('Celestial', 'Celestial', 31876, 'Celestial staff', 'weapon', 'nightmare_staff', 37059, 37060, MAGE,
     b(mag=22, crush=10, dmag=15, mdmg=12, pray=2), {'weapon_type': 'MAGIC_STAFF'}, 'A golden staff channelling the light of distant stars.'),
    ('Assassin', 'Assassin', 31878, 'Assassin hood', 'head', 'torva_helm', 35861, 35861, ROGUE,
     b(stab=10, slash=8, mag=-4, rng=-2, dstab=30, dslash=30, dcrush=30, drng=30, str_=4), HH, 'A hood that swallows the light around its wearer.'),
    ('Assassin', 'Assassin', 31880, 'Assassin garb', 'body', 'torva_body', 35859, 35859, ROGUE,
     b(stab=22, slash=18, mag=-10, rng=-6, dstab=70, dslash=66, dcrush=70, drng=70, str_=5), {'hide_arms': True}, 'Dark leathers made for striking unseen.'),
    ('Assassin', 'Assassin', 31882, 'Assassin legs', 'legs', 'torva_legs', 35863, 35863, ROGUE,
     b(stab=14, slash=12, mag=-7, rng=-4, dstab=50, dslash=48, dcrush=50, drng=48, str_=4), {}, 'A long, silent coat-tail of shadow-dyed leather.'),
    ('Assassin', 'Assassin', 31884, 'Assassin greatsword', 'weapon', 'ags', 35865, 35865, ROGUE,
     b(stab=100, slash=138, crush=70, str_=138, pray=4), {'weapon_type': 'GODSWORD', 'two_handed': True}, 'A long, blackened blade. It never shines.'),
    ('Elderthorn', 'Forestwood/v2', 31886, 'Elderthorn helm', 'head', 'masori_mask', 38474, 38474, RANGE,
     b(mag=-1, rng=13, dstab=9, dslash=9, dcrush=9, dmag=12, drng=12, rstr=2, pray=1), {'hide_hair': True}, 'A crown of living thorns.'),
    ('Elderthorn', 'Forestwood/v2', 31888, 'Elderthorn body', 'body', 'masori_body', 38473, 38473, RANGE,
     b(mag=-4, rng=45, dstab=52, dslash=48, dcrush=58, dmag=70, drng=60, rstr=4, pray=1), {'hide_arms': True}, 'Bark armour grown, not forged.'),
    ('Elderthorn', 'Forestwood/v2', 31890, 'Elderthorn chaps', 'legs', 'masori_chaps', 38470, 38470, RANGE,
     b(mag=-2, rng=30, dstab=32, dslash=28, dcrush=36, dmag=44, drng=36, rstr=3, pray=1), {}, 'Thorned leggings of ancient heartwood.'),
    ('Elderthorn', 'Forestwood/v2', 31892, 'Elderthorn vambraces', 'hands', 'zaryte_vambs', 38471, 38472, RANGE,
     b(rng=18, dstab=8, dslash=8, dcrush=8, dmag=5, drng=8, rstr=2, pray=1), {}, 'Bark bracers that steady an archer\'s draw.'),
    ('Elderthorn', 'Forestwood/v2', 31894, 'Elderthorn boots', 'feet', 'pegasian', 38469, 38469, RANGE,
     b(mag=-10, rng=12, dstab=6, dslash=6, dcrush=6, dmag=6, drng=4, rstr=1), {}, 'Rooted boots that never slip.'),
    ('Duskwarden', 'Purple', 31896, 'Duskwarden helm', 'head', 'torva_helm', 36555, 36554, TANK,
     b(mag=-5, rng=-5, dstab=62, dslash=62, dcrush=64, dmag=-2, drng=60, str_=5, pray=1), HH, 'A spiked helm forged at the edge of the void.'),
    ('Duskwarden', 'Purple', 31898, 'Duskwarden platebody', 'body', 'torva_body', 36560, 36559, TANK,
     b(mag=-18, rng=-14, dstab=120, dslash=114, dcrush=120, dmag=-10, drng=140, str_=4, pray=1), {'hide_arms': True}, 'Violet-veined plate that drinks in the dusk.'),
    ('Duskwarden', 'Purple', 31900, 'Duskwarden platelegs', 'legs', 'torva_legs', 36552, 36551, TANK,
     b(mag=-12, rng=-10, dstab=90, dslash=88, dcrush=86, dmag=-6, drng=92, str_=3, pray=1), {}, 'Heavy greaves of void-tempered steel.'),
    ('Duskwarden', 'Purple', 31902, 'Duskwarden blade', 'weapon', 'saeldor', 36557, 36556, TANK | {'attack_level': 80},
     b(stab=50, slash=98, str_=90, pray=1), {'weapon_type': 'SCIMITAR'}, 'A violet blade that hums in the dark.'),
    ('Duskwarden', 'Purple', 31904, 'Duskwarden kiteshield', 'shield', 'elysian', 36553, 36558, TANK,
     b(dstab=70, dslash=72, dcrush=76, dmag=5, drng=68, str_=2, pray=2), {'shield_type': 'DEFAULT'}, 'A void-forged kiteshield. Blows seem to sink into it.'),
    ('Hellfang', 'Red', 31906, 'Hellfang helm', 'head', 'torva_helm', 36567, 36566, BRUTE,
     b(stab=4, slash=4, crush=4, mag=-6, rng=-4, dstab=45, dslash=46, dcrush=48, dmag=-3, drng=45, str_=7, pray=1), HH, 'A horned helm, still warm from the forge of a hell-beast.'),
    ('Hellfang', 'Red', 31908, 'Hellfang platebody', 'body', 'torva_body', 36571, 36570, BRUTE,
     b(stab=10, slash=10, crush=10, mag=-18, rng=-12, dstab=90, dslash=85, dcrush=92, dmag=-10, drng=95, str_=8, pray=1), {'hide_arms': True}, 'Crimson-edged plate made for those who never retreat.'),
    ('Hellfang', 'Red', 31910, 'Hellfang platelegs', 'legs', 'torva_legs', 36562, 36561, BRUTE,
     b(stab=6, slash=6, crush=6, mag=-10, rng=-8, dstab=64, dslash=60, dcrush=64, dmag=-6, drng=66, str_=7, pray=1), {}, 'Spiked greaves stained a deep crimson.'),
    # Red's weapon (36569 / 36572) is a crossbow, not claws (user, 2026-10-20): Armadyl crossbow record, ACB stance /
    # 4230 fire, Accurate / Rapid / Longrange, standard crossbow bolts.
    ('Hellfang', 'Red', 31912, 'Hellfang crossbow', 'weapon', 'acb', 36569, 36572, {'ranged_level': 80},
     b(rng=105, dstab=4, dslash=4, dcrush=4, dmag=6, drng=8), {'weapon_type': 'ARMADYL_CROSSBOW', 'ranged_weapon': 'CROSSBOW'},
     'A crimson-bladed crossbow. Its bolts bite like fangs.'),
]

here = os.path.dirname(os.path.abspath(__file__))
w = lambda name, lines: open(os.path.join(here, name), 'w', newline='\n').write('\n'.join(lines) + '\n')
mm, items, pri, frame, js = [], [], [], [], []
hdr = '# Armor sets 2026-10-20, generated by gen_armor_1020.py (edit the table there, not this file)'
items.append(hdr); pri.append(hdr); frame.append(hdr)
model = 61290
for (s, folder, iid, name, slot, tpl, inv, worn, req, bon, extra, exam) in P:
    t_item, t_inv, t_m, t_f = T[tpl]
    inv_m, worn_m = model, model + 1
    model += 2
    mm.append(f'{folder}/{inv}.dat|{inv_m}')
    mm.append(f'{folder}/{worn}.dat|{worn_m}')
    items.append(f'# {s}: {name} on the {tpl} record (inventory {inv} -> {inv_m}, worn {worn} -> {worn_m})')
    ops = WIELD if slot in ('weapon', 'shield') else WEAR
    if s in DESTROY:
        ops = ops.replace('|Drop', '|Destroy')
    items.append(f'variant {t_item} {iid} {iid + 1} 1 {ops} {name}')
    items.append(f'strip {iid} 40,41')
    pairs = [f'{t_inv}:{inv_m}', f'{t_m}:{worn_m}'] + ([f'{t_f}:{worn_m}'] if t_f != t_m else [])
    items.append(f'models {iid} ' + ','.join(pairs))
    if slot not in ('weapon', 'shield'):
        pri.append(f'{worn_m} {t_m}   # {name} <- {tpl}')
    frame.append(f'item {iid} ' + FRAME[iid] if iid in FRAME else f'item {iid}' + ('' if slot in ('weapon', 'shield') else ' front'))
    e = {'id': iid, 'name': name, 'tradeable': True, 'examine': exam, 'weight': 2.0 if slot != 'body' else 5.0,
         'protect_value': 150000000 if slot == 'weapon' else 100000000, 'equip_slot': SLOT[slot]}
    e.update(req); e.update(bon); e.update(extra); e['dropAnnounce'] = True
    js.append((f'  {{ #Custom: {name} ({s} set, armor batch 2026-10-20, models {inv_m}/{worn_m})', e))
w('armor_1020_modelmap.txt', mm)
w('armor_1020_items.txt', items)
w('armor_1020_priorities.txt', pri)
w('armor_1020_framing.txt', frame)
out = []
for head, e in js:
    body = json.dumps(e, indent=4).split('\n')[1:-1]   # the comment line carries the opening brace
    out.append(head + '\r\n' + '\r\n'.join(body) + '\r\n  }')
open(os.path.join(here, 'armor_1020_item_info.json'), 'w', newline='').write(',\r\n'.join(out) + '\r\n')
print('models', 61290, '-', model - 1, 'items', P[0][2], '-', P[-1][2] + 1)
