#!/usr/bin/env python3
"""
Generates the collection-log-luck client plugin's drop_rates.json resource
directly from this server's live drop-rate data:
  - npc_names.tsv (id\\tname)          -> npc id -> boss name (dumped from the
    game cache via DumpAllNpcNames.java -- npc display names live only in the
    binary cache, not in any JSON data file, so that tool must be run first)
  - data/npcs/drops/newDrops/<id>.json -> item drop rates for that npc

Output: itemId -> { bossName -> {dropRate, minAmount, maxAmount} }
(an item can drop from multiple bosses at different rates, so keying by
itemId alone isn't enough -- the client picks the right entry using the
boss name of whichever collection log page is currently open.)

Re-run this whenever a boss's drop table changes:
  1. java -cp "out_reencode;cp_with_jna.txt-contents" DumpAllNpcNames <cachePath> npc_names.tsv
  2. python3 gen_drop_rates.py <output path> [path to npc_names.tsv]
"""
import json
import os
import sys

DATA_ROOT = r"C:\Users\tamer\OneDrive\Desktop\zsv1\osrs server\data zelus\data"
DROPS_DIR = os.path.join(DATA_ROOT, "npcs", "drops", "newDrops")
COMBAT_DIR = os.path.join(DATA_ROOT, "npcs", "combat")
PET_JAVA_PATH = r"C:\Users\tamer\OneDrive\Desktop\zsv1\osrs server\server zelus\kronos-server\src\main\java\io\ruin\model\item\actions\impl\pet\Pet.java"
OUT_PATH = sys.argv[1] if len(sys.argv) > 1 else "drop_rates.json"
NPC_NAMES_PATH = sys.argv[2] if len(sys.argv) > 2 else "npc_names.tsv"


def strip_comments(text):
    out = []
    in_string = False
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if in_string:
            out.append(c)
            if c == '\\' and i + 1 < n:
                out.append(text[i + 1])
                i += 2
                continue
            if c == '"':
                in_string = False
            i += 1
            continue
        if c == '"':
            in_string = True
            out.append(c)
            i += 1
            continue
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            while i < n and text[i] != '\n':
                i += 1
            continue
        if c == '#':
            while i < n and text[i] != '\n':
                i += 1
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def load_jsonc(path):
    with open(path, 'r', encoding='utf-8') as f:
        raw = f.read()
    cleaned = strip_comments(raw)
    # tolerate trailing commas before ] or }
    import re
    cleaned = re.sub(r',\s*([\]}])', r'\1', cleaned)
    return json.loads(cleaned)


def split_top_level_args(arg_str):
    args = []
    depth = 0
    current = []
    for c in arg_str:
        if c == '(' :
            depth += 1
            current.append(c)
        elif c == ')':
            depth -= 1
            current.append(c)
        elif c == ',' and depth == 0:
            args.append(''.join(current).strip())
            current = []
        else:
            current.append(c)
    if current:
        args.append(''.join(current).strip())
    return args


def parse_pets(path):
    """Returns dict: PET_ENUM_NAME -> (itemId, dropAverage) for pets with a real numeric
    drop rate (the (itemId, npcId, mysteryBox, dropAverage) constructor overload only)."""
    import re
    with open(path, 'r', encoding='utf-8') as f:
        raw = f.read()
    cleaned = strip_comments(raw)

    pets = {}
    pattern = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\(([^;]*?)\)\s*[,;]', re.MULTILINE)
    for m in pattern.finditer(cleaned):
        name = m.group(1)
        args = split_top_level_args(m.group(2))
        if len(args) == 4 and args[2] in ('true', 'false'):
            try:
                item_id = int(args[0])
                drop_average = int(args[3])
            except ValueError:
                continue
            if drop_average > 0:
                pets[name] = (item_id, drop_average)
    return pets


def find_pet_field_assignments():
    """Returns list of (npc_id, pet_enum_name) for every npc combat entry with a non-empty
    "pet" field."""
    import re
    assignments = []
    for fname in os.listdir(COMBAT_DIR):
        if not fname.endswith(".json"):
            continue
        path = os.path.join(COMBAT_DIR, fname)
        try:
            data = load_jsonc(path)
        except Exception:
            continue
        if not isinstance(data, list):
            continue
        for entry in data:
            pet_name = entry.get("pet")
            ids = entry.get("ids")
            if not pet_name or not ids:
                continue
            for npc_id in ids:
                assignments.append((npc_id, pet_name))
    return assignments


def main():
    # npc id -> boss name, from the cache dump (real display names aren't in any JSON file)
    npc_names = {}
    with open(NPC_NAMES_PATH, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line:
                continue
            npc_id_str, name = line.split('\t', 1)
            npc_names[int(npc_id_str)] = name

    registry = {}  # itemId(str) -> bossName -> {dropRate,minAmount,maxAmount}
    bosses_included = set()
    skipped_drops = 0
    for fname in os.listdir(DROPS_DIR):
        if not fname.endswith(".json"):
            continue
        npc_id_str = fname[:-5]
        try:
            npc_id = int(npc_id_str)
        except ValueError:
            continue
        boss_name = npc_names.get(npc_id)
        if not boss_name:
            continue
        path = os.path.join(DROPS_DIR, fname)
        try:
            drops = load_jsonc(path)
        except Exception:
            skipped_drops += 1
            continue
        if not isinstance(drops, list):
            continue
        for drop in drops:
            item_id = drop.get("itemid")
            drop_rate = drop.get("dropRate")
            if item_id is None or not drop_rate:
                continue
            entry = registry.setdefault(str(item_id), {})
            # if the same item/boss pair appears in multiple npc ids sharing
            # the same boss name (e.g. multiple combat forms), keep the rarest
            # (highest dropRate) entry -- conservative for luck calculations.
            existing = entry.get(boss_name)
            if existing is None or drop_rate > existing["dropRate"]:
                entry[boss_name] = {
                    "dropRate": drop_rate,
                    "minAmount": drop.get("minAmount", 1),
                    "maxAmount": drop.get("maxAmount", 1),
                }
            bosses_included.add(boss_name)

    # Pets are NOT part of newDrops -- they're a separate (itemId, npcId, mysteryBox, dropAverage)
    # entry in Pet.java, referenced from a boss's combat json via a "pet": "ENUM_NAME" field. Cross
    # -reference those three sources to add pet entries the drop-table pass above can't see at all.
    pets_by_name = parse_pets(PET_JAVA_PATH)
    pet_entries_added = 0
    for npc_id, pet_enum_name in find_pet_field_assignments():
        boss_name = npc_names.get(npc_id)
        pet_info = pets_by_name.get(pet_enum_name)
        if not boss_name or not pet_info:
            continue
        item_id, drop_average = pet_info
        entry = registry.setdefault(str(item_id), {})
        if boss_name not in entry:
            entry[boss_name] = {
                "dropRate": drop_average,
                "minAmount": 1,
                "maxAmount": 1,
            }
            bosses_included.add(boss_name)
            pet_entries_added += 1

    with open(OUT_PATH, 'w', encoding='utf-8') as f:
        json.dump(registry, f, indent=2, sort_keys=True)

    print(f"Wrote {len(registry)} items across {len(bosses_included)} bosses to {OUT_PATH}")
    print(f"(skipped {skipped_drops} unparsable drop files, added {pet_entries_added} pet entries)")


if __name__ == "__main__":
    main()
