"""Rev 241 id remap of the server's static data (everything the save migration doesn't own).

  python remap_data.py <manifest> <rules.json> <dataDir> <report.md> [--apply]

Dry run by default: writes only the report. --apply patches the files in place: numbers are
replaced by byte span, so comments, spacing and line endings are untouched, and files named by an
id (newDrops/<npc>.json, item_icons/<item>.png) are renamed. A marker file
(<dataDir>/.rev241-data-remapped) makes a second --apply refuse to run: the manifest's model map
has a chain, so the remap isn't idempotent for every type.

The report also lists "unruled" numbers: values equal to a MOVING old id that sit at a path with
no rule but whose key looks like an id (id/item/npc/anim/gfx/model/obj/sound). Each one is either
a missing rule or a coincidence and must be looked at before the cutover.
"""
import json, os, re, sys, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lenjson

MARKER = '.rev241-data-remapped'
IDISH = re.compile(r'(?i)(id|item|npc|anim|gfx|graphic|model|obj|sound|spot|seq)[^.]*$')


def glob_re(g):
    out = ''
    i = 0
    while i < len(g):
        if g.startswith('**', i):
            out += '.*'
            i += 2
        elif g[i] == '*':
            out += '[^/]*'
            i += 1
        else:
            out += re.escape(g[i])
            i += 1
    return re.compile('^' + out + '$')


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    apply = '--apply' in sys.argv
    man_path, rules_path, data, report = args
    manifest = json.load(open(man_path))['types']
    moves = {t: {int(a): int(b) for a, b in v['map'].items() if int(a) != int(b)} for t, v in manifest.items()}
    rules = json.load(open(rules_path))
    jrules = [(glob_re(r['glob']), re.compile(r['path']), r['type']) for r in rules['json']]
    yrules = [(glob_re(r['glob']), r) for r in rules['yaml']]
    frules = [(glob_re(r['glob']), r['type']) for r in rules['filenames']]
    skip = set(rules['skip'])
    for _, _, t in jrules:
        assert t in moves, 'unknown type ' + t

    # dropIds: reviewed ids whose entries are dead in our 231 data (the id was undefined in our cache) but would
    # bind to Jagex content in 241; they are removed from the id arrays. --drops-only applies just this step
    # (idempotent) to an already-remapped data dir.
    drops = {t: set(v) for t, v in rules.get('dropIds', {}).items() if not t.startswith('_')}
    drops_only = '--drops-only' in sys.argv
    if apply and not drops_only and os.path.exists(os.path.join(data, MARKER)):
        sys.exit('refusing: %s already exists (this data dir was remapped)' % MARKER)

    stats = collections.Counter()      # (file group, rule path/type) -> replacements
    unruled = collections.Counter()    # (file, path, type) -> count
    unruled_ex = {}
    files_changed = 0
    renames = []
    errors = []

    for root, dirs, files in os.walk(data):
        rel_root = os.path.relpath(root, data).replace(os.sep, '/')
        # backup copies (e.g. npcs/spawns_backup_*) are never loaded by the server
        dirs[:] = [d for d in dirs if 'backup' not in d.lower()]
        if rel_root == '.':
            rel_root = ''
        # skip entries are paths relative to <data> ("runtime/saves", "cache")
        dirs[:] = [d for d in dirs if ((rel_root + '/' + d) if rel_root else d) not in skip]
        for f in files:
            rel = (rel_root + '/' + f) if rel_root else f
            if rel in skip or f == MARKER:
                continue
            p = os.path.join(root, f)

            # id-named files
            for g, t in ([] if drops_only else frules):
                if g.match(rel):
                    stem, ext = os.path.splitext(f)
                    if stem.isdigit() and int(stem) in moves[t]:
                        dst = os.path.join(root, '%d%s' % (moves[t][int(stem)], ext))
                        renames.append((p, dst, t))

            if f.endswith('.json'):
                with open(p, encoding='utf-8', errors='surrogateescape', newline='') as fh:
                    text = fh.read()
                try:
                    toks = lenjson.scan(text)
                except Exception as e:
                    errors.append('%s: %s' % (rel, e))
                    continue
                edits = []
                drop_arrays = {}  # '[' offset -> ']' offset of id arrays holding a dropped id
                for a, b, v, path, is_key, in_str in toks:
                    hit = None
                    for g, rx, t in jrules:
                        if g.match(rel) and rx.search(path):
                            hit = t
                            break
                    if hit and v in drops.get(hit, ()) and path.endswith('[]'):
                        lo, hi = text.rfind('[', 0, a), text.find(']', b)
                        if lo < 0 or hi < 0 or not re.fullmatch(r'[\d,\s]*', text[lo + 1:hi]):
                            errors.append('%s: dropped id %d is not in a plain number array' % (rel, v))
                        else:
                            drop_arrays[lo] = (hi, hit)
                            stats[(rel, path, 'DROPPED ' + hit)] += 1
                        continue
                    if drops_only:
                        continue
                    if hit:
                        if v in moves[hit]:
                            edits.append((a, b, str(moves[hit][v])))
                            # group per-entry keys (teleport_previews.<NAME>.model, weapon_types.<TYPE>...)
                            gpath = re.sub(r'^\.[A-Z][A-Z0-9_]*\.', '.<NAME>.', path)
                            stats[(rel.rsplit('/', 1)[0] + '/*' if '/' in rel else rel, gpath, hit)] += 1
                        continue
                    if IDISH.search(path):
                        for t, mv in moves.items():
                            if v in mv and t not in ('texture', 'frames', 'skeleton', 'overlay'):
                                unruled[(rel, path, t)] += 1
                                unruled_ex.setdefault((rel, path, t), v)
                for lo, (hi, dropped_t) in drop_arrays.items():
                    keep = [x for x in re.findall(r'\d+', text[lo + 1:hi]) if int(x) not in drops.get(dropped_t, ())]
                    edits.append((lo + 1, hi, (' ' + ', '.join(keep) + ' ') if keep else ''))
                if edits:
                    files_changed += 1
                    if apply:
                        for a, b, s in sorted(edits, reverse=True):
                            text = text[:a] + s + text[b:]
                        with open(p, 'w', encoding='utf-8', errors='surrogateescape', newline='') as fh:
                            fh.write(text)

            elif f.endswith(('.yaml', '.yml')) and not drops_only:
                for g, r in yrules:
                    if not g.match(rel):
                        continue
                    with open(p, encoding='utf-8', newline='') as fh:
                        lines = fh.readlines()
                    inside = False
                    changed = False
                    key_rx = re.compile(r'^(\s*-?\s*%s:\s*"?)(\d+)("?\s*)$' % re.escape(r['key']))
                    for i, line in enumerate(lines):
                        body = line.rstrip('\r\n')
                        eol = line[len(body):]
                        if re.match(r'^\S', body):
                            inside = body.startswith(r['under'] + ':')
                            continue
                        if not inside:
                            continue
                        m = key_rx.match(body)
                        if m and int(m.group(2)) in moves[r['type']]:
                            lines[i] = m.group(1) + str(moves[r['type']][int(m.group(2))]) + m.group(3) + eol
                            stats[(rel.rsplit('/', 1)[0] + '/*', r['under'] + '[].' + r['key'], r['type'])] += 1
                            changed = True
                    if changed:
                        files_changed += 1
                        if apply:
                            with open(p, 'w', encoding='utf-8', newline='') as fh:
                                fh.writelines(lines)

    clash = [dst for _, dst, _ in renames if os.path.exists(dst)]
    if apply and not drops_only:
        if clash:
            errors.append('rename targets already exist (not renamed): %s' % clash[:10])
        else:
            for src, dst, _ in renames:
                os.rename(src, dst)
        with open(os.path.join(data, MARKER), 'w') as fh:
            fh.write('rev 241 data remap applied with %s\n' % os.path.abspath(man_path))

    out = ['# Rev 241 static data remap: %s' % ('APPLIED' if apply else 'dry run'), '',
           'Data dir: `%s`' % os.path.abspath(data), '',
           '- files with id replacements: %d' % files_changed,
           '- replacements: %d' % sum(stats.values()),
           '- id-named files to rename: %d (%s)' % (len(renames), ', '.join('%s %d' % kv for kv in collections.Counter(t for _, _, t in renames).items()) or '-'),
           '- rename clashes: %d' % len(clash),
           '- parse errors: %d' % len(errors),
           '- unruled id-looking hits: %d' % sum(unruled.values()), '',
           '## Replacements by rule', '', '| files | path | type | count |', '|---|---|---|---|']
    for (fg, path, t), n in sorted(stats.items()):
        out.append('| %s | `%s` | %s | %d |' % (fg, path, t, n))
    out += ['', '## Renames', '']
    out += ['- %s -> %s (%s)' % (os.path.relpath(s, data), os.path.basename(d), t) for s, d, t in renames] or ['- none']
    out += ['', '## Unruled id-looking numbers (review each)', '', '| file | path | moving type | count | e.g. |', '|---|---|---|---|---|']
    for (rel, path, t), n in sorted(unruled.items()):
        out.append('| %s | `%s` | %s | %d | %d |' % (rel, path, t, n, unruled_ex[(rel, path, t)]))
    if errors:
        out += ['', '## Errors', ''] + ['- ' + e for e in errors]
    with open(report, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(out) + '\n')
    print('\n'.join(out[:12]))


if __name__ == '__main__':
    main()
