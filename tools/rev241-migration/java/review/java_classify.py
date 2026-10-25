"""Classify each colliding-literal hit (jh2.tsv from java_census.py) by its usage context.
verdict: MOVE (context type is one of the literal's colliding types), STAY (context says a type the
literal doesn't collide in, or a coordinate/amount), REVIEW (no context)."""
import re, sys, collections


# (type, regex over the context string). Order matters: first match wins.
CTX = [
    ('coord', r'\b(Position|teleport|Bounds|Area|PolygonArea|tele|TeleportTo|regionId|getRegion|Region\.get|inside|isAt|absX|absY|x\s*[<>=]|y\s*[<>=]|getX|getY|coords?)\b'),
    ('clientscript', r'(sendClientScript|runClientScript|ClientScript|clientScript|runScript|SCRIPT|[Ss]cript)'),
    ('sound', r'(sound|Sound|SOUND|playJingle|jingle)'),
    ('spotanim', r'(graphics|Graphic|GFX|gfx|Gfx|Projectile|projectile|spotanim|SpotAnim|SPOTANIM|graphic|tint)'),
    ('seq', r'(animate|Animation|anim|Anim|ANIM|SEQ|seq|Seq)'),
    ('varbit', r'(varpbit|Varbit|varbit|VARBIT|Config\.varp|Config\.varpbit|Varp|varp|VARP|sendVarp|Config\()'),
    ('npc', r'(\bNPC\(|npc\.getId|npc\.id|NPCAction|NPCDef|npcId|NpcId|NPC_ID|NPC\b|npc\b|transform|spawnNpc|Npc)'),
    ('obj', r'(ObjectAction|GameObject|ObjectDef|obj\.id|getObject|objectId|ObjectId|OBJECT|OBJ_|Tile\.|addObject|[Oo]bj\b|Portal|Chest|chest|PORTAL|CHEST|door|Door|DOOR)'),
    ('item', r'(Item\(|ItemDef|ItemAction|ShopItem|itemId|ItemId|ITEM|item|Item|getId\(\)|hasItem|contains|inventory|equipment|Equipment|bank|Bank|reward|Reward|box\(|registerClaimTicket)'),
    ('struct', r'(STRUCT|struct|Struct|enum|ENUM|Enum)'),
    ('amount', r'(amount|Amount|MAX_AMOUNT|Math\.min|price|Price|cost|Cost|xp|XP|damage|Damage|delay|ticks|chance|weight|points|Points)'),
]
CTXRE = [(t, re.compile(r)) for t, r in CTX]


def innermost_call(code, pos):
    depth = 0
    i = pos - 1
    while i >= 0:
        c = code[i]
        if c == ')':
            depth += 1
        elif c == '(':
            if depth == 0:
                j = i
                while j > 0 and (code[j - 1].isalnum() or code[j - 1] in '_.'):
                    j -= 1
                k = j
                if code[max(0, k - 4):k] == 'new ':
                    pass
                return code[j:i + 1]
            depth -= 1
        i -= 1
    return ''


def lhs_name(code, pos):
    m = re.search(r'([A-Za-z_][\w]*)\s*(\[\s*\])?\s*=\s*(new\s+\w+\s*\[\s*\]\s*)?\{?[^=;]*$', code[:pos])
    return m.group(1) if m else ''


def enum_name(code):
    m = re.match(r'\s*([A-Z][A-Z0-9_]+)\s*\(', code)
    return m.group(1) if m else ''


def classify(code, lit_pos):
    call = innermost_call(code, lit_pos)
    lhs = lhs_name(code, lit_pos)
    pieces = [('call', call), ('lhs', lhs), ('line', code)]
    for src, s in pieces:
        if not s:
            continue
        for t, rx in CTXRE:
            if rx.search(s):
                return t, src, s
    return None, '', ''


if __name__ == '__main__':
    IN, OUT = sys.argv[1], sys.argv[2]
    rows = []
    for line in open(IN, encoding='utf-8'):
        types, f, ln, v, src = line.rstrip('\n').split('\t', 4)
        types = types.split(',')
        code = src
        pos = None
        for mt in re.finditer(r'(?<![\w.])%s(?![\w.])' % v, code):
            pos = mt.start()
            break
        if pos is None:
            pos = 0
        t, how, s = classify(code, pos)
        if t is None:
            verdict = 'REVIEW'
        elif t in types or (t == 'varbit' and 'varp' in types):
            verdict = 'MOVE'
        elif t in ('coord', 'amount', 'struct') or t not in types:
            verdict = 'STAY'
        rows.append((verdict, t or '-', how, ','.join(types), f, ln, v, src))

    with open(OUT, 'w', encoding='utf-8') as o:
        for r in rows:
            o.write('\t'.join(str(x) for x in r) + '\n')

    c = collections.Counter((r[0], r[3] if r[0] != 'MOVE' else r[1]) for r in rows)
    for k, n in sorted(c.items()):
        print('%-8s %-25s %d' % (k[0], k[1], n))
    print('files with MOVE:', len({r[4] for r in rows if r[0] == 'MOVE'}), ' REVIEW files:', len({r[4] for r in rows if r[0] == 'REVIEW'}))
