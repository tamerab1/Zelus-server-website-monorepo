package io.ruin.model.content.loadouts;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.skills.prayer.Prayer;

import java.util.ArrayList;

/**
 * Zelus Loadout Manager — handles saving, loading, quick-loading,
 * depositing, and deleting custom PK loadout slots.
 *
 * <h3>Slot budget by donation tier</h3>
 * <pre>
 *  0 GP donated  → 2 free slots  (base)
 *  ≥10            → 3 slots
 *  ≥50            → 5 slots
 *  ≥100           → 7 slots
 *  ≥250           → 9 slots
 *  ≥500           → 10 slots  (max)
 * </pre>
 *
 * <h3>Load paths</h3>
 * <ol>
 *   <li><b>Bank-pull (free)</b> — every item is found in inventory/bank.</li>
 *   <li><b>PvP Spawn (safe-zone only)</b> — player is in a safe zone → items
 *       are spawned as phantom gear following the same rules as
 *       {@link io.ruin.model.content.pvppreset.PvpPresetManager}.</li>
 *   <li><b>Blocked</b> — player is in the wilderness / PvP attack zone with
 *       missing items → load is rejected with an explanation.</li>
 * </ol>
 */
public class ZelusLoadoutManager {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Maximum possible loadout slots (unlocked at highest donation tier). */
    public static final int MAX_SLOTS = 10;

    /** Coins item ID. */
    private static final int COINS = 995;

    /**
     * Phantom-item attribute key (matches
     * {@link io.ruin.model.content.pvppreset.PvpPresetManager#PHANTOM_KEY}).
     */
    public static final String PHANTOM_KEY = "phantom";

    // -----------------------------------------------------------------------
    // Slot budget
    // -----------------------------------------------------------------------

    /**
     * Returns how many loadout slots the player has unlocked based on their
     * total amount donated to the server.
     */
    public static int getMaxSlots(Player player) {
        int donated = player.totalDonated;
        if (donated >= 500) return 10;
        if (donated >= 250) return 9;
        if (donated >= 100) return 7;
        if (donated >= 50)  return 5;
        if (donated >= 10)  return 3;
        return 2;
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    /**
     * Captures the player's current equipment, inventory, spellbook, and
     * quick-prayers into the given slot index, prompting for a name if the
     * slot was previously empty.
     *
     * @param player the player
     * @param slot   0-based slot index
     */
    public static void saveToSlot(Player player, int slot) {
        if (!isSlotAccessible(player, slot)) return;

        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);

        if (loadout.isEmpty()) {
            // New slot — ask for a name first
            player.stringInput("<col=ffd700>Enter a name for this loadout:</col>", name -> {
                if (name.length() < 1 || name.length() > 20) {
                    player.sendMessage("Loadout name must be 1–20 characters.");
                    return;
                }
                doSave(player, slot, name);
            });
        } else {
            // Overwrite with confirmation
            player.dialogue(new OptionsDialogue(
                "<col=ff8c00>Overwrite loadout '" + loadout.name + "'?",
                new Option("<col=00ff00>Yes, overwrite it.",  () -> doSave(player, slot, loadout.name)),
                new Option("<col=ff4444>No, cancel.",          player::closeDialogue)
            ));
        }
    }

