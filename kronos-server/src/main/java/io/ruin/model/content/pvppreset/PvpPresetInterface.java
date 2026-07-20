package io.ruin.model.content.pvppreset;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.skills.magic.SpellBook;

import java.util.Map;

/**
 * PvP Preset selection interface — uses the server's built-in preset interface (ID 832).
 *
 * <h3>Layout</h3>
 * <ul>
 *   <li>Left panel: list of preset names (up to 13 slots, we use 3).</li>
 *   <li>Centre: equipment + inventory preview of the highlighted preset.</li>
 *   <li>Bottom: "Activate" button (component 109) and cost label (component 117).</li>
 * </ul>
 *
 * <h3>Activation paths</h3>
 * Pressing Activate calls {@link PvpPresetManager#activate(Player, PvpPreset)},
 * which auto-selects bank-load (free) or rental (GP cost) depending on inventory/bank.
 *
 * <h3>Opening</h3>
 * Called from {@code ::pvpmode} command and the Devouring Forge object action.
 */
public class PvpPresetInterface {

    private static final int INTERFACE_ID = Interface.PRESET_INTERFACE; // 832

    // ── Component IDs (mirror GearPresetInterface mapping) ──────────────────

    /** Component 72 + (i*3) = name label for preset slot i */
    private static final int PRESET_LIST_BASE = 72;

    /** Displayed name of the currently selected preset */
    private static final int SELECTED_NAME_COMPONENT = 117;

    /** Activate/load button */
    private static final int ACTIVATE_BUTTON = 109;

    /** Cost / status label shown below the preset name */
    private static final int COST_LABEL_COMPONENT = 118;

    // Equipment item components (slot → component)
    private static int equipComponent(int slot) {
        return switch (slot) {
            case Equipment.SLOT_HAT    -> 161;
            case Equipment.SLOT_AMULET -> 162;
            case Equipment.SLOT_CHEST  -> 163;
            case Equipment.SLOT_LEGS   -> 164;
            case Equipment.SLOT_FEET   -> 165;
            case Equipment.SLOT_HANDS  -> 166;
            case Equipment.SLOT_RING   -> 167;
            case Equipment.SLOT_AMMO   -> 168;
            case Equipment.SLOT_CAPE   -> 169;
            case Equipment.SLOT_SHIELD -> 170;
            case Equipment.SLOT_WEAPON -> 171;
            default -> -1;
        };
    }

    // Container IDs for equipment preview (4732–4742) and inventory (2732–2759)
    private static final int EQUIP_CONTAINER_BASE = 4732;
    private static final int INV_CONTAINER_BASE   = 2732;
    private static final int INV_COMPONENT_BASE   = 133;

    // ── Public entry points ───────────────────────────────────────────────────

    /**
     * Opens the preset selection interface. If a preset is already active, it
     * pre-selects that preset in the list so the player can see what's loaded.
     */
    public static void open(Player player) {
        player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE_ID);

        sendPresetList(player);

