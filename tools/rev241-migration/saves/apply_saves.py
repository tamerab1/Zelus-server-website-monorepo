"""Applies the rev 241 save migration from a dry run's migrated copies.

  python apply_saves.py <data dir> <dumpDir> <dry-run report.md> [--apply]

save_migration_dryrun.py never touches a save; given a dumpDir it writes every migrated file there. This
copies back only the files whose parsed JSON changed, and only when the report's verdict is READY (every
path holding a colliding id classified, every item stack preserved under the mapping). Files are written
compactly, as the server's Gson writes them. A marker (<data>/.rev241-saves-migrated) stops a second run:
the remap is not idempotent for every type.
"""
import json, os, sys

data, dump, report = sys.argv[1:4]
apply = '--apply' in sys.argv
MARKER = os.path.join(data, '.rev241-saves-migrated')
if '**READY**' not in open(report, encoding='utf-8').read():
    sys.exit('the dry-run report is not READY; refusing')
if apply and os.path.exists(MARKER):
    sys.exit('refusing: %s exists (these saves were migrated)' % MARKER)

changed = same = 0
for dp, _, files in os.walk(dump):
    for f in files:
        src = os.path.join(dp, f)
        rel = os.path.relpath(src, dump)
        dst = os.path.join(data, rel)
        new = json.load(open(src, encoding='utf-8'))
        old = json.load(open(dst, encoding='utf-8'))
        if new == old:
            same += 1
            continue
        changed += 1
        if apply:
            with open(dst, 'w', encoding='utf-8', newline='') as fh:
                json.dump(new, fh, ensure_ascii=False, separators=(',', ':'))
if apply:
    open(MARKER, 'w').write('rev 241 save migration applied from %s\n' % os.path.abspath(dump))
print('%s %d changed files, %d unchanged' % ('migrated' if apply else 'would migrate', changed, same))
