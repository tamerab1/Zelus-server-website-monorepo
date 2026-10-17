package io.ruin.utility;

import io.ruin.cache.Icon;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.network.central.CentralClient;

import java.util.function.BiConsumer;

public enum Broadcast {

	FRIENDS(Icon.GREEN_INFO_BADGE, (player, message) -> CentralClient.sendGlobalMessage(player.getUserId(), message)),
	WORLD(Icon.BLUE_INFO_BADGE, (player, message) -> World.players().forEach(p -> {
		//If the player has toggle off announcement broadcasts don't send.
		if (!p.broadcastAnnouncements && (message.contains("Announcements") || message.contains("::Vote for"))) {
			return;
		}
		//If the player has toggle off tournament broadcasts don't send.
		if (!p.broadcastTournaments && message.contains("Tournament is starting")) {
			return;
		}
		if (!p.broadcastBossEvent && message.contains("Wilderness Event")) {
			return;
		}
		p.sendMessage(message);
	})),
	WORLD_NOTIFICATION(Icon.BLUE_INFO_BADGE, (player, message) -> World.players().forEach(p -> {
		p.sendMessage(message);
//        p.sendNotification(message);
	})),
	GLOBAL(Icon.YELLOW_INFO_BADGE, (player, message) -> CentralClient.sendGlobalMessage(-1, message));

	private final Icon newsIcon;


	private final BiConsumer<Player, String> consumer;

	Broadcast(Icon newsIcon, BiConsumer<Player, String> consumer) {
		this.newsIcon = newsIcon;
		this.consumer = consumer;
	}

	/**
	 * Send the given message as is.
	 */

	public void sendPlain(String message) {
		sendPlain(null, message);
	}

	public void sendPlain(Player player, String message) {
		//Player only required to be set when this == FRIENDS.
		consumer.accept(player, message);
	}

	/**
	 * Send the given message with a news portion added to it.
	 */

	public void sendNews(String message) {
		sendNews(null, null, null, message);
	}

	public void sendNews(Player player, String message) {
		sendNews(player, null, null, message);
	}

	public void sendNews(Icon overrideIcon, String title, String message) {
		sendNews(null, overrideIcon, title, message);
	}

	public void sendNews(Icon overrideIcon, String message) {
		sendNews(null, overrideIcon, null, message);
	}

	public void sendNews(Player player, Icon overrideIcon, String title, String message) {
		//Player only required to be set when this == FRIENDS.
		consumer.accept(player, (overrideIcon == null ? newsIcon.tag() : overrideIcon.tag()) + " "
			+ (title == null ? "" : "<col=7f0000><shad=000000>[" + title + "]:</shad></col> ") + "<col=000080>" + message + "</col>");
	}

	/**
	 * ::testbroadcasts: one sample of every kind of broadcast, sent ONLY to the given player, with the exact same
	 * formatting as the real thing (news line / drop-news line), so every icon can be checked without waiting.
	 */
	public static void preview(Player player) {
		player.sendMessage("<col=7f0000>--- Broadcast preview (only you see these) ---</col>");
		Object[][] news = {
			{Icon.PIT_TROPHY, "The Pit Championship", "Registration is open for The Pit Championship (Dharok's)! Type ::pit to join."},
			{Icon.WANTED_SKULL, "WANTED", "Someone placed a WANTED bounty of 5,000 BM on " + player.getName() + "!"},
			{Icon.WILDY_KEY, "Wilderness Key", player.getName() + " picked up a wilderness key at level 30!"},
			{Icon.WILDY_SKULL, "Wilderness", "The Revenant Maledictus has awoken in the Wilderness!"},
			{Icon.SERVER_EVENT, "Event", "The Halloween event has started!"},
			{Icon.SERVER_RATES, null, "Experience is now being doubled! (x2)"},
			{Icon.DONATOR_ICON, "Donation", player.getName() + " has claimed a donation! Thank you for the support!"},
			{Icon.BOND, "Bonds", player.getName() + " has redeemed a $25 bond!"},
			{Icon.VOTE_STAR, "Vote", "10 more votes until the Vote Boss spawns!"},
			{Icon.UPGRADE_GEM, "Upgrade", player.getName() + " has successfully forged a Bloodforged godsword!"},
			{Icon.SKILL_STAR, "Skills", player.getName() + " has just achieved level 99 Slayer!"},
			{Icon.NEWCOMER_SMILE, "Welcome", "Welcome " + player.getName() + " to Zelus!"},
			{Icon.ACHIEVEMENT_TROPHY, "Inferno", player.getName() + " has completed the Inferno!"},
			{Icon.ADMINISTRATOR, "Staff", "This is a staff broadcast."},
			{Icon.ANNOUNCEMENT2, "Info", "Did you know you can check your collection log with ::clog?"},
		};
		for (Object[] n : news)
			player.sendMessage(((Icon) n[0]).tag() + " " + (n[1] == null ? "" : "<col=7f0000><shad=000000>[" + n[1] + "]:</shad></col> ")
				+ "<col=000080>" + n[2] + "</col>");
		Object[][] drops = {
			{Icon.YELLOW_KEY, " received <shad=D80808>1x twisted bow</shad> reward from a Mystery Box!"},
			{Icon.RARE_DROP, " received a drop: <shad=D80808>Elysian sigil</shad> from Corporeal Beast!"},
			{Icon.COLLECTION_LOG, " has completed the Zulrah collection log!"},
			{Icon.PET_DROP, " received a pet drop: <shad=D80808>Pet snakeling</shad>!"},
		};
		for (Object[] d : drops)
			player.sendMessage(((Icon) d[0]).tag() + " [<shad=D80808>News</shad>] <col=000000>" + player.getName() + d[1]);
		player.sendMessage(Icon.PK_SWORDS.tag() + " " + io.ruin.cache.Color.PVP_RED.tag() + player.getName()
			+ " has ended Someone's killing spree of 10!");
		player.sendMessage(Icon.HCIM_DEATH.tag() + " " + io.ruin.cache.Color.RED.wrap(player.getName()
			+ " has died as a Hardcore Ironman with a total level of 1500!"));
	}

	public void sendNewsDropMessage(Player player, Icon overrideIcon, String title, String message) {
		String ironmanSymbol = "";
		if (player.getGameMode().isHardcoreGroupIronman()) {
			ironmanSymbol = "<img=42>";
		} else if (player.getGameMode().isHardcoreIronman()) {
			ironmanSymbol = "<img=10>";
		} else if (player.getGameMode().isGroupIronman()) {
			ironmanSymbol = "<img=41>";
		} else if (player.getGameMode().isUltimateIronman()) {
			ironmanSymbol = "<img=3>";
		} else if (player.getGameMode().isIronMan()) {
			ironmanSymbol = "<img=2>";
		} else {
			ironmanSymbol = "";
		}

		consumer.accept(player, "" + overrideIcon.tag() + ironmanSymbol + " [<shad=D80808>News</shad>] " +
			(title == null ? "" : "<shad=D80808>" + title + "</shad>") + message);
	}

}
