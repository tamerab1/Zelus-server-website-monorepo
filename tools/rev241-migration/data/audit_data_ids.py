"""Rev 241 data audit: every id the server data references (by the remap rules) must exist in the cache.

  python audit_data_ids.py <rules.json> <dataDir> <ids.json> <report.md>

ids.json holds the defined ids per type in the target cache (ExportIds). Run it on the migrated data dir
against cache241: a miss is a reference to something the 241 cache doesn't define (a remap gap, a
displaced vanilla id, or a reference that was already dangling in 231). -1 and 0 are treated as "none".
"""
import json, os, re, sys, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lenjson
from remap_data import glob_re


def main():
    rules_path, data, ids_path, report = sys.argv[1:5]
    rules = json.load(open(rules_path))
    ids = {k: set(v) for k, v in json.load(open(ids_path)).items()}
    ids['obj'] = ids.get('obj', set())
    jrules = [(glob_re(r['glob']), re.compile(r['path']), r['type']) for r in rules['json']]
    skip = set(rules['skip'])

    misses = collections.defaultdict(list)  # (file group, path, type) -> [(file, value)]
    checked = collections.Counter()
    for root, dirs, files in os.walk(data):
        rel_root = os.path.relpath(root, data).replace(os.sep, '/')
        rel_root = '' if rel_root == '.' else rel_root
        dirs[:] = [d for d in dirs if 'backup' not in d.lower() and ((rel_root + '/' + d) if rel_root else d) not in skip]
        for f in files:
            if not f.endswith('.json'):
                continue
            rel = (rel_root + '/' + f) if rel_root else f
            if rel in skip:
                continue
            with open(os.path.join(root, f), encoding='utf-8', errors='surrogateescape', newline='') as fh:
                text = fh.read()
            try:
                toks = lenjson.scan(text)
            except Exception:
                continue
            for a, b, v, path, is_key, in_str in toks:
                for g, rx, t in jrules:
                    if g.match(rel) and rx.search(path):
                        if v in (-1, 0):
                            break
                        checked[t] += 1
                        if t in ids and v not in ids[t]:
                            group = rel.rsplit('/', 1)[0] + '/*' if '/' in rel else rel
                            gpath = re.sub(r'^\.[A-Z][A-Z0-9_]*\.', '.<NAME>.', path)
                            misses[(group, gpath, t)].append((rel, v))
                        break

    total = sum(len(v) for v in misses.values())
    out = ['# Rev 241 data id audit', '', 'Data dir: `%s`' % os.path.abspath(data), '',
           '- references checked: %s' % dict(checked),
           '- references to ids the cache does not define: %d' % total, '',
           '| files | path | type | misses | examples |', '|---|---|---|---|---|']
    for (group, path, t), lst in sorted(misses.items(), key=lambda kv: -len(kv[1])):
        ex = ', '.join('%s=%d' % (os.path.basename(r), v) for r, v in lst[:4])
        out.append('| %s | `%s` | %s | %d | %s |' % (group, path, t, len(lst), ex))
    with open(report, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(out) + '\n')
    print('\n'.join(out[:40]))


if __name__ == '__main__':
    main()
