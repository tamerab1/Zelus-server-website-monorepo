package io.ruin.model.content.capeforge;

import io.ruin.Server;
import io.ruin.cache.ObjType;
import io.ruin.model.content.cosmetic.Stardust;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.InterfaceHandler;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.actions.SimpleAction;
import io.ruin.model.item.Item;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.network.PacketSender;
import io.ruin.services.Loggers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Zelus Cape Forge (interface 1167, built by .dev/cache-restore-tool BuildCapeForgeInterface): turns an Infernal max cape,
 * an Infernal cape or a Fire cape into a themed variant (Void / Glacial / Cosmic, and Venomous / Lunar / Umbral Fire)
 * for Tokkul + a themed catalyst. Left: large model preview, the
 * description and the 3 requirements (green = owned, red = missing); right: the 3x3 style grid and Infuse (click twice
 * to confirm). Opened from the Zelus Upgrader (object 60015) "Cape Forge" option and ::capeforge.
 * <p>
 * The variants keep the base cape's stats (item_info.json entries already exist). Costs below are first-pass
 * placeholders.
 */
public final class CapeForge {

	public static final int INTERFACE = 1167;
	public static final int UPGRADER_OBJECT = 60015;
	/** "Cape Forge Master" (was 'Lecherous' Lee), spawned at home 3095,3504 (build_cape_forge_master.sh dresses it). */
	public static final int FORGE_MASTER = 3873;
	private static final int CLOSE = 2, TITLE = 3, PREVIEW_NAME = 9, MODEL = 14, DESC = 15, FIRST_REQ = 18,
		REQ_STRIDE = 4, FIRST_CARD = 36, CARD_STRIDE = 8, STATUS = 108, INFUSE = 109;
	/** The preview is 132px tall, an inventory icon 32: zoom in by roughly that much. */
	private static final double PREVIEW_ZOOM_SCALE = 0.32;
	private static final int CONFIRM_TICKS = 10, SUCCESS_SOUND = 2401;

	private static final int FIRE_CAPE = 6570, INFERNAL_CAPE = 21295, INFERNAL_MAX_CAPE = 21285, INFERNAL_MAX_HOOD = 21282;
	private static final int TOKKUL = 6529, CRYSTAL_SHARD = 23962, ANCIENT_SHARD = 19677, ZULRAHS_SCALES = 12934,
		STARDUST = 25527, NIHIL_SHARD = 26231;
	/** The themed max hoods (MaxCape.MaxCapes GLACIAL / COSMIC / VOID), handed out with the themed max capes. */
	private static final int GLACIAL_MAX_HOOD = 31412, COSMIC_MAX_HOOD = 31414, VOID_MAX_HOOD = 31416;

	private static final String GREEN = "<col=00ff00>", RED = "<col=ff5050>", WHITE = "<col=ffffff>", GREY = "<col=9a9a9a>",
		GOLD = "<col=ffc35a>", END = "</col>";
	private static final String TAG = "<col=b85900><shad=000000>[Cape Forge]:</shad></col> ";

	/** Grid order = enum order: row 1 themed max capes, row 2 themed Infernal capes, row 3 themed Fire capes. */
	enum Recipe {
		VOID_MAX(31410, VOID_MAX_HOOD, "Void<br>Max cape", INFERNAL_MAX_CAPE,
			"Cut from the space between worlds. Same stats as an Infernal max cape; comes with its hood.",
			new Item(TOKKUL, 250_000), new Item(NIHIL_SHARD, 3_500)),
		GLACIAL_MAX(31406, GLACIAL_MAX_HOOD, "Glacial<br>Max cape", INFERNAL_MAX_CAPE,
			"Living ice flowing like lava. Same stats as an Infernal max cape; comes with its hood.",
			new Item(TOKKUL, 250_000), new Item(CRYSTAL_SHARD, 750)),
		COSMIC_MAX(31408, COSMIC_MAX_HOOD, "Cosmic<br>Max cape", INFERNAL_MAX_CAPE,
			"A swirl of starlight and nebula. Same stats as an Infernal max cape; comes with its hood.",
			new Item(TOKKUL, 250_000), new Item(STARDUST, 7_500)),
		VOID(31404, -1, "Void<br>Cape", INFERNAL_CAPE, "Cut from the space between worlds. Same stats as an Infernal cape.",
			new Item(TOKKUL, 200_000), new Item(NIHIL_SHARD, 2_500)),
		GLACIAL(31400, -1, "Glacial<br>Cape", INFERNAL_CAPE, "Living ice flowing like lava. Same stats as an Infernal cape.",
			new Item(TOKKUL, 200_000), new Item(CRYSTAL_SHARD, 500)),
		COSMIC(31402, -1, "Cosmic<br>Cape", INFERNAL_CAPE, "A swirl of starlight and nebula. Same stats as an Infernal cape.",
			new Item(TOKKUL, 200_000), new Item(STARDUST, 5_000)),
		VENOMOUS_FIRE(59985, -1, "Venomous<br>Fire cape", FIRE_CAPE, "A fire cape steeped in Zulrah's venom. Same stats as a Fire cape.",
			new Item(TOKKUL, 50_000), new Item(ZULRAHS_SCALES, 25_000)),
		LUNAR_FIRE(59983, -1, "Lunar<br>Fire cape", FIRE_CAPE, "A fire cape burning with silver moonlight. Same stats as a Fire cape.",
			new Item(TOKKUL, 50_000), new Item(CRYSTAL_SHARD, 100)),
		UMBRAL_FIRE(59984, -1, "Umbral<br>Fire cape", FIRE_CAPE, "A fire cape smouldering with blood and shadow. Same stats as a Fire cape.",
			new Item(TOKKUL, 50_000), new Item(ANCIENT_SHARD, 50));

