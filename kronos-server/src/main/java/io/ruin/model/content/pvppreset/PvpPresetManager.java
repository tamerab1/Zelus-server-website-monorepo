package io.ruin.model.content.pvppreset;

import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.stat.StatType;

import java.util.Map;

/**
 * Core logic for the PvP Preset system.
 *
 * <h3>Two activation paths</h3>
 * <ol>
 *   <li><b>Bank-load (free)</b> — if the player already owns every item from the
 *       preset in their bank or inventory, those real items are pulled out and
 *       equipped.  No GP is charged; the gear behaves normally on death (items
 *       drop / are lost as usual).</li>
 *   <li><b>Rental (paid)</b> — if any item is missing, the player pays
 *       {@link PvpPreset#getRentalCost()} GP (deducted from inventory first,
 *       then bank).  Spawned gear is phantom: it vanishes on death, cannot be
 *       dropped or traded, and cannot be taken into PvM boss areas.</li>
 * </ol>
 *
 * <h3>Economy protection rules (all enforced here)</h3>
 * <ol>
 *   <li>Phantom items are never given to the player's real inventory; they exist
 *       only as transient equipment/inventory state while the preset is active.</li>
 *   <li>On death while in <em>rental</em> (phantom) mode the dropped items list
 *       is cleared — the killer receives PK Points instead of real items (see
 *       {@link #handlePresetDeath}).  The rental fee is already consumed and is
 *       NOT refunded.</li>
 *   <li>Attempting to enter a designated PvM boss region while in rental preset
 *       mode is blocked (see {@link #isPresetGearBlocked}).</li>
 *   <li>Bank-load mode uses real items, so normal death rules apply — the block
 *       above does NOT apply to bank-loaded presets.</li>
 * </ol>
 */
public class PvpPresetManager {

    /** PK Points awarded to the killer per <em>rental</em> preset-mode kill. */
    public static final int PRESET_KILL_PKP_REWARD = 3;

    /** Item ID for coins (GP). */
    private static final int COINS = 995;

    /**
     * Attribute key stamped on every item spawned in rental mode.
     * Used by trade, shop, ground-item, and inventory handlers to block these
     * items from leaving the player's possession in any way.
     */
    public static final String PHANTOM_KEY = "phantom";

    /**
     * Returns {@code true} if the item carries the phantom rental flag.
     * Null-safe — always returns {@code false} for null items or empty attributes.
     */
    public static boolean isPhantomItem(Item item) {
        return item != null
            && item.attributes != null
            && "1".equals(item.attributes.get(PHANTOM_KEY));
    }

    // -----------------------------------------------------------------------
    // Activation entry point
    // -----------------------------------------------------------------------

    /**
     * Activates the given preset on {@code player}.
     *
     * <p>Tries the bank-load path first (free).  Falls back to the rental path
     * if any item is missing.</p>
     *
     * @param player the player to apply the preset to
     * @param preset the preset to apply
     */
    /** Activates preset using the preset's default spellbook. */
    public static void activate(Player player, PvpPreset preset) {
        activate(player, preset, null);
    }

    /**
     * Activates preset with an optional spellbook override.
     * Pass {@code null} to use the preset's {@link PvpPreset#defaultSpellbook()}.
     */
    public static void activate(Player player, PvpPreset preset, SpellBook spellbookOverride) {
        if (player.pvpPresetActive) {
            deactivate(player, false);
        }

        if (canLoadFromBank(player, preset)) {
            activateFromBank(player, preset);
        } else {
            // PVM Mode accounts (regular PVM + all ironman tiers) are blocked from renting
            // system PvP presets — they may only load a preset they already own in their bank.
            if (player.isPvmMode()) {
                player.sendMessage("[PvP Preset] PVM Mode accounts can only load presets they already own in their bank.");
                return;
            }
            if (player.getInventory().isNotEmpty()) {
                player.sendMessage("You must have an empty inventory before loading a preset.");
                return;
            }
            activateRental(player, preset);
        }

        SpellBook book = (spellbookOverride != null) ? spellbookOverride : preset.defaultSpellbook();
        book.setActive(player);
    }

