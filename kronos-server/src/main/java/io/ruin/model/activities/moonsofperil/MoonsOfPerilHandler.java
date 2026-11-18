package io.ruin.model.activities.moonsofperil;

import discord.webhooks.notifications.RareDropHook;
import io.ruin.api.utils.Random;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.cache.ObjType;
import io.ruin.model.activities.moonsofperil.bosses.BloodMoon;
import io.ruin.model.activities.moonsofperil.bosses.BlueMoon;
import io.ruin.model.activities.moonsofperil.bosses.EclipseMoon;
import io.ruin.model.activities.moonsofperil.bosses.MoonBoss;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.player.KillCounter;
import io.ruin.model.entity.player.Player;
import io.ruin.model.inter.dialogue.MessageDialogue;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.item.actions.ItemAction;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.MapListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;
import io.ruin.utility.Broadcast;
import io.ruin.utility.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.*;
import java.util.function.Function;

/**
 * Moons of Peril (Neypotzli). Gathering supplies, the three Moon fights (solo instances, see {@link MoonBoss}) and the
 * Lunar Chest. Loot per the OSRS wiki (2026-11-13):
 * <ul>
 * <li>Each Moon subdued gives an independent 1/56 roll for a piece of its own set (helm, chestplate, tassets, weapon),
 * with duplicate protection until that set is complete.</li>
 * <li>If no unique is rolled the standard table is rolled 1 / 3 / 6 times for 1 / 2 / 3 Moons.</li>
 * <li>At least one Moon must be subdued to open the chest, which resets each time it is opened.</li>
 * </ul>
 */
@Slf4j
public class MoonsOfPerilHandler {

	public enum Moon {
		BLOOD("Blood Moon", BloodMoon.ID, 5526, BloodMoon.SPAWN_X, BloodMoon.SPAWN_Y, BloodMoon.ENTRY_X, BloodMoon.ENTRY_Y,
			new Position(1413, 9632, 0), p -> p.bloodMoonKills, new int[] { 29028, 29022, 29025, 28997 }),
		BLUE("Blue Moon", BlueMoon.ID, 5783, BlueMoon.SPAWN_X, BlueMoon.SPAWN_Y, BlueMoon.ENTRY_X, BlueMoon.ENTRY_Y,
			new Position(1440, 9658, 0), p -> p.blueMoonKills, new int[] { 29019, 29013, 29016, 28988 }),
		ECLIPSE("Eclipse Moon", EclipseMoon.ID, 6038, EclipseMoon.SPAWN_X, EclipseMoon.SPAWN_Y, EclipseMoon.ENTRY_X,
			EclipseMoon.ENTRY_Y, new Position(1466, 9632, 0), p -> p.eclipseMoonKills, new int[] { 29010, 29004, 29007, 29000 });

		public final String name;
		public final int npcId, region, spawnX, spawnY, entryX, entryY;
		public final Position statue;
		public final Function<Player, KillCounter> kills;
		public final int[] set;

		Moon(String name, int npcId, int region, int spawnX, int spawnY, int entryX, int entryY, Position statue,
			Function<Player, KillCounter> kills, int[] set) {
			this.name = name;
			this.npcId = npcId;
			this.region = region;
			this.spawnX = spawnX;
			this.spawnY = spawnY;
			this.entryX = entryX;
			this.entryY = entryY;
			this.statue = statue;
			this.kills = kills;
			this.set = set;
		}
	}

	boolean bloodMoonDead = false;
	boolean eclipseMoonDead = false;
	boolean blueMoonDead = false;

	/* the fight in progress */
	private Moon fightMoon;
	private DynamicMap fightMap;
	private NPC fightNpc;

	static Bounds earthBoundCavernBounds = new Bounds(1345, 9663, 1406, 9725, 0);
	static Bounds streamBoundCavernBounds = new Bounds(1473, 9663, 1535, 9725, 0);
	static Bounds ancientPrisonBounds = new Bounds(1344, 9535, 1406, 9595, 0);
	static Bounds connectorBounds = new Bounds(1408, 9599, 1470, 9660, 1);
	static Bounds connectorBounds2 = new Bounds(1408, 9599, 1470, 9660, 0);
	static Bounds ancientShrineBound = new Bounds(1492, 9557, 1531, 9595, 0);

