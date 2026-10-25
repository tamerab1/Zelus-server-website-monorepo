"""The component renumbering the SERVER must follow: from our live cache's layout to cache241's.

  python effective_component_map.py <component-map-241.json> <ours-alignment-231.json> <build-interfaces-report.json> <out.json>

Per group, for each of our component ids:
- groups our live cache has exactly as vanilla 231: Jagex's 231 -> 241 renumbering (gameval names),
- groups Zelus overwrote with its own interface (or rebuilt outright): nothing moves,
- groups Zelus edited ("aligned"): our id -> the vanilla 231 component it lines up with (AlignOurs231)
  -> Jagex's renumbering; our own components keep their id unless stage 4 moved them (componentRemap).
Output: {group: {"name": ..., "moved": {ours: new}, "removed": {ours: vanilla name}}}."""
import json, sys

names, align_path, report, out_path = sys.argv[1:5]
cmap = json.load(open(names))
aligns = json.load(open(align_path))
rep = json.load(open(report))
custom_moves = {}
for r in rep['componentRemap']:
    custom_moves.setdefault(r['from'] >> 16, {})[r['from'] & 0xffff] = r['to'] & 0xffff


def vanilla_to(g, c):
    info = cmap.get(str(g), {})
    if str(c) in info.get('removed', {}):
        return None, info['removed'][str(c)]
    return info.get('moved', {}).get(str(c), c), None


out = {}
for g, info in cmap.items():
    if g in aligns or info.get('groupRemoved'):
        continue
    if info.get('moved') or info.get('removed'):
        out[int(g)] = {'name': info['name'], 'moved': {int(a): b for a, b in info.get('moved', {}).items()},
                       'removed': {int(a): n for a, n in info.get('removed', {}).items()}}
for g, al in aligns.items():
    g = int(g)
    if al['mode'] != 'aligned':
        continue
    moved, removed = {}, {}
    for k in al['ours']:
        if k in al['custom']:
            to = custom_moves.get(g, {}).get(k, k)
        else:
            b = int(al['align'].get(str(k), k))
            if k in custom_moves.get(g, {}):  # our edit stage 4 moved out of Jagex's way (bank 120 -> 158)
                to = custom_moves[g][k]
            else:
                to, gone = vanilla_to(g, b)
                if to is None:
                    removed[k] = gone
                    continue
        if to != k:
            moved[k] = to
    if moved or removed:
        out[g] = {'name': cmap.get(str(g), {}).get('name', str(g)), 'moved': moved, 'removed': removed}
json.dump({str(k): {'name': v['name'], 'moved': {str(a): b for a, b in sorted(v['moved'].items())},
                    'removed': {str(a): n for a, n in sorted(v['removed'].items())}}
           for k, v in sorted(out.items())}, open(out_path, 'w'), indent=1)
print('groups', len(out), 'moved', sum(len(v['moved']) for v in out.values()),
      'removed', sum(len(v['removed']) for v in out.values()),
      'overwritten/outright (no change):', sorted(int(g) for g, a in aligns.items() if a['mode'] != 'aligned'))