    // -----------------------------------------------------------------------
    // Path A — bank-load (free, real items)
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the player owns <em>every</em> item required by
     * {@code preset} across their inventory and bank (quantity-aware).
     */
    public static boolean canLoadFromBank(Player player, PvpPreset preset) {
        // Check equipment items
        for (Item item : preset.buildEquipment().values()) {
            int needed = item.getAmount();
            int have   = player.getInventory().getAmount(item.getId())
                       + player.getBank().getAmount(item.getId());
            if (have < needed) return false;
        }
        // Check inventory items
        for (Item item : preset.buildInventory()) {
            if (item == null) continue;
            int needed = item.getAmount();
            int have   = player.getInventory().getAmount(item.getId())
                       + player.getBank().getAmount(item.getId());
            if (have < needed) return false;
        }
        return true;
    }

    /**
     * Activates a preset by pulling the real items from the player's bank and
     * inventory.  No GP is charged.  The player's existing gear is deposited to
     * the bank first so nothing is lost.
     *
     * <p>Sets {@code pvpPresetBankMode = true} so that {@link #deactivate} and
     * {@link #handlePresetDeath} know to treat this as real-item gear.</p>
     */
    private static void activateFromBank(Player player, PvpPreset preset) {
        // Save real loadout (so we can restore it on deactivate/death)
        player.pvpPresetSavedEquipment = cloneEquipment(player);
        player.pvpPresetSavedInventory = cloneInventory(player);
        player.pvpPresetSavedStats     = saveStats(player);

        // Deposit current gear (+ rune pouch contents) to bank so nothing is lost
        player.getBank().deposit(player.getEquipment(), false);
        player.getBank().deposit(player.getInventory(), false);
        saveAndClearRunePouch(player, true);

        clearEquipment(player);
        clearInventory(player);

        // Pull equipment items from bank/inventory
        Map<Integer, Item> equipment = preset.buildEquipment();
        for (Map.Entry<Integer, Item> entry : equipment.entrySet()) {
            int itemId = entry.getValue().getId();
            int amount = entry.getValue().getAmount();

            int fromInv = Math.min(player.getInventory().getAmount(itemId), amount);
            if (fromInv > 0) {
                player.getInventory().remove(itemId, fromInv);
                amount -= fromInv;
            }
            if (amount > 0) {
                player.getBank().remove(itemId, amount);
            }
            player.getEquipment().set(entry.getKey(), new Item(entry.getValue().getId(), entry.getValue().getAmount()));
        }
        player.getEquipment().sendUpdates();

        // Pull inventory items from bank/inventory
        Item[] presetInv = preset.buildInventory();
        for (int slot = 0; slot < Math.min(presetInv.length, 28); slot++) {
            Item item = presetInv[slot];
            if (item == null) continue;
            int itemId = item.getId();
            int amount = item.getAmount();

            int fromInv = Math.min(player.getInventory().getAmount(itemId), amount);
            if (fromInv > 0) {
                player.getInventory().remove(itemId, fromInv);
                amount -= fromInv;
            }
            if (amount > 0) {
                player.getBank().remove(itemId, amount);
            }
            player.getInventory().set(slot, new Item(item.getId(), item.getAmount()));
        }
        player.getInventory().sendUpdates();

        // Apply rune pouch (pull runes from bank)
        applyRunePouch(player, preset, true);

        // Lock skill levels to preset values
        int[] levels = preset.buildCombatLevels();
        setStats(player, levels);

        // Flag state
        player.pvpPresetActive   = true;
        player.pvpActivePreset   = preset;
        player.pvpPresetBankMode = true;

        player.sendMessage("[PvP Preset] " + preset.getDisplayName() + " loaded from your bank (free). Type ::pvpmode off to restore your gear.");
    }

    // -----------------------------------------------------------------------
    // Path B — rental (paid, phantom items)
    // -----------------------------------------------------------------------

