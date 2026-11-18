package io.ruin.model.activities.bosses.hueycoatl;

import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.NPCDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.dialogue.PlayerDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.actions.ItemNPCAction;
import io.ruin.model.var.VarPlayerRepository;

/**
 * The Darkfrost base camp below the Hueycoatl (OSRS wiki: Taala, Marla, Viero, Seer (The Darkfrost), Manauia, Worker
 * (The Darkfrost), 2026-11-14). Ids, options, examines and positions are the cache's / wiki's; the npcs are spawned from
 * npcs/spawns/Hueycoatl.json.
 * <ul>
 * <li>Chef / Marla and Guard / Viero are multinpcs on varbit huey_food_errand_state (11411): 0 neither named, 1 Marla
 * named (you've brought her Viero's request), 2+ Viero named. The errand: Viero wants raw sunlight antelope cooked with
 * redberries; Marla makes redberry antelope; Viero eats it (no reward). States: 1 asked Marla, 2 holding the dish /
 * Viero met, 3 Viero has eaten it.</li>
 * <li>The base-camp seer trades 1 Tome of Earth (empty) for 250 soiled pages.</li>
 * </ul>
 * NOTE: the wiki transcripts can't be quoted here in full, so most lines below follow the transcripts' content in our
 * words; option menus and the seer's trade prompts are the wiki's exact text.
 */
public final class HueyCamp {

	private HueyCamp() {
	}

	public static final int TAALA = 13996, SEER = 13997, CHEF = 14101, GUARD = 14102, WORKER_1 = 14003, WORKER_2 = 14004, MANAUIA = 14007;
	private static final int RAW_SUNLIGHT_ANTELOPE = 29116, REDBERRIES = 1951, REDBERRY_ANTELOPE = 30103, EGG = 1944, DAMP_EGG = 25571;
	private static final int TOME_EMPTY = 30066, SOILED_PAGE = 30068;

	public static final VarPlayerRepository FOOD_ERRAND = VarPlayerRepository.varpbit(11411, true);

	public static void register() {
		NPCAction.register(TAALA, 1, HueyCamp::taala);
		NPCAction.register(SEER, 1, HueyCamp::seer);
		NPCAction.register(CHEF, 1, HueyCamp::chef);
		NPCAction.register(GUARD, 1, HueyCamp::guard);
		NPCAction.register(MANAUIA, 1, (p, n) -> p.dialogue(
			new NPCDialogue(n, "Nilsal."),
			new NPCDialogue(n, "It is far too cold up here. I keep the dwarves happy, and still they claim they do all the work."),
			new NPCDialogue(n, "...Well. Perhaps they do some of it.")));
		NPCAction.register(WORKER_1, 1, (p, n) -> p.dialogue(
			new NPCDialogue(n, "The beast still rages in that mountain, but there's treasure in those tunnels."),
			new NPCDialogue(n, "The raw materials keep Cam Torum going, and anyone who helps gets a share.")));
		NPCAction.register(WORKER_2, 1, (p, n) -> p.dialogue(
			new NPCDialogue(n, "Poor Karlo... still trapped under all that rock."),
			new PlayerDialogue("For Karlo!")));
		ItemNPCAction.register(EGG, CHEF, (p, item, n) -> p.dialogue(new NPCDialogue(n, "An egg? Thank you, I'll make good use of it.")));
		ItemNPCAction.register(DAMP_EGG, CHEF, (p, item, n) -> p.dialogue(
			new NPCDialogue(n, "What have you done to this egg?"),
			new PlayerDialogue("I found it like this!"),
			new MessageDialogue("Marla is too busy cooking to hear you.")));
		ItemNPCAction.register(REDBERRY_ANTELOPE, CHEF, (p, item, n) -> p.dialogue(new NPCDialogue(n, "That's hardly gourmet cooking.")));
		ItemNPCAction.register(REDBERRY_ANTELOPE, GUARD, (p, item, n) -> guard(p, n));
		ItemNPCAction.register(RAW_SUNLIGHT_ANTELOPE, GUARD, (p, item, n) -> guard(p, n));
	}

	/* ------------------------------------------------------------------ Taala */

	private static void taala(Player p, NPC n) {
		p.dialogue(
			new NPCDialogue(n, "Welcome to the camp. What can I do for you?"),
			new OptionsDialogue(
				new Option("Remind me, what's going on here?", () -> p.dialogue(
					new NPCDialogue(n, "The Hueycoatl has awoken. It's coiled right through this mountain, and nobody knows how old it is."),
					new NPCDialogue(n, "It's dangerous, but it's also shaking all sorts of materials loose. We intend to collect them."))),
				new Option("How can I help?", () -> help(p, n)),
				new Option("Are there any rewards for helping?", () -> p.dialogue(
					new NPCDialogue(n, "Every time the beast moves it displaces materials and treasures. We gather them up,"),
					new NPCDialogue(n, "and you'll get a share for helping."))),
				new Option("Goodbye.")));
	}

	private static void help(Player p, NPC n) {
		if (p.getCombat().getLevel() < 85) {
			p.dialogue(
				new NPCDialogue(n, "I'm not sure you have the combat experience for this yet."),
				new MessageDialogue("The Hueycoatl is recommended for players with a combat level of at least 85 and a Prayer level of at least 43."));
			return;
		}
		p.dialogue(
			new NPCDialogue(n, "Your prayers will protect you from its shards. First, the exposed parts of its body need a helpful nudge"),
			new NPCDialogue(n, "to clear the way to the head. Then balance your prayers with the seer up top, and weaken the beast."),
			new PlayerDialogue("Move the body, balance the prayers, weaken the beast. Got it."));
	}

	/* ------------------------------------------------------------------ the seer's tome exchange */

