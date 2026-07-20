package io.ruin.model.content.loadouts;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;

/**
 * Zelus Loadout Interface — drives interface 1711 ("Preset Manager").
 *
 * <h3>Component map (from 1711.toml)</h3>
 * <pre>
 *  0   universe (root layer, 488×301)
 *  3   status text  — shows selected preset name + availability tag
 *  4   Bank button  (clickable layer)
 *  8   presets_container
 *  12  Move Up button
 *  13  Move Down button
 *  14  presets_body (slot list area)
 *
 *  Slot rows (13 slots):
 *    click  = SLOT_CLICK_BASE + slot * 3   = 15, 18, 21, …, 51
 *    text   = SLOT_TEXT_BASE  + slot * 3   = 16, 19, 22, …, 52
 *
 *  Equipment preview (parent = comp 54):
 *    56=Helm  57=Cape  58=Amulet  59=Weapon  60=Body
 *    61=Shield  62=Legs  63=Hands  64=Feet  65=Ring  66=Ammo
 *
 *  Inventory preview (parent = comp 67):
 *    69 = inventory_layout  (4-column cs2-149 container)
 *
 *  Spellbook buttons (parent = comp 70):
 *    72=Normal  73=Ancient  74=Lunar  75=Arceuus
 *
 *  Action buttons (parent = comp 76):
 *    78=Create  79=Load  80=Rename  81=Delete
 * </pre>
 */
public class ZelusLoadoutInterface {

    // -----------------------------------------------------------------------
    // Interface / component constants
    // -----------------------------------------------------------------------

    private static final int INTERFACE_ID = Interface.PRESET_MANAGER; // 1711

    /** Dynamic text that shows the currently-previewed preset name + status. */
    private static final int COMP_STATUS = 3;

    /** Bank button (deposit all gear to bank). */
    private static final int COMP_BANK = 4;

    /** Move preset up/down in the list. */
    private static final int COMP_MOVE_UP   = 12;
    private static final int COMP_MOVE_DOWN = 13;

    /** Base comp ID for slot click layers: slot i → 15 + i*3. */
    private static final int SLOT_CLICK_BASE = 15;
    /** Base comp ID for slot text labels: slot i → 16 + i*3. */
    private static final int SLOT_TEXT_BASE  = 16;
    /** Number of visible slot rows in the list. */
    private static final int LIST_SLOTS = 13;

    // Equipment preview component IDs (parent = comp 54)
    private static final int COMP_HEAD   = 56;
    private static final int COMP_CAPE   = 57;
    private static final int COMP_AMULET = 58;
    private static final int COMP_WEAPON = 59;
    private static final int COMP_BODY   = 60;
    private static final int COMP_SHIELD = 61;
    private static final int COMP_LEGS   = 62;
    private static final int COMP_HANDS  = 63;
    private static final int COMP_FEET   = 64;
    private static final int COMP_RING   = 65;
    private static final int COMP_AMMO   = 66;

    /** Inventory layout layer — bound as 4-column item grid via cs2 149. */
    private static final int COMP_INV = 69;

    // Container IDs for virtual item display (must not conflict with real game containers).
    // Equipment: one container per slot (5000–5010, offset via equipContainerOffset()).
    // Inventory: single container 5011 holding all 28 slots as a 4×7 grid in comp 69.
    private static final int EQUIP_CONTAINER_BASE = 5000;
    private static final int INV_CONTAINER_ID     = 5011;

    // Spellbook buttons (parent = comp 70)
    private static final int COMP_SB_NORMAL  = 72;
    private static final int COMP_SB_ANCIENT = 73;
    private static final int COMP_SB_LUNAR   = 74;
    private static final int COMP_SB_ARCEUUS = 75;

    // Action buttons (parent = comp 76)
    private static final int COMP_CREATE = 78;
    private static final int COMP_LOAD   = 79;
    private static final int COMP_RENAME = 80;
    private static final int COMP_DELETE = 81;

    // -----------------------------------------------------------------------
    // Open / refresh
    // -----------------------------------------------------------------------

