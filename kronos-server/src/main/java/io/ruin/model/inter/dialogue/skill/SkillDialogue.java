package io.ruin.model.inter.dialogue.skill;

import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.Interface;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.Subcomponent;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.DefaultAction;
import io.ruin.model.inter.dialogue.Dialogue;
import io.ruin.model.var.VarPlayerRepository;
import io.ruin.model.item.Item;

import java.util.function.BiConsumer;

public class SkillDialogue implements Dialogue {

	private static final SkillItem EMPTY = new SkillItem(-1).name("");
	/** rev 241's make dialogue (270) has 18 item slots, components 15..32 (231 had 10) */
	private static final int SLOTS = 18;

	/**
	 * Handling
	 */

	public static void register() {
		InterfaceHandler.register(Interface.MAKE_DIALOGUE, h -> {
			for (int i = 0; i < SLOTS; i++) {
				int index = i;
				h.actions[15 + i] = (DefaultAction) (player, option, slot, itemId) -> {
					select(player, index, slot);
				};
			}
			h.closedAction = (player, integer) -> {
				VarPlayerRepository.CHATBOX_INTERFACE_USE_FULL_FRAME.set(player, 0);
				player.skillDialogue = null;
			};
		});
	}

	private static void select(Player player, int itemIndex, int itemAmount) {
		SkillDialogue d = player.skillDialogue;
		if (d == null)
			return;
		if (itemIndex < 0 || itemIndex >= d.items.length)
			return;
		player.lastMakeXAmount = itemAmount;
		if (d.getAction() != null) {
			d.getAction().accept(player, new Item(d.items[itemIndex].id, itemAmount));
		} else {
			d.items[itemIndex].select(player, itemAmount);
		}
	}

	/**
	 * Opening
	 */

	public static void cook(Player player, SkillItem... item) {
		player.dialogue(new SkillDialogue("How many would you like to cook?", item));
	}

	public static void make(Player player, BiConsumer<Player, Item> action, SkillItem... items) {
		player.dialogue(new SkillDialogue(items).setAction(action));
	}

	public static void make(Player player, SkillItem... items) {
		player.dialogue(new SkillDialogue(items));
	}

	public static void makeNoMakeXAllowed(Player player, SkillItem... items) {
		player.dialogue(new SkillDialogue(items).noMakeX());
	}

	private SkillItem getItem(int index) {
		if (index < 0)
			return null;
		if (index >= items.length)
			return EMPTY;
		return items[index];
	}

	/**
	 * Separator
	 */

	private SkillItem[] items;
	private String title;
	private boolean allowMakeX;
	private int maxAmount = 28;
	private BiConsumer<Player, Item> action;

	public SkillDialogue(String title, SkillItem... items) {
		this.title = title;
		this.items = items;
		allowMakeX = true;
	}

	private SkillDialogue(SkillItem... items) {
		this(items.length > 1 ? "What would you like to make?" : "How many would you like to make?", items);
	}

	public SkillDialogue noMakeX() {
		allowMakeX = false;
		return this;
	}

	public SkillDialogue maxAmount(int maxAmount) {
		this.maxAmount = maxAmount;
		return this;
	}

	@Override
	public void open(Player player) {
		player.skillDialogue = this;
		player.getPacketSender().sendToplevelSubInterface(Interface.MAKE_DIALOGUE, Subcomponent.DIALOGUE, ToplevelComponent.CHATBOX);
		StringBuilder sb = new StringBuilder(title);
		sb.append("|");
		for (int i = 0; i < SLOTS; i++) {
			sb.append(getItem(i).name)
				.append("|");
		}
		// rev 241 script 2046: mode, "title|names", max amount, the 18 slot items, last Make-X amount (21 ints, 1 string);
		// with 231's 10 items the last amount landed in slot 11 and the real one was missing
		Object[] args = new Object[3 + SLOTS + 1];
		args[0] = allowMakeX ? 0 : 3;
		args[1] = sb.toString();
		args[2] = maxAmount;
		for (int i = 0; i < SLOTS; i++)
			args[3 + i] = getItem(i).id;
		args[3 + SLOTS] = player.lastMakeXAmount;
		player.getPacketSender().sendClientScript(2046, "isi" + "o".repeat(SLOTS) + "i", args);
		for (int i = 0; i < items.length; i++) {
			player.getPacketSender().sendIfEvents(Interface.MAKE_DIALOGUE, 15 + i, 1, 8, 1);
		}
		VarPlayerRepository.CHATBOX_INTERFACE_USE_FULL_FRAME.set(player, 1);
	}

	public SkillDialogue setAction(BiConsumer<Player, Item> selectedAction) {
		this.action = selectedAction;
		return this;
	}

	public BiConsumer<Player, Item> getAction() {
		return action;
	}
}