    /**
     * Activates a preset by charging a rental fee and spawning phantom gear.
     * If the player cannot afford the fee, activation is aborted with a message.
     */
    private static void activateRental(Player player, PvpPreset preset) {
        int cost = preset.getRentalCost();

        if (!takePayment(player, cost)) {
            player.sendMessage("[PvP Preset] You need " + String.format("%,d", cost) + " GP to rent this setup.");
            return;
        }

        // Save real loadout
        player.pvpPresetSavedEquipment = cloneEquipment(player);
        player.pvpPresetSavedInventory = cloneInventory(player);
        player.pvpPresetSavedStats     = saveStats(player);

        // Clear current gear (save rune pouch without depositing to bank)
        saveAndClearRunePouch(player, false);
        clearEquipment(player);
        clearInventory(player);

        // Apply phantom equipment — each item is stamped with the phantom flag
        Map<Integer, Item> equipment = preset.buildEquipment();
        for (Map.Entry<Integer, Item> entry : equipment.entrySet()) {
            Item phantom = new Item(entry.getValue().getId(), entry.getValue().getAmount());
            phantom.putAttribute(PHANTOM_KEY, "1");
            player.getEquipment().set(entry.getKey(), phantom);
        }
        player.getEquipment().sendUpdates();

        // Apply phantom inventory — same flag on every slot
        Item[] presetInv = preset.buildInventory();
        for (int slot = 0; slot < Math.min(presetInv.length, 28); slot++) {
            if (presetInv[slot] != null) {
                Item phantom = new Item(presetInv[slot].getId(), presetInv[slot].getAmount());
                phantom.putAttribute(PHANTOM_KEY, "1");
                player.getInventory().set(slot, phantom);
            }
        }
        player.getInventory().sendUpdates();

        // Apply rune pouch (spawn runes directly — no bank needed for rental)
        applyRunePouch(player, preset, false);

        // Lock skill levels
        int[] levels = preset.buildCombatLevels();
        setStats(player, levels);

        // Flag state
        player.pvpPresetActive   = true;
        player.pvpActivePreset   = preset;
        player.pvpPresetBankMode = false;

        player.sendMessage("[PvP Preset] " + preset.getDisplayName() + " loaded! Rental fee: "
                + String.format("%,d", cost) + " GP. Type ::pvpmode off to restore your gear.");
    }