	public boolean initialized = false;

	static List<Integer> moonsOfPerilItems = Arrays.asList(
			29076, 29077, 29078, 29079, 29080, 29216, 29217, 28893, 29081, 29082, 29083);

	public void init() {
		initialized = true;
	}

	boolean isDead(Moon moon) {
		return switch (moon) {
			case BLOOD -> bloodMoonDead;
			case BLUE -> blueMoonDead;
			case ECLIPSE -> eclipseMoonDead;
		};
	}

	private void setDead(Moon moon) {
		switch (moon) {
			case BLOOD -> bloodMoonDead = true;
			case BLUE -> blueMoonDead = true;
			case ECLIPSE -> eclipseMoonDead = true;
		}
	}

	/* the HUD's checkmarks: pmoon_boss_blood_dead / _blue_dead / _eclipse_dead (rev 241 varbits) */
	private static final io.ruin.model.var.VarPlayerRepository V_BLOOD_DEAD = io.ruin.model.var.VarPlayerRepository.varpbit(9858, false),
		V_BLUE_DEAD = io.ruin.model.var.VarPlayerRepository.varpbit(9859, false),
		V_ECLIPSE_DEAD = io.ruin.model.var.VarPlayerRepository.varpbit(9860, false);

	void syncDead(Player player) {
		V_BLOOD_DEAD.set(player, bloodMoonDead ? 1 : 0);
		V_BLUE_DEAD.set(player, blueMoonDead ? 1 : 0);
		V_ECLIPSE_DEAD.set(player, eclipseMoonDead ? 1 : 0);
	}

	/** Neypotzli's HUD (pmoon_hud 869: which Moons are subdued), shown the whole time you're in the dungeon. */
	private static void entered(Player player) {
		player.getMoonsOfPerilHandler().syncDead(player);
		if (!player.isVisibleInterface(MoonBoss.HUD))
			player.openInterface(io.ruin.model.inter.ToplevelComponent.WILDERNESS_OVERLAY, MoonBoss.HUD);
	}

	/* ------------------------------------------------------------------ fights */

	void startFight(Player player, Moon moon) {
		if (fightMap != null) {
			player.sendMessage("You're already fighting a Moon.");
			return;
		}
		if (isDead(moon)) {
			player.sendMessage("The " + moon.name + " has already been subdued. Claim your reward from the Lunar Chest first.");
			return;
		}
		DynamicMap map;
		try {
			map = new DynamicMap().build(moon.region, 1);
		} catch (DynamicMap.DynamicMapBuildException e) {
			log.error("Unable to build the {} arena", moon.name, e);
			player.sendMessage("The way is blocked... try again in a moment.");
			return;
		}
		fightMoon = moon;
		fightMap = map;
		player.getMovement().teleport(map.convertX(moon.entryX), map.convertY(moon.entryY), 0);
		map.assignListener(player).onExit(this::fightExit);
		NPC npc = new NPC(moon.npcId).spawn(map.convertX(moon.spawnX), map.convertY(moon.spawnY), 0, 0);
		fightNpc = npc;
		player.set("MOON_NPC", npc);
		if (npc.getCombat() instanceof MoonBoss boss)
			boss.bind(player, map);
		npc.deathEndListener = (entity, killer, killHit) -> moonDefeated(player, moon);
	}

	private void moonDefeated(Player player, Moon moon) {
		setDead(moon);
		syncDead(player);
		moon.kills.apply(player).increment(player);
		if (fightNpc != null && fightNpc.getCombat() instanceof MoonBoss boss)
			boss.cleanup();
		player.getMovement().teleport(campAfter(moon)); // leaving the instance tears it down (fightExit)
		player.sendMessage("Eyatlalli's voice echoes through the surrounding earth...");
		boolean all = bloodMoonDead && blueMoonDead && eclipseMoonDead;
		player.sendMessage("<col=0000c0>Eyatlalli: Thank you. The " + moon.name + " of Peril is sufficiently distracted. "
			+ (all ? "You should now return to me." : "You should now proceed on to the next one.") + "</col>");
	}

