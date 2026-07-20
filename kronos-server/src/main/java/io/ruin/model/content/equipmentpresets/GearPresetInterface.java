package io.ruin.model.content.equipmentpresets;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.containers.Equipment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GearPresetInterface {

    // -----------------------------------------------------------------------
    // Server-defined PvP presets (always shown first, cannot be deleted)
    // -----------------------------------------------------------------------

    public static final List<GearPreset> SERVER_PRESETS = new ArrayList<>();

    static {
        // Zerker
        HashMap<Integer, Item> zerkerEq = new HashMap<>();
        zerkerEq.put(Equipment.SLOT_HAT,    new Item(3751));
        zerkerEq.put(Equipment.SLOT_CAPE,   new Item(6568));
        zerkerEq.put(Equipment.SLOT_AMULET, new Item(6585));
        zerkerEq.put(Equipment.SLOT_WEAPON, new Item(4151));
        zerkerEq.put(Equipment.SLOT_CHEST,  new Item(10551));
        zerkerEq.put(Equipment.SLOT_SHIELD, new Item(8844));
        zerkerEq.put(Equipment.SLOT_LEGS,   new Item(11632));
        zerkerEq.put(Equipment.SLOT_HANDS,  new Item(22981));
        zerkerEq.put(Equipment.SLOT_FEET,   new Item(11840));
        zerkerEq.put(Equipment.SLOT_RING,   new Item(11773));
        zerkerEq.put(Equipment.SLOT_AMMO,   new Item(892, 1));
        List<Item> zerkerInv = List.of(new Item(12695, 2), new Item(6685, 8), new Item(3024, 6), new Item(385, 4));
        SERVER_PRESETS.add(new GearPreset("Zerker", zerkerInv, zerkerEq));

        // Pure Melee
        HashMap<Integer, Item> pureMeleeEq = new HashMap<>();
        pureMeleeEq.put(Equipment.SLOT_HAT,    new Item(21298));
        pureMeleeEq.put(Equipment.SLOT_CAPE,   new Item(6568));
        pureMeleeEq.put(Equipment.SLOT_AMULET, new Item(1729));
        pureMeleeEq.put(Equipment.SLOT_WEAPON, new Item(4587));
        pureMeleeEq.put(Equipment.SLOT_CHEST,  new Item(10551));
        pureMeleeEq.put(Equipment.SLOT_SHIELD, new Item(8844));
        pureMeleeEq.put(Equipment.SLOT_LEGS,   new Item(11632));
        pureMeleeEq.put(Equipment.SLOT_HANDS,  new Item(22981));
        pureMeleeEq.put(Equipment.SLOT_FEET,   new Item(11840));
        pureMeleeEq.put(Equipment.SLOT_RING,   new Item(11773));
        List<Item> pureMeleeInv = List.of(new Item(2440, 3), new Item(2436, 2), new Item(6685, 6), new Item(3024, 4), new Item(385, 5));
        SERVER_PRESETS.add(new GearPreset("Pure Melee", pureMeleeInv, pureMeleeEq));

        // Pure M&R
        HashMap<Integer, Item> pureMREq = new HashMap<>();
        pureMREq.put(Equipment.SLOT_HAT,    new Item(6602));
        pureMREq.put(Equipment.SLOT_CAPE,   new Item(22109));
        pureMREq.put(Equipment.SLOT_AMULET, new Item(6585));
        pureMREq.put(Equipment.SLOT_WEAPON, new Item(6724));
        pureMREq.put(Equipment.SLOT_CHEST,  new Item(10551));
        pureMREq.put(Equipment.SLOT_SHIELD, new Item(8844));
        pureMREq.put(Equipment.SLOT_LEGS,   new Item(11632));
        pureMREq.put(Equipment.SLOT_HANDS,  new Item(22981));
        pureMREq.put(Equipment.SLOT_FEET,   new Item(2577));
        pureMREq.put(Equipment.SLOT_RING,   new Item(11771));
        pureMREq.put(Equipment.SLOT_AMMO,   new Item(892, 200));
        List<Item> pureMRInv = List.of(new Item(4587), new Item(11773), new Item(2444, 2), new Item(2440, 2), new Item(2436, 1), new Item(6685, 6), new Item(3024, 4), new Item(385, 5));
        SERVER_PRESETS.add(new GearPreset("Pure M&R", pureMRInv, pureMREq));

        // Pure NH
        HashMap<Integer, Item> pureNHEq = new HashMap<>();
        pureNHEq.put(Equipment.SLOT_HAT,    new Item(4708));
        pureNHEq.put(Equipment.SLOT_CAPE,   new Item(21791));
        pureNHEq.put(Equipment.SLOT_AMULET, new Item(12002));
        pureNHEq.put(Equipment.SLOT_WEAPON, new Item(12904));
        pureNHEq.put(Equipment.SLOT_CHEST,  new Item(4712));
        pureNHEq.put(Equipment.SLOT_SHIELD, new Item(6889));
        pureNHEq.put(Equipment.SLOT_LEGS,   new Item(4714));
        pureNHEq.put(Equipment.SLOT_HANDS,  new Item(19547));
        pureNHEq.put(Equipment.SLOT_FEET,   new Item(6920));
        pureNHEq.put(Equipment.SLOT_RING,   new Item(11770));
        pureNHEq.put(Equipment.SLOT_AMMO,   new Item(560, 500));
        List<Item> pureNHInv = List.of(new Item(6685, 8), new Item(3024, 6), new Item(2444, 2), new Item(4675), new Item(9185), new Item(21944, 100), new Item(21946, 60));
        SERVER_PRESETS.add(new GearPreset("Pure NH", pureNHInv, pureNHEq));

        // Main
        HashMap<Integer, Item> mainEq = new HashMap<>();
        mainEq.put(Equipment.SLOT_HAT,    new Item(26470));
        mainEq.put(Equipment.SLOT_CAPE,   new Item(21295));
        mainEq.put(Equipment.SLOT_AMULET, new Item(19553));
        mainEq.put(Equipment.SLOT_WEAPON, new Item(21003));
        mainEq.put(Equipment.SLOT_CHEST,  new Item(11832));
        mainEq.put(Equipment.SLOT_SHIELD, new Item(8844));
        mainEq.put(Equipment.SLOT_LEGS,   new Item(11834));
        mainEq.put(Equipment.SLOT_HANDS,  new Item(22981));
        mainEq.put(Equipment.SLOT_FEET,   new Item(13239));
        mainEq.put(Equipment.SLOT_RING,   new Item(11773));
        mainEq.put(Equipment.SLOT_AMMO,   new Item(892, 1));
        List<Item> mainInv = List.of(new Item(12695, 2), new Item(6685, 8), new Item(3024, 6), new Item(385, 4));
        SERVER_PRESETS.add(new GearPreset("Main", mainInv, mainEq));
    }

    // -----------------------------------------------------------------------
    // Per-player state
    // -----------------------------------------------------------------------

    public GearPreset currentPreset;

    public void open(Player player) {
        player.openInterface(ToplevelComponent.MAINMODAL, 832);
        update(player);
    }

    // -----------------------------------------------------------------------
    // Combined list helpers
    // -----------------------------------------------------------------------

    /** Returns server presets + player custom presets as one ordered list. */
    private List<GearPreset> combined(Player player) {
        List<GearPreset> all = new ArrayList<>(SERVER_PRESETS);
        all.addAll(player.gearPresets);
        return all;
    }

    private boolean isServerPreset(GearPreset preset) {
        return SERVER_PRESETS.contains(preset);
    }

    public int getUnlockedCustomSlots(Player player) {
        if (player.totalDonated > 1000) return 8;
        if (player.totalDonated > 500)  return 6;
        if (player.totalDonated > 200)  return 4;
        if (player.totalDonated > 100)  return 3;
        if (player.totalDonated > 50)   return 2;
        return 1;
    }

    // -----------------------------------------------------------------------
    // Interface rendering
    // -----------------------------------------------------------------------

    private void update(Player player) {
        sendEmptyEquipmentAndInventory(player);
        sendPresets(player);
        if (currentPreset != null) {
            sendEquipment(player);
            sendInventory(player);
            player.getPacketSender().sendString(832, 117, currentPreset.presetName);
        } else {
            player.getPacketSender().sendString(832, 117, "None");
        }
    }

    private void sendPresets(Player player) {
        List<GearPreset> all = combined(player);
        int customSlotsAllowed = getUnlockedCustomSlots(player);
        int totalVisible = SERVER_PRESETS.size() + customSlotsAllowed;

        for (int i = 0; i < 13; i++) {
            int component = 72 + (i * 3);
            if (i < all.size()) {
                GearPreset preset = all.get(i);
                boolean active = preset == currentPreset;
                String color = isServerPreset(preset) ? "ffd700" : "e6804d";
                if (active) color = "00ff00";
                player.getPacketSender().sendString(832, component, "<col=" + color + ">" + preset.presetName);
            } else if (i < totalVisible) {
                player.getPacketSender().sendString(832, component, "<col=e6804d>Empty");
            } else {
                player.getPacketSender().sendString(832, component, "<col=990000>Locked");
            }
        }
    }

    private void selectPreset(Player player, GearPreset preset) {
        currentPreset = preset;
        update(player);
    }

    private void sendEmptyEquipmentAndInventory(Player player) {
        for (int i = 34; i < 45; i++) {
            player.getPacketSender().setHidden(832, i, false);
        }
        int loops = 0;
        for (int i = 161; i < 172; i++) {
            sendItemToContainer(player, 4732 + loops, i, new Item(-1));
            loops++;
        }
        int loops2 = 0;
        for (int i = 133; i < 161; i++) {
            sendItemToContainer(player, 2732 + loops2, i, new Item(-1));
            loops2++;
        }
    }

    private void sendEquipment(Player player) {
        int startingContainerId = 4732;
        for (int i = 0; i < 14; i++) {
            if (i == 6 || i == 8 || i == 11) continue;
            int comp = getItemComponentIdFromSlot(i);
            Item item = currentPreset.equipment.getOrDefault(i, new Item(-1));
            if (item.getId() != -1) {
                player.getPacketSender().setHidden(832, getItemSpriteComponentIdFromSlot(i), true);
            }
            sendItemToContainer(player, startingContainerId, comp, item);
            startingContainerId++;
        }
    }

    private void sendInventory(Player player) {
        int componentId = 133;
        int startingContainerId = 2732;
        List<Item> inv = currentPreset.inventory;
        for (int i = 0; i < 28; i++) {
            Item item = (i < inv.size() && inv.get(i) != null) ? inv.get(i) : new Item(-1);
            sendItemToContainer(player, startingContainerId, componentId, item);
            componentId++;
            startingContainerId++;
        }
    }

    private int getItemComponentIdFromSlot(int slot) {
        return switch (slot) {
            case Equipment.SLOT_AMMO   -> 168;
            case Equipment.SLOT_CAPE   -> 169;
            case Equipment.SLOT_HAT    -> 161;
            case Equipment.SLOT_WEAPON -> 171;
            case Equipment.SLOT_AMULET -> 162;
            case Equipment.SLOT_CHEST  -> 163;
            case Equipment.SLOT_LEGS   -> 164;
            case Equipment.SLOT_FEET   -> 165;
            case Equipment.SLOT_HANDS  -> 166;
            case Equipment.SLOT_RING   -> 167;
            case Equipment.SLOT_SHIELD -> 170;
            default -> -1;
        };
    }

    private int getItemSpriteComponentIdFromSlot(int slot) {
        return switch (slot) {
            case Equipment.SLOT_AMMO   -> 44;
            case Equipment.SLOT_CAPE   -> 35;
            case Equipment.SLOT_HAT    -> 34;
            case Equipment.SLOT_WEAPON -> 37;
            case Equipment.SLOT_AMULET -> 36;
            case Equipment.SLOT_CHEST  -> 38;
            case Equipment.SLOT_LEGS   -> 40;
            case Equipment.SLOT_FEET   -> 42;
            case Equipment.SLOT_HANDS  -> 41;
            case Equipment.SLOT_RING   -> 43;
            case Equipment.SLOT_SHIELD -> 39;
            default -> -1;
        };
    }

    private void sendItemToContainer(Player player, int containerId, int componentId, Item item) {
        player.getPacketSender().sendClientScript(
                149, "IviiiIsssss",
                832 << 16 | componentId, containerId,
                4, 7, 1, -1, "", "", "", "", ""
        );
        player.getPacketSender().sendItems(containerId, new Item(item.getId(), item.getAmount()));
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public static void register() {
        InterfaceHandler.register(832, h -> {

            // Preset list slot clicks (0–12)
            for (int i = 0; i < 13; i++) {
                final int idx = i;
                int component = 71 + (i * 3);
                h.actions[component] = (SimpleAction) player -> {
                    List<GearPreset> all = player.getGearPresetInterface().combined(player);
                    if (idx < all.size()) {
                        player.getGearPresetInterface().selectPreset(player, all.get(idx));
                    } else {
                        int totalVisible = SERVER_PRESETS.size() + player.getGearPresetInterface().getUnlockedCustomSlots(player);
                        if (idx < totalVisible) {
                            player.sendMessage("Empty slot — save your current gear with the Save button.");
                        } else {
                            player.sendMessage("This slot is locked. Donate to unlock more custom preset slots.");
                        }
                    }
                };
            }

            // Save button
            h.actions[47] = (SimpleAction) player -> {
                int allowed = player.getGearPresetInterface().getUnlockedCustomSlots(player);
                if (player.gearPresets.size() >= allowed) {
                    player.sendMessage("You have no free custom preset slots. Donate to unlock more.");
                    return;
                }
                player.dialogue(new OptionsDialogue("Save your current gear and inventory as a new preset?",
                        new Option("Yes, save it.", () -> GearPresetHandler.saveGearPreset(player)),
                        new Option("Cancel.")));
            };

            // Load/equip button
            h.actions[109] = (SimpleAction) player -> {
                GearPreset preset = player.getGearPresetInterface().currentPreset;
                if (preset == null) {
                    player.sendMessage("Select a preset from the list first.");
                    return;
                }
                GearPresetHandler.equipPreset(player, preset);
            };

            // Delete button
            h.actions[131] = (SimpleAction) player -> {
                GearPreset preset = player.getGearPresetInterface().currentPreset;
                if (preset == null) {
                    player.sendMessage("Select a preset first.");
                    return;
                }
                if (player.getGearPresetInterface().isServerPreset(preset)) {
                    player.sendMessage("Server presets cannot be deleted.");
                    return;
                }
                player.dialogue(new OptionsDialogue("Delete preset '" + preset.presetName + "'?",
                        new Option("Yes, delete it.", () -> GearPresetHandler.deleteGearPreset(player, preset)),
                        new Option("Cancel.")));
            };
        });
    }
}