	private static void seer(Player p, NPC n) {
		p.dialogue(new OptionsDialogue(
			new Option("What are you doing up here? It doesn't seem very safe.", () -> p.dialogue(
				new NPCDialogue(n, "The creature's divine energy interferes with prayer. Follow the dwarves' lead and you'll be fine."),
				new NPCDialogue(n, "If you have more questions, Taala is the one to ask."))),
			new Option("Could I trade some tomes in?", () -> trade(p, n)),
			new Option("Goodbye.")));
	}

	private static void trade(Player p, NPC n) {
		if (!p.getInventory().hasId(TOME_EMPTY)) {
			p.dialogue(new NPCDialogue(n, "You don't have any empty tomes of earth to trade."));
			return;
		}
		p.dialogue(new OptionsDialogue("Trade in 1x Tome of Earth (empty) for 250x soiled pages?",
			new Option("Yes.", () -> {
				if (!p.getInventory().hasId(TOME_EMPTY))
					return;
				p.getInventory().remove(TOME_EMPTY, 1);
				p.getInventory().addOrDrop(SOILED_PAGE, 250);
			}),
			new Option("No.")));
	}

	/* ------------------------------------------------------------------ Marla / Viero: the redberry antelope */

	private static void chef(Player p, NPC n) {
		int state = FOOD_ERRAND.get(p);
		if (state == 0) {
			p.dialogue(new NPCDialogue(n, "Need something? I keep everyone up here fed and watered. Go and have a drink of water."));
			return;
		}
		if (state >= 3) {
			p.dialogue(new OptionsDialogue(
				new Option("What are you doing up here?", () -> chefJob(p, n)),
				new Option("Viero thought the redberry antelope was 'interesting and complex'.", () -> p.dialogue(
					new NPCDialogue(n, "Interesting and complex? I'll take that as a compliment."))),
				new Option("I'm fine, thanks.")));
			return;
		}
		p.dialogue(new OptionsDialogue(
			new Option("What are you doing up here?", () -> chefJob(p, n)),
			new Option(state >= 2 ? "I need another redberry antelope." : "I have a request from the guard over there.", () -> request(p, n)),
			new Option(state >= 2 ? "I'm fine, thanks." : "No, never mind.")));
	}

	private static void chefJob(Player p, NPC n) {
		p.dialogue(new NPCDialogue(n, "Keeping this lot fed and hydrated. Nobody fights a mountain on an empty stomach."));
	}

	private static void request(Player p, NPC n) {
		if (!p.getInventory().hasId(RAW_SUNLIGHT_ANTELOPE) || !p.getInventory().hasId(REDBERRIES)) {
			p.dialogue(
				new NPCDialogue(n, "Sunlight antelope with redberries, is it? Fine, I'll indulge him."),
				new NPCDialogue(n, "Bring me a nice piece of raw sunlight antelope and some redberries, then."));
			return;
		}
		p.getInventory().remove(RAW_SUNLIGHT_ANTELOPE, 1);
		p.getInventory().remove(REDBERRIES, 1);
		p.getInventory().addOrDrop(REDBERRY_ANTELOPE, 1);
		if (FOOD_ERRAND.get(p) < 2)
			FOOD_ERRAND.set(p, 2);
		p.dialogue(new NPCDialogue(n, "There. It smells pretty good, but I'm not convinced. See what Viero thinks."));
	}

	private static void guard(Player p, NPC n) {
		int state = FOOD_ERRAND.get(p);
		if (state >= 3) {
			if (p.getInventory().hasId(REDBERRY_ANTELOPE)) {
				p.dialogue(new NPCDialogue(n, "No more for me, thanks. I've had quite enough."));
				return;
			}
			p.dialogue(new OptionsDialogue(
				new Option("What are you doing here?", () -> guardJob(p, n)),
				new Option("Would you like some more redberry antelope?", () -> p.dialogue(new NPCDialogue(n, "No, thank you. Once was plenty."))),
				new Option("Never mind.")));
			return;
		}
		if (p.getInventory().hasId(REDBERRY_ANTELOPE)) {
			p.getInventory().remove(REDBERRY_ANTELOPE, 1);
			FOOD_ERRAND.set(p, 3);
			p.dialogue(
				new NPCDialogue(n, "Is that the redberry antelope? Let's see... Hmm. Interesting and complex. Thank you."),
				new PlayerDialogue("Uh, usually there's a reward for doing this sort of thing."),
				new NPCDialogue(n, "You should be doing this out of kindness! ...Fine. You can have a bite."),
				new OptionsDialogue(new Option("Can I try some?"), new Option("Enjoy your meal.")));
			return;
		}
		if (state >= 1) {
			p.dialogue(new OptionsDialogue(
				new Option("What are you doing here?", () -> guardJob(p, n)),
				new Option("What did you want me to get you?", () -> p.dialogue(
					new NPCDialogue(n, "Raw sunlight antelope and redberries. Take them to Marla and have her cook them for me."))),
				new Option("Never mind.")));
			return;
		}
		if (p.getInventory().hasId(RAW_SUNLIGHT_ANTELOPE)) {
			p.dialogue(
				new NPCDialogue(n, "Is that raw sunlight antelope? Take it and some redberries to the chef and have her cook them. Will you?"),
				new OptionsDialogue(
					new Option("Yes.", () -> {
						FOOD_ERRAND.set(p, 1);
						p.dialogue(new PlayerDialogue("Alright, I'll see what she can do."));
					}),
					new Option("No.")));
			return;
		}
		guardJob(p, n);
	}

	private static void guardJob(Player p, NPC n) {
		p.dialogue(new NPCDialogue(n, "I protect the camp. You won't catch me climbing that mountain. I just wish the food was more adventurous."));
	}
}
