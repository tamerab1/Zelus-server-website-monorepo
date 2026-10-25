"""Vanilla interface component renumbering 231 -> 241, by Jagex's own component names.

  python component_map.py <InterfaceID.java rev 231> <InterfaceID.java rev 241> <out.json> [ifshift.txt]

RuneLite's generated gameval InterfaceID names every component (Bankmain.ITEMS = 0x000c_000c).
Matching names across the two revisions gives old -> new component ids per group. Output:
{group: {"name": ..., "moved": {old: new}, "removed": {old: name}, "added": n}}. With the
InterfaceShift output, also prints where the byte-signature moves disagree with the names."""
import json, re, sys

CLS = re.compile(r'public static final class (\w+)\s*\{(.*?)\n\t\}', re.S)
CONST = re.compile(r'public static final int (\w+) = 0x([0-9a-f]{4})_([0-9a-f]{4});')
TOP = re.compile(r'^\tpublic static final int (\w+) = (\d+);', re.M)


def load(path):
    text = open(path, encoding='utf-8').read()
    groups = {}
    for cname, body in CLS.findall(text):
        for name, g, c in CONST.findall(body):
            groups.setdefault(int(g, 16), {'class': cname, 'comps': {}})['comps'][name] = int(c, 16)
    return groups


def main():
    old, new = load(sys.argv[1]), load(sys.argv[2])
    out = {}
    for gid, og in sorted(old.items()):
        ng = new.get(gid)
        if not ng:
            out[gid] = {'name': og['class'], 'groupRemoved': True}
            continue
        moved = {og['comps'][n]: ng['comps'][n] for n in og['comps'] if n in ng['comps'] and og['comps'][n] != ng['comps'][n]}
        removed = {og['comps'][n]: n for n in og['comps'] if n not in ng['comps']}
        added = len([n for n in ng['comps'] if n not in og['comps']])
        if moved or removed:
            out[gid] = {'name': og['class'], 'moved': moved, 'removed': removed, 'added': added}
    json.dump(out, open(sys.argv[3], 'w'), indent=1, sort_keys=True)
    print('groups with moved/removed components:', len(out), ' moved comps:', sum(len(v.get('moved', {})) for v in out.values()),
          ' removed:', sum(len(v.get('removed', {})) for v in out.values()))
    if len(sys.argv) > 4:
        agree = disagree = 0
        for line in open(sys.argv[4]):
            m = re.match(r'GROUP (\d+): .*MOVED \d+ \[(.*)\]', line)
            if not m:
                continue
            gid = int(m.group(1))
            names = out.get(gid, {}).get('moved', {})
            for a, b in re.findall(r'(\d+)->(\d+)(?=,|$)', m.group(2)):
                a, b = int(a), int(b)
                if names.get(a, a) == b:
                    agree += 1
                else:
                    disagree += 1
                    print('  disagree group %d comp %d: bytes say %d, names say %s' % (gid, a, b, names.get(a, 'unmoved')))
        print('byte-signature moves vs names: %d agree, %d disagree' % (agree, disagree))


if __name__ == '__main__':
    main()
