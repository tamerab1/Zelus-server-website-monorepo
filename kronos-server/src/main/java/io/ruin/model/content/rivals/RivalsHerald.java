package io.ruin.model.content.rivals;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;

/**
 * The Division Rivals Herald (npc 30608, 2026-10-17): the lobby's guide to the whole Rivals system. A clone of the
 * Rivals Overseer 3351 in the Zelus partyhat / relic outfit and Challenger cape (.dev/cache-restore-tool
 * build_rivals_herald.sh), spawned at 3365,3231 facing west (npcs/spawns/duel_arena.json).
 * <p>
 * Ops: Talk-to (greeting + topic menu), Explain Rewards (straight to the rewards topic), Quick-Guide (a short summary).
 * Every figure below is read from the live system, so keep them in step when it changes: RivalsProfile (steps,
 * checkpoint, points, weekly chests, rematch rule), RivalsShop (prices, division gates), RivalsMode (brackets),
 * RivalsItems.BOX (box tiers), ChallengeBell (cooldown), RivalsQueueManager (ready check, same-owner rule).
 */
public final class RivalsHerald {

	public static final int HERALD = 30608;

	private RivalsHerald() {
	}

	static void register() {
		NPCAction.register(HERALD, 1, RivalsHerald::talk);
		NPCAction.register(HERALD, 2, RivalsHerald::rewards);
		NPCAction.register(HERALD, 3, RivalsHerald::quickGuide);
	}

	private static void talk(Player player, NPC npc) {
		player.dialogue(
			new NPCDialogue(npc, "Greetings, warrior. I oversee the Division Rivals proving grounds. Step into the arena, "
				+ "prove your combat prowess and rise through the ranks to claim glorious spoils."),
			menu(player, npc));
	}

	private static OptionsDialogue menu(Player player, NPC npc) {
		return new OptionsDialogue("What would you like to know?",
			new Option("How does Division Rivals work?", () -> howItWorks(player, npc)),
			new Option("How do I climb divisions and earn points?", () -> climbing(player, npc)),
			new Option("What rewards can I earn?", () -> rewards(player, npc)),
			new Option("How do I challenge other players?", () -> challenging(player, npc)),
			new Option("Goodbye.", player::closeDialogue));
	}

	private static void howItWorks(Player player, NPC npc) {
		player.dialogue(
			new NPCDialogue(npc, "Division Rivals is ranked one-versus-one combat. Every match hands both fighters the "
				+ "same kit and the same levels, and nothing you own is ever at risk."),
			new NPCDialogue(npc, "It's a fair fight: your perks, pet perks, bonds and special attack discounts are all "
				+ "switched off in a match. Only skill decides it."),
			new NPCDialogue(npc, "There are four brackets: Endgame Prismatic, Dharok Risk-Fight with no overhead prayers, "
				+ "Wilderness Outlaw, and Maxed Pure at 75 Attack and 1 Defence."),
			new NPCDialogue(npc, "Use 'Configure layout' on a bracket's card to arrange that kit's inventory your way. "
				+ "It's saved and used in every match of that bracket."),
			new NPCDialogue(npc, "When an opponent is found, both of you accept within 15 seconds, then the countdown "
				+ "starts. A match lasts up to 10 minutes; if time runs out, whoever has more hitpoints wins."),
			new NPCDialogue(npc, "Logging out mid-match counts as a loss. When it ends, your own gear and stats come back "
				+ "exactly as they were."),
			menu(player, npc));
	}

	private static void climbing(Player player, NPC npc) {
		player.dialogue(
			new NPCDialogue(npc, "Divisions run from 10, Iron, up to 1, Champion, and beyond that lies Elite. Each "
				+ "division has " + RivalsProfile.STEPS + " steps: a win is one step up, and the "
				+ ordinal(RivalsProfile.STEPS) + " step promotes you."),
			new NPCDialogue(npc, "Reach step " + RivalsProfile.CHECKPOINT + " and that progress is secured: losses never "
				+ "drop you below it, and you're never demoted. Above it, a loss costs one step."),
			new NPCDialogue(npc, "Your skill rating starts at " + RivalsProfile.START_RATING + " and decides who you're "
				+ "matched against. The Elite ladder is ranked by it."),
			new NPCDialogue(npc, "Rivals Points: " + RivalsProfile.WIN_POINTS + " for a win, " + RivalsProfile.LOSS_POINTS
				+ " for a loss. Donator ranks add 5 to 25% on wins, and owning the Champion cape adds another "
				+ RivalsProfile.CAPE_BONUS_PERCENT + "%."),
			new NPCDialogue(npc, "Facing the same opponent again within 30 minutes is a friendly: it changes no rating, "
				+ "steps or points."),
			menu(player, npc));
	}

	private static void rewards(Player player, NPC npc) {
		player.dialogue(
			new NPCDialogue(npc, "Spend your points at the Overseer's Rewards Shop: Rivals mystery boxes, the Celestial "
				+ "ornament kit, titles, lamps, supplies, and the Rivals capes."),
			new NPCDialogue(npc, "The Challenger cape needs Division 5 or better. The Champion cape is for Elite fighters "
				+ "only. Neither is ever found in a box: they're earned by rank."),
			new NPCDialogue(npc, "Every week, " + RivalsProfile.TIER1_WINS + " wins unlocks a chest with "
				+ RivalsProfile.TIER1_POINTS + " points and a mystery box. " + RivalsProfile.TIER2_WINS + " wins unlocks "
				+ RivalsProfile.TIER2_POINTS + " points, two boxes and a chance at the Weekly Warlord title."),
			new NPCDialogue(npc, "The weekly chests reset every Monday. Claim them from the Division Rivals window."),
			new NPCDialogue(npc, "A Rivals mystery box has four tiers. Common: blood money and PK supplies. Uncommon: "
				+ "bigger blood money, ancient ice sacks, a granite clamp or a shortbow scroll."),
			new NPCDialogue(npc, "Rare: large blood money bags, PvP weapons, an ornate maul handle, dark bow paints or the "
				+ "ornament kit. Unique: the 'the Rival' title, a million blood money or an ancient relic."),
			menu(player, npc));
	}

	private static void challenging(Player player, NPC npc) {
		player.dialogue(
			new NPCDialogue(npc, "Speak to the Rivals Overseer and choose Matchmaking, then pick a bracket and press Find "
				+ "match. Type ::rivals anywhere to come back to this lobby."),
			new NPCDialogue(npc, "Want a fight now? Ring the Challenge Bell here in the lobby. Its chime carries across "
				+ "the whole server and summons worthy opponents to the arena."),
			new NPCDialogue(npc, "The bell can be rung once every 15 minutes. The leaderboard beside it shows the top "
				+ "Rivals, and the Overseer's Profile shows your own division."),
			menu(player, npc));
	}

	private static void quickGuide(Player player, NPC npc) {
		player.dialogue(
			new NPCDialogue(npc, "Quick guide: speak to the Overseer, choose Matchmaking, pick a bracket and find a match. "
				+ "Same kit for both fighters, no perks: pure skill."),
			new NPCDialogue(npc, "Win to climb from Division 10 to 1 and on to Elite. Earn points for every match, "
				+ "unlock weekly chests at " + RivalsProfile.TIER1_WINS + " and " + RivalsProfile.TIER2_WINS
				+ " wins, and ring the Challenge Bell to call out the server."),
			new NPCDialogue(npc, "Talk to me any time for the full details."));
	}

	private static String ordinal(int n) {
		return n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		});
	}
}
