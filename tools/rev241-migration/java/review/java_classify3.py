import json, os, re, sys, collections
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from java_classify import CTXRE, lhs_name
import sigindex

IN, IDS, ROOT, OUT = sys.argv[1:5]
ids = {k: set(v) for k, v in json.load(open(IDS)).items()}
PRUNE = {'net', 'org', 'tools', '.git', 'build', '.gradle', 'website', '.dev', 'buildSrc'}


def strip(line):
    line = re.sub(r'"(\\.|[^"\\])*"', '""', line)
    line = re.sub(r'/\*.*?\*/', ' ', line)
    return line.split('//')[0]


# signature index over all sources
idx = sigindex.Index()
texts = {}
for root, dirs, files in os.walk(ROOT):
    rel_root = os.path.relpath(root, ROOT).replace(os.sep, '/')
    dirs[:] = [d for d in dirs if not (d in PRUNE and (rel_root == '.' or d in ('build', '.gradle')))]
    for f in files:
        if f.endswith(('.java', '.kt')):
            p = os.path.join(root, f)
            t = open(p, encoding='utf-8', errors='replace').read()
            texts[os.path.relpath(p, ROOT).replace(os.sep, '/')] = t
            idx.add_file(re.sub(r'"(\\.|[^"\\])*"', '""', t))


def ctx_type(s):
    for t, rx in CTXRE:
        if rx.search(s):
            return t
    return None


PARAM = {
    'Bone.id': 'item', 'Bones.boneId': 'item', 'BossDisplay.objectId': 'obj', 'BreakItems.itemId': 'item',
    'Buildable.xp': 'amount', 'CardSprite.spriteId': 'amount', 'Chinchompa.itemId': 'item', 'Dart.id': 'item',
    'DropItem.item': 'item', 'GameObject.id': 'obj', 'Item.id': 'item', 'ItemBreaking.coinRepairCost': 'amount',
    'ItemCurrencyHandler.currencyItemId': 'item', 'LootItem.id': 'item', 'LootItem.maxAmount': 'amount',
    'MaxCapes.newCapeId': 'item', 'MaxCapes.newHoodId': 'item', 'MaxCapes.secondaryId': 'item',
    'PerkSets.scriptId': 'clientscript', 'PerkTasks.resourceId': 'amount', 'PestleAndMortar.before': 'item',
    'Pet.itemId': 'item', 'Pet.metaId': 'npc', 'Pet.npcId': 'npc', 'Pickaxe.id': 'item',
    'Pickaxe.regularAnimationID': 'seq', 'Position.y': 'coord', 'Position.x': 'coord', 'Projectile.gfxId': 'spotanim',
    'Recipe.resultId': 'item', 'Rock.petOdds': 'amount', 'ServerTeleports.modelId': 'model',
    'ShopCategories.scriptId': 'clientscript', 'ShopItem.cost': 'amount', 'ShopItem.itemId': 'item',
    'ShopItem.price': 'amount', 'SoulAura.gfx': 'spotanim', 'SoulWeaponType.ascended': 'item',
    'SoulWeaponType.base': 'item', 'SoulWeaponType.empowered': 'item', 'TabEmote.animationID': 'seq',
    'Tree.petOdds': 'amount', 'WheatPosition.y': 'coord', 'addOrDrop.id': 'item', 'arrow.gfxId': 'spotanim',
    'box.ids': 'item', 'convertY.absY': 'coord', 'convertX.absX': 'coord', 'getObject.id': 'obj',
    'graphics.id': 'spotanim', 'handleGatherResource.resourceId': 'amount', 'hasId.id': 'item',
    'register.objectId': 'obj', 'register.y': 'coord', 'register.x': 'coord', 'registerClaimTicket.itemId': 'item',
    'registerEquipment.itemId': 'item', 'registerInventory.itemId': 'item', 'sendClientScript.id': 'clientscript',
    'sendGraphics.id': 'spotanim', 'spawn.id': 'obj', 'varp.id': 'varp', 'varpbit.id': 'varbit',
    'wearingItem.itemId': 'item', 'tint.t1': 'spotanim', 'tint.t2': 'spotanim', 'tint.t3': 'spotanim',
    'Bounds.swY': 'coord', 'Bounds.neY': 'coord', 'Bounds.swX': 'coord', 'Bounds.neX': 'coord',
}
CMP = re.compile(r'([\w\]\[]+(?:\([^()]*\))?)\s*\.\s*(?:getId\(\)|id|getDef\(\)\.id)\s*(?:==|!=)\s*$')