	/* the campsite cooking stoves (pmoon_range 51362, 2x2), as placed in the cache's map */
	private static final int[][] STOVES = { { 1349, 9580 }, { 1374, 9710 }, { 1510, 9693 } }; // Ancient Prison, Earthbound, Streambound
	private static final int PRISON = 0, EARTHBOUND = 1, STREAMBOUND = 2;

	/**
	 * Wiki (Moons of Peril, "Defeat"): you're put by a campsite stove on the way to the next Moon. Eclipse -> Earthbound
	 * (Streambound if Blood is down); Blue -> Ancient Prison (Streambound if Blood is down); Blood -> Earthbound (Ancient
	 * Prison if Blue is down).
	 */
	private Position campAfter(Moon moon) {
		int camp = switch (moon) {
			case ECLIPSE -> bloodMoonDead ? STREAMBOUND : EARTHBOUND;
			case BLUE -> bloodMoonDead ? STREAMBOUND : PRISON;
			case BLOOD -> blueMoonDead ? PRISON : EARTHBOUND;
		};
		return besideStove(STOVES[camp][0], STOVES[camp][1]);
	}

	/** a walkable tile next to the stove, on whichever level the stove actually loaded (the Streambound one is a bridge loc) */
	private static Position besideStove(int x, int y) {
		int z = 0;
		for (int plane = 0; plane < 2; plane++)
			if (io.ruin.model.map.Tile.getObject(51362, x, y, plane) != null) {
				z = plane;
				break;
			}
		Position near = io.ruin.model.map.route.RouteFinder.findWalkable(x, y, z, 1, 3);
		return near != null ? near : new Position(x + 2, y, z);
	}

	private void fightExit(Player player, boolean logout) {
		Moon moon = fightMoon;
		endFight(player);
		if (logout && moon != null)
			player.getMovement().teleport(moon.statue);
	}

	private void endFight(Player player) {
		if (fightNpc != null) {
			NPC npc = fightNpc;
			fightNpc = null;
			if (npc.getCombat() instanceof MoonBoss boss)
				boss.cleanup();
			npc.remove();
		}
		player.remove("MOON_NPC");
		player.remove(BlueMoon.FROZEN_KEY);
		if (fightMap != null) {
			DynamicMap m = fightMap;
			fightMap = null;
			m.destroy();
		}
		fightMoon = null;
	}

	boolean inFight() {
		return fightMap != null;
	}

	void escape(Player player, boolean confirm) {
		Moon moon = fightMoon;
		if (moon == null)
			return;
		Runnable leave = () -> player.getMovement().teleport(moon.statue);
		if (!confirm) {
			leave.run();
			return;
		}
		player.dialogue(new MessageDialogue("Are you sure you want to leave the boss room?"),
			new OptionsDialogue(new Option("Yes.", leave), new Option("No.")));
	}

	/* ------------------------------------------------------------------ lunar chest */

	private static final int[][] STANDARD = { // id, min, max, weight /30, noted
		{ 28991, 72, 120, 5, 0 }, // Atlatl dart
		{ 29381, 160, 179, 2, 0 }, // Blessed bone shards
		{ 28899, 42, 54, 1, 1 }, // Wyrmling bones
		{ 29378, 6, 12, 3, 1 }, // Sun-kissed bones
		{ 1939, 79, 119, 4, 0 }, // Swamp tar
		{ 571, 30, 45, 2, 1 }, // Water orb
		{ 6034, 6, 12, 3, 1 }, // Supercompost
		{ 1761, 15, 25, 3, 1 }, // Soft clay
		{ 205, 12, 18, 3, 1 }, // Grimy harralander
		{ 209, 12, 18, 1, 1 }, // Grimy irit leaf
		{ 5314, 1, 2, 2, 0 }, // Maple seed
		{ 5315, 1, 1, 1, 0 }, // Yew seed
	};