        // Pre-select the active or first preset for the preview
        PvpPreset toShow = player.pvpPresetActive && player.pvpActivePreset != null
            ? player.pvpActivePreset
            : PvpPreset.values()[0];
        player.pvpPresetViewing = toShow;
        sendPreview(player, toShow);
    }

    // ── Interface registration ────────────────────────────────────────────────

    public static void register() {
        InterfaceHandler.register(INTERFACE_ID, h -> {

            // Preset list click handlers (slots 0–3, components 71/74/77/80)
            PvpPreset[] presets = PvpPreset.values();
            for (int i = 0; i < presets.length; i++) {
                final PvpPreset preset = presets[i];
                int component = 71 + (i * 3);
                h.actions[component] = (SimpleAction) player -> {
                    player.pvpPresetViewing = preset;
                    sendPreview(player, preset);
                };
            }

            // Activate button
            h.actions[ACTIVATE_BUTTON] = (SimpleAction) player -> {
                PvpPreset preset = player.pvpPresetViewing;
                if (preset == null) preset = PvpPreset.values()[0];
                final PvpPreset chosen = preset;

                boolean owned = PvpPresetManager.canLoadFromBank(player, chosen);
                String costLine = owned
                    ? "Items will be pulled from your bank for free."
                    : "<col=8b0000>Rental cost: " + String.format("%,d", chosen.getRentalCost()) + " GP. Gear is lost on death.</col>";
                String defaultBook = chosen.defaultSpellbook().name().charAt(0)
                    + chosen.defaultSpellbook().name().substring(1).toLowerCase();

                player.dialogue(new OptionsDialogue(
                    chosen.getDisplayName() + " — " + costLine,
                    new Option("Activate (auto: " + defaultBook + " spellbook)", () -> {
                        PvpPresetManager.activate(player, chosen);
                        player.closeInterfaces();
                    }),
                    new Option("Activate with custom spellbook...", () -> {
                        player.closeDialogue();
                        openSpellbookDialogue(player, chosen);
                    }),
                    new Option("Cancel", player::closeDialogue)
                ));
            };
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Sends all preset names to the list panel. */
    private static void sendPresetList(Player player) {
        PvpPreset[] presets = PvpPreset.values();

        // Fill used slots with preset names
        for (int i = 0; i < presets.length; i++) {
            PvpPreset preset = presets[i];
            boolean isActive = player.pvpPresetActive && player.pvpActivePreset == preset;
            boolean owned    = PvpPresetManager.canLoadFromBank(player, preset);

            String color = isActive ? "00ff00" : (owned ? "e6804d" : "e6804d");
            String suffix = isActive ? " <col=00ff00>(Active)" : "";
            int component = PRESET_LIST_BASE + (i * 3);
            player.getPacketSender().sendString(INTERFACE_ID, component,
                "<col=" + color + ">" + preset.getDisplayName() + suffix);
        }

        // Lock unused slots
        for (int i = presets.length; i < 13; i++) {
            int component = PRESET_LIST_BASE + (i * 3);
            player.getPacketSender().sendString(INTERFACE_ID, component,
                "<col=555555>—");
        }
    }

    /**
     * Sends equipment and inventory item previews for the given preset,
     * and updates the name + cost labels.
     */
    private static void sendPreview(Player player, PvpPreset preset) {
        // Name label
        boolean owned = PvpPresetManager.canLoadFromBank(player, preset);
        player.getPacketSender().sendString(INTERFACE_ID, SELECTED_NAME_COMPONENT,
            "<col=ffd700>" + preset.getDisplayName());

        // Cost label
        String costText = owned
            ? "<col=00ff00>FREE — you own the items"
            : "<col=ff4444>Rental: " + String.format("%,d", preset.getRentalCost()) + " GP";
        player.getPacketSender().sendString(INTERFACE_ID, COST_LABEL_COMPONENT, costText);

        // Clear equipment slots first
        int containerIdx = 0;
        for (int slot = 0; slot < 14; slot++) {
            int comp = equipComponent(slot);
            if (comp == -1) continue;
            sendItem(player, EQUIP_CONTAINER_BASE + containerIdx, comp, new Item(-1));
            containerIdx++;
        }

        // Send equipment items
        Map<Integer, Item> equipment = preset.buildEquipment();
        containerIdx = 0;
        for (int slot = 0; slot < 14; slot++) {
            int comp = equipComponent(slot);
            if (comp == -1) continue;
            Item item = equipment.getOrDefault(slot, new Item(-1));
            sendItem(player, EQUIP_CONTAINER_BASE + containerIdx, comp, item);
            containerIdx++;
        }

        // Send inventory items
        Item[] inventory = preset.buildInventory();
        for (int slot = 0; slot < 28; slot++) {
            Item item = (slot < inventory.length && inventory[slot] != null)
                ? inventory[slot] : new Item(-1);
            sendItem(player, INV_CONTAINER_BASE + slot, INV_COMPONENT_BASE + slot, item);
        }
    }

    /** Sends a single item to a container slot in the interface. */
    private static void sendItem(Player player, int containerId, int componentId, Item item) {
        player.getPacketSender().sendClientScript(
            149, "IviiiIsssss",
            INTERFACE_ID << 16 | componentId, containerId,
            4, 7, 1, -1, "", "", "", "", ""
        );
        player.getPacketSender().sendItems(containerId, new Item(item.getId(), item.getAmount()));
    }

    /** Opens a dialogue to manually select which spellbook to use when activating the preset. */
    private static void openSpellbookDialogue(Player player, PvpPreset preset) {
        player.dialogue(new OptionsDialogue(
            "Select spellbook for " + preset.getDisplayName() + ":",
            new Option("Standard (Modern)", () -> {
                PvpPresetManager.activate(player, preset, SpellBook.MODERN);
                player.closeInterfaces();
            }),
            new Option("Ancient Magicks", () -> {
                PvpPresetManager.activate(player, preset, SpellBook.ANCIENT);
                player.closeInterfaces();
            }),
            new Option("Lunar Spellbook", () -> {
                PvpPresetManager.activate(player, preset, SpellBook.LUNAR);
                player.closeInterfaces();
            }),
            new Option("Arceuus Spellbook", () -> {
                PvpPresetManager.activate(player, preset, SpellBook.ARCEUUS);
                player.closeInterfaces();
            }),
            new Option("Cancel", player::closeDialogue)
        ));
    }
}
