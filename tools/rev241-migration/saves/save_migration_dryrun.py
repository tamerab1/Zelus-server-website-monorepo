"""Rev 241 save-migration DRY RUN. Reads saves, writes only a report; never touches a save file.

Usage: python save_migration_dryrun.py <remap-241.json> <data dir> <report.md> [rules.json]

1. Census: walk every JSON file under <data>/runtime/saves and <data>/gear_loadouts. Record every
   JSON path (array indices collapsed to []) whose numeric value -- or numeric object key -- is a
   colliding custom id of some type. Old colliding ids are unambiguous in rev 231 saves: vanilla
   231 never used them, so in a save they can only mean our custom content (or be an unrelated
   number that happens to fall in the range, e.g. xp or a timestamp fragment).
2. Classify each path with rules.json: "item" (remap with the item table), "npc", "obj", ... or
   "ignore" (not an id). Built-in rule: {"id": N, "amount": ...} objects are items.
   A path with hits but no rule is UNCLASSIFIED -> the real migration must refuse to run.
3. Simulate: apply the mapping in memory and check per file that item counts/amounts are
   preserved under the mapping and that no old colliding id survives in an item path.

Special kinds (rules.json):
- "varp-index-array": an int array indexed by varp id (Player.varps). Values at old custom varp
  slots move to the new slot; the old slot is cleared (it belongs to a vanilla 241 varp now).
  The destination slot must have been empty.
- "packed-var-key": savedConfigs keys, packed as id << 16 | (1 if varbit else 0).

4. Varp relayout (optional 6th arg, varbit-moves-231-241.json from cache/VarbitMoves.java): ~1200 vanilla
   varbits changed varp/bits between 231 and 241, so a raw varp value saved in the 231 layout means
   something else in 241. After the id remap, each player's varps[] (with the raw-varp savedConfigs
   entries overlaid, as login applies them last) is re-packed: every moved varbit's value is read from its
   231 bits, all those bits are cleared, and the values are written to their 241 bits. A value that
   doesn't fit the new width is dropped to 0 (what VarPlayerRepository.set does). Varbit savedConfigs
   entries (key | 1) need nothing: login re-applies them through the 241 definitions.
"""
import json
import os
import re
import sys
from collections import Counter, defaultdict

manifest_path, data_dir, report_path = sys.argv[1], sys.argv[2], sys.argv[3]
rules_path = sys.argv[4] if len(sys.argv) > 4 else None
# optional: write the would-be migrated copies here for review (must not be inside data_dir)
dump_dir = sys.argv[5] if len(sys.argv) > 5 and sys.argv[5] != '-' else None
moves_path = sys.argv[6] if len(sys.argv) > 6 else None
if dump_dir and os.path.abspath(dump_dir).startswith(os.path.abspath(data_dir)):
	sys.exit('dump dir must be outside the data dir')

manifest = json.load(open(manifest_path, encoding='utf-8'))
maps = {k: {int(a): b for a, b in v['map'].items()} for k, v in manifest['types'].items() if v['map']}
# a model that "moves" to the same id isn't a change
maps = {k: {a: b for a, b in m.items() if a != b} for k, m in maps.items()}
SAVE_TYPES = ['item', 'npc', 'obj', 'varbit', 'varp', 'seq', 'spotanim']  # types that can plausibly appear in saves

rules = {'paths': {}, 'keyPaths': {}}
if rules_path and os.path.exists(rules_path):
	rules = json.load(open(rules_path, encoding='utf-8'))


def roots():
	for sub in ('runtime/saves', 'gear_loadouts'):
		base = os.path.join(data_dir, sub)
		for dirpath, _, files in os.walk(base):
			for f in files:
				if f.endswith('.json'):
					yield os.path.join(dirpath, f)


def file_group(path):
	"""Groups files of the same kind so paths aggregate: players/<stage>/<type>, attributes/<class>, ..."""
	rel = os.path.relpath(path, data_dir).replace('\\', '/')
	parts = rel.split('/')
	if parts[:3] == ['runtime', 'saves', 'players']:
		return 'players'
	if parts[:3] == ['runtime', 'saves', 'attributes']:
		return 'attributes/' + parts[3]
	if parts[:2] == ['runtime', 'saves'] and len(parts) > 3:
		return parts[2]
	return rel


def is_int(v):
	return isinstance(v, int) and not isinstance(v, bool)


def types_of(n):
	return [t for t in SAVE_TYPES if t in maps and n in maps[t]]