	private static Item rollStandard() {
		int roll = Random.get(0, 29);
		for (int[] e : STANDARD) {
			roll -= e[3];
			if (roll < 0) {
				int id = e[0];
				if (e[4] == 1) {
					ObjType def = ObjType.get(id);
					if (def != null && def.notedId != -1)
						id = def.notedId;
				}
				return new Item(id, Random.get(e[1], e[2]));
			}
		}
		return new Item(28991, 72);
	}

	private static int rollUnique(Player player, Moon moon) {
		if (Random.get(1, 56) != 1)
			return -1;
		List<Integer> missing = new ArrayList<>();
		for (int id : moon.set) {
			Integer got = player.uniqueDrops.get(id);
			if (got == null || got < 1)
				missing.add(id);
		}
		if (missing.isEmpty()) // set complete: duplicate protection lifts
			return moon.set[Random.get(0, moon.set.length - 1)];
		return missing.get(Random.get(0, missing.size() - 1));
	}

	void lootShrine(Player player) {
		if (inFight())
			return;
		List<Moon> subdued = new ArrayList<>();
		for (Moon m : Moon.values())
			if (isDead(m))
				subdued.add(m);
		if (subdued.isEmpty()) {
			player.sendMessage("You must subdue at least one of the Moons of Peril before the Lunar Chest will open.");
			return;
		}
		player.moonsOfPerilKills.increment(player);
		List<Item> rewards = new ArrayList<>();
		for (Moon m : subdued) {
			int unique = rollUnique(player, m);
			if (unique != -1)
				rewards.add(new Item(unique, 1));
		}
		boolean gotUnique = !rewards.isEmpty();
		if (!gotUnique) {
			int rolls = subdued.size() == 1 ? 1 : subdued.size() == 2 ? 3 : 6;
			for (int i = 0; i < rolls; i++)
				rewards.add(rollStandard());
		}
		bloodMoonDead = blueMoonDead = eclipseMoonDead = false;
		syncDead(player);
		player.animate(536);
		for (Item item : rewards) {
			player.getInventory().addOrDrop(item.getId(), item.getAmount());
			player.addToCollectionLog(item);
			player.sendMessage("Lunar Chest: <col=ef1020>" + Utils.formatMoneyString(item.getAmount()) + " x " + item.getDef().name);
		}
		if (gotUnique) {
			for (Item item : rewards) {
				Broadcast.WORLD.sendNewsDropMessage(player, Icon.RARE_DROP, "<col=000000>" + player.getName(),
					" just received " + item.getDef().descriptiveName + " from the Lunar Chest!");
				RareDropHook.sendDiscordMessage(() -> {
					var json = new JSONObject();
					json.put("player", player.getName());
					json.put("game_mode", player.getGameMode());
					json.put("item_id", item.getId());
					json.put("item_name", item.getDef().name);
					json.put("source", "Lunar Chest");
					json.put("total_attempts", Utils.formatMoneyString(player.moonsOfPerilKills.getKills()));
					return json;
				});
			}
		}
	}

	public void moveToLobby(Player player) {
		player.getMovement().teleport(1440, 9615, 1);
	}

	private static void exited(Player player, boolean logout) {
		MoonsOfPerilHandler h = player.getMoonsOfPerilHandler();
		if (h.inFight())
			return; // stepping into a Moon's arena instance isn't leaving Neypotzli
		if (player.isVisibleInterface(MoonBoss.HUD))
			player.closeInterface(io.ruin.model.inter.ToplevelComponent.WILDERNESS_OVERLAY);
		player.getStats().restore(true);
		h.confiscateItems(player);
		player.sendMessage("You have left the Moons of Peril minigame.");
	}

