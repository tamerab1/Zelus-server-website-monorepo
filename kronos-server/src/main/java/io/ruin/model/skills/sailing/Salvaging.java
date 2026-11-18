package io.ruin.model.skills.sailing;

import io.ruin.Server;
import io.ruin.model.World;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.stat.StatType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shipwreck salvaging, built from the OSRS wiki ("Shipwreck salvaging" and each shipwreck's page, 2026-11-14) and the
 * rev 241 cache:
 * <ul>
 * <li>Shipwrecks are world locs at sea (60464.. even ids, "Inspect" only); each tier has a sunk variant (odd id). They
 * sit in clusters; about 4 of every 6 sites are up at once. Starting to salvage a wreck starts its timer; when it runs
 * out the wreck sinks ("You salvage all you can from the shipwreck before it is reclaimed by the sea.") and another
 * site in the cluster rises. Wrecks don't cycle unless someone has salvaged them.</li>
 * <li>Salvaging is done with the boat's salvaging hook facility (op1 "Deploy", locs 60483-60489 on a raft) next to a
 * wreck. The player rolls every 4 ticks; the chance is the wiki's skilling-success curve for the hook tier:
 * {@code (floor(low*(99-L)/98 + high*(L-1)/98 + 0.5) + 1) / 256}. A success gives one salvage and the wreck's XP.</li>
 * <li>Salvage is sorted at a salvaging station ("Sort-salvage", pier locs 60462/60463) for the sorting XP and a roll on
 * the salvage's table ({@link SalvageLoot}).</li>
 * <li>Hook anims (1x3 raft) from the cache: player drop 13569 / salvaging 13596 / reset 13571; hook loc drop 13566 /
 * salvaging 13595 / reset 13568 / idle 13565.</li>
 * </ul>
 */
@Slf4j
public final class Salvaging {

	private Salvaging() {
	}

	/** Hook tiers: raft hook loc = 60483 + tier (facility rows 8434-8440: Sailing 15/21/27/44/59/74/86). */
	public enum Hook {
		BRONZE(15), IRON(21), STEEL(27), MITHRIL(44), ADAMANT(59), RUNE(74), DRAGON(86);

		public final int level;

		Hook(int level) {
			this.level = level;
		}

		public int raftLoc() {
			return 60483 + ordinal();
		}

		static Hook forRaftLoc(int loc) {
			int i = loc - 60483;
			return i >= 0 && i < values().length ? values()[i] : null;
		}
	}

	record Cluster(String name, boolean hazardous, int[][] sites) {
	}

	private static Cluster c(String name, int... xy) {
		return c(name, false, xy);
	}

	private static Cluster c(String name, boolean hazardous, int... xy) {
		int[][] sites = new int[xy.length / 2][];
		for (int i = 0; i < sites.length; i++)
			sites[i] = new int[]{xy[i * 2], xy[i * 2 + 1]};
		return new Cluster(name, hazardous, sites);
	}

	/**
	 * The eight shipwreck tiers. Level, salvage XP, sort XP, salvage item, locs, how long a wreck lasts once salvaging
	 * starts (ticks), the success chart per hook tier (low, high) and the clusters, all from the wiki.
	 */
	public enum Shipwreck {
		SMALL("Small", 15, 10, 5.5, 32847, 60464, 250, SalvageLoot.SMALL,
			new int[][]{{50, 100}, {52, 105}, {55, 110}, {57, 115}, {60, 120}, {62, 125}, {67, 135}},
			c("Shipwreck Cove", 1574, 3326, 1592, 3303, 1599, 3299, 1599, 3313, 1568, 3330, 1590, 3339, 1596, 3334, 1604, 3318),
			c("Kharidian Sea", 3095, 2928, 3096, 2934, 3085, 2955, 3090, 2959, 3120, 2989, 3125, 2984, 3126, 2957, 3132, 2951)),
		FISHERMAN("Fisherman's", 26, 17, 9, 32849, 60466, 300, SalvageLoot.FISHY,
			new int[][]{{48, 96}, {50, 100}, {52, 105}, {55, 110}, {57, 115}, {60, 120}, {64, 129}},
			c("Catherby Bay", 2752, 3324, 2759, 3318, 2779, 3324, 2798, 3308, 2805, 3311, 2777, 3333),
			c("Gu'tanoth Bay", 2696, 3050, 2704, 3050, 2723, 3027, 2726, 3066, 2730, 3034, 2736, 3066),
			c("Gulf of Kourend", 1863, 3832, 1871, 3828, 1887, 3830, 1896, 3827, 1913, 3836, 1906, 3844)),
		BARRACUDA("Barracuda", 35, 31, 15.5, 32851, 60468, 300, SalvageLoot.BARRACUDA,
			new int[][]{{45, 90}, {47, 94}, {49, 99}, {51, 103}, {54, 108}, {56, 112}, {60, 121}},
			// the Storm Tempor needs at least an oak mast and linen sails ("not safe to salvage while in hazardous waters")
			c("The Storm Tempor", true, 2977, 2890, 2977, 2899, 2982, 2918, 2990, 2924, 3002, 2934, 3007, 2925, 3024, 2878),
			c("Sea Spirit Dock", 3097, 2759, 3104, 2753, 3106, 2776, 3110, 2768, 3123, 2814, 3103, 2841, 3127, 2823),
			c("Backwater", 2477, 2964, 2478, 2993, 2481, 2953, 2481, 3001, 2456, 3017, 2461, 3008),
			c("Myths' Guild", 2487, 2813, 2473, 2836, 2481, 2839, 2492, 2822, 2509, 2833, 2519, 2832),
			c("Tirannwn Bight", 2133, 3291, 2155, 3280, 2127, 3386, 2153, 3375, 2160, 3346),
			c("Ynysdail", 2195, 3450, 2204, 3445, 2235, 3474, 2269, 3460, 2282, 3467),
			c("Angler's Retreat", 2489, 2694)),
		LARGE("Large", 53, 48, 24, 32853, 60470, 300, SalvageLoot.LARGE,
			new int[][]{{42, 84}, {44, 88}, {46, 92}, {48, 96}, {50, 100}, {52, 105}, {56, 113}},
			c("South of Shimmering Atoll", 1546, 2636, 1554, 2643, 1558, 2675, 1559, 2662, 1575, 2655, 1580, 2665),
			c("North of Isle of Serpents", 1861, 2546, 1865, 2556, 1888, 2554, 1899, 2549, 1910, 2530, 1914, 2543),
			c("South-west of Wyrmscraig", 2438, 2172, 2434, 2162, 2469, 2149, 2457, 2146, 2489, 2168, 2493, 2154),
			c("South-west of Grimstone", 2795, 4028, 2804, 4024, 2809, 3996, 2816, 3987, 2877, 4044, 2883, 4030),
			c("South of The Little Pearl", 3372, 2112, 3382, 2119, 3386, 2161, 3398, 2152, 3419, 2132, 3428, 2124)),
		PIRATE("Pirate", 64, 76, 31.5, 32855, 60472, 300, SalvageLoot.PLUNDERED,
			new int[][]{{40, 80}, {42, 84}, {44, 88}, {46, 92}, {48, 96}, {50, 100}, {54, 108}},
			c("South of Pest Control", 2626, 2539, 2629, 2547, 2659, 2556, 2667, 2558, 2681, 2538, 2683, 2548),
			c("Aureum Coast", 1792, 2869, 1797, 2878, 1809, 2828, 1818, 2836, 1840, 2825, 1847, 2817),
			c("South-east of Buccaneers' Haven", 2088, 3656, 2098, 3654, 2109, 3669, 2120, 3664, 2139, 3662, 2146, 3668),
			c("Pirates' Cove", 2177, 3804, 2180, 3815, 2230, 3834, 2194, 3840, 2202, 3845, 2224, 3840)),
		MERCENARY("Mercenary", 73, 138, 63.5, 32857, 60474, 325, SalvageLoot.MARTIAL,
			new int[][]{{32, 64}, {33, 67}, {35, 70}, {36, 73}, {38, 76}, {40, 80}, {43, 86}},
			c("West of the Isle of Serpents", 1673, 2384, 1683, 2381, 1686, 2428, 1697, 2422, 1705, 2402, 1712, 2394),
			c("South-western Southern Expanse", 1739, 2232, 1740, 2195, 1747, 2189, 1750, 2237, 1769, 2210, 1780, 2211),
			c("South-west of Sunbleak Island", 2114, 2217, 2120, 2226, 2123, 2192, 2131, 2189, 2163, 2192, 2170, 2200)),
		FREMENNIK("Fremennik", 80, 162, 75, 32859, 60476, 375, SalvageLoot.FREMENNIK,
			new int[][]{{28, 58}, {29, 60}, {30, 63}, {32, 66}, {33, 69}, {35, 72}, {37, 78}},
			c("Everwinter Sea", 1767, 4090, 1763, 4111, 1773, 4148, 1775, 4099, 1786, 4151, 1793, 4149),
			c("North of Ungael", 2246, 4115, 2257, 4114, 2257, 4147, 2270, 4143, 2283, 4107, 2290, 4116),
			c("Stoneheart Sea", 2571, 4152, 2581, 4147, 2599, 4097, 2601, 4130, 2606, 4106, 2611, 4125)),
		MERCHANT("Merchant", 87, 200, 95, 32861, 60478, 400, SalvageLoot.OPULENT,
			new int[][]{{24, 50}, {25, 52}, {26, 55}, {27, 57}, {28, 60}, {30, 62}, {32, 67}},
			c("Dusk's Maw", 1029, 2957, 1034, 2966, 1060, 2967, 1063, 2975, 1083, 2949, 1093, 2952),
			c("Winter's Edge", 1794, 3995, 1795, 3984, 1817, 4022, 1822, 4011, 1848, 3970, 1852, 3979),
			c("Southern Expanse", 1861, 2163, 1873, 2168, 1885, 2117, 1893, 2124, 1905, 2140, 1914, 2145));

		public final String name;
		public final int level, salvage, loc, sunkLoc, durationTicks;
		public final double xp, sortXp;
		final SalvageLoot.Table loot;
		final int[][] chart;
		final Cluster[] clusters;

		Shipwreck(String name, int level, double xp, double sortXp, int salvage, int loc, int durationTicks, SalvageLoot.Table loot,
				  int[][] chart, Cluster... clusters) {
			this.name = name;
			this.level = level;
			this.xp = xp;
			this.sortXp = sortXp;
			this.salvage = salvage;
			this.loc = loc;
			this.sunkLoc = loc + 1;
			this.durationTicks = durationTicks;
			this.loot = loot;
			this.chart = chart;
			this.clusters = clusters;
		}

		/** the wiki skilling-success curve (Module:Skilling success chart), out of 1 */
		public double chance(Hook hook, int level) {
			int l = Math.max(1, Math.min(99, level));
			int low = chart[hook.ordinal()][0], high = chart[hook.ordinal()][1];
			int value = (int) Math.floor(low * (99 - l) / 98.0 + high * (l - 1) / 98.0 + 0.5) + 1;
			return Math.min(1.0, Math.max(0.0, value / 256.0));
		}

		static Shipwreck forSalvage(int id) {
			for (Shipwreck w : values())
				if (w.salvage == id)
					return w;
			return null;
		}

		static Shipwreck forLoc(int id) {
			for (Shipwreck w : values())
				if (w.loc == id || w.sunkLoc == id)
					return w;
			return null;
		}
	}

	/* ------------------------------------------------------------------ the wrecks at sea */

	private static final int ROLL_TICKS = 4, SUNK_TICKS = 5, SORT_TICKS = 3;
	/** how far (tiles) from the wreck's footprint the hook may be. The wiki only says "adjacent". */
	private static final double HOOK_RANGE = 2.0;

	static final class Site {
		final Shipwreck wreck;
		final ClusterState cluster;
		final int x, y;
		GameObject obj;
		/** tick the wreck sinks at; 0 = nobody has salvaged it yet */
		long sinkAt;
		boolean sinking;

		Site(Shipwreck wreck, ClusterState cluster, int x, int y) {
			this.wreck = wreck;
			this.cluster = cluster;
			this.x = x;
			this.y = y;
		}

		boolean up() {
			return obj != null && !sinking;
		}

		/** distance from a sea point to the wreck's 2x1 footprint (loc width 2) */
		double distance(double px, double py) {
			double dx = Math.max(0, Math.max(x - px, px - (x + 2)));
			double dy = Math.max(0, Math.max(y - py, py - (y + 1)));
			return Math.max(dx, dy);
		}
	}

	static final class ClusterState {
		final Shipwreck wreck;
		final Cluster cluster;
		final List<Site> sites = new ArrayList<>();

		ClusterState(Shipwreck wreck, Cluster cluster) {
			this.wreck = wreck;
			this.cluster = cluster;
		}
	}

	private static final List<Site> SITES = new ArrayList<>();
	private static final Map<Player, Session> SESSIONS = new HashMap<>();

	static final class Session {
		final Boat boat;
		final Site site;
		long lastTick;

		Session(Boat boat, Site site, long lastTick) {
			this.boat = boat;
			this.site = site;
			this.lastTick = lastTick;
		}
	}

	public static void register() {
		for (Shipwreck w : Shipwreck.values()) {
			for (Cluster cl : w.clusters) {
				ClusterState cs = new ClusterState(w, cl);
				for (int[] s : cl.sites)
					cs.sites.add(new Site(w, cs, s[0], s[1]));
				SITES.addAll(cs.sites);
				// about 4 of every 6 sites are up at once
				int up = cs.sites.size() <= 2 ? cs.sites.size() : Math.max(1, (int) Math.round(cs.sites.size() * 2 / 3.0));
				List<Site> shuffled = new ArrayList<>(cs.sites);
				Collections.shuffle(shuffled);
				for (int i = 0; i < up; i++)
					raise(shuffled.get(i));
			}
			ObjectAction.register(w.loc, 1, (p, obj) -> p.sendMessage("This " + w.name.toLowerCase() + " shipwreck can be salvaged with a Sailing level of " + w.level + "."));
		}
		for (Hook h : Hook.values())
			ObjectAction.register(h.raftLoc(), 1, (p, obj) -> deploy(p));
		ObjectAction.register(60462, 1, (p, obj) -> sort(p, obj));
		ObjectAction.register(60463, 1, (p, obj) -> sort(p, obj));
		World.startEvent(e -> {
			while (true) {
				e.delay(1);
				try {
					tick();
				} catch (Exception ex) {
					log.error("Salvaging tick failed", ex);
				}
			}
		});
	}

	private static void raise(Site site) {
		site.sinkAt = 0;
		site.sinking = false;
		site.obj = GameObject.spawn(site.wreck.loc, site.x, site.y, 0, 10, 0);
	}

	private static void tick() {
		long now = Server.currentTick();
		for (Site site : SITES) {
			if (site.up() && site.sinkAt > 0 && now >= site.sinkAt)
				sink(site);
		}
		// a salvaging loop that stopped running (walked off, took the helm, logged out...) raises its hook
		Iterator<Map.Entry<Player, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			var en = it.next();
			if (now - en.getValue().lastTick > 1) {
				it.remove();
				raiseHook(en.getKey(), en.getValue().boat);
			}
		}
	}

	private static void sink(Site site) {
		site.sinking = true;
		site.obj.setId(site.wreck.sunkLoc);
		for (var en : new ArrayList<>(SESSIONS.entrySet())) {
			if (en.getValue().site == site) {
				SESSIONS.remove(en.getKey());
				raiseHook(en.getKey(), en.getValue().boat);
				en.getKey().sendMessage("You salvage all you can from the shipwreck before it is reclaimed by the sea.");
			}
		}
		World.startEvent(e -> {
			e.delay(SUNK_TICKS);
			site.obj.remove();
			site.obj = null;
			site.sinking = false;
			site.sinkAt = 0;
			// another site in the area rises
			List<Site> down = new ArrayList<>();
			for (Site s : site.cluster.sites)
				if (s != site && s.obj == null)
					down.add(s);
			raise(down.isEmpty() ? site : down.get(ThreadLocalRandom.current().nextInt(down.size())));
		});
	}

	/* ------------------------------------------------------------------ the hook */

	public static int raftHookLoc(Player p) {
		Hook[] all = Hook.values();
		return all[Math.max(0, Math.min(all.length - 1, p.sailingHookTier))].raftLoc();
	}

	private static Site nearbyWreck(Boat boat) {
		BoatType.Part hook = boat.type.part(BoatType.Role.FACILITY);
		double[] at = boat.seaPointOf(boat.tileOf(hook));
		Site best = null;
		double bestDist = HOOK_RANGE;
		for (Site s : SITES) {
			if (!s.up())
				continue;
			double d = s.distance(at[0], at[1]);
			if (d <= bestDist) {
				best = s;
				bestDist = d;
			}
		}
		return best;
	}

	private static void deploy(Player p) {
		Boat boat = p.boat;
		if (boat == null)
			return;
		Hook hook = Hook.forRaftLoc(boat.locFor(boat.type.part(BoatType.Role.FACILITY)));
		if (hook == null)
			return;
		Site site = nearbyWreck(boat);
		if (site == null) {
			p.sendMessage("There's no shipwreck close enough to salvage.");
			return;
		}
		Shipwreck wreck = site.wreck;
		if (p.getStats().get(StatType.Sailing).currentLevel < wreck.level) {
			p.sendMessage("You need a Sailing level of at least " + wreck.level + " to salvage this shipwreck.");
			return;
		}
		if (site.cluster.cluster.hazardous()) {
			p.sendMessage("It's not safe to salvage while in hazardous waters.");
			return;
		}
		if (p.getInventory().isFull()) {
			p.sendMessage("You don't have enough inventory space to hold any salvage.");
			return;
		}
		if (boat.navigating)
			Sailing.stopNavigating(p, false);
		Position stand = p.getPosition().copy();
		p.startEvent(e -> {
			long now = Server.currentTick();
			SESSIONS.put(p, new Session(boat, site, now));
			if (site.sinkAt == 0)
				site.sinkAt = now + wreck.durationTicks;
			p.animate(13569);
			boat.animateDeck(BoatType.Role.FACILITY, 13566);
			p.sendFilteredMessage("You deploy your salvaging hook.");
			int ticks = 0;
			while (true) {
				e.delay(1);
				Session s = SESSIONS.get(p);
				if (s == null || s.site != site)
					return; // the wreck sank (already handled)
				s.lastTick = Server.currentTick();
				if (p.boat != boat || !site.up() || !p.getPosition().equals(stand) || boat.navigating) {
					stop(p, boat);
					return;
				}
				if (nearbyWreck(boat) != site) {
					stop(p, boat);
					p.sendMessage("You've drifted too far from the shipwreck.");
					return;
				}
				if (++ticks % ROLL_TICKS != 0)
					continue;
				p.animate(13596);
				boat.animateDeck(BoatType.Role.FACILITY, 13595);
				if (ThreadLocalRandom.current().nextDouble() < wreck.chance(hook, p.getStats().get(StatType.Sailing).currentLevel)) {
					p.getInventory().add(wreck.salvage, 1);
					p.getStats().addXp(StatType.Sailing, wreck.xp, true);
					p.sendFilteredMessage("You haul up some " + io.ruin.cache.ObjType.get(wreck.salvage).name.toLowerCase() + ".");
					if (p.getInventory().isFull()) {
						stop(p, boat);
						p.sendMessage("Your inventory is too full to hold any more salvage.");
						return;
					}
				}
			}
		});
	}

	private static void stop(Player p, Boat boat) {
		if (SESSIONS.remove(p) != null)
			raiseHook(p, boat);
	}

	private static void raiseHook(Player p, Boat boat) {
		if (p.isOnline())
			p.animate(13571);
		if (boat != null)
			boat.animateDeck(BoatType.Role.FACILITY, 13568);
	}

	/* ------------------------------------------------------------------ sorting at a salvaging station */

	private static Shipwreck sortable(Player p) {
		int level = p.getStats().get(StatType.Sailing).currentLevel;
		Shipwreck tooHigh = null;
		for (Shipwreck w : Shipwreck.values()) {
			if (!p.getInventory().hasId(w.salvage))
				continue;
			if (level >= w.level)
				return w;
			tooHigh = w;
		}
		return tooHigh;
	}

	private static void sort(Player p, GameObject station) {
		Shipwreck first = sortable(p);
		if (first == null) {
			p.sendMessage("You don't have any salvage to sort.");
			return;
		}
		if (p.getStats().get(StatType.Sailing).currentLevel < first.level) {
			p.sendMessage("You need a Sailing level of at least " + first.level + " to sort " + io.ruin.cache.ObjType.get(first.salvage).name.toLowerCase() + ".");
			return;
		}
		p.face(station);
		p.startEvent(e -> {
			while (true) {
				Shipwreck w = sortable(p);
				if (w == null || p.getStats().get(StatType.Sailing).currentLevel < w.level) {
					p.sendFilteredMessage("You finish sorting your salvage.");
					return;
				}
				e.delay(SORT_TICKS);
				if (!p.getInventory().hasId(w.salvage))
					continue;
				p.getInventory().remove(w.salvage, 1);
				for (int[] item : w.loot.roll(p))
					p.getInventory().addOrDrop(item[0], item[1]);
				p.getStats().addXp(StatType.Sailing, w.sortXp, true);
			}
		});
	}
}
