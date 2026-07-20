package io.ruin.model.content.loadouts;

import com.google.gson.annotations.Expose;

import java.util.Arrays;

/**
 * Zelus — Custom PK loadout slot.
 *
 * <p>Stores a full gear snapshot as flat primitive arrays so it serialises
 * cleanly with Gson's {@code @Expose} and loads without any extra adapters.
 *
 * <ul>
 *   <li>{@code equipment[slot]} = item ID at that equipment slot (-1 = empty)</li>
 *   <li>{@code equipAmounts[slot]} = item amount (1 for most, >1 for stacked ammo)</li>
 *   <li>{@code inventory[slot]} = item ID at that inventory slot (-1 = empty)</li>
 *   <li>{@code invAmounts[slot]} = item amount</li>
 *   <li>{@code spellbook} = SpellBook ordinal (0=Modern, 1=Ancient, 2=Lunar, 3=Arceuus)</li>
 *   <li>{@code quickPrayers[ordinal]} = true if the prayer at that ordinal is a quick prayer</li>
 * </ul>
 */
public class ZelusLoadout {

    /** Maximum equipment slots (matches Equipment container size: 0-14). */
    public static final int EQUIP_SLOTS = 15;

    /** Maximum inventory slots. */
    public static final int INV_SLOTS = 28;

    // -----------------------------------------------------------------------
    // Persisted fields
    // -----------------------------------------------------------------------

    @Expose public String name = "";

    /** Item IDs for each equipment slot; -1 = empty. */
    @Expose public int[] equipment  = emptySlots(EQUIP_SLOTS);

    /** Item amounts matching each equipment slot. */
    @Expose public int[] equipAmounts = new int[EQUIP_SLOTS];

    /** Item IDs for each inventory slot; -1 = empty. */
    @Expose public int[] inventory   = emptySlots(INV_SLOTS);

    /** Item amounts matching each inventory slot. */
    @Expose public int[] invAmounts  = new int[INV_SLOTS];

    /** SpellBook ordinal (0-3). */
    @Expose public int spellbook = 0;

    /**
     * Quick prayer flags indexed by {@link io.ruin.model.skills.prayer.Prayer#ordinal()}.
     * Array length must match Prayer.VALUES.length — padded to 32 for headroom.
     */
    @Expose public boolean[] quickPrayers = new boolean[32];

    /** Item IDs for each rune pouch slot (3 slots); -1 = empty. */
    @Expose public int[] runePouch        = new int[]{ -1, -1, -1 };

    /** Item amounts for each rune pouch slot. */
    @Expose public int[] runePouchAmounts = new int[3];

    // -----------------------------------------------------------------------
    // Transient / helpers
    // -----------------------------------------------------------------------

    public ZelusLoadout() { }

    public ZelusLoadout(String name) {
        this.name = name;
    }

    /** True if this slot has never been saved to. */
    public boolean isEmpty() {
        return name == null || name.isEmpty();
    }

    /** Resets all arrays to empty/default without changing the name. */
    public void clear() {
        name = "";
        Arrays.fill(equipment,       -1);
        Arrays.fill(equipAmounts,     0);
        Arrays.fill(inventory,       -1);
        Arrays.fill(invAmounts,       0);
        spellbook = 0;
        Arrays.fill(quickPrayers,    false);
        Arrays.fill(runePouch,       -1);
        Arrays.fill(runePouchAmounts, 0);
    }

    private static int[] emptySlots(int size) {
        int[] arr = new int[size];
        Arrays.fill(arr, -1);
        return arr;
    }
}
