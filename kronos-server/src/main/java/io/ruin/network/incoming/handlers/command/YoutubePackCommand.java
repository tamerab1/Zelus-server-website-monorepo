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
		{42578, 1},                                     // Twisted bow (celestial)
		{42618, 1},                                     // Nightfall bow (celestial)
		{42600, 1}, {42602, 1}, {42604, 1},             // Zelus Relic hood / coat / legs
		{42594, 1}, {42598, 1},                         // Zelus Partyhat, Zelus Santa Hat
		{42568, 1}, {42570, 1}, {42572, 1}, {42576, 1}, // Emerald Draconies hood / platebody / platelegs / wings
		{42574, 1},                                     // Emerald Draconies Warhammer (EmeraldDraconiesWarhammer special)
		{59985, 1},                                     // Venomous Fire cape
		{42564, 1},                                     // Frostfall Wings
		{42596, 1},                                     // Zelus H'ween Mask
		{42610, 1},                                     // Zelus Cosmic Wings
		{59576, 1},                                     // Youtube Cape
		// boxes, pet shards and combat pets
		{42418, 3},                                     // Arcane mystery box
		{42592, 2},                                     // Rivals mystery box
		{6199, 3},                                      // Mystery box
		{42614, 1},                                     // Ascended Bestiary (empty)
		{42548, 10},                                    // Astral Spore
		{42616, 2000},                                  // Combat Pet Shard
		{42538, 1},                                     // Magma Skirmisher (T3)
		{42530, 1},                                     // Magmafist Smasher (T2)
		{42516, 1},                                     // Ironknuckle Brawler (T1)
		{42522, 1},                                     // Spectral Soulbinder (T1)
		{42542, 1},                                     // Starbound Invoker (T2)
		// endgame PvM / hybrid and Wilderness Forge upgrades
		{30777, 1}, {30779, 1}, {30781, 1},             // Radiant oathplate helm / chest / legs
		{42608, 1},                                     // Zelus Scythe of Vitur
		{59983, 1},                                     // Lunar Fire cape
		{42228, 1},                                     // Volcanic whip
		{27552, 1},                                     // Ghommal's avernic defender 6
		{42554, 1},                                     // Infernal Fury Gloves
		{60280, 1},                                     // Bond of the Sovereign Monarch (T1)
		{42180, 1},                                     // Bond of the Artisan (T2)
		{42161, 1},                                     // Bond of the Astral Archmage (T3)
		{42210, 1},                                     // Void max cape
		{42396, 1},                                     // Starweave boots
		{42324, 1},                                     // Bloodforged godsword
		{42326, 1},                                     // Bloodfang claws
		{42450, 1},                                     // Skyhunter bow
		{11212, 1000},                                  // Dragon arrow
		{42368, 1}, {42370, 1}, {42372, 1},             // Warbringer Bandos chestplate / tassets / boots
		{60400, 1},                                     // Draconic Pickaxe
		{42382, 1},                                     // Amulet of carnage
		{42346, 1},                                     // Warlord ring
		{42384, 1},                                     // Necklace of agony
		{30467, 1},                                     // $50 Donator bond
		{42448, 1},                                     // Grizzlemaw chainmace
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
