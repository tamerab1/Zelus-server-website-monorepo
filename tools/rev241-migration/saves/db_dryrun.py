"""Rev 241 DB dry run: which rows of the game database (MariaDB dump) hold colliding item ids.

Usage: python db_dryrun.py <remap-241.json> <mariadb.sql.gz> <report.md>

Read-only: parses the dump file, never connects to a database. For every table with an
item-id column, counts rows whose item id is an old colliding custom id (they would be rewritten
by the migration) and checks that no rewritten id lands on an id already used in that column.
"""
import gzip
import json
import re
import sys
from collections import Counter, defaultdict

manifest_path, dump_path, report_path = sys.argv[1:4]
item_map = {int(k): v for k, v in json.load(open(manifest_path, encoding='utf-8'))['types']['item']['map'].items()}

# table -> (column holding an item id, what the table is)
TARGETS = {
	'tp_containers': ('item_id', 'live trading post containers'),
	'tradepost_offers': ('item_id', 'live trading post offers'),
	'tradepost_collections': ('item_id', 'uncollected trading post items'),
	'tradepost_history': ('item_id', 'trading post history'),
	'tp_history': ('item_id', 'trading post history (old)'),
	'group_items': ('item_id', 'group item data'),
	'item': ('item_id', 'item table'),
	'products': ('item_id', 'store products'),
	'logs_item_drops': ('item_id', 'drop logs'),
	'logs_item_pickups': ('item_id', 'pickup logs'),
	'logs_drop_trades': ('item_id', 'drop-trade logs'),
}


def parse_values(s):
	"""Yields tuples from the VALUES part of an INSERT statement (MySQL dump quoting)."""
	i, n = 0, len(s)
	while i < n:
		if s[i] != '(':
			i += 1
			continue
		i += 1
		row, cur, in_str = [], [], False
		while i < n:
			c = s[i]
			if in_str:
				if c == '\\':
					cur.append(s[i:i + 2]); i += 2; continue
				if c == "'":
					in_str = False
				cur.append(c)
			elif c == "'":
				in_str = True; cur.append(c)
			elif c == ',':
				row.append(''.join(cur)); cur = []
			elif c == ')':
				row.append(''.join(cur)); i += 1
				break
			else:
				cur.append(c)
			i += 1
		yield row


columns = {}
db = None
rows = defaultdict(list)
with gzip.open(dump_path, 'rt', encoding='utf-8', errors='replace') as fh:
	table = None
	insert_table, insert_buf = None, []
	for line in fh:
		if insert_table is not None:
			# a dump may put each row of one INSERT on its own line; collect until the statement ends
			insert_buf.append(line)
			if line.rstrip().endswith(');'):
				if db == 'reason' and insert_table in TARGETS:
					rows[insert_table].extend(parse_values(''.join(insert_buf)))
				insert_table, insert_buf = None, []
			continue
		m = re.match(r'CREATE DATABASE .*?`([^`]+)`', line)
		if m:
			db = m.group(1)
			continue
		m = re.match(r'CREATE TABLE `([^`]+)`', line)
		if m:
			table = m.group(1)
			if db == 'reason':
				columns[table] = []
			continue
		if table in columns and db == 'reason':
			m = re.match(r'\s+`([^`]+)`\s', line)
			if m:
				columns[table].append(m.group(1))
		m = re.match(r'INSERT INTO `([^`]+)` VALUES\s*(.*)$', line, re.S)
		if m:
			insert_table, insert_buf = m.group(1), [m.group(2)]
			if m.group(2).rstrip().endswith(');'):
				if db == 'reason' and insert_table in TARGETS:
					rows[insert_table].extend(parse_values(m.group(2)))
				insert_table, insert_buf = None, []

out = ['# Rev 241 DB dry run (MariaDB `reason`)\n', 'Read-only, from the dump file. Item remap: %d ids.\n' % len(item_map)]
out.append('| table | what | rows | rows with colliding item ids | distinct colliding ids | collision after remap |')
out.append('|---|---|---|---|---|---|')
problems = 0
for table, (col, what) in TARGETS.items():
	if table not in columns:
		out.append('| %s | %s | (table not in dump) | | | |' % (table, what))
		continue
	idx = columns[table].index(col)
	ids = []
	for r in rows[table]:
		try:
			ids.append(int(r[idx]))
		except (ValueError, IndexError):
			pass
	hits = [i for i in ids if i in item_map]
	existing = set(ids)
	# a remapped id must not already be in use in the same column (it would merge two items)
	clash = sorted({item_map[i] for i in hits if item_map[i] in existing})
	problems += len(clash)
	out.append('| %s | %s | %d | %d | %d | %s |' % (table, what, len(ids), len(hits), len(set(hits)), clash[:5] if clash else 'none'))
verdict = 'READY' if problems == 0 else 'NOT READY'
out.insert(2, '**Verdict: %s** (%d remap targets already present in their column).\n' % (verdict, problems))
open(report_path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print('\n'.join(out))
