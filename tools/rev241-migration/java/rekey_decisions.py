"""Re-keys decisions.tsv to the code as it is now (after remap_java.py --apply and later digit-only edits).

  python rekey_decisions.py <manifest> <decisions.tsv> <repoRoot> [--write]

remap_java.py matches a decision by file + value + occurrence + the line's code text. After --apply the
lines it rewrote no longer match, so a re-run (e.g. after merging develop) would list them as unresolved.
Each decision is found again by its line with every number replaced by '#' (nearest line to the old line
number, as edits elsewhere in the file can shift it), and gets the literal's current value and the current
code text. Literals that moved are no longer moving ids, so only decisions that still apply are kept.
"""
import json, os, re, sys, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from remap_java import code_spans, norm, LIT, TYPES

man, dec, root = sys.argv[1:4]
write = '--write' in sys.argv
manifest = json.load(open(man))['types']
moves = {t: {int(a): int(b) for a, b in manifest[t]['map'].items() if int(a) != int(b)} for t in TYPES}
moving_any = set().union(*[set(m) for m in moves.values()])
# only standalone numbers: identifiers keep their digits (CLAN_WARS_TEAM_1_COUNT vs _2_)
digits = lambda s: re.sub(r'(?<![\w.])\d+(?![\w.])', '#', norm(s))

files = {}
def lines_of(rel):
    if rel not in files:
        with open(os.path.join(root, rel), encoding='utf-8', newline='') as f:
            files[rel] = f.read().split('\n')
    return files[rel]

header, out, lost = [], [], []
for line in open(dec, encoding='utf-8'):
    if line.startswith('#'):
        header.append(line)
        continue
    f, ln, v, occ, t, source, code = line.rstrip('\n').split('\t', 6)
    ln, v, occ = int(ln), int(v), int(occ)
    ls = lines_of(f)
    want = digits(code)
    cands = [i for i, l in enumerate(ls) if digits(l.rstrip('\r')) == want]
    if not cands:
        lost.append(line.rstrip('\n'))
        continue
    best = min(abs(i + 1 - ln) for i in cands)
    near = [i for i in cands if abs(i + 1 - ln) == best]
    if len(near) > 1:
        lost.append(line.rstrip('\n'))
        continue
    i = near[0]
    cur = ls[i].rstrip('\r')
    # the literal: the occ-th occurrence of v in the OLD text sits at the same token index in the new text
    old_tokens = [int(x) for x in re.findall(r'(?<![\w.])\d{3,6}(?![\w.])', code)]
    new_tokens = [int(x) for x in re.findall(r'(?<![\w.])\d{3,6}(?![\w.])', cur)]
    k, seen = None, 0
    for idx, x in enumerate(old_tokens):
        if x == v:
            seen += 1
            if seen == occ:
                k = idx
                break
    if k is None or len(old_tokens) != len(new_tokens):
        lost.append(line.rstrip('\n'))
        continue
    nv = new_tokens[k]
    if nv not in moving_any:
        continue  # moved (or no longer a moving id): nothing left to decide
    nocc = new_tokens[:k + 1].count(nv)
    out.append('\t'.join(map(str, (f, i + 1, nv, nocc, t, source, cur.strip()))) + '\n')

print('decisions kept %d, dropped (moved) %d, could not re-find %d' % (
    len(out), sum(1 for l in open(dec, encoding='utf-8') if not l.startswith('#')) - len(out) - len(lost), len(lost)))
for l in lost[:20]:
    print('  LOST ' + l[:160])
if write and not lost:
    with open(dec, 'w', encoding='utf-8') as f:
        f.writelines(header + out)
    print('written')
elif write:
    print('not written: re-find the lost decisions first')
