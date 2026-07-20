package io.ruin.model.inter.questtab.presets;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.questtab.presets.main.Hybrid;
import io.ruin.model.inter.questtab.presets.main.MainNoHonorTribrid;
import io.ruin.model.inter.questtab.presets.main.Melee;
import io.ruin.model.inter.questtab.presets.main.Zerker;
import io.ruin.model.inter.questtab.presets.pure.PureMelee;
import io.ruin.model.inter.questtab.presets.pure.PureNoHonorTribrid;
import io.ruin.model.inter.questtab.presets.pure.RangeDDS;
import io.ruin.model.inter.utils.Option;

public class PresetsMenu {

    public static void open(Player player) {
        player.dialogue(new OptionsDialogue("PvP Presets:",
            new Option("Main Presets", () -> openMain(player)),
            new Option("Pure Presets", () -> openPure(player)),
            new Option("Custom Presets", () -> openCustom(player, 0))
        ));
    }

    private static void openMain(Player player) {
        player.dialogue(new OptionsDialogue("Main Presets:",
            new Option("Zerker", () -> new Zerker().select(player)),
            new Option("Melee", () -> new Melee().select(player)),
            new Option("Hybrid", () -> new Hybrid().select(player)),
            new Option("Main NH Tribrid", () -> new MainNoHonorTribrid().select(player)),
            new Option("Back", () -> open(player))
        ));
    }

    private static void openPure(Player player) {
        player.dialogue(new OptionsDialogue("Pure Presets:",
            new Option("Pure Melee", () -> new PureMelee().select(player)),
            new Option("Range DDS", () -> new RangeDDS().select(player)),
            new Option("Pure NH Tribrid", () -> new PureNoHonorTribrid().select(player)),
            new Option("Back", () -> open(player))
        ));
    }

    private static void openCustom(Player player, int page) {
        player.getPresetManager().open(player);
    }
}