		final int result, hood, base;
		final String label, description;
		final Item[] catalysts;

		Recipe(int result, int hood, String label, int base, String description, Item... catalysts) {
			this.result = result;
			this.hood = hood;
			this.label = label;
			this.base = base;
			this.description = description;
			this.catalysts = catalysts;
		}

		/** Base cape first, then the catalysts: the 3 requirement slots. */
		Item[] requirements() {
			Item[] all = new Item[catalysts.length + 1];
			all[0] = new Item(base, 1);
			System.arraycopy(catalysts, 0, all, 1, catalysts.length);
			return all;
		}
	}

	private static final Recipe[] RECIPES = Recipe.values();

	private static final class View {
		int selected;
		long confirmUntilTick = -1;
	}

	private static final Map<String, View> views = new HashMap<>();

	private CapeForge() {
	}

	public static void register() {
		InterfaceHandler.register(INTERFACE, h -> {
			h.actions[CLOSE] = (SimpleAction) Player::closeInterfaces;
			for (int g = 0; g < RECIPES.length; g++) {
				int index = g;
				h.actions[FIRST_CARD + g * CARD_STRIDE] = (SimpleAction) p -> select(p, index);
			}
			for (int r = 0; r < 3; r++) {
				int index = r;
				h.actions[FIRST_REQ + r * REQ_STRIDE + 2] = (SimpleAction) p -> examineRequirement(p, index);
			}
			h.actions[INFUSE] = (SimpleAction) CapeForge::infuse;
			h.closedAction = (p, i) -> {
				if (i != INTERFACE)
					views.remove(p.uuid());
			};
		});
		ObjectAction.register(UPGRADER_OBJECT, 3, (player, obj) -> open(player));
		// the Cape Forge Master at home (NPC 3873, cache ops Talk-to / Upgrade)
		io.ruin.model.entity.npc.NPCAction.register(FORGE_MASTER, 1, (player, npc) -> talk(player, npc));
		io.ruin.model.entity.npc.NPCAction.register(FORGE_MASTER, 2, (player, npc) -> open(player));
	}