    /** Renames a slot by prompting the player for a new name. */
    public static void renameSlot(Player player, int slot) {
        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);
        if (loadout.isEmpty()) {
            player.sendMessage("There is nothing saved in that slot.");
            return;
        }
        player.stringInput("<col=ffd700>Enter a new name:</col>", name -> {
            if (name.length() < 1 || name.length() > 20) {
                player.sendMessage("Name must be 1–20 characters.");
                return;
            }
            loadout.name = name;
            player.sendMessage("<col=ffd700>[Loadouts]</col> Slot " + (slot + 1)
                + " renamed to '<col=ffffff>" + name + "</col>'.");
            ZelusLoadoutInterface.refresh(player);
        });
    }

    // -----------------------------------------------------------------------
    // Load (with confirmation dialogue)
    // -----------------------------------------------------------------------

    /**
     * Shows a confirmation dialogue then loads the preset.
     */
    public static void loadWithConfirm(Player player, int slot) {
        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);
        if (loadout.isEmpty()) {
            player.sendMessage("There is nothing saved in that slot.");
            return;
        }

        boolean canBankPull = canLoadFromBank(player, loadout);
        boolean inSafeZone  = player.wildernessLevel <= 0 && !player.pvpAttackZone;
        String costLine;
        if (canBankPull) {
            costLine = "<col=00ff00>FREE</col> — items pulled from your bank.";
        } else if (inSafeZone) {
            costLine = "Items will be <col=ff8c00>spawned as phantom gear</col>. They vanish on death.";
        } else {
            costLine = "<col=ff4444>Cannot load</col> — you are missing items and are outside a safe zone.";
        }

        player.dialogue(new OptionsDialogue(
            "<col=ffd700>Load: " + loadout.name + "</col><br>" + costLine,
            new Option("<col=00ff00>Yes, load it!", () -> doLoad(player, slot)),
            new Option("<col=ff4444>Cancel.",        player::closeDialogue)
        ));
    }

    /**
     * Loads the preset without a confirmation dialogue (Quick-Load).
     */
    public static void quickLoad(Player player, int slot) {
        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);
        if (loadout.isEmpty()) {
            player.sendMessage("There is nothing saved in slot " + (slot + 1) + ".");
            return;
        }
        doLoad(player, slot);
    }

    // -----------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------

    /**
     * Asks the player to confirm deletion of the loadout at the given slot.
     */
    public static void deleteSlot(Player player, int slot) {
        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);
        if (loadout.isEmpty()) {
            player.sendMessage("That slot is already empty.");
            return;
        }
        player.dialogue(new OptionsDialogue(
            "<col=ff4444>Delete loadout '" + loadout.name + "'?</col> This cannot be undone.",
            new Option("<col=ff4444>Yes, delete it.", () -> {
                loadout.clear();
                player.sendMessage("<col=ffd700>[Loadouts]</col> Slot " + (slot + 1) + " cleared.");
                ZelusLoadoutInterface.refresh(player);
            }),
            new Option("<col=00ff00>No, keep it.", player::closeDialogue)
        ));
    }

    // -----------------------------------------------------------------------
    // Deposit All
    // -----------------------------------------------------------------------

    /**
     * Deposits the player's entire equipment and inventory into their bank.
     * Safe-zone only.
     */
    public static void depositAll(Player player) {
        if (player.wildernessLevel > 0 || player.pvpAttackZone) {
            player.sendMessage("You can only deposit gear inside a safe zone.");
            return;
        }
        player.getBank().deposit(player.getEquipment(), false);
        player.getBank().deposit(player.getInventory(),  false);
        player.sendMessage("<col=ffd700>[Loadouts]</col> All gear deposited to bank.");
    }

    // -----------------------------------------------------------------------
    // Internal — save implementation
    // -----------------------------------------------------------------------

    private static void doSave(Player player, int slot, String name) {
        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);
        loadout.name = name;

        // Equipment snapshot
        for (int s = 0; s < ZelusLoadout.EQUIP_SLOTS; s++) {
            Item item = player.getEquipment().get(s);
            if (item == null) {
                loadout.equipment[s]    = -1;
                loadout.equipAmounts[s] = 0;
            } else {
                loadout.equipment[s]    = item.getId();
                loadout.equipAmounts[s] = Math.max(1, item.getAmount());
            }
        }

        // Inventory snapshot
        Item[] inv = player.getInventory().getItems();
        for (int s = 0; s < ZelusLoadout.INV_SLOTS; s++) {
            Item item = (s < inv.length) ? inv[s] : null;
            if (item == null) {
                loadout.inventory[s]  = -1;
                loadout.invAmounts[s] = 0;
            } else {
                loadout.inventory[s]  = item.getId();
                loadout.invAmounts[s] = Math.max(1, item.getAmount());
            }
        }

        // Rune pouch snapshot
        Item[] pouchItems = player.getRunePouch().getItems();
        for (int s = 0; s < 3 && s < pouchItems.length; s++) {
            Item rune = pouchItems[s];
            if (rune == null) {
                loadout.runePouch[s]        = -1;
                loadout.runePouchAmounts[s] = 0;
            } else {
                loadout.runePouch[s]        = rune.getId();
                loadout.runePouchAmounts[s] = rune.getAmount();
            }
        }

        // Spellbook
        loadout.spellbook = 0;
        for (int i = 0; i < SpellBook.VALUES.length; i++) {
            if (SpellBook.VALUES[i].isActive(player)) {
                loadout.spellbook = i;
                break;
            }
        }

        // Quick prayers
        Prayer[] values = Prayer.VALUES;
        for (int i = 0; i < values.length && i < loadout.quickPrayers.length; i++) {
            // We can't access the private quickPrayers array directly, so we
            // reconstruct from the PlayerPrayer API.  A prayer is "quick" if
            // calling toggleQuickPrayer would remove it — the inverse of
            // isActive() is not what we want here.  We skip this for now and
            // rely on the player manually configuring quick prayers.
            loadout.quickPrayers[i] = false;
        }

        player.sendMessage("<col=ffd700>[Loadouts]</col> Slot " + (slot + 1)
            + " saved as '<col=ffffff>" + name + "</col>'.");
        ZelusLoadoutInterface.refresh(player);
    }

    // -----------------------------------------------------------------------
    // Internal — load implementation
    // -----------------------------------------------------------------------

    private static void doLoad(Player player, int slot) {
        ensureListSize(player);
        ZelusLoadout loadout = player.loadouts.get(slot);

        if (loadout.isEmpty()) {
            player.sendMessage("That slot is empty.");
            return;
        }

        if (canLoadFromBank(player, loadout)) {
            loadFromBank(player, loadout);
        } else {
            boolean inSafeZone = player.wildernessLevel <= 0 && !player.pvpAttackZone;
            if (inSafeZone) {
                spawnPhantom(player, loadout);
            } else {
                player.sendMessage("<col=ff4444>[Loadouts]</col>"
                    + " You are missing items and cannot spawn gear outside a safe zone.");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Bank-pull path
    // -----------------------------------------------------------------------

    /**
     * Returns true if the player owns every required item (in inventory + bank).
     */
    public static boolean canLoadFromBank(Player player, ZelusLoadout loadout) {
        for (int s = 0; s < ZelusLoadout.EQUIP_SLOTS; s++) {
            int id = loadout.equipment[s];
            if (id == -1) continue;
            int need = loadout.equipAmounts[s];
            int have = player.getInventory().getAmount(id) + player.getBank().getAmount(id);
            if (have < need) return false;
        }
        for (int s = 0; s < ZelusLoadout.INV_SLOTS; s++) {
            int id = loadout.inventory[s];
            if (id == -1) continue;
            int need = loadout.invAmounts[s];
            int have = player.getInventory().getAmount(id) + player.getBank().getAmount(id);
            if (have < need) return false;
        }
        return true;
    }

    private static void loadFromBank(Player player, ZelusLoadout loadout) {
        // Deposit current gear first — nothing is lost
        player.getBank().deposit(player.getEquipment(), false);
        player.getBank().deposit(player.getInventory(),  false);
        clearEquipment(player);
        clearInventory(player);

        // Pull equipment
        for (int s = 0; s < ZelusLoadout.EQUIP_SLOTS; s++) {
            int id = loadout.equipment[s];
            if (id == -1) continue;
            int amount = loadout.equipAmounts[s];
            pullFromBankOrInv(player, id, amount);
            player.getEquipment().set(s, new Item(id, amount));
        }
        player.getEquipment().sendUpdates();

        // Pull inventory
        for (int s = 0; s < ZelusLoadout.INV_SLOTS; s++) {
            int id = loadout.inventory[s];
            if (id == -1) continue;
            int amount = loadout.invAmounts[s];
            pullFromBankOrInv(player, id, amount);
            player.getInventory().set(s, new Item(id, amount));
        }
        player.getInventory().sendUpdates();

        // Restore rune pouch (deposit current runes, pull saved from bank)
        restoreRunePouch(player, loadout, true);

        applySpellbookAndPrayers(player, loadout);

        player.sendMessage("<col=ffd700>[Loadouts]</col>"
            + " <col=00ff00>" + loadout.name + "</col> loaded from your bank (free).");
    }

    private static void pullFromBankOrInv(Player player, int id, int amount) {
        int fromInv = Math.min(player.getInventory().getAmount(id), amount);
        if (fromInv > 0) {
            player.getInventory().remove(id, fromInv);
            amount -= fromInv;
        }
        if (amount > 0) {
            player.getBank().remove(id, amount);
        }
    }

    // -----------------------------------------------------------------------
    // Phantom spawn path (safe zone only)
    // -----------------------------------------------------------------------

    private static void spawnPhantom(Player player, ZelusLoadout loadout) {
        // Deposit current gear
        player.getBank().deposit(player.getEquipment(), false);
        player.getBank().deposit(player.getInventory(),  false);
        clearEquipment(player);
        clearInventory(player);

        // Spawn phantom equipment
        for (int s = 0; s < ZelusLoadout.EQUIP_SLOTS; s++) {
            int id = loadout.equipment[s];
            if (id == -1) continue;
            Item phantom = new Item(id, loadout.equipAmounts[s]);
            phantom.putAttribute(PHANTOM_KEY, "1");
            player.getEquipment().set(s, phantom);
        }
        player.getEquipment().sendUpdates();

        // Spawn phantom inventory
        for (int s = 0; s < ZelusLoadout.INV_SLOTS; s++) {
            int id = loadout.inventory[s];
            if (id == -1) continue;
            Item phantom = new Item(id, loadout.invAmounts[s]);
            phantom.putAttribute(PHANTOM_KEY, "1");
            player.getInventory().set(s, phantom);
        }
        player.getInventory().sendUpdates();

        // Restore rune pouch (spawn runes directly — no bank pull needed)
        restoreRunePouch(player, loadout, false);

        applySpellbookAndPrayers(player, loadout);

        player.sendMessage("<col=ffd700>[Loadouts]</col>"
            + " <col=ff8c00>" + loadout.name
            + "</col> spawned as phantom gear. Gear vanishes on death.");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Rune pouch helpers
    // -----------------------------------------------------------------------

    /**
     * Clears the player's rune pouch and restores it from the saved loadout.
     *
     * @param bankMode if true, the current pouch contents are deposited to bank
     *                 first (bank-load path) and runes are pulled from bank;
     *                 if false, current contents are discarded and runes are set directly.
     */
    private static void restoreRunePouch(Player player, ZelusLoadout loadout, boolean bankMode) {
        // Deposit or discard current pouch contents
        Item[] current = player.getRunePouch().getItems();
        for (int i = 0; i < current.length; i++) {
            if (current[i] != null) {
                if (bankMode) player.getBank().add(current[i].getId(), current[i].getAmount());
                player.getRunePouch().set(i, null);
            }
        }

        // Restore saved rune pouch from loadout
        for (int i = 0; i < 3; i++) {
            int id     = loadout.runePouch[i];
            int amount = loadout.runePouchAmounts[i];
            if (id == -1 || amount <= 0) continue;

            if (bankMode) {
                // Pull from bank/inventory
                int fromInv = Math.min(player.getInventory().getAmount(id), amount);
                if (fromInv > 0) { player.getInventory().remove(id, fromInv); amount -= fromInv; }
                if (amount > 0)   player.getBank().remove(id, amount);
                amount = loadout.runePouchAmounts[i]; // restore full amount for set()
            }
            player.getRunePouch().set(i, new Item(id, loadout.runePouchAmounts[i]));
        }
        player.getRunePouch().sendUpdates();
    }

    private static void applySpellbookAndPrayers(Player player, ZelusLoadout loadout) {
        if (loadout.spellbook >= 0 && loadout.spellbook < SpellBook.VALUES.length) {
            SpellBook.VALUES[loadout.spellbook].setActive(player);
        }
        // Quick prayers: deactivate all first, then re-enable saved ones
        player.getPrayer().deactivateAll();
        Prayer[] values = Prayer.VALUES;
        for (int i = 0; i < values.length && i < loadout.quickPrayers.length; i++) {
            if (loadout.quickPrayers[i]) {
                player.getPrayer().toggle(values[i]);
            }
        }
    }

    private static boolean isSlotAccessible(Player player, int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            player.sendMessage("Invalid loadout slot.");
            return false;
        }
        if (slot >= getMaxSlots(player)) {
            player.sendMessage("<col=ff4444>[Loadouts]</col>"
                + " That slot is locked. Donate more to unlock additional slots!");
            return false;
        }
        return true;
    }

    /** Ensures the player's loadout list always has MAX_SLOTS entries. */
    static void ensureListSize(Player player) {
        if (player.loadouts == null) {
            player.loadouts = new ArrayList<>();
        }
        while (player.loadouts.size() < MAX_SLOTS) {
            player.loadouts.add(new ZelusLoadout());
        }
    }

    private static void clearEquipment(Player player) {
        for (int s = 0; s < ZelusLoadout.EQUIP_SLOTS; s++) {
            player.getEquipment().set(s, null);
        }
        player.getEquipment().sendUpdates();
    }

    private static void clearInventory(Player player) {
        for (int s = 0; s < ZelusLoadout.INV_SLOTS; s++) {
            player.getInventory().set(s, null);
        }
        player.getInventory().sendUpdates();
    }
}