def packed_types(n):
	"""savedConfigs-style key: id << 16 | flag (flag 1 = varbit, 0 = varp)."""
	if n < 65536 or (n & 0xFFFF) not in (0, 1):
		return []
	kind = 'varbit' if n & 1 else 'varp'
	return ['packed-' + kind] if n >> 16 in maps.get(kind, {}) else []


hits = defaultdict(lambda: {'count': 0, 'files': set(), 'samples': Counter(), 'types': Counter()})
key_hits = defaultdict(lambda: {'count': 0, 'files': set(), 'samples': Counter(), 'types': Counter()})
item_objects = defaultdict(int)


def walk(node, path, group, fname):
	if isinstance(node, dict):
		item_like = 'id' in node and 'amount' in node and is_int(node.get('id'))
		for k, v in node.items():
			if re.fullmatch(r'-?\d+', k):
				n = int(k)
				ts = types_of(n) + packed_types(n)
				if ts:
					kp = (group, path + '.{key}')
					key_hits[kp]['count'] += 1
					key_hits[kp]['files'].add(fname)
					key_hits[kp]['samples'][n] += 1
					for t in ts: key_hits[kp]['types'][t] += 1
				child = path + '.{key}'
			else:
				child = path + '.' + k
			if item_like and k == 'id':
				item_objects[(group, child)] += 1
			walk(v, child, group, fname)
	elif isinstance(node, list):
		for v in node:
			walk(v, path + '[]', group, fname)
	elif is_int(node):
		ts = types_of(node)
		if ts:
			hp = (group, path)
			hits[hp]['count'] += 1
			hits[hp]['files'].add(fname)
			hits[hp]['samples'][node] += 1
			for t in ts: hits[hp]['types'][t] += 1


all_files = list(roots())


def is_blank(f):
	with open(f, 'rb') as fh:
		return not fh.read().strip(b'\x00 \t\r\n')


players_dir = os.path.join(data_dir, 'runtime', 'saves', 'players')
account_names = {os.path.splitext(n)[0] for _, _, fs in os.walk(players_dir) for n in fs if n.endswith('.json')}


def is_orphan(f):
	return os.path.splitext(os.path.basename(f))[0] not in account_names


# Empty or NUL-filled files (a crash mid-write) that belong to no account are an existing quirk:
# the migration copies them through unchanged, so they are reported but don't block it.
# A blank file that belongs to a real account still counts as unreadable and blocks.
empty_files = [f for f in all_files if is_blank(f) and is_orphan(f)]
files = [f for f in all_files if f not in set(empty_files)]
parse_errors = []
for f in files:
	try:
		doc = json.load(open(f, encoding='utf-8'))
	except Exception as e:
		parse_errors.append((f, str(e)))
		continue
	walk(doc, '$', file_group(f), os.path.relpath(f, data_dir))


def classify(group, path, is_key):
	table = rules['keyPaths'] if is_key else rules['paths']
	for pattern, kind in table.items():
		g, p = pattern.split('|', 1)
		if (g == '*' or g == group) and re.fullmatch(p, path):
			return kind
	if not is_key and (group, path) in item_objects:
		return 'item'  # built-in: {"id": N, "amount": ...}
	return None


# ---- simulate the item remap per file and check invariants
item_map = maps.get('item', {})
old_item_ids = set(item_map)
sim = {'files': 0, 'itemFieldsChanged': 0, 'varpSlotsMoved': 0, 'packedKeysChanged': 0, 'violations': [],
	'relayoutFiles': 0, 'relayoutValues': 0, 'relayoutDropped': 0}

# vanilla varbits that moved 231 -> 241; our remapped custom varbits are excluded (the id remap handles them)
moves = []
if moves_path:
	for k, v in json.load(open(moves_path, encoding='utf-8')).items():
		if int(k) not in maps.get('varbit', {}):
			moves.append((int(k),) + tuple(v))


def bits(lsb, msb):
	return (1 << (msb - lsb + 1)) - 1


def to_int32(v):
	v &= 0xFFFFFFFF
	return v - (1 << 32) if v & 0x80000000 else v


