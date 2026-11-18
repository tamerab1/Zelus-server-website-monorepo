import re,os,glob,json,sys
G=r"C:/Users/tamer/OneDrive/Desktop/zsv1/osrs server/client zelus/client rev241/runelite-api/src/main/java/net/runelite/api/gameval"
C=r"C:/Users/tamer/OneDrive/Desktop/zsv1/osrs server/data zelus rev241/data/npcs/combat"
def load(f):
    d={}
    for m in re.finditer(r"public static final int ([A-Z0-9_]+) = (\d+);",open(os.path.join(G,f),encoding="utf8").read()): d[int(m.group(2))]=m.group(1)
    return d
npcn=load("NpcID.java"); anim=load("AnimationID.java")
# npc defs: readyanim/walkanim names
defs={};cur=None
for line in open(r"C:/Users/tamer/AppData/Local/Temp/claude/b45af14b2b7e04d4/414f7b6c-6d61-4ee0-b7b0-d21df985b1a2/scratchpad/ext/dumps/dump.npc",encoding="utf8"):
    line=line.rstrip("\n"); m=re.match(r"// (\d+)$",line)
    if m: cur=int(m.group(1)); defs[cur]={}; continue
    if cur is None: continue
    for k in ("readyanim","walkanim","name","vislevel"):
        if line.startswith(k+"="): defs[cur][k]=line[len(k)+1:]
animname_to_id={v.lower():k for k,v in anim.items()}
def stem(n):  # family prefix from an anim name like FIRE_GIANT_BRANDA_QUEEN_IDLE01
    n=n.upper()
    for suf in ("_IDLE","_READY","_WALK","_STAND"):
        i=n.find(suf)
        if i>0: return n[:i]
    return None
rows=[]
for f in sorted(glob.glob(C+"/*.json")):
    txt=re.sub(r"#[^\n]*","",open(f,encoding="utf8").read())
    try: data=json.loads(txt)
    except Exception: continue
    for e in (data if isinstance(data,list) else [data]):
        ids=e.get("ids") or []
        if not ids: continue
        npc=ids[0]; d=defs.get(npc,{})
        try: lvl=int(d.get("vislevel","0"))
        except: lvl=0
        ra=d.get("readyanim","")
        st=stem(ra) if ra else None
        fam={k:v for k,v in anim.items() if st and v.startswith(st)}
        issues=[]
        for key,word in (("attack_animation","ATTACK"),("defend_animation","DEF"),("death_animation","DEATH")):
            v=e.get(key,-1)
            if v in (-1,None):
                if any(word in n for n in fam.values()): issues.append(f"{key}=-1 but cache has {[ (k,n) for k,n in fam.items() if word in n][:3]}")
            elif fam and v not in fam:
                issues.append(f"{key}={v}({anim.get(v,'?')}) not in family {st}")
        if issues and (lvl>=100 or "boss" in os.path.basename(f).lower()):
            rows.append((os.path.basename(f), npc, d.get("name","?"), lvl, issues))
for r in rows:
    print(f"{r[0]} npc {r[1]} {r[2]} (lvl {r[3]})"); [print("    ",i) for i in r[4]]
print(len(rows),"entries with issues", file=sys.stderr)