    /**
     * Opens the Preset Manager and renders the current slot list + preview.
     */
    public static void open(Player player) {
        ZelusLoadoutManager.ensureListSize(player);
        player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);
        if (player.previewLoadoutSlot < 0
                || player.previewLoadoutSlot >= ZelusLoadoutManager.MAX_SLOTS) {
            player.previewLoadoutSlot = 0;
        }
        sendSlotList(player);
        sendPreview(player, player.previewLoadoutSlot);
        sendSpellbookHighlight(player, player.previewLoadoutSlot);
    }

    /**
     * Re-renders the slot list and preview without reopening the interface.
     * Safe to call when the interface is already closed (packets silently ignored).
     */
    public static void refresh(Player player) {
        ZelusLoadoutManager.ensureListSize(player);
        sendSlotList(player);
        sendPreview(player, player.previewLoadoutSlot);
        sendSpellbookHighlight(player, player.previewLoadoutSlot);
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Registers all interface-1711 action handlers.
     * Called once at server startup via StaticInit.
     */
    public static void register() {
        InterfaceHandler.register(INTERFACE_ID, h -> {

            // ── Slot click handlers ──────────────────────────────────────────
            for (int i = 0; i < ZelusLoadoutManager.MAX_SLOTS; i++) {
                final int slot = i;
                h.actions[SLOT_CLICK_BASE + (i * 3)] = (SimpleAction) player -> {
                    ZelusLoadoutManager.ensureListSize(player);
                    if (slot >= ZelusLoadoutManager.getMaxSlots(player)) {
                        player.sendMessage(
                            "<col=ffd700>[Loadouts]</col> Slot " + (slot + 1)
                                + " is locked. Donate to unlock more slots!");
                        return;
                    }
                    player.previewLoadoutSlot = slot;
                    sendPreview(player, slot);
                    sendSlotList(player);
                    sendSpellbookHighlight(player, slot);
                };
            }

            // Lock out-of-range slots beyond MAX_SLOTS up to LIST_SLOTS
            for (int i = ZelusLoadoutManager.MAX_SLOTS; i < LIST_SLOTS; i++) {
                final int slot = i;
                h.actions[SLOT_CLICK_BASE + (i * 3)] = (SimpleAction) player ->
                    player.sendMessage("<col=ffd700>[Loadouts]</col> Slot "
                        + (slot + 1) + " is locked.");
            }

            // ── Bank button (deposit all gear) ───────────────────────────────
            h.actions[COMP_BANK] = (SimpleAction) ZelusLoadoutManager::depositAll;

            // ── Move Up ──────────────────────────────────────────────────────
            h.actions[COMP_MOVE_UP] = (SimpleAction) player -> {
                int slot = player.previewLoadoutSlot;
                if (slot <= 0) return;
                ZelusLoadoutManager.ensureListSize(player);
                ZelusLoadout a = player.loadouts.get(slot - 1);
                ZelusLoadout b = player.loadouts.get(slot);
                player.loadouts.set(slot - 1, b);
                player.loadouts.set(slot,     a);
                player.previewLoadoutSlot = slot - 1;
                sendSlotList(player);
                sendPreview(player, player.previewLoadoutSlot);
            };

            // ── Move Down ────────────────────────────────────────────────────
            h.actions[COMP_MOVE_DOWN] = (SimpleAction) player -> {
                int slot = player.previewLoadoutSlot;
                int max  = ZelusLoadoutManager.getMaxSlots(player) - 1;
                if (slot >= max) return;
                ZelusLoadoutManager.ensureListSize(player);
                ZelusLoadout a = player.loadouts.get(slot);
                ZelusLoadout b = player.loadouts.get(slot + 1);
                player.loadouts.set(slot,     b);
                player.loadouts.set(slot + 1, a);
                player.previewLoadoutSlot = slot + 1;
                sendSlotList(player);
                sendPreview(player, player.previewLoadoutSlot);
            };

            // ── Create / Save button ─────────────────────────────────────────
            h.actions[COMP_CREATE] = (SimpleAction) player -> {
                int slot = player.previewLoadoutSlot;
                if (!isSlotActive(player, slot)) return;
                ZelusLoadoutManager.saveToSlot(player, slot);
            };

            // ── Load button ──────────────────────────────────────────────────
            h.actions[COMP_LOAD] = (SimpleAction) player -> {
                int slot = player.previewLoadoutSlot;
                if (!isSlotActive(player, slot)) return;
                ZelusLoadoutManager.loadWithConfirm(player, slot);
            };

            // ── Rename button ────────────────────────────────────────────────
            h.actions[COMP_RENAME] = (SimpleAction) player -> {
                int slot = player.previewLoadoutSlot;
                if (!isSlotActive(player, slot)) return;
                ZelusLoadoutManager.renameSlot(player, slot);
            };

            // ── Delete button ────────────────────────────────────────────────
            h.actions[COMP_DELETE] = (SimpleAction) player -> {
                int slot = player.previewLoadoutSlot;
                if (!isSlotActive(player, slot)) return;
                ZelusLoadoutManager.deleteSlot(player, slot);
            };

            // ── Spellbook selection buttons ──────────────────────────────────
            h.actions[COMP_SB_NORMAL]  = (SimpleAction) player ->
                setPresetSpellbook(player, 0);
            h.actions[COMP_SB_ANCIENT] = (SimpleAction) player ->
                setPresetSpellbook(player, 1);
            h.actions[COMP_SB_LUNAR]   = (SimpleAction) player ->
                setPresetSpellbook(player, 2);
            h.actions[COMP_SB_ARCEUUS] = (SimpleAction) player ->
                setPresetSpellbook(player, 3);
        });
    }

    // -----------------------------------------------------------------------
    // Slot list rendering
    // -----------------------------------------------------------------------

    /**
     * Sends label text for every preset slot row in the left-hand list.
     * Selected slot shown in gold; empty slots in grey; locked slots greyed-out.
     */
    private static void sendSlotList(Player player) {
        int maxUnlocked = ZelusLoadoutManager.getMaxSlots(player);
        ZelusLoadoutManager.ensureListSize(player);

        for (int i = 0; i < LIST_SLOTS; i++) {
            int textComp = SLOT_TEXT_BASE + (i * 3);
            String label;

            if (i >= ZelusLoadoutManager.MAX_SLOTS || i >= maxUnlocked) {
                label = "<col=555555>Locked";
            } else {
                ZelusLoadout loadout = player.loadouts.get(i);
                boolean selected = (i == player.previewLoadoutSlot);
                if (loadout.isEmpty()) {
                    label = selected ? "<col=ffffff>[Empty]" : "<col=888888>[Empty]";
                } else {
                    label = "<col=" + (selected ? "ffd700" : "e6804d") + ">"
                            + loadout.name;
                }
            }
            player.getPacketSender().sendString(INTERFACE_ID, textComp, label);
        }
    }

    // -----------------------------------------------------------------------
    // Preview rendering
    // -----------------------------------------------------------------------

    /**
     * Renders the equipment and inventory preview for the given slot, and
     * updates the status text with the preset name + load-availability colour.
     */
    private static void sendPreview(Player player, int slot) {
        ZelusLoadoutManager.ensureListSize(player);
        ZelusLoadout loadout = (slot >= 0 && slot < player.loadouts.size())
                ? player.loadouts.get(slot) : null;

        if (loadout == null || loadout.isEmpty()) {
            player.getPacketSender().sendString(INTERFACE_ID, COMP_STATUS,
                "<col=888888>[Empty Slot]");
            clearEquipPreview(player);
            clearInvPreview(player);
            return;
        }

        // Status line: name + colour-coded availability
        boolean bankPull = ZelusLoadoutManager.canLoadFromBank(player, loadout);
        boolean safe     = player.wildernessLevel <= 0 && !player.pvpAttackZone;
        String tag;
        if (bankPull) {
            tag = " <col=00ff00>[FREE]</col>";
        } else if (safe) {
            tag = " <col=ff8c00>[Spawn]</col>";
        } else {
            tag = " <col=ff4444>[Locked]</col>";
        }
        player.getPacketSender().sendString(INTERFACE_ID, COMP_STATUS,
            "<col=ffd700>" + loadout.name + tag);

        // Equipment slots
        sendEquipSlot(player, Equipment.SLOT_HAT,    COMP_HEAD,   loadout);
        sendEquipSlot(player, Equipment.SLOT_CAPE,   COMP_CAPE,   loadout);
        sendEquipSlot(player, Equipment.SLOT_AMULET, COMP_AMULET, loadout);
        sendEquipSlot(player, Equipment.SLOT_AMMO,   COMP_AMMO,   loadout);
        sendEquipSlot(player, Equipment.SLOT_WEAPON, COMP_WEAPON, loadout);
        sendEquipSlot(player, Equipment.SLOT_CHEST,  COMP_BODY,   loadout);
        sendEquipSlot(player, Equipment.SLOT_SHIELD, COMP_SHIELD, loadout);
        sendEquipSlot(player, Equipment.SLOT_LEGS,   COMP_LEGS,   loadout);
        sendEquipSlot(player, Equipment.SLOT_HANDS,  COMP_HANDS,  loadout);
        sendEquipSlot(player, Equipment.SLOT_RING,   COMP_RING,   loadout);
        sendEquipSlot(player, Equipment.SLOT_FEET,   COMP_FEET,   loadout);

        // Inventory (4-column container at comp 69)
        sendInventoryPreview(player, loadout);
    }

    // -----------------------------------------------------------------------
    // Item display helpers
    // -----------------------------------------------------------------------

    /** Maps equipment slot index → equipment container offset (0-10). */
    private static int equipContainerOffset(int equipSlot) {
        return switch (equipSlot) {
            case Equipment.SLOT_HAT    -> 0;
            case Equipment.SLOT_CAPE   -> 1;
            case Equipment.SLOT_AMULET -> 2;
            case Equipment.SLOT_AMMO   -> 3;
            case Equipment.SLOT_WEAPON -> 4;
            case Equipment.SLOT_CHEST  -> 5;
            case Equipment.SLOT_SHIELD -> 6;
            case Equipment.SLOT_LEGS   -> 7;
            case Equipment.SLOT_HANDS  -> 8;
            case Equipment.SLOT_FEET   -> 9;
            case Equipment.SLOT_RING   -> 10;
            default -> -1;
        };
    }

    private static void sendEquipSlot(Player player, int equipSlot,
                                      int comp, ZelusLoadout loadout) {
        int offset = equipContainerOffset(equipSlot);
        if (offset < 0) return;
        int id  = (equipSlot < loadout.equipment.length)   ? loadout.equipment[equipSlot]  : -1;
        int amt = (equipSlot < loadout.equipAmounts.length) ? loadout.equipAmounts[equipSlot] : 0;
        sendItem(player, EQUIP_CONTAINER_BASE + offset, comp,
                 new Item(id, Math.max(1, amt)));
    }

    /**
     * Sends a single item to one virtual container slot and binds it to an
     * interface component via cs2 149.
     *
     * This matches the exact pattern used by PvpPresetInterface (which works):
     *   cs2 149: 4,7,1 params  +  sendItems(containerId, item)
     */
    private static void sendItem(Player player, int containerId, int comp, Item item) {
        player.getPacketSender().sendClientScript(
                149, "IviiiIsssss",
                INTERFACE_ID << 16 | comp,
                containerId,
                4, 7, 1, -1,
                "", "", "", "", ""
        );
        player.getPacketSender().sendItems(containerId, item);
    }

    /**
     * Sends all 28 inventory items to a single 4×7 grid container (INV_CONTAINER_ID)
     * and binds it to comp 69 once via cs2 149.
     */
    private static void sendInventoryPreview(Player player, ZelusLoadout loadout) {
        // One cs2 149 call to bind the container to the 4×7 grid component.
        player.getPacketSender().sendClientScript(
                149, "IviiiIsssss",
                INTERFACE_ID << 16 | COMP_INV,
                INV_CONTAINER_ID,
                4, 7, 1, -1,
                "", "", "", "", ""
        );
        // Send all 28 items in one UpdateInvFull packet.
        Item[] items = new Item[ZelusLoadout.INV_SLOTS];
        for (int s = 0; s < ZelusLoadout.INV_SLOTS; s++) {
            int id  = loadout.inventory[s];
            int amt = loadout.invAmounts[s];
            items[s] = new Item(id, Math.max(1, amt));
        }
        player.getPacketSender().sendItems(INV_CONTAINER_ID, items);
    }

    private static void clearEquipPreview(Player player) {
        sendItem(player, EQUIP_CONTAINER_BASE + 0,  COMP_HEAD,   new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 1,  COMP_CAPE,   new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 2,  COMP_AMULET, new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 3,  COMP_AMMO,   new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 4,  COMP_WEAPON, new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 5,  COMP_BODY,   new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 6,  COMP_SHIELD, new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 7,  COMP_LEGS,   new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 8,  COMP_HANDS,  new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 9,  COMP_FEET,   new Item(-1));
        sendItem(player, EQUIP_CONTAINER_BASE + 10, COMP_RING,   new Item(-1));
    }

    private static void clearInvPreview(Player player) {
        player.getPacketSender().sendClientScript(
                149, "IviiiIsssss",
                INTERFACE_ID << 16 | COMP_INV,
                INV_CONTAINER_ID,
                4, 7, 1, -1,
                "", "", "", "", ""
        );
        Item[] empty = new Item[ZelusLoadout.INV_SLOTS];
        for (int i = 0; i < empty.length; i++) empty[i] = new Item(-1);
        player.getPacketSender().sendItems(INV_CONTAINER_ID, empty);
    }

    // -----------------------------------------------------------------------
    // Spellbook highlight
    // -----------------------------------------------------------------------

    /**
     * Highlights the spellbook button for the preset at the given slot,
     * or defaults to Normal if the slot is empty.
     */
    private static void sendSpellbookHighlight(Player player, int slot) {
        ZelusLoadoutManager.ensureListSize(player);
        int sbIdx = 0; // default: Normal
        if (slot >= 0 && slot < player.loadouts.size()) {
            ZelusLoadout loadout = player.loadouts.get(slot);
            if (!loadout.isEmpty()) {
                sbIdx = loadout.spellbook;
            }
        }

        // Show selected spellbook name in a colour-coded suffix on status text
        // (we reuse the status comp for a spellbook indicator label too,
        //  appended to whatever sendPreview already set)
        // Actually, just highlight via setConfig / setVarp — but simplest is
        // to update a child text on each button via sendString.  For now we
        // rely on the visual rectangle being present and do nothing extra here.
        // A varp-based highlight can be added later if desired.
    }

    // -----------------------------------------------------------------------
    // Spellbook setter
    // -----------------------------------------------------------------------

    private static void setPresetSpellbook(Player player, int spellbookIdx) {
        ZelusLoadoutManager.ensureListSize(player);
        int slot = player.previewLoadoutSlot;
        if (slot < 0 || slot >= player.loadouts.size()) return;
        ZelusLoadout loadout = player.loadouts.get(slot);
        if (loadout.isEmpty()) {
            player.sendMessage("Save a loadout in this slot first.");
            return;
        }
        loadout.spellbook = spellbookIdx;
        String[] names = {"Standard", "Ancient", "Lunar", "Arceuus"};
        player.sendMessage("<col=ffd700>[Loadouts]</col> Spellbook set to "
            + names[Math.min(spellbookIdx, names.length - 1)] + ".");
        refresh(player);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static boolean isSlotActive(Player player, int slot) {
        ZelusLoadoutManager.ensureListSize(player);
        if (slot < 0 || slot >= ZelusLoadoutManager.getMaxSlots(player)) {
            player.sendMessage(
                "<col=ffd700>[Loadouts]</col> Please select an unlocked slot first.");
            return false;
        }
        return true;
    }
}