	public static void register() {
		// the combat npcs (pmoon_boss_*_moon) carry no name of their own: the client shows the multivar "_vis" child
		io.ruin.cache.NPCType.get(BloodMoon.ID).name = "Blood Moon";
		io.ruin.cache.NPCType.get(BlueMoon.ID).name = "Blue Moon";
		io.ruin.cache.NPCType.get(EclipseMoon.ID).name = "Eclipse Moon";
		// ONE listener over every part of Neypotzli: walking from one cavern into the next isn't leaving the dungeon
		// (each part used to have its own listener, so every passage confiscated your moonlight potions and food)
		MapListener.registerBounds(earthBoundCavernBounds, streamBoundCavernBounds, ancientPrisonBounds, connectorBounds,
				connectorBounds2, ancientShrineBound)
				.onEnter(MoonsOfPerilHandler::entered)
				.onExit(MoonsOfPerilHandler::exited);
		ItemAction.registerInventory(28893, "Release", (player, item) -> {
			player.getMoonsOfPerilHandler().releaseMoonlightMoth(player);
		});
		ItemItemAction.register(ItemID.PESTLE_AND_MORTAR, 29078, (player, primary, secondary) -> {
			player.getMoonsOfPerilHandler().crushMoonlightGrub(player);
		});
		ItemItemAction.register(ItemID.VIAL_OF_WATER, 29079, (player, primary, secondary) -> {
			player.getMoonsOfPerilHandler().mixMoonlightPotion(player);
		});
		NPCAction.register(12772, "catch", (player, npc) -> player.getMoonsOfPerilHandler().catchMoonlightMoth(player));
		BlueMoon.register();
	}

	void catchMossLizard(Player player) {
		if (!player.getInventory().hasRoomFor(29076)) {
			player.sendMessage("You need more inventory space to catch a moss lizard.");
			return;
		}
		if (player.getInventory().hasId(954)) {
			player.startEvent(e -> {
				player.animate(832);
				player.sendFilteredMessage("You lay the trap...");
				e.delay(1);
				player.getInventory().add(29076, 1);
				player.getStats().addXp(StatType.Hunter, 108, true);
				player.sendFilteredMessage("You catch a moss lizard.");
			});
		} else {
			player.sendMessage("You need a rope to use this trap.");
		}
	}

	void cookMossLizard(Player player) {
		if (player.getInventory().hasId(29076)) {
			player.startEvent(e -> {
				while (player.getInventory().hasId(29076)) {
					player.animate(897);
					e.delay(1);
					player.getInventory().remove(29076, 1);
					player.getInventory().addOrDrop(29077, Random.get(1, 3));
					player.getStats().addXp(StatType.Cooking, 100, true);
					player.sendFilteredMessage("You cook a cooked moss lizard.");
				}
			});
		}
	}

	private void catchMoonlightMoth(Player player) {
		if (player.getInventory().hasId(ItemID.BUTTERFLY_NET) && player.getInventory().hasId(ItemID.BUTTERFLY_JAR)) {
			if (player.getStats().get(StatType.Hunter).currentLevel < 75) {
				player.sendMessage("You need a Hunter level of 75 to catch a moonlight moth.");
				return;
			}
			player.startEvent(e -> {
				player.animate(6605);
				player.sendFilteredMessage("You catch a moonlight moth.");
				e.delay(1);
				player.getInventory().remove(ItemID.BUTTERFLY_JAR, 1);
				player.getInventory().add(28893, 1);
				player.getStats().addXp(StatType.Hunter, 108, true);
			});
		} else {
			if (player.getStats().get(StatType.Hunter).currentLevel < 85) {
				player.sendMessage("You need a Hunter level of 85 to catch a moonlight moth barehanded.");
				return;
			}
			player.startEvent(e -> {
				player.animate(7171);
				player.sendFilteredMessage("You catch a moonlight moth.");
				e.delay(1);
				Stat prayer = player.getStats().get(StatType.Prayer);
				prayer.alter(Math.min(prayer.fixedLevel, prayer.currentLevel + 22));
				player.getStats().addXp(StatType.Hunter, 108, true);
			});
		}
	}

	void pickMoonlightGrub(Player player) {
		player.startEvent(e -> {
			player.animate(2280);
			player.sendFilteredMessage("You pick a moonlight grub.");
			e.delay(2);
			player.getInventory().add(29078, 1);
			player.getStats().addXp(StatType.Hunter, 5, true);
		});
	}

