package io.ruin.model.entity.player.presets;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.LoginListener;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SlotAction;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.ItemContainer;
import io.ruin.model.map.Position;
import io.ruin.model.skills.magic.SpellBook;
import io.ruin.model.var.VarPlayerRepository;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PresetManager {

    private static final int HIGHLIGHT_SPELLBOOK_CLIENTSCRIPT               = 10904;
    private static final int BUILD_EQUIPMENT_AND_INVENTORY_CONTAINER_CLIENTSCRIPT = 10901;
    private static final int SELECT_PRESET_CLIENTSCRIPT                     = 10909;
    private static final int HIDE_SPELLBOOK_BUTTONS_CLIENTSCRIPT            = 10902;
    private static final int BUILD_PRESET_INTERFACE_BASE_CLIENTSCRIPT       = 10900;
    private static final int REFRESH_PRESET_LIST_CLIENTSCRIPT               = 10906;

    public static final List<Preset> DEFAULT_PRESETS = new ArrayList<>();

    private final Player player;

    private final List<Preset> presets = new ArrayList<>();

    private int currentIndex;
    public Preset current;

    public PresetManager(Player player) {
        this.player = player;
    }

    // -----------------------------------------------------------------------
    // Static registration + JSON loading
    // -----------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public static void saveToFile(Player player) {
        try {
            File dir = new File(ServerWrapper.dataFolder, "runtime/custom_presets");
            dir.mkdirs();
            File file = new File(dir, player.uuid() + "_pm.json");
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(player.getPresetManager().presets, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadFromFile(Player player) {
        try {
            File file = new File(ServerWrapper.dataFolder, "runtime/custom_presets/" + player.uuid() + "_pm.json");
            if (!file.exists()) return;
            try (FileReader reader = new FileReader(file)) {
                List<Preset> loaded = GSON.fromJson(reader, new TypeToken<List<Preset>>(){}.getType());
                if (loaded != null) {
                    player.getPresetManager().presets.clear();
                    player.getPresetManager().presets.addAll(loaded);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void register() {
        LoginListener.register(PresetManager::loadFromFile);
        InterfaceHandler.register(711, h -> {
            h.actions[8] = (SlotAction) (p, s) -> {
                if (s < p.getPresetManager().presets.size()) {
                    p.getPresetManager().preview(p, p.getPresetManager().presets.get(s), false);
                }
            };
            h.actions[41] = (SimpleAction) player -> {
                player.closeInterfaces();
                player.getPresetManager().load(player, PresetType.CUSTOM_PRESET, player.getPresetManager().currentIndex);
            };
            h.actions[45] = (SimpleAction) player -> {
                Preset cur = player.getPresetManager().current;
                if (cur == null) return;
                player.dialogue(new OptionsDialogue(
                    "Delete preset '" + cur.getName() + "'?",
                    new Option("Yes, delete it.", () -> player.getPresetManager().delete(player.getPresetManager().currentIndex)),
                    new Option("Cancel.", () -> {})
                ));
            };
            h.actions[39] = (SimpleAction) player -> {
                player.stringInput("Set the name of the preset:", name -> {
                    if (name.length() < 3 || name.length() > 20) {
                        player.sendMessage("The name must be between 3 and 20 characters.");
                        return;
                    }
                    player.getPresetManager().save(player, name);
                });
            };
        });
    }

    public static void loadDefaultPresets() {
        try {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(Preset.class, new PresetDeserializer())
                .create();
            Preset[] loaded = gson.fromJson(new FileReader(new File("data/items/presets.json")), Preset[].class);
            for (Preset p : loaded) {
                if (p != null) DEFAULT_PRESETS.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------
    // Instance methods
    // -----------------------------------------------------------------------

    public void save(Player player, String name) {
        try {
            if (presets.stream().anyMatch(p -> p.getName().equalsIgnoreCase(name))) {
                player.dialogue(new MessageDialogue("You already have a preset with this name."));
                return;
            }

            Preset preset = new Preset(name, PresetType.CUSTOM_PRESET);

            Item[] inv = player.getInventory().getItems();
            Item[] copy = new Item[inv.length];
            for (int i = 0; i < inv.length; i++) {
                copy[i] = inv[i] == null ? null : new Item(inv[i].getId(), inv[i].getAmount());
            }
            preset.getInventory().add(copy);

            Item[] eq = player.getEquipment().getItems();
            Item[] eqCopy = new Item[eq.length];
            for (int i = 0; i < eq.length; i++) {
                eqCopy[i] = eq[i] == null ? null : new Item(eq[i].getId(), eq[i].getAmount());
            }
            preset.getEquipment().add(eqCopy);

            for (int i = 0; i < SpellBook.VALUES.length; i++) {
                if (SpellBook.VALUES[i].isActive(player)) {
                    preset.setSpellBook(i);
                    break;
                }
            }

            this.current = preset;
            presets.add(preset);
            saveToFile(player);
            player.sendMessage("Your setup has been saved as '" + name + "'.");
            preview(player, preset);
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage("Unable to save preset.");
        }
    }

    public void preview(Player player, Preset preset) {
        preview(player, preset, true);
    }

    public void preview(Player player, Preset preset, boolean send) {
        current = preset;
        for (int i = 0; i < presets.size(); i++) {
            if (current == presets.get(i)) currentIndex = i;
        }
        if (send) {
            player.openInterface(ToplevelComponent.MAINMODAL, 711);
        }
        player.getPacketSender().sendClientScript(3281, "ii", 2520, 1);
        player.getPacketSender().sendClientScript(BUILD_PRESET_INTERFACE_BASE_CLIENTSCRIPT);
        refresh(player, Optional.of(preset), send);
        if (send) {
            refreshPresetsList(player);
            refreshSize(player);
        }

        String[] SPELLBOOK_VALUES = { "Normal", "Ancient", "Lunar", "Arceuus" };
        int index = 0;
        for (String spellbook : SPELLBOOK_VALUES) {
            int componentHash = 711 << 16 | getSpellbookComponent(spellbook);
            player.getPacketSender().sendClientScript(HIGHLIGHT_SPELLBOOK_CLIENTSCRIPT, "iI",
                    preset.getSpellBook() == index ? 1 : 0, componentHash);
            index++;
        }
    }

    private int getSpellbookComponent(String name) {
        return switch (name) {
            case "Normal"  -> 33;
            case "Ancient" -> 34;
            case "Lunar"   -> 35;
            case "Arceuus" -> 36;
            default        -> 33;
        };
    }

    private void refreshSize(Player player) {
        player.getPacketSender().sendString(711, 52, String.valueOf(presets.size()));
        player.getPacketSender().sendString(711, 55, "10");
    }

    private void refreshPresetsList(Player player) {
        StringBuilder builder = new StringBuilder();
        for (Preset preset : presets) {
            builder.append(preset.getName()).append("|");
        }
        player.getPacketSender().sendClientScript(REFRESH_PRESET_LIST_CLIENTSCRIPT,
                "IIs", presets.size(), 10, builder.toString());
    }

    private static void refresh(Player player, Optional<Preset> currentPreset, boolean reselectPreset) {
        currentPreset.ifPresent(preset -> {
            ItemContainer container = new ItemContainer();
            container.init(28 + 14, false);

            Item[] eq  = preset.getEquipment().isEmpty() ? new Item[14] : preset.getEquipment().get(0);
            Item[] inv = preset.getInventory().isEmpty()  ? new Item[28] : preset.getInventory().get(0);

            for (int i = 0; i < 14; i++) {
                container.set(i, (eq != null && i < eq.length) ? eq[i] : null);
            }
            for (int i = 0; i < 28; i++) {
                container.set(14 + i, (inv != null && i < inv.length) ? inv[i] : null);
            }

            player.getPacketSender().sendItems(-1, 207, container.getItems(), 28 + 14);
            player.getPacketSender().sendClientScript(BUILD_EQUIPMENT_AND_INVENTORY_CONTAINER_CLIENTSCRIPT);

            if (reselectPreset) {
                player.getPacketSender().sendClientScript(SELECT_PRESET_CLIENTSCRIPT,
                        player.getPresetManager().currentIndex, 1, 1);
            }
            VarPlayerRepository.ACTIVE_SELECTED_PRESET_VARP.set(player, 0);
            VarPlayerRepository.ACTIVE_SELECTED_PRESET_VARP.forceSend();
        });
    }

    public void load(Player player, PresetType type, int index) {
        try {
            final Preset preset = getPreset(type, index);
            if (preset == null) {
                player.sendMessage("Preset not found.");
                return;
            }

            List<Integer> missing = check(preset);
            if (missing == null) return;

            player.getPrayer().deactivateAll();
            player.getCombat().resetSkull();
            player.getInventory().clear();

            Item[] inv = preset.getInventory().isEmpty() ? new Item[0] : preset.getInventory().get(0);
            for (Item item : inv) {
                if (item == null || missing.contains(item.getId())) continue;
                if (item.getId() != 995) {
                    player.getInventory().add(new Item(item.getId(), item.getAmount()));
                    player.getBank().remove(item.getId(), item.getAmount());
                }
            }

            player.getEquipment().clear();
            Item[] eq = preset.getEquipment().isEmpty() ? new Item[0] : preset.getEquipment().get(0);
            for (int i = 0; i < eq.length; i++) {
                Item item = eq[i];
                if (item == null || missing.contains(item.getId())) continue;
                player.getEquipment().set(i, new Item(item.getId(), item.getAmount()));
                player.getBank().remove(item.getId(), item.getAmount());
            }
            player.getEquipment().sendUpdates();

            SpellBook.VALUES[preset.getSpellBook()].setActive(player);

            player.dialogue(new MessageDialogue("Your setup '" + preset.getName() + "' has been loaded; your previous items have been banked."));
        } catch (Exception e) {
            player.dialogue(new MessageDialogue("This preset could not be loaded."));
            e.printStackTrace();
        }
    }

    public void delete(int index) {
        try {
            Preset preset = getPreset(PresetType.CUSTOM_PRESET, index);
            if (preset == null) {
                player.sendMessage("Preset not found.");
                return;
            }
            presets.remove(index);
            current = null;
            saveToFile(player);
            player.dialogue(new MessageDialogue("Your preset '" + preset.getName() + "' has been deleted."));
        } catch (Exception e) {
            player.sendMessage("Unable to delete the preset.");
        }
    }

    private List<Integer> check(Preset preset) {
        final List<Integer> missingIds = new ArrayList<>();

        player.getBank().deposit(player.getInventory(), false);
        player.getBank().deposit(player.getEquipment(), false);

        Item[] inv = preset.getInventory().isEmpty() ? new Item[0] : preset.getInventory().get(0);
        Item[] eq  = preset.getEquipment().isEmpty()  ? new Item[0] : preset.getEquipment().get(0);

        for (Item item : inv) {
            if (item == null || item.getId() == 995) continue;
            if (!player.getBank().contains(item)) {
                player.sendMessage("Missing: " + item.getAmount() + "x " + item.getDef().name);
                missingIds.add(item.getId());
            }
        }
        for (Item item : eq) {
            if (item == null) continue;
            if (!player.getBank().contains(item)) {
                player.sendMessage("Missing: " + item.getDef().name);
                missingIds.add(item.getId());
            }
        }
        return missingIds;
    }

    public Preset getPreset(PresetType type, int index) {
        List<Preset> list = (type == PresetType.DEFAULT_PRESET) ? DEFAULT_PRESETS : presets;
        return (index >= 0 && index < list.size()) ? list.get(index) : null;
    }

    public List<Preset> getPresets() { return presets; }
    public Preset getCurrent()       { return current; }
    public void   setCurrent(Preset p) { current = p; }

    // -----------------------------------------------------------------------
    // Open the interface
    // -----------------------------------------------------------------------

    public void open(Player player) {
        player.openInterface(ToplevelComponent.MAINMODAL, 711);
        player.getPacketSender().sendClientScript(BUILD_PRESET_INTERFACE_BASE_CLIENTSCRIPT);
        refreshPresetsList(player);
        refreshSize(player);
        if (!presets.isEmpty()) {
            preview(player, presets.get(0), false);
        } else {
            refresh(player, Optional.empty(), false);
        }
    }
}
