package io.ruin.model.inter.questtab.presets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.ruin.api.utils.ServerWrapper;
import io.ruin.cache.Color;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.player.PlayerGroup;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.model.item.Item;
import io.ruin.model.skills.magic.SpellBook;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class PresetCustom extends Preset {

	public static final PresetCustom[] ENTRIES = {
		new PresetCustom(1),
		new PresetCustom(2),
		new PresetCustom(3),
		new PresetCustom(4),
		new PresetCustom(5),
		new PresetCustom(6),
		new PresetCustom(7),
		new PresetCustom(8),
		new PresetCustom(9),
		new PresetCustom(10),
		new PresetCustom(11),
	};

	public static final PresetCustom[] ECO_ENTRIES = {
		new PresetCustom(1),
		new PresetCustom(2),
		new PresetCustom(3),
		new PresetCustom(4),
		new PresetCustom(5),
		new PresetCustom(6),
		new PresetCustom(7),
		new PresetCustom(8),
		new PresetCustom(9),
		new PresetCustom(10),
		new PresetCustom(11),
	};

	public static PresetCustom[] getEntries() {
		return ECO_ENTRIES;
	}

	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	public static void save(Player player) {
		try {
			File dir = new File(ServerWrapper.dataFolder, "runtime/custom_presets");
			dir.mkdirs();
			File file = new File(dir, player.uuid() + ".json");
			try (FileWriter writer = new FileWriter(file)) {
				GSON.toJson(player.customPresets, PresetCustom[].class, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void load(Player player) {
		try {
			File file = new File(ServerWrapper.dataFolder, "runtime/custom_presets/" + player.uuid() + ".json");
			if (!file.exists()) return;
			try (FileReader reader = new FileReader(file)) {
				PresetCustom[] loaded = GSON.fromJson(reader, PresetCustom[].class);
				if (loaded != null) {
					player.customPresets = new PresetCustom[getEntries().length];
					for (int i = 0; i < loaded.length && i < player.customPresets.length; i++) {
						PresetCustom p = loaded[i];
						if (p != null) {
							if (p.invItems == null) p.invItems = new Item[28];
							if (p.equipItems == null) p.equipItems = new Item[14];
							if (p.runePouchItems == null) p.runePouchItems = new Item[3];
						}
						player.customPresets[i] = p;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private int id;
	private PlayerGroup[] donatorGroups;

	public PresetCustom(int id, PlayerGroup... donatorGroups) {
		this.id = id;
		this.donatorGroups = donatorGroups;
	}

	public static void check(Player player) {
        /*if (player.customPresets.length < getEntries().length) {
            PresetCustom[] custom = player.customPresets;
            player.customPresets = new PresetCustom[getEntries().length];
            System.arraycopy(custom, 0, player.customPresets, 0, custom.length);
        }*///TODO OLEM UNCOMMENT
	}

	@Override
	public void send(Player player) {
        PresetCustom preset = player.customPresets[id - 1];
        if (preset == null)
            sendEmpty(player);
        else
            sendCustom(player, preset);
	}

	private void sendEmpty(Player player) {
		send(player, "<img=" + (getImageId()) + "> Empty", Color.BRONZE);
	}

	private void sendCustom(Player player, PresetCustom preset) {
		if (preset.equipItems.length != 14) { //todo remove
			Item[] newItems = new Item[14];
			System.arraycopy(preset.equipItems, 0, newItems, 0, 14);
			preset.equipItems = newItems;
		}
		send(player, "<img=" + getImageId() + "> " + preset.name, Color.BRONZE);
	}

	private int getImageId() {
		return donatorGroups == null || donatorGroups.length == 0 ? id + 77 : donatorGroups[0].clientImgId;
	}

	@Override
	public void selectStart(Player player) {
        PresetCustom preset = player.customPresets[id - 1];
        if (donatorGroups != null && donatorGroups.length > 0) {
            boolean allowed = false;
            for (PlayerGroup tier : donatorGroups) {
                if (player.isGroup(tier)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed && !player.isAdmin()) {
                player.dialogue(new MessageDialogue("Your donator rank is not high enough to use this preset slot."));
                return;
            }
        }
        if (preset == null) {
            player.dialogue(
                    new MessageDialogue("Would you like to create a custom preset?"),
                    new OptionsDialogue(
                            new Option("Yes", () -> player.nameInput("Enter desired preset name:", name -> {
                                PresetCustom newPreset = new PresetCustom(id, donatorGroups);
                                newPreset.name = name;
                                newPreset.overwrite(player);
                                player.customPresets[id - 1] = newPreset;
                                save(player);
                                player.dialogue(new MessageDialogue("Your preset has been created."));
                                sendCustom(player, newPreset);
                            })),
                            new Option("No")
                    )
            );
        } else {
            player.dialogue(
                    new OptionsDialogue(
                            "Custom Preset: " + preset.name,
                            new Option("Select Preset", () -> preset.selectFinish(player)),
                            new Option("Rename Preset", () -> player.nameInput("Enter desired preset name:", name -> {
                                preset.name = name;
                                save(player);
                                player.dialogue(new MessageDialogue("Your preset name has been changed."));
                                sendCustom(player, preset);
                            })),
                            new Option("Overwrite Preset", () -> player.dialogue(
                                    new MessageDialogue("Are you sure you want to overwrite this preset?"),
                                    new OptionsDialogue(
                                            new Option("Yes", () -> {
                                                preset.overwrite(player);
                                                save(player);
                                                player.dialogue(new MessageDialogue("Your preset items, stats, and spellbook have been updated."));
                                            }),
                                            new Option("No")
                                    )
                            )),
                            new Option("More Options", () -> {
                                player.dialogue(
                                        new OptionsDialogue(
                                                new Option("Delete Preset", () -> player.dialogue(
                                                        new MessageDialogue("Are you sure you want to delete this preset?"),
                                                        new OptionsDialogue(
                                                                new Option("Yes", () -> {
                                                                    player.customPresets[id - 1] = null;
                                                                    save(player);
                                                                    player.dialogue(new MessageDialogue("Your preset has been deleted."));
                                                                    sendEmpty(player);
                                                                }),
                                                                new Option("No")
                                                        )
                                                )),
                                                new Option("Swap Preset", () -> player.integerInput("Enter the preset number to swap with:", num -> {
                                                    if (num < 1 || num > getEntries().length) {
                                                        player.dialogue(new MessageDialogue("Preset " + num + " does not exist."));
                                                        return;
                                                    }
                                                    PresetCustom swap = player.customPresets[num - 1];
                                                    player.customPresets[id - 1] = swap;
                                                    if (swap == null)
                                                        getEntries()[id - 1].sendEmpty(player);
                                                    else
                                                        getEntries()[id - 1].sendCustom(player, swap);
                                                    player.customPresets[num - 1] = preset;
                                                    getEntries()[num - 1].sendCustom(player, preset);
                                                    save(player);
                                                    player.dialogue(new MessageDialogue("Presets " + id + " has been swapped with preset " + num + "."));
                                                }))
                                        )
                                );
                            })
                    )
            );
        }
	}

	private void overwrite(Player player) {
        /*if (player.isPKMode()) {//TODO OLEM UNCOMMENT
            attack = level(player.getStats().get(StatType.Attack));
            strength = level(player.getStats().get(StatType.Strength));
            defence = level(player.getStats().get(StatType.Defence));
            ranged = level(player.getStats().get(StatType.Ranged));
            prayer = level(player.getStats().get(StatType.Prayer));
            magic = level(player.getStats().get(StatType.Magic));
            hitpoints = level(player.getStats().get(StatType.Hitpoints));
        } else {*/
		attack = strength = defence = ranged = prayer = magic = hitpoints = null;
		//}
		spellBook = SpellBook.VALUES[VarPlayerRepository.MAGIC_BOOK.get(player)];
		copy(player.getInventory(), invItems);
		copy(player.getEquipment(), equipItems);
		if (runePouchItems != null && !player.getRunePouch().isEmpty())
			copy(player.getRunePouch(), runePouchItems);
	}

}