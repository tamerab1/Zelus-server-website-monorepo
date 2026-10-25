"""Adds the Zelus custom 117HD materials to the rev 241 client's materials.json at their rev 241 texture ids.

  python apply_hd_materials.py <manifest remap-241.json> <client repo> [--write]

Source: <client>/117hd/zelus-custom-materials.json (rev 231 texture id + template per material, ported from
the old Material enum). Each texture id goes through the manifest's texture map (custom textures 130-215
collided with vanilla 241 and moved +258). 'template' copies every setting of that vanilla material, as the
old enum entries were exact copies of INFERNAL_CAPE / FIRE_CAPE; null is a plain {name, vanillaTextureIndex}
entry like RED_LAVA. Re-runnable: entries with these names are replaced. The edit is textual (top-level
objects are '  {' .. '  }' blocks; upstream hand-formats some arrays inline), so the rest of the file
stays byte-identical; the result must parse back to the expected list.
"""
import json, sys

man, client = sys.argv[1], sys.argv[2]
write = '--write' in sys.argv
tex = {int(a): b for a, b in json.load(open(man))['types']['texture']['map'].items()}
src = json.load(open(client + '/117hd/zelus-custom-materials.json'))['materials']
path = client + '/117hd/src/main/resources/rs117/hd/scene/materials.json'
raw = open(path, encoding='utf-8', newline='').read()
mats = json.loads(raw)

NL = '\r\n' if '\r\n' in raw else '\n'
# top-level objects of the array: '  {' ... '  }' or '  },' (the layout 117HD keeps the file in)
blocks, cur, head, tail = [], None, [], []
for ln in raw.split(NL):
    if cur is None and ln == '  {':
        cur = [ln]
    elif cur is not None:
        cur.append(ln)
        if ln in ('  }', '  },'):
            blocks.append(cur)
            cur = None
    elif not blocks:
        head.append(ln)
    else:
        tail.append(ln)
if cur is not None or len(blocks) != len(mats):
    sys.exit('materials.json layout not understood (%d blocks, %d materials)' % (len(blocks), len(mats)))

ours = {m['name'] for m in src}
keep = [i for i, m in enumerate(mats) if m.get('name') not in ours]
mats = [mats[i] for i in keep]
blocks = [blocks[i] for i in keep]
by_name = {m['name']: m for m in mats}
used = {m['vanillaTextureIndex'] for m in mats if 'vanillaTextureIndex' in m}

new = []
for m in src:
    tid = tex.get(m['rev231TextureId'], m['rev231TextureId'])
    if tid in used:
        sys.exit('%s: texture %d already has a material' % (m['name'], tid))
    used.add(tid)
    if m['template']:
        e = dict(by_name[m['template']])
        e['name'] = m['name']
        e['vanillaTextureIndex'] = tid
        e = {k: e[k] for k in ['name', 'vanillaTextureIndex'] + [k for k in e if k not in ('name', 'vanillaTextureIndex')]}
    else:
        e = {'name': m['name'], 'vanillaTextureIndex': tid}
    new.append(e)

last = max(i for i, m in enumerate(mats) if 'vanillaTextureIndex' in m)
new_blocks = [['  ' + ln for ln in json.dumps(e, indent=2).split('\n')] for e in new]
blocks[last + 1:last + 1] = new_blocks
mats[last + 1:last + 1] = new
for i, b in enumerate(blocks):  # commas: every object but the last
    b[-1] = '  }' if i == len(blocks) - 1 else '  },'
out = NL.join(head + [ln for b in blocks for ln in b] + tail)
if json.loads(out) != mats:
    sys.exit('internal error: rewritten file does not parse back to the expected list')

print('%d Zelus materials after %s (texture ids %d-%d)' % (len(new), mats[last]['name'],
      min(e['vanillaTextureIndex'] for e in new), max(e['vanillaTextureIndex'] for e in new)))
if write:
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(out)
    print('written', path)
