package io.ruin.model.inter.questtab.main;

import io.ruin.cache.Color;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.questtab.QuestTabEntry;
import io.ruin.model.inter.questtab.presets.PresetsMenu;

public class PvpPresetsEntry extends QuestTabEntry {

    public static final PvpPresetsEntry INSTANCE = new PvpPresetsEntry();

    @Override
    public void send(Player player) {
        send(player, "PvP Presets", "Click to view", Color.WHITE);
    }

    @Override
    public void select(Player player) {
        PresetsMenu.open(player);
    }
}