def receiver_type(name):
    n = name.lower()
    if 'npc' in n or 'target' in n or 'boss' in n or 'pet' in n and 'item' not in n:
        return 'npc'
    if 'obj' in n or 'object' in n or 'loc' in n or 'door' in n:
        return 'obj'
    if any(w in n for w in ('item', 'weapon', 'wep', 'equip', 'cape', 'helm', 'ring', 'amulet', 'shield', 'ammo', 'get(equipment', 'slot', 'legs', 'body', 'boots', 'gloves', 'hat', 'def')):
        return 'item'
    return None
unknown_params = collections.Counter()

rows = []
for line in open(IN, encoding='utf-8'):
    types, f, ln, v, src = line.rstrip('\n').split('\t', 4)
    ln, v = int(ln), int(v)
    moving = types.split(',')
    lines = texts[f].split('\n')
    code = strip(lines[ln - 1])
    m = next(re.finditer(r'(?<![\w.])%d(?![\w.])' % v, code), None)
    pos = m.start() if m else 0
    # join continuation lines so multi-line calls get their full arity
    logical = code
    depth = logical.count('(') - logical.count(')')
    k = ln
    while depth > 0 and k < len(lines) and k < ln + 12:
        nxt = strip(lines[k])
        logical += ' ' + nxt
        depth += nxt.count('(') - nxt.count(')')
        k += 1
    # ... and preceding lines when the literal sits inside a call opened earlier
    pre = ''
    j = ln - 2
    d = 0
    for c in code[:pos]:
        d += (c == '(') - (c == ')')
    while j >= 0 and j > ln - 14:
        prev = strip(lines[j])
        opened = prev.count('(') - prev.count(')')
        pre = prev + ' ' + pre
        if opened + d > 0 or re.match(r'\s*[A-Z][A-Z0-9_]+\s*\(', prev):
            break
        j -= 1
    if pre.strip() and (pre.count('(') - pre.count(')')) > 0:
        logical2 = pre + ' ' + logical
        pos2 = len(pre) + 1 + pos
    else:
        logical2, pos2 = logical, pos

    decided, how = None, ''
    mc = CMP.search(code[:pos])
    if mc:
        rt = receiver_type(mc.group(1))
        if rt:
            decided, how = rt, 'cmp:' + mc.group(1)
    call = sigindex.call_at(logical2, pos2) if decided is None else None
    pname = None
    if call:
        name, ai, arity = call
        target = name
        mline = re.match(r'\s*([A-Z][A-Z0-9_]+)\s*\(', logical2)
        if mline and mline.group(1) == name:
            target = sigindex.enclosing_enum(lines, ln) or name
        if arity is not None and name not in ('entry', 'of', 'asList', 'add', 'put', 'get', 'contains', 'equals', 'max', 'min'):
            pname = idx.lookup(target, arity, ai)
        if pname:
            key = '%s.%s' % (target, pname)
            if key in PARAM:
                decided, how = PARAM[key], 'param:' + key
            else:
                unknown_params[key] += 1
        if decided is None and name:
            ct = ctx_type(name + '(')
            if ct:
                decided, how = ct, 'call:' + name
    if decided is None:
        lhs = lhs_name(code, pos)
        if lhs:
            ct = ctx_type(lhs)
            if ct:
                decided, how = ct, 'lhs:' + lhs
    plausible = {t for t in ('item', 'npc', 'obj', 'seq', 'spotanim') if v in ids[t]}
    plausible |= {t for t in moving if t in ('sound', 'clientscript', 'varp', 'varbit', 'model')}
    if decided is None and len(plausible) == 1:
        decided, how = next(iter(plausible)), 'exists'
    if decided is None:
        ct = ctx_type(code)
        if ct:
            decided, how = ct, 'line'
    if decided == 'varbit' and 'varp' in moving and 'varbit' not in moving:
        decided = 'varp'
    if decided is None:
        verdict = 'REVIEW'
        decided = '/'.join(sorted(plausible)) or '-'
    elif decided in moving:
        verdict = 'MOVE'
    else:
        verdict = 'STAY'
    rows.append((verdict, decided, how, ','.join(moving), f, ln, v, src))

with open(OUT, 'w', encoding='utf-8') as o:
    for r in rows:
        o.write('\t'.join(str(x) for x in r) + '\n')
print('unmapped params:', dict(unknown_params))
c = collections.Counter((r[0], r[2].split(':')[0]) for r in rows)
for k, n in sorted(c.items()):
    print('%-7s %-8s %d' % (k[0], k[1], n))
