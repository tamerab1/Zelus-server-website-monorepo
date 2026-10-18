package io.ruin.network.incoming.handlers.command;

import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ::youtubepack &lt;player name&gt; (owner / developer only, 2026-10-17): deposits the YouTuber Creator Pack into an
 * online player's bank as one new tab (Bank.addToNewTab: all-or-nothing, never dropped on the floor).
 * <p>
 * Ids were resolved from the live cache by exact name. Where two items share a name, the REAL item is used, not its
 * 16-bit ground proxy (GroundItem maps 60280 -> 31156 and 60400 -> 31224): Bond of the Sovereign Monarch (T1) 60280,
 * Draconic Pickaxe 60400. Bonds are given un-attuned.
 */
public final class YoutubePackCommand {

	/** {item id, amount} */
	private static final int[][] PACK = {
		// custom cosmetics, weapons and armour
		{59591, 1}, {59592, 1}, {59593, 1},             // Morveth fullhelm / platebody / platelegs (or)
		{31778, 1},                                     // Twisted bow (celestial)
		{31818, 1},                                     // Nightfall bow (celestial)
		{31800, 1}, {31802, 1}, {31804, 1},             // Zelus Relic hood / coat / legs
		{31794, 1}, {31798, 1},                         // Zelus Partyhat, Zelus Santa Hat
		{31768, 1}, {31770, 1}, {31772, 1}, {31776, 1}, // Emerald Draconies hood / platebody / platelegs / wings
		{31774, 1},                                     // Emerald Draconies Warhammer (EmeraldDraconiesWarhammer special)
		{59985, 1},                                     // Venomous Fire cape
		{31764, 1},                                     // Frostfall Wings
		{31796, 1},                                     // Zelus H'ween Mask
		{31810, 1},                                     // Zelus Cosmic Wings
		{59576, 1},                                     // Youtube Cape
		// boxes, pet shards and combat pets
		{31618, 3},                                     // Arcane mystery box
		{31792, 2},                                     // Rivals mystery box
		{6199, 3},                                      // Mystery box
		{31814, 1},                                     // Ascended Bestiary (empty)
		{31748, 10},                                    // Astral Spore
		{31816, 2000},                                  // Combat Pet Shard
		{31738, 1},                                     // Magma Skirmisher (T3)
		{31730, 1},                                     // Magmafist Smasher (T2)
		{31716, 1},                                     // Ironknuckle Brawler (T1)
		{31722, 1},                                     // Spectral Soulbinder (T1)
		{31742, 1},                                     // Starbound Invoker (T2)
		// endgame PvM / hybrid and Wilderness Forge upgrades
		{30777, 1}, {30779, 1}, {30781, 1},             // Radiant oathplate helm / chest / legs
		{31808, 1},                                     // Zelus Scythe of Vitur
		{59983, 1},                                     // Lunar Fire cape
		{31428, 1},                                     // Volcanic whip
		{27552, 1},                                     // Ghommal's avernic defender 6
		{31754, 1},                                     // Infernal Fury Gloves
		{60280, 1},                                     // Bond of the Sovereign Monarch (T1)
		{31380, 1},                                     // Bond of the Artisan (T2)
		{31361, 1},                                     // Bond of the Astral Archmage (T3)
		{31410, 1},                                     // Void max cape
		{31596, 1},                                     // Starweave boots
		{31524, 1},                                     // Bloodforged godsword
		{31526, 1},                                     // Bloodfang claws
		{31650, 1},                                     // Skyhunter bow
		{11212, 1000},                                  // Dragon arrow
		{31568, 1}, {31570, 1}, {31572, 1},             // Warbringer Bandos chestplate / tassets / boots
		{60400, 1},                                     // Draconic Pickaxe
		{31582, 1},                                     // Amulet of carnage
		{31546, 1},                                     // Warlord ring
		{31584, 1},                                     // Necklace of agony
		{30467, 1},                                     // $50 Donator bond
		{31648, 1},                                     // Grizzlemaw chainmace
	};

	private YoutubePackCommand() {
	}

	/** Called by CommandHandlerAdmin; the owner / developer check is done here so the rank rule lives with the command. */
	static void run(Player player, String[] args) {
		if (!player.isDev()) {
			player.sendMessage("Only the owner or a developer can use ::youtubepack.");
			return;
		}
		if (args == null || args.length == 0) {
			player.sendMessage("Usage: ::youtubepack <player name>");
			return;
		}
		String name = String.join(" ", Arrays.copyOfRange(args, 0, args.length)).trim();
		Player target = World.getPlayer(name);
		if (target == null) {
			player.sendMessage(String.format("<col=ff0000>Player %s is currently offline.", name));
			return;
		}

		List<Item> pack = new ArrayList<>(PACK.length);
		for (int[] entry : PACK) {
			ObjType def = ObjType.get(entry[0]);
			if (def == null || def.isPlaceholder()) {
				// fail loud: a missing id means the cache changed, never hand out a partial pack silently
				player.sendMessage("<col=ff0000>::youtubepack aborted: item " + entry[0] + " does not exist in the cache.");
				return;
			}
			pack.add(new Item(entry[0], entry[1]));
		}

		int result = target.getBank().addToNewTab(pack);
		if (result == -1) {
			player.sendMessage("<col=ff0000>" + target.getName() + " can't use a bank (ultimate ironman). Nothing was given.");
			return;
		}
		if (result > 0) {
			player.sendMessage("<col=ff0000>" + target.getName() + "'s bank is too full: " + result
				+ " more free slot" + (result == 1 ? " is" : "s are") + " needed. Nothing was given.");
			return;
		}
		player.sendMessage(String.format("Successfully loaded YouTuber starter pack into %s's bank.", target.getName()));
		if (target != player)
			target.sendMessage("<col=ef1020>A YouTuber Creator Pack has been deposited into your bank!");
		else
			player.sendMessage("<col=ef1020>A YouTuber Creator Pack has been deposited into your bank!");
	}
}
