"""Rewrites the literal component ids iface_census.py found on MOVE sites.

  python iface_remap.py <census.tsv> <repoRoot> [--apply]

Only plain integer literals are rewritten (h.actions[13], sendString(Interface.BANK, 9, ...), the 20 in
`HP_HUD << 16 | 20`, packed hash literals). Named constants, computed ids (`14 + i`, `book.component(i)`),
REMOVED and UNKNOWN sites are listed for review and need a hand edit. Run it once, on the code as the
census saw it, before any hand edit: a second run would map ids that are already 241 ids.
"""
import collections, re, sys

census, root = sys.argv[1], sys.argv[2]
apply = '--apply' in sys.argv
edits = collections.defaultdict(dict)   # file -> {(start, end): (old text, new text)}
review = []
for line in open(census, encoding='utf-8'):
    kind, rel, ln, g, c, expr, status, span, code = line.rstrip('\n').split('\t', 8)
    if status == 'SAME':
        continue
    start, end = map(int, span.split(':'))
    if status.startswith('MOVE') and re.fullmatch(r'\d+', expr):
        new = int(status[5:])
        text = str((int(g) << 16) | new) if kind == 'hashlit' else str(new)
        prev = edits[rel].get((start, end))
        if prev and prev[1] != text:
            sys.exit('conflicting rewrites at %s %d' % (rel, start))
        edits[rel][(start, end)] = (expr, text)
    else:
        review.append('%s:%s g%s %s -> %s   %s' % (rel.rsplit('/', 1)[-1], ln, g, expr, status, code[:100]))

total = 0
for rel, spans in sorted(edits.items()):
    path = root + '/' + rel
    with open(path, encoding='utf-8', newline='') as f:
        text = f.read()
    for (start, end), (old, new) in spans.items():
        if text[start:end] != old:
            sys.exit('%s @%d: expected %r, found %r (code changed since the census)' % (rel, start, old, text[start:end]))
    for (start, end), (old, new) in sorted(spans.items(), reverse=True):
        text = text[:start] + new + text[end:]
    total += len(spans)
    if apply:
        with open(path, 'w', encoding='utf-8', newline='') as f:
            f.write(text)
print('%s: %d literals in %d files' % ('rewrote' if apply else 'would rewrite', total, len(edits)))
print('needs a hand edit (%d):' % len(review))
for r in review:
    print('  ' + r)