	private void crushMoonlightGrub(Player player) {
		if (player.getInventory().hasId(ItemID.PESTLE_AND_MORTAR)) {
			player.startEvent(e -> {
				while (player.getInventory().hasId(29078)) {
					player.animate(364);
					player.sendFilteredMessage("You crush the moonlight grub...");
					e.delay(1);
					player.getInventory().remove(29078, 1);
					player.getInventory().add(29079, 1);
					player.getStats().addXp(StatType.Herblore, 5, true);
					player.sendFilteredMessage("You crush a moonlight grub.");
				}
			});
		}
	}

	private void mixMoonlightPotion(Player player) {
		if (player.getInventory().hasId(29079) && player.getInventory().hasId(ItemID.VIAL_OF_WATER)) {
			if (player.getStats().get(StatType.Herblore).currentLevel < 38) {
				player.sendMessage("You need a Herblore level of 38 to mix a moonlight potion.");
				return;
			}
			player.startEvent(e -> {
				while (player.getInventory().hasId(29079) && player.getInventory().hasId(ItemID.VIAL_OF_WATER)) {
					player.animate(363);
					player.sendFilteredMessage("You mix the moonlight potion...");
					e.delay(1);
					player.getInventory().remove(29079, 1);
					player.getInventory().remove(ItemID.VIAL_OF_WATER, 1);
					player.getInventory().add(29080, 1);
					player.getStats().addXp(StatType.Herblore, 5, true);
					player.sendFilteredMessage("You mix a moonlight potion.");
				}
			});
		}
	}

	void fishBream(Player player) {
		if (player.getInventory().hasId(ItemID.BIG_FISHING_NET)) {
			if (player.getStats().get(StatType.Fishing).currentLevel < 20) {
				player.sendMessage("You need a Fishing level of 20 to fish here.");
				return;
			}
			player.startEvent(e -> {
				while (player.getInventory().hasRoomFor(29216)) {
					player.animate(621);
					e.delay(2);
					player.getInventory().add(29216, 1);
					player.getStats().addXp(StatType.Fishing, 50, true);
				}
			});
		} else {
			player.sendMessage("You need a big fishing net to fish here.");
		}
	}

	void cookBream(Player player) {
		if (player.getInventory().hasId(29216)) {
			if (player.getStats().get(StatType.Cooking).currentLevel < 30) {
				player.sendMessage("You need a Cooking level of 30 to cook bream.");
				return;
			}
			player.startEvent(e -> {
				while (player.getInventory().hasId(29216)) {
					player.animate(896);
					e.delay(1);
					player.getInventory().remove(29216, 1);
					player.getInventory().addOrDrop(29217, 1);
					player.getStats().addXp(StatType.Cooking, 50, true);
					player.sendFilteredMessage("You cook a cooked bream.");
				}
			});
		}
	}

	private void releaseMoonlightMoth(Player player) {
		if (player.getInventory().hasId(28893)) {
			player.startEvent(e -> {
				player.animate(7172);
				player.sendFilteredMessage("You release the moonlight moth.");
				e.delay(1);
				player.getInventory().remove(28893, 1);
				Stat prayer = player.getStats().get(StatType.Prayer);
				prayer.alter(Math.min(prayer.fixedLevel, prayer.currentLevel + 22));
			});
		}
	}

	private void confiscateItems(Player player) {
		boolean confiscated = false;
		for (int i = 0; i < 28; i++) {
			if (player.getInventory().get(i) != null && moonsOfPerilItems.contains(player.getInventory().get(i).getId())) {
				int id = player.getInventory().get(i).getId();
				if (id >= 29080 && id <= 29083) // wiki: a moonlight potion becomes a vial of water
					player.getInventory().get(i).setId(227);
				else
					player.getInventory().get(i).remove();
				confiscated = true;
			}
		}
		if (confiscated) {
			player.sendMessage("The resources from Neypotzli fizzle to dust as they are severed from its magic.");
		}
	}
}
