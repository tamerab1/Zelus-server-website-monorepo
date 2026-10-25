"""Rev 241 id remap of int literals in the server's Java/Kotlin sources.

  python remap_java.py <manifest> <decisions.tsv> <repoRoot> <report.md> [--apply]

Every int literal equal to a MOVING old id (manifest entries whose new id differs) needs a reviewed
decision in decisions.tsv: its semantic type (item/npc/obj/seq/spotanim/sound/clientscript/varp/
varbit/model) or "none" (a coordinate, amount, colour, region, ...). The same number means different
things in different places (60010 is a custom item AND a custom object), so a literal is only
rewritten when its decided type moves it. Decisions are matched by file + value + occurrence on the
line + the line's code text, so unrelated edits that shift line numbers don't invalidate them.

Literals with no decision are listed and --apply refuses to run until each one has been reviewed.
In the generated vanilla constant files (cache/ItemID, NpcID, ObjectID) a hit is decided by the
file: only custom entries are in moving ranges for their own type.
"""
import json, os, re, sys, collections

PRUNE = {'net', 'org', 'tools', '.git', 'build', '.gradle', 'website', '.dev', 'buildSrc'}
LIT = re.compile(r'(?<![\w.])(\d{3,6})(?![\w.])')
CONSTANT_FILES = {'ItemID.java': 'item', 'NpcID.java': 'npc', 'ObjectID.java': 'obj'}
TYPES = ['item', 'npc', 'obj', 'seq', 'spotanim', 'sound', 'clientscript', 'varp', 'varbit', 'model']


def code_spans(line, in_block):
    """Yields (start, end) of code outside comments and string literals; returns new in_block."""
    spans, i, n, start = [], 0, len(line), 0
    while i < n:
        if in_block:
            e = line.find('*/', i)
            if e < 0:
                return spans, True
            i = start = e + 2
            in_block = False
            continue
        c = line[i]
        if line.startswith('//', i):
            spans.append((start, i))
            return spans, False
        if line.startswith('/*', i):
            spans.append((start, i))
            in_block = True
            i += 2
            continue
        if c == '"' or c == "'":
            spans.append((start, i))
            j = i + 1
            while j < n and line[j] != c:
                j += 2 if line[j] == '\\' else 1
            i = start = j + 1
            continue
        i += 1
    spans.append((start, n))
    return spans, in_block


def norm(code):
    return re.sub(r'\s+', ' ', code).strip()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    apply = '--apply' in sys.argv
    man_path, dec_path, root, report = args
    manifest = json.load(open(man_path))['types']
    moves = {t: {int(a): int(b) for a, b in manifest[t]['map'].items() if int(a) != int(b)} for t in TYPES}
    moving_any = set().union(*[set(m) for m in moves.values()])

    decisions = {}
    for line in open(dec_path, encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        f, ln, v, occ, t, source, code = line.rstrip('\n').split('\t', 6)
        if t != 'none' and t not in TYPES:
            sys.exit('bad type %r in decisions: %s' % (t, line))
        decisions[(f, int(v), int(occ), norm(code))] = t

    stats = collections.Counter()
    unresolved = []
    used = set()
    changed_files = 0
    for dirpath, dirs, files in os.walk(root):
        rel_root = os.path.relpath(dirpath, root).replace(os.sep, '/')
        dirs[:] = [d for d in dirs if not (d in PRUNE and (rel_root == '.' or d in ('build', '.gradle')))]
        for fname in files:
            if not fname.endswith(('.java', '.kt')):
                continue
            path = os.path.join(dirpath, fname)
            rel = os.path.relpath(path, root).replace(os.sep, '/')
            with open(path, encoding='utf-8', newline='') as fh:
                lines = fh.readlines()
            in_block = False
            edits = 0
            for li, raw in enumerate(lines):
                spans, in_block_next = code_spans(raw, in_block)
                in_block = in_block_next
                hits = []
                for a, b in spans:
                    for m in LIT.finditer(raw, a, b):
                        v = int(m.group(1))
                        if v in moving_any:
                            hits.append((m.start(1), m.end(1), v))
                if not hits:
                    continue
                code = norm(raw.rstrip('\r\n'))
                new_line = raw
                hits_by_occ = []
                seen = collections.Counter()
                for a, b, v in sorted(hits):
                    seen[v] += 1
                    hits_by_occ.append((a, b, v, seen[v]))
                for a, b, v, occ in sorted(hits_by_occ, reverse=True):
                    if fname in CONSTANT_FILES and 'cache/' in rel:
                        t = CONSTANT_FILES[fname]
                    else:
                        key = (rel, v, occ, code)
                        if key not in decisions:
                            unresolved.append((rel, li + 1, v, occ, raw.strip()))
                            continue
                        used.add(key)
                        t = decisions[key]
                    if t == 'none' or v not in moves[t]:
                        stats[('stay', t)] += 1
                        continue
                    stats[('move', t)] += 1
                    new_line = new_line[:a] + str(moves[t][v]) + new_line[b:]
                    edits += 1
                lines[li] = new_line
            if edits:
                changed_files += 1
                if apply and not unresolved:
                    with open(path, 'w', encoding='utf-8', newline='') as fh:
                        fh.writelines(lines)

    stale = [k for k in decisions if k not in used]
    out = ['# Rev 241 Java literal remap: %s' % ('APPLIED' if apply and not unresolved else 'dry run'), '',
           '- files with rewrites: %d' % changed_files,
           '- literals moved: %s' % ', '.join('%s %d' % (t, n) for (k, t), n in sorted(stats.items()) if k == 'move'),
           '- literals kept: %d' % sum(n for (k, _), n in stats.items() if k == 'stay'),
           '- unresolved (no decision): %d' % len(unresolved),
           '- decisions that matched nothing (code changed since review): %d' % len(stale), '']
    if unresolved:
        out += ['## Unresolved', '', '| file | line | value | occ | code |', '|---|---|---|---|---|']
        out += ['| %s | %d | %d | %d | `%s` |' % (r[0], r[1], r[2], r[3], r[4][:120].replace('|', '\\|')) for r in unresolved]
    if stale:
        out += ['', '## Stale decisions', ''] + ['- %s %d (occ %d): `%s`' % (k[0], k[1], k[2], k[3][:120]) for k in stale]
    with open(report, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(out) + '\n')
    print('\n'.join(out[:8]))
    if apply and unresolved:
        sys.exit('refusing to apply: %d unresolved literals' % len(unresolved))


if __name__ == '__main__':
    main()
