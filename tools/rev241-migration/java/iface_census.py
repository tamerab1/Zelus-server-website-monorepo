"""Every place the server addresses a component of an interface Jagex renumbered in rev 241.

  python iface_census.py <component-map-effective.json> <repoRoot> <out.tsv>

Sites: calls whose signature takes (interface, component) at known argument positions,
`h.actions[C]` inside InterfaceHandler.register(G, h -> {...}), `G << 16 | C` hashes and packed
hash literals. Group and component expressions are evaluated through a constant index (literals,
static final ints, Class.NAME, nested classes, << | + - *). Output TSV rows:
kind, file, line, group, comp value (or ?), comp expression, status (MOVE:new / REMOVED:name /
SAME / UNKNOWN), start:end of the expression in the file, code."""
import json, os, re, sys, collections

MAP, ROOT, OUT = sys.argv[1:4]
emap = {int(g): v for g, v in json.load(open(MAP)).items()}
R = {g for g, v in emap.items() if v['moved'] or v['removed']}
PRUNE = {'net', 'org', 'tools', '.git', 'build', '.gradle', 'website', '.dev', 'buildSrc'}

# method name -> list of (arity or None for varargs-min, [(gIdx, cIdx), ...]); arity None = any
CALLS = {
    'sendString': [(3, [(0, 1)])], 'setHidden': [(3, [(0, 1)])], 'sendItem': [(4, [(0, 1)])],
    'sendItems': [(4, [(0, 1)]), (5, [(0, 1)])], 'setAlignment': [(4, [(0, 1)])], 'sendModel': [(3, [(0, 1)])],
    'sendModelInformation': [(5, [(0, 1)])], 'sendNpcHead': [(3, [(0, 1)])], 'sendPlayerHead': [(2, [(0, 1)])],
    'animateInterface': [(3, [(0, 1)])], 'setGraphic': [(3, [(0, 1)])], 'setTextStyle': [(5, [(0, 1)])],
    'ifSetBodyType': [(3, [(0, 1)])], 'removeSubInterface': [(2, [(0, 1)])],
    'removeSubtoplevelInterface': [(2, [(0, 1)])], 'removeVisibleInterface': [(2, [(0, 1)])],
    'openSubInterface': [(4, [(1, 2)])], 'moveInterface': [(4, [(0, 1), (2, 3)])],
    'moveVisibleInterface': [(4, [(0, 1), (2, 3)])], 'setVisibleInterface': [(3, [(1, 2)])],
    'sendIfEvents': [('>=5', [(0, 1)])], 'register': [(3, [(0, 1)])], 'getAction': [(3, [(1, 2)])],
    'accessMasks': [(5, [(0, 1)])], 'tickButton': [(3, [(1, 2)])], 'resetButton': [(3, [(1, 2)])],
}
SIG_INT = re.compile(r'\b(?:public|private|protected)?\s*static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);')
SIG_INT2 = re.compile(r'\bstatic\s+final\s+int\s+([^;]+);')


def strip_code(text):
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith('//', i):
            j = text.find('\n', i)
            j = n if j < 0 else j
            out.append(' ' * (j - i)); i = j; continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r'[^\n]', ' ', text[i:j])); i = j; continue
        c = text[i]
        if c in '"\'':
            j = i + 1
            while j < n and text[j] != c and text[j] != '\n':
                j += 2 if text[j] == '\\' else 1
            out.append(c + ' ' * (j - i - 1) + (c if j < n else '')); i = j + 1; continue
        out.append(c); i += 1
    return ''.join(out)


files = {}
for dp, dirs, fs in os.walk(ROOT):
    rel_root = os.path.relpath(dp, ROOT).replace(os.sep, '/')
    dirs[:] = [d for d in dirs if not (d in PRUNE and (rel_root == '.' or d in ('build', '.gradle')))]
    for f in fs:
        if f.endswith(('.java', '.kt')):
            p = os.path.join(dp, f)
            raw = open(p, encoding='utf-8', errors='replace', newline='').read()  # raw: offsets are file offsets
            files[os.path.relpath(p, ROOT).replace(os.sep, '/')] = (raw, strip_code(raw))

# constant index: per file short names, and qualified Class[.Inner].NAME globally
local = collections.defaultdict(dict)
qual = {}
CLASS = re.compile(r'\b(?:class|interface|enum)\s+(\w+)')
for rel, (raw, code) in files.items():
    stack = []  # (class name, depth)
    depth = 0
    pending = None
    pos = 0
    for m in re.finditer(r'\b(?:class|interface|enum)\s+(\w+)|[{}]|\bstatic\s+final\s+int\s+([^;]+);', code):
        if m.group(1):
            pending = m.group(1)
        elif m.group(0) == '{':
            depth += 1
            if pending:
                stack.append((pending, depth)); pending = None
        elif m.group(0) == '}':
            if stack and stack[-1][1] == depth:
                stack.pop()
            depth -= 1
        elif m.group(2):
            for decl in re.split(r',(?![^(]*\))', m.group(2)):
                if '=' not in decl:
                    continue
                name, expr = decl.split('=', 1)
                name = name.strip()
                if not re.fullmatch(r'\w+', name):
                    continue
                cls = [c for c, _ in stack]
                local[rel][name] = expr.strip()
                for k in range(len(cls)):
                    qual['.'.join(cls[k:] + [name])] = (rel, expr.strip())

EXPR_TOKEN = re.compile(r'\s*(0x[0-9a-fA-F_]+|\d[\d_]*|[A-Za-z_][\w.]*|<<|>>|[|+\-*()&])')


