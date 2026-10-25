"""Signature index + argument-position lookup for Java literals.

param_type(file_text, enum_name_for_line, call_name, arity, index) -> (type or None, param name)
Signatures are collected from every scanned source: `Name(Type a, Type b)` declarations
(constructors, methods, records). Enum constants resolve to the enclosing enum's constructors."""
import re, os, collections

SIG = re.compile(r'(?:^|[\s(,])(?:public |private |protected |static |final )*(?:[\w<>\[\], ?]+\s+)?([A-Z]?\w+)\s*\(([^()]*)\)\s*(?:throws [\w., ]+)?\s*\{', re.M)
RECORD = re.compile(r'\brecord\s+(\w+)\s*\(([^()]*)\)')
ENUM = re.compile(r'^\s*(?:(?:public|private|protected|static|final)\s+)*enum\s+(\w+)')

PTYPES = [
    ('clientscript', re.compile(r'(?i)script')),
    ('sound', re.compile(r'(?i)sound|jingle')),
    ('spotanim', re.compile(r'(?i)gfx|graphic|spotanim|projectile')),
    ('seq', re.compile(r'(?i)anim|seq')),
    ('varbit', re.compile(r'(?i)varbit|varpbit')),
    ('varp', re.compile(r'(?i)varp|config')),
    ('npc', re.compile(r'(?i)npc|pet(?!.*item)|boss|monster|creature')),
    ('obj', re.compile(r'(?i)object|obj(?!ect)|loc\b|locId|door|chest|portal|stall|tree|rock')),
    ('model', re.compile(r'(?i)model')),
    ('item', re.compile(r'(?i)item|reward|weapon|cape|helm|body|legs|key|ticket|bond|box|cost|currency|ingredient|product|piece|staff|ring|amulet|ammo|id\b|^id$|Ids?$')),
    ('coord', re.compile(r'(?i)^(x|y|z|plane|height|absx|absy|regionid|region)$')),
    ('amount', re.compile(r'(?i)amount|price|value|level|xp|exp|chance|weight|count|ticks|delay|duration|charges|max|min|points|time|width|colou?r|offset|slot|index|tier')),
]


def ptype(name):
    for t, rx in PTYPES:
        if rx.search(name):
            return t
    return None


def split_params(s):
    out, depth, cur = [], 0, ''
    for c in s:
        if c in '<(':
            depth += 1
        elif c in '>)':
            depth -= 1
        if c == ',' and depth == 0:
            out.append(cur)
            cur = ''
        else:
            cur += c
    if cur.strip():
        out.append(cur)
    names = []
    for p in out:
        p = re.sub(r'@\w+(\([^)]*\))?', '', p).strip()
        toks = p.replace('...', ' ').split()
        if not toks:
            return None
        names.append(toks[-1])
    return names


class Index:
    def __init__(self):
        self.sigs = collections.defaultdict(list)  # name -> [param names]

    def add_file(self, text):
        for rx in (SIG, RECORD):
            for m in rx.finditer(text):
                name, params = m.group(1), m.group(2)
                if name in ('if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new'):
                    continue
                ps = split_params(params) if params.strip() else []
                if ps is not None and all(re.fullmatch(r'\w+', p) for p in ps):
                    if ps not in self.sigs[name]:
                        self.sigs[name].append(ps)

    def lookup(self, name, arity, index):
        cands = [ps for ps in self.sigs.get(name, []) if len(ps) == arity]
        names = {ps[index] for ps in cands}
        if len(names) == 1:
            return next(iter(names))
        # varargs-ish / overloads: if all candidate names give the same type, use it
        types = {ptype(n) for n in names}
        if len(types) == 1 and names:
            return sorted(names)[0]
        return None


def enclosing_enum(lines, ln):
    for i in range(ln - 1, -1, -1):
        m = ENUM.search(lines[i])
        if m:
            return m.group(1)
    return None


def call_at(code, pos):
    """(call name, arg index, arity) of the innermost call containing pos, on this line only."""
    depth = 0
    i = pos - 1
    while i >= 0:
        c = code[i]
        if c in ')]}':
            depth += 1
        elif c in '([{':
            if depth == 0:
                if c != '(':
                    return None
                j = i
                while j > 0 and (code[j - 1].isalnum() or code[j - 1] in '_$'):
                    j -= 1
                name = code[j:i]
                # args: from i+1 to matching close
                d, k, args, start = 0, i + 1, [], i + 1
                while k < len(code):
                    ch = code[k]
                    if ch in '([{':
                        d += 1
                    elif ch in ')]}':
                        if d == 0:
                            break
                        d -= 1
                    elif ch == ',' and d == 0:
                        args.append((start, k))
                        start = k + 1
                    k += 1
                args.append((start, k))
                closed = k < len(code)
                idx = next((n for n, (a, b) in enumerate(args) if a <= pos < b), None)
                if idx is None:
                    return None
                return name, idx, (len(args) if closed else None)
            depth -= 1
        i -= 1
    return None
