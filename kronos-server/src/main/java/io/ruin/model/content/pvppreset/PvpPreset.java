package io.ruin.model.content.pvppreset;

import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.magic.SpellBook;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-defined PvP preset loadouts.
 *
 * Two activation paths (both enforced in {@link PvpPresetManager}):
 *   Bank-load (free) — all required items found in bank/inventory.
 *   Rental (paid)    — missing items; GP deducted, phantom gear spawned.
 */
public enum PvpPreset {

    // -----------------------------------------------------------------------
    // Zerker  (75 Att / 99 Str / 45 Def / 52 Prayer)
    // -----------------------------------------------------------------------
    ZERKER("Zerker", "75 Att / 99 Str / 45 Def — Whip + Fighter torso.", 400_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(3751));       // Berserker helm
            eq.put(Equipment.SLOT_CAPE,   new Item(6568));       // Obsidian cape
            eq.put(Equipment.SLOT_AMULET, new Item(6585));       // Amulet of fury
            eq.put(Equipment.SLOT_WEAPON, new Item(4151));       // Abyssal whip
            eq.put(Equipment.SLOT_CHEST,  new Item(10551));      // Fighter torso
            eq.put(Equipment.SLOT_SHIELD, new Item(8844));       // Dragon defender
            eq.put(Equipment.SLOT_LEGS,   new Item(11632));      // Obsidian platelegs
            eq.put(Equipment.SLOT_HANDS,  new Item(22981));      // Ferocious gloves
            eq.put(Equipment.SLOT_FEET,   new Item(11840));      // Dragon boots
            eq.put(Equipment.SLOT_RING,   new Item(11773));      // Berserker ring (i)
            eq.put(Equipment.SLOT_AMMO,   new Item(892, 1));     // Rune arrow x1
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(12695, 2),  // Super combat potion(4) x2
                new Item(6685,  8),  // Saradomin brew(4) x8
                new Item(3024,  6),  // Super restore(4) x6
                new Item(385,   4),  // Shark x4
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 75; l[STRENGTH] = 99; l[DEFENCE]  = 45;
            l[HITPOINTS] = 99; l[RANGED]   = 1;  l[PRAYER]   = 52;
            l[MAGIC]     = 1;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.MODERN; }
    },

    // -----------------------------------------------------------------------
    // Pure Melee  (60 Att / 99 Str / 1 Def / 52 Prayer)
    // -----------------------------------------------------------------------
    PURE_MELEE("Pure Melee", "60 Att / 99 Str / 1 Def — Obsidian + Scimitar.", 350_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(21298));      // Obsidian helm
            eq.put(Equipment.SLOT_CAPE,   new Item(6568));       // Obsidian cape
            eq.put(Equipment.SLOT_AMULET, new Item(1729));       // Amulet of strength
            eq.put(Equipment.SLOT_WEAPON, new Item(4587));       // Dragon scimitar
            eq.put(Equipment.SLOT_CHEST,  new Item(10551));      // Fighter torso
            eq.put(Equipment.SLOT_SHIELD, new Item(8844));       // Dragon defender
            eq.put(Equipment.SLOT_LEGS,   new Item(11632));      // Obsidian platelegs
            eq.put(Equipment.SLOT_HANDS,  new Item(22981));      // Ferocious gloves
            eq.put(Equipment.SLOT_FEET,   new Item(11840));      // Dragon boots
            eq.put(Equipment.SLOT_RING,   new Item(11773));      // Berserker ring (i)
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(2440,  3),  // Super strength(4) x3
                new Item(2436,  2),  // Super attack(4) x2
                new Item(6685,  6),  // Saradomin brew(4) x6
                new Item(3024,  4),  // Super restore(4) x4
                new Item(385,   5),  // Shark x5
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 60; l[STRENGTH] = 99; l[DEFENCE]  = 1;
            l[HITPOINTS] = 99; l[RANGED]   = 1;  l[PRAYER]   = 52;
            l[MAGIC]     = 1;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.MODERN; }
    },

    // -----------------------------------------------------------------------
    // Pure Melee & Range  (60 Att / 99 Str / 1 Def / 99 Range / 52 Prayer)
    // -----------------------------------------------------------------------
    PURE_MELEE_RANGE("Pure M&R", "60 Att / 99 Str / 1 Def / 99 Range — MSB(i) + Scimitar switch.", 450_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(6602));       // Robin hood hat
            eq.put(Equipment.SLOT_CAPE,   new Item(22109));      // Ava's assembler
            eq.put(Equipment.SLOT_AMULET, new Item(6585));       // Amulet of fury
            eq.put(Equipment.SLOT_WEAPON, new Item(6724));       // Magic shortbow (i)
            eq.put(Equipment.SLOT_CHEST,  new Item(10551));      // Fighter torso
            eq.put(Equipment.SLOT_SHIELD, new Item(8844));       // Dragon defender
            eq.put(Equipment.SLOT_LEGS,   new Item(11632));      // Obsidian platelegs
            eq.put(Equipment.SLOT_HANDS,  new Item(22981));      // Ferocious gloves
            eq.put(Equipment.SLOT_FEET,   new Item(2577));       // Ranger boots
            eq.put(Equipment.SLOT_RING,   new Item(11771));      // Archers ring (i)
            eq.put(Equipment.SLOT_AMMO,   new Item(892, 200));   // Rune arrow x200
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(4587),      // Dragon scimitar (melee switch)
                new Item(11773),     // Berserker ring (i) (ring switch)
                new Item(2444,  2),  // Ranging potion(4) x2
                new Item(2440,  2),  // Super strength(4) x2
                new Item(2436,  1),  // Super attack(4) x1
                new Item(6685,  6),  // Saradomin brew(4) x6
                new Item(3024,  4),  // Super restore(4) x4
                new Item(385,   5),  // Shark x5
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 60; l[STRENGTH] = 99; l[DEFENCE]  = 1;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 52;
            l[MAGIC]     = 1;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.MODERN; }
    },

    // -----------------------------------------------------------------------
    // Pure NH  (60 Att / 99 Str / 1 Def / 94 Mage / 99 Range / 52 Prayer)
    // -----------------------------------------------------------------------
    PURE_NH("Pure NH", "60 Att / 99 Str / 1 Def — Ahrim's + Toxic Staff switch.", 500_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(4708));       // Ahrim's hood
            eq.put(Equipment.SLOT_CAPE,   new Item(21791));      // Imbued sara cape
            eq.put(Equipment.SLOT_AMULET, new Item(12002));      // Occult necklace
            eq.put(Equipment.SLOT_WEAPON, new Item(12904));      // Toxic staff of the dead
            eq.put(Equipment.SLOT_CHEST,  new Item(4712));       // Ahrim's robe top
            eq.put(Equipment.SLOT_SHIELD, new Item(6889));       // Mage's book
            eq.put(Equipment.SLOT_LEGS,   new Item(4714));       // Ahrim's robe skirt
            eq.put(Equipment.SLOT_HANDS,  new Item(19547));      // Tormented bracelet
            eq.put(Equipment.SLOT_FEET,   new Item(6920));       // Infinity boots
            eq.put(Equipment.SLOT_RING,   new Item(11770));      // Seers ring (i)
            eq.put(Equipment.SLOT_AMMO,   new Item(560, 500));   // Death rune x500
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(6685,  8),   // Saradomin brew(4) x8
                new Item(3024,  6),   // Super restore(4) x6
                new Item(2444,  2),   // Ranging potion(4) x2
                new Item(4675),       // Ancient staff
                new Item(9185),       // Rune crossbow
                new Item(21944, 100), // Ruby dragon bolts (e) x100
                new Item(21946, 60),  // Diamond dragon bolts (e) x60
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 60; l[STRENGTH] = 99; l[DEFENCE]  = 1;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 52;
            l[MAGIC]     = 94;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.ANCIENT; }
    },

    // -----------------------------------------------------------------------
    // Main  (99 all — Bandos + Elder Maul)
    // -----------------------------------------------------------------------
    MAIN("Main", "99 all — Bandos + Elder Maul + Infernal.", 750_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(26470));      // Neitiznot faceguard
            eq.put(Equipment.SLOT_CAPE,   new Item(21295));      // Infernal cape
            eq.put(Equipment.SLOT_AMULET, new Item(19553));      // Amulet of torture
            eq.put(Equipment.SLOT_WEAPON, new Item(21003));      // Elder maul
            eq.put(Equipment.SLOT_CHEST,  new Item(11832));      // Bandos chestplate
            eq.put(Equipment.SLOT_SHIELD, new Item(8844));       // Dragon defender
            eq.put(Equipment.SLOT_LEGS,   new Item(11834));      // Bandos tassets
            eq.put(Equipment.SLOT_HANDS,  new Item(22981));      // Ferocious gloves
            eq.put(Equipment.SLOT_FEET,   new Item(13239));      // Primordial boots
            eq.put(Equipment.SLOT_RING,   new Item(11773));      // Berserker ring (i)
            eq.put(Equipment.SLOT_AMMO,   new Item(892, 1));     // Rune arrow x1
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(12695, 2),  // Super combat potion(4) x2
                new Item(6685,  8),  // Saradomin brew(4) x8
                new Item(3024,  6),  // Super restore(4) x6
                new Item(385,   4),  // Shark x4
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 99; l[STRENGTH] = 99; l[DEFENCE]  = 99;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 99;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.MODERN; }
    },

    // -----------------------------------------------------------------------
    // 126 Max Melee  (99 all — Rune armor + D-Scim, Lunar)
    // -----------------------------------------------------------------------
    MAX_MELEE("126 Max Melee", "99 all — Rune armor + D-Scim. Starter welfare build.", 200_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(10828));      // Helm of Neitiznot
            eq.put(Equipment.SLOT_CAPE,   new Item(1052));       // Cape of legends
            eq.put(Equipment.SLOT_AMULET, new Item(1712));       // Amulet of glory(4)
            eq.put(Equipment.SLOT_WEAPON, new Item(4587));       // Dragon scimitar
            eq.put(Equipment.SLOT_CHEST,  new Item(1127));       // Rune platebody
            eq.put(Equipment.SLOT_SHIELD, new Item(8850));       // Rune defender
            eq.put(Equipment.SLOT_LEGS,   new Item(1079));       // Rune platelegs
            eq.put(Equipment.SLOT_HANDS,  new Item(7462));       // Barrows gloves
            eq.put(Equipment.SLOT_FEET,   new Item(4131));       // Rune boots
            eq.put(Equipment.SLOT_RING,   new Item(2550));       // Ring of recoil
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(10925, 2),  // Sanfew serum(4) x2
                new Item(6685,  2),  // Saradomin brew(4) x2
                new Item(2440,  1),  // Super strength(4) x1
                new Item(2436,  1),  // Super attack(4) x1
                new Item(385,   12), // Shark x12
                new Item(1215,  1),  // Dragon dagger
            };
        }

        @Override
        public Item[] buildRunePouch() {
            return new Item[]{
                new Item(560, 250),  // Death rune x250
                new Item(9075, 250), // Astral rune x250
                new Item(557, 500),  // Earth rune x500
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 99; l[STRENGTH] = 99; l[DEFENCE]  = 99;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 99;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.LUNAR; }
    },

    // -----------------------------------------------------------------------
    // 126 Tribrid  (99 all — Mystic + Spirit Shield + Ancient, Ancient)
    // -----------------------------------------------------------------------
    TRIBRID("126 Tribrid", "99 all — Mystic + Spirit Shield + Ancient Staff.", 350_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(10828));           // Helm of Neitiznot
            eq.put(Equipment.SLOT_CAPE,   new Item(2412));            // Saradomin cape
            eq.put(Equipment.SLOT_AMULET, new Item(1712));            // Amulet of glory(4)
            eq.put(Equipment.SLOT_WEAPON, new Item(4675));            // Ancient staff
            eq.put(Equipment.SLOT_CHEST,  new Item(4091));            // Mystic robe top
            eq.put(Equipment.SLOT_SHIELD, new Item(12829));           // Spirit shield
            eq.put(Equipment.SLOT_LEGS,   new Item(4093));            // Mystic robe bottom
            eq.put(Equipment.SLOT_HANDS,  new Item(7462));            // Barrows gloves
            eq.put(Equipment.SLOT_FEET,   new Item(3105));            // Climbing boots
            eq.put(Equipment.SLOT_RING,   new Item(2550));            // Ring of recoil
            eq.put(Equipment.SLOT_AMMO,   new Item(9244, 100));       // Dragonstone bolts (e) x100
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(10925, 4),  // Sanfew serum(4) x4
                new Item(6685,  8),  // Saradomin brew(4) x8
                new Item(9185,  1),  // Rune crossbow
                new Item(4587,  1),  // Dragon scimitar
                new Item(2503,  1),  // Black d-hide body
                new Item(8850,  1),  // Rune defender
                new Item(1079,  1),  // Rune platelegs
                new Item(1231,  1),  // Dragon dagger(p)
                new Item(3040,  1),  // Magic potion(4)
                new Item(2440,  1),  // Super strength(4)
                new Item(2436,  1),  // Super attack(4)
                new Item(385,   3),  // Shark x3
            };
        }

        @Override
        public Item[] buildRunePouch() {
            return new Item[]{
                new Item(565, 1000), // Blood rune x1000
                new Item(555, 1000), // Water rune x1000
                new Item(560, 1000), // Death rune x1000
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 99; l[STRENGTH] = 99; l[DEFENCE]  = 99;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 99;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.ANCIENT; }
    },

    // -----------------------------------------------------------------------
    // Pure Melee Welfare  (75 Att / 99 Str / 1 Def / 52 Prayer — welfare gear)
    // -----------------------------------------------------------------------
    DMM_PURE_MELEE("Pure Melee (W)", "75 Att / 99 Str / 1 Def — Bearhead + D-Scim welfare.", 150_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(4502));       // Bearhead
            eq.put(Equipment.SLOT_CAPE,   new Item(1052));       // Cape of legends
            eq.put(Equipment.SLOT_AMULET, new Item(1712));       // Amulet of glory(4)
            eq.put(Equipment.SLOT_WEAPON, new Item(4587));       // Dragon scimitar
            eq.put(Equipment.SLOT_CHEST,  new Item(544));        // Monk's robe top
            eq.put(Equipment.SLOT_SHIELD, new Item(12610));      // Book of law
            eq.put(Equipment.SLOT_LEGS,   new Item(2497));       // Black d-hide chaps
            eq.put(Equipment.SLOT_HANDS,  new Item(7458));       // Mithril gloves
            eq.put(Equipment.SLOT_FEET,   new Item(3105));       // Climbing boots
            eq.put(Equipment.SLOT_RING,   new Item(2550));       // Ring of recoil
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(6685,  2),  // Saradomin brew(4) x2
                new Item(10925, 2),  // Sanfew serum(4) x2
                new Item(2440,  1),  // Super strength(4) x1
                new Item(2436,  1),  // Super attack(4) x1
                new Item(385,   10), // Shark x10
                new Item(1215,  1),  // Dragon dagger
                new Item(7158,  1),  // Dragon 2h sword
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 75; l[STRENGTH] = 99; l[DEFENCE]  = 1;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 52;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.MODERN; }
    },

    // -----------------------------------------------------------------------
    // Pure NH Welfare  (75 Att / 99 Str / 1 Def — Ghostly + Ancient Staff)
    // -----------------------------------------------------------------------
    DMM_PURE_NH("Pure NH (W)", "75 Att / 99 Str / 1 Def — Ghostly robes + Ancient Staff.", 200_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(6109));            // Ghostly hood
            eq.put(Equipment.SLOT_CAPE,   new Item(2412));            // Saradomin cape
            eq.put(Equipment.SLOT_AMULET, new Item(1712));            // Amulet of glory(4)
            eq.put(Equipment.SLOT_WEAPON, new Item(4675));            // Ancient staff
            eq.put(Equipment.SLOT_CHEST,  new Item(6107));            // Ghostly robe top
            eq.put(Equipment.SLOT_SHIELD, new Item(12610));           // Book of law
            eq.put(Equipment.SLOT_LEGS,   new Item(6108));            // Ghostly robe bottom
            eq.put(Equipment.SLOT_HANDS,  new Item(7458));            // Mithril gloves
            eq.put(Equipment.SLOT_FEET,   new Item(3105));            // Climbing boots
            eq.put(Equipment.SLOT_RING,   new Item(2550));            // Ring of recoil
            eq.put(Equipment.SLOT_AMMO,   new Item(9341, 2000));      // Dragonstone bolts x2000
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(10925, 4),  // Sanfew serum(4) x4
                new Item(6685,  8),  // Saradomin brew(4) x8
                new Item(10499, 1),  // Ava's accumulator
                new Item(2497,  1),  // Black d-hide chaps
                new Item(2444,  1),  // Ranging potion(4)
                new Item(2440,  1),  // Super strength(4)
                new Item(2436,  1),  // Super attack(4)
                new Item(1215,  1),  // Dragon dagger
                new Item(9185,  1),  // Rune crossbow
                new Item(4587,  1),  // Dragon scimitar
                new Item(385,   2),  // Shark x2
            };
        }

        @Override
        public Item[] buildRunePouch() {
            return new Item[]{
                new Item(565, 2000), // Blood rune x2000
                new Item(555, 2000), // Water rune x2000
                new Item(560, 2000), // Death rune x2000
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 75; l[STRENGTH] = 99; l[DEFENCE]  = 1;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 52;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.ANCIENT; }
    },

    // -----------------------------------------------------------------------
    // Zerk Melee  (75 Att / 99 Str / 45 Def / 52 Prayer — Fighter Torso + Rune)
    // -----------------------------------------------------------------------
    ZERK_MELEE("Zerk Melee", "75 Att / 99 Str / 45 Def — Fighter Torso + Rune Defender.", 250_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(3751));       // Berserker helm
            eq.put(Equipment.SLOT_CAPE,   new Item(6570));       // Fire cape
            eq.put(Equipment.SLOT_AMULET, new Item(1712));       // Amulet of glory(4)
            eq.put(Equipment.SLOT_WEAPON, new Item(4587));       // Dragon scimitar
            eq.put(Equipment.SLOT_CHEST,  new Item(10551));      // Fighter torso
            eq.put(Equipment.SLOT_SHIELD, new Item(8850));       // Rune defender
            eq.put(Equipment.SLOT_LEGS,   new Item(1079));       // Rune platelegs
            eq.put(Equipment.SLOT_HANDS,  new Item(7462));       // Barrows gloves
            eq.put(Equipment.SLOT_FEET,   new Item(3105));       // Climbing boots
            eq.put(Equipment.SLOT_RING,   new Item(6737));       // Berserker ring
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(10925, 2),  // Sanfew serum(4) x2
                new Item(6685,  3),  // Saradomin brew(4) x3
                new Item(2440,  1),  // Super strength(4) x1
                new Item(2436,  1),  // Super attack(4) x1
                new Item(385,   14), // Shark x14
                new Item(1215,  1),  // Dragon dagger
            };
        }

        @Override
        public Item[] buildRunePouch() {
            return new Item[]{
                new Item(9075, 2000), // Astral rune x2000
                new Item(557,  2000), // Earth rune x2000
                new Item(560,  2000), // Death rune x2000
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 75; l[STRENGTH] = 99; l[DEFENCE]  = 45;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 52;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.LUNAR; }
    },

    // -----------------------------------------------------------------------
    // Zerk Tribrid  (75 Att / 99 Str / 45 Def / 52 Prayer — Mystic + Spirit Shield)
    // -----------------------------------------------------------------------
    ZERK_TRIBRID("Zerk Tribrid", "75 Att / 99 Str / 45 Def — Mystic + Spirit Shield + Ancient.", 300_000) {
        @Override
        public Map<Integer, Item> buildEquipment() {
            Map<Integer, Item> eq = new HashMap<>();
            eq.put(Equipment.SLOT_HAT,    new Item(3751));            // Berserker helm
            eq.put(Equipment.SLOT_CAPE,   new Item(2412));            // Saradomin cape
            eq.put(Equipment.SLOT_AMULET, new Item(1712));            // Amulet of glory(4)
            eq.put(Equipment.SLOT_WEAPON, new Item(4675));            // Ancient staff
            eq.put(Equipment.SLOT_CHEST,  new Item(4091));            // Mystic robe top
            eq.put(Equipment.SLOT_SHIELD, new Item(12829));           // Spirit shield
            eq.put(Equipment.SLOT_LEGS,   new Item(4093));            // Mystic robe bottom
            eq.put(Equipment.SLOT_HANDS,  new Item(7462));            // Barrows gloves
            eq.put(Equipment.SLOT_FEET,   new Item(3105));            // Climbing boots
            eq.put(Equipment.SLOT_RING,   new Item(6737));            // Berserker ring
            eq.put(Equipment.SLOT_AMMO,   new Item(9341, 1000));      // Dragonstone bolts x1000
            return eq;
        }

        @Override
        public Item[] buildInventory() {
            return new Item[]{
                new Item(6685,  1),  // Saradomin brew(4) x1
                new Item(3024,  2),  // Super restore(4) x2
                new Item(2440,  1),  // Super strength(4) x1
                new Item(2436,  1),  // Super attack(4) x1
                new Item(2503,  1),  // Black d-hide body
                new Item(2497,  1),  // Black d-hide chaps
                new Item(9185,  1),  // Rune crossbow
                new Item(10551, 1),  // Fighter torso
                new Item(1079,  1),  // Rune platelegs
                new Item(4587,  1),  // Dragon scimitar
                new Item(1215,  1),  // Dragon dagger
                new Item(385,   10), // Shark x10
                new Item(8013,  1),  // Teleport to house
            };
        }

        @Override
        public Item[] buildRunePouch() {
            return new Item[]{
                new Item(565, 1000), // Blood rune x1000
                new Item(554, 2000), // Fire rune x2000
                new Item(560, 2000), // Death rune x2000
            };
        }

        @Override
        public int[] buildCombatLevels() {
            int[] l = new int[7];
            l[ATTACK]    = 75; l[STRENGTH] = 99; l[DEFENCE]  = 45;
            l[HITPOINTS] = 99; l[RANGED]   = 99; l[PRAYER]   = 52;
            l[MAGIC]     = 99;
            return l;
        }

        @Override
        public SpellBook defaultSpellbook() { return SpellBook.ANCIENT; }
    };

    // -----------------------------------------------------------------------
    // StatType indices (matches io.ruin StatType enum order)
    // -----------------------------------------------------------------------
    public static final int ATTACK = 0, DEFENCE = 1, STRENGTH = 2,
                            HITPOINTS = 3, RANGED = 4, PRAYER = 5, MAGIC = 6;

    // -----------------------------------------------------------------------
    // Fields & constructor
    // -----------------------------------------------------------------------

    private final String displayName;
    private final String description;
    private final int    rentalCost;

    PvpPreset(String displayName, String description, int rentalCost) {
        this.displayName = displayName;
        this.description = description;
        this.rentalCost  = rentalCost;
    }

    public String   getDisplayName() { return displayName; }
    public String   getDescription() { return description; }
    public int      getRentalCost()  { return rentalCost; }

    // -----------------------------------------------------------------------
    // Abstract contract
    // -----------------------------------------------------------------------

    public abstract Map<Integer, Item> buildEquipment();
    public abstract Item[]             buildInventory();
    public abstract int[]              buildCombatLevels();

    /** Spellbook automatically applied on activation. */
    public abstract SpellBook defaultSpellbook();

    /**
     * Returns the rune pouch contents for this preset (up to 3 items), or
     * {@code null} if this preset does not use a rune pouch.
     * Item IDs should be rune IDs; amounts indicate quantity to put in the pouch.
     */
    public Item[] buildRunePouch() { return null; }
}
