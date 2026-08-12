package io.ruin.model.item.actions.impl.boxes.mystery;

import io.ruin.cache.Color;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;

import static io.ruin.cache.ItemID.COINS_995;

// Item 290 -- handed out (up to 5 at a time) by LimitedMysteryBox and NormalBundle, but never had
// an "open" handler of its own; it only had the generic MysteryBoxCrate "gift" action registered
// alongside it, same as every other openable box, which is why the item looked openable but
// wasn't. Common/low tier by design (LootItem weights/amounts are intentionally modest -- this is
// meant to be a minor filler reward, not a rare-drop-worthy one).
public class ResearchPackage {

	private static final int RESEARCH_PACKAGE = 290;

	private static final LootTable RESEARCH_PACKAGE_TABLE = new LootTable().addTable(1,
		new LootItem(COINS_995, 5000, 15000, 40),
		new LootItem(608, 1, 15),      // Drop Rate Scroll
		new LootItem(30570, 1, 15),    // Perk point scroll
		new LootItem(30572, 1, 15),    // 5% Drop rate scroll
		new LootItem(30579, 1, 15)     // Small exp lamp
	);

	public static void register() {
		ItemAction.registerInventory(RESEARCH_PACKAGE, "open", (player, item) -> {
			player.lock();
			Item reward = RESEARCH_PACKAGE_TABLE.rollItem();
			player.closeDialogue();
			item.remove();
			player.getInventory().add(reward);
			player.sendFilteredMessage(Color.DARK_GREEN.wrap("You open the research package to find " + reward.getDef().descriptiveName + "."));
			player.unlock();
		});
	}
}