def relayout_player(doc):
	"""Re-packs moved vanilla varbits in doc['varps'] and the raw-varp savedConfigs entries (see 4. above)."""
	arr = doc.get('varps')
	if not moves or not isinstance(arr, list):
		return doc
	configs = doc.get('savedConfigs') if isinstance(doc.get('savedConfigs'), dict) else {}
	raw = {int(k) >> 16: k for k in configs if re.fullmatch(r'\d+', k) and int(k) & 0xFFFF == 0}
	view = defaultdict(int)
	for i, v in enumerate(arr):
		if v:
			view[i] = v
	for varp, k in raw.items():
		view[varp] = configs[k]
	before = dict(view)
	values = []
	for vb, ov, ol, om, nv, nl, nm in moves:
		values.append((view[ov] >> ol) & bits(ol, om) if ov in view else 0)
	for vb, ov, ol, om, nv, nl, nm in moves:
		if ov in view:
			view[ov] &= ~(bits(ol, om) << ol)
	moved = 0
	for (vb, ov, ol, om, nv, nl, nm), val in zip(moves, values):
		if not val:
			continue
		if val > bits(nl, nm):
			sim['relayoutDropped'] += 1
			continue
		view[nv] = (view[nv] & ~(bits(nl, nm) << nl)) | val << nl
		moved += 1
	# only varps whose bits the relayout changed are written back; every other varp keeps its saved value
	touched = {i: to_int32(v) for i, v in view.items() if to_int32(v) != to_int32(before.get(i, 0))}
	if not touched:
		return doc
	sim['relayoutFiles'] += 1
	sim['relayoutValues'] += moved
	new_arr = list(arr)
	for i, v in touched.items():
		if i >= len(new_arr):
			new_arr.extend([0] * (i + 1 - len(new_arr)))
		new_arr[i] = v
	out = dict(doc)
	out['varps'] = new_arr
	if any(i in raw for i in touched):
		new_configs = dict(configs)
		for i, v in touched.items():
			if i in raw:
				new_configs[raw[i]] = v
		out['savedConfigs'] = new_configs
	return out
current_file = [None]


def remap_varp_array(node):
	vm = maps.get('varp', {})
	arr = list(node)
	for old, new in vm.items():
		if old < len(arr) and arr[old]:
			if new >= len(arr):
				arr.extend([0] * (new + 1 - len(arr)))
			if arr[new]:
				sim['violations'].append((current_file[0], 'varps[%d] not empty, cannot receive varps[%d]' % (new, old)))
			arr[new] = arr[old]
			arr[old] = 0
			sim['varpSlotsMoved'] += 1
	if sorted(x for x in arr if x) != sorted(x for x in node if x):
		sim['violations'].append((current_file[0], 'varps values not preserved'))
	return arr


def remap_packed_key(n):
	if n >= 65536 and (n & 0xFFFF) in (0, 1):
		kind = 'varbit' if n & 1 else 'varp'
		if n >> 16 in maps.get(kind, {}):
			sim['packedKeysChanged'] += 1
			return maps[kind][n >> 16] << 16 | (n & 1)
	return n


def remap(node, path, group, counts_before, counts_after):
	if isinstance(node, list) and classify(group, path + '[]', False) == 'varp-index-array':
		return remap_varp_array(node)
	if isinstance(node, dict):
		out = {}
		item_like = 'id' in node and 'amount' in node and is_int(node.get('id'))
		for k, v in node.items():
			nk = k
			key_kind = classify(group, path + '.{key}', True) if re.fullmatch(r'-?\d+', k) else None
			if key_kind == 'item' and int(k) in item_map:
				nk = str(item_map[int(k)])
			elif key_kind == 'packed-var-key':
				nk = str(remap_packed_key(int(k)))
			if nk in out:
				sim['violations'].append((current_file[0], 'key %s collides after remap at %s' % (nk, path)))
			child = path + ('.{key}' if re.fullmatch(r'-?\d+', k) else '.' + k)
			out[nk] = remap(v, child, group, counts_before, counts_after)
		if item_like:
			amt = node.get('amount') if is_int(node.get('amount')) else 0
			counts_before[node['id']] += amt
			counts_after[out['id']] += amt
		return out
	if isinstance(node, list):
		return [remap(v, path + '[]', group, counts_before, counts_after) for v in node]
	if is_int(node) and node in item_map and classify(group, path, False) == 'item':
		sim['itemFieldsChanged'] += 1
		return item_map[node]
	return node


