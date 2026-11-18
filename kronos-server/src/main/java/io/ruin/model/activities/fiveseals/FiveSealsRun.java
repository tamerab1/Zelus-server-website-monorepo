package io.ruin.model.activities.fiveseals;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.ObjType;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.DeathListener;
import io.ruin.model.inter.ToplevelComponent;
import io.ruin.model.inter.dialogue.OptionsDialogue;
import io.ruin.model.inter.utils.Option;
import io.ruin.model.item.Item;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.stat.StatType;
import io.ruin.network.PacketSender;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * One run of The Five Seals: a private copy of region 13128 for a party of 1-5, played seal by seal.
 * <p>
 * Map (rev 241 cache, region 13128, read 2026-11-16; coordinates below are the static map's and are converted into the
 * instance): five numbered rooms around a central cross of coloured walkways. The hub (3297-3312, 4639-4640) holds the
 * Hall of Seals board (3309,4639) with the exit ladder behind it (24082 at 3312,4640; the hut went 2026-11-17, stage 60); five shimmering barriers at the cross gate the walkways:
 * <pre>
 *  room            colour  floor (x, y)               altar 7812     barrier            walkway side tile
 *  1 Awakening     green   3272-3284, 4650-4661       3272,4655      30396 3295,4641    3295,4642 (north)
 *  2 Carnage       red     3272-3284, 4634-4645       3272,4639      30397 3294,4639    3293,4639 (west)
 *  3 Arcana        blue    3272-3284, 4618-4629       3272,4623      30398 3295,4638    3295,4637 (south)
 *  4 Shadows       purple  3307-3319, 4650-4661       3319,4655      30399 3296,4641    3296,4642 (north)
 *  5 Sovereign     yellow  3307-3319, 4618-4629       3319,4623      30400 3296,4638    3296,4637 (south)
 * </pre>
 * Reward multiplier by seals completed: 1.0 / 1.5 / 2.0 / 3.0 / 4.0 / 5.0. The loot is only real once cashed out (the
 * altars of seals 2-4 offer "cash out or push on"; the Sovereign's altar pays the full run). A wipe loses it all.
 */
@Slf4j
public class FiveSealsRun {

	static final int REGION = 13128;
	static final double[] MULT = { 1.0, 1.5, 2.0, 3.0, 4.0, 5.0 };
	static final String[] SEAL_NAME = { "", "Awakening", "Carnage", "Arcana", "Shadows", "the Sovereign Vault" };
	/** room floors {x0, y0, x1, y1} */
	static final int[][] ROOM = { null, { 3272, 4650, 3284, 4661 }, { 3272, 4634, 3284, 4645 }, { 3272, 4618, 3284, 4629 },
		{ 3307, 4650, 3319, 4661 }, { 3307, 4618, 3319, 4629 } };
	static final int[][] ALTAR = { null, { 3272, 4655 }, { 3272, 4639 }, { 3272, 4623 }, { 3319, 4655 }, { 3319, 4623 } };
	/** barrier loc id -> room; the walkway tile beyond it */
	static final int[] BARRIER = { 0, 30396, 30397, 30398, 30399, 30400 };
	static final int[][] WALKWAY = { null, { 3295, 4642 }, { 3293, 4639 }, { 3295, 4637 }, { 3296, 4642 }, { 3296, 4637 } };
	static final int HUB_X = 3300, HUB_Y = 4640, HUB_RETURN_X = 3297, HUB_RETURN_Y = 4640;
	/** runs start in the Seal of Awakening (room 1) */
	static final int START_X = 3279, START_Y = 4656;

	/* creatures (cache npcs with combat data; all run on SealCombat: no drops, no kill credit) */
	static final int GUARDIAN = 30262, ANKOU = 30261, WARDEN_OF_CARNAGE = 30263, ARCANE_WARDEN = 30264, STALKER = 30265,
		SOVEREIGN = 30266;
	/** our creatures (cache stages 51 / 52) -> the npc whose stats they fight with */
	static final int[][] CREATURE_BASE = { { GUARDIAN, 2841 }, { ANKOU, 2514 }, { WARDEN_OF_CARNAGE, 7144 }, { ARCANE_WARDEN, 3257 },
		{ STALKER, 3449 }, { SOVEREIGN, 13599 } };
	/* objects spawned in the instance (their usual handlers still run everywhere else) */
	/* the Seal of Awakening's own Bloodgold rocks / Karnwood trees (cache stage 51; Mining / Woodcutting run them) */
	static final int SEAL_ROCK = 63309, SEAL_TREE = 63307;
	/* the Karn objects of cache stage 52 (the Seal altar 63317 is in the map itself, stage 53) */
	static final int CONDUIT = 63311, CONDUIT_LIT = 63312, NODE = 63313, NODE_DONE = 63314, SUPPLY_ALTAR = 63315, VAULT = 63316,
		SEAL_ALTAR = 63317, SHADOW_BARS = 63319;
	static final int KARNWOOD_LOGS = 42775, BLOODGOLD_ORE = 42776;

	/* HUD (interface 1189, BuildKarnHub.fiveSealsHud) */
	static final int HUD = 1189, HUD_SEAL = 8, HUD_OBJECTIVE = 9, HUD_FIRST_SEG = 11, HUD_SEGS = 20, HUD_MULT = 31, HUD_TIMER = 32;

	final DynamicMap map;
	final List<Player> members = new ArrayList<>();
	final boolean group;
	/** party size when the run started (1 = solo): every seal scales on it */
	final int size;
	final int startTick;
	int seal = 1, completed = 0, charge = 0;
	boolean ended, buff, awaitingChoice;
	/** the full clear's loot was rolled into every member's vault window (FiveSealsVault); the run ends once all are dealt with */
	boolean vaultPaid;
	private int vaultDeadline;
	/** ticks the members get to deal with their vault windows before the rest is banked */
	static final int VAULT_TICKS = 100;
	private final List<NPC> npcs = new ArrayList<>();
	private final List<GameObject> objects = new ArrayList<>();
	private final boolean[] started = new boolean[6];

	/* room 2 */
	private boolean wardenSpawned, wardenDead;
	/* room 3 */
	private final List<GameObject> conduits = new ArrayList<>();
	private int conduitsLit, lastConduitTick;
	private boolean shieldsDown;
	private int wardensLeft;
	private GameObject supplyAltar;
	private final Set<Integer> suppliedUsers = new HashSet<>();
	/* room 4 */
	private final List<GameObject> nodes = new ArrayList<>();
	private final boolean[] nodeDone = new boolean[4];
	private int nodesDone, roomTicks;
	/* room 5 */
	private NPC sovereign;
	/** someone in the run owns the Altar Conduit */
	private boolean conduit;
	/** Seal 4's walkway bars while they're down (object 63319) */
	private GameObject bars;

	FiveSealsRun(DynamicMap map, List<Player> party) {
		this.map = map;
		this.members.addAll(party);
		this.group = party.size() > 1;
		this.size = Math.max(1, Math.min(5, party.size()));
		this.startTick = (int) io.ruin.Server.currentTick();
	}

	/* ------------------------------------------------------------------ coordinates */

	Position at(int x, int y) {
		return new Position(map.convertX(x), map.convertY(y), 0);
	}

	boolean inRoom(Player p, int room) {
		return inRoom(p.getPosition(), room);
	}

	boolean inRoom(Position pos, int room) {
		int[] r = ROOM[room];
		int x = pos.getX(), y = pos.getY();
		return pos.getZ() == 0 && x >= map.convertX(r[0]) && x <= map.convertX(r[2]) && y >= map.convertY(r[1]) && y <= map.convertY(r[3]);
	}

	List<Player> membersInRoom(int room) {
		List<Player> list = new ArrayList<>();
		for (Player p : members)
			if (p.isOnline() && !p.getCombat().isDead() && inRoom(p, room))
				list.add(p);
		return list;
	}

	void track(NPC npc) {
		npcs.add(npc);
	}

	void broadcast(String message) {
		for (Player p : members)
			p.sendMessage(message);
	}

	double multiplier() {
		return MULT[Math.min(completed, 5)];
	}

	/* ------------------------------------------------------------------ start / tick / end */

	void start() {
		for (Player p : members)
			conduit |= p.fiveSealsConduit;
		for (Player p : members) {
			io.ruin.model.map.route.routes.TargetRoute.reset(p); // no leftover walk-to-npc route follows them in
			p.closeInterfaces(); // the Party Board / any dialogue
			FiveSeals.fadeMove(p, new Position(map.convertX(START_X), map.convertY(START_Y), 0), () -> {
				map.assignListener(p).onExit((pl, logout) -> left(pl, logout));
				p.deathEndListener = (DeathListener.Simple) () -> died(p);
			}, () -> {
				p.openInterface(ToplevelComponent.OVERLAY, HUD);
				p.sendMessage(Color.PURPLE.wrap("The Five Seals stir. Break the Seal of Awakening (room 1, through the green barrier)."));
				FiveSealsShop.giveSupplies(p);
			});
		}
		World.startEvent(e -> {
			e.delay(6); // the fade-in lands everyone (fadeMove: 2 + 1 + 2 ticks) before the first "still in the instance?" check
			while (!ended) {
				try {
					tick();
				} catch (Exception ex) {
					log.error("Five Seals tick failed", ex);
				}
				e.delay(1);
			}
		});
	}

	private void tick() {
		if (io.ruin.Server.currentTick() % 2 == 0)
			keepHunting();
		members.removeIf(p -> !p.isOnline() || !map.isIn(p));
		if (members.isEmpty()) {
			fail(null);
			return;
		}
		if (vaultPaid) {
			boolean waiting = false;
			for (Player m : members)
				waiting |= FiveSealsVault.has(m);
			if (!waiting || io.ruin.Server.currentTick() >= vaultDeadline) {
				for (Player m : members)
					FiveSealsVault.bankAll(m, "The vault seals itself; the rest of your loot was sent to your bank.");
				end();
			}
			return;
		}
		for (int room = 1; room <= 5; room++)
			if (!started[room] && room <= seal && !membersInRoom(room).isEmpty())
				startRoom(room);
		switch (seal) {
			case 1 -> tickAwakening();
			case 2 -> tickCarnage();
			case 3 -> tickArcana();
			case 4 -> tickShadows();
			default -> { }
		}
		hud();
	}

	private void hud() {
		int ticks = (int) io.ruin.Server.currentTick() - startTick;
		int objective = Math.max(0, Math.min(100, objective()));
		int fill = objective * HUD_SEGS / 100;
		for (Player p : members) {
			PacketSender ps = p.getPacketSender();
			ps.sendString(HUD, HUD_SEAL, "Seal " + Math.min(seal, 5) + "/5 - " + SEAL_NAME[Math.min(seal, 5)]);
			ps.sendString(HUD, HUD_OBJECTIVE, completed >= 5 ? "Vorgath falls! Claim the vault." : "Objective: " + objective + "%");
			for (int s = 0; s < HUD_SEGS; s++)
				ps.setHidden(HUD, HUD_FIRST_SEG + s, s >= fill);
			ps.sendString(HUD, HUD_MULT, "Reward x" + multiplier());
			ps.sendString(HUD, HUD_TIMER, "Time " + FiveSeals.time(ticks));
		}
	}

	private int objective() {
		if (awaitingChoice || completed >= seal)
			return 100;
		return switch (seal) {
			case 1, 2 -> charge * 100 / target(seal);
			case 3 -> conduitsLit * 15 + (shieldsDown ? (3 - wardensLeft) * 18 + 1 : 0);
			case 4 -> nodesDone * 25;
			case 5 -> sovereign == null || sovereign.isRemoved() ? 0 : 100 - sovereign.getHp() * 100 / Math.max(1, sovereign.getMaxHp());
			default -> 0;
		};
	}

	/** a member left the instance (walked out of the ladder, teleported, logged out) */
	private void left(Player p, boolean logout) {
		cleanupPlayer(p);
		members.remove(p);
		if (logout)
			p.getMovement().teleport(FiveSealsLobby.CARPET); // never saved inside the instance
		if (!ended && !logout && p.isOnline())
			p.sendMessage("You leave The Five Seals. Any loot you hadn't cashed out is gone.");
		if (members.isEmpty() && !ended)
			fail(null);
	}

	/** deaths are safe: the fallen member is carried out of the vault and loses their share */
	private void died(Player p) {
		p.sendMessage(Color.RED.wrap("You fall in The Five Seals and are carried out. Your share of the run is lost."));
		members.remove(p);
		cleanupPlayer(p);
		p.getMovement().teleport(FiveSealsLobby.CARPET);
		if (members.isEmpty() && !ended)
			fail("The party has been wiped out. The Five Seals reclaim their treasure.");
	}

	private void cleanupPlayer(Player p) {
		FiveSealsVault.bankAll(p, "Your unclaimed vault loot was sent to your bank.");
		FiveSealsShop.removeSupplies(p);
		p.getPacketSender().resetHintIcon(false);
		removeResources(p);
		if (p.deathEndListener != null)
			p.deathEndListener = null;
		if (p.isVisibleInterface(HUD))
			p.closeInterface(ToplevelComponent.OVERLAY);
		FiveSeals.RUNS.remove(p.getUserId());
	}

	void fail(String message) {
		if (ended)
			return;
		if (message != null)
			broadcast(Color.RED.wrap(message));
		end();
	}

	void end() {
		if (ended)
			return;
		ended = true;
		for (NPC n : npcs)
			if (!n.isRemoved())
				n.remove();
		npcs.clear();
		for (GameObject o : objects)
			if (!o.isRemoved())
				o.remove();
		objects.clear();
		List<Player> leaving = new ArrayList<>(members);
		members.clear();
		for (Player p : leaving) {
			cleanupPlayer(p);
			if (p.isOnline() && map.isIn(p))
				FiveSeals.fadeMove(p, FiveSealsLobby.CARPET, null, null);
		}
		World.startEvent(e -> {
			e.delay(5); // after the fades have moved everyone out
			try {
				map.destroy();
			} catch (Exception ex) {
				log.error("Unable to destroy a Five Seals instance", ex);
			}
		});
	}

	/** the ladder in the hub hut (24082): the way out; anything not cashed out is lost */
	boolean ladder(Player p) {
		p.dialogue(new OptionsDialogue("Leave The Five Seals?" + (completed > 0 && completed < 5 ? " Loot not cashed out is lost!" : ""),
			new Option("Yes, climb out.", () -> {
				if (map.isIn(p))
					FiveSeals.fadeMove(p, FiveSealsLobby.CARPET, null, null);
			}),
			new Option("No, stay.")));
		return true;
	}

	/* ------------------------------------------------------------------ gates + altars */

	/** a shimmering barrier: through to the room's walkway if its seal is open, back to the hub from the walkway side */
	boolean barrier(Player p, int objectId) {
		int room = -1;
		for (int r = 1; r <= 5; r++)
			if (BARRIER[r] == objectId)
				room = r;
		if (room == -1)
			return false;
		int[] w = WALKWAY[room];
		Position walkway = at(w[0], w[1]);
		if (!onHubSide(p)) {
			fadeThrough(p, new Position(map.convertX(HUB_RETURN_X), map.convertY(HUB_RETURN_Y), 0));
			return true;
		}
		if (room > seal) {
			if (awaitingChoice && room == seal + 1)
				choice(p);
			else
				p.sendMessage("The Seal of " + SEAL_NAME[room] + " is still sealed. Break the seals in order.");
			return true;
		}
		fadeThrough(p, walkway);
		return true;
	}

	/** through a barrier: the raid-style fade, the HUD back once the screen clears */
	private void fadeThrough(Player p, Position to) {
		FiveSeals.fadeMove(p, to, null, () -> {
			if (!ended && members.contains(p))
				p.openInterface(ToplevelComponent.OVERLAY, HUD);
		});
	}

	/** the 2x2 centre inside the barriers and the hub east of it (3295-3312, 4639-4640) */
	private boolean onHubSide(Player p) {
		int x = p.getAbsX(), y = p.getAbsY();
		return x >= map.convertX(3295) && x <= map.convertX(3312) && y >= map.convertY(4639) && y <= map.convertY(4640);
	}

	/**
	 * Every creature of the run keeps a living target in its own room: one with none (or whose target died, left the room
	 * or the run) turns on the nearest member there (user 2026-11-16: "they sometimes stop attacking").
	 */
	private void keepHunting() {
		for (NPC n : new ArrayList<>(npcs)) {
			if (n.isRemoved() || !(n.getCombat() instanceof SealCombat sc) || sc.isDead())
				continue;
			int room = roomOf(n);
			if (room == -1)
				continue;
			io.ruin.model.entity.Entity t = sc.getTarget();
			Player cur = t == null ? null : t.player;
			if (cur != null && cur.isOnline() && !cur.getCombat().isDead() && members.contains(cur) && inRoom(cur, room))
				continue;
			Player next = nearest(n, room);
			if (next != null)
				sc.hunt(next);
		}
	}

	/** the room an npc stands in, or -1 */
	private int roomOf(NPC n) {
		int x = n.getAbsX(), y = n.getAbsY();
		for (int r = 1; r <= 5; r++) {
			int[] b = ROOM[r];
			if (x >= map.convertX(b[0]) - 4 && x <= map.convertX(b[2]) && y >= map.convertY(b[1]) - 4 && y <= map.convertY(b[3]))
				return r;
		}
		return -1;
	}

	/** a random member standing in a room, or null */
	Player anyIn(int room) {
		List<Player> list = membersInRoom(room);
		return list.isEmpty() ? null : list.get(Random.get(list.size() - 1));
	}

	/** room 4's walkway bars cycle: lifted 8 ticks (11 solo: 40% longer), down 8 ticks */
	private boolean barsClosed() {
		int open = size == 1 ? 11 : 8, cycle = open + 8;
		return (io.ruin.Server.currentTick() - startTick) % cycle >= open;
	}

	/** a room altar (7812) */
	boolean altar(Player p) {
		int room = -1;
		for (int r = 1; r <= 5; r++)
			if (inRoom(p, r))
				room = r;
		if (room == -1)
			return false;
		if (room == 5 && completed >= 5)
			return vault(p);
		if (room == seal && awaitingChoice) {
			choice(p);
			return true;
		}
		if (room == 1 && seal == 1 && completed < 1 && offer(p))
			return true;
		if (room < seal || completed >= room) {
			p.sendMessage("This seal is already broken.");
			return true;
		}
		String hint = switch (room) {
			case 1 -> "Chop the Karnwood trees and mine the Bloodgold rocks, then offer the logs and ore here. Slain guardians charge it too.";
			case 2 -> "Slay the Crimson Ankous near this altar to fill it with soul energy. At 60% Baelgor the Felreaver descends - slay him too.";
			case 3 -> "Light the three arcane conduits in order (west, north-east, south-east) to break the wardens' shields.";
			case 4 -> "Channel all four shadow nodes before time runs out. Shadow stalkers lunge at you - strike them to stagger them, or step away.";
			default -> "Vorgath the Shadowrider guards the vault. His shout tells his shot: arrows or shadows. Defeat him.";
		};
		p.sendMessage(Color.PURPLE.wrap("Seal of " + SEAL_NAME[room] + ": " + objective() + "%. ") + hint);
		return true;
	}

	/** the Sovereign's vault (63316): the full clear's reward */
	boolean vault(Player p) {
		if (ended)
			return true;
		if (completed < 5) {
			p.sendMessage("The vault is sealed while Vorgath lives.");
			return true;
		}
		if (vaultPaid) {
			if (FiveSealsVault.has(p))
				FiveSealsVault.open(p);
			else
				p.sendMessage("You have already claimed your share of the vault.");
			return true;
		}
		payout(true);
		return true;
	}

	/** a broken seal's cash-out-or-push-on choice (its altar, the next room's barrier, or offered when it breaks) */
	void choice(Player p) {
		if (ended || !awaitingChoice)
			return;
		double next = MULT[Math.min(completed + 1, 5)];
		p.dialogue(new OptionsDialogue("The Seal of " + SEAL_NAME[seal] + " is broken. Reward x" + multiplier() + ".",
			new Option("Cash out current loot (x" + multiplier() + ")", () -> {
				if (!ended && awaitingChoice)
					payout(false);
			}),
			new Option("Advance to Seal " + (seal + 1) + " (x" + next + " once broken)", () -> advance(p)),
			new Option("Not yet.")));
	}

	private void advance(Player p) {
		if (ended || !awaitingChoice)
			return;
		awaitingChoice = false;
		seal++;
		broadcast(Color.PURPLE.wrap(p.getName() + " pushes on! The Seal of " + SEAL_NAME[seal] + " opens (room " + seal + ")."));
	}

	/** room cleared */
	private void complete(int room) {
		if (completed >= room)
			return;
		completed = room;
		charge = 0;
		clearRoom(room);
		if (room == 1) {
			for (Player m : members)
				removeResources(m);
			buff = true;
			seal = 2;
			broadcast(Color.PURPLE.wrap("The Seal of Awakening breaks! You feel empowered: +10% accuracy for the rest of the run. "
				+ "The red barrier opens (room 2)."));
			return;
		}
		if (room == 5) {
			broadcast(Color.DARK_GREEN.wrap("Vorgath falls! The Sovereign's vault opens - claim it (x5.0, x6.0 with Sovereign Greed)."));
			return;
		}
		awaitingChoice = true;
		broadcast(Color.PURPLE.wrap("The Seal of " + SEAL_NAME[room] + " breaks! Reward x" + multiplier()
			+ ". Cash out, or push on to Seal " + (room + 1) + " (its altar or the next barrier asks again)."));
		for (Player m : members)
			if (m.isOnline())
				choice(m);
	}

	private void clearRoom(int room) {
		for (NPC n : new ArrayList<>(npcs))
			if (!n.isRemoved() && n != sovereign) {
				n.remove();
				npcs.remove(n);
			}
		for (GameObject o : new ArrayList<>(objects))
			if (!o.isRemoved() && o != supplyAltar) {
				o.remove();
				objects.remove(o);
			}
		conduits.clear();
		nodes.clear();
	}

	private GameObject object(int id, int x, int y) {
		Position pos = at(x, y);
		GameObject o = GameObject.spawn(id, pos.getX(), pos.getY(), 0, 10, 0);
		objects.add(o);
		return o;
	}

	private void startRoom(int room) {
		started[room] = true;
		roomTicks = 0;
		switch (room) {
			case 1 -> {
				object(SEAL_ROCK, 3282, 4660); // user 2026-11-17 (::pos)
				object(SEAL_ROCK, 3275, 4660);
				object(SEAL_TREE, 3275, 4651);
				object(SEAL_TREE, 3282, 4651);
				for (int i = 0; i < Math.min(5, 2 + size); i++)
					guardian();
			}
			case 2 -> wave();
			case 3 -> {
				conduits.add(object(CONDUIT, 3275, 4627));
				conduits.add(object(CONDUIT, 3283, 4627));
				conduits.add(object(CONDUIT, 3283, 4619));
				int[][] spots = { { 3279, 4626 }, { 3282, 4623 }, { 3279, 4620 } };
				wardensLeft = spots.length;
				for (int[] s : spots) {
					NPC w = SealCombat.spawn(this, ARCANE_WARDEN, at(s[0], s[1]), () -> {
						if (--wardensLeft <= 0)
							arcanaCleared();
					});
					w.hitListener.preDamage(hit -> {
						if (!shieldsDown) {
							hit.block();
							if (hit.attacker != null && hit.attacker.player != null)
								hit.attacker.player.sendFilteredMessage("An arcane shield absorbs the blow. Light the conduits!");
						}
					});
				}
				pointAtConduit();
				broadcast(Color.PURPLE.wrap("Arcane wardens raise their shields. Light the three conduits in order: west, north-east, south-east"
					+ " - each within " + (size == 1 ? 25 : 10) + " seconds of the last."));
			}
			case 4 -> {
				int[][] spots = { { 3309, 4659 }, { 3317, 4659 }, { 3309, 4652 }, { 3317, 4652 } };
				for (int[] s : spots)
					nodes.add(object(NODE, s[0], s[1]));
				Arrays.fill(nodeDone, false);
				nodesDone = 0;
				broadcast(Color.PURPLE.wrap("Shadows gather. Channel all four shadow nodes within 3 minutes - stand still while you channel."));
			}
			case 5 -> {
				VorgathSeal c = new VorgathSeal(this);
				sovereign = SealCombat.spawn(this, VorgathSeal.NPC_ID, at(3311, 4622), c, () -> {
					complete(5);
					object(VAULT, 3309, 4629); // user 2026-11-17 (::pos)
				});
				c.scale(size);
				sovereign.forceText("You dare open my vault? Then be hunted!");
				broadcast(Color.RED.wrap("Vorgath the Shadowrider guards the vault. Read his shout: arrows = Protect from Missiles, shadows = Protect from Magic."));
				Player first = anyIn(5);
				if (first != null)
					c.hunt(first);
			}
			default -> { }
		}
	}

	/* ------------------------------------------------------------------ seal 1: Awakening */

	private void guardian() {
		int[][] spots = { { 3277, 4655 }, { 3281, 4656 }, { 3283, 4654 }, { 3276, 4658 } };
		int[] s = spots[Random.get(spots.length - 1)];
		NPC g = SealCombat.spawn(this, GUARDIAN, at(s[0], s[1]), () -> {
			if (seal == 1 && completed < 1) {
				addCharge(1, 6);
				World.startEvent(e -> {
					e.delay(5);
					if (!ended && seal == 1 && completed < 1)
						guardian();
				});
			}
		});
		Player target = anyIn(1);
		if (target != null && g.getCombat() instanceof SealCombat sc)
			sc.hunt(target);
	}

	private void tickAwakening() {
		// guardians pick a fight with anyone in the room
		if (io.ruin.Server.currentTick() % 4 == 0)
			for (NPC n : npcs)
				if (!n.isRemoved() && n.getCombat() instanceof SealCombat sc && sc.getTarget() == null) {
					Player t = anyIn(1);
					if (t != null)
						sc.hunt(t);
				}
	}

	/** seal energy needed: room 1 = 100 x party size; room 2 = 60 + 40 x party size */
	int target(int room) {
		return room == 1 ? 100 * size : room == 2 ? 60 + 40 * size : 100;
	}

	private void addCharge(int room, int amount) {
		if (seal != room || completed >= room)
			return;
		if (room <= 2 && conduit)
			amount = (int) Math.round(amount * 1.2); // the Altar Conduit (Token Shop)
		charge = Math.min(target(room), charge + amount);
		if (charge >= target(room))
			complete(room);
	}

	/**
	 * mining a Bloodgold rock / chopping a Karnwood tree: while the Seal of Awakening is open the real Mining / Woodcutting
	 * runs (false = fall through to it: axes, pickaxes, depletion, pets, nests); afterwards the spot is spent
	 */
	boolean gather(Player p, GameObject obj, boolean rock) {
		if (seal == 1 && completed < 1)
			return false;
		p.sendMessage("The seal's energy has already been drawn from this.");
		return true;
	}

	/** offering Karnwood logs / Bloodgold ore at the Seal of Awakening's altar */
	private boolean offer(Player p) {
		int n = p.getInventory().getAmount(KARNWOOD_LOGS) + p.getInventory().getAmount(BLOODGOLD_ORE);
		if (n == 0)
			return false;
		removeResources(p);
		p.animate(645);
		int per = size == 1 ? 8 : 5; // solo: double the seal energy per offering
		addCharge(1, n * per);
		p.sendMessage(Color.PURPLE.wrap("You offer " + n + " resource" + (n == 1 ? "" : "s") + " to the Seal of Awakening (+" + n * per
			+ " energy). It is " + (completed >= 1 ? "broken!" : objective() + "% charged.")));
		return true;
	}

	/** the seal's resources never leave the run */
	static void removeResources(Player p) {
		for (int id : new int[] { KARNWOOD_LOGS, BLOODGOLD_ORE }) {
			int n = p.getInventory().getAmount(id);
			if (n > 0)
				p.getInventory().remove(id, n);
		}
	}

	/* ------------------------------------------------------------------ seal 2: Carnage */

	private void wave() {
		int[][] all = { { 3280, 4642 }, { 3282, 4640 }, { 3280, 4637 }, { 3283, 4644 }, { 3278, 4644 }, { 3283, 4636 },
			{ 3277, 4641 }, { 3281, 4645 }, { 3284, 4641 }, { 3278, 4635 }, { 3282, 4643 }, { 3276, 4638 } };
		int count = Math.min(all.length, 2 + 2 * size); // solo 4 ... 5 players 12
		for (int i = 0; i < count; i++) {
			int[] s = all[i];
			NPC a = SealCombat.spawn(this, ANKOU, at(s[0], s[1]), null);
			NPC self = a;
			((SealCombat) a.getCombat()).onDeath = () -> {
				Position altar = at(ALTAR[2][0], ALTAR[2][1]);
				boolean near = self.getPosition().isWithinDistance(altar, 8);
				addCharge(2, near ? 10 : 5);
			};
			Player t = anyIn(2);
			if (t != null)
				((SealCombat) a.getCombat()).hunt(t);
		}
	}

	private void tickCarnage() {
		if (!started[2] || completed >= 2)
			return;
		npcs.removeIf(NPC::isRemoved);
		if (charge >= target(2) * 6 / 10 && !wardenSpawned) {
			wardenSpawned = true;
			BaelgorSeal w = new BaelgorSeal(this);
			NPC warden = SealCombat.spawn(this, BaelgorSeal.NPC_ID, at(3279, 4640), w, () -> {
				wardenDead = true;
				addCharge(2, 40);
			});
			w.scale(size);
			warden.forceText("Your souls will feed the fel!");
			Player t = anyIn(2);
			if (t != null)
				w.hunt(t);
			broadcast(Color.RED.wrap("Baelgor the Felreaver descends! He guards against one style (watch his overhead) - switch when he does."));
		}
		if (npcs.isEmpty() && charge < target(2) && (!wardenSpawned || wardenDead))
			wave();
		if (io.ruin.Server.currentTick() % 4 == 0)
			for (NPC n : npcs)
				if (n.getCombat() instanceof SealCombat sc && sc.getTarget() == null) {
					Player t = anyIn(2);
					if (t != null)
						sc.hunt(t);
				}
	}

	/* ------------------------------------------------------------------ seal 3: Arcana */

	/** a conduit / node / supply altar (prayer altar 409 spawned in the instance) */
	boolean conduit(Player p, GameObject obj) {
		if (obj == supplyAltar) {
			if (!suppliedUsers.add(p.getUserId())) {
				p.sendMessage("The supply altar has already restored you this run.");
				return true;
			}
			p.animate(645);
			p.getStats().restore(false);
			p.getMovement().restoreEnergy(100);
			p.sendMessage(Color.DARK_GREEN.wrap("The supply altar restores your hitpoints, prayer and energy."));
			return true;
		}
		int ci = conduits.indexOf(obj);
		if (ci != -1) {
			channel(p, obj, 3, () -> {
				if (shieldsDown || conduitsLit != ci) {
					if (!shieldsDown && conduitsLit != ci) {
						darkenConduits();
						broadcast(Color.RED.wrap("The conduits flicker and go dark - they must be lit in order!"));
					}
					return;
				}
				conduitsLit++;
				obj.setId(CONDUIT_LIT);
				lastConduitTick = (int) io.ruin.Server.currentTick();
				pointAtConduit();
				World.sendGraphics(1309, 0, 0, obj.getPosition());
				broadcast(Color.PURPLE.wrap("Conduit " + conduitsLit + "/3 lit."));
				if (conduitsLit >= 3) {
					shieldsDown = true;
					for (Player m : members)
						m.getPacketSender().resetHintIcon(false);
					broadcast(Color.PURPLE.wrap("The wardens' shields shatter! Strike them down."));
				}
			});
			return true;
		}
		int ni = nodes.indexOf(obj);
		if (ni != -1) {
			if (nodeDone[ni]) {
				p.sendMessage("This shadow node is already neutralised.");
				return true;
			}
			channel(p, obj, 5, () -> {
				if (nodeDone[ni])
					return;
				nodeDone[ni] = true;
				nodesDone++;
				obj.setId(NODE_DONE);
				World.sendGraphics(1309, 0, 0, obj.getPosition());
				broadcast(Color.PURPLE.wrap("Shadow node " + nodesDone + "/4 neutralised."));
				if (nodesDone >= 4)
					complete(4);
			});
			return true;
		}
		return false;
	}

	/** every conduit back to dark, the arrow back on the first */
	private void darkenConduits() {
		conduitsLit = 0;
		for (GameObject c : conduits)
			if (!c.isRemoved())
				c.setId(CONDUIT);
		pointAtConduit();
	}

	/** a hint arrow over the next conduit to light, for everyone in the run */
	void pointAtConduit() {
		if (shieldsDown || conduitsLit >= conduits.size())
			return;
		Position next = conduits.get(conduitsLit).getPosition();
		for (Player m : members)
			m.getPacketSender().sendHintIcon(next);
	}

	/** stand still at an object for {@code ticks}; moving or being knocked away breaks the channel */
	private void channel(Player p, GameObject obj, int ticks, Runnable done) {
		Position from = p.getPosition().copy();
		p.startEvent(e -> {
			p.face(obj);
			for (int t = 0; t < ticks; t++) {
				p.animate(645);
				e.delay(1);
				if (ended || !p.getPosition().equals(from) || p.getCombat().isDead()) {
					p.sendMessage("Your channel is broken.");
					return;
				}
			}
			done.run();
		});
	}

	/** ticks allowed between lighting one conduit and the next: solo 25 seconds, groups 10 seconds */
	int conduitWindow() {
		return size == 1 ? 42 : 17;
	}

	private void tickArcana() {
		if (!started[3] || completed >= 3)
			return;
		if (!shieldsDown && conduitsLit > 0 && io.ruin.Server.currentTick() - lastConduitTick > conduitWindow()) {
			darkenConduits();
			broadcast(Color.RED.wrap("The lit conduits fade - light the next one within " + (size == 1 ? 25 : 10) + " seconds!"));
		}
		// corridor energy: every 6 ticks the blue walkway and the room crackle under the players; move off within 2 ticks
		if (io.ruin.Server.currentTick() % 6 != 0)
			return;
		for (Player p : members) {
			if (!inRoom(p, 3) && !onBlueWalkway(p))
				continue;
			List<Position> tiles = new ArrayList<>();
			tiles.add(p.getPosition().copy());
			for (int i = 0; i < 2; i++)
				tiles.add(new Position(p.getAbsX() + Random.get(-2, 2), p.getAbsY() + Random.get(-2, 2), 0));
			for (Position t : tiles)
				World.sendGraphics(1014, 0, 0, t);
			World.startEvent(e -> {
				e.delay(2);
				if (ended)
					return;
				for (Player q : members)
					for (Position t : tiles)
						if (q.getAbsX() == t.getX() && q.getAbsY() == t.getY()) {
							q.hit(new io.ruin.model.combat.Hit().randDamage(8, 15));
							break;
						}
			});
		}
	}

	private boolean onBlueWalkway(Player p) {
		int x = p.getAbsX(), y = p.getAbsY();
		boolean vertical = x == map.convertX(3295) && y >= map.convertY(4623) && y <= map.convertY(4637);
		boolean row = y >= map.convertY(4623) && y <= map.convertY(4624) && x >= map.convertX(3285) && x <= map.convertX(3295);
		return vertical || row;
	}

	private void arcanaCleared() {
		complete(3);
		Position c = at(3278, 4623);
		supplyAltar = GameObject.spawn(SUPPLY_ALTAR, c.getX(), c.getY(), 0, 10, 0);
		objects.add(supplyAltar);
		broadcast(Color.DARK_GREEN.wrap("A supply altar rises in the Seal of Arcana: pray at it once to restore yourself."));
	}

	/* ------------------------------------------------------------------ seal 4: Shadows */

	private void tickShadows() {
		if (!started[4] || completed >= 4)
			return;
		roomTicks++;
		if (roomTicks == 300) { // 3 minutes
			roomTicks = 0;
			Arrays.fill(nodeDone, false);
			nodesDone = 0;
			for (GameObject n : nodes)
				if (!n.isRemoved())
					n.setId(NODE); // back to black-crimson
			broadcast(Color.RED.wrap("The shadows reclaim the nodes! Channel them again - faster this time."));
		}
		boolean closed = barsClosed();
		if (closed != barsWereClosed) {
			barsWereClosed = closed;
			setBars(closed);
			broadcast(closed ? Color.PURPLE.wrap("The shadow bars slam down across the purple walkway.")
				: Color.PURPLE.wrap("The shadow bars lift."));
		}
		npcs.removeIf(NPC::isRemoved);
		if (roomTicks % 12 == 0 && npcs.size() < 1 + size / 2) {
			StalkerCombat stalker = new StalkerCombat(this);
			NPC s = SealCombat.spawn(this, STALKER, at(3313, 4651), stalker, null);
			s.hitListener.postDamage(hit -> {
				if (hit.damage > 0)
					stalker.stagger(); // a landed blow cancels its lunge
			});
			Player t = nearest(s, 4);
			if (t != null)
				((SealCombat) s.getCombat()).hunt(t);
		}
		// group: a snared member is freed when a teammate reaches them (or after 20 ticks)
		for (Iterator<Map.Entry<Player, Integer>> it = snared.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Player, Integer> e = it.next();
			Player p = e.getKey();
			boolean rescued = false;
			for (Player q : members)
				if (q != p && Math.max(Math.abs(q.getAbsX() - p.getAbsX()), Math.abs(q.getAbsY() - p.getAbsY())) <= 1)
					rescued = true;
			if (rescued || e.getValue() <= 1 || !members.contains(p)) {
				it.remove();
				p.resetFreeze();
				if (rescued)
					broadcast(Color.DARK_GREEN.wrap(p.getName() + " is pulled free of the shadows!"));
			} else {
				e.setValue(e.getValue() - 1);
			}
		}
	}

	private boolean barsWereClosed;

	/** the Shadow bars across the purple walkway (3296,4648): spawned while down, removed when they lift */
	private void setBars(boolean down) {
		if (!down) {
			if (bars != null && !bars.isRemoved())
				bars.remove();
			bars = null;
			return;
		}
		Position at = at(3296, 4648);
		for (Player p : members) // nobody is caught inside the bars: the slam pushes them back a tile
			if (p.getAbsX() == at.getX() && p.getAbsY() == at.getY() && p.getHeight() == 0)
				p.getMovement().teleport(at.getX(), at.getY() - 1, 0);
		bars = GameObject.spawn(SHADOW_BARS, at.getX(), at.getY(), 0, 10, 0);
		objects.add(bars);
	}
	/** group runs: members a stalker has snared -> ticks left before they break free on their own */
	private final Map<Player, Integer> snared = new HashMap<>();

	/**
	 * A shadow stalker caught {@code p}: 20% of their max hitpoints. Solo, they're thrown back to the start of the purple
	 * walkway; in a group they're snared in place until a teammate stands next to them.
	 */
	void caught(Player p) {
		if (!members.contains(p) || snared.containsKey(p))
			return;
		int dmg = Math.max(1, p.getMaxHp() / 5);
		p.hit(new io.ruin.model.combat.Hit().fixedDamage(dmg));
		if (size == 1) {
			p.getMovement().teleport(map.convertX(3296), map.convertY(4642), 0);
			p.sendMessage(Color.RED.wrap("A shadow stalker drags you back down the purple walkway!"));
		} else {
			snared.put(p, 20);
			p.freeze(12, null); // 12 seconds = the 20-tick snare
			broadcast(Color.RED.wrap(p.getName() + " is snared by the shadows! Stand next to them to pull them free."));
		}
	}

	/** hit points x (1 + perExtra x (party size - 1)) */
	void scaleHp(NPC npc, double perExtra) {
		var hp = npc.getCombat().getStat(StatType.Hitpoints);
		int max = (int) Math.round(hp.fixedLevel * (1 + perExtra * (size - 1)));
		hp.fixedLevel = max;
		hp.currentLevel = max;
	}

	Player nearest(NPC n, int room) {
		Player best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Player p : membersInRoom(room)) {
			int d = Math.max(Math.abs(p.getAbsX() - n.getAbsX()), Math.abs(p.getAbsY() - n.getAbsY()));
			if (d < bestDist) {
				best = p;
				bestDist = d;
			}
		}
		return best;
	}

	/* ------------------------------------------------------------------ rewards */

	/**
	 * Pays every member still in the run: fixed pay (shards, coins, perk points, a perk lamp from seal 3) + FiveSealsLoot's
	 * raid-style table rolls (purples and the pet on a full clear). A cash-out pays straight into the inventory (bank when full) and ends the run; a
	 * full clear rolls each member's loot into their Sovereign Vault window (FiveSealsVault, user 2026-11-17: "opening the
	 * rewards in an interface like the other raids") and the run ends once every window is dealt with. The haul scales with the seals broken and their multiplier:
	 * Ascension shards, perk points, coins, the Item Upgrader materials, a perk lamp from seal 3 on; everything but the lamp
	 * x(1 + 5% per Ascension sigil tier). Rare (full clears only): the Ascension sigil's free upgrade (it replaced the Karn
	 * partyhat, user 2026-11-17).
	 */
	void payout(boolean fullClear) {
		if (ended)
			return;
		double m = multiplier();
		int seals = completed;
		double rare = group ? 1.10 : 1.0; // group runs: +10% rare drop chance
		int ticks = (int) io.ruin.Server.currentTick() - startTick;
		for (Player p : new ArrayList<>(members)) {
			double sigil = FiveSealsSigil.multiplier(p);                      // Ascension sigil: +5% per tier
			double pm = (fullClear && p.fiveSealsGreed ? 6.0 : m) * sigil;     // Sovereign Greed (Token Shop)
			List<Item> loot = new ArrayList<>();
			add(loot, 34669, (int) Math.round(15 * seals * pm));              // Ascension shards
			int perkPoints = (int) Math.round(2 * seals * pm);
			p.perkPoints += perkPoints;
			add(loot, 995, (int) Math.round(75_000 * seals * pm));             // coins
			if (seals >= 3)
				add(loot, 43820, seals >= 5 ? 2 : 1);                            // Perk exp lamp
			// the raid-style table (common / uncommon / rare) + on a full clear the purple and pet rolls (user 2026-11-17:
			// essence and teeth are no longer guaranteed, they're rare-table rolls)
			FiveSealsLoot.roll(p, loot, seals, pm, fullClear, group);
			if (fullClear) {
				FiveSealsLoot.countClear(p);
				if (Random.get() < rare / 300)
					FiveSealsSigil.rareDrop(p);                                   // the sigil's free upgrade
			}
			p.sendMessage(Color.DARK_GREEN.wrap("The Five Seals reward you (x" + String.format("%.2f", pm) + (sigil > 1 ? ", sigil +"
				+ FiveSealsSigil.percent(FiveSealsSigil.tier(p)) + "%" : "") + "): " + perkPoints + " perk points"
				+ (fullClear ? " and the vault's loot. Run time: " + FiveSeals.time(ticks) + "." : " and your loot.")));
			if (fullClear) {
				FiveSealsVault.put(p, loot);
				FiveSealsVault.open(p);
			} else
				for (Item item : loot)
					give(p, item.getId(), item.getAmount());
		}
		if (!fullClear) {
			end();
			return;
		}
		FiveSeals.recordTime(members, group, ticks);
		vaultPaid = true;
		vaultDeadline = (int) io.ruin.Server.currentTick() + VAULT_TICKS;
		broadcast(Color.DARK_GREEN.wrap("The Sovereign's vault is open: bank, take or discard your loot. (Close it and it goes to your bank.)"));
	}

	private static void add(List<Item> loot, int id, int amount) {
		if (amount > 0)
			loot.add(new Item(id, amount));
	}

	private static void give(Player p, int id, int amount) {
		if (amount <= 0)
			return;
		if (p.getInventory().hasRoomFor(id, amount))
			p.getInventory().add(id, amount);
		else {
			p.getBank().add(id, amount, null);
			p.sendMessage(amount + " x " + ObjType.get(id).name + " was sent to your bank.");
		}
	}
}
