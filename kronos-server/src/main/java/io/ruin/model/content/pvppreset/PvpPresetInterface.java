package io.ruin.model.content.pvppreset;

import io.ruin.cache.ObjType;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.SpellBook;

import java.util.ArrayList;
import java.util.List;

/**
 * PvP Preset selection — chatbox dialogue based (not a client interface).
 *
 * <p>There is no real client-side widget for a "list of presets + live
 * equipment/inventory preview" screen — that only exists as a RuneLite
 * plugin (Inventory Setups), not as a networked OSRS interface. An earlier
 * version of this class reused interface 832 assuming it was some generic
 * container-heavy screen; in this cache 832 is actually the fixed
 * "Vardorvis Statistics" page with no item-container children at all, which
 * is why the equipment/inventory preview rendered garbage (noted icons,
 * wrong slots, null items). This version avoids that entirely by listing
 * presets and their contents as chatbox dialogues instead.</p>
 *
 * <h3>Flow</h3>
 * Devouring Forge → {@link #open} → paginated preset list (chatbox options,
 * max 4 per page + "More...") → selecting one shows its contents as chat
 * lines → Activate/Cancel dialogue → {@link PvpPresetManager#activate}.
 */
public class PvpPresetInterface {

    private static final int PAGE_SIZE = 4;

    // ── Public entry points ───────────────────────────────────────────────────

    public static void open(Player player) {
        showPresetChoice(player, 0);
    }

    // ── Preset list (paginated chatbox options) ─────────────────────────────

    private static void showPresetChoice(Player player, int startIndex) {
        PvpPreset[] presets = PvpPreset.values();
        int end = Math.min(startIndex + PAGE_SIZE, presets.length);

        List<Option> options = new ArrayList<>();
        for (int i = startIndex; i < end; i++) {
            PvpPreset preset = presets[i];
            boolean active = player.pvpPresetActive && player.pvpActivePreset == preset;
            options.add(new Option(preset.getDisplayName() + (active ? " (Active)" : ""),
                () -> previewPreset(player, preset)));
        }
        if (end < presets.length) {
            options.add(new Option("More...", () -> showPresetChoice(player, end)));
        }

        player.dialogue(new OptionsDialogue("Choose a PvP preset", options));
    }

    // ── Preset preview (chat lines) + activation dialogue ───────────────────

    private static void previewPreset(Player player, PvpPreset preset) {
        boolean owned = PvpPresetManager.canLoadFromBank(player, preset);

        player.sendMessage("<col=ffd700>" + preset.getDisplayName() + "</col> - " + preset.getDescription());

        player.sendMessage("Equipment:");
        for (Item item : preset.buildEquipment().values()) {
            player.sendMessage("- " + describe(item));
        }

        player.sendMessage("Inventory:");
        for (Item item : preset.buildInventory()) {
            if (item == null) continue;
            player.sendMessage("- " + describe(item));
        }

        player.sendMessage(owned
            ? "<col=00ff00>You own everything needed — this will load for free from your bank."
            : "<col=ff4444>Rental cost: " + String.format("%,d", preset.getRentalCost()) + " GP (missing items).");

        String defaultBook = capitalize(preset.defaultSpellbook().name());
        player.dialogue(new OptionsDialogue(
            preset.getDisplayName() + " — " + (owned ? "load for free?" : "rent for " + String.format("%,d", preset.getRentalCost()) + " GP?"),
            new Option("Activate (auto: " + defaultBook + " spellbook)", () -> {
                PvpPresetManager.activate(player, preset);
                player.closeInterfaces();
            }),
            new Option("Activate with custom spellbook...", () -> {
                player.closeDialogue();
                openSpellbookDialogue(player, preset);
            }),
            new Option("Back to preset list", () -> showPresetChoice(player, 0)),
            new Option("Cancel", player::closeDialogue)
        ));
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

    private static String describe(Item item) {
        ObjType def = item.getDef();
        String name = def != null ? def.name : ("Item " + item.getId());
        return item.getAmount() > 1 ? name + " x" + String.format("%,d", item.getAmount()) : name;
    }

    private static String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
