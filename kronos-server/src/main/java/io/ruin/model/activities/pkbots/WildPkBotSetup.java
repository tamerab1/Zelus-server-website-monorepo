package io.ruin.model.activities.pkbots;

import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.stat.StatType;

/**
 * Configuration for each server-spawned wilderness PK bot.
 *
 * One bot is created per constant at server start and respawns automatically
 * at its wilderness tile on death.
 *
 * Spawn positions are in low-level wilderness near Edgeville (level 1-5 wild),
 * close enough for players to find them easily.
 */
public enum WildPkBotSetup {

    // ── Zerker (75 Att / 99 Str / 45 Def) ────────────────────────────────────
    ZERKER("PkBot", new Position(3098, 3526, 0)) {
        @Override public void applyStats(WildPkBotPlayer bot) {
            bot.stats.set(StatType.Attack,    75);
            bot.stats.set(StatType.Strength,  99);
            bot.stats.set(StatType.Defence,   45);
            bot.stats.set(StatType.Hitpoints, 99);
            bot.stats.set(StatType.Ranged,     1);
            bot.stats.set(StatType.Prayer,    52);
            bot.stats.set(StatType.Magic,      1);
        }
        @Override public void applyGear(WildPkBotPlayer bot) {
            bot.equipment.set(Equipment.SLOT_HAT,    new Item(3751));   // Berserker helm
            bot.equipment.set(Equipment.SLOT_CAPE,   new Item(6568));   // Obsidian cape
            bot.equipment.set(Equipment.SLOT_AMULET, new Item(6585));   // Amulet of fury
            bot.equipment.set(Equipment.SLOT_WEAPON, new Item(4151));   // Abyssal whip
            bot.equipment.set(Equipment.SLOT_CHEST,  new Item(10551));  // Fighter torso
            bot.equipment.set(Equipment.SLOT_SHIELD, new Item(8844));   // Dragon defender
            bot.equipment.set(Equipment.SLOT_LEGS,   new Item(11632));  // Obsidian platelegs
            bot.equipment.set(Equipment.SLOT_HANDS,  new Item(22981));  // Ferocious gloves
            bot.equipment.set(Equipment.SLOT_FEET,   new Item(11840));  // Dragon boots
            bot.equipment.set(Equipment.SLOT_RING,   new Item(11773));  // Berserker ring (i)
            bot.getCombat().updateWeapon(false, false);
        }
    },

    // ── Pure (60 Att / 99 Str / 1 Def) ───────────────────────────────────────
    PURE("PkBot1", new Position(3101, 3523, 0)) {
        @Override public void applyStats(WildPkBotPlayer bot) {
            bot.stats.set(StatType.Attack,    60);
            bot.stats.set(StatType.Strength,  99);
            bot.stats.set(StatType.Defence,    1);
            bot.stats.set(StatType.Hitpoints, 99);
            bot.stats.set(StatType.Ranged,     1);
            bot.stats.set(StatType.Prayer,    52);
            bot.stats.set(StatType.Magic,      1);
        }
        @Override public void applyGear(WildPkBotPlayer bot) {
            bot.equipment.set(Equipment.SLOT_HAT,    new Item(21298));  // Obsidian helm
            bot.equipment.set(Equipment.SLOT_CAPE,   new Item(6568));   // Obsidian cape
            bot.equipment.set(Equipment.SLOT_AMULET, new Item(1729));   // Amulet of strength
            bot.equipment.set(Equipment.SLOT_WEAPON, new Item(4587));   // Dragon scimitar
            bot.equipment.set(Equipment.SLOT_CHEST,  new Item(10551));  // Fighter torso
            bot.equipment.set(Equipment.SLOT_SHIELD, new Item(8844));   // Dragon defender
            bot.equipment.set(Equipment.SLOT_LEGS,   new Item(11632));  // Obsidian platelegs
            bot.equipment.set(Equipment.SLOT_HANDS,  new Item(22981));  // Ferocious gloves
            bot.equipment.set(Equipment.SLOT_FEET,   new Item(11840));  // Dragon boots
            bot.equipment.set(Equipment.SLOT_RING,   new Item(11773));  // Berserker ring (i)
            bot.getCombat().updateWeapon(false, false);
        }
    },

    // ── Main (99 all) ─────────────────────────────────────────────────────────
    MAIN("PkBot2", new Position(3095, 3521, 0)) {
        @Override public void applyStats(WildPkBotPlayer bot) {
            bot.stats.set(StatType.Attack,    99);
            bot.stats.set(StatType.Strength,  99);
            bot.stats.set(StatType.Defence,   99);
            bot.stats.set(StatType.Hitpoints, 99);
            bot.stats.set(StatType.Ranged,    99);
            bot.stats.set(StatType.Prayer,    99);
            bot.stats.set(StatType.Magic,     99);
        }
        @Override public void applyGear(WildPkBotPlayer bot) {
            bot.equipment.set(Equipment.SLOT_HAT,    new Item(26470));  // Neitiznot faceguard
            bot.equipment.set(Equipment.SLOT_CAPE,   new Item(21295));  // Infernal cape
            bot.equipment.set(Equipment.SLOT_AMULET, new Item(19553));  // Amulet of torture
            bot.equipment.set(Equipment.SLOT_WEAPON, new Item(4151));   // Abyssal whip
            bot.equipment.set(Equipment.SLOT_CHEST,  new Item(11832));  // Bandos chestplate
            bot.equipment.set(Equipment.SLOT_SHIELD, new Item(8844));   // Dragon defender
            bot.equipment.set(Equipment.SLOT_LEGS,   new Item(11834));  // Bandos tassets
            bot.equipment.set(Equipment.SLOT_HANDS,  new Item(22981));  // Ferocious gloves
            bot.equipment.set(Equipment.SLOT_FEET,   new Item(13239));  // Primordial boots
            bot.equipment.set(Equipment.SLOT_RING,   new Item(11773));  // Berserker ring (i)
            bot.getCombat().updateWeapon(false, false);
        }
    };

    // ──────────────────────────────────────────────────────────────────────────

    public final String username;
    public final Position spawnPosition;

    WildPkBotSetup(String username, Position spawnPosition) {
        this.username      = username;
        this.spawnPosition = spawnPosition;
    }

    public abstract void applyStats(WildPkBotPlayer bot);
    public abstract void applyGear(WildPkBotPlayer bot);
}