def resolve_name(name, rel, seen):
    if name in seen:
        return None
    seen = seen | {name}
    if '.' not in name and name in local[rel]:
        return evaluate(local[rel][name], rel, seen)
    if name in qual:
        r2, e = qual[name]
        return evaluate(e, r2, seen)
    parts = name.split('.')
    for k in range(1, len(parts)):
        sub = '.'.join(parts[k:])
        if sub in qual:
            r2, e = qual[sub]
            return evaluate(e, r2, seen)
    return None


def evaluate(expr, rel, seen=frozenset()):
    toks = EXPR_TOKEN.findall(expr)
    if ''.join(t.strip() for t in toks) != re.sub(r'\s+', '', expr):
        return None
    vals = []
    for t in toks:
        t = t.strip()
        if re.fullmatch(r'0x[0-9a-fA-F_]+', t):
            vals.append(str(int(t.replace('_', ''), 16)))
        elif re.fullmatch(r'\d[\d_]*', t):
            vals.append(t.replace('_', ''))
        elif re.fullmatch(r'[A-Za-z_][\w.]*', t):
            v = resolve_name(t, rel, seen)
            if v is None:
                return None
            vals.append(str(v))
        else:
            vals.append(t)
    try:
        v = eval(' '.join(vals), {'__builtins__': {}})
        return v if isinstance(v, int) else None
    except Exception:
        return None


def split_args(code, open_idx):
    """args (start, end) of the call whose '(' is at open_idx; None if not closed."""
    depth, i, args, start = 0, open_idx + 1, [], open_idx + 1
    while i < len(code):
        c = code[i]
        if c in '([{':
            depth += 1
        elif c in ')]}':
            if depth == 0:
                args.append((start, i))
                return args, i
            depth -= 1
        elif c == ',' and depth == 0:
            args.append((start, i)); start = i + 1
        i += 1
    return None, None


def status(g, c):
    if c is None:
        return 'UNKNOWN'
    e = emap[g]
    if str(c) in e['moved']:
        return 'MOVE:%d' % e['moved'][str(c)]
    if str(c) in e['removed']:
        return 'REMOVED:' + e['removed'][str(c)]
    return 'SAME'


rows = []


def add(kind, rel, code, raw, pos, g, cexpr):
    lead = len(cexpr) - len(cexpr.lstrip())
    start, end = pos + lead, pos + lead + len(cexpr.strip())
    line = code.count('\n', 0, start) + 1
    c = evaluate(cexpr, rel)
    src = raw.split('\n')[line - 1].strip()
    rows.append((kind, rel, line, g, '?' if c is None else c, re.sub(r'\s+', ' ', cexpr.strip()), status(g, c),
                 '%d:%d' % (start, end), src))


CALL = re.compile(r'(?<![\w$])(\w+)\s*\(')
for rel, (raw, code) in files.items():
    for m in CALL.finditer(code):
        name = m.group(1)
        if name not in CALLS:
            continue
        args, close = split_args(code, m.end() - 1)
        if not args:
            continue
        n = len(args)
        for arity, pairs in CALLS[name]:
            ok = (n >= int(arity[2:])) if isinstance(arity, str) else n == arity
            if name == 'sendIfEvents' and n >= 6 and code[args[0][0]:args[0][1]].strip() in ('true', 'false'):
                pairs = [(1, 2)]
            if not ok:
                continue
            for gi, ci in pairs:
                gexpr = code[args[gi][0]:args[gi][1]]
                g = evaluate(gexpr, rel)
                if g in R:
                    add('call:' + name, rel, code, raw, args[ci][0], g, code[args[ci][0]:args[ci][1]])
            break
    # InterfaceHandler.register(G, h -> { ... h.actions[C] ... })
    for m in re.finditer(r'InterfaceHandler\s*\.\s*register\s*\(', code):
        args, close = split_args(code, m.end() - 1)
        if not args or len(args) != 2:
            continue
        g = evaluate(code[args[0][0]:args[0][1]], rel)
        if g not in R:
            continue
        body = code[args[1][0]:args[1][1]]
        pm = re.match(r'\s*\(?\s*(\w+)\s*\)?\s*->', body)
        hv = pm.group(1) if pm else 'h'
        for am in re.finditer(r'\b%s\s*\.\s*actions\s*\[([^\]\[]+)\]' % re.escape(hv), body):
            add('actions', rel, code, raw, args[1][0] + am.start(1), g, am.group(1))
    # G << 16 | C  and  (G << 16) | C / + C
    for m in re.finditer(r'(\(?\s*([\w.]+)\s*<<\s*16\s*\)?)\s*[|+]\s*(\(?[\w.]+(?:\s*[+\-*]\s*[\w.]+)*\)?)', code):
        g = evaluate(m.group(2), rel)
        if g in R:
            add('hash', rel, code, raw, m.start(3), g, m.group(3))
    # packed literals
    for m in re.finditer(r'(?<![\w.])(\d{6,9})(?![\w.])', code):
        v = int(m.group(1))
        g, c = v >> 16, v & 0xffff
        if g in R and (str(c) in emap[g]['moved'] or str(c) in emap[g]['removed']):
            line = code.count('\n', 0, m.start()) + 1
            rows.append(('hashlit', rel, line, g, c, m.group(1), status(g, c), '%d:%d' % (m.start(1), m.end(1)),
                         raw.split('\n')[line - 1].strip()))

with open(OUT, 'w', encoding='utf-8') as o:
    for r in rows:
        o.write('\t'.join(str(x) for x in r) + '\n')
by = collections.Counter((r[6].split(':')[0]) for r in rows)
print('sites', len(rows), dict(by))
print('by group:', collections.Counter(r[3] for r in rows if not r[6].startswith('SAME')).most_common())
print('files with non-SAME sites:', len({r[1] for r in rows if r[6] != 'SAME'}))
