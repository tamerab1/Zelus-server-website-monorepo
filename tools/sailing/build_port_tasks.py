"""Build data/sailing/port_tasks.json: every OSRS port task (cache db table 197, port_task) joined with its Sailing XP
from the OSRS wiki tables (data/sailing/port_task_xp_wiki.txt; the cache has no XP column).

usage: python build_port_tasks.py <db dump txt> <data dir>
The db dump is the DumpDB output of the rev 241 cache ("T197 row N: c0[INTEGER]=[..] ..." lines).
Columns (gameval dbtable port_task): 0 task id, 1 name, 2 type (0 courier, 1 bounty), 3 level, 4 board port,
5 cargo port, 6 end port (sailing_dock db rows; 8587 + Port ordinal), 7 cancellable, 17 cargo {obj, qty},
19 bounty npc alive, 20 bounty npc dead, 21 bounty item, 22 bounty amount, 23 bounty item rarity (1/N), 25 flavour text.
"""
import json, re, sys
from collections import defaultdict

PORTS = ["Port Sarim", "The Pandemonium", "Land's End", "Musa Point", "Hosidius", "Rimmington", "Catherby",
	"Port Piscarilius", "Brimhaven", "Ardougne", "Port Khazard", "Witchaven", "Entrana", "Civitas illa Fortis",
	"Corsair Cove", "Cairn Isle", "Sunset Coast", "The Summer Shore", "Aldarin", "Ruins of Unkah",
	"Void Knights' Outpost", "Port Roberts", "Red Rock", "Rellekka", "Etceteria", "Port Tyras", "Deepfin Point",
	"Jatizso", "Neitiznot", "Prifddinas", "Piscatoris", "Lunar Isle"]
IDX = {n: i for i, n in enumerate(PORTS)}
QUEST_XP = {599: 100, 600: 150}  # Pandemonium ship parts, Prying Times pirate looty


def cols(line):
	out = {}
	for m in re.finditer(r"c(\d+)\[([A-Z, ]+)\]=\[(.*?)\](?= c\d+\[|$)", line):
		out[int(m.group(1))] = m.group(3)
	return out


def stem(word):
	word = word.lower()
	for suf in ("ies", "es", "s"):
		if word.endswith(suf) and len(word) > len(suf) + 2:
			return word[: -len(suf)]
	return word


def main(dump, data):
	courier = defaultdict(list)  # (board, cargo, dest, level, qty) -> [(xp, cargo name)]
	bounty = defaultdict(list)  # (board, monster, level, qty) -> xp
	for line in open(data + "/sailing/port_task_xp_wiki.txt", encoding="utf-8"):
		if not line.strip() or line.startswith("#"):
			continue
		p = line.strip().split("|")
		if p[0] == "C":
			courier[(IDX[p[3]], IDX[p[4]], IDX[p[5]], int(p[1]), int(p[7]))].append((int(p[2]) if p[2] else 0, p[6]))
		else:
			bounty[(IDX[p[3]], p[4].lower(), int(p[1]), int(p[6]))].append(int(p[2]))

	tasks, missing = [], []
	for line in open(dump, encoding="cp1252", errors="replace"):
		if not line.startswith("T197 "):
			continue
		c = cols(line.rstrip("\n"))
		t = {"row": int(line.split()[2].rstrip(":")), "id": int(c[0]), "name": c[1], "type": int(c[2]), "level": int(c[3]),
			"board": int(c.get(4, -1)) - 8587 if 4 in c else -1,
			"cargoPort": int(c.get(5, -1)) - 8587 if 5 in c else -1,
			"endPort": int(c[6]) - 8587, "flavour": c.get(25, "")}
		if 7 in c:
			t["cancellable"] = int(c[7])
		if 17 in c:
			obj, qty = [int(x) for x in c[17].split(", ")]
			t["cargoItem"], t["cargoQty"] = obj, qty
		if t["type"] == 1:
			t["npcAlive"], t["npcDead"] = int(c[19]), int(c[20])
			t["item"], t["amount"], t["rarity"] = int(c[21]), int(c[22]), int(c[23])
			monster = re.sub(r"^.*? (\S+(?: \S+)?) bounty$", r"\1", t["name"]).lower()
			xp = None
			for (b, mon, lvl, qty), xs in bounty.items():
				if b == t["board"] and lvl == t["level"] and qty == t["amount"] and t["name"].lower().endswith(mon + " bounty"):
					xp = xs[0]
					break
			t["xp"] = xp
		else:
			key = (t["board"], t["cargoPort"], t["endPort"], t["level"], t.get("cargoQty", 1))
			cands = courier.get(key, [])
			xp = None
			if len({x for x, _ in cands}) == 1:
				xp = cands[0][0]
			elif cands:
				words = {stem(w) for w in re.findall(r"[a-z]+", t["name"].lower())}
				for x, cargo in cands:
					if stem(cargo.split()[-1]) in words:
						xp = x
						break
			t["xp"] = xp
		if t["xp"] is None and t["id"] in QUEST_XP:
			t["xp"] = QUEST_XP[t["id"]]  # wiki "Two special deliveries" (quest-only, never on a board)
		if t["xp"] is None:
			missing.append(f'{t["id"]} {t["name"]} lvl{t["level"]}')
		tasks.append(t)
	tasks.sort(key=lambda t: t["id"])
	json.dump(tasks, open(data + "/sailing/port_tasks.json", "w", encoding="utf-8"), indent=1)
	print(len(tasks), "tasks,", len(missing), "without XP")
	for m in missing:
		print("  no xp:", m)


if __name__ == "__main__":
	main(sys.argv[1], sys.argv[2])