	private static void talk(Player player, io.ruin.model.entity.npc.NPC npc) {
		player.dialogue(
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "Greetings! I am the Cape Forge Master. Bring me a Fire cape, an "
				+ "Infernal cape or an Infernal max cape and I'll reforge its flames into something rarer."),
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "Each style needs Tokkul and a catalyst: crystal shards, ancient shards, "
				+ "Zulrah's scales, stardust or Nihil shards. The original cape is sacrificed in the forge."),
			new io.ruin.model.inter.dialogue.NPCDialogue(npc, "The new cape keeps the stats of the one you gave me. Max capes come "
				+ "back with a matching hood, too."),
			new io.ruin.model.inter.dialogue.OptionsDialogue("Open the Cape Forge?",
				new io.ruin.model.inter.utils.Option("Yes, show me the styles.", () -> open(player)),
				new io.ruin.model.inter.utils.Option("Maybe later.")));
	}

	public static void open(Player player) {
		View v = view(player);
		// start on the first style the player can actually make, if any
		for (int g = 0; g < RECIPES.length; g++) {
			if (player.getInventory().contains(RECIPES[g].base, 1)) {
				v.selected = g;
				break;
			}
		}
		player.openInterface(ToplevelComponent.MAINMODAL, INTERFACE);
		draw(player);
	}

	private static View view(Player player) {
		return views.computeIfAbsent(player.uuid(), k -> new View());
	}

	private static void select(Player player, int index) {
		View v = view(player);
		v.selected = index;
		v.confirmUntilTick = -1;
		draw(player);
	}

	private static void draw(Player player) {
		PacketSender ps = player.getPacketSender();
		View v = view(player);
		Recipe recipe = RECIPES[v.selected];
		ObjType result = ObjType.get(recipe.result);
		ps.sendString(INTERFACE, TITLE, "Zelus Cape Forge");

		ps.sendString(INTERFACE, PREVIEW_NAME, result.name);
		ps.sendItem(INTERFACE, MODEL, recipe.result, 1);
		ps.sendModelInformation(INTERFACE, MODEL, (int) (result.zoom2d * PREVIEW_ZOOM_SCALE), result.xan2d, result.yan2d);
		ps.sendString(INTERFACE, DESC, recipe.description);

		Item[] reqs = recipe.requirements();
		boolean ready = true;
		for (int r = 0; r < 3; r++) {
			int slot = FIRST_REQ + r * REQ_STRIDE;
			boolean used = r < reqs.length;
			for (int part = 0; part < REQ_STRIDE; part++)
				ps.setHidden(INTERFACE, slot + part, !used);
			if (!used)
				continue;
			Item req = reqs[r];
			int have = player.getInventory().getAmount(req.getId());
			boolean ok = have >= req.getAmount();
			ready &= ok;
			ps.sendItem(INTERFACE, slot + 2, req.getId(), req.getAmount());
			ps.sendString(INTERFACE, slot + 3, (ok ? GREEN : RED) + shortAmount(Math.min(have, req.getAmount())) + "/"
				+ shortAmount(req.getAmount()) + END);
		}

		for (int g = 0; g < RECIPES.length; g++) {
			int card = FIRST_CARD + g * CARD_STRIDE;
			boolean selected = g == v.selected;
			for (int part = 2; part <= 5; part++)
				ps.setHidden(INTERFACE, card + part, !selected);
			ps.sendItem(INTERFACE, card + 6, RECIPES[g].result, 1);
			ps.sendString(INTERFACE, card + 7, (selected ? GOLD : "") + RECIPES[g].label + (selected ? END : ""));
		}

		String status;
		if (ready)
			status = GREEN + "You have everything needed." + END + "<br>" + GREY + "Your "
				+ ObjType.get(recipe.base).name + " will be consumed." + END;
		else
			status = RED + "Missing materials (shown in red)." + END + "<br>" + GREY + "Requirements must be in your inventory."
				+ END;
		ps.sendString(INTERFACE, STATUS, status);
		boolean armed = Server.currentTick() <= v.confirmUntilTick;
		ps.sendString(INTERFACE, INFUSE + 1, armed ? GOLD + "Confirm infusion?" + END : "Infuse / Recolour");
	}

	private static void examineRequirement(Player player, int index) {
		Item[] reqs = RECIPES[view(player).selected].requirements();
		if (index >= reqs.length)
			return;
		ObjType def = ObjType.get(reqs[index].getId());
		player.sendMessage(TAG + fmt(reqs[index].getAmount()) + " x " + def.name + " (you have "
			+ fmt(player.getInventory().getAmount(reqs[index].getId())) + ").");
	}

	/** First click arms the button, a second click within 6 seconds infuses. Everything is re-checked on the second. */
	private static void infuse(Player player) {
		View v = view(player);
		Recipe recipe = RECIPES[v.selected];
		for (Item req : recipe.requirements()) {
			if (player.getInventory().getAmount(req.getId()) < req.getAmount()) {
				player.sendMessage(TAG + "<col=7f0000>You need " + fmt(req.getAmount()) + " x " + ObjType.get(req.getId()).name
					+ " in your inventory.</col>");
				v.confirmUntilTick = -1;
				draw(player);
				return;
			}
		}
		// a max cape also hands out its hood: it replaces a carried Infernal max hood, otherwise it needs a free slot
		boolean swapHood = recipe.hood != -1 && player.getInventory().contains(INFERNAL_MAX_HOOD, 1);
		if (recipe.hood != -1 && !swapHood && player.getInventory().getFreeSlots() < 1) {
			player.sendMessage(TAG + "<col=7f0000>You need a free inventory slot for the hood.</col>");
			v.confirmUntilTick = -1;
			draw(player);
			return;
		}
		if (Server.currentTick() > v.confirmUntilTick) {
			v.confirmUntilTick = Server.currentTick() + CONFIRM_TICKS;
			draw(player);
			return;
		}
		v.confirmUntilTick = -1;
		if (io.ruin.model.content.pvppreset.SpawnedItems.refuseIfSpawned(player, "infused",
				java.util.Arrays.stream(recipe.requirements()).mapToInt(Item::getId).toArray())) {
			draw(player);
			return;
		}
		// all removed in this tick before the cape is added; the base cape slot is reused so space is never an issue
		for (Item req : recipe.requirements())
			player.getInventory().remove(req.getId(), req.getAmount());
		player.getInventory().add(recipe.result, 1);
		if (recipe.hood != -1) {
			if (swapHood)
				player.getInventory().remove(INFERNAL_MAX_HOOD, 1);
			player.getInventory().add(recipe.hood, 1);
		}
		Loggers.logBond(player.getUserId(), player.getName(), player.getIp(), "Cape Forge: "
			+ ObjType.get(recipe.base).name + " -> " + ObjType.get(recipe.result).name);
		player.animate(899);
		Stardust.celebrate(player);
		player.privateSound(SUCCESS_SOUND);
		player.sendMessage(TAG + "<col=006600>You infuse your " + ObjType.get(recipe.base).name + " into a "
			+ ObjType.get(recipe.result).name + "!</col>");
		draw(player);
	}

	private static String shortAmount(int amount) {
		if (amount >= 1_000_000)
			return trim(amount / 1_000_000.0) + "m";
		if (amount >= 10_000)
			return trim(amount / 1_000.0) + "k";
		return fmt(amount);
	}

	private static String trim(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	private static String fmt(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
