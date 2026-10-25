"""Moves the custom item ids hard-coded in the client's Zelus plugins to their rev 241 ids.

  python remap_client_items.py <manifest remap-241.json> <client repo> [--write]

Reviewed 2026-10-25: across every client file Zelus changed since the fork base (10b6d68d3), the only
literals equal to a moving id that ARE ids are custom items (31132-34731, +10800) in the five plugin files
below; the other hits are colours, sizes and coordinates. Items 59xxx/60xxx don't move (rev 241 items end
at 34731). Only code is rewritten; in comments the custom texture ids the plugins quote follow the texture
map. Not re-runnable: after a run the old ids are gone (a second run finds nothing to move).
"""
import json, re, sys

man, client = sys.argv[1], sys.argv[2]
write = '--write' in sys.argv
types = json.load(open(man))['types']
items = {int(a): b for a, b in types['item']['map'].items() if int(a) != int(b)}
tex = {int(a): b for a, b in types['texture']['map'].items() if int(a) != int(b)}
P = client + '/runelite-client/src/main/java/net/runelite/client/plugins/'
FILES = ['zelusanimatedicons/AnimatedItemIconsPlugin.java', 'zelusweaponsparkles/WeaponSparklesOverlay.java',
         'zelussoulweapon/SoulWeaponPlugin.java', 'zeluspettimer/CombatPetTimerPlugin.java']
LIT = re.compile(r'(?<![\w.])(\d{5})(?![\w.])')
TEXREF = re.compile(r'(texture )(\d{3})\b')

total = 0
for f in FILES:
    path = P + f
    text = open(path, encoding='utf-8', newline='').read()
    out, n = [], 0
    for line in text.split('\n'):
        cut = line.find('//')
        code, comment = (line, '') if cut < 0 else (line[:cut], line[cut:])
        # string literals in these files hold no ids
        new_code = LIT.sub(lambda m: str(items.get(int(m.group(1)), int(m.group(1)))), code)
        n += sum(1 for a, b in zip(LIT.findall(code), LIT.findall(new_code)) if a != b)
        comment = TEXREF.sub(lambda m: m.group(1) + str(tex.get(int(m.group(2)), int(m.group(2)))), comment)
        out.append(new_code + comment)
    total += n
    print('%-55s %d item ids' % (f, n))
    if write:
        open(path, 'w', encoding='utf-8', newline='').write('\n'.join(out))
print('total', total)
