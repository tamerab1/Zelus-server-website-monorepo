import re, sys, os, glob
G = r"C:/Users/tamer/OneDrive/Desktop/zsv1/osrs server/client zelus/client rev241/runelite-api/src/main/java/net/runelite/api/gameval"
SRC = r"C:/Users/tamer/OneDrive/Desktop/zsv1/osrs server/server zelus rev241/kronos-server/src/main/java/io/ruin"
CATS = {"AnimationID": "anim", "SpotanimID": "spot", "NpcID": "npc", "ObjectID": "loc", "ObjectID1": "loc", "ItemID": "obj", "VarbitID": "varbit", "VarPlayerID": "varp"}
gv = []  # (cat, name, id)
byid = {}
for f, cat in CATS.items():
    txt = open(os.path.join(G, f + ".java"), encoding="utf8").read()
    # top-level only for ItemID (skip nested Cert/Placeholder classes)
    depth = 0
    for line in txt.splitlines():
        depth += line.count("{") - line.count("}")
        m = re.search(r"public static final int ([A-Z0-9_]+) = (\d+);", line)
        if m and depth <= 1:
            e = (cat, m.group(1), int(m.group(2)))
            gv.append(e)
            byid.setdefault((cat, e[2]), []).append(e[1])
boss, prefix, files = sys.argv[1], sys.argv[2], sys.argv[3:]
pre = re.compile(prefix)
mine = [e for e in gv if pre.search(e[1])]
code = ""
for pat in files:
    for p in glob.glob(os.path.join(SRC, pat), recursive=True):
        code += open(p, encoding="utf8").read()
nums = set(int(n) for n in re.findall(r"(?<![\w.])(\d{3,6})(?![\w.])", code))
print(f"##### {boss}: {len(mine)} cache ids match /{prefix}/, code uses {len(nums)} numbers")
print("--- cache ids of this boss NOT used in code:")
for c, n, i in sorted(mine):
    if i not in nums:
        print(f"  {c:6} {i:6} {n}")
mineids = {(c, i) for c, n, i in mine}
print("--- used in code AND a boss-cache id:")
for c, n, i in sorted(mine):
    if i in nums:
        print(f"  {c:6} {i:6} {n}")