    /**
     * Attempts to deduct {@code amount} GP from the player — inventory first,
     * then bank for any remainder.
     *
     * @return {@code true} if the full amount was successfully deducted
     */
    private static boolean takePayment(Player player, int amount) {
        int invCoins  = player.getInventory().getAmount(COINS);
        int bankCoins = player.getBank().getAmount(COINS);

        if (invCoins + bankCoins < amount) {
            return false;
        }

        int fromInv = Math.min(invCoins, amount);
        if (fromInv > 0) {
            player.getInventory().remove(COINS, fromInv);
            amount -= fromInv;
        }
        if (amount > 0) {
            player.getBank().remove(COINS, amount);
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Deactivation
    // -----------------------------------------------------------------------

    /**
     * Deactivates the active preset, removes all preset items, and restores
     * the player's real loadout.
     *
     * @param player  the player
     * @param message whether to send a confirmation message
     */
    public static void deactivate(Player player, boolean message) {
        if (!player.pvpPresetActive) {
            if (message)
                player.sendMessage("You don't have an active PvP preset.");
            return;
        }

        // Remove all preset gear (phantom or real)
        clearEquipment(player);
        clearInventory(player);

        // Restore real gear
        if (player.pvpPresetSavedEquipment != null) {
            for (int slot = 0; slot < player.pvpPresetSavedEquipment.length; slot++) {
                player.getEquipment().set(slot, player.pvpPresetSavedEquipment[slot]);
            }
            player.getEquipment().sendUpdates();
        }
        if (player.pvpPresetSavedInventory != null) {
            for (int slot = 0; slot < player.pvpPresetSavedInventory.length; slot++) {
                player.getInventory().set(slot, player.pvpPresetSavedInventory[slot]);
            }
            player.getInventory().sendUpdates();
        }

        // Restore real skill levels
        if (player.pvpPresetSavedStats != null) {
            restoreStats(player, player.pvpPresetSavedStats);
        }

        // Restore rune pouch
        restoreRunePouch(player);

        // Clear state
        player.pvpPresetActive          = false;
        player.pvpActivePreset           = null;
        player.pvpPresetBankMode         = false;
        player.pvpPresetSavedEquipment   = null;
        player.pvpPresetSavedInventory   = null;
        player.pvpPresetSavedStats       = null;
        player.pvpPresetSavedRunePouch   = null;

        if (message)
            player.sendMessage("[PvP Preset] Preset deactivated — your original gear has been restored.");
    }

    // -----------------------------------------------------------------------
    // Death handling  (economy protection)
    // -----------------------------------------------------------------------

    /**
     * Called from the death hook when a player in preset mode is killed.
     *
     * <ul>
     *   <li><b>Rental mode</b>: phantom gear vanishes; killer receives bonus PKP.</li>
     *   <li><b>Bank mode</b>: real items have already been removed from the bank,
     *       so they follow normal death rules (i.e., they may drop).  We just
     *       restore the player's stats and clear the preset flags.</li>
     * </ul>
     *
     * @param killed the dead player
     * @param killer the killing player (may be null if not a player kill)
     */
    public static void handlePresetDeath(Player killed, Player killer) {
        boolean wasBankMode = killed.pvpPresetBankMode;

        if (!wasBankMode) {
            // Rental mode: phantom items must NOT drop — clear them now
            clearEquipment(killed);
            clearInventory(killed);

            // Restore real gear so the player respawns with their items
            if (killed.pvpPresetSavedEquipment != null) {
                for (int slot = 0; slot < killed.pvpPresetSavedEquipment.length; slot++) {
                    killed.getEquipment().set(slot, killed.pvpPresetSavedEquipment[slot]);
                }
            }
            if (killed.pvpPresetSavedInventory != null) {
                for (int slot = 0; slot < killed.pvpPresetSavedInventory.length; slot++) {
                    killed.getInventory().set(slot, killed.pvpPresetSavedInventory[slot]);
                }
            }
        }
        // Bank mode: real items already in containers — normal drop logic in caller handles them

        // Always restore stats from snapshot
        if (killed.pvpPresetSavedStats != null) {
            restoreStats(killed, killed.pvpPresetSavedStats);
        }

        // Restore rune pouch
        restoreRunePouch(killed);

        // Reset preset flags
        killed.pvpPresetActive         = false;
        killed.pvpActivePreset          = null;
        killed.pvpPresetBankMode        = false;
        killed.pvpPresetSavedEquipment  = null;
        killed.pvpPresetSavedInventory  = null;
        killed.pvpPresetSavedStats      = null;
        killed.pvpPresetSavedRunePouch  = null;

        // Rental-only: award killer bonus PKP (no items dropped)
        if (!wasBankMode && killer != null && killer.getIpInt() != killed.getIpInt()) {
            killer.updatePKPoints(PRESET_KILL_PKP_REWARD);
            killer.sendMessage("[PvP Preset] +" + PRESET_KILL_PKP_REWARD + " bonus PKP for killing a rental preset player! (no item drop)");
        }
    }

    // -----------------------------------------------------------------------
    // Economy gate  (boss/PvM area block)
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the player is in <em>rental</em> preset mode and
     * should be blocked from entering a protected PvM area.
     *
     * <p>Bank-mode players use real items, so they are NOT blocked.</p>
     *
     * @param player the player attempting to enter the area
     * @return true if entry must be blocked
     */
    public static boolean isPresetGearBlocked(Player player) {
        return player.pvpPresetActive && !player.pvpPresetBankMode;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Rune pouch helpers
    // -----------------------------------------------------------------------

    /**
     * Saves the current rune pouch contents then clears the pouch.
     * If {@code depositToBank} is true the contents are also deposited to bank
     * (used in bank-load path so nothing is lost).
     */
    private static void saveAndClearRunePouch(Player player, boolean depositToBank) {
        Item[] pouchItems = player.getRunePouch().getItems();
        Item[] snapshot = new Item[pouchItems.length];
        for (int i = 0; i < pouchItems.length; i++) {
            snapshot[i] = pouchItems[i] == null ? null
                : new Item(pouchItems[i].getId(), pouchItems[i].getAmount());
            if (depositToBank && pouchItems[i] != null) {
                player.getBank().add(pouchItems[i].getId(), pouchItems[i].getAmount());
            }
            player.getRunePouch().set(i, null);
        }
        player.pvpPresetSavedRunePouch = snapshot;
        player.getRunePouch().sendUpdates();
    }

    /**
     * Fills the rune pouch with the preset's rune pouch items.
     * Bank-load path pulls runes from bank/inventory first.
     * Rental path sets them directly (they disappear on deactivate/death).
     */
    private static void applyRunePouch(Player player, PvpPreset preset, boolean bankMode) {
        Item[] runes = preset.buildRunePouch();
        if (runes == null) return;
        for (int i = 0; i < Math.min(runes.length, 3); i++) {
            if (runes[i] == null) continue;
            if (bankMode) {
                int need = runes[i].getAmount();
                int fromInv = Math.min(player.getInventory().getAmount(runes[i].getId()), need);
                if (fromInv > 0) { player.getInventory().remove(runes[i].getId(), fromInv); need -= fromInv; }
                if (need > 0)     player.getBank().remove(runes[i].getId(), need);
            }
            player.getRunePouch().set(i, new Item(runes[i].getId(), runes[i].getAmount()));
        }
        player.getRunePouch().sendUpdates();
    }

    /**
     * Clears the current preset runes from the pouch and restores the saved snapshot.
     * If bank-mode, current preset runes are deposited to bank; otherwise discarded.
     */
    private static void restoreRunePouch(Player player) {
        if (player.pvpPresetSavedRunePouch == null) return;
        Item[] pouchItems = player.getRunePouch().getItems();
        // Deposit or discard current (preset) runes
        for (int i = 0; i < pouchItems.length; i++) {
            if (pouchItems[i] == null) continue;
            if (player.pvpPresetBankMode) {
                player.getBank().add(pouchItems[i].getId(), pouchItems[i].getAmount());
            }
            player.getRunePouch().set(i, null);
        }
        // Restore saved rune pouch
        Item[] saved = player.pvpPresetSavedRunePouch;
        for (int i = 0; i < Math.min(saved.length, pouchItems.length); i++) {
            player.getRunePouch().set(i, saved[i]);
        }
        player.getRunePouch().sendUpdates();
    }

    /** Saves a snapshot of all equipment slots. */
    private static Item[] cloneEquipment(Player player) {
        Item[] original = player.getEquipment().getItems();
        Item[] copy = new Item[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : new Item(original[i].getId(), original[i].getAmount());
        }
        return copy;
    }

    /** Saves a snapshot of the full inventory. */
    private static Item[] cloneInventory(Player player) {
        Item[] original = player.getInventory().getItems();
        Item[] copy = new Item[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : new Item(original[i].getId(), original[i].getAmount());
        }
        return copy;
    }

    private static final StatType[] COMBAT_STATS = {
        StatType.Attack, StatType.Defence, StatType.Strength,
        StatType.Hitpoints, StatType.Ranged, StatType.Prayer, StatType.Magic
    };

    /**
     * Saves [fixedLevel, currentLevel, (int)experience] for all 7 combat stats.
     */
    private static int[][] saveStats(Player player) {
        int[][] saved = new int[COMBAT_STATS.length][3];
        for (int i = 0; i < COMBAT_STATS.length; i++) {
            var stat = player.getStats().get(COMBAT_STATS[i]);
            saved[i][0] = stat.fixedLevel;
            saved[i][1] = stat.currentLevel;
            saved[i][2] = (int) stat.experience;
        }
        return saved;
    }

    /** Sets all combat skill levels to the preset-defined values. */
    private static void setStats(Player player, int[] levels) {
        for (int i = 0; i < COMBAT_STATS.length; i++) {
            if (levels[i] > 0) {
                player.getStats().set(COMBAT_STATS[i], levels[i]);
            }
        }
        int presetHp = levels[PvpPreset.HITPOINTS] > 0 ? levels[PvpPreset.HITPOINTS] : 99;
        player.setHp(presetHp);
    }

    /** Restores saved [fixedLevel, currentLevel, experience] triples. */
    private static void restoreStats(Player player, int[][] saved) {
        for (int i = 0; i < COMBAT_STATS.length; i++) {
            player.getStats().set(COMBAT_STATS[i], saved[i][0], saved[i][2]);
            player.getStats().get(COMBAT_STATS[i]).currentLevel = saved[i][1];
            player.getPacketSender().sendStat(
                COMBAT_STATS[i].ordinal(),
                saved[i][1],
                saved[i][2]
            );
        }
        player.setHp(player.getStats().get(StatType.Hitpoints).currentLevel);
    }

    /** Removes all items from the equipment container. */
    private static void clearEquipment(Player player) {
        for (int slot = 0; slot < 15; slot++) {
            player.getEquipment().set(slot, null);
        }
        player.getEquipment().sendUpdates();
    }

    /** Removes all items from the inventory container. */
    private static void clearInventory(Player player) {
        for (int slot = 0; slot < 28; slot++) {
            player.getInventory().set(slot, null);
        }
        player.getInventory().sendUpdates();
    }
}
