"""Undoes the wrong part of the rev 241 varp relayout (2026-10-29).

save_migration_dryrun.py re-packed 1214 "moved" vanilla varbits from their 231 bits to the 241 bits of the SAME id. For
1110 of them that was wrong: rev 241 renumbered those varbits (SLAYER_BLOCKED_1 was 3209, is 17807) and kept them in
the same varp and bits, so the migration cleared good data and wrote it into unrelated vars (3209 is now
CA_TOTAL_TASKS_COMPLETED_DOM). A move is kept only if the id kept its name (19 true relayouts) or has no 231 name (85).

Per player, from the pre-switch save (backup) B:
  wrong = relayout(B, all moves)          what the migration wrote
  right = relayout(B, kept moves only)    what it should have written
and for every varp where they differ: result = right, with the bits the player changed since the switch
(current ^ wrong) kept from current. Every other varp, and the rest of the save, is left alone.

  python fix_relayout.py <live data dir> <backup data dir> <varbit-moves.json> <remap-241.json>
                         <VarbitID_231.java> <VarbitID_241.java> <report.md> [--apply]
"""
import json, os, re, sys
from collections import defaultdict

live, backup, moves_path, remap_path, names231_path, names241_path, report = sys.argv[1:8]
apply = '--apply' in sys.argv
MARKER = os.path.join(live, '.rev241-relayout-fixed')
if apply and os.path.exists(MARKER):
	sys.exit('refusing: %s exists (already fixed)' % MARKER)


def names(path):
	m = {}
	for n, v in re.findall(r'public static final int (\w+) = (\d+);', open(path, encoding='utf-8').read()):
		m.setdefault(int(v), n)
	return m


n231, n241 = names(names231_path), names(names241_path)
custom = set(int(k) for k in json.load(open(remap_path, encoding='utf-8')).get('varbit', {}))
all_moves, kept = [], []
for k, v in json.load(open(moves_path, encoding='utf-8')).items():
	vb = int(k)
	if vb in custom:
		continue
	move = (vb,) + tuple(v)
	all_moves.append(move)
	a, b = n231.get(vb), n241.get(vb)
	if a is None or a == b or b == a + '_OLD':
		kept.append(move)


def bits(lsb, msb):
	return (1 << (msb - lsb + 1)) - 1


def u32(v):
	return v & 0xFFFFFFFF


def to_int32(v):
	v &= 0xFFFFFFFF
	return v - (1 << 32) if v & 0x80000000 else v


def view_of(doc):
	"""varp -> value from doc['varps'] and the raw-varp savedConfigs entries, as the migration read them."""
	view = defaultdict(int)
	for i, v in enumerate(doc.get('varps') or []):
		if v:
			view[i] = u32(v)
	configs = doc.get('savedConfigs') if isinstance(doc.get('savedConfigs'), dict) else {}
	for k, v in configs.items():
		if re.fullmatch(r'\d+', k) and int(k) & 0xFFFF == 0 and isinstance(v, int):
			view[int(k) >> 16] = u32(v)
	return view


def relayout(base, moves):
	view = defaultdict(int, base)
	values = [(view[ov] >> ol) & bits(ol, om) if ov in view else 0 for vb, ov, ol, om, nv, nl, nm in moves]
	for vb, ov, ol, om, nv, nl, nm in moves:
		if ov in view:
			view[ov] &= ~(bits(ol, om) << ol)
	for (vb, ov, ol, om, nv, nl, nm), val in zip(moves, values):
		if val and val <= bits(nl, nm):
			view[nv] = (view[nv] & ~(bits(nl, nm) << nl)) | val << nl
	return view


stats = defaultdict(int)
lines = []
for dp, _, files in os.walk(os.path.join(live, 'runtime', 'saves', 'players')):
	for f in files:
		if not f.endswith('.json'):
			continue
		path = os.path.join(dp, f)
		rel = os.path.relpath(path, live)
		bpath = os.path.join(backup, rel)
		stats['files'] += 1
		if not os.path.exists(bpath):
			stats['no_backup'] += 1  # made after the switch: never migrated
			continue
		try:
			doc, bdoc = json.load(open(path, encoding='utf-8')), json.load(open(bpath, encoding='utf-8'))
		except Exception:
			stats['unreadable'] += 1
			continue
		if not isinstance(doc.get('varps'), list):
			continue
		base = view_of(bdoc)
		wrong, right, current = relayout(base, all_moves), relayout(base, kept), view_of(doc)
		changes = {}
		for i in set(wrong) | set(right):
			w, r = u32(wrong.get(i, 0)), u32(right.get(i, 0))
			if w == r:
				continue
			c = u32(current.get(i, 0))
			since = c ^ w  # bits the player changed after the switch
			result = (r & ~since) | (c & since)
			if result != c:
				changes[i] = result
		if not changes:
			continue
		stats['fixed_files'] += 1
		stats['fixed_varps'] += len(changes)
		if len(lines) < 20:
			lines.append('- %s: %d varps' % (rel, len(changes)))
		if apply:
			arr = doc['varps']
			configs = doc.get('savedConfigs') if isinstance(doc.get('savedConfigs'), dict) else {}
			for i, v in changes.items():
				key = str(i << 16)
				if key in configs and isinstance(configs[key], int):
					configs[key] = to_int32(v)
				if i < len(arr):
					arr[i] = to_int32(v)
			with open(path, 'w', encoding='utf-8', newline='') as fh:
				json.dump(doc, fh, ensure_ascii=False, separators=(',', ':'))

with open(report, 'w', encoding='utf-8') as out:
	out.write('# rev 241 relayout fix\n\n')
	out.write('- moves: %d (kept %d, undone %d)\n' % (len(all_moves), len(kept), len(all_moves) - len(kept)))
	for k in ('files', 'no_backup', 'unreadable', 'fixed_files', 'fixed_varps'):
		out.write('- %s: %d\n' % (k, stats[k]))
	out.write('\n' + '\n'.join(lines) + '\n')
if apply:
	open(MARKER, 'w').write('rev 241 relayout fix applied\n')
print(('fixed' if apply else 'would fix'), dict(stats), 'kept moves', len(kept), 'of', len(all_moves))
