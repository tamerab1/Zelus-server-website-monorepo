"""Find int literals in server Java/Kotlin that equal a colliding custom id (old id in the 241
manifest). Writes one TSV row per hit: type-candidates, file, line, literal, source line."""
import json, os, re, sys, collections

MAN, ROOT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(MAN))['types']
TYPES = ['item', 'npc', 'obj', 'seq', 'spotanim', 'varbit', 'varp', 'sound', 'inv', 'clientscript', 'model', 'texture']
sets = {t: set(int(k) for k, v in m[t]['map'].items() if int(k) != int(v)) for t in TYPES}
TYPES = [t for t in TYPES if t != 'texture']
PRUNE = {'net', 'org', 'tools', '.git', 'build', '.gradle', 'website', '.dev', 'buildSrc'}
LIT = re.compile(r'(?<![\w.])(\d{3,6})(?![\w.])')

rows = []
for root, dirs, files in os.walk(ROOT):
    rel_root = os.path.relpath(root, ROOT).replace(os.sep, '/')
    dirs[:] = [d for d in dirs if not (d in PRUNE and (rel_root == '.' or d in ('build', '.gradle')))]
    for f in files:
        if not f.endswith(('.java', '.kt')):
            continue
        p = os.path.join(root, f)
        rel = os.path.relpath(p, ROOT).replace(os.sep, '/')
        in_block = False
        for ln, line in enumerate(open(p, encoding='utf-8', errors='replace'), 1):
            code = line
            # strip comments and string literals (roughly)
            if in_block:
                e = code.find('*/')
                if e < 0:
                    continue
                code = code[e + 2:]
                in_block = False
            code = re.sub(r'/\*.*?\*/', ' ', code)
            if '/*' in code:
                code = code[:code.find('/*')]
                in_block = True
            code = re.sub(r'"(\\.|[^"\\])*"', '""', code)
            code = code.split('//')[0]
            for mt in LIT.finditer(code):
                v = int(mt.group(1))
                ts = [t for t in TYPES if v in sets[t]]
                if ts:
                    rows.append((','.join(ts), rel, ln, v, line.strip()))

with open(OUT, 'w', encoding='utf-8') as o:
    for r in rows:
        o.write('%s\t%s\t%d\t%d\t%s\n' % r)

byType = collections.Counter(r[0] for r in rows)
byFile = collections.Counter(r[1] for r in rows)
print('hits', len(rows), 'files', len(byFile))
for k, v in byType.most_common():
    print('  %-30s %d' % (k, v))
print('top files:')
for k, v in byFile.most_common(40):
    print('  %5d %s' % (v, k))