for f in files:
	try:
		doc = json.load(open(f, encoding='utf-8'))
	except Exception:
		continue
	group = file_group(f)
	current_file[0] = os.path.relpath(f, data_dir)
	before, after = Counter(), Counter()
	new_doc = remap(doc, '$', group, before, after)
	if group == 'players' and isinstance(new_doc, dict):
		new_doc = relayout_player(new_doc)
	if dump_dir:
		target = os.path.join(dump_dir, current_file[0])
		os.makedirs(os.path.dirname(target), exist_ok=True)
		json.dump(new_doc, open(target, 'w', encoding='utf-8'))
	sim['files'] += 1
	expected = Counter()
	for i, a in before.items():
		expected[item_map.get(i, i)] += a
	if expected != after:
		sim['violations'].append((os.path.relpath(f, data_dir), 'item amounts not preserved under the mapping'))
	leftovers = [i for i in after if i in old_item_ids]
	if leftovers:
		sim['violations'].append((os.path.relpath(f, data_dir), 'old colliding item ids left in item objects: %s' % leftovers[:5]))

# ---- report
out = []
w = out.append
w('# Rev 241 save migration -- dry run report\n')
w('Read-only. Data: `%s`. Manifest: `%s`.\n' % (data_dir, os.path.basename(manifest_path)))
w('- JSON files scanned: **%d** (%d unreadable; %d empty files passed through unchanged)' % (len(files), len(parse_errors), len(empty_files)))
w('- Remap tables used: ' + ', '.join('%s (%d ids)' % (t, len(maps[t])) for t in SAVE_TYPES if t in maps))
w('- Simulation: %d files; %d item fields, %d varp slots and %d packed var keys would change; **%d invariant violations**\n' % (
	sim['files'], sim['itemFieldsChanged'], sim['varpSlotsMoved'], sim['packedKeysChanged'], len(sim['violations'])))
if moves_path:
	w('- Varp relayout (%d moved vanilla varbits): %d player files re-packed, %d varbit values moved, %d dropped (value too wide for the 241 bits)\n' % (
		len(moves), sim['relayoutFiles'], sim['relayoutValues'], sim['relayoutDropped']))
else:
	w('- Varp relayout: **not run** (no varbit-moves file given)\n')

rows = []
for (group, path), h in list(hits.items()):
	rows.append((classify(group, path, False), group, path, h, False))
for (group, path), h in list(key_hits.items()):
	rows.append((classify(group, path, True), group, path, h, True))

unclassified = [r for r in rows if r[0] is None]
w('## Verdict\n')
if unclassified or sim['violations'] or parse_errors:
	w('**NOT READY**: %d unclassified paths, %d violations, %d unreadable files. The real migration must refuse to run until these are resolved.\n' % (len(unclassified), len(sim['violations']), len(parse_errors)))
else:
	w('**READY**: every path holding a colliding id is classified and the simulated remap preserves every item stack.\n')


def table(title, selected):
	w('## %s\n' % title)
	if not selected:
		w('(none)\n')
		return
	w('| class | file group | JSON path | hits | files | id types matched | sample values |')
	w('|---|---|---|---|---|---|---|')
	for kind, group, path, h, is_key in sorted(selected, key=lambda r: (str(r[0]), r[1], r[2])):
		sample = ', '.join('%d' % v for v, _ in h['samples'].most_common(6))
		types = ', '.join('%s' % t for t, _ in h['types'].most_common())
		w('| %s | %s | `%s`%s | %d | %d | %s | %s |' % (kind or '**UNCLASSIFIED**', group, path, ' (key)' if is_key else '', h['count'], len(h['files']), types, sample))
	w('')


table('Unclassified paths (need a rule)', unclassified)
table('Classified paths', [r for r in rows if r[0] is not None])
if sim['violations']:
	w('## Invariant violations\n')
	for f, msg in sim['violations']:
		w('- `%s`: %s' % (f, msg))
	w('')
if parse_errors:
	w('## Unreadable files\n')
	for f, e in parse_errors:
		w('- `%s`: %s' % (f, e))
open(report_path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
if empty_files:
	w('\n## Empty files (copied through unchanged)\n')
	for f in empty_files:
		w('- `%s`' % os.path.relpath(f, data_dir))
	open(report_path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print('files=%d empty=%d unclassified=%d classified=%d violations=%d unreadable=%d itemFields=%d varpSlots=%d packedKeys=%d' % (
	len(files), len(empty_files), len(unclassified), len(rows) - len(unclassified), len(sim['violations']), len(parse_errors),
	sim['itemFieldsChanged'], sim['varpSlotsMoved'], sim['packedKeysChanged']))
